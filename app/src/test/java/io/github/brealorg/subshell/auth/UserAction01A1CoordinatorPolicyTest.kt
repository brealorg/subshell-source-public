package io.github.brealorg.subshell.auth

import io.github.brealorg.subshell.data.account.ContentSafetyOperation
import io.github.brealorg.subshell.data.account.ContentSafetyPhase
import io.github.brealorg.subshell.data.account.HiddenHistoryReconciliation
import io.github.brealorg.subshell.data.account.RedditContentReportRequest
import io.github.brealorg.subshell.data.account.RedditPostVisibilityRequest
import io.github.brealorg.subshell.data.account.RedditUserBlockRequest
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedContentSafetyResult
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedContentSafetyWriter
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
