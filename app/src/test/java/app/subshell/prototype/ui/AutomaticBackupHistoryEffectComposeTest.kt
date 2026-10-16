package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.backup.SettingsBackupRepository
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.ui.app.AutomaticBackupHistoryEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The automatic backup history in its own file, against the real store:
 * the portable state is recorded once on entry, and a portable change is
 * recorded again only after the 30 s debounce.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class AutomaticBackupHistoryEffectComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun thePortableStateIsRecordedOnEntryAndAChangeAfterTheDebounce() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val repository = SettingsBackupRepository(context, appVersion = "test")
        val state = SubshellState()
        assertFalse(state.reduceMotion)
        composeRule.mainClock.autoAdvance = false

        composeRule.setContent {
            AutomaticBackupHistoryEffect(state = state, backupRepository = repository)
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitUntil(timeoutMillis = 5_000) { repository.listSnapshots().isNotEmpty() }
        assertFalse(repository.listSnapshots().first().settings.reduceMotion)
        val afterEntry = repository.listSnapshots().size
        // Let the effect return from the entry write and subscribe to
        // snapshotFlow before changing state (its first emission is dropped).
        repeat(3) {
            Thread.sleep(100)
            composeRule.mainClock.advanceTimeByFrame()
        }

        state.updateReduceMotion(true)
        // A write outside composition reaches snapshotFlow through the global
        // apply notification, which the paused test clock does not send.
        Snapshot.sendApplyNotifications()
        composeRule.mainClock.advanceTimeBy(10_000L)
        Thread.sleep(200)
        assertEquals(afterEntry, repository.listSnapshots().size)

        composeRule.mainClock.advanceTimeBy(25_000L)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            repository.listSnapshots().any { snapshot -> snapshot.settings.reduceMotion }
        }
        assertTrue(repository.listSnapshots().size > afterEntry)
    }
}
