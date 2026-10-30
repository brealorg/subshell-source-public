package app.subshell.prototype.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.subshell.prototype.data.account.RedditAccessRefusal
import app.subshell.prototype.ui.error.ErrorAction
import app.subshell.prototype.ui.error.ErrorActionKind
import app.subshell.prototype.ui.error.ErrorPresentation
import app.subshell.prototype.ui.error.ErrorPresentationKind
import app.subshell.prototype.ui.error.ViewportErrorHostState
import kotlinx.coroutines.launch

// ISSUE32_REDDIT_REFUSED_ACCESS_IS_ONE_NOTICE_V1 (#32): while Reddit refuses
// the app's Client ID, the tray says so once (every screen's own "couldn't
// refresh" is left out, see ViewportErrorHostState.present) and the stored
// feeds, threads and inbox stay readable. Try again lets one request through
// to the token endpoint; the notice leaves by itself when Reddit lets the app
// in again or the reader signs in with another Client ID.

internal const val REDDIT_ACCESS_REFUSED_SCOPE = "reddit-access-refused"

internal fun redditAccessRefusedPresentation(): ErrorPresentation = ErrorPresentation(
    kind = ErrorPresentationKind.DEGRADED,
    title = "Reddit has shut out this app's Client ID",
    message = "Reddit refuses the Client ID subshell signs in with, so nothing new loads. " +
        "This happens when Reddit stops accepting a Client ID, as it does from January 2027 for apps " +
        "it hasn't registered. What's stored on this device stays readable: feeds, threads you've opened " +
        "and your inbox. Your sign-in is kept. If you have another Client ID, set it under " +
        "Settings › Reddit connection. Try again asks Reddit once more.",
    primaryAction = ErrorAction(ErrorActionKind.RETRY, "Try again"),
)

@Composable
internal fun RedditAccessRefusedNotice(
    noticeHost: ViewportErrorHostState,
    checkAccess: (suspend () -> Unit)?,
) {
    val refusal by RedditAccessRefusal.state.collectAsStateWithLifecycle()
    val check by rememberUpdatedState(checkAccess)
    val scope = rememberCoroutineScope()
    val refused = refusal != null
    LaunchedEffect(refused) {
        if (!refused) {
            noticeHost.clear(REDDIT_ACCESS_REFUSED_SCOPE)
            return@LaunchedEffect
        }
        noticeHost.present(
            scopeKey = REDDIT_ACCESS_REFUSED_SCOPE,
            presentation = redditAccessRefusedPresentation(),
            failureDetails = null,
            onRetry = {
                RedditAccessRefusal.allowCheck()
                check?.let { read -> scope.launch { read() } }
            },
        )
    }
}
