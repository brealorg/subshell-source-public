package app.subshell.prototype.ui.screens

import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import app.subshell.prototype.data.account.TextPostWritePhase
import app.subshell.prototype.data.account.TextPostWriteRequest
import app.subshell.prototype.data.account.TextPostWriteUiState
import app.subshell.prototype.ui.ChromeBehavior
import app.subshell.prototype.ui.ComposerCommunityOption
import app.subshell.prototype.ui.ComposerCommunityPolicy
import app.subshell.prototype.ui.ComposerCommunitySearchSnapshot
import app.subshell.prototype.ui.ComposerCrosspostSource
import app.subshell.prototype.ui.ComposerPolicySnapshot
import app.subshell.prototype.ui.ComposerPolicyStatus
import app.subshell.prototype.ui.ComposerPostRequirementsStatus
import app.subshell.prototype.ui.CrosspostReviewPhase
import app.subshell.prototype.ui.CrosspostReviewUiState
import app.subshell.prototype.ui.PostComposerDraft
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.components.RouteTopBar
import app.subshell.prototype.ui.components.rememberRouteTopBarScrollBehavior
import app.subshell.prototype.ui.crosspostRequestForIdentityOnly
import app.subshell.prototype.ui.crosspostReviewIdentity
import app.subshell.prototype.ui.crosspostWriteAllowsReview
import app.subshell.prototype.ui.crosspostWriteStateForDraft
import app.subshell.prototype.ui.symbols.Symbols
import app.subshell.prototype.ui.validateCrosspostComposerDraftIssue
import app.subshell.prototype.ui.withCrosspostDestination
import app.subshell.prototype.ui.composerDestinationLabel
import app.subshell.prototype.ui.composerKnownCommunityOptions
import app.subshell.prototype.model.Community
import kotlinx.coroutines.delay
import app.subshell.prototype.ui.components.ComposerDiscardAction
import app.subshell.prototype.ui.components.ComposerPrimaryAction
import app.subshell.prototype.data.account.TextPostWriteValidationField
import app.subshell.prototype.ui.app.predictiveBackPage
import app.subshell.prototype.ui.app.rememberPredictiveBackPage

