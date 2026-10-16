package app.subshell.prototype.data.auth.reddit

import kotlinx.serialization.json.Json
import okhttp3.ResponseBody
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// CHATLIVE02_SAFE_MATRIX_JSON_V1: Matrix event content is written by the other
// chat member and is not checked by the homeserver. kotlinx's .jsonPrimitive /
// .jsonObject / .jsonArray throw on the wrong shape, and one such event broke
// the live stream on every reconnect (#156). Read untrusted fields with these.
internal val JsonElement.primitiveOrNull: JsonPrimitive? get() = this as? JsonPrimitive
internal val JsonElement.objectOrNull: JsonObject? get() = this as? JsonObject
internal val JsonElement.arrayOrNull: JsonArray? get() = this as? JsonArray

// CHAT403_ROOM_REFUSAL_KEEPS_TOKEN_V1 (OCB-613): a 403 on a room action with
// Matrix errcode M_FORBIDDEN is the room refusing (the peer left or blocked,
// a power-level rule), not a dead token. Such an answer keeps the chat token
// and fails the action for that room only. A 401, a 403 with a token errcode
// (M_UNKNOWN_TOKEN, M_MISSING_TOKEN) or a 403 without a readable errcode still
// ends the chat session as before. Whether Reddit's homeserver sends
// M_FORBIDDEN for room refusals is not verified on a device.
internal fun redditChatMatrixErrcode(body: String?): String? = runCatching {
    (Json.parseToJsonElement(body.orEmpty()) as? JsonObject)
        ?.get("errcode")
        ?.primitiveOrNull
        ?.takeIf(JsonPrimitive::isString)
        ?.content
}.getOrNull()

internal fun redditChatMatrixRoomRefusal(statusCode: Int, errcode: String?): Boolean =
    statusCode == 403 && errcode == "M_FORBIDDEN"

internal fun redditChatMatrixSessionRefusal(statusCode: Int, errcode: String?): Boolean =
    statusCode == 401 || (statusCode == 403 && !redditChatMatrixRoomRefusal(statusCode, errcode))

// LOW5_MATRIX_DISCOVERY_ASKS_ONLY_FOR_MEMBERS_V1 (OCB-482): room discovery reads
// only membership state: no timeline, presence, receipts, account data or other
// state events of every joined room.
internal const val REDDIT_CHAT_MATRIX_ROOM_DISCOVERY_FILTER =
    """{"presence":{"types":[]},"account_data":{"types":[]},""" +
        """"room":{"timeline":{"limit":0},"state":{"types":["m.room.member"]},""" +
        """"ephemeral":{"types":[]},"account_data":{"types":[]}}}"""

// LOW5_MATRIX_ANSWERS_READ_WITHIN_A_CAP_V1 (OCB-482): a Matrix read answer is read
// only up to this size; a larger one is not loaded into memory and reads as
// unreadable (empty text, which parses as nothing).
internal const val REDDIT_CHAT_MATRIX_MAX_READ_BYTES = 8L * 1024L * 1024L

internal fun ResponseBody.redditChatMatrixTextWithinCap(
    maxBytes: Long = REDDIT_CHAT_MATRIX_MAX_READ_BYTES,
): String {
    if (contentLength() > maxBytes) return ""
    val source = source()
    if (source.request(maxBytes + 1)) return ""
    return source.buffer.readUtf8()
}
