package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.COMMENTS02_MORE_BATCH_SIZE
import app.subshell.prototype.data.auth.LIVE03_COMMENT_DEPTH
import app.subshell.prototype.data.auth.LIVE03_COMMENT_LIMIT
import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.data.auth.normalizeRedditPostFullname
import app.subshell.prototype.data.network.JsonGetRequest
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.data.network.map
import app.subshell.prototype.model.Comment
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

internal data class RedditCommentPage(
    val comments: List<Comment>,
    val suggestedSort: RedditCommentSort?,
    // ISSUE22_COMMENT_AUTHOR_FULLNAME_V1: author username -> "t2_…" account
    // fullname, for the batch avatar lookup. Transient: not part of Comment
    // and never cached.
    val authorFullnames: Map<String, String> = emptyMap(),
)

internal data class RedditMoreChildrenPage(
    val comments: List<Comment>,
    val authorFullnames: Map<String, String> = emptyMap(),
    // DATALAYER02: Reddit refused the request (json.errors, e.g. RATELIMIT)
    // but answered 200; comments is then empty and must not be merged.
    val rejectedByReddit: Boolean = false,
    // ISSUE281_THREAD_READS_IN_THE_REPORT_V1: Reddit's error code (RATELIMIT …), lowercase.
    val rejectionCode: String? = null,
)

internal class RedditCommentNetworkDataSource(
    private val httpClient: SubshellHttpClient,
    private val json: Json = SubshellNetworkJson.providerJson,
    private val clock: EpochSecondsClock = SystemEpochSecondsClock,
) {
    suspend fun comments(
        postId: String,
        sort: RedditCommentSort,
        limit: Int = LIVE03_COMMENT_LIMIT,
        depth: Int = LIVE03_COMMENT_DEPTH,
        focusedCommentId: String? = null,
        context: Int = 0,
    ): NetworkResult<RedditCommentPage> {
        require(limit in 1..REDDIT_MAX_COMMENT_LIMIT) {
            "Reddit comment limit must be between 1 and $REDDIT_MAX_COMMENT_LIMIT"
        }
        require(depth in 1..REDDIT_MAX_COMMENT_DEPTH) {
            "Reddit comment depth must be between 1 and $REDDIT_MAX_COMMENT_DEPTH"
        }
        return httpClient.getJson(
            request = redditCommentsJsonRequest(
                postId = postId,
                sort = sort,
                limit = limit,
                depth = depth,
                focusedCommentId = focusedCommentId,
                context = context,
            ),
            deserializer = ListSerializer(RedditCommentListingDto.serializer()),
        ).map { listings ->
            val authorFullnames = linkedMapOf<String, String>()
            RedditCommentPage(
                comments = listings.toComments(
                    json = json,
                    nowEpochSeconds = clock.epochSeconds(),
                    limit = limit,
                    maxDepth = depth,
                    authorFullnames = authorFullnames,
                ),
                suggestedSort = listings.suggestedCommentSort(),
                authorFullnames = authorFullnames,
            )
        }
    }

    suspend fun moreChildren(
        postId: String,
        childIds: List<String>,
        sort: RedditCommentSort,
        depth: Int = LIVE03_COMMENT_DEPTH,
    ): NetworkResult<RedditMoreChildrenPage> {
        require(depth in 1..REDDIT_MAX_COMMENT_DEPTH) {
            "Reddit comment depth must be between 1 and $REDDIT_MAX_COMMENT_DEPTH"
        }
        return httpClient.getJson(
            request = redditMoreChildrenJsonRequest(
                postId = postId,
                childIds = childIds,
                sort = sort,
                depth = depth,
            ),
            deserializer = RedditMoreChildrenResponseDto.serializer(),
        ).map { response ->
            val authorFullnames = linkedMapOf<String, String>()
            RedditMoreChildrenPage(
                comments = response.toComments(
                    jsonCodec = json,
                    nowEpochSeconds = clock.epochSeconds(),
                    maxDepth = depth,
                    authorFullnames = authorFullnames,
                ),
                authorFullnames = authorFullnames,
                rejectedByReddit = response.json.errors.isNotEmpty(),
                rejectionCode = response.json.errors.firstOrNull()?.redditErrorCodeOrNull(),
            )
        }
    }

    /**
     * ISSUE281_MORE_REPLIES_WITHOUT_MORECHILDREN_V1 (#281): Reddit answers
     * /api/morechildren with a 403 "blocked by network security" page (seen
     * 2026-10-30 with the RedReader Client ID; other third-party apps too), while a
     * thread focused on one comment still loads. This reads each of
     * [childIds] that way (comment=<id>, context=0), in parallel, and gives
     * the same page morechildren would: each child with its replies, at
     * [childDepth]. A child that is gone is left out; an id already inside an
     * earlier child's replies is not read twice. Any failed read fails all.
     */
    suspend fun moreChildrenByFocus(
        postId: String,
        childIds: List<String>,
        sort: RedditCommentSort,
        childDepth: Int,
        depth: Int = LIVE03_COMMENT_DEPTH,
    ): NetworkResult<RedditMoreChildrenPage> = coroutineScope {
        val reads = childIds.map { childId ->
            async {
                childId to comments(
                    postId = postId,
                    sort = sort,
                    depth = depth,
                    focusedCommentId = childId,
                    context = 0,
                )
            }
        }.awaitAll()
        reads.firstNotNullOfOrNull { (_, result) -> result.failureOrNull() }?.let { failure ->
            return@coroutineScope failure
        }
        val authorFullnames = linkedMapOf<String, String>()
        val children = mutableListOf<Comment>()
        val seen = mutableSetOf<String>()
        reads.forEach { (childId, result) ->
            val page = (result as NetworkResult.Success).value
            val id = childId.trim().removePrefix("t1_")
            if (id in seen) return@forEach
            val root = page.comments.findComment("t1_$id") ?: return@forEach
            val rebased = root.rebasedTo(childDepth)
            rebased.collectIds(seen)
            children += rebased
            authorFullnames.putAll(page.authorFullnames)
        }
        NetworkResult.Success(
            RedditMoreChildrenPage(comments = children, authorFullnames = authorFullnames),
            reads.first().second.let { (it as NetworkResult.Success).metadata },
        )
    }

    companion object {
        const val REDDIT_MAX_COMMENT_LIMIT = 100
        const val REDDIT_MAX_COMMENT_DEPTH = 10
    }
}

