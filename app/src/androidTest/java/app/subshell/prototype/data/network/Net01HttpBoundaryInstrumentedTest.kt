package app.subshell.prototype.data.network

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.subshell.prototype.BuildConfig
import app.subshell.prototype.data.RepositoryPageRequest
import app.subshell.prototype.data.network.reddit.RedditListingNetworkDataSource
import app.subshell.prototype.data.network.reddit.RedditListingSort
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.net.InetAddress
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val NET01_LOG_TAG = "subshell-net01"
private const val NET01_KEYSTORE_ASSET = "subshell_media04a1_test_server.p12"
private const val NET01_KEYSTORE_PASSWORD = "subshell-test"

@RunWith(AndroidJUnit4::class)
class Net01HttpBoundaryInstrumentedTest {
    @Test
    fun secureJsonBoundaryEnforcesHeadersBoundsFailuresAndCancellation() {
        runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val tokenProvider = MutableBearerTokenProvider()
        val server = LocalJsonHttpsServer(instrumentation.context).also(LocalJsonHttpsServer::start)
        val okHttpClient = SubshellOkHttpClientFactory.create()
        val capturingFactory = CapturingCallFactory(okHttpClient)
        val httpClient = SubshellHttpClient(
            callFactory = capturingFactory,
            endpoint = NetworkEndpoint.https("https://127.0.0.1:${server.port}/"),
            json = SubshellNetworkJson.providerJson,
            userAgent = "android:${BuildConfig.APPLICATION_ID}:0.46.0 (by /u/subshell-test)",
            bearerTokenProvider = tokenProvider,
            clock = NetworkClock { 1_000_000L },
            maxJsonBodyBytes = 4_096L,
        )
        val dataSource = RedditListingNetworkDataSource(
            httpClient = httpClient,
            clock = { 10_000L },
        )

        server.enqueue(
            FixtureResponse(
                status = "200 OK",
                body = LISTING_JSON,
                headers = mapOf(
                    "Content-Type" to "application/json; charset=utf-8",
                    "X-Ratelimit-Remaining" to "95.5",
                    "X-Ratelimit-Used" to "4.5",
                    "X-Ratelimit-Reset" to "60",
                ),
            ),
        )
        val success = dataSource.posts(
            community = "android",
            sort = RedditListingSort.HOT,
            request = RepositoryPageRequest(cursor = "t3_cursor", limit = 2),
        )
        assertTrue(success is NetworkResult.Success)
        success as NetworkResult.Success
        assertEquals(2, success.value.items.size)
        assertEquals("t3_next", success.value.nextCursor)
        assertEquals(95.5, success.metadata.rateLimit.remaining)
        assertEquals(60_000L, success.metadata.rateLimit.resetAfterMs)

        val firstRequest = server.requestsSnapshot().single()
        assertTrue(firstRequest.requestLine.startsWith("GET /r/android/hot?"))
        assertTrue(firstRequest.requestLine.contains("raw_json=1"))
        assertTrue(firstRequest.requestLine.contains("limit=2"))
        assertTrue(firstRequest.requestLine.contains("after=t3_cursor"))
        assertEquals("application/json", firstRequest.headers["accept"])
        assertTrue(firstRequest.headers["user-agent"].orEmpty().contains(BuildConfig.APPLICATION_ID))
        assertFalse(firstRequest.headers.containsKey("authorization"))

        tokenProvider.token = "test-access-token"
        server.enqueue(jsonResponse(EMPTY_LISTING_JSON))
        val authenticated = dataSource.posts(
            sort = RedditListingSort.BEST,
            request = RepositoryPageRequest(limit = 1),
        )
        assertTrue(authenticated is NetworkResult.Success)
        assertEquals(
            "Bearer test-access-token",
            server.requestsSnapshot().last().headers["authorization"],
        )

        server.enqueue(
            FixtureResponse(
                status = "429 Too Many Requests",
                body = "{}",
                headers = mapOf(
                    "Content-Type" to "application/json",
                    "Retry-After" to "7",
                ),
            ),
        )
        val rateLimited = dataSource.posts(request = RepositoryPageRequest(limit = 1))
        assertTrue(rateLimited is NetworkResult.HttpFailure)
        rateLimited as NetworkResult.HttpFailure
        assertTrue(rateLimited.retryable)
        assertEquals(7_000L, rateLimited.metadata.retryAfterMs)

        server.enqueue(jsonResponse("{not-json"))
        val malformed = dataSource.posts(request = RepositoryPageRequest(limit = 1))
        assertEquals(
            NetworkProtocolFailureKind.MALFORMED_JSON,
            (malformed as NetworkResult.ProtocolFailure).kind,
        )

        server.enqueue(jsonResponse("{\"padding\":\"${"x".repeat(5_000)}\"}"))
        val oversized = dataSource.posts(request = RepositoryPageRequest(limit = 1))
        assertEquals(
            NetworkProtocolFailureKind.BODY_TOO_LARGE,
            (oversized as NetworkResult.ProtocolFailure).kind,
        )

        server.enqueue(jsonResponse(EMPTY_LISTING_JSON, delayMs = 5_000L))
        val cancelledJob = launch(start = CoroutineStart.UNDISPATCHED) {
            dataSource.posts(request = RepositoryPageRequest(limit = 1))
        }
        assertTrue(
            "The cancellation request did not reach the HTTPS fixture server",
            server.awaitRequestCount(expected = 6, timeoutMs = 5_000L),
        )
        cancelledJob.cancelAndJoin()
        assertTrue(capturingFactory.latestCall()?.isCanceled() == true)

        server.close()
        server.assertHealthy()
        assertEquals(6, server.requestsSnapshot().size)

        Log.i(
            NET01_LOG_TAG,
            "NET01_HTTPS_REQUESTS=6 JSON_ITEMS=2 RATE_LIMIT_RETRY_MS=7000 " +
                "MALFORMED_JSON=true BODY_LIMIT=true CALL_CANCELLED=true AUTH_HEADER_SCOPED=true",
        )
        Log.i(NET01_LOG_TAG, "RESULT=SUBSHELL_NET01_SECURE_HTTP_RUNTIME_PASS")
        }
    }
}

