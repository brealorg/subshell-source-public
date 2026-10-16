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
 * STEP3 (inbox01a14): on the first inbox page, sent history newer than the
 * inbox page is paged back until it reaches the inbox page's age (and the
 * inbox in turn), so your older reply shows in its thread; the paging stops
 * at four pages a side.
 */
class Step3InboxFirstPageBalanceTest {
    private val inboxPage = """
        {"kind":"Listing","data":{"after":"t4_i2","children":[
          {"kind":"t4","data":{"id":"root","name":"t4_root","author":"other_user","dest":"CurrentUser",
           "subject":"Old thread","body":"Old question","created_utc":1000}}]}}
    """.trimIndent()

    private val olderInboxPage = """
        {"kind":"Listing","data":{"after":null,"children":[
          {"kind":"t4","data":{"id":"older","name":"t4_older","author":"third","dest":"CurrentUser",
           "subject":"Older thread","body":"Older question","created_utc":850}}]}}
    """.trimIndent()

    private fun sentPage(after: String?, createdUtc: Int, extra: String = "") = """
        {"kind":"Listing","data":{"after":${after?.let { "\"$it\"" } ?: "null"},"children":[
          {"kind":"t4","data":{"id":"s$createdUtc","name":"t4_s$createdUtc","author":"CurrentUser","dest":"someone",
           "subject":"Thread $createdUtc","body":"Sent $createdUtc","created_utc":$createdUtc}}$extra]}}
    """.trimIndent()

    private val oldReply = """,
          {"kind":"t4","data":{"id":"reply","name":"t4_reply","author":"CurrentUser","dest":"other_user",
           "subject":"re: Old thread","body":"My old answer","first_message_name":"t4_root","created_utc":1100}}"""

    private val inboxRequests = mutableListOf<String?>()
    private val sentRequests = mutableListOf<String?>()

    private fun source(sent: (String?) -> String): RedditInboxNetworkDataSource {
        val callFactory = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val after = request.url.queryParameter("after")
            val body = if (request.url.encodedPath.contains("/message/sent")) {
                sentRequests += after
                sent(after)
            } else {
                inboxRequests += after
                if (after == null) inboxPage else olderInboxPage
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

    @Test
    fun theFirstPagePagesSentHistoryBackToTheInboxAge() = runBlocking {
        val source = source { after ->
            if (after == null) sentPage(after = "t4_s2", createdUtc = 5000) else sentPage(after = null, createdUtc = 900, extra = oldReply)
        }

        val result = source.page(cursor = null, limit = 25, currentUsername = "CurrentUser")

        assertTrue(result.toString(), result is NetworkResult.Success)
        val thread = (result as NetworkResult.Success).value.items.single { it.id == "t4_root" }
        assertTrue(thread.conversation.any { it.body == "My old answer" })
        assertEquals(listOf(null, "t4_s2"), sentRequests)
        // Both sides are balanced: the sent page went older than the inbox
        // page, so the inbox is paged back once too.
        assertEquals(listOf(null, "t4_i2"), inboxRequests)
    }

    @Test
    fun theFirstPageStopsAfterFourSentPages() = runBlocking {
        var page = 0
        val source = source { _ ->
            page += 1
            sentPage(after = "t4_more$page", createdUtc = 5000 - page)
        }

        source.page(cursor = null, limit = 25, currentUsername = "CurrentUser")

        assertEquals(4, sentRequests.size)
    }
}
