package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.account.CommunityMembershipAction
import app.subshell.prototype.data.account.normalizeCommunityMembershipName
import app.subshell.prototype.data.network.FormPostRequest
import app.subshell.prototype.data.network.NetworkProtocolFailureKind
import app.subshell.prototype.data.network.NetworkResponseMetadata
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.NetworkTransportFailureKind
import app.subshell.prototype.data.network.SubshellHttpClient

internal sealed interface RedditCommunityMembershipNetworkResult {
    data class Updated(
        val metadata: NetworkResponseMetadata,
    ) : RedditCommunityMembershipNetworkResult

    data class HttpFailure(
        val statusCode: Int,
        val retryable: Boolean,
        val metadata: NetworkResponseMetadata,
        val providerReason: String? = null,
    ) : RedditCommunityMembershipNetworkResult

    data class TransportFailure(
        val kind: NetworkTransportFailureKind,
        val retryable: Boolean,
    ) : RedditCommunityMembershipNetworkResult

    data class ProtocolFailure(
        val kind: NetworkProtocolFailureKind,
        val jsonPath: String? = null,
    ) : RedditCommunityMembershipNetworkResult
}

internal class RedditCommunityMembershipNetworkDataSource(
    private val httpClient: SubshellHttpClient,
) {
    suspend fun update(
        communityName: String,
        action: CommunityMembershipAction,
    ): RedditCommunityMembershipNetworkResult = when (
        val result = httpClient.postFormStatus(
            redditCommunityMembershipFormRequest(
                communityName = communityName,
                action = action,
            ),
        )
    ) {
        is NetworkResult.Success ->
            RedditCommunityMembershipNetworkResult.Updated(result.metadata)

        is NetworkResult.HttpFailure ->
            RedditCommunityMembershipNetworkResult.HttpFailure(
                statusCode = result.statusCode,
                retryable = result.retryable,
                metadata = result.metadata,
                providerReason = result.providerReason,
            )

        is NetworkResult.TransportFailure ->
            RedditCommunityMembershipNetworkResult.TransportFailure(
                kind = result.kind,
                retryable = result.retryable,
            )

        is NetworkResult.ProtocolFailure ->
            RedditCommunityMembershipNetworkResult.ProtocolFailure(result.kind, result.jsonPath)
    }
}

internal fun redditCommunityMembershipFormRequest(
    communityName: String,
    action: CommunityMembershipAction,
): FormPostRequest = FormPostRequest(
    pathSegments = listOf("api", "subscribe"),
    fields = buildList {
        add(
            "action" to when (action) {
                CommunityMembershipAction.JOIN -> "sub"
                CommunityMembershipAction.LEAVE -> "unsub"
            },
        )
        add("sr_name" to normalizeCommunityMembershipName(communityName))
        if (action == CommunityMembershipAction.JOIN) {
            add("skip_initial_defaults" to "true")
        }
    },
)
