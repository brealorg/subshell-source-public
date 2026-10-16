package app.subshell.prototype.ui.app

import app.subshell.prototype.data.account.PostDeleteRequest
import app.subshell.prototype.data.account.PostDeleteUiState
import app.subshell.prototype.data.account.PostEditRequest
import app.subshell.prototype.data.account.PostEditUiState
import app.subshell.prototype.data.account.PostManageMutationRequest
import app.subshell.prototype.data.account.PostManageTarget
import app.subshell.prototype.data.account.PostManageUiState

/**
 * The post mutation runtime as SubshellApp receives it from MainActivity:
 * the post edit, delete and manage UI states with their submit/load,
 * authorize, cancel-authorization and reset entry points. Property names and
 * defaults are the former SubshellApp parameters, unchanged.
 */
data class PostMutationEntryPoints(
    val postEditState: PostEditUiState = PostEditUiState(),
    val onEditPost: (PostEditRequest) -> Unit = {},
    val onAuthorizePostEdit: () -> Unit = {},
    val onCancelPostEditAuthorization: () -> Unit = {},
    val onResetPostEditState: () -> Unit = {},
    val onCheckPostEdit: () -> Unit = {},
    val postDeleteState: PostDeleteUiState = PostDeleteUiState(),
    val onDeletePost: (PostDeleteRequest) -> Unit = {},
    val onAuthorizePostDelete: () -> Unit = {},
    val onCancelPostDeleteAuthorization: () -> Unit = {},
    val onResetPostDeleteState: () -> Unit = {},
    val onCheckPostDelete: () -> Unit = {},
    val postManageState: PostManageUiState = PostManageUiState(),
    val onLoadPostManagement: (PostManageTarget) -> Unit = {},
    val onMutatePostManagement: (PostManageMutationRequest) -> Unit = {},
    val onAuthorizePostManagement: () -> Unit = {},
    val onCancelPostManageAuthorization: () -> Unit = {},
    val onResetPostManageState: () -> Unit = {},
)
