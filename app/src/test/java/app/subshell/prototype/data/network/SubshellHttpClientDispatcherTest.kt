package app.subshell.prototype.data.network

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertTrue
import org.junit.Test

class SubshellHttpClientDispatcherTest {
    @Test
    fun responseLifecycleUsesOwnedIoDispatcherInsteadOfCallerDispatcher() {
        val callerExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, CALLER_THREAD)
        }
        val ioExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, IO_THREAD)
        }
        val callerDispatcher = callerExecutor.asCoroutineDispatcher()
        val ioDispatcher = ioExecutor.asCoroutineDispatcher()
        val observedProcessingThread = AtomicReference<String>()

        try {
            val client = SubshellHttpClient(
                callFactory = ImmediateJsonCallFactory("{\"value\":\"ok\"}"),
                endpoint = NetworkEndpoint.https("https://example.test/"),
                json = SubshellNetworkJson.providerJson,
                userAgent = "subshell-net01-dispatcher-test",
                clock = NetworkClock {
                    observedProcessingThread.set(Thread.currentThread().name)
                    1_000L
                },
                ioDispatcher = ioDispatcher,
            )

            val result = runBlocking(callerDispatcher) {
                val callerThreadName = Thread.currentThread().name
                assertTrue(
                    "Expected caller dispatcher thread prefix $CALLER_THREAD but was $callerThreadName",
                    callerThreadName.startsWith(CALLER_THREAD),
                )
                client.getJson(
                    request = JsonGetRequest(pathSegments = listOf("fixture")),
                    deserializer = DispatcherFixture.serializer(),
                )
            }

            assertTrue(result is NetworkResult.Success)
            val processingThreadName = observedProcessingThread.get().orEmpty()
            assertTrue(
                "Expected owned IO dispatcher thread prefix $IO_THREAD but was $processingThreadName",
                processingThreadName.startsWith(IO_THREAD),
            )
            assertTrue(
                "Response processing remained on caller dispatcher: $processingThreadName",
                !processingThreadName.startsWith(CALLER_THREAD),
            )
        } finally {
            callerDispatcher.close()
            ioDispatcher.close()
        }
    }

    private companion object {
        const val CALLER_THREAD = "subshell-net01-caller"
        const val IO_THREAD = "subshell-net01-io"
    }
}

@Serializable
private data class DispatcherFixture(
    val value: String,
)

private class ImmediateJsonCallFactory(
    body: String,
) : Call.Factory {
    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
        .build()

    override fun newCall(request: Request): Call = client.newCall(request)
}
