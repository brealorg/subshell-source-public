package app.subshell.prototype.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.subshell.prototype.ui.error.ErrorPresentation
import app.subshell.prototype.ui.error.ErrorPresentationKind
import app.subshell.prototype.ui.error.ViewportErrorHostState

// ISSUE41_UNSUPPORTED_REDDIT_LINK_NOTICE_V1
// A Reddit link that Subshell cannot open used to do nothing at all. The
// deep-link effect now reports it, and this notice says so in the tray.
// Subshell does not send Reddit links out of the app, so there is no
// browser fallback.
internal enum class RedditLinkUnavailableReason {
    /** Subshell does not know this kind of reddit.com link. */
    UNSUPPORTED_LINK,

    /** The link names a post that could not be loaded. */
    POST_UNAVAILABLE,

    /** Reddit did not say where a share link leads. */
    SHARE_LINK_UNAVAILABLE,
}

internal data class RedditLinkUnavailable(
    val requestId: Long,
    val rawUri: String,
    val reason: RedditLinkUnavailableReason,
)

internal const val RedditLinkNoticeScope = "reddit-link"

internal fun redditLinkUnavailablePresentation(
    reason: RedditLinkUnavailableReason,
): ErrorPresentation = ErrorPresentation(
    kind = ErrorPresentationKind.DEGRADED,
    title = when (reason) {
        RedditLinkUnavailableReason.UNSUPPORTED_LINK -> "Can't open this link here"
        RedditLinkUnavailableReason.POST_UNAVAILABLE -> "Couldn't open this post"
        RedditLinkUnavailableReason.SHARE_LINK_UNAVAILABLE -> "Couldn't open this shared link"
    },
    message = when (reason) {
        RedditLinkUnavailableReason.UNSUPPORTED_LINK ->
            "subshell doesn't support this kind of Reddit link"
        RedditLinkUnavailableReason.POST_UNAVAILABLE ->
            "It may have been removed, its community banned, or the connection failed"
        RedditLinkUnavailableReason.SHARE_LINK_UNAVAILABLE ->
            "Reddit didn't say where it leads, or the connection failed"
    },
)

@Composable
internal fun RedditLinkUnavailableNoticeEffect(
    unavailable: RedditLinkUnavailable?,
    onShown: () -> Unit,
    noticeHost: ViewportErrorHostState,
) {
    LaunchedEffect(unavailable?.requestId) {
        val current = unavailable ?: return@LaunchedEffect
        noticeHost.presentRedditLinkUnavailable(current.reason)
        onShown()
    }
}

internal fun ViewportErrorHostState.presentRedditLinkUnavailable(reason: RedditLinkUnavailableReason) {
    // clear() first forgets an earlier dismissal of the same notice.
    clear(RedditLinkNoticeScope)
    present(
        scopeKey = RedditLinkNoticeScope,
        presentation = redditLinkUnavailablePresentation(reason),
        failureDetails = null,
        onRetry = null,
    )
}
