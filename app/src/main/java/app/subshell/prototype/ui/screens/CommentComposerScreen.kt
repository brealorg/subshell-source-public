package app.subshell.prototype.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalContext
import app.subshell.prototype.ui.ScreenMargin
import app.subshell.prototype.ui.components.pastedImageKeptInApp
import androidx.compose.ui.Alignment
import app.subshell.prototype.ui.commentDraftBelongsToAnotherAccount
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.data.account.CommentWriteFailureKind
import app.subshell.prototype.data.account.CommentWriteMediaAttachment
import app.subshell.prototype.data.account.CommentWritePhase
import app.subshell.prototype.data.account.CommentWriteRequest
import app.subshell.prototype.data.account.CommentWriteUiState
import app.subshell.prototype.data.account.RedditPeopleSearchUiState
import app.subshell.prototype.model.Comment
import app.subshell.prototype.model.Post
import app.subshell.prototype.ui.symbols.Symbols
import app.subshell.prototype.ui.threadMentionCandidates
import app.subshell.prototype.ui.components.ComposerMentions
import app.subshell.prototype.ui.CommentComposerDraft
import app.subshell.prototype.ui.ComposerMediaAttachment
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.ComposerMediaKind
import app.subshell.prototype.ui.commentMediaPolicyKey
import app.subshell.prototype.ui.CommentMediaPolicy
import app.subshell.prototype.ui.composerActionsAtStart
import app.subshell.prototype.ui.error.ErrorActionKind
import app.subshell.prototype.ui.error.commentWriteErrorPresentation
import app.subshell.prototype.ui.components.CompactReplyComposer
import app.subshell.prototype.ui.components.ComposerMediaReplacementDialog
import app.subshell.prototype.ui.components.ErrorNotice
import app.subshell.prototype.ui.components.rememberComposerMediaPicker
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CommentComposerScreen(
    post: Post,
    state: SubshellState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    commentSort: RedditCommentSort = RedditCommentSort.BEST,
    commentWriteState: CommentWriteUiState = CommentWriteUiState(),
    onSubmitComment: (CommentWriteRequest) -> Unit = {},
    onAuthorizeCommentWrite: () -> Unit = {},
    onCancelCommentWriteAuthorization: () -> Unit = {},
    onResetCommentWriteState: () -> Unit = {},
    commentMediaPolicies: Map<String, CommentMediaPolicy> = emptyMap(),
    onLoadCommentMediaPolicy: (String, String) -> Unit = { _, _ -> },
    threadComments: List<Comment> = emptyList(),
    mentionSearchState: RedditPeopleSearchUiState = RedditPeopleSearchUiState(),
    onSearchMentions: (String) -> Unit = {},
) {
    val draft = state.commentComposerDraft ?: return
    val target = draft.target
    if (target.postId != post.id) return

    // COMPOSER01_REPLY_TEXT_KEYED_ON_DRAFT_V1: "Discard and reply" swaps the
    // draft without leaving composition; the text field belongs to one
    // target and account, so a discarded text never follows to the new one.
    val reply = key(target.stableKey, draft.accountStableId) {
        rememberTextFieldState(initialText = draft.body)
    }
    var pendingMediaReplacement by remember(target.stableKey) {
        mutableStateOf<ComposerMediaAttachment?>(null)
    }
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val composerActionsAtStart = state.interactionSide.composerActionsAtStart()
    val media = draft.media
    val authenticated = state.activeAccountIsAuthenticated
    // UIPOST02: a draft written with another account is never sent from this one.
    val writtenWithAnotherAccount = commentDraftBelongsToAnotherAccount(draft, state.activeAccountId)
    val relevantWriteState = commentWriteState.takeIf { candidate ->
        candidate.matches(state.activeAccountId, target.stableKey)
    } ?: CommentWriteUiState(
        completionRevision = commentWriteState.completionRevision,
    )
    // COMPOSER10_COMMENT_MEDIA_RULES_V1: the community's comment media rules;
    // unknown rules allow everything (Reddit decides, as before).
    LaunchedEffect(authenticated, state.activeAccountId, post.community) {
        if (authenticated) onLoadCommentMediaPolicy(state.activeAccountId, post.community)
    }
    val mediaPolicy = commentMediaPolicies[commentMediaPolicyKey(state.activeAccountId, post.community)]
        .takeIf { authenticated }
    val mediaBlockedMessage = media?.let { attached -> commentMediaNotAllowedMessage(attached, mediaPolicy) }
        .takeIf { authenticated }
    val remoteMediaBlocked = mediaBlockedMessage != null
    // ISSUE229_MENTION_AUTOCOMPLETE_V1: the thread's people first, then the
    // active account's own people search (not for a draft from another account).
    val ownUsername = state.activeAccountUsername
    val mentionThread = remember(post, threadComments, target.parentAuthor, ownUsername) {
        threadMentionCandidates(post, threadComments, target.parentAuthor, ownUsername)
    }
    val mentions = ComposerMentions(
        thread = mentionThread,
        remote = mentionSearchState.takeIf { it.accountStableId == state.activeAccountId }
            ?: RedditPeopleSearchUiState(),
        searchEnabled = authenticated && !writtenWithAnotherAccount,
        onSearch = onSearchMentions,
        avatarFor = { author -> commentRowAvatarUri(state, author) },
    )
    val remoteTargetUnavailable = authenticated && target.parentFullname == null
    val remotePostEnabled = authenticated &&
        !writtenWithAnotherAccount &&
        !remoteMediaBlocked &&
        !remoteTargetUnavailable &&
        (reply.text.isNotBlank() || media != null) &&
        relevantWriteState.phase == CommentWritePhase.IDLE
    // COMPOSER01_INPUT_LOCKED_WHILE_SENDING_V1: from Send until the result the
    // text is part of the request; edits made then were cleared on success.
    // ISSUE91_LOCKED_WHILE_OUTCOME_UNKNOWN_V1: also while the outcome is
    // unknown, so "Check again" rebuilds the same comment and reads it back;
    // an edited text used to be sent as a new comment.
    val requestInFlight = authenticated && relevantWriteState.phase in setOf(
        CommentWritePhase.AUTHORIZATION_REQUIRED,
        CommentWritePhase.AUTHORIZING,
        CommentWritePhase.AWAITING_CALLBACK,
        CommentWritePhase.SENDING,
        CommentWritePhase.SUCCEEDED,
        CommentWritePhase.OUTCOME_UNKNOWN,
    )
    val localPostEnabled = !authenticated &&
        !writtenWithAnotherAccount &&
        (reply.text.isNotBlank() || media != null)

    fun updateDraft(transform: (CommentComposerDraft) -> CommentComposerDraft) {
        val current = state.commentComposerDraft
            ?.takeIf { candidate -> candidate.target.stableKey == target.stableKey }
            ?: return
        state.updateCommentComposerDraft(transform(current))
    }

    fun updateMedia(next: ComposerMediaAttachment?) {
        updateDraft { current -> current.copy(media = next) }
    }

    fun requestMedia(next: ComposerMediaAttachment) {
        if (requestInFlight) return
        if (media == null) {
            updateMedia(next)
        } else {
            pendingMediaReplacement = next
        }
    }

    var requestUnbuildable by remember(target.stableKey, media) { mutableStateOf(false) }

    fun remoteRequest(): CommentWriteRequest? {
        val parentFullname = target.parentFullname ?: return null
        val body = reply.text.toString()
        return runCatching {
            CommentWriteRequest(
                accountStableId = state.activeAccountId,
                postId = post.id,
                targetStableKey = target.stableKey,
                parentFullname = parentFullname,
                text = body,
                sort = commentSort,
                media = media?.let { attachment ->
                    CommentWriteMediaAttachment(
                        uri = attachment.uri,
                        mimeType = attachment.mimeType,
                        sourceLinkUri = attachment.sourceLinkUri,
                    )
                },
            )
        }.getOrNull()
    }

    fun submitRemote() {
        updateDraft { current -> current.copy(body = reply.text.toString()) }
        // ISSUE90_SENDABLE_IMAGE_V1: Send used to do nothing at all when the
        // request could not be built.
        val request = remoteRequest()
        requestUnbuildable = request == null && target.parentFullname != null
        request?.let(onSubmitComment)
    }

    val mediaPicker = rememberComposerMediaPicker(::requestMedia)

    BackHandler(onBack = onClose)
    LaunchedEffect(target.stableKey) {
        focusRequester.requestFocus()
    }
    LaunchedEffect(reply, target.stableKey, state) {
        snapshotFlow { reply.text.toString() }
            .collectLatest { body ->
                val current = state.commentComposerDraft
                    ?.takeIf { candidate -> candidate.target.stableKey == target.stableKey }
                    ?: return@collectLatest
                if (current.body != body) {
                    state.updateCommentComposerDraft(current.copy(body = body))
                }
            }
    }

    // COMPOSER232_COMPOSER_SITS_ON_THE_KEYBOARD_V1: PostScreen fits this
    // composer above the keyboard; it adds no keyboard padding of its own.
    Surface(
        modifier = modifier.fillMaxWidth(),
        // M3225_DOCKED_BARS_ARE_FLAT_SURFACE_CONTAINERS_V1 (#225, components-13,
        // settings-15): a docked bar is surfaceContainer and flat, as M3's
        // bottom app bar, not the legacy background role with a 10 dp shadow.
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (writtenWithAnotherAccount) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "This draft was written with another account. Switch back to it to send, or discard it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            state.discardCommentComposerDraft()
                            onClose()
                        },
                    ) {
                        Text("Discard")
                    }
                }
            }
            CommentWriteStatus(
                authenticated = authenticated,
                mediaBlocked = remoteMediaBlocked,
                mediaBlockedMessage = mediaBlockedMessage,
                requestUnbuildable = requestUnbuildable,
                targetUnavailable = remoteTargetUnavailable,
                writeState = relevantWriteState,
                onGrantAccess = onAuthorizeCommentWrite,
                onRetry = ::submitRemote,
                onCancelAuthorization = onCancelCommentWriteAuthorization,
                onDismiss = onResetCommentWriteState,
            )

            CompactReplyComposer(
                state = reply,
                media = media,
                placeholder = if (target.isPostTarget) "Add a comment" else "Reply",
                sendEnabled = if (authenticated) remotePostEnabled else localPostEnabled,
                onSend = {
                    updateDraft { current -> current.copy(body = reply.text.toString()) }
                    if (authenticated) {
                        submitRemote()
                    } else if (state.publishLocalCommentComposer(post) != null) {
                        onClose()
                    }
                },
                // COMPOSER16_PASTED_IMAGE_COPIED_V1
                onMediaReceived = { pasted -> requestMedia(pastedImageKeptInApp(context, pasted)) },
                onMediaRemoved = { updateMedia(null) },
                onPickImage = mediaPicker.pickImage,
                onPickGif = mediaPicker.pickGif,
                mediaEnabled = true,
                imageEnabled = mediaPolicy?.imagesAllowed ?: true,
                gifEnabled = mediaPolicy?.gifsAllowed ?: true,
                interactionActionsAtStart = composerActionsAtStart,
                inputLocked = requestInFlight,
                contextLabel = if (target.isPostTarget) {
                    "Commenting on r/${post.community} · ${post.title}"
                } else {
                    "Replying to u/${target.parentAuthor ?: "unknown"} · ${post.title}"
                },
                onDismissContext = onClose,
                focusRequester = focusRequester,
                maxCharacters = 10_000,
                sendContentDescription = if (target.isPostTarget) "Post comment" else "Post reply",
                mentions = mentions,
                longReplyTitle = if (target.isPostTarget) {
                    "Comment on r/${post.community}"
                } else {
                    "Reply to u/${target.parentAuthor ?: "unknown"}"
                },
                quote = if (target.isPostTarget) {
                    post.title
                } else {
                    threadComments.findComment(target.parentCommentId)?.body
                },
                sendLabel = "Post",
            )
        }
    }

    val replacement = pendingMediaReplacement
    if (replacement != null && media != null) {
        ComposerMediaReplacementDialog(
            currentMedia = media,
            replacementMedia = replacement,
            onReplace = {
                pendingMediaReplacement = null
                updateMedia(replacement)
            },
            onKeepExisting = { pendingMediaReplacement = null },
        )
    }
}

