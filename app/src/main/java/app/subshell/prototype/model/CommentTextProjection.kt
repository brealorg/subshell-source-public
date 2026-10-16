package app.subshell.prototype.model

internal data class MarkdownLinkTarget(
    val start: Int,
    val end: Int,
    val url: String,
)

internal enum class RemoteCommentMediaKind {
    IMAGE,
    GIF,
}

internal data class RemoteCommentMedia(
    val uri: String,
    val kind: RemoteCommentMediaKind,
)

internal data class CommentContentProjection(
    val displayText: String,
    val media: List<RemoteCommentMedia>,
)

private val commentWebSchemeSeparator = ":" + "/" + "/"
private val commentWebUrlRegex = Regex(
    "(?i)\\b(?:http|https)" +
        Regex.escape(commentWebSchemeSeparator) +
        // COMMENTTEXT03_BALANCED_LINK_PARENS_V1 (#151): parentheses belong to
        // the URL (Wikipedia); an unbalanced trailing ')' is trimmed later.
        "[^\\s<>]+",
)
private val commentMarkdownLinkRegex = Regex(
    // One level of balanced parentheses inside the target (#151).
    "!?\\[([^\\n\\]]+)\\]\\(((?:[^()\\s]|\\([^()\\s]*\\))+)\\)",
)
private val commentRepeatedBlankLinesRegex = Regex("\\n{3,}")
private const val EMOTE_SHIELD_START = '\uE000'
private const val EMOTE_SHIELD_END = '\uE001'
// REDDITURL06_PLAIN_MENTION_LINKS_V1: `r/name` and `u/name` in text are links
// on Reddit. Not after a letter, digit, `.`, `:` or `/`, so the paths inside
// URLs and file names stay text.
private val redditMentionRegex = Regex(
    // ISSUE46_REDDIT_USERNAME_LENGTH_V1: a user name is 3..20 characters
    // (u/ab is not a user); community names may be 2 (r/de).
    "(?<![A-Za-z0-9_/.:])/?(?:r/[A-Za-z0-9_][A-Za-z0-9_-]{1,20}|u/[A-Za-z0-9_-]{3,20})(?![A-Za-z0-9_-])",
)
private val redditMatrixThumbnailRegex = Regex(
    "(?i)^(https://matrix\\.redditspace\\.com)/_matrix/media/v3/thumbnail/([a-z0-9.-]+)/([a-z0-9_-]+)(?:\\?[^#]*)?$",
)

/**
 * Link ranges in [renderedText]. [renderedMarkdownLinks] are the markdown links
 * the renderer placed itself (COMMENTTEXT03_RENDERER_LINK_RANGES_V1, #152);
 * without them the labels are searched for, which can land on an earlier
 * occurrence of the same words.
 */
// ISSUE52_UNDERSCORES_IN_LINKS_STAY_TEXT_V1: the spans of bare URLs and
// u/ r/ mentions in markdown source; an underscore inside one is text, never
// emphasis, so the link the renderer finds keeps its real target.
internal fun plainLinkTokenRanges(source: String): List<IntRange> =
    (commentWebUrlRegex.findAll(source) + redditMentionRegex.findAll(source))
        .map { match -> match.range }
        .toList()

