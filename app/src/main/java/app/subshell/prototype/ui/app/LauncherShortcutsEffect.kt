package app.subshell.prototype.ui.app

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.subshell.prototype.data.account.CustomFeedsUiState
import app.subshell.prototype.shortcuts.AndroidLauncherShortcutPublisher
import app.subshell.prototype.shortcuts.LauncherShortcutEntry
import app.subshell.prototype.shortcuts.customFeedPathKey
import app.subshell.prototype.shortcuts.customFeedShortcutLabel
import app.subshell.prototype.shortcuts.LauncherShortcutPublisher
import app.subshell.prototype.shortcuts.launcherCommunityDisplayName
import app.subshell.prototype.shortcuts.planLauncherShortcuts
import app.subshell.prototype.shortcuts.rememberLauncherShortcutsEnabled
import app.subshell.prototype.ui.SubshellState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * ISSUE37_LAUNCHER_SHORTCUTS_V1: keeps the launcher's shortcuts in step
 * with the setting, the active account's Inbox (signed-in accounts only)
 * and its favourite and hidden communities and favourite custom feeds. An account switch replaces both lists, so the effect
 * restarts for the new account and cancels the old one's work. Launcher
 * calls are serialised: a cancelled run that is already inside a launcher
 * call finishes before the newer run publishes, so the newer one wins.
 */
@Composable
internal fun LauncherShortcutsEffect(
    state: SubshellState,
    customFeedsState: CustomFeedsUiState,
    publisher: LauncherShortcutPublisher? = null,
) {
    val context = LocalContext.current
    val launcher = publisher ?: remember(context) { AndroidLauncherShortcutPublisher(context) }
    val enabled by rememberLauncherShortcutsEnabled()
    // Display names (Reddit's casing when known) in the drawer's order.
    val favorites = state.favoriteCommunities().map { community ->
        launcherCommunityDisplayName(community.name)
    }
    val hidden = state.hiddenCommunityNames.toList()
    val account = state.activeAccountId
    // Favourite feed paths with their loaded names; the list may not be
    // loaded yet, and a feed shortcut must not wait for it.
    val loadedFeedNames = customFeedsState
        .takeIf { feeds -> feeds.accountStableId == account }
        ?.feeds
        .orEmpty()
        .associate { feed -> customFeedPathKey(feed.path) to feed.displayName }
    val feeds = state.favoriteCustomFeedPaths.map { path ->
        LauncherShortcutEntry.CustomFeed(
            accountStableId = account,
            path = path,
            displayName = customFeedShortcutLabel(path, loadedFeedNames[customFeedPathKey(path)]),
        )
    }
    val inbox = LauncherShortcutEntry.Inbox(account).takeIf { state.activeAccountIsAuthenticated }
    val launcherCalls = remember(launcher) { Mutex() }
    LaunchedEffect(launcher, enabled, favorites, hidden, feeds, inbox) {
        try {
            launcherCalls.withLock {
                withContext(Dispatchers.IO) {
                    val pinned = launcher.pinnedShortcuts()
                    ensureActive()
                    launcher.apply(
                        planLauncherShortcuts(
                            enabled = enabled,
                            favoriteCommunityNames = favorites,
                            hiddenCommunityNames = hidden,
                            pinned = pinned,
                            favoriteCustomFeeds = feeds,
                            inbox = inbox,
                        ),
                    )
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: RuntimeException) {
            // The launcher may refuse or rate-limit; the next change retries.
            Log.w("SubshellShortcuts", "phase=publish_failed type=${failure.javaClass.simpleName}")
        }
    }
}