@Composable
internal fun CommentComposerLauncher(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // M3223_COMMENT_BAR_HAS_A_BACKING_V1 (#223): the pill sits on a strip of the
    // screen's own colour, so comment text no longer shows around it.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background),
    ) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenMargin, vertical = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 4.dp,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(
                text = "Add a comment",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
            Icon(
                Symbols.Send,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    }
}

@Composable
private fun CommentWriteStatus(
    authenticated: Boolean,
    mediaBlocked: Boolean,
    mediaBlockedMessage: String? = null,
    requestUnbuildable: Boolean = false,
    targetUnavailable: Boolean,
    writeState: CommentWriteUiState,
    onGrantAccess: () -> Unit,
    onRetry: () -> Unit,
    onCancelAuthorization: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (
        authenticated &&
        !mediaBlocked &&
        !targetUnavailable &&
        writeState.phase in setOf(
            CommentWritePhase.FAILED,
            CommentWritePhase.OUTCOME_UNKNOWN,
        )
    ) {
        ErrorNotice(
            presentation = commentWriteErrorPresentation(writeState),
            diagnosticScopeKey = "comments_write",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            onAction = { action ->
                when (action) {
                    ErrorActionKind.RETRY -> onRetry()
                    ErrorActionKind.DISMISS -> onDismiss()
                    else -> Unit
                }
            },
        )
        return
    }

    val message = when {
        !authenticated -> null
        mediaBlocked -> mediaBlockedMessage
        requestUnbuildable -> COMMENT_REQUEST_UNBUILDABLE_MESSAGE
        targetUnavailable ->
            "This comment target is unavailable."
        else -> commentWriteStatusMessage(writeState)
    } ?: return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = if (
                writeState.phase in setOf(
                    CommentWritePhase.FAILED,
                    CommentWritePhase.OUTCOME_UNKNOWN,
                ) || mediaBlocked || requestUnbuildable || targetUnavailable
            ) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Row {
            when (writeState.phase) {
                CommentWritePhase.AUTHORIZATION_REQUIRED -> {
                    TextButton(onClick = onGrantAccess) {
                        Text("Grant comment access")
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Not now")
                    }
                }
                CommentWritePhase.AUTHORIZING,
                CommentWritePhase.AWAITING_CALLBACK,
                -> TextButton(onClick = onCancelAuthorization) {
                    Text("Cancel authorization")
                }
                CommentWritePhase.FAILED -> {
                    if (writeState.retryable) {
                        TextButton(onClick = onRetry) {
                            Text("Try again")
                        }
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Dismiss")
                    }
                }
                CommentWritePhase.OUTCOME_UNKNOWN -> TextButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
                else -> Unit
            }
        }
    }
}

