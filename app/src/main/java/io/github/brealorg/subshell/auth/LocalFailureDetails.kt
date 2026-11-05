package io.github.brealorg.subshell.auth

import io.github.brealorg.subshell.data.error.FailureDetails
import io.github.brealorg.subshell.data.error.LocalFailureKind

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
