package app.subshell.prototype.model

// ISSUE8_INLINE_REDDIT_MEDIA_V1
// Reddit writes inline media into a comment or self-post body as a
// placeholder whose target is not an address: `![img](<asset id>)`,
// `![gif](giphy|<id>[|size])`, `![img](emote|<pack>|<id>)`. The address is in
// media_metadata (or, for Giphy, derived from the id). Subshell keeps the
// placeholder exactly as Reddit wrote it and adds the resolved address as a
// tagged markdown title:
//
//   ![img](emote|t5_x|123 "subshell-media https://…/123.png 20x20")
//
// Media Reddit lists in media_metadata without a placeholder in the body is
// added at the end as `![img](<id> "subshell-appended https://…")`.
// `redditSourceMarkdown` removes both again, so what an edit, a copy or a
// quote hands on is Reddit's own text. No cache codec change: the annotation
// lives inside the body string.

internal const val SUBSHELL_MEDIA_TITLE = "subshell-media"
internal const val SUBSHELL_APPENDED_MEDIA_TITLE = "subshell-appended"

/** One Reddit media placeholder in a body, resolved or not. */
internal data class RedditMediaPlaceholder(
    val range: IntRange,
    val label: String,
    val target: String,
    val url: String?,
    val width: Int?,
    val height: Int?,
    val appended: Boolean,
) {
    val isEmote: Boolean get() = target.startsWith("emote|", ignoreCase = true)

    /** A short, readable stand-in for the emote (`:joy:`), or null. */
    val emoteName: String?
        get() = if (!isEmote) {
            null
        } else {
            target.substringAfterLast('|').trim().takeIf { name ->
                name.isNotEmpty() && name.any(Char::isLetter)
            }
        }
}

/** The address and size Subshell resolved for one placeholder target. */
internal data class ResolvedRedditMedia(
    val url: String,
    val width: Int? = null,
    val height: Int? = null,
)

private val redditMediaPlaceholderRegex = Regex(
    "!\\[([^\\]\\n]*)\\]\\(([^)\\s\"]+)" +
        "(?: \"(" + SUBSHELL_MEDIA_TITLE + "|" + SUBSHELL_APPENDED_MEDIA_TITLE + ") " +
        "(https://[^\"\\s)]+)(?: ([0-9]{1,5})x([0-9]{1,5}))?\")?\\)",
)

private val redditAnnotatedMediaTitleRegex = Regex(
    " \"" + SUBSHELL_MEDIA_TITLE + " https://[^\"\\s)]+(?: [0-9]{1,5}x[0-9]{1,5})?\"\\)",
)

private val redditAppendedMediaRegex = Regex(
    "(?:\\n\\n)?!\\[[^\\]\\n]*\\]\\([^)\\s\"]+ \"" + SUBSHELL_APPENDED_MEDIA_TITLE +
        " https://[^\"\\s)]+(?: [0-9]{1,5}x[0-9]{1,5})?\"\\)",
)

/**
 * A native Reddit placeholder target: an asset id, `giphy|…` or `emote|…`.
 * Anything else inside `![…](…)` (a web URL, a relative path) is not ours.
 */
internal fun isRedditMediaPlaceholderTarget(target: String): Boolean {
    val trimmed = target.trim()
    if (trimmed.isEmpty()) return false
    if (trimmed.startsWith("giphy|", ignoreCase = true)) return true
    if (trimmed.startsWith("emote|", ignoreCase = true)) return true
    return trimmed.all(Char::isLetterOrDigit)
}

internal fun parseRedditMediaPlaceholders(body: String): List<RedditMediaPlaceholder> =
    redditMediaPlaceholderRegex.findAll(body)
        .filter { match -> isRedditMediaPlaceholderTarget(match.groupValues[2]) }
        .map { match ->
            val tag = match.groupValues[3]
            RedditMediaPlaceholder(
                range = match.range,
                label = match.groupValues[1],
                target = match.groupValues[2],
                url = match.groupValues[4].takeIf(String::isNotEmpty),
                width = match.groupValues[5].toIntOrNull(),
                height = match.groupValues[6].toIntOrNull(),
                appended = tag == SUBSHELL_APPENDED_MEDIA_TITLE,
            )
        }
        .toList()

