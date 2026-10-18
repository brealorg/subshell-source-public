package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.account.RedditModeratorAction
import app.subshell.prototype.data.account.RedditModeratorActionRequest
import app.subshell.prototype.data.account.normalized
import app.subshell.prototype.data.network.FormPostRequest
import app.subshell.prototype.data.network.NetworkProtocolFailureKind
import app.subshell.prototype.data.network.NetworkResponseMetadata
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.NetworkTransportFailureKind
import app.subshell.prototype.data.network.SubshellHttpClient

internal sealed interface RedditModerationActionNetworkResult {
    data class Updated(
        val metadata: NetworkResponseMetadata,
    ) : RedditModerationActionNetworkResult

    data class HttpFailure(
        val statusCode: Int,
        val retryable: Boolean,
        val metadata: NetworkResponseMetadata,
        val providerReason: String? = null,
    ) : RedditModerationActionNetworkResult

    // CORRECT04_DISTINGUISH_READS_JSON_ERRORS_V1 (OCB-635): distinguish is sent
    // with api_type=json, so Reddit's refusal can arrive as 200 + json.errors.
    data class Rejected(
        val code: String?,
        val message: String?,
        val metadata: NetworkResponseMetadata,
    ) : RedditModerationActionNetworkResult

    data class TransportFailure(
        val kind: NetworkTransportFailureKind,
        val retryable: Boolean,
    ) : RedditModerationActionNetworkResult

    data class ProtocolFailure(
        val kind: NetworkProtocolFailureKind,
        val jsonPath: String? = null,
    ) : RedditModerationActionNetworkResult
}

// MOD01A2_CLASSIC_REDDIT_APPROVE_REMOVE_TRANSPORT_V1
internal class RedditModerationActionNetworkDataSource(
    private val httpClient: SubshellHttpClient,
) {
    suspend fun mutate(
        request: RedditModeratorActionRequest,
    ): RedditModerationActionNetworkResult {
        val normalized = request.normalized()
        if (
            normalized.action == RedditModeratorAction.DISTINGUISH ||
            normalized.action == RedditModeratorAction.UNDISTINGUISH
        ) {
            return distinguish(normalized)
        }
        return when (
            val result = httpClient.postFormStatus(
                redditModerationActionRequest(normalized),
            )
        ) {
            is NetworkResult.Success ->
                RedditModerationActionNetworkResult.Updated(result.metadata)
            is NetworkResult.HttpFailure ->
                RedditModerationActionNetworkResult.HttpFailure(
                    statusCode = result.statusCode,
                    retryable = result.retryable,
                    metadata = result.metadata,
                    providerReason = result.providerReason,
                )
            is NetworkResult.TransportFailure ->
                RedditModerationActionNetworkResult.TransportFailure(
                    kind = result.kind,
                    retryable = result.retryable,
                )
            is NetworkResult.ProtocolFailure ->
                RedditModerationActionNetworkResult.ProtocolFailure(result.kind, result.jsonPath)
        }
    }

    private suspend fun distinguish(
        normalized: RedditModeratorActionRequest,
    ): RedditModerationActionNetworkResult = when (
        val result = httpClient.postFormJson(
            redditModerationActionRequest(normalized),
            RedditApiTypeJsonResponseDto.serializer(),
        )
    ) {
        is NetworkResult.Success -> result.value.apiErrors().firstOrNull()
            ?.let { refusal ->
                RedditModerationActionNetworkResult.Rejected(refusal.code, refusal.message, result.metadata)
            }
            ?: RedditModerationActionNetworkResult.Updated(result.metadata)
        is NetworkResult.HttpFailure ->
            RedditModerationActionNetworkResult.HttpFailure(
                statusCode = result.statusCode,
                retryable = result.retryable,
                metadata = result.metadata,
                providerReason = result.providerReason,
            )
        is NetworkResult.TransportFailure ->
            RedditModerationActionNetworkResult.TransportFailure(
                kind = result.kind,
                retryable = result.retryable,
            )
        is NetworkResult.ProtocolFailure ->
            RedditModerationActionNetworkResult.ProtocolFailure(result.kind, result.jsonPath)
    }
}

internal fun redditModerationActionRequest(
    request: RedditModeratorActionRequest,
): FormPostRequest = when (request.action) {
    RedditModeratorAction.APPROVE -> FormPostRequest(
        pathSegments = listOf("api", "approve"),
        fields = listOf("id" to request.thingFullname),
    )

    RedditModeratorAction.REMOVE -> FormPostRequest(
        pathSegments = listOf("api", "remove"),
        fields = listOf(
            "id" to request.thingFullname,
            "spam" to "false",
        ),
    )

    // MODERATION02A1_EXPLICIT_SPAM_REMOVE_TRANSPORT_V1
    // MODERATION02A2_INDEPENDENT_REPORT_CONTROL_ENDPOINTS_V1
    RedditModeratorAction.IGNORE_REPORTS -> FormPostRequest(
        pathSegments = listOf("api", "ignore_reports"),
        fields = listOf("id" to request.thingFullname),
    )

    RedditModeratorAction.UNIGNORE_REPORTS -> FormPostRequest(
        pathSegments = listOf("api", "unignore_reports"),
        fields = listOf("id" to request.thingFullname),
    )

    // MODERATION02A3_LOCK_DISTINGUISH_TRANSPORT_V1
    RedditModeratorAction.LOCK -> FormPostRequest(
        pathSegments = listOf("api", "lock"),
        fields = listOf("id" to request.thingFullname),
    )

    RedditModeratorAction.UNLOCK -> FormPostRequest(
        pathSegments = listOf("api", "unlock"),
        fields = listOf("id" to request.thingFullname),
    )

    RedditModeratorAction.DISTINGUISH -> FormPostRequest(
        pathSegments = listOf("api", "distinguish"),
        fields = listOf(
            "api_type" to "json",
            "how" to "yes",
            "id" to request.thingFullname,
        ),
    )

    RedditModeratorAction.UNDISTINGUISH -> FormPostRequest(
        pathSegments = listOf("api", "distinguish"),
        fields = listOf(
            "api_type" to "json",
            "how" to "no",
            "id" to request.thingFullname,
        ),
    )

    RedditModeratorAction.SPAM -> FormPostRequest(
        pathSegments = listOf("api", "remove"),
        fields = listOf(
            "id" to request.thingFullname,
            "spam" to "true",
        ),
    )
}
