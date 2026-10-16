package app.subshell.prototype.auth

import app.subshell.prototype.data.account.ModeratorInviteFailureKind
import app.subshell.prototype.data.account.RedditModeratorInviteRequest
import app.subshell.prototype.data.account.RedditModeratorPermission
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityResult
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratorInviteResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratorInviteWriter
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.error.FailureDetails
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Moderation02A5A5ModeratorInviteCoordinatorTest {
    @Test
    fun acknowledgedInviteIsOneWriteAndRecordsPendingInvitationNotMembership() = runBlocking {
        var writes = 0
        val coordinator = coordinator(
            writer = writer {
                writes += 1
                AuthenticatedRedditModeratorInviteResult.Acknowledged
            },
        )
        coordinator.open("acct", "subshell")
        coordinator.invite(request())

        assertEquals(1, writes)
        assertEquals("target_user", coordinator.uiState.value.lastInvitedUsername)
        assertEquals("+posts", coordinator.uiState.value.lastInvitedPermissions)
        assertEquals(1L, coordinator.uiState.value.completionRevision)
        assertFalse(coordinator.uiState.value.busy)
    }

    @Test
    fun authorizationCompletionNeverReplaysInvitation() = runBlocking {
        var writes = 0
        val capability = StoredCallbackCapability()
        val coordinator = coordinator(
            capability = capability,
            writer = writer {
                writes += 1
                if (writes == 1) {
                    AuthenticatedRedditModeratorInviteResult.AuthorizationRequired
                } else {
                    AuthenticatedRedditModeratorInviteResult.Acknowledged
                }
            },
        )
        coordinator.open("acct", "subshell")
        coordinator.invite(request())
        assertEquals(1, writes)
        assertTrue(coordinator.uiState.value.authorizationRequired)

        assertTrue(coordinator.resumeStoredAuthorizationIfPresent())
        assertEquals(1, writes)
        assertTrue(coordinator.uiState.value.authorizationReady)
        assertFalse(coordinator.uiState.value.authorizationRequired)

        coordinator.invite(request())
        assertEquals(2, writes)
        assertEquals("target_user", coordinator.uiState.value.lastInvitedUsername)
    }

    @Test
    fun accountChangeBlocksInvitationBeforeWriter() = runBlocking {
        var active = "acct"
        var writes = 0
        val coordinator = coordinator(
            active = { active },
            writer = writer {
                writes += 1
                AuthenticatedRedditModeratorInviteResult.Acknowledged
            },
        )
        coordinator.open("acct", "subshell")
        active = "other"
        coordinator.invite(request())

        assertEquals(0, writes)
        assertEquals(ModeratorInviteFailureKind.ACCOUNT_CHANGED, coordinator.uiState.value.failureKind)
    }

    @Test
    fun redditFailureDetailsSurviveWriterAndCoordinatorBoundary() = runBlocking {
        val failureDetails = FailureDetails().withProviderDetails(
            code = "SUBREDDIT_RATELIMIT · field=name",
            message = "too many moderator invitations",
        )
        val coordinator = coordinator(
            writer = writer {
                AuthenticatedRedditModeratorInviteResult.Failed(
                    kind = ModeratorInviteFailureKind.VALIDATION,
                    failureDetails = failureDetails,
                )
            },
        )
        coordinator.open("acct", "subshell")
        coordinator.invite(request())

        assertEquals(ModeratorInviteFailureKind.VALIDATION, coordinator.uiState.value.failureKind)
        assertEquals(failureDetails, coordinator.uiState.value.failureDetails)
    }

    @Test
    fun unknownOutcomeIsNotAutomaticallyRetried() = runBlocking {
        var writes = 0
        val coordinator = coordinator(
            writer = writer {
                writes += 1
                AuthenticatedRedditModeratorInviteResult.Failed(
                    ModeratorInviteFailureKind.OUTCOME_UNKNOWN,
                )
            },
        )
        coordinator.open("acct", "subshell")
        coordinator.invite(request())

        assertEquals(1, writes)
        assertEquals(ModeratorInviteFailureKind.OUTCOME_UNKNOWN, coordinator.uiState.value.failureKind)
        assertFalse(coordinator.uiState.value.busy)
    }

    @Test
    fun ownAccountCannotBeInvited() = runBlocking {
        var writes = 0
        val coordinator = coordinator(
            activeUsername = { "target_user" },
            writer = writer {
                writes += 1
                AuthenticatedRedditModeratorInviteResult.Acknowledged
            },
        )
        coordinator.open("acct", "subshell")
        coordinator.invite(request())

        assertEquals(0, writes)
        assertEquals(ModeratorInviteFailureKind.VALIDATION, coordinator.uiState.value.failureKind)
    }

    // LOW10_REOPEN_KEEPS_AN_INVITE_IN_FLIGHT_V1 (OCB-427)
    @Test
    fun reopeningTheDialogWhileAnInviteRunsKeepsItSending() = runBlocking {
        val gate = kotlinx.coroutines.CompletableDeferred<AuthenticatedRedditModeratorInviteResult>()
        val coordinator = coordinator(writer = writer { gate.await() })
        coordinator.open("acct", "subshell")
        val job = launch(kotlinx.coroutines.Dispatchers.Unconfined, kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            coordinator.invite(request())
        }
        assertTrue(coordinator.uiState.value.sending)

        coordinator.dismiss()
        coordinator.open("acct", "subshell")

        assertTrue(coordinator.uiState.value.sending)
        gate.complete(AuthenticatedRedditModeratorInviteResult.Acknowledged)
        job.join()
        assertFalse(coordinator.uiState.value.sending)
    }

    private fun coordinator(
        capability: AuthCommentWriteCapability? = null,
        active: () -> String? = { "acct" },
        activeUsername: () -> String? = { "owner" },
        writer: AuthenticatedRedditModeratorInviteWriter,
    ) = ModeratorInviteCoordinator(
        capability = capability,
        writer = writer,
        clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
        activeAccountStableId = active,
        activeAccountUsername = activeUsername,
    )

    private fun request() = RedditModeratorInviteRequest(
        accountStableId = "acct",
        communityName = "subshell",
        username = "target_user",
        permissions = setOf(RedditModeratorPermission.POSTS),
    )

    private fun writer(
        block: suspend (RedditModeratorInviteRequest) -> AuthenticatedRedditModeratorInviteResult,
    ) = object : AuthenticatedRedditModeratorInviteWriter {
        override suspend fun invite(
            request: RedditModeratorInviteRequest,
        ): AuthenticatedRedditModeratorInviteResult = block(request)
    }

    private class StoredCallbackCapability : AuthCommentWriteCapability {
        private var stored = true
        override suspend fun beginAuthorization(accountStableId: String): String = "https://example.invalid/oauth"
        override fun hasPendingAuthorization(): Boolean = false
        override fun hasStoredCallback(): Boolean = stored
        override fun cancelPendingAuthorization() { stored = false }
        override suspend fun completePendingAuthorization(): AuthCommentWriteCapabilityResult {
            stored = false
            return AuthCommentWriteCapabilityResult.Upgraded("acct", "owner")
        }
        override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult =
            AuthSessionResult.ReauthenticationRequired
    }
}
