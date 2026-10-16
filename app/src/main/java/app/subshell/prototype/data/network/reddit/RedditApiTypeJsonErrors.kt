package app.subshell.prototype.data.network.reddit

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

// FLAIR02_API_TYPE_JSON_ERRORS_V1: a write sent with api_type=json gets its
// refusal from Reddit as HTTP 200 with {"json":{"errors":[[code, message,
// field], ...]}}. Reading only the status turns that refusal into a success.
@Serializable
internal data class RedditApiTypeJsonResponseDto(
    val json: RedditApiTypeJsonBodyDto = RedditApiTypeJsonBodyDto(),
)

@Serializable
internal data class RedditApiTypeJsonBodyDto(
    val errors: List<JsonElement> = emptyList(),
)

internal data class RedditApiTypeJsonError(
    val code: String?,
    val message: String?,
)

/** Reddit's errors in the order it sent them; an entry it could not read keeps null parts. */
internal fun RedditApiTypeJsonResponseDto.apiErrors(): List<RedditApiTypeJsonError> =
    json.errors.map { value ->
        val tuple = value as? JsonArray
        RedditApiTypeJsonError(
            code = tuple?.stringPart(0),
            message = tuple?.stringPart(1),
        )
    }

private fun JsonArray.stringPart(index: Int): String? =
    (getOrNull(index) as? JsonPrimitive)
        ?.takeIf(JsonPrimitive::isString)
        ?.content
        ?.trim()
        ?.takeIf(String::isNotEmpty)
