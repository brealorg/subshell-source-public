package app.subshell.prototype.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.metrics.performance.PerformanceMetricsState
import app.subshell.prototype.ui.SubshellRoute
import app.subshell.prototype.ui.SubshellState

internal const val JANK_SCREEN_STATE = "screen"

/**
 * ISSUE248_JANK_SUMMARY_PER_FOREGROUND_SESSION_V1 (#248): tells JankStats which
 * screen type is in front, so a slow frame is counted against it. The token is
 * the route's kind (home, post, settings …), never an ID, name or text.
 */
@Composable
internal fun JankScreenStateEffect(state: SubshellState) {
    val view = LocalView.current
    val token = jankScreenToken(state.currentNavigationRoute)
    DisposableEffect(view, token) {
        val holder = PerformanceMetricsState.getHolderForHierarchy(view)
        holder.state?.putState(JANK_SCREEN_STATE, token)
        onDispose { holder.state?.removeState(JANK_SCREEN_STATE) }
    }
}

internal fun jankScreenToken(route: SubshellRoute): String = when (route) {
    is SubshellRoute.Main -> route.destination.name.lowercase()
    is SubshellRoute.Secondary -> route.secondaryDestination.name.lowercase()
    else -> route::class.simpleName.orEmpty()
        .replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
        .lowercase()
        .ifEmpty { "unknown" }
}
