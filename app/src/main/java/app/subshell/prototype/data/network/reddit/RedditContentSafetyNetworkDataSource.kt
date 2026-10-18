package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.account.RedditContentReportRequest
import app.subshell.prototype.data.account.RedditPostVisibilityRequest
import app.subshell.prototype.data.network.NetworkProtocolFailureKind
import app.subshell.prototype.data.network.NetworkResponseMetadata
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.NetworkTransportFailureKind
import app.subshell.prototype.data.network.SubshellHttpClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import app.subshell.prototype.data.account.RedditUserBlockRequest

internal data class RedditContentReportApiError(
    val code: String? = null,
    val message: String? = null,
    val field: String? = null,
)

@Serializable
internal data class RedditContentReportResponseDto(
    val json: RedditContentReportJsonDto = RedditContentReportJsonDto(),
)

@Serializable
internal data class RedditUserBlockResponseDto(
    val json: RedditContentReportJsonDto = RedditContentReportJsonDto(),
)

@Serializable
internal data class RedditContentReportJsonDto(
    val errors: List<JsonElement> = emptyList(),
)

internal sealed interface RedditContentSafetyNetworkResult {
    data class Acknowledged(
        val metadata: NetworkResponseMetadata,
    ) : RedditContentSafetyNetworkResult

    data class ApiFailure(
        val errors: List<RedditContentReportApiError>,
        val metadata: NetworkResponseMetadata,
    ) : RedditContentSafetyNetworkResult

    data class HttpFailure(
        val statusCode: Int,
        val retryable: Boolean,
        val metadata: NetworkResponseMetadata,
        val providerReason: String? = null,
    ) : RedditContentSafetyNetworkResult

    data class TransportFailure(
        val kind: NetworkTransportFailureKind,
        val retryable: Boolean,
    ) : RedditContentSafetyNetworkResult

    data class ProtocolFailure(
        val kind: NetworkProtocolFailureKind,
        val jsonPath: String? = null,
    ) : RedditContentSafetyNetworkResult

    data object MalformedPayload : RedditContentSafetyNetworkResult
}

