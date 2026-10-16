package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.model.FlairRichTextSegment
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// USERFLAIR01A3A_SHARED_REDDIT_FLAIR_RICHTEXT_DTO_V1
@Serializable
internal data class RedditFlairRichTextDto(
    @SerialName("e")
    val elementType: String = "",
    @SerialName("t")
    val text: String? = null,
    @SerialName("a")
    val emojiRef: String? = null,
    @SerialName("u")
    val imageUrl: String? = null,
)

internal fun RedditFlairRichTextDto.toFlairRichTextSegment():
    FlairRichTextSegment? {
    val type = elementType
        .trim()
        .lowercase()
        .takeIf(String::isNotEmpty)
        ?: return null

    val normalizedText = text
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.take(512)

    val normalizedEmojiRef = emojiRef
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.take(256)

    val normalizedImageUrl = imageUrl
        ?.trim()
        ?.takeIf {
            it.length <= 2048 &&
                it.startsWith("https://", ignoreCase = true)
        }

    return FlairRichTextSegment(
        elementType = type,
        text = normalizedText,
        emojiRef = normalizedEmojiRef,
        imageUrl = normalizedImageUrl,
    )
}

internal fun List<RedditFlairRichTextDto>.toFlairRichTextSegments():
    List<FlairRichTextSegment> =
    asSequence()
        .mapNotNull(RedditFlairRichTextDto::toFlairRichTextSegment)
        .take(20)
        .toList()
