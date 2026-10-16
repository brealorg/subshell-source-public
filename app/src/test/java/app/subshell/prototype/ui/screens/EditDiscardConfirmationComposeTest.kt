package app.subshell.prototype.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.PostEditUiState
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.components.editHasUnsavedChanges
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * COMPOSE05: the edit surfaces keep no draft, so closing with unsaved edits
 * asks first. Covered through PostEditScreen (Edit comment shares the same
 * dialog and policy).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class EditDiscardConfirmationComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val account = AccountSessionSummary(
        stableId = "reddit:test",
        username = "connected-user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    @Test
    fun unsavedChangesIgnoreSurroundingWhitespaceOnly() {
        assertFalse(editHasUnsavedChanges("body", "body"))
        assertFalse(editHasUnsavedChanges("body", "  body \n"))
        assertTrue(editHasUnsavedChanges("body", "body!"))
        assertTrue(editHasUnsavedChanges("", "x"))
    }

    @Test
    fun unchangedTextClosesWithoutAsking() {
        var closes = 0
        setPostEditor(onClose = { closes++ })

        composeRule.onNodeWithContentDescription("Close post editor").performClick()
        composeRule.waitForIdle()
        assertEquals(1, closes)
        composeRule.onNodeWithText("Discard changes?").assertDoesNotExist()
    }

    @Test
    fun editedTextAsksAndKeepEditingStays() {
        var closes = 0
        setPostEditor(onClose = { closes++ })

        composeRule.onNodeWithText("Share the details…").performTextInput("more")
        composeRule.onNodeWithContentDescription("Close post editor").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Discard changes?").assertIsDisplayed()
        assertEquals(0, closes)

        composeRule.onNodeWithText("Keep editing").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Discard changes?").assertDoesNotExist()
        assertEquals(0, closes)
        composeRule.onNodeWithText("Edit post").assertIsDisplayed()
    }

    @Test
    fun editedTextAsksAndDiscardCloses() {
        var closes = 0
        setPostEditor(onClose = { closes++ })

        composeRule.onNodeWithText("Share the details…").performTextInput("more")
        composeRule.onNodeWithContentDescription("Close post editor").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Discard").performClick()
        composeRule.waitForIdle()
        assertEquals(1, closes)
    }

    private fun setPostEditor(onClose: () -> Unit) {
        // An empty body: the editor placeholder is then the field's text node.
        val post = SampleData.posts.first { it.kind == PostKind.TEXT }.copy(id = "t3_abc123", body = "")
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        composeRule.setContent {
            PostEditScreen(
                post = post,
                state = state,
                editState = PostEditUiState(),
                onSubmitEdit = {},
                onAuthorizeEdit = {},
                onCancelAuthorization = {},
                onResetEditState = {},
                onSaved = {},
                onClose = onClose,
            )
        }
        composeRule.waitForIdle()
    }
}
