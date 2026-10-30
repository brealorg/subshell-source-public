package app.subshell.prototype.communityposts

import android.content.Context
import android.util.Base64

/**
 * ISSUE239_SEEN_POSTS_STAY_ON_THE_DEVICE_V1: the newest post seen per account
 * and community, with when it was checked. Device-local (never in a backup):
 * a restored or new phone starts with a silent baseline.
 */
internal class CommunityPostSeenStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(COMMUNITY_POST_SEEN_PREFERENCES, Context.MODE_PRIVATE)

    /**
     * The watermark to compare with, or null for a new baseline: never
     * checked, or checked so long ago (the phone was off, the account was not
     * active) that everything since would arrive at once.
     */
    fun watermark(
        accountStableId: String,
        communityName: String,
        nowEpochMillis: Long,
        stream: CommunityStream = CommunityStream.POSTS,
    ): Long? {
        val key = key(accountStableId, communityName, stream)
        val checkedAt = preferences.getLong(key + CHECKED_AT_SUFFIX, -1L)
        if (checkedAt < 0L || nowEpochMillis - checkedAt > COMMUNITY_POST_BASELINE_MAX_AGE_MILLIS) return null
        return preferences.getLong(key, -1L).takeIf { it >= 0L }
    }

    fun save(
        accountStableId: String,
        communityName: String,
        watermark: Long?,
        nowEpochMillis: Long,
        stream: CommunityStream = CommunityStream.POSTS,
    ) {
        val key = key(accountStableId, communityName, stream)
        preferences.edit()
            .apply { if (watermark == null) remove(key) else putLong(key, watermark) }
            .putLong(key + CHECKED_AT_SUFFIX, nowEpochMillis)
            .apply()
    }

    /** Drops what was seen in communities no longer watched, so watching again starts silently. */
    fun forgetAllBut(accountStableId: String, watched: Collection<String>) {
        val prefix = accountPrefix(accountStableId)
        val keep = watched.flatMapTo(hashSetOf()) { name ->
            CommunityStream.entries.map { stream -> key(accountStableId, name, stream) }
        }
        val stale = preferences.all.keys.filter { stored ->
            stored.startsWith(prefix) && stored.removeSuffix(CHECKED_AT_SUFFIX) !in keep
        }
        if (stale.isEmpty()) return
        preferences.edit().apply { stale.forEach(::remove) }.apply()
    }

    private fun key(accountStableId: String, communityName: String, stream: CommunityStream): String =
        accountPrefix(accountStableId) + communityName.lowercase() + stream.keySuffix

    private fun accountPrefix(accountStableId: String): String =
        "seen:" + Base64.encodeToString(
            accountStableId.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        ) + ":"
}

/** What is watched in a community; each keeps its own newest-seen id. */
internal enum class CommunityStream(val keySuffix: String) {
    POSTS(""),
    // ISSUE239_NEW_COMMENTS_IN_A_WATCHED_COMMUNITY_V1
    COMMENTS("#comments"),
}

internal const val COMMUNITY_POST_SEEN_PREFERENCES = "subshell_community_posts"
private const val CHECKED_AT_SUFFIX = "@at"

/** As the Inbox (two hours): longer gaps start again silently. */
internal const val COMMUNITY_POST_BASELINE_MAX_AGE_MILLIS = 2L * 60L * 60L * 1000L
