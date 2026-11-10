package io.github.brealorg.subshell.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.brealorg.subshell.data.account.RedditModeratorQueueItem
import io.github.brealorg.subshell.data.account.reportTarget
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.brealorg.subshell.data.account.ModeratorReportTarget
import io.github.brealorg.subshell.data.account.RedditModeratorAction
import io.github.brealorg.subshell.data.account.RedditModeratorActionPhase
import io.github.brealorg.subshell.data.account.RedditModeratorActionRequest
import io.github.brealorg.subshell.data.account.RedditModeratorActionUiState
import io.github.brealorg.subshell.data.account.RedditModeratorQueueKind
import io.github.brealorg.subshell.data.account.RedditRemovalReasonUiState
import io.github.brealorg.subshell.data.account.RedditRemovalStepOutcome
import io.github.brealorg.subshell.data.normalizeRedditCommunityName
import io.github.brealorg.subshell.ui.error.ErrorAction
import io.github.brealorg.subshell.ui.error.ErrorActionKind
import io.github.brealorg.subshell.ui.error.ErrorPresentation
import io.github.brealorg.subshell.ui.error.ErrorPresentationKind
import io.github.brealorg.subshell.ui.error.ViewportErrorHostState
import io.github.brealorg.subshell.ui.noticeFailure
import io.github.brealorg.subshell.ui.noticeInfo
import io.github.brealorg.subshell.ui.symbols.Symbols

/**
 * ISSUE295_MOD_ACTIONS_IN_THE_MENU_V1 (#295, reader request; owner: straight in
 * the ⋮ menu): a moderator approves, removes or marks as spam a post or comment
 * from wherever it is shown (feed, community, post, thread), not only from the
 * mod queue. The writes go through the queue's own owner-bound coordinator
 * (same authorization, same outcome-unknown handling); Remove opens the queue's
 * removal form (reason, mod note, removal message).
 *
 * ISSUE295_ONE_MODERATE_ENTRY_OPENS_A_SHEET_V1 (owner, after seeing a ⋮ menu
 * as tall as the screen; Material: a long list of actions on one object is a
 * bottom sheet, a menu stays short): the ⋮ menu has one "Moderate…" entry and
 * every moderator action for the item is in the sheet it opens.
 */
internal data class ModeratorQuickActionsEntryPoints(
    val canModerate: (String) -> Boolean,
    val moderate: (ModeratorReportTarget, RedditModeratorAction) -> Unit,
    val requestRemoval: (ModeratorReportTarget) -> Unit,
    val openSheet: (ModeratorReportTarget, (() -> Unit)?) -> Unit = { _, _ -> },
)

internal val LocalModeratorQuickActions =
    staticCompositionLocalOf<ModeratorQuickActionsEntryPoints?> { null }

/** The removal form waiting to open and the menu action waiting for Reddit. */
@Stable
internal class ModeratorQuickActionsState {
    var sheetTarget by mutableStateOf<ModeratorReportTarget?>(null)

    /** ISSUE295_MODERATOR_SETS_POST_FLAIR_V1: how the open sheet's post changes its flair. */
    var sheetChangeFlair by mutableStateOf<(() -> Unit)?>(null)
    var pendingRemoval by mutableStateOf<ModeratorReportTarget?>(null)

    /** ISSUE295_SPAM_ASKS_FROM_THE_SHEET_TOO_V1: the spam question waiting for an answer. */
    var pendingSpam by mutableStateOf<RedditModeratorActionRequest?>(null)
    var awaiting by mutableStateOf<RedditModeratorActionRequest?>(null)
}

/** The request a menu action sends, or null when the target names no community. */
internal fun moderatorQuickActionRequest(
    accountStableId: String,
    target: ModeratorReportTarget,
    action: RedditModeratorAction,
): RedditModeratorActionRequest? {
    val community = target.communityName
        ?.let { name -> runCatching { normalizeRedditCommunityName(name) }.getOrNull() }
        ?: return null
    return runCatching {
        RedditModeratorActionRequest(
            accountStableId = accountStableId,
            communityName = community,
            thingFullname = target.fullname,
            action = action,
        )
    }.getOrNull()
}

