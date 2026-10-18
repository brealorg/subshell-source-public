package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.app.AppFloatingActionButton
import app.subshell.prototype.ui.components.LocalVideoPlayer
import app.subshell.prototype.ui.components.SwipeActionSurface
import app.subshell.prototype.ui.components.SwipeActionVisual
import app.subshell.prototype.ui.components.VideoTimeline
import app.subshell.prototype.ui.components.videoTimelineStateDescription
import app.subshell.prototype.ui.symbols.Symbols
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * VIDEO03 (#227): VIDEO03_ONE_OWNER_FOR_THE_SEEK_DRAG_V1,
 * VIDEO03_TIMELINE_READS_AS_TIME_V1, VIDEO03_FAB_STEPS_ASIDE_FOR_INLINE_CONTROLS_V1.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Video03SeekBarTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private val action = SwipeActionVisual(
        label = "Vote",
        icon = Symbols.ThumbUp,
        containerColor = Color.Blue,
        contentColor = Color.White,
        enabled = true,
    )

    private class Recorded {
        val scrubs = mutableListOf<Long>()
        var finished = 0
        val seeks = mutableListOf<Long>()
        var swipes = 0
    }

    /** A swipe row holding a timeline and, below it, plain card content. */
    private fun showTimelineInASwipeRow(durationChangesOnFirstScrub: Boolean = false): Recorded {
        val recorded = Recorded()
        val duration = mutableStateOf(296_000L)
        composeRule.setContent {
            SwipeActionSurface(
                enabled = true,
                leftAction = action,
                rightAction = action.copy(icon = Symbols.ThumbDown),
                onLeft = { recorded.swipes++ },
                onRight = { recorded.swipes++ },
            ) { contentModifier ->
                Column(contentModifier) {
                    VideoTimeline(
                        positionMs = 60_000L,
                        durationMs = duration.value,
                        bufferedPositionMs = 120_000L,
                        seekable = true,
                        scrubbing = recorded.scrubs.isNotEmpty(),
                        size = VideoTimelineSize.STANDARD,
                        onScrub = {
                            recorded.scrubs += it
                            // DASH refines a duration while it plays; the Slider is then rebuilt.
                            if (durationChangesOnFirstScrub) duration.value = 296_480L
                        },
                        onScrubFinished = { recorded.finished++ },
                        onSeekBy = { recorded.seeks += it },
                        modifier = Modifier.testTag("timeline"),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .testTag("card"),
                    )
                }
            }
        }
        return recorded
    }

    @Test
    fun aDragOnTheTimelineSeeksAndNeverSwipesTheRow() {
        val recorded = showTimelineInASwipeRow()
        val cardLeftBefore = composeRule.onNodeWithTag("card").fetchSemanticsNode().positionInRoot.x

        composeRule.onNodeWithTag("timeline").performTouchInput {
            down(centerLeft.copy(x = width * 0.25f))
            repeat(12) { moveBy(androidx.compose.ui.geometry.Offset(width * 0.05f, 0f)) }
        }
        composeRule.waitForIdle()
        val cardLeftDuring = composeRule.onNodeWithTag("card").fetchSemanticsNode().positionInRoot.x
        composeRule.onNodeWithTag("timeline").performTouchInput { up() }
        composeRule.waitForIdle()

        assertTrue("the drag scrubbed", recorded.scrubs.isNotEmpty())
        assertEquals(1, recorded.finished)
        assertEquals("the row did not move", cardLeftBefore, cardLeftDuring)
        assertEquals(0, recorded.swipes)
    }

    @Test
    fun aDurationChangeDuringTheDragDoesNotHandTheDragToTheRow() {
        val recorded = showTimelineInASwipeRow(durationChangesOnFirstScrub = true)
        val cardLeftBefore = composeRule.onNodeWithTag("card").fetchSemanticsNode().positionInRoot.x

        composeRule.onNodeWithTag("timeline").performTouchInput {
            down(centerLeft.copy(x = width * 0.25f))
            repeat(12) { moveBy(androidx.compose.ui.geometry.Offset(width * 0.05f, 0f)) }
        }
        composeRule.waitForIdle()
        val cardLeftDuring = composeRule.onNodeWithTag("card").fetchSemanticsNode().positionInRoot.x
        composeRule.onNodeWithTag("timeline").performTouchInput { up() }
        composeRule.waitForIdle()

        assertEquals("the row did not move", cardLeftBefore, cardLeftDuring)
        assertEquals(0, recorded.swipes)
        assertTrue("the drag kept scrubbing: ${recorded.scrubs}", recorded.scrubs.size > 3)
        assertEquals(1, recorded.finished)
    }

    @Test
    fun theSameDragOnTheCardStillSwipesTheRow() {
        // Control for the test above: the row swipe works outside the timeline.
        val recorded = showTimelineInASwipeRow()
        val cardLeftBefore = composeRule.onNodeWithTag("card").fetchSemanticsNode().positionInRoot.x

        composeRule.onNodeWithTag("card").performTouchInput {
            down(centerLeft.copy(x = width * 0.25f))
            repeat(12) { moveBy(androidx.compose.ui.geometry.Offset(width * 0.05f, 0f)) }
        }
        composeRule.waitForIdle()
        val cardLeftDuring = composeRule.onNodeWithTag("card").fetchSemanticsNode().positionInRoot.x
        composeRule.onNodeWithTag("card").performTouchInput { up() }

        assertTrue("the row moved", cardLeftDuring > cardLeftBefore)
        assertTrue(recorded.scrubs.isEmpty())
    }

    @Test
    fun talkBackReadsTimeAndStepsTenSeconds() {
        val recorded = showTimelineInASwipeRow()
        val node = composeRule.onNodeWithContentDescription("Seek").fetchSemanticsNode()

        assertEquals("1:00 of 4:56", node.config.getOrNull(SemanticsProperties.StateDescription))
        val actions = node.config.getOrNull(SemanticsActions.CustomActions).orEmpty()
        assertEquals(listOf("Back 10 seconds", "Forward 10 seconds"), actions.map { it.label })
        composeRule.runOnIdle { actions.forEach { it.action() } }
        assertEquals(listOf(-10_000L, 10_000L), recorded.seeks)
        assertEquals("0:00 of 0:09", videoTimelineStateDescription(-5L, 9_000L))
    }

    @Test
    fun theCreateButtonStepsAsideWhileTheReaderUsesInlineControls() {
        val state = SubshellState()
        composeRule.setContent {
            AppFloatingActionButton(
                state = state,
                customFeedFabVisible = false,
                createButtonVisible = true,
                createPostButtonVisible = true,
                fabTravelPx = 0f,
                bottomBarHiddenFractionState = mutableFloatStateOf(0f),
                customFeedCreateRequestedState = mutableStateOf(false),
            )
        }
        composeRule.onNodeWithText("Create", useUnmergedTree = true).fetchSemanticsNode()

        composeRule.runOnIdle { state.updateInlineVideoControls("t3_v", visible = true) }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Create", useUnmergedTree = true).assertCountEquals(0)

        // Another video's surface going away does not bring it back early.
        composeRule.runOnIdle { state.updateInlineVideoControls("t3_other", visible = false) }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Create", useUnmergedTree = true).assertCountEquals(0)

        composeRule.runOnIdle { state.updateInlineVideoControls("t3_v", visible = false) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Create", useUnmergedTree = true).fetchSemanticsNode()
    }

    @Test
    fun onlyControlsTheReaderAskedForMoveTheCreateButton() {
        ensureWorkManagerInitialized(context)
        val state = SubshellState()
        val post = SampleData.posts.first().copy(
            id = "t3_video03",
            kind = PostKind.VIDEO,
            localMediaUri = "content://media/picked/video03",
            localMediaMimeType = "video/mp4",
            remoteVideoUri = null,
            remoteVideoHlsUri = null,
            remoteVideoFallbackUri = null,
            mediaAsset = null,
        )
        state.playVideo(post.id)
        // Released: nothing the player does reaches the test.
        val coordinator = testVideoPlaybackCoordinator(context).apply { release() }
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                LocalVideoPlayer(post = post, state = state)
            }
        }
        fun frames() = repeat(4) { composeRule.mainClock.advanceTimeByFrame() }
        frames()
        // The controls an autoplayed video shows by itself do not count.
        assertNull(state.inlineVideoControlsMediaId)

        val frame = composeRule.onNode(
            androidx.compose.ui.test.SemanticsMatcher("video frame") { node ->
                node.config.getOrNull(SemanticsActions.OnClick)?.label in
                    setOf("Hide video controls", "Show video controls")
            },
        )
        // VIDEO06: feed controls start hidden, so one tap is the reader asking.
        frame.performClick()
        frames()
        assertEquals(post.id, state.inlineVideoControlsMediaId)
    }
}
