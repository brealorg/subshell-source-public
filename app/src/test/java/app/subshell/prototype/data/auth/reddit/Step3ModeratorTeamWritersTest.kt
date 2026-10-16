package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.account.ModeratorInviteFailureKind
import app.subshell.prototype.data.account.ModeratorPermissionUpdateFailureKind
import app.subshell.prototype.data.account.ModeratorRemovalFailureKind
import app.subshell.prototype.data.account.RedditModeratorInviteRequest
import app.subshell.prototype.data.account.RedditModeratorPermissionUpdateRequest
import app.subshell.prototype.data.account.RedditModeratorRemovalRequest
import app.subshell.prototype.data.auth.AuthAccountIdentity
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityResult
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratorInviteResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratorPermissionUpdateResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratorRemovalResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.OAuthProvider
import app.subshell.prototype.data.auth.OAuthTokenSet
import app.subshell.prototype.data.auth.StoredOAuthSession
import app.subshell.prototype.data.auth.authenticatedAccountId
import app.subshell.prototype.data.network.NetworkEndpoint
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * STEP3 (moderation02a5a6, a5a7, a5a5): the moderator permission, removal
 * and invitation writers send one change at a time, only for the account that is active
 * before and after authorization (nothing goes out otherwise), ask to
 * authorize when the grant is missing, and read Reddit's answer as: 401/403
 * needs authorization, 5xx or a lost answer has an unknown outcome.
 */
class Step3ModeratorTeamWritersTest {
    private val account = authenticatedAccountId(OAuthProvider.REDDIT, OAuthClientMode.REDREADER_COMPATIBILITY, "me")
    private val endpoint = NetworkEndpoint.https("https://oauth.reddit.test/")
    private val session = StoredOAuthSession(
        provider = OAuthProvider.REDDIT,
        tokens = OAuthTokenSet("access", "refresh", "bearer", "identity read modothers", Long.MAX_VALUE),
        identity = AuthAccountIdentity(providerAccountId = "t2me", username = "me"),
    )
    private var active: String? = account
    private val requests = mutableListOf<String>()

    private class Capability(
        private val answer: suspend () -> AuthSessionResult,
    ) : AuthCommentWriteCapability {
        override suspend fun beginAuthorization(accountStableId: String) = "https://example.test/authorize"
        override fun hasPendingAuthorization() = false
        override fun hasStoredCallback() = false
        override fun cancelPendingAuthorization() = Unit
        override suspend fun completePendingAuthorization(): AuthCommentWriteCapabilityResult = error("unused")
        override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult = answer()
    }

    private fun client(answer: () -> Pair<Int, String>) = OkHttpClient.Builder().addInterceptor(
        Interceptor { chain ->
            requests += chain.request().url.encodedPath
            val (code, body) = answer()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(code).message("answer")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        },
    ).build()

    private fun failingClient() = OkHttpClient.Builder().addInterceptor(
        Interceptor { chain ->
            requests += chain.request().url.encodedPath
            throw IOException("connection lost")
        },
    ).build()

    private fun authenticated(): suspend () -> AuthSessionResult = { AuthSessionResult.Authenticated(session) }

    private fun permissions(
        capability: AuthCommentWriteCapability = Capability(authenticated()),
        callFactory: okhttp3.Call.Factory = client { 200 to "{}" },
    ) = RedditModeratorPermissionWriter(capability, callFactory, endpoint, "subshell-test", { active }, Dispatchers.Unconfined)

    private fun removals(
        capability: AuthCommentWriteCapability = Capability(authenticated()),
        callFactory: okhttp3.Call.Factory = client { 200 to "{}" },
    ) = RedditModeratorRemovalWriter(capability, callFactory, endpoint, "subshell-test", { active }, Dispatchers.Unconfined)

    private fun invites(
        capability: AuthCommentWriteCapability = Capability(authenticated()),
        callFactory: okhttp3.Call.Factory = client { 200 to "{\"json\":{\"errors\":[]}}" },
    ) = RedditModeratorInvitationWriter(capability, callFactory, endpoint, "subshell-test", { active }, Dispatchers.Unconfined)

    private val invite = RedditModeratorInviteRequest(account, "kotlin", "someone", allPermissions = true)

    private val permissionChange = RedditModeratorPermissionUpdateRequest(account, "kotlin", "someone", allPermissions = true)
    private val removal = RedditModeratorRemovalRequest(account, "kotlin", "someone")

    private fun AuthenticatedRedditModeratorPermissionUpdateResult.kind() =
        (this as AuthenticatedRedditModeratorPermissionUpdateResult.Failed).kind

    private fun AuthenticatedRedditModeratorInviteResult.kind() =
        (this as AuthenticatedRedditModeratorInviteResult.Failed).kind

    private fun AuthenticatedRedditModeratorRemovalResult.kind() =
        (this as AuthenticatedRedditModeratorRemovalResult.Failed).kind

