package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.account.PostManageFlairOption
import app.subshell.prototype.data.account.PostManageMutationRequest
import app.subshell.prototype.data.account.PostManageOperation
import app.subshell.prototype.data.account.PostManageSnapshot
import app.subshell.prototype.data.account.normalizePostEditFullname
import app.subshell.prototype.data.account.normalizePostManageCommunity
import app.subshell.prototype.data.network.FormPostRequest
import app.subshell.prototype.data.network.JsonGetRequest
import app.subshell.prototype.data.network.NetworkResponseMetadata
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.NetworkProtocolFailureKind
import app.subshell.prototype.data.network.NetworkTransportFailureKind
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

internal sealed interface RedditPostManageLoadNetworkResult {
    data class Loaded(
        val snapshot: PostManageSnapshot,
        val metadata: NetworkResponseMetadata,
    ) : RedditPostManageLoadNetworkResult

    data class HttpFailure(
        val statusCode: Int,
        val retryable: Boolean,
        val metadata: NetworkResponseMetadata,
    ) : RedditPostManageLoadNetworkResult

    data class TransportFailure(
        val kind: NetworkTransportFailureKind,
        val retryable: Boolean,
    ) : RedditPostManageLoadNetworkResult

    data class ProtocolFailure(
        val kind: NetworkProtocolFailureKind,
        val jsonPath: String? = null,
    ) : RedditPostManageLoadNetworkResult
}

internal sealed interface RedditPostManageMutationNetworkResult {
    data class Updated(
        val metadata: NetworkResponseMetadata,
    ) : RedditPostManageMutationNetworkResult

    /** Reddit answered 2xx but listed errors in the api_type=json body. */
    data class Rejected(
        val errors: List<RedditApiTypeJsonError>,
        val metadata: NetworkResponseMetadata,
    ) : RedditPostManageMutationNetworkResult

    data class HttpFailure(
        val statusCode: Int,
        val retryable: Boolean,
        val metadata: NetworkResponseMetadata,
    ) : RedditPostManageMutationNetworkResult

    data class TransportFailure(
        val kind: NetworkTransportFailureKind,
        val retryable: Boolean,
    ) : RedditPostManageMutationNetworkResult

    data class ProtocolFailure(
        val kind: NetworkProtocolFailureKind,
        val jsonPath: String? = null,
    ) : RedditPostManageMutationNetworkResult
}

internal class RedditPostManageNetworkDataSource(
    private val httpClient: SubshellHttpClient,
) {
    suspend fun load(
        postFullname: String,
        flairScopeGranted: Boolean,
    ): RedditPostManageLoadNetworkResult {
        val canonicalFullname = normalizePostEditFullname(postFullname)
        val info = httpClient.getJson(
            redditPostManageInfoRequest(canonicalFullname),
            RedditPostManageListing.serializer(),
        )
        val infoSuccess = when (info) {
            is NetworkResult.Success -> info
            is NetworkResult.HttpFailure ->
                return RedditPostManageLoadNetworkResult.HttpFailure(
                    statusCode = info.statusCode,
                    retryable = info.retryable,
                    metadata = info.metadata,
                )
            is NetworkResult.TransportFailure ->
                return RedditPostManageLoadNetworkResult.TransportFailure(
                    kind = info.kind,
                    retryable = info.retryable,
                )
            is NetworkResult.ProtocolFailure ->
                return RedditPostManageLoadNetworkResult.ProtocolFailure(info.kind, info.jsonPath)
        }
        val post = infoSuccess.value.data.children
            .asSequence()
            .map(RedditPostManageThing::data)
            .firstOrNull { data ->
                data.name?.trim()?.lowercase() == canonicalFullname.lowercase()
            }
            ?: return RedditPostManageLoadNetworkResult.ProtocolFailure(NetworkProtocolFailureKind.MALFORMED_JSON)

        val community = runCatching {
            normalizePostManageCommunity(post.subreddit.orEmpty())
        }.getOrElse {
            return RedditPostManageLoadNetworkResult.ProtocolFailure(NetworkProtocolFailureKind.MALFORMED_JSON)
        }
        val author = post.author?.trim()?.takeIf(String::isNotEmpty)
            ?: return RedditPostManageLoadNetworkResult.ProtocolFailure(NetworkProtocolFailureKind.MALFORMED_JSON)

        val flairResponse = if (flairScopeGranted) {
            httpClient.getJson(
                redditPostManageFlairRequest(community),
                ListSerializer(RedditPostManageFlairDto.serializer()),
            )
        } else {
            null
        }
        val flairOptions = (flairResponse as? NetworkResult.Success)
            ?.value
            ?.mapNotNull(RedditPostManageFlairDto::toOption)
            ?.distinctBy(PostManageFlairOption::templateId)
            .orEmpty()

        return RedditPostManageLoadNetworkResult.Loaded(
            snapshot = PostManageSnapshot(
                postFullname = canonicalFullname,
                author = author,
                community = community,
                flairTemplateId = post.linkFlairTemplateId
                    ?.trim()
                    ?.takeIf(String::isNotEmpty),
                flairText = post.linkFlairText?.trim()?.takeIf(String::isNotEmpty),
                nsfw = post.over18,
                spoiler = post.spoiler,
                sendReplies = post.sendReplies,
                flairOptions = flairOptions,
                flairOptionsLoaded = flairResponse is NetworkResult.Success,
                flairAuthorizationRequired = !flairScopeGranted,
            ),
            metadata = infoSuccess.metadata,
        )
    }

    suspend fun mutate(
        request: PostManageMutationRequest,
    ): RedditPostManageMutationNetworkResult {
        val form = requireNotNull(redditPostManageMutationFormRequest(request)) {
            "Authorization-only post management must not reach network mutation"
        }
        val result = if (form.fields.contains("api_type" to "json")) {
            // FLAIR02_API_TYPE_JSON_ERRORS_V1: selectflair reports a refusal in the body.
            httpClient.postFormJson(form, RedditApiTypeJsonResponseDto.serializer())
                .map(RedditApiTypeJsonResponseDto::apiErrors)
        } else {
            httpClient.postFormStatus(form).map { emptyList<RedditApiTypeJsonError>() }
        }
        return when (result) {
            is NetworkResult.Success ->
                if (result.value.isEmpty()) {
                    RedditPostManageMutationNetworkResult.Updated(result.metadata)
                } else {
                    RedditPostManageMutationNetworkResult.Rejected(result.value, result.metadata)
                }
            is NetworkResult.HttpFailure ->
                RedditPostManageMutationNetworkResult.HttpFailure(
                    statusCode = result.statusCode,
                    retryable = result.retryable,
                    metadata = result.metadata,
                )
            is NetworkResult.TransportFailure ->
                RedditPostManageMutationNetworkResult.TransportFailure(
                    kind = result.kind,
                    retryable = result.retryable,
                )
            is NetworkResult.ProtocolFailure ->
                RedditPostManageMutationNetworkResult.ProtocolFailure(result.kind, result.jsonPath)
        }
    }
}