internal fun markdownLinkTargetsForRenderedText(
    source: String,
    renderedText: String,
    renderedMarkdownLinks: List<MarkdownLinkTarget>? = null,
): List<MarkdownLinkTarget> {
    val links = mutableListOf<MarkdownLinkTarget>()
    var searchOffset = 0
    if (renderedMarkdownLinks != null) links += renderedMarkdownLinks
    if (renderedMarkdownLinks == null) commentMarkdownLinkRegex.findAll(source).forEach { match ->
        // ISSUE24_GIPHY_COMMENT_MEDIA_V1: an image placeholder is not a link;
        // the renderer drops it, so it has no range in the rendered text.
        if (match.value.startsWith("!")) return@forEach
        val label = match.groupValues[1]
        val uri = normalizedCommentWebUrl(match.groupValues[2]) ?: return@forEach
        val start = renderedText.indexOf(label, startIndex = searchOffset)
        if (start >= 0) {
            links += MarkdownLinkTarget(
                start = start,
                end = start + label.length,
                url = uri,
            )
            searchOffset = start + label.length
        }
    }
    commentWebUrlRegex.findAll(renderedText).forEach { match ->
        val uri = normalizedCommentWebUrl(match.value) ?: return@forEach
        val start = match.range.first
        // Trimmed trailing punctuation (or an unbalanced ')') is not part of the link.
        val end = if (match.value.startsWith(uri)) start + uri.length else match.range.last + 1
        if (links.any { link -> start < link.end && link.start < end }) return@forEach
        links += MarkdownLinkTarget(start = start, end = end, url = uri)
    }
    redditMentionRegex.findAll(renderedText).forEach { match ->
        val start = match.range.first
        val end = match.range.last + 1
        if (links.any { link -> start < link.end && link.start < end }) return@forEach
        links += MarkdownLinkTarget(
            start = start,
            end = end,
            url = "/" + match.value.removePrefix("/"),
        )
    }
    return links.distinctBy { target -> Triple(target.start, target.end, target.url) }
}

internal fun projectCommentContent(source: String): CommentContentProjection {
    val media = linkedMapOf<String, RemoteCommentMedia>()

    fun record(rawUri: String): RemoteCommentMedia? {
        val normalized = normalizedCommentWebUrl(rawUri) ?: return null
        val uri = canonicalRemoteCommentMediaUri(normalized)
        val kind = remoteCommentMediaKind(uri) ?: return null
        return RemoteCommentMedia(uri = uri, kind = kind).also { item ->
            media.putIfAbsent(uri, item)
        }
    }

    // ISSUE8_INLINE_REDDIT_MEDIA_V1: a resolved image/Giphy placeholder is
    // media like a pasted URL; an emote stays in the text (the renderer draws
    // it inline) and is shielded from the URL passes below.
    val shieldedEmotes = mutableListOf<String>()
    // COMMENTTEXT01_SHIELD_OUTSIDE_USER_TEXT_V1 (#153): the shield markers are
    // private-use characters the source does not contain, so the author's own
    // text can never be read back as a shield (no crash, no spliced emote).
    val shieldStart = source.unusedPrivateUseCharacter(EMOTE_SHIELD_START, except = null)
    val shieldEnd = shieldStart?.let { start ->
        source.unusedPrivateUseCharacter(EMOTE_SHIELD_END, except = start)
    }

    fun liftRedditMedia(line: String): String {
        val placeholders = parseRedditMediaPlaceholders(line)
        if (placeholders.isEmpty()) return line
        return buildString {
            var cursor = 0
            placeholders.forEach { placeholder ->
                append(line, cursor, placeholder.range.first)
                val raw = line.substring(placeholder.range)
                when {
                    placeholder.url == null -> append(raw)
                    placeholder.isEmote && shieldEnd != null -> {
                        append(shieldStart).append(shieldedEmotes.size).append(shieldEnd)
                        shieldedEmotes += raw
                    }
                    placeholder.isEmote -> append(raw)
                    else -> record(placeholder.url)
                }
                cursor = placeholder.range.last + 1
            }
            append(line, cursor, line.length)
        }
    }

    fun projectLine(rawLine: String): String {
        val line = liftRedditMedia(rawLine)
        val trimmed = line.trim()
        val wholeMarkdown = commentMarkdownLinkRegex.matchEntire(trimmed)
        if (wholeMarkdown != null && record(wholeMarkdown.groupValues[2]) != null) {
            return ""
        }
        val wholeUrl = commentWebUrlRegex.matchEntire(trimmed)
        if (wholeUrl != null && record(wholeUrl.value) != null) {
            return ""
        }
        var projected = commentMarkdownLinkRegex.replace(line) { match ->
            if (record(match.groupValues[2]) != null) {
                match.groupValues[1]
            } else {
                match.value
            }
        }
        projected = commentWebUrlRegex.replace(projected) { match ->
            val uri = normalizedCommentWebUrl(match.value)
            if (uri != null && record(match.value) != null) {
                // Keep what was trimmed off the URL, e.g. the ')' that closes "(see URL)".
                if (match.value.startsWith(uri)) match.value.substring(uri.length) else ""
            } else {
                match.value
            }
        }
        return projected.trimEnd()
    }

    val displayText = source
        .lineSequence()
        .map(::projectLine)
        .joinToString("\n")
        .replace(commentRepeatedBlankLinesRegex, "\n\n")
        .trim('\n', '\r')
        .let { text ->
            if (shieldedEmotes.isEmpty() || shieldStart == null || shieldEnd == null) {
                text
            } else {
                val shield = Regex(
                    Regex.escape(shieldStart.toString()) + "([0-9]+)" +
                        Regex.escape(shieldEnd.toString()),
                )
                shield.replace(text) { match ->
                    match.groupValues[1].toIntOrNull()
                        ?.let(shieldedEmotes::getOrNull)
                        ?: match.value
                }
            }
        }

    return CommentContentProjection(
        displayText = displayText,
        media = media.values.toList(),
    )
}