internal fun redditCommentsJsonRequest(
    postId: String,
    sort: RedditCommentSort,
    limit: Int = LIVE03_COMMENT_LIMIT,
    depth: Int = LIVE03_COMMENT_DEPTH,
    focusedCommentId: String? = null,
    context: Int = 0,
): JsonGetRequest {
    require(limit in 1..RedditCommentNetworkDataSource.REDDIT_MAX_COMMENT_LIMIT)
    require(depth in 1..RedditCommentNetworkDataSource.REDDIT_MAX_COMMENT_DEPTH)
    val articleId = normalizeRedditPostFullname(postId).removePrefix("t3_")
    require(context in 0..8) {
        "Reddit focused-comment context is outside the supported range"
    }
    val normalizedFocusedCommentId =
        normalizeRedditFocusedCommentId(focusedCommentId)

    return JsonGetRequest(
        pathSegments = listOf("comments", articleId),
        queryParameters = buildList {
                add("raw_json" to "1")
                add("limit" to limit.toString())
                add("depth" to depth.toString())
                add("sort" to sort.apiValue)

                if (normalizedFocusedCommentId != null) {
                    add("comment" to normalizedFocusedCommentId)
                    add("context" to context.toString())
                }
            },
    )
}
internal fun normalizeRedditFocusedCommentId(
    value: String?,
): String? {
    val identifier = value
        ?.trim()
        ?.removePrefix("t1_")
        ?: return null

    require(
        identifier.isNotEmpty() &&
            identifier.length <= 32 &&
            identifier.all(Char::isLetterOrDigit),
    ) {
        "Reddit focused-comment ID is invalid"
    }

    return identifier
}


