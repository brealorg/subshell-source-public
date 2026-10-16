package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.ui.screens.CreatePostScreen
import app.subshell.prototype.ui.screens.composerBodyToApply
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * COMPOSER13_BODY_SYNC_ECHO_V1 (ui review round 3 P12): the draft's echo of
 * what the editor wrote must not overwrite text typed since; a body changed
 * elsewhere still reaches the editor.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Composer13BodySyncComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theEchoOfTheEditorsOwnWriteKeepsWhatWasTypedSince() {
        // Typed "a" (written to the draft), then "b" before the draft's echo arrived.
        assertNull(composerBodyToApply(draftBody = "a", editorBody = "ab", lastWrittenByEditor = "a"))
        assertNull(composerBodyToApply(draftBody = "ab", editorBody = "ab", lastWrittenByEditor = "ab"))
        assertEquals(
            "From elsewhere",
            composerBodyToApply(draftBody = "From elsewhere", editorBody = "ab", lastWrittenByEditor = "ab"),
        )
    }

    @Test
    fun typingReachesTheDraftAndABodyChangedElsewhereReachesTheEditor() {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        val state = SubshellState(
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
            initialInteractionSideConfigured = true,
        )
        state.openPostComposer()
        state.updatePostComposerDraft(state.postComposerDraft.copy(community = "subshell", title = "Title", body = "Start"))
        composeRule.setContent { SubshellTheme { CreatePostScreen(state = state) } }
        composeRule.waitForIdle()

        composeRule.onNode(hasSetTextAction() and hasText("Start")).performTextInput(" typed")
        composeRule.waitForIdle()
        assertEquals("Start typed", state.postComposerDraft.body)

        composeRule.runOnIdle {
            state.updatePostComposerDraft(state.postComposerDraft.copy(body = "From elsewhere"))
        }
        composeRule.waitForIdle()
        assertEquals(1, composeRule.onAllNodesWithText("From elsewhere").fetchSemanticsNodes().size)
        assertEquals("From elsewhere", state.postComposerDraft.body)
    }
}
