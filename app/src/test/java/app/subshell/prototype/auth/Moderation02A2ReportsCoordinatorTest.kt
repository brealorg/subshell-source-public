package app.subshell.prototype.auth

import app.subshell.prototype.data.account.*
import app.subshell.prototype.data.auth.*
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditModerationActionResult
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class Moderation02A2ReportsCoordinatorTest {
    private val target = ModeratorReportTarget("t3_abc", "norge")
    private fun item(ignored: Boolean? = false, allowed: Boolean? = true, id: String = "t3_abc") = RedditModeratorQueueItem(
        id, RedditModeratorQueueKind.POST, "norge", "author", "Summary",
        reportDetails = RedditModeratorReportDetails(ignored = ignored, canModerate = allowed),
    )
    private fun loaded(ignored: Boolean? = false, allowed: Boolean? = true) = AuthenticatedRedditReportTargetResult.Loaded(item(ignored, allowed))
    private class Loader(val read: suspend () -> AuthenticatedRedditReportTargetResult) : AuthenticatedRedditModerationLoader {
        override suspend fun loadReportTarget(accountStableId: String, target: ModeratorReportTarget) = read()
        override suspend fun loadModeratorCommunities(accountStableId: String) = AuthenticatedRedditModeratedCommunitiesResult.Loaded(setOf("norge"))
        override suspend fun loadModQueue(accountStableId: String, rawCommunityName: String, after: String?) = error("Report read must not use the queue")
        override suspend fun resolveTargetPost(accountStableId: String, postId: String) = AuthenticatedRedditModerationTargetResult.NotFound
    }
    private fun coordinator(
        read: suspend () -> AuthenticatedRedditReportTargetResult,
        active: () -> String? = { "owner" },
        write: suspend (RedditModeratorActionRequest) -> AuthenticatedRedditModerationActionResult,
    ) = ModeratorReportsCoordinator(Loader(read), active) { request, verify ->
        if (verify()) write(request) else AuthenticatedRedditModerationActionResult.Failed(RedditModeratorActionFailureKind.VALIDATION)
    }
    private suspend fun opened(c: ModeratorReportsCoordinator): ModeratorReportConfirmation {
        c.open("owner", target); c.refresh("owner")
        return requireNotNull(reportConfirmationOrNull(c.uiState.value, "owner"))
    }
    @Test fun ignoreThenUnignoreRequiresFreshReadAndNeverDropsTarget() = runBlocking {
        var ignored = false; var reads = 0; val writes = mutableListOf<RedditModeratorAction>()
        val c = coordinator(read = { reads++; loaded(ignored) }) { request ->
            writes += request.action; ignored = requireNotNull(request.action.desiredReportsIgnored())
            AuthenticatedRedditModerationActionResult.Updated()
        }
        val first = opened(c); c.confirm(first); c.confirm(first)
        assertEquals(listOf(RedditModeratorAction.IGNORE_REPORTS), writes)
        assertEquals(3, reads); assertEquals(ModeratorReportsNotice.VERIFIED, c.uiState.value.notice)
        c.dismiss(); c.open("owner", target); c.refresh("owner")
        val undo = requireNotNull(reportConfirmationOrNull(c.uiState.value, "owner"))
        assertEquals(RedditModeratorAction.UNIGNORE_REPORTS, undo.action)
        c.confirm(undo)
        assertEquals(false, c.uiState.value.item?.reportDetails?.ignored)
        assertEquals(listOf(target), c.uiState.value.recentTargets)
    }
    @Test fun unknownOrMissingReadbackIsNotPromotedToSuccess() = runBlocking {
        var reads = 0
        val c = coordinator(read = { reads++; if (reads < 3) loaded() else AuthenticatedRedditReportTargetResult.NotFound }) { AuthenticatedRedditModerationActionResult.Updated() }
        c.confirm(opened(c))
        assertEquals(ModeratorReportsNotice.NOT_CONFIRMED, c.uiState.value.notice)
        assertNull(c.uiState.value.item); assertNull(reportConfirmationOrNull(c.uiState.value, "owner"))
    }
    @Test fun successAckWithUnchangedServerStateIsNotConfirmed() = runBlocking {
        val c = coordinator(read = { loaded() }) { AuthenticatedRedditModerationActionResult.Updated() }
        c.confirm(opened(c)); assertEquals(ModeratorReportsNotice.NOT_CONFIRMED, c.uiState.value.notice)
        assertEquals(false, c.uiState.value.item?.reportDetails?.ignored)
    }
    @Test fun permissionLossOrStateChangeBeforeWritePreventsDispatch() = runBlocking {
        listOf(loaded(allowed = false), loaded(ignored = true), loaded(ignored = null)).forEach { changed ->
            var reads = 0; var writes = 0
            val c = coordinator(read = { if (++reads == 1) loaded() else changed }) { writes++; AuthenticatedRedditModerationActionResult.Updated() }
            c.confirm(opened(c)); assertEquals(0, writes); assertFalse(c.uiState.value.mutating)
        }
    }
    @Test fun authorizationRequiredDoesNotReplayOrKeepActionEnabled() = runBlocking {
        var writes = 0
        val c = coordinator(read = { loaded() }) { writes++; AuthenticatedRedditModerationActionResult.AuthorizationRequired }
        val confirmation = opened(c); c.confirm(confirmation); c.confirm(confirmation)
        assertEquals(1, writes); assertEquals(ModeratorReportsNotice.AUTHORIZATION_REQUIRED, c.uiState.value.notice)
        assertNull(reportConfirmationOrNull(c.uiState.value, "owner"))
    }
    @Test fun unknownOutcomeReadsBackWithoutResending() = runBlocking {
        var reads = 0; var writes = 0
        val c = coordinator(read = { loaded(ignored = ++reads >= 3) }) { writes++; AuthenticatedRedditModerationActionResult.Failed(RedditModeratorActionFailureKind.OUTCOME_UNKNOWN, retryable = true) }
        val confirmation = opened(c); c.confirm(confirmation); c.confirm(confirmation)
        assertEquals(1, writes); assertEquals(ModeratorReportsNotice.VERIFIED_AFTER_UNKNOWN, c.uiState.value.notice)
    }
    @Test fun accountSwitchDiscardsLateReadAndSessionHistory() = runBlocking {
        var active = "owner"
        val gate = CompletableDeferred<Unit>(); val started = CompletableDeferred<Unit>()
        val c = coordinator(active = { active }, read = { started.complete(Unit); gate.await(); loaded() }) { error("No write") }
        c.open("owner", target)
        val job = launch { c.refresh("owner") }; started.await()
        active = "other"; c.accountChanged(active); gate.complete(Unit); job.join()
        assertEquals("other", c.uiState.value.accountStableId); assertNull(c.uiState.value.item)
        assertEquals(emptyList<Any>(), c.uiState.value.recentTargets); assertFalse(c.uiState.value.visible)
    }
    @Test fun staleConfirmationAfterRefreshOrTargetChangeCannotWrite() = runBlocking {
        var writes = 0
        val c = coordinator(read = { loaded() }) { writes++; AuthenticatedRedditModerationActionResult.Updated() }
        val old = opened(c); c.refresh("owner"); c.confirm(old)
        assertEquals(0, writes)
        c.open("owner", ModeratorReportTarget("t3_other", "norge")); c.confirm(old)
        assertEquals(0, writes)
    }
    @Test fun rapidConfirmationCannotBecomeQueuedSecondWrite() = runBlocking {
        withTimeout(2000) {
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>(); var writes = 0
            val c = coordinator(read = { loaded() }) { writes++; entered.complete(Unit); release.await(); AuthenticatedRedditModerationActionResult.Updated() }
            val confirmation = opened(c)
            val first = launch { c.confirm(confirmation) }; entered.await()
            val second = launch { c.confirm(confirmation) }; second.join(); release.complete(Unit); first.join()
            assertEquals(1, writes)
        }
    }
    @Test fun cancelledWriteDoesNotLeaveBusyStateOrRetryToken() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val c = coordinator(read = { loaded() }) { entered.complete(Unit); awaitCancellation() }
        val confirmation = opened(c); val job = launch { c.confirm(confirmation) }; entered.await(); job.cancelAndJoin()
        assertFalse(c.uiState.value.busy); assertNull(reportConfirmationOrNull(c.uiState.value, "owner"))
        assertEquals(ModeratorReportsNotice.NOT_CONFIRMED, c.uiState.value.notice)
    }
    @Test fun wrongTargetFromLoaderCannotEnableAction() = runBlocking {
        val c = coordinator(read = { AuthenticatedRedditReportTargetResult.Loaded(item(id = "t3_wrong")) }) { error("No write") }
        c.open("owner", target); c.refresh("owner")
        assertNull(reportConfirmationOrNull(c.uiState.value, "owner")); assertEquals(ModeratorReportsNotice.NOT_FOUND, c.uiState.value.notice)
    }
}
