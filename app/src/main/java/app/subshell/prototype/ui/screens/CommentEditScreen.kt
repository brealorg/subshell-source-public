package app.subshell.prototype.ui.screens

import androidx.compose.ui.input.nestedscroll.nestedScroll
import app.subshell.prototype.data.account.COMMENT_EDIT_TEXT_MAX_CHARS
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fitInside
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.WindowInsetsRulers
import androidx.compose.ui.unit.dp
import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.data.account.CommentEditFailureKind
import app.subshell.prototype.data.account.CommentEditPhase
import app.subshell.prototype.data.account.CommentEditRequest
import app.subshell.prototype.data.account.CommentEditUiState
import app.subshell.prototype.data.account.normalizeCommentEditFullname
import app.subshell.prototype.model.redditSourceMarkdown
import app.subshell.prototype.model.Comment
import app.subshell.prototype.model.Post
import app.subshell.prototype.ui.ChromeBehavior
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.components.RouteTopBar
import app.subshell.prototype.ui.components.rememberRouteTopBarScrollBehavior
import app.subshell.prototype.ui.error.ErrorActionKind
import app.subshell.prototype.ui.error.commentEditErrorPresentation
import app.subshell.prototype.ui.components.ErrorNotice
import androidx.compose.ui.text.TextRange
import app.subshell.prototype.ui.MarkdownAction
import app.subshell.prototype.ui.applyMarkdownAction
import app.subshell.prototype.ui.components.ComposerBodyHeader
import app.subshell.prototype.ui.components.ComposerFormattingDock
import app.subshell.prototype.ui.components.MarkdownComposerPreview
import app.subshell.prototype.ui.components.PostStyleComposerEditor
import app.subshell.prototype.ui.components.ComposerPrimaryAction
import app.subshell.prototype.ui.components.ComposerContextNote
import app.subshell.prototype.ui.components.ComposerDiscardChangesDialog
import app.subshell.prototype.ui.components.editHasUnsavedChanges

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CommentEditScreen(
    post: Post,
    comment: Comment,
    state: SubshellState,
    commentSort: RedditCommentSort,
    editState: CommentEditUiState,
    onSubmitEdit: (CommentEditRequest) -> Unit,
    onAuthorizeEdit: () -> Unit,
    onCancelAuthorization: () -> Unit,
    onResetEditState: () -> Unit,
    onSaved: () -> Unit,
    onCheckAgain: () -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val commentFullname = remember(comment.id) {
        runCatching { normalizeCommentEditFullname(comment.id) }.getOrNull()
    } ?: return
    // ISSUE8_INLINE_REDDIT_MEDIA_V1: the editor holds Reddit's own text, so
    // images, Giphy and emotes go back exactly as Reddit wrote them.
    val sourceBody = remember(comment.body) { comment.body.redditSourceMarkdown() }
    val editor = rememberTextFieldState(initialText = sourceBody)
    // COMPOSE01A1_SHARED_EDITOR_V1: the same field, dock and preview as the
    // post composer; no separate plain/markdown mode.
    var previewVisible by rememberSaveable(comment.id) { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    fun applyFormat(action: MarkdownAction) {
        val edit = applyMarkdownAction(
            text = editor.text.toString(),
            selectionStart = editor.selection.start,
            selectionEnd = editor.selection.end,
            action = action,
        )
        editor.edit {
            replace(0, length, edit.text)
            selection = TextRange(edit.selectionStart, edit.selectionEnd)
        }
    }
    val relevantState = editState.takeIf { candidate ->
        candidate.matches(state.activeAccountId, commentFullname)
    } ?: CommentEditUiState(
        completionRevision = editState.completionRevision,
    )
    // ISSUE92_COMMENT_EDIT_LOCKED_WHILE_SAVING_V1: the request holds the text
    // from Save; what was typed after it was lost when the editor closed on
    // success. Same lock as the post editor (POSTEDIT02).
    val inputLocked = relevantState.inProgress
    fun formatUnlessLocked(action: MarkdownAction) {
        if (!inputLocked) applyFormat(action)
    }
    val body = editor.text.toString()
    // UIPOST03: over the limit Save used to stay enabled and silently do nothing.
    val saveEnabled = commentEditSaveEnabled(
        body = body,
        originalBody = sourceBody,
        idle = relevantState.phase == CommentEditPhase.IDLE,
    )

    fun request(): CommentEditRequest? = runCatching {
        CommentEditRequest(
            accountStableId = state.activeAccountId,
            postId = post.id,
            commentFullname = commentFullname,
            commentAuthor = comment.author,
            text = body,
            sort = commentSort,
        )
    }.getOrNull()

    fun submit() {
        request()?.let(onSubmitEdit)
    }

    // SUBSHELL_COMPOSE05_EDIT_DISCARD_CONFIRM_V1: closing with unsaved edits
    // asks first; there is no draft to fall back on.
    var discardConfirmVisible by rememberSaveable(comment.id) { mutableStateOf(false) }
    fun requestClose() {
        if (editHasUnsavedChanges(sourceBody, body)) discardConfirmVisible = true else onClose()
    }
    BackHandler(onBack = ::requestClose)
    if (discardConfirmVisible) {
        ComposerDiscardChangesDialog(
            subject = "comment",
            onKeepEditing = { discardConfirmVisible = false },
            onDiscard = {
                discardConfirmVisible = false
                onClose()
            },
        )
    }

    LaunchedEffect(commentFullname) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(relevantState.completionRevision, relevantState.phase) {
        if (relevantState.phase == CommentEditPhase.SUCCEEDED) {
            onSaved()
        }
    }

    val routeTopBarScroll = rememberRouteTopBarScrollBehavior(ChromeBehavior.ALWAYS_SHOW)
    Scaffold(
        modifier = modifier.fillMaxSize()
            .nestedScroll(routeTopBarScroll.nestedScrollConnection),
        topBar = {
            RouteTopBar(
                scrollBehavior = routeTopBarScroll,
                title = { Text("Edit comment") },
                navigationIcon = {
                    IconButton(onClick = ::requestClose) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Close comment editor",
                        )
                    }
                },
                actions = {
                    ComposerPrimaryAction(
                        text = when (relevantState.phase) {
                            CommentEditPhase.SENDING -> "Saving…"
                            CommentEditPhase.CHECKING -> "Checking…"
                            CommentEditPhase.AUTHORIZING,
                            CommentEditPhase.AWAITING_CALLBACK,
                            -> "Authorizing…"
                            else -> "Save"
                        },
                        enabled = saveEnabled,
                        onClick = ::submit,
                    )
                },
                // The host (PostScreen) already applies the safe-area padding;
                // the default statusBars inset would stack on top of it, as
                // PostEditScreen's top bar also avoids.
            )
        },
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .consumeWindowInsets(scaffoldPadding)
                .fitInside(WindowInsetsRulers.Ime.current),
        ) {
            ComposerContextNote(
                text = "Editing your comment in r/${post.community}",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )

            CommentEditStatus(
                state = relevantState,
                onGrantAccess = onAuthorizeEdit,
                onRetry = ::submit,
                onCheckAgain = onCheckAgain,
                onCancelAuthorization = onCancelAuthorization,
                onDismiss = onResetEditState,
            )

            ComposerBodyHeader(
                title = "Comment",
                length = body.length,
                maxLength = 10_000,
                previewVisible = previewVisible,
                onTogglePreview = { previewVisible = !previewVisible },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (previewVisible) {
                MarkdownComposerPreview(
                    text = body,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            } else {
                PostStyleComposerEditor(
                    state = editor,
                    placeholder = "Add a comment",
                    focusRequester = focusRequester,
                    onFormat = ::formatUnlessLocked,
                    onMediaReceived = {},
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    minLines = 6,
                    maxLines = Int.MAX_VALUE,
                    inputLocked = inputLocked,
                )
            }
            ComposerFormattingDock(
                state = editor,
                onFormat = ::formatUnlessLocked,
                enabled = !inputLocked,
                onReturnToEditor = {
                    previewVisible = false
                    focusRequester.requestFocus()
                },
            )
        }
    }
}

