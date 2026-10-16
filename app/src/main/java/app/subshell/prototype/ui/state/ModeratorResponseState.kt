package app.subshell.prototype.ui.state

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import app.subshell.prototype.data.account.RedditRemovalMessageType
import app.subshell.prototype.data.moderation.ModeratorSavedResponse
import app.subshell.prototype.data.moderation.normalizeModeratorSavedResponses
import java.util.UUID

// MODRESPONSE01A3_SAVED_RESPONSES_STATE_V1
/**
 * The moderator's saved responses: an ordered, bounded list persisted on
 * every change. Templates are explicitly chosen in the remove dialog and
 * remain editable there; this state never sends anything.
 */
internal class ModeratorResponseState(
    initialResponses: List<ModeratorSavedResponse>,
    private val persist: (List<ModeratorSavedResponse>) -> Unit,
    private val currentTimeMillis: () -> Long,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    initialMessageTypesByAccount: Map<String, RedditRemovalMessageType> = emptyMap(),
    private val persistMessageType: (accountStableId: String, type: RedditRemovalMessageType) -> Unit = { _, _ -> },
) {
    val responses = mutableStateListOf<ModeratorSavedResponse>().apply {
        addAll(normalizeModeratorSavedResponses(initialResponses))
    }

    // MODRESPONSE01A5_LAST_USED_MESSAGE_TYPE_V1: device-local, per account,
    // never portable. Defaults to the most conservative type.
    private val messageTypesByAccount = mutableStateMapOf<String, RedditRemovalMessageType>().apply {
        putAll(initialMessageTypesByAccount)
    }

    fun messageTypeFor(accountStableId: String): RedditRemovalMessageType =
        messageTypesByAccount[accountStableId.trim()] ?: RedditRemovalMessageType.PRIVATE

    fun rememberMessageType(accountStableId: String, type: RedditRemovalMessageType) {
        val key = accountStableId.trim().takeIf(String::isNotEmpty) ?: return
        if (messageTypesByAccount[key] == type) return
        messageTypesByAccount[key] = type
        persistMessageType(key, type)
    }

    /** Adds a response, or updates the one with the same title (case-insensitive). Returns it, or null if invalid. */
    fun save(title: String, body: String): ModeratorSavedResponse? {
        val trimmedTitle = title.trim().take(ModeratorSavedResponse.MAX_TITLE_CHARS)
        val trimmedBody = body.trim().take(ModeratorSavedResponse.MAX_BODY_CHARS)
        if (trimmedTitle.isEmpty() || trimmedBody.isEmpty()) return null
        val existing = responses.firstOrNull { it.title.equals(trimmedTitle, ignoreCase = true) }
        val response = ModeratorSavedResponse(
            id = existing?.id ?: newId(),
            title = trimmedTitle,
            body = trimmedBody,
            updatedAtEpochMs = currentTimeMillis(),
        )
        if (existing == null && responses.size >= ModeratorSavedResponse.MAX_RESPONSES) return null
        replaceAll(responses.filterNot { it.id == response.id } + response)
        return response
    }

    fun delete(id: String) {
        if (responses.none { it.id == id }) return
        replaceAll(responses.filterNot { it.id == id })
    }

    /** Live-state projection of a restore whose durable commit already happened. */
    fun applyRestored(value: List<ModeratorSavedResponse>) {
        responses.clear()
        responses.addAll(normalizeModeratorSavedResponses(value))
    }

    private fun replaceAll(value: List<ModeratorSavedResponse>) {
        val normalized = normalizeModeratorSavedResponses(value)
        responses.clear()
        responses.addAll(normalized)
        persist(normalized)
    }
}
