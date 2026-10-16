package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.backup.SettingsBackupRepository
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.ui.screens.SettingsDestination
import app.subshell.prototype.ui.screens.SettingsScreen
import app.subshell.prototype.ui.screens.settingsSearchResult
import app.subshell.prototype.ui.theme.SubshellTheme
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
 * STEP3 (settings02d, settings03b, settings03c, settings02): the side menu
 * offers only Left and Right, the Navigation page has its Communities labels
 * row (and search finds it), the Appearance toggles and dialogs change the
 * state, a search shows only the rows it matched, and a search that matches
 * nothing says so and offers to clear it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3SettingsComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val state = SubshellState(initialInteractionSideConfigured = true)

    private fun show(destination: SettingsDestination) {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val repository = SettingsBackupRepository(context, "test")
        composeRule.setContent {
            SubshellTheme {
                SettingsScreen(
                    state = state,
                    backupRepository = repository,
                    onBack = {},
                    initialDestinationName = destination.name,
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun scrollTo(text: String) {
        composeRule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText(text))
        composeRule.waitForIdle()
    }

    private fun count(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun theSideMenuOffersOnlyLeftAndRight() {
        show(SettingsDestination.NAVIGATION)
        scrollTo("Side menu position")

        assertTrue(count("Left") > 0)
        assertTrue(count("Right") > 0)
        assertEquals(0, count("Follow"))
        // Another setting on this page has its own "Left"; take the side menu's.
        composeRule.onNode(
            hasText("Left") and hasAnyAncestor(hasAnyChild(hasText("Side menu position"))),
        ).performClick()
        composeRule.waitForIdle()

        assertEquals(DrawerSidePreference.LEFT, state.drawerSidePreference)
        assertFalse("drawer-side" in settingsSearchResult("follow").itemKeys)
    }

    @Test
    fun theNavigationPageHasItsCommunitiesLabelsRowAndSearchFindsIt() {
        show(SettingsDestination.NAVIGATION)
        scrollTo("Communities labels")

        assertTrue(count("Communities labels") > 0)
        assertTrue("subreddit-labels" in settingsSearchResult("subreddit labels").itemKeys)
    }

    @Test
    fun theAppearanceTogglesChangeTheState() {
        show(SettingsDestination.APPEARANCE)
        val dynamic = state.dynamicColor
        val motion = state.reduceMotion

        scrollTo("Dynamic colour")
        composeRule.onNodeWithText("Dynamic colour").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        scrollTo("Reduce motion")
        composeRule.onNodeWithText("Reduce motion").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals(!dynamic, state.dynamicColor)
        assertEquals(!motion, state.reduceMotion)
    }

    @Test
    fun theThemeAndCodeFontDialogsChangeTheState() {
        show(SettingsDestination.APPEARANCE)

        scrollTo("Theme")
        composeRule.onNodeWithText("Theme").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Pure black").performClick()
        composeRule.waitForIdle()
        assertEquals(ThemeChoice.PURE_BLACK, state.themeChoice)

        val other = CodeFont.entries.first { it != state.codeFont }
        scrollTo("Code font")
        composeRule.onNodeWithText("Code font").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText(other.label).onFirst().performClick()
        composeRule.waitForIdle()
        assertEquals(other, state.codeFont)
    }

    @Test
    fun aSearchThatFindsNothingSaysSoAndClearsItself() {
        state.settingsQuery = "definitely-not-a-setting"
        show(SettingsDestination.ROOT)

        assertEquals(1, count("No settings found"))
        assertEquals(1, count("Try another word or clear the search."))
        // The text button, not the field's clear icon (a content description).
        composeRule.onNode(hasText("Clear search") and hasClickAction())
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals("", state.settingsQuery)
        assertEquals(0, count("No settings found"))
    }

    @Test
    fun aSearchShowsOnlyTheRowsItMatched() {
        state.settingsQuery = "loop video"
        show(SettingsDestination.ROOT)

        assertEquals(1, count("Loop videos"))
        assertEquals(1, count("Video"))
        assertEquals(0, count("Start videos muted"))
        assertEquals(0, count("Media cache"))
        assertEquals(0, count("Theme"))
        assertEquals(0, count("Browse by area"))
    }
}
