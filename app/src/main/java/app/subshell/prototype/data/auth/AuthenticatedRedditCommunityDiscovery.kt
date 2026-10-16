package app.subshell.prototype.data.auth

import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.model.COMMUNITY_AUTOCOMPLETE_MAX_QUERY_LENGTH
import app.subshell.prototype.model.COMMUNITY_DISCOVERY_MAX_SEARCH_QUERY_LENGTH
import app.subshell.prototype.model.COMMUNITY_DISCOVERY_MIN_SEARCH_QUERY_LENGTH
import app.subshell.prototype.model.CommunityDiscoveryItem
import app.subshell.prototype.model.CommunityDiscoverySort
import app.subshell.prototype.model.RedditPeopleSearchItem

internal enum class AuthenticatedRedditCommunityDiscoveryFailureKind {
    CLIENT_CONFIGURATION,
    SESSION,
    HTTP,
    TRANSPORT,
    PROTOCOL,
    STORAGE,
}

@ConsistentCopyVisibility
internal data class AuthenticatedRedditCommunityDiscoveryTarget private constructor(
    val searchQuery: String?,
    val searchSort: CommunityDiscoverySort?,
    val autocompleteIncludeOver18: Boolean?,
    // ISSUE211_SEARCH_FOLLOWS_SHOW_NSFW_V1 (#211): include_over_18 for
    // Popular and full search; null keeps the account's own preference.
    val listingIncludeOver18: Boolean? = null,
) {
    init {
        when {
            searchQuery == null -> require(
                searchSort == null && autocompleteIncludeOver18 == null,
            ) {
                "Popular community discovery cannot include search parameters"
            }
            autocompleteIncludeOver18 != null -> require(searchSort == null && listingIncludeOver18 == null) {
                "Community autocomplete cannot include a listing sort"
            }
            else -> require(searchSort != null) {
                "Full community search requires an explicit sort"
            }
        }
        require(
            searchQuery == null ||
                searchQuery.length in
                COMMUNITY_DISCOVERY_MIN_SEARCH_QUERY_LENGTH..maximumQueryLength,
        ) {
            "Community discovery query must contain between " +
                "$COMMUNITY_DISCOVERY_MIN_SEARCH_QUERY_LENGTH and $maximumQueryLength characters"
        }
    }

    val isAutocomplete: Boolean
        get() = autocompleteIncludeOver18 != null

    val pageSize: Int
        get() = if (isAutocomplete) {
            COMMUNITY_AUTOCOMPLETE_PAGE_SIZE
        } else {
            COMMUNITY_DISCOVERY_PAGE_SIZE
        }

    private val maximumQueryLength: Int
        get() = if (isAutocomplete) {
            COMMUNITY_AUTOCOMPLETE_MAX_QUERY_LENGTH
        } else {
            COMMUNITY_DISCOVERY_MAX_SEARCH_QUERY_LENGTH
        }

    val cacheToken: String
        get() = when {
            isAutocomplete -> {
                val audience = if (autocompleteIncludeOver18 == true) "all" else "safe"
                "autocomplete:$audience:${requireNotNull(searchQuery).lowercase()}"
            }
            searchQuery != null ->
                "search:${requireNotNull(searchSort).queryValue}:${searchQuery.lowercase()}" + nsfwCacheSuffix
            else -> "popular$nsfwCacheSuffix"
        }

    // A listing fetched with NSFW is not reused for a reader without it, nor
    // the reverse; the keys without NSFW are unchanged.
    private val nsfwCacheSuffix: String
        get() = if (listingIncludeOver18 == true) "|nsfw" else ""

    companion object {
        val Popular = AuthenticatedRedditCommunityDiscoveryTarget(
            searchQuery = null,
            searchSort = null,
            autocompleteIncludeOver18 = null,
        )

        fun search(
            rawQuery: String,
            sort: CommunityDiscoverySort,
            includeOver18: Boolean? = null,
        ): AuthenticatedRedditCommunityDiscoveryTarget =
            AuthenticatedRedditCommunityDiscoveryTarget(
                searchQuery = normalizeCommunityDiscoverySearchQuery(rawQuery),
                searchSort = sort,
                autocompleteIncludeOver18 = null,
                listingIncludeOver18 = includeOver18,
            )

        fun popular(includeOver18: Boolean?): AuthenticatedRedditCommunityDiscoveryTarget =
            AuthenticatedRedditCommunityDiscoveryTarget(
                searchQuery = null,
                searchSort = null,
                autocompleteIncludeOver18 = null,
                listingIncludeOver18 = includeOver18,
            )

        fun autocomplete(
            rawQuery: String,
            includeOver18: Boolean,
        ): AuthenticatedRedditCommunityDiscoveryTarget =
            AuthenticatedRedditCommunityDiscoveryTarget(
                searchQuery = normalizeCommunityAutocompleteQuery(rawQuery),
                searchSort = null,
                autocompleteIncludeOver18 = includeOver18,
            )
    }
}

