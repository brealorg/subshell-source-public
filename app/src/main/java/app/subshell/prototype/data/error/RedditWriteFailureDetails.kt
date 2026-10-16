package app.subshell.prototype.data.error

import app.subshell.prototype.data.network.NetworkProtocolFailureKind
import app.subshell.prototype.data.network.NetworkResponseMetadata
import app.subshell.prototype.data.network.NetworkTransportFailureKind

internal fun redditWriteHttpFailureDetails(
    statusCode: Int,
    retryable: Boolean,
    metadata: NetworkResponseMetadata? = null,
): FailureDetails = FailureDetails(
    // LOWCRASH01_UNTRUSTED_HEADER_VALUES_CLAMPED_V1 (OCB-627): a status a
    // proxy made up (600-999) is left out instead of failing FailureDetails' check.
    httpStatusCode = statusCode.takeIf(::isHttpStatusCodeInRange),
    // LOW_ERROR_RATE_WINDOW_ONLY_FOR_429_V1 (OCB-532): Reddit sends its rate
    // window reset on every answer; it is a "retry after" only for a 429.
    retryAfterMs = (
        metadata?.retryAfterMs
            ?: metadata?.rateLimit?.resetAfterMs?.takeIf { statusCode == 429 }
        )?.coerceAtLeast(0L),
    retryable = retryable,
)

internal fun redditWriteTransportFailureDetails(
    kind: NetworkTransportFailureKind,
    retryable: Boolean,
): FailureDetails = FailureDetails(
    retryable = retryable,
    transportKind = kind,
)

internal fun redditWriteProtocolFailureDetails(
    kind: NetworkProtocolFailureKind,
    jsonPath: String? = null,
): FailureDetails = FailureDetails(
    retryable = false,
    protocolKind = kind,
    jsonPath = safeJsonPathText(jsonPath),
)

internal fun redditWriteProviderFailureDetails(
    code: String?,
    message: String? = null,
    retryable: Boolean? = null,
    retryAfterMs: Long? = null,
): FailureDetails = FailureDetails(
    retryAfterMs = retryAfterMs,
    retryable = retryable,
).withProviderDetails(
    code = code,
    message = message,
)

// DATALAYER07: a write answered with one of these may have been applied
// (gateway or timeout after Reddit stored it). The outcome is unknown: read
// back, never send again. RedditCommentSubmitter has used this set since
// COMMENT write; the reply writers now share it.
internal fun isRedditWriteOutcomeUnknownStatus(statusCode: Int): Boolean =
    statusCode == 408 ||
        statusCode == 421 ||
        statusCode == 425 ||
        statusCode in 500..599
