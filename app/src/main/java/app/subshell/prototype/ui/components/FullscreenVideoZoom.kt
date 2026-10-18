package app.subshell.prototype.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import app.subshell.prototype.ui.screens.clampZoomTranslation

// ISSUE231_PINCH_TO_ZOOM_VIDEOS_V1 (#231, owner 2026-10-16): in full screen,
// behind a setting (default off), two fingers zoom the picture 1×–4× around
// the fingers and one finger pans a zoomed picture within its edges. Only the
// TextureView frame is transformed, never the controls. While zoomed the
// other picture gestures pause (one owner per gesture space) and a double-tap
// resets. Zoom is never saved: a new video, a rotation, PiP or leaving full
// screen starts at 1×.

internal data class FullscreenVideoZoom(
    val scale: Float = 1f,
    val translation: Offset = Offset.Zero,
) {
    val zoomed: Boolean get() = scale > FULLSCREEN_VIDEO_ZOOMED_THRESHOLD
}

internal const val FULLSCREEN_VIDEO_MAX_ZOOM = 4f
private const val FULLSCREEN_VIDEO_ZOOMED_THRESHOLD = 1.01f

/**
 * [centroid] is in the viewport's own coordinates; [zoomChange] multiplies the
 * scale around it and [pan] moves the picture, which never leaves its edges.
 */
internal fun FullscreenVideoZoom.transformedBy(
    centroid: Offset,
    zoomChange: Float,
    pan: Offset,
    viewportSize: Size,
    contentAspectRatio: Float?,
): FullscreenVideoZoom {
    val newScale = (scale * zoomChange).coerceIn(1f, FULLSCREEN_VIDEO_MAX_ZOOM)
    if (newScale <= FULLSCREEN_VIDEO_ZOOMED_THRESHOLD) return FullscreenVideoZoom()
    val center = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
    val focus = (centroid.takeIf { it.isSpecified } ?: center) - center
    val scaleRatio = newScale / scale
    // The picture point under the fingers stays under them while the scale changes.
    val next = focus - (focus - translation) * scaleRatio + pan
    return FullscreenVideoZoom(
        scale = newScale,
        translation = clampZoomTranslation(next, newScale, viewportSize, contentAspectRatio),
    )
}

/** The decoded video's shape once the player knows it, else [fallback]. */
internal fun fullscreenVideoZoomAspectRatio(decodedVideoSize: Size, fallback: Float): Float =
    if (decodedVideoSize.isSpecified && decodedVideoSize.width > 0f && decodedVideoSize.height > 0f) {
        decodedVideoSize.width / decodedVideoSize.height
    } else {
        fallback
    }

/** The picture's transform; apply it to the video frame only. */
internal fun Modifier.fullscreenVideoZoomLayer(zoom: () -> FullscreenVideoZoom): Modifier =
    graphicsLayer {
        val current = zoom()
        scaleX = current.scale
        scaleY = current.scale
        translationX = current.translation.x
        translationY = current.translation.y
        transformOrigin = TransformOrigin.Center
    }

/**
 * Pinch and pan for the full-screen picture. It listens in the Initial pass so a
 * second finger always means zoom: from then on it consumes every change of the
 * gesture, which cancels the taps, long press and brightness/volume swipes below.
 * One finger on a picture at 1× is left alone; on a zoomed picture it pans once
 * it has moved past the touch slop, so a tap or double-tap still reaches the taps.
 */
internal fun Modifier.fullscreenVideoPinchToZoom(
    key: Any?,
    zoom: () -> FullscreenVideoZoom,
    onZoom: (centroid: Offset, zoomChange: Float, pan: Offset) -> Unit,
): Modifier = pointerInput(key) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var twoFingers = false
        var panning = false
        var slopPan = Offset.Zero
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = event.changes.count { it.pressed }
            if (pressed >= 2) twoFingers = true
            val pan = event.calculatePan()
            if (!twoFingers && !panning && zoom().zoomed) {
                slopPan += pan
                if (slopPan.getDistance() > viewConfiguration.touchSlop) panning = true
            }
            if (twoFingers || panning) {
                val zoomChange = if (pressed >= 2) event.calculateZoom() else 1f
                if (zoomChange != 1f || pan != Offset.Zero) {
                    onZoom(event.calculateCentroid(useCurrent = false), zoomChange, pan)
                }
                event.changes.fastForEach { it.consume() }
            }
        } while (event.changes.fastAny { it.pressed })
    }
}
