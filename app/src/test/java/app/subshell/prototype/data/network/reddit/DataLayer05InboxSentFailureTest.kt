package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.network.NetworkBearerTokenProvider
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DATALAYER05: every inbox page also reads /message/sent. On a later page
 * (appended to the cached list) a failed sent request no longer fails the
 * inbox page that did arrive; the first page, which replaces the list,
 * still fails so threads holding only your messages do not vanish.
 */
class DataLayer05InboxSentFailureTest {
    private val inboxBody = """
        {"kind":"Listing","data":{"after":null,"children":[
          {"kind":"t4","data":{"id":"root","name":"t4_root","author":"other_user",
           "subject":"Thread","body":"Root message","created_utc":1100}}]}}
    """.trimIndent()

    private fun source(): RedditInboxNetworkDataSource {
        val callFactory = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            // INBOXORDER01: a first page also reads the unread list; not part of this test.
            if (request.url.encodedPath.contains("/message/unread")) {
                return@addInterceptor Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK")
                    .body("""{"kind":"Listing","data":{"after":null,"children":[]}}""".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            val sent = request.url.encodedPath.contains("/message/sent")
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(if (sent) 503 else 200).message(if (sent) "Unavailable" else "OK")
                .body((if (sent) "{}" else inboxBody).toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        return RedditInboxNetworkDataSource(
            SubshellHttpClient(
                callFactory = callFactory,
                endpoint = NetworkEndpoint.https("https://example.test/"),
                json = SubshellNetworkJson.providerJson,
                userAgent = "subshell-unit-test",
                bearerTokenProvider = NetworkBearerTokenProvider { "token" },
            ),
        )
    }

    @Test
    fun aLaterPageKeepsItsInboxItemsWhenSentFails() = runBlocking {
        val result = source().page(cursor = "t4_previous", limit = 25, currentUsername = "CurrentUser")

        assertTrue(result.toString(), result is NetworkResult.Success)
        assertEquals(
            listOf("t4_root"),
            (result as NetworkResult.Success).value.items.map { item -> item.id },
        )
    }

    @Test
    fun theFirstPageStillFailsWhenSentFails() = runBlocking {
        val result = source().page(cursor = null, limit = 25, currentUsername = "CurrentUser")

        assertTrue(result.toString(), result is NetworkResult.HttpFailure)
        assertEquals(503, (result as NetworkResult.HttpFailure).statusCode)
    }
}
