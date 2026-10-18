package app.subshell.prototype.ui.app

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import app.subshell.prototype.ui.SubshellState

/**
 * SETTINGSREVIEW01_REDUCE_MOTION_FOLLOWS_ANDROID_V1 (owner 2026-10-17): keeps
 * [SubshellState.systemRemovesAnimations] equal to Android's "Remove
 * animations" (animator duration scale 0), read at start and again whenever
 * the system setting changes, so reduced motion follows the system as
 * Google's guidance asks.
 */
@Composable
internal fun SystemMotionEffect(state: SubshellState) {
    val resolver = LocalContext.current.contentResolver
    DisposableEffect(resolver) {
        fun read() {
            state.systemRemovesAnimations = systemRemovesAnimations(
                Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f),
            )
        }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = read()
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        read()
        onDispose { resolver.unregisterContentObserver(observer) }
    }
}

internal fun systemRemovesAnimations(animatorDurationScale: Float): Boolean =
    animatorDurationScale == 0f
