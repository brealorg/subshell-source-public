package app.subshell.prototype.ui.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import app.subshell.prototype.share.ShareIntoSubshellLaunch
import app.subshell.prototype.share.pruneSharedMedia
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.error.ErrorPresentation
import app.subshell.prototype.ui.error.ErrorPresentationKind
import app.subshell.prototype.ui.error.ViewportErrorHostState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ISSUE33_SHARE_INTO_SUBSHELL_V1
// Opens a share from another app as a prefilled post in the composer, for
// the active account. Posting needs a signed-in Reddit account: without one
// the tray says so instead of making a local-only post. Items that could
// not be used are reported too. Copies of shared images no draft uses any
// more are deleted once per app start.
@Composable
internal fun ShareIntoSubshellEffect(
    state: SubshellState,
    launch: ShareIntoSubshellLaunch?,
    onConsumed: () -> Unit,
    noticeHost: ViewportErrorHostState,
    // LOWUI8_SHARE_WAITS_FOR_THE_POST_BEING_SENT_V1 (OCB-122): a share opened
    // during a send replaced the locked draft under the send; it waits,
    // unconsumed, and opens once the send has finished.
    postWriteInProgress: Boolean = false,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        // COMPOSER16_PASTED_IMAGE_COPIED_V1: pasted images in the comment
        // field are copies in the same folder.
        val referenced = (state.postComposerDrafts.toList() + state.postComposerDraft)
            .flatMap { draft -> draft.media.map { media -> media.uri } }
            .toSet() + listOfNotNull(state.commentComposerDraft?.media?.uri)
        withContext(Dispatchers.IO) { pruneSharedMedia(context, referenced) }
    }
    LaunchedEffect(launch?.requestId, postWriteInProgress) {
        val current = launch ?: return@LaunchedEffect
        if (postWriteInProgress && current.content != null && state.activeAccountIsAuthenticated) {
            return@LaunchedEffect
        }
        val content = current.content
        when {
            content == null -> presentShareNotice(
                noticeHost,
                title = "Nothing to post",
                message = "subshell can post links, text and images",
            )
            !state.activeAccountIsAuthenticated -> presentShareNotice(
                noticeHost,
                title = "Sign in to Reddit to post",
                message = "Connect an account, then share again",
            )
            else -> {
                state.openSharedPostComposer(content)
                if (content.skippedCount > 0) {
                    presentShareNotice(
                        noticeHost,
                        title = if (content.skippedCount == 1) "1 item left out" else "${content.skippedCount} items left out",
                        message = "Only JPEG, PNG, WebP and GIF images, up to 20, can be posted",
                    )
                }
            }
        }
        onConsumed()
    }
}

internal const val ShareIntoSubshellNoticeScope = "share-into-subshell"

private fun presentShareNotice(noticeHost: ViewportErrorHostState, title: String, message: String) {
    noticeHost.clear(ShareIntoSubshellNoticeScope)
    noticeHost.present(
        scopeKey = ShareIntoSubshellNoticeScope,
        presentation = ErrorPresentation(
            kind = ErrorPresentationKind.DEGRADED,
            title = title,
            message = message,
        ),
        failureDetails = null,
        onRetry = null,
    )
}
