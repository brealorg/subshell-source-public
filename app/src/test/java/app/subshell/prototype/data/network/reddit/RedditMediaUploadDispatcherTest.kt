package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.auth.reddit.RedditMediaStageResult
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertTrue
import org.junit.Test

class RedditMediaUploadDispatcherTest {
    @Test
    fun binaryUploadLifecycleUsesOwnedIoDispatcherInsteadOfCallerDispatcher() {
        val callerExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, CALLER_THREAD)
        }
        val ioExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, IO_THREAD)
        }
        val callerDispatcher = callerExecutor.asCoroutineDispatcher()
        val ioDispatcher = ioExecutor.asCoroutineDispatcher()
        val observedNewCallThread = AtomicReference<String>()
        val uploadCallFactory = RecordingUploadCallFactory(observedNewCallThread)
        val stagedFile = Files.createTempFile("subshell-upload-", ".gif").toFile()

        stagedFile.writeBytes(byteArrayOf(0x47, 0x49, 0x46, 0x38, 0x39, 0x61))

        try {
            val redditHttpClient = SubshellHttpClient(
                callFactory = uploadCallFactory,
                endpoint = NetworkEndpoint.https("https://example.test/"),
                json = SubshellNetworkJson.providerJson,
                userAgent = "subshell-media-upload-dispatcher-test",
            )
            val source = RedditMediaUploadNetworkDataSource(
                redditHttpClient = redditHttpClient,
                uploadCallFactory = uploadCallFactory,
                webSocketClient = OkHttpClient(),
                json = SubshellNetworkJson.providerJson,
                ioDispatcher = ioDispatcher,
            )
            val lease = RedditMediaUploadLease(
                uploadUrl =
                    "https://reddit-uploaded-media.s3-accelerate.amazonaws.com".toHttpUrl(),
                fields = listOf(
                    "key" to "t2_test/dispatcher.gif",
                    "policy" to "opaque-policy",
                ),
            )
            val staged = RedditMediaStageResult.Staged(
                file = stagedFile,
                fileName = stagedFile.name,
                mimeType = "image/gif",
                sizeBytes = stagedFile.length(),
            )

            val result = runBlocking(callerDispatcher) {
                assertTrue(Thread.currentThread().name.startsWith(CALLER_THREAD))
                source.upload(lease, staged)
            }

            assertTrue(result is RedditMediaBinaryUploadResult.Uploaded)

            val networkThread = observedNewCallThread.get().orEmpty()
            assertTrue(
                "Expected upload newCall on $IO_THREAD but was $networkThread",
                networkThread.startsWith(IO_THREAD),
            )
            assertTrue(
                "Binary upload remained on caller dispatcher: $networkThread",
                !networkThread.startsWith(CALLER_THREAD),
            )
        } finally {
            stagedFile.delete()
            callerDispatcher.close()
            ioDispatcher.close()
        }
    }

    private companion object {
        const val CALLER_THREAD = "subshell-media-upload-caller"
        const val IO_THREAD = "subshell-media-upload-io"
    }
}

private class RecordingUploadCallFactory(
    private val observedNewCallThread: AtomicReference<String>,
) : Call.Factory {
    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(204)
                .message("No Content")
                .body(ByteArray(0).toResponseBody(null))
                .build()
        }
        .build()

    override fun newCall(request: Request): Call {
        observedNewCallThread.set(Thread.currentThread().name)
        return client.newCall(request)
    }
}
