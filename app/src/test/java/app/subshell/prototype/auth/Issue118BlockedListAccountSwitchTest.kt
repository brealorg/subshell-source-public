package app.subshell.prototype.auth

import app.subshell.prototype.data.account.RedditBlockedAccount
import app.subshell.prototype.data.account.RedditContentReportRequest
import app.subshell.prototype.data.account.RedditPostVisibilityRequest
import app.subshell.prototype.data.auth.reddit.AuthenticatedBlockedAccountsResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedContentSafetyResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedContentSafetyWriter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ISSUE118_BLOCKED_LIST_SURVIVES_ACCOUNT_SWITCH_V1: a blocked-list load whose
 * answer arrived after an account switch was dropped with the state left "in
 * progress" for good, and a refresh emptied the list while it ran.
 */
class Issue118BlockedListAccountSwitchTest {
    private val blocked = RedditBlockedAccount(username = "oiz92", accountFullname = "t2_oiz92")

    private class GatedWriter : AuthenticatedContentSafetyWriter {
        var gate: CompletableDeferred<AuthenticatedBlockedAccountsResult>? = null

        override suspend fun setPostHidden(request: RedditPostVisibilityRequest): AuthenticatedContentSafetyResult =
            error("unexpected")

        override suspend fun report(request: RedditContentReportRequest): AuthenticatedContentSafetyResult =
            error("unexpected")

        override suspend fun loadBlockedAccounts(
            accountStableId: String,
            after: String?,
        ): AuthenticatedBlockedAccountsResult = requireNotNull(gate).await()
    }

    @Test
    fun anAnswerAfterAnAccountSwitchDoesNotLeaveTheListLoading() = runBlocking {
        var active = "reddit:a"
        val writer = GatedWriter()
        val coordinator = ContentSafetyCoordinator(
            capability = null,
            writer = writer,
            clientMode = null,
            activeAccountStableId = { active },
        )
        writer.gate = CompletableDeferred(AuthenticatedBlockedAccountsResult.Loaded("reddit:a", listOf(blocked), null))
        coordinator.refreshBlockedAccounts("reddit:a")

        writer.gate = CompletableDeferred()
        val refresh = async(start = CoroutineStart.UNDISPATCHED) { coordinator.refreshBlockedAccounts("reddit:a") }
        assertTrue(coordinator.uiState.value.blockedAccounts.inProgress)
        active = "reddit:b"
        requireNotNull(writer.gate).complete(AuthenticatedBlockedAccountsResult.Loaded("reddit:a", emptyList(), null))
        refresh.await()

        val state = coordinator.uiState.value.blockedAccounts
        assertFalse(state.inProgress)
        assertEquals("reddit:a", state.accountStableId)
        assertEquals(listOf(blocked), state.items)
    }

    @Test
    fun aRefreshKeepsTheListWhileItRuns() = runBlocking {
        val writer = GatedWriter()
        val coordinator = ContentSafetyCoordinator(
            capability = null,
            writer = writer,
            clientMode = null,
            activeAccountStableId = { "reddit:a" },
        )
        writer.gate = CompletableDeferred(AuthenticatedBlockedAccountsResult.Loaded("reddit:a", listOf(blocked), null))
        coordinator.refreshBlockedAccounts("reddit:a")

        writer.gate = CompletableDeferred()
        val refresh = async(start = CoroutineStart.UNDISPATCHED) { coordinator.refreshBlockedAccounts("reddit:a") }

        assertTrue(coordinator.uiState.value.blockedAccounts.inProgress)
        assertEquals(listOf(blocked), coordinator.uiState.value.blockedAccounts.items)
        requireNotNull(writer.gate).complete(AuthenticatedBlockedAccountsResult.Loaded("reddit:a", emptyList(), null))
        refresh.await()
        assertEquals(emptyList<RedditBlockedAccount>(), coordinator.uiState.value.blockedAccounts.items)
    }
}
