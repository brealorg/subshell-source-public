package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.auth.normalizeRedditPostFullname
import app.subshell.prototype.data.network.JsonGetRequest
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder

// ISSUE281_READS_AVOID_API_INFO_V1 (#281, 2026-10-30): Reddit answers
// /api/info (and /by_id) with a 403 "blocked by network security" page for
// this app's bearer, while a thread read still works. A post or comment is
// read from its thread instead: /comments/<post> with one comment gives the
// post as the first listing; /comments/<post>/_/<comment> gives that comment
// as the only root of the second listing. Both listings have the shape
// /api/info had, so each caller keeps its own parsing.

/** The post, as the first listing of its thread. */
internal fun redditPostReadRequest(postId: String): JsonGetRequest = JsonGetRequest(
    pathSegments = listOf("comments", normalizeRedditPostFullname(postId).removePrefix("t3_")),
    queryParameters = listOf(
        "raw_json" to "1",
        "limit" to "1",
        "depth" to "1",
    ),
)

/** The comment, as the only root of its thread's second listing. */
internal fun redditCommentReadRequest(postId: String, commentFullname: String): JsonGetRequest {
    val commentId = commentFullname.trim().removePrefix("t1_")
    require(commentId.length in 1..32 && commentId.all(Char::isLetterOrDigit)) {
        "Reddit comment ID is invalid"
    }
    return JsonGetRequest(
        pathSegments = listOf(
            "comments",
            normalizeRedditPostFullname(postId).removePrefix("t3_"),
            "_",
            commentId.lowercase(),
        ),
        queryParameters = listOf(
            "raw_json" to "1",
            "limit" to "1",
            "depth" to "1",
            "context" to "0",
        ),
    )
}

internal const val REDDIT_THREAD_POST_LISTING = 0
internal const val REDDIT_THREAD_COMMENT_LISTING = 1

/** Decodes one listing of a thread answer (a JSON array of two listings). */
internal fun <T> redditThreadListing(
    listing: DeserializationStrategy<T>,
    index: Int,
): DeserializationStrategy<T> = object : DeserializationStrategy<T> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("RedditThreadListing$index")

    override fun deserialize(decoder: Decoder): T {
        val json = decoder as? JsonDecoder ?: throw SerializationException("A thread answer is JSON")
        val listings = json.decodeJsonElement() as? JsonArray
            ?: throw SerializationException("A thread answer is an array of listings")
        val element = listings.getOrNull(index)
            ?: throw SerializationException("A thread answer has no listing $index")
        return json.json.decodeFromJsonElement(listing, element)
    }
}
