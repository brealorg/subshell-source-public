package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.account.ModeratorInviteFailureKind
import app.subshell.prototype.data.account.RedditModeratorInviteRequest
import app.subshell.prototype.data.account.normalized
import app.subshell.prototype.data.account.wirePermissions
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.failureDetailsOrNull
import app.subshell.prototype.data.error.isRedditWriteOutcomeUnknownStatus
import app.subshell.prototype.data.network.FormPostRequest
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import kotlinx.serialization.json.JsonElement

// MODERATION02A5A5_CANONICAL_MODERATOR_INVITE_TRANSPORT_V1
internal class RedditModeratorInvitationNetworkDataSource(
    private val httpClient: SubshellHttpClient,
) {
    suspend fun invite(
        request: RedditModeratorInviteRequest,
    ): RedditModeratorInvitationNetworkResult {
        val normalized = request.normalized()
        return when (
            val result = httpClient.postFormJson(
                request = redditModeratorInvitationFormRequest(normalized),
                deserializer = RedditModeratorUserMutationResponseDto.serializer(),
            )
        ) {
            is NetworkResult.Success -> {
                val rawErrors = result.value.json.errors
                val failureDetails = moderatorInviteFailureDetails(rawErrors)
                when {
                    rawErrors.isEmpty() -> RedditModeratorInvitationNetworkResult.Acknowledged
                    failureDetails != null ->
                        RedditModeratorInvitationNetworkResult.Failed(
                            kind = ModeratorInviteFailureKind.VALIDATION,
                            failureDetails = failureDetails,
                        )
                    else -> RedditModeratorInvitationNetworkResult.Failed(
                        ModeratorInviteFailureKind.PROTOCOL,
                    )
                }
            }
            is NetworkResult.HttpFailure -> RedditModeratorInvitationNetworkResult.Failed(
                // MODWRITE01_OUTCOME_UNKNOWN_V1: a 5xx/408/421/425 may come after Reddit
                // stored the invitation; the outcome is unknown and Invite stays off.
                kind = when {
                    result.statusCode == 401 || result.statusCode == 403 ->
                        ModeratorInviteFailureKind.AUTHORIZATION
                    isRedditWriteOutcomeUnknownStatus(result.statusCode) ->
                        ModeratorInviteFailureKind.OUTCOME_UNKNOWN
                    else -> ModeratorInviteFailureKind.HTTP
                },
                httpStatusCode = result.statusCode,
                failureDetails = result.failureDetailsOrNull(),
            )
            is NetworkResult.TransportFailure -> RedditModeratorInvitationNetworkResult.Failed(
                kind = ModeratorInviteFailureKind.OUTCOME_UNKNOWN,
                failureDetails = result.failureDetailsOrNull(),
            )
            // A 2xx whose body cannot be read: Reddit may have stored the invitation.
            is NetworkResult.ProtocolFailure -> RedditModeratorInvitationNetworkResult.Failed(
                kind = ModeratorInviteFailureKind.OUTCOME_UNKNOWN,
                failureDetails = result.failureDetailsOrNull(),
            )
        }
    }
}

internal fun redditModeratorInvitationFormRequest(
    request: RedditModeratorInviteRequest,
): FormPostRequest {
    val normalized = request.normalized()
    return FormPostRequest(
        pathSegments = listOf("r", normalized.communityName, "api", "friend"),
        fields = listOf(
            "api_type" to "json",
            "name" to normalized.username,
            "permissions" to normalized.wirePermissions(),
            "type" to "moderator_invite",
        ),
    )
}

internal fun moderatorInviteFailureDetails(
    values: List<JsonElement>,
): FailureDetails? =
    parseRedditContentReportApiErrors(values)
        .firstOrNull()
        ?.let { parsed ->
            val codeAndField = listOfNotNull(
                parsed.code?.trim()?.takeIf(String::isNotEmpty),
                parsed.field
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?.let { field -> "field=$field" },
            ).joinToString(" · ").takeIf(String::isNotEmpty)
            FailureDetails().withProviderDetails(
                code = codeAndField,
                message = parsed.message,
            )
        }
        ?.takeIf { details ->
            details.providerCode != null || details.providerMessage != null
        }

internal sealed interface RedditModeratorInvitationNetworkResult {
    data object Acknowledged : RedditModeratorInvitationNetworkResult
    data class Failed(
        val kind: ModeratorInviteFailureKind,
        val httpStatusCode: Int? = null,
        val failureDetails: FailureDetails? = null,
    ) : RedditModeratorInvitationNetworkResult
}
