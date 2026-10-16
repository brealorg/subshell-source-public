package app.subshell.prototype.auth

import app.subshell.prototype.data.account.ModeratorPermissionUpdateFailureKind
import app.subshell.prototype.data.account.RedditModeratorPermission
import app.subshell.prototype.data.account.RedditModeratorPermissionUpdateRequest
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityResult
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratorPermissionUpdateResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratorPermissionWriter
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.auth.AUTH_CALLBACK_CANCELLED_TOKEN
import app.subshell.prototype.data.auth.AuthBrowserLaunchResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Moderation02A5A6ModeratorPermissionCoordinatorTest {
    @Test
    fun acknowledgedPermissionUpdateIsOneWriteAndRecordsResult() = runBlocking {
        var writes = 0
        val coordinator = coordinator(
            writer = writer {
                writes += 1
                AuthenticatedRedditModeratorPermissionUpdateResult.Acknowledged
            },
        )
        coordinator.open("acct", "subshell", "target_user")
        coordinator.update(request())

        assertEquals(1, writes)
        assertEquals("target_user", coordinator.uiState.value.lastUpdatedUsername)
        assertEquals("+posts", coordinator.uiState.value.lastUpdatedPermissions)
        assertEquals(1L, coordinator.uiState.value.completionRevision)
        assertFalse(coordinator.uiState.value.busy)
    }

    @Test
    fun authorizationCompletionNeverReplaysPermissionUpdate() = runBlocking {
        var writes = 0
        val capability = StoredCallbackCapability()
        val coordinator = coordinator(
            capability = capability,
            writer = writer {
                writes += 1
                if (writes == 1) {
                    AuthenticatedRedditModeratorPermissionUpdateResult.AuthorizationRequired
                } else {
                    AuthenticatedRedditModeratorPermissionUpdateResult.Acknowledged
                }
            },
        )
        coordinator.open("acct", "subshell", "target_user")
        coordinator.update(request())
        assertEquals(1, writes)
        assertTrue(coordinator.uiState.value.authorizationRequired)

        assertTrue(coordinator.resumeStoredAuthorizationIfPresent())
        assertEquals(1, writes)
        assertTrue(coordinator.uiState.value.authorizationReady)
        assertFalse(coordinator.uiState.value.authorizationRequired)

        coordinator.update(request())
        assertEquals(2, writes)
        assertEquals("target_user", coordinator.uiState.value.lastUpdatedUsername)
    }

    @Test
    fun ownModeratorPermissionsCannotBeEdited() = runBlocking {
        var writes = 0
        val coordinator = coordinator(
            activeUsername = { "target_user" },
            writer = writer {
                writes += 1
                AuthenticatedRedditModeratorPermissionUpdateResult.Acknowledged
            },
        )
        coordinator.open("acct", "subshell", "target_user")
        coordinator.update(request())

        assertEquals(0, writes)
        assertEquals(
            ModeratorPermissionUpdateFailureKind.VALIDATION,
            coordinator.uiState.value.failureKind,
        )
    }

    @Test
    fun accountChangeBlocksPermissionWriteBeforeWriter() = runBlocking {
        var active = "acct"
        var writes = 0
        val coordinator = coordinator(
            active = { active },
            writer = writer {
                writes += 1
                AuthenticatedRedditModeratorPermissionUpdateResult.Acknowledged
            },
        )
        coordinator.open("acct", "subshell", "target_user")
        active = "other"
        coordinator.update(request())

        assertEquals(0, writes)
        assertEquals(
            ModeratorPermissionUpdateFailureKind.ACCOUNT_CHANGED,
            coordinator.uiState.value.failureKind,
        )
    }

    @Test
    fun redditFailureDetailsSurvivePermissionBoundaries() = runBlocking {
        val failureDetails = FailureDetails().withProviderDetails(
            code = "INVALID_PERMISSIONS · field=permissions",
            message = "invalid moderator permission",
        )
        val coordinator = coordinator(
            writer = writer {
                AuthenticatedRedditModeratorPermissionUpdateResult.Failed(
                    kind = ModeratorPermissionUpdateFailureKind.VALIDATION,
                    failureDetails = failureDetails,
                )
            },
        )
        coordinator.open("acct", "subshell", "target_user")
        coordinator.update(request())

        assertEquals(
            ModeratorPermissionUpdateFailureKind.VALIDATION,
            coordinator.uiState.value.failureKind,
        )
        assertEquals(failureDetails, coordinator.uiState.value.failureDetails)
    }

    @Test
    fun unknownOutcomeIsNotAutomaticallyRetried() = runBlocking {
        var writes = 0
        val coordinator = coordinator(
            writer = writer {
                writes += 1
                AuthenticatedRedditModeratorPermissionUpdateResult.Failed(
                    ModeratorPermissionUpdateFailureKind.OUTCOME_UNKNOWN,
                )
            },
        )
        coordinator.open("acct", "subshell", "target_user")
        coordinator.update(request())

        assertEquals(1, writes)
        assertEquals(
            ModeratorPermissionUpdateFailureKind.OUTCOME_UNKNOWN,
            coordinator.uiState.value.failureKind,
        )
        assertFalse(coordinator.uiState.value.busy)
    }


    @Test
    fun sharedModeratorTeamOwnerRoutesCallbackToCoordinatorWithPendingRequest() = runBlocking {
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
        val permissionCoordinator = coordinator(
            capability = capability,
            writer = writer {
                AuthenticatedRedditModeratorPermissionUpdateResult.AuthorizationRequired
            },
        )
        permissionCoordinator.open("acct", "subshell", "target_user")
        permissionCoordinator.update(request())

        assertFalse(inviteCoordinator.handleCallbackResultTokenIfOwned("ok"))
        assertTrue(capability.hasStoredCallback())
        assertTrue(permissionCoordinator.handleCallbackResultTokenIfOwned("ok"))
        assertTrue(permissionCoordinator.uiState.value.authorizationReady)
    }

    @Test
    fun aCancelledAuthorizationCanBeStartedAgain() = runBlocking {
        // AUTHCALLBACK11: the dialog keeps showing Authorize after a cancel,
        // so the request it authorizes must survive the cancel.
        val coordinator = coordinator(
            capability = StoredCallbackCapability(),
            writer = writer { AuthenticatedRedditModeratorPermissionUpdateResult.AuthorizationRequired },
        )
        coordinator.open("acct", "subshell", "target_user")
        coordinator.update(request())
        assertTrue(coordinator.beginAuthorization() is AuthBrowserLaunchResult.Ready)

        assertTrue(coordinator.handleCallbackResultTokenIfOwned(AUTH_CALLBACK_CANCELLED_TOKEN))
        assertTrue(coordinator.uiState.value.authorizationRequired)
        assertFalse(coordinator.uiState.value.authorizing)

        assertTrue(coordinator.beginAuthorization() is AuthBrowserLaunchResult.Ready)
    }

    private fun coordinator(
        capability: AuthCommentWriteCapability? = null,
        active: () -> String? = { "acct" },
        activeUsername: () -> String? = { "owner" },
        writer: AuthenticatedRedditModeratorPermissionWriter,
    ) = ModeratorPermissionCoordinator(
        capability = capability,
        writer = writer,
        clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
        activeAccountStableId = active,
        activeAccountUsername = activeUsername,
    )

    private fun request() = RedditModeratorPermissionUpdateRequest(
        accountStableId = "acct",
        communityName = "subshell",
        username = "target_user",
        permissions = setOf(RedditModeratorPermission.POSTS),
    )

    private fun writer(
        block: suspend (
            RedditModeratorPermissionUpdateRequest,
        ) -> AuthenticatedRedditModeratorPermissionUpdateResult,
    ) = object : AuthenticatedRedditModeratorPermissionWriter {
        override suspend fun update(
            request: RedditModeratorPermissionUpdateRequest,
        ): AuthenticatedRedditModeratorPermissionUpdateResult = block(request)
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
