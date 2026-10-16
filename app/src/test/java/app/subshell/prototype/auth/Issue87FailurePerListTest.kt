package app.subshell.prototype.auth

import app.subshell.prototype.data.account.ModeratorUserManagementFailureKind
import app.subshell.prototype.data.account.ModeratorUserManagementFailureSection
import app.subshell.prototype.data.account.RedditModeratorUserBanRequest
import app.subshell.prototype.data.auth.AuthenticatedRedditBannedUsersReader
import app.subshell.prototype.data.auth.AuthenticatedRedditBannedUsersResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratorUserMutationResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratorUserWriter
import app.subshell.prototype.data.auth.AuthenticatedRedditMutedUsersReader
import app.subshell.prototype.data.auth.AuthenticatedRedditMutedUsersResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * ISSUE87_FAILURE_KEPT_PER_LIST_V1: banned, muted and approved shared one
 * failure slot; a later muted read erased a banned-list failure, and a muted
 * read erased an "Action outcome not confirmed" warning about a ban.
 */
class Issue87FailurePerListTest {
    private var bannedRead: () -> AuthenticatedRedditBannedUsersResult = {
        AuthenticatedRedditBannedUsersResult.Loaded("subshell", emptyList(), null)
    }

    private fun userManagement(): ModeratorUserManagementCoordinator {
        val coordinator = ModeratorUserManagementCoordinator(
            capability = null,
            reader = object : AuthenticatedRedditBannedUsersReader {
                override suspend fun load(accountStableId: String, rawCommunityName: String, after: String?, username: String?) =
                    bannedRead()
            },
            writer = object : AuthenticatedRedditModeratorUserWriter {
                override suspend fun mutate(request: RedditModeratorUserBanRequest) =
                    AuthenticatedRedditModeratorUserMutationResult.Acknowledged
            },
            mutedReader = object : AuthenticatedRedditMutedUsersReader {
                override suspend fun load(accountStableId: String, rawCommunityName: String, after: String?, username: String?) =
                    AuthenticatedRedditMutedUsersResult.Loaded("subshell", emptyList(), null)
            },
            clientMode = null,
            activeAccountStableId = { "acct" },
            activeAccountUsername = { "me" },
        )
        coordinator.open("acct", "subshell")
        return coordinator
    }

    @Test
    fun aFailedBannedReadSurvivesTheMutedRead() = runBlocking<Unit> {
        bannedRead = {
            AuthenticatedRedditBannedUsersResult.Failed(
                "subshell",
                ModeratorUserManagementFailureKind.TRANSPORT,
                retryable = true,
            )
        }
        val coordinator = userManagement()

        coordinator.load("acct")
        coordinator.loadMuted("acct")

        val state = coordinator.uiState.value
        assertEquals(ModeratorUserManagementFailureKind.TRANSPORT, state.failureKind)
        assertEquals(ModeratorUserManagementFailureSection.BANNED, state.failureSection)
    }

    @Test
    fun anUnconfirmedBanWarningSurvivesOtherListsAndClearsOnTheBannedRead() = runBlocking<Unit> {
        // The ban is acknowledged but the read-back never shows it.
        val coordinator = userManagement()
        coordinator.mutate(
            RedditModeratorUserBanRequest(
                accountStableId = "acct",
                communityName = "subshell",
                username = "target_user",
                banned = true,
            ),
        )
        assertEquals(ModeratorUserManagementFailureKind.OUTCOME_UNKNOWN, coordinator.uiState.value.failureKind)

        coordinator.loadMuted("acct", forceRefresh = true)
        assertEquals(ModeratorUserManagementFailureKind.OUTCOME_UNKNOWN, coordinator.uiState.value.failureKind)

        coordinator.load("acct", forceRefresh = true)
        assertNull(coordinator.uiState.value.failureKind)
    }
}
