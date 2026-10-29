package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.CustomFeedPostsUiState
import app.subshell.prototype.data.account.CustomFeedSummary
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.error.LocalViewportErrorHostState
import app.subshell.prototype.ui.error.ViewportErrorHostState
import app.subshell.prototype.ui.screens.CustomFeedBrowser
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE275_CUSTOM_FEED_VIDEOS_PLAY_IN_THE_FEED_V1: a custom feed shown at Home
 * after the Home feed plays its videos. Home's viewport had made Home's route
 * the feed that holds playback; the custom feed had no viewport of its own, so
 * a tap on play was revoked at once (a spinner that stopped).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue275CustomFeedVideoComposeTest {
    private val composeRule = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val account = AccountSessionSummary(
        stableId = "reddit:test",
        username = "tester",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    private val feed = CustomFeedSummary(
        path = "/user/tester/m/celebs/",
        displayName = "celebs",
        description = "",
        visibility = "private",
        subredditNames = listOf("celebs"),
    )

    private val video: Post = SampleData.posts.first().copy(
        id = "t3_video275",
        title = "Video title",
        kind = PostKind.VIDEO,
        nsfw = false,
        spoiler = false,
        localMediaUri = "content://media/picked/video275",
        localMediaMimeType = "video/mp4",
        remoteVideoUri = null,
        remoteVideoHlsUri = null,
        remoteVideoFallbackUri = null,
        mediaAsset = null,
    )

    private fun showHomeThenCustomFeed(autoplay: VideoAutoplay): SubshellState {
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        state.updateVideoAutoplay(autoplay)
        ensureWorkManagerInitialized(context)
        val coordinator = testVideoPlaybackCoordinator(context)
        var customFeedShown by mutableStateOf(false)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            SubshellTheme {
                CompositionLocalProvider(
                    LocalVideoPlaybackCoordinator provides coordinator,
                    LocalViewportErrorHostState provides ViewportErrorHostState(),
                ) {
                    if (!customFeedShown) {
                        // The Home feed, as HomeScreen wraps it.
                        GoogleFeedVideoViewport(state = state, sessionKey = "home", modifier = Modifier.fillMaxSize()) {}
                    } else {
                        CustomFeedBrowser(
                            modifier = Modifier.fillMaxSize(),
                            state = state,
                            accountStableId = account.stableId,
                            feed = feed,
                            postsState = CustomFeedPostsUiState(
                                accountStableId = account.stableId,
                                feedPath = feed.path,
                                posts = listOf(video),
                                nextCursor = null,
                                contentRevision = 1L,
                            ),
                            contentPadding = PaddingValues(),
                            showContextHeader = false,
                            onBack = {},
                            onRefresh = {},
                            onLoadInitial = {},
                            onAppend = {},
                        )
                    }
                }
            }
        }
        repeat(5) { composeRule.mainClock.advanceTimeByFrame() }
        customFeedShown = true
        composeRule.waitForIdle()
        repeat(10) { composeRule.mainClock.advanceTimeByFrame() }
        return state
    }

    @Test
    fun aTapOnPlayInACustomFeedAtHomeKeepsTheVideoPlaying() {
        val state = showHomeThenCustomFeed(VideoAutoplay.NEVER)

        composeRule.onNodeWithContentDescription("Play video").performClick()
        repeat(10) { composeRule.mainClock.advanceTimeByFrame() }

        assertEquals(video.id, state.activeAnimatedMediaId)
    }
}