internal fun redditPostManageInfoRequest(postFullname: String): JsonGetRequest =
    JsonGetRequest(
        pathSegments = listOf("api", "info"),
        queryParameters = listOf(
            "id" to normalizePostEditFullname(postFullname),
            "raw_json" to "1",
        ),
    )

internal fun redditPostManageFlairRequest(community: String): JsonGetRequest =
    JsonGetRequest(
        pathSegments = listOf(
            "r",
            normalizePostManageCommunity(community),
            "api",
            "link_flair_v2",
        ),
        queryParameters = listOf("raw_json" to "1"),
    )

internal fun redditPostManageMutationFormRequest(
    request: PostManageMutationRequest,
): FormPostRequest? = when (request.operation) {
    PostManageOperation.FLAIR_ACCESS -> null
    PostManageOperation.SET_FLAIR -> FormPostRequest(
        pathSegments = listOf(
            "r",
            request.target.community,
            "api",
            "selectflair",
        ),
        fields = listOf(
            "api_type" to "json",
            "link" to request.target.postFullname,
            // LOWUX3_POST_FLAIR_CAN_BE_REMOVED_V1 (OCB-233): an empty template id
            // removes the post's flair.
            "flair_template_id" to request.flairTemplateId.orEmpty(),
            "text" to request.flairText.orEmpty(),
        ),
    )
    PostManageOperation.SET_NSFW -> FormPostRequest(
        pathSegments = listOf(
            "api",
            if (request.enabled == true) "marknsfw" else "unmarknsfw",
        ),
        fields = listOf("id" to request.target.postFullname),
    )
    PostManageOperation.SET_SPOILER -> FormPostRequest(
        pathSegments = listOf(
            "api",
            if (request.enabled == true) "spoiler" else "unspoiler",
        ),
        fields = listOf("id" to request.target.postFullname),
    )
    PostManageOperation.SET_SEND_REPLIES -> FormPostRequest(
        pathSegments = listOf("api", "sendreplies"),
        fields = listOf(
            "id" to request.target.postFullname,
            "state" to requireNotNull(request.enabled).toString(),
        ),
    )
}

@Serializable
private data class RedditPostManageListing(
    val data: RedditPostManageListingData = RedditPostManageListingData(),
)

@Serializable
private data class RedditPostManageListingData(
    val children: List<RedditPostManageThing> = emptyList(),
)

@Serializable
private data class RedditPostManageThing(
    val data: RedditPostManageData = RedditPostManageData(),
)

@Serializable
private data class RedditPostManageData(
    val name: String? = null,
    val author: String? = null,
    val subreddit: String? = null,
    @SerialName("link_flair_template_id") val linkFlairTemplateId: String? = null,
    @SerialName("link_flair_text") val linkFlairText: String? = null,
    @SerialName("over_18") val over18: Boolean = false,
    val spoiler: Boolean = false,
    @SerialName("send_replies") val sendReplies: Boolean = true,
)

@Serializable
private data class RedditPostManageFlairDto(
    val id: String? = null,
    val text: String = "",
    @SerialName("text_editable") val textEditable: Boolean = false,
) {
    fun toOption(): PostManageFlairOption? = id
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.let { templateId ->
            // CORRECT01_BAD_FLAIR_TEMPLATE_SKIPPED_V1 (OCB-638): a template that
            // fails the option's checks is left out, not a failed Manage post.
            runCatching {
                PostManageFlairOption(
                    templateId = templateId,
                    text = text,
                    editable = textEditable,
                )
            }.getOrNull()
        }
}
