package app.subshell.prototype.ui

// ISSUE33_SHARE_INTO_SUBSHELL_V1
// What another app shared into Subshell, as the post it starts. A shared
// URL starts a link post, other text a text post, and images an image or
// gallery post. The community is left empty so the composer asks for it.
data class SharedPostContent(
    val type: ComposerPostType,
    val title: String = "",
    val body: String = "",
    val linkUrl: String = "",
    val media: List<ComposerMediaAttachment> = emptyList(),
    /** Shared items that could not be used (unsupported type, over the limit). */
    val skippedCount: Int = 0,
)

/** A shared text as a link post when it is a URL, or a URL with a short caption. */
fun sharedPostFromText(text: String?, subject: String?): SharedPostContent? {
    val body = text?.trim().orEmpty()
    val title = subject?.trim().orEmpty().take(SHARED_POST_TITLE_MAX)
    if (body.isEmpty()) {
        return title.takeIf(String::isNotEmpty)?.let { SharedPostContent(ComposerPostType.TEXT, title = it) }
    }
    val match = SHARED_URL_REGEX.findAll(body).toList().singleOrNull()
    if (match != null) {
        // LOWUI_SHARED_URL_KEEPS_ITS_OWN_BRACKETS_V1 (OCB-092): a closing
        // bracket is trimmed only when it has no opening partner in the URL
        // (…/Mercury_(planet) keeps its ')').
        val url = trimSharedUrlEnd(match.value)
        // The whole match goes, so punctuation after the URL does not end up
        // in the title.
        val caption = body.removeRange(match.range).trim().trimEnd(':', '-', '|').trim()
        if (caption.length <= SHARED_POST_TITLE_MAX && '\n' !in caption) {
            return SharedPostContent(
                type = ComposerPostType.LINK,
                title = title.ifEmpty { caption },
                linkUrl = url,
            )
        }
    }
    return SharedPostContent(ComposerPostType.TEXT, title = title, body = body)
}

/** Shared images as an image or gallery post; [skipped] counts what was left out. */
fun sharedPostFromImages(
    images: List<ComposerMediaAttachment>,
    text: String?,
    subject: String?,
    skipped: Int,
): SharedPostContent? {
    if (images.isEmpty()) return null
    val kept = images.take(COMPOSER_GALLERY_MAX_ITEMS)
    return SharedPostContent(
        type = ComposerPostType.MEDIA,
        title = subject?.trim().orEmpty().take(SHARED_POST_TITLE_MAX),
        body = text?.trim().orEmpty(),
        media = kept,
        skippedCount = skipped + (images.size - kept.size),
    )
}

/** Image types Reddit posts accept, and the composer kind for each. */
fun sharedImageKindOrNull(mimeType: String?): ComposerMediaKind? = when (mimeType?.lowercase()) {
    "image/jpeg", "image/jpg", "image/png", "image/webp" -> ComposerMediaKind.IMAGE
    "image/gif" -> ComposerMediaKind.GIF
    else -> null
}

private val SHARED_URL_REGEX = Regex("https?://\\S+", RegexOption.IGNORE_CASE)
private const val SHARED_POST_TITLE_MAX = 300

internal fun trimSharedUrlEnd(raw: String): String {
    var url = raw
    while (url.isNotEmpty()) {
        val last = url.last()
        val trim = when (last) {
            '.', ',', '!', '?' -> true
            ')' -> url.count { it == ')' } > url.count { it == '(' }
            ']' -> url.count { it == ']' } > url.count { it == '[' }
            else -> false
        }
        if (!trim) break
        url = url.dropLast(1)
    }
    return url
}
