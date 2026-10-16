package app.subshell.prototype.ui

import app.subshell.prototype.model.Comment
import app.subshell.prototype.model.isMoreRepliesNode
import java.nio.charset.StandardCharsets
import java.util.Base64

internal data class CommentVisitBaseline(
    val exists: Boolean,
    val commentIds: Set<String>,
)

private data class CommentVisitEntry(
    val key: String,
    val commentIds: Set<String>,
)

internal class CommentVisitHistory private constructor(
    private val entries: List<CommentVisitEntry>,
) {
    constructor() : this(emptyList())

    fun baseline(accountStableId: String, postId: String): Set<String>? {
        val key = commentVisitKey(accountStableId, postId)
        return entries.lastOrNull { entry -> entry.key == key }?.commentIds
    }

    // LOWUI5_VISIT_HISTORY_COMPARES_ONE_ENTRY_V1 (OCB-086): a visit that adds
    // nothing to the newest entry leaves the history as it is; no re-encode.
    fun recordingChanges(
        accountStableId: String,
        postId: String,
        commentIds: Set<String>,
    ): Boolean {
        val newest = entries.lastOrNull() ?: return true
        return newest.key != commentVisitKey(accountStableId, postId) ||
            !newest.commentIds.containsAll(commentIds)
    }

    fun recording(
        accountStableId: String,
        postId: String,
        commentIds: Set<String>,
    ): CommentVisitHistory {
        val key = commentVisitKey(accountStableId, postId)
        val accumulatedIds = entries
            .lastOrNull { entry -> entry.key == key }
            ?.commentIds
            .orEmpty() + commentIds
        val retained = entries.filterNot { entry -> entry.key == key }.toMutableList()
        if (accumulatedIds.size <= MAX_COMMENT_IDS) {
            retained += CommentVisitEntry(key, accumulatedIds.toSortedSet())
        }
        while (
            retained.size > MAX_VISITED_POSTS ||
            retained.sumOf { entry -> entry.commentIds.size } > MAX_COMMENT_IDS
        ) {
            retained.removeAt(0)
        }
        return CommentVisitHistory(retained)
    }

    fun encode(): String = entries.joinToString("\n") { entry ->
        "${entry.key.toStorageToken()}\t${encodeCommentIdSet(entry.commentIds).toStorageToken()}"
    }

    companion object {
        fun decode(encoded: String): CommentVisitHistory {
            if (encoded.isBlank()) return CommentVisitHistory()
            val entries = encoded.lineSequence().mapNotNull { line ->
                val fields = line.split('\t', limit = 2)
                if (fields.size != 2) return@mapNotNull null
                val key = fields[0].fromStorageToken()?.takeIf(String::isNotBlank)
                    ?: return@mapNotNull null
                val encodedIds = fields[1].fromStorageToken() ?: return@mapNotNull null
                CommentVisitEntry(key, decodeCommentIdSet(encodedIds))
            }.toList()
            return CommentVisitHistory(entries).trimmed()
        }
    }

    private fun trimmed(): CommentVisitHistory {
        var result = CommentVisitHistory()
        entries.forEach { entry ->
            val fields = entry.key.split(COMMENT_VISIT_KEY_SEPARATOR, limit = 2)
            if (fields.size == 2) {
                result = result.recording(fields[0], fields[1], entry.commentIds)
            }
        }
        return result
    }
}

internal fun knownCommentIds(comments: List<Comment>): Set<String> = buildSet {
    fun collect(comment: Comment) {
        if (comment.isMoreRepliesNode) {
            addAll(comment.knownMoreCommentIds())
            return
        }
        add(comment.id)
        comment.children.forEach(::collect)
    }
    comments.forEach(::collect)
}

internal fun newCommentIds(
    baseline: CommentVisitBaseline,
    currentCommentIds: Set<String>,
): Set<String> = if (!baseline.exists) {
    emptySet()
} else {
    (currentCommentIds - baseline.commentIds)
        .filterNotTo(linkedSetOf()) { id -> id.startsWith(LOCAL_COMMENT_ID_PREFIX) }
}

