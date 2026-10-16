package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.normalizeRedditUsername
import app.subshell.prototype.data.network.JsonGetRequest
import app.subshell.prototype.data.network.NetworkPage
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.map
import app.subshell.prototype.model.RedditPeopleSearchItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal interface RedditPeopleSearchPageSource {
    suspend fun page(
        searchQuery: String,
        cursor: String?,
        limit: Int,
    ): NetworkResult<NetworkPage<RedditPeopleSearchItem>>
}

internal class RedditPeopleSearchNetworkDataSource(
    private val httpClient: SubshellHttpClient,
) : RedditPeopleSearchPageSource {
    override suspend fun page(
        searchQuery: String,
        cursor: String?,
        limit: Int,
    ): NetworkResult<NetworkPage<RedditPeopleSearchItem>> = httpClient.getJson(
        request = redditPeopleSearchJsonRequest(
            searchQuery = searchQuery,
            cursor = cursor,
            limit = limit,
        ),
        deserializer = RedditPeopleSearchListingDto.serializer(),
    ).map(RedditPeopleSearchListingDto::toNetworkPage)
}

@Serializable
internal data class RedditPeopleSearchListingDto(
    val data: RedditPeopleSearchListingDataDto? = null,
)

@Serializable
internal data class RedditPeopleSearchListingDataDto(
    val after: String? = null,
    val children: List<RedditPeopleSearchThingDto?> = emptyList(),
)

@Serializable
internal data class RedditPeopleSearchThingDto(
    val kind: String? = null,
    val data: RedditPeopleSearchDataDto? = null,
)

@Serializable
internal data class RedditPeopleSearchDataDto(
    val id: String? = null,
    val name: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("display_name_prefixed") val displayNamePrefixed: String? = null,
    val title: String? = null,
    @SerialName("public_description") val publicDescription: String? = null,
    val url: String? = null,
    @SerialName("subreddit_type") val subredditType: String? = null,
    @SerialName("community_icon") val communityIcon: String? = null,
    @SerialName("icon_img") val iconImage: String? = null,
    val over18: Boolean? = null,
    @SerialName("over_18") val legacyOver18: Boolean? = null,
    val subreddit: RedditPeopleSearchProfileSubredditDto? = null,
)

@Serializable
internal data class RedditPeopleSearchProfileSubredditDto(
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("display_name_prefixed") val displayNamePrefixed: String? = null,
    val title: String? = null,
    @SerialName("public_description") val publicDescription: String? = null,
    val url: String? = null,
    @SerialName("subreddit_type") val subredditType: String? = null,
    @SerialName("community_icon") val communityIcon: String? = null,
    @SerialName("icon_img") val iconImage: String? = null,
    val over18: Boolean? = null,
    @SerialName("over_18") val legacyOver18: Boolean? = null,
)

internal fun RedditPeopleSearchListingDto.toNetworkPage():
    NetworkPage<RedditPeopleSearchItem> = NetworkPage(
    items = data?.children.orEmpty().mapNotNull { thing ->
        val value = thing ?: return@mapNotNull null
        when {
            value.kind.equals(REDDIT_ACCOUNT_KIND, ignoreCase = true) ->
                value.data?.toAccountPeopleSearchItem()
            value.kind == null ||
                value.kind.equals(REDDIT_USER_PROFILE_SUBREDDIT_KIND, ignoreCase = true) ->
                value.data?.toLegacyProfileSubredditPeopleSearchItem()
            else -> null
        }
    },
    nextCursor = data?.after?.trim()?.takeIf(String::isNotEmpty),
)

internal fun redditPeopleSearchJsonRequest(
    searchQuery: String,
    cursor: String?,
    limit: Int,
): JsonGetRequest {
    val normalizedQuery = normalizePeopleSearchQuery(searchQuery)
    require(limit in 1..REDDIT_PEOPLE_SEARCH_MAX_PAGE_SIZE) {
        "Reddit people search limit must be between 1 and " +
            "$REDDIT_PEOPLE_SEARCH_MAX_PAGE_SIZE"
    }
    val useProfileAutocomplete =
        normalizedQuery.length <= REDDIT_PEOPLE_AUTOCOMPLETE_MAX_QUERY_LENGTH
    if (useProfileAutocomplete) {
        require(cursor.isNullOrBlank()) {
            "Reddit People profile autocomplete does not support listing cursors"
        }
    }

    return JsonGetRequest(
        pathSegments = if (useProfileAutocomplete) {
            listOf("api", "subreddit_autocomplete_v2")
        } else {
            listOf("users", "search")
        },
        queryParameters = buildList {
            add("raw_json" to "1")
            add("limit" to limit.toString())
            if (useProfileAutocomplete) {
                add("query" to normalizedQuery)
                add("include_over_18" to "true")
                add("include_profiles" to "true")
                add("typeahead_active" to "true")
            } else {
                add("q" to normalizedQuery)
                add("sort" to "relevance")
                add("sr_detail" to "true")
                add("typeahead_active" to "false")
                cursor
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?.let { after -> add("after" to after) }
            }
        },
    )
}

private fun RedditPeopleSearchDataDto.toAccountPeopleSearchItem(): RedditPeopleSearchItem? {
    val profile = subreddit
    val username = name
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.validatedPeopleUsername()
        ?: profile?.resolvedProfileUsername()
        ?: return null

    return RedditPeopleSearchItem(
        username = username,
        title = profile?.title?.trim().orEmpty(),
        description = profile?.publicDescription?.trim().orEmpty(),
        avatarImageUri = sequenceOf(profile?.communityIcon, profile?.iconImage)
            .mapNotNull { candidate -> candidate.securePeopleImageUrlOrNull() }
            .firstOrNull(),
        nsfw = profile?.over18 == true || profile?.legacyOver18 == true,
    )
}

