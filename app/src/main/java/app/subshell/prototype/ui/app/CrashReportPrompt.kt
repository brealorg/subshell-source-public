package app.subshell.prototype.ui.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import app.subshell.prototype.data.diagnostics.CrashKind
import app.subshell.prototype.data.diagnostics.CrashRecord
import app.subshell.prototype.data.diagnostics.CrashRecordStore
import app.subshell.prototype.data.diagnostics.DiagnosticEventStore
import app.subshell.prototype.data.diagnostics.DiagnosticEvents
import app.subshell.prototype.data.diagnostics.recordProcessExitReasons
import app.subshell.prototype.ui.error.ErrorAction
import app.subshell.prototype.ui.error.ErrorActionKind
import app.subshell.prototype.ui.error.ErrorPresentation
import app.subshell.prototype.ui.error.ErrorPresentationKind
import app.subshell.prototype.ui.error.TrayTone
import app.subshell.prototype.ui.error.ViewportErrorHostState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// CRASH01_NEXT_START_OFFERS_A_REPORT_V1 (owner 2026-10-16): after a crash or
// an ANR, the next start says so once in the tray. "Send report" opens
// Diagnostics, where the report is shown before the reader picks where it
// goes; nothing is sent from here. Always on, no setting (owner).

internal const val CRASH_REPORT_PROMPT_SCOPE = "crash-report-prompt"

internal fun crashReportPromptPresentation(records: List<CrashRecord>): ErrorPresentation? {
    if (records.isEmpty()) return null
    val onlyNotResponding = records.all { it.kind == CrashKind.NOT_RESPONDING }
    return ErrorPresentation(
        kind = ErrorPresentationKind.DEGRADED,
        title = if (onlyNotResponding) {
            "subshell stopped responding last time"
        } else {
            "subshell closed unexpectedly last time"
        },
        message = "A diagnostic report helps fix it. It holds no posts, names or messages, " +
            "and you see it before anything is sent.",
        primaryAction = ErrorAction(ErrorActionKind.RETRY, "Send report"),
        secondaryAction = ErrorAction(ErrorActionKind.DISMISS, "Dismiss"),
    )
}

/**
 * Collects the crashes not yet offered (adding Android's ANR and native-crash
 * records first), writes them into the diagnostic history at their own time,
 * and marks them offered.
 */
internal fun takeCrashesToOffer(
    context: Context,
    crashStore: CrashRecordStore = CrashRecordStore(context),
    diagnosticStore: DiagnosticEventStore = DiagnosticEventStore(context),
): List<CrashRecord> {
    runCatching { recordProcessExitReasons(context, crashStore) }
    val fresh = crashStore.takeUnacknowledged()
    fresh.forEach { record -> diagnosticStore.record(DiagnosticEvents.appStoppedUnexpectedly(record)) }
    return fresh
}

@Composable
internal fun CrashReportPrompt(
    noticeHost: ViewportErrorHostState,
    onOpenDiagnostics: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    LaunchedEffect(Unit) {
        val records = withContext(Dispatchers.IO) { takeCrashesToOffer(context) }
        val presentation = crashReportPromptPresentation(records) ?: return@LaunchedEffect
        noticeHost.present(
            scopeKey = CRASH_REPORT_PROMPT_SCOPE,
            presentation = presentation,
            failureDetails = null,
            onRetry = {
                noticeHost.clear(CRASH_REPORT_PROMPT_SCOPE)
                onOpenDiagnostics()
            },
            tone = TrayTone.INFO,
        )
    }
}
