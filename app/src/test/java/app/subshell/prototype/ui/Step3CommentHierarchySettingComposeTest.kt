package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.backup.SettingsBackupRepository
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.ui.screens.SettingsDestination
import app.subshell.prototype.ui.screens.SettingsScreen
import app.subshell.prototype.ui.screens.settingsSearchResult
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (issue10): Settings > Comments previews a parent comment and one reply
 * (no third level, no reply context, no style choices inside the preview);
 * the Comment hierarchy row shows the chosen name and opens a dialog with the
 * same names, which changes the style; search finds the row.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3CommentHierarchySettingComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val state = SubshellState(initialInteractionSideConfigured = true)

    private fun show() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val repository = SettingsBackupRepository(context, "test")
        composeRule.setContent {
            SubshellTheme {
                SettingsScreen(
                    state = state,
                    backupRepository = repository,
                    onBack = {},
                    initialDestinationName = SettingsDestination.COMMENTS.name,
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun count(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun thePreviewShowsAParentAndOneReply() {
        show()

        assertEquals(1, count("This is the parent comment body."))
        assertEquals(1, count("This is the reply body."))
        assertEquals(0, count("Reply context"))
        // The style is chosen in the row's dialog, not inside the preview.
        assertEquals(0, count("Parent connectors"))
    }

    @Test
    fun theHierarchyRowOpensItsDialogAndTheChoiceChangesTheStyle() {
        show()
        composeRule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("Comment hierarchy"))
        composeRule.waitForIdle()
        // SETTINGSREVIEW01_ROWS_USE_THE_DIALOG_WORDS_V1: the row says what the dialog says.
        assertTrue(count("Guide lines") > 0)
        assertEquals(0, count("Parent connectors"))

        composeRule.onAllNodesWithText("Comment hierarchy").onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertTrue(count("Parent connectors") > 0)
        composeRule.onAllNodesWithText("Parent connectors").onFirst().performClick()
        composeRule.waitForIdle()

        assertEquals(CommentHierarchyStyle.PARENT_CONNECTORS, state.commentHierarchyStyle)
        assertTrue(count("Parent connectors") > 0)
        assertEquals(0, count("Guide lines"))
    }

    @Test
    fun searchFindsTheHierarchyRow() {
        assertTrue("comment-hierarchy-style" in settingsSearchResult("hierarchy").itemKeys)
        assertTrue("comment-hierarchy-style" in settingsSearchResult("parent connectors").itemKeys)
    }
}
