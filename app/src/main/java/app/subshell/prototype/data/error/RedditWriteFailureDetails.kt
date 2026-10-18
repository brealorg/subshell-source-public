package app.subshell.prototype.data.error

import app.subshell.prototype.data.network.NetworkProtocolFailureKind
import app.subshell.prototype.data.network.NetworkResponseMetadata
import app.subshell.prototype.data.network.NetworkTransportFailureKind

internal fun redditWriteHttpFailureDetails(
    statusCode: Int,
    retryable: Boolean,
    metadata: NetworkResponseMetadata? = null,
    // DIAG04_REDDIT_ERROR_CODE_ON_400_V1: Reddit's code from the refused answer (too_old, thread_locked …).
    providerReason: String? = null,
): FailureDetails = FailureDetails(
    providerCode = SafeFailureText.fromUntrusted(providerReason),
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

/**
 * LOW_ERROR_RATE_WINDOW_ONLY_FOR_429_V1 (OCB-532, extended by WRITEDIAG01
 * 2026-10-18): Reddit's rate-window reset is a "retry after" only when the
 * answer is a rate limit (HTTP 429 or a RATELIMIT error); a Retry-After header
 * always counts. Before, the submit, edit, reply and manage paths put the
 * reset on any failure (a 400 vote showed retry_after_ms in a report).
 */
internal fun redditWriteRetryAfterMs(metadata: NetworkResponseMetadata, rateLimited: Boolean): Long? =
    metadata.retryAfterMs ?: metadata.rateLimit.resetAfterMs.takeIf { rateLimited }

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
