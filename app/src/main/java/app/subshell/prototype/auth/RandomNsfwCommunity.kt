package app.subshell.prototype.auth

import app.subshell.prototype.model.CommunityDiscoveryItem
import kotlin.random.Random

// ISSUE211_RANDOM_NSFW_COMMUNITY_V1 (#211, owner: behind a setting, off by
// default, only while NSFW is shown): candidates come from Reddit's own
// listings (Popular, else a community search) fetched with
// include_over_18=on, kept only when Reddit marks them over18. No
// hard-coded lists. CommunityAccessCoordinator passes isAvailable = true:
// picks are not checked against their about page (owner: speed).

internal const val RANDOM_NSFW_COMMUNITY_MAX_CHECKS = 3

/**
 * A random NSFW community from [candidates] that [isAvailable] confirms,
 * skipping [previous] when there is any other choice; null when none is.
 */
internal suspend fun pickRandomNsfwCommunity(
    candidates: List<CommunityDiscoveryItem>,
    previous: String?,
    random: Random,
    isAvailable: suspend (String) -> Boolean,
    maxChecks: Int = RANDOM_NSFW_COMMUNITY_MAX_CHECKS,
): String? {
    val names = candidates
        .filter { item -> item.nsfw }
        .map { item -> item.community.name }
        .distinctBy(String::lowercase)
    val choices = names
        .filterNot { name -> previous != null && name.equals(previous, ignoreCase = true) }
        .ifEmpty { names }
        .shuffled(random)
    return choices.take(maxChecks).firstOrNull { name -> isAvailable(name) }
}
