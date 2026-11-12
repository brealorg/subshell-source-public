package io.github.brealorg.subshell.data.network.reddit

import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.JsonPrimitive
import io.github.brealorg.subshell.data.RedditCommentSort
import io.github.brealorg.subshell.data.redditSuggestedCommentSort
import io.github.brealorg.subshell.model.Comment
import io.github.brealorg.subshell.model.ResolvedRedditMedia
import io.github.brealorg.subshell.model.annotateRedditMedia
import io.github.brealorg.subshell.model.isContinueThreadNode
import io.github.brealorg.subshell.model.isMoreRepliesNode
import io.github.brealorg.subshell.model.continueThreadNode
import io.github.brealorg.subshell.model.parseRedditMediaPlaceholders
import io.github.brealorg.subshell.model.redditSourceMarkdown
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

@Serializable
internal data class RedditCommentListingDto(
    val data: RedditCommentListingDataDto = RedditCommentListingDataDto(),
)

@Serializable
internal data class RedditCommentListingDataDto(
    val children: List<RedditCommentThingDto> = emptyList(),
)

@Serializable
internal data class RedditCommentThingDto(
    val kind: String? = null,
    val data: RedditCommentDataDto = RedditCommentDataDto(),
)

@Serializable
internal data class RedditCommentDataDto(
    val id: String = "",
    val name: String? = null,
    val author: String? = null,
    // ISSUE22_COMMENT_AUTHOR_FULLNAME_V1
    @SerialName("author_fullname") val authorFullname: String? = null,
    val body: String = "",
    val score: Long = 0L,
    val likes: Boolean? = null,
    // COMMENTSAVE01A1_SERVER_SAVED_STATE_V1
    val saved: Boolean = false,
    @SerialName("created_utc") val createdUtc: Double? = null,
    val depth: Int = 0,
    val distinguished: String? = null,
    @SerialName("is_submitter") val isSubmitter: Boolean = false,
    @SerialName("total_awards_received") val totalAwardsReceived: Int = 0,
    @SerialName("parent_id") val parentId: String? = null,
    @SerialName("suggested_sort") val suggestedSort: String? = null,
    val count: Int = 0,
    val children: List<String> = emptyList(),
    @SerialName("media_metadata") val mediaMetadata: Map<String, RedditMediaMetadataDto> = emptyMap(),
    // EDITREADBACK01_EDITED_STAMP_V1: false, or epoch seconds of the last edit.
    val edited: JsonElement? = null,
    val replies: JsonElement = JsonNull,
    // USERFLAIR01A2_REDDIT_COMMENT_AUTHOR_FLAIR_V1
    @SerialName("author_flair_text")
    val authorFlairText: String? = null,
    @SerialName("author_flair_text_color")
    val authorFlairTextColor: String? = null,
    @SerialName("author_flair_background_color")
    val authorFlairBackgroundColor: String? = null,
    // USERFLAIR01A3A_COMMENT_AUTHOR_RICHTEXT_V1
    @SerialName("author_flair_richtext")
    val authorFlairRichText: List<RedditFlairRichTextDto> = emptyList(),
    // ISSUE226_REMOVED_COMMENT_IS_MARKED_V1: sent to moderators only;
    // banned_by is the remover's name, true, or absent.
    val removed: Boolean = false,
    @SerialName("banned_by") val bannedBy: JsonElement? = null,
)

internal fun List<RedditCommentListingDto>.suggestedCommentSort(): RedditCommentSort? =
    firstOrNull()
        ?.data
        ?.children
        ?.firstOrNull()
        ?.data
        ?.suggestedSort
        .let(::redditSuggestedCommentSort)

@Serializable
internal data class RedditMoreChildrenResponseDto(
    val json: RedditMoreChildrenJsonDto = RedditMoreChildrenJsonDto(),
)

@Serializable
internal data class RedditMoreChildrenJsonDto(
    val errors: List<JsonElement> = emptyList(),
    val data: RedditMoreChildrenDataDto = RedditMoreChildrenDataDto(),
)

@Serializable
internal data class RedditMoreChildrenDataDto(
    val things: List<RedditCommentThingDto> = emptyList(),
)

