package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.diagnostics.ListingSkipReports
import app.subshell.prototype.data.network.NetworkPage
import app.subshell.prototype.data.network.safeJsonFieldPath
import app.subshell.prototype.data.network.sanitizeJsonFieldPath
import app.subshell.prototype.model.redGifsClipId
import app.subshell.prototype.model.redGifsVideoUri
import app.subshell.prototype.model.CrosspostPreview
import app.subshell.prototype.model.GalleryItem
import app.subshell.prototype.model.MediaFormat
import app.subshell.prototype.model.Poll
import app.subshell.prototype.model.PollOption
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Serializable
internal data class RedditListingDto(
    val kind: String? = null,
    val data: RedditListingDataDto = RedditListingDataDto(),
)

@Serializable
internal data class RedditListingDataDto(
    val after: String? = null,
    val children: List<RedditThingDto> = emptyList(),
)

@Serializable
internal data class RedditThingDto(
    val kind: String? = null,
    val data: RedditPostDataDto = RedditPostDataDto(),
)

@Serializable
internal data class RedditPostDataDto(
    val id: String = "",
    val name: String? = null,
    val subreddit: String? = null,
    @SerialName("subreddit_name_prefixed") val subredditNamePrefixed: String? = null,
    val author: String? = null,
    @SerialName("created_utc") val createdUtc: Double? = null,
    val title: String = "",
    val selftext: String = "",
    // ISSUE42_TITLE_ONLY_SELF_POST_IS_TEXT_V1
    @SerialName("is_self") val isSelf: Boolean = false,
    val score: Long = 0,
    @SerialName("num_comments") val numComments: Long = 0,
    @SerialName("post_hint") val postHint: String? = null,
    @SerialName("is_video") val isVideo: Boolean = false,
    @SerialName("is_gallery") val isGallery: Boolean = false,
    val domain: String? = null,
    @SerialName("url_overridden_by_dest") val destinationUrl: String? = null,
    val permalink: String? = null,
    @SerialName("crosspost_parent") val crosspostParent: String? = null,
    @SerialName("crosspost_parent_list")
    val crosspostParentList: List<RedditCrosspostParentDto> = emptyList(),
    val preview: RedditPreviewDto? = null,
    @SerialName("gallery_data") val galleryData: RedditGalleryDataDto? = null,
    @SerialName("media_metadata") val mediaMetadata: Map<String, RedditMediaMetadataDto> = emptyMap(),
    // EDITREADBACK01_EDITED_STAMP_V1: false, or epoch seconds of the last edit.
    val edited: JsonElement? = null,
    // POLLDATA02_TOP_LEVEL_FALSE_IS_NO_POLL_V1: Reddit also sends false at the
    // top level (an upvoted-history page failed whole on it, 2026-10-18).
    @SerialName("poll_data")
    @Serializable(with = RedditCrosspostPollDataSerializer::class)
    val pollData: RedditPollDataDto? = null,
    val media: RedditSecureMediaDto? = null,
    @SerialName("secure_media") val secureMedia: RedditSecureMediaDto? = null,
    @SerialName("link_flair_text") val linkFlairText: String? = null,
    val stickied: Boolean = false,
    val distinguished: String? = null,
    val spoiler: Boolean = false,
    @SerialName("over_18") val over18: Boolean = false,
    val saved: Boolean = false,
    val likes: Boolean? = null,
    // USERFLAIR01A2_REDDIT_POST_AUTHOR_FLAIR_V1
    @SerialName("author_flair_text")
    val authorFlairText: String? = null,
    @SerialName("author_flair_text_color")
    val authorFlairTextColor: String? = null,
    @SerialName("author_flair_background_color")
    val authorFlairBackgroundColor: String? = null,
    // USERFLAIR01A3A_POST_AUTHOR_RICHTEXT_V1
    @SerialName("author_flair_richtext")
    val authorFlairRichText: List<RedditFlairRichTextDto> = emptyList(),
    // ISSUE44_POST_FLAIR_RICHTEXT_V1
    @SerialName("link_flair_richtext")
    val linkFlairRichText: List<RedditFlairRichTextDto> = emptyList(),
    // CROSSPOST01A1B_DESTINATION_FLAIR_ID_READBACK_V1
    @SerialName("link_flair_template_id") val linkFlairTemplateId: String? = null,
)

