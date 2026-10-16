package app.subshell.prototype.auth

import app.subshell.prototype.data.account.*
import app.subshell.prototype.data.auth.*
import app.subshell.prototype.data.auth.reddit.*
import app.subshell.prototype.data.network.NetworkBearerTokenProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * ISSUE96_CANCEL_WAITING_AUTHORIZATION_V1 (96d): waiting for the browser has
 * no timeout. A moderator action can start again while it waits (the old
 * authorization is dropped), and closing the mod log cancels its authorization.
 */
class Issue96dModerationWaitingAuthorizationTest {
    private class FakeCapability : AuthCommentWriteCapability {
        var cancels = 0
        override suspend fun beginAuthorization(accountStableId: String) = "https://www.reddit.com/api/v1/authorize"
        override fun hasPendingAuthorization() = true
        override fun hasStoredCallback() = false
        override fun cancelPendingAuthorization() { cancels += 1 }
        override suspend fun completePendingAuthorization(): AuthCommentWriteCapabilityResult =
            AuthCommentWriteCapabilityResult.Failed(AuthCommentWriteCapabilityFailureKind.entries.first())
        override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult =
            throw UnsupportedOperationException()
        override fun bearerTokenProvider(accessToken: String) = NetworkBearerTokenProvider { accessToken }
    }

    private val approve = RedditModeratorActionRequest("owner", "subshell", "t3_abc", RedditModeratorAction.APPROVE)
    private val remove = RedditModeratorActionRequest("owner", "subshell", "t3_def", RedditModeratorAction.REMOVE)

    @Test
    fun aModeratorActionWhileWaitingForTheBrowserDropsTheOldAuthorizationAndIsSent() = runBlocking {
        val capability = FakeCapability()
        val writes = mutableListOf<RedditModeratorActionRequest>()
        var authorized = false
        val coordinator = ModerationActionCoordinator(
            capability = capability,
            writer = object : AuthenticatedRedditModerationWriter {
                override suspend fun mutate(request: RedditModeratorActionRequest): AuthenticatedRedditModerationActionResult {
                    if (!authorized) return AuthenticatedRedditModerationActionResult.AuthorizationRequired
                    writes += request
                    return AuthenticatedRedditModerationActionResult.Updated()
                }
            },
            clientMode = OAuthClientMode.NATIVE_PRODUCTION,
            activeAccountStableId = { "owner" },
        )
        coordinator.moderate(approve)
        assertEquals(RedditModeratorActionPhase.AUTHORIZATION_REQUIRED, coordinator.uiState.value.phase)
        coordinator.beginAuthorization()
        assertEquals(RedditModeratorActionPhase.AWAITING_CALLBACK, coordinator.uiState.value.phase)
        assertFalse(coordinator.uiState.value.inProgress)

        // The browser never answers; the token arrived another way, the moderator acts again.
        authorized = true
        coordinator.moderate(remove)

        assertEquals(1, capability.cancels)
        assertEquals(listOf(remove), writes)
    }

    @Test
    fun closingTheModLogWhileWaitingCancelsItsAuthorization() = runBlocking {
        val capability = FakeCapability()
        val coordinator = ModerationLogCoordinator(
            capability = capability,
            loader = object : AuthenticatedRedditModerationLogLoader {
                override suspend fun load(accountStableId: String, rawCommunityName: String, after: String?) =
                    AuthenticatedRedditModerationLogResult.AuthorizationRequired
            },
            clientMode = OAuthClientMode.NATIVE_PRODUCTION,
            activeAccountStableId = { "acct" },
        )
        coordinator.open("acct", "subshell")
        coordinator.load("acct")
        coordinator.beginAuthorization()
        assertTrue(coordinator.uiState.value.authorizing)

        coordinator.dismiss()

        assertEquals(1, capability.cancels)
        assertFalse(coordinator.uiState.value.authorizing)
    }
}