internal fun List<RedditCommentListingDto>.toComments(
    json: Json,
    nowEpochSeconds: Long,
    limit: Int,
    maxDepth: Int,
    authorFullnames: MutableMap<String, String>? = null,
): List<Comment> {
    require(limit in 1..RedditCommentNetworkDataSource.REDDIT_MAX_COMMENT_LIMIT) {
        "Reddit comment limit is outside the supported range"
    }
    require(maxDepth in 1..RedditCommentNetworkDataSource.REDDIT_MAX_COMMENT_DEPTH) {
        "Reddit comment depth is outside the supported range"
    }
    val budget = CommentMappingBudget(limit)
    return getOrNull(REDDIT_COMMENT_LISTING_INDEX)
        ?.data
        ?.toComments(
            json = json,
            nowEpochSeconds = nowEpochSeconds,
            maxDepth = maxDepth,
            treeDepth = 0,
            budget = budget,
            authorFullnames = authorFullnames,
        )
        .orEmpty()
}

private fun RedditCommentListingDataDto.toComments(
    json: Json,
    nowEpochSeconds: Long,
    maxDepth: Int,
    treeDepth: Int,
    budget: CommentMappingBudget,
    authorFullnames: MutableMap<String, String>?,
): List<Comment> = buildList {
    // DATALAYER06: comments past the mapping budget are kept loadable through
    // a more node instead of vanishing (Reddit's limit is approximate).
    val overBudget = mutableListOf<RedditCommentDataDto>()
    for (thing in children) {
        if (thing.kind == REDDIT_MORE_KIND) {
            thing.toMoreComment(maxDepth)?.let { more ->
                if (none { existing -> existing.id == more.id }) add(more)
            }
            continue
        }
        if (thing.kind != null && thing.kind != REDDIT_COMMENT_KIND) continue
        if (budget.exhausted) {
            overBudget += thing.data
            continue
        }
        val data = thing.data
        val commentId = data.name
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: data.id.trim().takeIf(String::isNotEmpty)?.let { id -> "t1_$id" }
            ?: continue
        // LOW_NET_BLANK_COMMENT_KEEPS_ITS_REPLIES_V1 (OCB-631): a blank
        // comment is dropped only when nothing hangs under it.
        val projectedBody = data.bodyWithRedditMediaMetadata()
            .ifBlank { if (data.hasReplies(json)) BLANK_COMMENT_BODY else "" }
        if (projectedBody.isBlank()) continue
        budget.consume()
        // An exhausted budget still maps the replies: they come back as one
        // more node, so the last comment's replies stay loadable.
        val children = if (treeDepth < maxDepth) {
            data.replies
                .takeIf { replies -> replies is JsonObject }
                ?.let { replies ->
                    runCatching {
                        json.decodeFromJsonElement(
                            RedditCommentListingDto.serializer(),
                            replies,
                        )
                    }.getOrNull()
                }
                ?.data
                ?.toComments(
                    json = json,
                    nowEpochSeconds = nowEpochSeconds,
                    maxDepth = maxDepth,
                    treeDepth = treeDepth + 1,
                    budget = budget,
                    authorFullnames = authorFullnames,
                )
                .orEmpty()
        } else if (data.hasReplies(json)) {
            // ISSUE3_CONTINUE_THREAD_NODE_V1: past the requested depth the
            // replies are not mapped; keep a node that loads them on request.
            listOf(
                continueThreadNode(
                    parentFullname = commentId,
                    depth = data.depth.coerceIn(0, maxDepth) + 1,
                    accent = redditCommentAccent(data.depth.coerceIn(0, maxDepth) + 1),
                ),
            )
        } else {
            emptyList()
        }
        val author = data.author
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: "[deleted]"
        if (authorFullnames != null && author != "[deleted]") {
            data.authorFullname.redditAccountFullnameOrNull()?.let { fullname ->
                authorFullnames.putIfAbsent(author, fullname)
            }
        }
        add(
            Comment(
                id = commentId,
                author = author,
                authorFlairText =
                    data.authorFlairText
                        ?.trim()
                        ?.takeIf(String::isNotEmpty),
                authorFlairTextColor =
                    data.authorFlairTextColor
                        ?.trim()
                        ?.takeIf(String::isNotEmpty),
                authorFlairBackgroundColor =
                    data.authorFlairBackgroundColor
                        ?.trim()
                        ?.takeIf(String::isNotEmpty),
                authorFlairRichText =
                    data.authorFlairRichText.toFlairRichTextSegments(),
                age = redditRelativeAgeLabel(
                    nowEpochSeconds,
                    data.createdUtc?.toLong(),
                ),
                createdEpochSeconds = data.createdUtc?.toLong(),
                body = projectedBody,
                score = data.score
                    .coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())
                    .toInt(),
                vote = when (data.likes) {
                    true -> 1
                    false -> -1
                    null -> 0
                },
                saved = data.saved,
                depth = data.depth.coerceIn(0, maxDepth),
                accent = redditCommentAccent(data.depth.coerceIn(0, maxDepth)),
                award = data.totalAwardsReceived
                    .takeIf { count -> count > 0 }
                    ?.let { count -> if (count == 1) "1 award" else "$count awards" },
                isOp = data.isSubmitter,
                isModerator = data.distinguished == "moderator",
                removedByModerator = data.removed || data.bannedBy.marksRemoval(),
                children = children,
                parentId = data.parentId?.trim()?.takeIf(String::isNotEmpty),
            ),
        )
    }
    // LOW_NET_OVER_BUDGET_REPLIES_STAY_LOADABLE_V1 (OCB-630): morechildren is
    // asked with limit_children=true, which returns only the listed ids, so
    // the replies embedded under an over-budget comment are listed too
    // (parents first; the expansion nests them by parent id).
    val overBudgetIds = overBudget
        .flatMap { data -> data.idWithEmbeddedReplyIds(json) }
        .distinct()
    if (overBudgetIds.isNotEmpty()) {
        val redditMore = indexOfLast { comment -> comment.moreChildIds.isNotEmpty() }
        if (redditMore >= 0) {
            val existing = this[redditMore]
            this[redditMore] = existing.copy(
                moreChildIds = (overBudgetIds + existing.moreChildIds).distinct(),
                moreCount = existing.moreCount + overBudgetIds.size,
            )
        } else {
            val first = overBudget.first()
            val parentId = first.parentId?.trim()?.takeIf(String::isNotEmpty)
            add(
                Comment(
                    id = "more:${parentId ?: "root"}:${overBudgetIds.first()}",
                    author = "More replies",
                    age = "",
                    body = "",
                    score = 0,
                    depth = first.depth.coerceIn(0, maxDepth),
                    accent = redditCommentAccent(first.depth.coerceIn(0, maxDepth)),
                    parentId = parentId,
                    moreChildIds = overBudgetIds,
                    moreCount = overBudgetIds.size,
                ),
            )
        }
    }
}

