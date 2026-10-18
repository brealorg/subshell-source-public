package app.subshell.prototype.data.network.reddit

import android.util.Log
import app.subshell.prototype.BuildConfig
import app.subshell.prototype.data.account.normalizeCommentEditFullname
import app.subshell.prototype.data.network.FormPostRequest
import app.subshell.prototype.data.network.JsonGetRequest
import app.subshell.prototype.data.network.NetworkProtocolFailureKind
import app.subshell.prototype.data.network.NetworkResponseMetadata
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.NetworkTransportFailureKind
import app.subshell.prototype.data.network.SubshellHttpClient
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
internal data class RedditCommentDeleteResponseDto(
    val json: RedditCommentDeleteJsonDto? = null,
    val error: JsonElement? = null,
    val message: JsonElement? = null,
    val success: Boolean? = null,
)

@Serializable
internal data class RedditCommentDeleteJsonDto(
    val errors: List<JsonElement> = emptyList(),
)

internal enum class RedditCommentDeleteReadbackState {
    DELETED_PLACEHOLDER,
    ABSENT,
    PRESENT,
    MALFORMED,
}

internal sealed interface RedditCommentDeleteNetworkResult {
    data class Deleted(
        val metadata: NetworkResponseMetadata,
    ) : RedditCommentDeleteNetworkResult

    data class OutcomeNotConfirmed(
        val metadata: NetworkResponseMetadata,
    ) : RedditCommentDeleteNetworkResult

    // WRITEOUT01_COMMENT_DELETE_REFUSAL_IS_NOT_UNKNOWN_V1 (OCB-490/541): Reddit
    // answered and refused the delete (json.errors, error, success=false).
    data class Rejected(
        val code: String?,
        val metadata: NetworkResponseMetadata,
    ) : RedditCommentDeleteNetworkResult

    data class HttpFailure(
        val statusCode: Int,
        val retryable: Boolean,
        val metadata: NetworkResponseMetadata,
        val providerReason: String? = null,
    ) : RedditCommentDeleteNetworkResult

    data class TransportFailure(
        val kind: NetworkTransportFailureKind,
        val retryable: Boolean,
    ) : RedditCommentDeleteNetworkResult

    data class ProtocolFailure(
        val kind: NetworkProtocolFailureKind,
        val jsonPath: String? = null,
    ) : RedditCommentDeleteNetworkResult
}

