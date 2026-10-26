package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.RedditSubscriptionsUiState
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.model.Community
import app.subshell.prototype.ui.app.FeedEntryPoints
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE257_COLD_START_LANDS_ON_CACHED_SUBSCRIPTIONS_V1 (#257): a cold start of
 * the account that was already active lands on Home from a cached list that
 * is not empty (no wait for Reddit's answer); an empty or missing cache, a
 * sign-in and an account switch still wait for Reddit (AUTH07B2).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue257ColdStartLandingTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val account = AccountSessionSummary(
        stableId = "reddit:reader",
        username = "reader",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val other = account.copy(stableId = "reddit:other", username = "other")
    private val community = Community(id = "t5_test", name = "test", description = "", members = "1", accent = 0xFF6750A4L)

    private fun cached(accountId: String, communities: List<Community> = listOf(community)) =
        RedditSubscriptionsUiState(
            accountStableId = accountId,
            communities = communities,
            loaded = true,
            fromCache = true,
            loading = true,
        )

    private fun authoritative(accountId: String) = RedditSubscriptionsUiState(
        accountStableId = accountId,
        communities = listOf(community),
        loaded = true,
    )

    @Test
    fun theColdStartPolicyTakesACachedListThatIsNotEmpty() {
        val cachedJoined = cached(account.stableId)
        assertEquals(
            AuthenticatedMainFeedLandingDecision.HOME,
            resolveAuthenticatedLandingFeed(account.stableId, cachedJoined, coldStart = true),
        )
        assertEquals(
            AuthenticatedMainFeedLandingDecision.WAIT,
            resolveAuthenticatedLandingFeed(account.stableId, cachedJoined, coldStart = false),
        )
        assertEquals(
            AuthenticatedMainFeedLandingDecision.WAIT,
            resolveAuthenticatedLandingFeed(
                account.stableId,
                cached(account.stableId, communities = emptyList()),
                coldStart = true,
            ),
        )
    }

    private fun signedIn() = SubshellState(
        initialAccountSessions = listOf(account, other),
        initialActiveAccountId = account.stableId,
        initialInteractionSideConfigured = true,
    )

    @Test
    fun aColdStartLandsOnHomeFromTheCacheAndASwitchStillWaitsForReddit() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val state = signedIn()
        // A sort the landing replaces (Home lands on Best), so the landing is visible.
        state.updateFeedSort(FeedSort.TOP)
        var subscriptions by mutableStateOf(cached(account.stableId))
        composeRule.setContent {
            SubshellApp(
                videoPlaybackCoordinator = testVideoPlaybackCoordinator(context),
                state = state,
                redditFeed = FeedEntryPoints(redditSubscriptionsState = subscriptions),
            )
        }
        composeRule.waitForIdle()
        assertEquals("the cold start landed from the cache", FeedSort.BEST, state.feedSort)

        composeRule.runOnIdle {
            state.switchAccount(other.stableId)
            state.updateFeedSort(FeedSort.TOP)
            subscriptions = cached(other.stableId)
        }
        composeRule.waitForIdle()
        assertEquals("a switch waits for Reddit", FeedSort.TOP, state.feedSort)

        composeRule.runOnIdle { subscriptions = authoritative(other.stableId) }
        composeRule.waitForIdle()
        assertEquals(FeedSort.BEST, state.feedSort)

        // Switching back to the account the app started with is a switch too.
        composeRule.runOnIdle {
            state.switchAccount(account.stableId)
            state.updateFeedSort(FeedSort.TOP)
            subscriptions = cached(account.stableId)
        }
        composeRule.waitForIdle()
        assertEquals("a switch back waits for Reddit", FeedSort.TOP, state.feedSort)
    }
}
