package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.account.CommunityMembershipAction
import app.subshell.prototype.data.account.CommunityMembershipFailureKind
import app.subshell.prototype.data.account.CommunityMembershipRequest
import app.subshell.prototype.data.auth.AuthAccountIdentity
import app.subshell.prototype.data.auth.AuthCustomFeedWriteCapability
import app.subshell.prototype.data.auth.AuthCustomFeedWriteCapabilityResult
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.OAuthProvider
import app.subshell.prototype.data.auth.OAuthTokenSet
import app.subshell.prototype.data.auth.StoredOAuthSession
import app.subshell.prototype.data.auth.authenticatedAccountId
import app.subshell.prototype.data.network.NetworkEndpoint
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * DATALAYER09: a join/leave answered with 408/421/425/5xx may have been
 * applied. It is outcome unknown so the #43
 * reconciliation confirms it against Reddit's list, instead of a definite
 * failure that reverts the button.
 */
class DataLayer09MembershipOutcomeTest {
    private val account =
        authenticatedAccountId(OAuthProvider.REDDIT, OAuthClientMode.REDREADER_COMPATIBILITY, "me")

    private val session = StoredOAuthSession(
        provider = OAuthProvider.REDDIT,
        tokens = OAuthTokenSet("test-token", null, "bearer", "identity subscribe mysubreddits", Long.MAX_VALUE),
        identity = AuthAccountIdentity("me", "test-user"),
        clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
    )

    private fun join(code: Int, body: String) = runBlocking {
        val callFactory = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(code).message(if (code == 200) "OK" else "Error")
                .body(body.toResponseBody("text/html".toMediaType()))
                .build()
        }.build()
        ConfiguredRedditCommunityMembershipWriter(
            capability = Capability(session),
            callFactory = callFactory,
            endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
            userAgent = "subshell-test",
        ).update(CommunityMembershipRequest(account, "subshell", CommunityMembershipAction.JOIN))
    }

    private fun kindOf(result: AuthenticatedCommunityMembershipResult): CommunityMembershipFailureKind =
        (result as AuthenticatedCommunityMembershipResult.Failed).kind

    @Test
    fun aJoinAnsweredWith502IsOutcomeUnknown() {
        assertEquals(CommunityMembershipFailureKind.OUTCOME_UNKNOWN, kindOf(join(502, "{}")))
    }

    @Test
    fun aJoinAnsweredWith400IsStillADefiniteHttpFailure() {
        assertEquals(CommunityMembershipFailureKind.HTTP, kindOf(join(400, "{}")))
    }
}

private class Capability(private val session: StoredOAuthSession) : AuthCustomFeedWriteCapability {
    override suspend fun beginAuthorization(accountStableId: String): String = throw UnsupportedOperationException()
    override suspend fun hasScopeGrant(accountStableId: String): Boolean = true
    override suspend fun authorizedSession(accountStableId: String): StoredOAuthSession? = session
    override fun hasPendingAuthorization(): Boolean = false
    override fun hasStoredCallback(): Boolean = false
    override fun cancelPendingAuthorization() = Unit
    override suspend fun completePendingAuthorization(): AuthCustomFeedWriteCapabilityResult =
        throw UnsupportedOperationException()
    override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult =
        AuthSessionResult.Authenticated(session)
}
