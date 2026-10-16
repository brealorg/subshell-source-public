package app.subshell.prototype.data.search

import app.subshell.prototype.ui.SavedSearch
import app.subshell.prototype.ui.SearchArea
import app.subshell.prototype.ui.SearchScope
import app.subshell.prototype.ui.normalizeSavedSearches
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

// ISSUE206_SAVED_SEARCHES_V1: the stored JSON form of one account's saved
// searches (also the shape of one account in the backup module). Lives
// outside ui/ because the UI layer must not use kotlinx.serialization (NET01).

private const val SAVED_SEARCH_QUERY_FIELD = "query"
private const val SAVED_SEARCH_SCOPE_FIELD = "scope"
private const val SAVED_SEARCH_AREA_FIELD = "area"
private const val SAVED_SEARCH_COMMUNITY_FIELD = "community"

internal fun savedSearchesToJson(searches: List<SavedSearch>): JsonArray =
    JsonArray(
        normalizeSavedSearches(searches).map { search ->
            JsonObject(
                buildMap {
                    put(SAVED_SEARCH_QUERY_FIELD, JsonPrimitive(search.query))
                    put(SAVED_SEARCH_SCOPE_FIELD, JsonPrimitive(search.scope.name))
                    put(SAVED_SEARCH_AREA_FIELD, JsonPrimitive(search.area.name))
                    search.community?.let { put(SAVED_SEARCH_COMMUNITY_FIELD, JsonPrimitive(it)) }
                },
            )
        },
    )

/** Entries with an unknown scope or area, or that are malformed, are dropped, never thrown. */
internal fun savedSearchesFromJson(element: JsonElement): List<SavedSearch> {
    val entries = element as? JsonArray ?: return emptyList()
    return normalizeSavedSearches(
        entries.mapNotNull { entry ->
            val fields = entry as? JsonObject ?: return@mapNotNull null
            fun field(name: String): String? =
                runCatching { fields[name]?.jsonPrimitive?.contentOrNull }.getOrNull()
            SavedSearch(
                query = field(SAVED_SEARCH_QUERY_FIELD) ?: return@mapNotNull null,
                scope = SearchScope.entries.firstOrNull { it.name == field(SAVED_SEARCH_SCOPE_FIELD) }
                    ?: return@mapNotNull null,
                area = SearchArea.entries.firstOrNull { it.name == field(SAVED_SEARCH_AREA_FIELD) }
                    ?: return@mapNotNull null,
                community = field(SAVED_SEARCH_COMMUNITY_FIELD),
            )
        },
    )
}

internal fun encodeSavedSearches(searches: List<SavedSearch>): String =
    savedSearchesToJson(searches).toString()

internal fun decodeSavedSearches(encoded: String): List<SavedSearch> {
    if (encoded.isBlank()) return emptyList()
    val root = runCatching { Json.parseToJsonElement(encoded) }.getOrNull() ?: return emptyList()
    return savedSearchesFromJson(root)
}
