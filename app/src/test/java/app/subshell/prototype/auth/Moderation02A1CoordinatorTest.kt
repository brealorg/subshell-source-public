package app.subshell.prototype.auth

import app.subshell.prototype.data.account.RedditModeratorAction
import app.subshell.prototype.data.account.RedditModeratorActionFailureKind
import app.subshell.prototype.data.account.RedditModeratorActionPhase
import app.subshell.prototype.data.account.RedditModeratorActionRequest
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditModerationActionResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditModerationWriter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Moderation02A1CoordinatorTest {
    @Test
    fun successfulSpamSendsPinnedRequestOnceAndReconciles() = runBlocking {
        val writes = mutableListOf<RedditModeratorActionRequest>()
        val events = mutableListOf<String>()
        val coordinator = coordinator(events) { request ->
            writes += request
            AuthenticatedRedditModerationActionResult.Updated()
        }
        coordinator.moderate(request())
        coordinator.retry()
        assertEquals(listOf(request()), writes)
        assertEquals(listOf("optimistic", "reconcile"), events)
        assertEquals(RedditModeratorActionPhase.SUCCEEDED, coordinator.uiState.value.phase)
        assertEquals(1L, coordinator.uiState.value.completionRevision)
    }

    @Test
    fun wrongAccountNeverReachesWriterOrOptimisticProjection() = runBlocking {
        var writes = 0
        val events = mutableListOf<String>()
        val coordinator = coordinator(events, active = { "account-b" }) {
            writes += 1
            AuthenticatedRedditModerationActionResult.Updated()
        }
        coordinator.moderate(request())
        assertEquals(0, writes)
        assertEquals(emptyList<String>(), events)
        assertEquals(RedditModeratorActionFailureKind.ACCOUNT_CHANGED, coordinator.uiState.value.failureKind)
    }

    @Test
    fun unknownOutcomeReconcilesWithoutRollbackOrRetryEvenWithErroneousRetryFlag() = runBlocking {
        var writes = 0
        val events = mutableListOf<String>()
        val coordinator = coordinator(events) {
            writes += 1
            AuthenticatedRedditModerationActionResult.Failed(
                kind = RedditModeratorActionFailureKind.OUTCOME_UNKNOWN,
                retryable = true,
            )
        }
        coordinator.moderate(request())
        coordinator.retry()
        assertEquals(1, writes)
        assertEquals(listOf("optimistic", "reconcile"), events)
        assertEquals(RedditModeratorActionFailureKind.OUTCOME_UNKNOWN, coordinator.uiState.value.failureKind)
        assertFalse(coordinator.uiState.value.retryable)
    }

    @Test
    fun permissionDenialRollsBackWithoutAutomaticRetry() = runBlocking {
        var writes = 0
        val events = mutableListOf<String>()
        val coordinator = coordinator(events) {
            writes += 1
            AuthenticatedRedditModerationActionResult.Failed(
                kind = RedditModeratorActionFailureKind.AUTHORIZATION,
                httpStatusCode = 403,
            )
        }
        coordinator.moderate(request())
        coordinator.retry()
        assertEquals(1, writes)
        assertEquals(listOf("optimistic", "rollback"), events)
        assertEquals(403, coordinator.uiState.value.httpStatusCode)
    }

    @Test
    fun authorizationRequiredPreservesExactSpamRequestWithoutRepeatingWrite() = runBlocking {
        var writes = 0
        val events = mutableListOf<String>()
        val coordinator = coordinator(events) {
            writes += 1
            AuthenticatedRedditModerationActionResult.AuthorizationRequired
        }
        coordinator.moderate(request())
        coordinator.retry()
        assertEquals(1, writes)
        assertEquals(listOf("optimistic", "rollback"), events)
        assertEquals(RedditModeratorActionPhase.AUTHORIZATION_REQUIRED, coordinator.uiState.value.phase)
        assertEquals(request(), coordinator.uiState.value.request)
    }

    @Test
    fun busyRequestsAreDroppedRatherThanQueuedForLaterReplay() = runBlocking {
        withTimeout(2_000) {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val writes = mutableListOf<RedditModeratorActionRequest>()
            val events = mutableListOf<String>()
            val coordinator = coordinator(events) { request ->
                writes += request
                entered.complete(Unit)
                release.await()
                AuthenticatedRedditModerationActionResult.Updated()
            }
            val first = launch { coordinator.moderate(request()) }
            entered.await()
            val second = launch {
                coordinator.moderate(request().copy(thingFullname = "t1_other"))
            }
            second.join()
            assertEquals(listOf(request()), writes)
            assertEquals(RedditModeratorActionPhase.SENDING, coordinator.uiState.value.phase)
            assertEquals(listOf("optimistic"), events)
            release.complete(Unit)
            first.join()
            assertEquals(listOf(request()), writes)
            assertEquals(listOf("optimistic", "reconcile"), events)
        }
    }

    @Test
    fun cancelledWriterReleasesMutationGuard() = runBlocking {
        withTimeout(2_000) {
            val entered = CompletableDeferred<Unit>()
            val hold = CompletableDeferred<Unit>()
            var writes = 0
            val coordinator = coordinator(mutableListOf()) {
                writes += 1
                if (writes == 1) {
                    entered.complete(Unit)
                    hold.await()
                }
                AuthenticatedRedditModerationActionResult.Updated()
            }
            val job = launch { coordinator.moderate(request()) }
            entered.await()
            job.cancelAndJoin()
            coordinator.reset()
            coordinator.moderate(request())
            assertEquals(2, writes)
            assertEquals(RedditModeratorActionPhase.SUCCEEDED, coordinator.uiState.value.phase)
        }
    }

    // LOW10_UNEXPECTED_WRITE_ERROR_IS_UNKNOWN_OUTCOME_V1 (OCB-589)
    @Test
    fun anUnexpectedWriteErrorIsReadBackNotRolledBack() = runBlocking {
        val events = mutableListOf<String>()
        val coordinator = coordinator(events) { error("socket closed after sending") }

        coordinator.moderate(request())

        assertEquals(listOf("optimistic", "reconcile"), events)
        assertEquals(RedditModeratorActionFailureKind.OUTCOME_UNKNOWN, coordinator.uiState.value.failureKind)
    }

    // STEP3 (mod01a2): approving and removing a queue item are each one
    // write with an optimistic change that is then read back from Reddit.
    @Test
    fun approveAndRemoveAreEachOneWriteThatIsReadBack() = runBlocking {
        for (action in listOf(RedditModeratorAction.APPROVE, RedditModeratorAction.REMOVE)) {
            val writes = mutableListOf<RedditModeratorActionRequest>()
            val events = mutableListOf<String>()
            val coordinator = coordinator(events) { request ->
                writes += request
                AuthenticatedRedditModerationActionResult.Updated()
            }

            coordinator.moderate(request().copy(action = action))

            assertEquals(action.name, listOf(request().copy(action = action)), writes)
            assertEquals(action.name, listOf("optimistic", "reconcile"), events)
            assertEquals(action.name, RedditModeratorActionPhase.SUCCEEDED, coordinator.uiState.value.phase)
        }
    }

    // STEP3 (mod01a21): a removal whose reason could not be attached still
    // succeeds, and the state says the reason is missing.
    @Test
    fun aRemovalWhoseReasonFailedSucceedsWithAWarning() = runBlocking {
        val coordinator = coordinator(mutableListOf()) {
            AuthenticatedRedditModerationActionResult.Updated(removalReasonWarning = true)
        }

        coordinator.moderate(request().copy(action = RedditModeratorAction.REMOVE))

        assertEquals(RedditModeratorActionPhase.SUCCEEDED, coordinator.uiState.value.phase)
        assertTrue(coordinator.uiState.value.removalReasonWarning)
    }

    private fun coordinator(
        events: MutableList<String>,
        active: () -> String? = { "account-a" },
        write: suspend (RedditModeratorActionRequest) -> AuthenticatedRedditModerationActionResult,
    ) = ModerationActionCoordinator(
        capability = null,
        writer = object : AuthenticatedRedditModerationWriter {
            override suspend fun mutate(request: RedditModeratorActionRequest) = write(request)
        },
        clientMode = null,
        activeAccountStableId = active,
        onOptimisticApply = { events += "optimistic" },
        onRollback = { events += "rollback" },
        onReconcile = { events += "reconcile" },
    )

    private fun request() = RedditModeratorActionRequest(
        accountStableId = "account-a",
        communityName = "subshell",
        thingFullname = "t3_abc123",
        action = RedditModeratorAction.SPAM,
    )
}
