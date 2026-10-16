package app.subshell.prototype.media

import android.content.Context
import android.util.Log
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.subshell.prototype.R
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.net.InetAddress
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val MEDIA04A1_LOG_TAG = "subshell-cache"
private const val MEDIA04A1_KEYSTORE_ASSET = "subshell_media04a1_test_server.p12"
private const val MEDIA04A1_KEYSTORE_PASSWORD = "subshell-test"
private const val MEDIA04A1_CACHE_KEY = "subshell:media04a1:https-progressive-fixture:v1"
private const val MEDIA04A1_CACHE_DATABASE = "exoplayer_internal.db"

@RunWith(AndroidJUnit4::class)
class VideoMediaHttpsCacheInstrumentedTest {
    @Test
    fun coldHttpsReadWritesCacheAndOfflineReadHitsIt() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixture = context.resources.openRawResource(R.raw.subshell_video_lab_a).use { input -> input.readBytes() }
        val fixtureSha256 = sha256(fixture)
        val cacheDirectory = File(context.cacheDir, VIDEO_MEDIA_CACHE_DIRECTORY)

        // The instrumentation process is started after a force-stop, before the media coordinator.
        // Reset both cache files and their index so the first request is provably cold.
        assertTrue(cacheDirectory.deleteRecursively() || !cacheDirectory.exists())
        context.deleteDatabase(MEDIA04A1_CACHE_DATABASE)

        val telemetryBefore = VideoMediaDataSource.telemetrySnapshot()
        val server = LocalHttpsFixtureServer(
            context = instrumentation.context,
            payload = fixture,
        ).also(LocalHttpsFixtureServer::start)

        val fixtureUri = "https://127.0.0.1:${server.port}/media04a1.mp4"
        val firstRead = readFully(
            factory = VideoMediaDataSource.factory(context),
            uri = fixtureUri,
            cacheKey = MEDIA04A1_CACHE_KEY,
        )
        val requestsAfterFirstRead = server.requestCount
        server.close()
        server.assertHealthy()

        val telemetryAfterFirst = VideoMediaDataSource.telemetrySnapshot()
        val cacheBytesAfterFirst = cacheDirectory.totalFileBytes()

        assertArrayEquals(fixture, firstRead)
        assertEquals(fixtureSha256, sha256(firstRead))
        assertTrue("The cold read must reach the HTTPS server", requestsAfterFirstRead > 0)
        assertTrue(
            "The cold read must consume upstream bytes",
            telemetryAfterFirst.upstreamBytesRead - telemetryBefore.upstreamBytesRead >= fixture.size,
        )
        assertTrue("The cold read must create cache files", cacheBytesAfterFirst > 0L)
        assertEquals(
            "The cold request must not bypass the cache",
            telemetryBefore.cacheIgnoredEvents,
            telemetryAfterFirst.cacheIgnoredEvents,
        )

        // The server is now closed. Success therefore requires an actual cache hit.
        val secondRead = readFully(
            factory = VideoMediaDataSource.factory(context),
            uri = fixtureUri,
            cacheKey = MEDIA04A1_CACHE_KEY,
        )
        val telemetryAfterSecond = VideoMediaDataSource.telemetrySnapshot()

        assertArrayEquals(fixture, secondRead)
        assertEquals(fixtureSha256, sha256(secondRead))
        assertTrue(
            "The offline read must consume cached bytes",
            telemetryAfterSecond.cachedBytesRead - telemetryAfterFirst.cachedBytesRead >= fixture.size,
        )
        assertTrue(
            "The offline read must emit a cache-read event",
            telemetryAfterSecond.cacheReadEvents > telemetryAfterFirst.cacheReadEvents,
        )
        assertEquals(
            "No new upstream request may occur after the HTTPS server is closed",
            telemetryAfterFirst.upstreamRequests,
            telemetryAfterSecond.upstreamRequests,
        )
        assertEquals(
            "No new upstream bytes may be read after the HTTPS server is closed",
            telemetryAfterFirst.upstreamBytesRead,
            telemetryAfterSecond.upstreamBytesRead,
        )
        assertEquals(
            "The offline request must not bypass the cache",
            telemetryAfterFirst.cacheIgnoredEvents,
            telemetryAfterSecond.cacheIgnoredEvents,
        )

        Log.i(
            MEDIA04A1_LOG_TAG,
            "MEDIA04A1_HTTPS_REQUESTS=$requestsAfterFirstRead " +
                "FIXTURE_BYTES=${fixture.size} " +
                "CACHE_FILES_BYTES=$cacheBytesAfterFirst " +
                "UPSTREAM_BYTES=${telemetryAfterFirst.upstreamBytesRead - telemetryBefore.upstreamBytesRead} " +
                "CACHED_BYTES=${telemetryAfterSecond.cachedBytesRead - telemetryAfterFirst.cachedBytesRead}",
        )
        Log.i(MEDIA04A1_LOG_TAG, "RESULT=SUBSHELL_MEDIA04A1_HTTPS_CACHE_RUNTIME_PASS")
    }
}

