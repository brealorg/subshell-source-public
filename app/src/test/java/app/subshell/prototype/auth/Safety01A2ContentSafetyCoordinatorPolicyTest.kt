package app.subshell.prototype.auth

import app.subshell.prototype.data.account.ContentSafetyFailureKind
import app.subshell.prototype.data.account.ContentSafetyOperation
import app.subshell.prototype.data.account.ContentSafetyPhase
import app.subshell.prototype.data.account.HiddenHistoryReconciliation
import app.subshell.prototype.data.account.RedditContentReportRequest
import app.subshell.prototype.data.account.RedditPostVisibilityRequest
import app.subshell.prototype.data.auth.reddit.AuthenticatedContentSafetyResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedContentSafetyWriter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Safety01A2ContentSafetyCoordinatorPolicyTest {
    @Test
    fun successfulVisibilityMutationPublishesAuthoritativeHistoryRefresh() = runBlocking {
        val coordinator = ContentSafetyCoordinator(
            capability = null,
            writer = object : AuthenticatedContentSafetyWriter {
                override suspend fun setPostHidden(
                    request: RedditPostVisibilityRequest,
                ): AuthenticatedContentSafetyResult =
                    AuthenticatedContentSafetyResult.Updated(
                        accountStableId = request.accountStableId,
                        thingFullname = request.postFullname,
                        hidden = request.hidden,
                        hiddenHistoryReconciliation =
                            HiddenHistoryReconciliation.REFRESHED,
                    )

                override suspend fun report(
                    request: RedditContentReportRequest,
                ): AuthenticatedContentSafetyResult =
                    error("Unexpected report")
            },
            clientMode = null,
            activeAccountStableId = { "acct" },
        )

        coordinator.setPostHidden(
            RedditPostVisibilityRequest(
                accountStableId = "acct",
                postFullname = "t3_post1",
                hidden = true,
            ),
        )

        val state = coordinator.uiState.value
        assertEquals(ContentSafetyPhase.SUCCEEDED, state.phase)
        assertEquals(ContentSafetyOperation.SET_POST_HIDDEN, state.operation)
        assertEquals("t3_post1", state.thingFullname)
        assertEquals(true, state.hidden)
        assertEquals(
            HiddenHistoryReconciliation.REFRESHED,
            state.hiddenHistoryReconciliation,
        )
        assertEquals(1L, state.completionRevision)
    }

    @Test
    fun reportReasonRemainsTransientAndPostReportPublishesHiddenSideEffect() = runBlocking {
        val secretReason = "private-report-reason-7a9b"
        var writerReason: String? = null
        val coordinator = ContentSafetyCoordinator(
            capability = null,
            writer = object : AuthenticatedContentSafetyWriter {
                override suspend fun setPostHidden(
                    request: RedditPostVisibilityRequest,
                ): AuthenticatedContentSafetyResult =
                    error("Unexpected visibility mutation")

                override suspend fun report(
                    request: RedditContentReportRequest,
                ): AuthenticatedContentSafetyResult {
                    writerReason = request.reason
                    return AuthenticatedContentSafetyResult.Updated(
                        accountStableId = request.accountStableId,
                        thingFullname = request.thingFullname,
                        hidden = true,
                        hiddenHistoryReconciliation =
                            HiddenHistoryReconciliation.REFRESHED,
                    )
                }
            },
            clientMode = null,
            activeAccountStableId = { "acct" },
        )

        coordinator.report(
            RedditContentReportRequest(
                accountStableId = "acct",
                thingFullname = "t3_post1",
                reason = secretReason,
            ),
        )

        assertEquals(secretReason, writerReason)
        val state = coordinator.uiState.value
        assertEquals(ContentSafetyPhase.SUCCEEDED, state.phase)
        assertEquals(ContentSafetyOperation.REPORT_CONTENT, state.operation)
        assertEquals(true, state.hidden)
        assertFalse(state.toString().contains(secretReason))
        assertEquals(1L, state.completionRevision)
    }

    // STEP3 (safety01a2): the community a report names travels to Reddit and
    // is not kept in the state the screens read.
    @Test
    fun reportCommunityReachesTheWriterButIsNotKeptInTheState() = runBlocking {
        val secretCommunity = "secret-sub-91"
        val writer = RecordingWriter { request ->
            AuthenticatedContentSafetyResult.Updated(
                accountStableId = request.accountStableId,
                thingFullname = request.thingFullname,
                hidden = true,
            )
        }
        val coordinator = coordinator(writer)

        coordinator.report(RedditContentReportRequest("acct", "t3_post1", "Spam", communityName = secretCommunity))

        assertEquals(listOf(secretCommunity), writer.reports.map { it.communityName })
        val state = coordinator.uiState.value
        assertEquals(ContentSafetyPhase.SUCCEEDED, state.phase)
        assertFalse(state.toString().contains(secretCommunity))
    }

    // STEP3 (safety01a2): Retry sends a report again only when Reddit said
    // it may; an outcome Reddit may have stored is never sent twice.
    @Test
    fun retrySendsAgainOnlyAfterARetryableFailure() = runBlocking {
        var next: AuthenticatedContentSafetyResult = AuthenticatedContentSafetyResult.Failed(
            kind = ContentSafetyFailureKind.OUTCOME_UNKNOWN,
            retryable = false,
        )
        val writer = RecordingWriter { next }
        val coordinator = coordinator(writer)

        coordinator.report(RedditContentReportRequest("acct", "t3_post1", "Spam"))
        assertEquals(ContentSafetyPhase.FAILED, coordinator.uiState.value.phase)
        coordinator.retry()
        assertEquals(1, writer.reports.size)

        next = AuthenticatedContentSafetyResult.Failed(
            kind = ContentSafetyFailureKind.HTTP,
            retryable = true,
            statusCode = 429,
        )
        coordinator.report(RedditContentReportRequest("acct", "t3_post2", "Spam"))
        assertEquals(2, writer.reports.size)
        coordinator.retry()
        assertEquals(listOf("t3_post1", "t3_post2", "t3_post2"), writer.reports.map { it.thingFullname })
    }

    // STEP3 (safety01a2): a change asked for by one account is not sent once
    // another account is active.
    @Test
    fun aChangeForAnAccountThatIsNoLongerActiveIsNeverSent() = runBlocking {
        val writer = RecordingWriter { error("Unexpected report") }
        val coordinator = coordinator(writer, active = "other")

        coordinator.report(RedditContentReportRequest("acct", "t3_post1", "Spam"))
        coordinator.setPostHidden(RedditPostVisibilityRequest("acct", "t3_post1", hidden = true))

        assertTrue(writer.reports.isEmpty())
        assertTrue(writer.hides.isEmpty())
        val state = coordinator.uiState.value
        assertEquals(ContentSafetyPhase.FAILED, state.phase)
        assertEquals(ContentSafetyFailureKind.ACCOUNT_CHANGED, state.failureKind)
    }

    private fun coordinator(writer: AuthenticatedContentSafetyWriter, active: String = "acct") =
        ContentSafetyCoordinator(
            capability = null,
            writer = writer,
            clientMode = null,
            activeAccountStableId = { active },
        )

    private class RecordingWriter(
        private val reportAnswer: (RedditContentReportRequest) -> AuthenticatedContentSafetyResult,
    ) : AuthenticatedContentSafetyWriter {
        val reports = mutableListOf<RedditContentReportRequest>()
        val hides = mutableListOf<RedditPostVisibilityRequest>()

        override suspend fun setPostHidden(request: RedditPostVisibilityRequest): AuthenticatedContentSafetyResult {
            hides += request
            return AuthenticatedContentSafetyResult.Updated(request.accountStableId, request.postFullname, hidden = request.hidden)
        }

        override suspend fun report(request: RedditContentReportRequest): AuthenticatedContentSafetyResult {
            reports += request
            return reportAnswer(request)
        }
    }
}
