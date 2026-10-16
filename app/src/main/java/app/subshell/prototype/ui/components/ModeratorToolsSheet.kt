package app.subshell.prototype.ui.components

import app.subshell.prototype.ui.error.redditReadHttpReason
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import app.subshell.prototype.data.normalizeRedditCommunityName
import androidx.compose.material3.SheetValue


import androidx.compose.runtime.staticCompositionLocalOf

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import app.subshell.prototype.data.account.RedditRemovalMessageType
import app.subshell.prototype.data.account.RedditRemovalStepOutcome
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Block
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.subshell.prototype.data.account.RedditModerationUiFailureKind
import app.subshell.prototype.data.account.RedditModerationUiState
import app.subshell.prototype.data.account.RedditModeratorAction
import app.subshell.prototype.data.account.RedditModeratorActionFailureKind
import app.subshell.prototype.data.account.RedditModeratorActionPhase
import app.subshell.prototype.data.account.RedditModeratorActionRequest
import app.subshell.prototype.data.account.RedditModeratorActionUiState
import app.subshell.prototype.data.account.RedditModeratorQueueItem
import app.subshell.prototype.data.account.RedditModeratorQueueKind
import app.subshell.prototype.data.account.RedditRemovalReasonPhase
import app.subshell.prototype.data.account.RedditRemovalReasonUiState
import androidx.compose.runtime.key
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.ui.graphics.Color

// MODERATION02A5A1R3_MOD_TOOLS_WORKSPACE_NAVIGATION_V1
enum class ModeratorToolsWorkspaceDestination {
    ROOT,
    MOD_LOG,
    USER_MANAGEMENT,
    MODERATORS,
}

data class ModeratorToolsNavigationEntryPoints(
    val isRootVisible: (String) -> Boolean = { false },
    val isVisible: (String) -> Boolean = { false },
    val destinationFor: (String) -> ModeratorToolsWorkspaceDestination? = { null },
    val openRoot: (String) -> Unit = {},
    val openChild: (String, ModeratorToolsWorkspaceDestination) -> Unit = { _, _ -> },
    val returnToRoot: (String, ModeratorToolsWorkspaceDestination) -> Unit = { _, _ -> },
    val dismiss: (String) -> Unit = {},
)

val LocalModeratorToolsNavigation =
    staticCompositionLocalOf { ModeratorToolsNavigationEntryPoints() }

// MODERATION02A5A1R4_SINGLE_SHEET_WORKSPACE_V1
data class ModeratorToolsWorkspaceContentEntryPoints(
    val modLog: @Composable (() -> Unit) -> Unit = { _ -> },
    val userManagement: @Composable (() -> Unit) -> Unit = { _ -> },
    val moderators: @Composable (String, () -> Unit) -> Unit = { _, _ -> },
    val dismissModLog: () -> Unit = {},
    val dismissUserManagement: () -> Unit = {},
)

val LocalModeratorToolsWorkspaceContent =
    staticCompositionLocalOf { ModeratorToolsWorkspaceContentEntryPoints() }

