package app.subshell.prototype.auth

import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.LocalFailureKind

// SUBSHELL_COMMUNITYWIKI01_COORDINATOR_V1: the two local FailureDetails the
// ViewModel and its coordinators report for "no client configured" and
// "storage failed"; one copy for the package instead of one per file.
internal fun configurationFailureDetails(): FailureDetails = FailureDetails(
    retryable = false,
    localKind = LocalFailureKind.CONFIGURATION,
)

internal fun storageFailureDetails(): FailureDetails = FailureDetails(
    retryable = true,
    localKind = LocalFailureKind.STORAGE,
)
