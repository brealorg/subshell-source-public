package app.subshell.prototype.ui

import androidx.compose.ui.platform.UriHandler

// ISSUE41_IN_APP_REDDIT_LINKS_V1
// Links tapped inside Subshell went to the platform: a reddit.com link then
// reached Subshell only if it was the verified reddit.com handler, and
// otherwise opened a browser. Reddit links now stay in the app and take the
// same path as an incoming VIEW intent; every other link goes to the platform
// as before.
internal class InAppRedditUriHandler(
    private val platform: UriHandler,
    private val onRedditLink: (String) -> Unit,
) : UriHandler {
    override fun openUri(uri: String) {
        if (isInAppRedditLink(uri)) onRedditLink(uri.trim()) else platform.openUri(uri)
    }
}

internal fun isInAppRedditLink(uri: String): Boolean =
    resolveRedditTarget(uri) != null || isRedditWebLink(uri)
