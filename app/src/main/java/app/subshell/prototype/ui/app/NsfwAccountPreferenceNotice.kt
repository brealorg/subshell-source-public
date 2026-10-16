package app.subshell.prototype.ui.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import app.subshell.prototype.data.account.NsfwPreferencesResult
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.error.ErrorAction
import app.subshell.prototype.ui.error.ErrorActionKind
import app.subshell.prototype.ui.error.ErrorPresentation
import app.subshell.prototype.ui.error.ErrorPresentationKind
import app.subshell.prototype.ui.error.TrayTone
import app.subshell.prototype.ui.error.ViewportErrorHostState

// ISSUE211_ACCOUNT_NSFW_PREFERENCE_V1 (#211): with "Show NSFW posts" on, the
// active Reddit account's own over_18 is read once per account (and again
// when the setting is turned on). If the account hides NSFW content, Reddit
// leaves it out of every feed and search whatever Subshell shows, so the tray
// says so and offers Reddit's settings (owner: a link only, Subshell writes
// nothing to the account). The tray's one action button runs onRetry, so the
// link is that action with its own label.

internal const val NSFW_ACCOUNT_PREFERENCE_SCOPE = "nsfw-account-preference"
internal val NSFW_ACCOUNT_PREFERENCE_RETRY_DELAYS_MS = listOf(15_000L, 60_000L, 300_000L)
internal const val REDDIT_PREFERENCES_URL = "https://www.reddit.com/settings/preferences"

internal fun nsfwAccountPreferencePresentation(): ErrorPresentation = ErrorPresentation(
    kind = ErrorPresentationKind.DEGRADED,
    title = "Your Reddit account hides NSFW content",
    message = "Reddit leaves NSFW posts and communities out for this account until " +
        "\"Show mature content\" is on in your Reddit settings.",
    primaryAction = ErrorAction(ErrorActionKind.RETRY, "Open Reddit settings"),
    secondaryAction = ErrorAction(ErrorActionKind.DISMISS, "Dismiss"),
)

/** True when the account's answer means the notice should show. */
internal fun nsfwAccountPreferenceNeedsNotice(result: NsfwPreferencesResult): Boolean =
    result is NsfwPreferencesResult.Known && !result.preferences.over18

@Composable
internal fun NsfwAccountPreferenceNotice(
    state: SubshellState,
    readPreferences: (suspend (String) -> NsfwPreferencesResult)?,
    noticeHost: ViewportErrorHostState,
) {
    val host = noticeHost
    val context = LocalContext.current
    val read by rememberUpdatedState(readPreferences)
    val wanted = state.showNsfwPosts && state.activeAccountIsAuthenticated
    val account = state.activeAccountId
    LaunchedEffect(wanted, account) {
        host.clear(NSFW_ACCOUNT_PREFERENCE_SCOPE)
        val reader = read
        if (!wanted || reader == null) return@LaunchedEffect
        // ISSUE211_ACCOUNT_PREFERENCE_READ_RETRIES_V1: a read that failed (a cold
        // start without network, seen on the owner's phone) is tried again a
        // few times instead of skipping the notice for the whole run.
        var result = reader(account)
        for (pause in NSFW_ACCOUNT_PREFERENCE_RETRY_DELAYS_MS) {
            if (result !is NsfwPreferencesResult.Failed) break
            kotlinx.coroutines.delay(pause)
            result = (read ?: return@LaunchedEffect)(account)
        }
        if (nsfwAccountPreferenceNeedsNotice(result)) {
            host.present(
                scopeKey = NSFW_ACCOUNT_PREFERENCE_SCOPE,
                presentation = nsfwAccountPreferencePresentation(),
                failureDetails = null,
                onRetry = { openInBrowser(context, REDDIT_PREFERENCES_URL) },
                tone = TrayTone.INFO, // ISSUE222: information, not an error
            )
        }
    }
}

/**
 * Reddit's own site in the browser: Subshell handles reddit.com links itself,
 * so the default browser is named explicitly (as for polls).
 */
private fun openInBrowser(context: Context, url: String) {
    val uri = Uri.parse(url)
    val browser = context.packageManager
        .resolveActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE),
            android.content.pm.PackageManager.MATCH_DEFAULT_ONLY,
        )
        ?.activityInfo
        ?.packageName
        ?.takeUnless { packageName -> packageName == "android" }
    val intent = Intent(Intent.ACTION_VIEW, uri)
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .apply { if (browser != null) setPackage(browser) }
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
