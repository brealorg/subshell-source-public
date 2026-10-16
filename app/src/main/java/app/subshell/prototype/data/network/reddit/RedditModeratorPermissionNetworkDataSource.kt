package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.account.ModeratorPermissionUpdateFailureKind
import app.subshell.prototype.data.account.RedditModeratorPermissionUpdateRequest
import app.subshell.prototype.data.account.normalized
import app.subshell.prototype.data.account.wirePermissions
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.failureDetailsOrNull
import app.subshell.prototype.data.error.isRedditWriteOutcomeUnknownStatus
import app.subshell.prototype.data.network.FormPostRequest
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import kotlinx.serialization.json.JsonElement

// MODERATION02A5A6_CANONICAL_SET_MODERATOR_PERMISSIONS_TRANSPORT_V1
internal class RedditModeratorPermissionNetworkDataSource(
    private val httpClient: SubshellHttpClient,
) {
    suspend fun update(
        request: RedditModeratorPermissionUpdateRequest,
    ): RedditModeratorPermissionNetworkResult {
        val normalized = request.normalized()
        return when (
            val result = httpClient.postFormJson(
                request = redditModeratorPermissionFormRequest(normalized),
                deserializer = RedditModeratorUserMutationResponseDto.serializer(),
            )
        ) {
            is NetworkResult.Success -> {
                val rawErrors = result.value.json.errors
                val failureDetails = moderatorPermissionFailureDetails(rawErrors)
                when {
                    rawErrors.isEmpty() -> RedditModeratorPermissionNetworkResult.Acknowledged
                    failureDetails != null -> RedditModeratorPermissionNetworkResult.Failed(
                        kind = ModeratorPermissionUpdateFailureKind.VALIDATION,
                        failureDetails = failureDetails,
                    )
                    else -> RedditModeratorPermissionNetworkResult.Failed(
                        ModeratorPermissionUpdateFailureKind.PROTOCOL,
                    )
                }
            }
            // WRITEOUT01_MODERATOR_PERMISSIONS_OUTCOME_UNKNOWN_V1 (OCB-551): a
            // 5xx/408/421/425 or an unreadable answer may have applied the change.
            is NetworkResult.HttpFailure -> RedditModeratorPermissionNetworkResult.Failed(
                kind = when {
                    result.statusCode == 401 || result.statusCode == 403 ->
                        ModeratorPermissionUpdateFailureKind.AUTHORIZATION
                    isRedditWriteOutcomeUnknownStatus(result.statusCode) ->
                        ModeratorPermissionUpdateFailureKind.OUTCOME_UNKNOWN
                    else -> ModeratorPermissionUpdateFailureKind.HTTP
                },
                httpStatusCode = result.statusCode,
                failureDetails = result.failureDetailsOrNull(),
            )
            is NetworkResult.TransportFailure -> RedditModeratorPermissionNetworkResult.Failed(
                kind = ModeratorPermissionUpdateFailureKind.OUTCOME_UNKNOWN,
                failureDetails = result.failureDetailsOrNull(),
            )
            is NetworkResult.ProtocolFailure -> RedditModeratorPermissionNetworkResult.Failed(
                kind = ModeratorPermissionUpdateFailureKind.OUTCOME_UNKNOWN,
                failureDetails = result.failureDetailsOrNull(),
            )
        }
    }
}

internal fun redditModeratorPermissionFormRequest(
    request: RedditModeratorPermissionUpdateRequest,
): FormPostRequest {
    val normalized = request.normalized()
    return FormPostRequest(
        pathSegments = listOf("r", normalized.communityName, "api", "setpermissions"),
        fields = listOf(
            "api_type" to "json",
            "name" to normalized.username,
            "permissions" to normalized.wirePermissions(),
            "type" to "moderator",
        ),
    )
}

internal fun moderatorPermissionFailureDetails(
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

internal sealed interface RedditModeratorPermissionNetworkResult {
    data object Acknowledged : RedditModeratorPermissionNetworkResult
    data class Failed(
        val kind: ModeratorPermissionUpdateFailureKind,
        val httpStatusCode: Int? = null,
        val failureDetails: FailureDetails? = null,
    ) : RedditModeratorPermissionNetworkResult
}
