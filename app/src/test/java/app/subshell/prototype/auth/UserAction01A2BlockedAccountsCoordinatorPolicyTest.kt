package app.subshell.prototype.auth

import app.subshell.prototype.data.account.HiddenHistoryReconciliation
import app.subshell.prototype.data.account.RedditBlockedAccount
import app.subshell.prototype.data.account.RedditContentReportRequest
import app.subshell.prototype.data.account.RedditPostVisibilityRequest
import app.subshell.prototype.data.account.RedditUserBlockRequest
import app.subshell.prototype.data.auth.reddit.AuthenticatedBlockedAccountsResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedContentSafetyResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedContentSafetyWriter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserAction01A2BlockedAccountsCoordinatorPolicyTest {
    @Test
    fun refreshAndAppendPublishAccountScopedAuthoritativePages() = runBlocking {
        val requestedCursors = mutableListOf<String?>()
        val writer = object : AuthenticatedContentSafetyWriter {
            override suspend fun setPostHidden(
                request: RedditPostVisibilityRequest,
            ): AuthenticatedContentSafetyResult = error("Unexpected post visibility")

            override suspend fun report(
                request: RedditContentReportRequest,
            ): AuthenticatedContentSafetyResult = error("Unexpected report")

            override suspend fun setUserBlocked(
                request: RedditUserBlockRequest,
            ): AuthenticatedContentSafetyResult =
                AuthenticatedContentSafetyResult.Updated(
                    accountStableId = request.accountStableId,
                    username = request.username,
                    blocked = request.blocked,
                    hiddenHistoryReconciliation = HiddenHistoryReconciliation.NOT_APPLICABLE,
                )

            override suspend fun loadBlockedAccounts(
                accountStableId: String,
                after: String?,
            ): AuthenticatedBlockedAccountsResult {
                requestedCursors += after
                return if (after == null) {
                    AuthenticatedBlockedAccountsResult.Loaded(
                        accountStableId = accountStableId,
                        items = listOf(
                            RedditBlockedAccount(
                                username = "oiz92",
                                accountFullname = "t2_oiz92",
                                relationshipId = "r9_first",
                                blockedAtEpochSeconds = 1_700_846_613L,
                            ),
                        ),
                        nextCursor = "cursor-2",
                    )
                } else {
                    AuthenticatedBlockedAccountsResult.Loaded(
                        accountStableId = accountStableId,
                        items = listOf(
                            RedditBlockedAccount(
                                username = "RaspberrySea9",
                                accountFullname = "t2_raspberry",
                                relationshipId = "r9_second",
                            ),
                        ),
                        nextCursor = null,
                    )
                }
            }
        }
        val coordinator = ContentSafetyCoordinator(
            capability = null,
            writer = writer,
            clientMode = null,
            activeAccountStableId = { "acct" },
            activeAccountUsername = { "current_user" },
        )

        coordinator.refreshBlockedAccounts("acct")
        val first = coordinator.uiState.value.blockedAccounts
        assertTrue(first.loaded)
        assertFalse(first.inProgress)
        assertEquals(listOf("oiz92"), first.items.map { it.username })
        assertEquals("cursor-2", first.nextCursor)

        coordinator.appendBlockedAccounts("acct")
        val second = coordinator.uiState.value.blockedAccounts
        assertEquals(listOf(null, "cursor-2"), requestedCursors)
        assertEquals(listOf("oiz92", "RaspberrySea9"), second.items.map { it.username })
        assertEquals(null, second.nextCursor)
    }
}
