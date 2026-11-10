package io.github.brealorg.subshell.ui.components

import io.github.brealorg.subshell.ui.error.redditModQueueFailureMessage
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.Role
import io.github.brealorg.subshell.data.account.RedditRemovalMessageType
import io.github.brealorg.subshell.data.account.RedditRemovalStepOutcome
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.brealorg.subshell.data.account.RedditModerationUiFailureKind
import io.github.brealorg.subshell.data.account.RedditModeratorAction
import io.github.brealorg.subshell.data.account.RedditModeratorActionFailureKind
import io.github.brealorg.subshell.data.account.RedditModeratorActionPhase
import io.github.brealorg.subshell.data.account.RedditModeratorActionRequest
import io.github.brealorg.subshell.data.account.RedditModeratorActionUiState
import io.github.brealorg.subshell.data.account.RedditModeratorQueueItem
import io.github.brealorg.subshell.data.account.RedditModeratorQueueKind
import io.github.brealorg.subshell.data.account.RedditRemovalReasonPhase
import io.github.brealorg.subshell.data.account.RedditRemovalReasonUiState
import io.github.brealorg.subshell.ui.symbols.Symbols

// MOD01A22_INBOX_MOD_QUEUE_PRESENTATION_V1
// MODERATION02A2R3_COMPACT_INBOX_REVIEW_CARD_V1
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InboxModeratorQueueItemCard(
    item: RedditModeratorQueueItem,
    actionUiState: RedditModeratorActionUiState,
    onOpen: () -> Unit,
) {
    val itemInProgress = actionUiState.inProgressFor(item.fullname)
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column {
            ModeratorQueueItemSummary(
                item = item,
                inProgress = itemInProgress,
                modifier = Modifier.clickable(enabled = !itemInProgress, onClick = onOpen),
            )
            // ISSUE295_QUEUE_USES_THE_SHEET_V1
            ModeratorQueueItemActions(item, inProgress = actionUiState.inProgress)
        }
    }
}

@Composable
internal fun InboxModeratorQueueFailure(
    failureKind: RedditModerationUiFailureKind,
    httpStatusCode: Int?,
    retryable: Boolean,
    onRetry: () -> Unit,
    // BLOCKPAGE02_SMALLER_READ_SURFACES_NAME_THE_BLOCK_PAGE_V1
    details: io.github.brealorg.subshell.data.error.FailureDetails? = null,
) {
    val message = redditModQueueFailureMessage(failureKind, httpStatusCode, details)
    InboxModerationNotice(
        title = "Couldn't load mod queue",
        message = message,
        actionLabel = "Retry".takeIf { retryable },
        onAction = onRetry,
    )
}

@Composable
private fun InboxModerationNotice(
    title: String,
    message: String,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    // ISSUE222_ONE_INLINE_ERROR_NOTICE_V1
    InlineErrorNotice(
        message = message,
        title = title,
        actionLabel = actionLabel,
        onAction = onAction,
    )
}
