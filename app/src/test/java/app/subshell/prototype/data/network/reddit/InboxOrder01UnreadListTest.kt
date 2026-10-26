package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.network.NetworkBearerTokenProvider
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import java.util.Collections
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * INBOXORDER01_STORED_ROWS_READ_ELSEWHERE_TURN_READ_V1: a first inbox page
 * also reads Reddit's unread list (without marking it read) and names the
 * unread items by the inbox's own ids (a reply in a thread counts for the
 * thread); a list with more pages, or a failed read, says nothing.
 */
class InboxOrder01UnreadListTest {
    private val inboxPage = """
        {"kind":"Listing","data":{"after":null,"children":[
          {"kind":"t1","data":{"id":"c1","name":"t1_c1","author":"peer","dest":"CurrentUser","body":"hi","new":true,
           "created_utc":1000,"subject":"comment reply","was_comment":true,
           "context":"/r/test/comments/p1/_/c1/?context=3","link_title":"A post","subreddit":"test"}}]}}
    """.trimIndent()
    private val emptyListing = """{"kind":"Listing","data":{"after":null,"children":[]}}"""

    private fun unreadList(after: String?) = """
        {"kind":"Listing","data":{"after":${after?.let { "\"$it\"" } ?: "null"},"children":[
          {"kind":"t1","data":{"id":"c1","name":"t1_c1","author":"peer","dest":"CurrentUser","body":"hi","new":true,
           "created_utc":1000,"subject":"comment reply","was_comment":true,
           "context":"/r/test/comments/p1/_/c1/?context=3","link_title":"A post","subreddit":"test"}},
          {"kind":"t4","data":{"id":"m2","name":"t4_m2","author":"peer","dest":"CurrentUser","body":"later",
           "new":true,"created_utc":900,"subject":"re: Thread","first_message_name":"t4_root"}}]}}
    """.trimIndent()

    private val requests = Collections.synchronizedList(mutableListOf<String>())

    private fun source(unread: () -> Pair<Int, String>): RedditInboxNetworkDataSource {
        val callFactory = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val path = request.url.encodedPath
            val (code, body) = when {
                path.contains("/message/unread") -> {
                    requests += "unread mark=${request.url.queryParameter("mark")}"
                    unread()
                }
                path.contains("/message/sent") -> 200 to emptyListing
                else -> {
                    requests += "inbox after=${request.url.queryParameter("after")}"
                    200 to inboxPage
                }
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(code).message("x")
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
    fun aRefreshNamesEveryUnreadItemByItsInboxId() = runBlocking {
        val result = source { 200 to unreadList(after = null) }
            .page(cursor = null, limit = 100, currentUsername = "CurrentUser", balanceHistory = false)

        assertEquals(setOf("t1_c1", "t4_root"), (result as NetworkResult.Success).value.unreadItemIds)
        assertTrue(requests.toString(), "unread mark=false" in requests)
    }

    @Test
    fun anUnreadListWithMorePagesSaysNothing() = runBlocking {
        val result = source { 200 to unreadList(after = "t1_c1") }
            .page(cursor = null, limit = 100, currentUsername = "CurrentUser", balanceHistory = false)

        assertNull((result as NetworkResult.Success).value.unreadItemIds)
    }

    @Test
    fun aFailedUnreadReadKeepsThePage() = runBlocking {
        val result = source { 500 to "{}" }
            .page(cursor = null, limit = 100, currentUsername = "CurrentUser", balanceHistory = false)

        assertTrue(result.toString(), result is NetworkResult.Success)
        assertNull((result as NetworkResult.Success).value.unreadItemIds)
    }

    @Test
    fun aLaterPageDoesNotReadTheUnreadList() = runBlocking {
        source { 200 to unreadList(after = null) }
            .page(cursor = "t1_x", limit = 100, currentUsername = "CurrentUser")

        assertTrue(requests.toString(), requests.none { it.startsWith("unread") })
    }
}
