package app.subshell.prototype.ui.error

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.subshell.prototype.data.diagnostics.DiagnosticEventStore
import app.subshell.prototype.data.diagnostics.DiagnosticPresentationKind
import app.subshell.prototype.data.error.FailureDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ReportErrorPresentationDiagnostic(
    scopeKey: String?,
    presentation: ErrorPresentation,
) {
    val normalizedScope = scopeKey
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: return
    val appContext = LocalContext.current.applicationContext
    val store = remember(appContext) { DiagnosticEventStore(appContext) }
    val diagnosticKind = when (presentation.kind) {
        ErrorPresentationKind.BLOCKING -> DiagnosticPresentationKind.BLOCKING
        ErrorPresentationKind.DEGRADED -> DiagnosticPresentationKind.DEGRADED
        ErrorPresentationKind.INLINE -> DiagnosticPresentationKind.INLINE
    }

    LaunchedEffect(
        store,
        normalizedScope,
        diagnosticKind,
        presentation,
    ) {
        withContext(Dispatchers.IO) {
            runCatching {
                store.recordPresentedFailure(
                    scopeKey = normalizedScope,
                    presentationKind = diagnosticKind,
                    details = presentation.failureDetails,
                )
            }
        }
    }
}

/**
 * DIAG05_PLAIN_NOTICES_ARE_RECORDED_V1: a failure shown as plain text or a
 * notice card (no ErrorPresentation) is recorded once while [active], with
 * its details, so the report names it like a tray error.
 */
@Composable
internal fun ReportFailureDiagnostic(
    scopeKey: String,
    active: Boolean,
    failureDetails: FailureDetails?,
    kind: DiagnosticPresentationKind = DiagnosticPresentationKind.INLINE,
) {
    if (!active || scopeKey.isBlank()) return
    val appContext = LocalContext.current.applicationContext
    val store = remember(appContext) { DiagnosticEventStore(appContext) }
    LaunchedEffect(store, scopeKey, kind, failureDetails) {
        withContext(Dispatchers.IO) {
            runCatching {
                store.recordPresentedFailure(
                    scopeKey = scopeKey,
                    presentationKind = kind,
                    details = failureDetails,
                )
            }
        }
    }
}