// MOD01A1_READ_ONLY_MODQUEUE_SHEET_V1
// MOD01A2_APPROVE_REMOVE_MODQUEUE_ACTIONS_V1
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ModeratorToolsSheet(
    communityLabel: String,
    communityName: String,
    accountStableId: String,
    uiState: RedditModerationUiState,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onAppend: () -> Unit,
    actionUiState: RedditModeratorActionUiState = RedditModeratorActionUiState(),
    onModerate: (RedditModeratorActionRequest) -> Unit = {},
    onRetryAction: () -> Unit = {},
    onAuthorizeAction: () -> Unit = {},
    removalReasonState: RedditRemovalReasonUiState = RedditRemovalReasonUiState(),
    onLoadRemovalReasons: (Boolean) -> Unit = {},
    onAuthorizeRemovalReasons: () -> Unit = {},
    // STEP3_SAVED_RESPONSES_ARE_WIRED_V1: required, so the compiler proves the
    // screen passes the moderator's saved responses (was nullable, default null).
    savedResponses: SavedResponseActions,
) {
    val workspaceNavigation = LocalModeratorToolsNavigation.current
    val workspaceContent = LocalModeratorToolsWorkspaceContent.current
    // ISSUE54_CANONICAL_MODERATED_COMMUNITY_V1: a community page can carry the
    // display case ("AskReddit"); Reddit requests require the canonical name
    // (RedditModeratorActionRequest), and a mixed-case name made Approve and
    // Remove throw on the main thread. Navigation and the queue keep the name
    // they were opened with.
    val moderatedCommunityName = remember(communityName) {
        runCatching { normalizeRedditCommunityName(communityName) }.getOrDefault(communityName)
    }
    val workspaceDestination = workspaceNavigation.destinationFor(communityName)
        ?: ModeratorToolsWorkspaceDestination.ROOT
    val returnToRoot: (ModeratorToolsWorkspaceDestination) -> Unit = { destination ->
        when (destination) {
            ModeratorToolsWorkspaceDestination.MOD_LOG -> workspaceContent.dismissModLog()
            ModeratorToolsWorkspaceDestination.USER_MANAGEMENT ->
                workspaceContent.dismissUserManagement()
            ModeratorToolsWorkspaceDestination.MODERATORS -> Unit
            ModeratorToolsWorkspaceDestination.ROOT -> Unit
        }
        workspaceNavigation.returnToRoot(communityName, destination)
    }
    // ISSUE86_EDITOR_STAYS_WHILE_ITS_WRITE_RUNS_V1
    val workspaceBusy = remember { ModeratorWorkspaceBusy() }
    // LOWUX2_MOD_SHEET_KEEPS_ONE_SHEET_STATE_V1 (OCB-306): the state is
    // remembered with its confirmValueChange as a key, and a new lambda per
    // destination built a new sheet that slid in again; one lambda reads the
    // current destination.
    val latestDestination by rememberUpdatedState(workspaceDestination)
    val latestReturnToRoot by rememberUpdatedState(returnToRoot)
    val confirmSheetChange: (SheetValue) -> Boolean = remember(workspaceBusy) {
        { target ->
            if (target == SheetValue.Hidden && workspaceBusy.busy) {
                false
            } else if (
                target == SheetValue.Hidden &&
                latestDestination != ModeratorToolsWorkspaceDestination.ROOT
            ) {
                latestReturnToRoot(latestDestination)
                false
            } else {
                true
            }
        }
    }
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        confirmValueChange = confirmSheetChange,
    )
    // LOWUI4_MOD_SHEET_BACK_THROUGH_THE_SHEET_V1 (OCB-151): Back reaches the
    // sheet's own window, so a tool returns to the root through
    // confirmValueChange above (which also respects a running write); an
    // activity-level BackHandler here never ran.
    val matches = uiState.queueMatches(accountStableId, communityName)
    val items = if (matches) uiState.queueItems else emptyList()
    val loading = !matches || uiState.queueLoading
    val failureKind = uiState.queueFailureKind.takeIf { matches }
    val nextCursor = uiState.queueNextCursor.takeIf { matches }
    var pendingRemove by remember(communityName) {
        mutableStateOf<RedditModeratorQueueItem?>(null)
    }
    pendingRemove?.let { item ->
        ModeratorRemovalDialog(
            item = item,
            accountStableId = accountStableId,
            communityName = moderatedCommunityName,
            displayCommunityName = communityName,
            actionUiState = actionUiState,
            removalReasonState = removalReasonState,
            removalReasonsCommunity = communityName,
            onLoadRemovalReasons = onLoadRemovalReasons,
            onAuthorizeRemovalReasons = onAuthorizeRemovalReasons,
            onModerate = onModerate,
            onDismiss = { pendingRemove = null },
            savedResponses = savedResponses,
        )
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (workspaceBusy.busy) return@ModalBottomSheet
            when (workspaceDestination) {
                ModeratorToolsWorkspaceDestination.ROOT -> onDismiss()
                ModeratorToolsWorkspaceDestination.MOD_LOG ->
                    returnToRoot(ModeratorToolsWorkspaceDestination.MOD_LOG)
                ModeratorToolsWorkspaceDestination.USER_MANAGEMENT ->
                    returnToRoot(ModeratorToolsWorkspaceDestination.USER_MANAGEMENT)
                ModeratorToolsWorkspaceDestination.MODERATORS ->
                    returnToRoot(ModeratorToolsWorkspaceDestination.MODERATORS)
            }
        },
        sheetState = sheetState,
    ) {
        when (workspaceDestination) {
            ModeratorToolsWorkspaceDestination.ROOT -> {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item("mod-tools-title") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AdminPanelSettings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Mod tools",
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            Text(
                                text = communityLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = onRefresh) {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Refresh")
                        }
                    }
                }

                item("moderator-log-entry") {
                    ModeratorLogButton(
                        communityName = communityName,
                        onBeforeOpen = {
                            workspaceNavigation.openChild(
                                communityName,
                                ModeratorToolsWorkspaceDestination.MOD_LOG,
                            )
                        },
                    )
                }

                item("moderator-user-management-entry") {
                    ModeratorUserManagementButton(
                        communityName = communityName,
                        onBeforeOpen = {
                            workspaceNavigation.openChild(
                                communityName,
                                ModeratorToolsWorkspaceDestination.USER_MANAGEMENT,
                            )
                        },
                    )
                }

                item("moderator-team-entry") {
                    ModeratorTeamButton(
                        onClick = {
                            workspaceNavigation.openChild(
                                communityName,
                                ModeratorToolsWorkspaceDestination.MODERATORS,
                            )
                        },
                    )
                }

                item("moderator-report-lookup") {
                    ModeratorReportLookupButton(communityName)
                }

                item("modqueue-heading") {
                    Column(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "Mod queue",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "Review reported posts and comments, then approve, remove, or mark them as spam.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (failureKind != null) {
                    item("modqueue-error") {
                        ModeratorQueueFailure(
                            failureKind = failureKind,
                            httpStatusCode = uiState.queueFailureHttpStatusCode,
                            retryable = uiState.queueFailureRetryable,
                            onRetry = onRefresh,
                        )
                    }
                }

                if (
                    actionUiState.phase == RedditModeratorActionPhase.AUTHORIZATION_REQUIRED &&
                    actionUiState.request?.communityName == moderatedCommunityName
                ) {
                    item("modqueue-action-auth") {
                        ModerationActionNotice(
                            title = "Moderator authorization required",
                            message = "Reddit needs the modposts permission before this action can be sent.",
                            actionLabel = "Authorize",
                            onAction = onAuthorizeAction,
                        )
                    }
                } else if (
                    actionUiState.phase == RedditModeratorActionPhase.FAILED &&
                    actionUiState.request?.communityName == moderatedCommunityName
                ) {
                    item("modqueue-action-error") {
                        ModerationActionFailure(
                            uiState = actionUiState,
                            onRetry = onRetryAction,
                        )
                    }
                }

                if (
                    actionUiState.phase == RedditModeratorActionPhase.SUCCEEDED &&
                    (actionUiState.removalReasonWarning || actionUiState.removalMessageOutcome == RedditRemovalStepOutcome.FAILED) &&
                    actionUiState.request?.communityName == moderatedCommunityName
                ) {
                    item("modqueue-removal-reason-warning") {
                        ModerationActionNotice(
                            title = "Item removed",
                            message = removalPartialSuccessMessage(
                                reasonWarning = actionUiState.removalReasonWarning,
                                messageOutcome = actionUiState.removalMessageOutcome,
                            ),
                            actionLabel = null,
                            onAction = {},
                        )
                    }
                }

                if (loading && items.isEmpty() && failureKind == null) {
                    item("modqueue-loading") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            InlineProgressIndicator()
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Loading mod queue…",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (matches && uiState.queueLoaded && items.isEmpty() && failureKind == null) {
                    item("modqueue-empty") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = "Mod queue is clear",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = "Reddit returned no posts or comments requiring review.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                items(
                    items = items,
                    key = { item -> item.fullname },
                ) { item ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Column {
                            // MODERATION02A2R3_COMPACT_COMMUNITY_REVIEW_CARD_V1
                            ModeratorQueueItemSummary(
                                item = item,
                                inProgress = actionUiState.inProgressFor(item.fullname),
                            )
                            ModeratorReportsButton(item)
                            FlowRow(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                // MODERATION02A3_COMMUNITY_CONTENT_CONTROLS_ENTRY_V1
                                ModeratorContentControlsButton(item)
                                TextButton(
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    onClick = {
                                        onModerate(
                                            RedditModeratorActionRequest(
                                                accountStableId = accountStableId,
                                                communityName = moderatedCommunityName,
                                                thingFullname = item.fullname,
                                                action = RedditModeratorAction.APPROVE,
                                            ),
                                        )
                                    },
                                    enabled = !actionUiState.inProgress,
                                ) {
                                    ModeratorActionIcon(Icons.Rounded.Check)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Approve")
                                }
                                TextButton(
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    onClick = { pendingRemove = item },
                                    enabled = !actionUiState.inProgress,
                                ) {
                                    ModeratorActionIcon(Icons.Rounded.Block)
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

                if (nextCursor != null) {
                    // LOWUX2_MOD_QUEUE_PAGES_ON_ITS_OWN_V1 (OCB-153, owner rule: no
                    // load-more taps): the next page loads when the end comes into
                    // view; a failed page offers Retry.
                    item("modqueue-more-$nextCursor") {
                        val pageFailed = uiState.queueFailureKind != null
                        LaunchedEffect(nextCursor, pageFailed) {
                            if (!pageFailed && !uiState.queueLoading) onAppend()
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (pageFailed && !uiState.queueLoading) {
                                Text(
                                    "Couldn't load more of the queue",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Spacer(Modifier.width(8.dp))
                                TextButton(
                                    onClick = onAppend,
                                    enabled = !actionUiState.inProgress,
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) {
                                    Text("Retry")
                                }
                            } else {
                                InlineProgressIndicator()
                            }
                        }
                    }
                }
            }

            }
            ModeratorToolsWorkspaceDestination.MODERATORS -> {
                Column(modifier = Modifier.fillMaxWidth().heightIn(min = 320.dp)) {
                    CompositionLocalProvider(LocalModeratorWorkspaceBusy provides workspaceBusy) {
                        workspaceContent.moderators(moderatedCommunityName) {
                            returnToRoot(ModeratorToolsWorkspaceDestination.MODERATORS)
                        }
                    }
                }
            }
            ModeratorToolsWorkspaceDestination.MOD_LOG -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 320.dp),
                ) {
                    workspaceContent.modLog {
                        returnToRoot(ModeratorToolsWorkspaceDestination.MOD_LOG)
                    }
                }
            }
            ModeratorToolsWorkspaceDestination.USER_MANAGEMENT -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 320.dp),
                ) {
                    workspaceContent.userManagement {
                        returnToRoot(
                            ModeratorToolsWorkspaceDestination.USER_MANAGEMENT,
                        )
                    }
                }
            }
        }
    }
}

