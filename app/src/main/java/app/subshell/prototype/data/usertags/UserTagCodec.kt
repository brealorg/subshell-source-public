package app.subshell.prototype.data.usertags

import android.content.SharedPreferences
import app.subshell.prototype.ui.UserTag
import app.subshell.prototype.ui.storedColorName
import app.subshell.prototype.ui.userTagColorFromStoredName
import app.subshell.prototype.ui.normalizeUserTags
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

// ISSUE34_USER_TAGS_V1: the stored JSON form of one account's tags. Lives
// outside ui/ because the UI layer must not use kotlinx.serialization (NET01).

private const val USER_TAG_LABEL_FIELD = "label"
private const val USER_TAG_COLOR_FIELD = "color"
private const val USER_TAG_NOTE_FIELD = "note"

internal fun encodeUserTags(tags: Map<String, UserTag>): String =
    JsonObject(
        normalizeUserTags(tags)
            .toSortedMap()
            .mapValues { (_, tag) ->
                JsonObject(
                    mapOf(
                        USER_TAG_LABEL_FIELD to JsonPrimitive(tag.label),
                        USER_TAG_COLOR_FIELD to JsonPrimitive(tag.storedColorName),
                        USER_TAG_NOTE_FIELD to JsonPrimitive(tag.note),
                    ),
                )
            },
    ).toString()

/** Unknown colours show as grey and keep their name; malformed entries or payloads are dropped, never thrown. */
internal fun decodeUserTags(encoded: String): Map<String, UserTag> {
    if (encoded.isBlank()) return emptyMap()
    val root = runCatching { Json.parseToJsonElement(encoded) as? JsonObject }
        .getOrNull()
        ?: return emptyMap()
    val decoded = root.mapNotNull { (username, element) ->
        val fields = element as? JsonObject ?: return@mapNotNull null
        fun field(name: String): String? =
            runCatching { fields[name]?.jsonPrimitive?.contentOrNull }.getOrNull()
        val (color, unknownColorName) = userTagColorFromStoredName(field(USER_TAG_COLOR_FIELD))
        username to UserTag(
            label = field(USER_TAG_LABEL_FIELD).orEmpty(),
            color = color,
            note = field(USER_TAG_NOTE_FIELD).orEmpty(),
            unknownColorName = unknownColorName,
        )
    }.toMap()
    return normalizeUserTags(decoded)
}

// DATALOSS01_UNREADABLE_USER_TAGS_KEPT_V1 (OCB-643): decodeUserTags reads a
// value that is not a JSON object as no tags, and the next tag edit replaced
// it for good. The first write over such a value copies it to its own key
// (outside the "user_tags:" prefix, so backups and restores never read it).
internal fun SharedPreferences.Editor.keepUnreadableUserTags(
    preferences: SharedPreferences,
    key: String,
): SharedPreferences.Editor {
    val stored = preferences.getString(key, null) ?: return this
    if (userTagsValueIsReadable(stored)) return this
    val unreadableKey = unreadableUserTagsKey(key)
    if (!preferences.contains(unreadableKey)) putString(unreadableKey, stored)
    return this
}

internal fun unreadableUserTagsKey(key: String): String = "unreadable:$key"

private fun userTagsValueIsReadable(encoded: String): Boolean =
    encoded.isBlank() ||
        runCatching { Json.parseToJsonElement(encoded) is JsonObject }.getOrDefault(false)
