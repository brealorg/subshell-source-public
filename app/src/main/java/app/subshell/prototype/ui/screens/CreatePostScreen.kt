package app.subshell.prototype.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.subshell.prototype.data.account.TextPostWriteFailureKind
import app.subshell.prototype.data.account.TextPostWritePhase
import app.subshell.prototype.data.account.TextPostWriteUiState
import app.subshell.prototype.ui.components.ErrorNotice
import app.subshell.prototype.ui.error.ErrorActionKind
import app.subshell.prototype.ui.error.textPostWriteErrorPresentation

internal fun textPostWriteStateForRequest(
    state: TextPostWriteUiState,
    requestKey: String?,
): TextPostWriteUiState = when {
    state.inProgress -> state
    requestKey != null && state.matchesDraft(requestKey) -> state
    else -> TextPostWriteUiState(completionRevision = state.completionRevision)
}

@Composable
internal fun TextPostWriteStatus(
    authenticated: Boolean,
    writeState: TextPostWriteUiState,
    grantEnabled: Boolean,
    retryEnabled: Boolean,
    onGrantAccess: () -> Unit,
    onRetry: () -> Unit,
    onReconcile: () -> Unit = {},
    onCancelAuthorization: () -> Unit,
    onDismiss: () -> Unit,
    sendingMessage: String = "Posting to Reddit…",
) {
    if (!authenticated) return
    if (
        writeState.phase in setOf(
            TextPostWritePhase.FAILED,
            TextPostWritePhase.OUTCOME_UNKNOWN,
        ) && writeState.validationIssues.isEmpty()
    ) {
        ErrorNotice(
            presentation = textPostWriteErrorPresentation(writeState),
            diagnosticScopeKey = "post_create",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            onAction = { action ->
                when (action) {
                    ErrorActionKind.RETRY -> {
                        if (
                            writeState.phase == TextPostWritePhase.OUTCOME_UNKNOWN ||
                            writeState.failureKind ==
                            TextPostWriteFailureKind.EARLIER_POST_UNCONFIRMED
                        ) {
                            onReconcile()
                        } else if (retryEnabled) {
                            onRetry()
                        }
                    }
                    ErrorActionKind.DISMISS -> onDismiss()
                    else -> Unit
                }
            },
        )
        return
    }
    val message = if (writeState.phase == TextPostWritePhase.SENDING) {
        sendingMessage
    } else {
        textPostWriteStatusMessage(writeState)
    } ?: return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row {
            when (writeState.phase) {
                TextPostWritePhase.AUTHORIZATION_REQUIRED -> {
                    TextButton(
                        enabled = grantEnabled,
                        onClick = onGrantAccess,
                    ) {
                        Text("Grant post access")
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Not now")
                    }
                }
                TextPostWritePhase.AUTHORIZING,
                TextPostWritePhase.AWAITING_CALLBACK,
                -> TextButton(onClick = onCancelAuthorization) {
                    Text("Cancel authorization")
                }
                // LOWUI4_UNREACHABLE_FAILED_ROW_REMOVED_V1 (OCB-202): FAILED and
                // OUTCOME_UNKNOWN are shown by the ErrorNotice above (or not at
                // all while validation issues are listed).
                else -> Unit
            }
        }
    }
}

internal fun textPostWriteStatusMessage(state: TextPostWriteUiState): String? {
    if (state.validationIssues.isNotEmpty()) return null
    return when (state.phase) {
        TextPostWritePhase.IDLE,
        TextPostWritePhase.SUCCEEDED,
        -> null
        TextPostWritePhase.AUTHORIZATION_REQUIRED ->
            "Post access is required for this account."
        TextPostWritePhase.AUTHORIZING ->
            "Preparing Reddit authorization…"
        TextPostWritePhase.AWAITING_CALLBACK ->
            "Waiting for Reddit authorization…"
        TextPostWritePhase.SENDING ->
            "Posting to Reddit…"
        TextPostWritePhase.RECONCILING ->
            "Checking Reddit for the submitted post…"
        TextPostWritePhase.OUTCOME_UNKNOWN ->
            "Couldn't confirm whether Reddit created this post. Check your profile before trying again to avoid a duplicate."
        TextPostWritePhase.FAILED -> textPostWriteFailureMessage(state)
    }
}

private fun textPostWriteFailureMessage(state: TextPostWriteUiState): String =
    when (state.failureKind) {
        TextPostWriteFailureKind.CONFIGURATION ->
            "Post publishing is not configured for this account."
        TextPostWriteFailureKind.ACCOUNT_CHANGED ->
            "The active account changed. Review the draft before posting."
        TextPostWriteFailureKind.BROWSER_LAUNCH ->
            "Reddit authorization could not be opened."
        TextPostWriteFailureKind.CALLBACK ->
            "Reddit authorization could not be completed."
        TextPostWriteFailureKind.AUTHORIZATION ->
            "Post access was not granted."
        TextPostWriteFailureKind.SESSION ->
            "The Reddit session could not be refreshed."
        TextPostWriteFailureKind.RATE_LIMIT -> state.retryAfterMs
            ?.let { delayMs ->
                "Reddit asked you to wait about ${((delayMs + 999L) / 1_000L)} seconds."
            }
            ?: "Reddit asked you to wait before posting again."
        TextPostWriteFailureKind.COMMUNITY_UNAVAILABLE ->
            "This community is unavailable."
        TextPostWriteFailureKind.PERMISSION ->
            "This account cannot post in this community."
        TextPostWriteFailureKind.SUBMISSION_RESTRICTED ->
            "This community is not accepting this post."
        TextPostWriteFailureKind.CHALLENGE_REQUIRED ->
            "Reddit requires an additional verification step."
        TextPostWriteFailureKind.VALIDATION ->
            "The post could not be published. Review the highlighted field."
        TextPostWriteFailureKind.API ->
            "Reddit rejected the post."
        TextPostWriteFailureKind.HTTP,
        TextPostWriteFailureKind.TRANSPORT,
        -> "The post could not be sent."
        TextPostWriteFailureKind.ALREADY_SUBMITTING ->
            "This post is already being published."
        TextPostWriteFailureKind.NOT_POSTED ->
            "Reddit doesn't show this post. You can post it again."
        TextPostWriteFailureKind.EARLIER_POST_UNCONFIRMED ->
            "Reddit may have received an earlier post from this account. Check it before posting this one."
        null -> "The post could not be published."
    }
