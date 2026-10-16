package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.account.RedditModeratorAction
import app.subshell.prototype.data.account.RedditModeratorActionFailureKind
import app.subshell.prototype.data.account.RedditModeratorActionRequest
import app.subshell.prototype.data.auth.AuthAccountIdentity
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityResult
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.OAuthProvider
import app.subshell.prototype.data.auth.OAuthTokenSet
import app.subshell.prototype.data.auth.StoredOAuthSession
import app.subshell.prototype.data.auth.authenticatedAccountId
import app.subshell.prototype.data.network.NetworkEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * STEP3 (moderation02a2, a3): ignoring reports and locking content go out
 * only for the account that is still active once authorization is done; an
 * account switch while the grant is checked sends nothing.
 */
class Step3ModerationWriterAccountRecheckTest {
    private val account = authenticatedAccountId(OAuthProvider.REDDIT, OAuthClientMode.REDREADER_COMPATIBILITY, "me")
    private val session = StoredOAuthSession(
        provider = OAuthProvider.REDDIT,
        tokens = OAuthTokenSet("access", "refresh", "bearer", "identity read modposts", Long.MAX_VALUE),
        identity = AuthAccountIdentity(providerAccountId = "t2me", username = "me"),
    )
    private var active: String? = account
    private val requests = mutableListOf<String>()

    private inner class SwitchingCapability(private val switchTo: String?) : AuthCommentWriteCapability {
        override suspend fun beginAuthorization(accountStableId: String) = "https://example.test/authorize"
        override fun hasPendingAuthorization() = false
        override fun hasStoredCallback() = false
        override fun cancelPendingAuthorization() = Unit
        override suspend fun completePendingAuthorization(): AuthCommentWriteCapabilityResult = error("unused")
        override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult {
            switchTo?.let { active = it }
            return AuthSessionResult.Authenticated(session)
        }
    }

    private val client = OkHttpClient.Builder().addInterceptor(
        Interceptor { chain ->
            requests += chain.request().url.encodedPath
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK")
                .body("{}".toResponseBody("application/json".toMediaType()))
                .build()
        },
    ).build()

    private fun writer(switchTo: String?) = ConfiguredRedditModerationWriter(
        capability = SwitchingCapability(switchTo),
        callFactory = client,
        endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
        userAgent = "subshell-test",
        ioDispatcher = Dispatchers.Unconfined,
        activeAccountStableId = { active },
    )

    private fun request(action: RedditModeratorAction) = RedditModeratorActionRequest(
        accountStableId = account,
        communityName = "kotlin",
        thingFullname = "t3_abc",
        action = action,
    )

    private fun failureKind(action: RedditModeratorAction, switchTo: String?): RedditModeratorActionFailureKind? =
        runBlocking {
            when (val result = writer(switchTo).mutate(request(action))) {
                is AuthenticatedRedditModerationActionResult.Failed -> result.kind
                else -> null
            }
        }

    @Test
    fun ignoringReportsAfterAnAccountSwitchSendsNothing() {
        assertEquals(RedditModeratorActionFailureKind.ACCOUNT_CHANGED, failureKind(RedditModeratorAction.IGNORE_REPORTS, "reddit:other"))
        assertEquals(emptyList<String>(), requests)
    }

    @Test
    fun lockingAfterAnAccountSwitchSendsNothing() {
        assertEquals(RedditModeratorActionFailureKind.ACCOUNT_CHANGED, failureKind(RedditModeratorAction.LOCK, "reddit:other"))
        assertEquals(emptyList<String>(), requests)
    }

    @Test
    fun withTheSameAccountBothGoOutOnce() {
        assertEquals(null, failureKind(RedditModeratorAction.IGNORE_REPORTS, switchTo = null))
        assertEquals(null, failureKind(RedditModeratorAction.LOCK, switchTo = null))
        assertEquals(2, requests.size)
    }
}
