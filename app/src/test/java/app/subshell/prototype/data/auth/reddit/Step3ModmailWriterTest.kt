package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.account.RedditModmailTriageAction
import app.subshell.prototype.data.auth.AuthAccountIdentity
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityResult
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModmailFailureKind
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.OAuthProvider
import app.subshell.prototype.data.auth.OAuthTokenSet
import app.subshell.prototype.data.auth.StoredOAuthSession
import app.subshell.prototype.data.network.NetworkEndpoint
import java.net.URLDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * STEP3 (modmail02, modmail04a1, modmail05, modmail06): each Modmail change
 * is exactly one request: a reply or internal note (an internal note is never
 * sent author-hidden), archive/unarchive as an empty form POST, highlight as
 * POST and unhighlight as DELETE, a mute with its hours, read/unread by
 * conversation id. An empty or too long reply sends nothing; an answer that
 * leaves the outcome unknown is never sent again.
 */
class Step3ModmailWriterTest {
    private data class Sent(val method: String, val path: String, val contentType: String?, val form: Map<String, String>)

    private val sent = mutableListOf<Sent>()
    private var status = 200

    private val recording = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        val raw = Buffer().also { buffer -> request.body?.writeTo(buffer) }.readUtf8()
        val form = raw.split('&').filter(String::isNotBlank).associate { pair ->
            val (key, value) = pair.split('=', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(value, "UTF-8")
        }
        sent += Sent(request.method, request.url.encodedPath, request.body?.contentType()?.toString(), form)
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
            .code(status).message(if (status == 200) "OK" else "Error")
            .body("{}".toResponseBody("application/json".toMediaType()))
            .build()
    }.build()

    private val session = StoredOAuthSession(
        provider = OAuthProvider.REDDIT,
        tokens = OAuthTokenSet("test-token", null, "bearer", "identity modmail", Long.MAX_VALUE),
        identity = AuthAccountIdentity("me", "test-user"),
        clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
    )

    private val capability = object : AuthCommentWriteCapability {
        override suspend fun beginAuthorization(accountStableId: String): String = throw UnsupportedOperationException()
        override fun hasPendingAuthorization(): Boolean = false
        override fun hasStoredCallback(): Boolean = false
        override fun cancelPendingAuthorization() = Unit
        override suspend fun completePendingAuthorization(): AuthCommentWriteCapabilityResult =
            throw UnsupportedOperationException()
        override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult =
            AuthSessionResult.Authenticated(session)
    }

    private val writer = ConfiguredRedditModmailWriter(
        capability = capability,
        callFactory = recording,
        endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
        userAgent = "subshell-test",
        ioDispatcher = Dispatchers.Unconfined,
    )

    private fun send(body: String, authorHidden: Boolean, internal: Boolean) = runBlocking {
        writer.send(AuthenticatedRedditModmailSendRequest("reddit:me", "abc123", body, internal, authorHidden))
    }

    @Test
    fun aReplyIsOnePostAndAnInternalNoteIsNeverAuthorHidden() {
        assertEquals(AuthenticatedRedditModmailSendResult.Sent, send(" Thanks! ", authorHidden = true, internal = false))
        assertEquals(1, sent.size)
        assertEquals("POST", sent.single().method)
        assertEquals("/api/mod/conversations/abc123", sent.single().path)
        assertEquals(mapOf("body" to "Thanks!", "isAuthorHidden" to "true", "isInternal" to "false"), sent.single().form)

        sent.clear()
        send("Note", authorHidden = true, internal = true)
        assertEquals(mapOf("body" to "Note", "isAuthorHidden" to "false", "isInternal" to "true"), sent.single().form)

        sent.clear()
        val blank = send("   ", authorHidden = false, internal = false)
        val tooLong = send("x".repeat(10_001), authorHidden = false, internal = false)
        assertEquals(AuthenticatedRedditModmailFailureKind.PROTOCOL, (blank as AuthenticatedRedditModmailSendResult.Failed).kind)
        assertEquals(AuthenticatedRedditModmailFailureKind.PROTOCOL, (tooLong as AuthenticatedRedditModmailSendResult.Failed).kind)
        assertEquals(emptyList<Sent>(), sent)
    }

    @Test
    fun archiveAndUnarchiveAreEmptyFormPosts() = runBlocking {
        writer.setArchived(AuthenticatedRedditModmailArchiveRequest("reddit:me", "abc123", archived = true))
        writer.setArchived(AuthenticatedRedditModmailArchiveRequest("reddit:me", "abc123", archived = false))

        assertEquals(
            listOf("/api/mod/conversations/abc123/archive", "/api/mod/conversations/abc123/unarchive"),
            sent.map { it.path },
        )
        assertTrue(sent.all { it.method == "POST" && it.form.isEmpty() })
        assertTrue(sent.all { it.contentType.orEmpty().startsWith("application/x-www-form-urlencoded") })
    }

    @Test
    fun triageUsesRedditsVerbsAndMuteSendsItsHours() = runBlocking {
        fun triage(action: RedditModmailTriageAction) = runBlocking {
            writer.triage(AuthenticatedRedditModmailTriageRequest("reddit:me", "abc123", action))
        }

        triage(RedditModmailTriageAction.HIGHLIGHT)
        triage(RedditModmailTriageAction.UNHIGHLIGHT)
        triage(RedditModmailTriageAction.MUTE_7_DAYS)
        triage(RedditModmailTriageAction.UNMUTE)

        assertEquals(
            listOf(
                "POST /api/mod/conversations/abc123/highlight",
                "DELETE /api/mod/conversations/abc123/highlight",
                "POST /api/mod/conversations/abc123/mute",
                "POST /api/mod/conversations/abc123/unmute",
            ),
            sent.map { "${it.method} ${it.path}" },
        )
        assertEquals(mapOf("num_hours" to "168"), sent[2].form)
    }

    @Test
    fun readAndUnreadNameTheConversation() = runBlocking {
        writer.setReadState(AuthenticatedRedditModmailReadStateRequest("reddit:me", "abc123", unread = false))
        writer.setReadState(AuthenticatedRedditModmailReadStateRequest("reddit:me", "abc123", unread = true))

        assertEquals(
            listOf("POST /api/mod/conversations/read", "POST /api/mod/conversations/unread"),
            sent.map { "${it.method} ${it.path}" },
        )
        assertTrue(sent.all { it.form == mapOf("conversationIds" to "abc123") })
    }

    @Test
    fun anUnknownOutcomeIsNeverSentAgain() = runBlocking {
        status = 502

        val reply = send("Thanks", authorHidden = false, internal = false) as AuthenticatedRedditModmailSendResult.Failed
        assertTrue(reply.outcomeUnknown)
        assertEquals(1, sent.size)

        sent.clear()
        val triage = writer.triage(
            AuthenticatedRedditModmailTriageRequest("reddit:me", "abc123", RedditModmailTriageAction.HIGHLIGHT),
        ) as AuthenticatedRedditModmailTriageResult.Failed
        assertTrue(triage.outcomeUnknown)
        assertEquals(1, sent.size)
    }
}