// MODRESPONSE01A2_PARTIAL_SUCCESS_NOTICE_V1
internal fun removalPartialSuccessMessage(
    reasonWarning: Boolean,
    messageOutcome: RedditRemovalStepOutcome,
): String {
    val parts = buildList {
        if (reasonWarning) add("the removal reason or mod note could not be confirmed")
        if (messageOutcome == RedditRemovalStepOutcome.FAILED) add("the removal message was not sent")
    }
    return "Reddit removed the item, but " + parts.joinToString(" and ") + "."
}

@Composable
private fun ModerationActionFailure(
    uiState: RedditModeratorActionUiState,
    onRetry: () -> Unit,
) {
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
        else ->
            "This moderation action couldn't be completed."
    }
    ModerationActionNotice(
        title = "Moderator action failed",
        message = message,
        actionLabel = "Retry".takeIf { uiState.retryable },
        onAction = onRetry,
    )
}

@Composable
private fun ModerationActionNotice(
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

@Composable
private fun ModeratorQueueFailure(
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
        else ->
            "Moderator tools aren't configured for the active account."
    }
    // ISSUE222_ONE_INLINE_ERROR_NOTICE_V1
    InlineErrorNotice(
        message = message,
        title = "Couldn't load mod queue",
        actionLabel = if (retryable) "Retry" else null,
        onAction = onRetry,
    )
}


