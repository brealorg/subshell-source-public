package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.backup.SettingsBackupRepository
import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.ui.screens.settingsRootDestinations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #224 (M3 audit group 7, adaptive layouts), owner decisions:
 * M3224_NAVIGATION_RAIL_FROM_MEDIUM_WIDTH_V1, M3224_READING_WIDTH_V1,
 * M3224_ONE_LIST_DETAIL_GEOMETRY_V1, M3224_SETTINGS_LIST_DETAIL_V1.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1280dp-h800dp")
class Issue224AdaptiveComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private val account = AccountSessionSummary(
        stableId = "reddit:test",
        username = "connected-user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    private fun compose(configure: (SubshellState) -> Unit = {}): SubshellState {
        ensureWorkManagerInitialized(context)
        SettingsBackupRepository(context, appVersion = "test").dismissFolderOnboarding()
        val state = SubshellState(
            repository = FakeSubshellRepository(),
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        configure(state)
        composeRule.setContent {
            SubshellApp(videoPlaybackCoordinator = VideoPlaybackCoordinator(context), state = state)
        }
        composeRule.waitForIdle()
        return state
    }

    private fun bounds(text: String): List<Rect> =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().map { it.boundsInRoot }

    // Navigation items carry their name as a content description (A11Y01A11).
    private fun item(name: String): List<Rect> =
        composeRule.onAllNodesWithContentDescription(name).fetchSemanticsNodes().map { it.boundsInRoot }

    private fun px(dp: Float): Float = with(composeRule.density) { dp.dp.toPx() }

    /** The rail: the destination labels stacked at the window's start edge. */
    private fun railShown(): Boolean {
        val inbox = item("Inbox").firstOrNull { it.left < px(80f) } ?: return false
        val home = item("Home").firstOrNull { it.left < px(80f) } ?: return false
        return inbox.top > home.bottom
    }

    private fun bottomBarShown(): Boolean {
        val bottom = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.bottom
        return item("Inbox").any { it.bottom > bottom - px(100f) && it.left > px(80f) }
    }

    @Test
    @Config(qualifiers = "w673dp-h841dp")
    fun aMediumWindowGetsTheRailInsteadOfTheBottomBar() {
        compose()

        assertTrue("rail at 673 dp", railShown())
        assertTrue("no bottom bar beside the rail", !bottomBarShown())
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun aPhoneKeepsTheBottomBar() {
        compose()

        assertTrue(bottomBarShown())
        assertTrue(!railShown())
    }

    @Test
    fun theRailStaysOnARoute() {
        compose { it.openSubreddit(SampleData.communities.first().name) }

        assertTrue("rail beside a community", railShown())
    }

    @Test
    fun theRailNamesCommunitiesAsTheBottomBarDoes() {
        compose()

        assertTrue(item("Communities").any { it.left < px(80f) })
        // The visible label (its text is cleared from semantics, A11Y01A11).
        assertEquals("Communities", navigationRailVisualLabel(MainDestination.SUBS))
    }

    @Test
    fun aSinglePaneFeedIsCappedAtTheReadingWidth() {
        val post = SampleData.posts.first()
        compose { it.openSubreddit(post.community) }

        val card = composeRule.onAllNodesWithText(post.title).fetchSemanticsNodes().first().boundsInRoot
        assertTrue("card ${card.width} px within 640 dp", card.width <= px(640f) + 1f)
    }

    @Test
    fun settingsAreListAndDetailOnAWideWindow() {
        val state = compose { it.openSecondary(SecondaryDestination.SETTINGS) }
        val first = settingsRootDestinations.first()
        val second = settingsRootDestinations[1]

        // The areas on the left, the first area open on the right.
        assertTrue(bounds("Browse by area").isNotEmpty())
        assertTrue("first area open: ${first.title}", bounds(first.title).size >= 2)

        composeRule.onNodeWithText(second.description).performClick()
        composeRule.waitForIdle()

        assertTrue("second area open: ${second.title}", bounds(second.title).size >= 2)
        assertEquals(SubshellRoute.Secondary::class, state.currentNavigationRoute::class)
    }
}
