package app.subshell.prototype.auth

import app.subshell.prototype.data.account.ModeratorUserManagementFailureKind
import app.subshell.prototype.data.account.RedditMutedUser
import app.subshell.prototype.data.account.RedditModeratorUserMuteRequest
import app.subshell.prototype.data.auth.AuthenticatedRedditMutedUsersReader
import app.subshell.prototype.data.auth.AuthenticatedRedditMutedUsersResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratorUserMuteWriter
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratorUserMutationResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class Moderation02A5A2CoordinatorTest {
    @Test
    fun muteUsesFreshPreflightSingleWriteAndAuthoritativeReadback() = runBlocking {
        var muted = false
        var writes = 0
        val coordinator = coordinator(
            reader = reader { _, _, _ ->
                AuthenticatedRedditMutedUsersResult.Loaded(
                    "subshell",
                    if (muted) listOf(RedditMutedUser("target_user")) else emptyList(),
                    null,
                )
            },
            writer = writer {
                writes++
                muted = it.muted
                AuthenticatedRedditModeratorUserMutationResult.Acknowledged
            },
        )
        coordinator.open("acct", "subshell")
        coordinator.loadMuted("acct")
        coordinator.mutateMuted(request(true))
        assertEquals(1, writes)
        assertEquals("target_user", coordinator.uiState.value.verifiedMutedUsername)
        assertEquals(true, coordinator.uiState.value.verifiedMuted)
        assertEquals(
            listOf("target_user"),
            coordinator.uiState.value.mutedUsers.map { it.username },
        )
        assertFalse(coordinator.uiState.value.mutationInProgress)
    }

    @Test
    fun unknownOutcomeNeverBlindRetries() = runBlocking {
        var writes = 0
        val coordinator = coordinator(
            reader = reader { _, _, _ ->
                AuthenticatedRedditMutedUsersResult.Loaded("subshell", emptyList(), null)
            },
            writer = writer {
                writes++
                AuthenticatedRedditModeratorUserMutationResult.Failed(
                    ModeratorUserManagementFailureKind.OUTCOME_UNKNOWN,
                )
            },
        )
        coordinator.open("acct", "subshell")
        coordinator.loadMuted("acct")
        coordinator.mutateMuted(request(true))
        assertEquals(1, writes)
        assertEquals(
            ModeratorUserManagementFailureKind.OUTCOME_UNKNOWN,
            coordinator.uiState.value.failureKind,
        )
        assertFalse(coordinator.uiState.value.failureRetryable)
    }

    @Test
    fun verifiedUnmuteRemovesUserFromListWithoutManualRefresh() = runBlocking {
        var muted = true
        var writes = 0
        val coordinator = coordinator(
            reader = reader { _, _, _ ->
                AuthenticatedRedditMutedUsersResult.Loaded(
                    "subshell",
                    if (muted) listOf(RedditMutedUser("target_user")) else emptyList(),
                    null,
                )
            },
            writer = writer {
                writes++
                muted = it.muted
                AuthenticatedRedditModeratorUserMutationResult.Acknowledged
            },
        )
        coordinator.open("acct", "subshell")
        coordinator.loadMuted("acct")
        assertEquals(
            listOf("target_user"),
            coordinator.uiState.value.mutedUsers.map { it.username },
        )

        coordinator.mutateMuted(request(false))

        assertEquals(1, writes)
        assertEquals("target_user", coordinator.uiState.value.verifiedMutedUsername)
        assertEquals(false, coordinator.uiState.value.verifiedMuted)
        assertEquals(emptyList<String>(), coordinator.uiState.value.mutedUsers.map { it.username })
        assertFalse(coordinator.uiState.value.mutationInProgress)
    }

    private fun coordinator(
        reader: AuthenticatedRedditMutedUsersReader,
        writer: AuthenticatedRedditModeratorUserMuteWriter,
    ) = ModeratorUserManagementCoordinator(
        capability = null,
        reader = null,
        writer = null,
        mutedReader = reader,
        muteWriter = writer,
        clientMode = null,
        activeAccountStableId = { "acct" },
        activeAccountUsername = { "current_user" },
    )

    private fun request(muted: Boolean) = RedditModeratorUserMuteRequest(
        accountStableId = "acct",
        communityName = "subshell",
        username = "target_user",
        muted = muted,
    )

    private fun reader(
        block: suspend (String, String, String?) -> AuthenticatedRedditMutedUsersResult,
    ) = object : AuthenticatedRedditMutedUsersReader {
        override suspend fun load(
            accountStableId: String,
            rawCommunityName: String,
            after: String?,
            username: String?,
        ) = block(accountStableId, rawCommunityName, after)
    }

    private fun writer(
        block: suspend (RedditModeratorUserMuteRequest) ->
            AuthenticatedRedditModeratorUserMutationResult,
    ) = object : AuthenticatedRedditModeratorUserMuteWriter {
        override suspend fun mutate(request: RedditModeratorUserMuteRequest) = block(request)
    }
}
