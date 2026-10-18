package app.subshell.prototype.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.RedditSubscriptionsUiFailureKind
import app.subshell.prototype.data.account.RedditSubscriptionsUiState
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.error.LocalViewportErrorHostState
import app.subshell.prototype.ui.error.ViewportErrorHostState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE97_MEMBERSHIP_SYNC_FAILURE_OFFERS_RETRY_V1: after a failed
 * subscriptions sync, Join stayed disabled on "Loading…" for good, with no
 * word and no way to retry short of leaving the community.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue97MembershipSyncRetryComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(RetryOnRobolectricRendererFlake())
        .around(composeRule)

    private val account = AccountSessionSummary(
        stableId = "reddit:me",
        username = "me_user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val noticeHost = ViewportErrorHostState()
    private val subscriptionLoads = mutableListOf<Boolean>()

    private fun showFailedSync() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        val coordinator = testVideoPlaybackCoordinator(context)
        composeRule.setContent {
            CompositionLocalProvider(
                LocalVideoPlaybackCoordinator provides coordinator,
                LocalViewportErrorHostState provides noticeHost,
            ) {
                CommunityScreen(
                    communityName = "android",
                    state = state,
                    onBack = {},
                    redditSubscriptionsState = RedditSubscriptionsUiState(
                        accountStableId = account.stableId,
                        failureKind = RedditSubscriptionsUiFailureKind.TRANSPORT,
                        failureRetryable = true,
                    ),
                    onLoadRedditSubscriptions = { _, force -> subscriptionLoads += force },
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun aFailedSyncSaysSoAndRetryReloadsIt() {
        showFailedSync()

        assertEquals("Couldn't check your communities", noticeHost.currentRequest?.presentation?.title)
        assertEquals(0, composeRule.onAllNodesWithText("Loading…").fetchSemanticsNodes().size)

        composeRule.runOnIdle { noticeHost.retryCurrent() }

        assertEquals(listOf(false, true), subscriptionLoads)
    }

    @Test
    fun pullToRefreshReloadsTheMembershipToo() {
        showFailedSync()

        composeRule.onAllNodes(hasScrollAction())[0].performTouchInput { swipeDown() }
        composeRule.waitForIdle()

        assertEquals(listOf(false, true), subscriptionLoads)
    }
}