internal fun readFully(
    factory: DataSource.Factory,
    uri: String,
    cacheKey: String,
): ByteArray {
    val source = factory.createDataSource()
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(32 * 1024)
    try {
        source.open(
            DataSpec.Builder()
                .setUri(uri)
                .setKey(cacheKey)
                .build(),
        )
        while (true) {
            val count = source.read(buffer, 0, buffer.size)
            if (count == -1) break
            output.write(buffer, 0, count)
        }
    } finally {
        source.close()
    }
    return output.toByteArray()
}

private fun File.totalFileBytes(): Long =
    takeIf(File::exists)
        ?.walkTopDown()
        ?.filter(File::isFile)
        ?.sumOf(File::length)
        ?: 0L

private fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

internal class LocalHttpsFixtureServer(
    context: Context,
    private val payload: ByteArray,
) : AutoCloseable {
    private val running = AtomicBoolean(false)
    private val requests = AtomicInteger()
    private val failure = AtomicReference<Throwable?>()
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "subshell-media04a1-https").apply { isDaemon = true }
    }
    private val serverSocket: SSLServerSocket = createServerSocket(context)

    val port: Int
        get() = serverSocket.localPort

    val requestCount: Int
        get() = requests.get()

    fun start() {
        check(running.compareAndSet(false, true)) { "HTTPS fixture server already started" }
        executor.execute {
            try {
                while (running.get()) {
                    val socket = try {
                        serverSocket.accept() as SSLSocket
                    } catch (error: SocketException) {
                        if (!running.get()) break
                        throw error
                    }
                    socket.use(::serve)
                }
            } catch (error: Throwable) {
                if (running.get()) failure.compareAndSet(null, error)
            }
        }
    }

    private fun serve(socket: SSLSocket) {
        socket.soTimeout = 10_000
        socket.startHandshake()
        val input = BufferedInputStream(socket.inputStream)
        val output = BufferedOutputStream(socket.outputStream)
        val requestLine = input.readAsciiLine()
        val headers = linkedMapOf<String, String>()
        while (true) {
            val line = input.readAsciiLine()
            if (line.isEmpty()) break
            val separator = line.indexOf(':')
            if (separator > 0) {
                headers[line.substring(0, separator).trim().lowercase()] =
                    line.substring(separator + 1).trim()
            }
        }

        check(requestLine.startsWith("GET /media04a1.mp4 HTTP/1.")) {
            "Unexpected request line: $requestLine"
        }
        requests.incrementAndGet()

        val range = headers["range"]?.let(::parseRange)
        val start = range?.first ?: 0
        val requestedEnd = range?.last ?: payload.lastIndex
        val end = requestedEnd.coerceAtMost(payload.lastIndex)
        check(start in 0..end) { "Invalid range start=$start end=$end" }
        val length = end - start + 1
        val status = if (range == null) "200 OK" else "206 Partial Content"

        output.write("HTTP/1.1 $status\r\n".toByteArray(StandardCharsets.US_ASCII))
        output.write("Content-Type: video/mp4\r\n".toByteArray(StandardCharsets.US_ASCII))
        output.write("Accept-Ranges: bytes\r\n".toByteArray(StandardCharsets.US_ASCII))
        output.write("Content-Length: $length\r\n".toByteArray(StandardCharsets.US_ASCII))
        if (range != null) {
            output.write(
                "Content-Range: bytes $start-$end/${payload.size}\r\n"
                    .toByteArray(StandardCharsets.US_ASCII),
            )
        }
        output.write("Connection: close\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
        output.write(payload, start, length)
        output.flush()
    }

    override fun close() {
        running.set(false)
        runCatching(serverSocket::close)
        executor.shutdownNow()
        executor.awaitTermination(5, TimeUnit.SECONDS)
    }

    fun assertHealthy() {
        failure.get()?.let { throw AssertionError("HTTPS fixture server failed", it) }
    }

    private fun createServerSocket(context: Context): SSLServerSocket {
        val password = MEDIA04A1_KEYSTORE_PASSWORD.toCharArray()
        val keyStore = KeyStore.getInstance("PKCS12")
        context.assets.open(MEDIA04A1_KEYSTORE_ASSET).use { input ->
            keyStore.load(input, password)
        }
        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keyManagerFactory.init(keyStore, password)
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(keyManagerFactory.keyManagers, null, null)
        return sslContext.serverSocketFactory.createServerSocket(
            0,
            8,
            InetAddress.getByName("127.0.0.1"),
        ) as SSLServerSocket
    }
}

private fun InputStream.readAsciiLine(): String {
    val output = ByteArrayOutputStream()
    while (true) {
        val value = read()
        when (value) {
            -1 -> if (output.size() == 0) throw EOFException("Unexpected end of HTTP request") else break
            '\n'.code -> break
            '\r'.code -> Unit
            else -> output.write(value)
        }
    }
    return output.toString(StandardCharsets.US_ASCII.name())
}

private fun parseRange(value: String): IntRange {
    val match = Regex("bytes=(\\d+)-(\\d*)").matchEntire(value)
        ?: error("Unsupported Range header: $value")
    val start = match.groupValues[1].toInt()
    val end = match.groupValues[2].takeIf(String::isNotEmpty)?.toInt() ?: Int.MAX_VALUE
    return start..end
}
