package io.github.brealorg.subshell.data.network.reddit

import io.github.brealorg.subshell.data.account.normalizePostSaveFullname
import io.github.brealorg.subshell.data.network.FormPostRequest
import io.github.brealorg.subshell.data.network.NetworkProtocolFailureKind
import io.github.brealorg.subshell.data.network.NetworkResponseMetadata
import io.github.brealorg.subshell.data.network.NetworkResult
import io.github.brealorg.subshell.data.network.NetworkTransportFailureKind
import io.github.brealorg.subshell.data.network.SubshellHttpClient

internal sealed interface RedditPostSaveNetworkResult {
    data class Updated(
        val metadata: NetworkResponseMetadata,
    ) : RedditPostSaveNetworkResult

    data class HttpFailure(
        val statusCode: Int,
        val retryable: Boolean,
        val metadata: NetworkResponseMetadata,
        val providerReason: String? = null,
    ) : RedditPostSaveNetworkResult

    data class TransportFailure(
        val kind: NetworkTransportFailureKind,
        val retryable: Boolean,
    ) : RedditPostSaveNetworkResult

    data class ProtocolFailure(
        val kind: NetworkProtocolFailureKind,
        val jsonPath: String? = null,
    ) : RedditPostSaveNetworkResult
}

internal class RedditPostSaveNetworkDataSource(
    private val httpClient: SubshellHttpClient,
) {
    suspend fun setSaved(
        postFullname: String,
        saved: Boolean,
    ): RedditPostSaveNetworkResult = when (
        val result = httpClient.postFormStatus(
            redditPostSaveFormRequest(
                postFullname = postFullname,
                saved = saved,
            ),
        )
    ) {
        is NetworkResult.Success ->
            RedditPostSaveNetworkResult.Updated(result.metadata)

        is NetworkResult.HttpFailure ->
            RedditPostSaveNetworkResult.HttpFailure(
                statusCode = result.statusCode,
                retryable = result.retryable,
                metadata = result.metadata,
                providerReason = result.providerReason,
            )

        is NetworkResult.TransportFailure ->
            RedditPostSaveNetworkResult.TransportFailure(
                kind = result.kind,
                retryable = result.retryable,
            )

        is NetworkResult.ProtocolFailure ->
            RedditPostSaveNetworkResult.ProtocolFailure(result.kind, result.jsonPath)
    }
}

internal fun redditPostSaveFormRequest(
    postFullname: String,
    saved: Boolean,
): FormPostRequest = FormPostRequest(
    pathSegments = listOf("api", if (saved) "save" else "unsave"),
    fields = listOf(
        "id" to normalizePostSaveFullname(postFullname),
    ),
)
