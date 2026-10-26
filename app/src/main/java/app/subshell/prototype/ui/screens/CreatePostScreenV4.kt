package app.subshell.prototype.ui.screens

import app.subshell.prototype.ui.components.postBodyMentions
import app.subshell.prototype.ui.components.MentionSuggestions
import app.subshell.prototype.data.account.RedditPeopleSearchUiState
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.stateDescription
import app.subshell.prototype.ui.ChromeBehavior
import app.subshell.prototype.ui.components.RouteTopBar
import app.subshell.prototype.ui.components.pastedImageKeptInApp
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.ui.CrosspostReviewUiState

import android.content.Intent
import android.net.Uri
import androidx.activity.ExperimentalActivityApi
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.contextmenu.builder.item
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.subshell.prototype.data.account.TextPostWriteGalleryItem
import app.subshell.prototype.data.account.TextPostWritePhase
import app.subshell.prototype.data.account.TextPostWriteRequest
import app.subshell.prototype.data.account.TextPostWriteUiState
import app.subshell.prototype.data.account.TextPostWriteValidationField
import app.subshell.prototype.data.account.TextPostWriteValidationIssue
import app.subshell.prototype.data.linkpreview.LinkPreviewMetadata
import app.subshell.prototype.data.linkpreview.LinkPreviewResolver
import app.subshell.prototype.ui.components.rememberRouteTopBarScrollBehavior
import app.subshell.prototype.ui.components.sendableImageAttachment
import app.subshell.prototype.ui.components.presentImageNotSendable
import app.subshell.prototype.ui.error.ViewportErrorHostState
import app.subshell.prototype.ui.error.ErrorPresentationKind
import app.subshell.prototype.ui.error.ErrorPresentation
import app.subshell.prototype.ui.error.LocalViewportErrorHostState
import app.subshell.prototype.ui.COMPOSER_BODY_NOT_ALLOWED_MESSAGE
import app.subshell.prototype.ui.composerCommunityPickerOptions
import app.subshell.prototype.ui.composerDestinationLabel
import app.subshell.prototype.ui.composerKnownCommunityOptions
import app.subshell.prototype.model.Community
import app.subshell.prototype.ui.COMPOSER_FLAIR_TEXT_MAX_LENGTH
import app.subshell.prototype.ui.COMPOSER_GALLERY_CAPTION_MAX_LENGTH
import app.subshell.prototype.ui.ComposerCommunityPolicy
import app.subshell.prototype.ui.ComposerCommunityOption
import app.subshell.prototype.ui.ComposerCommunitySearchSnapshot
import app.subshell.prototype.ui.ComposerCommunitySearchStatus
import app.subshell.prototype.ui.ComposerFlair
import app.subshell.prototype.ui.ComposerMediaAttachment
import app.subshell.prototype.ui.ComposerMediaKind
import app.subshell.prototype.ui.ComposerPolicySnapshot
import app.subshell.prototype.ui.ComposerPolicyStatus
import app.subshell.prototype.ui.ComposerSubmitPreflightAction
import app.subshell.prototype.ui.ComposerValidationField
import app.subshell.prototype.ui.ComposerValidationIssue
import app.subshell.prototype.ui.ComposerPostType
import app.subshell.prototype.ui.MarkdownAction
import app.subshell.prototype.ui.PostComposerDraft
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.applyMarkdownAction
import app.subshell.prototype.ui.composerSubmitPreflightAction
import app.subshell.prototype.ui.isComposerVideoGif
import app.subshell.prototype.ui.isComposerVideoSource
import app.subshell.prototype.ui.reconcileComposerFlairSelection
import app.subshell.prototype.ui.components.CommunityAvatar
import app.subshell.prototype.ui.components.LocalUriMedia
import app.subshell.prototype.ui.components.ComposerFormattingDock
import app.subshell.prototype.ui.components.PostStyleComposerEditor
import app.subshell.prototype.ui.components.RemoteImage
import app.subshell.prototype.ui.components.RemoteIdentityImage
import app.subshell.prototype.ui.components.ComposerDiscardAction
import app.subshell.prototype.ui.components.ComposerPrimaryAction
import app.subshell.prototype.ui.components.ComposerBodyHeader
import app.subshell.prototype.ui.components.MarkdownComposerPreview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView
import app.subshell.prototype.ui.symbols.Symbols
import app.subshell.prototype.ui.validatePostComposerDraftIssue
import app.subshell.prototype.ui.validateRemoteTextPostComposerDraftIssue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.subshell.prototype.ui.components.SubshellSearchField
import app.subshell.prototype.ui.components.InlineProgressIndicator

private const val V4_WIDE_EDITOR_BREAKPOINT_DP = 840
private const val V4_LINK_PREVIEW_DEBOUNCE_MS = 700L
private const val V4_FLAIR_SEARCH_THRESHOLD = 10

