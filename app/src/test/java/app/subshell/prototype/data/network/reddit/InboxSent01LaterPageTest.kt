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
 * INBOXSENT01 (OCB-632): a later inbox page was merged only with the newest
 * 100 sent messages, so your reply in an older thread never showed. Sent
 * history is now paged back to the page's oldest message.
 */
class InboxSent01LaterPageTest {
    private val inboxPage = """
        {"kind":"Listing","data":{"after":null,"children":[
          {"kind":"t4","data":{"id":"root","name":"t4_root","author":"other_user","dest":"CurrentUser",
           "subject":"Old thread","body":"Old question","created_utc":1100}}]}}
    """.trimIndent()
    private val sentNewest = """
        {"kind":"Listing","data":{"after":"t4_new","children":[
          {"kind":"t4","data":{"id":"new","name":"t4_new","author":"CurrentUser","dest":"someone",
           "subject":"New thread","body":"Recent","created_utc":5000}}]}}
    """.trimIndent()
    private val sentOlder = """
        {"kind":"Listing","data":{"after":null,"children":[
          {"kind":"t4","data":{"id":"reply","name":"t4_reply","author":"CurrentUser","dest":"other_user",
           "subject":"re: Old thread","body":"My old answer","first_message_name":"t4_root","created_utc":1200}},
          {"kind":"t4","data":{"id":"older","name":"t4_older","author":"CurrentUser","dest":"third",
           "subject":"Older","body":"Before","created_utc":900}}]}}
    """.trimIndent()
    private val sentRequests = mutableListOf<String?>()

    @Test
    fun aLaterPageShowsYourReplyFromOlderSentHistory() = runBlocking {
        val result = source().page(cursor = "t4_previous", limit = 25, currentUsername = "CurrentUser")

        assertTrue(result.toString(), result is NetworkResult.Success)
        val thread = (result as NetworkResult.Success).value.items.single { it.id == "t4_root" }
        assertTrue(thread.conversation.map { it.body }.toString(), thread.conversation.any { it.body == "My old answer" })
        assertEquals(listOf(null, "t4_new"), sentRequests)
    }

    private fun source(): RedditInboxNetworkDataSource {
        val callFactory = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = if (request.url.encodedPath.contains("/message/sent")) {
                val after = request.url.queryParameter("after")
                sentRequests += after
                if (after == null) sentNewest else sentOlder
            } else {
                inboxPage
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK")
                .body(body.toResponseBody("application/json".toMediaType()))
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
}
