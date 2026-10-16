package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.network.FormPostRequest
import app.subshell.prototype.data.account.normalizeCrosspostSourceFullname
import app.subshell.prototype.data.network.JsonPostRequest
import app.subshell.prototype.data.network.NetworkProtocolFailureKind
import app.subshell.prototype.data.network.NetworkResponseMetadata
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.NetworkTransportFailureKind
import app.subshell.prototype.data.network.SubshellHttpClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal data class RedditGallerySubmitItem(
    val mediaId: String,
    val caption: String = "",
    val outboundUrl: String = "",
)

@Serializable
internal data class RedditGallerySubmitRequestDto(
    @SerialName("api_type") val apiType: String = "json",
    val items: List<RedditGallerySubmitItemDto>,
    val nsfw: Boolean,
    @SerialName("sendreplies") val sendReplies: Boolean,
    val spoiler: Boolean,
    val sr: String,
    val title: String,
    @SerialName("validate_on_submit") val validateOnSubmit: Boolean = true,
    @SerialName("show_error_list") val showErrorList: Boolean = true,
    val text: String? = null,
    @SerialName("flair_id") val flairId: String? = null,
    @SerialName("flair_text") val flairText: String? = null,
)

@Serializable
internal data class RedditGallerySubmitItemDto(
    val caption: String,
    @SerialName("media_id") val mediaId: String,
    @SerialName("outbound_url") val outboundUrl: String,
)

internal enum class RedditSingleMediaSubmitKind(val apiValue: String) {
    IMAGE("image"),
    VIDEO("video"),
    VIDEOGIF("videogif"),
}

internal sealed interface RedditTextPostSubmitNetworkResult {
    data class Created(
        val post: RedditSubmittedTextPost,
        val metadata: NetworkResponseMetadata,
    ) : RedditTextPostSubmitNetworkResult

    data class ApiFailure(
        val errors: List<RedditTextPostApiError>,
        val metadata: NetworkResponseMetadata,
    ) : RedditTextPostSubmitNetworkResult

    data class MediaPending(
        val webSocketUrl: String,
        val metadata: NetworkResponseMetadata,
    ) : RedditTextPostSubmitNetworkResult

    data class HttpFailure(
        val statusCode: Int,
        val retryable: Boolean,
        val metadata: NetworkResponseMetadata,
    ) : RedditTextPostSubmitNetworkResult

    data class TransportFailure(
        val kind: NetworkTransportFailureKind,
        val retryable: Boolean,
    ) : RedditTextPostSubmitNetworkResult

    data class ProtocolFailure(
        val kind: NetworkProtocolFailureKind,
        val jsonPath: String? = null,
    ) : RedditTextPostSubmitNetworkResult

    data object MalformedPayload : RedditTextPostSubmitNetworkResult
}

