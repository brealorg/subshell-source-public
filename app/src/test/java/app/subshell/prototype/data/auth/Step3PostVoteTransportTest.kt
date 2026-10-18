package app.subshell.prototype.data.auth

import app.subshell.prototype.data.account.PostVoteFailureKind
import app.subshell.prototype.data.account.PostVoteRequest
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostVoteResult
import app.subshell.prototype.data.auth.reddit.ConfiguredRedditPostVoter
import app.subshell.prototype.data.network.NetworkEndpoint
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (vote01): a vote whose connection fails is sent once and reported as
 * a transport failure the reader can retry (a vote is an absolute direction,
 * so sending it again is safe).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3PostVoteTransportTest {
    private val session = StoredOAuthSession(
        provider = OAuthProvider.REDDIT,
        tokens = OAuthTokenSet("access", "refresh", "bearer", "vote", Long.MAX_VALUE),
        identity = AuthAccountIdentity(providerAccountId = "t2me", username = "me"),
        clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
    )

    @Test
    fun aLostConnectionIsSentOnceAndReportedAsARetryableTransportFailure() = runBlocking {
        var attempts = 0
        val voter = ConfiguredRedditPostVoter(
            capability = object : AuthCommentWriteCapability {
                override suspend fun beginAuthorization(accountStableId: String): String = error("unused")
                override fun hasPendingAuthorization() = false
                override fun hasStoredCallback() = false
                override fun cancelPendingAuthorization() = Unit
                override suspend fun completePendingAuthorization(): AuthCommentWriteCapabilityResult = error("unused")
                override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult =
                    AuthSessionResult.Authenticated(session)
            },
            callFactory = OkHttpClient.Builder().addInterceptor {
                attempts += 1
                throw IOException("connection reset")
            }.build(),
            endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
            userAgent = "subshell-test",
            ioDispatcher = Dispatchers.Unconfined,
        )

        val result = voter.vote(PostVoteRequest(accountStableId = "reddit:me", postFullname = "t3_abc", direction = 1))

        result as AuthenticatedPostVoteResult.Failed
        assertEquals(PostVoteFailureKind.TRANSPORT, result.kind)
        assertEquals(true, result.retryable)
        assertEquals(1, attempts)
    }

    // DIAG04_REDDIT_ERROR_CODE_ON_400_V1: a refused vote keeps Reddit's code, not its message.
    @Test
    fun aRefusedVoteKeepsRedditsErrorCode() = runBlocking {
        val voter = ConfiguredRedditPostVoter(
            capability = object : AuthCommentWriteCapability {
                override suspend fun beginAuthorization(accountStableId: String): String = error("unused")
                override fun hasPendingAuthorization() = false
                override fun hasStoredCallback() = false
                override fun cancelPendingAuthorization() = Unit
                override suspend fun completePendingAuthorization(): AuthCommentWriteCapabilityResult = error("unused")
                override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult =
                    AuthSessionResult.Authenticated(session)
            },
            callFactory = OkHttpClient.Builder().addInterceptor { chain ->
                okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(400).message("Bad Request")
                    .body(
                        okhttp3.ResponseBody.Companion.run {
                            """{"json": {"errors": [["TOO_OLD", "that's a piece of history now", "id"]]}}"""
                                .toResponseBody(okhttp3.MediaType.Companion.run { "application/json".toMediaType() })
                        },
                    )
                    .build()
            }.build(),
            endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
            userAgent = "subshell-test",
            ioDispatcher = Dispatchers.Unconfined,
        )

        val result = voter.vote(PostVoteRequest(accountStableId = "reddit:me", postFullname = "t3_abc", direction = 1))

        result as AuthenticatedPostVoteResult.Failed
        assertEquals(400, result.failureDetails?.httpStatusCode)
        assertEquals("too_old", result.failureDetails?.providerCode?.value)
        assertEquals(null, result.failureDetails?.providerMessage)
    }
}
