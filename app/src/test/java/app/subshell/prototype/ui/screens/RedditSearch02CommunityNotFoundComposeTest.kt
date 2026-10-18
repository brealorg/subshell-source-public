package app.subshell.prototype.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.RedditFeedUiFailureKind
import app.subshell.prototype.data.account.RedditFeedUiState
import app.subshell.prototype.data.FakeRepositoryMode
import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.ui.SearchArea
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.searchPostFeedQueryKey
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * REDDITSEARCH02: a search in a community that does not exist (the data layer
 * answers HTTP 404) says so, instead of "Couldn't load search results".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RedditSearch02CommunityNotFoundComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val account = AccountSessionSummary(
        stableId = "reddit:test",
        username = "connected-user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    // The search found nothing: the repository behind the result list is empty.
    private val fakeRepository = FakeSubshellRepository()

    private fun searchedIn(community: String): SubshellState = SubshellState(
        initialAccountSessions = listOf(account),
        initialActiveAccountId = account.stableId,
        initialInteractionSideConfigured = true,
        repository = fakeRepository,
    ).apply {
        openSubreddit(community)
        openSearch()
        updateSearchQuery("test")
        submitSearch()
        fakeRepository.mode = FakeRepositoryMode.EMPTY
    }

    private fun failedWith(state: SubshellState, statusCode: Int) = RedditFeedUiState(
        accountStableId = state.activeAccountId,
        queryKey = searchPostFeedQueryKey(
            account = state.activeAccountSession,
            destination = state.searchOriginDestination,
            originCommunity = state.searchOriginCommunity,
            query = state.searchSubmittedQuery,
            scope = state.searchScope,
            area = state.searchArea,
        ),
        failureKind = RedditFeedUiFailureKind.HTTP,
        failureDetails = FailureDetails(httpStatusCode = statusCode),
    )

    private fun show(state: SubshellState, feed: RedditFeedUiState) {
        val coordinator = testVideoPlaybackCoordinator(ApplicationProvider.getApplicationContext<Application>())
        composeRule.setContent {
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                SearchScreen(state = state, redditFeedState = feed, onBack = {})
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun aMissingCommunitySaysSo() {
        val state = searchedIn("doesnotexist")
        assertEquals(SearchArea.CURRENT_COMMUNITY, state.searchArea)

        show(state, failedWith(state, 404))

        composeRule.onNodeWithText("Couldn't find r/doesnotexist").assertExists()
        assertEquals(0, composeRule.onAllNodesWithText("Couldn't load search results").fetchSemanticsNodes().size)
    }

    @Test
    fun anotherHttpFailureIsStillAFailedSearch() {
        val state = searchedIn("kotlin")

        show(state, failedWith(state, 500))

        composeRule.onNodeWithText("Couldn't load search results").assertExists()
    }

    @Test
    fun onlyAnHttp404InACommunityIsNotFound() {
        val notFound = FailureDetails(httpStatusCode = 404)
        assertEquals("kotlin", searchCommunityNotFound(RedditFeedUiFailureKind.HTTP, notFound, "r/kotlin"))
        assertEquals(null, searchCommunityNotFound(RedditFeedUiFailureKind.HTTP, notFound, null))
        assertEquals(null, searchCommunityNotFound(RedditFeedUiFailureKind.TRANSPORT, notFound, "kotlin"))
        assertEquals(null, searchCommunityNotFound(RedditFeedUiFailureKind.HTTP, FailureDetails(httpStatusCode = 403), "kotlin"))
        assertEquals(null, searchCommunityNotFound(null, null, "kotlin"))
    }
}
