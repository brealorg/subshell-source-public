package app.subshell.prototype.ui

import androidx.compose.runtime.Immutable
import java.nio.charset.StandardCharsets
import java.util.Base64

internal const val SEARCH_HISTORY_LIMIT = 12
internal const val SEARCH_INDEX_LIMIT = 48
private const val SEARCH_HISTORY_QUERY_MAX_LENGTH = 256
private const val SEARCH_INDEX_KEY_MAX_LENGTH = 128
private const val SEARCH_INDEX_PRIMARY_MAX_LENGTH = 128
private const val SEARCH_INDEX_SECONDARY_MAX_LENGTH = 512
private const val SEARCH_INDEX_IMAGE_URI_MAX_LENGTH = 1_024
private const val SEARCH_INDEX_MAX_SELECTION_COUNT = 100_000
private const val SEARCH_AUTOCOMPLETE_MIN_LENGTH = 2
private const val SEARCH_AUTOCOMPLETE_MAX_LENGTH = 25
private const val SEARCH_INDEX_RECENCY_DAY_MS = 24L * 60L * 60L * 1_000L

// SEARCH02A1_ACCOUNT_SCOPED_INTERACTION_INDEX_V1
enum class SearchIndexKind {
    COMMUNITY,
    USER,
}

@Immutable
data class SearchIndexEntry(
    val kind: SearchIndexKind,
    val key: String,
    val primaryText: String,
    val secondaryText: String = "",
    val imageUri: String? = null,
    val selectionCount: Int = 1,
    val lastSelectedEpochMs: Long,
) {
    init {
        require(key.isNotBlank()) { "Search index key cannot be blank" }
        require(primaryText.isNotBlank()) { "Search index primary text cannot be blank" }
        require(selectionCount in 1..SEARCH_INDEX_MAX_SELECTION_COUNT) {
            "Search index selection count is out of range"
        }
        require(lastSelectedEpochMs >= 0L) {
            "Search index timestamp cannot be negative"
        }
    }
}

// SEARCH02A11_PERSONALIZED_ZERO_QUERY_COMMUNITIES_V1
@Immutable
internal data class ZeroQueryCommunitySuggestion(
    val name: String,
    val description: String = "",
    val imageUri: String? = null,
    val favorite: Boolean = false,
    val joined: Boolean = false,
    val interactionBoost: Int = 0,
)

internal fun selectZeroQueryCommunitySuggestions(
    authenticated: Boolean,
    candidates: Iterable<ZeroQueryCommunitySuggestion>,
    recentCommunityNames: Set<String>,
    limit: Int = 4,
): List<ZeroQueryCommunitySuggestion> {
    if (limit <= 0) return emptyList()
    val recentKeys = recentCommunityNames
        .mapNotNull(::normalizeZeroQueryCommunityKey)
        .toSet()
    val merged = linkedMapOf<String, ZeroQueryCommunitySuggestion>()

    candidates.forEach { candidate ->
        val key = normalizeZeroQueryCommunityKey(candidate.name) ?: return@forEach
        if (key in recentKeys) return@forEach
        val normalized = candidate.copy(
            name = candidate.name.trim()
                .removePrefix("/r/")
                .removePrefix("r/")
                .ifBlank { key },
            description = normalizeSearchQuery(candidate.description)
                .take(SEARCH_INDEX_SECONDARY_MAX_LENGTH),
            imageUri = candidate.imageUri
                ?.trim()
                ?.take(SEARCH_INDEX_IMAGE_URI_MAX_LENGTH)
                ?.takeIf(String::isNotEmpty),
            interactionBoost = candidate.interactionBoost.coerceIn(0, SEARCH_INDEX_MAX_BOOST),
        )
        val existing = merged[key]
        merged[key] = if (existing == null) {
            normalized
        } else {
            existing.copy(
                description = existing.description.ifBlank { normalized.description },
                imageUri = existing.imageUri ?: normalized.imageUri,
                favorite = existing.favorite || normalized.favorite,
                joined = existing.joined || normalized.joined,
                interactionBoost = maxOf(
                    existing.interactionBoost,
                    normalized.interactionBoost,
                ),
            )
        }
    }

    val distinct = merged.values.toList()
    if (!authenticated) return distinct.take(limit)

    return distinct
        .mapNotNull { suggestion ->
            val signalTier = when {
                suggestion.favorite -> 3
                suggestion.interactionBoost > 0 -> 2
                suggestion.joined -> 1
                else -> 0
            }
            if (signalTier == 0) {
                null
            } else {
                RankedZeroQueryCommunitySuggestion(
                    suggestion = suggestion,
                    signalTier = signalTier,
                )
            }
        }
        .sortedWith(
            compareByDescending<RankedZeroQueryCommunitySuggestion> { ranked -> ranked.signalTier }
                .thenByDescending { ranked -> ranked.suggestion.interactionBoost }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { ranked -> ranked.suggestion.name },
        )
        .take(limit)
        .map { ranked -> ranked.suggestion }
}

