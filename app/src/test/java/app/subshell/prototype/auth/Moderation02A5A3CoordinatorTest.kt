package app.subshell.prototype.auth

import app.subshell.prototype.data.account.ModeratorUserManagementFailureKind
import app.subshell.prototype.data.account.RedditApprovedUser
import app.subshell.prototype.data.account.RedditModeratorUserApprovalRequest
import app.subshell.prototype.data.auth.AuthenticatedRedditApprovedUsersReader
import app.subshell.prototype.data.auth.AuthenticatedRedditApprovedUsersResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratorUserApprovalWriter
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratorUserMutationResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class Moderation02A5A3CoordinatorTest {
    @Test
    fun approveUsesFreshPreflightSingleWriteReadbackAndImmediateProjection() = runBlocking {
        var approved = false
        var writes = 0
        val coordinator = coordinator(
            reader = reader { _, _, _ ->
                AuthenticatedRedditApprovedUsersResult.Loaded(
                    "subshell",
                    if (approved) listOf(RedditApprovedUser("target_user")) else emptyList(),
                    null,
                )
            },
            writer = writer {
                writes++
                approved = it.approved
                AuthenticatedRedditModeratorUserMutationResult.Acknowledged
            },
        )

        coordinator.open("acct", "subshell")
        coordinator.loadApproved("acct")
        coordinator.mutateApproved(request(true))

        assertEquals(1, writes)
        assertEquals("target_user", coordinator.uiState.value.verifiedApprovedUsername)
        assertEquals(true, coordinator.uiState.value.verifiedApproved)
        assertEquals(
            listOf("target_user"),
            coordinator.uiState.value.approvedUsers.map { it.username },
        )
        assertFalse(coordinator.uiState.value.mutationInProgress)
    }

    @Test
    fun verifiedUnapproveRemovesRowWithoutManualRefresh() = runBlocking {
        var approved = true
        var writes = 0
        val coordinator = coordinator(
            reader = reader { _, _, _ ->
                AuthenticatedRedditApprovedUsersResult.Loaded(
                    "subshell",
                    if (approved) listOf(RedditApprovedUser("target_user")) else emptyList(),
                    null,
                )
            },
            writer = writer {
                writes++
                approved = it.approved
                AuthenticatedRedditModeratorUserMutationResult.Acknowledged
            },
        )

        coordinator.open("acct", "subshell")
        coordinator.loadApproved("acct")
        assertEquals(
            listOf("target_user"),
            coordinator.uiState.value.approvedUsers.map { it.username },
        )

        coordinator.mutateApproved(request(false))

        assertEquals(1, writes)
        assertEquals(false, coordinator.uiState.value.verifiedApproved)
        assertEquals(
            emptyList<String>(),
            coordinator.uiState.value.approvedUsers.map { it.username },
        )
        assertFalse(coordinator.uiState.value.mutationInProgress)
    }

    @Test
    fun unknownOutcomeNeverBlindRetries() = runBlocking {
        var writes = 0
        val coordinator = coordinator(
            reader = reader { _, _, _ ->
                AuthenticatedRedditApprovedUsersResult.Loaded(
                    "subshell",
                    emptyList(),
                    null,
                )
            },
            writer = writer {
                writes++
                AuthenticatedRedditModeratorUserMutationResult.Failed(
                    ModeratorUserManagementFailureKind.OUTCOME_UNKNOWN,
                )
            },
        )

        coordinator.open("acct", "subshell")
        coordinator.loadApproved("acct")
        coordinator.mutateApproved(request(true))

        assertEquals(1, writes)
        assertEquals(
            ModeratorUserManagementFailureKind.OUTCOME_UNKNOWN,
            coordinator.uiState.value.failureKind,
        )
        assertFalse(coordinator.uiState.value.failureRetryable)
    }

    private fun coordinator(
        reader: AuthenticatedRedditApprovedUsersReader,
        writer: AuthenticatedRedditModeratorUserApprovalWriter,
    ) = ModeratorUserManagementCoordinator(
        capability = null,
        reader = null,
        writer = null,
        mutedReader = null,
        muteWriter = null,
        approvedReader = reader,
        approvalWriter = writer,
        clientMode = null,
        activeAccountStableId = { "acct" },
        activeAccountUsername = { "current_user" },
    )

    private fun request(approved: Boolean) = RedditModeratorUserApprovalRequest(
        accountStableId = "acct",
        communityName = "subshell",
        username = "target_user",
        approved = approved,
    )

    private fun reader(
        block: suspend (String, String, String?) -> AuthenticatedRedditApprovedUsersResult,
    ) = object : AuthenticatedRedditApprovedUsersReader {
        override suspend fun load(
            accountStableId: String,
            rawCommunityName: String,
            after: String?,
            username: String?,
        ) = block(accountStableId, rawCommunityName, after)
    }

    private fun writer(
        block: suspend (RedditModeratorUserApprovalRequest) ->
            AuthenticatedRedditModeratorUserMutationResult,
    ) = object : AuthenticatedRedditModeratorUserApprovalWriter {
        override suspend fun mutate(request: RedditModeratorUserApprovalRequest) = block(request)
    }
}
