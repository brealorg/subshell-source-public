package app.subshell.prototype.ui

import app.subshell.prototype.data.isValidRedditUsername
import app.subshell.prototype.model.Comment
import app.subshell.prototype.model.Post

// ISSUE229_MENTION_AUTOCOMPLETE_V1: typing `@` or `u/` in a composer suggests
// people to mention, first from the open thread (no network), then from
// Reddit's people search. Picking one writes `u/name `, the form Reddit
// notifies; `@name` notifies no one.

/** The mention being typed just before the cursor: `@par` or `u/par`. */
internal data class MentionQuery(
    /** Where the `@` or `u/` starts; the edit replaces from here to the cursor. */
    val start: Int,
    val end: Int,
    /** The name typed so far, without its `@` or `u/` (may be empty). */
    val partial: String,
)

private const val MENTION_NAME_MAX = 20

internal fun mentionQueryAt(text: CharSequence, cursor: Int): MentionQuery? {
    if (cursor < 0 || cursor > text.length) return null
    var nameStart = cursor
    while (nameStart > 0 && text[nameStart - 1].isMentionNameChar()) nameStart--
    val partial = text.subSequence(nameStart, cursor).toString()
    if (partial.length > MENTION_NAME_MAX) return null
    val start = when {
        nameStart >= 1 && text[nameStart - 1] == '@' -> nameStart - 1
        nameStart >= 2 &&
            text[nameStart - 1] == '/' &&
            text[nameStart - 2].equals('u', ignoreCase = true) -> nameStart - 2
        else -> return null
    }
    // A mention starts a word: not inside an e-mail address or a URL path.
    val before = if (start > 0) text[start - 1] else null
    if (before != null && !before.isWhitespace() && before !in MENTION_OPENERS) return null
    return MentionQuery(start = start, end = cursor, partial = partial)
}

private val MENTION_OPENERS = setOf('(', '[', '"', '\'', '*', '_', '>', '~')

private fun Char.isMentionNameChar(): Boolean = isLetterOrDigit() || this == '_' || this == '-'

/** Replaces the typed mention with `u/name ` and puts the cursor after it. */
internal fun applyMention(text: String, query: MentionQuery, username: String): MarkdownEdit {
    val start = query.start.coerceIn(0, text.length)
    val end = query.end.coerceIn(start, text.length)
    val next = text.getOrNull(end)
    val insert = if (next != null && next.isWhitespace()) "u/$username" else "u/$username "
    val edited = text.substring(0, start) + insert + text.substring(end)
    val cursor = start + insert.length + if (insert.endsWith(' ')) 0 else 1
    return MarkdownEdit(edited, cursor.coerceAtMost(edited.length), cursor.coerceAtMost(edited.length))
}

/**
 * The people in the open thread, in the order a reader most likely means:
 * the person being replied to, the post's author, then the others who
 * commented, newest first. Deleted and invalid names and the reader's own
 * name are left out; each name once.
 */
internal fun threadMentionCandidates(
    post: Post,
    comments: List<Comment>,
    replyingTo: String?,
    ownUsername: String?,
): List<String> {
    val others = buildList { comments.forEach { collectComments(it, this) } }
        .sortedByDescending { it.createdEpochSeconds ?: Long.MIN_VALUE }
        .map(Comment::author)
    val seen = HashSet<String>()
    ownUsername?.let { seen += it.lowercase() }
    return (listOfNotNull(replyingTo, post.author) + others)
        .filter { name -> isValidRedditUsername(name) && seen.add(name.lowercase()) }
}

private fun collectComments(comment: Comment, into: MutableList<Comment>) {
    into += comment
    comment.children.forEach { collectComments(it, into) }
}

internal const val MENTION_SUGGESTION_LIMIT = 6

/** Searching Reddit waits for this many typed letters. */
internal const val MENTION_REMOTE_MIN_CHARS = 2

/**
 * The suggestions for [partial]: thread names that start with it (then those
 * that contain it), then Reddit's results for the same query, each name once.
 */
internal fun mentionSuggestions(
    partial: String,
    thread: List<String>,
    remote: List<String>,
    limit: Int = MENTION_SUGGESTION_LIMIT,
): List<String> {
    val needle = partial.lowercase()
    val fromThread = if (needle.isEmpty()) {
        thread
    } else {
        thread.filter { it.lowercase().startsWith(needle) } +
            thread.filter { val name = it.lowercase(); !name.startsWith(needle) && needle in name }
    }
    val fromReddit = if (partial.length >= MENTION_REMOTE_MIN_CHARS) remote else emptyList()
    val seen = HashSet<String>()
    return (fromThread + fromReddit)
        .filter { seen.add(it.lowercase()) }
        .take(limit)
}
