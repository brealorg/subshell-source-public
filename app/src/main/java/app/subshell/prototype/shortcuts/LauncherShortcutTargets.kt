package app.subshell.prototype.shortcuts

import android.content.Intent
import app.subshell.prototype.data.account.CustomFeedsUiState
import app.subshell.prototype.ui.normalizeFavoriteCustomFeedPath

// ISSUE37_LAUNCHER_SHORTCUT_TARGETS_V1: feed and Inbox shortcuts belong to
// the account that published them. Like inbox notifications they never
// switch account: with another account active the shortcut only explains
// which account it needs. A custom feed opens once that account's feed list
// is loaded, because the feed is shown from the list (it may be private).

internal const val LAUNCHER_SHORTCUT_ACTION = "app.subshell.prototype.action.OPEN_LAUNCHER_SHORTCUT"
internal const val LAUNCHER_SHORTCUT_KIND_EXTRA = "subshell.shortcut.kind"
internal const val LAUNCHER_SHORTCUT_ACCOUNT_EXTRA = "subshell.shortcut.account"
internal const val LAUNCHER_SHORTCUT_FEED_PATH_EXTRA = "subshell.shortcut.feed_path"
private const val KIND_CUSTOM_FEED = "custom_feed"
private const val KIND_INBOX = "inbox"

sealed interface LauncherShortcutTarget {
    val accountStableId: String
    val requestId: Long

    data class CustomFeed(
        override val accountStableId: String,
        val path: String,
        override val requestId: Long,
    ) : LauncherShortcutTarget

    data class Inbox(
        override val accountStableId: String,
        override val requestId: Long,
    ) : LauncherShortcutTarget
}

internal fun launcherShortcutTargetIntent(target: LauncherShortcutTarget): Intent =
    Intent(LAUNCHER_SHORTCUT_ACTION)
        .putExtra(LAUNCHER_SHORTCUT_ACCOUNT_EXTRA, target.accountStableId)
        .apply {
            when (target) {
                is LauncherShortcutTarget.CustomFeed -> {
                    putExtra(LAUNCHER_SHORTCUT_KIND_EXTRA, KIND_CUSTOM_FEED)
                    putExtra(LAUNCHER_SHORTCUT_FEED_PATH_EXTRA, target.path)
                }
                is LauncherShortcutTarget.Inbox ->
                    putExtra(LAUNCHER_SHORTCUT_KIND_EXTRA, KIND_INBOX)
            }
        }

internal fun launcherShortcutTargetOrNull(
    action: String?,
    kind: String?,
    accountStableId: String?,
    feedPath: String?,
    requestId: Long,
): LauncherShortcutTarget? {
    if (action != LAUNCHER_SHORTCUT_ACTION) return null
    val account = accountStableId?.trim()?.takeIf(String::isNotEmpty) ?: return null
    return when (kind) {
        KIND_CUSTOM_FEED -> feedPath
            ?.let(::normalizeFavoriteCustomFeedPath)
            ?.let { path -> LauncherShortcutTarget.CustomFeed(account, path, requestId) }
        KIND_INBOX -> LauncherShortcutTarget.Inbox(account, requestId)
        else -> null
    }
}

/**
 * Reads a launcher shortcut target from [candidate] and removes its extras,
 * so a recreated activity does not open it a second time.
 */
internal fun takeLauncherShortcutTarget(candidate: Intent?, requestId: Long): LauncherShortcutTarget? {
    candidate ?: return null
    val target = launcherShortcutTargetOrNull(
        action = candidate.action,
        kind = candidate.getStringExtra(LAUNCHER_SHORTCUT_KIND_EXTRA),
        accountStableId = candidate.getStringExtra(LAUNCHER_SHORTCUT_ACCOUNT_EXTRA),
        feedPath = candidate.getStringExtra(LAUNCHER_SHORTCUT_FEED_PATH_EXTRA),
        requestId = requestId,
    )
    if (candidate.action == LAUNCHER_SHORTCUT_ACTION) {
        candidate.removeExtra(LAUNCHER_SHORTCUT_KIND_EXTRA)
        candidate.removeExtra(LAUNCHER_SHORTCUT_ACCOUNT_EXTRA)
        candidate.removeExtra(LAUNCHER_SHORTCUT_FEED_PATH_EXTRA)
    }
    // LOW_SHORTCUT_NOT_REPLAYED_FROM_RECENTS_V1 (OCB-572): after process death
    // Recents relaunches the task with the original intent (extras intact);
    // the shortcut was already taken, and the restored screen stays (as
    // UPDATE02 does for Open update).
    if (candidate.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return null
    return target
}

internal sealed interface LauncherShortcutResolution {
    /** The account is signed in but not active: say which account to switch to. */
    data class OtherAccount(val accountStableId: String) : LauncherShortcutResolution

    /** The account is not on this device any more. */
    data object AccountGone : LauncherShortcutResolution

    /** The active account's feed list is not loaded yet: load it and wait. */
    data object LoadFeeds : LauncherShortcutResolution

    data object FeedsFailed : LauncherShortcutResolution

    data object FeedMissing : LauncherShortcutResolution

    data class OpenFeed(val path: String) : LauncherShortcutResolution

    data object OpenInbox : LauncherShortcutResolution
}

internal fun resolveLauncherShortcutTarget(
    target: LauncherShortcutTarget,
    activeAccountId: String,
    signedInAccountIds: Collection<String>,
    customFeeds: CustomFeedsUiState,
): LauncherShortcutResolution {
    if (target.accountStableId != activeAccountId) {
        return if (target.accountStableId in signedInAccountIds) {
            LauncherShortcutResolution.OtherAccount(target.accountStableId)
        } else {
            LauncherShortcutResolution.AccountGone
        }
    }
    return when (target) {
        is LauncherShortcutTarget.Inbox -> LauncherShortcutResolution.OpenInbox
        is LauncherShortcutTarget.CustomFeed -> {
            val forAccount = customFeeds.accountStableId == target.accountStableId
            when {
                !forAccount || customFeeds.loading -> LauncherShortcutResolution.LoadFeeds
                customFeeds.failureKind != null -> LauncherShortcutResolution.FeedsFailed
                customFeeds.contentRevision <= 0L -> LauncherShortcutResolution.LoadFeeds
                // The list's paths are Reddit's (often with a trailing slash);
                // the target's is normalised, so compare normalised paths.
                else -> customFeeds.feeds
                    .firstOrNull { feed -> sameCustomFeedPath(feed.path, target.path) }
                    ?.let { feed -> LauncherShortcutResolution.OpenFeed(feed.path) }
                    ?: LauncherShortcutResolution.FeedMissing
            }
        }
    }
}