/** One "Moderate…" entry in a post's or comment's ⋮ menu, for a moderator of its community. */
@Composable
internal fun ModeratorMenuItem(
    target: ModeratorReportTarget?,
    onDismissMenu: () -> Unit,
    onChangeFlair: (() -> Unit)? = null,
) {
    val entries = LocalModeratorQuickActions.current ?: return
    val resolved = target ?: return
    val community = resolved.communityName ?: return
    if (!entries.canModerate(community)) return
    DropdownMenuItem(
        text = { Text("Moderate…") },
        leadingIcon = { Icon(Symbols.AdminPanelSettings, contentDescription = null) },
        onClick = {
            onDismissMenu()
            entries.openSheet(resolved, onChangeFlair)
        },
    )
}

/**
 * Every moderator action for one post or comment. Reports and Lock or
 * distinguish open the dialogs the ⋮ menu opened before
 * (MODERATION02A2_POST_REPORT_REOPEN_OUTSIDE_QUEUE_V1,
 * MODERATION02A2_COMMENT_REPORT_REOPEN_OUTSIDE_QUEUE_V1,
 * MODERATION02A3_POST_CONTENT_CONTROLS_REOPEN_V1,
 * MODERATION02A3_COMMENT_CONTENT_CONTROLS_REOPEN_V1).
 */
@Composable
internal fun ModeratorActionsSheet(
    target: ModeratorReportTarget,
    onModerate: (RedditModeratorAction) -> Unit,
    onRemove: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenContentControls: () -> Unit,
    onDismiss: () -> Unit,
    onChangeFlair: (() -> Unit)? = null,
) {
    val isPost = target.fullname.startsWith("t3_")
    ItemActionsSheet(
        title = (if (isPost) "Moderate post" else "Moderate comment") +
            (target.communityName?.let { " · r/$it" } ?: ""),
        groups = listOf(
            listOf(
                SheetAction("Approve", Symbols.CheckCircle) { onModerate(RedditModeratorAction.APPROVE) },
                SheetAction("Remove…", Symbols.Block) { onRemove() },
                SheetAction("Spam", Symbols.Report) { onModerate(RedditModeratorAction.SPAM) },
            ),
            listOfNotNull(
                // ISSUE295_MODERATOR_SETS_POST_FLAIR_V1: flair, NSFW and spoiler.
                onChangeFlair?.takeIf { isPost }?.let { change ->
                    SheetAction("Change flair…", Symbols.Label) { change() }
                },
                SheetAction("Lock or distinguish…", Symbols.AdminPanelSettings) { onOpenContentControls() },
                SheetAction("Reports…", Symbols.FactCheck) { onOpenReports() },
            ),
        ),
        onDismiss = onDismiss,
    )
}

private const val MODERATOR_ACTION_NOTICE_SCOPE = "moderator-action"

internal fun moderatorQuickActionSuccessTitle(request: RedditModeratorActionRequest): String {
    val what = if (request.thingFullname.startsWith("t3_")) "Post" else "Comment"
    return when (request.action) {
        RedditModeratorAction.APPROVE -> "$what approved"
        RedditModeratorAction.REMOVE -> "$what removed"
        RedditModeratorAction.SPAM -> "$what marked as spam"
        else -> "Done"
    }
}

/**
 * The removal form for a menu Remove, the authorization question when Reddit
 * needs it, and one tray notice when Reddit answered a menu action. A queue or
 * report action (another request) is left to its own surface.
 */