@Serializable
internal data class RedditCrosspostParentDto(
    val id: String = "",
    val name: String? = null,
    val subreddit: String? = null,
    @SerialName("subreddit_name_prefixed") val subredditNamePrefixed: String? = null,
    val author: String? = null,
    @SerialName("created_utc") val createdUtc: Double? = null,
    val title: String = "",
    val selftext: String = "",
    // ISSUE42_TITLE_ONLY_SELF_POST_IS_TEXT_V1
    @SerialName("is_self") val isSelf: Boolean = false,
    val score: Long = 0,
    @SerialName("num_comments") val numComments: Long = 0,
    @SerialName("post_hint") val postHint: String? = null,
    @SerialName("is_video") val isVideo: Boolean = false,
    @SerialName("is_gallery") val isGallery: Boolean = false,
    val domain: String? = null,
    @SerialName("url_overridden_by_dest") val destinationUrl: String? = null,
    val permalink: String? = null,
    val preview: RedditPreviewDto? = null,
    @SerialName("gallery_data") val galleryData: RedditGalleryDataDto? = null,
    @SerialName("media_metadata") val mediaMetadata: Map<String, RedditMediaMetadataDto> = emptyMap(),
    @SerialName("poll_data")
    @Serializable(with = RedditCrosspostPollDataSerializer::class)
    val pollData: RedditPollDataDto? = null,
    val media: RedditSecureMediaDto? = null,
    @SerialName("secure_media") val secureMedia: RedditSecureMediaDto? = null,
    @SerialName("link_flair_text") val linkFlairText: String? = null,
    // ISSUE44_POST_FLAIR_RICHTEXT_V1
    @SerialName("link_flair_richtext")
    val linkFlairRichText: List<RedditFlairRichTextDto> = emptyList(),
    val spoiler: Boolean = false,
    @SerialName("over_18") val over18: Boolean = false,
)

// REDDITLISTING01_CROSSPOST_POLL_FALSE_TOLERANCE_V1
//
// Reddit has been observed returning boolean false for poll_data inside
// crosspost_parent_list. In that position false means that there is no
// poll payload. Keep the tolerance local to the crosspost DTO:
//   false -> null
//   null/object -> canonical nullable RedditPollDataDto decoding
//   every other JSON shape -> canonical decode failure
internal object RedditCrosspostPollDataSerializer :
    KSerializer<RedditPollDataDto?> {

    private val delegate =
        RedditPollDataDto.serializer().nullable

    override val descriptor: SerialDescriptor =
        delegate.descriptor

    override fun deserialize(
        decoder: Decoder,
    ): RedditPollDataDto? {
        val jsonDecoder =
            decoder as? JsonDecoder
                ?: return delegate.deserialize(decoder)

        val element =
            jsonDecoder.decodeJsonElement()

        if (
            element is JsonPrimitive &&
            !element.isString &&
            element.booleanOrNull == false
        ) {
            return null
        }

        return jsonDecoder.json.decodeFromJsonElement(
            delegate,
            element,
        )
    }

    override fun serialize(
        encoder: Encoder,
        value: RedditPollDataDto?,
    ) {
        delegate.serialize(encoder, value)
    }
}


@Serializable
internal data class RedditPollDataDto(
    val options: List<RedditPollOptionDto> = emptyList(),
    @SerialName("total_vote_count") val totalVoteCount: Long = 0,
    @SerialName("voting_end_timestamp") val votingEndTimestamp: Long? = null,
)

@Serializable
internal data class RedditPollOptionDto(
    val id: String = "",
    val text: String = "",
    @SerialName("vote_count") val voteCount: Long? = null,
)

