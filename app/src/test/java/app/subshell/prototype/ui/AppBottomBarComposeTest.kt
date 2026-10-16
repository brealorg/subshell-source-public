package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.ui.app.AppBottomBar
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
 * The Scaffold's bottom bar in its own host: its items navigate (Home also
 * leaves a custom feed), the inbox badge caps at 99+, the You item's
 * long-press opens the account switcher and a suppressed click is swallowed
 * once, every item keeps its full accessibility name at large text, and the
 * bar stays away inside a route, in the expanded layout and in an open
 * modmail thread.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class AppBottomBarComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun itemsNavigateAndHomeLeavesACustomFeed() {
        val customFeedPath = mutableStateOf<String?>("/user/alice/m/cats")
        val state = host(activeCustomFeedPath = customFeedPath)

        composeRule.onNodeWithContentDescription("Inbox").performClick()
        composeRule.waitForIdle()
        assertEquals(MainDestination.INBOX, state.destination)
        assertEquals("/user/alice/m/cats", customFeedPath.value)

        composeRule.onNodeWithContentDescription("Home").performClick()
        composeRule.waitForIdle()
        assertEquals(MainDestination.HOME, state.destination)
        assertNull(customFeedPath.value)
    }

    @Test
    fun theInboxBadgeShowsTheCountAndCapsAt99Plus() {
        val unread = mutableStateOf(3)
        host(inboxUnreadCount = unread)
        composeRule.onNodeWithText("3", useUnmergedTree = true).assertIsDisplayed()

        unread.value = 150
        composeRule.waitForIdle()
        composeRule.onNodeWithText("99+", useUnmergedTree = true).assertIsDisplayed()

        unread.value = 0
        composeRule.waitForIdle()
        assertEquals(
            0,
            composeRule.onAllNodesWithText("99+", useUnmergedTree = true)
                .fetchSemanticsNodes().size,
        )
    }

    @Test
    fun longPressOnYouOpensTheAccountSwitcher() {
        val switcherVisible = mutableStateOf(false)
        host(accountSwitcherVisible = switcherVisible)

        composeRule.onNodeWithContentDescription("You")
            .performSemanticsAction(SemanticsActions.OnLongClick)
        composeRule.waitForIdle()

        assertTrue(switcherVisible.value)
    }

    @Test
    fun aSuppressedYouClickIsSwallowedOnce() {
        val suppress = mutableStateOf(true)
        val state = host(suppressYouClick = suppress)

        composeRule.onNodeWithContentDescription("You").performClick()
        composeRule.waitForIdle()
        assertEquals(MainDestination.HOME, state.destination)
        assertFalse(suppress.value)

        composeRule.onNodeWithContentDescription("You").performClick()
        composeRule.waitForIdle()
        assertEquals(MainDestination.YOU, state.destination)
    }

    @Test
    fun itemsKeepTheirFullAccessibilityNameAtLargeText() {
        host(density = Density(density = 2.625f, fontScale = 2.0f))

        composeRule.onNodeWithContentDescription("Communities").assertIsDisplayed()
        assertEquals(
            0,
            composeRule.onAllNodesWithContentDescription("Subs").fetchSemanticsNodes().size,
        )
    }

    @Test
    fun withLabelsOffTheIconCarriesTheName() {
        val state = SubshellState()
        state.updateNavigationLabelMode(NavigationLabelMode.OFF)
        host(state = state)

        composeRule.onNodeWithContentDescription("Communities").assertIsDisplayed()
        assertEquals(
            0,
            composeRule.onAllNodesWithText("Communities", useUnmergedTree = true)
                .fetchSemanticsNodes().size,
        )
    }

    @Test
    fun nothingRendersInsideARouteTheExpandedLayoutOrAnOpenModmailThread() {
        val inContext = mutableStateOf(true)
        val expanded = mutableStateOf(false)
        val modmailThreadOpen = mutableStateOf(false)
        host(inContext = inContext, expanded = expanded, modmailThreadOpen = modmailThreadOpen)
        assertEquals(0, composeRule.onAllNodesWithContentDescription("Inbox").fetchSemanticsNodes().size)

        inContext.value = false
        expanded.value = true
        composeRule.waitForIdle()
        assertEquals(0, composeRule.onAllNodesWithContentDescription("Inbox").fetchSemanticsNodes().size)

        expanded.value = false
        modmailThreadOpen.value = true
        composeRule.waitForIdle()
        assertEquals(0, composeRule.onAllNodesWithContentDescription("Inbox").fetchSemanticsNodes().size)

        modmailThreadOpen.value = false
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Inbox").assertIsDisplayed()
    }

    private fun host(
        state: SubshellState = SubshellState(),
        density: Density = Density(density = 2.625f, fontScale = 1.0f),
        inboxUnreadCount: MutableState<Int> = mutableStateOf(0),
        accountSwitcherVisible: MutableState<Boolean> = mutableStateOf(false),
        suppressYouClick: MutableState<Boolean> = mutableStateOf(false),
        activeCustomFeedPath: MutableState<String?> = mutableStateOf(null),
        inContext: MutableState<Boolean> = mutableStateOf(false),
        expanded: MutableState<Boolean> = mutableStateOf(false),
        modmailThreadOpen: MutableState<Boolean> = mutableStateOf(false),
    ): SubshellState {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val interactionSources = MainDestination.entries.associateWith { MutableInteractionSource() }
        composeRule.setContent {
            SubshellTheme {
                AppBottomBar(
                    state = state,
                    inContext = inContext.value,
                    expandedMainLayout = expanded.value,
                    modmailThreadOpen = modmailThreadOpen.value,
                    compactNavigationBarHeight = 80.dp,
                    bottomBarHiddenFractionState = mutableFloatStateOf(0f),
                    density = density,
                    inboxUnreadCount = inboxUnreadCount.value,
                    navInteractionSources = interactionSources,
                    accountSwitcherVisibleState = accountSwitcherVisible,
                    suppressYouClickState = suppressYouClick,
                    activeCustomFeedPathState = activeCustomFeedPath,
                )
            }
        }
        composeRule.waitForIdle()
        return state
    }
}
