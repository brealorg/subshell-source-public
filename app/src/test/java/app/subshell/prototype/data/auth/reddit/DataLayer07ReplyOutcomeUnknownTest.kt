package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.account.MessageReplyChannel
import app.subshell.prototype.data.account.MessageReplyFailureKind
import app.subshell.prototype.data.account.MessageReplyRequest
import app.subshell.prototype.data.auth.AuthAccountIdentity
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityResult
import app.subshell.prototype.data.auth.AuthInboxReadCapability
import app.subshell.prototype.data.auth.AuthInboxReadCapabilityResult
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.OAuthProvider
import app.subshell.prototype.data.auth.OAuthProviderConfiguration
import app.subshell.prototype.data.auth.OAuthTokenSet
import app.subshell.prototype.data.auth.StoredOAuthSession
import app.subshell.prototype.data.auth.authenticatedAccountId
import app.subshell.prototype.data.network.NetworkEndpoint
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DATALAYER07: a reply answered with 408/421/425/5xx may have been stored
 * by Reddit. It is outcome unknown (read back, never resend), not a plain
 * HTTP failure the user can send again and so post twice.
 */
class DataLayer07ReplyOutcomeUnknownTest {
    private val config = OAuthProviderConfiguration(
        provider = OAuthProvider.REDDIT,
        authorizationEndpoint = "https://example.test/authorize".toHttpUrl(),
        tokenEndpoint = "https://example.test/token".toHttpUrl(),
        revokeEndpoint = "https://example.test/revoke".toHttpUrl(),
        clientId = "test-client",
        redirectUri = "test://callback",
        scopes = setOf("identity"),
        permanentAccess = true,
    )
    private val account = authenticatedAccountId(OAuthProvider.REDDIT, config.clientMode, "me")

    private val session = StoredOAuthSession(
        provider = OAuthProvider.REDDIT,
        tokens = OAuthTokenSet(
            "test-token",
            null,
            "bearer",
            (RedditOAuth.liveInboxReadScopes + RedditOAuth.liveModmailReadScopes + "modmail").joinToString(" "),
            Long.MAX_VALUE,
        ),
        identity = AuthAccountIdentity("me", "test-user"),
        clientMode = config.clientMode,
        clientIdFingerprint = config.clientIdFingerprint,
    )

    private fun answering(code: Int): Call.Factory = OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
            .code(code).message(if (code == 200) "OK" else "Error")
            .body("""{"json":{"errors":[]}}""".toResponseBody("application/json".toMediaType()))
            .build()
    }.build()

    private fun sendPrivateMessageReply(code: Int) = runBlocking {
        RedditMessageReplyWriter(
            callFactory = answering(code),
            endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
            userAgent = "subshell-test",
            capability = InboxCapability(session),
        ).send(
            MessageReplyRequest(
                accountStableId = account,
                inboxItemId = "t4_root",
                parentFullname = "t4_root",
                channel = MessageReplyChannel.PRIVATE_MESSAGE_THREAD,
                text = "Thanks!",
            ),
        )
    }

    private fun sendModmailReply(code: Int) = runBlocking {
        ConfiguredRedditModmailWriter(
            capability = WriteCapability(session),
            callFactory = answering(code),
            endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
            userAgent = "subshell-test",
        ).send(
            AuthenticatedRedditModmailSendRequest(
                accountStableId = account,
                conversationId = "abc123",
                body = "Thanks!",
                internal = false,
                authorHidden = false,
            ),
        )
    }

    @Test
    fun aPrivateMessageReplyAnsweredWith502IsOutcomeUnknownAndNotRetryable() {
        val result = sendPrivateMessageReply(502) as AuthenticatedRedditMessageReplyResult.Failed

        assertEquals(MessageReplyFailureKind.OUTCOME_UNKNOWN, result.kind)
        assertFalse(result.retryable)
    }

    @Test
    fun aPrivateMessageReplyAnsweredWith400IsStillAPlainHttpFailure() {
        val result = sendPrivateMessageReply(400) as AuthenticatedRedditMessageReplyResult.Failed

        assertEquals(MessageReplyFailureKind.HTTP, result.kind)
    }

    @Test
    fun aModmailReplyAnsweredWith502IsOutcomeUnknown() {
        val result = sendModmailReply(502) as AuthenticatedRedditModmailSendResult.Failed

        assertTrue(result.outcomeUnknown)
        assertFalse(result.retryable)
    }

    @Test
    fun aModmailReplyAnsweredWith400IsNotOutcomeUnknown() {
        val result = sendModmailReply(400) as AuthenticatedRedditModmailSendResult.Failed

        assertFalse(result.outcomeUnknown)
    }
}

private class InboxCapability(private val session: StoredOAuthSession) : AuthInboxReadCapability {
    override suspend fun beginAuthorization(accountStableId: String): String = throw UnsupportedOperationException()
    override suspend fun hasScopeGrant(accountStableId: String): Boolean = true
    override fun hasPendingAuthorization(): Boolean = false
    override fun hasStoredCallback(): Boolean = false
    override fun cancelPendingAuthorization() = Unit
    override suspend fun completePendingAuthorization(): AuthInboxReadCapabilityResult =
        throw UnsupportedOperationException()
    override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult =
        AuthSessionResult.Authenticated(session)
}

private class WriteCapability(private val session: StoredOAuthSession) : AuthCommentWriteCapability {
    override suspend fun beginAuthorization(accountStableId: String): String = throw UnsupportedOperationException()
    override fun hasPendingAuthorization(): Boolean = false
    override fun hasStoredCallback(): Boolean = false
    override fun cancelPendingAuthorization() = Unit
    override suspend fun completePendingAuthorization(): AuthCommentWriteCapabilityResult =
        throw UnsupportedOperationException()
    override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult =
        AuthSessionResult.Authenticated(session)
}