@Serializable
internal data class RedditSecureMediaDto(
    @SerialName("reddit_video") val redditVideo: RedditVideoDto? = null,
)

@Serializable
internal data class RedditVideoDto(
    @SerialName("dash_url") val dashUrl: String? = null,
    @SerialName("hls_url") val hlsUrl: String? = null,
    @SerialName("fallback_url") val fallbackUrl: String? = null,
    @SerialName("is_gif") val isGif: Boolean = false,
    val width: Int? = null,
    val height: Int? = null,
)

@Serializable
internal data class RedditPreviewDto(
    val images: List<RedditPreviewImageDto> = emptyList(),
    @SerialName("reddit_video_preview")
    val redditVideoPreview: RedditVideoDto? = null,
)

@Serializable
internal data class RedditPreviewImageDto(
    val source: RedditPreviewImageSourceDto? = null,
    val variants: RedditPreviewImageVariantsDto = RedditPreviewImageVariantsDto(),
)

@Serializable
internal data class RedditPreviewImageVariantsDto(
    val gif: RedditPreviewImageVariantDto? = null,
    val mp4: RedditPreviewImageVariantDto? = null,
)

@Serializable
internal data class RedditPreviewImageVariantDto(
    val source: RedditPreviewImageSourceDto? = null,
)

@Serializable
internal data class RedditPreviewImageSourceDto(
    val url: String? = null,
    val width: Int? = null,
    val height: Int? = null,
)

@Serializable
internal data class RedditGalleryDataDto(
    val items: List<RedditGalleryItemDto> = emptyList(),
)

@Serializable
internal data class RedditGalleryItemDto(
    @SerialName("media_id") val mediaId: String? = null,
    val caption: String? = null,
    @SerialName("outbound_url") val outboundUrl: String? = null,
)

@Serializable
internal data class RedditMediaMetadataDto(
    val status: String? = null,
    @SerialName("e") val mediaKind: String? = null,
    @SerialName("m") val mimeType: String? = null,
    @SerialName("s") val source: RedditMediaImageSourceDto? = null,
    @SerialName("p") val previews: List<RedditMediaImageSourceDto> = emptyList(),
)

@Serializable
internal data class RedditMediaImageSourceDto(
    @SerialName("u") val url: String? = null,
    @SerialName("gif") val gifUrl: String? = null,
    @SerialName("mp4") val mp4Url: String? = null,
    @SerialName("x") val width: Int? = null,
    @SerialName("y") val height: Int? = null,
)

// LISTINGSKIP01_ONE_BAD_POST_DOES_NOT_FAIL_THE_PAGE_V1 (owner 2026-10-18): a
// read listing (feeds, search, a user's posts, custom feeds, account history)
// decodes each child on its own and leaves out one it cannot read, as the
// saved history already did; one post with an unexpected shape (POLLDATA02)
// failed the whole page. The envelope stays strict: a body that is not a
// listing, or children that are not a list, is still MALFORMED_JSON. Read-backs
// after a write and single-post lookups keep RedditListingDto, where a skipped
// post would read as missing.
@Serializable
internal data class RedditReadListingDto(
    val kind: String? = null,
    val data: RedditReadListingDataDto = RedditReadListingDataDto(),
)

@Serializable
internal data class RedditReadListingDataDto(
    val after: String? = null,
    val children: List<JsonElement> = emptyList(),
)

internal fun RedditReadListingDto.toNetworkPage(json: Json, nowEpochSeconds: Long): NetworkPage<Post> =
    RedditListingDto(
        kind = kind,
        data = RedditListingDataDto(
            after = data.after,
            children = data.children.mapIndexedNotNull { index, child ->
                try {
                    json.decodeFromJsonElement(RedditThingDto.serializer(), child)
                } catch (error: SerializationException) {
                    reportSkippedChild(index, error)
                    null
                } catch (error: IllegalArgumentException) {
                    reportSkippedChild(index, error)
                    null
                }
            },
        ),
    ).toNetworkPage(nowEpochSeconds)

