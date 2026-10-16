package app.subshell.prototype.auth

import app.subshell.prototype.data.account.*
import app.subshell.prototype.data.auth.*
import app.subshell.prototype.data.auth.reddit.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class Moderation02A2SharedWriteGateTest {
    private fun request(action: RedditModeratorAction = RedditModeratorAction.IGNORE_REPORTS) =
        RedditModeratorActionRequest("owner", "norge", "t3_abc", action)
    private fun coordinator(
        events: MutableList<String> = mutableListOf(),
        active: () -> String? = { "owner" },
        capability: AuthCommentWriteCapability? = null,
        write: suspend (RedditModeratorActionRequest) -> AuthenticatedRedditModerationActionResult,
    ) = ModerationActionCoordinator(
        capability, object : AuthenticatedRedditModerationWriter { override suspend fun mutate(request: RedditModeratorActionRequest) = write(request) },
        null, active, { events += "optimistic" }, { events += "rollback" }, { events += "reconcile" },
    )
    @Test fun reportMutationNeverUsesOptimisticContentRemovalOrRollback() = runBlocking {
        val events = mutableListOf<String>()
        val c = coordinator(events) { AuthenticatedRedditModerationActionResult.Updated() }
        c.reportAction(request()) { true }; assertEquals(listOf("reconcile"), events)
    }
    @Test fun legacyEntryPointCannotBypassReportConfirmation() = runBlocking {
        var writes = 0; val c = coordinator { writes++; AuthenticatedRedditModerationActionResult.Updated() }
        c.moderate(request()); assertEquals(0, writes)
        c.reportAction(request()) { false }; assertEquals(0, writes)
    }
    @Test fun reportAndContentWritesShareOneNonQueuingLock() = runBlocking {
        withTimeout(2000) {
            val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            val writes = mutableListOf<RedditModeratorAction>()
            val c = coordinator { req -> writes += req.action; AuthenticatedRedditModerationActionResult.Updated() }
            val first = launch { c.reportAction(request()) { started.complete(Unit); release.await(); true } }
            started.await(); c.moderate(request(RedditModeratorAction.APPROVE))
            val second = c.reportAction(request()) { true }
            assertEquals(RedditModeratorActionFailureKind.ALREADY_MUTATING, (second as AuthenticatedRedditModerationActionResult.Failed).kind)
            release.complete(Unit); first.join(); assertEquals(listOf(RedditModeratorAction.IGNORE_REPORTS), writes)
        }
    }
    @Test fun accountChangedDuringReadBlocksWrite() = runBlocking {
        var owner = "owner"; var writes = 0
        val c = coordinator(active = { owner }) { writes++; AuthenticatedRedditModerationActionResult.Updated() }
        c.reportAction(request()) { owner = "other"; true }; assertEquals(0, writes)
    }
    @Test fun retryCannotResendReportEvenWithErroneousRetryFlag() = runBlocking {
        var writes = 0; val c = coordinator { writes++; AuthenticatedRedditModerationActionResult.Failed(RedditModeratorActionFailureKind.TRANSPORT, retryable = true) }
        c.reportAction(request()) { true }; c.retry(); assertEquals(1, writes); assertFalse(c.uiState.value.retryable)
    }
    @Test fun completedReportAuthorizationNeverResumesWrite() = runBlocking {
        var writes = 0
        val capability = object : AuthCommentWriteCapability {
            override suspend fun beginAuthorization(accountStableId: String) = "unused"
            override fun hasPendingAuthorization() = false
            override fun hasStoredCallback() = true
            override fun cancelPendingAuthorization() = Unit
            override suspend fun completePendingAuthorization() = AuthCommentWriteCapabilityResult.Upgraded("owner", "mod")
            override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult = AuthSessionResult.ReauthenticationRequired
        }
        val c = coordinator(capability = capability) { writes++; AuthenticatedRedditModerationActionResult.AuthorizationRequired }
        c.reportAction(request()) { true }; c.resumeStoredAuthorizationIfPresent(); c.retry()
        assertEquals(1, writes); assertEquals(RedditModeratorActionPhase.IDLE, c.uiState.value.phase)
    }
}
