package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.account.RedditModmailListMode
import app.subshell.prototype.data.auth.AuthAccountIdentity
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityResult
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModmailResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModmailUnreadCountsResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.OAuthProvider
import app.subshell.prototype.data.auth.OAuthTokenSet
import app.subshell.prototype.data.auth.StoredOAuthSession
import app.subshell.prototype.data.network.NetworkEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * STEP3 (modmail01a1, modmail05, modmail06): New Modmail is read only: every
 * request is a GET. A list asks which communities have mail, then the unread
 * counts, then the conversations of the chosen queue (Reddit's state names,
 * newest first); without mail communities nothing else is asked. A thread is
 * read without marking it read. A missing grant asks to authorize and sends
 * nothing; a 401 from Reddit asks to authorize too.
 */
class Step3ModmailLoaderTest {
    private data class Sent(val method: String, val path: String, val query: Map<String, String?>)

    private val sent = mutableListOf<Sent>()
    private var subreddits = SUBREDDITS
    private var conversationsStatus = 200

    private val recording = OkHttpClient.Builder().addInterceptor { chain ->
        val request: Request = chain.request()
        sent += Sent(
            method = request.method,
            path = request.url.encodedPath,
            query = request.url.queryParameterNames.associateWith { request.url.queryParameter(it) },
        )
        val path = request.url.encodedPath
        val (code, body) = when {
            path.endsWith("/subreddits") -> 200 to subreddits
            path.endsWith("/unread/count") -> 200 to """{"new":2,"inprogress":1,"archived":4}"""
            path == "/api/mod/conversations" -> conversationsStatus to "{}"
            else -> 200 to "{}"
        }
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
            .code(code).message(if (code == 200) "OK" else "Error")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }.build()

    private val session = StoredOAuthSession(
        provider = OAuthProvider.REDDIT,
        tokens = OAuthTokenSet("test-token", null, "bearer", "identity modmail", Long.MAX_VALUE),
        identity = AuthAccountIdentity("me", "test-user"),
        clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
    )

    private var authorization: AuthSessionResult = AuthSessionResult.Authenticated(session)

    private val capability = object : AuthCommentWriteCapability {
        override suspend fun beginAuthorization(accountStableId: String): String = throw UnsupportedOperationException()
        override fun hasPendingAuthorization(): Boolean = false
        override fun hasStoredCallback(): Boolean = false
        override fun cancelPendingAuthorization() = Unit
        override suspend fun completePendingAuthorization(): AuthCommentWriteCapabilityResult =
            throw UnsupportedOperationException()
        override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult = authorization
    }

    private val loader = RedditModmailLoader(
        capability = capability,
        callFactory = recording,
        endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
        userAgent = "subshell-test",
        ioDispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun everyQueueAsksForItsOwnRedditStateNewestFirst() = runBlocking {
        val expected = mapOf(
            RedditModmailListMode.ACTIVE to "all",
            RedditModmailListMode.NEW to "new",
            RedditModmailListMode.IN_PROGRESS to "inprogress",
            RedditModmailListMode.FILTERED to "filtered",
            RedditModmailListMode.HIGHLIGHTED to "highlighted",
            RedditModmailListMode.ARCHIVED to "archived",
            RedditModmailListMode.APPEALS to "appeals",
            RedditModmailListMode.JOIN_REQUESTS to "join_requests",
            RedditModmailListMode.MOD_DISCUSSIONS to "mod",
            RedditModmailListMode.NOTIFICATIONS to "notifications",
        )
        assertEquals(RedditModmailListMode.entries.toSet(), expected.keys)

        for ((mode, state) in expected) {
            sent.clear()
            loader.loadByMode("reddit:me", null, mode)

            assertEquals(
                listOf(
                    "/api/mod/conversations/subreddits",
                    "/api/mod/conversations/unread/count",
                    "/api/mod/conversations",
                ),
                sent.map { it.path },
            )
            assertEquals(state, sent.last().query["state"])
            assertEquals("recent", sent.last().query["sort"])
            assertTrue(sent.all { it.method == "GET" })
        }
    }

    @Test
    fun withoutMailCommunitiesNothingElseIsAsked() = runBlocking {
        subreddits = """{"subreddits":{}}"""

        val result = loader.loadByMode("reddit:me", null, RedditModmailListMode.ACTIVE)

        assertEquals(listOf("/api/mod/conversations/subreddits"), sent.map { it.path })
        val loaded = result as AuthenticatedRedditModmailResult.Loaded
        assertTrue(loaded.mailCommunities.isEmpty())
        assertTrue(loaded.conversations.isEmpty())
    }

    @Test
    fun aThreadIsReadWithoutMarkingItRead() = runBlocking {
        loader.loadThread("reddit:me", "abc123")

        assertEquals(1, sent.size)
        assertEquals("GET", sent.single().method)
        assertEquals("/api/mod/conversations/abc123", sent.single().path)
        assertEquals("false", sent.single().query["markRead"])
    }

    @Test
    fun aMissingGrantOrA401AsksToAuthorize() = runBlocking {
        authorization = AuthSessionResult.AuthorizationDenied("missing_incremental_scope")

        assertEquals(
            AuthenticatedRedditModmailResult.AuthorizationRequired,
            loader.loadByMode("reddit:me", null, RedditModmailListMode.ACTIVE),
        )
        assertEquals(emptyList<Sent>(), sent)

        authorization = AuthSessionResult.Authenticated(session)
        conversationsStatus = 401

        assertEquals(
            AuthenticatedRedditModmailResult.AuthorizationRequired,
            loader.loadByMode("reddit:me", null, RedditModmailListMode.ACTIVE),
        )
    }

    @Test
    fun unreadReadsAskForTheUnreadSortAndTheCounts() = runBlocking {
        loader.loadUnread("reddit:me")

        assertEquals("/api/mod/conversations", sent.single().path)
        assertEquals("all", sent.single().query["state"])
        assertEquals("unread", sent.single().query["sort"])
        assertEquals("100", sent.single().query["limit"])

        sent.clear()
        val counts = loader.loadUnreadCounts("reddit:me") as AuthenticatedRedditModmailUnreadCountsResult.Loaded

        assertEquals(listOf("/api/mod/conversations/unread/count"), sent.map { it.path })
        assertEquals(2, counts.counts.new)
        assertEquals(1, counts.counts.inProgress)
        assertEquals(4, counts.counts.archived)
        assertTrue(sent.all { it.method == "GET" })
    }

    private companion object {
        const val SUBREDDITS =
            """{"subreddits":{"t5_alpha":{"display_name":"AlphaMods","lastUpdated":"2026-08-27T00:00:00.000Z"}}}"""
    }
}