internal class RedditTextPostSubmitNetworkDataSource(
    private val httpClient: SubshellHttpClient,
) {
    suspend fun submit(
        community: String,
        title: String,
        text: String,
        sendReplies: Boolean,
        nsfw: Boolean,
        spoiler: Boolean,
        linkUrl: String? = null,
        mediaUrl: String? = null,
        mediaKind: RedditSingleMediaSubmitKind = RedditSingleMediaSubmitKind.IMAGE,
        videoPosterUrl: String? = null,
        flairId: String? = null,
        flairText: String? = null,
        crosspostFullname: String? = null,
    ): RedditTextPostSubmitNetworkResult {
        val request = redditTextPostSubmitFormRequest(
            community = community,
            title = title,
            text = text,
            sendReplies = sendReplies,
            nsfw = nsfw,
            spoiler = spoiler,
            linkUrl = linkUrl,
            mediaUrl = mediaUrl,
            mediaKind = mediaKind,
            videoPosterUrl = videoPosterUrl,
            flairId = flairId,
            flairText = flairText,
            crosspostFullname = crosspostFullname,
        )
        val normalizedFields = request.fields.toMap()
        return when (
            val result = httpClient.postFormJson(
                request = request,
                deserializer = RedditTextPostSubmitResponseDto.serializer(),
            )
        ) {
            is NetworkResult.Success -> when (
                val payload = result.value.toTextPostSubmitPayload(
                    community = normalizedFields.getValue("sr"),
                    title = normalizedFields.getValue("title"),
                    text = normalizedFields["text"].orEmpty(),
                    mediaSubmission = normalizedFields["kind"] in setOf(
                        "image",
                        "video",
                        "videogif",
                    ),
                )
            ) {
                is RedditTextPostSubmitPayload.Created ->
                    if (isCrosspostCreateResponseIdentityValid(
                            sourceFullname = normalizedFields["crosspost_fullname"],
                            createdFullname = payload.post.fullname,
                        )
                    ) {
                        RedditTextPostSubmitNetworkResult.Created(
                            post = payload.post,
                            metadata = result.metadata,
                        )
                    } else {
                        RedditTextPostSubmitNetworkResult.MalformedPayload
                    }

                is RedditTextPostSubmitPayload.ApiFailure ->
                    RedditTextPostSubmitNetworkResult.ApiFailure(
                        errors = payload.errors,
                        metadata = result.metadata,
                    )

                is RedditTextPostSubmitPayload.AwaitMediaCompletion ->
                    RedditTextPostSubmitNetworkResult.MediaPending(
                        webSocketUrl = payload.webSocketUrl,
                        metadata = result.metadata,
                    )

                RedditTextPostSubmitPayload.Malformed ->
                    RedditTextPostSubmitNetworkResult.MalformedPayload
            }

            is NetworkResult.HttpFailure -> RedditTextPostSubmitNetworkResult.HttpFailure(
                statusCode = result.statusCode,
                retryable = result.retryable,
                metadata = result.metadata,
            )

            is NetworkResult.TransportFailure ->
                RedditTextPostSubmitNetworkResult.TransportFailure(
                    kind = result.kind,
                    retryable = result.retryable,
                )

            is NetworkResult.ProtocolFailure ->
                RedditTextPostSubmitNetworkResult.ProtocolFailure(
                    kind = result.kind,
                    jsonPath = result.jsonPath,
                )
        }
    }
    suspend fun submitGallery(
        community: String,
        title: String,
        text: String,
        sendReplies: Boolean,
        nsfw: Boolean,
        spoiler: Boolean,
        items: List<RedditGallerySubmitItem>,
        flairId: String? = null,
        flairText: String? = null,
    ): RedditTextPostSubmitNetworkResult {
        val request = redditGallerySubmitRequest(
            community = community,
            title = title,
            text = text,
            sendReplies = sendReplies,
            nsfw = nsfw,
            spoiler = spoiler,
            items = items,
            flairId = flairId,
            flairText = flairText,
        )
        return when (
            val result = httpClient.postJson(
                request = JsonPostRequest(
                    pathSegments = listOf("api", "submit_gallery_post"),
                    body = request,
                ),
                serializer = RedditGallerySubmitRequestDto.serializer(),
                deserializer = RedditTextPostSubmitResponseDto.serializer(),
            )
        ) {
            is NetworkResult.Success -> when (
                val payload = result.value.toGallerySubmitPayload(
                    community = request.sr,
                    title = request.title,
                    text = request.text.orEmpty(),
                )
            ) {
                is RedditTextPostSubmitPayload.Created ->
                    RedditTextPostSubmitNetworkResult.Created(
                        post = payload.post,
                        metadata = result.metadata,
                    )
                is RedditTextPostSubmitPayload.ApiFailure ->
                    RedditTextPostSubmitNetworkResult.ApiFailure(
                        errors = payload.errors,
                        metadata = result.metadata,
                    )
                is RedditTextPostSubmitPayload.AwaitMediaCompletion ->
                    RedditTextPostSubmitNetworkResult.MalformedPayload
                RedditTextPostSubmitPayload.Malformed ->
                    RedditTextPostSubmitNetworkResult.MalformedPayload
            }
            is NetworkResult.HttpFailure -> RedditTextPostSubmitNetworkResult.HttpFailure(
                statusCode = result.statusCode,
                retryable = result.retryable,
                metadata = result.metadata,
            )
            is NetworkResult.TransportFailure ->
                RedditTextPostSubmitNetworkResult.TransportFailure(
                    kind = result.kind,
                    retryable = result.retryable,
                )
            is NetworkResult.ProtocolFailure ->
                RedditTextPostSubmitNetworkResult.ProtocolFailure(result.kind, result.jsonPath)
        }
    }

}