private val redditNativeProcessingImageRegex = Regex(
    pattern = "(?i)^\\s*\\*?Processing\\s+img\\s+([A-Za-z0-9]+)(?:\\.\\.\\.)?\\*?\\s*$",
)

// COMPOSER11_MEDIA_COMMENT_READ_BACK_V1: a body with a line Reddit writes
// while an uploaded image/GIF is still being processed.
internal fun redditCommentBodyStillProcessing(body: String): Boolean =
    body.lines().any { line -> redditNativeProcessingImageRegex.matches(line) }

// ISSUE24_GIPHY_COMMENT_MEDIA_V1
// A Giphy comment arrives as `![gif](giphy|<id>)` (optionally with a size
// hint, `giphy|<id>|downsized`). Reddit does send media_metadata for it, but
// the entry is a stub — status "invalid", no kind, no source URL — so there
// is nothing to look up: the id in the placeholder is the only address of
// the GIF. Verified against 25 comments of the thread in issue #24.
/** The canonical GIF address of a Giphy asset id. */
internal fun redditGiphyMediaUrl(assetId: String): String =
    "https://i.giphy.com/media/$assetId/giphy.gif"

internal fun RedditCommentDataDto.bodyWithRedditMediaMetadata(): String =
    body.withRedditMediaAnnotations(mediaMetadata, appendUnplaced = true)

/**
 * ISSUE8_INLINE_REDDIT_MEDIA_V1: every placeholder Reddit wrote keeps its
 * text and gains its address as a tagged title (see RedditMediaMarkup.kt);
 * [appendUnplaced] adds media listed in media_metadata but absent from the
 * body (comments only: a post's media_metadata also carries its gallery).
 * Giphy placeholders carry their own address (ISSUE24_GIPHY_COMMENT_MEDIA_V1).
 */
