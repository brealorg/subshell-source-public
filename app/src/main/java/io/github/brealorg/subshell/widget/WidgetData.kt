package io.github.brealorg.subshell.widget

import android.content.Context
import androidx.core.content.edit
import io.github.brealorg.subshell.applock.AppLockSettingsStore
import io.github.brealorg.subshell.applock.hidesNotificationContent
import io.github.brealorg.subshell.auth.AuthApplicationRuntimeFactory
import io.github.brealorg.subshell.data.RedditMainFeed
import io.github.brealorg.subshell.data.RepositoryQueryKey
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.cache.processRuntimeCacheStore
import io.github.brealorg.subshell.data.normalizeRedditCommunityName
import io.github.brealorg.subshell.data.scopedToAccount
import io.github.brealorg.subshell.model.Community
import io.github.brealorg.subshell.ui.SubshellSettings
import io.github.brealorg.subshell.ui.activeMutedCommunityNamesAt
import io.github.brealorg.subshell.ui.communityMutesPreferenceKey
import io.github.brealorg.subshell.ui.decodeCommunityMutes
import io.github.brealorg.subshell.ui.hiddenCommunityNamesPreferenceKey
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val SUBSHELL_PREFERENCES = "subshell_preferences"

/**
 * ISSUE267_WIDGETS_SHOW_WHAT_THE_APP_HAS_STORED_V1: reads for the widgets,
 * all from this device's stores, off the main thread. Nothing here talks to
 * Reddit.
 */
internal object SubshellWidgetData {
    /** The active account when it is a signed-in Reddit account, as the app's runtime sees it. */
    suspend fun activeAccount(context: Context): String? = withContext(Dispatchers.IO) {
        try {
            val directory = AuthApplicationRuntimeFactory.create(context).accountSessionDirectory
                ?: return@withContext null
            val snapshot = directory.hydratedSnapshot()
            snapshot.accounts
                .firstOrNull { account ->
                    account.stableId == snapshot.activeAccountId &&
                        account.source == AccountSessionSource.AUTHENTICATED
                }
                ?.stableId
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    fun hidesContent(context: Context): Boolean =
        AppLockSettingsStore.read(context).hidesNotificationContent

    suspend fun inbox(context: Context): InboxWidgetContent {
        if (hidesContent(context)) return InboxWidgetContent.Locked
        val account = activeAccount(context)
        val snapshot = account?.let { id ->
            withContext(Dispatchers.IO) {
                runCatching { processRuntimeCacheStore(context).liveInboxSnapshot(id) }.getOrNull()
            }
        }
        return inboxWidgetContent(
            accountStableId = account,
            items = snapshot?.items,
            locallySeenIds = snapshot?.locallySeenIds.orEmpty(),
            hidesContent = false,
        )
    }

    suspend fun posts(context: Context, source: PostsWidgetSource): PostsWidgetContent {
        if (hidesContent(context)) return PostsWidgetContent.Locked
        val account = activeAccount(context)
            ?: return postsWidgetContent(null, source, null, false, false, emptySet())
        val snapshot = withContext(Dispatchers.IO) {
            runCatching {
                processRuntimeCacheStore(context).newestPostFeedSnapshot(postsWidgetQueryPrefix(account, source))
            }.getOrNull()
        }
        val preferences = context.getSharedPreferences(SUBSHELL_PREFERENCES, Context.MODE_PRIVATE)
        val hidden = preferences.getStringSet(hiddenCommunityNamesPreferenceKey(account), emptySet()).orEmpty()
        val muted = activeMutedCommunityNamesAt(
            decodeCommunityMutes(preferences.getStringSet(communityMutesPreferenceKey(account), emptySet()).orEmpty()),
            System.currentTimeMillis(),
        )
        return postsWidgetContent(
            accountStableId = account,
            source = source,
            posts = snapshot?.posts,
            hidesContent = false,
            showNsfwPosts = preferences.getBoolean(SubshellSettings.SHOW_NSFW_POSTS, false),
            hiddenCommunities = hidden + muted,
        )
    }

    /** The signed-in account's joined communities, for the Posts widget's picker; hidden ones left out. */
    suspend fun joinedCommunities(context: Context): List<Community> {
        val account = activeAccount(context) ?: return emptyList()
        val hidden = context.getSharedPreferences(SUBSHELL_PREFERENCES, Context.MODE_PRIVATE)
            .getStringSet(hiddenCommunityNamesPreferenceKey(account), emptySet())
            .orEmpty()
            .mapTo(HashSet()) { name -> name.removePrefix("r/").lowercase() }
        return withContext(Dispatchers.IO) {
            runCatching { processRuntimeCacheStore(context).subscriptionSnapshot(account)?.communities }
                .getOrNull()
                .orEmpty()
                .filter { community -> community.name.removePrefix("r/").lowercase() !in hidden }
                .sortedBy { community -> community.name.lowercase() }
        }
    }
}

/**
 * Every stored feed of [source] for [account] shares this prefix, whatever
 * its sort: Home is `main|home|<sort>`, a community `live04|community|<name>|<sort>`.
 */
internal fun postsWidgetQueryPrefix(account: String, source: PostsWidgetSource): String {
    val place = when (val community = source.community) {
        null -> "main|${RedditMainFeed.HOME.queryToken}|"
        else -> "live04|community|${normalizeRedditCommunityName(community)}|"
    }
    return RepositoryQueryKey(place)
        .scopedToAccount(stableId = account, source = AccountSessionSource.AUTHENTICATED)
        .value
}

/** Which place each Posts widget shows; device-local, never backed up. */
internal object PostsWidgetSourceStore {
    private const val PREFERENCES = "subshell_widgets"
    private const val COMMUNITY_KEY_PREFIX = "posts_widget_community."

    fun read(context: Context, appWidgetId: Int): PostsWidgetSource =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(COMMUNITY_KEY_PREFIX + appWidgetId, null)
            ?.removePrefix("r/")
            ?.takeIf { name -> runCatching { normalizeRedditCommunityName(name) }.isSuccess }
            ?.let { name -> PostsWidgetSource(community = name) }
            ?: PostsWidgetSource.Home

    fun write(context: Context, appWidgetId: Int, source: PostsWidgetSource) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit {
            // Kept as the community writes its name; lookups normalize it.
            val community = source.community?.removePrefix("r/")
                ?.takeIf { name -> runCatching { normalizeRedditCommunityName(name) }.isSuccess }
            if (community == null) remove(COMMUNITY_KEY_PREFIX + appWidgetId)
            else putString(COMMUNITY_KEY_PREFIX + appWidgetId, community)
        }
    }

    fun forget(context: Context, appWidgetIds: IntArray) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit {
            appWidgetIds.forEach { id -> remove(COMMUNITY_KEY_PREFIX + id) }
        }
    }
}