internal fun redditTextPostSubmitFormRequest(
    community: String,
    title: String,
    text: String,
    sendReplies: Boolean = true,
    nsfw: Boolean = false,
    spoiler: Boolean = false,
    linkUrl: String? = null,
    mediaUrl: String? = null,
    mediaKind: RedditSingleMediaSubmitKind = RedditSingleMediaSubmitKind.IMAGE,
    videoPosterUrl: String? = null,
    flairId: String? = null,
    flairText: String? = null,
    crosspostFullname: String? = null,
): FormPostRequest = FormPostRequest(
    pathSegments = listOf("api", "submit"),
    fields = buildList {
        // CROSSPOST01A1A_NATIVE_FORM_V1
        val normalizedCrosspost = crosspostFullname?.let(::normalizeCrosspostSourceFullname)
        if (normalizedCrosspost != null) {
            require(text.isBlank()) { "Crosspost cannot contain a replacement body" }
            require(linkUrl == null && mediaUrl == null && videoPosterUrl == null) {
                "Crosspost cannot also be a link or uploaded media"
            }
            require(flairText.isNullOrBlank() || !flairId.isNullOrBlank()) {
                "Crosspost flair text requires a destination flair template"
            }
            require(flairText == null || flairText.length <= 64) {
                "Crosspost flair text is too long"
            }
        }
        require(linkUrl == null || mediaUrl == null) {
            "Reddit post cannot be both link and uploaded media"
        }
        require(
            videoPosterUrl == null ||
                (mediaUrl != null && mediaKind != RedditSingleMediaSubmitKind.IMAGE)
        ) {
            "Video poster requires a video media submission"
        }
        add("api_type" to "json")
        add("raw_json" to "1")
        if (mediaUrl != null) {
            add("resubmit" to "true")
            add("validate_on_submit" to "true")
        }
        add(
            "kind" to when {
                normalizedCrosspost != null -> "crosspost"
                mediaUrl != null -> mediaKind.apiValue
                linkUrl != null -> "link"
                else -> "self"
            },
        )
        add("sr" to normalizeRedditTextPostCommunity(community))
        add("title" to normalizeRedditTextPostTitle(title))
        val normalizedText = normalizeRedditTextPostBody(text)
        when {
            normalizedCrosspost != null -> add("crosspost_fullname" to normalizedCrosspost)
            mediaUrl != null -> {
                add("url" to normalizeRedditPostLink(mediaUrl))
                videoPosterUrl?.let { posterUrl ->
                    add("video_poster_url" to normalizeRedditPostLink(posterUrl))
                }
                if (normalizedText.isNotEmpty()) add("text" to normalizedText)
            }
            linkUrl != null -> {
                add("url" to normalizeRedditPostLink(linkUrl))
                if (normalizedText.isNotEmpty()) add("text" to normalizedText)
            }
            else -> add("text" to normalizedText)
        }
        add("sendreplies" to sendReplies.toString())
        add("nsfw" to nsfw.toString())
        add("spoiler" to spoiler.toString())
        flairId?.trim()?.takeIf(String::isNotEmpty)?.let { add("flair_id" to it) }
        flairText?.trim()?.takeIf(String::isNotEmpty)?.let { add("flair_text" to it) }
    },
)


