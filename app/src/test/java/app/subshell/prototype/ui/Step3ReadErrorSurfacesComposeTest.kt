package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.RepositoryQueryKey
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.RedditFeedUiFailureKind
import app.subshell.prototype.data.account.RedditFeedUiState
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.ui.error.LocalViewportErrorHostState
import app.subshell.prototype.ui.error.ViewportErrorHostState
import app.subshell.prototype.ui.screens.CommunityScreen
import app.subshell.prototype.ui.screens.HomeScreen
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (error01a2, error01a22): a feed that already shows posts reports a
 * failed refresh or a failed next page in the tray, under its own scope, with
 * the failure's details — never as a row in the list — and the tray's Retry
 * repeats the same operation (refresh or next page).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3ReadErrorSurfacesComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val alice = AccountSessionSummary(
        stableId = "reddit:alice",
        username = "alice",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val details = FailureDetails(httpStatusCode = 503, retryable = true)
    private val host = ViewportErrorHostState()
    private val communityName = app.subshell.prototype.data.SampleData.posts.first().community
    private val calls = mutableListOf<String>()

    @Before
    fun initWorkManager() {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
    }

    private fun signedInState() = SubshellState(
        repository = FakeSubshellRepository(),
        initialAccountSessions = listOf(alice),
        initialActiveAccountId = alice.stableId,
        initialInteractionSideConfigured = true,
    )

    private fun failed(key: RepositoryQueryKey, operation: RepositoryLoadOperation) = RedditFeedUiState(
        accountStableId = alice.stableId,
        queryKey = key,
        failedOperation = operation,
        failureKind = RedditFeedUiFailureKind.HTTP,
        failureRetryable = true,
        failureDetails = details,
    )

    private fun showHome(operation: RepositoryLoadOperation) {
        val state = signedInState()
        val key = mainPostFeedQueryKey(
            account = state.activeAccountSession,
            source = state.feedSource,
            sort = state.feedSort,
            timePeriod = state.listingTimePeriod,
        )
        state.ensurePostPaging(key)
        assertTrue("posts should be cached", state.postPagingSnapshot(key).items.isNotEmpty())
        val coordinator = testVideoPlaybackCoordinator(ApplicationProvider.getApplicationContext<Application>())
        composeRule.setContent {
            SubshellTheme {
                CompositionLocalProvider(
                    LocalVideoPlaybackCoordinator provides coordinator,
                    LocalViewportErrorHostState provides host,
                ) {
                    HomeScreen(
                        state = state,
                        redditFeedState = failed(key, operation),
                        onRefreshRedditFeed = { _, _, _, _ -> calls += "refresh" },
                        onAppendRedditFeed = { _, _, _, _ -> calls += "append" },
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun showCommunity() {
        val state = signedInState()
        val key = communityPostFeedQueryKey(
            account = state.activeAccountSession,
            communityName = communityName,
            sort = state.communitySort,
            timePeriod = state.listingTimePeriod,
        )
        state.ensurePostPaging(key)
        assertTrue("posts should be cached", state.postPagingSnapshot(key).items.any { it.community == communityName })
        val coordinator = testVideoPlaybackCoordinator(ApplicationProvider.getApplicationContext<Application>())
        composeRule.setContent {
            SubshellTheme {
                CompositionLocalProvider(
                    LocalVideoPlaybackCoordinator provides coordinator,
                    LocalViewportErrorHostState provides host,
                ) {
                    CommunityScreen(
                        communityName = communityName,
                        state = state,
                        redditFeedState = failed(key, RepositoryLoadOperation.REFRESH),
                        onRefreshRedditFeed = { _, _, _, _ -> calls += "refresh" },
                        onBack = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun shownInTheScreen(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun aFailedRefreshGoesToTheTrayAndRetryRefreshes() {
        showHome(RepositoryLoadOperation.REFRESH)
        val request = requireNotNull(host.currentRequest)

        assertTrue(request.scopeKey, request.scopeKey.startsWith("home-feed:"))
        assertEquals("Couldn't refresh the feed", request.presentation.title)
        assertEquals(details, request.failureDetails)
        assertEquals(0, shownInTheScreen("Couldn't refresh the feed"))

        calls.clear()
        composeRule.runOnIdle { host.retryCurrent() }
        assertEquals(listOf("refresh"), calls)
    }

    @Test
    fun aFailedNextPageGoesToTheTrayAndRetryAsksForTheNextPage() {
        showHome(RepositoryLoadOperation.APPEND)
        val request = requireNotNull(host.currentRequest)

        assertEquals("Couldn't load more posts", request.presentation.title)
        assertEquals(0, shownInTheScreen("Couldn't load more posts"))

        calls.clear()
        composeRule.runOnIdle { host.retryCurrent() }
        assertEquals(listOf("append"), calls)
    }

    @Test
    fun aCommunityReportsUnderItsOwnScope() {
        showCommunity()
        val request = requireNotNull(host.currentRequest)

        assertTrue(request.scopeKey, request.scopeKey.startsWith("community-feed:${alice.stableId}:$communityName:"))
        assertEquals(details, request.failureDetails)
        assertEquals(0, shownInTheScreen(request.presentation.title))
    }
}
