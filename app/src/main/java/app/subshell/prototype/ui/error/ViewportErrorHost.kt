package app.subshell.prototype.ui.error

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import app.subshell.prototype.data.diagnostics.DiagnosticEventStore
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.hasMeaningfulExpandedDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class ViewportErrorRequest internal constructor(
    val scopeKey: String,
    val fingerprint: String,
    val presentation: ErrorPresentation,
    val failureDetails: FailureDetails?,
    val onRetry: (() -> Unit)?,
    val tone: TrayTone = TrayTone.ERROR,
    // ISSUE238_CONFIRMATIONS_DISMISS_THEMSELVES_V1: a one-off confirmation
    // leaves the tray by itself, like a snackbar without an action.
    val dismissesItself: Boolean = false,
)

/**
 * ISSUE222_TRAY_HAS_A_NEUTRAL_TONE_V1 (owner decision): the tray also carries
 * notices that are not errors ("Muted r/x", "Press back again to exit"); they
 * show with an info icon and the ordinary title colour.
 */
internal enum class TrayTone { ERROR, INFO }

@Stable
internal class ViewportErrorHostState {
    var currentRequest by mutableStateOf<ViewportErrorRequest?>(null)
        private set

    var detailsVisible by mutableStateOf(false)
        private set

    // ISSUE88_ERROR_TRAY_KEEPS_EVERY_ACTIVE_SCOPE_V1: the tray had one slot, so
    // a newer error replaced an older one that still applied, and clearing the
    // newer emptied the tray (a failed feed lost its Retry after a vote error
    // came and went). Every scope keeps its own request and its own dismissal;
    // the tray shows the newest active request and falls back to the next one.
    private val activeRequests = LinkedHashMap<String, ViewportErrorRequest>()
    private val dismissedFingerprints = HashMap<String, String>()

    private fun showNewestActive() {
        val newest = activeRequests.values.lastOrNull()
        if (newest?.fingerprint != currentRequest?.fingerprint) {
            detailsVisible = false
        }
        currentRequest = newest
    }

    fun present(
        scopeKey: String,
        presentation: ErrorPresentation,
        failureDetails: FailureDetails?,
        tone: TrayTone = TrayTone.ERROR,
        dismissesItself: Boolean = false,
        onRetry: (() -> Unit)?,
    ) {
        require(scopeKey.isNotBlank())
        require(presentation.kind == ErrorPresentationKind.DEGRADED)

        val fingerprint = buildFingerprint(
            scopeKey = scopeKey,
            presentation = presentation,
            failureDetails = failureDetails,
        )
        if (dismissedFingerprints[scopeKey] == fingerprint) {
            return
        }

        val request = ViewportErrorRequest(
            scopeKey = scopeKey,
            fingerprint = fingerprint,
            presentation = presentation,
            failureDetails = failureDetails,
            onRetry = onRetry.takeIf {
                presentation.primaryAction?.kind == ErrorActionKind.RETRY
            },
            tone = tone,
            dismissesItself = dismissesItself,
        )
        val previous = activeRequests[scopeKey]
        if (previous?.fingerprint == fingerprint) {
            // The same error again (a new retry lambda): keep its place.
            activeRequests[scopeKey] = request
        } else {
            // A new error is the newest.
            activeRequests.remove(scopeKey)
            activeRequests[scopeKey] = request
        }
        showNewestActive()
    }

    fun clear(scopeKey: String) {
        dismissedFingerprints.remove(scopeKey)
        activeRequests.remove(scopeKey)
        showNewestActive()
    }

    fun dismissCurrent() {
        currentRequest?.let { request ->
            dismissedFingerprints[request.scopeKey] = request.fingerprint
            activeRequests.remove(request.scopeKey)
        }
        showNewestActive()
    }

    fun openDetails() {
        detailsVisible = currentRequest?.failureDetails.hasMeaningfulExpandedDetails()
    }

    fun closeDetails() {
        detailsVisible = false
    }

    fun retryCurrent() {
        currentRequest?.onRetry?.invoke()
    }
}

/**
 * A one-off message in the tray (ISSUE222_NO_TOASTS_V1), in place of a toast;
 * clear() first, so the same message can show again after a dismissal.
 */
internal fun ViewportErrorHostState.presentNotice(
    scopeKey: String,
    title: String,
    message: String,
    tone: TrayTone = TrayTone.INFO,
    dismissesItself: Boolean = false,
) {
    clear(scopeKey)
    present(
        scopeKey = scopeKey,
        presentation = ErrorPresentation(
            kind = ErrorPresentationKind.DEGRADED,
            title = title,
            message = message,
        ),
        failureDetails = null,
        onRetry = null,
        tone = tone,
        dismissesItself = dismissesItself,
    )
}

internal val LocalViewportErrorHostState =
    staticCompositionLocalOf<ViewportErrorHostState?> { null }

@Composable
internal fun rememberViewportErrorHostState(): ViewportErrorHostState =
    remember { ViewportErrorHostState() }

@Composable
internal fun ReportViewportError(
    scopeKey: String,
    presentation: ErrorPresentation?,
    failureDetails: FailureDetails?,
    onRetry: (() -> Unit)?,
) {
    val hostState = LocalViewportErrorHostState.current
    val diagnosticAppContext = LocalContext.current.applicationContext
    val diagnosticEventStore = remember(diagnosticAppContext) {
        DiagnosticEventStore(diagnosticAppContext)
    }
    val latestRetry = rememberUpdatedState(onRetry)
    val retryAvailable = onRetry != null

    LaunchedEffect(
        hostState,
        scopeKey,
        presentation,
        failureDetails,
        retryAvailable,
    ) {
        val host = hostState ?: return@LaunchedEffect
        if (presentation == null) {
            host.clear(scopeKey)
        } else {
            host.present(
                scopeKey = scopeKey,
                presentation = presentation,
                failureDetails = failureDetails,
                onRetry = if (retryAvailable) {
                    { latestRetry.value?.invoke() }
                } else {
                    null
                },
            )
            withContext(Dispatchers.IO) {
                runCatching {
                    diagnosticEventStore.recordViewportFailure(
                        scopeKey = scopeKey,
                        details = failureDetails,
                    )
                }
            }
        }
    }

    DisposableEffect(hostState, scopeKey) {
        onDispose {
            hostState?.clear(scopeKey)
        }
    }
}

private fun buildFingerprint(
    scopeKey: String,
    presentation: ErrorPresentation,
    failureDetails: FailureDetails?,
): String = buildString {
    append(scopeKey)
    append('|')
    append(presentation.title)
    append('|')
    append(presentation.message)
    append('|')
    append(presentation.technicalDetail.orEmpty())
    append('|')
    append(failureDetails?.hashCode() ?: 0)
}