private class MutableBearerTokenProvider : NetworkBearerTokenProvider {
    @Volatile
    var token: String? = null

    override fun bearerToken(): String? = token
}

private class CapturingCallFactory(
    private val delegate: Call.Factory,
) : Call.Factory {
    private val latest = AtomicReference<Call?>()

    override fun newCall(request: Request): Call = delegate.newCall(request).also(latest::set)

    fun latestCall(): Call? = latest.get()
}

internal data class FixtureResponse(
    val status: String,
    val body: String,
    val headers: Map<String, String>,
    val delayMs: Long = 0L,
)

internal data class RecordedRequest(
    val requestLine: String,
    val headers: Map<String, String>,
    val body: String,
)

internal fun jsonResponse(body: String, delayMs: Long = 0L): FixtureResponse = FixtureResponse(
    status = "200 OK",
    body = body,
    headers = mapOf("Content-Type" to "application/json; charset=utf-8"),
    delayMs = delayMs,
)

internal class LocalJsonHttpsServer(
    context: Context,
) : AutoCloseable {
    private val running = AtomicBoolean(false)
    private val failure = AtomicReference<Throwable?>()
    private val responses = LinkedBlockingQueue<FixtureResponse>()
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "subshell-net01-https").apply { isDaemon = true }
    }
    private val serverSocket: SSLServerSocket = createServerSocket(context)

    val port: Int
        get() = serverSocket.localPort

    fun enqueue(response: FixtureResponse) {
        responses.put(response)
    }

    fun requestsSnapshot(): List<RecordedRequest> = requests.toList()

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

    fun awaitRequestCount(expected: Int, timeoutMs: Long): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (System.nanoTime() < deadline) {
            if (requests.size >= expected) return true
            Thread.sleep(10L)
        }
        return requests.size >= expected
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
        val contentLength = headers["content-length"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val requestBody = ByteArray(contentLength)
        var offset = 0
        while (offset < requestBody.size) {
            val read = input.read(requestBody, offset, requestBody.size - offset)
            if (read == -1) throw EOFException("Unexpected end of HTTP request body")
            offset += read
        }
        requests += RecordedRequest(
            requestLine = requestLine,
            headers = headers,
            body = requestBody.toString(StandardCharsets.UTF_8),
        )
        val response = responses.poll(5, TimeUnit.SECONDS)
            ?: error("No queued NET01 HTTPS response")
        if (response.delayMs > 0L) Thread.sleep(response.delayMs)

        val bodyBytes = response.body.toByteArray(StandardCharsets.UTF_8)
        output.write("HTTP/1.1 ${response.status}\r\n".toByteArray(StandardCharsets.US_ASCII))
        response.headers.forEach { (name, value) ->
            output.write("$name: $value\r\n".toByteArray(StandardCharsets.US_ASCII))
        }
        output.write("Content-Length: ${bodyBytes.size}\r\n".toByteArray(StandardCharsets.US_ASCII))
        output.write("Connection: close\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
        output.write(bodyBytes)
        output.flush()
    }

    override fun close() {
        running.set(false)
        runCatching(serverSocket::close)
        executor.shutdownNow()
        executor.awaitTermination(5, TimeUnit.SECONDS)
    }

    fun assertHealthy() {
        failure.get()?.let { throw AssertionError("NET01 HTTPS fixture server failed", it) }
    }

    private fun createServerSocket(context: Context): SSLServerSocket {
        val password = NET01_KEYSTORE_PASSWORD.toCharArray()
        val keyStore = KeyStore.getInstance("PKCS12")
        context.assets.open(NET01_KEYSTORE_ASSET).use { input ->
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

private const val LISTING_JSON = """
{
  "kind":"Listing",
  "data":{
    "after":"t3_next",
    "children":[
      {"kind":"t3","data":{"id":"one","name":"t3_one","subreddit_name_prefixed":"r/android","author":"alice","created_utc":9400,"title":"First","selftext":"Body","score":10,"num_comments":2,"unknown_field":"ignored"}},
      {"kind":"t3","data":{"id":"two","name":"t3_two","subreddit":"android","author":"bob","created_utc":9000,"title":"Second","post_hint":"link","domain":"example.test","score":5,"num_comments":1}}
    ],
    "unknown_listing_field":true
  }
}
"""

private const val EMPTY_LISTING_JSON = """{"kind":"Listing","data":{"after":null,"children":[]}}"""