internal fun commentWriteStatusMessage(state: CommentWriteUiState): String? =
    when (state.phase) {
        CommentWritePhase.IDLE,
        CommentWritePhase.SUCCEEDED,
        -> null
        CommentWritePhase.AUTHORIZATION_REQUIRED ->
            "Comment access is required for this account."
        CommentWritePhase.AUTHORIZING ->
            "Preparing Reddit authorization…"
        CommentWritePhase.AWAITING_CALLBACK ->
            "Waiting for Reddit authorization…"
        CommentWritePhase.SENDING ->
            "Posting comment…"
        CommentWritePhase.OUTCOME_UNKNOWN ->
            "Reddit may have received this comment. Check the thread before trying again to avoid a duplicate."
        CommentWritePhase.FAILED -> commentWriteFailureMessage(state)
    }

private fun commentWriteFailureMessage(state: CommentWriteUiState): String =
    when (state.failureKind) {
        CommentWriteFailureKind.CONFIGURATION ->
            "Comment posting is not configured for this account."
        CommentWriteFailureKind.ACCOUNT_CHANGED ->
            "The active account changed. Review the draft before posting."
        CommentWriteFailureKind.BROWSER_LAUNCH ->
            "Reddit authorization could not be opened."
        CommentWriteFailureKind.CALLBACK ->
            "Reddit authorization could not be completed."
        CommentWriteFailureKind.AUTHORIZATION ->
            "Comment access was not granted."
        CommentWriteFailureKind.SESSION ->
            "The Reddit session could not be refreshed."
        CommentWriteFailureKind.RATE_LIMIT -> state.retryAfterMs
            ?.let { delayMs ->
                "Reddit asked you to wait about ${((delayMs + 999L) / 1_000L)} seconds."
            }
            ?: "Reddit asked you to wait before posting again."
        CommentWriteFailureKind.PARENT_UNAVAILABLE ->
            "This thread can no longer accept replies."
        CommentWriteFailureKind.PERMISSION ->
            "This account cannot comment in this community."
        CommentWriteFailureKind.VALIDATION ->
            "The comment could not be posted. Review the text and try again."
        CommentWriteFailureKind.NOT_POSTED ->
            "Reddit doesn't show this comment. You can send it again."
        CommentWriteFailureKind.MEDIA_NOT_ALLOWED ->
            "This community doesn't allow this kind of image in comments. Remove it to send the text."
        CommentWriteFailureKind.MEDIA_TOO_LARGE ->
            "This image is too large to post. Choose a smaller one."
        CommentWriteFailureKind.MEDIA_UNSUPPORTED ->
            "This image type can't be posted. Choose a JPEG, PNG, WebP, or GIF."
        CommentWriteFailureKind.MEDIA_UNAVAILABLE ->
            "This image can no longer be read. Choose it again."
        CommentWriteFailureKind.API ->
            "Reddit rejected the comment."
        CommentWriteFailureKind.HTTP,
        CommentWriteFailureKind.TRANSPORT,
        -> "The comment could not be sent."
        CommentWriteFailureKind.ALREADY_SUBMITTING ->
            "This comment is already being posted."
        null -> "The comment could not be posted."
    }

// COMPOSER10_COMMENT_MEDIA_RULES_V1: why an attached image/GIF cannot be sent
// to this community, or null when it can (or the rules are unknown).
internal fun commentMediaNotAllowedMessage(
    media: ComposerMediaAttachment,
    policy: CommentMediaPolicy?,
): String? {
    policy ?: return null
    val gif = media.kind == ComposerMediaKind.GIF
    val allowed = if (gif) policy.gifsAllowed else policy.imagesAllowed
    if (allowed) return null
    val noun = if (gif) "GIFs" else "images"
    return "This community doesn't allow $noun in comments. Remove it to send the text."
}

internal const val COMMENT_REQUEST_UNBUILDABLE_MESSAGE =
    "This reply can't be sent as it is. Check its text and image."

private fun List<Comment>.findComment(id: String?): Comment? {
    if (id == null) return null
    for (comment in this) {
        if (comment.id == id) return comment
        comment.children.findComment(id)?.let { return it }
    }
    return null
}