private fun String.unusedPrivateUseCharacter(preferred: Char, except: Char?): Char? =
    if (preferred != except && preferred !in this) {
        preferred
    } else {
        ('\uE000'..'\uF8FF').firstOrNull { candidate ->
            candidate != except && candidate !in this
        }
    }

/** The URL a markdown link target opens, or null when it is not a web or site-relative link. */
internal fun normalizedMarkdownLinkUrl(raw: String): String? = normalizedCommentWebUrl(raw)

/**
 * The index of the ')' that closes a markdown link target starting at
 * [urlStart], allowing one level of balanced parentheses inside it
 * (COMMENTTEXT03_BALANCED_LINK_PARENS_V1, #151); -1 when the line ends first.
 */
internal fun markdownLinkTargetEnd(source: String, urlStart: Int): Int {
    var depth = 0
    var index = urlStart
    while (index < source.length) {
        when (source[index]) {
            '\n' -> return -1
            '(' -> depth += 1
            ')' -> if (depth == 0) return index else depth -= 1
        }
        index += 1
    }
    return -1
}

private fun normalizedCommentWebUrl(raw: String): String? {
    var candidate = raw
        .trim()
        .trimEnd('.', ',', ';', ':', '!', '?', ']', '}')
    // COMMENTTEXT03_BALANCED_LINK_PARENS_V1 (#151): a trailing ')' belongs to
    // the URL only when it closes a '(' inside it.
    while (candidate.endsWith(')') && candidate.count { it == '(' } < candidate.count { it == ')' }) {
        candidate = candidate.dropLast(1).trimEnd('.', ',', ';', ':', '!', '?', ']', '}')
    }
    if (candidate.isEmpty()) return null
    // REDDITURL06_PLAIN_MENTION_LINKS_V1: `[label](/r/name)` and friends are
    // site-relative Reddit links. They stay relative: the in-app link handler
    // resolves them, and this presentation source names no web endpoint.
    if (REDDIT_RELATIVE_LINK_PREFIXES.any { prefix -> candidate.startsWith(prefix, ignoreCase = true) }) {
        return candidate
    }
    return candidate.takeIf { value ->
        value.startsWith("http$commentWebSchemeSeparator", ignoreCase = true) ||
            value.startsWith("https$commentWebSchemeSeparator", ignoreCase = true)
    }
}

internal fun canonicalRemoteCommentMediaUri(uri: String): String {
    val match = redditMatrixThumbnailRegex.matchEntire(uri) ?: return uri
    val origin = match.groupValues[1]
    val serverName = match.groupValues[2]
    val mediaId = match.groupValues[3]
    return "$origin/_matrix/media/v3/download/$serverName/$mediaId"
}

