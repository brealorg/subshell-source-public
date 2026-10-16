package app.subshell.prototype.ui

import app.subshell.prototype.data.normalizeRedditCommunityName
import app.subshell.prototype.model.Post

private const val COMMUNITY_MUTE_SEPARATOR = '|'
private const val COMMUNITY_MUTE_INDEFINITE_TOKEN = "indefinite"

const val COMMUNITY_MUTE_ONE_HOUR_MILLIS = 60L * 60L * 1_000L
const val COMMUNITY_MUTE_EIGHT_HOURS_MILLIS = 8L * COMMUNITY_MUTE_ONE_HOUR_MILLIS
const val COMMUNITY_MUTE_ONE_DAY_MILLIS = 24L * COMMUNITY_MUTE_ONE_HOUR_MILLIS
const val COMMUNITY_MUTE_THREE_DAYS_MILLIS = 3L * COMMUNITY_MUTE_ONE_DAY_MILLIS
const val COMMUNITY_MUTE_ONE_WEEK_MILLIS = 7L * COMMUNITY_MUTE_ONE_DAY_MILLIS

data class CommunityMute(
    val communityName: String,
    val expiresAtEpochMillis: Long? = null,
) {
    fun activeAt(nowEpochMillis: Long): Boolean =
        expiresAtEpochMillis == null || expiresAtEpochMillis > nowEpochMillis
}

enum class CommunityMutePreset(
    val label: String,
    val durationMillis: Long?,
) {
    ONE_HOUR("1 hour", COMMUNITY_MUTE_ONE_HOUR_MILLIS),
    EIGHT_HOURS("8 hours", COMMUNITY_MUTE_EIGHT_HOURS_MILLIS),
    ONE_DAY("24 hours", COMMUNITY_MUTE_ONE_DAY_MILLIS),
    THREE_DAYS("3 days", COMMUNITY_MUTE_THREE_DAYS_MILLIS),
    ONE_WEEK("1 week", COMMUNITY_MUTE_ONE_WEEK_MILLIS),
    INDEFINITE("Indefinitely", null),
    ;

    fun expiryFrom(nowEpochMillis: Long): Long? = durationMillis?.let { duration ->
        nowEpochMillis + duration
    }
}

internal fun normalizedCommunityMuteOrNull(mute: CommunityMute): CommunityMute? {
    val normalizedName = runCatching {
        normalizeRedditCommunityName(mute.communityName)
    }.getOrNull() ?: return null
    val normalizedExpiry = mute.expiresAtEpochMillis?.takeIf { expiry -> expiry > 0L }
        ?: if (mute.expiresAtEpochMillis == null) null else return null
    return CommunityMute(
        communityName = normalizedName,
        expiresAtEpochMillis = normalizedExpiry,
    )
}

fun normalizeCommunityMutes(mutes: Iterable<CommunityMute>): List<CommunityMute> = mutes
    .mapNotNull(::normalizedCommunityMuteOrNull)
    .groupBy { mute -> mute.communityName.lowercase() }
    .values
    .mapNotNull { sameCommunity ->
        sameCommunity.firstOrNull { mute -> mute.expiresAtEpochMillis == null }
            ?: sameCommunity.maxByOrNull { mute -> mute.expiresAtEpochMillis ?: Long.MAX_VALUE }
    }
    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, CommunityMute::communityName))

fun activeCommunityMutesAt(
    mutes: Iterable<CommunityMute>,
    nowEpochMillis: Long,
): List<CommunityMute> = normalizeCommunityMutes(mutes)
    .filter { mute -> mute.activeAt(nowEpochMillis) }

fun communityMuteFor(
    mutes: Iterable<CommunityMute>,
    communityName: String,
    nowEpochMillis: Long,
): CommunityMute? {
    val normalizedName = runCatching {
        normalizeRedditCommunityName(communityName)
    }.getOrNull() ?: return null
    return activeCommunityMutesAt(mutes, nowEpochMillis)
        .firstOrNull { mute ->
            mute.communityName.equals(normalizedName, ignoreCase = true)
        }
}

fun communityMutesForManagement(
    mutes: Iterable<CommunityMute>,
    nowEpochMillis: Long,
): List<CommunityMute> = activeCommunityMutesAt(mutes, nowEpochMillis)
    .sortedWith(
        compareBy<CommunityMute> { mute -> mute.expiresAtEpochMillis == null }
            .thenBy { mute -> mute.expiresAtEpochMillis ?: Long.MAX_VALUE }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { mute -> mute.communityName },
    )

fun activeMutedCommunityNamesAt(
    mutes: Iterable<CommunityMute>,
    nowEpochMillis: Long,
): Set<String> = activeCommunityMutesAt(mutes, nowEpochMillis)
    .mapTo(linkedSetOf(), CommunityMute::communityName)

fun encodeCommunityMutes(mutes: Iterable<CommunityMute>): Set<String> =
    normalizeCommunityMutes(mutes).mapTo(linkedSetOf()) { mute ->
        val expiry = mute.expiresAtEpochMillis?.toString() ?: COMMUNITY_MUTE_INDEFINITE_TOKEN
        "$expiry$COMMUNITY_MUTE_SEPARATOR${mute.communityName}"
    }

fun decodeCommunityMutes(encoded: Iterable<String>): List<CommunityMute> = normalizeCommunityMutes(
    encoded.mapNotNull { value ->
        val separator = value.indexOf(COMMUNITY_MUTE_SEPARATOR)
        if (separator <= 0 || separator == value.lastIndex) return@mapNotNull null
        val expiryToken = value.substring(0, separator)
        val expiry = when (expiryToken) {
            COMMUNITY_MUTE_INDEFINITE_TOKEN -> null
            else -> expiryToken.toLongOrNull()?.takeIf { it > 0L } ?: return@mapNotNull null
        }
        CommunityMute(
            communityName = value.substring(separator + 1),
            expiresAtEpochMillis = expiry,
        )
    },
)

fun filterMutedCommunityPosts(
    posts: List<Post>,
    source: FeedSource,
    mutedCommunityNames: Collection<String>,
): List<Post> {
    if (source == FeedSource.SAVED || mutedCommunityNames.isEmpty()) return posts
    val normalizedMuted = mutedCommunityNames.mapNotNullTo(linkedSetOf()) { communityName ->
        runCatching { normalizeRedditCommunityName(communityName) }.getOrNull()?.lowercase()
    }
    if (normalizedMuted.isEmpty()) return posts
    return posts.filterNot { post ->
        runCatching { normalizeRedditCommunityName(post.community) }
            .getOrNull()
            ?.lowercase() in normalizedMuted
    }
}


internal const val MUTED_COMMUNITY_DIRECT_VIEW_MODE_KEY =
    "muted_community_direct_view_mode"

enum class MutedCommunityDirectViewMode(
    val label: String,
    val description: String,
) {
    SHOW_POSTS(
        label = "Show posts",
        description = "Direct visits can still show posts while the community is muted",
    ),
    ABOUT_ONLY(
        label = "About only",
        description = "Direct visits show community information without any posts",
    ),
}

fun decodeMutedCommunityDirectViewMode(
    stored: String?,
): MutedCommunityDirectViewMode =
    MutedCommunityDirectViewMode.entries.firstOrNull { mode ->
        mode.name == stored
    } ?: MutedCommunityDirectViewMode.SHOW_POSTS

fun shouldHideMutedCommunityPosts(
    isMuted: Boolean,
    mode: MutedCommunityDirectViewMode,
): Boolean = isMuted && mode == MutedCommunityDirectViewMode.ABOUT_ONLY
