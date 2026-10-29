package app.subshell.prototype.ui.screens

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.backup.SettingsBackupRepository
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.theme.SubshellTheme
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE89_BACKUP_SCREEN_READS_OFF_MAIN_V1: the backup screen listed and
 * decoded every snapshot, read the folder status and ran "Stop using this
 * folder" (under the repository monitor a vault sync holds for its whole run)
 * on the main thread, so a tap during a sync froze the app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue89BackupScreenOffMainComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun stopUsingTheFolderDoesNotWaitForARunningSync() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        // A connected folder, as SettingsBackupRepository stores it.
        context.getSharedPreferences("subshell_backup_preferences", Context.MODE_PRIVATE)
            .edit()
            .putString("tree_uri", "content://test.documents/tree/backups")
            .putString("folder_location", "Backups")
            .putLong("last_sync", 1L)
            .commit()
        val repository = SettingsBackupRepository(context, "test")
        composeRule.setContent {
            SubshellTheme {
                BackupRestoreScreen(state = SubshellState(), repository = repository, onBack = {})
            }
        }
        // The folder status is read on IO and the menu appears only after
        // that read; waitForIdle does not wait for Dispatchers.IO, so under
        // load the menu was not there yet. Wait for the menu itself.
        composeRule.waitUntil(timeoutMillis = 60_000) {
            composeRule.onAllNodesWithContentDescription("More backup folder options")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("More backup folder options").performClick()
        composeRule.waitForIdle()

        // A vault sync holds the repository monitor (its writes are @Synchronized).
        val held = CountDownLatch(1)
        val release = CountDownLatch(1)
        val syncFinished = AtomicBoolean(false)
        val sync = thread {
            synchronized(repository) {
                held.countDown()
                // Held until the test releases it (generous: under a loaded
                // full run 3 s could pass before the tap was checked).
                release.await(60, TimeUnit.SECONDS)
                // Set before the monitor is released: whoever waited for it sees it.
                syncFinished.set(true)
            }
        }
        assertTrue(held.await(60, TimeUnit.SECONDS))

        composeRule.onNodeWithText("Stop copying to this folder").performClick()

        // The tap returned while the sync still held the repository.
        assertFalse(syncFinished.get())
        release.countDown()
        sync.join(60_000)
        composeRule.waitForIdle()
    }
}