private fun normalizeZeroQueryCommunityKey(value: String): String? = normalizeSearchQuery(value)
    .lowercase()
    .removePrefix("/r/")
    .removePrefix("r/")
    .trim()
    .take(SEARCH_INDEX_KEY_MAX_LENGTH)
    .takeIf(String::isNotEmpty)

private data class RankedZeroQueryCommunitySuggestion(
    val suggestion: ZeroQueryCommunitySuggestion,
    val signalTier: Int,
)

@Immutable
internal data class SearchSessionSnapshot(
    val draftQuery: String = "",
    val submittedQuery: String = "",
    val submissionRevision: Long = 0L,
) {
    init {
        require(submissionRevision >= 0L) {
            "Search submission revision cannot be negative"
        }
    }

    val hasUnsubmittedChanges: Boolean
        get() = normalizeSearchQuery(draftQuery) != normalizeSearchQuery(submittedQuery)

    fun updateDraft(query: String): SearchSessionSnapshot = copy(draftQuery = query)

    fun submit(): SearchSessionSnapshot? {
        val normalized = normalizeSearchQuery(draftQuery)
        if (normalized.isEmpty()) return null
        return copy(
            draftQuery = normalized,
            submittedQuery = normalized,
            submissionRevision = submissionRevision + 1L,
        )
    }

    fun restore(
        draftQuery: String,
        submittedQuery: String,
    ): SearchSessionSnapshot {
        val nextSubmitted = normalizeSearchQuery(submittedQuery)
        val changedSubmission = nextSubmitted != normalizeSearchQuery(this.submittedQuery)
        return copy(
            draftQuery = draftQuery,
            submittedQuery = nextSubmitted,
            submissionRevision = submissionRevision + if (changedSubmission) 1L else 0L,
        )
    }
}

internal enum class SearchBackAction {
    RESTORE_SUBMITTED_QUERY,
    CLEAR_TO_SEARCH_HUB,
    CLOSE_SEARCH,
}

internal fun resolveSearchBackAction(
    draftQuery: String,
    submittedQuery: String,
): SearchBackAction {
    val draft = normalizeSearchQuery(draftQuery)
    val submitted = normalizeSearchQuery(submittedQuery)
    return when {
        submitted.isNotEmpty() && draft != submitted ->
            SearchBackAction.RESTORE_SUBMITTED_QUERY
        draft.isNotEmpty() || submitted.isNotEmpty() ->
            SearchBackAction.CLEAR_TO_SEARCH_HUB
        else -> SearchBackAction.CLOSE_SEARCH
    }
}

internal enum class SearchProviderKind {
    DIRECT,
    COMMUNITIES,
    POSTS,
    USERS,
    LOCAL_CACHE,
}

@Immutable
internal data class SearchDirectAction(
    val target: RedditTarget,
    val title: String,
    val subtitle: String,
)

@Immutable
internal data class SearchQueryPlan(
    val normalizedQuery: String,
    val providers: List<SearchProviderKind>,
    val directAction: SearchDirectAction?,
    val communityAutocompleteQuery: String?,
)

