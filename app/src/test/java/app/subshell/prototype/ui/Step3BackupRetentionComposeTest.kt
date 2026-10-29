package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.backup.BackupRetention
import app.subshell.prototype.backup.SettingsBackupRepository
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.ui.screens.BackupRestoreScreen
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction

/**
 * STEP3 (settings02c): a stricter backup limit asks before it deletes, Cancel
 * keeps the old limit, and a looser limit applies without asking.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3BackupRetentionComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    @Test
    fun aStricterLimitAsksFirstAndALooserOneApplies() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val repository = SettingsBackupRepository(context, "test")
        composeRule.setContent {
            SubshellTheme {
                BackupRestoreScreen(state = SubshellState(), repository = repository, onBack = {})
            }
        }
        composeRule.waitForIdle()
        // ISSUE270_BACKUPS_ARE_ONE_LIST_WITH_THREE_TABS_V1: the automatic limit
        // is under its tab. M3225_RETENTION_IS_AN_EXPOSED_DROPDOWN_V1: the field
        // is named by its label.
        val tab = hasText("Automatic", substring = true) and hasClickAction()
        composeRule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(tab)
        composeRule.onNode(tab).performClick()
        composeRule.waitForIdle()
        val chooser = hasText("Keep the newest")
        composeRule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(chooser)

        composeRule.onNode(chooser).performClick()
        composeRule.onAllNodesWithText(BackupRetention.FIVE.label).onLast().performClick()
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithText("Keep only 5 backups?").fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()
        assertEquals(BackupRetention.TEN, repository.automaticHistoryRetention())

        composeRule.onNode(chooser).performClick()
        composeRule.onNodeWithText(BackupRetention.TWENTY.label).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            repository.automaticHistoryRetention() == BackupRetention.TWENTY
        }
        assertEquals(0, composeRule.onAllNodesWithText("Keep only 20 backups?").fetchSemanticsNodes().size)
    }
}
