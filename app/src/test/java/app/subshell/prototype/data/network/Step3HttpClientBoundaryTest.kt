package app.subshell.prototype.data.network

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * STEP3 (net01): the shared read client never follows a redirect on its own
 * (a redirect could carry the bearer token to another host) and retries only
 * the connection; every JSON read names the app and carries the account's
 * token; an answer larger than the limit is a protocol failure, never a crash
 * or an unbounded read; and a read whose coroutine is cancelled cancels its
 * OkHttp call instead of leaving it running.
 */
class Step3HttpClientBoundaryTest {
    private val endpoint = NetworkEndpoint.https("https://oauth.reddit.test/")
    private val jsonType = "application/json".toMediaType()

    private fun client(
        callFactory: Call.Factory,
        maxJsonBodyBytes: Long = SubshellHttpClient.DEFAULT_MAX_JSON_BODY_BYTES,
    ) = SubshellHttpClient(
        callFactory = callFactory,
        endpoint = endpoint,
        json = SubshellNetworkJson.providerJson,
        userAgent = "android:subshell-test:1.0 (by /u/tester)",
        bearerTokenProvider = { "token-123" },
        maxJsonBodyBytes = maxJsonBodyBytes,
    )

    private fun answering(
        requests: MutableList<Request> = mutableListOf(),
        body: (Request) -> okhttp3.ResponseBody,
    ): OkHttpClient = OkHttpClient.Builder().addInterceptor(
        Interceptor { chain ->
            requests += chain.request()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK")
                .body(body(chain.request()))
                .build()
        },
    ).build()

    private fun read(client: SubshellHttpClient): NetworkResult<JsonElement> = runBlocking {
        client.getJson(JsonGetRequest(listOf("api", "v1", "me")), JsonElement.serializer())
    }

    /** A JSON array of [count] small numbers: valid JSON well past a small limit. */
    private fun bigJsonArray(count: Int): String =
        (1..count).joinToString(prefix = "[", postfix = "]", separator = ",") { "1" }

    @Test
    fun theSharedReadClientFollowsNoRedirectAndRetriesOnlyTheConnection() {
        val shared = SubshellNetworkTransport.sharedCallFactory as OkHttpClient
        assertFalse("no redirect is followed", shared.followRedirects)
        assertFalse("no HTTPS → HTTP redirect is followed", shared.followSslRedirects)
        assertTrue("a dropped connection is retried", shared.retryOnConnectionFailure)

        val fresh = SubshellOkHttpClientFactory.create()
        assertFalse(fresh.followRedirects)
        assertFalse(fresh.followSslRedirects)
        assertTrue(fresh.retryOnConnectionFailure)
    }

    @Test
    fun aJsonReadNamesTheAppAndCarriesTheAccountsToken() {
        val requests = mutableListOf<Request>()
        val result = read(client(answering(requests) { "{}".toResponseBody(jsonType) }))

        assertTrue(result.toString(), result is NetworkResult.Success)
        val request = requests.single()
        assertEquals("android:subshell-test:1.0 (by /u/tester)", request.header("User-Agent"))
        assertEquals("Bearer token-123", request.header("Authorization"))
        assertEquals("GET", request.method)
        assertEquals("/api/v1/me", request.url.encodedPath)
    }

    @Test
    fun anAnswerLargerThanTheLimitIsABodyTooLargeFailure() {
        val body = bigJsonArray(200)
        val limit = 64L

        // Reddit says how long the answer is.
        val announced = read(client(answering { body.toResponseBody(jsonType) }, maxJsonBodyBytes = limit))
        assertEquals(
            NetworkResult.ProtocolFailure(NetworkProtocolFailureKind.BODY_TOO_LARGE),
            announced,
        )

        // Reddit does not say (chunked): the read stops at the limit.
        val streamed = read(
            client(
                answering { Buffer().writeUtf8(body).asResponseBody(jsonType, -1L) },
                maxJsonBodyBytes = limit,
            ),
        )
        assertEquals(
            NetworkResult.ProtocolFailure(NetworkProtocolFailureKind.BODY_TOO_LARGE),
            streamed,
        )

        // The same answer within the limit is read.
        val fits = read(client(answering { body.toResponseBody(jsonType) }))
        assertTrue(fits.toString(), fits is NetworkResult.Success)
    }

    @Test
    fun cancellingTheReadCancelsItsCall() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val call = AtomicReference<Call>()
        val blocking = OkHttpClient.Builder().addInterceptor(
            Interceptor { chain ->
                call.set(chain.call())
                started.countDown()
                release.await(10, TimeUnit.SECONDS)
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK")
                    .body("{}".toResponseBody(jsonType))
                    .build()
            },
        ).build()
        val client = client(blocking)

        try {
            val read = launch(Dispatchers.Default) {
                client.getJson(JsonGetRequest(listOf("api", "v1", "me")), JsonElement.serializer())
            }
            assertTrue("the call reached the network", started.await(10, TimeUnit.SECONDS))

            read.cancelAndJoin()

            val inFlight = call.get()
            assertNotNull(inFlight)
            assertTrue("the OkHttp call is cancelled with its coroutine", inFlight.isCanceled())
        } finally {
            release.countDown()
        }
    }
}