internal fun planSearchQuery(
    rawQuery: String,
    area: SearchArea,
    authenticated: Boolean,
): SearchQueryPlan {
    val query = normalizeSearchQuery(rawQuery)
    if (query.isEmpty()) {
        return SearchQueryPlan(
            normalizedQuery = "",
            providers = emptyList(),
            directAction = null,
            communityAutocompleteQuery = null,
        )
    }

    val directAction = resolveRedditTarget(query)?.toSearchDirectAction()
    if (directAction != null) {
        return SearchQueryPlan(
            normalizedQuery = query,
            providers = listOf(SearchProviderKind.DIRECT),
            directAction = directAction,
            communityAutocompleteQuery = null,
        )
    }

    val providers = buildList {
        if (area != SearchArea.CURRENT_COMMUNITY) add(SearchProviderKind.COMMUNITIES)
        add(SearchProviderKind.POSTS)
        if (area == SearchArea.ALL_SUBSHELL) add(SearchProviderKind.USERS)
        add(SearchProviderKind.LOCAL_CACHE)
    }
    val autocomplete = query
        .removePrefix("r/")
        .removePrefix("/r/")
        .takeIf { candidate ->
            area != SearchArea.CURRENT_COMMUNITY &&
                candidate.length in SEARCH_AUTOCOMPLETE_MIN_LENGTH..SEARCH_AUTOCOMPLETE_MAX_LENGTH &&
                candidate.all { character -> character.isLetterOrDigit() || character == '_' }
        }
        .takeIf { authenticated }

    return SearchQueryPlan(
        normalizedQuery = query,
        providers = providers,
        directAction = null,
        communityAutocompleteQuery = autocomplete,
    )
}

internal fun searchMatchScore(
    rawQuery: String,
    primaryText: String,
    secondaryText: String = "",
): Int? {
    val query = normalizeSearchQuery(rawQuery).lowercase()
    if (query.isEmpty()) return null
    val primary = normalizeSearchQuery(primaryText).lowercase()
    val secondary = normalizeSearchQuery(secondaryText).lowercase()

    return when {
        primary == query -> 1_000
        primary.startsWith(query) -> 900
        primary.split(SEARCH_TOKEN_SEPARATOR).any { token -> token.startsWith(query) } -> 780
        primary.contains(query) -> 650
        secondary.startsWith(query) -> 520
        secondary.split(SEARCH_TOKEN_SEPARATOR).any { token -> token.startsWith(query) } -> 440
        secondary.contains(query) -> 320
        else -> null
    }
}

internal fun <T> rankSearchMatches(
    rawQuery: String,
    values: Iterable<T>,
    primaryText: (T) -> String,
    secondaryText: (T) -> String = { "" },
    boost: (T) -> Int = { 0 },
): List<T> = values
    .mapNotNull { value ->
        searchMatchScore(
            rawQuery = rawQuery,
            primaryText = primaryText(value),
            secondaryText = secondaryText(value),
        )?.let { score ->
            RankedSearchValue(
                value = value,
                score = score + boost(value).coerceIn(0, SEARCH_INDEX_MAX_BOOST),
            )
        }
    }
    .sortedWith(
        compareByDescending<RankedSearchValue<T>> { ranked -> ranked.score }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { ranked -> primaryText(ranked.value) },
    )
    .map { ranked -> ranked.value }

internal fun <T> rerankSearchProviderResults(
    rawQuery: String,
    values: Iterable<T>,
    primaryText: (T) -> String,
    secondaryText: (T) -> String = { "" },
    boost: (T) -> Int = { 0 },
): List<T> = values
    .mapIndexed { index, value ->
        RankedProviderSearchValue(
            value = value,
            providerPosition = index,
            score = (searchMatchScore(
                rawQuery = rawQuery,
                primaryText = primaryText(value),
                secondaryText = secondaryText(value),
            ) ?: 0) + boost(value).coerceIn(0, SEARCH_INDEX_MAX_BOOST),
        )
    }
    .sortedWith(
        compareByDescending<RankedProviderSearchValue<T>> { ranked -> ranked.score }
            .thenBy { ranked -> ranked.providerPosition },
    )
    .map { ranked -> ranked.value }