// SAFETY01A2_REDDIT_HIDE_UNHIDE_REPORT_TRANSPORT_V1
internal class RedditContentSafetyNetworkDataSource(
    private val httpClient: SubshellHttpClient,
) {
    suspend fun setPostHidden(
        request: RedditPostVisibilityRequest,
    ): RedditContentSafetyNetworkResult = when (
        val result = httpClient.postFormStatus(
            redditPostVisibilityFormRequest(request),
        )
    ) {
        is NetworkResult.Success ->
            RedditContentSafetyNetworkResult.Acknowledged(result.metadata)

        is NetworkResult.HttpFailure ->
            RedditContentSafetyNetworkResult.HttpFailure(
                statusCode = result.statusCode,
                retryable = result.retryable,
                metadata = result.metadata,
                providerReason = result.providerReason,
            )

        is NetworkResult.TransportFailure ->
            RedditContentSafetyNetworkResult.TransportFailure(
                kind = result.kind,
                retryable = result.retryable,
            )

        is NetworkResult.ProtocolFailure ->
            RedditContentSafetyNetworkResult.ProtocolFailure(result.kind, result.jsonPath)
    }

    // USERACTION01A1_BLOCK_UNBLOCK_TRANSPORT_V1
    suspend fun setUserBlocked(
        request: RedditUserBlockRequest,
        currentUserFullname: String,
    ): RedditContentSafetyNetworkResult {
        val form = redditUserBlockFormRequest(
            request = request,
            currentUserFullname = currentUserFullname,
        )
        return if (request.blocked) {
            when (
                val result = httpClient.postFormJson(
                    request = form,
                    deserializer = RedditUserBlockResponseDto.serializer(),
                )
            ) {
                is NetworkResult.Success -> {
                    val rawErrors = result.value.json.errors
                    if (rawErrors.isEmpty()) {
                        RedditContentSafetyNetworkResult.Acknowledged(result.metadata)
                    } else {
                        val errors = parseRedditContentReportApiErrors(rawErrors)
                        if (errors.isEmpty()) {
                            RedditContentSafetyNetworkResult.MalformedPayload
                        } else {
                            RedditContentSafetyNetworkResult.ApiFailure(
                                errors = errors,
                                metadata = result.metadata,
                            )
                        }
                    }
                }

                is NetworkResult.HttpFailure ->
                    RedditContentSafetyNetworkResult.HttpFailure(
                        statusCode = result.statusCode,
                        retryable = result.retryable,
                        metadata = result.metadata,
                        providerReason = result.providerReason,
                    )

                is NetworkResult.TransportFailure ->
                    RedditContentSafetyNetworkResult.TransportFailure(
                        kind = result.kind,
                        retryable = result.retryable,
                    )

                is NetworkResult.ProtocolFailure ->
                    RedditContentSafetyNetworkResult.ProtocolFailure(result.kind, result.jsonPath)
            }
        } else {
            when (val result = httpClient.postFormStatus(form)) {
                is NetworkResult.Success ->
                    RedditContentSafetyNetworkResult.Acknowledged(result.metadata)

                is NetworkResult.HttpFailure ->
                    RedditContentSafetyNetworkResult.HttpFailure(
                        statusCode = result.statusCode,
                        retryable = result.retryable,
                        metadata = result.metadata,
                        providerReason = result.providerReason,
                    )

                is NetworkResult.TransportFailure ->
                    RedditContentSafetyNetworkResult.TransportFailure(
                        kind = result.kind,
                        retryable = result.retryable,
                    )

                is NetworkResult.ProtocolFailure ->
                    RedditContentSafetyNetworkResult.ProtocolFailure(result.kind, result.jsonPath)
            }
        }
    }

    suspend fun report(
        request: RedditContentReportRequest,
    ): RedditContentSafetyNetworkResult = when (
        val result = httpClient.postFormJson(
            request = redditContentReportFormRequest(request),
            deserializer = RedditContentReportResponseDto.serializer(),
        )
    ) {
        is NetworkResult.Success -> {
            val rawErrors = result.value.json.errors
            if (rawErrors.isEmpty()) {
                RedditContentSafetyNetworkResult.Acknowledged(result.metadata)
            } else {
                val errors = parseRedditContentReportApiErrors(rawErrors)
                if (errors.isEmpty()) {
                    RedditContentSafetyNetworkResult.MalformedPayload
                } else {
                    RedditContentSafetyNetworkResult.ApiFailure(
                        errors = errors,
                        metadata = result.metadata,
                    )
                }
            }
        }

        is NetworkResult.HttpFailure ->
            RedditContentSafetyNetworkResult.HttpFailure(
                statusCode = result.statusCode,
                retryable = result.retryable,
                metadata = result.metadata,
                providerReason = result.providerReason,
            )

        is NetworkResult.TransportFailure ->
            RedditContentSafetyNetworkResult.TransportFailure(
                kind = result.kind,
                retryable = result.retryable,
            )

        is NetworkResult.ProtocolFailure ->
            RedditContentSafetyNetworkResult.ProtocolFailure(result.kind, result.jsonPath)
    }
}

internal fun parseRedditContentReportApiErrors(
    values: List<JsonElement>,
): List<RedditContentReportApiError> = values.mapNotNull { value ->
    val tuple = value as? JsonArray ?: return@mapNotNull null
    val code = tuple.reportApiPart(0, REPORT_API_CODE_MAX_CHARS)
        ?.uppercase()
        ?.takeIf { candidate ->
            candidate.all { character ->
                character.isLetterOrDigit() || character == '_' || character == '-'
            }
        }
    val message = tuple.reportApiPart(1, REPORT_API_MESSAGE_MAX_CHARS)
    val field = tuple.reportApiPart(2, REPORT_API_FIELD_MAX_CHARS)
        ?.takeIf { candidate ->
            candidate.all { character ->
                character.isLetterOrDigit() || character == '_'
            }
        }
    RedditContentReportApiError(
        code = code,
        message = message,
        field = field,
    )
}

private fun JsonArray.reportApiPart(
    index: Int,
    maxChars: Int,
): String? =
    (getOrNull(index) as? JsonPrimitive)
        ?.contentOrNull
        ?.trim()
        ?.filterNot(Char::isISOControl)
        ?.take(maxChars)
        ?.takeIf(String::isNotEmpty)

private const val REPORT_API_CODE_MAX_CHARS = 80
private const val REPORT_API_MESSAGE_MAX_CHARS = 240
private const val REPORT_API_FIELD_MAX_CHARS = 64
