package app.subshell.prototype.ui

import android.app.Application
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.releaseWithoutHanging
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.components.FULLSCREEN_VIDEO_FRAME_TEST_TAG
import app.subshell.prototype.ui.components.FULLSCREEN_VIDEO_MAX_ZOOM
import app.subshell.prototype.ui.components.FULLSCREEN_VIDEO_RESET_ZOOM_LABEL
import app.subshell.prototype.ui.components.FullscreenVideoZoom
import app.subshell.prototype.ui.components.LocalVideoPlayer
import app.subshell.prototype.ui.components.VideoPlayerPresentation
import app.subshell.prototype.ui.components.fullscreenVideoZoomAspectRatio
import app.subshell.prototype.ui.components.transformedBy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #231 ISSUE231_PINCH_TO_ZOOM_VIDEOS_V1: pinch to zoom a full-screen video,
 * behind a setting that is off by default.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue231VideoZoomTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private fun video(id: String): Post = SampleData.posts.first().copy(
        id = id,
        kind = PostKind.VIDEO,
        localMediaUri = "content://media/picked/$id",
        localMediaMimeType = "video/mp4",
        remoteVideoUri = null,
        remoteVideoHlsUri = null,
        remoteVideoFallbackUri = null,
        mediaAsset = null,
        videoWidth = 1280,
        videoHeight = 720,
    )

    private var shownPost by mutableStateOf(video("t3_zoom_a"))
    private var orientation by mutableStateOf(Configuration.ORIENTATION_PORTRAIT)

    private var coordinator: VideoPlaybackCoordinator? = null

    @After
    fun releaseThePlayer() {
        coordinator?.releaseWithoutHanging()
    }

    /** [live] keeps the player, which holding for 2× needs. */
    private fun showFullscreen(state: SubshellState, live: Boolean = false) {
        ensureWorkManagerInitialized(context)
        val coordinator = testVideoPlaybackCoordinator(context).apply { if (!live) release() }
        this.coordinator = coordinator
        state.playVideo(shownPost.id)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                this.orientation = this@Issue231VideoZoomTest.orientation
            }
            CompositionLocalProvider(
                LocalVideoPlaybackCoordinator provides coordinator,
                LocalConfiguration provides configuration,
            ) {
                LocalVideoPlayer(
                    post = shownPost,
                    state = state,
                    presentation = VideoPlayerPresentation.FULLSCREEN,
                    onExitFullscreen = {},
                )
            }
        }
        frames()
    }

    private fun frames(count: Int = 30) {
        repeat(count) { composeRule.mainClock.advanceTimeByFrame() }
    }

    private val videoFrame = SemanticsMatcher("video frame") { node ->
        node.config.getOrNull(SemanticsActions.OnClick)?.label in setOf("Hide video controls", "Show video controls")
    }

    private val resetZoomOffered = SemanticsMatcher("offers Reset zoom") { node ->
        node.config.getOrNull(SemanticsActions.CustomActions)
            ?.any { it.label == FULLSCREEN_VIDEO_RESET_ZOOM_LABEL } == true
    }

    private fun zoomed(): Boolean =
        composeRule.onAllNodes(resetZoomOffered).fetchSemanticsNodes().isNotEmpty()

    private fun pinchOut() {
        composeRule.onNode(videoFrame).performTouchInput {
            // Above the centre: the play button sits in the middle of the picture.
            val fingers = Offset(width / 2f, height * 0.3f)
            pinch(
                start0 = fingers - Offset(20f, 0f),
                end0 = fingers - Offset(220f, 0f),
                start1 = fingers + Offset(20f, 0f),
                end1 = fingers + Offset(220f, 0f),
                durationMillis = 400,
            )
        }
        frames(4)
    }

    private fun doubleTapLeft() {
        composeRule.onNode(videoFrame).performTouchInput {
            click(centerLeft.copy(x = width * 0.2f))
            advanceEventTime(80)
            click(centerLeft.copy(x = width * 0.2f))
        }
        frames(4)
    }

    // --- the transform ---

    @Test
    fun thePointUnderTheFingersStaysUnderThem() {
        val viewport = Size(1000f, 1000f)
        val zoom = FullscreenVideoZoom().transformedBy(
            centroid = Offset(700f, 500f),
            zoomChange = 2f,
            pan = Offset.Zero,
            viewportSize = viewport,
            contentAspectRatio = 1f,
        )
        assertEquals(2f, zoom.scale)
        // The picture point at x = 700 (200 right of centre) sits at 200 × 2 + tx.
        assertEquals(700f, 500f + 200f * zoom.scale + zoom.translation.x, 0.01f)
        assertTrue(zoom.zoomed)
    }

    @Test
    fun zoomStopsAtFourTimesAndPansOnlyWithinThePicture() {
        val viewport = Size(1000f, 2000f)
        val wide = 16f / 9f // fitted 1000 × 562.5 in a tall viewport
        val zoom = FullscreenVideoZoom()
            .transformedBy(Offset(500f, 1000f), 10f, Offset.Zero, viewport, wide)
            .transformedBy(Offset(500f, 1000f), 1f, Offset(10_000f, 10_000f), viewport, wide)
        assertEquals(FULLSCREEN_VIDEO_MAX_ZOOM, zoom.scale)
        assertEquals((1000f * 4f - 1000f) / 2f, zoom.translation.x, 0.01f)
        assertEquals((562.5f * 4f - 2000f) / 2f, zoom.translation.y, 0.01f)
    }

    @Test
    fun pinchingBackToOneTimesCentresThePictureAgain() {
        val viewport = Size(1000f, 1000f)
        val zoomed = FullscreenVideoZoom(scale = 3f, translation = Offset(300f, 0f))
        val back = zoomed.transformedBy(Offset(100f, 100f), 0.2f, Offset(50f, 50f), viewport, 1f)
        assertEquals(FullscreenVideoZoom(), back)
        assertFalse(back.zoomed)
    }

    @Test
    fun theEdgesFollowTheDecodedVideoNotRedditsSize() {
        // Reddit said 9:16; the decoded video is narrower, so ContentFit leaves
        // black side bars that panning must not reveal.
        val decoded = fullscreenVideoZoomAspectRatio(Size(436f, 1000f), fallback = 9f / 16f)
        assertEquals(0.436f, decoded, 0.0001f)
        assertEquals(9f / 16f, fullscreenVideoZoomAspectRatio(Size.Unspecified, fallback = 9f / 16f))
        assertEquals(9f / 16f, fullscreenVideoZoomAspectRatio(Size(0f, 0f), fallback = 9f / 16f))

        val viewport = Size(1080f, 2400f)
        val atTheLeftEdge = FullscreenVideoZoom()
            .transformedBy(Offset(540f, 1200f), 4f, Offset.Zero, viewport, decoded)
            .transformedBy(Offset(540f, 1200f), 1f, Offset(10_000f, 0f), viewport, decoded)
        val pictureWidth = 2400f * decoded
        assertEquals((pictureWidth * 4f - 1080f) / 2f, atTheLeftEdge.translation.x, 0.5f)
    }

    // --- the setting ---

    @Test
    fun theSettingIsOffByDefault() {
        assertFalse(SubshellState().videoPinchToZoom)
    }

    @Test
    fun withoutTheSettingAPinchDoesNotZoom() {
        showFullscreen(SubshellState())
        pinchOut()
        assertFalse(zoomed())
    }

    @Test
    fun withTheSettingAPinchZoomsAndADoubleTapResets() {
        showFullscreen(SubshellState(initialVideoPinchToZoom = true))
        pinchOut()
        assertTrue(zoomed())

        doubleTapLeft()
        assertFalse(zoomed())
    }

    @Test
    fun onlyThePictureIsZoomedNotTheControls() {
        showFullscreen(SubshellState(initialVideoPinchToZoom = true))
        val exitBefore = composeRule.onNodeWithContentDescription("Exit full screen").fetchSemanticsNode().boundsInRoot
        val frameBefore = composeRule.onNodeWithTag(FULLSCREEN_VIDEO_FRAME_TEST_TAG).fetchSemanticsNode()
            .layoutInfo.coordinates.boundsInRoot()
        pinchOut()
        assertTrue(zoomed())
        composeRule.onNodeWithContentDescription("Exit full screen").assertExists()
        assertEquals(exitBefore, composeRule.onNodeWithContentDescription("Exit full screen").fetchSemanticsNode().boundsInRoot)
        val picture = composeRule.onNodeWithTag(FULLSCREEN_VIDEO_FRAME_TEST_TAG).fetchSemanticsNode()
            .layoutInfo.coordinates
        // The frame's own box is unchanged; its content is drawn scaled through the layer.
        assertEquals(frameBefore, picture.boundsInRoot())
        val corner = picture.localToRoot(Offset.Zero)
        assertTrue("the picture's top-left moved outward: $corner", corner.x < frameBefore.left - 1f)
    }

    @Test
    fun aTalkBackReaderCanResetTheZoom() {
        showFullscreen(SubshellState(initialVideoPinchToZoom = true))
        pinchOut()
        val actions = composeRule.onNode(resetZoomOffered).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        composeRule.runOnIdle { actions.first { it.label == FULLSCREEN_VIDEO_RESET_ZOOM_LABEL }.action() }
        frames(4)
        assertFalse(zoomed())
    }

    // --- the other gestures ---

    @Test
    fun whileZoomedADoubleTapResetsInsteadOfSkippingAndAtOneTimesItSkipsAgain() {
        showFullscreen(SubshellState(initialVideoPinchToZoom = true, initialVideoDoubleTapSeek = true))
        pinchOut()
        doubleTapLeft()
        assertFalse(zoomed())
        composeRule.onAllNodesWithText("−10 s").assertCountEquals(0)

        doubleTapLeft()
        composeRule.onNodeWithText("−10 s").assertExists()
    }

    @Test
    fun whileZoomedHoldingDoesNotLendTwoTimes() {
        showFullscreen(SubshellState(initialVideoPinchToZoom = true, initialVideoHoldForDoubleSpeed = true), live = true)
        pinchOut()
        assertTrue(zoomed())
        composeRule.onNode(videoFrame).performTouchInput { down(Offset(width * 0.75f, height * 0.35f)) }
        composeRule.mainClock.advanceTimeBy(800)
        frames(4)
        composeRule.onAllNodesWithText("2× speed").assertCountEquals(0)
        composeRule.onNode(videoFrame).performTouchInput { up() }
        frames(4)
    }

    @Test
    fun atOneTimesOneFingerStillReachesTheOtherGestures() {
        showFullscreen(SubshellState(initialVideoPinchToZoom = true, initialVideoHoldForDoubleSpeed = true), live = true)
        composeRule.onNode(videoFrame).performTouchInput { down(Offset(width * 0.75f, height * 0.35f)) }
        composeRule.mainClock.advanceTimeBy(800)
        frames(4)
        composeRule.onNodeWithText("2× speed").assertExists()
        composeRule.onNode(videoFrame).performTouchInput { up() }
        frames(4)
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-land")
    fun whileZoomedTheBrightnessSwipeIsPausedAndAtOneTimesItIsBack() {
        orientation = Configuration.ORIENTATION_LANDSCAPE
        showFullscreen(SubshellState(initialVideoPinchToZoom = true))
        fun swipeLeftSideUp() {
            composeRule.onNode(videoFrame).performTouchInput {
                swipe(
                    start = Offset(width * 0.2f, height * 0.7f),
                    end = Offset(width * 0.2f, height * 0.3f),
                    durationMillis = 300,
                )
            }
            frames(4)
        }
        pinchOut()
        assertTrue(zoomed())
        swipeLeftSideUp()
        composeRule.onAllNodesWithContentDescription("Brightness").assertCountEquals(0)

        doubleTapLeft()
        assertFalse(zoomed())
        swipeLeftSideUp()
        composeRule.onNodeWithContentDescription("Brightness").assertExists()
    }

    @Test
    fun theEdgesFollowTheVideosShapeAsItIsNow() {
        showFullscreen(SubshellState(initialVideoPinchToZoom = true))
        // A touch first starts the gesture detectors, as on the phone.
        composeRule.onNode(videoFrame).performTouchInput { click(Offset(width * 0.75f, height * 0.35f)) }
        frames()
        // The same video learns a narrower shape after the picture opened.
        composeRule.runOnIdle { shownPost = shownPost.copy(videoWidth = 300, videoHeight = 1000) }
        frames()
        pinchOut()
        assertTrue(zoomed())
        // Pan right as far as it goes (each swipe moves less than the edges allow).
        repeat(4) {
            composeRule.onNode(videoFrame).performTouchInput {
                swipe(start = Offset(width * 0.1f, height * 0.3f), end = Offset(width * 0.9f, height * 0.3f), durationMillis = 300)
            }
            frames(4)
        }

        val picture = composeRule.onNodeWithTag(FULLSCREEN_VIDEO_FRAME_TEST_TAG).fetchSemanticsNode().layoutInfo.coordinates
        val frame = picture.boundsInRoot()
        val w = picture.size.width.toFloat()
        val h = picture.size.height.toFloat()
        val left = picture.localToRoot(Offset.Zero)
        val right = picture.localToRoot(Offset(w, 0f))
        val scale = (right.x - left.x) / w
        val translationX = left.x - frame.left - (w - w * scale) / 2f
        val fittedWidth = minOf(w, h * 0.3f)
        assertEquals((fittedWidth * scale - w) / 2f, translationX, 2f)
    }

    // --- zoom is never kept ---

    @Test
    fun aNewVideoStartsAtOneTimes() {
        val state = SubshellState(initialVideoPinchToZoom = true)
        showFullscreen(state)
        pinchOut()
        assertTrue(zoomed())
        composeRule.runOnIdle {
            shownPost = video("t3_zoom_b")
            state.playVideo(shownPost.id)
        }
        frames()
        composeRule.onNode(videoFrame).assertExists()
        assertFalse(zoomed())
    }

    @Test
    fun aRotationStartsAtOneTimes() {
        showFullscreen(SubshellState(initialVideoPinchToZoom = true))
        pinchOut()
        assertTrue(zoomed())
        composeRule.runOnIdle { orientation = Configuration.ORIENTATION_LANDSCAPE }
        frames()
        assertFalse(zoomed())

        // Owner's phone check: after a rotation the pinch did nothing.
        pinchOut()
        assertTrue("a pinch zooms again after the rotation", zoomed())
    }

    @Test
    fun pictureInPictureDropsTheZoom() {
        val state = SubshellState(initialVideoPinchToZoom = true)
        showFullscreen(state)
        pinchOut()
        assertTrue(zoomed())
        composeRule.runOnIdle { state.updateInPictureInPicture(true) }
        frames()
        composeRule.runOnIdle { state.updateInPictureInPicture(false) }
        frames()
        assertFalse(zoomed())
    }
}
