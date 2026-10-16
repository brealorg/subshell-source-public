package app.subshell.prototype.auth

import app.subshell.prototype.data.account.AccountSessionDirectory
import app.subshell.prototype.data.account.AccountSessionDirectorySnapshot
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.TextPostWritePhase
import app.subshell.prototype.data.account.TextPostWriteRequest
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.reddit.AuthenticatedTextPostSubmitFailureKind
import app.subshell.prototype.data.auth.reddit.AuthenticatedTextPostSubmitRequest
import app.subshell.prototype.data.auth.reddit.AuthenticatedTextPostSubmitResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedTextPostSubmitter
import app.subshell.prototype.data.drafts.Crosspost01A1AMemoryPreferences
import app.subshell.prototype.data.drafts.TextPostSubmitAttemptStore
import app.subshell.prototype.data.network.NetworkRateLimit
import app.subshell.prototype.data.network.NetworkResponseMetadata
import app.subshell.prototype.data.network.reddit.RedditSubmittedTextPost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class Crosspost01A1BReconciliationCoordinatorPolicyTest {
    private val account = account("crossposter")
    private val other = account("otherwriter")
    private fun request() = TextPostWriteRequest(accountStableId = account.stableId,
        community = "target", title = "Shared original", text = "", crosspostFullname = "t3_source123")
    private fun success() = AuthenticatedTextPostSubmitResult.Submitted(
        accountStableId = account.stableId,
        post = RedditSubmittedTextPost(fullname = "t3_created123", id = "created123", community = "target",
            title = "Shared original", text = "", permalink = "/r/target/comments/created123/",
            verifiedCrosspostFullname = "t3_source123"),
        metadata = NetworkResponseMetadata(statusCode = 200, receivedAtEpochMs = 1_005_000L,
            rateLimit = NetworkRateLimit(remaining = null, used = null, resetAfterMs = null), retryAfterMs = null),
    )
    private inner class Fixture {
        val prefs = Crosspost01A1AMemoryPreferences()
        val store = TextPostSubmitAttemptStore(prefs.preferences) { 1_000_000L }
        val directory = Directory(account, other)
        val submitter = Reader(success())
        init { store.stage(request()); store.markDispatched(request().accountStableId, request().requestKey) }
        fun coordinator() = TextPostWriteCoordinator(null, submitter, directory,
            OAuthClientMode.REDREADER_COMPATIBILITY, TextPostSubmitAttemptStore(prefs.preferences))
    }

    @Test
    fun recreatedPendingAttemptReadsOnlyAndCompletesTheWrapperOnce() = runBlocking {
        val f = Fixture(); val coordinator = f.coordinator()
        coordinator.restorePendingSubmitState()
        assertEquals(TextPostWritePhase.OUTCOME_UNKNOWN, coordinator.uiState.value.phase)
        coordinator.reconcilePendingSubmit()
        assertEquals(TextPostWritePhase.SUCCEEDED, coordinator.uiState.value.phase)
        assertEquals("t3_created123", coordinator.uiState.value.submittedPost?.fullname)
        assertEquals("t3_source123", coordinator.uiState.value.submittedPost?.crosspostFullname)
        assertEquals("", coordinator.uiState.value.submittedPost?.text)
        assertEquals(1L, coordinator.uiState.value.completionRevision)
        assertNull(f.store.load(account.stableId))
        coordinator.reconcilePendingSubmit()
        assertEquals(1, f.submitter.reads)
        assertEquals(0, f.submitter.writes)
        assertEquals("crossposter", f.submitter.username)
        assertEquals(1_000_000L, f.submitter.dispatchedAt)
        assertEquals(request().requestKey, f.submitter.request?.requestKey)
        assertEquals("t3_source123", f.submitter.request?.crosspostFullname)
    }

    @Test
    fun missingOrWrongParentProofRetainsPendingAndDuplicateGuard() = runBlocking {
        for (proof in listOf(null, "t3_other123")) {
            val f = Fixture()
            f.submitter.result = success().copy(post = success().post.copy(verifiedCrosspostFullname = proof))
            val coordinator = f.coordinator(); coordinator.reconcilePendingSubmit()
            assertEquals(TextPostWritePhase.OUTCOME_UNKNOWN, coordinator.uiState.value.phase)
            assertEquals(0L, coordinator.uiState.value.completionRevision)
            assertNotNull(f.store.load(account.stableId))
            coordinator.submit(request().copy(crosspostFullname = "t3_another"))
            assertEquals(0, f.submitter.writes)
            assertEquals("t3_source123", f.store.load(account.stableId)?.request?.crosspostFullname)
        }
    }

    @Test
    fun accountChangeBeforeReadMakesNoNetworkCall() = runBlocking {
        val f = Fixture(); f.directory.selectAccount(other.stableId)
        val coordinator = f.coordinator(); coordinator.reconcilePendingSubmit()
        // COMPOSER02: attempts are per account; the active account has none to show.
        assertEquals(TextPostWritePhase.IDLE, coordinator.uiState.value.phase)
        assertEquals(0, f.submitter.reads); assertEquals(0, f.submitter.writes)
        assertNotNull(f.store.load(account.stableId))
    }

    @Test
    fun accountChangeDuringReadCannotClearPendingOrReportSuccess() = runBlocking {
        val f = Fixture(); f.submitter.duringRead = { f.directory.selectAccount(other.stableId) }
        val coordinator = f.coordinator(); coordinator.reconcilePendingSubmit()
        assertEquals(TextPostWritePhase.OUTCOME_UNKNOWN, coordinator.uiState.value.phase)
        assertEquals(0L, coordinator.uiState.value.completionRevision)
        assertNotNull(f.store.load(account.stableId))
        assertEquals(1, f.submitter.reads); assertEquals(0, f.submitter.writes)
    }

    @Test
    fun everyFailedReadKeepsTheDispatchedAttemptUnresolved() = runBlocking {
        // COMPOSER02: NOT_POSTED is a verified absence, not a failed read
        // (Composer02PostSubmitAttemptTest).
        for (kind in AuthenticatedTextPostSubmitFailureKind.entries - AuthenticatedTextPostSubmitFailureKind.NOT_POSTED) {
            val f = Fixture(); f.submitter.result = AuthenticatedTextPostSubmitResult.Failed(kind, retryable = true)
            val coordinator = f.coordinator(); coordinator.reconcilePendingSubmit()
            assertEquals(TextPostWritePhase.OUTCOME_UNKNOWN, coordinator.uiState.value.phase)
            assertNotNull(f.store.load(account.stableId)); assertEquals(0, f.submitter.writes)
        }
    }

    @Test
    fun forgedSuccessIdentityOrReplacementContentCannotClearPending() = runBlocking {
        val good = success()
        val invalid = listOf(good.copy(accountStableId = other.stableId),
            good.copy(post = good.post.copy(fullname = "t3_source123", id = "source123")),
            good.copy(post = good.post.copy(community = "wrong")),
            good.copy(post = good.post.copy(title = "wrong")),
            good.copy(post = good.post.copy(text = "source body")),
            good.copy(post = good.post.copy(permalink = "/r/source/comments/source123/")),
            good.copy(post = good.post.copy(remoteImageUri = "https://example.org/source.jpg")))
        for (bad in invalid) {
            val f = Fixture(); f.submitter.result = bad
            val coordinator = f.coordinator(); coordinator.reconcilePendingSubmit()
            assertEquals(TextPostWritePhase.OUTCOME_UNKNOWN, coordinator.uiState.value.phase)
            assertNotNull(f.store.load(account.stableId)); assertEquals(0L, coordinator.uiState.value.completionRevision)
        }
    }

    @Test
    fun cancelledReadPropagatesCancellationAndKeepsPending() = runBlocking {
        val f = Fixture(); f.submitter.duringRead = { throw CancellationException("fixture") }
        try {
            f.coordinator().reconcilePendingSubmit()
            throw AssertionError("Cancellation was swallowed")
        } catch (_: CancellationException) {
            assertNotNull(f.store.load(account.stableId)); assertEquals(0, f.submitter.writes)
        }
    }

    private class Reader(var result: AuthenticatedTextPostSubmitResult) : AuthenticatedTextPostSubmitter {
        var reads = 0; var writes = 0
        var request: AuthenticatedTextPostSubmitRequest? = null
        var username: String? = null; var dispatchedAt: Long? = null
        var duringRead: () -> Unit = {}
        override suspend fun submit(request: AuthenticatedTextPostSubmitRequest): AuthenticatedTextPostSubmitResult {
            writes++; error("Reconciliation must never submit")
        }
        override suspend fun reconcile(request: AuthenticatedTextPostSubmitRequest, username: String,
            dispatchedAtEpochMs: Long): AuthenticatedTextPostSubmitResult {
            reads++; this.request = request; this.username = username; dispatchedAt = dispatchedAtEpochMs
            duringRead(); return result
        }
    }
    private class Directory(vararg accounts: AccountSessionSummary) : AccountSessionDirectory {
        private var current = AccountSessionDirectorySnapshot(accounts.toList(), accounts.first().stableId)
        override fun snapshot() = current
        override fun selectAccount(stableId: String): AccountSessionDirectorySnapshot {
            current = current.copy(activeAccountId = stableId); return current
        }
    }
    private fun account(name: String) = AccountSessionSummary(
        stableId = "reddit:redreader_compatibility:t2_$name", username = name,
        source = AccountSessionSource.AUTHENTICATED, state = AccountSessionState.CONNECTED, providerLabel = "Reddit",
    )
}
