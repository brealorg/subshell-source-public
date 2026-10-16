package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.network.FormPostRequest
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.model.parseRedditMediaPlaceholders
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

// ISSUE8B_RICHTEXT_MEDIA_EDIT_V1
// Reddit refuses a plain-text edit (`text`) whose markdown contains media
// placeholders (INVALID_COMMENT_SUBMISSION, device-verified 2026-09-27). Its
// own clients edit such text as rich text. Recipe (PRAW's recorded exchange
// for editing text with existing inline media, test_edit__existing_inline_media):
//   1. every placeholder becomes a unique letters-and-digits token,
//   2. POST api/convert_rte_body_format (output_mode=rtjson) lets Reddit turn
//      the markdown into RTJSON, so all formatting survives exactly,
//   3. each token becomes its media element — `{"e":"img"|"gif"|"video",
//      "id": <placeholder target>}` — inline in its paragraph (emotes), or as
//      its own block when it is alone in a paragraph (images, GIFs),
//   4. POST api/editusertext with `richtext_json`.
// The element type comes from the item's current media_metadata (Giphy is
// always a GIF). Anything that cannot be mapped refuses the edit before it
// is sent.

private const val MEDIA_TOKEN_PREFIX = "SubshellMediaToken"
private const val RICHTEXT_EDIT_MAX_LENGTH = 100_000
private const val MEDIA_TOKEN_SUFFIX = "End"

internal data class RedditMediaEditMarkdown(
    val markdown: String,
    /** Token → the placeholder target it stands for (`<asset>`, `giphy|…`, `emote|…`). */
    val targets: Map<String, String>,
)

/** Null when [source] has no Reddit media placeholder (plain `text` edit). */
internal fun redditMediaEditMarkdown(source: String): RedditMediaEditMarkdown? {
    val placeholders = parseRedditMediaPlaceholders(source)
    if (placeholders.isEmpty()) return null
    var salt = 0
    while (source.contains("$MEDIA_TOKEN_PREFIX$salt")) salt += 1
    val targets = linkedMapOf<String, String>()
    val markdown = buildString {
        var cursor = 0
        placeholders.forEachIndexed { index, placeholder ->
            append(source, cursor, placeholder.range.first)
            val token = "$MEDIA_TOKEN_PREFIX$salt${'x'}$index$MEDIA_TOKEN_SUFFIX"
            targets[token] = placeholder.target
            append(token)
            cursor = placeholder.range.last + 1
        }
        append(source, cursor, source.length)
    }
    return RedditMediaEditMarkdown(markdown = markdown, targets = targets)
}

/** The RTJSON element type of a placeholder target. */
internal fun redditMediaEditElementKind(
    target: String,
    mediaMetadata: Map<String, RedditMediaMetadataDto>,
): String {
    if (target.startsWith("giphy|", ignoreCase = true)) return "gif"
    val metadata = mediaMetadata.entries
        .firstOrNull { (key, _) -> key.equals(target, ignoreCase = true) }
        ?.value
    return when {
        metadata?.mediaKind == "AnimatedImage" -> "gif"
        metadata?.mimeType.equals("image/gif", ignoreCase = true) -> "gif"
        metadata?.mediaKind == "RedditVideo" -> "video"
        else -> "img"
    }
}

/**
 * [document] (Reddit's conversion of [edit]'s markdown) with every token
 * replaced by its media element, as the `richtext_json` value; null when a
 * token is missing or sits where media cannot go.
 */
