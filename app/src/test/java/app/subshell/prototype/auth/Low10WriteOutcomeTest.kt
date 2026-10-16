package app.subshell.prototype.auth

import app.subshell.prototype.data.account.MessageReplyChannel
import app.subshell.prototype.data.account.MessageReplyFailureKind
import app.subshell.prototype.data.account.MessageReplyPhase
import app.subshell.prototype.data.account.MessageReplyRequest
import app.subshell.prototype.data.account.ModeratorUserManagementFailureKind
import app.subshell.prototype.data.account.RedditBannedUser
import app.subshell.prototype.data.account.RedditModeratorUserBanRequest
import app.subshell.prototype.data.auth.AuthenticatedRedditBannedUsersReader
import app.subshell.prototype.data.auth.AuthenticatedRedditBannedUsersResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratorUserMutationResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratorUserWriter
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditMessageReplyResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LOW10: write outcomes that were misread: an invalid reply replaced one still
 * sending (OCB-588), re-banning with new terms sent nothing (OCB-429), and a
 * failed status read lost its kind (OCB-428).
 */
class Low10WriteOutcomeTest {
    @Test
    fun anInvalidReplyLeavesTheOneStillSendingAlone() {
        val gate = CompletableDeferred<AuthenticatedRedditMessageReplyResult>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val coordinator = MessageReplyCoordinator(
            messageReplyWriter = { gate.await() },
            loadRedditInbox = { _, _ -> },
            scope = scope,
        )
        coordinator.sendRedditMessageReply(reply(text = "hello"))
        assertEquals(MessageReplyPhase.SENDING, coordinator.messageReplyUiState.value.phase)

        coordinator.sendRedditMessageReply(reply(text = ""))
        assertEquals(MessageReplyPhase.SENDING, coordinator.messageReplyUiState.value.phase)

        gate.complete(AuthenticatedRedditMessageReplyResult.Failed(MessageReplyFailureKind.MEDIA_UNAVAILABLE, retryable = true))
        assertEquals(MessageReplyFailureKind.MEDIA_UNAVAILABLE, coordinator.messageReplyUiState.value.failureKind)
        scope.cancel()
    }

    @Test
    fun reBanningWithNewTermsSendsThem() = runBlocking<Unit> {
        var writes = 0
        val coordinator = userManagement(
            read = { _ -> AuthenticatedRedditBannedUsersResult.Loaded("subshell", listOf(RedditBannedUser("target_user")), null) },
            onWrite = { writes++ },
        )
        coordinator.mutate(ban(banReason = "spam"))
        assertEquals(1, writes)

        coordinator.mutate(ban())
        assertEquals("no new terms: nothing sent", 1, writes)
    }

    @Test
    fun aFailedStatusReadKeepsItsKind() = runBlocking<Unit> {
        val coordinator = userManagement(
            read = { _ ->
                AuthenticatedRedditBannedUsersResult.Failed(
                    "subshell",
                    ModeratorUserManagementFailureKind.TRANSPORT,
                    retryable = true,
                )
            },
            onWrite = {},
        )
        coordinator.mutate(ban())

        val state = coordinator.uiState.value
        assertEquals(ModeratorUserManagementFailureKind.TRANSPORT, state.failureKind)
        assertTrue(state.failureRetryable)
    }

    private fun userManagement(
        read: (String?) -> AuthenticatedRedditBannedUsersResult,
        onWrite: () -> Unit,
    ): ModeratorUserManagementCoordinator {
        val coordinator = ModeratorUserManagementCoordinator(
            capability = null,
            reader = object : AuthenticatedRedditBannedUsersReader {
                override suspend fun load(accountStableId: String, rawCommunityName: String, after: String?, username: String?) =
                    read(username)
            },
            writer = object : AuthenticatedRedditModeratorUserWriter {
                override suspend fun mutate(request: RedditModeratorUserBanRequest): AuthenticatedRedditModeratorUserMutationResult {
                    onWrite()
                    return AuthenticatedRedditModeratorUserMutationResult.Acknowledged
                }
            },
            clientMode = null,
            activeAccountStableId = { "acct" },
            activeAccountUsername = { "me" },
        )
        coordinator.open("acct", "subshell")
        return coordinator
    }

    private fun ban(banReason: String? = null) = RedditModeratorUserBanRequest(
        accountStableId = "acct",
        communityName = "subshell",
        username = "target_user",
        banned = true,
        banReason = banReason,
    )

    private fun reply(text: String) = MessageReplyRequest(
        accountStableId = "acct",
        inboxItemId = "t4_item",
        parentFullname = "t4_item",
        channel = MessageReplyChannel.PRIVATE_MESSAGE_THREAD,
        text = text,
    )
}
