package app.subshell.prototype.ui

import androidx.compose.runtime.staticCompositionLocalOf
import app.subshell.prototype.data.account.PostFollowRequest
import app.subshell.prototype.data.account.PostFollowUiState

// FOLLOWPOST01A1B_ROOT_RUNTIME_LOCAL_V1
internal data class PostFollowRuntimeEntryPoints(
    val state: PostFollowUiState = PostFollowUiState(),
    val onSetFollowed: (PostFollowRequest) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onAuthorize: () -> Unit = {},
)

internal val LocalPostFollowRuntime =
    staticCompositionLocalOf { PostFollowRuntimeEntryPoints() }
