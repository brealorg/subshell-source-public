package app.subshell.prototype.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.subshell.prototype.BuildConfig
import app.subshell.prototype.media.findLifecycleOwner
import app.subshell.prototype.ui.error.ErrorAction
import app.subshell.prototype.ui.error.ErrorActionKind
import app.subshell.prototype.ui.error.ErrorPresentation
import app.subshell.prototype.ui.error.ErrorPresentationKind
import app.subshell.prototype.ui.error.TrayTone
import app.subshell.prototype.ui.error.ViewportErrorHostState
import app.subshell.prototype.update.UpdateManifestRelease
import app.subshell.prototype.update.UpdatePresentationCoordinator
import app.subshell.prototype.update.UpdateStateStore

// ISSUE286_UPDATE_NOTICE_COMES_BACK_AFTER_A_DAY_V1 (#286, owner: tray, one
// day): once the reader has seen the update dialog for a release, the tray
// says a newer version is ready until they update. Closing it (or opening
// Updates from it) hides it for a day; a newer release shows it at once. The
// tray's one action button runs onRetry, so "Update" is that action (as #211).

internal const val UPDATE_AVAILABLE_NOTICE_SCOPE = "update-available"
internal const val UPDATE_NOTICE_HIDDEN_FOR_MILLIS = 24L * 60L * 60L * 1000L

internal fun updateAvailableNoticePresentation(release: UpdateManifestRelease): ErrorPresentation =
    ErrorPresentation(
        kind = ErrorPresentationKind.DEGRADED,
        title = "subshell ${release.versionName} is ready",
        message = "You're on ${BuildConfig.VERSION_NAME}. Update in Settings › Updates.",
        primaryAction = ErrorAction(ErrorActionKind.RETRY, "Update"),
        secondaryAction = ErrorAction(ErrorActionKind.DISMISS, "Dismiss"),
    )

/** True when the tray should offer [release]; a pure decision so it can be tested. */
internal fun updateNoticeShows(
    release: UpdateManifestRelease?,
    currentVersionCode: Int,
    lastPresentedVersionCode: Int,
    dismissedVersionCode: Int,
    dismissedAtEpochMillis: Long,
    nowEpochMillis: Long,
): Boolean {
    if (release == null || release.versionCode <= currentVersionCode) return false
    // The dialog comes first; the notice is for the days after it.
    if (lastPresentedVersionCode < release.versionCode) return false
    if (dismissedVersionCode < release.versionCode) return true
    // A clock set back counts as a day gone, as the update check does.
    return nowEpochMillis < dismissedAtEpochMillis ||
        nowEpochMillis - dismissedAtEpochMillis >= UPDATE_NOTICE_HIDDEN_FOR_MILLIS
}

@Composable
internal fun UpdateAvailableNotice(
    enabled: Boolean,
    noticeHost: ViewportErrorHostState,
    onOpenUpdates: () -> Unit,
    clock: () -> Long = System::currentTimeMillis,
) {
    val context = LocalContext.current
    val store = remember(context) { UpdateStateStore(context.applicationContext) }
    val openUpdates by rememberUpdatedState(onOpenUpdates)
    // A check that finds a release, a closed dialog and every return to the app look again.
    val request by UpdatePresentationCoordinator.request.collectAsStateWithLifecycle()
    var resumes by remember { mutableIntStateOf(0) }
    val lifecycleOwner = remember(context) { context.findLifecycleOwner() }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumes++
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }
    LaunchedEffect(enabled, request, resumes) {
        val release = store.availableRelease(BuildConfig.VERSION_CODE)
        val shows = enabled && updateNoticeShows(
            release = release,
            currentVersionCode = BuildConfig.VERSION_CODE,
            lastPresentedVersionCode = store.lastPresentedUpdateVersionCode(),
            dismissedVersionCode = store.updateNoticeDismissedVersionCode(),
            dismissedAtEpochMillis = store.updateNoticeDismissedAtEpochMillis(),
            nowEpochMillis = clock(),
        )
        if (!shows || release == null) {
            noticeHost.clear(UPDATE_AVAILABLE_NOTICE_SCOPE)
            return@LaunchedEffect
        }
        noticeHost.present(
            scopeKey = UPDATE_AVAILABLE_NOTICE_SCOPE,
            presentation = updateAvailableNoticePresentation(release),
            failureDetails = null,
            tone = TrayTone.INFO,
            onDismiss = { store.dismissUpdateNotice(release.versionCode, clock()) },
            onRetry = {
                store.dismissUpdateNotice(release.versionCode, clock())
                noticeHost.clear(UPDATE_AVAILABLE_NOTICE_SCOPE)
                openUpdates()
            },
        )
    }
}
