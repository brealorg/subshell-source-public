package app.subshell.prototype.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.subshell.prototype.ui.MainDestination
import app.subshell.prototype.ui.PostComposerDraft
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Behaviour tests for the draft card wiring in the profile's Drafts tab
 * that the retired check-next01a4c-contract.sh pinned as text: opening
 * a card opens that draft in the composer, opening another card while the
 * composer is up rebinds in place, and the delete affordance removes the
 * draft (discarding it when it is the one being edited). Rendered on the
 * JVM with the Robolectric compose harness.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class UserProfileDraftsWorkspaceComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aDraftCardOpensItsDraftAndAnotherCardRebindsTheOpenComposer() {
        val first = draft("first", "First draft")
        val second = draft("second", "Second draft")
        val state = state(listOf(first, second))
        composeRule.setContent { SubshellTheme { UserProfileScreen(state.activeAccountUsername, state) } }

        composeRule.onNodeWithText("First draft").assertIsDisplayed()
        composeRule.onNodeWithText("First draft").performClick()
        composeRule.waitForIdle()
        assertTrue(state.postComposerVisible)
        assertEquals("first", state.postComposerDraft.id)
        val depth = state.navigationRouteDepth

        composeRule.onNodeWithText("Second draft").performClick()
        composeRule.waitForIdle()
        assertEquals("second", state.postComposerDraft.id)
        assertEquals(depth, state.navigationRouteDepth)
        assertTrue(state.postComposerVisible)
    }

    @Test
    fun theDeleteAffordanceRemovesADraftAndDiscardsTheOneBeingEdited() {
        val first = draft("first", "First draft")
        val second = draft("second", "Second draft")
        val state = state(listOf(first, second))
        composeRule.setContent { SubshellTheme { UserProfileScreen(state.activeAccountUsername, state) } }

        composeRule.onAllNodesWithContentDescription("Delete draft")[1].performClick()
        composeRule.waitForIdle()
        assertEquals(listOf("first"), state.postComposerDrafts.map { it.id })
        assertFalse(state.postComposerVisible)

        composeRule.onNodeWithText("First draft").performClick()
        composeRule.waitForIdle()
        assertEquals("first", state.postComposerDraft.id)
        composeRule.onNodeWithContentDescription("Delete draft").performClick()
        composeRule.waitForIdle()
        assertNull(state.postComposerDrafts.firstOrNull { it.id == "first" })
        assertFalse(state.postComposerVisible)
        assertFalse(state.postComposerDraft.hasContent)
    }

    private fun state(drafts: List<PostComposerDraft>): SubshellState = SubshellState(
        initialPostComposerDrafts = drafts,
    ).also { state ->
        state.navigate(MainDestination.YOU)
        state.selectUserProfileTab(state.activeAccountUsername, 7)
    }

    // Fixed timestamps: the state lists drafts newest first, and two drafts
    // stamped with the wall clock straddle a millisecond often enough to flip
    // the order the delete test indexes into. "first" is the newest.
    private fun draft(id: String, title: String): PostComposerDraft =
        PostComposerDraft(
            id = id,
            community = "subshell",
            title = title,
            createdAtEpochMs = if (id == "first") 2_000L else 1_000L,
        )
}