@Composable
private fun CommentEditStatus(
    state: CommentEditUiState,
    onGrantAccess: () -> Unit,
    onRetry: () -> Unit,
    onCheckAgain: () -> Unit,
    onCancelAuthorization: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (state.phase == CommentEditPhase.FAILED) {
        ErrorNotice(
            presentation = commentEditErrorPresentation(state),
            diagnosticScopeKey = "comments_edit",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            onAction = { action ->
                when (action) {
                    // H1_COMMENT_EDIT_CHECK_AGAIN_READS_ONLY_V1
                    ErrorActionKind.RETRY ->
                        if (state.failureKind == CommentEditFailureKind.OUTCOME_UNKNOWN) onCheckAgain() else onRetry()
                    ErrorActionKind.DISMISS -> onDismiss()
                    else -> Unit
                }
            },
        )
        return
    }
    val message = commentEditStatusMessage(state) ?: return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row {
            when (state.phase) {
                CommentEditPhase.AUTHORIZATION_REQUIRED -> {
                    TextButton(onClick = onGrantAccess) {
                        Text("Grant edit access")
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Not now")
                    }
                }
                CommentEditPhase.AUTHORIZING,
                CommentEditPhase.AWAITING_CALLBACK,
                -> TextButton(onClick = onCancelAuthorization) {
                    Text("Cancel authorization")
                }
                // LOWUI4_UNREACHABLE_FAILED_ROW_REMOVED_V1 (OCB-179): FAILED is
                // shown by the ErrorNotice above, which returns first.
                else -> Unit
            }
        }
    }
}

