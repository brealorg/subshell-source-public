package app.subshell.prototype.ui.screens

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.backup.PortableModeratorSavedResponse
import app.subshell.prototype.backup.SettingsBackupRepository
import app.subshell.prototype.backup.SnapshotKind
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.ui.SubshellSettings
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (backup04a4): restoring a saved setup that holds moderator responses
 * replaces the device's responses only while "Include saved moderator
 * responses" is on; with it off the current responses stay as they are.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3ModeratorResponsesRestoreComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val application = ApplicationProvider.getApplicationContext<Application>()

    private fun restoreWithResponsesWhile(included: Boolean): List<String> {
        ensureWorkManagerInitialized(application)
        val repository = SettingsBackupRepository(application, appVersion = "test")
        // The setup was saved while the opt-in was on, so it holds the module.
        repository.setModeratorSavedResponsesBackupEnabled(true)
        repository.saveCurrent(
            name = "Mod setup",
            settings = SubshellSettings(),
            moderatorSavedResponses = listOf(
                PortableModeratorSavedResponse("backed", "Backed up", "From the backup.", 1L),
            ),
        )
        repository.setModeratorSavedResponsesBackupEnabled(included)
        val state = SubshellState()
        assertNotNull(state.saveModeratorResponse("Current", "On this device."))

        composeRule.setContent {
            SubshellTheme { BackupRestoreScreen(state = state, repository = repository, onBack = {}) }
        }
        composeRule.waitForIdle()
        // The saved setups sit below the backup switches; bring the card on screen.
        composeRule.waitUntil(10_000) {
            runCatching {
                composeRule.onAllNodes(hasScrollToNodeAction()).onFirst()
                    .performScrollToNode(hasText("Mod setup", substring = true))
            }.isSuccess
        }
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(hasText("Restore") and hasClickAction()).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodes(hasText("Restore") and hasClickAction()).onLast()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(hasText("Restore Mod setup?")).fetchSemanticsNodes().isNotEmpty()
        }
        // The dialog's own Restore is the last one on screen.
        composeRule.onAllNodes(hasText("Restore") and hasClickAction()).onLast()
            .performSemanticsAction(SemanticsActions.OnClick)
        // A restore saves the setup before it first, then applies the backup.
        composeRule.waitUntil(10_000) {
            repository.listSnapshots().any { it.kind == SnapshotKind.SAFETY }
        }
        composeRule.waitForIdle()
        return composeRule.runOnIdle { state.moderatorSavedResponses.map { it.title } }
    }

    @Test
    fun withTheOptInOffARestoreKeepsTheCurrentResponses() {
        assertEquals(listOf("Current"), restoreWithResponsesWhile(included = false))
    }

    @Test
    fun withTheOptInOnARestoreBringsTheBackedUpResponses() {
        assertEquals(listOf("Backed up"), restoreWithResponsesWhile(included = true))
    }
}
