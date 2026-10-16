package app.subshell.prototype.data.auth

import app.subshell.prototype.data.account.PostVoteRequest
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostVoteResult
import app.subshell.prototype.data.auth.reddit.ConfiguredRedditPostVoter
import app.subshell.prototype.data.network.NetworkEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * LOW1: a 403 on a write (vote/save/follow, modmail) is Reddit refusing, not a
 * missing grant (OCB-517/617); a storage or network session fault is not a
 * reason to send the user to authorize (OCB-497/506/508).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Low1AuthorizationMappingTest {
    private val session = StoredOAuthSession(
        provider = OAuthProvider.REDDIT,
        tokens = OAuthTokenSet("access", "refresh", "bearer", "vote", Long.MAX_VALUE),
        identity = AuthAccountIdentity(providerAccountId = "t2me", username = "me"),
        clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
    )

    @Test
    fun aVoteRefusedWith403IsAnHttpFailureNotAnAuthorizationLoop() = runBlocking {
        val result = voterAnswering(403)
            .vote(PostVoteRequest(accountStableId = "reddit:me", postFullname = "t3_abc", direction = 1))

        result as AuthenticatedPostVoteResult.Failed
        assertEquals(403, result.statusCode)
    }

    @Test
    fun aVoteAnswered401StillAsksToAuthorize() = runBlocking {
        val result = voterAnswering(401)
            .vote(PostVoteRequest(accountStableId = "reddit:me", postFullname = "t3_abc", direction = 1))

        assertEquals(AuthenticatedPostVoteResult.AuthorizationRequired, result)
    }

    @Test
    fun onlyAMissingGrantNeedsAuthorization() {
        assertTrue(AuthSessionResult.ReauthenticationRequired.needsAuthorization)
        assertFalse(AuthSessionResult.StorageFailure.needsAuthorization)
        assertFalse(AuthSessionResult.ClientConfigurationMismatch.needsAuthorization)
    }

    private fun voterAnswering(code: Int) = ConfiguredRedditPostVoter(
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
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(code).message("answer")
                .body("{}".toResponseBody("application/json".toMediaType()))
                .build()
        }.build(),
        endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
        userAgent = "subshell-test",
        ioDispatcher = Dispatchers.Unconfined,
    )
}
