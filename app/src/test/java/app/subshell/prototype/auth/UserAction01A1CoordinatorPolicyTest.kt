package app.subshell.prototype.auth

import app.subshell.prototype.data.account.ContentSafetyOperation
import app.subshell.prototype.data.account.ContentSafetyPhase
import app.subshell.prototype.data.account.HiddenHistoryReconciliation
import app.subshell.prototype.data.account.RedditContentReportRequest
import app.subshell.prototype.data.account.RedditPostVisibilityRequest
import app.subshell.prototype.data.account.RedditUserBlockRequest
import app.subshell.prototype.data.auth.reddit.AuthenticatedContentSafetyResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedContentSafetyWriter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class UserAction01A1CoordinatorPolicyTest {
    @Test
    fun successfulBlockAndUnblockPublishTargetStateWithoutPersistingReasonData() = runBlocking {
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
                    hiddenHistoryReconciliation =
                        HiddenHistoryReconciliation.NOT_APPLICABLE,
                )
        }
        val coordinator = ContentSafetyCoordinator(
            capability = null,
            writer = writer,
            clientMode = null,
            activeAccountStableId = { "acct" },
            activeAccountUsername = { "current_user" },
        )

        coordinator.setUserBlocked(
            RedditUserBlockRequest("acct", "target_user", blocked = true),
        )
        assertEquals(ContentSafetyPhase.SUCCEEDED, coordinator.uiState.value.phase)
        assertEquals(
            ContentSafetyOperation.SET_USER_BLOCKED,
            coordinator.uiState.value.operation,
        )
        assertEquals("target_user", coordinator.uiState.value.username)
        assertEquals(true, coordinator.uiState.value.blocked)
        assertEquals(1L, coordinator.uiState.value.completionRevision)

        coordinator.setUserBlocked(
            RedditUserBlockRequest("acct", "target_user", blocked = false),
        )
        assertEquals(false, coordinator.uiState.value.blocked)
        assertEquals(2L, coordinator.uiState.value.completionRevision)
    }
}
