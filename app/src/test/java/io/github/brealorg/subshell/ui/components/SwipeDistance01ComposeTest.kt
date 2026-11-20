package io.github.brealorg.subshell.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import io.github.brealorg.subshell.ui.SubshellSettings
import io.github.brealorg.subshell.ui.SubshellState
import io.github.brealorg.subshell.ui.SwipeDistance
import io.github.brealorg.subshell.ui.UiText
import io.github.brealorg.subshell.ui.VoteColorPalette
import io.github.brealorg.subshell.ui.VoteControlMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SWIPEDISTANCE01_THE_READER_PICKS_HOW_FAR_A_SWIPE_GOES_V1 (owner 2026-11-18): on the
 * Pixel 11 with a larger display size, 56 dp is a longer physical drag and swipes felt
 * "sluggish". Short (56 dp, Material 3's own), Standard (80 dp) and Long (104 dp) set
 * how far a slow swipe must go; Standard is today's behaviour and the backup keeps it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class SwipeDistance01ComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var commits = 0
    private var distance by mutableStateOf(SwipeDistance.STANDARD)
    private var shown = false

    private fun show(newDistance: SwipeDistance) {
        distance = newDistance
        if (!shown) {
            shown = true
            val upvote = voteSwipeActionVisual(
                direction = 1,
                label = UiText.Plain("Upvote"),
                palette = VoteColorPalette.RED_BLUE,
                mode = VoteControlMode.COMFORTABLE,
                enabled = true,
            )
            composeRule.setContent {
                CompositionLocalProvider(LocalSwipeDistance provides distance) {
                    SwipeActionSurface(
                        enabled = true,
                        leftAction = null,
                        rightAction = upvote,
                        onLeft = {},
                        onRight = { commits += 1 },
                        modifier = Modifier.width(360.dp).height(96.dp),
                    ) { contentModifier ->
                        Box(contentModifier.height(96.dp).background(Color.White).testTag("card"))
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    /**
     * A slow drag (well under the 125 dp/s fling speed), so only the distance counts. The
     * row starts moving after the touch slop, so a drag commits about 15 dp past the
     * threshold (probed on AnchoredDraggable: SHORT 67-72, STANDARD 91-96, LONG 115-120 dp); 84 and 108 dp sit
     * between the steps.
     */
    private fun slowSwipe(distanceDp: Float) {
        composeRule.onNodeWithTag("card").performTouchInput {
            val start = 20.dp.toPx()
            swipeRight(startX = start, endX = start + distanceDp.dp.toPx(), durationMillis = 3_000L)
        }
        composeRule.waitForIdle()
    }

    @Test
    fun shortRunsTheActionWhereStandardDoesNot() {
        show(SwipeDistance.SHORT)
        slowSwipe(84f)
        assertEquals(1, commits)

        commits = 0
        show(SwipeDistance.STANDARD)
        slowSwipe(84f)
        assertEquals(0, commits)
    }

    @Test
    fun longNeedsMoreThanStandard() {
        show(SwipeDistance.STANDARD)
        slowSwipe(108f)
        assertEquals(1, commits)

        commits = 0
        show(SwipeDistance.LONG)
        slowSwipe(108f)
        assertEquals(0, commits)
    }

    @Test
    fun theDistanceIsBackedUpAndOlderBackupsGetStandard() {
        val chosen = SubshellSettings(swipeDistance = SwipeDistance.SHORT)
        val stored = chosen.toStorageMap()
        assertEquals("SHORT", stored[SubshellSettings.SWIPE_DISTANCE])
        assertEquals(SwipeDistance.SHORT, SubshellSettings.fromStorageMap(stored).swipeDistance)
        assertEquals(
            SwipeDistance.STANDARD,
            SubshellSettings.fromStorageMap(stored - SubshellSettings.SWIPE_DISTANCE).swipeDistance,
        )
        assertEquals(SwipeDistance.STANDARD, SubshellSettings().swipeDistance)

        val state = SubshellState(initialInteractionSideConfigured = true)
        state.restoreSettings(chosen)
        assertEquals(SwipeDistance.SHORT, state.swipeDistance)
        assertEquals(SwipeDistance.SHORT, state.captureSettings().swipeDistance)
    }

    // SWIPEDISTANCE02_A_CARD_TO_TRY_THE_SWIPE_ON_V1
    @Test
    fun theTryCardSaysWhatTheSwipeWouldDoAndSendsNothing() {
        val state = SubshellState(initialInteractionSideConfigured = true)
        composeRule.setContent { SwipeDistanceTryCard(state = state) }
        composeRule.waitForIdle()
        fun shown(text: String) =
            composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

        assertEquals(1, shown("Nothing is sent to Reddit."))
        composeRule.onNodeWithTag(SWIPE_DISTANCE_TRY_CARD_TEST_TAG).performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        assertEquals(1, shown("That swipe: Upvote"))
        composeRule.onNodeWithTag(SWIPE_DISTANCE_TRY_CARD_TEST_TAG).performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        assertEquals(1, shown("That swipe: Downvote"))
    }

    // SWIPEACTION02_THE_ACTION_RUNS_ON_RELEASE_AND_THE_ROW_SPRINGS_BACK_V1
    @Test
    fun oneSwipeRunsTheActionOnceAndTheNextSwipeRunsItAgain() {
        show(SwipeDistance.SHORT)
        slowSwipe(100f)
        assertEquals(1, commits)
        slowSwipe(100f)
        assertEquals(2, commits)
    }

    @Test
    fun aSwipeTakenBackBeforeReleaseRunsNothing() {
        show(SwipeDistance.SHORT)
        composeRule.onNodeWithTag("card").performTouchInput {
            val start = 20.dp.toPx()
            down(androidx.compose.ui.geometry.Offset(start, centerY))
            repeat(40) { moveBy(androidx.compose.ui.geometry.Offset(3.dp.toPx(), 0f), delayMillis = 40) }
            repeat(40) { moveBy(androidx.compose.ui.geometry.Offset(-3.dp.toPx(), 0f), delayMillis = 40) }
            up()
        }
        composeRule.waitForIdle()
        assertEquals(0, commits)
    }

    @Test
    fun theRowSpringsBackWithoutSlidingOff() {
        show(SwipeDistance.SHORT)
        val before = composeRule.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("card").performTouchInput {
            val start = 20.dp.toPx()
            swipeRight(startX = start, endX = start + 100.dp.toPx(), durationMillis = 2_000L)
        }
        // Follow the release frame by frame: the row stops at its reach (half the
        // card: 180 dp here), far from sliding off the card's 360 dp width.
        var farthest = 0f
        repeat(60) {
            composeRule.mainClock.advanceTimeByFrame()
            val left = composeRule.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot.left
            farthest = maxOf(farthest, left - before.left)
        }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertEquals(1, commits)
        val widthDp = with(composeRule.density) { farthest.toDp().value }
        org.junit.Assert.assertTrue("row travelled $widthDp dp after release", widthDp < 200f)
        assertEquals(before.left, composeRule.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot.left, 1f)
    }

    // Seen on the Pixel 11 (2026-11-19): a real vote changes the action's visual
    // (Downvote -> Remove downvote) and switches the vote action off while Reddit
    // answers (votes in flight), right as the row heads back; the row stayed out.
    @Test
    fun theRowComesBackWhenTheActionChangesTheRow() {
        var voted by mutableStateOf(false)
        var inFlight by mutableStateOf(false)
        composeRule.setContent {
            androidx.compose.runtime.LaunchedEffect(inFlight) {
                if (inFlight) {
                    kotlinx.coroutines.delay(400)
                    inFlight = false
                }
            }
            val visual = voteSwipeActionVisual(
                direction = 1,
                label = UiText.Plain(if (voted) "Remove upvote" else "Upvote"),
                palette = VoteColorPalette.RED_BLUE,
                mode = VoteControlMode.COMFORTABLE,
                enabled = !inFlight,
            )
            CompositionLocalProvider(LocalSwipeDistance provides SwipeDistance.STANDARD) {
                SwipeActionSurface(
                    enabled = true,
                    leftAction = null,
                    rightAction = visual,
                    onLeft = {},
                    onRight = { voted = !voted; inFlight = true; commits += 1 },
                    modifier = Modifier.width(360.dp).height(96.dp),
                ) { contentModifier ->
                    Box(contentModifier.height(96.dp).background(Color.White).testTag("card"))
                }
            }
        }
        composeRule.waitForIdle()
        val restLeft = composeRule.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot.left
        repeat(2) { round ->
            // Like Reddit answering the vote: the action is back before the next swipe
            // (SWIPEACTION03: a row with no enabled action is not dragged).
            composeRule.waitUntil(5_000) { !inFlight }
            slowSwipe(130f)
            assertEquals(round + 1, commits)
            assertEquals(
                restLeft,
                composeRule.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot.left,
                1f,
            )
        }
    }

    // SWIPEACTION02_A_GRAB_ON_THE_WAY_BACK_NEVER_STRANDS_THE_ROW_V1 (Pixel 11): a second
    // swipe while the row is on its way back must count, and every later swipe too.
    @Test
    fun aSecondSwipeOnTheWayBackCountsAndTheRowStillComesBack() {
        show(SwipeDistance.STANDARD)
        val restLeft = composeRule.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot.left
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("card").performTouchInput {
            swipeRight(startX = 20.dp.toPx(), endX = 300.dp.toPx(), durationMillis = 80L)
        }
        // Advance until the first action ran, then a little into the way back.
        var frames = 0
        while (commits < 1 && frames < 120) { composeRule.mainClock.advanceTimeByFrame(); frames++ }
        assertEquals(1, commits)
        repeat(2) { composeRule.mainClock.advanceTimeByFrame() }
        val midWay = composeRule.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot.left
        org.junit.Assert.assertTrue("row is on its way back", midWay - restLeft > 10f)
        composeRule.onNodeWithTag("card").performTouchInput {
            swipeRight(startX = 40.dp.toPx(), endX = 320.dp.toPx(), durationMillis = 80L)
        }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertEquals(2, commits)
        assertEquals(restLeft, composeRule.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot.left, 1f)

        slowSwipe(100f)
        assertEquals(3, commits)
        assertEquals(restLeft, composeRule.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot.left, 1f)
    }

    // SWIPEACTION03_A_ROW_WITHOUT_AN_ENABLED_ACTION_IS_NOT_DRAGGED_V1 (review)
    @Test
    fun aRowWhoseActionsAreAllOffDoesNotMove() {
        val off = voteSwipeActionVisual(
            direction = 1,
            label = UiText.Plain("Upvote"),
            palette = VoteColorPalette.RED_BLUE,
            mode = VoteControlMode.COMFORTABLE,
            enabled = false,
        )
        composeRule.setContent {
            SwipeActionSurface(
                enabled = true,
                leftAction = off,
                rightAction = off,
                onLeft = { commits += 1 },
                onRight = { commits += 1 },
                modifier = Modifier.width(360.dp).height(96.dp),
            ) { contentModifier ->
                Box(contentModifier.height(96.dp).background(Color.White).testTag("card"))
            }
        }
        composeRule.waitForIdle()
        val restLeft = composeRule.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot.left
        composeRule.onNodeWithTag("card").performTouchInput {
            down(androidx.compose.ui.geometry.Offset(20.dp.toPx(), centerY))
            repeat(20) { moveBy(androidx.compose.ui.geometry.Offset(5.dp.toPx(), 0f)) }
        }
        composeRule.waitForIdle()
        assertEquals(restLeft, composeRule.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot.left, 1f)
        composeRule.onNodeWithTag("card").performTouchInput { up() }
        assertEquals(0, commits)
    }

    // SWIPEDISTANCE04_THE_THRESHOLD_STAYS_WITHIN_REACH_V1 (review)
    @Test
    fun aNarrowRowStillTakesASlowSwipeAtLong() {
        val upvote = voteSwipeActionVisual(
            direction = 1,
            label = UiText.Plain("Upvote"),
            palette = VoteColorPalette.RED_BLUE,
            mode = VoteControlMode.COMFORTABLE,
            enabled = true,
        )
        composeRule.setContent {
            CompositionLocalProvider(LocalSwipeDistance provides SwipeDistance.LONG) {
                SwipeActionSurface(
                    enabled = true,
                    leftAction = null,
                    rightAction = upvote,
                    onLeft = {},
                    onRight = { commits += 1 },
                    // Half of 160 dp is 80 dp, short of Long's 104 dp.
                    modifier = Modifier.width(160.dp).height(96.dp),
                ) { contentModifier ->
                    Box(contentModifier.height(96.dp).background(Color.White).testTag("card"))
                }
            }
        }
        composeRule.waitForIdle()
        // Released short of the 80 dp anchor (about 70 dp after the touch slop), past 80 %.
        slowSwipe(85f)
        assertEquals(1, commits)
    }
}
