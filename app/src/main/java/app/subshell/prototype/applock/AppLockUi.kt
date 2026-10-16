package app.subshell.prototype.applock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// ISSUE38_APP_LOCK_V1

/** What Settings needs from the activity-owned app lock. */
internal data class AppLockEntryPoints(
    val settings: AppLockSettings,
    val deviceUnlockAvailable: Boolean,
    val onChange: (AppLockSettings) -> Unit,
    val onRequestUnlock: ((Boolean) -> Unit) -> Unit,
    // ISSUE115_TURNING_PROTECTION_OFF_NEEDS_UNLOCK_V1: the device unlock with
    // its own title and subtitle, for turning a protection off.
    val onRequestUnlockFor: (title: String, subtitle: String, onResult: (Boolean) -> Unit) -> Unit =
        { _, _, onResult -> onResult(false) },
)

/** Null outside MainActivity (previews, tests): Settings then shows no app lock rows. */
internal val LocalAppLock = compositionLocalOf<AppLockEntryPoints?> { null }

/** True while the app lock covers the app. Video waits for unlock before it resumes. */
internal val LocalAppLocked = compositionLocalOf { false }

/**
 * MEDIA07_APP_LOCK_READ_LIVE_AT_START_V1 (#150): the lock state at the moment
 * of the call. A lifecycle callback cannot use [LocalAppLocked]: the lock
 * flips during the same ON_STOP/ON_START dispatch, before any recomposition,
 * so the composed value is still the old one.
 */
internal val LocalAppLockedNow = staticCompositionLocalOf<() -> Boolean> { { false } }

internal fun appLockDelayLabel(minutes: Int): String {
    val normalized = normalizeAppLockDelayMinutes(minutes)
    val hours = normalized / 60
    val rest = normalized % 60
    return when {
        normalized == 0 -> "Immediately"
        hours == 0 -> if (rest == 1) "1 minute" else "$rest minutes"
        rest == 0 -> if (hours == 1) "1 hour" else "$hours hours"
        else -> "$hours h $rest min"
    }
}

/**
 * The app while locked. The cover sits in the app's own window, so nothing of
 * the app is drawn even before the dialog window appears; the dialog window
 * is added last, so it also covers dialogs and sheets that were open.
 */
@Composable
internal fun AppLockOverlay(
    onUnlock: () -> Unit,
    onLeave: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            },
    )
    Dialog(
        onDismissRequest = onLeave,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        AppLockScreen(onUnlock = onUnlock)
    }
}

@Composable
internal fun AppLockScreen(onUnlock: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("app-lock-screen"),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.size(16.dp))
            Text(
                "subshell is locked",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(24.dp))
            Button(onClick = onUnlock) {
                Text("Unlock")
            }
        }
    }
}

/** Presets select at once; Custom takes hours and minutes and is saved with Save. */
@Composable
internal fun AppLockDelayDialog(
    selectedMinutes: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var custom by remember { mutableStateOf(selectedMinutes !in APP_LOCK_PRESET_DELAY_MINUTES) }
    var hoursText by remember { mutableStateOf((selectedMinutes / 60).toString()) }
    var minutesText by remember { mutableStateOf((selectedMinutes % 60).toString()) }
    val customMinutes = appLockDelayMinutes(
        hours = hoursText.toIntOrNull() ?: 0,
        minutes = minutesText.toIntOrNull() ?: 0,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lock after") },
        text = {
            // LOW_APPLOCK_DELAY_OPTIONS_ARE_RADIO_BUTTONS_V1 (OCB-395): TalkBack
            // announces one choice of a group and which one is selected.
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.selectableGroup(),
            ) {
                APP_LOCK_PRESET_DELAY_MINUTES.forEach { minutes ->
                    AppLockDelayOption(
                        label = appLockDelayLabel(minutes),
                        selected = !custom && minutes == selectedMinutes,
                        onClick = { onSelect(minutes) },
                    )
                }
                AppLockDelayOption(
                    label = "Custom",
                    selected = custom,
                    onClick = { custom = true },
                )
                if (custom) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = hoursText,
                            onValueChange = { value -> hoursText = value.filter(Char::isDigit).take(2) },
                            label = { Text("Hours") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = minutesText,
                            onValueChange = { value -> minutesText = value.filter(Char::isDigit).take(2) },
                            label = { Text("Minutes") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        appLockDelayLabel(customMinutes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            if (custom) {
                TextButton(onClick = { onSelect(customMinutes) }) {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun AppLockDelayOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
