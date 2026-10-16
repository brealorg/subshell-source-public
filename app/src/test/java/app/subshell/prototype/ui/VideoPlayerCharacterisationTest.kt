package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.components.LocalVideoPlayer
import app.subshell.prototype.ui.components.VideoPlayerPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * VIDEO00: what LocalVideoPlayer shows and does today, pinned before the
 * player is split into smaller files (#227). Behaviour only; no source text.
 * VIDEO04 changed the panel on purpose: it is an M3 bottom sheet and hides
 * Audio/Subtitles when there is nothing to choose.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class VideoPlayerCharacterisationTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val account = AccountSessionSummary(
        stableId = "reddit:me",
        username = "me_user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    private fun signedIn() = SubshellState(
        initialAccountSessions = listOf(account),
        initialActiveAccountId = account.stableId,
        initialInteractionSideConfigured = true,
    )

    private val video: Post = SampleData.posts.first().copy(
        id = "t3_video00",
        title = "Video zero title",
        kind = PostKind.VIDEO,
        localMediaUri = "content://media/picked/video00",
        localMediaMimeType = "video/mp4",
        remoteVideoUri = null,
        remoteVideoHlsUri = null,
        remoteVideoFallbackUri = null,
        mediaAsset = null,
    )

    private fun show(
        state: SubshellState,
        post: Post = video,
        presentation: VideoPlayerPresentation = VideoPlayerPresentation.INLINE,
        onExitFullscreen: (() -> Unit)? = null,
        onLongPress: (() -> Unit)? = null,
        releasedCoordinator: Boolean = false,
    ) {
        ensureWorkManagerInitialized(context)
        val coordinator = VideoPlaybackCoordinator(context).apply { if (releasedCoordinator) release() }
        // The progress poll never ends while a surface is selected; drive frames by hand.
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                LocalVideoPlayer(
                    post = post,
                    state = state,
                    presentation = presentation,
                    onExitFullscreen = onExitFullscreen,
                    onLongPress = onLongPress,
                )
            }
        }
        frames()
    }

    private val videoFrame = SemanticsMatcher("video frame") { node ->
        node.config.getOrNull(SemanticsActions.OnClick)?.label in setOf("Hide video controls", "Show video controls")
    }

    private fun frames() {
        // Enough frames for the playback options sheet to finish moving (VIDEO04).
        repeat(30) { composeRule.mainClock.advanceTimeByFrame() }
    }

    @Test
    fun anUnselectedVideoShowsItsPosterAndPlayStartsIt() {
        val state = signedIn()
        show(state)

        composeRule.onNodeWithContentDescription("Play video").assertIsDisplayed().performClick()
        frames()

        assertEquals(video.id, state.activeAnimatedMediaId)
    }

    @Test
    fun aVideoWithoutSourcesSaysItIsUnavailable() {
        val state = signedIn()
        show(state, post = video.copy(localMediaUri = null, localMediaMimeType = null))

        composeRule.onNodeWithText("Video unavailable").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("Play video").assertCountEquals(0)
    }

    @Test
    fun inlineControlsOfferMuteAndFullscreenButNoPlaybackOptions() {
        val state = signedIn()
        state.playVideo(video.id)
        val mutedBefore = state.feedVideoMuted
        // Released: the player's own state cannot bring the controls up (VIDEO06).
        show(state, releasedCoordinator = true)
        // VIDEO06: a feed video starts without controls; a tap shows them.
        composeRule.onAllNodesWithContentDescription("Enter full screen").assertCountEquals(0)
        composeRule.onNode(videoFrame).performClick()
        frames()

        composeRule.onAllNodesWithContentDescription("Playback options").assertCountEquals(0)
        val muteLabel = if (mutedBefore) "Turn sound on" else "Mute video"
        composeRule.onNodeWithContentDescription(muteLabel).performClick()
        frames()
        assertNotEquals(mutedBefore, state.feedVideoMuted)

        composeRule.onNodeWithContentDescription("Enter full screen").performClick()
        frames()
        assertEquals(video.id, state.selectedMediaPost?.id)
    }

    @Test
    fun fullscreenShowsTitleOneExitButtonAndPlaybackOptions() {
        val state = signedIn()
        state.playVideo(video.id)
        var exits = 0
        show(state, presentation = VideoPlayerPresentation.FULLSCREEN, onExitFullscreen = { exits++ })

        composeRule.onNodeWithText("Video zero title").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Playback options").assertIsDisplayed()
        // VIDEO06: the back arrow is the one way out (there were two buttons).
        val exitButtons = composeRule.onAllNodesWithContentDescription("Exit full screen")
        exitButtons.assertCountEquals(1)
        exitButtons.onFirst().performClick()
        frames()

        assertEquals(1, exits)
    }

    @Test
    fun playbackOptionsOpenSubPanelsAndCloseAgain() {
        val state = signedIn()
        state.playVideo(video.id)
        show(state, presentation = VideoPlayerPresentation.FULLSCREEN, onExitFullscreen = {})

        composeRule.onNodeWithContentDescription("Playback options").performClick()
        frames()
        // The sheet is its own window, which Robolectric does not lay out:
        // its nodes exist but have no size, so they are found, not "displayed".
        composeRule.onNodeWithText("Choices apply to this video session").assertExists()
        composeRule.onNodeWithText("Quality").assertExists()
        // VIDEO11: Technical details is behind a setting, off by default.
        composeRule.onAllNodesWithText("Technical details").assertCountEquals(0)
        // VIDEO04: no tracks to choose from, so no Audio or Subtitles row.
        composeRule.onAllNodesWithText("Audio").assertCountEquals(0)
        composeRule.onAllNodesWithText("Subtitles").assertCountEquals(0)
        // The panel replaces the transport controls.
        composeRule.onAllNodesWithContentDescription("Exit full screen").assertCountEquals(0)

        composeRule.onNodeWithText("Quality").performClick()
        frames()
        composeRule.onNodeWithText("Auto").assertExists()
        composeRule.onAllNodesWithText("Choices apply to this video session").assertCountEquals(0)

        composeRule.onNodeWithContentDescription("Back to playback options").performClick()
        frames()
        composeRule.onNodeWithText("Choices apply to this video session").assertExists()

        composeRule.onNodeWithContentDescription("Close playback options").performClick()
        frames()
        composeRule.onAllNodesWithText("Choices apply to this video session").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Playback options").assertIsDisplayed()
    }

    @Test
    fun longPressIsOfferedAsMediaActionsOnlyWhenGiven() {
        val state = signedIn()
        state.playVideo(video.id)
        var longPresses = 0
        show(state, onLongPress = { longPresses++ })
        val mediaActions = SemanticsMatcher("long press labelled Media actions") { node ->
            node.config.getOrNull(SemanticsActions.OnLongClick)?.label == "Media actions"
        }

        composeRule.onNode(mediaActions).performSemanticsAction(SemanticsActions.OnLongClick)
        frames()

        assertEquals(1, longPresses)
    }
}