internal fun commentEditStatusMessage(state: CommentEditUiState): String? =
    when (state.phase) {
        CommentEditPhase.IDLE,
        CommentEditPhase.SUCCEEDED,
        -> null
        CommentEditPhase.AUTHORIZATION_REQUIRED ->
            "Edit access is required for this account."
        CommentEditPhase.AUTHORIZING ->
            "Preparing Reddit authorization…"
        CommentEditPhase.AWAITING_CALLBACK ->
            "Waiting for Reddit authorization…"
        CommentEditPhase.SENDING ->
            "Saving comment…"
        CommentEditPhase.CHECKING ->
            "Checking Reddit for your edit…"
        CommentEditPhase.FAILED -> when (state.failureKind) {
            CommentEditFailureKind.CONFIGURATION ->
                "Comment editing is not configured for this account."
            CommentEditFailureKind.ACCOUNT_CHANGED ->
                "The active account changed. Reopen the comment before editing."
            CommentEditFailureKind.NOT_OWNER ->
                "Only comments from the active account can be edited."
            CommentEditFailureKind.BROWSER_LAUNCH ->
                "Reddit authorization could not be opened."
            CommentEditFailureKind.CALLBACK ->
                "Reddit authorization could not be completed."
            CommentEditFailureKind.AUTHORIZATION ->
                "Edit access was not granted."
            CommentEditFailureKind.SESSION ->
                "The Reddit session could not be refreshed."
            CommentEditFailureKind.RATE_LIMIT -> state.retryAfterMs
                ?.let { delayMs ->
                    "Reddit asked you to wait about ${((delayMs + 999L) / 1_000L)} seconds."
                }
                ?: "Reddit asked you to wait before editing again."
            CommentEditFailureKind.VALIDATION ->
                "The edited comment is invalid."
            CommentEditFailureKind.API ->
                "Reddit rejected the edit."
            CommentEditFailureKind.HTTP,
            CommentEditFailureKind.TRANSPORT,
            -> "The edited comment could not be sent."
            CommentEditFailureKind.ALREADY_EDITING ->
                "This comment is already being edited."
            CommentEditFailureKind.OUTCOME_UNKNOWN ->
                "The edit may have been saved."
            CommentEditFailureKind.NOT_SAVED ->
                "The edit wasn't saved."
            null -> "The comment could not be edited."
        }
    }

internal fun commentEditSaveEnabled(body: String, originalBody: String, idle: Boolean): Boolean =
    body.isNotBlank() &&
        body.length <= COMMENT_EDIT_TEXT_MAX_CHARS &&
        body.trim() != originalBody.trim() &&
        idle