// DIAG04_LISTING_SKIP_EVENT_V1: the skipped child's own field, under its place in the listing.
private fun reportSkippedChild(index: Int, error: Throwable) {
    val childPath = error.safeJsonFieldPath()?.removePrefix("$").orEmpty()
    ListingSkipReports.report(sanitizeJsonFieldPath("$.data.children[$index]$childPath"))
}

internal fun RedditListingDto.toNetworkPage(nowEpochSeconds: Long): NetworkPage<Post> =
    NetworkPage(
        items = data.children
            .asSequence()
            .filter { child -> child.kind == null || child.kind == REDDIT_POST_KIND }
            .map(RedditThingDto::data)
            .filter { post -> post.id.isNotBlank() && post.title.isNotBlank() }
            .map { post -> post.toPost(nowEpochSeconds) }
            .toList(),
        nextCursor = data.after?.trim()?.takeIf(String::isNotEmpty),
    )

private fun RedditPostDataDto.toPost(nowEpochSeconds: Long): Post {
    val crosspost = crosspostParentList
        .asSequence()
        .mapNotNull { parent -> parent.toCrosspostPreview(nowEpochSeconds) }
        .firstOrNull()

    val secureRedditVideo = secureMedia?.redditVideo
    val mediaRedditVideo = media?.redditVideo
    val previewRedditVideo = preview?.redditVideoPreview
    val redditVideo = secureRedditVideo ?: mediaRedditVideo ?: previewRedditVideo
    val redditVideoIsGif = sequenceOf(
        secureRedditVideo,
        mediaRedditVideo,
        previewRedditVideo,
    ).any { video -> video?.isGif == true }

    // ISSUE11A1_REDDIT_ORDERED_VIDEO_CANDIDATES_V1
    val dashVideoUrl = redditVideo
        ?.dashUrl
        ?.decodeRedditUrlEntities()
        ?.httpsUrlOrNull()
    val hlsVideoUrl = redditVideo
        ?.hlsUrl
        ?.decodeRedditUrlEntities()
        ?.httpsUrlOrNull()
    val fallbackVideoUrl = redditVideo
        ?.fallbackUrl
        ?.decodeRedditUrlEntities()
        ?.httpsUrlOrNull()
    val videoUrl = sequenceOf(
        dashVideoUrl,
        hlsVideoUrl,
        fallbackVideoUrl,
    )
        .filterNotNull()
        .firstOrNull()
    val previewSource = preview
        ?.images
        ?.firstOrNull()
        ?.source
    val previewImageUrl = previewSource
        ?.url
        ?.decodeRedditUrlEntities()
        ?.httpsUrlOrNull()
    val externalUrl = destinationUrl
        ?.decodeRedditUrlEntities()
        ?.webUrlOrNull()
    val rawDirectImageUrl = externalUrl
        ?.takeIf {
            postHint == "image" ||
                domain == REDDIT_IMAGE_DOMAIN ||
                domain == REDDIT_PREVIEW_IMAGE_DOMAIN
        }
        ?.httpsUrlOrNull()
    val previewGifMp4Url = preview
        ?.images
        ?.firstOrNull()
        ?.variants
        ?.mp4
        ?.source
        ?.url
        ?.decodeRedditUrlEntities()
        ?.httpsUrlOrNull()
    val imageIsGif =
        rawDirectImageUrl?.isRemoteGifImageUrl() == true ||
            previewGifMp4Url != null
    val directImageUrl = rawDirectImageUrl?.let { url ->
        if (imageIsGif && previewGifMp4Url == null) {
            url.canonicalRedditGifSourceUrl()
        } else {
            url
        }
    }
    val imageUrl = if (imageIsGif && previewGifMp4Url != null) {
        previewImageUrl ?: directImageUrl
    } else {
        directImageUrl ?: previewImageUrl
    }
    // ISSUE207_REDGIFS_PLAYS_IN_THE_APP_V1: Reddit's own copy plays first; only
    // an NSFW RedGIFs post without one becomes a video the player resolves.
    val redGifsVideoUrl = redGifsClipId(externalUrl)
        ?.takeIf { over18 && videoUrl == null && previewGifMp4Url == null && galleryData == null }
        ?.let(::redGifsVideoUri)
    val resolvedVideoUrl = if (imageIsGif) previewGifMp4Url ?: videoUrl else videoUrl ?: redGifsVideoUrl
    val usesPreviewGifMp4 = imageIsGif && previewGifMp4Url != null
    val resolvedVideoHlsUrl = hlsVideoUrl.takeUnless { usesPreviewGifMp4 }
    val resolvedVideoFallbackUrl = fallbackVideoUrl.takeUnless { usesPreviewGifMp4 }
    val poll = pollData?.toPoll(nowEpochSeconds)
    val galleryItems = galleryData
        ?.items
        .orEmpty()
        .mapIndexedNotNull { index, item ->
            val mediaId = item.mediaId?.trim()?.takeIf(String::isNotEmpty)
                ?: return@mapIndexedNotNull null
            val metadata = mediaMetadata[mediaId]
                ?: return@mapIndexedNotNull null
            val remoteImageUrl = metadata.remoteImageUrl()
                ?: return@mapIndexedNotNull null
            val caption = item.caption.orEmpty()
            val outboundUrl = item.outboundUrl
                ?.decodeRedditUrlEntities()
                ?.webUrlOrNull()
                .orEmpty()
            GalleryItem(
                id = mediaId,
                label = caption.ifBlank { "Image ${index + 1}" },
                format = if (metadata.mimeType.equals("image/gif", ignoreCase = true)) {
                    MediaFormat.GIF
                } else {
                    MediaFormat.IMAGE
                },
                remoteImageUri = remoteImageUrl,
                caption = caption,
                outboundUrl = outboundUrl,
            )
        }
    val kind = when {
        crosspost != null -> PostKind.CROSSPOST
        poll != null -> PostKind.POLL
        isVideo || resolvedVideoUrl != null -> PostKind.VIDEO
        galleryItems.isNotEmpty() -> PostKind.GALLERY
        imageIsGif -> PostKind.GIF
        postHint == "image" && imageUrl != null -> PostKind.IMAGE
        postHint == "link" || externalUrl != null -> PostKind.LINK
        // A title-only self post has no body; Reddit still marks it is_self.
        isSelf || selftext.isNotBlank() -> PostKind.TEXT
        else -> PostKind.LINK
    }
    return Post(
        id = name?.takeIf(String::isNotBlank) ?: id,
        community = subredditNamePrefixed
            ?.normalizedRedditCommunityName()
            ?: subreddit?.normalizedRedditCommunityName()
            ?: "unknown",
        author = author?.takeIf(String::isNotBlank) ?: "[deleted]",
        authorFlairText =
            authorFlairText
                ?.trim()
                ?.takeIf(String::isNotEmpty),
        authorFlairTextColor =
            authorFlairTextColor
                ?.trim()
                ?.takeIf(String::isNotEmpty),
        authorFlairBackgroundColor =
            authorFlairBackgroundColor
                ?.trim()
                ?.takeIf(String::isNotEmpty),
        authorFlairRichText =
            authorFlairRichText.toFlairRichTextSegments(),
        age = redditRelativeAgeLabel(nowEpochSeconds, createdUtc?.toLong()),
        title = title,
        // ISSUE8_INLINE_REDDIT_MEDIA_V1: inline images, Giphy and emotes in a
        // self post resolve like a comment's; the gallery is not repeated.
        body = (
            if (poll != null) selftext.withoutRedundantRedditPollLink(id) else selftext
        ).withRedditMediaAnnotations(mediaMetadata, appendUnplaced = false),
        score = score.toSafeInt(),
        comments = numComments.toSafeInt(),
        kind = kind,
        mediaLabel = when {
            kind == PostKind.CROSSPOST -> ""
            kind == PostKind.VIDEO && previewGifMp4Url != null -> "GIF"
            kind == PostKind.VIDEO && redditVideoIsGif -> "GIF"
            kind == PostKind.VIDEO -> "Video"
            kind == PostKind.GIF -> "GIF"
            kind == PostKind.IMAGE -> "Image"
            kind == PostKind.GALLERY -> "${galleryItems.size} images"
            kind == PostKind.LINK -> externalUrl.orEmpty()
            else -> ""
        },
        remoteVideoUri = resolvedVideoUrl,
        remoteVideoHlsUri = resolvedVideoHlsUrl,
        remoteVideoFallbackUri = resolvedVideoFallbackUrl,
        gallery = galleryItems,
        crosspost = crosspost,
        poll = poll,
        domain = domain
            ?.takeIf(String::isNotBlank)
            ?: externalUrl?.toHttpUrlOrNull()?.host?.removePrefix("www."),
        flair = linkFlairText?.takeIf(String::isNotBlank),
        flairRichText = linkFlairText
            ?.takeIf(String::isNotBlank)
            ?.let { linkFlairRichText.toFlairRichTextSegments() }
            .orEmpty(),
        pinned = stickied,
        moderatorPost = distinguished == "moderator",
        spoiler = spoiler,
        nsfw = over18,
        saved = saved,
        vote = when (likes) {
            true -> 1
            false -> -1
            null -> 0
        },
        remoteImageUri = imageUrl,
        videoWidth = if (kind == PostKind.VIDEO) {
            previewSource?.width?.takeIf { it > 0 }
                ?: redditVideo?.width?.takeIf { it > 0 }
        } else {
            null
        },
        videoHeight = if (kind == PostKind.VIDEO) {
            previewSource?.height?.takeIf { it > 0 }
                ?: redditVideo?.height?.takeIf { it > 0 }
        } else {
            null
        },
        externalUrl = externalUrl,
        permalink = permalink
            ?.decodeRedditUrlEntities()
            ?.trim()
            ?.takeIf(String::isNotEmpty),
    )
}

