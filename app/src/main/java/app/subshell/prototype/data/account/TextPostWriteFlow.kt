package app.subshell.prototype.data.account

import java.security.MessageDigest
import app.subshell.prototype.data.error.FailureDetails

/**
 * UI-facing write state for one text-only Reddit post submission.
 *
 * The transport and OAuth implementation remain outside the UI package. The request pins the
 * authenticated account and exposes only a deterministic draft key, never bearer material.
 */
enum class TextPostWritePhase {
    IDLE,
    AUTHORIZATION_REQUIRED,
    AUTHORIZING,
    AWAITING_CALLBACK,
    SENDING,
    RECONCILING,
    FAILED,
    OUTCOME_UNKNOWN,
    SUCCEEDED,
}

enum class TextPostWriteFailureKind {
    CONFIGURATION,
    ACCOUNT_CHANGED,
    BROWSER_LAUNCH,
    CALLBACK,
    AUTHORIZATION,
    SESSION,
    RATE_LIMIT,
    COMMUNITY_UNAVAILABLE,
    PERMISSION,
    SUBMISSION_RESTRICTED,
    CHALLENGE_REQUIRED,
    VALIDATION,
    API,
    HTTP,
    TRANSPORT,
    ALREADY_SUBMITTING,
    // COMPOSER02_SUBMIT_ATTEMPT_ABSENCE_V1: the read-back found no such post.
    NOT_POSTED,
    // COMPOSER02_SUBMIT_ATTEMPT_PER_ACCOUNT_V1: another post of this account
    // is still unconfirmed.
    EARLIER_POST_UNCONFIRMED,
}

enum class TextPostWriteValidationField {
    COMMUNITY,
    TITLE,
    FLAIR,
    BODY,
    LINK,
    MEDIA,
    GENERAL,
}

data class TextPostWriteValidationIssue(
    val field: TextPostWriteValidationField,
    val message: String,
) {
    init {
        require(message.isNotBlank()) { "Text-post validation message cannot be blank" }
        require(message.length <= 240) { "Text-post validation message is too long" }
    }
}


data class TextPostWriteGalleryItem(
    val mediaUri: String,
    val mediaMimeType: String,
    val caption: String = "",
    val outboundUrl: String = "",
) {
    init {
        require(mediaUri.isNotBlank()) { "Gallery media URI cannot be blank" }
        require(mediaMimeType.isNotBlank()) { "Gallery media MIME type cannot be blank" }
        require(caption.length <= MAX_GALLERY_CAPTION_CHARS) {
            "Gallery caption is too long"
        }
        require(outboundUrl.isBlank() || normalizedPostLink(outboundUrl) != null) {
            "Gallery outbound URL is invalid"
        }
    }
}

