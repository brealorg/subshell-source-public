package app.subshell.prototype.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.ui.SavedSearch
import app.subshell.prototype.ui.SearchArea
import app.subshell.prototype.ui.SearchScope
import app.subshell.prototype.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE206_SAVED_SEARCHES_V1 (#206): a submitted search can be saved from the
 * filter row; the hub lists saved searches first, a tap runs one and its
 * menu removes it. Nothing shows with the setting off.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue206SavedSearchesComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(RetryOnRobolectricRendererFlake())
        .around(composeRule)

    private val account = AccountSessionSummary(
        stableId = "reddit:test",
        username = "connected-user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    private fun show(state: SubshellState) {
        val coordinator = VideoPlaybackCoordinator(ApplicationProvider.getApplicationContext<Application>())
        composeRule.setContent {
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                SearchScreen(state = state, onBack = {})
            }
        }
        composeRule.waitForIdle()
    }

    private fun state(enabled: Boolean) = SubshellState(
        initialAccountSessions = listOf(account),
        initialActiveAccountId = account.stableId,
        initialInteractionSideConfigured = true,
        initialSavedSearchesEnabled = enabled,
    ).apply { openSearch(refocus = false) }

    private fun shows(text: String): Boolean = runCatching {
        composeRule.onNode(hasScrollToNodeAction())
            .performScrollToNode(hasText(text, substring = true))
        composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }.getOrDefault(false)

    @Test
    fun aSubmittedSearchCanBeSavedFromTheFilterRow() {
        val state = state(enabled = true).apply {
            updateSearchQuery("author:spez cats")
            submitSearch()
        }
        show(state)

        composeRule.onNodeWithText("Save search").performClick()
        composeRule.waitForIdle()

        assertEquals(
            listOf(SavedSearch("author:spez cats", SearchScope.ALL, SearchArea.ALL_SUBSHELL)),
            state.savedSearches,
        )
        composeRule.onNodeWithText("Saved").assertIsDisplayed()
        composeRule.onNodeWithText("Saved").performClick()
        composeRule.waitForIdle()
        assertTrue(state.savedSearches.isEmpty())
    }

    @Test
    fun theHubListsSavedSearchesRunsOneAndRemovesOne() {
        val state = state(enabled = true).apply {
            saveSearch(SavedSearch("dogs", SearchScope.POSTS, SearchArea.ALL_SUBSHELL))
            saveSearch(SavedSearch("cats", SearchScope.COMMUNITIES, SearchArea.ALL_SUBSHELL))
        }
        show(state)

        assertTrue(shows("Saved searches"))
        assertTrue(shows("Communities · All Reddit"))

        composeRule.onNodeWithContentDescription("Options for saved search dogs").performClick()
        composeRule.onNodeWithText("Remove").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf("cats"), state.savedSearches.map { it.query })

        composeRule.onNodeWithText("cats").performClick()
        composeRule.waitForIdle()
        assertEquals("cats", state.searchSubmittedQuery)
        assertEquals(SearchScope.COMMUNITIES, state.searchScope)
    }

    @Test
    fun nothingShowsWithTheSettingOff() {
        val state = state(enabled = false).apply {
            updateSearchQuery("cats")
            submitSearch()
        }
        show(state)

        assertTrue(composeRule.onAllNodesWithText("Save search").fetchSemanticsNodes().isEmpty())
    }
}
