package app.subshell.prototype.data.account

import android.content.Context
import android.content.SharedPreferences

/**
 * REDDITFAV01_DEVICE_LOCAL_REDDIT_FAVORITES_V1: the communities Reddit marks
 * as the account's favorites, as the last successful subscriptions read saw
 * them, and whether Subshell syncs favorites with Reddit at all. Device-local
 * (its own preferences file, never in a backup): Reddit owns this list, a
 * restore must not bring back an old copy of it.
 */
internal interface RedditFavoriteCommunityStore {
    fun read(accountStableId: String): Set<String>?

    fun replace(accountStableId: String, communityNames: Set<String>)

    fun remove(accountStableId: String)
}

internal object RedditFavoriteSyncSettingStore {
    fun preferences(context: Context): SharedPreferences =
        context.applicationContext
            .getSharedPreferences(REDDIT_FAVORITES_PREFERENCES, Context.MODE_PRIVATE)

    fun read(context: Context): Boolean =
        preferences(context).getBoolean(REDDIT_FAVORITE_SYNC_ENABLED_KEY, false)

    fun write(context: Context, enabled: Boolean) {
        preferences(context)
            .edit()
            .putBoolean(REDDIT_FAVORITE_SYNC_ENABLED_KEY, enabled)
            .apply()
    }
}

internal class SharedPreferencesRedditFavoriteCommunityStore(
    context: Context,
) : RedditFavoriteCommunityStore {
    private val preferences = RedditFavoriteSyncSettingStore.preferences(context)

    override fun read(accountStableId: String): Set<String>? =
        preferences.getStringSet(favoritesKey(accountStableId), null)?.toSet()

    override fun replace(accountStableId: String, communityNames: Set<String>) {
        preferences.edit()
            .putStringSet(favoritesKey(accountStableId), communityNames.toSet())
            .apply()
    }

    override fun remove(accountStableId: String) {
        preferences.edit().remove(favoritesKey(accountStableId)).apply()
    }

    private fun favoritesKey(accountStableId: String): String =
        REDDIT_FAVORITE_NAMES_KEY_PREFIX + accountStableId.trim()
}

internal const val REDDIT_FAVORITES_PREFERENCES = "subshell_reddit_favorites"
internal const val REDDIT_FAVORITE_SYNC_ENABLED_KEY = "sync_enabled"
private const val REDDIT_FAVORITE_NAMES_KEY_PREFIX = "names."
