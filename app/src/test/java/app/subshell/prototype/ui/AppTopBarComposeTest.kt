package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.CustomFeedSummary
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.ui.app.AppTopBar
import app.subshell.prototype.ui.screens.InboxContextualSelection
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Scaffold's top bar in its own host: the inbox selection bar takes the
 * header's place while activity is selected, the header follows the active
 * custom feed and the community-actions search, choosing a sort drops a
 * pending authenticated landing, and the bar stays away inside a route or
 * an open modmail thread.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class AppTopBarComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theSelectionBarReplacesTheHeaderAndRoutesItsActions() {
        val calls = mutableListOf<String>()
        val selection = InboxContextualSelection(
            selectedCount = 2,
            canMarkRead = true,
            allVisibleSelected = false,
            onClose = { calls += "close" },
            onMarkRead = { calls += "markRead" },
            onDismiss = { calls += "dismiss" },
            onSelectAll = { calls += "selectAll" },
        )
        host(selection = selection)

        composeRule.onNodeWithText("2 selected").assertIsDisplayed()
        assertEquals(
            0,
            composeRule.onAllNodesWithContentDescription("Choose feed sorting")
                .fetchSemanticsNodes().size,
        )
        composeRule.onNodeWithContentDescription("Mark selected activity as read").performClick()
        composeRule.onNodeWithContentDescription("Dismiss selected activity").performClick()
        composeRule.onNodeWithContentDescription("Select all activity").performClick()
        composeRule.onNodeWithContentDescription("Exit selection").performClick()

        assertEquals(listOf("markRead", "dismiss", "selectAll", "close"), calls)
    }

    @Test
    fun anActiveCustomFeedTitlesTheHeaderAndHidesTheSort() {
        host(
            customFeed = CustomFeedSummary(
                path = "/user/alice/m/cats",
                displayName = "Cats",
                description = "",
                visibility = "private",
                subredditNames = listOf("cats"),
            ),
        )

        composeRule.onNodeWithText("Feed: Cats").assertIsDisplayed()
        assertEquals(
            0,
            composeRule.onAllNodesWithContentDescription("Choose feed sorting")
                .fetchSemanticsNodes().size,
        )
    }

    @Test
    fun choosingASortUpdatesTheFeedAndDropsAPendingLanding() {
        val pending = mutableStateOf<String?>("reddit:alice")
        val state = host(pendingLanding = pending)

        composeRule.onNodeWithContentDescription("Choose feed sorting").performClick()
        composeRule.onNodeWithText(FeedSort.NEW.label).performClick()
        composeRule.waitForIdle()

        assertEquals(FeedSort.NEW, state.feedSort)
        assertNull(pending.value)
    }

    @Test
    fun theSearchActionFollowsTheCommunityActionsSwitch() {
        host(subsCommunityActions = true)

        composeRule.onNodeWithContentDescription("Search communities").assertIsDisplayed()
    }

    @Test
    fun thePlainSearchActionWithoutCommunityActions() {
        host(subsCommunityActions = false)

        composeRule.onNodeWithContentDescription("Search").assertIsDisplayed()
        assertEquals(
            0,
            composeRule.onAllNodesWithContentDescription("Search communities")
                .fetchSemanticsNodes().size,
        )
    }

    @Test
    fun nothingRendersInsideARouteOrAnOpenModmailThread() {
        val selection = InboxContextualSelection(
            selectedCount = 1,
            canMarkRead = true,
            allVisibleSelected = false,
            onClose = {},
            onMarkRead = {},
            onDismiss = {},
            onSelectAll = {},
        )
        val inContext = mutableStateOf(true)
        val modmailThreadOpen = mutableStateOf(false)
        host(selection = selection, inContext = inContext, modmailThreadOpen = modmailThreadOpen)
        assertEquals(0, composeRule.onAllNodesWithText("1 selected").fetchSemanticsNodes().size)

        inContext.value = false
        modmailThreadOpen.value = true
        composeRule.waitForIdle()
        assertEquals(0, composeRule.onAllNodesWithText("1 selected").fetchSemanticsNodes().size)

        modmailThreadOpen.value = false
        composeRule.waitForIdle()
        composeRule.onNodeWithText("1 selected").assertIsDisplayed()
    }

    private fun host(
        selection: InboxContextualSelection? = null,
        customFeed: CustomFeedSummary? = null,
        subsCommunityActions: Boolean = false,
        pendingLanding: MutableState<String?> = mutableStateOf(null),
        inContext: MutableState<Boolean> = mutableStateOf(false),
        modmailThreadOpen: MutableState<Boolean> = mutableStateOf(false),
    ): SubshellState {
        val state = SubshellState()
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        composeRule.setContent {
            SubshellTheme {
                AppTopBar(
                    state = state,
                    inContext = inContext.value,
                    modmailThreadOpen = modmailThreadOpen.value,
                    topBarHiddenFractionState = mutableFloatStateOf(0f),
                    activeInboxContextualSelection = selection,
                    activeCustomFeed = customFeed,
                    subsCommunityActions = subsCommunityActions,
                    pendingAuthenticatedLandingAccountIdState = pendingLanding,
                )
            }
        }
        composeRule.waitForIdle()
        return state
    }
}
