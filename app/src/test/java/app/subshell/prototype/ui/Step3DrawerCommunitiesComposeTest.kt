package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.CustomFeedSummary
import app.subshell.prototype.data.account.CustomFeedsUiState
import app.subshell.prototype.data.account.RedditSubscriptionsUiState
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.Community
import app.subshell.prototype.ui.app.CustomFeedsEntryPoints
import app.subshell.prototype.ui.app.FeedEntryPoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (subs04c1, subs04c2): the drawer reads Feeds, Custom feeds,
 * Communities, then You (Inbox, Profile), with no Saved or Library item. The
 * custom feeds and communities groups share one open slot (opening one closes
 * the other), the Communities row itself opens Communities, the custom feeds
 * header is lit while a custom feed is open and its group is closed, Full
 * lists the communities without a chevron and Current only links to
 * Communities. The open group resets for another account, and the theme row
 * switches the theme.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3DrawerCommunitiesComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val account = AccountSessionSummary(
        stableId = "reddit:test",
        username = "connected-user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val other = AccountSessionSummary(
        stableId = "reddit:other",
        username = "other-user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val feed = CustomFeedSummary(
        path = "/user/connected-user/m/tech",
        displayName = "Tech",
        description = "",
        visibility = "private",
        subredditNames = listOf("android", "kotlin"),
    )
    private val communities = listOf("kotlinlang", "rustlang").map { name ->
        Community(id = "t5_$name", name = name, description = "", members = "1k", accent = 0xFF336699)
    }

    // The drawer's rows live in its own lazy list; the bottom bar has a
    // "Communities" item too.
    private val inDrawer = hasAnyAncestor(hasScrollToIndexAction())

    private fun compose(
        mode: DrawerCommunitiesMode = DrawerCommunitiesMode.COLLAPSIBLE,
        accounts: List<AccountSessionSummary> = listOf(account),
    ): SubshellState {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val state = SubshellState(
            initialAccountSessions = accounts,
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        state.updateDrawerCommunitiesMode(mode)
        composeRule.setContent {
            SubshellApp(
                videoPlaybackCoordinator = VideoPlaybackCoordinator(context),
                state = state,
                customFeeds = CustomFeedsEntryPoints(
                    customFeedsState = CustomFeedsUiState(accountStableId = account.stableId, feeds = listOf(feed)),
                ),
                redditFeed = FeedEntryPoints(
                    redditSubscriptionsState = RedditSubscriptionsUiState(
                        accountStableId = account.stableId,
                        communities = communities,
                        loaded = true,
                    ),
                ),
            )
        }
        composeRule.waitForIdle()
        state.requestDrawer()
        composeRule.waitForIdle()
        return state
    }

    private fun count(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun described(description: String) =
        composeRule.onAllNodes(hasContentDescription(description)).fetchSemanticsNodes().size

    private fun drawerRow(text: String) = composeRule.onAllNodes(hasText(text) and inDrawer).onFirst()

    private fun top(text: String): Float = drawerRow(text).fetchSemanticsNode().boundsInRoot.top

    private fun click(matcher: SemanticsMatcher) {
        composeRule.onAllNodes(matcher).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun theDrawerReadsFeedsCustomFeedsCommunitiesThenYou() {
        compose()

        val order = listOf("Feeds", "Custom feeds · 1", "Communities", "You", "Inbox", "Profile").map(::top)
        assertEquals(order.sorted(), order)
        assertEquals(order.size, order.toSet().size)
        assertEquals(0, composeRule.onAllNodes(hasText("Saved") and inDrawer).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodes(hasText("Library") and inDrawer).fetchSemanticsNodes().size)
    }

    @Test
    fun openingOneGroupClosesTheOtherAndTheCommunitiesRowOpensCommunities() {
        val state = compose()
        assertEquals(0, count("Tech"))
        assertEquals(0, count("r/kotlinlang"))

        click(hasContentDescription("Expand custom feeds"))
        assertEquals(1, count("Tech"))

        click(hasContentDescription("Expand communities") and hasClickAction())
        assertEquals(1, count("r/kotlinlang"))
        assertEquals("opening communities closes custom feeds", 0, count("Tech"))

        click(hasContentDescription("Collapse communities") and hasClickAction())
        assertEquals(0, count("r/kotlinlang"))
        assertEquals(0, count("Tech"))

        click(hasText("Communities") and hasClickAction() and inDrawer)
        assertEquals(MainDestination.SUBS, state.destination)
    }

    @Test
    fun theCustomFeedsHeaderIsLitWhileAFeedIsOpenAndItsGroupIsClosed() {
        val state = compose()
        click(hasText("Custom feeds · 1") and hasClickAction())
        click(hasText("Tech") and hasClickAction())
        assertEquals(1, count("Feed: Tech"))

        state.requestDrawer()
        composeRule.waitForIdle()
        // Opening a feed from the drawer may leave its group open; close it.
        if (described("Collapse custom feeds") > 0) {
            click(hasContentDescription("Collapse custom feeds"))
        }
        assertEquals(1, described("Expand custom feeds"))
        drawerRow("Custom feeds · 1").assertIsSelected()

        click(hasText("Custom feeds · 1") and hasClickAction())
        assertEquals(1, described("Collapse custom feeds"))
        drawerRow("Custom feeds · 1").assertIsNotSelected()
    }

    @Test
    fun fullListsTheCommunitiesWithoutAChevron() {
        val state = compose(DrawerCommunitiesMode.FULL)

        assertEquals(1, count("r/kotlinlang"))
        assertEquals(1, count("r/rustlang"))
        assertEquals(0, described("Expand communities"))
        assertEquals(0, described("Collapse communities"))

        click(hasText("Communities") and hasClickAction() and inDrawer)
        assertEquals(MainDestination.SUBS, state.destination)
    }

    @Test
    fun currentOnlyLinksToCommunities() {
        val state = compose(DrawerCommunitiesMode.CURRENT)

        assertEquals(0, count("r/kotlinlang"))
        assertEquals(0, described("Expand communities"))

        click(hasText("Communities") and hasClickAction() and inDrawer)
        assertEquals(MainDestination.SUBS, state.destination)
    }

    @Test
    fun anotherAccountStartsWithTheGroupsClosed() {
        val state = compose(accounts = listOf(account, other))
        click(hasContentDescription("Expand communities") and hasClickAction())
        assertEquals(1, described("Collapse communities"))

        state.switchAccount(other.stableId)
        composeRule.waitForIdle()
        state.requestDrawer()
        composeRule.waitForIdle()

        assertEquals(1, described("Expand communities"))
        assertEquals(0, described("Collapse communities"))
    }

    @Test
    fun theThemeRowSwitchesTheTheme() {
        val state = compose()
        assertTrue(state.themeChoice != ThemeChoice.DARK)

        click(hasText("Dark") and hasClickAction())

        assertEquals(ThemeChoice.DARK, state.themeChoice)
    }
}