internal fun String.withRedditMediaAnnotations(
    mediaMetadata: Map<String, RedditMediaMetadataDto>,
    appendUnplaced: Boolean,
): String {
    val remoteMedia = mediaMetadata.entries
        .mapNotNull { (assetId, metadata) ->
            metadata.remoteImageUrl()?.let { url ->
                assetId.lowercase() to ResolvedRedditMedia(
                    url = url,
                    width = metadata.source?.width,
                    height = metadata.source?.height,
                )
            }
        }
        .toMap()
    // A "Processing img <id>" line whose media Reddit has since resolved
    // becomes that media's placeholder, in the same place.
    val source = redditSourceMarkdown()
        .lineSequence()
        .map { line ->
            val processing = redditNativeProcessingImageRegex.matchEntire(line)
            if (processing != null && processing.groupValues[1].lowercase() in remoteMedia) {
                "![img](${processing.groupValues[1]})"
            } else {
                line
            }
        }
        .joinToString("\n")
    val placedTargets = parseRedditMediaPlaceholders(source)
        .map { placeholder -> placeholder.target.lowercase() }
        .toSet()
    val sourceLines = source
        .lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toSet()
    val unplaced = if (appendUnplaced) {
        remoteMedia.entries
            .filter { (target, media) -> target !in placedTargets && media.url !in sourceLines }
            .distinctBy { (_, media) -> media.url }
            .map { (target, media) -> target to media }
    } else {
        emptyList()
    }
    return annotateRedditMedia(
        body = source,
        resolve = { target ->
            val key = target.lowercase()
            if (key.startsWith("giphy|")) {
                target.split('|').getOrNull(1)
                    ?.takeIf { id -> id.isNotEmpty() && id.all(Char::isLetterOrDigit) }
                    ?.let { id -> ResolvedRedditMedia(url = redditGiphyMediaUrl(id)) }
            } else {
                remoteMedia[key]
            }
        },
        unplaced = unplaced,
    )
}

internal fun RedditMoreChildrenResponseDto.toComments(
    jsonCodec: Json,
    nowEpochSeconds: Long,
    maxDepth: Int,
    authorFullnames: MutableMap<String, String>? = null,
): List<Comment> {
    if (json.errors.isNotEmpty()) return emptyList()
    val budget = CommentMappingBudget(RedditCommentNetworkDataSource.REDDIT_MAX_COMMENT_LIMIT)
    val mapped = RedditCommentListingDataDto(children = json.data.things).toComments(
        json = jsonCodec,
        nowEpochSeconds = nowEpochSeconds,
        maxDepth = maxDepth,
        treeDepth = 0,
        budget = budget,
        authorFullnames = authorFullnames,
    )
    return assembleExpandedCommentForest(mapped)
}

/**
 * DATALAYER02: a refused expansion is not an empty one. Merging it would
 * replace the more node with nothing and drop the requested replies from
 * the cached thread; null keeps the thread as it is.
 */
internal fun mergeMoreChildrenPage(
    current: List<Comment>,
    moreNodeId: String,
    page: RedditMoreChildrenPage,
    requestedChildCount: Int,
): List<Comment>? =
    if (page.rejectedByReddit) {
        null
    } else {
        mergeExpandedComments(
            current = current,
            moreNodeId = moreNodeId,
            expanded = page.comments,
            requestedChildCount = requestedChildCount,
        )
    }

/**
 * ISSUE3_CONTINUE_THREAD_NODE_V1: grafts the replies of a thread focused on
 * the continue node's parent (comment=<parent>, context=0) in place of the
 * node, re-based to the node's depth. Null when the focused answer does not
 * contain the parent.
 */
