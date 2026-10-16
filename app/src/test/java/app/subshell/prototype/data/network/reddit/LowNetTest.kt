package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.account.RedditModeratorQueueKind
import app.subshell.prototype.data.network.FormPostRequest
import app.subshell.prototype.data.network.GoogleTrendsRssNetworkDataSource
import app.subshell.prototype.data.network.NetworkBearerTokenProvider
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NET-A (low data/network findings): status-only calls run on the client's IO
 * dispatcher (OCB-539), the trends read stops at its limit (OCB-538), earlier
 * avatar chunks survive a later failure (OCB-547), a sent-only thread to a
 * community names r/name (OCB-549), a Giphy-only queue comment reads "GIF
 * comment" (OCB-550).
 */
class LowNetTest {
    @Test
    fun statusOnlyCallsReadTheTokenAndHandleA401OnTheIoDispatcher() = runBlocking {
        val threads = mutableListOf<String>()
        val io = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "net-io") }
        try {
            val client = SubshellHttpClient(
                callFactory = OkHttpClient.Builder().addInterceptor(answering { 401 to "{}" }).build(),
                endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
                json = SubshellNetworkJson.providerJson,
                userAgent = "subshell-test",
                bearerTokenProvider = object : NetworkBearerTokenProvider {
                    override fun bearerToken(): String {
                        threads += "token:" + Thread.currentThread().name.substringBefore(" @")
                        return "token"
                    }

                    override fun onUnauthorized() {
                        threads += "401:" + Thread.currentThread().name.substringBefore(" @")
                    }
                },
                ioDispatcher = io.asCoroutineDispatcher(),
            )

            val result = client.postFormStatus(
                FormPostRequest(pathSegments = listOf("api", "del"), fields = listOf("id" to "t3_x")),
            )

            assertTrue(result is NetworkResult.HttpFailure)
            assertEquals(listOf("token:net-io", "401:net-io"), threads)
        } finally {
            io.shutdown()
        }
    }

    @Test
    fun aTrendsBodyOfUnknownLengthIsNotReadPastTheLimit() = runBlocking {
        val delivered = AtomicLong()
        val huge = Buffer().write(ByteArray(4 * 1024 * 1024) { 'a'.code.toByte() })
        val unknownLength = object : ResponseBody() {
            private val counted = object : ForwardingSource(huge) {
                override fun read(sink: Buffer, byteCount: Long): Long =
                    super.read(sink, byteCount).also { if (it > 0) delivered.addAndGet(it) }
            }.buffer()

            override fun contentType(): MediaType = "application/rss+xml".toMediaType()
            override fun contentLength(): Long = -1L
            override fun source(): BufferedSource = counted
        }
        val factory = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(unknownLength)
                .build()
        }).build()

        val items = GoogleTrendsRssNetworkDataSource.load(factory)

        assertTrue(items.isEmpty())
        assertTrue("read ${delivered.get()} bytes", delivered.get() < 1024L * 1024L)
    }

    @Test
    fun aFailedLaterAvatarChunkKeepsTheEarlierChunksAvatars() = runBlocking {
        var calls = 0
        val client = SubshellHttpClient(
            callFactory = OkHttpClient.Builder().addInterceptor(answering { request ->
                calls += 1
                if (calls == 1) {
                    val ids = request.url.queryParameter("ids").orEmpty().split(",")
                    200 to ids.joinToString(",", "{", "}") { id ->
                        "\"$id\": {\"name\": \"u_$id\", \"profile_img\": \"https://i.redd.it/$id.png\"}"
                    }
                } else {
                    500 to "{}"
                }
            }).build(),
            endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
            json = SubshellNetworkJson.providerJson,
            userAgent = "subshell-test",
        )
        val ids = (1..150).map { index -> "t2_id$index" }

        val result = userDataByAccountIds(client, ids)

        assertTrue(result is NetworkResult.Success)
        val value = (result as NetworkResult.Success).value
        assertEquals(100, value.size)
        assertEquals("https://i.redd.it/t2_id1.png", value["t2_id1"])
        assertTrue("the failed chunk's ids are asked again later", "t2_id150" !in value)
    }

    @Test
    fun aSentOnlyThreadToACommunityNamesTheCommunity() {
        val sent = RedditInboxListingDto(
            data = RedditInboxListingDataDto(
                children = listOf(
                    RedditInboxThingDto(
                        kind = "t4",
                        data = RedditInboxDataDto(
                            name = "t4_mine",
                            author = "currentuser",
                            dest = "#foo",
                            subject = "Question for the mods",
                            body = "Hello",
                            createdUtc = 1_000.0,
                        ),
                    ),
                ),
            ),
        )

        val page = mergeInboxAndSentListings(RedditInboxListingDto(), sent).toNetworkPage(
            nowEpochSeconds = 1_200L,
            currentUsername = "CurrentUser",
        )

        assertEquals("r/foo", page.items.single { it.id == "t4_mine" }.sender)
    }

    @Test
    fun aGiphyOnlyQueueCommentIsSummarisedAsAGif() {
        assertEquals(
            "GIF comment",
            moderationQueueSummary("![gif](giphy|abc123)", RedditModeratorQueueKind.COMMENT),
        )
        assertEquals(
            "nice · GIF",
            moderationQueueSummary("nice ![gif](giphy|abc123|downsized)", RedditModeratorQueueKind.COMMENT),
        )
        assertEquals(
            "Image comment",
            moderationQueueSummary("![img](emote|t5_x|1)", RedditModeratorQueueKind.COMMENT),
        )
    }

    private fun answering(answer: (okhttp3.Request) -> Pair<Int, String>) = Interceptor { chain ->
        val (code, body) = answer(chain.request())
        Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("test")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }
}