    @Test
    fun anotherActiveAccountSendsNothingBeforeOrAfterAuthorization() = runBlocking {
        active = "reddit:someone_else"
        assertEquals(ModeratorPermissionUpdateFailureKind.ACCOUNT_CHANGED, permissions().update(permissionChange).kind())
        assertEquals(ModeratorRemovalFailureKind.ACCOUNT_CHANGED, removals().remove(removal).kind())
        assertEquals(ModeratorInviteFailureKind.ACCOUNT_CHANGED, invites().invite(invite).kind())

        active = account
        val switching = Capability {
            active = "reddit:someone_else"
            AuthSessionResult.Authenticated(session)
        }
        assertEquals(ModeratorPermissionUpdateFailureKind.ACCOUNT_CHANGED, permissions(switching).update(permissionChange).kind())
        active = account
        assertEquals(ModeratorRemovalFailureKind.ACCOUNT_CHANGED, removals(switching).remove(removal).kind())
        active = account
        assertEquals(ModeratorInviteFailureKind.ACCOUNT_CHANGED, invites(switching).invite(invite).kind())

        assertEquals(emptyList<String>(), requests)
    }

    @Test
    fun aSecondChangeWhileOneRunsIsRefused() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val waiting = Capability {
            release.await()
            AuthSessionResult.Authenticated(session)
        }
        val permissionWriter = permissions(waiting)
        val removalWriter = removals(waiting)
        val inviteWriter = invites(waiting)

        val firstPermission = async(start = CoroutineStart.UNDISPATCHED) { permissionWriter.update(permissionChange) }
        val firstRemoval = async(start = CoroutineStart.UNDISPATCHED) { removalWriter.remove(removal) }
        val firstInvite = async(start = CoroutineStart.UNDISPATCHED) { inviteWriter.invite(invite) }
        // A writer without its guard would wait here for the first change; the
        // timeout turns that into a failure instead of a hang.
        withTimeout(5_000) {
            assertEquals(ModeratorPermissionUpdateFailureKind.ALREADY_MUTATING, permissionWriter.update(permissionChange).kind())
            assertEquals(ModeratorRemovalFailureKind.ALREADY_MUTATING, removalWriter.remove(removal).kind())
            assertEquals(ModeratorInviteFailureKind.ALREADY_MUTATING, inviteWriter.invite(invite).kind())
        }
        release.complete(Unit)

        assertEquals(AuthenticatedRedditModeratorPermissionUpdateResult.Acknowledged, firstPermission.await())
        assertEquals(AuthenticatedRedditModeratorRemovalResult.Acknowledged, firstRemoval.await())
        assertEquals(AuthenticatedRedditModeratorInviteResult.Acknowledged, firstInvite.await())
        assertEquals(3, requests.size)
    }

    @Test
    fun aMissingGrantAsksToAuthorizeWithoutSending() = runBlocking {
        val denied = Capability { AuthSessionResult.AuthorizationDenied("missing_incremental_scope") }

        assertEquals(AuthenticatedRedditModeratorPermissionUpdateResult.AuthorizationRequired, permissions(denied).update(permissionChange))
        assertEquals(AuthenticatedRedditModeratorRemovalResult.AuthorizationRequired, removals(denied).remove(removal))
        assertEquals(AuthenticatedRedditModeratorInviteResult.AuthorizationRequired, invites(denied).invite(invite))
        assertEquals(emptyList<String>(), requests)
    }

    @Test
    fun aRemovalAnswerIsReadAsAuthorizationOrUnknownOutcome() = runBlocking {
        assertEquals(ModeratorRemovalFailureKind.AUTHORIZATION, removals(callFactory = client { 403 to "{}" }).remove(removal).kind())
        assertEquals(ModeratorRemovalFailureKind.AUTHORIZATION, removals(callFactory = client { 401 to "{}" }).remove(removal).kind())
        assertEquals(ModeratorRemovalFailureKind.OUTCOME_UNKNOWN, removals(callFactory = client { 502 to "{}" }).remove(removal).kind())
        assertEquals(ModeratorRemovalFailureKind.OUTCOME_UNKNOWN, removals(callFactory = failingClient()).remove(removal).kind())
        assertEquals(ModeratorRemovalFailureKind.HTTP, removals(callFactory = client { 404 to "{}" }).remove(removal).kind())
        assertEquals(AuthenticatedRedditModeratorRemovalResult.Acknowledged, removals().remove(removal))
    }

    @Test
    fun anInvitationRefusedByRedditCarriesItsReason() = runBlocking {
        val refusal = "{\"json\":{\"errors\":[[\"USER_REQUIRED\",\"please enter a username\",\"name\"]]}}"

        val failed = invites(callFactory = client { 200 to refusal }).invite(invite) as AuthenticatedRedditModeratorInviteResult.Failed

        val details = requireNotNull(failed.failureDetails)
        assertEquals("please enter a username", details.providerMessage?.value)
        assertEquals(listOf("/r/kotlin/api/friend"), requests.map { it.trimEnd('/') })
    }
}
