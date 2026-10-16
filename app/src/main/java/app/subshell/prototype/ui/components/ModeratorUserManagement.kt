package app.subshell.prototype.ui.components

import androidx.compose.material.icons.automirrored.rounded.ArrowBack

import androidx.activity.compose.BackHandler

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.subshell.prototype.data.error.toSafeTechnicalDetail
import app.subshell.prototype.data.account.MODERATOR_BAN_MESSAGE_MAX_CHARS
import app.subshell.prototype.data.account.MODERATOR_BAN_NOTE_MAX_CHARS
import app.subshell.prototype.data.account.MODERATOR_MUTE_NOTE_MAX_CHARS
import app.subshell.prototype.data.account.MODERATOR_BAN_REASON_MAX_CHARS
import app.subshell.prototype.data.account.ModeratorUserManagementFailureKind
import app.subshell.prototype.data.account.ModeratorUserManagementUiState
import app.subshell.prototype.data.account.RedditApprovedUser
import app.subshell.prototype.data.account.RedditBannedUser
import app.subshell.prototype.data.account.RedditMutedUser
import app.subshell.prototype.data.account.RedditModeratorUserApprovalRequest
import app.subshell.prototype.data.account.RedditModeratorUserBanRequest
import app.subshell.prototype.data.account.RedditModeratorUserMuteRequest
import app.subshell.prototype.data.account.RedditPeopleSearchUiState
import app.subshell.prototype.data.account.normalizeRedditSafetyUsername
import app.subshell.prototype.model.RedditPeopleSearchItem
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay

// MODERATION02A5A1_USER_MANAGEMENT_ENTRYPOINTS_V1
data class ModeratorUserManagementEntryPoints(
    val canInspect: (String) -> Boolean = { false },
    val open: (String) -> Unit = {},
    val peopleSearchState: () -> RedditPeopleSearchUiState = { RedditPeopleSearchUiState() },
    val searchPeople: (String) -> Unit = {},
)

val LocalModeratorUserManagement =
    staticCompositionLocalOf { ModeratorUserManagementEntryPoints() }

private enum class ModeratorUserManagementSection {
    BANNED,
    MUTED,
    APPROVED,
}