internal fun redditGallerySubmitRequest(
    community: String,
    title: String,
    text: String,
    sendReplies: Boolean = true,
    nsfw: Boolean = false,
    spoiler: Boolean = false,
    items: List<RedditGallerySubmitItem>,
    flairId: String? = null,
    flairText: String? = null,
): RedditGallerySubmitRequestDto {
    require(items.size in 2..REDDIT_GALLERY_ITEM_MAX_COUNT) {
        "Reddit gallery item count is outside the supported range"
    }
    val normalizedItems = items.map { item ->
        val mediaId = item.mediaId.trim().lowercase()
        require(
            mediaId.length in 1..REDDIT_GALLERY_MEDIA_ID_MAX_LENGTH &&
                mediaId.all(Char::isLetterOrDigit)
        ) {
            "Reddit gallery media ID is invalid"
        }
        require(item.caption.length <= REDDIT_GALLERY_CAPTION_MAX_LENGTH) {
            "Reddit gallery caption is too long"
        }
        val outboundUrl = item.outboundUrl.trim().let { value ->
            if (value.isEmpty()) "" else normalizeRedditPostLink(value)
        }
        RedditGallerySubmitItemDto(
            caption = item.caption,
            mediaId = mediaId,
            outboundUrl = outboundUrl,
        )
    }
    val normalizedText = normalizeRedditTextPostBody(text)
    return RedditGallerySubmitRequestDto(
        items = normalizedItems,
        nsfw = nsfw,
        sendReplies = sendReplies,
        spoiler = spoiler,
        sr = normalizeRedditTextPostCommunity(community),
        title = normalizeRedditTextPostTitle(title),
        text = normalizedText.takeIf(String::isNotEmpty),
        flairId = flairId?.trim()?.takeIf(String::isNotEmpty),
        flairText = flairText?.trim()?.takeIf(String::isNotEmpty),
    )
}

internal fun normalizeRedditPostLink(value: String): String {
    val uri = runCatching { java.net.URI(value.trim()) }.getOrNull()
    require(uri?.scheme in setOf("http", "https") && !uri?.host.isNullOrBlank()) {
        "Reddit post link is invalid"
    }
    return requireNotNull(uri).toASCIIString()
}

internal fun normalizeRedditTextPostCommunity(value: String): String =
    requireNotNull(value.normalizedRedditCommunityName()) {
        "Reddit community name is invalid"
    }.lowercase()

internal fun normalizeRedditTextPostTitle(value: String): String {
    val normalized = value.trim()
    require(normalized.isNotEmpty()) { "Reddit post title cannot be blank" }
    require('\u0000' !in normalized) { "Reddit post title contains a NUL character" }
    require('\n' !in normalized && '\r' !in normalized) {
        "Reddit post title must be one line"
    }
    require(normalized.length <= REDDIT_TEXT_POST_TITLE_MAX_LENGTH) {
        "Reddit post title exceeds the supported limit"
    }
    return normalized
}

internal fun normalizeRedditTextPostBody(value: String): String {
    require('\u0000' !in value) { "Reddit post body contains a NUL character" }
    val normalized = value
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .trim()
    require(normalized.length <= REDDIT_TEXT_POST_BODY_MAX_LENGTH) {
        "Reddit post body exceeds the supported limit"
    }
    return normalized
}

internal const val REDDIT_TEXT_POST_TITLE_MAX_LENGTH = 300
internal const val REDDIT_TEXT_POST_BODY_MAX_LENGTH = 40_000
internal const val REDDIT_GALLERY_ITEM_MAX_COUNT = 20
internal const val REDDIT_GALLERY_CAPTION_MAX_LENGTH = 180
private const val REDDIT_GALLERY_MEDIA_ID_MAX_LENGTH = 64

// CROSSPOST01A1A_CREATED_WRAPPER_IDENTITY_V1
internal fun isCrosspostCreateResponseIdentityValid(
    sourceFullname: String?,
    createdFullname: String,
): Boolean {
    if (sourceFullname == null) return true
    val source = runCatching { normalizeCrosspostSourceFullname(sourceFullname) }
        .getOrNull() ?: return false
    val created = runCatching { normalizeCrosspostSourceFullname(createdFullname) }
        .getOrNull() ?: return false
    return source != created
}
