package app.subshell.prototype.backup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * BACKUP09_DELETIONS_TRAVEL_AS_TOMBSTONES_V1
 * Backup ids that were deleted (by the user on any device, or trimmed by the
 * device that made them), with the time. A deleted backup is removed on
 * every device instead of being written back by one that still had a copy.
 * The newest [MAX_BACKUP_TOMBSTONES] are kept.
 */
internal const val MAX_BACKUP_TOMBSTONES = 2_000

internal fun mergeBackupTombstones(vararg sources: Map<String, Long>): Map<String, Long> {
    val merged = hashMapOf<String, Long>()
    sources.forEach { source ->
        source.forEach { (id, deletedAt) -> merged[id] = maxOf(merged[id] ?: 0L, deletedAt) }
    }
    return merged.entries
        .sortedByDescending { it.value }
        .take(MAX_BACKUP_TOMBSTONES)
        .associate { it.key to it.value }
}

internal fun encodeBackupTombstones(tombstones: Map<String, Long>): String =
    buildJsonObject {
        put("schemaVersion", JsonPrimitive(1))
        put(
            "deleted",
            buildJsonArray {
                tombstones.entries.sortedBy { it.key }.forEach { (id, deletedAt) ->
                    add(
                        buildJsonObject {
                            put("id", JsonPrimitive(id))
                            put("deletedAtEpochMillis", JsonPrimitive(deletedAt))
                        },
                    )
                }
            },
        )
    }.toString()

/** Unreadable or malformed content is no tombstones (nothing is deleted because of it). */
/**
 * LOW_BACKUP_UNREADABLE_TOMBSTONES_ARE_NOT_EMPTY_V1 (OCB-456): null when the
 * text is not a tombstone file at all (truncated, damaged, foreign), so a
 * reader can tell "unreadable" from "no deletions".
 */
internal fun decodeBackupTombstonesOrNull(text: String): Map<String, Long>? {
    val root = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return null
    if (root["deleted"] !is JsonArray) return null
    return decodeBackupTombstones(text)
}

internal fun decodeBackupTombstones(text: String): Map<String, Long> =
    runCatching {
        val rows = (Json.parseToJsonElement(text) as JsonObject)["deleted"] as? JsonArray
            ?: return emptyMap()
        rows.mapNotNull { row ->
            val item = row as? JsonObject ?: return@mapNotNull null
            val id = item["id"]?.jsonPrimitive?.content?.takeIf(SNAPSHOT_ID_PATTERN::matches)
                ?: return@mapNotNull null
            val deletedAt = item["deletedAtEpochMillis"]?.jsonPrimitive?.longOrNull
                ?: return@mapNotNull null
            id to deletedAt
        }.toMap()
    }.getOrDefault(emptyMap())