private fun RedditCrosspostParentDto.toCrosspostPreview(
    nowEpochSeconds: Long,
): CrosspostPreview? {
    val sourceCommunity = subredditNamePrefixed
        ?.normalizedRedditCommunityName()
        ?: subreddit?.normalizedRedditCommunityName()
        ?: return null
    val sourceTitle = title.trim().takeIf(String::isNotEmpty)
        ?: return null

    val sourcePost = RedditPostDataDto(
        id = id,
        name = name,
        subreddit = sourceCommunity,
        subredditNamePrefixed = "r/$sourceCommunity",
        author = author,
        createdUtc = createdUtc,
        title = sourceTitle,
        selftext = selftext,
        isSelf = isSelf,
        score = score,
        numComments = numComments,
        postHint = postHint,
        isVideo = isVideo,
        isGallery = isGallery,
        domain = domain,
        destinationUrl = destinationUrl,
        permalink = permalink,
        preview = preview,
        galleryData = galleryData,
        mediaMetadata = mediaMetadata,
        pollData = pollData,
        media = media,
        secureMedia = secureMedia,
        linkFlairText = linkFlairText,
        linkFlairRichText = linkFlairRichText,
        spoiler = spoiler,
        over18 = over18,
    ).toPost(nowEpochSeconds)

    return CrosspostPreview(
        community = sourcePost.community,
        author = sourcePost.author,
        title = sourcePost.title,
        score = sourcePost.score,
        id = sourcePost.id,
        age = sourcePost.age,
        body = sourcePost.body,
        comments = sourcePost.comments,
        kind = sourcePost.kind,
        mediaLabel = sourcePost.mediaLabel,
        remoteVideoUri = sourcePost.remoteVideoUri,
        remoteVideoHlsUri = sourcePost.remoteVideoHlsUri,
        remoteVideoFallbackUri = sourcePost.remoteVideoFallbackUri,
        gallery = sourcePost.gallery,
        domain = sourcePost.domain,
        poll = sourcePost.poll,
        flair = sourcePost.flair,
        spoiler = sourcePost.spoiler,
        nsfw = sourcePost.nsfw,
        remoteImageUri = sourcePost.remoteImageUri,
        videoWidth = sourcePost.videoWidth,
        videoHeight = sourcePost.videoHeight,
        externalUrl = sourcePost.externalUrl,
        permalink = sourcePost.permalink,
        flairRichText = sourcePost.flairRichText,
    )
}

