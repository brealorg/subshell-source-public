package app.subshell.prototype.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import app.subshell.prototype.ui.ChromeBehavior

// M3223_ONE_ROUTE_TOP_BAR_V1 (#223): every route screen (post, community, profile,
// settings, help, composers …) uses this one Material top app bar. It draws
// behind the status bar (the route content gets no top padding from the app's
// Scaffold), is `surface` at rest and `surfaceContainer` once content scrolls
// under it. The reader's "Top bar" setting decides whether it hides on scroll
// down and comes back on scroll up (enterAlways, default) or stays (pinned).
// The app draws routes without top padding on a phone, so the bar takes the status
// bar inset itself; beside a list pane (expanded layouts) the panes keep it.
internal val LocalRouteTopBarDrawsStatusBar = staticCompositionLocalOf { true }

// M3224_NAVIGATION_RAIL_FROM_MEDIUM_WIDTH_V1: beside the rail, the rail takes
// the start inset (status bar side, cutout), so the bars take only the end.
internal val LocalNavigationRailShown = staticCompositionLocalOf { false }

@Composable
internal fun chromeHorizontalSides(): WindowInsetsSides =
    if (LocalNavigationRailShown.current) WindowInsetsSides.End else WindowInsetsSides.Horizontal

// The reader's "Top bar" setting, provided by SubshellApp for every route.
internal val LocalRouteTopBarBehavior = staticCompositionLocalOf { ChromeBehavior.HIDE_ON_SCROLL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun routeTopBarWindowInsets(): WindowInsets =
    if (LocalRouteTopBarDrawsStatusBar.current) {
        // M3223_CUTOUT_IS_SAFE_V1: safeDrawing also keeps the bar's icons clear of a
        // landscape display cutout (systemBars alone does not).
        WindowInsets.safeDrawing.only(chromeHorizontalSides() + WindowInsetsSides.Top)
    } else {
        WindowInsets(0, 0, 0, 0)
    }

/** Wraps Material's still-experimental scroll behaviour so screens need no opt-in. */
@Stable
internal class RouteTopBarScroll @OptIn(ExperimentalMaterial3Api::class) constructor(
    internal val behavior: TopAppBarScrollBehavior,
) {
    @OptIn(ExperimentalMaterial3Api::class)
    val nestedScrollConnection: NestedScrollConnection
        get() = behavior.nestedScrollConnection
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun rememberRouteTopBarScrollBehavior(
    behavior: ChromeBehavior = LocalRouteTopBarBehavior.current,
    // A list that fits the screen must not hide the bar: at the top a downward
    // drag is the pull-to-refresh gesture, so the bar could never come back.
    canScroll: () -> Boolean = { true },
): RouteTopBarScroll {
    val topBarState = rememberTopAppBarState()
    val scrollBehavior = when (behavior) {
        ChromeBehavior.ALWAYS_SHOW -> TopAppBarDefaults.pinnedScrollBehavior(topBarState, canScroll)
        ChromeBehavior.HIDE_ON_SCROLL -> TopAppBarDefaults.enterAlwaysScrollBehavior(topBarState, canScroll)
    }
    return remember(scrollBehavior) { RouteTopBarScroll(scrollBehavior) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RouteTopBar(
    title: String,
    scrollBehavior: RouteTopBarScroll?,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    windowInsets: WindowInsets = routeTopBarWindowInsets(),
) {
    RouteTopBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        scrollBehavior = scrollBehavior,
        modifier = modifier,
        onBack = onBack,
        actions = actions,
        windowInsets = windowInsets,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RouteTopBar(
    title: @Composable () -> Unit,
    scrollBehavior: RouteTopBarScroll?,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    windowInsets: WindowInsets = routeTopBarWindowInsets(),
) {
    TopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = {
            if (navigationIcon != null) {
                navigationIcon()
            } else if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
            }
        },
        actions = actions,
        windowInsets = windowInsets,
        scrollBehavior = scrollBehavior?.behavior,
    )
}
