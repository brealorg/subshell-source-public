package app.subshell.prototype.data.diagnostics

import app.subshell.prototype.data.network.NetworkBearerTokenProvider
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.data.network.reddit.RedditInboxTargetNetworkDataSource
import app.subshell.prototype.data.network.reddit.postThreadAnswer
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ISSUE281_THREAD_READS_IN_THE_REPORT_V1 (#281): an inbox post Reddit answered
 * without, a failed replies load and a find's coverage reach the report as
 * counts and tokens, with Reddit's rate-limit headers as last seen.
 */
class Issue281ReadDiagnosticsTest {
    private val recorded = mutableListOf<Pair<String, Map<String, Any?>>>()

    @After
    fun reset() {
        RedditReadDiagnostics.installForTest(null)
    }

    private fun recordInMemory() {
        RedditReadDiagnostics.installForTest { kind, fields -> recorded += kind to fields.toMap() }
    }

    @Test
    fun anInboxPostRedditAnsweredWithoutIsRecorded() = runBlocking {
        recordInMemory()
        // A comment where the post should be, as Reddit may answer.
        val listing = """{"kind":"Listing","data":{"children":[{"kind":"t1","data":{"id":"x","title":""}}]}}"""

        val result = RedditInboxTargetNetworkDataSource(client(postThreadAnswer(listing), remaining = null)).post("abc")

        assertNull((result as NetworkResult.Success).value)
        assertEquals(
            listOf(READ_EVENT_INBOX_TARGET_EMPTY to mapOf("children" to 1, "posts" to 0, "untitled" to 0)),
            recorded,
        )
    }

    @Test
    fun aFoundInboxPostRecordsNothing() = runBlocking {
        recordInMemory()
        val listing = """{"kind":"Listing","data":{"children":[{"kind":"t3","data":{"id":"abc","name":"t3_abc",
            "title":"A post","subreddit":"x","author":"me","created_utc":1.0}}]}}"""

        val result = RedditInboxTargetNetworkDataSource(client(postThreadAnswer(listing), remaining = null)).post("abc")

        assertEquals("t3_abc", (result as NetworkResult.Success).value?.id)
        assertTrue(recorded.isEmpty())
    }

    @Test
    fun rateLimitHeadersAreKeptAndALowQuotaIsRecordedOnce() = runBlocking {
        recordInMemory()
        val listing = """{"kind":"Listing","data":{"children":[]}}"""

        RedditInboxTargetNetworkDataSource(client(postThreadAnswer(listing), remaining = "42.0")).post("abc")
        RedditInboxTargetNetworkDataSource(client(postThreadAnswer(listing), remaining = "3.0")).post("abc")
        RedditInboxTargetNetworkDataSource(client(postThreadAnswer(listing), remaining = "2.0")).post("abc")
        RedditInboxTargetNetworkDataSource(client(postThreadAnswer(listing), remaining = "57.0")).post("abc")

        val snapshot = RedditReadDiagnostics.rateLimitSnapshot()!!
        assertEquals(57, snapshot.remaining)
        assertEquals(12, snapshot.used)
        assertEquals(300, snapshot.resetSeconds)
        assertEquals(2, snapshot.lowestRemaining)
        assertFalse(RedditReadDiagnostics.rateLimitIsLow())
        // Once a minute at most, so a stretch near the limit is one line.
        assertEquals(1, recorded.count { (kind, _) -> kind == READ_EVENT_RATE_LIMIT_LOW })
        assertEquals(3, recorded.single { (kind, _) -> kind == READ_EVENT_RATE_LIMIT_LOW }.second["remaining"])
    }

    @Test
    fun theStoreKeepsCountsAndTokensOnlyAndTheNewestLines() {
        val directory = Files.createTempDirectory("reads").toFile()
        val store = RedditReadEventStore(directory)

        store.record(
            READ_EVENT_MORE_REPLIES_FAILED,
            listOf(
                "branch" to "rejected_by_reddit",
                "code" to "ratelimit",
                "requested" to 25,
                "parent_mod" to true,
                "returned" to null,
                // Never kept: anything that is not a number, boolean or short token.
                "text" to "Hello there",
                "url" to "https://reddit.com/r/x",
            ),
            atEpochMs = 1_000L,
        )
        store.record("not_a_kind", listOf("n" to 1), atEpochMs = 2_000L)

        val event = store.read().single()
        assertEquals(READ_EVENT_MORE_REPLIES_FAILED, event.kind)
        assertEquals(
            listOf("branch" to "rejected_by_reddit", "code" to "ratelimit", "requested" to "25", "parent_mod" to "true"),
            event.fields,
        )

        repeat(READ_EVENT_LIMIT + 5) { index ->
            store.record(READ_EVENT_THREAD_FIND, listOf("loads" to index), atEpochMs = 10_000L + index)
        }
        val kept = store.read()
        assertEquals(READ_EVENT_LIMIT, kept.size)
        assertEquals("${READ_EVENT_LIMIT + 4}", kept.last().fields.single().second)

        store.clear()
        assertTrue(store.read().isEmpty())
        directory.deleteRecursively()
    }

    @Test
    fun theReportHasAReadsSection() {
        val text = buildString {
            appendRedditReads(
                events = listOf(
                    RedditReadEvent(
                        0L,
                        READ_EVENT_THREAD_FIND,
                        listOf("loaded" to "120", "total" to "1584", "stop" to "cap"),
                    ),
                ),
                rateLimit = RedditRateLimitSnapshot(
                    remaining = 80,
                    used = 20,
                    resetSeconds = 120,
                    atEpochMs = 0L,
                    lowestRemaining = 4,
                    lowestAtEpochMs = 0L,
                ),
            )
        }

        assertTrue(text, text.contains("[reddit_reads]"))
        assertTrue(text, text.contains("rate_limit remaining=80 used=20 reset_s=120"))
        assertTrue(text, text.contains("lowest=4"))
        assertTrue(text, text.contains("thread_find loaded=120 total=1584 stop=cap"))
        assertEquals(
            "\n[reddit_reads]\nrate_limit=not_seen\nevents=none\n",
            buildString { appendRedditReads(emptyList(), null) },
        )
        assertEquals("", buildString { appendRedditReads(null, null) })
    }

    private fun client(body: String, remaining: String?) = SubshellHttpClient(
        callFactory = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .apply {
                    if (remaining != null) {
                        header("X-Ratelimit-Remaining", remaining)
                        header("X-Ratelimit-Used", "12")
                        header("X-Ratelimit-Reset", "300")
                    }
                }
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }.build(),
        endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
        json = SubshellNetworkJson.providerJson,
        userAgent = "subshell-test",
        bearerTokenProvider = NetworkBearerTokenProvider { "token" },
    )
}
