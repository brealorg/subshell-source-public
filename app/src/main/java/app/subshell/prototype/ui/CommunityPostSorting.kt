package app.subshell.prototype.ui

import app.subshell.prototype.data.normalizeRedditCommunityName
import app.subshell.prototype.model.Post
import kotlin.math.pow

fun sortCommunityPosts(posts: List<Post>, sort: CommunitySort): List<Post> = when (sort) {
    CommunitySort.CONTROVERSIAL -> posts.sortedWith(
        compareByDescending<Post> { it.score + it.comments * 4 }
            .thenBy { it.ageInMinutes() }
            .thenBy { it.id },
    )
    CommunitySort.HOT -> posts.sortedWith(
        compareByDescending<Post> { it.hotScore() }
            .thenBy { it.ageInMinutes() }
            .thenBy { it.id },
    )
    CommunitySort.NEW -> posts.sortedWith(
        compareBy<Post> { it.ageInMinutes() }
            .thenByDescending { it.score }
            .thenBy { it.id },
    )
    CommunitySort.RISING -> posts.sortedWith(
        compareByDescending<Post> { it.score / (it.comments + 1f) }
            .thenBy { it.ageInMinutes() }
            .thenBy { it.id },
    )
    CommunitySort.TOP -> posts.sortedWith(
        compareByDescending<Post> { it.score }
            .thenByDescending { it.comments }
            .thenBy { it.id },
    )
}

internal fun preferredCommunitySort(
    preferences: Map<String, CommunitySort>,
    communityName: String,
): CommunitySort {
    val normalizedName = runCatching {
        normalizeRedditCommunityName(communityName)
    }.getOrNull() ?: return CommunitySort.HOT
    return preferences[normalizedName] ?: CommunitySort.HOT
}

internal fun encodeCommunitySortPreferences(
    preferences: Map<String, CommunitySort>,
): String = normalizeCommunitySortPreferences(preferences)
    .entries
    .sortedBy { entry -> entry.key }
    .joinToString("\n") { (communityName, sort) ->
        "$communityName=${sort.name}"
    }

internal fun decodeCommunitySortPreferences(encoded: String): Map<String, CommunitySort> =
    encoded.lineSequence().mapNotNull { line ->
        val delimiterIndex = line.indexOf('=')
        if (delimiterIndex <= 0) return@mapNotNull null
        val communityName = runCatching {
            normalizeRedditCommunityName(line.substring(0, delimiterIndex))
        }.getOrNull() ?: return@mapNotNull null
        val sort = decodeCommunitySortName(line.substring(delimiterIndex + 1))
            ?: return@mapNotNull null
        communityName to sort
    }.toMap(linkedMapOf())

internal fun decodeCommunitySortName(name: String): CommunitySort? =
    if (name == LEGACY_COMMUNITY_BEST) {
        CommunitySort.HOT
    } else {
        CommunitySort.entries.firstOrNull { it.name == name }
    }

private fun normalizeCommunitySortPreferences(
    preferences: Map<String, CommunitySort>,
): Map<String, CommunitySort> = preferences.entries.mapNotNull { (communityName, sort) ->
    runCatching {
        normalizeRedditCommunityName(communityName) to sort
    }.getOrNull()
}.toMap(linkedMapOf())

private fun Post.hotScore(): Double {
    val ageHours = ageInMinutes() / 60.0
    val engagement = score.coerceAtLeast(0) + comments.coerceAtLeast(0) * 2.0
    return engagement / (ageHours + 2.0).pow(1.35)
}

private fun Post.ageInMinutes(): Int {
    val match = AGE_PATTERN.matchEntire(age.trim()) ?: return Int.MAX_VALUE
    val amount = match.groupValues[1].toIntOrNull() ?: return Int.MAX_VALUE
    return when (match.groupValues[2]) {
        "m" -> amount
        "h" -> amount * 60
        "d" -> amount * 24 * 60
        "w" -> amount * 7 * 24 * 60
        else -> Int.MAX_VALUE
    }
}

private val AGE_PATTERN = Regex("^(\\d+)(m|h|d|w)$")
private const val LEGACY_COMMUNITY_BEST = "BEST"