data class TextPostWriteRequest(
    val accountStableId: String,
    val community: String,
    val title: String,
    val text: String,
    val sendReplies: Boolean = true,
    val nsfw: Boolean = false,
    val spoiler: Boolean = false,
    val linkUrl: String? = null,
    val linkPreviewImageUrl: String? = null,
    val mediaUri: String? = null,
    val mediaMimeType: String? = null,
    val galleryItems: List<TextPostWriteGalleryItem> = emptyList(),
    val flairId: String? = null,
    val flairText: String? = null,
    val mediaAsGif: Boolean = false,
    // CROSSPOST01A1A_REQUEST_SOURCE_IDENTITY_V1
    val crosspostFullname: String? = null,
) {
    val requestKey: String = textPostWriteRequestKey(
        community = community,
        title = title,
        text = text,
        sendReplies = sendReplies,
        nsfw = nsfw,
        spoiler = spoiler,
        linkUrl = linkUrl,
        mediaUri = mediaUri,
        mediaMimeType = mediaMimeType,
        galleryItems = galleryItems,
        flairId = flairId,
        flairText = flairText,
        mediaAsGif = mediaAsGif,
        crosspostFullname = crosspostFullname,
    )

    init {
        if (crosspostFullname != null) {
            normalizeCrosspostSourceFullname(crosspostFullname)
            require(text.isBlank()) { "Crosspost cannot contain a replacement body" }
            require(
                linkUrl == null && linkPreviewImageUrl == null &&
                    mediaUri == null && mediaMimeType == null &&
                    galleryItems.isEmpty() && !mediaAsGif
            ) { "Crosspost cannot also be a link or uploaded media" }
            require(flairText.isNullOrBlank() || !flairId.isNullOrBlank()) {
                "Crosspost flair text requires a destination flair template"
            }
        }
        require(accountStableId.isNotBlank()) { "Text-post account ID cannot be blank" }
        require(normalizedTextPostCommunity(community) != null) {
            "Text-post community is invalid"
        }
        val normalizedTitle = title.trim()
        require(normalizedTitle.isNotEmpty()) { "Text-post title cannot be blank" }
        require(normalizedTitle.length <= MAX_TEXT_POST_TITLE_CHARS) {
            "Text-post title is too long"
        }
        require('\u0000' !in normalizedTitle && '\n' !in normalizedTitle && '\r' !in normalizedTitle) {
            "Text-post title must be one line"
        }
        require('\u0000' !in text) { "Text-post body contains a NUL character" }
        require(normalizedTextPostBody(text).length <= MAX_TEXT_POST_BODY_CHARS) {
            "Text-post body is too long"
        }
        require(linkUrl == null || normalizedPostLink(linkUrl) != null) {
            "Post link URL is invalid"
        }
        require(linkPreviewImageUrl == null || linkUrl != null) {
            "Post link preview image requires a destination URL"
        }
        require((mediaUri == null) == (mediaMimeType == null)) {
            "Post media URI and MIME type must be provided together"
        }
        require(linkUrl == null || mediaUri == null) {
            "A post cannot contain both a destination link and uploaded media"
        }
        require(linkUrl == null || galleryItems.isEmpty()) {
            "A post cannot contain both a destination link and a gallery"
        }
        require(mediaUri == null || galleryItems.isEmpty()) {
            "A post cannot contain both single media and a gallery"
        }
        require(galleryItems.isEmpty() || galleryItems.size in 2..MAX_GALLERY_ITEMS) {
            "A gallery must contain between 2 and $MAX_GALLERY_ITEMS items"
        }
        require(galleryItems.map { it.mediaUri }.distinct().size == galleryItems.size) {
            "Gallery media URIs must be distinct"
        }
        require(mediaUri == null || mediaUri.isNotBlank()) {
            "Post media URI cannot be blank"
        }
        require(mediaMimeType == null || mediaMimeType.isNotBlank()) {
            "Post media MIME type cannot be blank"
        }
        require(
            !mediaAsGif ||
                (
                    mediaUri != null &&
                        mediaMimeType?.startsWith("video/", ignoreCase = true) == true
                    )
        ) {
            "Post-as-GIF requires video media"
        }
        require(
            linkPreviewImageUrl == null || normalizedHttpsPreviewImage(linkPreviewImageUrl) != null,
        ) {
            "Post link preview image URL is invalid"
        }
        require(flairId == null || flairId.isNotBlank()) { "Post flair ID cannot be blank" }
        require(flairText == null || flairText.length <= MAX_POST_FLAIR_TEXT_CHARS) {
            "Post flair text is too long"
        }
    }
}


data class SubmittedTextPostGalleryItem(
    val mediaLocalUri: String,
    val mediaMimeType: String,
    val mediaRemoteImageUrl: String? = null,
    val caption: String = "",
    val outboundUrl: String = "",
) {
    init {
        require(mediaLocalUri.isNotBlank()) { "Submitted gallery media URI cannot be blank" }
        require(mediaMimeType.isNotBlank()) { "Submitted gallery MIME type cannot be blank" }
        require(mediaRemoteImageUrl == null || normalizedHttpsPreviewImage(mediaRemoteImageUrl) != null) {
            "Submitted gallery remote media URL is invalid"
        }
        require(caption.length <= MAX_GALLERY_CAPTION_CHARS) {
            "Submitted gallery caption is too long"
        }
        require(outboundUrl.isBlank() || normalizedPostLink(outboundUrl) != null) {
            "Submitted gallery outbound URL is invalid"
        }
    }
}

