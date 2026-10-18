package app.subshell.prototype.ui.screens

import app.subshell.prototype.ui.components.postBodyMentions
import app.subshell.prototype.ui.components.MentionSuggestions
import app.subshell.prototype.data.account.RedditPeopleSearchUiState
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import app.subshell.prototype.data.account.POST_EDIT_TEXT_MAX_CHARS
import app.subshell.prototype.data.account.PostEditFailureKind
import app.subshell.prototype.data.account.PostEditPhase
import app.subshell.prototype.data.account.PostEditRequest
import app.subshell.prototype.data.account.PostEditUiState
import app.subshell.prototype.data.account.normalizePostEditFullname
import app.subshell.prototype.model.redditSourceMarkdown
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.ChromeBehavior
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.components.ErrorNotice
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
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
import app.subshell.prototype.ui.components.RouteTopBar
import app.subshell.prototype.ui.components.editHasUnsavedChanges
import androidx.compose.foundation.BorderStroke
import app.subshell.prototype.ui.components.rememberRouteTopBarScrollBehavior
import app.subshell.prototype.ui.error.ErrorActionKind
import app.subshell.prototype.ui.error.postEditErrorPresentation
import app.subshell.prototype.ui.symbols.Symbols

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostEditScreen(
    post: Post,
    state: SubshellState,
    editState: PostEditUiState,
    onSubmitEdit: (PostEditRequest) -> Unit,
    onAuthorizeEdit: () -> Unit,
    onCancelAuthorization: () -> Unit,
    onResetEditState: () -> Unit,
    onSaved: (String) -> Unit,
    onClose: () -> Unit,
    onCheckAgain: () -> Unit = {},
    modifier: Modifier = Modifier,
    // ISSUE229_POST_BODY_MENTIONS_V1
    mentionSearchState: RedditPeopleSearchUiState = RedditPeopleSearchUiState(),
    onSearchMentions: (String) -> Unit = {},
) {
    val accountStableId = state.activeAccountId
    val postFullname = normalizePostEditFullname(post.id)
    val scopedState = if (editState.matches(accountStableId, postFullname)) {
        editState
    } else {
        PostEditUiState()
    }
    // COMPOSE01A1_SHARED_EDITOR_V1: the same field, dock and preview as the
    // post composer.
    // ISSUE8_INLINE_REDDIT_MEDIA_V1: the editor holds Reddit's own text, so
    // images, Giphy and emotes go back exactly as Reddit wrote them.
    val sourceBody = remember(post.body) { post.body.redditSourceMarkdown() }
    val editor = rememberTextFieldState(initialText = sourceBody)
    val body = editor.text.toString()
    var previewVisible by rememberSaveable(post.id, accountStableId) { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    // POSTEDIT02_EDITOR_LOCKED_WHILE_IN_FLIGHT_V1: while authorizing, sending
    // or checking, the text already belongs to the request; edits would not
    // reach Reddit.
    val inputLocked = scopedState.inProgress
    fun applyFormat(action: MarkdownAction) {
        if (inputLocked) return
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
    // LOWUI8_POST_EDIT_KEEPS_LEADING_INDENTATION_V1 (OCB-319): only the end is trimmed.
    val normalizedBody = body.trimEnd()
    val changed = normalizedBody != sourceBody.trimEnd()
    // POSTEDIT01_NO_RESEND_WHILE_UNCONFIRMED_V1: after an unknown outcome
    // only Check again (a read) is offered; Save would send the edit again.
    val awaitingCheck = scopedState.phase == PostEditPhase.FAILED &&
        scopedState.failureKind == PostEditFailureKind.OUTCOME_UNKNOWN
    val canSave = !scopedState.inProgress &&
        !awaitingCheck &&
        body.length <= POST_EDIT_TEXT_MAX_CHARS &&
        changed
    // POSTEDIT01_CLOSE_DURING_AUTHORIZATION_V1: waiting for Reddit's
    // authorization is not a write in flight; closing cancels it.
    val authorizing = scopedState.phase == PostEditPhase.AUTHORIZING ||
        scopedState.phase == PostEditPhase.AWAITING_CALLBACK
    val closeEnabled = !scopedState.inProgress || authorizing

    fun submit() {
        val text = normalizedBody
        onSubmitEdit(
            PostEditRequest(
                accountStableId = accountStableId,
                postFullname = postFullname,
                postAuthor = post.author,
                text = text,
            ),
        )
    }

    LaunchedEffect(scopedState.phase, scopedState.completionRevision) {
        if (scopedState.phase == PostEditPhase.SUCCEEDED) {
            // POSTEDIT01_SAVED_TEXT_IS_SENT_TEXT_V1
            onSaved(scopedState.savedText ?: body.trimEnd())
        }
    }

    // SUBSHELL_COMPOSE05_EDIT_DISCARD_CONFIRM_V1: closing with unsaved edits
    // asks first; there is no draft to fall back on.
    var discardConfirmVisible by rememberSaveable(post.id, accountStableId) { mutableStateOf(false) }
    fun close() {
        onCancelAuthorization()
        onResetEditState()
        onClose()
    }
    fun requestClose() {
        if (editHasUnsavedChanges(sourceBody, body)) discardConfirmVisible = true else close()
    }
    // LOWUI8_BACK_WAITS_FOR_THE_EDIT_V1 (OCB-231): while the edit is sent or
    // checked, Back is swallowed (as the close button is disabled) instead of
    // falling through to close the post before the result is applied.
    BackHandler(enabled = true) {
        if (closeEnabled) requestClose()
    }
    if (discardConfirmVisible) {
        ComposerDiscardChangesDialog(
            subject = "post",
            onKeepEditing = { discardConfirmVisible = false },
            onDiscard = {
                discardConfirmVisible = false
                close()
            },
        )
    }

    val routeTopBarScroll = rememberRouteTopBarScrollBehavior(ChromeBehavior.ALWAYS_SHOW)
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .nestedScroll(routeTopBarScroll.nestedScrollConnection),
        topBar = {
            RouteTopBar(
                scrollBehavior = routeTopBarScroll,
                title = { Text("Edit post") },
                navigationIcon = {
                    IconButton(
                        enabled = closeEnabled,
                        onClick = ::requestClose,
                    ) {
                        Icon(
                            Symbols.ArrowBack,
                            contentDescription = "Close post editor",
                        )
                    }
                },
                actions = {
                    ComposerPrimaryAction(
                        text = when (scopedState.phase) {
                            PostEditPhase.SENDING -> "Saving…"
                            PostEditPhase.CHECKING -> "Checking…"
                            else -> "Save"
                        },
                        enabled = canSave,
                        onClick = ::submit,
                    )
                },
            )
        },
        bottomBar = {
            Column {
                // ISSUE229_POST_BODY_MENTIONS_V1
                if (!inputLocked) {
                    MentionSuggestions(
                        state = editor,
                        mentions = postBodyMentions(
                            accountStableId = accountStableId,
                            authenticated = state.activeAccountIsAuthenticated,
                            searchState = mentionSearchState,
                            onSearch = onSearchMentions,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                ComposerFormattingDock(
                    state = editor,
                    onFormat = ::applyFormat,
                    onReturnToEditor = {
                        previewVisible = false
                        focusRequester.requestFocus()
                    },
                    enabled = !inputLocked,
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            // COMPOSE06: the fixed title mirrors the Body field (same header
            // style, outlined surface in the editor's shape and text style).
            Text("Title", style = MaterialTheme.typography.titleMedium)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 9.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Text(
                    post.title,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            ComposerContextNote(
                if (post.kind == PostKind.TEXT) {
                    "Only the body of this Reddit post is edited. " +
                        "The title, community and post type stay unchanged."
                } else {
                    "Controlled test: only the Markdown body is sent to Reddit. " +
                        "The title, link, media, community and post type stay unchanged."
                },
                modifier = Modifier.padding(top = 8.dp),
            )
            // label = { Text("Post body") } is now the editor's placeholder; the
            // POST_EDIT_TEXT_MAX_CHARS limit gates Save and colours the counter.
            ComposerBodyHeader(
                title = "Body",
                length = body.length,
                maxLength = POST_EDIT_TEXT_MAX_CHARS,
                previewVisible = previewVisible,
                onTogglePreview = { previewVisible = !previewVisible },
                modifier = Modifier.padding(top = 12.dp),
            )
            if (previewVisible) {
                MarkdownComposerPreview(
                    text = body,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp),
                )
            } else {
                PostStyleComposerEditor(
                    state = editor,
                    placeholder = "Share the details…",
                    focusRequester = focusRequester,
                    onFormat = ::applyFormat,
                    onMediaReceived = {},
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 8,
                    maxLines = 24,
                    inputLocked = inputLocked,
                )
            }

            when (scopedState.phase) {
                PostEditPhase.IDLE,
                PostEditPhase.SUCCEEDED,
                -> Unit

                PostEditPhase.AUTHORIZATION_REQUIRED -> {
                    Text(
                        "Reddit's edit permission is required before this post can be changed.",
                        modifier = Modifier.padding(top = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = onAuthorizeEdit,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text("Authorize Reddit")
                    }
                }

                PostEditPhase.AUTHORIZING,
                PostEditPhase.AWAITING_CALLBACK,
                PostEditPhase.SENDING,
                PostEditPhase.CHECKING,
                -> {
                    Text(
                        when (scopedState.phase) {
                            PostEditPhase.SENDING -> "Saving post body to Reddit…"
                            PostEditPhase.CHECKING -> "Checking Reddit for your edit…"
                            PostEditPhase.AUTHORIZING -> "Opening Reddit authorization…"
                            else -> "Waiting for Reddit authorization…"
                        },
                        modifier = Modifier.padding(top = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    CircularProgressIndicator(
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    if (authorizing) {
                        TextButton(
                            onClick = onCancelAuthorization,
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            Text("Cancel authorization")
                        }
                    }
                }

                PostEditPhase.FAILED -> {
                    ErrorNotice(
                        presentation = postEditErrorPresentation(scopedState),
                        diagnosticScopeKey = "post_edit",
                        modifier = Modifier.padding(top = 12.dp),
                        onAction = { action ->
                            // H2_POST_EDIT_CHECK_AGAIN_READS_ONLY_V1
                            if (action == ErrorActionKind.RETRY) {
                                if (scopedState.failureKind == PostEditFailureKind.OUTCOME_UNKNOWN) onCheckAgain() else submit()
                            }
                        },
                    )
                }
            }
        }
    }
}
