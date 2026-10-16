package app.subshell.prototype.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.subshell.prototype.data.account.CustomFeedsUiState
import app.subshell.prototype.shortcuts.LauncherShortcutResolution
import app.subshell.prototype.shortcuts.LauncherShortcutTarget
import app.subshell.prototype.shortcuts.resolveLauncherShortcutTarget
import app.subshell.prototype.ui.MainDestination
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.error.ErrorPresentation
import app.subshell.prototype.ui.error.ErrorPresentationKind
import app.subshell.prototype.ui.error.ViewportErrorHostState

internal const val LauncherShortcutNoticeScope = "launcher-shortcut"

/**
 * ISSUE37_LAUNCHER_SHORTCUT_TARGETS_V1: opens a custom-feed or Inbox
 * launcher shortcut for the account it belongs to, the way the drawer and
 * inbox notifications open them (a chosen destination also ends a pending
 * authenticated landing). With another account active it only explains
 * which account the shortcut needs; it never switches account. A feed waits
 * for that account's feed list and is dropped with a notice if the list
 * fails or no longer has it.
 */
@Composable
internal fun LauncherShortcutTargetEffect(
    state: SubshellState,
    feedTarget: LauncherShortcutTarget.CustomFeed?,
    onFeedTargetConsumed: () -> Unit,
    inboxTarget: LauncherShortcutTarget.Inbox?,
    onInboxTargetConsumed: () -> Unit,
    customFeedsState: CustomFeedsUiState,
    onLoadCustomFeeds: (String, Boolean) -> Unit,
    activeCustomFeedPathState: MutableState<String?>,
    pendingAuthenticatedLandingAccountIdState: MutableState<String?>,
    noticeHost: ViewportErrorHostState,
) {
    var activeCustomFeedPath by activeCustomFeedPathState
    var pendingAuthenticatedLandingAccountId by pendingAuthenticatedLandingAccountIdState
    val target: LauncherShortcutTarget? = feedTarget ?: inboxTarget
    LaunchedEffect(
        target?.requestId,
        state.activeAccountId,
        customFeedsState.accountStableId,
        customFeedsState.loading,
        customFeedsState.contentRevision,
        customFeedsState.failureKind,
    ) {
        target ?: return@LaunchedEffect
        val consume = if (target is LauncherShortcutTarget.CustomFeed) {
            onFeedTargetConsumed
        } else {
            onInboxTargetConsumed
        }
        val resolution = resolveLauncherShortcutTarget(
            target = target,
            activeAccountId = state.activeAccountId,
            signedInAccountIds = state.accountSessions.map { session -> session.stableId },
            customFeeds = customFeedsState,
        )
        when (resolution) {
            LauncherShortcutResolution.LoadFeeds -> {
                onLoadCustomFeeds(target.accountStableId, false)
                return@LaunchedEffect
            }
            is LauncherShortcutResolution.OpenFeed -> {
                pendingAuthenticatedLandingAccountId = null
                activeCustomFeedPath = resolution.path
                state.navigate(MainDestination.HOME)
            }
            LauncherShortcutResolution.OpenInbox -> {
                pendingAuthenticatedLandingAccountId = null
                state.openInboxNotificationRoot()
            }
            is LauncherShortcutResolution.OtherAccount -> {
                val username = state.accountSessions
                    .firstOrNull { session -> session.stableId == resolution.accountStableId }
                    ?.username
                presentNotice(
                    noticeHost,
                    title = username?.let { name -> "Switch to u/$name" } ?: "Switch account",
                    message = "This shortcut belongs to another account",
                )
            }
            LauncherShortcutResolution.AccountGone -> presentNotice(
                noticeHost,
                title = "Account not on this device",
                message = "This shortcut belongs to an account that is no longer signed in",
            )
            LauncherShortcutResolution.FeedsFailed -> presentNotice(
                noticeHost,
                title = "Couldn't open the custom feed",
                message = "Your custom feeds did not load",
            )
            LauncherShortcutResolution.FeedMissing -> presentNotice(
                noticeHost,
                title = "Custom feed not found",
                message = "It may have been deleted or renamed",
            )
        }
        consume()
    }
}

private fun presentNotice(
    noticeHost: ViewportErrorHostState,
    title: String,
    message: String,
) {
    // clear() first forgets an earlier dismissal of the same notice.
    noticeHost.clear(LauncherShortcutNoticeScope)
    noticeHost.present(
        scopeKey = LauncherShortcutNoticeScope,
        presentation = ErrorPresentation(
            kind = ErrorPresentationKind.DEGRADED,
            title = title,
            message = message,
        ),
        failureDetails = null,
        onRetry = null,
    )
}
