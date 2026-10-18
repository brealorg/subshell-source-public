package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.account.normalizePostVoteDirection
import app.subshell.prototype.data.account.normalizePostVoteFullname
import app.subshell.prototype.data.network.FormPostRequest
import app.subshell.prototype.data.network.NetworkProtocolFailureKind
import app.subshell.prototype.data.network.NetworkResponseMetadata
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.NetworkTransportFailureKind
import app.subshell.prototype.data.network.SubshellHttpClient

internal sealed interface RedditPostVoteNetworkResult {
    data class Updated(
        val metadata: NetworkResponseMetadata,
    ) : RedditPostVoteNetworkResult

    data class HttpFailure(
        val statusCode: Int,
        val retryable: Boolean,
        val metadata: NetworkResponseMetadata,
        val providerReason: String? = null,
    ) : RedditPostVoteNetworkResult

    data class TransportFailure(
        val kind: NetworkTransportFailureKind,
        val retryable: Boolean,
    ) : RedditPostVoteNetworkResult

    data class ProtocolFailure(
        val kind: NetworkProtocolFailureKind,
        val jsonPath: String? = null,
    ) : RedditPostVoteNetworkResult
}

internal class RedditPostVoteNetworkDataSource(
    private val httpClient: SubshellHttpClient,
) {
    suspend fun vote(
        postFullname: String,
        direction: Int,
    ): RedditPostVoteNetworkResult = when (
        val result = httpClient.postFormStatus(
            redditPostVoteFormRequest(
                postFullname = postFullname,
                direction = direction,
            ),
        )
    ) {
        is NetworkResult.Success ->
            RedditPostVoteNetworkResult.Updated(result.metadata)

        is NetworkResult.HttpFailure ->
            RedditPostVoteNetworkResult.HttpFailure(
                statusCode = result.statusCode,
                retryable = result.retryable,
                metadata = result.metadata,
                providerReason = result.providerReason,
            )

        is NetworkResult.TransportFailure ->
            RedditPostVoteNetworkResult.TransportFailure(
                kind = result.kind,
                retryable = result.retryable,
            )

        is NetworkResult.ProtocolFailure ->
            RedditPostVoteNetworkResult.ProtocolFailure(result.kind, result.jsonPath)
    }
}

internal fun redditPostVoteFormRequest(
    postFullname: String,
    direction: Int,
): FormPostRequest = FormPostRequest(
    pathSegments = listOf("api", "vote"),
    fields = listOf(
        "id" to normalizePostVoteFullname(postFullname),
        "dir" to normalizePostVoteDirection(direction).toString(),
    ),
)
