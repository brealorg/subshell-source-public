package app.subshell.prototype.auth

import app.subshell.prototype.data.account.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class UserStatus01A1CoordinatorTest {
    private class FakeService : RedditOnlineVisibilityService {
        var reads = 0
        var writes = 0
        var readResult: OnlineVisibilityResult = OnlineVisibilityResult.Known(false)
        var writeResult: OnlineVisibilityWriteResult = OnlineVisibilityWriteResult.Verified(true, true)
        var readWait: CompletableDeferred<Unit>? = null
        var writeWait: CompletableDeferred<Unit>? = null
        override suspend fun read(account: String, isCurrent: () -> Boolean): OnlineVisibilityResult {
            reads++
            readWait?.await()
            return readResult
        }
        override suspend fun change(confirmation: OnlineVisibilityConfirmation, isCurrent: () -> Boolean): OnlineVisibilityWriteResult {
            writes++
            writeWait?.await()
            return writeResult
        }
    }

    @Test fun unknownIsNeverPresentedAsFalse() = runBlocking {
        val fake = FakeService().apply { readWait = CompletableDeferred() }
        val coordinator = OnlineVisibilityCoordinator(this, fake) { "account" }
        assertNull(coordinator.uiState.value.value)
        coordinator.open("account")
        assertEquals(OnlineVisibilityPhase.LOADING, coordinator.uiState.value.phase)
        assertFalse(coordinator.uiState.value.canChange)
        coordinator.propose("account", true)
        assertNull(coordinator.uiState.value.confirmation)
        coordinator.close("account")
    }

    @Test fun openingAndRefreshingNeverWrites() = runBlocking {
        val fake = FakeService()
        val c = OnlineVisibilityCoordinator(this, fake) { "account" }
        c.open("account"); yield()
        assertEquals(false, c.uiState.value.value)
        c.refresh("account"); yield()
        assertEquals(2, fake.reads)
        assertEquals(0, fake.writes)
    }

    @Test fun proposingDoesNotOptimisticallyChangeValueAndCancelDoesNotWrite() = runBlocking {
        val f = FakeService(); val c = OnlineVisibilityCoordinator(this, f) { "account" }
        c.open("account"); yield(); c.propose("account", true)
        assertNotNull(c.uiState.value.confirmation)
        assertEquals(false, c.uiState.value.value)
        assertEquals(0, f.writes)
        c.cancelConfirmation()
        assertNull(c.uiState.value.confirmation)
        assertEquals(0, f.writes)
    }

    @Test fun exactConfirmationIsConsumedBeforeAsyncDispatch() = runBlocking {
        val f = FakeService().apply { writeWait = CompletableDeferred() }
        val c = OnlineVisibilityCoordinator(this, f) { "account" }
        c.open("account"); yield(); c.propose("account", true)
        val confirmation = requireNotNull(c.uiState.value.confirmation)
        c.confirm(confirmation); c.confirm(confirmation); yield()
        assertEquals(1, f.writes)
        assertEquals(OnlineVisibilityPhase.SAVING, c.uiState.value.phase)
        assertNull(c.uiState.value.value)
        assertNull(c.uiState.value.confirmation)
        c.refresh("account"); c.propose("account", false)
        assertEquals(1, f.reads)
        f.writeWait!!.complete(Unit); yield()
        assertEquals(true, c.uiState.value.value)
        assertEquals(OnlineVisibilityNotice.SAVED, c.uiState.value.notice)
    }

    @Test fun forgedAndStaleConfirmationsCannotWrite() = runBlocking {
        val f = FakeService(); val c = OnlineVisibilityCoordinator(this, f) { "account" }
        c.open("account"); yield(); c.propose("account", true)
        val confirmation = requireNotNull(c.uiState.value.confirmation)
        c.confirm(confirmation.copy(accountStableId = "other"))
        c.confirm(confirmation.copy(expectedValue = true))
        c.confirm(confirmation.copy(revision = confirmation.revision + 1))
        assertEquals(0, f.writes)
        c.refresh("account"); yield(); c.confirm(confirmation); yield()
        assertEquals(0, f.writes)
    }

    @Test fun accountSwitchClearsPendingConfirmationAndRejectsOldResult() = runBlocking {
        var active = "account"
        val f = FakeService(); val c = OnlineVisibilityCoordinator(this, f) { active }
        c.open("account"); yield(); c.propose("account", true)
        val confirmation = requireNotNull(c.uiState.value.confirmation)
        active = "other"; c.accountChanged()
        assertNull(c.uiState.value.accountStableId)
        c.confirm(confirmation); yield()
        assertEquals(0, f.writes)
        c.open("other"); yield()
        assertEquals("other", c.uiState.value.accountStableId)
    }

    @Test fun closedScreenCannotReceiveLateRead() = runBlocking {
        val f = FakeService().apply { readWait = CompletableDeferred() }
        val c = OnlineVisibilityCoordinator(this, f) { "account" }
        c.open("account"); yield(); c.close("account")
        f.readWait!!.complete(Unit); yield()
        assertNull(c.uiState.value.value)
        assertEquals(OnlineVisibilityPhase.IDLE, c.uiState.value.phase)
    }

    @Test fun unknownWriteBlocksAnotherChangeUntilAnExplicitRead() = runBlocking {
        val f = FakeService().apply {
            writeResult = OnlineVisibilityWriteResult.Failed(OnlineVisibilityIssue.OUTCOME_UNKNOWN, false, 500)
        }
        val c = OnlineVisibilityCoordinator(this, f) { "account" }
        c.open("account"); yield(); c.propose("account", true)
        c.confirm(requireNotNull(c.uiState.value.confirmation)); yield()
        assertEquals(OnlineVisibilityIssue.OUTCOME_UNKNOWN, c.uiState.value.issue)
        assertNull(c.uiState.value.value)
        assertFalse(c.uiState.value.canChange)
        c.propose("account", true); yield()
        assertEquals(1, f.writes)
        c.refresh("account"); yield()
        assertEquals(1, f.writes)
        assertTrue(c.uiState.value.canChange)
    }

    @Test fun authFailureAndReconnectReopenNeverReplayWrite() = runBlocking {
        val f = FakeService().apply {
            writeResult = OnlineVisibilityWriteResult.Failed(OnlineVisibilityIssue.AUTHORIZATION)
        }
        val c = OnlineVisibilityCoordinator(this, f) { "account" }
        c.open("account"); yield(); c.propose("account", true)
        c.confirm(requireNotNull(c.uiState.value.confirmation)); yield()
        assertEquals(OnlineVisibilityIssue.AUTHORIZATION, c.uiState.value.issue)
        c.close("account"); c.open("account"); yield()
        assertEquals(1, f.writes)
        assertNull(c.uiState.value.confirmation)
    }

    @Test fun missingFieldIsUnavailableNotAnOffSwitch() = runBlocking {
        val f = FakeService().apply { readResult = OnlineVisibilityResult.Unavailable }
        val c = OnlineVisibilityCoordinator(this, f) { "account" }
        c.open("account"); yield()
        assertEquals(OnlineVisibilityPhase.UNAVAILABLE, c.uiState.value.phase)
        assertNull(c.uiState.value.value)
        c.propose("account", true)
        assertNull(c.uiState.value.confirmation)
    }

    @Test fun externallyChangedValueRequiresNewConfirmation() = runBlocking {
        val f = FakeService().apply { writeResult = OnlineVisibilityWriteResult.Changed(true) }
        val c = OnlineVisibilityCoordinator(this, f) { "account" }
        c.open("account"); yield(); c.propose("account", true)
        val confirmation = requireNotNull(c.uiState.value.confirmation)
        c.confirm(confirmation); yield()
        assertEquals(true, c.uiState.value.value)
        assertEquals(OnlineVisibilityIssue.CHANGED_ON_REDDIT, c.uiState.value.issue)
        assertNull(c.uiState.value.confirmation)
        c.confirm(confirmation); yield()
        assertEquals(1, f.writes)
    }

    @Test fun verifiedAfter500DoesNotClaimCleanSaveAcknowledgment() = runBlocking {
        val f = FakeService().apply { writeResult = OnlineVisibilityWriteResult.Verified(true, false) }
        val c = OnlineVisibilityCoordinator(this, f) { "account" }
        c.open("account"); yield(); c.propose("account", true)
        c.confirm(requireNotNull(c.uiState.value.confirmation)); yield()
        assertEquals(true, c.uiState.value.value)
        assertEquals(OnlineVisibilityNotice.CURRENT_VALUE_VERIFIED, c.uiState.value.notice)
    }
}
