package io.github.brealorg.subshell.ui.screens

import io.github.brealorg.subshell.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.brealorg.subshell.data.account.TextPostWriteFailureKind
import io.github.brealorg.subshell.data.account.TextPostWritePhase
import io.github.brealorg.subshell.data.account.TextPostWriteUiState
import io.github.brealorg.subshell.ui.components.ErrorNotice
import io.github.brealorg.subshell.ui.error.ErrorActionKind
import io.github.brealorg.subshell.ui.error.textPostWriteErrorPresentation

internal fun textPostWriteStateForRequest(
    state: TextPostWriteUiState,
    requestKey: String?,
): TextPostWriteUiState = when {
    state.inProgress -> state
    requestKey != null && state.matchesDraft(requestKey) -> state
    else -> TextPostWriteUiState(completionRevision = state.completionRevision)
}

internal fun textPostWriteNoticeShown(state: TextPostWriteUiState): Boolean =
    state.phase in setOf(
        TextPostWritePhase.FAILED,
        TextPostWritePhase.OUTCOME_UNKNOWN,
    ) && state.validationIssues.isEmpty()

/**
 * COMPOSERERR01_A_REFUSED_POST_SCROLLS_ITS_NOTICE_INTO_VIEW_V1: the write notice
 * is an item near the top of the form; with the body focused and the form
 * scrolled down it stayed above the screen and Post seemed to do nothing (seen
 * on the Pixel 11, 2026-11-18). Each write result scrolls to it once (as
 * LOWUX1 reveals a field issue once).
 */
@Composable
internal fun RevealTextPostWriteNotice(
    writeState: TextPostWriteUiState,
    listState: LazyListState,
    noticeItemIndex: Int,
    draftKey: String,
) {
    var revealedKey by rememberSaveable(draftKey) { mutableStateOf<String?>(null) }
    val noticeKey = if (textPostWriteNoticeShown(writeState)) {
        "${writeState.requestKey}:${writeState.completionRevision}:${writeState.phase}"
    } else {
        null
    }
    LaunchedEffect(noticeKey) {
        if (noticeKey == null || noticeKey == revealedKey) return@LaunchedEffect
        revealedKey = noticeKey
        listState.animateScrollToItem(noticeItemIndex)
    }
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
    sendingMessage: String = stringResource(R.string.all_posting_to_reddit),
) {
    if (!authenticated) return
    if (textPostWriteNoticeShown(writeState)) {
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
                        Text(stringResource(R.string.sw_grant_post_access))
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.sw_not_now))
                    }
                }
                TextPostWritePhase.AUTHORIZING,
                TextPostWritePhase.AWAITING_CALLBACK,
                -> TextButton(onClick = onCancelAuthorization) {
                    Text(stringResource(R.string.sw_cancel_authorization))
                }
                // LOWUI4_UNREACHABLE_FAILED_ROW_REMOVED_V1 (OCB-202): FAILED and
                // OUTCOME_UNKNOWN are shown by the ErrorNotice above (or not at
                // all while validation issues are listed).
                else -> Unit
            }
        }
    }
}

@androidx.compose.runtime.Composable
internal fun textPostWriteStatusMessage(state: TextPostWriteUiState): String? {
    if (state.validationIssues.isNotEmpty()) return null
    return when (state.phase) {
        TextPostWritePhase.IDLE,
        TextPostWritePhase.SUCCEEDED,
        -> null
        TextPostWritePhase.AUTHORIZATION_REQUIRED ->
            stringResource(R.string.scr_post_write_1)
        TextPostWritePhase.AUTHORIZING ->
            stringResource(R.string.scr_post_write_2)
        TextPostWritePhase.AWAITING_CALLBACK ->
            stringResource(R.string.scr_post_write_3)
        TextPostWritePhase.SENDING ->
            stringResource(R.string.scr_post_write_4)
        TextPostWritePhase.RECONCILING ->
            stringResource(R.string.scr_post_write_5)
        TextPostWritePhase.OUTCOME_UNKNOWN ->
            stringResource(R.string.scr_post_write_6)
        TextPostWritePhase.FAILED -> textPostWriteFailureMessage(state)
    }
}

@androidx.compose.runtime.Composable
private fun textPostWriteFailureMessage(state: TextPostWriteUiState): String =
    when (state.failureKind) {
        TextPostWriteFailureKind.CONFIGURATION ->
            stringResource(R.string.scr_post_write_failed_1)
        TextPostWriteFailureKind.ACCOUNT_CHANGED ->
            stringResource(R.string.scr_post_write_failed_2)
        TextPostWriteFailureKind.BROWSER_LAUNCH ->
            stringResource(R.string.scr_post_write_failed_3)
        TextPostWriteFailureKind.CALLBACK ->
            stringResource(R.string.scr_post_write_failed_4)
        TextPostWriteFailureKind.AUTHORIZATION ->
            stringResource(R.string.scr_post_write_failed_5)
        TextPostWriteFailureKind.SESSION ->
            stringResource(R.string.scr_post_write_failed_6)
        TextPostWriteFailureKind.RATE_LIMIT -> state.retryAfterMs
            ?.let { delayMs ->
                stringResource(R.string.scr_post_write_failed_7, ((delayMs + 999L) / 1_000L))
            }
            ?: stringResource(R.string.scr_post_write_failed_8)
        TextPostWriteFailureKind.COMMUNITY_UNAVAILABLE ->
            stringResource(R.string.scr_post_write_failed_9)
        TextPostWriteFailureKind.PERMISSION ->
            stringResource(R.string.scr_post_write_failed_10)
        TextPostWriteFailureKind.SUBMISSION_RESTRICTED ->
            stringResource(R.string.scr_post_write_failed_11)
        TextPostWriteFailureKind.CHALLENGE_REQUIRED ->
            stringResource(R.string.scr_post_write_failed_12)
        TextPostWriteFailureKind.VALIDATION ->
            stringResource(R.string.scr_post_write_failed_13)
        TextPostWriteFailureKind.API ->
            stringResource(R.string.scr_post_write_failed_14)
        TextPostWriteFailureKind.HTTP,
        TextPostWriteFailureKind.TRANSPORT,
        -> stringResource(R.string.scr_post_write_failed_15)
        TextPostWriteFailureKind.ALREADY_SUBMITTING ->
            stringResource(R.string.scr_post_write_failed_16)
        TextPostWriteFailureKind.NOT_POSTED ->
            stringResource(R.string.scr_post_write_failed_17)
        TextPostWriteFailureKind.EARLIER_POST_UNCONFIRMED ->
            stringResource(R.string.scr_post_write_failed_18)
        null -> stringResource(R.string.scr_post_write_failed_19)
    }