internal fun normalizeSearchIndex(entries: Iterable<SearchIndexEntry>): List<SearchIndexEntry> {
    val merged = linkedMapOf<Pair<SearchIndexKind, String>, SearchIndexEntry>()
    entries.forEach { entry ->
        val normalized = normalizeSearchIndexEntry(entry) ?: return@forEach
        val identity = normalized.kind to normalized.key
        val existing = merged[identity]
        merged[identity] = if (existing == null) {
            normalized
        } else {
            val mostRecent = if (
                normalized.lastSelectedEpochMs >= existing.lastSelectedEpochMs
            ) {
                normalized
            } else {
                existing
            }
            mostRecent.copy(
                selectionCount = (existing.selectionCount + normalized.selectionCount)
                    .coerceAtMost(SEARCH_INDEX_MAX_SELECTION_COUNT),
                lastSelectedEpochMs = maxOf(
                    existing.lastSelectedEpochMs,
                    normalized.lastSelectedEpochMs,
                ),
            )
        }
    }
    return merged.values
        .sortedWith(
            compareByDescending<SearchIndexEntry> { entry -> entry.lastSelectedEpochMs }
                .thenByDescending { entry -> entry.selectionCount }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { entry -> entry.primaryText },
        )
        .take(SEARCH_INDEX_LIMIT)
}

internal fun recordSearchIndexSelection(
    entries: Iterable<SearchIndexEntry>,
    kind: SearchIndexKind,
    key: String,
    primaryText: String,
    secondaryText: String = "",
    imageUri: String? = null,
    selectedAtEpochMs: Long,
): List<SearchIndexEntry> {
    require(selectedAtEpochMs >= 0L) { "Search index timestamp cannot be negative" }
    val normalizedKey = normalizeSearchIndexKey(kind, key) ?: return normalizeSearchIndex(entries)
    val current = normalizeSearchIndex(entries)
    val existing = current.firstOrNull { entry ->
        entry.kind == kind && entry.key == normalizedKey
    }
    val candidate = SearchIndexEntry(
        kind = kind,
        key = normalizedKey,
        primaryText = normalizeSearchIndexPrimary(kind, primaryText, normalizedKey),
        secondaryText = normalizeSearchIndexText(
            secondaryText,
            SEARCH_INDEX_SECONDARY_MAX_LENGTH,
        ),
        imageUri = imageUri
            ?.trim()
            ?.take(SEARCH_INDEX_IMAGE_URI_MAX_LENGTH)
            ?.takeIf(String::isNotEmpty),
        selectionCount = ((existing?.selectionCount ?: 0) + 1)
            .coerceAtMost(SEARCH_INDEX_MAX_SELECTION_COUNT),
        lastSelectedEpochMs = selectedAtEpochMs,
    )
    return normalizeSearchIndex(
        listOf(candidate) + current.filterNot { entry ->
            entry.kind == kind && entry.key == normalizedKey
        },
    )
}

internal fun searchIndexBoost(
    entries: Iterable<SearchIndexEntry>,
    kind: SearchIndexKind,
    key: String,
    nowEpochMs: Long,
): Int {
    val normalizedKey = normalizeSearchIndexKey(kind, key) ?: return 0
    val entry = entries.firstOrNull { candidate ->
        candidate.kind == kind && candidate.key == normalizedKey
    } ?: return 0
    val countBoost = entry.selectionCount.coerceAtMost(20) * 2
    val ageMs = (nowEpochMs - entry.lastSelectedEpochMs).coerceAtLeast(0L)
    val recencyBoost = when {
        ageMs < SEARCH_INDEX_RECENCY_DAY_MS -> 40
        ageMs < 7L * SEARCH_INDEX_RECENCY_DAY_MS -> 30
        ageMs < 30L * SEARCH_INDEX_RECENCY_DAY_MS -> 20
        ageMs < 90L * SEARCH_INDEX_RECENCY_DAY_MS -> 10
        else -> 0
    }
    return (countBoost + recencyBoost).coerceAtMost(SEARCH_INDEX_MAX_BOOST)
}