// M3225_FORM_DIALOGS_ARE_FULL_SCREEN_ON_PHONES_V1 (modinbox-07): the removal form
// (reason, mod note, removal message) is a full-screen dialog on phones and one
// composable for the mod tools sheet and the Inbox queue, not a form inside an
// AlertDialog copied twice. The reasons are plain rows in the form's own
// scroll, not a nested scrolling list.
@Composable
internal fun ModeratorRemovalDialog(
    item: RedditModeratorQueueItem,
    accountStableId: String,
    communityName: String,
    displayCommunityName: String,
    actionUiState: RedditModeratorActionUiState,
    removalReasonState: RedditRemovalReasonUiState,
    removalReasonsCommunity: String,
    onLoadRemovalReasons: (Boolean) -> Unit,
    onAuthorizeRemovalReasons: () -> Unit,
    onModerate: (RedditModeratorActionRequest) -> Unit,
    onDismiss: () -> Unit,
    savedResponses: SavedResponseActions,
) {
    var selectedRemovalReasonId by remember(item.fullname) { mutableStateOf<String?>(null) }
    var removalModNote by remember(item.fullname) { mutableStateOf("") }
    // MODRESPONSE01A2_REMOVAL_RESPONSE_DRAFT_V1
    val removalResponse = remember(item.fullname) {
        RemovalResponseDraft(initialType = savedResponses.initialType)
    }
    val removalReasonsMatch = removalReasonState.matches(accountStableId, removalReasonsCommunity)
    val removalReasons = if (removalReasonsMatch && removalReasonState.loaded) {
        removalReasonState.reasons
    } else {
        emptyList()
    }
    LaunchedEffect(item.fullname, accountStableId, removalReasonsCommunity) {
        selectedRemovalReasonId = null
        removalModNote = ""
        onLoadRemovalReasons(false)
    }
    val selectedRemovalReason = removalReasons.firstOrNull { reason -> reason.id == selectedRemovalReasonId }
    LaunchedEffect(selectedRemovalReason) {
        removalResponse.prefillFrom(selectedRemovalReason)
    }

    AdaptiveFullScreenDialog(
        title = when (item.kind) {
            RedditModeratorQueueKind.POST -> "Remove this post?"
            RedditModeratorQueueKind.COMMENT -> "Remove this comment?"
        },
        onDismissRequest = onDismiss,
        closeLabel = "Cancel",
        actions = {
            TextButton(
                onClick = {
                    val reasonsAvailable = removalReasonState.loaded && removalReasonsMatch
                    val reason = selectedRemovalReason.takeIf { reasonsAvailable }
                    onDismiss()
                    onModerate(
                        RedditModeratorActionRequest(
                            accountStableId = accountStableId,
                            communityName = communityName,
                            thingFullname = item.fullname,
                            action = RedditModeratorAction.REMOVE,
                            removalReasonId = selectedRemovalReasonId
                                .takeIf { reasonsAvailable },
                            modNote = removalModNote
                                .trim()
                                .takeIf { reasonsAvailable && it.isNotEmpty() },
                            removalMessage = removalResponse.toMessageOrNull(reason),
                        ),
                    )
                },
                enabled = !actionUiState.inProgress && !removalResponse.blocksConfirm,
            ) {
                Text(if (removalResponse.enabled) "Remove and send" else "Remove")
            }
        },
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Remove from r/$displayCommunityName. A subreddit removal reason is optional and " +
                    "is recorded for moderators; it does not send a removal message by itself.",
            )
            when {
                removalReasonState.phase == RedditRemovalReasonPhase.AUTHORIZATION_REQUIRED &&
                    removalReasonsMatch -> {
                    Text(
                        "Reddit needs the modcontributors permission to read this " +
                            "community's removal reasons.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onAuthorizeRemovalReasons) {
                        Text("Authorize removal reasons")
                    }
                    Text(
                        "You can still remove without a reason.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                !removalReasonsMatch || removalReasonState.inProgress -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        InlineProgressIndicator()
                        Spacer(Modifier.width(8.dp))
                        Text("Loading removal reasons…")
                    }
                }
                removalReasonState.phase == RedditRemovalReasonPhase.FAILED -> {
                    Text(
                        "Couldn't load removal reasons. You can retry or remove without one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (removalReasonState.retryable) {
                        TextButton(onClick = { onLoadRemovalReasons(true) }) {
                            Text("Retry reasons")
                        }
                    }
                }
                removalReasonState.loaded -> {
                    if (removalReasons.isEmpty()) {
                        Text(
                            "This community has no configured removal reasons.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            "Removal reason",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        // LOWUI7_CHOICE_ROWS_SAY_WHICH_IS_SELECTED_V1 (OCB-134, OCB-152): the row is the radio.
                        Column(Modifier.fillMaxWidth().selectableGroup()) {
                            RemovalReasonRow(
                                title = "No removal reason",
                                message = "",
                                selected = selectedRemovalReasonId == null,
                                onSelect = { selectedRemovalReasonId = null },
                            )
                            removalReasons.forEach { reason ->
                                key(reason.id) {
                                    RemovalReasonRow(
                                        title = reason.title,
                                        message = reason.message,
                                        selected = selectedRemovalReasonId == reason.id,
                                        onSelect = { selectedRemovalReasonId = reason.id },
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = removalModNote,
                            onValueChange = { value -> removalModNote = value.take(100) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Mod note (optional)") },
                            supportingText = { Text("${removalModNote.length}/100") },
                            singleLine = true,
                        )
                    }
                }
            }
            // MODRESPONSE01A2_REMOVAL_RESPONSE_DRAFT_V1
            RemovalResponseSection(
                draft = removalResponse,
                kind = item.kind,
                savedResponses = savedResponses,
            )
        }
    }
}

@Composable
private fun RemovalReasonRow(
    title: String,
    message: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    ListItem(
        content = { Text(title) },
        supportingContent = if (message.isNotBlank()) {
            { Text(message, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        } else {
            null
        },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.selectable(
            selected = selected,
            role = Role.RadioButton,
            onClick = onSelect,
        ),
    )
}
