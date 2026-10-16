package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.account.normalizePostFollowFullname
import app.subshell.prototype.data.network.FormPostRequest
import app.subshell.prototype.data.network.NetworkProtocolFailureKind
import app.subshell.prototype.data.network.NetworkResponseMetadata
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.NetworkTransportFailureKind
import app.subshell.prototype.data.network.SubshellHttpClient

// FOLLOWPOST01A1A_CANONICAL_FOLLOW_POST_TRANSPORT_V1
internal sealed interface RedditPostFollowNetworkResult {
    data class Updated(
        val metadata: NetworkResponseMetadata,
    ) : RedditPostFollowNetworkResult

    data class HttpFailure(
        val statusCode: Int,
        val retryable: Boolean,
        val metadata: NetworkResponseMetadata,
    ) : RedditPostFollowNetworkResult

    data class TransportFailure(
        val kind: NetworkTransportFailureKind,
        val retryable: Boolean,
    ) : RedditPostFollowNetworkResult

    data class ProtocolFailure(
        val kind: NetworkProtocolFailureKind,
        val jsonPath: String? = null,
    ) : RedditPostFollowNetworkResult
}

internal class RedditPostFollowNetworkDataSource(
    private val httpClient: SubshellHttpClient,
) {
    suspend fun setFollowed(
        postFullname: String,
        followed: Boolean,
    ): RedditPostFollowNetworkResult = when (
        val result = httpClient.postFormStatus(
            redditPostFollowFormRequest(
                postFullname = postFullname,
                followed = followed,
            ),
        )
    ) {
        is NetworkResult.Success ->
            RedditPostFollowNetworkResult.Updated(result.metadata)

        is NetworkResult.HttpFailure ->
            RedditPostFollowNetworkResult.HttpFailure(
                statusCode = result.statusCode,
                retryable = result.retryable,
                metadata = result.metadata,
            )

        is NetworkResult.TransportFailure ->
            RedditPostFollowNetworkResult.TransportFailure(
                kind = result.kind,
                retryable = result.retryable,
            )

        is NetworkResult.ProtocolFailure ->
            RedditPostFollowNetworkResult.ProtocolFailure(result.kind, result.jsonPath)
    }
}

internal fun redditPostFollowFormRequest(
    postFullname: String,
    followed: Boolean,
): FormPostRequest = FormPostRequest(
    pathSegments = listOf("api", "follow_post"),
    fields = listOf(
        "follow" to followed.toString(),
        "fullname" to normalizePostFollowFullname(postFullname),
    ),
)
