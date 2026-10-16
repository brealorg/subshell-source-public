package app.subshell.prototype.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureSafeRefreshGesturePolicyTest {
    @Test
    fun tapAndSmallFingerDriftRemainAvailableToTheChildClickTarget() {
        assertEquals(
            CaptureSafeRefreshGestureDecision.WAIT,
            captureSafeRefreshGestureDecision(
                eligibleAtGestureStart = true,
                accumulatedX = 1f,
                accumulatedY = 5f,
                touchSlop = 8f,
            ),
        )
    }

    @Test
    fun deliberateVerticalDownwardDragBeyondTouchSlopArmsRefresh() {
        assertEquals(
            CaptureSafeRefreshGestureDecision.ARM,
            captureSafeRefreshGestureDecision(
                eligibleAtGestureStart = true,
                accumulatedX = 2f,
                accumulatedY = 12f,
                touchSlop = 8f,
            ),
        )
    }

    @Test
    fun horizontalDominantGestureCancelsRefreshOwnership() {
        assertEquals(
            CaptureSafeRefreshGestureDecision.CANCEL,
            captureSafeRefreshGestureDecision(
                eligibleAtGestureStart = true,
                accumulatedX = 12f,
                accumulatedY = 9f,
                touchSlop = 8f,
            ),
        )
    }

    @Test
    fun upwardGestureCancelsRefreshOwnership() {
        assertEquals(
            CaptureSafeRefreshGestureDecision.CANCEL,
            captureSafeRefreshGestureDecision(
                eligibleAtGestureStart = true,
                accumulatedX = 0f,
                accumulatedY = -9f,
                touchSlop = 8f,
            ),
        )
    }

    @Test
    fun gestureThatDidNotStartAtTheTopCannotTakeRefreshOwnership() {
        assertEquals(
            CaptureSafeRefreshGestureDecision.CANCEL,
            captureSafeRefreshGestureDecision(
                eligibleAtGestureStart = false,
                accumulatedX = 0f,
                accumulatedY = 24f,
                touchSlop = 8f,
            ),
        )
    }
}
