package app.subshell.prototype.media

import kotlin.math.abs

/** One visible LazyList item used to estimate movement across variable-height feed rows. */
data class FeedViewportItemSample(
    val index: Int,
    val offsetPx: Int,
    val sizePx: Int,
    // LOW_MEDIA_MOTION_MATCHES_ROWS_BY_KEY_V1 (OCB-645): the row's lazy-list
    // key, so a row inserted or removed above the centre is not read as motion.
    val key: Any? = null,
)

/** Geometry sampled from LazyListState while the feed is moving. */
data class FeedViewportMotionSample(
    val reportedDirection: FeedScrollDirection,
    val viewportStartOffsetPx: Int,
    val viewportEndOffsetPx: Int,
    val firstVisibleItemIndex: Int,
    val firstVisibleItemScrollOffsetPx: Int,
    val visibleItems: List<FeedViewportItemSample>,
)

/**
 * Minimal production input for the RAMPED viewport anchor.
 *
 * Only distance since the latest direction change is retained. Decoder, preload, shadow-profile,
 * ring-buffer and capture telemetry are deliberately absent from production ownership.
 */
data class FeedViewportMotionMetrics(
    val viewportHeightPx: Int = 0,
    val distanceSinceDirectionChangePx: Float = 0f,
) {
    val distanceSinceDirectionChangeFraction: Float
        get() = if (viewportHeightPx > 0) {
            distanceSinceDirectionChangePx / viewportHeightPx
        } else {
            0f
        }
}

/** Converts variable-height LazyList geometry into the reversal distance required by RAMPED. */
internal class FeedViewportMotionTracker {
    private var previousSample: FeedViewportMotionSample? = null
    private var previousDirection = FeedScrollDirection.NONE
    private var distanceSinceDirectionChangePx = 0f

    fun update(sample: FeedViewportMotionSample): FeedViewportMotionMetrics {
        val previous = previousSample
        val deltaPx = previous?.let { estimateScrollDeltaPx(it, sample) } ?: 0f
        val measuredDirection = when {
            deltaPx > DIRECTION_EPSILON_PX -> FeedScrollDirection.DOWN
            deltaPx < -DIRECTION_EPSILON_PX -> FeedScrollDirection.UP
            sample.reportedDirection != FeedScrollDirection.NONE -> sample.reportedDirection
            else -> previousDirection
        }
        val directionChanged = measuredDirection != FeedScrollDirection.NONE &&
            measuredDirection != previousDirection

        distanceSinceDirectionChangePx = when {
            previous == null -> 0f
            directionChanged -> abs(deltaPx)
            measuredDirection == FeedScrollDirection.NONE -> distanceSinceDirectionChangePx
            else -> distanceSinceDirectionChangePx + abs(deltaPx)
        }

        previousSample = sample
        if (measuredDirection != FeedScrollDirection.NONE) previousDirection = measuredDirection

        return FeedViewportMotionMetrics(
            viewportHeightPx =
                (sample.viewportEndOffsetPx - sample.viewportStartOffsetPx).coerceAtLeast(0),
            distanceSinceDirectionChangePx = distanceSinceDirectionChangePx,
        )
    }

    private fun estimateScrollDeltaPx(
        previous: FeedViewportMotionSample,
        current: FeedViewportMotionSample,
    ): Float {
        val previousByIndex = previous.visibleItems.associateBy(FeedViewportItemSample::index)
        val previousByKey = previous.visibleItems
            .filter { it.key != null }
            .associateBy { it.key }
        val matchByKey = current.visibleItems.any { it.key != null && it.key in previousByKey }
        val viewportCenter = (current.viewportStartOffsetPx + current.viewportEndOffsetPx) / 2f
        val common = current.visibleItems
            .mapNotNull { currentItem ->
                val previousItem = if (matchByKey) {
                    currentItem.key?.let(previousByKey::get)
                } else {
                    previousByIndex[currentItem.index]
                }
                previousItem?.let { previousItem ->
                    val distanceFromCenter = abs(
                        currentItem.offsetPx + currentItem.sizePx / 2f - viewportCenter,
                    )
                    Triple(previousItem, currentItem, distanceFromCenter)
                }
            }
            .minByOrNull { it.third }
        if (common != null) {
            val (previousItem, currentItem) = common
            return (previousItem.offsetPx - currentItem.offsetPx).toFloat()
        }

        // Conservative fallback for a large jump with no common visible item.
        return when {
            current.firstVisibleItemIndex > previous.firstVisibleItemIndex ->
                abs(current.firstVisibleItemScrollOffsetPx - previous.firstVisibleItemScrollOffsetPx)
                    .coerceAtLeast(1)
                    .toFloat()
            current.firstVisibleItemIndex < previous.firstVisibleItemIndex ->
                -abs(current.firstVisibleItemScrollOffsetPx - previous.firstVisibleItemScrollOffsetPx)
                    .coerceAtLeast(1)
                    .toFloat()
            else -> (current.firstVisibleItemScrollOffsetPx - previous.firstVisibleItemScrollOffsetPx)
                .toFloat()
        }
    }

    private companion object {
        const val DIRECTION_EPSILON_PX = 0.5f
    }
}