private fun RedditPollDataDto.toPoll(nowEpochSeconds: Long): Poll? {
    val validOptions = options.mapNotNull { option ->
        val optionId = option.id.trim().takeIf(String::isNotEmpty)
            ?: return@mapNotNull null
        val label = option.text.trim().takeIf(String::isNotEmpty)
            ?: return@mapNotNull null
        option to PollOption(
            id = optionId,
            label = label,
            votes = option.voteCount
                ?.coerceAtLeast(0L)
                ?.toSafeInt()
                ?: 0,
        )
    }
    if (validOptions.size < 2) return null

    val endEpochMillis = votingEndTimestamp?.normalizeRedditPollEpochMillis()
    val nowEpochMillis = nowEpochSeconds.coerceAtLeast(0L) * 1_000L
    return Poll(
        options = validOptions.map { (_, mapped) -> mapped },
        totalVotes = totalVoteCount.coerceAtLeast(0L).toSafeInt(),
        closesIn = redditPollTimingLabel(nowEpochMillis, endEpochMillis),
        resultsVisible = validOptions.all { (source, _) -> source.voteCount != null },
        isClosed = endEpochMillis != null && endEpochMillis <= nowEpochMillis,
    )
}

private fun Long.normalizeRedditPollEpochMillis(): Long? = when {
    this <= 0L -> null
    this < 100_000_000_000L -> this * 1_000L
    else -> this
}