// CROSSPOST01A1E_DEDICATED_NATIVE_CREATE_SURFACE_V1
// Shares the established destination and flair pickers; never enters generic text/link submit.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CrosspostCreateScreen(
    state: SubshellState,
    modifier: Modifier,
    writeState: TextPostWriteUiState,
    composerPolicy: ComposerPolicySnapshot,
    onLoadPolicy: (String, String) -> Unit,
    communitySearch: ComposerCommunitySearchSnapshot,
    onSearchCommunities: (String, String) -> Unit,
    reviewState: CrosspostReviewUiState,
    onReview: (PostComposerDraft) -> Unit,
    onPublish: (PostComposerDraft, Long) -> Unit,
    onInvalidateReview: () -> Unit,
    onReconcile: () -> Unit,
    onAuthorizeWrite: (TextPostWriteRequest) -> Unit,
    onAuthorizeRequirements: (String) -> Boolean,
    onAuthorizeFlair: (String) -> Unit,
    onCancelAuthorization: () -> Unit,
    onResetWriteState: () -> Unit,
    subscribedCommunities: List<Community> = emptyList(),
) {
    val draft = state.postComposerDraft
    val source = draft.crosspostSource
    val authenticated = state.activeAccountIsAuthenticated
    val ownerMatches = authenticated && draft.accountStableId == state.activeAccountId
    val snapshot = composerPolicy.takeIf { it.community.equals(draft.community, ignoreCase = true) }
    val policy = snapshot?.policy ?: ComposerCommunityPolicy.local(draft.community)
    val issue = validateCrosspostComposerDraftIssue(draft, state.activeAccountId, authenticated, policy)
    val identityRequest = draft.crosspostRequestForIdentityOnly()
    val activeWrite = crosspostWriteStateForDraft(
        writeState,
        state.activeAccountId,
        identityRequest?.requestKey,
    )
    val writeBlocked = !crosspostWriteAllowsReview(
        writeState,
        state.activeAccountId,
        identityRequest?.requestKey,
    )
    val editingBlocked = !ownerMatches || reviewState.busy || activeWrite.inProgress ||
        activeWrite.phase == TextPostWritePhase.OUTCOME_UNKNOWN
    val canReview = ownerMatches && !writeBlocked && !reviewState.busy &&
        snapshot?.status == ComposerPolicyStatus.LOADED && issue == null
    var communitySheet by remember(draft.id) { mutableStateOf(false) }
    var communityQuery by remember(draft.id) { mutableStateOf("") }
    var flairSheet by remember(draft.id) { mutableStateOf(false) }
    var confirmDiscard by remember(draft.id) { mutableStateOf(false) }

    fun close() {
        onInvalidateReview()
        state.closePostComposer()
    }
    fun update(next: PostComposerDraft) {
        onInvalidateReview()
        state.updatePostComposerDraft(next)
    }
    fun refreshPolicy() {
        onInvalidateReview()
        if (ownerMatches && draft.community.isNotBlank()) onLoadPolicy(state.activeAccountId, draft.community)
    }

    // ISSUE249_IN_SCREEN_PAGES_FOLLOW_THE_BACK_GESTURE_V1: the draft is kept,
    // so Back always closes and follows the gesture.
    val pageBack = rememberPredictiveBackPage(enabled = true, onBack = ::close)
    val latestInvalidate by rememberUpdatedState(onInvalidateReview)
    val view = LocalView.current
    DisposableEffect(view, draft.id, state.activeAccountId) {
        val lifecycle = view.findViewTreeLifecycleOwner()?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) latestInvalidate()
        }
        lifecycle?.addObserver(observer)
        onDispose {
            lifecycle?.removeObserver(observer)
            latestInvalidate()
        }
    }
    // Restored drafts may reopen the editor, never a previously authorized Review/Publish.
    LaunchedEffect(draft.crosspostReviewIdentity(), state.activeAccountId) {
        if (reviewState.draft != null && !reviewState.matches(draft)) onInvalidateReview()
    }
    LaunchedEffect(draft.id, state.activeAccountId, draft.community) {
        if (ownerMatches && draft.community.isNotBlank()) {
            state.ensureCommunityIdentities(listOf(draft.community))
            onLoadPolicy(state.activeAccountId, draft.community)
        }
    }
    LaunchedEffect(communitySheet, communityQuery, state.activeAccountId) {
        if (communitySheet && ownerMatches) {
            delay(350L)
            onSearchCommunities(state.activeAccountId, communityQuery)
        }
    }
    LaunchedEffect(draft.id, draft.updatedAtEpochMs) {
        delay(450L)
        if (state.postComposerDraft.id == draft.id) state.savePostComposerDraft()
    }

    val routeTopBarScroll = rememberRouteTopBarScrollBehavior(ChromeBehavior.ALWAYS_SHOW)
    Scaffold(
        modifier = modifier.predictiveBackPage(pageBack).fillMaxSize().imePadding()
            .nestedScroll(routeTopBarScroll.nestedScrollConnection),
        topBar = {
            RouteTopBar(
                scrollBehavior = routeTopBarScroll,
                title = { Text("Crosspost") },
                navigationIcon = {
                    IconButton(onClick = ::close) {
                        Icon(Symbols.ArrowBack, contentDescription = "Save crosspost draft and close")
                    }
                },
                actions = {
                    ComposerDiscardAction(enabled = !editingBlocked, onClick = { confirmDiscard = true })
                    ComposerPrimaryAction(
                        text = if (reviewState.busy) "Checking…" else "Review",
                        enabled = canReview,
                        onClick = { onReview(state.postComposerDraft) },
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        ) {
            item("source") {
                source?.let { CrosspostSourceCard(it) } ?: Text(
                    "This crosspost draft has no valid original post. Discard it and start from a post.",
                    color = MaterialTheme.colorScheme.error,
                )
            }
            item("write-status") {
                TextPostWriteStatus(
                    authenticated = authenticated, writeState = activeWrite,
                    grantEnabled = ownerMatches && identityRequest != null,
                    retryEnabled = ownerMatches && identityRequest != null,
                    onGrantAccess = {
                        onInvalidateReview()
                        identityRequest?.let(onAuthorizeWrite)
                    },
                    onRetry = { if (canReview) onReview(state.postComposerDraft) },
                    onReconcile = onReconcile,
                    onCancelAuthorization = onCancelAuthorization,
                    onDismiss = {
                        if (activeWrite.phase != TextPostWritePhase.OUTCOME_UNKNOWN && !activeWrite.inProgress) {
                            onResetWriteState()
                            onInvalidateReview()
                        }
                    },
                    sendingMessage = "Publishing your crosspost to Reddit…",
                )
                // M3225_TITLE_COUNTER_AND_ERRORS_ON_THE_FIELD_V1: a title issue
                // is shown on the title field; the rest stay here.
                activeWrite.validationIssues
                    .filter { issue -> issue.field != TextPostWriteValidationField.TITLE }
                    .forEach { issue ->
                        Text(issue.message, color = MaterialTheme.colorScheme.error)
                    }
            }
            item("destination") {
                val identity = draft.community.takeIf(String::isNotBlank)?.let(state::communityIdentityFor)
                Column(Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
                    V4CommunitySelector(
                        community = draft.community, iconUri = identity?.iconImageUri, accent = identity?.accent,
                        onClick = { if (!editingBlocked) communitySheet = true },
                    )
                    if (draft.community.isNotBlank()) {
                        TextButton(enabled = !editingBlocked, onClick = ::refreshPolicy) { Text("Refresh posting requirements") }
                    }
                    if (snapshot?.status == ComposerPolicyStatus.LOADING) Text("Checking destination requirements…")
                    issue?.let { Text(it.message, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (
                        issue == null &&
                        policy.crosspostDestination?.allowCrossposts == null
                    ) {
                        Text(
                            "Reddit did not expose a destination crosspost flag. Support will be confirmed when you publish.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (
                        issue == null &&
                        policy.postRequirementsStatus in setOf(
                            ComposerPostRequirementsStatus.NOT_CHECKED,
                            ComposerPostRequirementsStatus.UNAVAILABLE,
                        )
                    ) {
                        Text(
                            "Reddit's detailed posting requirements could not be loaded. They will be validated when you publish.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (ownerMatches && policy.postRequirementsStatus == ComposerPostRequirementsStatus.AUTHORIZATION_REQUIRED) {
                        TextButton(onClick = {
                            onInvalidateReview()
                            onAuthorizeRequirements(state.activeAccountId)
                        }) { Text("Grant access to posting requirements") }
                    }
                }
            }
            item("title") {
                // COMPOSE03: same title field as Create post (label, placeholder,
                // counter from 80 % of the limit); the multi-line bounds stay.
                Column(
                    Modifier.widthIn(max = 760.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    val titleIssue = activeWrite.validationIssues
                        .firstOrNull { issue -> issue.field == TextPostWriteValidationField.TITLE }
                        ?.message
                    OutlinedTextField(
                        value = draft.title, onValueChange = { update(draft.copy(title = it)) },
                        enabled = !editingBlocked, label = { Text("Title") },
                        placeholder = { Text("Add a clear, interesting title") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2, maxLines = 5,
                        isError = titleIssue != null || draft.title.length > policy.titleMaxLength,
                        supportingText = composerTitleSupportingText(
                            message = titleIssue,
                            length = draft.title.length,
                            maxLength = policy.titleMaxLength,
                        ),
                    )
                }
            }
            item("flair") {
                Column(Modifier.widthIn(max = 760.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (policy.flairRequired) "Post flair · required" else "Post flair", style = MaterialTheme.typography.titleSmall)
                    draft.flair?.let { V4FlairPreview(it) }
                    when {
                        policy.flairAuthorizationRequired -> TextButton(
                            enabled = ownerMatches && !editingBlocked,
                            onClick = { onInvalidateReview(); onAuthorizeFlair(state.activeAccountId) },
                        ) { Text("Grant post-flair access") }
                        policy.flairOptionsLoaded && (policy.flairOptions.isNotEmpty() || draft.flair != null) ->
                            TextButton(enabled = !editingBlocked, onClick = { flairSheet = true }) {
                                Text(if (draft.flair == null) "Choose post flair" else "Change post flair")
                            }
                        draft.community.isBlank() -> Text("Choose a destination first")
                        else -> Text("No selectable post flair loaded")
                    }
                }
            }
            item("flags") {
                Column(Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
                    CrosspostFlag("NSFW", draft.nsfw, !editingBlocked && source?.nsfw != true) {
                        update(draft.copy(nsfw = it))
                    }
                    CrosspostFlag("Spoiler", draft.spoiler, !editingBlocked && source?.spoiler != true) {
                        update(draft.copy(spoiler = it))
                    }
                    CrosspostFlag("Send reply notifications", draft.sendReplies, !editingBlocked) {
                        update(draft.copy(sendReplies = it))
                    }
                }
            }
            item("review-message") {
                Column(Modifier.widthIn(max = 760.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    reviewState.message?.takeIf { reviewState.matches(draft) }?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                    Text(
                        "The original post stays linked. Review checks the source and destination; only Publish sends a crosspost.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text("Saved crosspost drafts are not included in portable backup.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (communitySheet) {
        // COMPOSER19_PICKER_OWN_COMMUNITIES_V1
        val known = composerKnownCommunityOptions(
            cached = state.communityPickerCommunities(),
            subscribed = subscribedCommunities,
            joinedIds = state.joinedCommunityIds,
            isFavorite = state::isCommunityFavorite,
            identity = state::communityIdentityFor,
        )
        V4CommunitySheet(
            query = communityQuery, onQueryChange = { communityQuery = it }, knownOptions = known,
            remoteSearch = communitySearch, selectedCommunity = draft.community, authenticated = ownerMatches,
            profileUsername = state.activeAccountUsername.takeIf { state.activeAccountIsAuthenticated },
            onSelect = { option ->
                if (!editingBlocked) update(state.postComposerDraft.withCrosspostDestination(option.name))
                communitySheet = false
                communityQuery = ""
            },
            onDismiss = { communitySheet = false; communityQuery = "" },
        )
    }
    if (flairSheet) {
        V4FlairSheet(
            community = draft.community, required = policy.flairRequired, options = policy.flairOptions,
            selected = draft.flair,
            onSelect = { flair -> if (!editingBlocked) update(draft.copy(flair = flair)); flairSheet = false },
            onClear = { if (!editingBlocked) update(draft.copy(flair = null)); flairSheet = false },
            onDismiss = { flairSheet = false },
        )
    }
    // CROSSPOST01A1E_EXPLICIT_REVIEW_DIALOG_V1
    if (reviewState.matches(draft) && reviewState.phase in setOf(CrosspostReviewPhase.READY, CrosspostReviewPhase.VERIFYING)) {
        val verifying = reviewState.phase == CrosspostReviewPhase.VERIFYING
        AlertDialog(
            onDismissRequest = { if (!verifying) onInvalidateReview() },
            title = { Text("Publish crosspost?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Posting as u/${state.activeAccountUsername} to ${composerDestinationLabel(draft.community)}")
                    Text(draft.title, style = MaterialTheme.typography.titleMedium)
                    source?.let { CrosspostSourceCard(it) }
                    draft.flair?.let { V4FlairPreview(it) }
                    // LOWUX3_CROSSPOST_SUMMARY_SAYS_ON_OR_OFF_V1 (OCB-210)
                    Text(
                        "NSFW: ${if (draft.nsfw) "on" else "off"} · " +
                            "Spoiler: ${if (draft.spoiler) "on" else "off"}",
                    )
                    Text("Reply notifications: ${if (draft.sendReplies) "on" else "off"}")
                    Text(if (verifying) "Rechecking source and destination…" else "Publish creates a new Reddit post linked to this original.")
                }
            },
            confirmButton = {
                Button(enabled = ownerMatches && !verifying && !writeBlocked, onClick = {
                    onPublish(state.postComposerDraft, reviewState.revision)
                }) { Text(if (verifying) "Checking…" else "Publish") }
            },
            dismissButton = { TextButton(enabled = !verifying, onClick = onInvalidateReview) { Text("Back to editing") } },
        )
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false }, title = { Text("Discard crosspost draft?") },
            text = { Text("The original Reddit post is not changed.") },
            // ISSUE222_DESTRUCTIVE_CONFIRMS_ARE_RED_V1
            confirmButton = { TextButton(
                onClick = { onInvalidateReview(); state.discardPostComposerDraft(); confirmDiscard = false },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }
}

@Composable
private fun CrosspostSourceCard(source: ComposerCrosspostSource) {
    Surface(
        modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
        shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Original post · source locked", style = MaterialTheme.typography.labelLarge)
            Text("r/${source.community} · u/${source.author}", style = MaterialTheme.typography.bodySmall)
            Text(source.title, style = MaterialTheme.typography.titleMedium)
            Text("Source access is checked again before publishing", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CrosspostFlag(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    // COMPOSER12_LABELLED_SWITCHES_V1: the row is the switch.
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, enabled = enabled, onCheckedChange = null)
    }
}
