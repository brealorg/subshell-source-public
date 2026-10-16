package app.subshell.prototype.data.moderation

import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// MODRESPONSE01A3_SAVED_RESPONSES_MODEL_V1
/**
 * A reusable moderator response template. Device-local and shared by every
 * account on the device (templates are text, not account state). Not part of
 * the portable backup yet; that is its own schema slice.
 */
data class ModeratorSavedResponse(
    val id: String,
    val title: String,
    val body: String,
    val updatedAtEpochMs: Long,
) {
    init {
        require(id.isNotBlank()) { "A saved response needs an id" }
        require(title.isNotBlank()) { "A saved response needs a title" }
        require(title.length <= MAX_TITLE_CHARS) { "Saved response title is too long" }
        require(body.isNotBlank()) { "A saved response needs a body" }
        require(body.length <= MAX_BODY_CHARS) { "Saved response body is too long" }
    }

    companion object {
        const val MAX_TITLE_CHARS = 60
        const val MAX_BODY_CHARS = 10_000
        const val MAX_RESPONSES = 50
    }
}

internal fun normalizeModeratorSavedResponses(
    responses: List<ModeratorSavedResponse>,
): List<ModeratorSavedResponse> = responses
    .distinctBy(ModeratorSavedResponse::id)
    .sortedWith(compareBy<ModeratorSavedResponse> { it.title.lowercase() }.thenByDescending { it.updatedAtEpochMs })
    .take(ModeratorSavedResponse.MAX_RESPONSES)

class ModeratorSavedResponseStore(
    private val preferences: SharedPreferences,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(): List<ModeratorSavedResponse> {
        val encoded = preferences.getString(KEY, null) ?: return emptyList()
        val rows = runCatching { json.decodeFromString<List<StoredModeratorSavedResponse>>(encoded) }
            .getOrNull() ?: return emptyList()
        return normalizeModeratorSavedResponses(
            rows.mapNotNull { row -> runCatching { row.toResponse() }.getOrNull() },
        )
    }

    fun save(responses: List<ModeratorSavedResponse>) {
        val editor = preferences.edit()
        stageRestore(editor, responses)
        editor.apply()
    }

    // BACKUP04A4_SAVED_RESPONSES_SHARED_EDITOR_STAGE_V1: part of the one
    // transactional restore commit; an empty list clears the key.
    fun stageRestore(editor: SharedPreferences.Editor, responses: List<ModeratorSavedResponse>) {
        // DATALOSS01_UNREADABLE_SAVED_RESPONSES_KEPT_V1 (OCB-629): load() reads
        // an unreadable value or row as absent, so the next save replaced it
        // for good. The first write over such a value copies it aside.
        preferences.getString(KEY, null)
            ?.takeUnless(::isFullyReadable)
            ?.takeUnless { preferences.contains(UNREADABLE_KEY) }
            ?.let { unreadable -> editor.putString(UNREADABLE_KEY, unreadable) }
        val normalized = normalizeModeratorSavedResponses(responses)
        if (normalized.isEmpty()) {
            editor.remove(KEY)
        } else {
            editor.putString(KEY, json.encodeToString(normalized.map(StoredModeratorSavedResponse::fromResponse)))
        }
    }

    private fun isFullyReadable(encoded: String): Boolean {
        val rows = runCatching { json.decodeFromString<List<StoredModeratorSavedResponse>>(encoded) }
            .getOrNull() ?: return false
        return rows.all { row -> runCatching { row.toResponse() }.isSuccess }
    }

    companion object {
        const val KEY = "moderator_saved_responses"
        const val UNREADABLE_KEY = "moderator_saved_responses.unreadable"
    }
}

@Serializable
private data class StoredModeratorSavedResponse(
    val id: String,
    val title: String,
    val body: String,
    val updatedAtEpochMs: Long,
) {
    fun toResponse() = ModeratorSavedResponse(id = id, title = title, body = body, updatedAtEpochMs = updatedAtEpochMs)

    companion object {
        fun fromResponse(response: ModeratorSavedResponse) = StoredModeratorSavedResponse(
            id = response.id,
            title = response.title,
            body = response.body,
            updatedAtEpochMs = response.updatedAtEpochMs,
        )
    }
}
