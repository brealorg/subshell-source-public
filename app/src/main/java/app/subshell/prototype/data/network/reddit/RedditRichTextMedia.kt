package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Builds the legacy Reddit RTJSON shape accepted by /api/comment for an inline
 * uploaded image/GIF. /api/media/asset.json returns an asset_id specifically
 * for this publication step; the temporary S3 upload-object URL must never be
 * embedded as the final comment/message media URL.
 */
internal fun redditInlineMediaRichTextJson(
    text: String,
    assetId: String,
): String {
    val normalizedText = text.trim()
    val normalizedAssetId = normalizeRedditInlineMediaAssetId(assetId)
    val document = buildJsonArray {
        if (normalizedText.isNotEmpty()) {
            add(
                buildJsonObject {
                    put("e", "par")
                    put(
                        "c",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("e", "text")
                                    put("t", normalizedText)
                                },
                            )
                        },
                    )
                },
            )
        }
        add(
            buildJsonObject {
                put("e", "img")
                put("id", normalizedAssetId)
            },
        )
    }
    return buildJsonObject { put("document", document) }.toString()
}

/**
 * MEDIARICHTEXT01_CONVERTED_TEXT_V1 (#145): RTJSON text nodes are literal, so
 * the markdown of a comment or message reply with an uploaded image showed raw.
 * The text goes through Reddit's own converter (api/convert_rte_body_format,
 * as the media edits do since ISSUE8B) and the image block is appended. Null
 * means the conversion failed and nothing may be sent; the send can be retried.
 */
internal suspend fun prepareRedditInlineMediaRichText(
    httpClient: SubshellHttpClient,
    text: String,
    assetId: String,
): String? {
    val normalizedAssetId = normalizeRedditInlineMediaAssetId(assetId)
    val markdown = text.trim()
    if (markdown.isEmpty()) return redditInlineMediaRichTextJson(text = "", assetId = normalizedAssetId)
    val converted = when (
        val result = httpClient.postFormJson(
            request = redditConvertRteBodyFormatRequest(markdown),
            deserializer = RedditConvertRteResponseDto.serializer(),
        )
    ) {
        is NetworkResult.Success -> result.value.output?.document?.takeIf(JsonArray::isNotEmpty) ?: return null
        is NetworkResult.HttpFailure,
        is NetworkResult.TransportFailure,
        is NetworkResult.ProtocolFailure,
        -> return null
    }
    val document = buildJsonArray {
        converted.forEach(::add)
        add(
            buildJsonObject {
                put("e", "img")
                put("id", normalizedAssetId)
            },
        )
    }
    return buildJsonObject { put("document", document) }.toString()
}

internal fun normalizeRedditInlineMediaAssetId(value: String): String {
    val normalized = value.trim().lowercase()
    require(normalized.length in 1..64) { "Reddit media asset ID has an invalid length" }
    require(normalized.all(Char::isLetterOrDigit)) {
        "Reddit media asset ID contains unsupported characters"
    }
    return normalized
}