@Composable
internal fun ModeratorQuickActionsHost(
    quickActions: ModeratorQuickActionsState,
    accountStableId: String,
    actionState: RedditModeratorActionUiState,
    removalReasonState: RedditRemovalReasonUiState,
    onLoadRemovalReasons: (String, String, Boolean) -> Unit,
    onAuthorizeRemovalReasons: () -> Unit,
    onModerate: (RedditModeratorActionRequest) -> Unit,
    onAuthorize: () -> Unit,
    savedResponses: SavedResponseActions,
    notices: ViewportErrorHostState?,
    onRetry: () -> Unit = {},
    // ISSUE295_REMOVED_POST_IS_MARKED_V1: runs once Reddit confirmed the action.
    onApplied: (RedditModeratorActionRequest) -> Unit = {},
) {
    quickActions.sheetTarget?.let { target ->
        val reports = LocalModeratorReports.current
        val contentControls = LocalModeratorContentControls.current
        ModeratorActionsSheet(
            target = target,
            onModerate = { action ->
                moderatorQuickActionRequest(accountStableId, target, action)?.let { request ->
                    if (action == RedditModeratorAction.SPAM) {
                        quickActions.pendingSpam = request
                    } else {
                        quickActions.awaiting = request
                        onModerate(request)
                    }
                }
            },
            onRemove = { quickActions.pendingRemoval = target },
            onOpenReports = { reports?.open?.invoke(target) },
            onOpenContentControls = { contentControls?.open?.invoke(target) },
            onDismiss = {
                quickActions.sheetTarget = null
                quickActions.sheetChangeFlair = null
            },
            onChangeFlair = quickActions.sheetChangeFlair,
        )
    }
    // A question asked for one account never stays open for another
    // (MODERATION02A1: no confirmation is kept across an account change).
    LaunchedEffect(accountStableId) {
        if (quickActions.pendingSpam?.accountStableId != accountStableId) quickActions.pendingSpam = null
    }
    quickActions.pendingSpam?.let { spam ->
        ModeratorSpamConfirmationDialog(
            kind = if (spam.thingFullname.startsWith("t3_")) {
                RedditModeratorQueueKind.POST
            } else {
                RedditModeratorQueueKind.COMMENT
            },
            confirmation = spam,
            summary = null,
            canConfirm = spam.accountStableId == accountStableId && !actionState.inProgress,
            onConfirm = {
                quickActions.pendingSpam = null
                if (spam.accountStableId == accountStableId) {
                    quickActions.awaiting = spam
                    onModerate(spam)
                }
            },
            onDismiss = { quickActions.pendingSpam = null },
        )
    }
    quickActions.pendingRemoval?.let { target ->
        val request = moderatorQuickActionRequest(accountStableId, target, RedditModeratorAction.REMOVE)
        if (request == null) {
            quickActions.pendingRemoval = null
        } else {
            ModeratorRemovalDialog(
                thingFullname = target.fullname,
                kind = if (target.fullname.startsWith("t3_")) {
                    RedditModeratorQueueKind.POST
                } else {
                    RedditModeratorQueueKind.COMMENT
                },
                accountStableId = accountStableId,
                communityName = request.communityName,
                displayCommunityName = request.communityName,
                actionUiState = actionState,
                removalReasonState = removalReasonState,
                removalReasonsCommunity = request.communityName,
                onLoadRemovalReasons = { force ->
                    onLoadRemovalReasons(accountStableId, request.communityName, force)
                },
                onAuthorizeRemovalReasons = onAuthorizeRemovalReasons,
                onModerate = { removal ->
                    quickActions.awaiting = removal
                    onModerate(removal)
                },
                onDismiss = { quickActions.pendingRemoval = null },
                savedResponses = savedResponses,
            )
        }
    }

    val awaiting = quickActions.awaiting
    val answered = actionState.request
    val ours = awaiting != null && answered != null &&
        answered.thingFullname == awaiting.thingFullname &&
        answered.action == awaiting.action &&
        answered.communityName == awaiting.communityName &&
        answered.accountStableId == awaiting.accountStableId
    LaunchedEffect(ours, actionState.phase, actionState.completionRevision) {
        if (!ours) return@LaunchedEffect
        when (actionState.phase) {
            RedditModeratorActionPhase.SUCCEEDED -> {
                quickActions.awaiting = null
                onApplied(awaiting)
                val partial = actionState.removalReasonWarning ||
                    actionState.removalMessageOutcome == RedditRemovalStepOutcome.FAILED
                if (partial) {
                    notices.noticeFailure(
                        moderatorQuickActionSuccessTitle(awaiting),
                        removalPartialSuccessMessage(
                            actionState.removalReasonWarning,
                            actionState.removalMessageOutcome,
                        ),
                    )
                } else {
                    notices.noticeInfo(
                        moderatorQuickActionSuccessTitle(awaiting),
                        "r/${awaiting.communityName}",
                    )
                }
            }
            RedditModeratorActionPhase.FAILED -> {
                // ISSUE295_QUEUE_USES_THE_SHEET_V1: the queue's inline Retry moved
                // here, so a failure that can be sent again offers it in the tray.
                val retryable = actionState.retryable
                if (!retryable) quickActions.awaiting = null
                notices?.present(
                    scopeKey = MODERATOR_ACTION_NOTICE_SCOPE,
                    presentation = ErrorPresentation(
                        kind = ErrorPresentationKind.DEGRADED,
                        title = "Moderator action failed",
                        message = moderatorActionFailureMessage(actionState),
                        primaryAction = ErrorAction(ErrorActionKind.RETRY, "Retry").takeIf { retryable },
                    ),
                    failureDetails = actionState.failureDetails,
                    onRetry = if (retryable) onRetry else null,
                )
            }
            else -> Unit
        }
    }
    if (ours && actionState.phase == RedditModeratorActionPhase.AUTHORIZATION_REQUIRED) {
        AlertDialog(
            onDismissRequest = { quickActions.awaiting = null },
            title = { Text("Allow moderation") },
            text = {
                Text(
                    "Reddit needs you to allow subshell to moderate for this account. " +
                        "The action is sent once you have allowed it.",
                )
            },
            confirmButton = {
                TextButton(onClick = onAuthorize) { Text("Allow") }
            },
            dismissButton = {
                TextButton(onClick = { quickActions.awaiting = null }) { Text("Cancel") }
            },
        )
    }
}