internal class RedditCommentDeleteNetworkDataSource(
    private val httpClient: SubshellHttpClient,
) {
    suspend fun delete(
        commentFullname: String,
    ): RedditCommentDeleteNetworkResult {
        val target = normalizeCommentEditFullname(commentFullname)
        val request = redditCommentDeleteFormRequest(target)

        commentDeleteDebugLog(COMMENT_DELETE_LOG_TAG, "dispatch target=$target")

        return when (
            val result = httpClient.postFormJson(
                request = request,
                deserializer = RedditCommentDeleteResponseDto.serializer(),
            )
        ) {
            is NetworkResult.Success -> {
                val errors = result.value.json?.errors.orEmpty()
                val apiCode = redditDeleteApiErrorCode(errors)
                val topLevelError = redditDeletePrimitiveValue(result.value.error)
                    ?.takeUnless { value ->
                        value == "0" || value.equals("null", true)
                    }
                val messagePresent =
                    !redditDeletePrimitiveValue(result.value.message).isNullOrBlank()

                val apiFailure =
                    errors.isNotEmpty() ||
                        topLevelError != null ||
                        result.value.success == false ||
                        messagePresent

                if (apiFailure) {
                    commentDeleteDebugLog(
                        COMMENT_DELETE_LOG_TAG,
                        "api_failure target=$target status=${result.metadata.statusCode} " +
                            "code=${apiCode ?: topLevelError ?: "unknown"} " +
                            "errors=${errors.size}",
                    )
                    RedditCommentDeleteNetworkResult.Rejected(
                        code = apiCode ?: topLevelError,
                        metadata = result.metadata,
                    )
                } else {
                    commentDeleteDebugLog(
                        COMMENT_DELETE_LOG_TAG,
                        "ack_success target=$target status=${result.metadata.statusCode}",
                    )
                    reconcileDelete(
                        target = target,
                        acknowledgementMetadata = result.metadata,
                    )
                }
            }

            is NetworkResult.HttpFailure ->
                RedditCommentDeleteNetworkResult.HttpFailure(
                    statusCode = result.statusCode,
                    retryable = result.retryable,
                    metadata = result.metadata,
                    providerReason = result.providerReason,
                )

            is NetworkResult.TransportFailure ->
                RedditCommentDeleteNetworkResult.TransportFailure(
                    kind = result.kind,
                    retryable = result.retryable,
                )

            is NetworkResult.ProtocolFailure ->
                RedditCommentDeleteNetworkResult.ProtocolFailure(result.kind, result.jsonPath)
        }
    }

    // WRITEOUT01_COMMENT_DELETE_READ_BACK_AFTER_LOST_ANSWER_V1 (OCB-489/542):
    // after a delete whose answer was lost or unreadable, read the comment
    // back: gone (deleted), there on every read (not deleted; a new delete is
    // safe, deleting is idempotent), or Reddit could not be read (unknown).
    suspend fun readBack(target: String): RedditCommentDeleteReadback {
        var consecutiveAbsent = 0
        for (delayMs in DELETE_READBACK_DELAYS_MS) {
            if (delayMs > 0L) delay(delayMs)
            when (
                val readback = httpClient.getJson(
                    request = redditCommentDeleteInfoRequest(target),
                    deserializer = RedditCommentListingDto.serializer(),
                )
            ) {
                is NetworkResult.Success -> when (
                    redditCommentDeleteReadbackState(listing = readback.value, targetFullname = target)
                ) {
                    RedditCommentDeleteReadbackState.DELETED_PLACEHOLDER ->
                        return RedditCommentDeleteReadback.Deleted(readback.metadata)
                    RedditCommentDeleteReadbackState.ABSENT -> {
                        consecutiveAbsent += 1
                        if (consecutiveAbsent >= 2) return RedditCommentDeleteReadback.Deleted(readback.metadata)
                    }
                    RedditCommentDeleteReadbackState.PRESENT -> consecutiveAbsent = 0
                    RedditCommentDeleteReadbackState.MALFORMED -> return RedditCommentDeleteReadback.Unknown
                }
                is NetworkResult.HttpFailure,
                is NetworkResult.TransportFailure,
                is NetworkResult.ProtocolFailure,
                -> return RedditCommentDeleteReadback.Unknown
            }
        }
        return if (consecutiveAbsent == 0) {
            RedditCommentDeleteReadback.StillPresent
        } else {
            RedditCommentDeleteReadback.Unknown
        }
    }

    private suspend fun reconcileDelete(
        target: String,
        acknowledgementMetadata: NetworkResponseMetadata,
    ): RedditCommentDeleteNetworkResult {
        var consecutiveAbsent = 0

        for (attempt in DELETE_READBACK_DELAYS_MS.indices) {
            val delayMs = DELETE_READBACK_DELAYS_MS[attempt]
            if (delayMs > 0L) {
                delay(delayMs)
            }

            when (
                val readback = httpClient.getJson(
                    request = redditCommentDeleteInfoRequest(target),
                    deserializer = RedditCommentListingDto.serializer(),
                )
            ) {
                is NetworkResult.Success -> {
                    val state = redditCommentDeleteReadbackState(
                        listing = readback.value,
                        targetFullname = target,
                    )

                    commentDeleteDebugLog(
                        COMMENT_DELETE_LOG_TAG,
                        "readback target=$target attempt=${attempt + 1} state=$state",
                    )

                    when (state) {
                        RedditCommentDeleteReadbackState.DELETED_PLACEHOLDER -> {
                            return RedditCommentDeleteNetworkResult.Deleted(
                                acknowledgementMetadata,
                            )
                        }

                        RedditCommentDeleteReadbackState.ABSENT -> {
                            consecutiveAbsent += 1
                            if (consecutiveAbsent >= 2) {
                                return RedditCommentDeleteNetworkResult.Deleted(
                                    acknowledgementMetadata,
                                )
                            }
                        }

                        RedditCommentDeleteReadbackState.PRESENT -> {
                            consecutiveAbsent = 0
                        }

                        RedditCommentDeleteReadbackState.MALFORMED -> {
                            return RedditCommentDeleteNetworkResult.ProtocolFailure(
                                NetworkProtocolFailureKind.MALFORMED_JSON,
                            )
                        }
                    }
                }

                is NetworkResult.HttpFailure,
                is NetworkResult.TransportFailure,
                is NetworkResult.ProtocolFailure,
                -> return RedditCommentDeleteNetworkResult.OutcomeNotConfirmed(
                    acknowledgementMetadata,
                )
            }
        }

        commentDeleteDebugLog(
            COMMENT_DELETE_LOG_TAG,
            "outcome_not_confirmed target=$target",
        )

        return RedditCommentDeleteNetworkResult.OutcomeNotConfirmed(
            acknowledgementMetadata,
        )
    }
}

