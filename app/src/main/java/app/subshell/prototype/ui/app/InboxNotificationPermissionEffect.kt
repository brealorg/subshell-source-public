package app.subshell.prototype.ui.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import app.subshell.prototype.ui.SubshellState

/**
 * NOTIFY02_ON_BY_DEFAULT_IN_THE_MAIN_APP_V1: with inbox notifications on by
 * default, Android 13+ still needs the reader's permission to show them.
 * While they are on for a signed-in account and the permission is missing,
 * ask once; a refusal turns the setting off (as the Settings switch does), so
 * the question is not repeated and Settings shows the truth.
 *
 * RECREATE02_PERMISSION_ANSWER_SURVIVES_RECREATION_V1 (#241): the question
 * goes through the activity result registry, which hands an answer given
 * after a dark-mode switch to the rebuilt screen; the activity's own callback
 * field died with the old activity, so the refusal was lost and the rebuilt
 * screen asked again. "Asked" is saved state, so a rebuild while the question
 * is open does not ask a second time. [onRequestPermission] replaces the
 * registry in tests.
 */
@Composable
internal fun InboxNotificationPermissionEffect(
    state: SubshellState,
    onRequestPermission: (((Boolean) -> Unit) -> Unit)? = null,
) {
    val context = LocalContext.current
    var asked by rememberSaveable(state.activeAccountId) { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) state.updateInboxNotificationsEnabled(false)
    }
    LaunchedEffect(
        state.activeAccountIsAuthenticated,
        state.activeAccountId,
        state.inboxNotificationsEnabled,
        state.commentActionOnboardingVisible,
    ) {
        if (!state.activeAccountIsAuthenticated || !state.inboxNotificationsEnabled) return@LaunchedEffect
        // ISSUE271_ONE_SETUP_FLOW_AFTER_SIGN_IN_V1: the setup flow asks on its own
        // page; after Skip this asks once, as before.
        if (state.commentActionOnboardingVisible) return@LaunchedEffect
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return@LaunchedEffect
        }
        if (asked) return@LaunchedEffect
        asked = true
        if (onRequestPermission != null) {
            onRequestPermission { granted ->
                if (!granted) state.updateInboxNotificationsEnabled(false)
            }
        } else {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
