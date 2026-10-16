package app.subshell.prototype.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalAccessibilityManager

/**
 * LOWUI7_AUTO_HIDE_FOLLOWS_THE_ACCESSIBILITY_TIMEOUT_V1 (OCB-142/318): chrome
 * that hides itself waits as long as the system's accessibility timeout
 * ("Time to take action") asks for, never shorter than [baseMillis].
 */
@Composable
internal fun rememberAccessibleAutoHideMillis(baseMillis: Long): Long {
    val accessibilityManager = LocalAccessibilityManager.current
    return remember(accessibilityManager, baseMillis) {
        accessibilityManager
            ?.calculateRecommendedTimeoutMillis(
                originalTimeoutMillis = baseMillis,
                containsIcons = true,
                containsText = true,
                containsControls = true,
            )
            ?.coerceAtLeast(baseMillis)
            ?: baseMillis
    }
}

/** Whether TalkBack-style touch exploration is on, kept current by the system's listener. */
@Composable
internal fun rememberTouchExplorationEnabled(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    val manager = remember(context) {
        context.getSystemService(android.view.accessibility.AccessibilityManager::class.java)
    }
    var enabled by remember(manager) { androidx.compose.runtime.mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    androidx.compose.runtime.DisposableEffect(manager) {
        val listener = android.view.accessibility.AccessibilityManager.TouchExplorationStateChangeListener { enabled = it }
        manager?.addTouchExplorationStateChangeListener(listener)
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return enabled
}
