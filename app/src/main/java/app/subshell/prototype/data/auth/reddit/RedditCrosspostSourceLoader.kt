package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.account.normalizeCrosspostSourceFullname
import app.subshell.prototype.data.auth.AuthAccountSessionCoordinator
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.needsAuthorization
import app.subshell.prototype.data.network.JsonGetRequest
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.data.network.reddit.REDDIT_THREAD_POST_LISTING
import app.subshell.prototype.data.network.reddit.redditPostReadRequest
import app.subshell.prototype.data.network.reddit.redditThreadListing
import app.subshell.prototype.ui.CrosspostSourceEvidence
import app.subshell.prototype.ui.normalizeCrosspostComposerCommunity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call

// CROSSPOST01A1E_FRESH_SOURCE_NON_MUTATING_V1
internal sealed interface CrosspostSourceLoadResult {
    data class Available(val evidence: CrosspostSourceEvidence) : CrosspostSourceLoadResult
    data object AuthorizationRequired : CrosspostSourceLoadResult
    data object Unavailable : CrosspostSourceLoadResult
}

internal fun interface AuthenticatedCrosspostSourceLoader {
    suspend fun load(accountStableId: String, fullname: String): CrosspostSourceLoadResult
}

@Serializable
internal data class CrosspostSourceListingDto(val data: CrosspostSourceListingDataDto? = null)
@Serializable
internal data class CrosspostSourceListingDataDto(val children: List<CrosspostSourceThingDto> = emptyList())
@Serializable
internal data class CrosspostSourceThingDto(val kind: String? = null, val data: CrosspostSourceDataDto? = null)
@Serializable
internal data class CrosspostSourceDataDto(
    val name: String? = null,
    val id: String? = null,
    val subreddit: String? = null,
    val author: String? = null,
    val title: String? = null,
    @SerialName("over_18") val nsfw: Boolean? = null,
    val spoiler: Boolean? = null,
    @SerialName("is_crosspostable") val crosspostable: Boolean? = null,
    @SerialName("removed_by_category") val removedByCategory: String? = null,
)

// ISSUE281_READS_AVOID_API_INFO_V1: the source post comes from its thread.
internal fun redditCrosspostSourceRequest(fullname: String): JsonGetRequest =
    redditPostReadRequest(normalizeCrosspostSourceFullname(fullname))

internal fun CrosspostSourceListingDto.toCrosspostSourceEvidence(fullname: String): CrosspostSourceEvidence? {
    val target = runCatching { normalizeCrosspostSourceFullname(fullname) }.getOrNull() ?: return null
    val thing = data?.children?.singleOrNull() ?: return null
    if (thing.kind != "t3") return null
    val source = thing.data ?: return null
    if (source.name != target || source.id != target.removePrefix("t3_") ||
        source.crosspostable != true || !source.removedByCategory.isNullOrBlank()
    ) return null
    val community = source.subreddit?.let(::normalizeCrosspostComposerCommunity) ?: return null
    val author = source.author?.takeIf {
        it.isNotBlank() && !it.startsWith("[") && it.length <= 64 && '\u0000' !in it
    } ?: return null
    val title = source.title?.takeIf { it.isNotBlank() && it.length <= 300 } ?: return null
    return CrosspostSourceEvidence(
        fullname = target, community = community, author = author, title = title,
        nsfw = source.nsfw ?: return null, spoiler = source.spoiler ?: return null,
    )
}

internal class ConfiguredRedditCrosspostSourceLoader(
    private val accountCoordinator: AuthAccountSessionCoordinator,
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val json: Json = SubshellNetworkJson.providerJson,
) : AuthenticatedCrosspostSourceLoader {
    override suspend fun load(accountStableId: String, fullname: String): CrosspostSourceLoadResult {
        val request = runCatching { redditCrosspostSourceRequest(fullname) }.getOrNull()
            ?: return CrosspostSourceLoadResult.Unavailable
        val session = when (val result = accountCoordinator.ensureAccessTokenForAccount(
            accountStableId = accountStableId, requiredScopes = setOf("read"),
        )) {
            is AuthSessionResult.Authenticated -> result.session
            else -> return if (result.needsAuthorization) {
                CrosspostSourceLoadResult.AuthorizationRequired
            } else {
                CrosspostSourceLoadResult.Unavailable
            }
        }
        val client = SubshellHttpClient(
            callFactory = callFactory, endpoint = endpoint, json = json, userAgent = userAgent,
            bearerTokenProvider = accountCoordinator.sessionBoundBearerTokenProvider(session.tokens.accessToken),
        )
        return when (val result = client.getJson(request, redditThreadListing(CrosspostSourceListingDto.serializer(), REDDIT_THREAD_POST_LISTING))) {
            is NetworkResult.Success -> result.value.toCrosspostSourceEvidence(fullname)
                ?.let(CrosspostSourceLoadResult::Available) ?: CrosspostSourceLoadResult.Unavailable
            else -> CrosspostSourceLoadResult.Unavailable
        }
    }
}
