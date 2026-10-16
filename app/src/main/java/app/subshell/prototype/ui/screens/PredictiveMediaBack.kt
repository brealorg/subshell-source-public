package app.subshell.prototype.ui.screens

import androidx.activity.BackEventCompat
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.subshell.prototype.ui.app.PredictiveRouteBackState

// ISSUE27_PREDICTIVE_MEDIA_BACK_V1: a media viewer's back gesture is about the
// media, not the frame: the black backdrop fades toward the app behind, the
// media itself shrinks with rounded corners, and the chrome hides (callers
// hide it while [PredictiveRouteBackState.progress] is above zero).

/** Scale the media reaches at the end of the gesture. */
internal const val PredictiveMediaBackMinScale = 0.75f

/** Backdrop alpha at the end of the gesture (from 1 = black). */
internal const val PredictiveMediaBackMinBackdropAlpha = 0.3f

/** The viewer's black backdrop, fading while the gesture runs. */
internal fun Modifier.predictiveMediaBackdrop(
    state: PredictiveRouteBackState,
    reduceMotion: Boolean,
): Modifier = drawBehind {
    val progress = if (reduceMotion) 0f else state.progress.value
    val alpha = 1f - (1f - PredictiveMediaBackMinBackdropAlpha) * progress
    drawRect(Color.Black.copy(alpha = alpha))
}

/** The media itself, shrinking away from the swipe edge while the gesture runs. */
internal fun Modifier.predictiveMediaContent(
    state: PredictiveRouteBackState,
    reduceMotion: Boolean,
): Modifier =
    if (reduceMotion) {
        this
    } else {
        graphicsLayer {
            val progress = state.progress.value
            if (progress > 0f) {
                val scale = 1f - (1f - PredictiveMediaBackMinScale) * progress
                scaleX = scale
                scaleY = scale
                val direction = if (state.swipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
                translationX = direction * 16.dp.toPx() * progress
                shape = RoundedCornerShape(24.dp * progress)
                clip = true
            }
        }
    }

/** True while a back gesture is in progress (the chrome hides). */
internal fun PredictiveRouteBackState.gestureInProgress(): Boolean = progress.value > 0f
