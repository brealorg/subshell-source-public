package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.PlaybackTrackChoice
import app.subshell.prototype.media.PlaybackTrackKey
import app.subshell.prototype.media.PlaybackTrackOptions
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.media.VideoTrackFormatDiagnostics
import app.subshell.prototype.media.keepsTrackChoicesOnReload
import app.subshell.prototype.media.videoShortEdge
import app.subshell.prototype.media.videoTrackChoiceTitle
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.components.LocalVideoPlayer
import app.subshell.prototype.ui.components.playbackAudioChoiceOffered
import app.subshell.prototype.ui.components.playbackSubtitleChoiceOffered
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * VIDEO04 (#227): VIDEO04_PLAYBACK_OPTIONS_PLACEMENT_V1,
 * VIDEO04_PLAYBACK_OPTIONS_IN_A_BOTTOM_SHEET_V1, VIDEO04_EMPTY_CHOICES_ARE_HIDDEN_V1,
 * VIDEO04_QUALITY_NAMED_BY_THE_SHORT_EDGE_V1, VIDEO04_TRACK_CHOICES_SURVIVE_A_RELOAD_V1.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Video04PlaybackOptionsTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private val video: Post = SampleData.posts.first().copy(
        id = "t3_video04",
        kind = PostKind.VIDEO,
        localMediaUri = "content://media/picked/video04",
        localMediaMimeType = "video/mp4",
        remoteVideoUri = null,
        remoteVideoHlsUri = null,
        remoteVideoFallbackUri = null,
        mediaAsset = null,
    )

    private fun showInline(placement: PlaybackOptionsPlacement, inPostDetail: Boolean): SubshellState {
        ensureWorkManagerInitialized(context)
        val state = SubshellState(initialPlaybackOptionsPlacement = placement)
        state.playVideo(video.id)
        // Released: nothing the player does reaches the test.
        val coordinator = VideoPlaybackCoordinator(context).apply { release() }
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                LocalVideoPlayer(post = video, state = state, inPostDetail = inPostDetail)
            }
        }
        frames()
        return state
    }

    private fun frames() {
        repeat(4) { composeRule.mainClock.advanceTimeByFrame() }
    }

    @Test
    fun thePlacementDecidesWhereTheButtonShows() {
        val default = PlaybackOptionsPlacement.POST_AND_FULLSCREEN
        assertTrue(playbackOptionsButtonShown(default, fullscreen = true, inPostDetail = false))
        assertTrue(playbackOptionsButtonShown(default, fullscreen = false, inPostDetail = true))
        assertFalse(playbackOptionsButtonShown(default, fullscreen = false, inPostDetail = false))
        assertFalse(playbackOptionsButtonShown(PlaybackOptionsPlacement.FULLSCREEN_ONLY, fullscreen = false, inPostDetail = true))
        assertTrue(playbackOptionsButtonShown(PlaybackOptionsPlacement.FULLSCREEN_ONLY, fullscreen = true, inPostDetail = false))
        assertTrue(playbackOptionsButtonShown(PlaybackOptionsPlacement.EVERYWHERE, fullscreen = false, inPostDetail = false))
        assertEquals(PlaybackOptionsPlacement.POST_AND_FULLSCREEN, SubshellState().playbackOptionsPlacement)
    }

    @Test
    fun aPostOpensThePlaybackOptionsInABottomSheet() {
        showInline(PlaybackOptionsPlacement.POST_AND_FULLSCREEN, inPostDetail = true)

        composeRule.onNodeWithContentDescription("Playback options").performClick()
        frames()

        composeRule.onNodeWithText("Choices apply to this video session").fetchSemanticsNode()
        composeRule.onNodeWithText("Quality").fetchSemanticsNode()

        composeRule.onNodeWithContentDescription("Close playback options").performClick()
        repeat(30) { composeRule.mainClock.advanceTimeByFrame() }
        composeRule.onAllNodesWithText("Choices apply to this video session").assertCountEquals(0)
    }

    @Test
    fun theFeedShowsTheButtonOnlyWhenTheReaderAskedForEverywhere() {
        showInline(PlaybackOptionsPlacement.POST_AND_FULLSCREEN, inPostDetail = false)
        composeRule.onAllNodesWithContentDescription("Playback options").assertCountEquals(0)
    }

    @Test
    fun everywhereAlsoPutsTheButtonOnFeedVideos() {
        showInline(PlaybackOptionsPlacement.EVERYWHERE, inPostDetail = false)
        // VIDEO06: feed controls wait for a tap.
        composeRule.onNode(
            androidx.compose.ui.test.SemanticsMatcher("video frame") { node ->
                node.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.OnClick)?.label ==
                    "Show video controls"
            },
        ).performClick()
        frames()
        composeRule.onNodeWithContentDescription("Playback options").fetchSemanticsNode()
    }

    @Test
    fun fullScreenOnlyKeepsThePostClean() {
        showInline(PlaybackOptionsPlacement.FULLSCREEN_ONLY, inPostDetail = true)
        composeRule.onAllNodesWithContentDescription("Playback options").assertCountEquals(0)
    }

    @Test
    fun qualityIsNamedByTheShortEdge() {
        assertEquals("720p", videoTrackChoiceTitle(VideoTrackFormatDiagnostics(width = 720, height = 1280), ordinal = 1))
        assertEquals("720p", videoTrackChoiceTitle(VideoTrackFormatDiagnostics(width = 1280, height = 720), ordinal = 1))
        assertEquals("480p", videoTrackChoiceTitle(VideoTrackFormatDiagnostics(height = 480), ordinal = 1))
        assertEquals(1080, videoShortEdge(1080, 1920))
        assertNull(videoShortEdge(1080, null))
    }

    @Test
    fun rowsWithNothingToChooseAreHidden() {
        fun choice(index: Int) = PlaybackTrackChoice(
            key = PlaybackTrackKey(groupIndex = index, trackIndex = 0),
            title = "Track $index",
            summary = "",
            selected = false,
            sortOrder = index.toLong(),
        )
        assertFalse(playbackAudioChoiceOffered(PlaybackTrackOptions(known = true, audio = listOf(choice(0)))))
        assertTrue(playbackAudioChoiceOffered(PlaybackTrackOptions(known = true, audio = listOf(choice(0), choice(1)))))
        assertFalse(playbackSubtitleChoiceOffered(PlaybackTrackOptions(known = true)))
        assertTrue(playbackSubtitleChoiceOffered(PlaybackTrackOptions(known = true, text = listOf(choice(2)))))
    }

    @Test
    fun onlyTheSameSourceLoadedAgainKeepsTheReadersChoices() {
        assertTrue(keepsTrackChoicesOnReload(fromSourceIndex = 0, toSourceIndex = 0))
        assertTrue(keepsTrackChoicesOnReload(fromSourceIndex = 1, toSourceIndex = 1))
        assertFalse(keepsTrackChoicesOnReload(fromSourceIndex = 0, toSourceIndex = 1))
        assertFalse(keepsTrackChoicesOnReload(fromSourceIndex = 2, toSourceIndex = 0))
    }
}