internal fun mergeContinuedThread(
    current: List<Comment>,
    continueNodeId: String,
    focused: List<Comment>,
): List<Comment>? {
    val node = current.findComment(continueNodeId)?.takeIf { it.isContinueThreadNode } ?: return null
    val root = focused.findComment(node.parentId ?: return null) ?: return null
    val shift = node.depth - (root.depth + 1)
    fun rebased(comment: Comment): Comment {
        val depth = (comment.depth + shift).coerceAtLeast(0)
        return comment.copy(
            depth = depth,
            accent = redditCommentAccent(depth),
            children = comment.children.map(::rebased),
        )
    }
    val replies = root.children.map(::rebased)
    // AUDIT298A_NO_COMMENT_IS_ASKED_FOR_TWICE_V1: a reply already in the thread
    // (the reader's own, posted under the comment) is not added again.
    val placement = placeExpandedComments(current, node, replies)
    var replaced = false
    fun replace(nodes: List<Comment>): List<Comment> = buildList {
        nodes.forEach { candidate ->
            if (!replaced && candidate.id == continueNodeId) {
                replaced = true
                addAll(placement.atNode)
            } else {
                add(candidate.copy(children = replace(candidate.children) + placement.underParent(candidate)))
            }
        }
    }
    val merged = replace(current)
    return merged.takeIf { replaced }
}

internal fun mergeExpandedComments(
    current: List<Comment>,
    moreNodeId: String,
    expanded: List<Comment>,
    requestedChildCount: Int,
): List<Comment> {
    require(moreNodeId.isNotBlank()) { "More-comment node ID cannot be blank" }
    require(requestedChildCount > 0) { "More-comment request must contain child IDs" }
    var replaced = false
    val node = current.findComment(moreNodeId)
    // AUDIT298A_NO_COMMENT_IS_ASKED_FOR_TWICE_V1 / ..._ORPHANS_FIND_THEIR_PARENT_V1:
    // what is already in the thread is not added again, and a comment whose
    // parent is elsewhere in the thread goes under it (a batch can cut a
    // parent from its child); the rest go at the node's place, at its depth.
    val placement = node?.let { placeExpandedComments(current, it, expanded) }
    val atNode = placement?.atNode ?: expanded

    fun replace(nodes: List<Comment>): List<Comment> = buildList {
        nodes.forEach { node ->
            if (node.id == moreNodeId && node.moreChildIds.isNotEmpty()) {
                replaced = true
                addAll(atNode)
                val remainingChildIds = node.moreChildIds.drop(requestedChildCount)
                val remainingCount = (node.moreCount - requestedChildCount)
                    .coerceAtLeast(remainingChildIds.size)
                if (remainingChildIds.isNotEmpty()) {
                    add(
                        node.copy(
                            moreChildIds = remainingChildIds,
                            moreCount = remainingCount,
                        ),
                    )
                }
            } else {
                add(node.copy(children = replace(node.children) + placement.underParent(node)))
            }
        }
    }

    val merged = replace(current)
    require(replaced) { "Requested more-comment node is not present in cache" }
    return merged
}

/**
 * AUDIT298A_ORPHANS_FIND_THEIR_PARENT_V1 (audit 2026-11-12): where the comments
 * of a more node's answer go. Comments already in [current] are left out (their
 * new replies go under the copy in the thread); a comment whose parent is in
 * the thread goes under that parent; everything else at the node's place,
 * re-based to the node's depth (Reddit's depth is clamped; a node inside a
 * continued thread sits deeper).
 */
internal class ExpandedPlacement(
    val atNode: List<Comment>,
    private val byParent: Map<String, List<Comment>>,
) {
    fun underParent(parent: Comment): List<Comment> {
        val children = byParent[parent.id.trim().removePrefix("t1_")].orEmpty()
        return children.map { child -> child.atDepth(parent.depth + 1) }
    }
}

internal fun ExpandedPlacement?.underParent(parent: Comment): List<Comment> =
    this?.underParent(parent).orEmpty()

/** The comment at [depth], its replies shifted with it (Reddit's depth is clamped). */
internal fun Comment.atDepth(depth: Int): Comment {
    val shift = depth - this.depth
    if (shift == 0) return this
    fun shifted(comment: Comment): Comment {
        val newDepth = (comment.depth + shift).coerceAtLeast(0)
        return comment.copy(
            depth = newDepth,
            accent = redditCommentAccent(newDepth),
            children = comment.children.map(::shifted),
        )
    }
    return shifted(this)
}

