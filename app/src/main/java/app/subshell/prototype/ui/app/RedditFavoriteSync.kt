package app.subshell.prototype.ui.app

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import app.subshell.prototype.data.account.CustomFeedsUiState
import app.subshell.prototype.data.account.RedditFavoriteUiFailure
import app.subshell.prototype.data.account.RedditFavoriteUiFailureKind
import app.subshell.prototype.data.account.RedditSubscriptionsUiState
import app.subshell.prototype.data.account.REDDIT_FAVORITE_SYNC_ENABLED_KEY
import app.subshell.prototype.data.account.RedditFavoriteSyncSettingStore
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.error.ErrorAction
import app.subshell.prototype.ui.error.ErrorActionKind
import app.subshell.prototype.ui.error.ErrorPresentation
import app.subshell.prototype.ui.error.ErrorPresentationKind
import app.subshell.prototype.ui.error.ViewportErrorHostState
import app.subshell.prototype.ui.state.RedditFavoriteSyncSnapshot

/** REDDITFAV01: the stored setting, updated whenever Settings writes it. */
@Composable
internal fun rememberRedditFavoriteSyncEnabled(): State<Boolean> {
    val appContext = LocalContext.current.applicationContext
    return produceState(
        initialValue = RedditFavoriteSyncSettingStore.read(appContext),
        appContext,
    ) {
        val preferences = RedditFavoriteSyncSettingStore.preferences(appContext)
        // Held here: SharedPreferences keeps its listeners weakly.
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == REDDIT_FAVORITE_SYNC_ENABLED_KEY) {
                value = RedditFavoriteSyncSettingStore.read(appContext)
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        value = RedditFavoriteSyncSettingStore.read(appContext)
        awaitDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}

/**
 * REDDITFAV01_SHOW_REDDIT_FAVORITES_V1 / REDDITFAV02_FAVORITE_WRITE_V1: hands
 * the subscriptions read (Reddit's favorites, the joined communities) and
 * the sent changes to the state, gives the state the write action, and
 * reports a change Reddit refused or did not confirm in the tray.
 */
@Composable
internal fun RedditFavoriteSyncEffects(
    state: SubshellState,
    subscriptions: RedditSubscriptionsUiState,
    customFeeds: CustomFeedsUiState,
    community: CommunityEntryPoints,
    noticeHost: ViewportErrorHostState,
) {
    val enabled by rememberRedditFavoriteSyncEnabled()
    val favorites = community.redditFavoriteState
    val authenticated = state.activeAccountIsAuthenticated

    val snapshot = redditFavoriteSyncSnapshot(
        enabled = enabled && authenticated,
        subscriptions = subscriptions,
        pending = favorites.pending.takeIf { favorites.accountStableId == subscriptions.accountStableId },
    ).withCustomFeeds(
        customFeeds = customFeeds,
        pending = favorites.pendingCustomFeeds.takeIf { favorites.accountStableId == customFeeds.accountStableId },
    )
    val onUpdate = community.onUpdateRedditFavorite
    val onUpdateCustomFeed = community.onUpdateRedditCustomFeedFavorite
    SideEffect {
        if (state.redditFavoriteSync != snapshot) {
            state.redditFavoriteSync = snapshot
        }
        state.redditFavoriteWriter = onUpdate
        state.redditCustomFeedFavoriteWriter = onUpdateCustomFeed
    }

    LaunchedEffect(favorites.failureRevision) {
        val failure = favorites.failure ?: return@LaunchedEffect
        noticeHost.clear(RedditFavoriteNoticeScope)
        // REDDITFAV03_FAVORITE_AUTHORIZATION_V1: a missing grant offers Authorize.
        val authorize = community.onAuthorizeRedditFavorite
        noticeHost.present(
            scopeKey = RedditFavoriteNoticeScope,
            presentation = redditFavoriteFailurePresentation(failure),
            failureDetails = null,
            onRetry = authorize.takeIf { failure.kind == RedditFavoriteUiFailureKind.AUTHORIZATION },
        )
        community.onDismissRedditFavoriteFailure()
    }
}

internal fun redditFavoriteSyncSnapshot(
    enabled: Boolean,
    subscriptions: RedditSubscriptionsUiState,
    pending: Map<String, Boolean>?,
): RedditFavoriteSyncSnapshot {
    val accountStableId = subscriptions.accountStableId
    if (!enabled || accountStableId == null || !subscriptions.loaded) {
        return RedditFavoriteSyncSnapshot(enabled = enabled)
    }
    return RedditFavoriteSyncSnapshot(
        enabled = true,
        accountStableId = accountStableId,
        favoriteCommunityNames = subscriptions.favoriteCommunityNames,
        joinedCommunityNames = subscriptions.communities.map { community -> community.name }.toSet(),
        pending = pending.orEmpty(),
    )
}

// REDDITFAV04_CUSTOM_FEED_FAVORITES_V1: the active account's own custom
// feeds; they count only for the account the subscriptions were read for.
internal fun RedditFavoriteSyncSnapshot.withCustomFeeds(
    customFeeds: CustomFeedsUiState,
    pending: Map<String, Boolean>?,
): RedditFavoriteSyncSnapshot {
    if (!enabled || accountStableId == null || customFeeds.accountStableId != accountStableId) return this
    return copy(
        customFeedPaths = customFeeds.feeds.map { feed -> feed.path }.toSet(),
        favoriteCustomFeedPaths = customFeeds.feeds.filter { feed -> feed.redditFavorite }.map { feed -> feed.path }.toSet(),
        pendingCustomFeeds = pending.orEmpty(),
    )
}

internal const val RedditFavoriteNoticeScope = "reddit-favorite"

internal fun redditFavoriteFailurePresentation(
    failure: RedditFavoriteUiFailure,
): ErrorPresentation {
    val community = if (failure.customFeed) failure.communityName else "r/${failure.communityName}"
    return ErrorPresentation(
        kind = ErrorPresentationKind.DEGRADED,
        title = if (failure.makeFavorite) {
            "Couldn't add $community to your Reddit favorites"
        } else {
            "Couldn't remove $community from your Reddit favorites"
        },
        message = when (failure.kind) {
            RedditFavoriteUiFailureKind.FAILED ->
                "Reddit didn't accept the change, or the connection failed"
            RedditFavoriteUiFailureKind.AUTHORIZATION ->
                "subshell needs your permission to manage your communities on Reddit"
            RedditFavoriteUiFailureKind.NOT_CONFIRMED ->
                "Reddit's list didn't show the change when subshell checked"
        },
        primaryAction = ErrorAction(ErrorActionKind.RETRY, "Authorize")
            .takeIf { failure.kind == RedditFavoriteUiFailureKind.AUTHORIZATION },
    )
}
