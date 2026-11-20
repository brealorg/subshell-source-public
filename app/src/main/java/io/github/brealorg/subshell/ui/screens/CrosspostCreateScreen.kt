package io.github.brealorg.subshell.ui.screens

import io.github.brealorg.subshell.R
import androidx.compose.ui.res.stringResource
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import io.github.brealorg.subshell.data.account.TextPostWritePhase
import io.github.brealorg.subshell.data.account.TextPostWriteRequest
import io.github.brealorg.subshell.data.account.TextPostWriteUiState
import io.github.brealorg.subshell.ui.ChromeBehavior
import io.github.brealorg.subshell.ui.ComposerCommunityOption
import io.github.brealorg.subshell.ui.ComposerCommunityPolicy
import io.github.brealorg.subshell.ui.ComposerCommunitySearchSnapshot
import io.github.brealorg.subshell.ui.ComposerCrosspostSource
import io.github.brealorg.subshell.ui.ComposerPolicySnapshot
import io.github.brealorg.subshell.ui.ComposerPolicyStatus
import io.github.brealorg.subshell.ui.ComposerPostRequirementsStatus
import io.github.brealorg.subshell.ui.CrosspostReviewPhase
import io.github.brealorg.subshell.ui.CrosspostReviewUiState
import io.github.brealorg.subshell.ui.PostComposerDraft
import io.github.brealorg.subshell.ui.SubshellState
import io.github.brealorg.subshell.ui.components.RouteTopBar
import io.github.brealorg.subshell.ui.components.rememberRouteTopBarScrollBehavior
import io.github.brealorg.subshell.ui.crosspostRequestForIdentityOnly
import io.github.brealorg.subshell.ui.crosspostReviewIdentity
import io.github.brealorg.subshell.ui.crosspostWriteAllowsReview
import io.github.brealorg.subshell.ui.crosspostWriteStateForDraft
import io.github.brealorg.subshell.ui.symbols.Symbols
import io.github.brealorg.subshell.ui.validateCrosspostComposerDraftIssue
import io.github.brealorg.subshell.ui.withCrosspostDestination
import io.github.brealorg.subshell.ui.composerDestinationLabel
import io.github.brealorg.subshell.ui.composerKnownCommunityOptions
import io.github.brealorg.subshell.model.Community
import kotlinx.coroutines.delay
import io.github.brealorg.subshell.ui.components.ComposerDiscardAction
import io.github.brealorg.subshell.ui.components.ComposerPrimaryAction
import io.github.brealorg.subshell.data.account.TextPostWriteValidationField
import io.github.brealorg.subshell.ui.app.predictiveBackPage
import io.github.brealorg.subshell.ui.app.rememberPredictiveBackPage

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

    val crosspostListState = rememberLazyListState()
    // COMPOSERERR01_A_REFUSED_POST_SCROLLS_ITS_NOTICE_INTO_VIEW_V1: the notice is
    // the second item, after the source card.
    RevealTextPostWriteNotice(
        writeState = activeWrite,
        listState = crosspostListState,
        noticeItemIndex = 1,
        draftKey = "${draft.id}",
    )
    val routeTopBarScroll = rememberRouteTopBarScrollBehavior(ChromeBehavior.ALWAYS_SHOW)
    Scaffold(
        modifier = modifier.predictiveBackPage(pageBack).fillMaxSize().imePadding()
            .nestedScroll(routeTopBarScroll.nestedScrollConnection),
        topBar = {
            RouteTopBar(
                scrollBehavior = routeTopBarScroll,
                title = { Text(stringResource(R.string.scr_crosspost)) },
                navigationIcon = {
                    IconButton(onClick = ::close) {
                        Icon(Symbols.ArrowBack, contentDescription = stringResource(R.string.scr_save_crosspost_draft_and_close))
                    }
                },
                actions = {
                    ComposerDiscardAction(enabled = !editingBlocked, onClick = { confirmDiscard = true })
                    ComposerPrimaryAction(
                        text = if (reviewState.busy) stringResource(R.string.all_checking) else stringResource(R.string.all_review),
                        enabled = canReview,
                        onClick = { onReview(state.postComposerDraft) },
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(
            state = crosspostListState,
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        ) {
            item("source") {
                source?.let { CrosspostSourceCard(it) } ?: Text(
                    stringResource(R.string.all_this_crosspost_draft_has_no_valid_original),
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
                    sendingMessage = stringResource(R.string.all_publishing_your_crosspost_to_reddit),
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
                        TextButton(enabled = !editingBlocked, onClick = ::refreshPolicy) { Text(stringResource(R.string.scr_refresh_posting_requirements)) }
                    }
                    if (snapshot?.status == ComposerPolicyStatus.LOADING) Text(stringResource(R.string.scr_checking_destination_requirements))
                    issue?.let { Text(it.message.asString(), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (
                        issue == null &&
                        policy.crosspostDestination?.allowCrossposts == null
                    ) {
                        Text(
                            stringResource(R.string.all_reddit_did_not_expose_a_destination_crosspost),
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
                            stringResource(R.string.all_reddit_s_detailed_posting_requirements_could_not),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (ownerMatches && policy.postRequirementsStatus == ComposerPostRequirementsStatus.AUTHORIZATION_REQUIRED) {
                        TextButton(onClick = {
                            onInvalidateReview()
                            onAuthorizeRequirements(state.activeAccountId)
                        }) { Text(stringResource(R.string.scr_grant_access_to_posting_requirements)) }
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
                        enabled = !editingBlocked, label = { Text(stringResource(R.string.scr_title)) },
                        placeholder = { Text(stringResource(R.string.scr_add_a_clear_interesting_title)) },
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
                    Text(if (policy.flairRequired) stringResource(R.string.all_post_flair_required) else stringResource(R.string.all_post_flair), style = MaterialTheme.typography.titleSmall)
                    draft.flair?.let { V4FlairPreview(it) }
                    when {
                        policy.flairAuthorizationRequired -> TextButton(
                            enabled = ownerMatches && !editingBlocked,
                            onClick = { onInvalidateReview(); onAuthorizeFlair(state.activeAccountId) },
                        ) { Text(stringResource(R.string.scr_grant_post_flair_access)) }
                        policy.flairOptionsLoaded && (policy.flairOptions.isNotEmpty() || draft.flair != null) ->
                            TextButton(enabled = !editingBlocked, onClick = { flairSheet = true }) {
                                Text(if (draft.flair == null) stringResource(R.string.scr_choose_post_flair) else stringResource(R.string.scr_change_post_flair))
                            }
                        draft.community.isBlank() -> Text(stringResource(R.string.scr_choose_a_destination_first))
                        else -> Text(stringResource(R.string.scr_no_selectable_post_flair_loaded))
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
                    CrosspostFlag(stringResource(R.string.all_send_reply_notifications), draft.sendReplies, !editingBlocked) {
                        update(draft.copy(sendReplies = it))
                    }
                }
            }
            item("review-message") {
                Column(Modifier.widthIn(max = 760.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    reviewState.message?.takeIf { reviewState.matches(draft) }?.let {
                        Text(it.asString(), color = MaterialTheme.colorScheme.error)
                    }
                    Text(
                        stringResource(R.string.all_the_original_post_stays_linked_review_checks),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(stringResource(R.string.scr_saved_crosspost_drafts_are_not_included_in), style = MaterialTheme.typography.bodySmall)
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
            title = { Text(stringResource(R.string.scr_publish_crosspost)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.scr_posting_as_u_to, state.activeAccountUsername, composerDestinationLabel(draft.community)))
                    Text(draft.title, style = MaterialTheme.typography.titleMedium)
                    source?.let { CrosspostSourceCard(it) }
                    draft.flair?.let { V4FlairPreview(it) }
                    // LOWUX3_CROSSPOST_SUMMARY_SAYS_ON_OR_OFF_V1 (OCB-210)
                    Text(
                        stringResource(
                            R.string.xpost_review_flags,
                            stringResource(if (draft.nsfw) R.string.xpost_flag_on else R.string.xpost_flag_off),
                            stringResource(if (draft.spoiler) R.string.xpost_flag_on else R.string.xpost_flag_off),
                        ),
                    )
                    Text(stringResource(if (draft.sendReplies) R.string.xpost_reply_notifications_on else R.string.xpost_reply_notifications_off))
                    Text(if (verifying) stringResource(R.string.all_rechecking_source_and_destination) else stringResource(R.string.all_publish_creates_a_new_reddit_post_linked))
                }
            },
            confirmButton = {
                Button(enabled = ownerMatches && !verifying && !writeBlocked, onClick = {
                    onPublish(state.postComposerDraft, reviewState.revision)
                }) { Text(if (verifying) stringResource(R.string.all_checking) else stringResource(R.string.all_publish)) }
            },
            dismissButton = { TextButton(enabled = !verifying, onClick = onInvalidateReview) { Text(stringResource(R.string.scr_back_to_editing)) } },
        )
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false }, title = { Text(stringResource(R.string.scr_discard_crosspost_draft)) },
            text = { Text(stringResource(R.string.scr_the_original_reddit_post_is_not_changed)) },
            // ISSUE222_DESTRUCTIVE_CONFIRMS_ARE_RED_V1
            confirmButton = { TextButton(
                onClick = { onInvalidateReview(); state.discardPostComposerDraft(); confirmDiscard = false },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.scr_discard)) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.scr_keep_editing)) } },
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
            Text(stringResource(R.string.scr_original_post_source_locked), style = MaterialTheme.typography.labelLarge)
            Text(stringResource(R.string.scr_r_u, source.community, source.author), style = MaterialTheme.typography.bodySmall)
            Text(source.title, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.scr_source_access_is_checked_again_before_publishing), style = MaterialTheme.typography.bodySmall)
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
