package app.subshell.prototype.ui.components

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.text.TextRange
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (messages02c4, 02c4r1), #232: the reply composer's Image and GIF
 * actions open their pickers and Aa swaps them for the formatting actions; in the editor Ctrl+B makes the selection bold, Ctrl+Z
 * undoes it and Ctrl+Shift+Z redoes it, and nothing changes while the reply
 * is locked.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3ReplyComposerEditorComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val picks = mutableListOf<String>()

    private fun show(state: TextFieldState, locked: Boolean = false) {
        composeRule.setContent {
            SubshellTheme {
                CompactReplyComposer(
                    state = state,
                    media = null,
                    placeholder = "Reply",
                    sendEnabled = true,
                    onSend = {},
                    onMediaReceived = {},
                    onMediaRemoved = {},
                    onPickImage = { picks += "image" },
                    onPickGif = { picks += "gif" },
                    mediaEnabled = true,
                    interactionActionsAtStart = false,
                    inputLocked = locked,
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun selected(text: String) = TextFieldState(text, TextRange(0, text.length))

    private fun ctrl(key: Key, shift: Boolean = false) {
        val editor = composeRule.onAllNodes(hasSetTextAction()).onFirst()
        // Key events reach only the focused field.
        editor.performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.waitForIdle()
        editor.performKeyInput {
            withKeyDown(Key.CtrlLeft) {
                if (shift) withKeyDown(Key.ShiftLeft) { pressKey(key) } else pressKey(key)
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun theMediaActionsOpenTheirPickersAndAaSwapsThemForTheFormattingActions() {
        show(TextFieldState())

        // #232 (owner): formatting waits behind Aa, as in Google's Jetchat.
        assertEquals(0, composeRule.onAllNodesWithContentDescription("Bold").fetchSemanticsNodes().size)
        composeRule.onAllNodes(hasContentDescription("Image") and hasClickAction()).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onAllNodes(hasContentDescription("GIF") and hasClickAction()).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(listOf("image", "gif"), picks)

        composeRule.onNode(hasContentDescription("Formatting") and isToggleable()).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        for (label in listOf("Undo", "Bold", "Italic", "Link")) {
            assertEquals(label, 1, composeRule.onAllNodesWithContentDescription(label).fetchSemanticsNodes().size)
        }
        assertEquals(0, composeRule.onAllNodesWithContentDescription("Image").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("Show formatting").fetchSemanticsNodes().size)
    }

    @Test
    fun ctrlBMakesTheSelectionBoldAndCtrlZUndoesIt() {
        val state = selected("word")
        show(state)

        ctrl(Key.B)
        assertEquals("**word**", state.text.toString())

        ctrl(Key.Z)
        assertEquals("word", state.text.toString())

        ctrl(Key.Z, shift = true)
        assertEquals("**word**", state.text.toString())
    }

    @Test
    fun aLockedReplyIgnoresTheShortcuts() {
        val state = selected("word")
        show(state, locked = true)

        ctrl(Key.B)

        assertEquals("word", state.text.toString())
    }
}
