package io.github.brealorg.subshell.ui.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import io.github.brealorg.subshell.data.diagnostics.CrashKind
import io.github.brealorg.subshell.data.diagnostics.CrashRecord
import io.github.brealorg.subshell.data.diagnostics.CrashRecordStore
import io.github.brealorg.subshell.data.diagnostics.DiagnosticEventStore
import io.github.brealorg.subshell.data.diagnostics.DiagnosticEvents
import io.github.brealorg.subshell.data.diagnostics.recordProcessExitReasons
import io.github.brealorg.subshell.ui.error.ErrorAction
import io.github.brealorg.subshell.ui.error.ErrorActionKind
import io.github.brealorg.subshell.ui.error.ErrorPresentation
import io.github.brealorg.subshell.ui.error.ErrorPresentationKind
import io.github.brealorg.subshell.ui.error.TrayTone
import io.github.brealorg.subshell.ui.error.ViewportErrorHostState
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
