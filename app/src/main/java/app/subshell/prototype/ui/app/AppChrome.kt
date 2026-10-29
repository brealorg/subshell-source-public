package app.subshell.prototype.ui.app

import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.subshell.prototype.ui.ChromeBehavior
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.screens.InboxContextualSelection

/**
 * Directional chrome: the main content's nested-scroll connection that
 * hides the chrome after 56 dp of downward scroll and shows it again after
 * 40 dp upward (or on an overscroll at the top).
 *
 * Moved out of SubshellApp verbatim.
 */
@Composable
internal fun rememberChromeScrollConnection(
    state: SubshellState,
    density: Density,
    // MODMAILCHROME01_A_COVERING_SURFACE_LEAVES_THE_CHROME_ALONE_V1: false while
    // a surface covers the bars (an open modmail thread). Its list and reply
    // field scrolled them away unseen, and back on the inbox they were gone;
    // at the top of a list a drag down could not bring them back.
    chromeShown: Boolean = true,
): NestedScrollConnection {
    val hideThresholdPx = with(density) { 56.dp.toPx() }
    val showThresholdPx = with(density) { 40.dp.toPx() }
    val currentChromeShown by rememberUpdatedState(chromeShown)
    val chromeScrollConnection = remember(state, hideThresholdPx, showThresholdPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!currentChromeShown) return Offset.Zero
                state.onMainContentScroll(
                    deltaPx = -available.y,
                    atTop = false,
                    hideThresholdPx = hideThresholdPx,
                    showThresholdPx = showThresholdPx,
                )
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (!currentChromeShown) return Offset.Zero
                if (available.y > 0f && consumed.y == 0f) state.showChrome()
                return Offset.Zero
            }
        }
    }
    return chromeScrollConnection
}

/**
 * How far the top and bottom chrome are hidden, animated (snapped under
 * reduced motion). The top bar stays shown while inbox activity is
 * selected; either bar stays shown when its behaviour is "always show".
 * The bars read these States in their graphics layers only.
 *
 * Moved out of SubshellApp verbatim; the two `by` delegates SubshellApp
 * reads stay there.
 */
@Composable
internal fun rememberChromeHiddenFractions(
    state: SubshellState,
    activeInboxContextualSelection: InboxContextualSelection?,
): Pair<State<Float>, State<Float>> {
    val topBarShown = activeInboxContextualSelection != null ||
        state.topBarBehavior == ChromeBehavior.ALWAYS_SHOW ||
        state.chromeVisible
    val bottomBarShown = state.bottomBarBehavior == ChromeBehavior.ALWAYS_SHOW || state.chromeVisible
    val motionSpec = if (state.motionReduced) snap<Float>() else tween(durationMillis = 220)
    val topBarHiddenFractionState = animateFloatAsState(
        targetValue = if (topBarShown) 0f else 1f,
        animationSpec = motionSpec,
        label = "top chrome offset",
    )
    val bottomBarHiddenFractionState = animateFloatAsState(
        targetValue = if (bottomBarShown) 0f else 1f,
        animationSpec = motionSpec,
        label = "bottom chrome offset",
    )
    return topBarHiddenFractionState to bottomBarHiddenFractionState
}
