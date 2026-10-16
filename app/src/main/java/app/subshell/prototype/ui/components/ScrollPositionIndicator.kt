package app.subshell.prototype.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

// ISSUE205_PASSIVE_SCROLL_POSITION_INDICATOR_V1 (#205): a passive scrollbar
// on the end edge of feeds and comment threads. Google is the reference:
// position and length come from Compose Foundation's own
// LazyListState.scrollIndicatorState, and the look and timing are those of
// Material 3's NonInteractiveScrollbar (material3 1.5, still alpha; we are on
// stable 1.4 — switch to Modifier.nonInteractiveScrollbar when it is stable):
// outline colour at 70 %, 4 dp thick, at least 24 dp and at most 90 % of the
// track, 2 dp inset, faded out 400 ms after scrolling over 250 ms.
// Subshell's own two rules: only the reader's scrolling shows it (a drag and
// its fling; the app's own scrolls in a thread made it flash), and while a
// feed can load more it never reaches the bottom, so it never suggests the
// end. No touch, no semantics (TalkBack already announces the position).

internal const val SCROLL_POSITION_INDICATOR_TAG = "scroll-position-indicator"
internal const val SCROLL_POSITION_MORE_CAN_LOAD_LIMIT = 0.92f
internal const val SCROLLBAR_MAX_LENGTH_FRACTION = 0.9f
private const val SCROLLBAR_THUMB_OPACITY = 0.7f
private const val SCROLLBAR_FADE_DELAY_MS = 400L
private const val SCROLLBAR_FADE_DURATION_MS = 250
private val SCROLLBAR_THICKNESS = 4.dp
private val SCROLLBAR_MIN_LENGTH = 24.dp
private val SCROLLBAR_TRACK_INSET = 2.dp

/** Where the thumb sits on the track, in pixels from the track's start. */
internal data class ScrollPositionThumb(val start: Float, val length: Float)

/**
 * The thumb on a track of [trackLength] px for a scroll at [scrollOffset] of
 * [contentSize] px through a [viewportSize] px viewport (Compose's
 * ScrollIndicatorState); null when the content fits. With [moreCanLoad] the
 * thumb's end stays at or above [SCROLL_POSITION_MORE_CAN_LOAD_LIMIT] of the
 * track.
 */
internal fun scrollPositionThumb(
    scrollOffset: Int,
    contentSize: Int,
    viewportSize: Int,
    trackLength: Float,
    minLength: Float,
    moreCanLoad: Boolean,
): ScrollPositionThumb? {
    if (trackLength <= 0f || viewportSize <= 0 || contentSize <= viewportSize) return null
    val track = if (moreCanLoad) trackLength * SCROLL_POSITION_MORE_CAN_LOAD_LIMIT else trackLength
    val length = (track * viewportSize / contentSize)
        .coerceIn(minOf(minLength, track), track * SCROLLBAR_MAX_LENGTH_FRACTION)
    val progress = (scrollOffset.toFloat() / (contentSize - viewportSize)).coerceIn(0f, 1f)
    return ScrollPositionThumb(start = progress * (track - length), length = length)
}

@Composable
internal fun ScrollPositionIndicator(
    state: LazyListState,
    moreCanLoad: Boolean,
    modifier: Modifier = Modifier,
) {
    val indicator = state.scrollIndicatorState
    // Only the reader's own scrolling shows the bar: a drag, and the fling
    // that follows it.
    var readerScrolling by remember(state) { mutableStateOf(false) }
    LaunchedEffect(state) {
        state.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) readerScrolling = true
        }
    }
    var shown by remember(state) { mutableStateOf(false) }
    val scrolling = state.isScrollInProgress
    LaunchedEffect(scrolling, readerScrolling) {
        if (scrolling && readerScrolling) {
            shown = true
        } else if (!scrolling) {
            if (shown) {
                delay(SCROLLBAR_FADE_DELAY_MS)
                shown = false
            }
            readerScrolling = false
        }
    }
    val visible by remember(indicator) {
        derivedStateOf {
            indicator != null && indicator.contentSize > indicator.viewportSize
        }
    }
    val alpha by animateFloatAsState(
        targetValue = if (shown && visible) 1f else 0f,
        animationSpec = tween(durationMillis = SCROLLBAR_FADE_DURATION_MS),
        label = "scroll-position-indicator",
    )
    if (indicator == null || alpha == 0f) return
    val color = MaterialTheme.colorScheme.outline
    Canvas(
        modifier = modifier
            .testTag(SCROLL_POSITION_INDICATOR_TAG)
            .clearAndSetSemantics {},
    ) {
        val inset = SCROLLBAR_TRACK_INSET.toPx()
        val thumb = scrollPositionThumb(
            scrollOffset = indicator.scrollOffset,
            contentSize = indicator.contentSize,
            viewportSize = indicator.viewportSize,
            trackLength = size.height - 2 * inset,
            minLength = SCROLLBAR_MIN_LENGTH.toPx(),
            moreCanLoad = moreCanLoad,
        ) ?: return@Canvas
        val width = SCROLLBAR_THICKNESS.toPx()
        drawRoundRect(
            color = color.copy(alpha = SCROLLBAR_THUMB_OPACITY * alpha),
            topLeft = Offset(size.width - width, inset + thumb.start),
            size = Size(width, thumb.length),
            cornerRadius = CornerRadius(width / 2f),
        )
    }
}
