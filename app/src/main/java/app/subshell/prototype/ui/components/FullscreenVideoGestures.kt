package app.subshell.prototype.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.media.AudioManager
import android.provider.Settings
import app.subshell.prototype.ui.symbols.Symbols
import kotlin.math.roundToInt

internal enum class FullscreenVideoGesture {
    BRIGHTNESS,
    VOLUME,
}

internal data class FullscreenVideoGestureOverlayState(
    val gesture: FullscreenVideoGesture,
    val level: Float,
)

internal fun fullscreenVideoGesturesActive(
    fullscreen: Boolean,
    landscape: Boolean,
    masterEnabled: Boolean,
    brightnessEnabled: Boolean,
    volumeEnabled: Boolean,
): Boolean = fullscreen && landscape && masterEnabled && (brightnessEnabled || volumeEnabled)

internal fun fullscreenVideoGestureForPosition(
    x: Float,
    width: Float,
    brightnessEnabled: Boolean,
    volumeEnabled: Boolean,
): FullscreenVideoGesture? {
    if (width <= 0f || x < 0f || x > width) return null
    return if (x < width / 2f) {
        FullscreenVideoGesture.BRIGHTNESS.takeIf { brightnessEnabled }
    } else {
        FullscreenVideoGesture.VOLUME.takeIf { volumeEnabled }
    }
}

internal fun fullscreenGestureLevelAfterDrag(
    current: Float,
    dragAmountPx: Float,
    heightPx: Float,
): Float {
    if (heightPx <= 0f) return current.coerceIn(0f, 1f)
    return (current - (dragAmountPx / heightPx)).coerceIn(0f, 1f)
}

internal fun currentFullscreenBrightness(context: Context, activity: android.app.Activity?): Float {
    val windowBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
    if (windowBrightness in 0f..1f) return windowBrightness
    // LOWUX2_BRIGHTNESS_STARTS_WHERE_THE_SCREEN_IS_V1 (OCB-304): the 0..255
    // setting is not on the window's scale (a half-way slider reads ~30), so
    // the first drag dimmed the screen sharply; Android 9+ keeps the float the
    // window uses.
    runCatching {
        Settings.System.getFloat(context.contentResolver, SCREEN_BRIGHTNESS_FLOAT_SETTING)
    }.getOrNull()?.takeIf { value -> value in 0f..1f }?.let { value -> return value.coerceIn(0.01f, 1f) }
    val systemBrightness = runCatching {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
    }.getOrDefault(128)
    return (systemBrightness / 255f).coerceIn(0.01f, 1f)
}

internal fun setFullscreenWindowBrightness(activity: android.app.Activity?, level: Float) {
    val window = activity?.window ?: return
    val attributes = window.attributes
    attributes.screenBrightness = level.coerceIn(0.01f, 1f)
    window.attributes = attributes
}

internal fun currentMediaVolumeFraction(audioManager: AudioManager): Float {
    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    if (max <= 0) return 0f
    return (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max.toFloat()).coerceIn(0f, 1f)
}

internal fun setMediaVolumeFraction(audioManager: AudioManager, level: Float) {
    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    if (max <= 0) return
    val target = (level.coerceIn(0f, 1f) * max.toFloat()).roundToInt()
    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
}

@Composable
internal fun FullscreenVideoGestureLevelOverlay(
    overlay: FullscreenVideoGestureOverlayState,
    modifier: Modifier = Modifier,
) {
    val percent = (overlay.level.coerceIn(0f, 1f) * 100f).roundToInt()
    Surface(
        modifier = modifier.widthIn(min = 116.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = when (overlay.gesture) {
                    FullscreenVideoGesture.BRIGHTNESS -> Symbols.Brightness6
                    FullscreenVideoGesture.VOLUME -> if (percent == 0) Symbols.VolumeOff else Symbols.VolumeUp
                },
                contentDescription = when (overlay.gesture) {
                    FullscreenVideoGesture.BRIGHTNESS -> "Brightness"
                    FullscreenVideoGesture.VOLUME -> "Media volume"
                },
                modifier = Modifier.size(30.dp),
            )
            Text(text = "$percent%", style = MaterialTheme.typography.titleMedium)
        }
    }
}

internal const val FULLSCREEN_GESTURE_OVERLAY_LINGER_MS = 650L


/** Settings.System's float brightness (hidden constant, Android 9+). */
private const val SCREEN_BRIGHTNESS_FLOAT_SETTING = "screen_brightness_float"