internal sealed interface AuthenticatedRedditCommunityDiscoveryResult {
    data class Loaded(
        val items: List<CommunityDiscoveryItem>,
        val fromCache: Boolean,
    ) : AuthenticatedRedditCommunityDiscoveryResult

    data object NotCached : AuthenticatedRedditCommunityDiscoveryResult

    data class Failed(
        val kind: AuthenticatedRedditCommunityDiscoveryFailureKind,
        val retryable: Boolean,
        val details: FailureDetails? = null,
        val httpStatusCode: Int? = null,
    ) : AuthenticatedRedditCommunityDiscoveryResult
}

@ConsistentCopyVisibility
internal data class AuthenticatedRedditPeopleSearchTarget private constructor(
    val searchQuery: String,
) {
    init {
        require(
            searchQuery.length in
                COMMUNITY_DISCOVERY_MIN_SEARCH_QUERY_LENGTH..
                    COMMUNITY_DISCOVERY_MAX_SEARCH_QUERY_LENGTH,
        ) {
            "Reddit people search query must contain between " +
                "$COMMUNITY_DISCOVERY_MIN_SEARCH_QUERY_LENGTH and " +
                "$COMMUNITY_DISCOVERY_MAX_SEARCH_QUERY_LENGTH characters"
        }
    }

    val pageSize: Int
        get() = REDDIT_PEOPLE_SEARCH_PAGE_SIZE

    companion object {
        fun search(rawQuery: String): AuthenticatedRedditPeopleSearchTarget =
            AuthenticatedRedditPeopleSearchTarget(
                searchQuery = normalizeCommunityDiscoverySearchQuery(rawQuery),
            )
    }
}

internal sealed interface AuthenticatedRedditPeopleSearchResult {
    data class Loaded(
        val items: List<RedditPeopleSearchItem>,
    ) : AuthenticatedRedditPeopleSearchResult

    data class Failed(
        val kind: AuthenticatedRedditCommunityDiscoveryFailureKind,
        val retryable: Boolean,
        val details: FailureDetails? = null,
        val httpStatusCode: Int? = null,
    ) : AuthenticatedRedditPeopleSearchResult
}

internal interface AuthenticatedRedditCommunityDiscoveryLoader {
    suspend fun cached(
        accountStableId: String,
        target: AuthenticatedRedditCommunityDiscoveryTarget,
    ): AuthenticatedRedditCommunityDiscoveryResult

    suspend fun synchronize(
        accountStableId: String,
        target: AuthenticatedRedditCommunityDiscoveryTarget,
    ): AuthenticatedRedditCommunityDiscoveryResult

    suspend fun searchPeople(
        accountStableId: String,
        target: AuthenticatedRedditPeopleSearchTarget,
    ): AuthenticatedRedditPeopleSearchResult
}

internal fun communityDiscoveryCacheKey(
    accountStableId: String,
    target: AuthenticatedRedditCommunityDiscoveryTarget,
): String {
    val normalizedAccountId = accountStableId.trim()
    require(normalizedAccountId.isNotEmpty()) {
        "Community discovery account stable ID cannot be blank"
    }
    return "account|$normalizedAccountId|community-discovery|${target.cacheToken}"
}

internal fun normalizeCommunityDiscoverySearchQuery(rawQuery: String): String =
    normalizeCommunityDiscoveryQuery(
        rawQuery = rawQuery,
        maximumLength = COMMUNITY_DISCOVERY_MAX_SEARCH_QUERY_LENGTH,
    )

internal fun normalizeCommunityAutocompleteQuery(rawQuery: String): String =
    normalizeCommunityDiscoveryQuery(
        rawQuery = rawQuery,
        maximumLength = COMMUNITY_AUTOCOMPLETE_MAX_QUERY_LENGTH,
    )

private fun normalizeCommunityDiscoveryQuery(
    rawQuery: String,
    maximumLength: Int,
): String {
    val normalized = rawQuery.trim().replace(WHITESPACE_RUN, " ")
    require(
        normalized.length in COMMUNITY_DISCOVERY_MIN_SEARCH_QUERY_LENGTH..maximumLength,
    ) {
        "Community discovery query must contain between " +
            "$COMMUNITY_DISCOVERY_MIN_SEARCH_QUERY_LENGTH and $maximumLength characters"
    }
    return normalized
}

internal const val COMMUNITY_DISCOVERY_PAGE_SIZE = 50
internal const val COMMUNITY_AUTOCOMPLETE_PAGE_SIZE = 8
internal const val REDDIT_PEOPLE_SEARCH_PAGE_SIZE = 10
private val WHITESPACE_RUN = Regex("\\s+")