internal fun encodeSearchIndex(entries: Iterable<SearchIndexEntry>): String =
    normalizeSearchIndex(entries).joinToString("\n") { entry ->
        listOf(
            entry.kind.name,
            encodeSearchIndexField(entry.key),
            encodeSearchIndexField(entry.primaryText),
            encodeSearchIndexField(entry.secondaryText),
            encodeSearchIndexField(entry.imageUri.orEmpty()),
            entry.selectionCount.toString(),
            entry.lastSelectedEpochMs.toString(),
        ).joinToString("|")
    }

internal fun decodeSearchIndex(encoded: String): List<SearchIndexEntry> = normalizeSearchIndex(
    encoded.lineSequence().mapNotNull { line ->
        val fields = line.split('|')
        if (fields.size != 7) return@mapNotNull null
        runCatching {
            SearchIndexEntry(
                kind = SearchIndexKind.valueOf(fields[0]),
                key = decodeSearchIndexField(fields[1]),
                primaryText = decodeSearchIndexField(fields[2]),
                secondaryText = decodeSearchIndexField(fields[3]),
                imageUri = decodeSearchIndexField(fields[4]).takeIf(String::isNotEmpty),
                selectionCount = fields[5].toInt(),
                lastSelectedEpochMs = fields[6].toLong(),
            )
        }.getOrNull()
    }.toList(),
)

internal fun searchIndexPreferenceKey(accountStableId: String): String =
    "search_index.v1." + accountStableId.trim().lowercase()

private fun normalizeSearchIndexEntry(entry: SearchIndexEntry): SearchIndexEntry? {
    val normalizedKey = normalizeSearchIndexKey(entry.kind, entry.key) ?: return null
    return entry.copy(
        key = normalizedKey,
        primaryText = normalizeSearchIndexPrimary(
            entry.kind,
            entry.primaryText,
            normalizedKey,
        ),
        secondaryText = normalizeSearchIndexText(
            entry.secondaryText,
            SEARCH_INDEX_SECONDARY_MAX_LENGTH,
        ),
        imageUri = entry.imageUri
            ?.trim()
            ?.take(SEARCH_INDEX_IMAGE_URI_MAX_LENGTH)
            ?.takeIf(String::isNotEmpty),
        selectionCount = entry.selectionCount.coerceIn(1, SEARCH_INDEX_MAX_SELECTION_COUNT),
        lastSelectedEpochMs = entry.lastSelectedEpochMs.coerceAtLeast(0L),
    )
}

private fun normalizeSearchIndexKey(kind: SearchIndexKind, value: String): String? {
    var normalized = normalizeSearchQuery(value).lowercase()
    normalized = when (kind) {
        SearchIndexKind.COMMUNITY -> normalized
            .removePrefix("/r/")
            .removePrefix("r/")
        SearchIndexKind.USER -> normalized
            .removePrefix("/u/")
            .removePrefix("u/")
    }
    return normalized
        .trim()
        .take(SEARCH_INDEX_KEY_MAX_LENGTH)
        .takeIf(String::isNotEmpty)
}

private fun normalizeSearchIndexPrimary(
    kind: SearchIndexKind,
    value: String,
    fallbackKey: String,
): String {
    val withoutPrefix = when (kind) {
        SearchIndexKind.COMMUNITY -> value.trim()
            .removePrefix("/r/")
            .removePrefix("r/")
        SearchIndexKind.USER -> value.trim()
            .removePrefix("/u/")
            .removePrefix("u/")
    }
    return normalizeSearchIndexText(
        withoutPrefix.ifBlank { fallbackKey },
        SEARCH_INDEX_PRIMARY_MAX_LENGTH,
    )
}

private fun normalizeSearchIndexText(value: String, maxLength: Int): String =
    normalizeSearchQuery(value).take(maxLength)

private fun encodeSearchIndexField(value: String): String = Base64.getUrlEncoder()
    .withoutPadding()
    .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

private fun decodeSearchIndexField(value: String): String = String(
    Base64.getUrlDecoder().decode(value),
    StandardCharsets.UTF_8,
)