internal fun placeExpandedComments(
    current: List<Comment>,
    node: Comment,
    expanded: List<Comment>,
): ExpandedPlacement {
    fun bare(id: String) = id.trim().removePrefix("t1_")
    val inThread = HashSet<String>()
    fun collect(comment: Comment) {
        if (comment.id != node.id) inThread += bare(comment.id)
        comment.children.forEach(::collect)
    }
    current.forEach(::collect)
    val threadBeforeAnswer = HashSet(inThread)
    val byParent = linkedMapOf<String, MutableList<Comment>>()
    fun prune(comment: Comment): Comment? {
        if (bare(comment.id) in inThread) {
            // Already shown: only its replies that are new go under that copy.
            comment.children.mapNotNull(::prune).forEach { child ->
                byParent.getOrPut(bare(comment.id)) { mutableListOf() } += child
            }
            return null
        }
        inThread += bare(comment.id)
        return comment.copy(children = comment.children.mapNotNull(::prune))
    }
    val siblingParent = node.parentId?.let(::bare)
    val atNode = mutableListOf<Comment>()
    expanded.forEach { root ->
        val parent = root.parentId?.let(::bare)
        val inThreadParent = parent != null && parent != siblingParent && parent in threadBeforeAnswer
        val kept = prune(root) ?: return@forEach
        if (inThreadParent) {
            byParent.getOrPut(parent) { mutableListOf() } += kept
        } else {
            atNode += kept.atDepth(node.depth)
        }
    }
    return ExpandedPlacement(atNode = atNode, byParent = byParent)
}

/**
 * ISSUE298_MORE_LOADS_FROM_THE_END_THE_READER_COMES_FROM_V1 (#298): a reader
 * who reaches a more node from below (a reply opened from the Inbox sits after
 * a top-level node of hundreds) gets its last comments first, placed between
 * the node and the rows below it, so the node moves up, out of the way, and
 * loads again only when the reader reaches it. Loading its first comments from
 * below put them on the far side of the node, which stayed next to the reader
 * and loaded again at once (a hundred loads in a few seconds on the Pixel).
 *
 * [requestedIds] are the ids asked for (the end of the node's list, ids
 * already in the thread left out). A node's list also names descendants of
 * its comments, so an answer can hold comments whose parent is not in it:
 * - parent elsewhere in the thread: attached under that parent;
 * - parent still in the node's list: kept in the list right after the parent,
 *   so they come with it later, not orphaned now;
 * - otherwise: placed with the node's own comments (what a first-comments
 *   load does).
 * A requested id that did not come back (deleted) leaves the list; ids that
 * are already in the thread leave it too.
 */
internal fun mergeExpandedCommentsFromEnd(
    current: List<Comment>,
    moreNodeId: String,
    expanded: List<Comment>,
    requestedIds: List<String>,
    // AUDIT298A_FROM_THE_END_ALWAYS_MOVES_ON_V1: false places comments whose
    // parent is still listed at the node too, so a load always takes ids off.
    waitForListedParents: Boolean = true,
): List<Comment> {
    require(moreNodeId.isNotBlank()) { "More-comment node ID cannot be blank" }
    fun bare(id: String) = id.trim().removePrefix("t1_")
    val node = current.findComment(moreNodeId)?.takeIf { it.moreChildIds.isNotEmpty() }
        ?: throw IllegalArgumentException("Requested more-comment node is not present in cache")
    val inThread = mutableSetOf<String>()
    // Real comment ids only: a more or continue row inside an answer is not
    // an id Reddit can be asked for (it failed the request on every try).
    fun collect(comment: Comment, into: MutableSet<String>) {
        if (!comment.isMoreRepliesNode) into += bare(comment.id)
        comment.children.forEach { child -> collect(child, into) }
    }
    current.forEach { comment -> collect(comment, inThread) }
    val listed = node.moreChildIds.map(::bare).toSet()
    val siblingParent = node.parentId?.let(::bare)

    val keepAfter = linkedMapOf<String, MutableList<String>>()
    val placed = mutableListOf<Comment>()
    val returned = mutableSetOf<String>()
    expanded.forEach { root ->
        val parent = root.parentId?.let(::bare)
        if (waitForListedParents && parent != null && parent != siblingParent && parent !in inThread && parent in listed) {
            val ids = mutableSetOf<String>().also { ids -> collect(root, ids) }
            keepAfter.getOrPut(parent) { mutableListOf() } += ids
        } else {
            placed += root
            collect(root, returned)
        }
    }
    // AUDIT298A_NO_COMMENT_IS_ASKED_FOR_TWICE_V1 / ..._ORPHANS_FIND_THEIR_PARENT_V1:
    // the same placement as from the start (no duplicates, parents in the thread).
    val placement = placeExpandedComments(current, node, placed)
    val kept = keepAfter.values.flatten().toSet()
    val consumed = (requestedIds.map(::bare).toSet() + returned + inThread) - kept
    val remaining = buildList {
        node.moreChildIds.forEach { id ->
            val plain = bare(id)
            if (plain !in consumed && plain !in kept) add(id)
            keepAfter[plain]?.let { ids -> addAll(ids.filter { it !in this }) }
        }
    }.distinct()
    val consumedCount = node.moreChildIds.size - remaining.size

    var replaced = false
    fun replace(nodes: List<Comment>): List<Comment> = buildList {
        nodes.forEach { candidate ->
            if (candidate.id == moreNodeId && candidate.moreChildIds.isNotEmpty()) {
                replaced = true
                if (remaining.isNotEmpty()) {
                    add(
                        candidate.copy(
                            moreChildIds = remaining,
                            moreCount = (candidate.moreCount - consumedCount).coerceAtLeast(remaining.size),
                        ),
                    )
                }
                addAll(placement.atNode)
            } else {
                add(candidate.copy(children = replace(candidate.children) + placement.underParent(candidate)))
            }
        }
    }
    val merged = replace(current)
    require(replaced) { "Requested more-comment node is not present in cache" }
    return merged
}