@Composable
fun ModeratorUserManagementButton(
    communityName: String,
    onBeforeOpen: () -> Unit = {},
) {
    val entryPoints = LocalModeratorUserManagement.current
    if (!entryPoints.canInspect(communityName)) return

    OutlinedButton(
        modifier = Modifier.heightIn(min = 48.dp),
        onClick = {
            onBeforeOpen()
            entryPoints.open(communityName)
        },
    ) {
        Icon(
            imageVector = Icons.Rounded.Group,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text("User management")
    }
}

// MODERATION02A5A1_MATERIAL_USER_MANAGEMENT_SHEET_V1
// MODERATION02A5A1R1_MATERIAL_LIST_AND_IME_STABLE_EDITOR_V1
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeratorUserManagementDialog(
    accountStableId: String,
    activeUsername: String,
    state: ModeratorUserManagementUiState,
    onRefresh: () -> Unit,
    onAppend: () -> Unit,
    onAppendMuted: () -> Unit = {},
    onAppendApproved: () -> Unit = {},
    onMutate: (RedditModeratorUserBanRequest) -> Unit,
    onMutateMuted: (RedditModeratorUserMuteRequest) -> Unit = {},
    onMutateApproved: (RedditModeratorUserApprovalRequest) -> Unit = {},
    onAuthorize: () -> Unit,
    onDismiss: () -> Unit,
    embeddedInModToolsWorkspace: Boolean = false,
) {
    if (!state.visible) return
    val community = state.communityName ?: return
    if (!state.matches(accountStableId, community)) return
    val entryPoints = LocalModeratorUserManagement.current

    var selectedSection by remember(accountStableId, community) {
        mutableStateOf(ModeratorUserManagementSection.BANNED)
    }
    var showBanEditor by remember(accountStableId, community) { mutableStateOf(false) }
    BackHandler(enabled = embeddedInModToolsWorkspace && showBanEditor) {
        showBanEditor = false
    }
    var showMuteEditor by remember(accountStableId, community) { mutableStateOf(false) }
    BackHandler(enabled = embeddedInModToolsWorkspace && showMuteEditor) {
        showMuteEditor = false
    }
    var showApproveEditor by remember(accountStableId, community) { mutableStateOf(false) }
    BackHandler(enabled = embeddedInModToolsWorkspace && showApproveEditor) {
        showApproveEditor = false
    }
    var pendingUnban by remember(accountStableId, community) {
        mutableStateOf<RedditBannedUser?>(null)
    }
    var pendingUnmute by remember(accountStableId, community) {
        mutableStateOf<RedditMutedUser?>(null)
    }
    var pendingUnapprove by remember(accountStableId, community) {
        mutableStateOf<RedditApprovedUser?>(null)
    }

    pendingUnban?.let { user ->
        AlertDialog(
            onDismissRequest = { pendingUnban = null },
            title = { Text("Unban u/${user.username}?") },
            text = {
                Text(
                    "This restores the account's ability to participate in r/$community. " +
                        "Reddit will be checked again after the action.",
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !state.mutationInProgress,
                    onClick = {
                        val request = RedditModeratorUserBanRequest(
                            accountStableId = accountStableId,
                            communityName = community,
                            username = user.username,
                            banned = false,
                        )
                        pendingUnban = null
                        onMutate(request)
                    },
                ) {
                    Text("Unban")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingUnban = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    pendingUnmute?.let { user ->
        AlertDialog(
            onDismissRequest = { pendingUnmute = null },
            title = { Text("Unmute u/${user.username}?") },
            text = {
                Text(
                    "This allows u/${user.username} to send modmail to r/$community again. " +
                        "Reddit will be checked again after the action.",
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !state.mutationInProgress,
                    onClick = {
                        val request = RedditModeratorUserMuteRequest(
                            accountStableId = accountStableId,
                            communityName = community,
                            username = user.username,
                            muted = false,
                        )
                        pendingUnmute = null
                        onMutateMuted(request)
                    },
                ) {
                    Text("Unmute")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingUnmute = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    pendingUnapprove?.let { user ->
        AlertDialog(
            onDismissRequest = { pendingUnapprove = null },
            title = { Text("Unapprove u/${user.username}?") },
            text = {
                Text(
                    "This removes u/${user.username} from the approved-user list for " +
                        "r/$community. Reddit will be checked again after the action.",
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !state.mutationInProgress,
                    onClick = {
                        val request = RedditModeratorUserApprovalRequest(
                            accountStableId = accountStableId,
                            communityName = community,
                            username = user.username,
                            approved = false,
                        )
                        pendingUnapprove = null
                        onMutateApproved(request)
                    },
                ) {
                    Text("Unapprove")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingUnapprove = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    // LOWUX1_BAN_AND_MUTE_DRAFTS_SURVIVE_A_REFUSAL_V1 (OCB-154): Confirm
    // closes the editor before Reddit answers; its fields (reason, note,
    // message to the user) are kept per account and community and dropped
    // only once the action went through, so a refusal does not lose them.
    val editorDrafts = rememberSaveableStateHolder()
    val latestUserState by rememberUpdatedState(state)
    var pendingDraftKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pendingDraftKey) {
        val key = pendingDraftKey ?: return@LaunchedEffect
        snapshotFlow { latestUserState.mutationInProgress }.first { inProgress -> inProgress }
        snapshotFlow { latestUserState.mutationInProgress }.first { inProgress -> !inProgress }
        if (latestUserState.failureKind == null) editorDrafts.removeState(key)
        pendingDraftKey = null
    }
    val banDraftKey = "ban:$accountStableId:$community"
    val muteDraftKey = "mute:$accountStableId:$community"
    val content: @Composable () -> Unit = {
                if (showBanEditor) {
                    editorDrafts.SaveableStateProvider(banDraftKey) {
                        ModeratorBanUserEditor(
                            accountStableId = accountStableId,
                            communityName = community,
                            activeUsername = activeUsername,
                            busy = state.mutationInProgress,
                            peopleSearchState = entryPoints.peopleSearchState(),
                            onSearchPeople = entryPoints.searchPeople,
                            onConfirm = { request ->
                                showBanEditor = false
                                pendingDraftKey = banDraftKey
                                onMutate(request)
                            },
                            onBack = { showBanEditor = false },
                        )
                    }
                } else if (showMuteEditor) {
                    editorDrafts.SaveableStateProvider(muteDraftKey) {
                        ModeratorMuteUserEditor(
                            accountStableId = accountStableId,
                            communityName = community,
                            activeUsername = activeUsername,
                            busy = state.mutationInProgress,
                            peopleSearchState = entryPoints.peopleSearchState(),
                            onSearchPeople = entryPoints.searchPeople,
                            onConfirm = { request ->
                                showMuteEditor = false
                                pendingDraftKey = muteDraftKey
                                onMutateMuted(request)
                            },
                            onBack = { showMuteEditor = false },
                        )
                    }
                } else if (showApproveEditor) {
                    ModeratorApproveUserEditor(
                        accountStableId = accountStableId,
                        communityName = community,
                        activeUsername = activeUsername,
                        busy = state.mutationInProgress,
                        peopleSearchState = entryPoints.peopleSearchState(),
                        onSearchPeople = entryPoints.searchPeople,
                        onConfirm = { request ->
                            showApproveEditor = false
                            onMutateApproved(request)
                        },
                        onBack = { showApproveEditor = false },
                    )
                } else {
                    ModeratorUserManagementOverview(
                        community = community,
                        state = state,
                        selectedSection = selectedSection,
                        onSelectedSection = { selectedSection = it },
                        onRefresh = onRefresh,
                        onAppend = onAppend,
                        onAppendMuted = onAppendMuted,
                        onAppendApproved = onAppendApproved,
                        onBan = {
                            selectedSection = ModeratorUserManagementSection.BANNED
                            showBanEditor = true
                        },
                        onMute = {
                            selectedSection = ModeratorUserManagementSection.MUTED
                            showMuteEditor = true
                        },
                        onApprove = {
                            selectedSection = ModeratorUserManagementSection.APPROVED
                            showApproveEditor = true
                        },
                        onUnban = { pendingUnban = it },
                        onUnmute = { pendingUnmute = it },
                        onUnapprove = { pendingUnapprove = it },
                        onAuthorize = onAuthorize,
                        embeddedInModToolsWorkspace = embeddedInModToolsWorkspace,
                        onBackToModTools = onDismiss,
                    )
                }

    }
    if (embeddedInModToolsWorkspace) {
        content()
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
        ) {
            content()
        }
    }
}

@Composable
private fun ModeratorUserManagementOverview(
    community: String,
    state: ModeratorUserManagementUiState,
    selectedSection: ModeratorUserManagementSection,
    onSelectedSection: (ModeratorUserManagementSection) -> Unit,
    onRefresh: () -> Unit,
    onAppend: () -> Unit,
    onAppendMuted: () -> Unit,
    onAppendApproved: () -> Unit,
    onBan: () -> Unit,
    onMute: () -> Unit,
    onApprove: () -> Unit,
    onUnban: (RedditBannedUser) -> Unit,
    onUnmute: (RedditMutedUser) -> Unit,
    onUnapprove: (RedditApprovedUser) -> Unit,
    onAuthorize: () -> Unit,
    embeddedInModToolsWorkspace: Boolean,
    onBackToModTools: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item("user-management-header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (embeddedInModToolsWorkspace) {
                    IconButton(onClick = onBackToModTools) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back to Mod tools",
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "User management",
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "r/$community",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    enabled = !state.loading && !state.mutationInProgress,
                    onClick = onRefresh,
                ) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = "Refresh user management",
                    )
                }
            }
        }

        item("user-management-sections") {
            val sections = ModeratorUserManagementSection.entries
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                sections.forEachIndexed { index, section ->
                    SegmentedButton(
                        selected = selectedSection == section,
                        onClick = { onSelectedSection(section) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = sections.size,
                        ),
                        label = {
                            Text(
                                when (section) {
                                    ModeratorUserManagementSection.BANNED -> "Banned"
                                    ModeratorUserManagementSection.MUTED -> "Muted"
                                    ModeratorUserManagementSection.APPROVED -> "Approved"
                                },
                            )
                        },
                    )
                }
            }
        }

        if (state.authorizationRequired) {
            item("user-management-auth") {
                ModeratorUserManagementNotice(
                    title = "User management permission required",
                    message =
                        "Reddit needs the modcontributors scope for banned, muted, and " +
                            "approved users and their moderation actions in r/$community.",
                    actionLabel = if (state.authorizing) null else "Authorize",
                    onAction = onAuthorize,
                )
            }
        }

        if (state.failureKind != null) {
            item("user-management-failure") {
                val permissionDenied =
                    state.failureKind == ModeratorUserManagementFailureKind.HTTP &&
                        state.failureHttpStatusCode == 403
                ModeratorUserManagementNotice(
                    title = if (permissionDenied) {
                        "Manage Users permission required"
                    } else if (
                        state.failureKind == ModeratorUserManagementFailureKind.OUTCOME_UNKNOWN
                    ) {
                        "Action outcome not confirmed"
                    } else {
                        "User management unavailable"
                    },
                    message = when {
                        permissionDenied ->
                            "Reddit denied this request. Your moderator role may not include " +
                                "Manage Users / Access permission for r/$community."
                        state.failureKind == ModeratorUserManagementFailureKind.OUTCOME_UNKNOWN ->
                            "subshell did not confirm the requested server state. Refresh user " +
                                "management before acting again."
                        state.failureKind == ModeratorUserManagementFailureKind.ALREADY_MUTATING ->
                            "Another user-management action is already in progress."
                        // LOW_NET_USER_MUTATION_KEEPS_REDDITS_ERROR_V1 (OCB-552)
                        state.failureKind == ModeratorUserManagementFailureKind.VALIDATION &&
                            state.failureDetails?.toSafeTechnicalDetail() != null ->
                            "Reddit refused this action for r/$community: " +
                                state.failureDetails.toSafeTechnicalDetail()
                        else ->
                            "Reddit user management could not be completed for r/$community."
                    },
                    actionLabel = "Retry".takeIf {
                        state.failureRetryable && !state.mutationInProgress
                    },
                    onAction = onRefresh,
                )
            }
        }

        if (selectedSection == ModeratorUserManagementSection.BANNED) {
            state.verifiedUsername?.let { username ->
                item("verified-$username-${state.verifiedBanned}") {
                    ModeratorUserManagementNotice(
                        title = if (state.verifiedBanned == true) "Ban confirmed" else "Unban confirmed",
                        message = "Reddit now reports u/$username as " +
                            if (state.verifiedBanned == true) "banned." else "not banned.",
                        actionLabel = null,
                        onAction = {},
                    )
                }
            }

            item("ban-user-action") {
                FilledTonalButton(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    enabled = !state.mutationInProgress,
                    onClick = onBan,
                ) {
                    Icon(Icons.Rounded.PersonAdd, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Ban user")
                }
            }

            item("banned-heading") {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Block,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Banned users",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.loaded && state.bannedUsers.isNotEmpty()) {
                        Text(
                            state.bannedUsers.size.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (state.loading && state.bannedUsers.isEmpty()) {
                item("banned-loading") { ModeratorBannedUsersLoadingCard() }
            }
            if (
                state.loaded && state.bannedUsers.isEmpty() && !state.loading &&
                state.failureKind == null && !state.authorizationRequired
            ) {
                item("banned-empty") {
                    ModeratorUserManagementNotice(
                        "No banned users",
                        "Reddit returned no banned users for r/$community.",
                        null,
                        {},
                    )
                }
            }
            // LOWUI1_LAZY_KEYS_NAMESPACED_V1 (OCB-155): a username cannot collide with the fixed items.
            items(state.bannedUsers, key = { user -> "banned-user:${user.username}" }) { user ->
                ModeratorBannedUserCard(
                    user = user,
                    busy = state.mutationInProgress,
                    onUnban = { onUnban(user) },
                )
            }
            state.nextCursor?.let {
                item("banned-more") {
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.loading && !state.mutationInProgress,
                        onClick = onAppend,
                    ) {
                        Text(if (state.loading) "Loading…" else "Load more")
                    }
                }
            }
        } else if (selectedSection == ModeratorUserManagementSection.MUTED) {
            state.verifiedMutedUsername?.let { username ->
                item("verified-muted-$username-${state.verifiedMuted}") {
                    ModeratorUserManagementNotice(
                        title = if (state.verifiedMuted == true) "Mute confirmed" else "Unmute confirmed",
                        message = "Reddit now reports u/$username as " +
                            if (state.verifiedMuted == true) "muted." else "not muted.",
                        actionLabel = null,
                        onAction = {},
                    )
                }
            }

            item("mute-user-action") {
                FilledTonalButton(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    enabled = !state.mutationInProgress,
                    onClick = onMute,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.VolumeOff, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Mute user")
                }
            }

            item("muted-heading") {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.VolumeOff,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Muted users",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.mutedLoaded && state.mutedUsers.isNotEmpty()) {
                        Text(
                            state.mutedUsers.size.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (state.mutedLoading && state.mutedUsers.isEmpty()) {
                item("muted-loading") { ModeratorMutedUsersLoadingCard() }
            }
            if (
                state.mutedLoaded && state.mutedUsers.isEmpty() && !state.mutedLoading &&
                state.failureKind == null && !state.authorizationRequired
            ) {
                item("muted-empty") {
                    ModeratorUserManagementNotice(
                        "No muted users",
                        "Reddit returned no muted users for r/$community.",
                        null,
                        {},
                    )
                }
            }
            items(state.mutedUsers, key = { user -> "muted-user:${user.username}" }) { user ->
                ModeratorMutedUserCard(
                    user = user,
                    busy = state.mutationInProgress,
                    onUnmute = { onUnmute(user) },
                )
            }
            state.mutedNextCursor?.let {
                item("muted-more") {
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.mutedLoading && !state.mutationInProgress,
                        onClick = onAppendMuted,
                    ) {
                        Text(if (state.mutedLoading) "Loading…" else "Load more")
                    }
                }
            }
        } else {
            state.verifiedApprovedUsername?.let { username ->
                item("verified-approved-$username-${state.verifiedApproved}") {
                    ModeratorUserManagementNotice(
                        title = if (state.verifiedApproved == true) {
                            "Approval confirmed"
                        } else {
                            "Unapprove confirmed"
                        },
                        message = "Reddit now reports u/$username as " +
                            if (state.verifiedApproved == true) {
                                "an approved user."
                            } else {
                                "not an approved user."
                            },
                        actionLabel = null,
                        onAction = {},
                    )
                }
            }

            item("approve-user-action") {
                FilledTonalButton(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    enabled = !state.mutationInProgress,
                    onClick = onApprove,
                ) {
                    Icon(Icons.Rounded.PersonAdd, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Approve user")
                }
            }

            item("approved-heading") {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.PersonAdd,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Approved users",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.approvedLoaded && state.approvedUsers.isNotEmpty()) {
                        Text(
                            state.approvedUsers.size.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (state.approvedLoading && state.approvedUsers.isEmpty()) {
                item("approved-loading") { ModeratorApprovedUsersLoadingCard() }
            }
            if (
                state.approvedLoaded && state.approvedUsers.isEmpty() && !state.approvedLoading &&
                state.failureKind == null && !state.authorizationRequired
            ) {
                item("approved-empty") {
                    ModeratorUserManagementNotice(
                        "No approved users",
                        "Reddit returned no approved users for r/$community.",
                        null,
                        {},
                    )
                }
            }
            items(state.approvedUsers, key = { user -> "approved-user:${user.username}" }) { user ->
                ModeratorApprovedUserCard(
                    user = user,
                    busy = state.mutationInProgress,
                    onUnapprove = { onUnapprove(user) },
                )
            }
            state.approvedNextCursor?.let {
                item("approved-more") {
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.approvedLoading && !state.mutationInProgress,
                        onClick = onAppendApproved,
                    ) {
                        Text(if (state.approvedLoading) "Loading…" else "Load more")
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeratorBannedUsersLoadingCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InlineProgressIndicator()
            Spacer(Modifier.width(10.dp))
            Text("Loading banned users…")
        }
    }
}

@Composable
private fun ModeratorBannedUserCard(
    user: RedditBannedUser,
    busy: Boolean,
    onUnban: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                ModeratorUserAvatar(
                    username = user.username,
                    avatarImageUri = user.avatarImageUri,
                    size = 44,
                )
            },
            content = {
                Text(
                    text = "u/${user.username}",
                    fontWeight = FontWeight.SemiBold,
                )
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    user.note?.let { note ->
                        Text(
                            text = "Moderator note · $note",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    user.createdEpochSeconds?.let { epoch ->
                        moderatorBanAgeLabel(epoch)?.let { age ->
                            Text(
                                text = "Banned $age",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            trailingContent = {
                TextButton(
                    enabled = !busy,
                    onClick = onUnban,
                ) {
                    Text("Unban")
                }
            },
        )
    }
}

@Composable
private fun ModeratorMutedUsersLoadingCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InlineProgressIndicator()
            Spacer(Modifier.width(10.dp))
            Text("Loading muted users…")
        }
    }
}

@Composable
private fun ModeratorMutedUserCard(
    user: RedditMutedUser,
    busy: Boolean,
    onUnmute: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                ModeratorUserAvatar(user.username, user.avatarImageUri, 44)
            },
            content = {
                Text("u/${user.username}", fontWeight = FontWeight.SemiBold)
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    user.note?.let { note ->
                        Text(
                            "Moderator note · $note",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    user.createdEpochSeconds?.let { epoch ->
                        moderatorBanAgeLabel(epoch)?.let { age ->
                            Text(
                                "Muted $age",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            trailingContent = {
                TextButton(enabled = !busy, onClick = onUnmute) { Text("Unmute") }
            },
        )
    }
}

@Composable
private fun ModeratorApprovedUsersLoadingCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InlineProgressIndicator()
            Spacer(Modifier.width(10.dp))
            Text("Loading approved users…")
        }
    }
}

@Composable
private fun ModeratorApprovedUserCard(
    user: RedditApprovedUser,
    busy: Boolean,
    onUnapprove: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                ModeratorUserAvatar(user.username, user.avatarImageUri, 44)
            },
            content = {
                Text("u/${user.username}", fontWeight = FontWeight.SemiBold)
            },
            supportingContent = {
                user.createdEpochSeconds?.let { epoch ->
                    moderatorBanAgeLabel(epoch)?.let { age ->
                        Text(
                            "Approved $age",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            trailingContent = {
                TextButton(enabled = !busy, onClick = onUnapprove) { Text("Unapprove") }
            },
        )
    }
}

// MODERATION02A5A1R1_IME_STABLE_BAN_EDITOR_V1
// MODERATION02A5A1R2_REUSE_REDDIT_PEOPLE_SEARCH_V1
@Composable
private fun ModeratorBanUserEditor(
    accountStableId: String,
    communityName: String,
    activeUsername: String,
    busy: Boolean,
    peopleSearchState: RedditPeopleSearchUiState,
    onSearchPeople: (String) -> Unit,
    onConfirm: (RedditModeratorUserBanRequest) -> Unit,
    onBack: () -> Unit,
) {
    var username by rememberSaveable { mutableStateOf("") }
    var selectedPerson by remember { mutableStateOf<RedditPeopleSearchItem?>(null) }
    var duration by rememberSaveable { mutableStateOf("") }
    var banReason by rememberSaveable { mutableStateOf("") }
    var moderatorNote by rememberSaveable { mutableStateOf("") }
    var messageToUser by rememberSaveable { mutableStateOf("") }

    val normalizedLookup = username
        .trim()
        .removePrefix("u/")
        .trim()
    val normalizedUsername = runCatching {
        normalizeRedditSafetyUsername(username)
    }.getOrNull()
    val durationDays = duration.trim().takeIf(String::isNotEmpty)?.toIntOrNull()
    val durationValid =
        duration.isBlank() || (durationDays != null && durationDays in 1..999)
    val targetValid =
        normalizedUsername != null &&
            !normalizedUsername.equals(activeUsername.trim(), ignoreCase = true)
    val searchEligible =
        selectedPerson == null && normalizedLookup.length in 2..24
    val searchMatches =
        searchEligible && peopleSearchState.matches(accountStableId, normalizedLookup)
    val people = if (searchMatches) {
        peopleSearchState.items
            .asSequence()
            .filterNot { person ->
                person.username.equals(activeUsername.trim(), ignoreCase = true)
            }
            .distinctBy { person -> person.username.lowercase() }
            .take(MODERATOR_USER_SEARCH_RESULT_LIMIT)
            .toList()
    } else {
        emptyList()
    }

    LaunchedEffect(normalizedLookup, selectedPerson?.username, accountStableId) {
        if (selectedPerson == null && normalizedLookup.length in 2..24) {
            delay(MODERATOR_USER_SEARCH_DEBOUNCE_MS)
            onSearchPeople(normalizedLookup)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item("ban-editor-header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back to User management",
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Ban user",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = "r/$communityName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item("ban-editor-explainer") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Text(
                    text = "This is a real subreddit ban. Leave duration blank for a " +
                        "permanent ban. subshell verifies the banned-user relationship after " +
                        "the action.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item("ban-editor-user") {
            if (selectedPerson != null) {
                ModeratorSelectedBanUser(
                    person = requireNotNull(selectedPerson),
                    enabled = !busy,
                    onClear = {
                        selectedPerson = null
                        username = ""
                    },
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SubshellSearchField(
                        query = username,
                        onQueryChange = { value ->
                            username = value.take(24)
                            selectedPerson = null
                        },
                        placeholder = "Username",
                        trailingIcon = if (username.isNotEmpty()) {
                            {
                                IconButton(
                                    enabled = !busy,
                                    onClick = { username = "" },
                                ) {
                                    Icon(
                                        Icons.Rounded.Close,
                                        contentDescription = "Clear user search",
                                    )
                                }
                            }
                        } else {
                            null
                        },
                        supportingText = {
                            when {
                                normalizedLookup.length == 1 ->
                                    Text("Type at least 2 characters for Reddit suggestions.")
                                peopleSearchState.loading && searchMatches ->
                                    Text("Searching Reddit users…")
                                peopleSearchState.loaded && searchMatches && people.isEmpty() ->
                                    Text("No matching users. An exact username can still be used.")
                                else ->
                                    Text("Search Reddit users or enter an exact username.")
                            }
                        },
                    )

                    people.forEach { person ->
                        ModeratorUserSearchResult(
                            person = person,
                            enabled = !busy,
                            onClick = {
                                selectedPerson = person
                                username = person.username
                            },
                        )
                    }
                }
            }
        }

        item("ban-editor-duration") {
            OutlinedTextField(
                value = duration,
                onValueChange = { value ->
                    duration = value.filter(Char::isDigit).take(3)
                },
                label = { Text("Duration days") },
                supportingText = {
                    Text(
                        if (duration.isBlank()) {
                            "Permanent · enter 1–999 for a temporary ban"
                        } else {
                            "1–999 days"
                        },
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item("ban-editor-reason") {
            OutlinedTextField(
                value = banReason,
                onValueChange = { banReason = it.take(MODERATOR_BAN_REASON_MAX_CHARS) },
                label = { Text("Ban reason (optional)") },
                supportingText = {
                    Text("${banReason.length}/$MODERATOR_BAN_REASON_MAX_CHARS")
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item("ban-editor-note") {
            OutlinedTextField(
                value = moderatorNote,
                onValueChange = {
                    moderatorNote = it.take(MODERATOR_BAN_NOTE_MAX_CHARS)
                },
                label = { Text("Moderator note (optional)") },
                supportingText = {
                    Text("${moderatorNote.length}/$MODERATOR_BAN_NOTE_MAX_CHARS")
                },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item("ban-editor-message") {
            OutlinedTextField(
                value = messageToUser,
                onValueChange = {
                    messageToUser = it.take(MODERATOR_BAN_MESSAGE_MAX_CHARS)
                },
                label = { Text("Message to user (optional)") },
                supportingText = {
                    Text("${messageToUser.length}/$MODERATOR_BAN_MESSAGE_MAX_CHARS")
                },
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item("ban-editor-actions") {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilledTonalButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    enabled = !busy && targetValid && durationValid,
                    onClick = {
                        val target = normalizedUsername ?: return@FilledTonalButton
                        onConfirm(
                            RedditModeratorUserBanRequest(
                                accountStableId = accountStableId,
                                communityName = communityName,
                                username = target,
                                banned = true,
                                durationDays = durationDays,
                                banReason = banReason.trim().takeIf(String::isNotEmpty),
                                moderatorNote =
                                    moderatorNote.trim().takeIf(String::isNotEmpty),
                                messageToUser =
                                    messageToUser.trim().takeIf(String::isNotEmpty),
                            ),
                        )
                    },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Block,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Confirm ban")
                }
                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onBack,
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

// MODERATION02A5A2_SEARCHABLE_IME_STABLE_MUTE_EDITOR_V1
@Composable
private fun ModeratorMuteUserEditor(
    accountStableId: String,
    communityName: String,
    activeUsername: String,
    busy: Boolean,
    peopleSearchState: RedditPeopleSearchUiState,
    onSearchPeople: (String) -> Unit,
    onConfirm: (RedditModeratorUserMuteRequest) -> Unit,
    onBack: () -> Unit,
) {
    var username by rememberSaveable { mutableStateOf("") }
    var selectedPerson by remember { mutableStateOf<RedditPeopleSearchItem?>(null) }
    var moderatorNote by rememberSaveable { mutableStateOf("") }

    val normalizedLookup = username.trim().removePrefix("u/").trim()
    val normalizedUsername = runCatching { normalizeRedditSafetyUsername(username) }.getOrNull()
    val targetValid =
        normalizedUsername != null &&
            !normalizedUsername.equals(activeUsername.trim(), ignoreCase = true)
    val searchEligible = selectedPerson == null && normalizedLookup.length in 2..24
    val searchMatches =
        searchEligible && peopleSearchState.matches(accountStableId, normalizedLookup)
    val people = if (searchMatches) {
        peopleSearchState.items.asSequence()
            .filterNot { it.username.equals(activeUsername.trim(), ignoreCase = true) }
            .distinctBy { it.username.lowercase() }
            .take(MODERATOR_USER_SEARCH_RESULT_LIMIT)
            .toList()
    } else emptyList()

    LaunchedEffect(normalizedLookup, selectedPerson?.username, accountStableId) {
        if (selectedPerson == null && normalizedLookup.length in 2..24) {
            delay(MODERATOR_USER_SEARCH_DEBOUNCE_MS)
            onSearchPeople(normalizedLookup)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().imePadding().navigationBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item("mute-editor-header") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back to User management",
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text("Mute user", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "r/$communityName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item("mute-editor-explainer") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Text(
                    "Muting prevents this user from sending new modmail to r/$communityName " +
                        "while Reddit's mute is active. subshell verifies the muted-user " +
                        "relationship after the action.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item("mute-editor-user") {
            if (selectedPerson != null) {
                ModeratorSelectedMuteUser(
                    person = requireNotNull(selectedPerson),
                    enabled = !busy,
                    onClear = {
                        selectedPerson = null
                        username = ""
                    },
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SubshellSearchField(
                        query = username,
                        onQueryChange = {
                            username = it.take(24)
                            selectedPerson = null
                        },
                        placeholder = "Username",
                        trailingIcon = if (username.isNotEmpty()) {
                            {
                                IconButton(enabled = !busy, onClick = { username = "" }) {
                                    Icon(Icons.Rounded.Close, "Clear user search")
                                }
                            }
                        } else null,
                        supportingText = {
                            when {
                                normalizedLookup.length == 1 ->
                                    Text("Type at least 2 characters for Reddit suggestions.")
                                peopleSearchState.loading && searchMatches ->
                                    Text("Searching Reddit users…")
                                peopleSearchState.loaded && searchMatches && people.isEmpty() ->
                                    Text("No matching users. An exact username can still be used.")
                                else ->
                                    Text("Search Reddit users or enter an exact username.")
                            }
                        },
                    )
                    people.forEach { person ->
                        ModeratorUserSearchResult(
                            person = person,
                            enabled = !busy,
                            onClick = {
                                selectedPerson = person
                                username = person.username
                            },
                        )
                    }
                }
            }
        }
        item("mute-editor-note") {
            OutlinedTextField(
                value = moderatorNote,
                onValueChange = { moderatorNote = it.take(MODERATOR_MUTE_NOTE_MAX_CHARS) },
                label = { Text("Moderator note (optional)") },
                supportingText = {
                    Text("${moderatorNote.length}/$MODERATOR_MUTE_NOTE_MAX_CHARS")
                },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item("mute-editor-actions") {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilledTonalButton(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    enabled = !busy && targetValid,
                    onClick = {
                        val target = normalizedUsername ?: return@FilledTonalButton
                        onConfirm(
                            RedditModeratorUserMuteRequest(
                                accountStableId = accountStableId,
                                communityName = communityName,
                                username = target,
                                muted = true,
                                moderatorNote = moderatorNote.trim().takeIf(String::isNotEmpty),
                            ),
                        )
                    },
                ) {
                    Icon(Icons.AutoMirrored.Rounded.VolumeOff, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Confirm mute")
                }
                TextButton(modifier = Modifier.fillMaxWidth(), onClick = onBack) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun ModeratorSelectedMuteUser(
    person: RedditPeopleSearchItem,
    enabled: Boolean,
    onClear: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 10.dp, end = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ModeratorUserAvatar(person.username, person.avatarImageUri, 44)
            Column(Modifier.weight(1f)) {
                Text(
                    "u/${person.username}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "Selected mute target",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(enabled = enabled, onClick = onClear) {
                Icon(Icons.Rounded.Close, "Change mute target")
            }
        }
    }
}

// MODERATION02A5A3_SEARCHABLE_IME_STABLE_APPROVE_EDITOR_V1
@Composable
private fun ModeratorApproveUserEditor(
    accountStableId: String,
    communityName: String,
    activeUsername: String,
    busy: Boolean,
    peopleSearchState: RedditPeopleSearchUiState,
    onSearchPeople: (String) -> Unit,
    onConfirm: (RedditModeratorUserApprovalRequest) -> Unit,
    onBack: () -> Unit,
) {
    var username by rememberSaveable { mutableStateOf("") }
    var selectedPerson by remember { mutableStateOf<RedditPeopleSearchItem?>(null) }

    val normalizedLookup = username.trim().removePrefix("u/").trim()
    val normalizedUsername = runCatching { normalizeRedditSafetyUsername(username) }.getOrNull()
    val targetValid =
        normalizedUsername != null &&
            !normalizedUsername.equals(activeUsername.trim(), ignoreCase = true)
    val searchEligible = selectedPerson == null && normalizedLookup.length in 2..24
    val searchMatches =
        searchEligible && peopleSearchState.matches(accountStableId, normalizedLookup)
    val people = if (searchMatches) {
        peopleSearchState.items.asSequence()
            .filterNot { it.username.equals(activeUsername.trim(), ignoreCase = true) }
            .distinctBy { it.username.lowercase() }
            .take(MODERATOR_USER_SEARCH_RESULT_LIMIT)
            .toList()
    } else emptyList()

    LaunchedEffect(normalizedLookup, selectedPerson?.username, accountStableId) {
        if (selectedPerson == null && normalizedLookup.length in 2..24) {
            delay(MODERATOR_USER_SEARCH_DEBOUNCE_MS)
            onSearchPeople(normalizedLookup)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().imePadding().navigationBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item("approve-editor-header") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back to User management",
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text("Approve user", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "r/$communityName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item("approve-editor-explainer") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Text(
                    "Approving adds this account to r/$communityName's approved-user list. " +
                        "Reddit controls how approved-user status affects participation based " +
                        "on the community's settings. subshell verifies the relationship " +
                        "after the action.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item("approve-editor-user") {
            if (selectedPerson != null) {
                ModeratorSelectedApprovedUser(
                    person = requireNotNull(selectedPerson),
                    enabled = !busy,
                    onClear = {
                        selectedPerson = null
                        username = ""
                    },
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SubshellSearchField(
                        query = username,
                        onQueryChange = {
                            username = it.take(24)
                            selectedPerson = null
                        },
                        placeholder = "Username",
                        trailingIcon = if (username.isNotEmpty()) {
                            {
                                IconButton(enabled = !busy, onClick = { username = "" }) {
                                    Icon(Icons.Rounded.Close, "Clear user search")
                                }
                            }
                        } else null,
                        supportingText = {
                            when {
                                normalizedLookup.length == 1 ->
                                    Text("Type at least 2 characters for Reddit suggestions.")
                                peopleSearchState.loading && searchMatches ->
                                    Text("Searching Reddit users…")
                                peopleSearchState.loaded && searchMatches && people.isEmpty() ->
                                    Text("No matching users. An exact username can still be used.")
                                else ->
                                    Text("Search Reddit users or enter an exact username.")
                            }
                        },
                    )
                    people.forEach { person ->
                        ModeratorUserSearchResult(
                            person = person,
                            enabled = !busy,
                            onClick = {
                                selectedPerson = person
                                username = person.username
                            },
                        )
                    }
                }
            }
        }
        item("approve-editor-actions") {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilledTonalButton(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    enabled = !busy && targetValid,
                    onClick = {
                        val target = normalizedUsername ?: return@FilledTonalButton
                        onConfirm(
                            RedditModeratorUserApprovalRequest(
                                accountStableId = accountStableId,
                                communityName = communityName,
                                username = target,
                                approved = true,
                            ),
                        )
                    },
                ) {
                    Icon(Icons.Rounded.PersonAdd, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Confirm approve")
                }
                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onBack,
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun ModeratorSelectedApprovedUser(
    person: RedditPeopleSearchItem,
    enabled: Boolean,
    onClear: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 10.dp, end = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ModeratorUserAvatar(person.username, person.avatarImageUri, 44)
            Column(Modifier.weight(1f)) {
                Text(
                    "u/${person.username}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "Selected approved-user target",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(enabled = enabled, onClick = onClear) {
                Icon(Icons.Rounded.Close, "Change approved-user target")
            }
        }
    }
}

@Composable
private fun ModeratorUserSearchResult(
    person: RedditPeopleSearchItem,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ListCard(
        headline = "u/${person.username}",
        supporting = person.description,
        supportingMaxLines = 2,
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        leading = {
            ModeratorUserAvatar(
                username = person.username,
                avatarImageUri = person.avatarImageUri,
                size = 40,
            )
        },
    )
}

@Composable
private fun ModeratorSelectedBanUser(
    person: RedditPeopleSearchItem,
    enabled: Boolean,
    onClear: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 10.dp, end = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ModeratorUserAvatar(
                username = person.username,
                avatarImageUri = person.avatarImageUri,
                size = 44,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "u/${person.username}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Selected ban target",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(enabled = enabled, onClick = onClear) {
                Icon(Icons.Rounded.Close, contentDescription = "Change ban target")
            }
        }
    }
}

@Composable
private fun ModeratorUserAvatar(
    username: String,
    avatarImageUri: String?,
    size: Int,
) {
    RemoteIdentityImage(
        uri = avatarImageUri,
        contentDescription = "Avatar for u/$username",
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = username.firstOrNull()?.uppercase() ?: "u",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private const val MODERATOR_USER_SEARCH_DEBOUNCE_MS = 300L
private const val MODERATOR_USER_SEARCH_RESULT_LIMIT = 6

private fun moderatorBanAgeLabel(
    createdEpochSeconds: Long,
    nowEpochSeconds: Long = Instant.now().epochSecond,
): String? {
    if (createdEpochSeconds <= 0L) return null
    val seconds = Duration.between(
        Instant.ofEpochSecond(createdEpochSeconds),
        Instant.ofEpochSecond(nowEpochSeconds),
    ).seconds.coerceAtLeast(0L)
    return when {
        seconds < 60L -> "just now"
        seconds < 3_600L -> "${seconds / 60L}m ago"
        seconds < 86_400L -> "${seconds / 3_600L}h ago"
        seconds < 604_800L -> "${seconds / 86_400L}d ago"
        else -> "${seconds / 604_800L}w ago"
    }
}

@Composable
private fun ModeratorUserManagementNotice(
    title: String,
    message: String,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            actionLabel?.let { label ->
                TextButton(onClick = onAction) {
                    Text(label)
                }
            }
        }
    }
}
