package app.subshell.prototype.ui

import android.app.Application
import android.content.Context
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.backup.SettingsBackupRepository
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.ui.components.MarkdownText
import app.subshell.prototype.ui.screens.BackupRestoreScreen
import app.subshell.prototype.ui.screens.PostFlairPickerSheet
import app.subshell.prototype.ui.screens.SwipeActionsSettingsSummary
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Issue #219: leftovers of the LOWUI7 pattern. A switch row is one control
 * whose label is its text, a choice says which one is selected, and an
 * expandable control says its state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue219SemanticsComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun role(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    private fun toggle(on: Boolean) = SemanticsMatcher.expectValue(
        SemanticsProperties.ToggleableState,
        if (on) ToggleableState.On else ToggleableState.Off,
    )

    @Test
    fun backupSwitchRowsAreLabelledSwitchesThatToggleFromTheRow() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        context.getSharedPreferences("subshell_backup_preferences", Context.MODE_PRIVATE).edit().clear().commit()
        val repository = SettingsBackupRepository(context, "test")
        composeRule.setContent {
            SubshellTheme {
                BackupRestoreScreen(state = SubshellState(), repository = repository, onBack = {})
            }
        }
        composeRule.waitUntil(timeoutMillis = 60_000) {
            composeRule.onAllNodesWithText("Include muted communities in backup & restore", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        val row = composeRule.onNode(
            hasText("Include muted communities in backup & restore", substring = true) and role(Role.Switch),
        )
        row.assert(toggle(false))
        row.performClick()
        composeRule.waitForIdle()
        row.assert(toggle(true))
        assertEquals(true, repository.communityMutesBackupEnabled())
    }

    @Test
    fun theSwipeActionsMasterRowIsTheSwitch() {
        var enabled = false
        composeRule.setContent {
            SubshellTheme {
                SwipeActionsSettingsSummary(
                    enabled = enabled,
                    expandedTarget = null,
                    postLeftAction = PostSwipeAction.SAVE,
                    postRightAction = PostSwipeAction.UPVOTE,
                    commentLeftAction = CommentSwipeAction.DOWNVOTE,
                    commentRightAction = CommentSwipeAction.UPVOTE,
                    onEnabledChange = { enabled = it },
                    onExpandedTargetChange = {},
                    onPostLeftActionChange = {},
                    onPostRightActionChange = {},
                    onCommentLeftActionChange = {},
                    onCommentRightActionChange = {},
                )
            }
        }
        composeRule.onNode(hasText("Swipe actions") and role(Role.Switch)).performClick()
        assertEquals(true, enabled)
        // The expandable rows say their state instead of "Expand Posts".
        val posts = composeRule.onNode(hasText("Posts") and role(Role.Button))
        assertEquals(
            "Collapsed",
            posts.fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription),
        )
    }

    @Test
    fun theFlairPickerSaysWhichFlairIsSelected() {
        val options = listOf(ComposerFlair("a", "News"), ComposerFlair("b", "Discussion"))
        composeRule.setContent {
            SubshellTheme {
                PostFlairPickerSheet(
                    required = false,
                    options = options,
                    selected = options[1],
                    onSelect = {},
                    onDismiss = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Discussion", useUnmergedTree = false).assertIsSelected()
        composeRule.onNodeWithText("News", useUnmergedTree = false).assertIsNotSelected()
    }

    @Test
    fun aSpoilerSaysWhetherItIsHiddenOrRevealed() {
        composeRule.setContent {
            SubshellTheme { MarkdownText(">!the butler did it!<") }
        }
        val spoiler = composeRule.onNodeWithText("Spoiler · tap to reveal")
        assertEquals(
            "Hidden",
            spoiler.fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription),
        )
        spoiler.performClick()
        composeRule.waitForIdle()
        assertEquals(
            "Revealed",
            composeRule.onNodeWithText("the butler did it").fetchSemanticsNode()
                .config.getOrNull(SemanticsProperties.StateDescription),
        )
    }
}
