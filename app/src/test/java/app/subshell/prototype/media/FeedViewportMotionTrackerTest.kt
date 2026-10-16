package app.subshell.prototype.media

import org.junit.Assert.assertEquals
import org.junit.Test

class FeedViewportMotionTrackerTest {
    @Test
    fun trackerMeasuresVariableHeightScrollAndDirectionReversal() {
        val tracker = FeedViewportMotionTracker()
        val first = tracker.update(sample(FeedScrollDirection.DOWN, 400))
        val down = tracker.update(sample(FeedScrollDirection.DOWN, 350))
        val up = tracker.update(sample(FeedScrollDirection.UP, 380))

        assertEquals(0f, first.distanceSinceDirectionChangePx, 0.0001f)
        assertEquals(50f, down.distanceSinceDirectionChangePx, 0.0001f)
        assertEquals(30f, up.distanceSinceDirectionChangePx, 0.0001f)
        assertEquals(0.03f, up.distanceSinceDirectionChangeFraction, 0.0001f)
    }

    @Test
    fun steadyMovementAccumulatesUntilDirectionChanges() {
        val tracker = FeedViewportMotionTracker()

        tracker.update(sample(FeedScrollDirection.DOWN, 400))
        val firstMove = tracker.update(sample(FeedScrollDirection.DOWN, 360))
        val secondMove = tracker.update(sample(FeedScrollDirection.DOWN, 310))
        val reversal = tracker.update(sample(FeedScrollDirection.UP, 335))

        assertEquals(40f, firstMove.distanceSinceDirectionChangePx, 0.0001f)
        assertEquals(90f, secondMove.distanceSinceDirectionChangePx, 0.0001f)
        assertEquals(25f, reversal.distanceSinceDirectionChangePx, 0.0001f)
    }

    // LOW_MEDIA_MOTION_MATCHES_ROWS_BY_KEY_V1 (OCB-645): when a row above the
    // centre leaves the list, the same index names another row; the motion is
    // read from a row both samples hold (by key), not from the index.
    @Test
    fun motionIsReadFromTheSameRowWhenARowAboveLeavesTheList() {
        val tracker = FeedViewportMotionTracker()
        tracker.update(
            keyed(
                FeedViewportItemSample(index = 4, offsetPx = 0, sizePx = 300, key = "a"),
                FeedViewportItemSample(index = 5, offsetPx = 300, sizePx = 400, key = "b"),
            ),
        )
        // "a" was removed: "b" moved up to index 4, "c" came in at index 5.
        val after = tracker.update(
            keyed(
                FeedViewportItemSample(index = 4, offsetPx = 0, sizePx = 400, key = "b"),
                FeedViewportItemSample(index = 5, offsetPx = 400, sizePx = 500, key = "c"),
            ),
        )

        assertEquals(300f, after.distanceSinceDirectionChangePx, 0.0001f)
    }

    private fun keyed(vararg items: FeedViewportItemSample) = FeedViewportMotionSample(
        reportedDirection = FeedScrollDirection.DOWN,
        viewportStartOffsetPx = 0,
        viewportEndOffsetPx = 1_000,
        firstVisibleItemIndex = items.first().index,
        firstVisibleItemScrollOffsetPx = -items.first().offsetPx,
        visibleItems = items.toList(),
    )

    private fun sample(
        direction: FeedScrollDirection,
        itemOffset: Int,
    ): FeedViewportMotionSample = FeedViewportMotionSample(
        reportedDirection = direction,
        viewportStartOffsetPx = 0,
        viewportEndOffsetPx = 1_000,
        firstVisibleItemIndex = 4,
        firstVisibleItemScrollOffsetPx = -itemOffset,
        visibleItems = listOf(
            FeedViewportItemSample(
                index = 4,
                offsetPx = itemOffset,
                sizePx = 600,
            ),
        ),
    )
}
