package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.account.ModeratorUserManagementFailureKind
import app.subshell.prototype.data.account.RedditModeratorUserApprovalRequest
import app.subshell.prototype.data.account.RedditModeratorUserBanRequest
import app.subshell.prototype.data.account.RedditModeratorUserMuteRequest
import app.subshell.prototype.data.auth.AuthAccountIdentity
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityResult
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratorUserMutationResult
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
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * STEP3 (moderation02a5a1-a5a3): banning, muting and approving a user go out
 * only for the account that is active before and after authorization
 * (otherwise nothing is sent), once, to the community's friend/unfriend
 * endpoint with the right relationship type; a missing grant asks to
 * authorize; and the ban and mute fields keep Reddit's limits.
 */
class Step3ModeratorUserWritersTest {
    private val account = authenticatedAccountId(OAuthProvider.REDDIT, OAuthClientMode.REDREADER_COMPATIBILITY, "me")
    private val endpoint = NetworkEndpoint.https("https://oauth.reddit.test/")
    private val session = StoredOAuthSession(
        provider = OAuthProvider.REDDIT,
        tokens = OAuthTokenSet("access", "refresh", "bearer", "identity read modcontributors", Long.MAX_VALUE),
        identity = AuthAccountIdentity(providerAccountId = "t2me", username = "me"),
    )
    private var active: String? = account
    private val requests = mutableListOf<Pair<String, String>>()

    private inner class Capability(private val answer: () -> AuthSessionResult) : AuthCommentWriteCapability {
        override suspend fun beginAuthorization(accountStableId: String) = "https://example.test/authorize"
        override fun hasPendingAuthorization() = false
        override fun hasStoredCallback() = false
        override fun cancelPendingAuthorization() = Unit
        override suspend fun completePendingAuthorization(): AuthCommentWriteCapabilityResult = error("unused")
        override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult = answer()
    }

    private val client = OkHttpClient.Builder().addInterceptor(
        Interceptor { chain ->
            val body = Buffer().also { buffer -> chain.request().body?.writeTo(buffer) }.readUtf8()
            requests += chain.request().url.encodedPath to body
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK")
                .body("{\"json\":{\"errors\":[]}}".toResponseBody("application/json".toMediaType()))
                .build()
        },
    ).build()

    private fun authenticated() = Capability { AuthSessionResult.Authenticated(session) }
    private fun switching() = Capability {
        active = "reddit:someone_else"
        AuthSessionResult.Authenticated(session)
    }
    private fun denied() = Capability { AuthSessionResult.AuthorizationDenied("missing_incremental_scope") }

    private val ban = RedditModeratorUserBanRequest(account, "subshell", "target_user", banned = true, banReason = "spam")
    private val mute = RedditModeratorUserMuteRequest(account, "subshell", "target_user", muted = true)
    private val approve = RedditModeratorUserApprovalRequest(account, "subshell", "target_user", approved = true)

    private suspend fun all(capability: AuthCommentWriteCapability): List<AuthenticatedRedditModeratorUserMutationResult> {
        val results = mutableListOf<AuthenticatedRedditModeratorUserMutationResult>()
        val saved = active
        results += RedditModeratorUserWriter(capability, client, endpoint, "subshell-test", { active }, Dispatchers.Unconfined).mutate(ban)
        active = saved
        results += RedditModeratorMuteWriter(capability, client, endpoint, "subshell-test", { active }, Dispatchers.Unconfined).mutate(mute)
        active = saved
        results += RedditModeratorApprovalWriter(capability, client, endpoint, "subshell-test", { active }, Dispatchers.Unconfined).mutate(approve)
        active = saved
        return results
    }

    private fun AuthenticatedRedditModeratorUserMutationResult.kind() =
        (this as? AuthenticatedRedditModeratorUserMutationResult.Failed)?.kind

    @Test
    fun anotherActiveAccountSendsNothingBeforeOrAfterAuthorization() = runBlocking {
        active = "reddit:someone_else"
        assertEquals(List(3) { ModeratorUserManagementFailureKind.SESSION }, all(authenticated()).map { it.kind() })

        active = account
        assertEquals(List(3) { ModeratorUserManagementFailureKind.SESSION }, all(switching()).map { it.kind() })

        assertEquals(emptyList<Pair<String, String>>(), requests)
    }

    @Test
    fun theActiveAccountSendsEachChangeOnceWithItsRelationshipType() = runBlocking {
        val results = all(authenticated())

        assertEquals(List(3) { AuthenticatedRedditModeratorUserMutationResult.Acknowledged }, results)
        assertEquals(List(3) { "/r/subshell/api/friend" }, requests.map { it.first })
        assertTrue(requests[0].second, "type=banned" in requests[0].second)
        assertTrue(requests[1].second, "type=muted" in requests[1].second)
        assertTrue(requests[2].second, "type=contributor" in requests[2].second)
    }

    @Test
    fun aMissingGrantAsksToAuthorizeWithoutSending() = runBlocking {
        assertEquals(List(3) { AuthenticatedRedditModeratorUserMutationResult.AuthorizationRequired }, all(denied()))
        assertEquals(emptyList<Pair<String, String>>(), requests)
    }

    private fun refused(block: () -> Unit) = runCatching(block).isFailure

    @Test
    fun banAndMuteFieldsKeepRedditsLimits() {
        fun ban(days: Int? = null, reason: String? = null, note: String? = null, message: String? = null) =
            RedditModeratorUserBanRequest(account, "subshell", "target_user", banned = true, durationDays = days, banReason = reason, moderatorNote = note, messageToUser = message)

        ban(reason = "r".repeat(100), note = "n".repeat(300), message = "m".repeat(1_000), days = 999)
        ban(days = 1)
        ban(days = null)
        assertTrue(refused { ban(reason = "r".repeat(101)) })
        assertTrue(refused { ban(note = "n".repeat(301)) })
        assertTrue(refused { ban(message = "m".repeat(1_001)) })
        assertTrue(refused { ban(days = 0) })
        assertTrue(refused { ban(days = 1_000) })
        assertTrue(refused { RedditModeratorUserBanRequest(account, "subshell", "target_user", banned = false, banReason = "x") })

        RedditModeratorUserMuteRequest(account, "subshell", "target_user", muted = true, moderatorNote = "n".repeat(300))
        assertTrue(refused { RedditModeratorUserMuteRequest(account, "subshell", "target_user", muted = true, moderatorNote = "n".repeat(301)) })
        assertTrue(refused { RedditModeratorUserMuteRequest(account, "subshell", "target_user", muted = false, moderatorNote = "x") })
    }
}