internal fun List<Comment>.findComment(commentId: String): Comment? {
    for (comment in this) {
        if (comment.id == commentId) return comment
        comment.children.findComment(commentId)?.let { return it }
    }
    return null
}

private fun RedditCommentThingDto.toMoreComment(
    maxDepth: Int,
): Comment? {
    val childIds = data.children.mapNotNull { childId ->
        childId
            .trim()
            .removePrefix("t1_")
            .takeIf { id -> id.isNotEmpty() && id.all(Char::isLetterOrDigit) }
    }.distinct()
    val parentId = data.parentId?.trim()?.takeIf(String::isNotEmpty)
    if (childIds.isEmpty()) {
        // ISSUE3_CONTINUE_THREAD_NODE_V1: Reddit's "Continue this thread" is a
        // more child with no ids; its replies load through the parent's
        // permalink, not morechildren. Only a comment parent can continue.
        return parentId
            ?.takeIf { id -> id.startsWith("t1_") }
            ?.let { id ->
                continueThreadNode(
                    parentFullname = id,
                    depth = data.depth.coerceIn(0, maxDepth),
                    accent = redditCommentAccent(data.depth.coerceIn(0, maxDepth)),
                )
            }
    }
    return Comment(
        id = "more:${parentId ?: "root"}:${childIds.first()}",
        author = "More replies",
        age = "",
        body = "",
        score = 0,
        depth = data.depth.coerceIn(0, maxDepth),
        accent = redditCommentAccent(data.depth.coerceIn(0, maxDepth)),
        parentId = parentId,
        moreChildIds = childIds,
        moreCount = data.count.coerceAtLeast(childIds.size),
    )
}

private fun assembleExpandedCommentForest(comments: List<Comment>): List<Comment> {
    val unique = LinkedHashMap<String, Comment>()
    fun register(comment: Comment) {
        unique.putIfAbsent(comment.id, comment.copy(children = emptyList()))
        comment.children.forEach(::register)
    }
    comments.forEach(::register)
    val ids = unique.keys
    val childrenByParent = unique.values.groupBy(Comment::parentId)

    fun attach(comment: Comment, visiting: Set<String>): Comment {
        if (comment.id in visiting) return comment
        val nested = comment.children + childrenByParent[comment.id].orEmpty()
        return comment.copy(
            children = nested
                .distinctBy(Comment::id)
                .map { child -> attach(child, visiting + comment.id) },
        )
    }

    return unique.values
        .filter { comment -> comment.parentId !in ids }
        .map { comment -> attach(comment, emptySet()) }
}

