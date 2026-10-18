package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.PostKind
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Characterisation of the expanded-window shell surfaces as hosted by
 * SubshellApp today, written before ExpandedMainNavigationRail,
 * ExpandedListDetailSurface, ExpandedContextSurface and mainContentPadding
 * move to their own file. Current behaviour, not a specification: the rail
 * navigates between the main destinations and its You item opens the
 * account switcher on long press; a post opened from the Home feed keeps
 * the list pane (with its source · sort header) visible next to the post.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1280dp-h800dp")
class ExpandedSurfacesComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun railNavigatesAndLongPressOpensTheSwitcherAndHomePostKeepsTheListPane() {
        val account = AccountSessionSummary(
            stableId = "reddit:test",
            username = "connected-user",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)

        composeRule.setContent {
            SubshellApp(
                videoPlaybackCoordinator = testVideoPlaybackCoordinator(context),
                state = state,
            )
        }
        composeRule.waitForIdle()

        // Rail items expose the destination label as content description; the
        // You item is the one node that carries the account-switcher long click,
        // and the other destinations are its siblings.
        val youRailItem = hasContentDescription("You") and SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick)
        composeRule.onNode(youRailItem).assertIsDisplayed()

        composeRule.onNode(hasContentDescription("Inbox") and hasAnySibling(youRailItem)).performClick()
        composeRule.waitForIdle()
        assertEquals(MainDestination.INBOX, state.destination)

        composeRule.onNode(hasContentDescription("Home") and hasAnySibling(youRailItem)).performClick()
        composeRule.waitForIdle()
        assertEquals(MainDestination.HOME, state.destination)

        // Long-pressing You opens the quick account switcher; its "Manage
        // accounts" row leads to the Settings secondary.
        composeRule.onNode(youRailItem).performSemanticsAction(SemanticsActions.OnLongClick)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Manage accounts").performClick()
        composeRule.waitForIdle()
        assertEquals(SecondaryDestination.SETTINGS, state.secondaryDestination)
        state.closeSecondary()
        composeRule.waitForIdle()

        // A post opened from the Home feed keeps the list pane and its header
        // next to the post, and the rail stays beside them (#224, owner).
        val post = SampleData.posts.first { it.kind == PostKind.TEXT }
        state.openFromMainFeed(post)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("${state.feedSource.label} · ${state.feedSort.label}").assertIsDisplayed()
        composeRule.onNodeWithText(post.title).assertIsDisplayed()
        composeRule.onNode(youRailItem).assertIsDisplayed()
    }
}