internal sealed interface RedditCommentDeleteReadback {
    data class Deleted(val metadata: NetworkResponseMetadata) : RedditCommentDeleteReadback
    data object StillPresent : RedditCommentDeleteReadback
    data object Unknown : RedditCommentDeleteReadback
}

internal fun redditCommentDeleteFormRequest(
    commentFullname: String,
): FormPostRequest = FormPostRequest(
    pathSegments = listOf("api", "del"),
    fields = listOf(
        "id" to normalizeCommentEditFullname(commentFullname),
    ),
)

internal fun redditCommentDeleteInfoRequest(
    commentFullname: String,
): JsonGetRequest = JsonGetRequest(
    pathSegments = listOf("api", "info"),
    queryParameters = listOf(
        "raw_json" to "1",
        "id" to normalizeCommentEditFullname(commentFullname),
    ),
)

internal fun redditCommentDeleteReadbackState(
    listing: RedditCommentListingDto,
    targetFullname: String,
): RedditCommentDeleteReadbackState {
    val target = normalizeCommentEditFullname(targetFullname)
    val children = listing.data.children

    if (children.isEmpty()) {
        return RedditCommentDeleteReadbackState.ABSENT
    }

    val matches = children.filter { thing ->
        redditCommentThingFullname(thing.data) == target
    }

    if (matches.size != 1) {
        return RedditCommentDeleteReadbackState.MALFORMED
    }

    val match = matches.single()

    if (match.kind != null && match.kind != REDDIT_DELETE_COMMENT_KIND) {
        return RedditCommentDeleteReadbackState.MALFORMED
    }

    val author = match.data.author?.trim()
    val body = match.data.body.trim()

    val deletedAuthor =
        author.isNullOrEmpty() ||
            author.equals("[deleted]", ignoreCase = true)

    val deletedBody =
        body.equals("[deleted]", ignoreCase = true)

    return if (deletedAuthor && deletedBody) {
        RedditCommentDeleteReadbackState.DELETED_PLACEHOLDER
    } else {
        RedditCommentDeleteReadbackState.PRESENT
    }
}

internal fun redditCommentThingFullname(
    data: RedditCommentDataDto,
): String? {
    data.name
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.let { fullname ->
            return runCatching {
                normalizeCommentEditFullname(fullname)
            }.getOrNull()
        }

    val id = data.id
        .trim()
        .takeIf(String::isNotEmpty)
        ?: return null

    return runCatching {
        normalizeCommentEditFullname("t1_$id")
    }.getOrNull()
}

private fun redditDeleteApiErrorCode(
    errors: List<JsonElement>,
): String? {
    val first = errors.firstOrNull() as? JsonArray ?: return null
    return (first.getOrNull(0) as? JsonPrimitive)
        ?.contentOrNull
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.take(64)
}

private fun redditDeletePrimitiveValue(
    value: JsonElement?,
): String? = (value as? JsonPrimitive)
    ?.contentOrNull
    ?.trim()
    ?.takeIf(String::isNotEmpty)
    ?.take(64)

private val DELETE_READBACK_DELAYS_MS =
    longArrayOf(0L, 300L, 900L, 1_800L)

private const val REDDIT_DELETE_COMMENT_KIND = "t1"
private const val COMMENT_DELETE_LOG_TAG = "SubshellCommentDelete"

// LOW_NET_DELETE_TARGETS_LOGGED_ONLY_IN_DEBUG_BUILDS_V1 (OCB-540): the
// deleted comment's fullname and outcome stay out of release logcat.
private fun commentDeleteDebugLog(tag: String, message: String) {
    if (BuildConfig.DEBUG) Log.d(tag, message)
}