/**
 * ISSUE295_QUEUE_USES_THE_SHEET_V1 (owner: one moderator design): a queue card
 * keeps the two actions a moderator triages with, Approve and Remove, and ⋮
 * opens the same Moderate sheet as a post's menu (Spam, Lock or distinguish,
 * Reports). The writes go through the same entry points, so the answer is the
 * same tray notice and the same authorization question.
 */
@Composable
internal fun ModeratorQueueItemActions(
    item: RedditModeratorQueueItem,
    inProgress: Boolean,
) {
    val entries = LocalModeratorQuickActions.current ?: return
    val target = item.reportTarget()
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            modifier = Modifier.heightIn(min = 48.dp),
            onClick = { entries.moderate(target, RedditModeratorAction.APPROVE) },
            enabled = !inProgress,
        ) {
            ModeratorActionIcon(Symbols.Check)
            Spacer(Modifier.width(6.dp))
            Text("Approve")
        }
        TextButton(
            modifier = Modifier.heightIn(min = 48.dp),
            onClick = { entries.requestRemoval(target) },
            enabled = !inProgress,
        ) {
            ModeratorActionIcon(Symbols.Block)
            Spacer(Modifier.width(6.dp))
            Text("Remove")
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { entries.openSheet(target, null) }) {
            Icon(Symbols.MoreVert, contentDescription = "More moderator actions")
        }
    }
}

/** ISSUE295_REMOVED_POST_IS_MARKED_V1: a removed post is marked for a moderator of its community. */
internal fun postShowsRemoved(post: io.github.brealorg.subshell.model.Post, entries: ModeratorQuickActionsEntryPoints?): Boolean =
    post.removed && entries?.canModerate?.invoke(post.community) == true
