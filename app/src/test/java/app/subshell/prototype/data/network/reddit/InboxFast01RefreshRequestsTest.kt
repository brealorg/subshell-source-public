package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.network.NetworkBearerTokenProvider
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
 * INBOXFAST01: a refresh of a stored inbox reads only the first inbox and
 * sent pages (the history balancing is for the first load), and the two
 * requests run at the same time.
 */
class InboxFast01RefreshRequestsTest {
    // The sent page is newer than the inbox page and has more pages: the
    // first load pages sent history back to the inbox page's age.
    private val inboxPage = """
        {"kind":"Listing","data":{"after":"t4_i2","children":[
          {"kind":"t4","data":{"id":"root","name":"t4_root","author":"other_user","dest":"CurrentUser",
           "subject":"Old thread","body":"Old question","created_utc":1000}}]}}
    """.trimIndent()

    private fun sentPage(after: String?, createdUtc: Int) = """
        {"kind":"Listing","data":{"after":${after?.let { "\"$it\"" } ?: "null"},"children":[
          {"kind":"t4","data":{"id":"s$createdUtc","name":"t4_s$createdUtc","author":"CurrentUser","dest":"someone",
           "subject":"Thread $createdUtc","body":"Sent $createdUtc","created_utc":$createdUtc}}]}}
    """.trimIndent()

    private val inboxRequests = Collections.synchronizedList(mutableListOf<String?>())
    private val sentRequests = Collections.synchronizedList(mutableListOf<String?>())

    private fun source(beforeInboxAnswer: () -> Unit = {}): RedditInboxNetworkDataSource {
        val callFactory = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            // INBOXORDER01: a first page also reads the unread list; not part of this test.
            if (request.url.encodedPath.contains("/message/unread")) {
                return@addInterceptor Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK")
                    .body("""{"kind":"Listing","data":{"after":null,"children":[]}}""".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            val after = request.url.queryParameter("after")
            val body = if (request.url.encodedPath.contains("/message/sent")) {
                sentRequests += after
                if (after == null) sentPage(after = "t4_s2", createdUtc = 5000) else sentPage(after = null, createdUtc = 900)
            } else {
                inboxRequests += after
                beforeInboxAnswer()
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

    @Test
    fun aRefreshOfAStoredInboxReadsOnlyTheFirstInboxAndSentPages() = runBlocking {
        val result = source().page(cursor = null, limit = 100, currentUsername = "CurrentUser", balanceHistory = false)

        assertTrue(result.toString(), result is NetworkResult.Success)
        assertEquals(listOf<String?>(null), inboxRequests.toList())
        assertEquals(listOf<String?>(null), sentRequests.toList())
    }

    // INBOXFAST01_A_REFRESH_IS_COMPLETE_ONLY_SINCE_ITS_OLDER_EDGE_V1
    @Test
    fun theFirstPageSaysFromWhenItIsComplete() = runBlocking {
        val result = source().page(cursor = null, limit = 100, currentUsername = "CurrentUser", balanceHistory = false)

        // Inbox reaches back to 1000, sent only to 5000: complete since 5000.
        assertEquals(5000L, (result as NetworkResult.Success).value.completeSinceEpochSeconds)
    }

    @Test
    fun aListingReadToItsEndLimitsNothing() {
        val inboxToTheEnd = RedditInboxListingDto(
            RedditInboxListingDataDto(after = null, children = listOf(thing(created = 1000.0))),
        )
        val sentWithMore = RedditInboxListingDto(
            RedditInboxListingDataDto(after = "t4_x", children = listOf(thing(created = 700.0))),
        )
        assertEquals(700L, redditInboxCompleteSince(listOf(inboxToTheEnd), listOf(sentWithMore)))
        assertEquals(0L, redditInboxCompleteSince(listOf(inboxToTheEnd), listOf(inboxToTheEnd)))
        val sentWithoutTimes = RedditInboxListingDto(
            RedditInboxListingDataDto(after = "t4_x", children = listOf(thing(created = null))),
        )
        assertEquals(null, redditInboxCompleteSince(listOf(inboxToTheEnd), listOf(sentWithoutTimes)))
    }

    private fun thing(created: Double?) = RedditInboxThingDto(kind = "t4", data = RedditInboxDataDto(id = "x", createdUtc = created))

    @Test
    fun theFirstLoadStillBalancesTheHistory() = runBlocking {
        source().page(cursor = null, limit = 100, currentUsername = "CurrentUser")

        assertEquals(listOf(null, "t4_s2"), sentRequests.toList())
    }

    @Test
    fun theInboxAndSentRequestsRunAtTheSameTime() = runBlocking {
        val sentArrived = CountDownLatch(1)
        var sentWhileInboxWaited = false
        val source = source(beforeInboxAnswer = {
            // The inbox answer waits for the sent request; one after the
            // other, it would wait in vain.
            sentWhileInboxWaited = sentArrived.await(3, TimeUnit.SECONDS)
        })
        val sentWatcher = Thread {
            while (sentRequests.isEmpty()) Thread.sleep(5)
            sentArrived.countDown()
        }.apply { isDaemon = true; start() }

        source.page(cursor = null, limit = 100, currentUsername = "CurrentUser", balanceHistory = false)
        sentWatcher.join(1_000)

        assertTrue(sentWhileInboxWaited)
    }
}
