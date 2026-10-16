package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.auth.AuthAccountSessionCoordinator
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.needsAuthorization
import app.subshell.prototype.data.network.JsonGetRequest
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.ui.ComposerCommunityOption
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal sealed interface AuthenticatedComposerCommunitySearchResult {
    data class Loaded(
        val query: String,
        val options: List<ComposerCommunityOption>,
    ) : AuthenticatedComposerCommunitySearchResult

    data object AuthorizationRequired : AuthenticatedComposerCommunitySearchResult
    data object Unavailable : AuthenticatedComposerCommunitySearchResult
}

internal fun interface AuthenticatedComposerCommunitySearchLoader {
    suspend fun search(
        accountStableId: String,
        query: String,
    ): AuthenticatedComposerCommunitySearchResult
}

internal class ConfiguredRedditComposerCommunitySearchLoader(
    private val accountCoordinator: AuthAccountSessionCoordinator,
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val json: Json = SubshellNetworkJson.providerJson,
) : AuthenticatedComposerCommunitySearchLoader {
    override suspend fun search(
        accountStableId: String,
        query: String,
    ): AuthenticatedComposerCommunitySearchResult {
        val normalizedQuery = query
            .trim()
            .removePrefix("/r/")
            .removePrefix("r/")
            .take(MAX_COMMUNITY_QUERY_LENGTH)
        if (normalizedQuery.length < MIN_COMMUNITY_QUERY_LENGTH) {
            return AuthenticatedComposerCommunitySearchResult.Loaded(
                query = normalizedQuery,
                options = emptyList(),
            )
        }
        val session = when (
            val result = accountCoordinator.ensureAccessTokenForAccount(
                accountStableId = accountStableId,
                requiredScopes = setOf("read"),
            )
        ) {
            is AuthSessionResult.Authenticated -> result.session
            else -> return if (result.needsAuthorization) {
                AuthenticatedComposerCommunitySearchResult.AuthorizationRequired
            } else {
                AuthenticatedComposerCommunitySearchResult.Unavailable
            }
        }
        val response = SubshellHttpClient(
            callFactory = callFactory,
            endpoint = endpoint,
            json = json,
            userAgent = userAgent,
            bearerTokenProvider = accountCoordinator.sessionBoundBearerTokenProvider(session.tokens.accessToken),
        ).getJson(
            request = JsonGetRequest(
                pathSegments = listOf("api", "subreddit_autocomplete_v2"),
                queryParameters = listOf(
                    "query" to normalizedQuery,
                    "include_profiles" to "false",
                    "include_over_18" to "true",
                    "typeahead_active" to "true",
                    "raw_json" to "1",
                ),
            ),
            deserializer = RedditCommunityAutocompleteListing.serializer(),
        )
        val options = when (response) {
            is NetworkResult.Success -> response.value.data.children
                .asSequence()
                .mapNotNull { child -> child.data.toComposerCommunityOption() }
                .distinctBy { option -> option.name.lowercase() }
                .take(MAX_COMMUNITY_RESULTS)
                .toList()
            else -> return AuthenticatedComposerCommunitySearchResult.Unavailable
        }
        return AuthenticatedComposerCommunitySearchResult.Loaded(
            query = normalizedQuery,
            options = options,
        )
    }
}

@Serializable
private data class RedditCommunityAutocompleteListing(
    val data: RedditCommunityAutocompleteListingData =
        RedditCommunityAutocompleteListingData(),
)

@Serializable
private data class RedditCommunityAutocompleteListingData(
    val children: List<RedditCommunityAutocompleteThing> = emptyList(),
)

@Serializable
private data class RedditCommunityAutocompleteThing(
    val data: RedditCommunityAutocompleteData = RedditCommunityAutocompleteData(),
)

@Serializable
private data class RedditCommunityAutocompleteData(
    @SerialName("display_name") val displayName: String? = null,
    val title: String? = null,
    val subscribers: Long? = null,
    @SerialName("community_icon") val communityIcon: String? = null,
    @SerialName("icon_img") val iconImage: String? = null,
    @SerialName("user_is_subscriber") val userIsSubscriber: Boolean? = null,
) {
    fun toComposerCommunityOption(): ComposerCommunityOption? {
        val name = displayName
            ?.trim()
            ?.takeIf { value ->
                value.length in 1..MAX_COMMUNITY_QUERY_LENGTH &&
                    value.all { character ->
                        character.isLetterOrDigit() || character == '_' || character == '-'
                    }
            }
            ?: return null
        return ComposerCommunityOption(
            name = name,
            title = title?.trim().orEmpty(),
            members = subscribers?.takeIf { it >= 0L },
            iconImageUri = communityIcon.normalizedRedditImageUri()
                ?: iconImage.normalizedRedditImageUri(),
            joined = userIsSubscriber == true,
        )
    }
}

private fun String?.normalizedRedditImageUri(): String? = this
    ?.trim()
    ?.replace("&amp;", "&")
    ?.takeIf { value -> value.toHttpUrlOrNull()?.isHttps == true }

private const val MIN_COMMUNITY_QUERY_LENGTH = 2
private const val MAX_COMMUNITY_QUERY_LENGTH = 100
private const val MAX_COMMUNITY_RESULTS = 25
