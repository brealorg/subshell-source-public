package app.subshell.prototype.auth

import app.subshell.prototype.data.account.TextPostWriteRequest
import app.subshell.prototype.data.auth.reddit.AuthenticatedComposerPolicyLoader
import app.subshell.prototype.data.auth.reddit.AuthenticatedComposerPolicyResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedCrosspostSourceLoader
import app.subshell.prototype.data.auth.reddit.CrosspostSourceLoadResult
import app.subshell.prototype.ui.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class Crosspost01A1ECreateCoordinatorPolicyTest {
    private class Fixture {
        var active: String? = "owner"
        var available = true
        var time = 1_000L
        var sourceReads = 0
        var policyReads = 0
        val writes = mutableListOf<TextPostWriteRequest>()
        var evidence = CrosspostSourceEvidence("t3_source123", "source", "author", "Original title", false, false)
        var sourceResult: CrosspostSourceLoadResult? = null
        var policy = crosspost01A1CPolicy()
        var duringSource: suspend () -> Unit = {}
        var duringPolicy: suspend () -> Unit = {}
        val coordinator = CrosspostCreateCoordinator(
            sourceLoader = AuthenticatedCrosspostSourceLoader { account, fullname ->
                assertEquals("owner", account); assertEquals("t3_source123", fullname)
                sourceReads++; duringSource()
                sourceResult ?: CrosspostSourceLoadResult.Available(evidence)
            },
            policyLoader = AuthenticatedComposerPolicyLoader { account, community ->
                assertEquals("owner", account); assertEquals("target", community)
                policyReads++; duringPolicy()
                AuthenticatedComposerPolicyResult.Loaded(policy)
            },
            activeAccountId = { active }, writeAvailable = { available },
            submit = { writes += it }, nowMs = { time },
        )
        val draft = crosspost01A1CDraft()
        suspend fun review() = coordinator.review(draft)
        suspend fun publish(d: PostComposerDraft = draft, token: Long = coordinator.uiState.value.revision) = coordinator.publish(d, token)
    }

    @Test fun reviewReadsSourceAndPolicyButNeverPublishes() = runBlocking {
        val f = Fixture(); f.review()
        assertEquals(CrosspostReviewPhase.READY, f.coordinator.uiState.value.phase)
        assertEquals(1, f.sourceReads); assertEquals(1, f.policyReads); assertTrue(f.writes.isEmpty())
    }
    @Test fun publishWithoutAnExplicitReviewCannotDispatch() = runBlocking {
        val f = Fixture(); f.publish(); assertEquals(0, f.sourceReads); assertTrue(f.writes.isEmpty())
    }
    @Test fun explicitPublishRechecksBothReadsAndDispatchesTheNativeRequestOnce() = runBlocking {
        val f = Fixture(); f.review(); val token = f.coordinator.uiState.value.revision
        f.publish(token = token); f.publish(token = token)
        assertEquals(2, f.sourceReads); assertEquals(2, f.policyReads); assertEquals(1, f.writes.size)
        assertEquals("t3_source123", f.writes.single().crosspostFullname)
        assertEquals("target", f.writes.single().community); assertEquals("", f.writes.single().text)
        assertEquals(CrosspostReviewPhase.DISPATCHED, f.coordinator.uiState.value.phase)
    }
    @Test fun editingAnyReviewedIdentityRequiresNewReview() = runBlocking {
        val changes: List<(PostComposerDraft) -> PostComposerDraft> = listOf(
            { it.copy(title = "Changed") }, { it.copy(community = "other") },
            { it.copy(accountStableId = "other") }, { it.copy(sendReplies = false) },
            { it.copy(crosspostSource = it.crosspostSource!!.copy(fullname = "t3_other")) },
        )
        for (change in changes) {
            val f = Fixture(); f.review(); f.publish(change(f.draft))
            assertTrue(f.writes.isEmpty()); assertEquals(1, f.sourceReads)
        }
    }

    @Test fun expiredAndReversedClockReviewsCannotDispatch() = runBlocking {
        for (time in listOf(999L, 61_001L)) {
            val f = Fixture(); f.review(); f.time = time; f.publish()
            assertTrue(f.writes.isEmpty()); assertEquals(CrosspostReviewPhase.FAILED, f.coordinator.uiState.value.phase)
        }
    }
    @Test fun sourceDisappearingAfterReviewBlocksPublish() = runBlocking {
        val f = Fixture(); f.review(); f.sourceResult = CrosspostSourceLoadResult.Unavailable; f.publish()
        assertTrue(f.writes.isEmpty()); assertEquals(1, f.policyReads)
    }
    @Test fun destinationRejectingCrosspostsAfterReviewBlocksPublish() = runBlocking {
        val f = Fixture(); f.review()
        f.policy = f.policy.copy(crosspostDestination = f.policy.crosspostDestination!!.copy(allowCrossposts = false))
        f.publish(); assertTrue(f.writes.isEmpty()); assertEquals(2, f.policyReads)
    }
    @Test fun sourceBecomingNsfwRequiresUserToEnableNsfwAndReviewAgain() = runBlocking {
        val f = Fixture(); f.review(); f.evidence = f.evidence.copy(nsfw = true); f.publish()
        assertTrue(f.writes.isEmpty()); assertTrue(f.coordinator.uiState.value.message!!.contains("NSFW"))
    }
    @Test fun accountChangeDuringSourceReadCannotApproveReview() = runBlocking {
        val f = Fixture(); f.duringSource = { f.active = "other" }; f.review()
        assertEquals(0, f.policyReads); assertTrue(f.writes.isEmpty())
        assertEquals(CrosspostReviewPhase.FAILED, f.coordinator.uiState.value.phase)
    }
    @Test fun accountChangeDuringConfirmPolicyReadCannotDispatch() = runBlocking {
        val f = Fixture(); f.review(); f.duringPolicy = { f.active = "other" }; f.publish()
        assertTrue(f.writes.isEmpty())
    }
    @Test fun invalidationDuringConfirmCancelsPendingReadNotBypassingIt() = runBlocking {
        val f = Fixture(); f.review(); f.duringSource = { f.coordinator.invalidateReview() }; f.publish()
        assertTrue(f.writes.isEmpty()); assertEquals(CrosspostReviewPhase.IDLE, f.coordinator.uiState.value.phase)
    }
    @Test fun staleReadCompletionCannotReplaceNewerReview() = runBlocking {
        val f = Fixture(); val started = CompletableDeferred<Unit>(); val resume = CompletableDeferred<Unit>()
        f.duringSource = { started.complete(Unit); resume.await() }
        val old = launch { f.review() }; started.await()
        f.coordinator.invalidateReview(); f.duringSource = {}; f.review()
        val revision = f.coordinator.uiState.value.revision
        resume.complete(Unit); old.join()
        assertEquals(revision, f.coordinator.uiState.value.revision)
        assertEquals(CrosspostReviewPhase.READY, f.coordinator.uiState.value.phase)
        assertEquals(1, f.policyReads); assertTrue(f.writes.isEmpty())
    }
    @Test fun pendingWriterOrWrongOwnerPreventsEvenReviewReads() = runBlocking {
        val f = Fixture(); f.available = false; f.review(); assertEquals(0, f.sourceReads)
        val other = Fixture(); other.active = "different"; other.review(); assertEquals(0, other.sourceReads)
    }
    @Test fun writerBecomingBusyAfterReviewBlocksPublish() = runBlocking {
        val f = Fixture(); f.review(); f.available = false; f.publish()
        assertTrue(f.writes.isEmpty()); assertEquals(1, f.sourceReads)
    }
    @Test fun authorizationResultNeverAutomaticallyPublishes() = runBlocking {
        val f = Fixture(); f.sourceResult = CrosspostSourceLoadResult.AuthorizationRequired; f.review(); f.publish()
        assertTrue(f.writes.isEmpty())
        f.sourceResult = null; f.review(); assertTrue(f.writes.isEmpty())
        f.publish(); assertEquals(1, f.writes.size)
    }
    @Test fun cancellationPropagatesAndClearsEphemeralReview() = runBlocking {
        val f = Fixture(); f.duringSource = { throw CancellationException("test") }
        val result = runCatching { f.review() }
        assertTrue(result.exceptionOrNull() is CancellationException)
        assertEquals(CrosspostReviewPhase.IDLE, f.coordinator.uiState.value.phase); assertTrue(f.writes.isEmpty())
    }
    @Test fun readExceptionCannotTurnIntoPublishOrReady() = runBlocking {
        val f = Fixture(); f.duringSource = { error("read failed") }; f.review()
        assertEquals(CrosspostReviewPhase.FAILED, f.coordinator.uiState.value.phase); assertTrue(f.writes.isEmpty())
    }
    @Test fun noConfiguredSourceReaderFailsClosed() = runBlocking {
        var writes = 0
        val c = CrosspostCreateCoordinator(null, null, { "owner" }, { true }, { writes++ })
        c.review(crosspost01A1CDraft()); assertEquals(CrosspostReviewPhase.FAILED, c.uiState.value.phase)
        assertEquals(0, writes)
    }
}
