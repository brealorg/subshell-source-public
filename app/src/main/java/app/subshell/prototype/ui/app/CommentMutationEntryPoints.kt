package app.subshell.prototype.ui.app

import app.subshell.prototype.data.account.CommentDeleteRequest
import app.subshell.prototype.data.account.CommentDeleteUiState
import app.subshell.prototype.data.account.CommentEditRequest
import app.subshell.prototype.data.account.CommentEditUiState
import app.subshell.prototype.data.account.CommentWriteRequest
import app.subshell.prototype.data.account.CommentWriteUiState
import app.subshell.prototype.ui.CommentMediaPolicy

/**
 * The comment mutation runtime as SubshellApp receives it from MainActivity:
 * the comment write, edit and delete UI states with their submit, authorize,
 * cancel-authorization and reset entry points. Property names and defaults
 * are the former SubshellApp parameters, unchanged.
 */
data class CommentMutationEntryPoints(
    val commentWriteState: CommentWriteUiState = CommentWriteUiState(),
    val onSubmitComment: (CommentWriteRequest) -> Unit = {},
    val onAuthorizeCommentWrite: () -> Unit = {},
    val onCancelCommentWriteAuthorization: () -> Unit = {},
    val onResetCommentWriteState: () -> Unit = {},
    val commentEditState: CommentEditUiState = CommentEditUiState(),
    val onEditComment: (CommentEditRequest) -> Unit = {},
    val onAuthorizeCommentEdit: () -> Unit = {},
    val onCancelCommentEditAuthorization: () -> Unit = {},
    val onResetCommentEditState: () -> Unit = {},
    val onCheckCommentEdit: () -> Unit = {},
    val commentDeleteState: CommentDeleteUiState = CommentDeleteUiState(),
    val onDeleteComment: (CommentDeleteRequest) -> Unit = {},
    val onAuthorizeCommentDelete: () -> Unit = {},
    val onCancelCommentDeleteAuthorization: () -> Unit = {},
    val onResetCommentDeleteState: () -> Unit = {},
    // COMPOSER10_COMMENT_MEDIA_RULES_V1: comment media rules per account and
    // community (commentMediaPolicyKey), and the request to read them.
    val commentMediaPolicies: Map<String, CommentMediaPolicy> = emptyMap(),
    val onLoadCommentMediaPolicy: (String, String) -> Unit = { _, _ -> },
)