// ISSUE22_COMMENT_AUTHOR_FULLNAME_V1
// Reddit account fullnames ("t2_" + base36 id) key the batch avatar lookup
// (/api/user_data_by_account_ids). Deleted or malformed authors carry none.
private val redditAccountFullnameRegex = Regex("t2_[a-z0-9]{1,16}")

internal fun String?.redditAccountFullnameOrNull(): String? =
    this?.trim()?.lowercase()?.takeIf(redditAccountFullnameRegex::matches)

private class CommentMappingBudget(
    private val limit: Int,
) {
    private var count: Int = 0

    val exhausted: Boolean
        get() = count >= limit

    fun consume() {
        check(!exhausted) { "Comment mapping budget is exhausted" }
        count += 1
    }
}

private const val REDDIT_COMMENT_LISTING_INDEX = 1
private const val REDDIT_COMMENT_KIND = "t1"
private const val BLANK_COMMENT_BODY = "[no text]"
private const val REDDIT_MORE_KIND = "more"
// LOW_NET_COMMENT_ACCENT_FOLLOWS_DEPTH_V1 (OCB-543): the rail colour comes
// from the comment's depth, not from where it sat in the answer it arrived
// in; morechildren answers are flat and continued threads are re-based, so
// position-based colours no longer showed the nesting.
internal fun redditCommentAccent(depth: Int): Long =
    COMMENT_ACCENTS[depth.mod(COMMENT_ACCENTS.size)]

private val COMMENT_ACCENTS = longArrayOf(
    0xFFFF8A50,
    0xFF7AB8FF,
    0xFFB38CFF,
    0xFF65CDAA,
    0xFFFFC857,
    0xFFFF7F9F,
)

private fun RedditCommentDataDto.idWithEmbeddedReplyIds(json: Json): List<String> = buildList {
    id.trim().removePrefix("t1_")
        .takeIf { id -> id.isNotEmpty() && id.all(Char::isLetterOrDigit) }
        ?.let(::add)
        ?: return@buildList
    val embedded = (replies as? JsonObject)
        ?.let { value ->
            runCatching { json.decodeFromJsonElement(RedditCommentListingDto.serializer(), value) }.getOrNull()
        }
        ?.data
        ?.children
        .orEmpty()
    for (reply in embedded) {
        when (reply.kind) {
            REDDIT_MORE_KIND -> reply.data.children.forEach { childId ->
                childId.trim().removePrefix("t1_")
                    .takeIf { id -> id.isNotEmpty() && id.all(Char::isLetterOrDigit) }
                    ?.let(::add)
            }
            null, REDDIT_COMMENT_KIND -> addAll(reply.data.idWithEmbeddedReplyIds(json))
        }
    }
}

private fun RedditCommentDataDto.hasReplies(json: Json): Boolean =
    (replies as? JsonObject)
        ?.let { value ->
            runCatching { json.decodeFromJsonElement(RedditCommentListingDto.serializer(), value) }.getOrNull()
        }
        ?.data
        ?.children
        ?.isNotEmpty() == true

// ISSUE226_REMOVED_COMMENT_IS_MARKED_V1: banned_by holds the remover (a name
// or true); null, JSON null and false mean the comment was not removed.
internal fun JsonElement?.marksRemoval(): Boolean = when (this) {
    null, JsonNull -> false
    is JsonPrimitive -> booleanOrNull ?: contentOrNull?.isNotBlank() ?: false
    else -> true
}

/**
 * AUDIT298A_FROM_THE_END_ALWAYS_MOVES_ON_V1: the window that starts at the
 * earliest listed parent of an answer whose comments all wait for a listed
 * parent. Null when no returned comment names a listed parent.
 */
internal fun moreNodeWindowFromListedParents(
    node: Comment,
    expanded: List<Comment>,
    isInThread: (String) -> Boolean,
    batchSize: Int,
): List<String>? {
    fun bare(id: String) = id.trim().removePrefix("t1_")
    val index = node.moreChildIds.withIndex().associate { (i, id) -> bare(id) to i }
    val start = expanded
        .mapNotNull { root -> root.parentId?.let(::bare)?.let(index::get) }
        .minOrNull()
        ?: return null
    return node.moreChildIds.drop(start).filterNot(isInThread).take(batchSize).takeIf { it.isNotEmpty() }
}
