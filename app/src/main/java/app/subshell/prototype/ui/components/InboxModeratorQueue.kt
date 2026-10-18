package app.subshell.prototype.ui.components

import app.subshell.prototype.ui.error.redditReadHttpReason
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.Role
import app.subshell.prototype.data.account.RedditRemovalMessageType
import app.subshell.prototype.data.account.RedditRemovalStepOutcome
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
import app.subshell.prototype.data.account.RedditModerationUiFailureKind
import app.subshell.prototype.data.account.RedditModeratorAction
import app.subshell.prototype.data.account.RedditModeratorActionFailureKind
import app.subshell.prototype.data.account.RedditModeratorActionPhase
import app.subshell.prototype.data.account.RedditModeratorActionRequest
import app.subshell.prototype.data.account.RedditModeratorActionUiState
import app.subshell.prototype.data.account.RedditModeratorQueueItem
import app.subshell.prototype.data.account.RedditModeratorQueueKind
import app.subshell.prototype.data.account.RedditRemovalReasonPhase
import app.subshell.prototype.data.account.RedditRemovalReasonUiState
import app.subshell.prototype.ui.symbols.Symbols

// MOD01A22_INBOX_MOD_QUEUE_PRESENTATION_V1
// MODERATION02A2R3_COMPACT_INBOX_REVIEW_CARD_V1
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InboxModeratorQueueItemCard(
    item: RedditModeratorQueueItem,
    accountStableId: String,
    actionUiState: RedditModeratorActionUiState,
    onOpen: () -> Unit,
    onModerate: (RedditModeratorActionRequest) -> Unit,
    onRequestRemove: (RedditModeratorQueueItem) -> Unit,
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
            ModeratorReportsButton(item)
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // MODERATION02A3_QUEUE_CONTENT_CONTROLS_ENTRY_V1
                ModeratorContentControlsButton(item)
                TextButton(
                    modifier = Modifier.heightIn(min = 48.dp),
                    onClick = {
                        onModerate(
                            RedditModeratorActionRequest(
                                accountStableId = accountStableId,
                                communityName = item.communityName,
                                thingFullname = item.fullname,
                                action = RedditModeratorAction.APPROVE,
                            ),
                        )
                    },
                    enabled = !actionUiState.inProgress,
                ) {
                    ModeratorActionIcon(Symbols.Check)
                    Spacer(Modifier.width(6.dp))
                    Text("Approve")
                }
                TextButton(
                    modifier = Modifier.heightIn(min = 48.dp),
                    onClick = { onRequestRemove(item) },
                    enabled = !actionUiState.inProgress,
                ) {
                    ModeratorActionIcon(Symbols.Block)
                    Spacer(Modifier.width(6.dp))
                    Text("Remove")
                }
                ModeratorSpamAction(
                    item = item,
                    accountStableId = accountStableId,
                    actionUiState = actionUiState,
                    onModerate = onModerate,
                )
            }
        }
    }
}

@Composable
internal fun InboxModeratorRemovalDialog(
    item: RedditModeratorQueueItem,
    accountStableId: String,
    actionUiState: RedditModeratorActionUiState,
    removalReasonState: RedditRemovalReasonUiState,
    onLoadRemovalReasons: (String, Boolean) -> Unit,
    onAuthorizeRemovalReasons: () -> Unit,
    onModerate: (RedditModeratorActionRequest) -> Unit,
    onDismiss: () -> Unit,
    // STEP3_SAVED_RESPONSES_ARE_WIRED_V1: required, so the compiler proves the
    // screen passes the moderator's saved responses (was nullable, default null).
    savedResponses: SavedResponseActions,
) {
    // M3225_FORM_DIALOGS_ARE_FULL_SCREEN_ON_PHONES_V1: the same removal form as
    // the mod tools sheet.
    ModeratorRemovalDialog(
        item = item,
        accountStableId = accountStableId,
        communityName = item.communityName,
        displayCommunityName = item.communityName,
        actionUiState = actionUiState,
        removalReasonState = removalReasonState,
        removalReasonsCommunity = item.communityName,
        onLoadRemovalReasons = { force -> onLoadRemovalReasons(item.communityName, force) },
        onAuthorizeRemovalReasons = onAuthorizeRemovalReasons,
        onModerate = onModerate,
        onDismiss = onDismiss,
        savedResponses = savedResponses,
    )
}

@Composable
internal fun InboxModerationActionStatus(
    uiState: RedditModeratorActionUiState,
    onRetry: () -> Unit,
    onAuthorize: () -> Unit,
) {
    when (uiState.phase) {
        RedditModeratorActionPhase.AUTHORIZATION_REQUIRED ->
            InboxModerationNotice(
                title = "Moderator authorization required",
                message = "Reddit needs the modposts permission before this action can be sent.",
                actionLabel = "Authorize",
                onAction = onAuthorize,
            )

        RedditModeratorActionPhase.FAILED -> {
            val message = when {
                uiState.failureKind == RedditModeratorActionFailureKind.AUTHORIZATION &&
                    uiState.httpStatusCode == 403 ->
                    "Reddit denied this moderation action. Your moderator role may not include the required Posts permission."
                uiState.failureKind == RedditModeratorActionFailureKind.OUTCOME_UNKNOWN ->
                    "The outcome could not be confirmed. The mod queue is being refreshed from Reddit before you act again."
                uiState.failureKind == RedditModeratorActionFailureKind.SESSION ->
                    "The Reddit sign-in for this account needs attention."
                uiState.failureKind == RedditModeratorActionFailureKind.TRANSPORT ->
                    "subshell couldn't reach Reddit for this moderation action."
                uiState.failureKind == RedditModeratorActionFailureKind.HTTP ->
                    "Reddit rejected this moderation action."
                else -> "This moderation action couldn't be completed."
            }
            InboxModerationNotice(
                title = "Moderator action failed",
                message = message,
                actionLabel = "Retry".takeIf { uiState.retryable },
                onAction = onRetry,
            )
        }

        RedditModeratorActionPhase.SUCCEEDED -> {
            if (uiState.removalReasonWarning || uiState.removalMessageOutcome == RedditRemovalStepOutcome.FAILED) {
                InboxModerationNotice(
                    title = "Item removed",
                    message = removalPartialSuccessMessage(
                        reasonWarning = uiState.removalReasonWarning,
                        messageOutcome = uiState.removalMessageOutcome,
                    ),
                    actionLabel = null,
                    onAction = {},
                )
            }
        }

        else -> Unit
    }
}

@Composable
internal fun InboxModeratorQueueFailure(
    failureKind: RedditModerationUiFailureKind,
    httpStatusCode: Int?,
    retryable: Boolean,
    onRetry: () -> Unit,
) {
    val message = when {
        failureKind == RedditModerationUiFailureKind.HTTP && httpStatusCode == 403 ->
            "Reddit denied this mod queue. Your moderator role may not include the Posts permission."
        failureKind == RedditModerationUiFailureKind.SESSION ->
            "The Reddit sign-in for this account needs attention."
        // ISSUE208_SERVER_FAILURES_ARE_NOT_REJECTIONS_V1
        failureKind == RedditModerationUiFailureKind.HTTP ->
            redditReadHttpReason(httpStatusCode, "Reddit rejected the mod queue request.")
        failureKind == RedditModerationUiFailureKind.TRANSPORT ->
            "subshell couldn't reach Reddit for the mod queue."
        failureKind == RedditModerationUiFailureKind.PROTOCOL ->
            "Reddit returned a mod queue response that subshell couldn't read."
        else -> "Moderator tools aren't configured for the active account."
    }
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
