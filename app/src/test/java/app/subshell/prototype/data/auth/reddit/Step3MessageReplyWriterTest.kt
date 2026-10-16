package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.account.MessageReplyChannel
import app.subshell.prototype.data.account.MessageReplyFailureKind
import app.subshell.prototype.data.account.MessageReplyRequest
import app.subshell.prototype.data.auth.AuthAccountIdentity
import app.subshell.prototype.data.auth.AuthInboxReadCapability
import app.subshell.prototype.data.auth.AuthInboxReadCapabilityResult
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.OAuthProvider
import app.subshell.prototype.data.auth.OAuthProviderConfiguration
import app.subshell.prototype.data.auth.OAuthTokenSet
import app.subshell.prototype.data.auth.StoredOAuthSession
import app.subshell.prototype.data.auth.authenticatedAccountId
import app.subshell.prototype.data.network.NetworkEndpoint
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * STEP3 (messages02c, 02c1): a private-message reply asks the inbox
 * capability for the account that sends it, and sends only with a session of
 * that same account that holds the inbox grant — one POST to api/comment with
 * the bearer token and no cookie. A lost answer (timeout, broken connection,
 * an answer that is not Reddit's envelope) is "may have been sent", never a
 * retry; a host that cannot be reached is a retryable transport failure.
 */
class Step3MessageReplyWriterTest {
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
    private val requests = mutableListOf<Request>()
    private val bodies = mutableListOf<String>()
    private val asked = mutableListOf<String>()

    private fun session(
        scopes: Set<String> = RedditOAuth.liveInboxReadScopes,
        providerAccountId: String = "me",
    ) = StoredOAuthSession(
        provider = OAuthProvider.REDDIT,
        tokens = OAuthTokenSet("test-token", null, "bearer", scopes.joinToString(" "), Long.MAX_VALUE),
        identity = AuthAccountIdentity(providerAccountId, "test-user"),
        clientMode = config.clientMode,
        clientIdFingerprint = config.clientIdFingerprint,
    )

    private fun capability(answer: AuthSessionResult) = object : AuthInboxReadCapability {
        override suspend fun beginAuthorization(accountStableId: String): String = throw UnsupportedOperationException()
        override suspend fun hasScopeGrant(accountStableId: String): Boolean = true
        override fun hasPendingAuthorization(): Boolean = false
        override fun hasStoredCallback(): Boolean = false
        override fun cancelPendingAuthorization() = Unit
        override suspend fun completePendingAuthorization(): AuthInboxReadCapabilityResult = throw UnsupportedOperationException()
        override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult {
            asked += accountStableId
            return answer
        }
    }

    private fun server(fail: Throwable? = null, body: String = """{"json":{"errors":[]}}""") = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        requests += request
        bodies += Buffer().also { request.body?.writeTo(it) }.readUtf8()
        fail?.let { throw it }
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }.build()

    private fun send(
        answer: AuthSessionResult = AuthSessionResult.Authenticated(session()),
        factory: OkHttpClient = server(),
    ) = runBlocking {
        RedditMessageReplyWriter(
            callFactory = factory,
            endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
            userAgent = "subshell-test",
            capability = capability(answer),
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

    private fun failure(result: AuthenticatedRedditMessageReplyResult) = result as AuthenticatedRedditMessageReplyResult.Failed

    @Test
    fun aReplyIsOnePostWithTheBearerTokenAndNoCookie() {
        val result = send()

        assertTrue(result.toString(), result is AuthenticatedRedditMessageReplyResult.Accepted)
        assertEquals(listOf(account), asked)
        val request = requests.single()
        assertEquals("POST", request.method)
        assertEquals("/api/comment", request.url.encodedPath)
        assertEquals("Bearer test-token", request.header("Authorization"))
        assertNull(request.header("Cookie"))
        assertTrue(bodies.single(), "thing_id=t4_root" in bodies.single())
        assertTrue(bodies.single(), "text=Thanks%21" in bodies.single())
    }

    @Test
    fun aSessionWithoutTheInboxGrantOrOfAnotherAccountSendsNothing() {
        val withoutGrant = send(AuthSessionResult.Authenticated(session(scopes = RedditOAuth.liveInboxReadScopes - "privatemessages")))
        val otherAccount = send(AuthSessionResult.Authenticated(session(providerAccountId = "someone-else")))

        assertEquals(MessageReplyFailureKind.AUTHORIZATION_REQUIRED, failure(withoutGrant).kind)
        assertEquals(MessageReplyFailureKind.AUTHORIZATION_REQUIRED, failure(otherAccount).kind)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun aMissingAuthorizationSendsNothing() {
        val result = send(AuthSessionResult.ReauthenticationRequired)

        assertEquals(MessageReplyFailureKind.AUTHORIZATION_REQUIRED, failure(result).kind)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun aLostAnswerMayHaveBeenSentAndIsNeverRetried() {
        for (lost in listOf(SocketTimeoutException("timeout"), IOException("connection reset"))) {
            requests.clear()
            val result = failure(send(factory = server(fail = lost)))

            assertEquals(lost.toString(), MessageReplyFailureKind.OUTCOME_UNKNOWN, result.kind)
            assertFalse(result.retryable)
            assertEquals(1, requests.size)
        }
        requests.clear()
        val notTheEnvelope = failure(send(factory = server(body = "<html>busy</html>")))
        assertEquals(MessageReplyFailureKind.OUTCOME_UNKNOWN, notTheEnvelope.kind)
        assertEquals(1, requests.size)
    }

    @Test
    fun anUnreachableHostIsARetryableTransportFailure() {
        val result = failure(send(factory = server(fail = UnknownHostException("oauth.reddit.test"))))

        assertEquals(MessageReplyFailureKind.TRANSPORT, result.kind)
        assertTrue(result.retryable)
    }
}