private fun redditPollTimingLabel(nowEpochMillis: Long, endEpochMillis: Long?): String {
    if (endEpochMillis == null) return "Voting end unavailable"
    val remainingMillis = endEpochMillis - nowEpochMillis
    if (remainingMillis <= 0L) return "Closed"

    val remainingSeconds = (remainingMillis + 999L) / 1_000L
    return when {
        remainingSeconds < 3_600L ->
            "${((remainingSeconds + 59L) / 60L).coerceAtLeast(1L)}m left"
        remainingSeconds < 86_400L ->
            "${(remainingSeconds + 3_599L) / 3_600L}h left"
        else ->
            "${(remainingSeconds + 86_399L) / 86_400L}d left"
    }
}

private fun String.withoutRedundantRedditPollLink(postId: String): String {
    val normalizedPostId = postId
        .trim()
        .removePrefix("t3_")
        .lowercase()
        .takeIf { id ->
            id.isNotEmpty() &&
                id.all { character -> character in 'a'..'z' || character in '0'..'9' }
        }
        ?: return this
    val pollTarget = java.net.URI(
        "https",
        "www.reddit.com",
        "/poll/$normalizedPostId",
        null,
    ).toString()
    return lineSequence()
        .filterNot { line ->
            val trimmed = line.trim()
            val openParen = trimmed.lastIndexOf("](")
            trimmed.startsWith("[") &&
                openParen > 1 &&
                trimmed.endsWith(")") &&
                trimmed.substring(openParen + 2, trimmed.length - 1)
                    .trimEnd('/')
                    .equals(pollTarget, ignoreCase = true)
        }
        .joinToString("\n")
        .trim()
}

internal fun String.isRemoteGifImageUrl(): Boolean =
    toHttpUrlOrNull()
        ?.encodedPath
        ?.lowercase()
        ?.endsWith(".gif") == true

