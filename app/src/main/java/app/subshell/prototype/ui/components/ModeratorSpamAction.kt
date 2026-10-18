package app.subshell.prototype.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.Modifier
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.subshell.prototype.data.account.RedditModeratorActionRequest
import app.subshell.prototype.data.account.RedditModeratorActionUiState
import app.subshell.prototype.data.account.RedditModeratorQueueItem
import app.subshell.prototype.data.account.RedditModeratorQueueKind
import app.subshell.prototype.data.account.canConfirmModeratorSpam
import app.subshell.prototype.data.account.moderatorSpamRequestOrNull
import app.subshell.prototype.ui.symbols.Symbols

// MODERATION02A1_SHARED_EXPLICIT_SPAM_CONFIRMATION_V1
// Used by both the Inbox aggregate queue and the community Mod tools queue.
@Composable
internal fun ModeratorSpamAction(
    item: RedditModeratorQueueItem,
    accountStableId: String,
    actionUiState: RedditModeratorActionUiState,
    onModerate: (RedditModeratorActionRequest) -> Unit,
) {
    // Do not retain a confirmation across an account, community or target change.
    // Recreating this UI (including rotation) cancels rather than replays a write.
    var pendingSpam by remember(accountStableId, item.communityName, item.fullname, item.kind) {
        mutableStateOf<RedditModeratorActionRequest?>(null)
    }
    val request = moderatorSpamRequestOrNull(accountStableId, item)

    TextButton(
        modifier = Modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        onClick = { pendingSpam = request },
        enabled = request != null && !actionUiState.inProgress,
    ) {
        ModeratorActionIcon(Symbols.Report)
        Spacer(Modifier.width(6.dp))
        Text("Spam")
    }

    val confirmation = pendingSpam ?: return
    AlertDialog(
        onDismissRequest = { pendingSpam = null },
        title = {
            Text(
                when (item.kind) {
                    RedditModeratorQueueKind.POST -> "Mark this post as spam?"
                    RedditModeratorQueueKind.COMMENT -> "Mark this comment as spam?"
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "r/${confirmation.communityName} · ${confirmation.thingFullname}",
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    item.summary,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "This removes the item and marks it as spam. " +
                        "Use Remove for other rule violations. " +
                        "This action does not ban the author or send a removal message.",
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = confirm@{
                    // Read the current pending value: a stale second click cannot resend it.
                    val pinnedRequest = pendingSpam ?: return@confirm
                    if (!canConfirmModeratorSpam(
                            request = pinnedRequest,
                            activeAccountStableId = accountStableId,
                            item = item,
                            actionInProgress = actionUiState.inProgress,
                        )
                    ) return@confirm
                    pendingSpam = null
                    onModerate(pinnedRequest)
                },
                enabled = canConfirmModeratorSpam(
                    request = confirmation,
                    activeAccountStableId = accountStableId,
                    item = item,
                    actionInProgress = actionUiState.inProgress,
                ),
            ) {
                Text("Mark as spam")
            }
        },
        dismissButton = {
            TextButton(onClick = { pendingSpam = null }) {
                Text("Cancel")
            }
        },
    )
}
