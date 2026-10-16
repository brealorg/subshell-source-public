package app.subshell.prototype.auth

import app.subshell.prototype.data.account.ModeratorRemovalFailureKind
import app.subshell.prototype.data.account.RedditModeratorRemovalRequest
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityResult
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratorRemovalResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratorRemovalWriter
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.error.FailureDetails
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Moderation02A5A7ModeratorRemovalCoordinatorTest {
    @Test
    fun acknowledgedRemovalIsOneWriteAndRecordsResult() = runBlocking {
        var writes = 0
        val coordinator = coordinator(
            writer = writer {
                writes += 1
                AuthenticatedRedditModeratorRemovalResult.Acknowledged
            },
        )
        coordinator.open("acct", "subshell", "target_user")
        coordinator.remove(request())

        assertEquals(1, writes)
        assertEquals("target_user", coordinator.uiState.value.lastRemovedUsername)
        assertEquals(1L, coordinator.uiState.value.completionRevision)
        assertFalse(coordinator.uiState.value.busy)
    }

    @Test
    fun authorizationCompletionNeverReplaysModeratorRemoval() = runBlocking {
        var writes = 0
        val capability = StoredCallbackCapability()
        val coordinator = coordinator(
            capability = capability,
            writer = writer {
                writes += 1
                if (writes == 1) {
                    AuthenticatedRedditModeratorRemovalResult.AuthorizationRequired
                } else {
                    AuthenticatedRedditModeratorRemovalResult.Acknowledged
                }
            },
        )
        coordinator.open("acct", "subshell", "target_user")
        coordinator.remove(request())
        assertEquals(1, writes)
        assertTrue(coordinator.uiState.value.authorizationRequired)

        assertTrue(coordinator.resumeStoredAuthorizationIfPresent())
        assertEquals(1, writes)
        assertTrue(coordinator.uiState.value.authorizationReady)
        assertFalse(coordinator.uiState.value.authorizationRequired)

        coordinator.remove(request())
        assertEquals(2, writes)
        assertEquals("target_user", coordinator.uiState.value.lastRemovedUsername)
    }

    @Test
    fun ownModeratorCannotBeRemoved() = runBlocking {
        var writes = 0
        val coordinator = coordinator(
            activeUsername = { "target_user" },
            writer = writer {
                writes += 1
                AuthenticatedRedditModeratorRemovalResult.Acknowledged
            },
        )
        coordinator.open("acct", "subshell", "target_user")
        coordinator.remove(request())

        assertEquals(0, writes)
        assertEquals(
            ModeratorRemovalFailureKind.VALIDATION,
            coordinator.uiState.value.failureKind,
        )
    }

    @Test
    fun accountChangeBlocksRemovalBeforeWriter() = runBlocking {
        var active = "acct"
        var writes = 0
        val coordinator = coordinator(
            active = { active },
            writer = writer {
                writes += 1
                AuthenticatedRedditModeratorRemovalResult.Acknowledged
            },
        )
        coordinator.open("acct", "subshell", "target_user")
        active = "other"
        coordinator.remove(request())

        assertEquals(0, writes)
        assertEquals(
            ModeratorRemovalFailureKind.ACCOUNT_CHANGED,
            coordinator.uiState.value.failureKind,
        )
    }

    @Test
    fun redditFailureDetailsSurviveRemovalBoundaries() = runBlocking {
        val failureDetails = FailureDetails().withProviderDetails(
            code = "HTTP_403",
            message = "moderator hierarchy refused",
        )
        val coordinator = coordinator(
            writer = writer {
                AuthenticatedRedditModeratorRemovalResult.Failed(
                    kind = ModeratorRemovalFailureKind.AUTHORIZATION,
                    httpStatusCode = 403,
                    failureDetails = failureDetails,
                )
            },
        )
        coordinator.open("acct", "subshell", "target_user")
        coordinator.remove(request())

        assertEquals(
            ModeratorRemovalFailureKind.AUTHORIZATION,
            coordinator.uiState.value.failureKind,
        )
        assertEquals(403, coordinator.uiState.value.failureHttpStatusCode)
        assertEquals(failureDetails, coordinator.uiState.value.failureDetails)
    }

    @Test
    fun unknownOutcomeIsNotAutomaticallyRetried() = runBlocking {
        var writes = 0
        val coordinator = coordinator(
            writer = writer {
                writes += 1
                AuthenticatedRedditModeratorRemovalResult.Failed(
                    ModeratorRemovalFailureKind.OUTCOME_UNKNOWN,
                )
            },
        )
        coordinator.open("acct", "subshell", "target_user")
        coordinator.remove(request())

        assertEquals(1, writes)
        assertEquals(
            ModeratorRemovalFailureKind.OUTCOME_UNKNOWN,
            coordinator.uiState.value.failureKind,
        )
        assertFalse(coordinator.uiState.value.busy)
    }

    @Test
    fun sharedModeratorTeamOwnerRoutesCallbackToRemovalWithPendingRequest() = runBlocking {
        val capability = StoredCallbackCapability()
        val inviteCoordinator = ModeratorInviteCoordinator(
            capability = capability,
            writer = object : app.subshell.prototype.data.auth.AuthenticatedRedditModeratorInviteWriter {
                override suspend fun invite(
                    request: app.subshell.prototype.data.account.RedditModeratorInviteRequest,
                ) = app.subshell.prototype.data.auth.AuthenticatedRedditModeratorInviteResult.Acknowledged
            },
            clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
            activeAccountStableId = { "acct" },
            activeAccountUsername = { "owner" },
        )
        val permissionCoordinator = ModeratorPermissionCoordinator(
            capability = capability,
            writer = object : app.subshell.prototype.data.auth.AuthenticatedRedditModeratorPermissionWriter {
                override suspend fun update(
                    request: app.subshell.prototype.data.account.RedditModeratorPermissionUpdateRequest,
                ) = app.subshell.prototype.data.auth.AuthenticatedRedditModeratorPermissionUpdateResult.Acknowledged
            },
            clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
            activeAccountStableId = { "acct" },
            activeAccountUsername = { "owner" },
        )
        val removalCoordinator = coordinator(
            capability = capability,
            writer = writer {
                AuthenticatedRedditModeratorRemovalResult.AuthorizationRequired
            },
        )
        removalCoordinator.open("acct", "subshell", "target_user")
        removalCoordinator.remove(request())

        assertFalse(inviteCoordinator.handleCallbackResultTokenIfOwned("ok"))
        assertTrue(capability.hasStoredCallback())
        assertFalse(permissionCoordinator.handleCallbackResultTokenIfOwned("ok"))
        assertTrue(capability.hasStoredCallback())
        assertTrue(removalCoordinator.handleCallbackResultTokenIfOwned("ok"))
        assertTrue(removalCoordinator.uiState.value.authorizationReady)
    }

    private fun coordinator(
        capability: AuthCommentWriteCapability? = null,
        active: () -> String? = { "acct" },
        activeUsername: () -> String? = { "owner" },
        writer: AuthenticatedRedditModeratorRemovalWriter,
    ) = ModeratorRemovalCoordinator(
        capability = capability,
        writer = writer,
        clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
        activeAccountStableId = active,
        activeAccountUsername = activeUsername,
    )

    private fun request() = RedditModeratorRemovalRequest(
        accountStableId = "acct",
        communityName = "subshell",
        username = "target_user",
    )

    private fun writer(
        block: suspend (
            RedditModeratorRemovalRequest,
        ) -> AuthenticatedRedditModeratorRemovalResult,
    ) = object : AuthenticatedRedditModeratorRemovalWriter {
        override suspend fun remove(
            request: RedditModeratorRemovalRequest,
        ): AuthenticatedRedditModeratorRemovalResult = block(request)
    }

    private class StoredCallbackCapability : AuthCommentWriteCapability {
        private var stored = true
        override suspend fun beginAuthorization(accountStableId: String): String =
            "https://example.invalid/oauth"
        override fun hasPendingAuthorization(): Boolean = false
        override fun hasStoredCallback(): Boolean = stored
        override fun cancelPendingAuthorization() {
            stored = false
        }
        override suspend fun completePendingAuthorization(): AuthCommentWriteCapabilityResult {
            stored = false
            return AuthCommentWriteCapabilityResult.Upgraded("acct", "owner")
        }
        override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult =
            AuthSessionResult.ReauthenticationRequired
    }
}