internal fun redditMediaEditRichTextJson(
    document: JsonArray,
    edit: RedditMediaEditMarkdown,
    kindOf: (target: String) -> String,
): String? {
    val placed = mutableSetOf<String>()
    fun mediaElement(token: String): JsonObject {
        val target = edit.targets.getValue(token)
        placed += token
        return buildJsonObject {
            put("e", kindOf(target))
            put("id", target)
        }
    }

    val tokenRegex = Regex(edit.targets.keys.joinToString("|") { Regex.escape(it) })

    fun splitText(node: JsonObject): List<JsonElement>? {
        val text = (node["t"] as? JsonPrimitive)?.contentOrNull ?: return null
        if (!tokenRegex.containsMatchIn(text)) return null
        // RTJSONOFFSETS01_FORMAT_RANGES_IN_CODE_POINTS_V1 (OCB-640): Reddit's
        // conversion (a Python backend) counts "f" ranges in code points, while
        // Kotlin indexes UTF-16 units; an emoji before a token shifted every
        // later range by one. Ranges are moved to UTF-16 here, split as before,
        // and each segment's ranges are counted back in code points. Text
        // without characters outside the BMP is unchanged.
        val codePoints = text.codePointCount(0, text.length)
        fun utf16Index(codePointOffset: Int): Int =
            text.offsetByCodePoints(0, codePointOffset.coerceIn(0, codePoints))
        val ranges = (node["f"] as? JsonArray).orEmpty().mapNotNull { range ->
            val parts = range as? JsonArray ?: return@mapNotNull null
            val style = parts.getOrNull(0)?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val start = parts.getOrNull(1)?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val length = parts.getOrNull(2)?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val from = utf16Index(start)
            Triple(style, from, utf16Index(start + length) - from)
        }
        fun textSegment(from: Int, to: Int): JsonObject? {
            if (from >= to) return null
            val segmentRanges = ranges.mapNotNull { (style, start, length) ->
                val a = maxOf(start, from)
                val b = minOf(start + length, to)
                if (a < b) {
                    Triple(style, text.codePointCount(from, a), text.codePointCount(a, b))
                } else {
                    null
                }
            }
            return buildJsonObject {
                put("e", "text")
                put("t", text.substring(from, to))
                if (segmentRanges.isNotEmpty()) {
                    put(
                        "f",
                        buildJsonArray {
                            segmentRanges.forEach { (style, start, length) ->
                                add(buildJsonArray { add(JsonPrimitive(style)); add(JsonPrimitive(start)); add(JsonPrimitive(length)) })
                            }
                        },
                    )
                }
            }
        }
        val out = mutableListOf<JsonElement>()
        var cursor = 0
        tokenRegex.findAll(text).forEach { match ->
            textSegment(cursor, match.range.first)?.let(out::add)
            out += mediaElement(match.value)
            cursor = match.range.last + 1
        }
        textSegment(cursor, text.length)?.let(out::add)
        return out
    }

    fun rewrite(element: JsonElement): JsonElement? {
        val node = element as? JsonObject ?: return element
        val kind = (node["e"] as? JsonPrimitive)?.contentOrNull
        val children = node["c"] as? JsonArray ?: run {
            // A token in any other string (raw code, link text, heading) is
            // a place Reddit cannot hold media.
            if (node.values.any { value -> (value as? JsonPrimitive)?.contentOrNull?.let(tokenRegex::containsMatchIn) == true }) {
                return null
            }
            return node
        }
        val rewritten = buildJsonArray {
            children.forEach { child ->
                val childNode = child as? JsonObject
                val childKind = (childNode?.get("e") as? JsonPrimitive)?.contentOrNull
                val split = if (childNode != null && childKind == "text" && kind == "par") splitText(childNode) else null
                if (split != null) {
                    split.forEach(::add)
                } else {
                    add(rewrite(child) ?: return null)
                }
            }
        }
        return JsonObject(node + ("c" to rewritten))
    }

    val blocks = buildJsonArray {
        document.forEach { block ->
            val rewritten = rewrite(block) as? JsonObject ?: return null
            val content = rewritten["c"] as? JsonArray
            val single = content?.singleOrNull() as? JsonObject
            val singleKind = (single?.get("e") as? JsonPrimitive)?.contentOrNull
            val singleId = (single?.get("id") as? JsonPrimitive)?.contentOrNull
            val lonelyMedia = (rewritten["e"] as? JsonPrimitive)?.contentOrNull == "par" &&
                singleKind in setOf("img", "gif", "video") &&
                singleId?.startsWith("emote|", ignoreCase = true) == false
            add(if (lonelyMedia) single else rewritten)
        }
    }
    if (placed != edit.targets.keys) return null
    return buildJsonObject { put("document", blocks) }.toString()
}

@Serializable
internal data class RedditConvertRteResponseDto(
    val output: RedditConvertRteOutputDto? = null,
)

@Serializable
internal data class RedditConvertRteOutputDto(
    val document: JsonArray? = null,
)

internal fun redditConvertRteBodyFormatRequest(markdown: String): FormPostRequest = FormPostRequest(
    pathSegments = listOf("api", "convert_rte_body_format"),
    fields = listOf(
        "api_type" to "json",
        "raw_json" to "1",
        "output_mode" to "rtjson",
        "markdown_text" to markdown,
    ),
)

internal sealed interface RedditMediaEditRichText {
    /** No media placeholder: a plain `text` edit. */
    data object NotNeeded : RedditMediaEditRichText
    data class Ready(val richtextJson: String) : RedditMediaEditRichText
    /** Nothing was sent to editusertext; the edit can be retried safely. */
    data class Unavailable(val reason: String) : RedditMediaEditRichText
}

/**
 * Prepares the `richtext_json` for an edit of [source] (Reddit's own
 * markdown). [mediaMetadata] reads the item's current media_metadata; null
 * means it could not be read.
 */
internal suspend fun prepareRedditMediaEditRichText(
    httpClient: SubshellHttpClient,
    source: String,
    mediaMetadata: suspend () -> Map<String, RedditMediaMetadataDto>?,
): RedditMediaEditRichText {
    val edit = redditMediaEditMarkdown(source) ?: return RedditMediaEditRichText.NotNeeded
    val metadata = mediaMetadata()
        ?: return RedditMediaEditRichText.Unavailable("MEDIA_METADATA_UNREADABLE")
    val document = when (
        val result = httpClient.postFormJson(
            request = redditConvertRteBodyFormatRequest(edit.markdown),
            deserializer = RedditConvertRteResponseDto.serializer(),
        )
    ) {
        is NetworkResult.Success -> result.value.output?.document
            ?: return RedditMediaEditRichText.Unavailable("CONVERSION_EMPTY")
        is NetworkResult.HttpFailure -> return RedditMediaEditRichText.Unavailable("CONVERSION_HTTP_${result.statusCode}")
        is NetworkResult.TransportFailure -> return RedditMediaEditRichText.Unavailable("CONVERSION_TRANSPORT")
        is NetworkResult.ProtocolFailure -> return RedditMediaEditRichText.Unavailable("CONVERSION_PROTOCOL")
    }
    val richtext = redditMediaEditRichTextJson(document, edit) { target ->
        redditMediaEditElementKind(target, metadata)
    } ?: return RedditMediaEditRichText.Unavailable("MEDIA_NOT_PLACEABLE")
    if (richtext.length > RICHTEXT_EDIT_MAX_LENGTH) {
        return RedditMediaEditRichText.Unavailable("RICHTEXT_TOO_LONG")
    }
    return RedditMediaEditRichText.Ready(richtext)
}

internal fun redditRichTextEditFormRequest(
    thingFullname: String,
    richtextJson: String,
): FormPostRequest = FormPostRequest(
    pathSegments = listOf("api", "editusertext"),
    fields = listOf(
        "api_type" to "json",
        "raw_json" to "1",
        "thing_id" to thingFullname,
        "richtext_json" to richtextJson,
    ),
)
