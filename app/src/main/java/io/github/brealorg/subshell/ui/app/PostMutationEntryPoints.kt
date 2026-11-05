package io.github.brealorg.subshell.ui.app

import io.github.brealorg.subshell.data.account.PostDeleteRequest
import io.github.brealorg.subshell.data.account.PostDeleteUiState
import io.github.brealorg.subshell.data.account.PostEditRequest
import io.github.brealorg.subshell.data.account.PostEditUiState
import io.github.brealorg.subshell.data.account.PostManageMutationRequest
import io.github.brealorg.subshell.data.account.PostManageTarget
import io.github.brealorg.subshell.data.account.PostManageUiState

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
