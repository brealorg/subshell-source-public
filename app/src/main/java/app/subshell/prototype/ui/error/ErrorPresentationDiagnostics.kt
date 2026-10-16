package app.subshell.prototype.ui.error

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.subshell.prototype.data.diagnostics.DiagnosticEventStore
import app.subshell.prototype.data.diagnostics.DiagnosticPresentationKind
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