@OptIn(
    ExperimentalActivityApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
)
@Composable
fun CreatePostScreen(
    state: SubshellState,
    modifier: Modifier = Modifier,
    textPostWriteState: TextPostWriteUiState = TextPostWriteUiState(),
    crosspostReviewState: CrosspostReviewUiState = CrosspostReviewUiState(),
    onReviewCrosspost: (PostComposerDraft) -> Unit = {},
    onPublishReviewedCrosspost: (PostComposerDraft, Long) -> Unit = { _, _ -> },
    onInvalidateCrosspostReview: () -> Unit = {},
    composerPolicy: ComposerPolicySnapshot = ComposerPolicySnapshot(),
    onLoadComposerPolicy: (String, String) -> Unit = { _, _ -> },
    // ISSUE229_POST_BODY_MENTIONS_V1
    mentionSearchState: RedditPeopleSearchUiState = RedditPeopleSearchUiState(),
    onSearchMentions: (String) -> Unit = {},
    composerCommunitySearch: ComposerCommunitySearchSnapshot =
        ComposerCommunitySearchSnapshot(),
    onSearchComposerCommunities: (String, String) -> Unit = { _, _ -> },
    onSubmitTextPost: (TextPostWriteRequest) -> Unit = {},
    onReconcileTextPostSubmit: () -> Unit = {},
    onAuthorizeTextPostWrite: (TextPostWriteRequest) -> Unit = {},
    onAuthorizeComposerPostRequirements: (String) -> Boolean = { false },
    onAuthorizeComposerFlair: (String) -> Unit = {},
    onCancelTextPostWriteAuthorization: () -> Unit = {},
    onResetTextPostWriteState: () -> Unit = {},
    // COMPOSER19_PICKER_OWN_COMMUNITIES_V1: the active account's subscriptions.
    subscribedCommunities: List<Community> = emptyList(),
) {
    // CROSSPOST01A1E_SOURCE_AWARE_COMPOSER_ROUTE_V1
    if (state.postComposerDraft.type == ComposerPostType.CROSSPOST ||
        state.postComposerDraft.crosspostSource != null
    ) {
        CrosspostCreateScreen(
            state = state, modifier = modifier, writeState = textPostWriteState,
            composerPolicy = composerPolicy, onLoadPolicy = onLoadComposerPolicy,
            communitySearch = composerCommunitySearch, onSearchCommunities = onSearchComposerCommunities,
            reviewState = crosspostReviewState, onReview = onReviewCrosspost,
            onPublish = onPublishReviewedCrosspost, onInvalidateReview = onInvalidateCrosspostReview,
            onReconcile = onReconcileTextPostSubmit, onAuthorizeWrite = onAuthorizeTextPostWrite,
            onAuthorizeRequirements = onAuthorizeComposerPostRequirements,
            onAuthorizeFlair = onAuthorizeComposerFlair,
            onCancelAuthorization = onCancelTextPostWriteAuthorization,
            onResetWriteState = onResetTextPostWriteState,
            subscribedCommunities = subscribedCommunities,
        )
        return
    }
    val draft = state.postComposerDraft
    val communities = state.communityPickerCommunities()
    val authenticated = state.activeAccountIsAuthenticated
    val selectedCommunity = draft.community
        .takeIf(String::isNotBlank)
        ?.let(state::communityIdentityFor)
    val matchingPolicySnapshot = composerPolicy.takeIf {
        it.community.equals(draft.community, ignoreCase = true)
    }
    var serverRequiredFlairCommunity by rememberSaveable(draft.id) {
        mutableStateOf<String?>(null)
    }
    val basePolicy = matchingPolicySnapshot?.policy ?:
        ComposerCommunityPolicy.local(draft.community)
    val policy = if (
        serverRequiredFlairCommunity.equals(draft.community, ignoreCase = true)
    ) {
        basePolicy.copy(flairRequired = true)
    } else {
        basePolicy
    }
    val policyReady = draft.community.isNotBlank() &&
        (!authenticated || matchingPolicySnapshot?.status == ComposerPolicyStatus.LOADED)
    val policyStatusMessage = when {
        draft.community.isBlank() -> null
        !authenticated || policyReady -> null
        matchingPolicySnapshot?.status == ComposerPolicyStatus.UNAVAILABLE ->
            "Community rules couldn't be loaded."
        else -> "Checking community rules…"
    }

    val bodyState = rememberTextFieldState(draft.body)
    val bodyText = bodyState.text.toString()
    // COMPOSER18_BODY_EDIT_BY_DEFAULT_V1: Preview lasts only while the composer
    // is shown; opening it (or a draft) always starts in the editor.
    var compactPreviewVisible by remember(draft.id) { mutableStateOf(false) }
    var communitySheetVisible by remember { mutableStateOf(false) }
    // ISSUE33_SHARE_INTO_SUBSHELL_V1: a shared draft asks for its community.
    LaunchedEffect(draft.id) {
        if (state.consumeComposerCommunityPicker(draft.id) && draft.community.isBlank()) {
            communitySheetVisible = true
        }
    }
    var communityQuery by rememberSaveable { mutableStateOf("") }
    var discardConfirmationVisible by remember { mutableStateOf(false) }
    var flairSheetVisible by remember { mutableStateOf(false) }
    var flairNotice by remember(draft.id) { mutableStateOf<String?>(null) }
    var mediaSheetVisible by remember { mutableStateOf(false) }
    var optionsExpanded by rememberSaveable(draft.id) { mutableStateOf(false) }
    var validationRequested by rememberSaveable(draft.id) { mutableStateOf(false) }
    var pendingPreflightRequestKey by rememberSaveable(draft.id) {
        mutableStateOf<String?>(null)
    }
    var pendingPreflightMinimumPolicyRevision by rememberSaveable(draft.id) {
        mutableLongStateOf(0L)
    }
    var awaitingPreflightAuthorization by rememberSaveable(draft.id) {
        mutableStateOf(false)
    }
    // COMPOSER15_PREFLIGHT_SURVIVES_PROCESS_DEATH_V1: the minimum policy
    // revision counts in the process that saved it; the policy coordinator
    // starts again from 0 in a new process, so a restored check waited
    // forever ("Checking…"). A check restored in another process waits for
    // the first fresh policy instead.
    var preflightProcessToken by rememberSaveable(draft.id) {
        mutableStateOf(ComposerProcessToken)
    }
    LaunchedEffect(preflightProcessToken) {
        pendingPreflightMinimumPolicyRevision = restoredPreflightMinimumRevision(
            savedProcessToken = preflightProcessToken,
            currentProcessToken = ComposerProcessToken,
            savedMinimumRevision = pendingPreflightMinimumPolicyRevision,
        )
        preflightProcessToken = ComposerProcessToken
    }
    var preflightFailureMessage by rememberSaveable(draft.id) {
        mutableStateOf<String?>(null)
    }
    var persistedRevision by remember(draft.id) { mutableLongStateOf(draft.updatedAtEpochMs) }
    var linkFocusRequestRevision by remember(draft.id) { mutableIntStateOf(0) }
    var resolvedLinkPreview by remember(draft.id) { mutableStateOf<LinkPreviewMetadata?>(null) }
    // LOWUI5_LINK_PREVIEW_KEPT_PER_URL_V1 (OCB-314): a URL's preview (or that
    // it has none) is kept for the draft, so the link row scrolling back into
    // view shows it at once instead of clearing and fetching it again.
    val linkPreviewsByUrl = remember(draft.id) { mutableMapOf<String, LinkPreviewMetadata?>() }
    val composerListState = rememberLazyListState()
    val titleFocusRequester = remember { FocusRequester() }
    val linkFocusRequester = remember { FocusRequester() }
    val linkBringIntoViewRequester = remember { BringIntoViewRequester() }
    val bodyFocusRequester = remember { FocusRequester() }
    val bodyBringIntoViewRequester = remember { BringIntoViewRequester() }
    val flairBringIntoViewRequester = remember { BringIntoViewRequester() }
    val mediaBringIntoViewRequester = remember { BringIntoViewRequester() }
    val composerScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    // COMPOSER03_POST_COMPOSER_LOCKED_WHILE_SENDING_V1: from Post until the result
    // the draft is the request; nothing in it changes, so a success always
    // matches the draft that was sent (text, media, link, flair, options).
    val composerLocked = authenticated && textPostWriteState.inProgress
    // OCB-205: a picker tapped while sending does not open once it ends.
    LaunchedEffect(composerLocked) {
        if (composerLocked) {
            communitySheetVisible = false
            flairSheetVisible = false
        }
    }

    fun update(transform: (PostComposerDraft) -> PostComposerDraft) {
        if (composerLocked) return
        state.updatePostComposerDraft(transform(state.postComposerDraft))
    }

    // COMPOSER04_CONFIRM_BEFORE_REPLACING_CONTENT_V1: adding a link, photos, a
    // video or a pasted image never silently removes what the post already
    // has; the change waits for a yes when it would drop anything.
    var pendingReplacement by remember(draft.id) {
        mutableStateOf<Pair<ComposerContentLoss, (PostComposerDraft) -> PostComposerDraft>?>(null)
    }

    fun updateConfirmingLoss(transform: (PostComposerDraft) -> PostComposerDraft) {
        if (composerLocked) return
        val current = state.postComposerDraft
        val loss = composerContentLoss(current, transform(current))
        if (loss == null) update(transform) else pendingReplacement = loss to transform
    }

    fun synchronizedDraft(): PostComposerDraft = state.postComposerDraft.copy(body = bodyText)

    fun applyFormat(action: MarkdownAction) {
        if (composerLocked) return
        bodyState.applyV4Markdown(action)
        compactPreviewVisible = false
    }

    fun persistUri(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    fun attachment(uri: Uri, fallbackMime: String): ComposerMediaAttachment {
        persistUri(uri)
        val mime = context.contentResolver.getType(uri)?.takeIf(String::isNotBlank) ?: fallbackMime
        val kind = when {
            mime.startsWith("video/") -> ComposerMediaKind.VIDEO
            mime.equals("image/gif", ignoreCase = true) ||
                uri.lastPathSegment?.endsWith(".gif", ignoreCase = true) == true ->
                ComposerMediaKind.GIF
            else -> ComposerMediaKind.IMAGE
        }
        return ComposerMediaAttachment(uri.toString(), mime, kind)
    }

    val imageNoticeHost = LocalViewportErrorHostState.current
    val photoPicker = rememberLauncherForActivityResult(PickMultipleVisualMedia(20)) { uris ->
        if (uris.isNotEmpty()) {
            // ISSUE90_SENDABLE_IMAGE_V1: HEIC/HEIF/AVIF photos become JPEGs.
            composerScope.launch {
                // LOWUI6_PICKED_MEDIA_RESOLVED_ONCE_OFF_MAIN_V1 (OCB-312): the
                // type and grant are provider calls, made once per pick on IO.
                val picked = withContext(Dispatchers.IO) { uris.map { attachment(it, "image/*") } }
                val addedPhotos = picked.mapNotNull { sendableImageAttachment(context, it) }
                if (addedPhotos.size < picked.size) imageNoticeHost?.presentImageNotSendable()
                if (addedPhotos.isEmpty()) return@launch
                // LOWUX1_GALLERY_SAYS_WHAT_DID_NOT_FIT_V1 (OCB-203): a gallery takes
                // 20 photos; the picks past that were dropped without a word.
                val existingCount = state.postComposerDraft.media
                    .takeIf { media -> media.none { it.isComposerVideoSource() } }
                    .orEmpty()
                    .map(ComposerMediaAttachment::uri)
                val leftOut = (existingCount + addedPhotos.map(ComposerMediaAttachment::uri))
                    .distinct().size - 20
                if (leftOut > 0) imageNoticeHost?.presentGalleryFull(leftOut)
                updateConfirmingLoss { current ->
                    val existingPhotos = current.media.takeIf { media ->
                        media.none { it.isComposerVideoSource() }
                    }.orEmpty()
                    current.copy(
                        type = ComposerPostType.MEDIA,
                        linkUrl = "",
                        media = (existingPhotos + addedPhotos)
                            .distinctBy(ComposerMediaAttachment::uri)
                            .take(20),
                    )
                }
            }
        }
    }
    val videoPicker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        uri?.let {
            // LOWUI6_PICKED_MEDIA_RESOLVED_ONCE_OFF_MAIN_V1 (OCB-312): resolved once
            // here, not in the transform that runs up to three times.
            composerScope.launch {
                val picked = withContext(Dispatchers.IO) { attachment(it, "video/*") }
                updateConfirmingLoss { current ->
                    val selected = if (
                        picked.isComposerVideoSource() &&
                        !policy.allowVideo &&
                        policy.allowVideoGifs
                    ) {
                        picked.copy(kind = ComposerMediaKind.GIF)
                    } else {
                        picked
                    }
                    current.copy(
                        type = ComposerPostType.MEDIA,
                        linkUrl = "",
                        media = listOf(selected),
                    )
                }
            }
        }
    }

    val accountMismatchMessage = draft.accountStableId
        ?.takeIf { it != state.activeAccountId }
        ?.let { "This draft belongs to another account" }
    val request = synchronizedDraft()
        .takeIf { accountMismatchMessage == null }
        ?.toV4RemoteRequest(state.activeAccountId, resolvedLinkPreview)
    val activeTextPostWriteState = textPostWriteStateForRequest(
        state = textPostWriteState,
        requestKey = request?.requestKey,
    )
    val localValidationIssue = accountMismatchMessage?.let { message ->
        ComposerValidationIssue(ComposerValidationField.GENERAL, message)
    } ?: if (authenticated) {
        validateRemoteTextPostComposerDraftIssue(synchronizedDraft(), policy)
    } else {
        validatePostComposerDraftIssue(synchronizedDraft(), communities.map { it.name }, policy)
    }
    val remoteValidationIssue = activeTextPostWriteState.validationIssues
        .firstOrNull()
        ?.toComposerValidationIssue()
    val preflightValidationIssue = preflightFailureMessage?.let { message ->
        ComposerValidationIssue(ComposerValidationField.GENERAL, message)
    }
    val activeValidationIssue = remoteValidationIssue ?: preflightValidationIssue ?:
        localValidationIssue.takeIf { validationRequested }
    val titleValidationMessage = activeValidationIssue
        ?.takeIf { it.field == ComposerValidationField.TITLE }
        ?.message
    val linkValidationMessage = activeValidationIssue
        ?.takeIf { it.field == ComposerValidationField.LINK }
        ?.message
    val bodyValidationMessage = activeValidationIssue
        ?.takeIf { it.field == ComposerValidationField.BODY }
        ?.message
    val flairValidationMessage = activeValidationIssue
        ?.takeIf { it.field == ComposerValidationField.FLAIR }
        ?.message
    val mediaValidationMessage = activeValidationIssue
        ?.takeIf { it.field == ComposerValidationField.MEDIA }
        ?.message
    val generalValidationMessage = activeValidationIssue
        ?.takeIf {
            it.field in setOf(
                ComposerValidationField.COMMUNITY,
                ComposerValidationField.GENERAL,
            )
        }
        ?.message
    val preflightPending = pendingPreflightRequestKey != null
    val postEnabled = !preflightPending &&
        (
            !authenticated ||
                activeTextPostWriteState.phase in setOf(
                    TextPostWritePhase.IDLE,
                    TextPostWritePhase.FAILED,
                )
            )
    val saveLabel = when {
        !draft.hasContent -> null
        draft.updatedAtEpochMs > persistedRevision -> "Saving…"
        else -> "Saved in Drafts"
    }

    fun revealValidationIssue(issue: ComposerValidationIssue) {
        when (issue.field) {
            ComposerValidationField.COMMUNITY -> {
                focusManager.clearFocus()
                communityQuery = ""
                communitySheetVisible = true
            }
            // LOWUX3_TOP_VALIDATION_ISSUES_SCROLL_INTO_VIEW_V1 (OCB-204): the title
            // and the general summary sit at the top; from a long body they were
            // off screen and Post seemed to do nothing.
            ComposerValidationField.TITLE -> composerScope.launch {
                composerListState.animateScrollToItem(0)
                titleFocusRequester.requestFocus()
            }
            ComposerValidationField.LINK -> {
                composerScope.launch {
                    linkBringIntoViewRequester.bringIntoView()
                    linkFocusRequester.requestFocus()
                }
            }
            ComposerValidationField.FLAIR -> {
                focusManager.clearFocus()
                composerScope.launch {
                    flairBringIntoViewRequester.bringIntoView()
                    delay(180L)
                    if (policy.flairAuthorizationRequired) {
                        onAuthorizeComposerFlair(state.activeAccountId)
                    } else if (policy.loadedFromReddit && !policy.flairOptionsLoaded) {
                        onLoadComposerPolicy(state.activeAccountId, draft.community)
                    } else {
                        flairSheetVisible = true
                    }
                }
            }
            ComposerValidationField.BODY -> {
                composerScope.launch {
                    bodyBringIntoViewRequester.bringIntoView()
                    bodyFocusRequester.requestFocus()
                }
            }
            ComposerValidationField.MEDIA -> {
                focusManager.clearFocus()
                composerScope.launch {
                    mediaBringIntoViewRequester.bringIntoView()
                }
            }
            ComposerValidationField.GENERAL -> composerScope.launch {
                composerListState.animateScrollToItem(0)
            }
        }
    }

    fun submit() {
        val current = synchronizedDraft()
        state.updatePostComposerDraft(current)
        validationRequested = true
        preflightFailureMessage = null
        val failure = accountMismatchMessage?.let { message ->
            ComposerValidationIssue(ComposerValidationField.GENERAL, message)
        } ?: if (authenticated) {
            validateRemoteTextPostComposerDraftIssue(current, policy)
        } else {
            validatePostComposerDraftIssue(current, communities.map { it.name }, policy)
        }
        if (failure != null) {
            revealValidationIssue(failure)
            return
        }
        if (authenticated) {
            val currentRequest = current.toV4RemoteRequest(
                state.activeAccountId,
                resolvedLinkPreview,
            ) ?: run {
                // ISSUE100_POST_REQUEST_FAILURE_VISIBLE_V1: Post used to do
                // nothing at all here.
                preflightFailureMessage = COMPOSER_REQUEST_UNBUILDABLE_MESSAGE
                return
            }
            onResetTextPostWriteState()
            pendingPreflightRequestKey = currentRequest.requestKey
            pendingPreflightMinimumPolicyRevision = composerPolicy.revision + 1L
            awaitingPreflightAuthorization = false
            onLoadComposerPolicy(state.activeAccountId, current.community)
        } else {
            state.publishPostComposerDraft()
        }
    }

    PredictiveBackHandler(enabled = true) { progress ->
        progress.collect()
        state.closePostComposer()
    }

    LaunchedEffect(authenticated, state.activeAccountId, draft.id, draft.community) {
        if (authenticated && draft.community.isNotBlank()) {
            state.ensureCommunityIdentities(listOf(draft.community))
            onLoadComposerPolicy(state.activeAccountId, draft.community)
        }
    }
    LaunchedEffect(authenticated, state.activeAccountId, communities.map { it.name }) {
        if (authenticated) state.ensureCommunityIdentities(communities.map { it.name })
    }
    LaunchedEffect(
        pendingPreflightRequestKey,
        pendingPreflightMinimumPolicyRevision,
        matchingPolicySnapshot?.revision,
        matchingPolicySnapshot?.status,
        matchingPolicySnapshot?.policy,
        request?.requestKey,
    ) {
        val pendingKey = pendingPreflightRequestKey ?: return@LaunchedEffect
        val currentRequest = request
        if (currentRequest == null || currentRequest.requestKey != pendingKey) {
            pendingPreflightRequestKey = null
            awaitingPreflightAuthorization = false
            return@LaunchedEffect
        }
        val snapshot = matchingPolicySnapshot ?: return@LaunchedEffect
        if (snapshot.revision < pendingPreflightMinimumPolicyRevision) {
            return@LaunchedEffect
        }
        when (snapshot.status) {
            ComposerPolicyStatus.IDLE,
            ComposerPolicyStatus.LOADING,
            -> return@LaunchedEffect

            ComposerPolicyStatus.UNAVAILABLE -> {
                pendingPreflightRequestKey = null
                awaitingPreflightAuthorization = false
                val failure = validateRemoteTextPostComposerDraftIssue(
                    synchronizedDraft(),
                    policy,
                )
                if (failure != null) {
                    validationRequested = true
                    revealValidationIssue(failure)
                } else {
                    onSubmitTextPost(currentRequest)
                }
            }

            ComposerPolicyStatus.LOADED -> {
                val refreshedBasePolicy = snapshot.policy ?:
                    ComposerCommunityPolicy.local(draft.community)
                val refreshedPolicy = if (
                    serverRequiredFlairCommunity.equals(
                        draft.community,
                        ignoreCase = true,
                    )
                ) {
                    refreshedBasePolicy.copy(flairRequired = true)
                } else {
                    refreshedBasePolicy
                }
                when (composerSubmitPreflightAction(refreshedPolicy)) {
                    ComposerSubmitPreflightAction.AUTHORIZE -> {
                        if (awaitingPreflightAuthorization) {
                            pendingPreflightRequestKey = null
                            awaitingPreflightAuthorization = false
                            preflightFailureMessage =
                                "Posting access is required to check community rules"
                            return@LaunchedEffect
                        }
                        val launched = onAuthorizeComposerPostRequirements(
                            state.activeAccountId,
                        )
                        if (launched) {
                            awaitingPreflightAuthorization = true
                            pendingPreflightMinimumPolicyRevision = snapshot.revision + 1L
                        } else {
                            pendingPreflightRequestKey = null
                            preflightFailureMessage =
                                "Posting access is required to check community rules"
                        }
                    }

                    ComposerSubmitPreflightAction.VALIDATE,
                    ComposerSubmitPreflightAction.FALLBACK_TO_REDDIT,
                    -> {
                        pendingPreflightRequestKey = null
                        awaitingPreflightAuthorization = false
                        val failure = validateRemoteTextPostComposerDraftIssue(
                            synchronizedDraft(),
                            refreshedPolicy,
                        )
                        if (failure != null) {
                            validationRequested = true
                            revealValidationIssue(failure)
                        } else {
                            onSubmitTextPost(currentRequest)
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(remoteValidationIssue) {
        val issue = remoteValidationIssue ?: return@LaunchedEffect
        if (issue.field == ComposerValidationField.FLAIR) {
            serverRequiredFlairCommunity = draft.community
        }
        if (authenticated && draft.community.isNotBlank()) {
            onLoadComposerPolicy(state.activeAccountId, draft.community)
        }
    }
    LaunchedEffect(
        communitySheetVisible,
        communityQuery,
        authenticated,
        state.activeAccountId,
    ) {
        if (!communitySheetVisible || !authenticated) return@LaunchedEffect
        delay(350L)
        onSearchComposerCommunities(state.activeAccountId, communityQuery)
    }
    LaunchedEffect(policy.loadedFromReddit, policy.community, policy.flairOptions) {
        val selectedFlair = state.postComposerDraft.flair ?: return@LaunchedEffect
        if (policy.loadedFromReddit && policy.flairOptionsLoaded) {
            val refreshedFlair = reconcileComposerFlairSelection(
                selected = selectedFlair,
                available = policy.flairOptions,
            )
            if (refreshedFlair == null) {
                update { it.copy(flair = null) }
                flairNotice = "This flair is no longer available."
            } else if (refreshedFlair != selectedFlair) {
                update { it.copy(flair = refreshedFlair) }
            }
        }
    }
    // LOWUX1_REMOTE_ISSUE_REVEALED_ONCE_V1 (OCB-313): the reveal ran again on
    // every re-entry of the composer (and after a recreation), relaunching the
    // flair authorization or a sheet without a tap; each stage of an issue is
    // revealed once per write result.
    var revealedRemoteIssueKey by rememberSaveable(draft.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(remoteValidationIssue, policy.flairAuthorizationRequired, policy.flairOptionsLoaded) {
        val issue = remoteValidationIssue ?: return@LaunchedEffect
        val revealKey = "${activeTextPostWriteState.requestKey}:${activeTextPostWriteState.completionRevision}:" +
            "${issue.field}:${policy.flairAuthorizationRequired}:${policy.flairOptionsLoaded}"
        if (revealKey == revealedRemoteIssueKey) return@LaunchedEffect
        revealedRemoteIssueKey = revealKey
        when (issue.field) {
            ComposerValidationField.COMMUNITY -> {
                focusManager.clearFocus()
                communityQuery = ""
                communitySheetVisible = true
            }
            ComposerValidationField.TITLE -> {
                composerListState.animateScrollToItem(0)
                titleFocusRequester.requestFocus()
            }
            ComposerValidationField.LINK -> {
                linkBringIntoViewRequester.bringIntoView()
                linkFocusRequester.requestFocus()
            }
            ComposerValidationField.FLAIR -> {
                focusManager.clearFocus()
                flairBringIntoViewRequester.bringIntoView()
                delay(180L)
                if (policy.flairAuthorizationRequired) {
                    onAuthorizeComposerFlair(state.activeAccountId)
                } else if (policy.loadedFromReddit && !policy.flairOptionsLoaded) {
                    onLoadComposerPolicy(state.activeAccountId, draft.community)
                } else {
                    flairSheetVisible = true
                }
            }
            ComposerValidationField.BODY -> {
                bodyBringIntoViewRequester.bringIntoView()
                bodyFocusRequester.requestFocus()
            }
            ComposerValidationField.MEDIA -> {
                focusManager.clearFocus()
                composerScope.launch {
                    mediaBringIntoViewRequester.bringIntoView()
                }
            }
            ComposerValidationField.GENERAL -> composerListState.animateScrollToItem(0)
        }
    }

    LaunchedEffect(flairNotice) {
        if (flairNotice != null) {
            delay(4_500)
            flairNotice = null
        }
    }
    // COMPOSER13_BODY_SYNC_ECHO_V1: the text the editor last wrote to the
    // draft. The draft's own echo of it must not overwrite what was typed
    // since (it dropped a fast keystroke); only a body changed elsewhere is
    // put back into the editor.
    val bodyEcho = remember(draft.id) { ComposerBodyEcho(draft.body) }
    LaunchedEffect(bodyState, draft.id) {
        snapshotFlow { bodyState.text.toString() }
            .distinctUntilChanged()
            .collect { nextBody ->
                val current = state.postComposerDraft
                if (current.id == draft.id && current.body != nextBody) {
                    bodyEcho.lastWritten = nextBody
                    state.updatePostComposerDraft(current.copy(body = nextBody))
                }
            }
    }
    LaunchedEffect(draft.id, draft.body) {
        composerBodyToApply(
            draftBody = draft.body,
            editorBody = bodyState.text.toString(),
            lastWrittenByEditor = bodyEcho.lastWritten,
        )?.let { body ->
            bodyEcho.lastWritten = body
            bodyState.replaceAllV4(body)
        }
    }
    LaunchedEffect(draft.updatedAtEpochMs) {
        if (draft.hasContent) {
            delay(450)
            state.savePostComposerDraft()
            persistedRevision = state.postComposerDraft.updatedAtEpochMs
        }
    }
    val pickerSheetVisible = communitySheetVisible || flairSheetVisible || mediaSheetVisible
    val flairAuthorizationRequired = policy.flairAuthorizationRequired
    val flairLoadFailed = policy.loadedFromReddit &&
        !policy.flairOptionsLoaded &&
        !flairAuthorizationRequired
    val showFlairAction = policy.flairRequired ||
        policy.flairOptions.isNotEmpty() ||
        draft.flair != null ||
        flairAuthorizationRequired ||
        flairLoadFailed
    val linkItemIndex = v4ComposerLinkItemIndex(
        hasPolicyStatus = policyStatusMessage != null,
        hasFlairAction = showFlairAction,
        hasFlairNotice = flairNotice != null,
        hasGeneralValidation = generalValidationMessage != null,
    )

    // ISSUE101_LINK_FOCUS_ONCE_V1: one Add link tap focuses the link field once.
    // A row appearing above it (rules check, flair, validation) moves the index
    // but must not pull the reader back from the body.
    val currentLinkItemIndex by rememberUpdatedState(linkItemIndex)
    var handledLinkFocusRevision by remember(draft.id) { mutableIntStateOf(0) }
    LaunchedEffect(linkFocusRequestRevision, draft.type) {
        if (linkFocusRequestRevision == handledLinkFocusRevision ||
            draft.type != ComposerPostType.LINK
        ) {
            return@LaunchedEffect
        }
        handledLinkFocusRevision = linkFocusRequestRevision
        composerListState.animateScrollToItem(currentLinkItemIndex)
        delay(32L)
        linkFocusRequester.requestFocus()
        delay(96L)
        linkBringIntoViewRequester.bringIntoView()
    }

    // LOWUX1_COMPOSER_INPUT_LOCKED_WHILE_SENDING_V1 (OCB-205): update() drops
    // every change while a post is sent; the fields, switches and pickers say
    // so (disabled) instead of taking input that disappears.
    CompositionLocalProvider(LocalComposerLocked provides composerLocked) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val wideEditor = maxWidth >= V4_WIDE_EDITOR_BREAKPOINT_DP.dp

        val routeTopBarScroll = rememberRouteTopBarScrollBehavior(ChromeBehavior.ALWAYS_SHOW)
        Scaffold(
            modifier = Modifier.fillMaxSize().imePadding()
                .nestedScroll(routeTopBarScroll.nestedScrollConnection),
            topBar = {
                RouteTopBar(
                    scrollBehavior = routeTopBarScroll,
                    title = {
                        Column {
                            Text("Create post")
                            saveLabel?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = state::closePostComposer) {
                            Icon(
                                Symbols.ArrowBack,
                                contentDescription = "Save draft and close",
                            )
                        }
                    },
                    actions = {
                        ComposerDiscardAction(
                            enabled = draft.hasContent &&
                                (!authenticated || !activeTextPostWriteState.inProgress),
                            onClick = { discardConfirmationVisible = true },
                        )
                        ComposerPrimaryAction(
                            text = when {
                                preflightPending -> "Checking…"
                                activeTextPostWriteState.phase == TextPostWritePhase.SENDING &&
                                    draft.type == ComposerPostType.MEDIA -> "Uploading…"
                                activeTextPostWriteState.phase ==
                                    TextPostWritePhase.RECONCILING -> "Checking…"
                                activeTextPostWriteState.inProgress -> "Posting…"
                                else -> "Post"
                            },
                            enabled = postEnabled,
                            onClick = ::submit,
                        )
                    },
                )
            },
            bottomBar = {
                if (policy.bodyAllowed && !pickerSheetVisible && !composerLocked) {
                    Column {
                        // ISSUE229_POST_BODY_MENTIONS_V1: above the dock, so the list
                        // stays on the keyboard wherever the cursor is in a long body.
                        MentionSuggestions(
                            state = bodyState,
                            mentions = postBodyMentions(
                                accountStableId = state.activeAccountId,
                                authenticated = authenticated,
                                searchState = mentionSearchState,
                                onSearch = onSearchMentions,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ComposerFormattingDock(
                            state = bodyState,
                            onFormat = ::applyFormat,
                            onReturnToEditor = { compactPreviewVisible = false },
                        )
                    }
                }
            },
        ) { padding ->
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.TopCenter,
            ) {
                LazyColumn(
                    state = composerListState,
                    modifier = Modifier.fillMaxHeight().widthIn(max = 1120.dp).fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = if (wideEditor) 24.dp else 16.dp,
                        top = 10.dp,
                        end = if (wideEditor) 24.dp else 16.dp,
                        bottom = 44.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item("write-status") {
                        TextPostWriteStatus(
                            authenticated = authenticated,
                            writeState = activeTextPostWriteState,
                            grantEnabled = request != null,
                            retryEnabled = request != null,
                            onGrantAccess = { request?.let(onAuthorizeTextPostWrite) },
                            onRetry = ::submit,
                            onReconcile = onReconcileTextPostSubmit,
                            onCancelAuthorization = onCancelTextPostWriteAuthorization,
                            onDismiss = onResetTextPostWriteState,
                            sendingMessage = if (draft.type == ComposerPostType.MEDIA) {
                                when {
                                    draft.media.size > 1 ->
                                        "Uploading gallery to Reddit…"
                                    draft.media.singleOrNull()?.isComposerVideoGif() == true ->
                                        "Uploading GIF to Reddit…"
                                    draft.media.singleOrNull()?.kind == ComposerMediaKind.GIF ->
                                        "Uploading GIF to Reddit…"
                                    draft.media.singleOrNull()?.isComposerVideoSource() == true ->
                                        "Uploading video to Reddit…"
                                    else ->
                                        "Uploading image to Reddit…"
                                }
                            } else {
                                "Posting to Reddit…"
                            },
                        )
                    }
                    item("community") {
                        V4CommunitySelector(
                            community = draft.community,
                            iconUri = selectedCommunity?.iconImageUri,
                            accent = selectedCommunity?.accent,
                            postingAs = composerPostingAsUsername(state),
                            onClick = {
                                focusManager.clearFocus()
                                communityQuery = ""
                                communitySheetVisible = true
                            },
                        )
                    }
                    if (policyStatusMessage != null) {
                        item("policy-status") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    policyStatusMessage,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (matchingPolicySnapshot?.status == ComposerPolicyStatus.UNAVAILABLE) {
                                    TextButton(
                                        onClick = {
                                            onLoadComposerPolicy(
                                                state.activeAccountId,
                                                draft.community,
                                            )
                                        },
                                    ) { Text("Retry") }
                                }
                            }
                        }
                    }
                    if (showFlairAction) {
                        item("flair") {
                            V4FlairAction(
                                modifier = Modifier.bringIntoViewRequester(
                                    flairBringIntoViewRequester,
                                ),
                                flair = draft.flair,
                                required = policy.flairRequired,
                                errorMessage = flairValidationMessage,
                                community = draft.community,
                                authorizationRequired = flairAuthorizationRequired,
                                loadFailed = flairLoadFailed,
                                onClick = { flairSheetVisible = true },
                                onAuthorize = {
                                    onAuthorizeComposerFlair(state.activeAccountId)
                                },
                                onRetry = {
                                    onLoadComposerPolicy(
                                        state.activeAccountId,
                                        draft.community,
                                    )
                                },
                            )
                        }
                    }
                    flairNotice?.let { notice ->
                        item("flair-notice") {
                            Text(
                                notice,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    generalValidationMessage?.let { message ->
                        item("validation-summary") {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                Text(
                                    message,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                    item("title") {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            OutlinedTextField(
                                value = draft.title,
                                enabled = !LocalComposerLocked.current,
                                // LOWUX1_PASTED_TITLE_LINES_BECOME_SPACES_V1 (OCB-206):
                                // a pasted two-line title was dropped whole.
                                onValueChange = { value ->
                                    val oneLine = composerTitleOnOneLine(value)
                                    update { it.copy(title = oneLine) }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(titleFocusRequester),
                                label = { Text("Title") },
                                placeholder = { Text("Add a clear, interesting title") },
                                isError = titleValidationMessage != null ||
                                    draft.title.length > policy.titleMaxLength,
                                supportingText = composerTitleSupportingText(
                                    message = titleValidationMessage,
                                    length = draft.title.length,
                                    maxLength = policy.titleMaxLength,
                                ),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Sentences,
                                    imeAction = ImeAction.Next,
                                ),
                            )
                        }
                    }
                    if (draft.type == ComposerPostType.LINK) {
                        item("link") {
                            V4LinkAttachment(
                                value = draft.linkUrl,
                                validationMessage = linkValidationMessage,
                                focusRequester = linkFocusRequester,
                                bringIntoViewRequester = linkBringIntoViewRequester,
                                previewsByUrl = linkPreviewsByUrl,
                                onPreviewChanged = { metadata ->
                                    resolvedLinkPreview = metadata
                                },
                                onValueChange = { value ->
                                    if (!composerLocked) {
                                        resolvedLinkPreview = null
                                        update { it.copy(type = ComposerPostType.LINK, linkUrl = value) }
                                    }
                                },
                                onRemove = {
                                    if (!composerLocked) {
                                        resolvedLinkPreview = null
                                        update { it.copy(type = ComposerPostType.TEXT, linkUrl = "") }
                                    }
                                },
                            )
                        }
                    }
                    if (draft.type == ComposerPostType.MEDIA) {
                        item("media") {
                            V4MediaSection(
                                modifier = Modifier.bringIntoViewRequester(
                                    mediaBringIntoViewRequester,
                                ),
                                media = draft.media,
                                validationMessage = mediaValidationMessage,
                                videoAsGif = draft.media.singleOrNull()?.isComposerVideoGif() == true,
                                videoAllowed = policy.allowVideo,
                                videoGifAllowed = policy.allowVideoGifs,
                                onVideoAsGifChange = { enabled ->
                                    update { current ->
                                        val item = current.media.singleOrNull()
                                        val targetAllowed = if (enabled) {
                                            policy.allowVideoGifs
                                        } else {
                                            policy.allowVideo
                                        }
                                        if (
                                            item == null ||
                                            !item.isComposerVideoSource() ||
                                            !targetAllowed
                                        ) {
                                            current
                                        } else {
                                            current.copy(
                                                media = listOf(
                                                    item.copy(
                                                        kind = if (enabled) {
                                                            ComposerMediaKind.GIF
                                                        } else {
                                                            ComposerMediaKind.VIDEO
                                                        },
                                                    ),
                                                ),
                                            )
                                        }
                                    }
                                },
                                onAddPhotos = {
                                    photoPicker.launch(
                                        PickVisualMediaRequest(PickVisualMedia.ImageOnly),
                                    )
                                },
                                onReplaceVideo = {
                                    videoPicker.launch(
                                        PickVisualMediaRequest(PickVisualMedia.VideoOnly),
                                    )
                                },
                                onRemove = { index ->
                                    update { current ->
                                        val remaining = current.media.filterIndexed { i, _ -> i != index }
                                        current.copy(
                                            type = if (remaining.isEmpty()) {
                                                ComposerPostType.TEXT
                                            } else {
                                                ComposerPostType.MEDIA
                                            },
                                            media = remaining,
                                        )
                                    }
                                },
                                onMove = { from, to ->
                                    update { current ->
                                        val moved = current.media.toMutableList()
                                        val movedItem = moved.removeAt(from)
                                        moved.add(to, movedItem)
                                        current.copy(media = moved)
                                    }
                                },
                                onCaptionChange = { index, caption ->
                                    update { current ->
                                        val changed = current.media.toMutableList()
                                        changed[index] = changed[index].copy(caption = caption)
                                        current.copy(media = changed)
                                    }
                                },
                                onOutboundUrlChange = { index, outboundUrl ->
                                    update { current ->
                                        val changed = current.media.toMutableList()
                                        changed[index] = changed[index].copy(outboundUrl = outboundUrl)
                                        current.copy(media = changed)
                                    }
                                },
                            )
                        }
                    }
                    if (policy.bodyAllowed) {
                        item("body") {
                            V4BodyEditor(
                                modifier = Modifier.bringIntoViewRequester(
                                    bodyBringIntoViewRequester,
                                ),
                                state = bodyState,
                                focusRequester = bodyFocusRequester,
                                required = policy.bodyRequired,
                                maxLength = policy.bodyMaxLength,
                                wide = wideEditor,
                                compactPreviewVisible = compactPreviewVisible,
                                onPreviewVisibleChange = { compactPreviewVisible = it },
                                onFormat = ::applyFormat,
                                validationMessage = bodyValidationMessage,
                                inputLocked = composerLocked,
                                visibleListHeight = {
                                    composerListState.layoutInfo.let { info ->
                                        info.viewportSize.height -
                                            info.beforeContentPadding - info.afterContentPadding
                                    }
                                },
                                scrollBodyToTop = {
                                    composerListState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { it.key == "body" }
                                        ?.let { composerListState.animateScrollToItem(it.index) }
                                },
                                onMediaReceived = { pasted ->
                                    // COMPOSER16_PASTED_IMAGE_COPIED_V1
                                    val received = pastedImageKeptInApp(context, pasted)
                                    updateConfirmingLoss { current ->
                                        current.copy(
                                            type = ComposerPostType.MEDIA,
                                            linkUrl = "",
                                            media = composerMediaWithPasted(current.media, received),
                                        )
                                    }
                                },
                            )
                        }
                    } else if (bodyText.isNotBlank()) {
                        // ISSUE56_BODY_NOT_ALLOWED_VISIBLE_V1: the community allows no
                        // body, but there is one (shared text, or written before the
                        // community changed): show it, not editable, with a way to remove it.
                        item("body") {
                            V4BodyNotAllowed(
                                modifier = Modifier.bringIntoViewRequester(
                                    bodyBringIntoViewRequester,
                                ),
                                body = bodyText,
                                enabled = !composerLocked,
                                onRemove = { bodyState.clearText() },
                            )
                        }
                    }
                    item("add-content") {
                        V4AddContentRow(
                            linkEnabled = policy.allows(ComposerPostType.LINK),
                            mediaEnabled = policy.allows(ComposerPostType.MEDIA) &&
                                (
                                    policy.allowImages ||
                                        policy.allowGalleries ||
                                        policy.allowVideo ||
                                        policy.allowVideoGifs
                                    ),
                            showMedia = draft.media.isEmpty(),
                            onAddLink = {
                                updateConfirmingLoss {
                                    it.copy(
                                        type = ComposerPostType.LINK,
                                        media = emptyList(),
                                    )
                                }
                                if (state.postComposerDraft.type == ComposerPostType.LINK) {
                                    linkFocusRequestRevision += 1
                                }
                            },
                            onAddMedia = { mediaSheetVisible = true },
                        )
                    }
                    item("options") {
                        V4PostOptions(
                            draft = draft,
                            expanded = optionsExpanded,
                            onExpandedChange = { optionsExpanded = !optionsExpanded },
                            onNsfwChange = { value -> update { it.copy(nsfw = value) } },
                            onSpoilerChange = { value -> update { it.copy(spoiler = value) } },
                            onRepliesChange = { value -> update { it.copy(sendReplies = value) } },
                        )
                    }
                }
            }
        }
    }
    }

    if (communitySheetVisible && !composerLocked) {
        val knownCommunityOptions = composerKnownCommunityOptions(
            cached = communities,
            subscribed = subscribedCommunities,
            joinedIds = state.joinedCommunityIds,
            isFavorite = state::isCommunityFavorite,
            identity = state::communityIdentityFor,
        )
        V4CommunitySheet(
            query = communityQuery,
            onQueryChange = { communityQuery = it },
            knownOptions = knownCommunityOptions,
            remoteSearch = composerCommunitySearch,
            selectedCommunity = draft.community,
            authenticated = authenticated,
            profileUsername = state.activeAccountUsername.takeIf { state.activeAccountIsAuthenticated },
            onSelect = { option ->
                communitySheetVisible = false
                communityQuery = ""
                update { current ->
                    current.copy(community = option.name, flair = null)
                }
            },
            onDismiss = {
                communitySheetVisible = false
                communityQuery = ""
            },
        )
    }
    if (flairSheetVisible && !composerLocked) {
        V4FlairSheet(
            community = draft.community,
            required = policy.flairRequired,
            options = policy.flairOptions,
            selected = draft.flair,
            onSelect = { flair ->
                update { it.copy(flair = flair) }
                flairSheetVisible = false
            },
            onClear = {
                update { it.copy(flair = null) }
                flairSheetVisible = false
            },
            onDismiss = { flairSheetVisible = false },
        )
    }
    if (mediaSheetVisible) {
        V4MediaPickerSheet(
            photosEnabled = policy.allowImages || policy.allowGalleries,
            videoEnabled = policy.allowVideo || policy.allowVideoGifs,
            onPickPhotos = {
                mediaSheetVisible = false
                photoPicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
            },
            onPickVideo = {
                mediaSheetVisible = false
                videoPicker.launch(PickVisualMediaRequest(PickVisualMedia.VideoOnly))
            },
            onDismiss = { mediaSheetVisible = false },
        )
    }
    pendingReplacement?.let { (loss, transform) ->
        AlertDialog(
            onDismissRequest = { pendingReplacement = null },
            title = { Text(loss.title) },
            text = { Text(loss.message) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingReplacement = null
                        update(transform)
                        if (state.postComposerDraft.type == ComposerPostType.LINK) {
                            linkFocusRequestRevision += 1
                        }
                    },
                ) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingReplacement = null }) { Text("Keep") }
            },
        )
    }
    if (discardConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { discardConfirmationVisible = false },
            title = { Text("Discard draft?") },
            text = { Text("This permanently removes this draft from Drafts.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        discardConfirmationVisible = false
                        state.discardPostComposerDraft()
                        if (authenticated) onResetTextPostWriteState()
                    },
                ) { Text("Discard", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { discardConfirmationVisible = false }) {
                    Text("Keep draft")
                }
            },
        )
    }
}


private fun TextPostWriteValidationIssue.toComposerValidationIssue(): ComposerValidationIssue =
    ComposerValidationIssue(
        field = when (field) {
            TextPostWriteValidationField.COMMUNITY -> ComposerValidationField.COMMUNITY
            TextPostWriteValidationField.TITLE -> ComposerValidationField.TITLE
            TextPostWriteValidationField.FLAIR -> ComposerValidationField.FLAIR
            TextPostWriteValidationField.BODY -> ComposerValidationField.BODY
            TextPostWriteValidationField.LINK -> ComposerValidationField.LINK
            TextPostWriteValidationField.MEDIA -> ComposerValidationField.MEDIA
            TextPostWriteValidationField.GENERAL -> ComposerValidationField.GENERAL
        },
        message = message,
    )

private fun TextFieldState.replaceAllV4(value: String) {
    edit {
        replace(0, length, value)
        selection = TextRange(value.length)
    }
}

private fun TextFieldState.applyV4Markdown(action: MarkdownAction) {
    val currentText = text.toString()
    val currentSelection = selection
    val result = applyMarkdownAction(
        text = currentText,
        selectionStart = currentSelection.start,
        selectionEnd = currentSelection.end,
        action = action,
    )
    edit {
        replace(0, length, result.text)
        selection = TextRange(result.selectionStart, result.selectionEnd)
    }
}

internal fun PostComposerDraft.toV4RemoteRequest(
    accountStableId: String,
    linkPreview: LinkPreviewMetadata?,
): TextPostWriteRequest? {
    // CROSSPOST01A1C_LEGACY_COMPOSER_SUBMIT_FENCE_V1
    if (type == ComposerPostType.CROSSPOST || crosspostSource != null) return null
    val remoteMedia = if (type == ComposerPostType.MEDIA && media.size == 1) {
        media.single().takeIf { item ->
            item.kind == ComposerMediaKind.IMAGE || item.isComposerVideoSource() ||
                item.kind == ComposerMediaKind.GIF
        } ?: return null
    } else {
        null
    }
    // ISSUE100_POST_REQUEST_FAILURE_VISIBLE_V1: a link preview image the request
    // rejects (java.net.URI refuses characters OkHttp keeps, such as '|') no
    // longer blocks the post: it is sent without the preview image.
    fun build(previewImageUrl: String?): TextPostWriteRequest? = runCatching {
        val remoteGallery = if (type == ComposerPostType.MEDIA && media.size > 1) {
            media.map { item ->
                TextPostWriteGalleryItem(
                    mediaUri = item.uri,
                    mediaMimeType = item.mimeType,
                    caption = item.caption,
                    outboundUrl = item.outboundUrl,
                )
            }
        } else {
            emptyList()
        }
        TextPostWriteRequest(
            accountStableId = accountStableId,
            community = community,
            title = title,
            text = body,
            sendReplies = sendReplies,
            nsfw = nsfw,
            spoiler = spoiler,
            linkUrl = linkUrl.takeIf { type == ComposerPostType.LINK },
            linkPreviewImageUrl = previewImageUrl,
            mediaUri = remoteMedia?.uri,
            mediaMimeType = remoteMedia?.mimeType,
            galleryItems = remoteGallery,
            mediaAsGif = remoteMedia?.isComposerVideoGif() == true,
            flairId = flair?.templateId,
            flairText = flair?.text,
        )
    }.getOrNull()
    val previewImageUrl = linkPreview?.imageUrl?.takeIf { type == ComposerPostType.LINK }
    return build(previewImageUrl) ?: previewImageUrl?.let { build(null) }
}

internal fun v4ComposerLinkItemIndex(
    hasPolicyStatus: Boolean,
    hasFlairAction: Boolean,
    hasFlairNotice: Boolean,
    hasGeneralValidation: Boolean,
): Int = 3 + listOf(
    hasPolicyStatus,
    hasFlairAction,
    hasFlairNotice,
    hasGeneralValidation,
).count { it }

@Composable
internal fun V4CommunitySelector(
    community: String,
    iconUri: String?,
    accent: Long?,
    onClick: () -> Unit,
    postingAs: String? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (community.isNotBlank()) {
                RemoteIdentityImage(
                    uri = iconUri,
                    contentDescription = "Icon for r/$community",
                    modifier = Modifier.size(38.dp).clip(CircleShape),
                ) {
                    CommunityAvatar(
                        community,
                        accent?.let(::Color) ?: MaterialTheme.colorScheme.tertiary,
                        Modifier.fillMaxSize(),
                    )
                }
            } else {
                Surface(
                    modifier = Modifier.size(38.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Symbols.Search, contentDescription = null)
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    postingAs?.let { username -> "Posting as u/$username to" } ?: "Posting to",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (community.isBlank()) "Choose a community" else composerDestinationLabel(community),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Icon(Symbols.ExpandMore, contentDescription = "Choose community")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun V4CommunitySheet(
    query: String,
    onQueryChange: (String) -> Unit,
    knownOptions: List<ComposerCommunityOption>,
    remoteSearch: ComposerCommunitySearchSnapshot,
    selectedCommunity: String,
    authenticated: Boolean,
    onSelect: (ComposerCommunityOption) -> Unit,
    onDismiss: () -> Unit,
    profileUsername: String? = null,
) {
    val normalizedQuery = query
        .trim()
        .removePrefix("/r/")
        .removePrefix("r/")
    val remoteMatches = remoteSearch.options.takeIf {
        remoteSearch.query.equals(normalizedQuery, ignoreCase = true)
    }.orEmpty()
    val options = composerCommunityPickerOptions(
        known = knownOptions,
        remoteMatches = remoteMatches,
        query = normalizedQuery,
        authenticated = authenticated,
        profileUsername = profileUsername,
    )
    val density = LocalDensity.current
    var suppressInitialPartialTransition by remember { mutableStateOf(true) }
    val sheetState = remember(density) {
        SheetState(
            enabledValues = setOf(SheetValue.Hidden, SheetValue.PartiallyExpanded, SheetValue.Expanded),
            positionalThreshold = {
                with(density) { 56.dp.toPx() }
            },
            velocityThreshold = {
                with(density) { 125.dp.toPx() }
            },
            initialValue = SheetValue.Expanded,
            confirmValueChange = { target ->
                if (
                    suppressInitialPartialTransition &&
                    target == SheetValue.PartiallyExpanded
                ) {
                    suppressInitialPartialTransition = false
                    false
                } else {
                    true
                }
            },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight(0.94f),
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Choose a community", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        if (authenticated) {
                            "Search Reddit or choose a known community"
                        } else {
                            "Choose from communities available in this prototype"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Symbols.Close, contentDescription = "Close community picker")
                }
            }
            SubshellSearchField(
                query = query,
                onQueryChange = onQueryChange,
                placeholder = "Search r/norge, r/android…",
                trailingIcon = if (query.isNotBlank()) {
                    {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Symbols.Close, contentDescription = "Clear search")
                        }
                    }
                } else {
                    null
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    when {
                        normalizedQuery.isNotBlank() -> "Results"
                        authenticated -> "Your communities"
                        else -> "Known communities"
                    },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (
                    remoteSearch.status == ComposerCommunitySearchStatus.LOADING &&
                    normalizedQuery.length >= 2
                ) {
                    InlineProgressIndicator()
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(
                    items = options,
                    key = { option -> option.name.lowercase() },
                ) { option ->
                    V4CommunityOptionRow(
                        option = option,
                        selected = option.name.equals(
                            selectedCommunity,
                            ignoreCase = true,
                        ),
                        onClick = { onSelect(option) },
                    )
                }
                if (options.isEmpty() && normalizedQuery.isBlank() && authenticated) {
                    item("community-none-joined") {
                        Text(
                            "Search for a community to post in.",
                            modifier = Modifier.padding(vertical = 18.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (options.isEmpty() && normalizedQuery.length >= 2) {
                    item("community-empty") {
                        Text(
                            if (remoteSearch.status == ComposerCommunitySearchStatus.UNAVAILABLE) {
                                "Reddit search is unavailable. Check the connection and try again."
                            } else if (
                                remoteSearch.status == ComposerCommunitySearchStatus.LOADED
                            ) {
                                "No communities found for “$normalizedQuery”."
                            } else {
                                "Searching Reddit…"
                            },
                            modifier = Modifier.padding(vertical = 18.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun V4CommunityOptionRow(
    option: ComposerCommunityOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RemoteIdentityImage(
                uri = option.iconImageUri,
                contentDescription = "Icon for ${composerDestinationLabel(option.name)}",
                modifier = Modifier.size(42.dp).clip(CircleShape),
            ) {
                CommunityAvatar(
                    option.name,
                    MaterialTheme.colorScheme.tertiary,
                    Modifier.fillMaxSize(),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(composerDestinationLabel(option.name), style = MaterialTheme.typography.titleMedium)
                val details = buildList {
                    if (option.joined) add("Joined")
                    option.members?.let { add(v4CompactMemberCount(it)) }
                    option.title.takeIf(String::isNotBlank)?.let(::add)
                }.joinToString(" · ")
                if (details.isNotBlank()) {
                    Text(
                        details,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (selected) {
                Icon(Symbols.Check, contentDescription = "Selected")
            } else {
                Icon(Symbols.ChevronRight, contentDescription = null)
            }
        }
    }
}

private fun v4CompactMemberCount(value: Long): String = when {
    value >= 1_000_000L -> "${value / 100_000L / 10.0}M members"
    value >= 1_000L -> "${value / 100L / 10.0}K members"
    else -> "$value members"
}

@Composable
private fun V4FlairAction(
    modifier: Modifier = Modifier,
    flair: ComposerFlair?,
    required: Boolean,
    errorMessage: String?,
    community: String,
    authorizationRequired: Boolean,
    loadFailed: Boolean,
    onClick: () -> Unit,
    onAuthorize: () -> Unit,
    onRetry: () -> Unit,
) {
    val isError = errorMessage != null
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                if (errorMessage != null) error(errorMessage)
            },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = if (isError) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.error)
        } else {
            null
        },
        onClick = when {
            authorizationRequired -> onAuthorize
            loadFailed -> onRetry
            else -> onClick
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = MaterialTheme.shapes.medium,
                color = if (isError) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
                contentColor = if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Symbols.Label,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "Post flair",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                when {
                    authorizationRequired -> {
                        Text(
                            "Allow flair access",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "Needed to load r/$community post flairs",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    loadFailed -> Text(
                        "Couldn't load post flairs · Tap to retry",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    flair != null -> V4FlairPreview(flair)
                    else -> Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (required) "Choose a flair" else "Add flair",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = when {
                                isError -> MaterialTheme.colorScheme.error
                                required -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                        )
                        if (required) V4RequiredBadge()
                    }
                }
                errorMessage?.let { message ->
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Icon(
                if (loadFailed) Symbols.Refresh else Symbols.ChevronRight,
                contentDescription = when {
                    authorizationRequired -> "Allow access to post flairs"
                    loadFailed -> "Retry loading post flairs"
                    else -> "Choose post flair"
                },
                tint = if (loadFailed || isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun V4FlairSheet(
    community: String,
    required: Boolean,
    options: List<ComposerFlair>,
    selected: ComposerFlair?,
    onSelect: (ComposerFlair) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember(options) { mutableStateOf("") }
    var editingTemplate by remember(options, selected?.templateId) {
        mutableStateOf<ComposerFlair?>(null)
    }
    val filteredOptions = remember(options, query) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) options else options.filter {
            it.text.contains(normalizedQuery, ignoreCase = true) ||
                it.richText.any { segment ->
                    segment.text?.contains(normalizedQuery, ignoreCase = true) == true ||
                        segment.emojiRef?.contains(normalizedQuery, ignoreCase = true) == true
                }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        if (editingTemplate != null) {
            V4EditableFlairEditor(
                community = community,
                template = requireNotNull(editingTemplate),
                selected = selected,
                onApply = onSelect,
                onCancel = { editingTemplate = null },
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Symbols.Label, contentDescription = null)
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Choose post flair", style = MaterialTheme.typography.headlineSmall)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                composerDestinationLabel(community),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (required) V4RequiredBadge()
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Symbols.Close, contentDescription = "Close flair picker")
                    }
                }

                if (options.size >= V4_FLAIR_SEARCH_THRESHOLD) {
                    SubshellSearchField(
                        query = query,
                        onQueryChange = { query = it },
                        placeholder = "Search flairs",
                        trailingIcon = if (query.isNotEmpty()) {
                            {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Symbols.Close, contentDescription = "Clear search")
                                }
                            }
                        } else {
                            null
                        },
                    )
                }

                when {
                    options.isEmpty() -> Text(
                        if (required) {
                            "This community requires flair, but Reddit returned no selectable flair."
                        } else {
                            "No post flair is available for this community."
                        },
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    filteredOptions.isEmpty() -> Text(
                        "No flairs match your search.",
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 520.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (!required) {
                                V4FlairChoiceChip(
                                    flair = null,
                                    selected = selected == null,
                                    onClick = onClear,
                                )
                            }
                            filteredOptions.forEach { option ->
                                V4FlairChoiceChip(
                                    flair = option,
                                    selected = selected?.templateId == option.templateId,
                                    onClick = {
                                        if (option.editable) editingTemplate = option
                                        else onSelect(option)
                                    },
                                )
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun V4FlairChoiceChip(
    flair: ComposerFlair?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            if (flair == null) Text("No flair") else V4FlairLabel(flair)
        },
        leadingIcon = when {
            selected -> {
                {
                    Icon(
                        Symbols.Check,
                        contentDescription = "Selected",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            flair == null -> {
                {
                    Icon(
                        Symbols.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            else -> null
        },
        trailingIcon = if (flair?.editable == true) {
            {
                Icon(
                    Symbols.Edit,
                    contentDescription = "Customize flair",
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            null
        },
    )
}

@Composable
private fun V4EditableFlairEditor(
    community: String,
    template: ComposerFlair,
    selected: ComposerFlair?,
    onApply: (ComposerFlair) -> Unit,
    onCancel: () -> Unit,
) {
    var editedText by remember(template.templateId, selected?.text) {
        mutableStateOf(
            if (selected?.templateId == template.templateId) selected.text else template.text,
        )
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) {
                Icon(Symbols.ArrowBack, contentDescription = "Back to flairs")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Customize flair", style = MaterialTheme.typography.headlineSmall)
                Text(
                    composerDestinationLabel(community),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Preview",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                V4FlairPreview(template.copy(text = editedText, richText = emptyList()))
            }
        }
        OutlinedTextField(
            value = editedText,
            onValueChange = { if (it.length <= COMPOSER_FLAIR_TEXT_MAX_LENGTH) editedText = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Flair text") },
            supportingText = { Text("${editedText.length} / $COMPOSER_FLAIR_TEXT_MAX_LENGTH") },
            singleLine = true,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Button(
                enabled = editedText.isNotBlank(),
                onClick = {
                    onApply(template.copy(text = editedText.trim(), richText = emptyList()))
                },
            ) { Text("Apply") }
        }
    }
}

@Composable
private fun V4RequiredBadge() {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(
            "Required",
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun V4FlairPreview(flair: ComposerFlair) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        V4FlairLabel(
            flair = flair,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun V4FlairLabel(
    flair: ComposerFlair,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (flair.richText.isEmpty()) {
            Text(
                flair.text.ifBlank { "Editable flair" },
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            flair.richText.forEach { segment ->
                when (segment.elementType.lowercase()) {
                    "emoji" -> if (!segment.imageUrl.isNullOrBlank()) {
                        RemoteImage(
                            uri = segment.imageUrl,
                            label = segment.emojiRef ?: "Flair emoji",
                            modifier = Modifier.size(18.dp).clip(MaterialTheme.shapes.small),
                            aspectRatio = null,
                            containerColor = Color.Transparent,
                            showFailureDetails = false,
                        )
                    } else {
                        Text(
                            segment.emojiRef.orEmpty(),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    else -> segment.text?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun V4LinkAttachment(
    value: String,
    validationMessage: String?,
    focusRequester: FocusRequester,
    bringIntoViewRequester: BringIntoViewRequester,
    previewsByUrl: MutableMap<String, LinkPreviewMetadata?>,
    onPreviewChanged: (LinkPreviewMetadata?) -> Unit,
    onValueChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    var previewState by remember(value) {
        mutableStateOf<V4LinkPreviewState>(
            if (value in previewsByUrl) {
                previewsByUrl[value]?.let(V4LinkPreviewState::Ready) ?: V4LinkPreviewState.Unavailable
            } else {
                V4LinkPreviewState.Idle
            },
        )
    }

    LaunchedEffect(value) {
        if (value in previewsByUrl) {
            onPreviewChanged(previewsByUrl[value])
            return@LaunchedEffect
        }
        onPreviewChanged(null)
        if (!LinkPreviewResolver.canResolve(value)) {
            previewState = V4LinkPreviewState.Idle
            return@LaunchedEffect
        }
        delay(V4_LINK_PREVIEW_DEBOUNCE_MS)
        previewState = V4LinkPreviewState.Loading
        val metadata = LinkPreviewResolver.resolve(value)
        previewsByUrl[value] = metadata
        previewState = metadata
            ?.let(V4LinkPreviewState::Ready)
            ?: V4LinkPreviewState.Unavailable
        onPreviewChanged(metadata)
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Symbols.Link, contentDescription = null)
                Text(
                    "Link attachment",
                    modifier = Modifier.weight(1f).padding(start = 10.dp),
                    style = MaterialTheme.typography.titleSmall,
                )
                IconButton(onClick = onRemove) {
                    Icon(Symbols.Close, contentDescription = "Remove link")
                }
            }
            OutlinedTextField(
                value = value,
                enabled = !LocalComposerLocked.current,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(bringIntoViewRequester)
                    .focusRequester(focusRequester),
                label = { Text("Destination URL") },
                placeholder = { Text("https:" + "//example.com") },
                isError = validationMessage != null,
                supportingText = validationMessage?.let { message ->
                    { Text(message) }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next,
                ),
            )
            V4CompactLinkPreview(previewState)
        }
    }
}

private sealed interface V4LinkPreviewState {
    data object Idle : V4LinkPreviewState
    data object Loading : V4LinkPreviewState
    data object Unavailable : V4LinkPreviewState
    data class Ready(val metadata: LinkPreviewMetadata) : V4LinkPreviewState
}

@Composable
private fun V4CompactLinkPreview(state: V4LinkPreviewState) {
    when (state) {
        V4LinkPreviewState.Idle -> Unit
        V4LinkPreviewState.Loading -> {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    InlineProgressIndicator()
                    Column {
                        Text(
                            "Finding preview…",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Checking page title and image",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        V4LinkPreviewState.Unavailable -> {
            Text(
                "Preview unavailable · Link can still be posted",
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        is V4LinkPreviewState.Ready -> {
            val metadata = state.metadata
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            "${metadata.displayHost} · Preview found",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            metadata.title ?: "Link preview ready",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (metadata.imageUrl != null) {
                        RemoteImage(
                            uri = metadata.imageUrl,
                            label = "Link preview image",
                            modifier = Modifier
                                .size(width = 96.dp, height = 72.dp)
                                .clip(MaterialTheme.shapes.medium),
                            aspectRatio = null,
                            showFailureDetails = false,
                        )
                    } else {
                        Surface(
                            modifier = Modifier.size(width = 72.dp, height = 64.dp),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Symbols.Link,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun V4MediaSection(
    modifier: Modifier = Modifier,
    media: List<ComposerMediaAttachment>,
    validationMessage: String?,
    videoAsGif: Boolean,
    videoAllowed: Boolean,
    videoGifAllowed: Boolean,
    onVideoAsGifChange: (Boolean) -> Unit,
    onAddPhotos: () -> Unit,
    onReplaceVideo: () -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onCaptionChange: (Int, String) -> Unit,
    onOutboundUrlChange: (Int, String) -> Unit,
) {
    var outboundLinkEditorUri by remember { mutableStateOf<String?>(null) }
    val video = media.singleOrNull()?.takeIf { it.isComposerVideoSource() }
    val pagerState = rememberPagerState(pageCount = { media.size })
    val pagerScope = rememberCoroutineScope()
    val activeIndex = pagerState.currentPage.coerceIn(0, media.lastIndex.coerceAtLeast(0))

    LaunchedEffect(media.size) {
        if (media.isNotEmpty() && pagerState.currentPage > media.lastIndex) {
            pagerState.scrollToPage(media.lastIndex)
        }
    }

    fun moveActive(to: Int) {
        if (activeIndex !in media.indices || to !in media.indices) return
        onMove(activeIndex, to)
        pagerScope.launch { pagerState.animateScrollToPage(to) }
    }

    Surface(
        modifier = modifier.semantics {
            validationMessage?.let { error(it) }
        },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = validationMessage?.let {
            BorderStroke(1.dp, MaterialTheme.colorScheme.error)
        },
    ) {
        Column(
            modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Symbols.PermMedia, contentDescription = null)
                Text(
                    when {
                        media.size > 1 -> "Gallery · ${media.size} items"
                        video != null -> if (videoAsGif) "GIF" else "Video"
                        else -> media.singleOrNull()?.kind?.label ?: "Media"
                    },
                    modifier = Modifier.weight(1f).padding(start = 10.dp),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (video != null) {
                    TextButton(
                        enabled = videoAllowed || videoGifAllowed,
                        onClick = onReplaceVideo,
                    ) {
                        Text("Replace video")
                    }
                } else {
                    TextButton(
                        enabled = media.size < 20,
                        onClick = onAddPhotos,
                    ) {
                        Icon(Symbols.Add, contentDescription = null, Modifier.size(18.dp))
                        Text("Add photos", Modifier.padding(start = 4.dp))
                    }
                }
            }
            if (media.isNotEmpty()) {
                val pageKeys = remember(media) { galleryPageKeys(media.map { item -> item.uri }) }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth().height(230.dp),
                    contentPadding = PaddingValues(horizontal = 32.dp),
                    pageSpacing = 8.dp,
                    key = { index -> pageKeys[index] },
                ) { index ->
                    val item = media[index]
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(MaterialTheme.shapes.medium),
                    ) {
                        if (item.isComposerVideoSource()) {
                            V4MutedVideoPreview(
                                uri = item.uri,
                                contentDescription = "Video ${index + 1} of ${media.size}",
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            LocalUriMedia(
                                uri = item.uri,
                                mimeType = item.mimeType,
                                contentDescription = "${item.kind.label} ${index + 1} of ${media.size}",
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        if (index == activeIndex) {
                            Surface(
                                modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.66f),
                            ) {
                                Text(
                                    "${index + 1} / ${media.size}",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                            Surface(
                                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.66f),
                            ) {
                                // A11Y01A1_COMPOSER_GALLERY_TOUCH_TARGETS_V1
                                IconButton(
                                    onClick = { onRemove(index) },
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(
                                        Symbols.Delete,
                                        // COMPOSER12_LABELLED_SWITCHES_V1: names what is removed.
                                        contentDescription = composerMediaRemoveLabel(item, index),
                                        tint = Color.White,
                                    )
                                }
                            }
                        }
                    }
                }
                if (media.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Reorder",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        IconButton(
                            enabled = activeIndex > 0,
                            onClick = { moveActive(activeIndex - 1) },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                Symbols.ArrowBack,
                                contentDescription = "Move image earlier",
                            )
                        }
                        IconButton(
                            enabled = activeIndex < media.lastIndex,
                            onClick = { moveActive(activeIndex + 1) },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                Symbols.ArrowForward,
                                contentDescription = "Move image later",
                            )
                        }
                    }
                    val activeItem = media.getOrNull(activeIndex)
                    if (activeItem != null) {
                        OutlinedTextField(
                            value = activeItem.caption,
                            enabled = !LocalComposerLocked.current,
                            onValueChange = { value ->
                                if (value.length <= COMPOSER_GALLERY_CAPTION_MAX_LENGTH) {
                                    onCaptionChange(activeIndex, value)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp),
                            label = { Text("Caption (optional)") },
                            supportingText = {
                                Text(
                                    "${activeItem.caption.length} / $COMPOSER_GALLERY_CAPTION_MAX_LENGTH",
                                )
                            },
                            minLines = 2,
                            maxLines = 4,
                        )
                        val outboundLinkExpanded =
                            activeItem.outboundUrl.isNotBlank() ||
                                outboundLinkEditorUri == activeItem.uri
                        if (outboundLinkExpanded) {
                            OutlinedTextField(
                                value = activeItem.outboundUrl,
                                enabled = !LocalComposerLocked.current,
                                onValueChange = { value ->
                                    onOutboundUrlChange(activeIndex, value)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp),
                                label = { Text("Attached link (optional)") },
                                placeholder = { Text("https:" + "//example.com") },
                                supportingText = {
                                    Text("Opens separately from the image")
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Uri,
                                    imeAction = ImeAction.Done,
                                ),
                            )
                        } else {
                            TextButton(
                                onClick = {
                                    outboundLinkEditorUri = activeItem.uri
                                },
                                modifier = Modifier.padding(horizontal = 14.dp),
                            ) {
                                Text("+ Add link")
                            }
                        }
                    }
                }
                if (video != null) {
                    // COMPOSER12_LABELLED_SWITCHES_V1
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = videoAsGif,
                                enabled = if (videoAsGif) videoAllowed else videoGifAllowed,
                                role = Role.Switch,
                                onValueChange = onVideoAsGifChange,
                            )
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                "Post as GIF",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                when {
                                    !videoAllowed && !videoGifAllowed ->
                                        "This community doesn't allow video posts"
                                    !videoGifAllowed ->
                                        "This community doesn't allow GIF-style video posts"
                                    !videoAllowed ->
                                        "This community only allows GIF-style video posts"
                                    else ->
                                        "Publish as Reddit's silent looping GIF-style video"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = videoAsGif,
                            onCheckedChange = null,
                            enabled = if (videoAsGif) videoAllowed else videoGifAllowed,
                        )
                    }
                }
                validationMessage?.let { message ->
                    Text(
                        message,
                        modifier = Modifier.padding(horizontal = 14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun V4MutedVideoPreview(
    uri: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val hostView = LocalView.current
    val lifecycle = remember(hostView) {
        hostView.findViewTreeLifecycleOwner()?.lifecycle
    }
    var playing by remember(uri) { mutableStateOf(true) }
    var failed by remember(uri) { mutableStateOf(false) }
    val previewPlayer = remember(uri, context) {
        ExoPlayer.Builder(context.applicationContext)
            .build()
            .apply {
                volume = 0f
                repeatMode = Player.REPEAT_MODE_ONE
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
                playWhenReady = true
            }
    }

    DisposableEffect(previewPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                failed = true
                playing = false
            }
        }
        previewPlayer.addListener(listener)
        onDispose {
            previewPlayer.removeListener(listener)
            previewPlayer.release()
        }
    }

    LaunchedEffect(previewPlayer, playing) {
        if (
            playing &&
            lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) != false
        ) {
            previewPlayer.play()
        } else {
            previewPlayer.pause()
        }
    }

    DisposableEffect(previewPlayer, lifecycle, playing) {
        val observer = lifecycle?.let { currentLifecycle ->
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START,
                    Lifecycle.Event.ON_RESUME,
                    -> if (playing) previewPlayer.play()

                    Lifecycle.Event.ON_PAUSE,
                    Lifecycle.Event.ON_STOP,
                    -> previewPlayer.pause()

                    else -> Unit
                }
            }.also(currentLifecycle::addObserver)
        }
        onDispose {
            if (observer != null) {
                lifecycle.removeObserver(observer)
            }
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .clickable(
                enabled = !failed,
                onClickLabel = if (playing) {
                    "Pause muted video preview"
                } else {
                    "Play muted video preview"
                },
            ) {
                playing = !playing
            },
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    useController = false
                    player = previewPlayer
                    this.contentDescription = contentDescription
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { playerView ->
                playerView.player = previewPlayer
                playerView.contentDescription = contentDescription
            },
            modifier = Modifier.fillMaxSize(),
        )

        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp),
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.68f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    when {
                        failed -> "Preview unavailable"
                        playing -> "Preview · Tap to pause"
                        else -> "Preview · Tap to play"
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        if (!playing && !failed) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.70f),
            ) {
                Icon(
                    Symbols.PlayArrowFilled,
                    contentDescription = "Play muted video preview",
                    tint = Color.White,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun V4MediaPickerSheet(
    photosEnabled: Boolean,
    videoEnabled: Boolean,
    onPickPhotos: () -> Unit,
    onPickVideo: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Add media", style = MaterialTheme.typography.headlineSmall)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                enabled = photosEnabled,
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                onClick = onPickPhotos,
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Symbols.Image, contentDescription = null)
                    Column(Modifier.padding(start = 14.dp)) {
                        Text("Photos or gallery", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Choose up to 20 images or GIFs",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                enabled = videoEnabled,
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                onClick = onPickVideo,
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Symbols.Movie, contentDescription = null)
                    Column(Modifier.padding(start = 14.dp)) {
                        Text("Video", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Choose one video",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.size(18.dp))
        }
    }
}

@Composable
private fun V4BodyEditor(
    modifier: Modifier = Modifier,
    state: TextFieldState,
    focusRequester: FocusRequester,
    required: Boolean,
    maxLength: Int,
    wide: Boolean,
    compactPreviewVisible: Boolean,
    onPreviewVisibleChange: (Boolean) -> Unit,
    onFormat: (MarkdownAction) -> Unit,
    validationMessage: String?,
    onMediaReceived: (ComposerMediaAttachment) -> Unit,
    inputLocked: Boolean,
    visibleListHeight: () -> Int,
    scrollBodyToTop: suspend () -> Unit,
) {
    val body = state.text.toString()
    // COMPOSER18_BODY_EDIT_BY_DEFAULT_V1: an empty body has nothing to preview.
    val previewShown = compactPreviewVisible && body.isNotBlank()
    Column(
        modifier = modifier.semantics {
            if (validationMessage != null) error(validationMessage)
        },
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        ComposerBodyHeader(
            title = "Body",
            length = body.length,
            maxLength = maxLength,
            previewVisible = previewShown,
            onTogglePreview = if (wide || body.isBlank()) null else {
                { onPreviewVisibleChange(!previewShown) }
            },
            required = required,
        )
        if (wide) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Editor",
                        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    V4BodyCanvas(
                        state = state,
                        modifier = Modifier.fillMaxWidth(),
                        focusRequester = focusRequester,
                        onFormat = onFormat,
                        onMediaReceived = onMediaReceived,
                        inputLocked = inputLocked,
                        visibleListHeight = visibleListHeight,
                        scrollBodyToTop = scrollBodyToTop,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "Preview",
                        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    MarkdownComposerPreview(body, Modifier.fillMaxWidth().heightIn(min = 340.dp))
                }
            }
        } else if (previewShown) {
            MarkdownComposerPreview(body, Modifier.fillMaxWidth().heightIn(min = 340.dp))
        } else {
            V4BodyCanvas(
                state = state,
                modifier = Modifier.fillMaxWidth(),
                focusRequester = focusRequester,
                onFormat = onFormat,
                onMediaReceived = onMediaReceived,
                inputLocked = inputLocked,
                visibleListHeight = visibleListHeight,
                scrollBodyToTop = scrollBodyToTop,
            )
        }
        validationMessage?.let { message ->
            Text(
                message,
                modifier = Modifier.padding(horizontal = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun V4BodyNotAllowed(
    modifier: Modifier,
    body: String,
    enabled: Boolean,
    onRemove: () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text("Body", style = MaterialTheme.typography.titleMedium)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(
                body,
                modifier = Modifier.padding(16.dp),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            COMPOSER_BODY_NOT_ALLOWED_MESSAGE,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        OutlinedButton(onClick = onRemove, enabled = enabled) {
            Text("Remove body text")
        }
    }
}

@Composable
private fun V4BodyCanvas(
    state: TextFieldState,
    modifier: Modifier,
    focusRequester: FocusRequester,
    onFormat: (MarkdownAction) -> Unit,
    onMediaReceived: (ComposerMediaAttachment) -> Unit,
    inputLocked: Boolean,
    visibleListHeight: () -> Int,
    scrollBodyToTop: suspend () -> Unit,
) {
    // COMPOSER17_BODY_FOCUS_REVEAL_V1: a tap in the body moves "Body" to the
    // top of the list once the keyboard is up, so the writing area is as large
    // as it can be (Compose itself only reveals the cursor line). When the
    // cursor is too far down for that, the area around the cursor is shown.
    val revealRequester = remember { BringIntoViewRequester() }
    val layout = remember { BodyRevealLayout() }
    var focusGains by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    LaunchedEffect(focusGains) {
        if (focusGains == 0) return@LaunchedEffect
        delay(BODY_FOCUS_REVEAL_DELAY_MS)
        val textLayout = layout.textLayout?.invoke()
        val cursor = textLayout?.getCursorRect(
            state.selection.start.coerceIn(0, textLayout.layoutInput.text.length),
        )
        val textTop = with(density) { BODY_FIELD_TEXT_TOP_PADDING_DP.dp.toPx() }
        val range = bodyFocusRevealRange(
            fieldHeight = layout.fieldHeight.toFloat(),
            cursorBottom = textTop + (cursor?.bottom ?: 0f),
            lineHeight = cursor?.height ?: with(density) { 24.dp.toPx() },
            visibleHeight = visibleListHeight().toFloat(),
        )
        val headerRoom = with(density) { BODY_HEADER_ROOM_DP.dp.toPx() }
        if (range.start == 0f && range.endInclusive + headerRoom <= visibleListHeight()) {
            scrollBodyToTop()
        } else {
            revealRequester.bringIntoView(Rect(0f, range.start, 1f, range.endInclusive))
        }
    }
    PostStyleComposerEditor(
        state = state,
        placeholder = "Share the details…",
        focusRequester = focusRequester,
        onFormat = onFormat,
        onMediaReceived = onMediaReceived,
        modifier = modifier
            .bringIntoViewRequester(revealRequester)
            .onSizeChanged { layout.fieldHeight = it.height }
            .onFocusChanged { focus ->
                if (focus.hasFocus && !layout.focused) focusGains += 1
                layout.focused = focus.hasFocus
            },
        minLines = 12,
        maxLines = Int.MAX_VALUE,
        inputLocked = inputLocked,
        onTextLayout = { getResult -> layout.textLayout = getResult },
    )
}

// Plain holder: layout callbacks write it without causing recomposition.
private class BodyRevealLayout {
    var textLayout: (() -> TextLayoutResult?)? = null
    var fieldHeight: Int = 0
    var focused: Boolean = false
}

private const val BODY_FOCUS_REVEAL_DELAY_MS = 350L
private const val BODY_FIELD_TEXT_TOP_PADDING_DP = 16
private const val BODY_HEADER_ROOM_DP = 64

/**
 * COMPOSER17_BODY_FOCUS_REVEAL_V1: the part of the body field (field
 * coordinates) to bring into view after a tap: from the field's top to
 * [linesBelow] lines under the cursor. When that is taller than the visible
 * list, the top gives way so the cursor stays in view.
 */
internal fun bodyFocusRevealRange(
    fieldHeight: Float,
    cursorBottom: Float,
    lineHeight: Float,
    visibleHeight: Float,
    linesBelow: Int = 3,
): ClosedFloatingPointRange<Float> {
    val bottom = (cursorBottom + linesBelow * lineHeight)
        .coerceAtMost(fieldHeight.coerceAtLeast(cursorBottom))
    val top = if (visibleHeight > 0f && bottom > visibleHeight) bottom - visibleHeight else 0f
    return top..bottom
}

@Composable
private fun V4AddContentRow(
    linkEnabled: Boolean,
    mediaEnabled: Boolean,
    showMedia: Boolean,
    onAddLink: () -> Unit,
    onAddMedia: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AssistChip(
            enabled = linkEnabled,
            onClick = onAddLink,
            label = { Text("Add link") },
            leadingIcon = {
                Icon(Symbols.AddLink, contentDescription = null, Modifier.size(18.dp))
            },
        )
        if (showMedia) {
            AssistChip(
                enabled = mediaEnabled,
                onClick = onAddMedia,
                label = { Text("Add media") },
                leadingIcon = {
                    Icon(Symbols.PermMedia, contentDescription = null, Modifier.size(18.dp))
                },
            )
        }
    }
}

@Composable
private fun V4PostOptions(
    draft: PostComposerDraft,
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    onNsfwChange: (Boolean) -> Unit,
    onSpoilerChange: (Boolean) -> Unit,
    onRepliesChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column {
            // ISSUE219_EXPANDABLE_ROWS_SAY_THEIR_STATE_V1
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" },
                color = Color.Transparent,
                onClick = onExpandedChange,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Post options", style = MaterialTheme.typography.titleSmall)
                        val active = buildList {
                            if (draft.nsfw) add("NSFW")
                            if (draft.spoiler) add("Spoiler")
                            if (!draft.sendReplies) add("Replies off")
                        }
                        if (active.isNotEmpty()) {
                            Text(
                                active.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Icon(
                        Symbols.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.rotate(if (expanded) 180f else 0f),
                    )
                }
            }
            if (expanded) {
                HorizontalDivider()
                V4OptionSwitch("Mark as NSFW", draft.nsfw, onNsfwChange)
                V4OptionSwitch("Mark as spoiler", draft.spoiler, onSpoilerChange)
                V4OptionSwitch(
                    "Send reply notifications",
                    draft.sendReplies,
                    onRepliesChange,
                )
            }
        }
    }
}

@Composable
private fun V4OptionSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    // COMPOSER12_LABELLED_SWITCHES_V1: the row is the switch, so the label is
    // read with its state and the whole row toggles.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = !LocalComposerLocked.current,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = null, enabled = !LocalComposerLocked.current)
    }
}

private val LocalComposerLocked = compositionLocalOf { false }

// ISSUE33_SHARE_INTO_SUBSHELL_V1: with more than one Reddit account signed
// in, the composer names the one that will post.
internal fun composerPostingAsUsername(state: SubshellState): String? =
    state.activeAccountUsername.takeIf {
        state.activeAccountIsAuthenticated &&
            state.accountSessions.count { session -> session.source == AccountSessionSource.AUTHENTICATED } > 1
    }

internal data class ComposerContentLoss(val title: String, val message: String)

// COMPOSER04_CONFIRM_BEFORE_REPLACING_CONTENT_V1: what a change to the draft
// would remove, or null when it keeps the link and every attachment.
internal fun composerContentLoss(
    current: PostComposerDraft,
    next: PostComposerDraft,
): ComposerContentLoss? {
    val keptUris = next.media.map(ComposerMediaAttachment::uri).toSet()
    val removedMedia = current.media.filterNot { it.uri in keptUris }
    val linkRemoved = current.type == ComposerPostType.LINK &&
        current.linkUrl.isNotBlank() &&
        (next.type != ComposerPostType.LINK || next.linkUrl.isBlank())
    val what = when {
        removedMedia.isNotEmpty() && linkRemoved -> "the link and the attachments"
        linkRemoved -> "the link"
        removedMedia.isEmpty() -> return null
        removedMedia.all { it.isComposerVideoSource() } -> "the video"
        removedMedia.size == 1 && removedMedia.single().kind == ComposerMediaKind.GIF -> "the GIF"
        removedMedia.size == 1 -> "the image"
        removedMedia.none { it.isComposerVideoSource() } -> "${removedMedia.size} images"
        else -> "${removedMedia.size} attachments"
    }
    return ComposerContentLoss(
        title = "Remove $what?",
        message = "A post can't have both. ${what.replaceFirstChar(Char::uppercase)} " +
            "will be removed from this draft.",
    )
}

// COMPOSER08_PASTED_MEDIA_DISTINCT_V1: an image pasted twice is attached once
// (the gallery pager keys on the URI, and Reddit wants distinct items); a
// pasted image replaces a video, and a gallery keeps at most 20 items.
internal fun composerMediaWithPasted(
    current: List<ComposerMediaAttachment>,
    received: ComposerMediaAttachment,
): List<ComposerMediaAttachment> = if (current.any { it.isComposerVideoSource() }) {
    listOf(received)
} else {
    (current + received).distinctBy(ComposerMediaAttachment::uri).take(20)
}

// COMPOSER12_LABELLED_SWITCHES_V1: the remove action names the attachment.
internal fun composerMediaRemoveLabel(item: ComposerMediaAttachment, index: Int): String = when {
    item.isComposerVideoSource() -> "Remove video"
    item.kind == ComposerMediaKind.GIF -> "Remove GIF ${index + 1}"
    else -> "Remove image ${index + 1}"
}

// COMPOSER13_BODY_SYNC_ECHO_V1
internal class ComposerBodyEcho(var lastWritten: String)

// The body the editor must take from the draft, or null: equal to the editor,
// or the editor's own last write coming back while the user kept typing.
internal fun composerBodyToApply(
    draftBody: String,
    editorBody: String,
    lastWrittenByEditor: String,
): String? = draftBody.takeIf { body -> body != editorBody && body != lastWrittenByEditor }

// COMPOSER15_PREFLIGHT_SURVIVES_PROCESS_DEATH_V1: one token per process.
internal val ComposerProcessToken: String = java.util.UUID.randomUUID().toString()

internal fun restoredPreflightMinimumRevision(
    savedProcessToken: String,
    currentProcessToken: String,
    savedMinimumRevision: Long,
): Long = if (savedProcessToken == currentProcessToken) savedMinimumRevision else 0L

internal const val COMPOSER_REQUEST_UNBUILDABLE_MESSAGE =
    "This post can't be sent as it is. Check its link and attachments."

// LOWUI1_GALLERY_PAGE_KEYS_UNIQUE_V1 (OCB-315): a draft saved before COMPOSER08
// can hold the same image twice; a repeat gets its own key instead of crashing
// the pager. Keys of distinct images stay their URI.
internal fun galleryPageKeys(uris: List<String>): List<String> {
    val seen = mutableMapOf<String, Int>()
    return uris.map { uri ->
        val repeat = seen.getOrDefault(uri, 0)
        seen[uri] = repeat + 1
        if (repeat == 0) uri else "$uri#$repeat"
    }
}

/** A title is one line: pasted line breaks become single spaces (OCB-206). */
internal fun composerTitleOnOneLine(value: String): String =
    value.replace(Regex("[\r\n]+"), " ")

private const val GALLERY_FULL_NOTICE_SCOPE = "composer-gallery-full"

private fun ViewportErrorHostState.presentGalleryFull(leftOut: Int) {
    clear(GALLERY_FULL_NOTICE_SCOPE)
    present(
        scopeKey = GALLERY_FULL_NOTICE_SCOPE,
        presentation = ErrorPresentation(
            kind = ErrorPresentationKind.DEGRADED,
            title = "A gallery holds 20 photos",
            message = if (leftOut == 1) "1 photo was left out." else "$leftOut photos were left out.",
        ),
        failureDetails = null,
        onRetry = null,
    )
}


// M3225_TITLE_COUNTER_AND_ERRORS_ON_THE_FIELD_V1 (#225, settings-06/07): the
// title's error and its counter (from 80 % of the limit, COMPOSE03) sit in the
// field's supporting row, so they are read with the field and drive its error
// state; Create post and Crosspost share it.
internal fun composerTitleSupportingText(
    message: String?,
    length: Int,
    maxLength: Int,
): (@Composable () -> Unit)? {
    val showCounter = length >= maxLength * 4 / 5
    if (message == null && !showCounter) return null
    return {
        Row(Modifier.fillMaxWidth()) {
            Text(message.orEmpty(), modifier = Modifier.weight(1f))
            if (showCounter) {
                Text("$length / $maxLength", modifier = Modifier.padding(start = 16.dp))
            }
        }
    }
}
