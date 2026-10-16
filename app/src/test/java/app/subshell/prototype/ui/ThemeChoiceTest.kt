package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Issue #31: pure black is a fourth theme in the pickers, stored as Dark
 * plus the pure-black flag, so picking it always means a dark theme.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ThemeChoiceTest {
    private val composeRule = createComposeRule()

    // Display-density guard for the Robolectric `lightZ … Infinity` launch
    // flake; same whole-app setup as RootBackComposeTest, where it struck.
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    @Test
    fun choicesMapToTheStoredModeAndFlag() {
        assertEquals(ThemeChoice.SYSTEM, themeChoice(ThemeMode.SYSTEM, pureBlack = false))
        assertEquals(ThemeChoice.LIGHT, themeChoice(ThemeMode.LIGHT, pureBlack = false))
        assertEquals(ThemeChoice.DARK, themeChoice(ThemeMode.DARK, pureBlack = false))
        assertEquals(ThemeChoice.PURE_BLACK, themeChoice(ThemeMode.DARK, pureBlack = true))
        // The flag only counts with Dark; the pickers never store it otherwise.
        assertEquals(ThemeChoice.SYSTEM, themeChoice(ThemeMode.SYSTEM, pureBlack = true))
        assertEquals(ThemeChoice.LIGHT, themeChoice(ThemeMode.LIGHT, pureBlack = true))
        for (choice in ThemeChoice.entries) {
            assertEquals(choice, themeChoice(choice.mode, choice.pureBlack))
        }
    }

    @Test
    fun pickingPureBlackAlwaysMeansDarkAndPickingAnotherThemeClearsIt() {
        val state = SubshellState(initialThemeMode = ThemeMode.SYSTEM)

        state.updateThemeChoice(ThemeChoice.PURE_BLACK)
        assertEquals(ThemeMode.DARK, state.themeMode)
        assertTrue(state.pureBlackDarkTheme)
        assertEquals(ThemeChoice.PURE_BLACK, state.themeChoice)

        state.updateThemeChoice(ThemeChoice.SYSTEM)
        assertEquals(ThemeMode.SYSTEM, state.themeMode)
        assertFalse(state.pureBlackDarkTheme)

        state.updateThemeChoice(ThemeChoice.PURE_BLACK)
        state.updateThemeChoice(ThemeChoice.DARK)
        assertEquals(ThemeMode.DARK, state.themeMode)
        assertFalse(state.pureBlackDarkTheme)
    }

    @Test
    fun theDrawerThemeRowOffersPureBlack() {
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
            initialThemeMode = ThemeMode.SYSTEM,
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

        // A quick switch without System; with System on, nothing is selected.
        assertEquals(listOf(ThemeChoice.LIGHT, ThemeChoice.DARK, ThemeChoice.PURE_BLACK), drawerThemeChoices)
        assertEquals(0, composeRule.onAllNodesWithText(ThemeChoice.SYSTEM.shortLabel).fetchSemanticsNodes().size)
        for (choice in drawerThemeChoices) {
            composeRule.onNodeWithText(choice.shortLabel).assertIsNotSelected()
        }

        composeRule.onNodeWithText(ThemeChoice.PURE_BLACK.shortLabel).performClick()
        composeRule.waitForIdle()
        assertEquals(ThemeChoice.PURE_BLACK, state.themeChoice)
        assertEquals(ThemeMode.DARK, state.themeMode)
        composeRule.onNodeWithText(ThemeChoice.PURE_BLACK.shortLabel).assertIsSelected()
    }
}
