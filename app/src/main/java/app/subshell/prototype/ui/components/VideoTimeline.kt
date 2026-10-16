package app.subshell.prototype.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.subshell.prototype.media.formatVideoPlaybackTime
import app.subshell.prototype.media.videoBufferedFraction
import app.subshell.prototype.ui.VideoTimelineSize
import kotlin.math.abs

/** How far the TalkBack actions on the timeline move the video. */
internal const val VIDEO_TIMELINE_ACCESSIBILITY_STEP_MS = 10_000L

/**
 * The video seek bar (#227 step 3).
 *
 * VIDEO03_ONE_OWNER_FOR_THE_SEEK_DRAG_V1: a horizontal drag that starts on the
 * timeline belongs to the timeline; the row swipe and the drawer around it never
 * see it (device: a seek drag in the feed also slid the card toward a vote).
 * VIDEO03_BUFFERED_RANGE_IN_THE_TRACK_V1: buffered, played and remaining parts
 * are drawn in the Slider's own track slot, so they line up; the thumb has a
 * fixed slot, so the track does not shrink when a drag starts, and the range
 * holds still during a drag.
 * VIDEO03_TIMELINE_READS_AS_TIME_V1: TalkBack reads "1:23 of 4:56", not a
 * percentage, and offers 10-second steps.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoTimeline(
    positionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long,
    seekable: Boolean,
    scrubbing: Boolean,
    size: VideoTimelineSize,
    onScrub: (Long) -> Unit,
    onScrubFinished: () -> Unit,
    onSeekBy: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // A new range rebuilds the Slider and drops the drag under the finger
    // (DASH refines its duration while playing); the range holds during a drag.
    var rangeDurationMs by remember { mutableLongStateOf(durationMs) }
    if (!scrubbing) rangeDurationMs = durationMs
    val safeDurationMs = rangeDurationMs.coerceAtLeast(1L)
    val bufferedFraction = videoBufferedFraction(
        bufferedPositionMs = bufferedPositionMs,
        durationMs = safeDurationMs,
    )
    val colors = SliderDefaults.colors(
        thumbColor = Color.White,
        activeTrackColor = Color.White,
        inactiveTrackColor = Color.White.copy(alpha = 0.24f),
    )
    val interactionSource = remember { MutableInteractionSource() }
    val timeDescription = videoTimelineStateDescription(positionMs, safeDurationMs)
    // material3 1.5 keeps only the stateful Slider; this is what the stateless
    // one did inside: one state per range, its value set from the position.
    val sliderState = remember(safeDurationMs) {
        SliderState(value = 0f, trackRange = 0f..safeDurationMs.toFloat())
    }
    sliderState.value = positionMs.coerceIn(0L, safeDurationMs).toFloat()
    Slider(
        state = sliderState,
        onValueChange = { value -> onScrub(value.toLong().coerceIn(0L, safeDurationMs)) },
        modifier = modifier
            .fillMaxWidth()
            .height(size.touchTargetHeightDp.dp)
            // VIDEO03_TIMELINE_CLEAR_OF_THE_BACK_GESTURE_V1: 0:00 sits near the
            // screen edge in fullscreen, inside the system back-gesture zone.
            .systemGestureExclusion()
            .keepHorizontalDragsOnTheTimeline()
            .semantics {
                contentDescription = "Seek"
                stateDescription = timeDescription
                if (seekable) {
                    customActions = listOf(
                        CustomAccessibilityAction("Back 10 seconds") {
                            onSeekBy(-VIDEO_TIMELINE_ACCESSIBILITY_STEP_MS)
                            true
                        },
                        CustomAccessibilityAction("Forward 10 seconds") {
                            onSeekBy(VIDEO_TIMELINE_ACCESSIBILITY_STEP_MS)
                            true
                        },
                    )
                }
            },
        enabled = seekable,
        colors = colors,
        interactionSource = interactionSource,
        thumb = {
            val diameterDp = if (scrubbing) size.scrubbingThumbDiameterDp else size.restingThumbDiameterDp
            Box(
                modifier = Modifier.size(size.scrubbingThumbDiameterDp.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(diameterDp.dp)
                        .background(Color.White, CircleShape),
                )
            }
        },
        track = { sliderState ->
            VideoTimelineTrack(
                playedFraction = sliderState.coercedValueAsFraction,
                bufferedFraction = bufferedFraction,
                trackHeightDp = size.trackHeightDp,
            )
        },
        onValueChangeFinished = onScrubFinished,
    )
}

@Composable
private fun VideoTimelineTrack(
    playedFraction: Float,
    bufferedFraction: Float,
    trackHeightDp: Int,
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(trackHeightDp.dp),
    ) {
        val y = size.height / 2f
        val stroke = size.height
        fun segment(fromFraction: Float, toFraction: Float, color: Color) {
            if (toFraction <= fromFraction) return
            drawLine(
                color = color,
                start = Offset(size.width * fromFraction, y),
                end = Offset(size.width * toFraction, y),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
        segment(0f, 1f, Color.White.copy(alpha = 0.24f))
        segment(0f, bufferedFraction.coerceIn(0f, 1f), Color.White.copy(alpha = 0.48f))
        segment(0f, playedFraction, Color.White)
    }
}

/**
 * Takes every move of a gesture once it has turned horizontal, after the
 * Slider (inside this modifier) has seen it, so the row swipe and drawer
 * (outside) cannot start. A vertical gesture is left alone: the feed scrolls.
 */
private fun Modifier.keepHorizontalDragsOnTheTimeline(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
        var horizontal = false
        var decided = false
        var totalX = 0f
        var totalY = 0f
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            val change = event.changes.firstOrNull() ?: break
            if (!decided) {
                val delta = change.positionChangeIgnoreConsumed()
                totalX += delta.x
                totalY += delta.y
                if (abs(totalX) > viewConfiguration.touchSlop || abs(totalY) > viewConfiguration.touchSlop) {
                    decided = true
                    horizontal = abs(totalX) > abs(totalY)
                }
            }
            if (horizontal) event.changes.forEach { it.consume() }
            if (event.changes.none { it.pressed }) break
        }
    }
}

internal fun videoTimelineStateDescription(positionMs: Long, durationMs: Long): String =
    "${formatVideoPlaybackTime(positionMs.coerceIn(0L, durationMs))} of ${formatVideoPlaybackTime(durationMs)}"
