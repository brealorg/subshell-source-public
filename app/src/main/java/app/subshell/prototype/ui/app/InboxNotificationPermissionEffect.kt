package app.subshell.prototype.ui.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import app.subshell.prototype.ui.SubshellState

/**
 * NOTIFY02_ON_BY_DEFAULT_IN_THE_MAIN_APP_V1: with inbox notifications on by
 * default, Android 13+ still needs the reader's permission to show them.
 * While they are on for a signed-in account and the permission is missing,
 * ask once; a refusal turns the setting off (as the Settings switch does), so
 * the question is not repeated and Settings shows the truth.
 */
@Composable
internal fun InboxNotificationPermissionEffect(
    state: SubshellState,
    onRequestPermission: ((Boolean) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(
        state.activeAccountIsAuthenticated,
        state.activeAccountId,
        state.inboxNotificationsEnabled,
    ) {
        if (!state.activeAccountIsAuthenticated || !state.inboxNotificationsEnabled) return@LaunchedEffect
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return@LaunchedEffect
        }
        onRequestPermission { granted ->
            if (!granted) state.updateInboxNotificationsEnabled(false)
        }
    }
}