private fun remoteCommentMediaKind(uri: String): RemoteCommentMediaKind? {
    val normalizedPath = uri
        .substringAfter(commentWebSchemeSeparator, missingDelimiterValue = "")
        .substringAfter('/', missingDelimiterValue = "")
        .substringBefore('?')
        .substringBefore('#')
        .lowercase()
    // COMMENTTEXT02_VIDEO_LINKS_STAY_LINKS_V1 (#154): i.imgur.com .gifv/.mp4 and
    // a preview `.gif?format=mp4` are video, not images; as image media they
    // were removed from the text and failed to load. They stay text links.
    val query = uri.substringAfter('?', missingDelimiterValue = "").substringBefore('#').lowercase()
    if (query.split('&').any { it == "format=mp4" }) return null
    val host = uri
        .substringAfter(commentWebSchemeSeparator, missingDelimiterValue = "")
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
        .substringBefore(':')
        .lowercase()
    // LOW_COMMENT_IMAGES_LOAD_ONLY_FROM_KNOWN_HOSTS_V1 (OCB-569, owner chose: the
    // common image services load as before, others stay links): an image URL
    // from any host loaded at once, so a commenter's own server saw every
    // reader's address. The list grows when a service in real use is missing.
    if (commentImageHostIsKnown(host)) {
        if (normalizedPath.endsWith(".gif")) return RemoteCommentMediaKind.GIF
        if (
            normalizedPath.endsWith(".jpg") ||
            normalizedPath.endsWith(".jpeg") ||
            normalizedPath.endsWith(".png") ||
            normalizedPath.endsWith(".webp")
        ) {
            return RemoteCommentMediaKind.IMAGE
        }
    }
    return when (host) {
        "matrix.redditspace.com" -> if (
            normalizedPath.startsWith("_matrix/media/v3/download/") ||
            normalizedPath.startsWith("_matrix/client/v1/media/download/")
        ) {
            // Matrix media IDs do not preserve a filename extension. Use the animated-capable
            // decoder; it renders both static images and animated GIF/WebP safely.
            RemoteCommentMediaKind.GIF
        } else {
            null
        }
        "i.redd.it",
        "preview.redd.it",
        "external-preview.redd.it",
        "reddit-uploaded-media.s3-accelerate.amazonaws.com",
        "i.imgur.com",
        // Only an extensionless path falls back to the host; .gifv/.mp4/.webm stay links.
        // ISSUE228_A_BARE_IMAGE_HOST_LINK_STAYS_A_LINK_V1 (#228): a link to the
        // host itself (a text link to i.redd.it with no path) names no file; it
        // is text.
        -> RemoteCommentMediaKind.IMAGE.takeUnless {
            val fileName = normalizedPath.substringAfterLast('/')
            fileName.isBlank() || '.' in fileName
        }
        else -> null
    }
}

/** Image services whose files load in comments without a tap (OCB-569). */
internal val COMMENT_IMAGE_HOSTS: Set<String> = setOf(
    // Reddit
    "i.redd.it",
    "preview.redd.it",
    "external-preview.redd.it",
    "reddit-uploaded-media.s3-accelerate.amazonaws.com",
    "matrix.redditspace.com",
    // Common image services
    "i.imgur.com",
    "imgur.com",
    "i.giphy.com",
    "media.tenor.com",
    "c.tenor.com",
    "pbs.twimg.com",
    "upload.wikimedia.org",
    "i.ibb.co",
    "i.postimg.cc",
)

/** Hosts under these domains count as known too (media0.giphy.com …). */
internal val COMMENT_IMAGE_HOST_DOMAINS: Set<String> = setOf(
    "redditmedia.com",
    "giphy.com",
)

internal fun commentImageHostIsKnown(host: String): Boolean =
    host in COMMENT_IMAGE_HOSTS ||
        COMMENT_IMAGE_HOST_DOMAINS.any { domain -> host.endsWith(".$domain") }

private val REDDIT_RELATIVE_LINK_PREFIXES = listOf("/r/", "/u/", "/user/")
