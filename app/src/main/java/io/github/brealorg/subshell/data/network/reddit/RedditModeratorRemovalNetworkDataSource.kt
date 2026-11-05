package io.github.brealorg.subshell.data.network.reddit

import io.github.brealorg.subshell.data.account.ModeratorRemovalFailureKind
import io.github.brealorg.subshell.data.account.RedditModeratorRemovalRequest
import io.github.brealorg.subshell.data.account.normalized
import io.github.brealorg.subshell.data.error.FailureDetails
import io.github.brealorg.subshell.data.error.failureDetailsOrNull
import io.github.brealorg.subshell.data.network.FormPostRequest
import io.github.brealorg.subshell.data.network.NetworkResult
import io.github.brealorg.subshell.data.network.SubshellHttpClient

// MODERATION02A5A7_CANONICAL_REMOVE_MODERATOR_TRANSPORT_V1
internal class RedditModeratorRemovalNetworkDataSource(
    private val httpClient: SubshellHttpClient,
) {
    suspend fun remove(
        request: RedditModeratorRemovalRequest,
    ): RedditModeratorRemovalNetworkResult {
        val normalized = request.normalized()
        return when (
            val result = httpClient.postFormStatus(
                redditModeratorRemovalFormRequest(normalized),
            )
        ) {
            is NetworkResult.Success -> RedditModeratorRemovalNetworkResult.Acknowledged
            is NetworkResult.HttpFailure -> RedditModeratorRemovalNetworkResult.Failed(
                kind = when {
                    result.statusCode == 401 || result.statusCode == 403 ->
                        ModeratorRemovalFailureKind.AUTHORIZATION
                    result.statusCode in 500..599 ->
                        ModeratorRemovalFailureKind.OUTCOME_UNKNOWN
                    else ->
                        ModeratorRemovalFailureKind.HTTP
                },
                httpStatusCode = result.statusCode,
                failureDetails = result.failureDetailsOrNull(),
            )
            is NetworkResult.TransportFailure -> RedditModeratorRemovalNetworkResult.Failed(
                kind = ModeratorRemovalFailureKind.OUTCOME_UNKNOWN,
                failureDetails = result.failureDetailsOrNull(),
            )
            is NetworkResult.ProtocolFailure -> RedditModeratorRemovalNetworkResult.Failed(
                kind = ModeratorRemovalFailureKind.PROTOCOL,
                failureDetails = result.failureDetailsOrNull(),
            )
        }
    }
}

internal fun redditModeratorRemovalFormRequest(
    request: RedditModeratorRemovalRequest,
): FormPostRequest {
    val normalized = request.normalized()
    return FormPostRequest(
        pathSegments = listOf("r", normalized.communityName, "api", "unfriend"),
        fields = listOf(
            "name" to normalized.username,
            "type" to "moderator",
        ),
    )
}

internal sealed interface RedditModeratorRemovalNetworkResult {
    data object Acknowledged : RedditModeratorRemovalNetworkResult
    data class Failed(
        val kind: ModeratorRemovalFailureKind,
        val httpStatusCode: Int? = null,
        val failureDetails: FailureDetails? = null,
    ) : RedditModeratorRemovalNetworkResult
}
