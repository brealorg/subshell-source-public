package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Behaviour tests for the You drafts workspace wiring in SubshellApp that
 * the retired check-next01a4c-contract.sh pinned as text: on an expanded
 * window in split mode the Drafts tab shows the list-detail surface with
 * the "Select a draft" placeholder, opening a draft hosts the composer in
 * the detail pane, and leaving the Drafts tab closes it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1280dp-h800dp")
class SubshellAppDraftsWorkspaceComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theDraftsTabShowsTheWorkspaceAndHostsTheComposerInTheDetailPane() {
        val draft = PostComposerDraft(id = "d1", community = "subshell", title = "Workspace draft")
        // A signed-in account keeps the first-run onboarding overlay away.
        val account = AccountSessionSummary(
            stableId = "reddit:test",
            username = "connected-user",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val state = SubshellState(
            // ISSUE271: an install that has chosen its side shows Home, not the setup flow.
            initialInteractionSideConfigured = true,
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialPostComposerDrafts = listOf(draft.copy(accountStableId = account.stableId)),
        )
        state.updateTabletLayoutMode(TabletLayoutMode.SPLIT)
        state.navigate(MainDestination.YOU)
        state.selectUserProfileTab(state.activeAccountUsername, 7)
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)

        composeRule.setContent {
            SubshellApp(videoPlaybackCoordinator = testVideoPlaybackCoordinator(context), state = state)
        }
        composeRule.onNodeWithText(YOU_DRAFTS_WORKSPACE_PLACEHOLDER).assertIsDisplayed()
        // #224 (adapt-07): the Drafts tab names the list; no second pane header.
        composeRule.onAllNodesWithText("Drafts").assertCountEquals(1)

        composeRule.onNodeWithText("Workspace draft").performClick()
        composeRule.waitForIdle()
        assertTrue(state.postComposerVisible)
        assertEquals("d1", state.postComposerDraft.id)
        assertTrue(state.currentPostComposerSupportsExpandedYouDraftsListDetail())
        composeRule.onAllNodesWithText("Drafts").assertCountEquals(2)
        // The list keeps its card and the detail pane hosts the composer with the same title.
        composeRule.onAllNodesWithText("Workspace draft").assertCountEquals(2)

        // Leaving the Drafts tab through the tab row closes the detail composer.
        composeRule.onNode(hasText("Comments") and hasClickAction()).performClick()
        composeRule.waitForIdle()
        assertEquals(1, state.userProfileTab(state.activeAccountUsername))
        assertFalse(state.postComposerVisible)
    }
}
