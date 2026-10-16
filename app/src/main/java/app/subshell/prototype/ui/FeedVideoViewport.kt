package app.subshell.prototype.ui

import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import app.subshell.prototype.media.FeedScrollDirection
import app.subshell.prototype.media.FeedViewportItemSample
import app.subshell.prototype.media.FeedViewportMotion
import app.subshell.prototype.media.FeedViewportMotionSample
import app.subshell.prototype.media.FeedViewportState
import app.subshell.prototype.media.FeedViewportMotionTracker
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Binds one LazyListState to the application-scoped video owner.
 *
 * LazyListState exposes drag interactions separately from isScrollInProgress, so direct
 * finger movement can move the playback anchor while an inertial fling remains start-gated.
 */
@Composable
fun ObserveFeedVideoViewport(
    listState: LazyListState,
    state: SubshellState,
) {
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    val motionTracker = remember(
        listState,
        state.videoFeedGeneration,
        state.foregroundResumeRequest,
    ) {
        FeedViewportMotionTracker()
    }

    LaunchedEffect(
        listState,
        isDragged,
        state.videoFeedGeneration,
        state.foregroundResumeRequest,
    ) {
        snapshotFlow {
            FeedViewportState(
                motion = when {
                    isDragged -> FeedViewportMotion.DRAGGING
                    listState.isScrollInProgress -> FeedViewportMotion.FLINGING
                    else -> FeedViewportMotion.IDLE
                },
                direction = when {
                    listState.lastScrolledForward -> FeedScrollDirection.DOWN
                    listState.lastScrolledBackward -> FeedScrollDirection.UP
                    else -> FeedScrollDirection.NONE
                },
                atStart = !listState.canScrollBackward,
                atEnd = !listState.canScrollForward,
            )
        }
            .distinctUntilChanged()
            .collect(state::updateFeedViewportState)
    }

    // RAMPED needs only the distance travelled since the latest direction change.
    // This geometry path has no shadow profiles, ring buffer or capture telemetry.
    LaunchedEffect(listState, isDragged, state.videoFeedGeneration) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            FeedViewportMotionSample(
                reportedDirection = when {
                    listState.lastScrolledForward -> FeedScrollDirection.DOWN
                    listState.lastScrolledBackward -> FeedScrollDirection.UP
                    else -> FeedScrollDirection.NONE
                },
                viewportStartOffsetPx = layoutInfo.viewportStartOffset,
                viewportEndOffsetPx = layoutInfo.viewportEndOffset,
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                firstVisibleItemScrollOffsetPx = listState.firstVisibleItemScrollOffset,
                visibleItems = layoutInfo.visibleItemsInfo.map { item ->
                    FeedViewportItemSample(
                        index = item.index,
                        offsetPx = item.offset,
                        sizePx = item.size,
                        key = item.key,
                    )
                },
            )
        }.collect { sample ->
            state.updateFeedViewportMotionMetrics(motionTracker.update(sample))
        }
    }
}
