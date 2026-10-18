package app.subshell.prototype.ui.components

import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import app.subshell.prototype.data.normalizeRedditCommunityName
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.subshell.prototype.data.account.ModeratorInviteFailureKind
import app.subshell.prototype.data.account.ModeratorPermissionUpdateFailureKind
import app.subshell.prototype.data.account.ModeratorPermissionUpdateUiState
import app.subshell.prototype.data.account.ModeratorRemovalFailureKind
import app.subshell.prototype.data.account.ModeratorRemovalUiState
import app.subshell.prototype.data.account.ModeratorInviteUiState
import app.subshell.prototype.data.account.RedditCommunityInfoUiState
import app.subshell.prototype.data.account.RedditModeratorInviteRequest
import app.subshell.prototype.data.account.RedditModeratorPermissionUpdateRequest
import app.subshell.prototype.data.account.RedditModeratorRemovalRequest
import app.subshell.prototype.data.account.RedditModeratorPermission
import app.subshell.prototype.data.account.normalizeRedditSafetyUsername
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.toSafeTechnicalDetail
import app.subshell.prototype.model.CommunityModerator
import app.subshell.prototype.model.RedditPeopleSearchItem
import app.subshell.prototype.ui.symbols.Symbols
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

// MODERATION02A5A4_READ_ONLY_MODERATOR_TEAM_ENTRY_V1
@Composable
fun ModeratorTeamButton(onClick: () -> Unit) {
    OutlinedButton(
        modifier = Modifier.heightIn(min = 48.dp),
        onClick = onClick,
    ) {
        Icon(imageVector = Symbols.Person, contentDescription = null,
            modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Moderators")
    }
}

// Content only: the existing R4 workspace owns the one sheet and system Back.
@Composable
fun ModeratorTeamContent(
    accountStableId: String,
    communityName: String,
    activeUsername: String,
    canRead: Boolean,
    uiState: RedditCommunityInfoUiState,
    avatarImageUri: (String) -> String?,
    onEnsureIdentity: suspend (String) -> Unit,
    onLoad: (Boolean) -> Unit,
    inviteState: ModeratorInviteUiState,
    onOpenInviteContext: () -> Unit,
    onInvite: (RedditModeratorInviteRequest) -> Unit,
    onAuthorizeInvite: () -> Unit,
    onDismissInvite: () -> Unit,
    permissionUpdateState: ModeratorPermissionUpdateUiState,
    onOpenPermissionUpdateContext: (String) -> Unit,
    onUpdatePermissions: (RedditModeratorPermissionUpdateRequest) -> Unit,
    onAuthorizePermissionUpdate: () -> Unit,
    onDismissPermissionUpdate: () -> Unit,
    removalState: ModeratorRemovalUiState,
    onOpenRemovalContext: (String) -> Unit,
    onRemoveModerator: (RedditModeratorRemovalRequest) -> Unit,
    onAuthorizeRemoval: () -> Unit,
    onDismissRemoval: () -> Unit,
    onBack: () -> Unit,
) {
    var query by rememberSaveable(accountStableId, communityName) { mutableStateOf("") }
    var showInviteEditor by rememberSaveable(accountStableId, communityName) { mutableStateOf(false) }
    var permissionEditorUsername by rememberSaveable(accountStableId, communityName) {
        mutableStateOf<String?>(null)
    }
    var removalEditorUsername by rememberSaveable(accountStableId, communityName) {
        mutableStateOf<String?>(null)
    }
    val identities = remember(accountStableId, communityName) { Semaphore(3) }
    val projection = moderatorTeamProjection(uiState, accountStableId, communityName, canRead)
    val rows = moderatorTeamFilteredRows(projection.moderators, query)
    val canInvite = moderatorTeamCanInvite(projection.moderators, activeUsername)
    val inviteMatches = inviteState.matches(accountStableId, communityName)
    // LOWUI8_DISMISSED_MODERATOR_RESULTS_STAY_DISMISSED_V1 (OCB-148): a
    // dismissed editor keeps its last result for the coordinator; the banner
    // and its refresh belong to a visible one only, so reopening Moderators
    // no longer brings an old result (and a forced reload) back.
    val invitedUsername = inviteState.lastInvitedUsername.takeIf { inviteMatches && inviteState.visible }
    val permissionUpdateMatches = permissionUpdateState.matches(accountStableId, communityName)
    val updatedUsername = permissionUpdateState.lastUpdatedUsername.takeIf {
        permissionUpdateMatches && permissionUpdateState.visible
    }
    val permissionTarget = permissionEditorUsername?.let { username ->
        projection.moderators.firstOrNull { moderator ->
            moderator.username.equals(username, ignoreCase = true)
        }
    }
    val removalMatches = removalState.matches(accountStableId, communityName)
    val removedUsername = removalState.lastRemovedUsername.takeIf { removalMatches && removalState.visible }
    val removalTarget = removalEditorUsername?.let { username ->
        projection.moderators.firstOrNull { moderator ->
            moderator.username.equals(username, ignoreCase = true)
        }
    }

    LaunchedEffect(accountStableId, communityName, canRead) {
        if (canRead) onLoad(false)
    }
    // ISSUE86_EDITOR_STAYS_WHILE_ITS_WRITE_RUNS_V1: Back (or a sheet swipe)
    // closed an editor while its write was in flight; dismiss cleared the busy
    // flag and the result (even OUTCOME_UNKNOWN) landed in hidden state, so the
    // moderator could send again. While a write runs, Back is swallowed and the
    // sheet refuses to hide (LocalModeratorWorkspaceBusy).
    val inviteBusy = showInviteEditor && inviteMatches && inviteState.busy
    val permissionUpdateBusy =
        permissionEditorUsername != null && permissionUpdateMatches && permissionUpdateState.busy
    val removalBusy = removalEditorUsername != null && removalMatches && removalState.busy
    val workspaceBusy = LocalModeratorWorkspaceBusy.current
    val anyEditorBusy = inviteBusy || permissionUpdateBusy || removalBusy
    SideEffect { workspaceBusy?.busy = anyEditorBusy }
    DisposableEffect(workspaceBusy) { onDispose { workspaceBusy?.busy = false } }
    BackHandler(enabled = showInviteEditor) {
        if (!inviteBusy) {
            showInviteEditor = false
            onDismissInvite()
        }
    }
    // LOWUI8_HIDDEN_EDITOR_DOES_NOT_TAKE_BACK_V1 (OCB-305): an editor whose
    // moderator is not in the current list is not shown, so its Back handler
    // stands down too; Back then leaves Moderators as the screen suggests.
    BackHandler(enabled = permissionEditorUsername != null && permissionTarget != null) {
        if (!permissionUpdateBusy) {
            permissionEditorUsername = null
            onDismissPermissionUpdate()
        }
    }
    BackHandler(enabled = removalEditorUsername != null && removalTarget != null) {
        if (!removalBusy) {
            removalEditorUsername = null
            onDismissRemoval()
        }
    }
    LaunchedEffect(inviteState.completionRevision, invitedUsername) {
        if (inviteMatches && invitedUsername != null && inviteState.completionRevision > 0L) {
            showInviteEditor = false
            onLoad(true)
        }
    }
    LaunchedEffect(permissionUpdateState.completionRevision, updatedUsername) {
        if (
            permissionUpdateMatches &&
            updatedUsername != null &&
            permissionUpdateState.completionRevision > 0L
        ) {
            permissionEditorUsername = null
            onLoad(true)
        }
    }
    // LOWUI6_UNKNOWN_MODERATOR_WRITE_IS_READ_BACK_V1 (OCB-149): a permission
    // change or removal whose answer was lost reloads the moderator list from
    // Reddit at once, so the list behind the editor shows what happened.
    val permissionOutcomeUnknown = permissionUpdateMatches &&
        permissionUpdateState.failureKind == ModeratorPermissionUpdateFailureKind.OUTCOME_UNKNOWN
    val removalOutcomeUnknown = removalMatches &&
        removalState.failureKind == ModeratorRemovalFailureKind.OUTCOME_UNKNOWN
    LaunchedEffect(permissionOutcomeUnknown, removalOutcomeUnknown) {
        if (permissionOutcomeUnknown || removalOutcomeUnknown) onLoad(true)
    }
    LaunchedEffect(removalState.completionRevision, removedUsername) {
        if (
            removalMatches &&
            removedUsername != null &&
            removalState.completionRevision > 0L
        ) {
            removalEditorUsername = null
            onLoad(true)
        }
    }

    if (showInviteEditor) {
        ModeratorInviteEditor(
            accountStableId = accountStableId,
            communityName = communityName,
            activeUsername = activeUsername,
            activeModerators = projection.moderators,
            state = inviteState,
            onInvite = onInvite,
            onAuthorize = onAuthorizeInvite,
            onBack = {
                showInviteEditor = false
                onDismissInvite()
            },
        )
        return
    }

    if (permissionTarget != null) {
        ModeratorPermissionEditor(
            accountStableId = accountStableId,
            communityName = communityName,
            target = permissionTarget,
            state = permissionUpdateState,
            onUpdate = onUpdatePermissions,
            onAuthorize = onAuthorizePermissionUpdate,
            onBack = {
                permissionEditorUsername = null
                onDismissPermissionUpdate()
            },
        )
        return
    }

    if (removalTarget != null) {
        ModeratorRemovalEditor(
            accountStableId = accountStableId,
            communityName = communityName,
            target = removalTarget,
            avatarUri = avatarImageUri(removalTarget.username),
            state = removalState,
            onRemove = onRemoveModerator,
            onAuthorize = onAuthorizeRemoval,
            onBack = {
                removalEditorUsername = null
                onDismissRemoval()
            },
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().imePadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("moderator-team-header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Symbols.ArrowBack, "Back to Mod tools")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Moderators", style = MaterialTheme.typography.headlineSmall)
                    Text("r/$communityName", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(enabled = canRead && !projection.loading, onClick = { onLoad(true) }) {
                    Icon(Symbols.Refresh, "Refresh moderators")
                }
            }
        }
        item("moderator-team-invite") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalButton(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    enabled = canRead && projection.ready && canInvite && !projection.loading,
                    onClick = {
                        onOpenInviteContext()
                        showInviteEditor = true
                    },
                ) {
                    Icon(Symbols.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Invite moderator")
                }
                if (canRead && projection.ready && !canInvite) {
                    Text(
                        "Reddit requires All moderator permissions to invite moderators. " +
                            "This account is not reported with All permissions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item("moderator-team-read-only") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Column(modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Moderator permissions", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Permissions are reported by Reddit. " +
                            "Eligible moderators can be edited individually. " +
                            "Moderator invitations are sent separately.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        invitedUsername?.let { username ->
            item("moderator-team-invite-success") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("Invitation sent", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "u/$username becomes a moderator after accepting the invitation on Reddit.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
        updatedUsername?.let { username ->
            item("moderator-team-permission-success") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("Permissions updated", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Reddit acknowledged the permission change for u/$username.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
        removedUsername?.let { username ->
            item("moderator-team-removal-success") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("Moderator removed", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Reddit acknowledged removal of u/$username. Refreshing the moderator list.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
        if (projection.ready && projection.moderators.isNotEmpty()) {
            item("moderator-team-search") {
                SubshellSearchField(
                    query = query,
                    onQueryChange = { query = it.take(100) },
                    placeholder = "Search moderators",
                    supportingText = { Text("Filters the loaded moderator list") },
                )
            }
        }
        if (projection.loading) {
            item("moderator-team-loading") {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically) {
                    InlineProgressIndicator()
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(if (projection.ready) "Refreshing moderators…" else "Loading moderators…")
                }
            }
        }
        projection.notice?.let { notice ->
            item("moderator-team-notice") {
                Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Text(
                        text = when (notice) {
                            ModeratorTeamNotice.ACCESS_UNAVAILABLE ->
                                "Moderator overview is unavailable for this account."
                            ModeratorTeamNotice.MODERATORS_UNAVAILABLE ->
                                "Moderator information could not be loaded. This does not mean the community has no moderators."
                            ModeratorTeamNotice.REFRESH_FAILED ->
                                "Could not refresh. Showing previously loaded moderator information."
                        },
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        if (projection.ready && rows.isEmpty() && !projection.loading) {
            item("moderator-team-empty") {
                Text(
                    text = if (query.isBlank()) "Reddit returned no moderators." else "No matching moderators.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(items = rows, key = { "moderator-${it.username.lowercase()}" }) { moderator ->
            // Same visible-row identity pattern as Community About. No bulk profile crawl.
            LaunchedEffect(accountStableId, communityName, moderator.username) {
                try {
                    identities.withPermit { onEnsureIdentity(moderator.username) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Avatar enrichment must not hide a valid moderator or permission row.
                }
            }
            val canEditPermissions = moderatorTeamCanEditPermissions(
                moderators = projection.moderators,
                activeUsername = activeUsername,
                target = moderator,
            )
            val canRemove = moderatorTeamCanRemove(
                moderators = projection.moderators,
                activeUsername = activeUsername,
                target = moderator,
            )
            // MODERATION02A5A6_PERMISSION_ACTION_CARD_ATTACHMENT_V1
            // MODERATION02A5A7_REMOVE_MODERATOR_V1
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column {
                    ModeratorTeamRow(
                        moderator = moderator,
                        isCurrentUser = moderatorTeamIsCurrentUser(
                            moderator.username,
                            activeUsername,
                        ),
                        avatarUri = avatarImageUri(moderator.username),
                    )
                    if (canEditPermissions) {
                        TextButton(
                            modifier = Modifier
                                .padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
                                .align(Alignment.Start),
                            onClick = {
                                onOpenPermissionUpdateContext(moderator.username)
                                permissionEditorUsername = moderator.username
                            },
                        ) {
                            Icon(
                                imageVector = Symbols.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Edit permissions")
                        }
                    }
                    if (canRemove) {
                        TextButton(
                            modifier = Modifier
                                .padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
                                .align(Alignment.Start),
                            onClick = {
                                onOpenRemovalContext(moderator.username)
                                removalEditorUsername = moderator.username
                            },
                        ) {
                            Icon(
                                imageVector = Symbols.Close,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Remove moderator",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
        if (projection.ready && projection.moderators.isNotEmpty()) {
            item("moderator-team-count") {
                Text(
                    text = "${projection.moderators.size} moderators loaded · Reddit order",
                    modifier = Modifier.padding(horizontal = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModeratorTeamRow(
    moderator: CommunityModerator,
    isCurrentUser: Boolean,
    avatarUri: String?,
) {
    val labels = moderatorTeamPermissionLabels(moderator.permissions)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RemoteIdentityImage(
                    uri = avatarUri,
                    contentDescription = "Avatar for u/${moderator.username}",
                    modifier = Modifier.size(44.dp).clip(CircleShape),
                ) {
                    Surface(modifier = Modifier.fillMaxSize(), shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(moderator.username.firstOrNull()?.uppercase() ?: "u",
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("u/${moderator.username}", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    if (isCurrentUser) {
                        Text("You", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (labels.isEmpty()) {
                // The existing parser cannot distinguish omitted permissions from an empty list.
                Text("Permission details not provided", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    labels.forEach { label ->
                        Surface(shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}


// MODERATION02A5A5_INVITE_EDITOR_V1
@Composable
private fun ModeratorInviteEditor(
    accountStableId: String,
    communityName: String,
    activeUsername: String,
    activeModerators: List<CommunityModerator>,
    state: ModeratorInviteUiState,
    onInvite: (RedditModeratorInviteRequest) -> Unit,
    onAuthorize: () -> Unit,
    onBack: () -> Unit,
) {
    val entryPoints = LocalModeratorUserManagement.current
    val peopleSearchState = entryPoints.peopleSearchState()
    var username by rememberSaveable(accountStableId, communityName) { mutableStateOf("") }
    var selectedPerson by remember(accountStableId, communityName) {
        mutableStateOf<RedditPeopleSearchItem?>(null)
    }
    var allPermissions by rememberSaveable(accountStableId, communityName) { mutableStateOf(false) }
    var selectedPermissionCodes by rememberSaveable(accountStableId, communityName) {
        mutableStateOf("")
    }
    var pendingConfirmation by remember(accountStableId, communityName) {
        mutableStateOf<RedditModeratorInviteRequest?>(null)
    }
    val selectedPermissions = selectedPermissionCodes
        .split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .mapNotNull { code -> RedditModeratorPermission.entries.firstOrNull { it.wireValue == code } }
        .toSet()
    val normalizedLookup = username.trim().removePrefix("u/").removePrefix("U/").trim()
    val normalizedUsername = runCatching { normalizeRedditSafetyUsername(username) }.getOrNull()
    val existingNames = activeModerators.mapTo(mutableSetOf()) { it.username.lowercase() }
    val targetValid = normalizedUsername != null &&
        !normalizedUsername.equals(activeUsername.trim().removePrefix("u/").removePrefix("U/"), ignoreCase = true) &&
        normalizedUsername.lowercase() !in existingNames
    val permissionValid = allPermissions || selectedPermissions.isNotEmpty()
    val searchEligible = selectedPerson == null && normalizedLookup.length in 2..24
    val searchMatches = searchEligible && peopleSearchState.matches(accountStableId, normalizedLookup)
    val people = if (searchMatches) {
        peopleSearchState.items.asSequence()
            .filterNot { it.username.equals(activeUsername, ignoreCase = true) }
            .filterNot { it.username.lowercase() in existingNames }
            .take(MODERATOR_INVITE_SEARCH_RESULT_LIMIT)
            .toList()
    } else {
        emptyList()
    }
    val exactUsernameFallback = normalizedUsername?.takeIf {
        selectedPerson == null &&
            searchMatches &&
            peopleSearchState.loaded &&
            people.isEmpty() &&
            targetValid
    }
    val stateMatches = state.matches(accountStableId, communityName)
    val busy = stateMatches && state.busy
    val outcomeUnknown = stateMatches && state.failureKind == ModeratorInviteFailureKind.OUTCOME_UNKNOWN
    val request = if (targetValid && permissionValid) {
        runCatching {
            RedditModeratorInviteRequest(
                accountStableId = accountStableId,
                communityName = communityName,
                username = requireNotNull(normalizedUsername),
                allPermissions = allPermissions,
                permissions = if (allPermissions) emptySet() else selectedPermissions,
            )
        }.getOrNull()
    } else {
        null
    }

    pendingConfirmation?.let { pending ->
        val permissionSummary = if (pending.allPermissions) {
            "All permissions"
        } else {
            pending.permissions
                .map(::moderatorInvitePermissionLabel)
                .sorted()
                .joinToString(", ")
        }
        AlertDialog(
            onDismissRequest = { if (!busy) pendingConfirmation = null },
            title = { Text("Invite u/${pending.username}?") },
            text = {
                Text(
                    "Reddit will send a moderator invitation for r/$communityName with: " +
                        permissionSummary + ". The account is not a moderator until it accepts.",
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        pendingConfirmation = null
                        onInvite(pending)
                    },
                ) {
                    Text("Send invitation")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = { pendingConfirmation = null },
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    LaunchedEffect(accountStableId, normalizedLookup, selectedPerson?.username) {
        if (searchEligible) {
            delay(MODERATOR_INVITE_SEARCH_DEBOUNCE_MS)
            entryPoints.searchPeople(normalizedLookup)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().imePadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("moderator-invite-header") {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(enabled = !busy, onClick = onBack) {
                    Icon(Symbols.ArrowBack, "Back to moderators")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Invite moderator", style = MaterialTheme.typography.headlineSmall)
                    Text("r/$communityName", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item("moderator-invite-explainer") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    "Reddit sends an invitation. The account becomes a moderator only after accepting it.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item("moderator-invite-target") {
            if (selectedPerson == null) {
                SubshellSearchField(
                    query = username,
                    onQueryChange = { username = it.take(24) },
                    placeholder = "Search Reddit users",
                    enabled = !busy,
                    supportingText = {
                        Text(
                            when {
                                normalizedUsername != null && normalizedUsername.lowercase() in existingNames ->
                                    "This account is already in the active moderator list"
                                normalizedUsername != null && normalizedUsername.equals(
                                    activeUsername.trim().removePrefix("u/").removePrefix("U/"),
                                    ignoreCase = true,
                                ) -> "You cannot invite your own account"
                                normalizedUsername != null ->
                                    "Select a result, or use the exact username after reviewing it"
                                else -> "Enter a valid Reddit username"
                            },
                        )
                    },
                )
            } else {
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
                        ModeratorInviteAvatar(requireNotNull(selectedPerson))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("u/${selectedPerson!!.username}", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold)
                            Text("Selected moderator invite", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(enabled = !busy, onClick = {
                            selectedPerson = null
                            username = ""
                        }) {
                            Icon(Symbols.Close, "Change moderator invite target")
                        }
                    }
                }
            }
        }
        if (selectedPerson == null && searchEligible) {
            when {
                peopleSearchState.loading && peopleSearchState.matches(accountStableId, normalizedLookup) -> {
                    item("moderator-invite-search-loading") {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically) {
                            InlineProgressIndicator()
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Searching Reddit users…")
                        }
                    }
                }
                people.isNotEmpty() -> {
                    items(items = people, key = { "moderator-invite-${it.username.lowercase()}" }) { person ->
                        ListCard(
                            headline = "u/${person.username}",
                            supporting = person.description,
                            onClick = {
                                selectedPerson = person
                                username = person.username
                            },
                            enabled = !busy,
                            shape = MaterialTheme.shapes.large,
                            leading = { ModeratorInviteAvatar(person) },
                        )
                    }
                }
                peopleSearchState.loaded && peopleSearchState.matches(accountStableId, normalizedLookup) -> {
                    item("moderator-invite-search-empty") {
                        if (exactUsernameFallback != null) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        "No profile match",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        "Reddit search did not return a profile for " +
                                            "u/$exactUsernameFallback. You can still try this exact " +
                                            "username; Reddit validates the account when the " +
                                            "invitation is sent.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        } else {
                            Text(
                                "No matching Reddit users.",
                                modifier = Modifier.padding(horizontal = 4.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        item("moderator-invite-permissions-title") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Permissions", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Text(
                    "Choose only the permissions this moderator needs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item("moderator-invite-all-permissions") {
            ModeratorInvitePermissionRow(
                label = "All permissions",
                description = "Full moderator access",
                checked = allPermissions,
                enabled = !busy,
                onCheckedChange = { checked ->
                    allPermissions = checked
                    if (checked) selectedPermissionCodes = ""
                },
            )
        }
        items(items = RedditModeratorPermission.entries, key = { "invite-permission-${it.wireValue}" }) { permission ->
            val checked = permission in selectedPermissions
            ModeratorInvitePermissionRow(
                label = moderatorInvitePermissionLabel(permission),
                description = moderatorInvitePermissionDescription(permission),
                checked = checked,
                enabled = !busy && !allPermissions,
                onCheckedChange = { value ->
                    val updated = selectedPermissions.toMutableSet()
                    if (value) updated += permission else updated -= permission
                    selectedPermissionCodes = updated.map { it.wireValue }.sorted().joinToString(",")
                },
            )
        }
        if (!permissionValid) {
            item("moderator-invite-permission-required") {
                Text("Choose at least one permission or All permissions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (stateMatches && state.authorizationRequired) {
            item("moderator-invite-authorize") {
                Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Authorization required", style = MaterialTheme.typography.titleSmall)
                        Text("subshell needs Reddit's moderator-management permission before it can send this invitation.")
                        FilledTonalButton(enabled = !state.authorizing, onClick = onAuthorize) {
                            Text(if (state.authorizing) "Authorizing…" else "Authorize moderator invites")
                        }
                    }
                }
            }
        }
        if (stateMatches && state.authorizationReady) {
            item("moderator-invite-authorized") {
                Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(
                        "Authorization updated. Review the target and permissions, then press Invite moderator again.",
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
        if (stateMatches && state.failureKind != null) {
            item("moderator-invite-failure") {
                InlineErrorNotice( // ISSUE222_ONE_INLINE_ERROR_NOTICE_V1
                    message = moderatorInviteFailureMessage(
                        requireNotNull(state.failureKind),
                        state.failureHttpStatusCode,
                        state.failureDetails,
                    ),
                )
            }
        }
        item("moderator-invite-confirm") {
            FilledTonalButton(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                enabled = request != null && !busy &&
                    !(stateMatches && state.authorizationRequired) && !outcomeUnknown,
                onClick = { request?.let { pendingConfirmation = it } },
            ) {
                Text(if (busy && state.sending) "Sending invitation…" else "Invite moderator")
            }
        }
    }
}

@Composable
private fun ModeratorInviteAvatar(person: RedditPeopleSearchItem) {
    RemoteIdentityImage(
        uri = person.avatarImageUri,
        contentDescription = "Avatar for u/${person.username}",
        modifier = Modifier.size(44.dp).clip(CircleShape),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Box(contentAlignment = Alignment.Center) {
                Text(person.username.firstOrNull()?.uppercase() ?: "u",
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ModeratorInvitePermissionRow(
    label: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        // LOWUI7_SWITCH_ROWS_ARE_LABELLED_V1 (OCB-150): the row is the checkbox.
        modifier = Modifier.fillMaxWidth().toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Checkbox,
            onValueChange = onCheckedChange,
        ),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Checkbox(checked = checked, enabled = enabled, onCheckedChange = null)
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun moderatorInvitePermissionLabel(permission: RedditModeratorPermission): String = when (permission) {
    RedditModeratorPermission.ACCESS -> "Access"
    RedditModeratorPermission.CONFIG -> "Configuration"
    RedditModeratorPermission.FLAIR -> "Flair"
    RedditModeratorPermission.MAIL -> "Modmail"
    RedditModeratorPermission.POSTS -> "Posts"
    RedditModeratorPermission.WIKI -> "Wiki"
}

private fun moderatorInvitePermissionDescription(permission: RedditModeratorPermission): String = when (permission) {
    RedditModeratorPermission.ACCESS -> "Manage approved, banned, and muted users"
    RedditModeratorPermission.CONFIG -> "Manage community settings and configuration"
    RedditModeratorPermission.FLAIR -> "Manage user and post flair"
    RedditModeratorPermission.MAIL -> "Read and reply to moderator mail"
    RedditModeratorPermission.POSTS -> "Approve, remove, spam, and distinguish content"
    RedditModeratorPermission.WIKI -> "Manage community wiki pages and access"
}

private fun moderatorInviteFailureMessage(
    kind: ModeratorInviteFailureKind,
    httpStatusCode: Int?,
    failureDetails: FailureDetails?,
): String {
    val summary = when (kind) {
        ModeratorInviteFailureKind.CLIENT_CONFIGURATION ->
            "Moderator invitations are not configured for this Reddit session."
        ModeratorInviteFailureKind.SESSION ->
            "The Reddit session could not authorize this moderator invitation."
        ModeratorInviteFailureKind.AUTHORIZATION ->
            "Reddit refused this moderator invitation. Full moderator permissions are required."
        ModeratorInviteFailureKind.HTTP ->
            "Reddit rejected the invitation."
        ModeratorInviteFailureKind.TRANSPORT ->
            "The invitation could not reach Reddit. No automatic retry was made."
        ModeratorInviteFailureKind.PROTOCOL ->
            "Reddit returned an unexpected response to the moderator invitation."
        ModeratorInviteFailureKind.VALIDATION ->
            "Reddit rejected the moderator invitation details."
        ModeratorInviteFailureKind.OUTCOME_UNKNOWN ->
            "The invitation outcome is unknown. Do not send it again until you verify the moderator invitation on Reddit."
        ModeratorInviteFailureKind.ALREADY_MUTATING ->
            "Another moderator invitation is already in progress."
        ModeratorInviteFailureKind.ACCOUNT_CHANGED ->
            "The active Reddit account changed. Reopen Moderators before inviting anyone."
    }
    val technical = failureDetails?.toSafeTechnicalDetail()
        ?: httpStatusCode
            ?.takeIf { kind == ModeratorInviteFailureKind.HTTP }
            ?.let { status -> "HTTP $status" }
    return technical?.let { detail -> "$summary\n$detail" } ?: summary
}

// MODERATION02A5A6_PERMISSION_EDITOR_V1
@Composable
private fun ModeratorPermissionEditor(
    accountStableId: String,
    communityName: String,
    target: CommunityModerator,
    state: ModeratorPermissionUpdateUiState,
    onUpdate: (RedditModeratorPermissionUpdateRequest) -> Unit,
    onAuthorize: () -> Unit,
    onBack: () -> Unit,
) {
    val originalAllPermissions = target.permissions.any {
        it.trim().equals("all", ignoreCase = true)
    }
    val originalPermissions = target.permissions.asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .filterNot { it.equals("all", ignoreCase = true) }
        .mapNotNull { code ->
            RedditModeratorPermission.entries.firstOrNull {
                it.wireValue.equals(code, ignoreCase = true)
            }
        }
        .toSet()
    val editorKey = target.permissions.joinToString(",")
    var allPermissions by rememberSaveable(
        accountStableId,
        communityName,
        target.username,
        editorKey,
    ) {
        mutableStateOf(originalAllPermissions)
    }
    var selectedPermissionCodes by rememberSaveable(
        accountStableId,
        communityName,
        target.username,
        editorKey,
    ) {
        mutableStateOf(
            if (originalAllPermissions) {
                ""
            } else {
                originalPermissions
                    .map(RedditModeratorPermission::wireValue)
                    .sorted()
                    .joinToString(",")
            },
        )
    }
    var pendingConfirmation by remember(
        accountStableId,
        communityName,
        target.username,
    ) {
        mutableStateOf<RedditModeratorPermissionUpdateRequest?>(null)
    }
    val selectedPermissions = selectedPermissionCodes
        .split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .mapNotNull { code ->
            RedditModeratorPermission.entries.firstOrNull { it.wireValue == code }
        }
        .toSet()
    val stateMatches = state.matches(accountStableId, communityName, target.username)
    val busy = stateMatches && state.busy
    val outcomeUnknown = stateMatches &&
        state.failureKind == ModeratorPermissionUpdateFailureKind.OUTCOME_UNKNOWN
    val permissionValid = allPermissions || selectedPermissions.isNotEmpty()
    val changed = if (allPermissions) {
        !originalAllPermissions
    } else {
        originalAllPermissions || selectedPermissions != originalPermissions
    }
    val request = if (
        moderatorTeamPermissionSetIsEditable(target.permissions) &&
        permissionValid &&
        changed
    ) {
        runCatching {
            // ISSUE53_CANONICAL_MODERATOR_TARGET_V1: the team lists usernames in
            // display case; the request requires canonical names (a mixed-case
            // name made Save permissions do nothing).
            RedditModeratorPermissionUpdateRequest(
                accountStableId = accountStableId,
                communityName = normalizeRedditCommunityName(communityName),
                username = normalizeRedditSafetyUsername(target.username),
                allPermissions = allPermissions,
                permissions = if (allPermissions) emptySet() else selectedPermissions,
            )
        }.getOrNull()
    } else {
        null
    }

    pendingConfirmation?.let { pending ->
        val permissionSummary = if (pending.allPermissions) {
            "All permissions"
        } else {
            pending.permissions
                .map(::moderatorInvitePermissionLabel)
                .sorted()
                .joinToString(", ")
        }
        AlertDialog(
            onDismissRequest = { if (!busy) pendingConfirmation = null },
            title = { Text("Update u/${pending.username}?") },
            text = {
                Text(
                    "Replace this moderator's permissions for r/$communityName with: " +
                        permissionSummary + ". This does not remove the moderator.",
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        pendingConfirmation = null
                        onUpdate(pending)
                    },
                ) {
                    Text("Save changes")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = { pendingConfirmation = null },
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().imePadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("moderator-permission-header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(enabled = !busy, onClick = onBack) {
                    Icon(Symbols.ArrowBack, "Back to moderators")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Edit permissions", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "u/${target.username} · r/$communityName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item("moderator-permission-explainer") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    "This replaces the selected moderator permissions. Reddit may refuse a " +
                        "change when the target is above you in the moderator hierarchy. " +
                        "subshell never edits your own moderator permissions here.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item("moderator-permission-all") {
            ModeratorInvitePermissionRow(
                label = "All permissions",
                description = "Full moderator access",
                checked = allPermissions,
                enabled = !busy,
                onCheckedChange = { checked ->
                    allPermissions = checked
                    if (checked) selectedPermissionCodes = ""
                },
            )
        }
        items(
            items = RedditModeratorPermission.entries,
            key = { "edit-permission-${it.wireValue}" },
        ) { permission ->
            val checked = permission in selectedPermissions
            ModeratorInvitePermissionRow(
                label = moderatorInvitePermissionLabel(permission),
                description = moderatorInvitePermissionDescription(permission),
                checked = checked,
                enabled = !busy && !allPermissions,
                onCheckedChange = { value ->
                    val updated = selectedPermissions.toMutableSet()
                    if (value) updated += permission else updated -= permission
                    selectedPermissionCodes = updated
                        .map(RedditModeratorPermission::wireValue)
                        .sorted()
                        .joinToString(",")
                },
            )
        }
        if (!permissionValid) {
            item("moderator-permission-required") {
                Text(
                    "Choose at least one permission or All permissions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (permissionValid && !changed) {
            item("moderator-permission-unchanged") {
                Text(
                    "No permission changes selected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (stateMatches && state.authorizationRequired) {
            item("moderator-permission-authorize") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Authorization required", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "subshell needs Reddit's moderator-management permission before " +
                                "it can update moderator permissions.",
                        )
                        FilledTonalButton(enabled = !state.authorizing, onClick = onAuthorize) {
                            Text(
                                if (state.authorizing) {
                                    "Authorizing…"
                                } else {
                                    "Authorize permission changes"
                                },
                            )
                        }
                    }
                }
            }
        }
        if (stateMatches && state.authorizationReady) {
            item("moderator-permission-authorized") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        "Authorization updated. Review the permissions, then press Save " +
                            "permissions again.",
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
        if (stateMatches && state.failureKind != null) {
            item("moderator-permission-failure") {
                InlineErrorNotice( // ISSUE222_ONE_INLINE_ERROR_NOTICE_V1
                    message = moderatorPermissionUpdateFailureMessage(
                        requireNotNull(state.failureKind),
                        state.failureHttpStatusCode,
                        state.failureDetails,
                    ),
                )
            }
        }
        item("moderator-permission-confirm") {
            FilledTonalButton(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                enabled = request != null && !busy &&
                    !(stateMatches && state.authorizationRequired) && !outcomeUnknown,
                onClick = { request?.let { pendingConfirmation = it } },
            ) {
                Text(if (busy && state.sending) "Saving permissions…" else "Save permissions")
            }
        }
    }
}

private fun moderatorPermissionUpdateFailureMessage(
    kind: ModeratorPermissionUpdateFailureKind,
    httpStatusCode: Int?,
    failureDetails: FailureDetails?,
): String {
    val summary = when (kind) {
        ModeratorPermissionUpdateFailureKind.CLIENT_CONFIGURATION ->
            "Moderator permission changes are not configured for this Reddit session."
        ModeratorPermissionUpdateFailureKind.SESSION ->
            "The Reddit session could not authorize this moderator permission change."
        ModeratorPermissionUpdateFailureKind.AUTHORIZATION ->
            "Reddit refused this permission change. The target may be outside your editable " +
                "moderator hierarchy."
        ModeratorPermissionUpdateFailureKind.HTTP ->
            "Reddit rejected the moderator permission change."
        ModeratorPermissionUpdateFailureKind.TRANSPORT ->
            "The permission change could not reach Reddit. No automatic retry was made."
        ModeratorPermissionUpdateFailureKind.PROTOCOL ->
            "Reddit returned an unexpected response to the moderator permission change."
        ModeratorPermissionUpdateFailureKind.VALIDATION ->
            "Reddit rejected the moderator permission details."
        ModeratorPermissionUpdateFailureKind.OUTCOME_UNKNOWN ->
            "The permission-change outcome is unknown. Refresh the moderator list before " +
                "trying again."
        ModeratorPermissionUpdateFailureKind.ALREADY_MUTATING ->
            "Another moderator permission change is already in progress."
        ModeratorPermissionUpdateFailureKind.ACCOUNT_CHANGED ->
            "The active Reddit account changed. Reopen Moderators before changing permissions."
    }
    val technical = failureDetails?.toSafeTechnicalDetail()
        ?: httpStatusCode
            ?.takeIf { kind == ModeratorPermissionUpdateFailureKind.HTTP }
            ?.let { status -> "HTTP $status" }
    return technical?.let { detail -> "$summary\n$detail" } ?: summary
}


// MODERATION02A5A7_REMOVE_MODERATOR_EDITOR_V1
@Composable
internal fun ModeratorRemovalEditor(
    accountStableId: String,
    communityName: String,
    target: CommunityModerator,
    avatarUri: String?,
    state: ModeratorRemovalUiState,
    onRemove: (RedditModeratorRemovalRequest) -> Unit,
    onAuthorize: () -> Unit,
    onBack: () -> Unit,
) {
    var pendingConfirmation by remember(
        accountStableId,
        communityName,
        target.username,
    ) {
        mutableStateOf<RedditModeratorRemovalRequest?>(null)
    }
    val stateMatches = state.matches(accountStableId, communityName, target.username)
    val busy = stateMatches && state.busy
    val outcomeUnknown = stateMatches &&
        state.failureKind == ModeratorRemovalFailureKind.OUTCOME_UNKNOWN
    val request = runCatching {
        // ISSUE53_CANONICAL_MODERATOR_TARGET_V1
        RedditModeratorRemovalRequest(
            accountStableId = accountStableId,
            communityName = normalizeRedditCommunityName(communityName),
            username = normalizeRedditSafetyUsername(target.username),
        )
    }.getOrNull()

    pendingConfirmation?.let { pending ->
        AlertDialog(
            onDismissRequest = { if (!busy) pendingConfirmation = null },
            title = { Text("Remove u/${pending.username}?") },
            text = {
                Text(
                    "Remove u/${pending.username} from the moderator team for " +
                        "r/$communityName? Their moderator access will be revoked. " +
                        "Re-adding them requires a new invitation. Reddit may refuse the " +
                        "removal when the target is above you in the moderator hierarchy.",
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        pendingConfirmation = null
                        onRemove(pending)
                    },
                ) {
                    Text(
                        "Remove moderator",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = { pendingConfirmation = null },
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().imePadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("moderator-removal-header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(enabled = !busy, onClick = onBack) {
                    Icon(Symbols.ArrowBack, "Back to moderators")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Remove moderator", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "u/${target.username} · r/$communityName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item("moderator-removal-explainer") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Text(
                    "Removing a moderator revokes their moderator access. " +
                        "Re-adding them requires a new invitation. Reddit decides whether " +
                        "this moderator can be removed based on the moderator hierarchy.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
        item("moderator-removal-target") {
            ModeratorTeamRow(
                moderator = target,
                isCurrentUser = false,
                avatarUri = avatarUri,
            )
        }
        if (stateMatches && state.authorizationRequired) {
            item("moderator-removal-authorize") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Authorization required", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "subshell needs Reddit's moderator-management permission before " +
                                "it can remove this moderator.",
                        )
                        FilledTonalButton(enabled = !state.authorizing, onClick = onAuthorize) {
                            Text(
                                if (state.authorizing) {
                                    "Authorizing…"
                                } else {
                                    "Authorize moderator removal"
                                },
                            )
                        }
                    }
                }
            }
        }
        if (stateMatches && state.authorizationReady) {
            item("moderator-removal-authorized") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        "Authorization updated. Review the target, then press Remove " +
                            "moderator again.",
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
        if (stateMatches && state.failureKind != null) {
            item("moderator-removal-failure") {
                InlineErrorNotice( // ISSUE222_ONE_INLINE_ERROR_NOTICE_V1
                    message = moderatorRemovalFailureMessage(
                        requireNotNull(state.failureKind),
                        state.failureHttpStatusCode,
                        state.failureDetails,
                    ),
                )
            }
        }
        item("moderator-removal-confirm") {
            FilledTonalButton(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                enabled = request != null && !busy &&
                    !(stateMatches && state.authorizationRequired) && !outcomeUnknown,
                onClick = { request?.let { pendingConfirmation = it } },
            ) {
                Text(
                    if (busy && state.removing) {
                        "Removing moderator…"
                    } else {
                        "Remove moderator"
                    },
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun moderatorRemovalFailureMessage(
    kind: ModeratorRemovalFailureKind,
    httpStatusCode: Int?,
    failureDetails: FailureDetails?,
): String {
    val summary = when (kind) {
        ModeratorRemovalFailureKind.CLIENT_CONFIGURATION ->
            "Moderator removal is not configured for this Reddit session."
        ModeratorRemovalFailureKind.SESSION ->
            "The Reddit session could not authorize this moderator removal."
        ModeratorRemovalFailureKind.AUTHORIZATION ->
            "Reddit refused this removal. The target may be above you in the moderator hierarchy."
        ModeratorRemovalFailureKind.HTTP ->
            "Reddit rejected the moderator removal."
        ModeratorRemovalFailureKind.TRANSPORT ->
            "The moderator removal could not reach Reddit. No automatic retry was made."
        ModeratorRemovalFailureKind.PROTOCOL ->
            "Reddit returned an unexpected response to the moderator removal."
        ModeratorRemovalFailureKind.VALIDATION ->
            "This moderator cannot be removed from this subshell workflow."
        ModeratorRemovalFailureKind.OUTCOME_UNKNOWN ->
            "The removal outcome is unknown. Refresh the moderator list before trying again."
        ModeratorRemovalFailureKind.ALREADY_MUTATING ->
            "Another moderator removal is already in progress."
        ModeratorRemovalFailureKind.ACCOUNT_CHANGED ->
            "The active Reddit account changed. Reopen Moderators before removing anyone."
    }
    val technical = failureDetails?.toSafeTechnicalDetail()
        ?: httpStatusCode
            ?.takeIf {
                kind == ModeratorRemovalFailureKind.HTTP ||
                    kind == ModeratorRemovalFailureKind.OUTCOME_UNKNOWN
            }
            ?.let { status -> "HTTP $status" }
    return technical?.let { detail -> "$summary\n$detail" } ?: summary
}

private const val MODERATOR_INVITE_SEARCH_DEBOUNCE_MS = 300L
private const val MODERATOR_INVITE_SEARCH_RESULT_LIMIT = 6

/** ISSUE86_EDITOR_STAYS_WHILE_ITS_WRITE_RUNS_V1: a workspace write in flight. */
internal class ModeratorWorkspaceBusy {
    var busy by mutableStateOf(false)
}

internal val LocalModeratorWorkspaceBusy = staticCompositionLocalOf<ModeratorWorkspaceBusy?> { null }
