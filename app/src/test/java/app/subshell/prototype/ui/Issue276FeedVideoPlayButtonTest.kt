package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.components.LocalVideoPlayer
import app.subshell.prototype.ui.components.VideoPlayerPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE276_NO_PLAY_BUTTON_WHILE_AUTOPLAY_IS_ABOUT_TO_START_V1: a feed video that
 * will start by itself shows its poster without the round play button during
 * the visibility dwell (a tap anywhere still plays it); the button stays when
 * the video will not start by itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue276FeedVideoPlayButtonTest {
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

    private val video: Post = SampleData.posts.first().copy(
        id = "t3_video276",
        title = "Video title",
        kind = PostKind.VIDEO,
        nsfw = false,
        spoiler = false,
        localMediaUri = "content://media/picked/video276",
        localMediaMimeType = "video/mp4",
        remoteVideoUri = null,
        remoteVideoHlsUri = null,
        remoteVideoFallbackUri = null,
        mediaAsset = null,
    )

    private fun show(autoplay: VideoAutoplay): SubshellState {
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        state.updateVideoAutoplay(autoplay)
        ensureWorkManagerInitialized(context)
        val coordinator = testVideoPlaybackCoordinator(context)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                LocalVideoPlayer(post = video, state = state, presentation = VideoPlayerPresentation.INLINE)
            }
        }
        repeat(10) { composeRule.mainClock.advanceTimeByFrame() }
        return state
    }

    /** The round button is 56 dp or so; the card is the width of the screen. */
    private fun playTargetWidth() = with(composeRule.density) {
        composeRule.onNodeWithContentDescription("Play video").fetchSemanticsNode().size.width.toDp()
    }

    @Test
    fun aVideoThatStartsByItselfShowsNoPlayButtonButATapStillPlaysIt() {
        val state = show(VideoAutoplay.ALWAYS)

        assertTrue("the whole card, not a round button: ${playTargetWidth()}", playTargetWidth() > 200.dp)
        composeRule.onNodeWithContentDescription("Play video").performClick()
        repeat(5) { composeRule.mainClock.advanceTimeByFrame() }
        assertEquals(video.id, state.activeAnimatedMediaId)
    }

    @Test
    fun aVideoThatWaitsForATapKeepsItsPlayButton() {
        show(VideoAutoplay.NEVER)

        assertTrue("a round button: ${playTargetWidth()}", playTargetWidth() < 100.dp)
    }
}