data class SubmittedTextPost(
    val fullname: String,
    val id: String,
    val community: String,
    val title: String,
    val text: String,
    val permalink: String?,
    val linkUrl: String? = null,
    val linkPreviewImageUrl: String? = null,
    val mediaLocalUri: String? = null,
    val mediaMimeType: String? = null,
    val mediaRemoteImageUrl: String? = null,
    val videoWidth: Int? = null,
    val videoHeight: Int? = null,
    val galleryItems: List<SubmittedTextPostGalleryItem> = emptyList(),
    val flairText: String? = null,
    val nsfw: Boolean = false,
    val spoiler: Boolean = false,
    val mediaAsGif: Boolean = false,
    // Requested parent identity, not an assertion that listing readback has verified it.
    val crosspostFullname: String? = null,
) {
    init {
        if (crosspostFullname != null) {
            normalizeCrosspostSourceFullname(crosspostFullname)
            require(text.isBlank()) { "Submitted crosspost cannot contain a replacement body" }
            require(
                linkUrl == null && linkPreviewImageUrl == null &&
                    mediaLocalUri == null && mediaMimeType == null &&
                    mediaRemoteImageUrl == null && galleryItems.isEmpty() &&
                    videoWidth == null && videoHeight == null && !mediaAsGif
            ) { "Submitted crosspost cannot also be a link or uploaded media" }
        }
        require(fullname == "t3_$id") { "Submitted text-post fullname must match its ID" }
        require(id.length in 1..MAX_REDDIT_THING_ID_CHARS && id.all(Char::isLetterOrDigit)) {
            "Submitted text-post ID is invalid"
        }
        require(normalizedTextPostCommunity(community) != null) {
            "Submitted text-post community is invalid"
        }
        require(title.isNotBlank()) { "Submitted text-post title cannot be blank" }
        require(permalink == null || permalink.startsWith('/')) {
            "Submitted text-post permalink must be a path"
        }
        require(linkUrl == null || normalizedPostLink(linkUrl) != null) {
            "Submitted post link URL is invalid"
        }
        require(linkPreviewImageUrl == null || linkUrl != null) {
            "Submitted post link preview image requires a destination URL"
        }
        require((mediaLocalUri == null) == (mediaMimeType == null)) {
            "Submitted post media URI and MIME type must be provided together"
        }
        require(linkUrl == null || mediaLocalUri == null) {
            "Submitted post cannot contain both a link and uploaded media"
        }
        require(linkUrl == null || galleryItems.isEmpty()) {
            "Submitted post cannot contain both a link and a gallery"
        }
        require(mediaLocalUri == null || galleryItems.isEmpty()) {
            "Submitted post cannot contain both single media and a gallery"
        }
        require(galleryItems.isEmpty() || galleryItems.size in 2..MAX_GALLERY_ITEMS) {
            "Submitted gallery must contain between 2 and $MAX_GALLERY_ITEMS items"
        }
        require(mediaRemoteImageUrl == null || mediaLocalUri != null) {
            "Submitted remote media URL requires local media identity"
        }
        require(
            mediaRemoteImageUrl == null || normalizedHttpsPreviewImage(mediaRemoteImageUrl) != null,
        ) {
            "Submitted remote media URL is invalid"
        }
        require((videoWidth == null) == (videoHeight == null)) {
            "Submitted video width and height must be provided together"
        }
        require(videoWidth == null || videoWidth > 0) {
            "Submitted video width must be positive"
        }
        require(videoHeight == null || videoHeight > 0) {
            "Submitted video height must be positive"
        }
        require(
            videoWidth == null ||
                mediaMimeType?.startsWith("video/", ignoreCase = true) == true,
        ) {
            "Submitted video dimensions require video media"
        }
        require(
            !mediaAsGif ||
                (
                    mediaLocalUri != null &&
                        mediaMimeType?.startsWith("video/", ignoreCase = true) == true
                    )
        ) {
            "Submitted post-as-GIF identity requires video media"
        }
        require(
            linkPreviewImageUrl == null || normalizedHttpsPreviewImage(linkPreviewImageUrl) != null,
        ) {
            "Submitted post link preview image URL is invalid"
        }
        require(flairText == null || flairText.length <= MAX_POST_FLAIR_TEXT_CHARS) {
            "Submitted post flair text is too long"
        }
    }
}

data class TextPostWriteUiState(
    val phase: TextPostWritePhase = TextPostWritePhase.IDLE,
    val accountStableId: String? = null,
    val requestKey: String? = null,
    val failureKind: TextPostWriteFailureKind? = null,
    val retryable: Boolean = false,
    val retryAfterMs: Long? = null,
    val failureDetails: FailureDetails? = null,
    val validationIssues: List<TextPostWriteValidationIssue> = emptyList(),
    val submittedPost: SubmittedTextPost? = null,
    val completionRevision: Long = 0L,
) {
    init {
        require(completionRevision >= 0L) {
            "Text-post completion revision cannot be negative"
        }
        require(retryAfterMs == null || retryAfterMs >= 0L) {
            "Text-post retry delay cannot be negative"
        }
        require(
            (accountStableId == null && requestKey == null) ||
                (!accountStableId.isNullOrBlank() && !requestKey.isNullOrBlank()),
        ) {
            "Text-post account and request identities must be absent or present together"
        }
        require(phase == TextPostWritePhase.SUCCEEDED || submittedPost == null) {
            "Only a successful text-post state may expose a submitted post"
        }
        require(
            phase in setOf(
                TextPostWritePhase.FAILED,
                TextPostWritePhase.OUTCOME_UNKNOWN,
            ) || failureKind == null,
        ) {
            "Only failed text-post states may expose a failure kind"
        }
        require(phase == TextPostWritePhase.FAILED || validationIssues.isEmpty()) {
            "Only failed text-post states may expose validation issues"
        }
        require(validationIssues.distinct() == validationIssues) {
            "Text-post validation issues must be distinct"
        }
    }

    val inProgress: Boolean
        get() = phase in setOf(
            TextPostWritePhase.AUTHORIZING,
            TextPostWritePhase.AWAITING_CALLBACK,
            TextPostWritePhase.SENDING,
            TextPostWritePhase.RECONCILING,
        )

    fun matchesDraft(requestKey: String): Boolean = this.requestKey == requestKey
}