internal fun String.canonicalRedditGifSourceUrl(): String {
    val parsed = toHttpUrlOrNull() ?: return this
    if (
        !parsed.host.equals(REDDIT_PREVIEW_IMAGE_DOMAIN, ignoreCase = true) ||
        !parsed.encodedPath.lowercase().endsWith(".gif")
    ) {
        return this
    }
    return parsed.newBuilder()
        .host(REDDIT_IMAGE_DOMAIN)
        .query(null)
        .fragment(null)
        .build()
        .toString()
}

internal fun String.normalizedRedditCommunityName(): String? {
    val trimmed = trim()
    val candidate = when {
        trimmed.startsWith("/r/", ignoreCase = true) -> trimmed.drop(3)
        trimmed.startsWith("r/", ignoreCase = true) -> trimmed.drop(2)
        else -> trimmed
    }
    return candidate.takeIf { name ->
        name.length in 1..MAX_REDDIT_COMMUNITY_NAME_LENGTH &&
            name.all { character ->
                character.isLetterOrDigit() || character == '_' || character == '-'
            }
    }
}

internal fun RedditMediaMetadataDto.remoteImageUrl(): String? {
    val valid = status == null || status == REDDIT_MEDIA_STATUS_VALID
    val image =
        mediaKind == REDDIT_MEDIA_KIND_IMAGE ||
            mediaKind == REDDIT_MEDIA_KIND_ANIMATED_IMAGE ||
            mimeType?.startsWith("image/") == true
    if (!valid || !image) return null
    val animated =
        mediaKind == REDDIT_MEDIA_KIND_ANIMATED_IMAGE ||
            mimeType.equals("image/gif", ignoreCase = true)
    val sourceUrl = if (animated) {
        source?.gifUrl ?: source?.url
    } else {
        source?.url
    }
    return (sourceUrl ?: previews.lastOrNull()?.url)
        ?.decodeRedditUrlEntities()
        ?.httpsUrlOrNull()
}

private fun String.decodeRedditUrlEntities(): String =
    replace("&amp;", "&")

private fun String.webUrlOrNull(): String? {
    val candidate = trim()
    val parsed = candidate.toHttpUrlOrNull() ?: return null
    return candidate.takeIf {
        parsed.scheme in setOf("http", "https") &&
            parsed.username.isEmpty() &&
            parsed.password.isEmpty()
    }
}

private fun String.httpsUrlOrNull(): String? {
    val candidate = trim()
    val parsed = candidate.toHttpUrlOrNull() ?: return null
    return candidate.takeIf {
        parsed.isHttps &&
            parsed.username.isEmpty() &&
            parsed.password.isEmpty()
    }
}

private fun Long.toSafeInt(): Int = coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()

internal fun redditRelativeAgeLabel(
    nowEpochSeconds: Long,
    createdEpochSeconds: Long?,
): String {
    if (createdEpochSeconds == null) return "—"
    val elapsed = (nowEpochSeconds - createdEpochSeconds).coerceAtLeast(0L)
    return when {
        elapsed < 3_600L -> "${elapsed / 60L}m"
        elapsed < 86_400L -> "${elapsed / 3_600L}h"
        elapsed < 2_592_000L -> "${elapsed / 86_400L}d"
        elapsed < 31_536_000L -> "${elapsed / 2_592_000L}mo"
        else -> "${elapsed / 31_536_000L}y"
    }
}

private const val REDDIT_POST_KIND = "t3"
private const val REDDIT_IMAGE_DOMAIN = "i.redd.it"
private const val REDDIT_PREVIEW_IMAGE_DOMAIN = "preview.redd.it"
private const val REDDIT_MEDIA_KIND_IMAGE = "Image"
private const val REDDIT_MEDIA_KIND_ANIMATED_IMAGE = "AnimatedImage"
private const val REDDIT_MEDIA_STATUS_VALID = "valid"
private const val MAX_REDDIT_COMMUNITY_NAME_LENGTH = 100
