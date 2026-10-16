package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.ui.app.AppFloatingActionButton
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE73_HIDDEN_FAB_LEAVES_THE_SCREEN_V1: with the bottom chrome hidden the
 * Create button was invisible but still on screen, tappable and focusable.
 * ISSUE75_NSFW_SWITCH_STATE_IS_ANNOUNCED_V1: the drawer's "Show NSFW posts"
 * row exposed no on/off state to accessibility services.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue73And75ChromeAccessibilityComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun createButtons() =
        composeRule.onAllNodesWithText("Create", substring = true, useUnmergedTree = true)
            .fetchSemanticsNodes().size

    @Test
    fun theCreateButtonLeavesWithTheChromeAndComesBack() {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        val hidden = mutableFloatStateOf(0f)
        val customFeed = mutableStateOf(false)
        composeRule.setContent {
            SubshellTheme {
                AppFloatingActionButton(
                    state = SubshellState(),
                    customFeedFabVisible = customFeed.value,
                    createButtonVisible = true,
                    createPostButtonVisible = true,
                    fabTravelPx = 315f,
                    bottomBarHiddenFractionState = hidden,
                    customFeedCreateRequestedState = mutableStateOf(false),
                )
            }
        }
        composeRule.waitForIdle()
        assertEquals(1, createButtons())

        hidden.floatValue = 1f
        composeRule.waitForIdle()
        assertEquals(0, createButtons())

        customFeed.value = true
        composeRule.waitForIdle()
        assertEquals(0, createButtons())

        hidden.floatValue = 0f
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Create feed", useUnmergedTree = true).assertExists()
    }

    @Test
    fun theNsfwRowIsASwitchWithItsState() {
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
            SubshellApp(videoPlaybackCoordinator = VideoPlaybackCoordinator(context), state = state)
        }
        composeRule.waitForIdle()
        state.requestDrawer()
        composeRule.waitForIdle()

        val row = composeRule.onNodeWithText("Show NSFW posts")
        row.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        row.assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))

        row.performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Show NSFW posts")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
    }
}