internal fun redditMoreChildrenJsonRequest(
    postId: String,
    childIds: List<String>,
    sort: RedditCommentSort,
    depth: Int = LIVE03_COMMENT_DEPTH,
): JsonGetRequest {
    require(childIds.size in 1..COMMENTS02_MORE_BATCH_SIZE) {
        "Reddit more-comment batch must contain 1..$COMMENTS02_MORE_BATCH_SIZE child IDs"
    }
    require(depth in 1..RedditCommentNetworkDataSource.REDDIT_MAX_COMMENT_DEPTH)
    val normalizedChildIds = childIds.map { childId ->
        childId.trim().removePrefix("t1_").also { normalized ->
            require(normalized.isNotEmpty() && normalized.all(Char::isLetterOrDigit)) {
                "Reddit child comment ID contains unsupported characters"
            }
        }
    }.distinct()
    require(normalizedChildIds.size == childIds.size) {
        "Reddit more-comment child IDs must be unique"
    }
    return JsonGetRequest(
        pathSegments = listOf("api", "morechildren"),
        queryParameters = listOf(
            "api_type" to "json",
            "raw_json" to "1",
            "link_id" to normalizeRedditPostFullname(postId),
            "children" to normalizedChildIds.joinToString(","),
            "limit_children" to "true",
            "depth" to depth.toString(),
            "sort" to sort.apiValue,
        ),
    )
}


internal fun mergeFocusedCommentContext(
    current: List<app.subshell.prototype.model.Comment>,
    focused: List<app.subshell.prototype.model.Comment>,
    // OCB-492: true when `focused` is fresher than `current` (a network focused
    // load over the cache); shared comments then take focused's fields.
    preferFocusedFields: Boolean = false,
): List<app.subshell.prototype.model.Comment> {
    if (current.isEmpty()) return focused
    if (focused.isEmpty()) return current

    fun mergeLevel(
        base: List<app.subshell.prototype.model.Comment>,
        overlay: List<app.subshell.prototype.model.Comment>,
    ): List<app.subshell.prototype.model.Comment> {
        val overlayById = overlay.associateBy { comment -> comment.id }
        val existingIds = base.mapTo(linkedSetOf()) { comment -> comment.id }
        return buildList {
            base.forEach { baseComment ->
                val overlayComment = overlayById[baseComment.id]
                add(
                    if (overlayComment == null) {
                        baseComment
                    } else {
                        (if (preferFocusedFields) overlayComment else baseComment).copy(
                            children = mergeLevel(
                                base = baseComment.children,
                                overlay = overlayComment.children,
                            ),
                        )
                    },
                )
            }
            overlay.forEach { overlayComment ->
                if (overlayComment.id !in existingIds) add(overlayComment)
            }
        }
    }

    return mergeLevel(current, focused)
}

internal fun focusedCommentContextForTarget(
    comments: List<app.subshell.prototype.model.Comment>,
    focusedCommentId: String,
): List<app.subshell.prototype.model.Comment> {
    fun pathToTarget(
        comment: app.subshell.prototype.model.Comment,
    ): app.subshell.prototype.model.Comment? {
        if (comment.id == focusedCommentId) return comment
        val childPath = comment.children
            .firstNotNullOfOrNull { child -> pathToTarget(child) }
            ?: return null
        return comment.copy(children = listOf(childPath))
    }

    return comments.mapNotNull { comment -> pathToTarget(comment) }
}

// Reddit's json.errors entries are ["CODE", "message", "field"]; only the code is kept.
private fun JsonElement.redditErrorCodeOrNull(): String? {
    val first = (this as? JsonArray)?.firstOrNull() ?: this
    val code = (first as? JsonPrimitive)?.takeIf { it.isString }?.content
    return code?.lowercase()?.takeIf { it.matches(Regex("[a-z0-9_]{1,64}")) }
}

private fun <T> NetworkResult<T>.failureOrNull(): NetworkResult<Nothing>? = when (this) {
    is NetworkResult.Success -> null
    is NetworkResult.HttpFailure -> this
    is NetworkResult.TransportFailure -> this
    is NetworkResult.ProtocolFailure -> this
}

private fun Comment.rebasedTo(rootDepth: Int): Comment {
    val shift = rootDepth - depth
    fun rebased(comment: Comment): Comment {
        val newDepth = (comment.depth + shift).coerceAtLeast(0)
        return comment.copy(
            depth = newDepth,
            accent = redditCommentAccent(newDepth),
            children = comment.children.map(::rebased),
        )
    }
    return rebased(this)
}

private fun Comment.collectIds(into: MutableSet<String>) {
    into += id.removePrefix("t1_")
    children.forEach { it.collectIds(into) }
}
