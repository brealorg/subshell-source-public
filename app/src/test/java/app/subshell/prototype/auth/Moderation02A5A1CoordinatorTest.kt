package app.subshell.prototype.auth

import app.subshell.prototype.data.account.ModeratorUserManagementFailureKind
import app.subshell.prototype.data.account.RedditBannedUser
import app.subshell.prototype.data.account.RedditModeratorUserBanRequest
import app.subshell.prototype.data.auth.AuthenticatedRedditBannedUsersReader
import app.subshell.prototype.data.auth.AuthenticatedRedditBannedUsersResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratorUserMutationResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratorUserWriter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class Moderation02A5A1CoordinatorTest {
    @Test
    fun banUsesFreshPreflightWriteAndAuthoritativeReadback() = runBlocking {
        var banned = false
        var writes = 0
        val coordinator = coordinator(
            reader = reader { _, _, _ ->
                AuthenticatedRedditBannedUsersResult.Loaded(
                    communityName = "subshell",
                    users = if (banned) listOf(RedditBannedUser("target_user")) else emptyList(),
                    nextCursor = null,
                )
            },
            writer = writer { request ->
                writes++
                banned = request.banned
                AuthenticatedRedditModeratorUserMutationResult.Acknowledged
            },
        )
        coordinator.open("acct", "subshell")
        coordinator.load("acct")
        coordinator.mutate(request(banned = true))
        assertEquals(1, writes)
        assertEquals("target_user", coordinator.uiState.value.verifiedUsername)
        assertEquals(true, coordinator.uiState.value.verifiedBanned)
        assertFalse(coordinator.uiState.value.mutationInProgress)
    }

    @Test
    fun outcomeUnknownNeverBlindRetriesAndRequiresReadback() = runBlocking {
        var writes = 0
        val coordinator = coordinator(
            reader = reader { _, _, _ ->
                AuthenticatedRedditBannedUsersResult.Loaded("subshell", emptyList(), null)
            },
            writer = writer {
                writes++
                AuthenticatedRedditModeratorUserMutationResult.Failed(
                    ModeratorUserManagementFailureKind.OUTCOME_UNKNOWN,
                )
            },
        )
        coordinator.open("acct", "subshell")
        coordinator.load("acct")
        coordinator.mutate(request(banned = true))
        assertEquals(1, writes)
        assertEquals(
            ModeratorUserManagementFailureKind.OUTCOME_UNKNOWN,
            coordinator.uiState.value.failureKind,
        )
        assertFalse(coordinator.uiState.value.failureRetryable)
    }

    @Test
    fun accountMismatchAndSelfBanFailClosedBeforeWrite() = runBlocking {
        var writes = 0
        val coordinator = ModeratorUserManagementCoordinator(
            capability = null,
            reader = reader { _, _, _ ->
                AuthenticatedRedditBannedUsersResult.Loaded("subshell", emptyList(), null)
            },
            writer = writer {
                writes++
                AuthenticatedRedditModeratorUserMutationResult.Acknowledged
            },
            clientMode = null,
            activeAccountStableId = { "acct" },
            activeAccountUsername = { "current_user" },
        )
        coordinator.open("other", "subshell")
        coordinator.mutate(request(true).copy(accountStableId = "other"))
        assertEquals(0, writes)

        coordinator.open("acct", "subshell")
        coordinator.load("acct")
        coordinator.mutate(request(true).copy(username = "current_user"))
        assertEquals(0, writes)
    }

    private fun coordinator(
        reader: AuthenticatedRedditBannedUsersReader,
        writer: AuthenticatedRedditModeratorUserWriter,
    ) = ModeratorUserManagementCoordinator(
        capability = null,
        reader = reader,
        writer = writer,
        clientMode = null,
        activeAccountStableId = { "acct" },
        activeAccountUsername = { "current_user" },
    )

    private fun request(banned: Boolean) = RedditModeratorUserBanRequest(
        accountStableId = "acct",
        communityName = "subshell",
        username = "target_user",
        banned = banned,
    )

    private fun reader(
        block: suspend (String, String, String?) -> AuthenticatedRedditBannedUsersResult,
    ) = object : AuthenticatedRedditBannedUsersReader {
        override suspend fun load(
            accountStableId: String,
            rawCommunityName: String,
            after: String?,
            username: String?,
        ) = block(accountStableId, rawCommunityName, after)
    }

    private fun writer(
        block: suspend (RedditModeratorUserBanRequest) -> AuthenticatedRedditModeratorUserMutationResult,
    ) = object : AuthenticatedRedditModeratorUserWriter {
        override suspend fun mutate(request: RedditModeratorUserBanRequest) = block(request)
    }
}
