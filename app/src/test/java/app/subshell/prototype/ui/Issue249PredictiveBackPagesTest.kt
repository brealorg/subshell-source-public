package app.subshell.prototype.ui

import android.app.Application
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.backup.SettingsBackupRepository
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.DirectChatStartUiState
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.ui.app.PredictiveRouteBackMinScale
import app.subshell.prototype.ui.screens.ContentFiltersScreen
import app.subshell.prototype.ui.screens.NewMessageScreen
import app.subshell.prototype.ui.screens.SettingsDestination
import app.subshell.prototype.ui.screens.SettingsScreen
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE249_IN_SCREEN_PAGES_FOLLOW_THE_BACK_GESTURE_V1 (#249): a page shown
 * inside a screen follows the back gesture as a route does (ISSUE27): it
 * shrinks with the progress and is left only when the gesture commits. A
 * guard (unsaved content) takes Back without a preview, and a screen that
 * only clears its search leaves the rest to the route stack's preview.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue249PredictiveBackPagesTest {
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

    private lateinit var dispatcher: OnBackPressedDispatcher

    private fun show(content: @Composable () -> Unit) {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        composeRule.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            content()
        }
        composeRule.waitForIdle()
    }

    private fun ui(block: () -> Unit) {
        composeRule.runOnUiThread(block)
        composeRule.waitForIdle()
    }

    private fun event(progress: Float) =
        BackEventCompat(touchX = 0f, touchY = 400f, progress = progress, swipeEdge = BackEventCompat.EDGE_LEFT)

    private fun width(text: String): Float =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().first().boundsInWindow.width

    private fun shown(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    /** Runs the gesture to the end and checks the page shrank but stayed until the commit. */
    private fun assertTheGestureShrinksThePage(title: String) {
        val fullWidth = width(title)
        ui { dispatcher.dispatchOnBackStarted(event(0f)) }
        ui { dispatcher.dispatchOnBackProgressed(event(1f)) }
        assertTrue("the page stays until the gesture commits", shown(title))
        assertEquals(fullWidth * PredictiveRouteBackMinScale, width(title), fullWidth * 0.02f)
        ui { dispatcher.onBackPressed() }
    }

    @Test
    fun aSettingsAreaFollowsTheGestureBackToTheList() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        show {
            SubshellTheme {
                SettingsScreen(
                    state = state,
                    backupRepository = SettingsBackupRepository(context, "test"),
                    onBack = {},
                    initialDestinationName = SettingsDestination.DIAGNOSTICS.name,
                )
            }
        }
        assertTheGestureShrinksThePage("Diagnostics")
        assertTrue("back at the Settings list", shown(SettingsDestination.ACCOUNTS.title))
    }

    @Test
    fun aContentFilterTypeFollowsTheGestureBackToTheOverview() {
        show {
            SubshellTheme {
                ContentFiltersScreen(filters = ContentFilters(), onAdd = { _, _, _ -> }, onRemove = { _, _, _ -> }, onBack = {})
            }
        }
        composeRule.onNodeWithText("Words").performClick()
        composeRule.waitForIdle()
        assertTrue(!shown("Content filters"))
        assertTheGestureShrinksThePage("Words")
        assertTrue("back at the overview", shown("Content filters"))
    }

    private fun newMessage(text: String, onBack: () -> Unit) {
        show {
            SubshellTheme {
                NewMessageScreen(
                    accountStableId = account.stableId,
                    authenticated = true,
                    peopleSearchQuery = "",
                    peopleSearchItems = emptyList(),
                    peopleSearchLoading = false,
                    peopleSearchLoaded = false,
                    directChatStartState = DirectChatStartUiState(),
                    onSearchPeople = {},
                    onStartDirectChat = {},
                    onResetDirectChatStartState = {},
                    onRefreshInbox = {},
                    onBack = onBack,
                    consumePrefill = { NewMessagePrefill(recipientUsername = "someone", text = text) },
                )
            }
        }
    }

    @Test
    fun anEmptyNewMessageClosesWithThePreview() {
        var closed = false
        newMessage(text = "") { closed = true }
        assertTheGestureShrinksThePage("New message")
        assertTrue(closed)
    }

    @Test
    fun aNewMessageWithTextAsksFirstWithoutAPreview() {
        var closed = false
        newMessage(text = "hello") { closed = true }
        val fullWidth = width("New message")
        ui { dispatcher.dispatchOnBackStarted(event(0f)) }
        ui { dispatcher.dispatchOnBackProgressed(event(1f)) }
        assertEquals("no preview while a guard owns Back", fullWidth, width("New message"), 0.5f)
        ui { dispatcher.onBackPressed() }
        assertTrue("the discard question", shown("Discard changes?"))
        assertTrue(!closed)
    }

    @Test
    fun discoverCommunitiesLeavesWithTheRoutePreview() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        show { SubshellApp(videoPlaybackCoordinator = testVideoPlaybackCoordinator(context), state = state) }
        ui { state.openSecondary(SecondaryDestination.COMMUNITY_EXPLORE) }
        assertTheGestureShrinksThePage("Discover communities")
        assertTrue(state.currentNavigationRoute !is SubshellRoute.Secondary)
    }
}
