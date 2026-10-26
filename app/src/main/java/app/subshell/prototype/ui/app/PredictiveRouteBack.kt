package app.subshell.prototype.ui.app

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.subshell.prototype.ui.theme.LocalReduceMotion
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

// ISSUE27_PREDICTIVE_ROUTE_BACK_V1: Back that pops the route stack follows the
// gesture: the current screen shrinks toward the centre and away from the
// swipe edge, over a plain backdrop, and the route pops only when the gesture
// commits. The previous route is not composed behind it (that would put two
// routes, and their video owners, on screen at once).

@Stable
internal class PredictiveRouteBackState {
    val progress = Animatable(0f)
    var swipeEdge by mutableIntStateOf(BackEventCompat.EDGE_LEFT)
}

@Composable
internal fun rememberPredictiveRouteBackState(): PredictiveRouteBackState =
    remember { PredictiveRouteBackState() }

@Composable
internal fun PredictiveRouteBackHandler(
    enabled: Boolean,
    state: PredictiveRouteBackState,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    PredictiveBackHandler(enabled = enabled) { events ->
        try {
            events.collect { event ->
                state.swipeEdge = event.swipeEdge
                state.progress.snapTo(event.progress)
            }
            onBack()
            state.progress.snapTo(0f)
        } catch (cancelled: CancellationException) {
            scope.launch { state.progress.animateTo(0f) }
            throw cancelled
        }
    }
}

/** Scale reached at the end of the gesture. */
internal const val PredictiveRouteBackMinScale = 0.9f

internal fun Modifier.predictiveRouteBack(
    state: PredictiveRouteBackState,
    backdrop: Color,
    reduceMotion: Boolean,
): Modifier =
    if (reduceMotion) {
        this
    } else {
        this
            .drawBehind {
                if (state.progress.value > 0f) drawRect(backdrop)
            }
            .graphicsLayer {
                val progress = state.progress.value
                if (progress > 0f) {
                    val scale = 1f - (1f - PredictiveRouteBackMinScale) * progress
                    scaleX = scale
                    scaleY = scale
                    val direction =
                        if (state.swipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
                    translationX = direction * 8.dp.toPx() * progress
                    shape = RoundedCornerShape(28.dp * progress)
                    clip = true
                }
            }
    }

/**
 * ISSUE249_IN_SCREEN_PAGES_FOLLOW_THE_BACK_GESTURE_V1 (#249): a page that Back
 * leaves (an area of Settings, a modmail conversation, a wiki page, a screen
 * that cleans up as it closes …) follows the back gesture as a route does
 * ([predictiveRouteBack]): it shrinks toward the centre while the gesture
 * runs, and [onBack] runs only when the gesture commits. Pass the page's
 * root layout [predictiveBackPage]. While [enabled] is false the handler
 * stands down, so a guard (unsaved changes, a write in flight) can take Back
 * with a plain BackHandler and no preview.
 */
@Stable
internal class PredictiveBackPageState(internal val gesture: PredictiveRouteBackState) {
    internal var backdrop by mutableStateOf(Color.Unspecified)
    internal var reduceMotion by mutableStateOf(false)
}

@Composable
internal fun rememberPredictiveBackPage(
    enabled: Boolean,
    onBack: () -> Unit,
): PredictiveBackPageState {
    val page = remember { PredictiveBackPageState(PredictiveRouteBackState()) }
    page.backdrop = MaterialTheme.colorScheme.surfaceDim
    page.reduceMotion = LocalReduceMotion.current
    PredictiveRouteBackHandler(enabled = enabled, state = page.gesture, onBack = onBack)
    return page
}

internal fun Modifier.predictiveBackPage(page: PredictiveBackPageState): Modifier =
    predictiveRouteBack(page.gesture, page.backdrop, page.reduceMotion)

/** A page shown only while it is open: composing it registers its Back. */
@Composable
internal fun PredictiveBackPage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val page = rememberPredictiveBackPage(enabled = enabled, onBack = onBack)
    Box(modifier = modifier.predictiveBackPage(page), propagateMinConstraints = true) {
        content()
    }
}
