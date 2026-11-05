package io.github.brealorg.subshell.data.network.reddit

import io.github.brealorg.subshell.data.account.RedditAccessProbe
import io.github.brealorg.subshell.data.account.RedditAccessProbeOutcome
import io.github.brealorg.subshell.data.network.JsonGetRequest
import io.github.brealorg.subshell.data.network.NetworkResult
import io.github.brealorg.subshell.data.network.SubshellHttpClient
import java.util.Locale
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// REDDITCHECK01_CHECK_REDDIT_ACCESS_V1: one GET for each kind of read the app
// uses, one at a time, in this order. The post, comment and account name the
// later reads need come from the earlier answers (the newest Best post, its
// first comment, /api/v1/me) and are never kept; a read whose input is missing
// is SKIPPED. The client must not retry, so a 429 or 403 shows as Reddit sent it.

internal val REDDIT_ACCESS_PROBE_ENDPOINTS = listOf(
    "me",
    "prefs",
    "subscriptions",
    "best",
    "popular",
    "community_about",
    "thread",
    "api_info",
    "by_id",
    "morechildren",
    "duplicates",
    "user_about",
    "user_comments",
    "inbox",
    "search_posts",
    "search_communities",
    "multis",
)

internal class RedditAccessProbeNetworkDataSource(
    private val client: SubshellHttpClient,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    /** Every probe in [REDDIT_ACCESS_PROBE_ENDPOINTS] order, or null once [isCurrent] turns false. */
    suspend fun probe(isCurrent: () -> Boolean): List<RedditAccessProbe>? {
        val probes = mutableListOf<RedditAccessProbe>()

        suspend fun ask(endpoint: String, request: JsonGetRequest?): JsonElement? {
            if (request == null) {
                probes += RedditAccessProbe(endpoint, RedditAccessProbeOutcome.SKIPPED)
                return null
            }
            val started = nanoTime()
            val result = client.getJson(request, JsonElement.serializer())
            val elapsedMs = ((nanoTime() - started) / 1_000_000L).coerceAtLeast(0L)
            probes += when (result) {
                is NetworkResult.Success ->
                    RedditAccessProbe(endpoint, RedditAccessProbeOutcome.ANSWERED, result.metadata.statusCode, null, elapsedMs)
                is NetworkResult.HttpFailure ->
                    RedditAccessProbe(endpoint, RedditAccessProbeOutcome.HTTP_FAILURE, result.statusCode, result.providerReason, elapsedMs)
                is NetworkResult.ProtocolFailure ->
                    RedditAccessProbe(endpoint, RedditAccessProbeOutcome.PROTOCOL_FAILURE, null, result.kind.token(), elapsedMs)
                is NetworkResult.TransportFailure ->
                    RedditAccessProbe(endpoint, RedditAccessProbeOutcome.TRANSPORT_FAILURE, null, result.kind.token(), elapsedMs)
            }
            return (result as? NetworkResult.Success)?.value
        }

        fun get(vararg path: String, query: List<Pair<String, String>> = emptyList()) =
            JsonGetRequest(pathSegments = path.toList(), queryParameters = listOf("raw_json" to "1") + query)

        val one = listOf("limit" to "1")
        val me = ask("me", get("api", "v1", "me"))
        val name = me.objectField("name")?.takeIf { it.matches(REDDIT_USER_NAME) }
        if (!isCurrent()) return null
        ask("prefs", nsfwPreferencesGetRequest())
        ask("subscriptions", get("subreddits", "mine", "subscriber", query = one))
        val best = ask("best", get("best", query = one))
        if (!isCurrent()) return null
        ask("popular", get("r", "popular", "hot", query = one))
        val post = best.firstListingChild()
        val postId = post.objectField("id")?.takeIf { it.matches(REDDIT_THING_ID) }
        val community = post.objectField("subreddit")?.takeIf { it.matches(REDDIT_COMMUNITY_NAME) }
        ask("community_about", community?.let { get("r", it, "about") })
        val thread = ask("thread", postId?.let { redditPostReadRequest(it) })
        if (!isCurrent()) return null
        val commentId = (thread as? JsonArray)?.getOrNull(1).firstListingChild(kind = "t1")
            .objectField("id")?.takeIf { it.matches(REDDIT_THING_ID) }
        ask("api_info", postId?.let { get("api", "info", query = listOf("id" to "t3_$it")) })
        ask("by_id", postId?.let { get("by_id", "t3_$it") })
        ask(
            "morechildren",
            if (postId != null && commentId != null) {
                get(
                    "api", "morechildren",
                    query = listOf("api_type" to "json", "link_id" to "t3_$postId", "children" to commentId),
                )
            } else {
                null
            },
        )
        ask("duplicates", postId?.let { get("duplicates", it, query = one) })
        if (!isCurrent()) return null
        ask("user_about", name?.let { get("user", it, "about") })
        ask("user_comments", name?.let { get("user", it, "comments", query = one) })
        ask("inbox", get("message", "inbox", query = one))
        if (!isCurrent()) return null
        ask("search_posts", get("search", query = one + ("q" to "reddit")))
        ask("search_communities", get("subreddits", "search", query = one + ("q" to "reddit")))
        ask("multis", get("api", "multi", "mine"))
        return probes.takeIf { isCurrent() }
    }
}

private val REDDIT_USER_NAME = Regex("[A-Za-z0-9_-]{3,20}")
private val REDDIT_THING_ID = Regex("[a-z0-9]{1,16}")
private val REDDIT_COMMUNITY_NAME = Regex("[A-Za-z0-9_]{2,21}")

private fun Enum<*>.token(): String = name.lowercase(Locale.ROOT)

private fun JsonElement?.objectField(name: String): String? =
    ((this as? JsonObject)?.get(name) as? JsonPrimitive)?.takeIf { it.isString }?.content

/** The `data` of the first child of a Listing, if it is of [kind]. */
private fun JsonElement?.firstListingChild(kind: String = "t3"): JsonObject? {
    val children = ((this as? JsonObject)?.get("data") as? JsonObject)?.get("children") as? JsonArray
    val child = children?.firstOrNull() as? JsonObject ?: return null
    if ((child["kind"] as? JsonPrimitive)?.content != kind) return null
    return child["data"] as? JsonObject
}