internal fun commentAncestorIdsForTargets(
    comments: List<Comment>,
    targetIds: Set<String>,
): Set<String> = buildSet {
    fun visit(comment: Comment, ancestors: List<String>) {
        val containsTarget = if (comment.isMoreRepliesNode) {
            comment.knownMoreCommentIds().any(targetIds::contains)
        } else {
            comment.id in targetIds
        }
        if (containsTarget) addAll(ancestors)
        if (!comment.isMoreRepliesNode) {
            comment.children.forEach { child -> visit(child, ancestors + comment.id) }
        }
    }
    comments.forEach { comment -> visit(comment, emptyList()) }
}

internal fun commentSiblingBranchesToCollapse(
    comments: List<Comment>,
    targetIds: Set<String>,
    autoExpandedAncestorIds: Set<String>,
): Set<String> = buildSet {
    fun containsTarget(comment: Comment): Boolean = if (comment.isMoreRepliesNode) {
        comment.knownMoreCommentIds().any(targetIds::contains)
    } else {
        comment.id in targetIds || comment.children.any(::containsTarget)
    }

    fun visit(comment: Comment, insideAutomaticallyOpenedPath: Boolean) {
        if (comment.isMoreRepliesNode) return
        val automaticallyOpenedPath =
            (insideAutomaticallyOpenedPath || comment.id in autoExpandedAncestorIds) &&
                containsTarget(comment)
        if (automaticallyOpenedPath) {
            comment.children
                .filterNot { child -> child.isMoreRepliesNode }
                .filterNot(::containsTarget)
                .forEach { sibling -> add(sibling.id) }
        }
        comment.children.forEach { child -> visit(child, automaticallyOpenedPath) }
    }
    comments.forEach { comment -> visit(comment, false) }
}

internal fun newDescendantCount(comment: Comment, newIds: Set<String>): Int {
    fun count(node: Comment): Int = if (node.isMoreRepliesNode) {
        node.knownMoreCommentIds().count(newIds::contains)
    } else {
        (if (node.id in newIds) 1 else 0) + node.children.sumOf(::count)
    }
    return comment.children.sumOf(::count)
}

internal fun Comment.knownMoreCommentIds(): Set<String> = moreChildIds
    .mapTo(linkedSetOf()) { id -> if (id.startsWith("t1_")) id else "t1_$id" }

internal fun encodeCommentVisitBaseline(commentIds: Set<String>?): String = when (commentIds) {
    null -> COMMENT_BASELINE_MISSING
    else -> COMMENT_BASELINE_PRESENT + encodeCommentIdSet(commentIds)
}

internal fun decodeCommentVisitBaseline(encoded: String): CommentVisitBaseline = when {
    encoded == COMMENT_BASELINE_MISSING -> CommentVisitBaseline(false, emptySet())
    encoded.startsWith(COMMENT_BASELINE_PRESENT) -> CommentVisitBaseline(
        exists = true,
        commentIds = decodeCommentIdSet(encoded.removePrefix(COMMENT_BASELINE_PRESENT)),
    )
    else -> CommentVisitBaseline(false, emptySet())
}

private fun commentVisitKey(accountStableId: String, postId: String): String =
    "$accountStableId$COMMENT_VISIT_KEY_SEPARATOR$postId"

private fun encodeCommentIdSet(ids: Set<String>): String = ids.sorted().joinToString("\n")

private fun decodeCommentIdSet(encoded: String): Set<String> = encoded
    .lineSequence()
    .map(String::trim)
    .filter(String::isNotEmpty)
    .toCollection(linkedSetOf())

private fun String.toStorageToken(): String = Base64.getUrlEncoder()
    .withoutPadding()
    .encodeToString(toByteArray(StandardCharsets.UTF_8))

private fun String.fromStorageToken(): String? = runCatching {
    String(Base64.getUrlDecoder().decode(this), StandardCharsets.UTF_8)
}.getOrNull()

private const val COMMENT_VISIT_KEY_SEPARATOR = "\u0000"
private const val COMMENT_BASELINE_MISSING = "v1:none"
private const val COMMENT_BASELINE_PRESENT = "v1:seen:"
private const val LOCAL_COMMENT_ID_PREFIX = "local-comment-"
private const val MAX_VISITED_POSTS = 100
private const val MAX_COMMENT_IDS = 50_000
