package app.subshell.prototype.ui.screens

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Issue #28: tap jumps to the next top-level comment, long press (or the accessibility action) to the previous. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TopLevelCommentJumpButtonComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tapIsNextAndLongPressIsPrevious() {
        val calls = mutableListOf<String>()
        composeRule.setContent {
            TopLevelCommentJumpButton(onNext = { calls += "next" }, onPrevious = { calls += "previous" })
        }
        val button = composeRule.onNodeWithContentDescription(TOP_LEVEL_COMMENT_JUMP_NEXT_LABEL, useUnmergedTree = true)

        button.performClick()
        button.performTouchInput { longClick() }
        composeRule.waitForIdle()
        assertEquals(listOf("next", "previous"), calls)
    }

    // LOWUI7_LABELS_SAY_WHAT_A_CONTROL_DOES_V1 (OCB-282): a named button whose
    // long-press action is "Previous", offered once (no duplicate custom action).
    @Test
    fun previousIsANamedLongPressActionOfAButton() {
        val calls = mutableListOf<String>()
        composeRule.setContent {
            TopLevelCommentJumpButton(onNext = { calls += "next" }, onPrevious = { calls += "previous" })
        }
        val clickable = composeRule.onNodeWithContentDescription(TOP_LEVEL_COMMENT_JUMP_NEXT_LABEL).fetchSemanticsNode()
        assertEquals(TOP_LEVEL_COMMENT_JUMP_PREVIOUS_LABEL, clickable.config[SemanticsActions.OnLongClick].label)
        assertEquals(TOP_LEVEL_COMMENT_JUMP_NEXT_LABEL, clickable.config[SemanticsActions.OnClick].label)
        assertEquals(true, SemanticsProperties.ContentDescription in clickable.config)
        assertEquals(Role.Button, clickable.config[SemanticsProperties.Role])
        assertEquals(false, SemanticsActions.CustomActions in clickable.config)
        composeRule.runOnUiThread { clickable.config[SemanticsActions.OnLongClick].action!!() }
        assertEquals(listOf("previous"), calls)
    }

    @Test
    fun theTouchTargetIsAtLeast48dp() {
        // UIPOST05: the button is drawn at 40dp but must be tappable at 48dp.
        composeRule.setContent {
            TopLevelCommentJumpButton(onNext = {}, onPrevious = {})
        }
        composeRule.onNodeWithContentDescription(TOP_LEVEL_COMMENT_JUMP_NEXT_LABEL)
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }
}