internal fun normalizeSearchHistory(entries: Iterable<String>): List<String> = entries
    .map(::normalizeSearchHistoryEntry)
    .filter(String::isNotEmpty)
    .distinctBy { query -> query.lowercase() }
    .take(SEARCH_HISTORY_LIMIT)

internal fun encodeSearchHistory(entries: Iterable<String>): String = normalizeSearchHistory(entries)
    .joinToString("\n") { query ->
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(query.toByteArray(StandardCharsets.UTF_8))
    }

internal fun decodeSearchHistory(encoded: String): List<String> = normalizeSearchHistory(
    encoded.lineSequence().mapNotNull { token ->
        token.takeIf(String::isNotBlank)?.let { value ->
            runCatching {
                String(
                    Base64.getUrlDecoder().decode(value),
                    StandardCharsets.UTF_8,
                )
            }.getOrNull()
        }
    }.toList(),
)

internal fun searchHistoryPreferenceKey(accountStableId: String): String =
    "search_history.v1." + accountStableId.trim().lowercase()

internal fun normalizeSearchQuery(value: String): String = value
    .trim()
    .replace(SEARCH_WHITESPACE, " ")

private fun normalizeSearchHistoryEntry(value: String): String = normalizeSearchQuery(value)
    .take(SEARCH_HISTORY_QUERY_MAX_LENGTH)

private fun RedditTarget.toSearchDirectAction(): SearchDirectAction = when (this) {
    is RedditTarget.Community -> SearchDirectAction(
        target = this,
        title = "Open r/$name",
        subtitle = sort?.let { "Community · ${it.label}" } ?: "Community",
    )
    is RedditTarget.Search -> SearchDirectAction(
        target = this,
        title = "Search Reddit for \"$query\"",
        subtitle = community?.let { "In r/$it" } ?: "Search",
    )
    is RedditTarget.CustomFeed -> SearchDirectAction(
        target = this,
        title = "Open custom feed $name",
        subtitle = owner?.let { "By u/$it" } ?: "Your custom feed",
    )
    is RedditTarget.Compose -> SearchDirectAction(
        target = this,
        title = "Message u/$recipient",
        subtitle = "New message",
    )
    is RedditTarget.ShareLink -> SearchDirectAction(
        target = this,
        title = "Open shared Reddit link",
        subtitle = "Reddit share link",
    )
    is RedditTarget.Image -> SearchDirectAction(
        target = this,
        title = if (animated) "Open GIF" else "Open image",
        subtitle = runCatching { java.net.URI(url).host }.getOrNull() ?: "Reddit media",
    )
    is RedditTarget.Feed -> SearchDirectAction(
        target = this,
        title = "Open ${source.label}",
        subtitle = "Feed",
    )
    is RedditTarget.User -> SearchDirectAction(
        target = this,
        title = "Open u/$username",
        subtitle = tab?.let { "User profile · ${it.name.lowercase().replaceFirstChar(Char::uppercase)}" }
            ?: "User profile",
    )
    is RedditTarget.Post -> SearchDirectAction(
        target = this,
        title = "Open Reddit post",
        subtitle = "t3_$postId",
    )
    is RedditTarget.Comment -> SearchDirectAction(
        target = this,
        title = "Open Reddit comment",
        subtitle = "t1_$commentId · t3_$postId",
    )
    is RedditTarget.InlineVideo -> SearchDirectAction(
        target = this,
        title = "Play Reddit video",
        subtitle = "v.redd.it/$mediaId",
    )
}

private const val SEARCH_INDEX_MAX_BOOST = 80

private data class RankedSearchValue<T>(
    val value: T,
    val score: Int,
)

private data class RankedProviderSearchValue<T>(
    val value: T,
    val providerPosition: Int,
    val score: Int,
)

private val SEARCH_WHITESPACE = Regex("\\s+")
// LOWUI_SEARCH_WORDS_ARE_UNICODE_V1 (OCB-091): æ, ø, å, accented and other
// letters are part of a word, not separators.
private val SEARCH_TOKEN_SEPARATOR = Regex("[^\\p{L}\\p{N}_]+")