/** The placeholder that starts exactly at [index], if any. */
internal fun redditMediaPlaceholderAt(source: String, index: Int): RedditMediaPlaceholder? {
    val match = redditMediaPlaceholderRegex.matchAt(source, index) ?: return null
    if (!isRedditMediaPlaceholderTarget(match.groupValues[2])) return null
    return RedditMediaPlaceholder(
        range = match.range,
        label = match.groupValues[1],
        target = match.groupValues[2],
        url = match.groupValues[4].takeIf(String::isNotEmpty),
        width = match.groupValues[5].toIntOrNull(),
        height = match.groupValues[6].toIntOrNull(),
        appended = match.groupValues[3] == SUBSHELL_APPENDED_MEDIA_TITLE,
    )
}

// MED1_MEDIA_SIZE_UP_TO_FIVE_DIGITS_V1 (OCB-655): a panorama over 9 999 px
// wrote a size the parsers (four digits) could not read back, so the tag
// leaked into display and edits. Sizes up to 99 999 round-trip; a larger one
// is left out.
private const val MEDIA_SIZE_MAX = 99_999

private fun ResolvedRedditMedia.title(tag: String): String {
    val size = if (
        width != null && height != null &&
        width in 1..MEDIA_SIZE_MAX && height in 1..MEDIA_SIZE_MAX
    ) {
        " ${width}x$height"
    } else {
        ""
    }
    return "\"$tag $url$size\""
}

/**
 * Adds the resolved address to every placeholder [resolve] knows, and adds
 * [unplaced] media (listed by Reddit but absent from the body) at the end.
 * Already annotated placeholders are left alone, so this is idempotent.
 */
internal fun annotateRedditMedia(
    body: String,
    resolve: (target: String) -> ResolvedRedditMedia?,
    unplaced: List<Pair<String, ResolvedRedditMedia>> = emptyList(),
): String {
    val source = body.redditSourceMarkdown()
    val annotated = redditMediaPlaceholderRegex.replace(source) { match ->
        val target = match.groupValues[2]
        if (!isRedditMediaPlaceholderTarget(target)) return@replace match.value
        val resolved = resolve(target) ?: return@replace match.value
        "![${match.groupValues[1]}]($target ${resolved.title(SUBSHELL_MEDIA_TITLE)})"
    }
    if (unplaced.isEmpty()) return annotated
    return buildString {
        append(annotated)
        unplaced.forEach { (target, resolved) ->
            if (isNotEmpty()) append("\n\n")
            append("![img]($target ${resolved.title(SUBSHELL_APPENDED_MEDIA_TITLE)})")
        }
    }
}

/**
 * Reddit's own text for a body Subshell annotated: tagged titles removed,
 * appended media removed. A body without annotations is returned unchanged.
 */
internal fun String.redditSourceMarkdown(): String {
    if (!contains(SUBSHELL_MEDIA_TITLE) && !contains(SUBSHELL_APPENDED_MEDIA_TITLE)) return this
    return redditAppendedMediaRegex.replace(this, "")
        .replace(redditAnnotatedMediaTitleRegex, ")")
}

/**
 * [source] (Reddit's own text, e.g. just saved by an edit) annotated with the
 * addresses [previous] already resolved, so a local rebind keeps its media.
 */
internal fun reannotateRedditMedia(source: String, previous: String): String {
    val known = parseRedditMediaPlaceholders(previous)
        .filter { placeholder -> placeholder.url != null }
        .associate { placeholder ->
            placeholder.target.lowercase() to ResolvedRedditMedia(
                url = placeholder.url.orEmpty(),
                width = placeholder.width,
                height = placeholder.height,
            )
        }
    if (known.isEmpty()) return source
    return annotateRedditMedia(source, resolve = { target -> known[target.lowercase()] })
}

/**
 * The text a reader copies: resolved images and Giphy as their address on
 * their own paragraph (as before ISSUE8), emotes as `:name:` when they have
 * one, unresolved placeholders dropped.
 */
internal fun String.redditReadableText(): String {
    val placeholders = parseRedditMediaPlaceholders(this)
    if (placeholders.isEmpty()) return this
    return buildString {
        var cursor = 0
        placeholders.forEach { placeholder ->
            append(this@redditReadableText, cursor, placeholder.range.first)
            val url = placeholder.url
            when {
                url == null -> Unit
                placeholder.isEmote -> placeholder.emoteName?.let { name -> append(':').append(name).append(':') }
                else -> append(url)
            }
            cursor = placeholder.range.last + 1
        }
        append(this@redditReadableText, cursor, this@redditReadableText.length)
    }.trim()
}