fun textPostWriteRequestKey(
    community: String,
    title: String,
    text: String,
    sendReplies: Boolean = true,
    nsfw: Boolean = false,
    spoiler: Boolean = false,
    linkUrl: String? = null,
    mediaUri: String? = null,
    mediaMimeType: String? = null,
    galleryItems: List<TextPostWriteGalleryItem> = emptyList(),
    flairId: String? = null,
    flairText: String? = null,
    mediaAsGif: Boolean = false,
    crosspostFullname: String? = null,
): String {
    val canonical = buildString {
        appendLengthPrefixed(normalizedTextPostCommunity(community).orEmpty().lowercase())
        appendLengthPrefixed(title.trim())
        appendLengthPrefixed(normalizedTextPostBody(text))
        append(if (sendReplies) '1' else '0')
        append(if (nsfw) '1' else '0')
        append(if (spoiler) '1' else '0')
        appendLengthPrefixed(linkUrl?.let(::normalizedPostLink).orEmpty())
        appendLengthPrefixed(mediaUri?.trim().orEmpty())
        appendLengthPrefixed(mediaMimeType?.trim()?.lowercase().orEmpty())
        appendLengthPrefixed(galleryItems.size.toString())
        galleryItems.forEach { item ->
            appendLengthPrefixed(item.mediaUri.trim())
            appendLengthPrefixed(item.mediaMimeType.trim().lowercase())
            appendLengthPrefixed(item.caption)
            appendLengthPrefixed(item.outboundUrl.trim().let { value ->
                if (value.isEmpty()) "" else normalizedPostLink(value).orEmpty()
            })
        }
        append(if (mediaAsGif) '1' else '0')
        appendLengthPrefixed(flairId?.trim().orEmpty())
        appendLengthPrefixed(flairText?.trim().orEmpty())
        // CROSSPOST01A1A_KEY_COMPATIBILITY_V1
        // Do not append anything for existing submissions: persisted legacy keys must not change.
        crosspostFullname?.let { source ->
            appendLengthPrefixed("crosspost")
            appendLengthPrefixed(normalizeCrosspostSourceFullname(source))
        }
    }
    return MessageDigest.getInstance("SHA-256")
        .digest(canonical.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

// OCB-087: the same web-link rule as the composer (any scheme case, a host
// with '_'), so a link the composer accepts also builds a request.
private fun normalizedPostLink(value: String): String? = runCatching {
    java.net.URI(value.trim()).takeIf { uri ->
        (uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)) &&
            !(uri.host ?: uri.rawAuthority?.substringAfterLast('@')?.substringBefore(':')).isNullOrBlank()
    }?.toASCIIString()
}.getOrNull()

private fun normalizedHttpsPreviewImage(value: String): String? = normalizedPostLink(value)
    ?.takeIf { normalized ->
        java.net.URI(normalized).scheme.equals("https", ignoreCase = true)
    }

private fun StringBuilder.appendLengthPrefixed(value: String) {
    append(value.length)
    append(':')
    append(value)
    append('|')
}

private fun normalizedTextPostCommunity(value: String): String? {
    val trimmed = value.trim()
    val candidate = when {
        trimmed.startsWith("/r/", ignoreCase = true) -> trimmed.drop(3)
        trimmed.startsWith("r/", ignoreCase = true) -> trimmed.drop(2)
        else -> trimmed
    }
    return candidate.takeIf { name ->
        name.length in 1..MAX_TEXT_POST_COMMUNITY_CHARS &&
            name.all { character ->
                character.isLetterOrDigit() || character == '_' || character == '-'
            }
    }
}

private fun normalizedTextPostBody(value: String): String = value
    .replace("\r\n", "\n")
    .replace('\r', '\n')
    .trim()

private const val MAX_TEXT_POST_COMMUNITY_CHARS = 100
private const val MAX_TEXT_POST_TITLE_CHARS = 300
private const val MAX_TEXT_POST_BODY_CHARS = 40_000
private const val MAX_POST_FLAIR_TEXT_CHARS = 64
private const val MAX_GALLERY_ITEMS = 20
private const val MAX_GALLERY_CAPTION_CHARS = 180
private const val MAX_REDDIT_THING_ID_CHARS = 16