private fun RedditPeopleSearchDataDto.toLegacyProfileSubredditPeopleSearchItem():
    RedditPeopleSearchItem? {
    if (!isUserProfileThing()) return null
    val username = sequenceOf(
        displayNamePrefixed?.usernameFromPrefixedDisplayName(),
        url?.usernameFromUserProfilePath(),
        displayName?.usernameFromDisplayName(
            allowPlain = subredditType.equals(REDDIT_USER_PROFILE_TYPE, ignoreCase = true),
        ),
    ).filterNotNull().firstOrNull()?.validatedPeopleUsername() ?: return null

    return RedditPeopleSearchItem(
        username = username,
        title = title?.trim().orEmpty(),
        description = publicDescription?.trim().orEmpty(),
        avatarImageUri = sequenceOf(communityIcon, iconImage)
            .mapNotNull { candidate -> candidate.securePeopleImageUrlOrNull() }
            .firstOrNull(),
        nsfw = over18 == true || legacyOver18 == true,
    )
}

private fun RedditPeopleSearchProfileSubredditDto.resolvedProfileUsername(): String? =
    sequenceOf(
        displayNamePrefixed?.usernameFromPrefixedDisplayName(),
        url?.usernameFromUserProfilePath(),
        displayName?.usernameFromDisplayName(
            allowPlain = subredditType.equals(REDDIT_USER_PROFILE_TYPE, ignoreCase = true),
        ),
    ).filterNotNull().firstOrNull()?.validatedPeopleUsername()

private fun String.validatedPeopleUsername(): String? {
    val candidate = trim().takeIf(String::isNotEmpty) ?: return null
    return candidate.takeIf {
        runCatching { normalizeRedditUsername(candidate) }.isSuccess
    }
}

private fun RedditPeopleSearchDataDto.isUserProfileThing(): Boolean =
    subredditType.equals(REDDIT_USER_PROFILE_TYPE, ignoreCase = true) ||
        displayNamePrefixed
            ?.trim()
            ?.startsWith("u/", ignoreCase = true) == true ||
        url
            ?.trim()
            ?.let { value ->
                value.startsWith("/user/", ignoreCase = true) ||
                    value.startsWith("/u/", ignoreCase = true)
            } == true ||
        displayName
            ?.trim()
            ?.startsWith("u_", ignoreCase = true) == true

private fun String.usernameFromPrefixedDisplayName(): String? {
    val normalized = trim()
    val prefixLength = when {
        normalized.startsWith("u/", ignoreCase = true) -> 2
        normalized.startsWith("u_", ignoreCase = true) -> 2
        else -> return null
    }
    return normalized
        .drop(prefixLength)
        .trim()
        .takeIf(String::isNotEmpty)
}

private fun String.usernameFromUserProfilePath(): String? {
    val normalized = trim().substringBefore('?').substringBefore('#')
    val path = normalized.toHttpUrlOrNull()?.encodedPath ?: normalized
    val prefix = when {
        path.startsWith("/user/", ignoreCase = true) -> "/user/"
        path.startsWith("/u/", ignoreCase = true) -> "/u/"
        else -> return null
    }
    return path
        .drop(prefix.length)
        .substringBefore('/')
        .trim()
        .takeIf(String::isNotEmpty)
}

private fun String.usernameFromDisplayName(allowPlain: Boolean): String? {
    val normalized = trim()
    if (normalized.isEmpty()) return null
    return when {
        normalized.startsWith("u_", ignoreCase = true) ->
            normalized.drop(2).trim().takeIf(String::isNotEmpty)
        allowPlain -> normalized
        else -> null
    }
}

private fun String?.securePeopleImageUrlOrNull(): String? {
    val candidate = this
        ?.replace("&amp;", "&")
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: return null
    val parsed = candidate.toHttpUrlOrNull() ?: return null
    return candidate.takeIf {
        parsed.isHttps &&
            parsed.username.isEmpty() &&
            parsed.password.isEmpty()
    }
}


internal fun normalizePeopleSearchQuery(rawQuery: String): String {
    val normalized = rawQuery.trim().replace(PEOPLE_SEARCH_WHITESPACE_RUN, " ")
    require(
        normalized.length in
            REDDIT_PEOPLE_SEARCH_MIN_QUERY_LENGTH..REDDIT_PEOPLE_SEARCH_MAX_QUERY_LENGTH,
    ) {
        "Reddit people search query must contain between " +
            "$REDDIT_PEOPLE_SEARCH_MIN_QUERY_LENGTH and " +
            "$REDDIT_PEOPLE_SEARCH_MAX_QUERY_LENGTH characters"
    }
    return normalized
}

internal const val REDDIT_PEOPLE_SEARCH_PAGE_SIZE = 10
internal const val REDDIT_PEOPLE_SEARCH_MAX_PAGE_SIZE = 10
internal const val REDDIT_PEOPLE_AUTOCOMPLETE_MAX_QUERY_LENGTH = 25
internal const val REDDIT_PEOPLE_SEARCH_MIN_QUERY_LENGTH = 2
internal const val REDDIT_PEOPLE_SEARCH_MAX_QUERY_LENGTH = 100
private const val REDDIT_ACCOUNT_KIND = "t2"
private const val REDDIT_USER_PROFILE_SUBREDDIT_KIND = "t5"
private const val REDDIT_USER_PROFILE_TYPE = "user"
private val PEOPLE_SEARCH_WHITESPACE_RUN = Regex("\\s+")
