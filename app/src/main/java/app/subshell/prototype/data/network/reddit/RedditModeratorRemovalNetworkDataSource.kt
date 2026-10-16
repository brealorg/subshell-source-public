package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.account.ModeratorRemovalFailureKind
import app.subshell.prototype.data.account.RedditModeratorRemovalRequest
import app.subshell.prototype.data.account.normalized
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.failureDetailsOrNull
import app.subshell.prototype.data.network.FormPostRequest
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient

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
