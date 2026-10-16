package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
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
 * STEP3 (issue17): the first visit to a signed-in feed asks Reddit once even
 * when posts are already cached; coming back to the same feed does not ask
 * again. Home and a community both claim through the state-owned gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3FeedEntryRefreshComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val alice = AccountSessionSummary(
        stableId = "reddit:alice",
        username = "alice",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

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

    @Test
    fun aCommunityAsksOnceOnFirstEntryWithCachedPostsAndNotOnReentry() {
        val state = signedInState()
        val key = communityPostFeedQueryKey(
            account = state.activeAccountSession,
            communityName = "pics",
            sort = state.communitySort,
            timePeriod = state.listingTimePeriod,
        )
        state.ensurePostPaging(key)
        assertTrue("posts should be cached", state.postPagingSnapshot(key).items.isNotEmpty())
        var refreshes = 0
        val shown = mutableStateOf(true)
        val coordinator = VideoPlaybackCoordinator(ApplicationProvider.getApplicationContext<Application>())
        composeRule.setContent {
            SubshellTheme {
                CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                    if (shown.value) {
                        CommunityScreen(
                            communityName = "pics",
                            state = state,
                            onRefreshRedditFeed = { _, _, _, _ -> refreshes += 1 },
                            onBack = {},
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals(1, refreshes)

        shown.value = false
        composeRule.waitForIdle()
        shown.value = true
        composeRule.waitForIdle()

        assertEquals(1, refreshes)
    }

    @Test
    fun homeAsksOnceOnFirstEntryWithCachedPostsAndNotOnReentry() {
        val state = signedInState()
        val key = mainPostFeedQueryKey(
            account = state.activeAccountSession,
            source = state.feedSource,
            sort = state.feedSort,
            timePeriod = state.listingTimePeriod,
        )
        state.ensurePostPaging(key)
        assertTrue("posts should be cached", state.postPagingSnapshot(key).items.isNotEmpty())
        var refreshes = 0
        val shown = mutableStateOf(true)
        val coordinator = VideoPlaybackCoordinator(ApplicationProvider.getApplicationContext<Application>())
        composeRule.setContent {
            SubshellTheme {
                CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                    if (shown.value) {
                        HomeScreen(
                            state = state,
                            onRefreshRedditFeed = { _, _, _, _ -> refreshes += 1 },
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals(1, refreshes)

        shown.value = false
        composeRule.waitForIdle()
        shown.value = true
        composeRule.waitForIdle()

        assertEquals(1, refreshes)
    }
}
