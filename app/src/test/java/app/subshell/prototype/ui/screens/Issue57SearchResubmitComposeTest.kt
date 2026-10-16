package app.subshell.prototype.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.RedditCommunityDiscoveryUiState
import app.subshell.prototype.data.account.RedditPeopleSearchUiState
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.Community
import app.subshell.prototype.model.CommunityDiscoveryItem
import app.subshell.prototype.model.CommunityDiscoverySort
import app.subshell.prototype.model.RedditPeopleSearchItem
import app.subshell.prototype.ui.SearchScope
import app.subshell.prototype.ui.SubshellState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE57_RESUBMIT_REFILLS_RETAINED_RESULTS_V1: submitting the same query
 * again (See all, or Search tapped again) emptied the Reddit community and
 * people results, because the answer was already loaded and nothing copied
 * it into the emptied holder.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue57SearchResubmitComposeTest {
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

    private val loadedAnswer = RedditCommunityDiscoveryUiState(
        accountStableId = account.stableId,
        searchQuery = "cats",
        searchSort = CommunityDiscoverySort.RELEVANCE,
        items = listOf(
            CommunityDiscoveryItem(
                community = Community(
                    id = "t5_cats57",
                    name = "catsfromreddit57",
                    description = "Cats",
                    members = "1k",
                    accent = 0xFF336699,
                ),
                nsfw = false,
            ),
        ),
        loaded = true,
        contentRevision = 1L,
        peopleSearch = RedditPeopleSearchUiState(
            accountStableId = account.stableId,
            searchQuery = "cats",
            items = listOf(RedditPeopleSearchItem(username = "catsperson57", title = "", description = "")),
            loaded = true,
            contentRevision = 1L,
        ),
    )

    private fun searchedForCats(): SubshellState {
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        ).apply {
            openSearch(refocus = false)
            updateSearchQuery("cats")
            submitSearch()
        }
        val coordinator = VideoPlaybackCoordinator(ApplicationProvider.getApplicationContext<Application>())
        composeRule.setContent {
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                SearchScreen(state = state, redditCommunityDiscoveryState = loadedAnswer, onBack = {})
            }
        }
        composeRule.waitForIdle()
        assertTrue(shows("catsfromreddit57"))
        assertTrue(shows("catsperson57"))
        return state
    }

    // The result list is lazy: bring the row into view before looking for it.
    private fun shows(text: String): Boolean = runCatching {
        composeRule.onNode(hasScrollToNodeAction())
            .performScrollToNode(hasText(text, substring = true))
        composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }.getOrDefault(false)

    @Test
    fun searchingTheSameQueryAgainKeepsRedditResults() {
        val state = searchedForCats()

        state.submitSearch()
        composeRule.waitForIdle()

        assertTrue(shows("catsfromreddit57"))
        assertTrue(shows("catsperson57"))
    }

    @Test
    fun seeAllCommunitiesKeepsRedditCommunities() {
        val state = searchedForCats()

        state.updateSearchScope(SearchScope.COMMUNITIES)
        state.submitSearch()
        composeRule.waitForIdle()

        assertTrue(shows("catsfromreddit57"))
    }

    @Test
    fun seeAllPeopleKeepsRedditPeople() {
        val state = searchedForCats()

        state.updateSearchScope(SearchScope.USERS)
        state.submitSearch()
        composeRule.waitForIdle()

        assertTrue(shows("catsperson57"))
    }
}
