package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.auth.AuthAccountIdentity
import app.subshell.prototype.data.auth.AuthCommentWriteTokenSource
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.OAuthProvider
import app.subshell.prototype.data.auth.OAuthTokenSet
import app.subshell.prototype.data.auth.StoredOAuthSession
import app.subshell.prototype.data.auth.authenticatedAccountId
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.NetworkProtocolFailureKind
import app.subshell.prototype.data.network.NetworkTransportFailureKind
import java.net.UnknownHostException
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (error01a4): a comment Reddit did not take says why — an unreachable
 * host is a network failure, an answer that is not JSON a response failure,
 * Reddit's own refusal keeps its code, and a rate limit keeps its wait.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3WriteFailureDetailsTest {
    private val account = authenticatedAccountId(OAuthProvider.REDDIT, OAuthClientMode.REDREADER_COMPATIBILITY, "t2me")
    private val capability = object : AuthCommentWriteTokenSource {
        override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult =
            AuthSessionResult.Authenticated(
                StoredOAuthSession(
                    provider = OAuthProvider.REDDIT,
                    tokens = OAuthTokenSet("access", "refresh", "bearer", RedditOAuth.liveCommentWriteScopes.joinToString(" "), Long.MAX_VALUE),
                    identity = AuthAccountIdentity(providerAccountId = "t2me", username = "me"),
                    clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
                ),
            )
    }

    private fun answering(code: Int, type: String, body: String, retryAfter: String? = null) = OkHttpClient.Builder().addInterceptor(
        Interceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(code).message("answer")
                .apply { retryAfter?.let { header("Retry-After", it) } }
                .body(body.toResponseBody(type.toMediaType()))
                .build()
        },
    ).build()

    private fun failure(callFactory: okhttp3.Call.Factory) = runBlocking {
        ConfiguredRedditCommentSubmitter(
            capability = capability,
            callFactory = callFactory,
            endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
            userAgent = "subshell-test",
        ).submit(
            AuthenticatedCommentSubmitRequest(accountStableId = account, parentFullname = "t3_abc", text = "hello"),
        ) as AuthenticatedCommentSubmitResult.Failed
    }

    @Test
    fun anUnreachableHostIsANetworkFailure() {
        val offline = OkHttpClient.Builder().addInterceptor { throw UnknownHostException("oauth.reddit.test") }.build()

        val failed = failure(offline)

        assertEquals(AuthenticatedCommentSubmitFailureKind.TRANSPORT, failed.kind)
        assertEquals(NetworkTransportFailureKind.DNS, failed.failureDetails?.transportKind)
    }

    @Test
    fun anAnswerThatIsNotJsonIsAResponseFailure() {
        val failed = failure(answering(200, "text/html", "<html>blocked</html>"))

        assertEquals(NetworkProtocolFailureKind.UNEXPECTED_CONTENT_TYPE, failed.failureDetails?.protocolKind)
    }

    @Test
    fun redditsRefusalKeepsItsCode() {
        val failed = failure(answering(200, "application/json", """{"json":{"errors":[["THREAD_LOCKED","that thread is locked","parent"]]}}"""))

        assertEquals("THREAD_LOCKED", failed.failureDetails?.providerCode?.value)
    }

    @Test
    fun aRateLimitKeepsItsWait() {
        val failed = failure(answering(429, "application/json", "{}", retryAfter = "30"))

        assertEquals(AuthenticatedCommentSubmitFailureKind.RATE_LIMIT, failed.kind)
        assertEquals(429, failed.failureDetails?.httpStatusCode)
        assertEquals(30_000L, failed.failureDetails?.retryAfterMs)
    }
}
