package app.subshell.prototype.ui.app

import android.view.ViewConfiguration
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import app.subshell.prototype.ui.DrawerCommunitiesMode
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.ui.SubshellState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/**
 * App-level effects that do not follow an account switch: system
 * notification routing (an inbox notification opens its item or resolves
 * its post; a modmail notification opens the inbox root and loads
 * modmail), the drawer and account-switcher open requests with the
 * drawer's cached-subscriptions load, and the You item's long-press into
 * the account switcher.
 *
 * Moved out of SubshellApp verbatim, in their original order. The effects
 * keyed on an account switch, the automatic backup history, the Back
 * handlers and the authenticated landing stay in SubshellApp.
 */
@Composable
internal fun AppEffectsBridge(
    state: SubshellState,
    redditInbox: InboxEntryPoints,
    redditModmail: ModmailEntryPoints,
    redditFeed: FeedEntryPoints,
    inContext: Boolean,
    drawerState: DrawerState,
    youInteractionSource: MutableInteractionSource,
    hapticFeedback: HapticFeedback,
    accountSwitcherVisibleState: MutableState<Boolean>,
    suppressYouClickState: MutableState<Boolean>,
) {
    val redditInboxState = redditInbox.redditInboxState
    val onLoadRedditInbox = redditInbox.onLoadRedditInbox
    val onMarkRedditInboxRead = redditInbox.onMarkRedditInboxRead
    val onResolveRedditInboxTarget = redditInbox.onResolveRedditInboxTarget
    val inboxNotificationTarget = redditInbox.inboxNotificationTarget
    val onInboxNotificationConsumed = redditInbox.onInboxNotificationConsumed
    val onLoadRedditModmail = redditModmail.onLoadRedditModmail
    val modmailNotificationTarget = redditModmail.modmailNotificationTarget
    val onLoadCachedRedditSubscriptions = redditFeed.onLoadCachedRedditSubscriptions
    var accountSwitcherVisible by accountSwitcherVisibleState
    var suppressYouClick by suppressYouClickState
    // ISSUE72_NOTIFICATION_WAITS_FOR_THE_RELOAD_V1: an inbox state from before
    // the notification may predate the notified item; only a state that
    // arrived after the target (a stored or reloaded inbox, or its failure)
    // may say the item is gone.
    // INBOXSSOT01_NOTIFICATION_OPENS_THE_STORED_ITEM_V1: the background sync
    // stored the item before it posted the notification, and the inbox
    // follows the store, so a tap opens it without waiting for Reddit. A
    // plain load (stored inbox first) starts following when nothing loaded
    // the inbox yet; Reddit is asked only when the item is not stored.
    val inboxAtNotification = remember(inboxNotificationTarget?.eventKey) { redditInboxState }
    LaunchedEffect(
        inboxNotificationTarget?.eventKey,
        state.activeAccountId,
    ) {
        val target = inboxNotificationTarget ?: return@LaunchedEffect
        if (target.accountStableId == state.activeAccountId) {
            onLoadRedditInbox(target.accountStableId, false)
        } else if (!switchToNotifiedAccount(state, target.accountStableId)) {
            onInboxNotificationConsumed()
        }
    }

    LaunchedEffect(
        inboxNotificationTarget?.eventKey,
        redditInboxState.contentRevision,
        redditInboxState.loading,
        state.activeAccountId,
    ) {
        val target = inboxNotificationTarget ?: return@LaunchedEffect
        if (
            target.accountStableId != state.activeAccountId ||
            !redditInboxState.matches(target.accountStableId)
        ) {
            return@LaunchedEffect
        }
        val item = redditInboxState.items
            .firstOrNull { candidate -> candidate.id == target.inboxItemId }
        if (item == null) {
            if (redditInboxState.loading || !redditInboxState.loaded) return@LaunchedEffect
            if (redditInboxState === inboxAtNotification) {
                // Not stored: the inbox in hand is all there is, so ask Reddit.
                onLoadRedditInbox(target.accountStableId, true)
            } else {
                onInboxNotificationConsumed()
                state.openInboxNotificationRoot()
            }
            return@LaunchedEffect
        }

        onInboxNotificationConsumed()
        if (item.kind == app.subshell.prototype.model.ActivityKind.MESSAGE) {
            state.openInboxNotificationItem(item)
            onMarkRedditInboxRead(target.accountStableId, item.id)
        } else {
            val postId = item.targetPostId
            if (postId == null) {
                state.openInboxNotificationItem(item, allowFallbackDetail = true)
                onMarkRedditInboxRead(target.accountStableId, item.id)
            } else {
                onResolveRedditInboxTarget(
                    target.accountStableId,
                    postId,
                ) { resolvedPost ->
                    // LOWUI6_NOTIFICATION_RESOLVE_CHECKS_THE_ACCOUNT_V1 (OCB-300):
                    // an answer that arrives after an account switch opens nothing.
                    if (state.activeAccountId != target.accountStableId) {
                        // Another account is active now: leave the screen alone.
                    } else if (resolvedPost == null) {
                        state.openInboxNotificationRoot()
                    } else {
                        val exactComment = item.targetCommentId != null
                        state.openInboxNotificationItem(
                            item = item,
                            resolvedPost = resolvedPost,
                            allowFallbackDetail = false,
                            verifiedReadAccountStableId =
                                target.accountStableId.takeIf { exactComment },
                        )
                        if (!exactComment) {
                            onMarkRedditInboxRead(target.accountStableId, item.id)
                        }
                    }
                }
            }
        }
    }

    // MODMAIL06_NOTIFICATION_ROOT_AND_LOAD_V1
    LaunchedEffect(
        modmailNotificationTarget?.eventKey,
        state.activeAccountId,
    ) {
        val target = modmailNotificationTarget ?: return@LaunchedEffect
        if (target.accountStableId == state.activeAccountId) {
            state.openInboxNotificationRoot()
            onLoadRedditModmail(target.accountStableId, true)
        } else if (!switchToNotifiedAccount(state, target.accountStableId)) {
            redditModmail.onModmailNotificationConsumed()
        }
    }

    LaunchedEffect(state.drawerOpenRequest) {
        if (state.drawerOpenRequest > 0 && !inContext) drawerState.open()
    }
    LaunchedEffect(
        drawerState.currentValue,
        state.activeAccountId,
        state.activeAccountIsAuthenticated,
        state.drawerCommunitiesMode,
    ) {
        if (drawerState.currentValue == DrawerValue.Open &&
            state.activeAccountIsAuthenticated &&
            state.drawerCommunitiesMode != DrawerCommunitiesMode.CURRENT
        ) {
            onLoadCachedRedditSubscriptions(state.activeAccountId)
        }
    }

    LaunchedEffect(state.accountSwitcherOpenRequest) {
        if (state.accountSwitcherOpenRequest > 0) accountSwitcherVisible = true
    }

    LaunchedEffect(youInteractionSource) {
        youInteractionSource.interactions.collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    suppressYouClick = false
                    delay(ViewConfiguration.getLongPressTimeout().toLong())
                    suppressYouClick = true
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    accountSwitcherVisible = true
                }
                is PressInteraction.Cancel -> suppressYouClick = false
                is PressInteraction.Release -> Unit
            }
        }
    }
}

/**
 * LOWUX1_NOTIFICATION_SWITCHES_TO_ITS_ACCOUNT_V1 (OCB-111, owner chose
 * switch and open): a notification of another signed-in account switches
 * to it, and the effects keyed on the active account then open the item as
 * for the active one. False when that account is no longer signed in here;
 * the caller drops the target instead of keeping it for a later switch.
 */
internal fun switchToNotifiedAccount(state: SubshellState, accountStableId: String): Boolean {
    val account = state.accountSessions.firstOrNull { session -> session.stableId == accountStableId }
        ?: return false
    if (account.source != AccountSessionSource.AUTHENTICATED) return false
    state.switchAccount(account.stableId)
    return state.activeAccountId == account.stableId
}
