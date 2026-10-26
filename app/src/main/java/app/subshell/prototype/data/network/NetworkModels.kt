package app.subshell.prototype.data.network

data class NetworkPage<T>(
    val items: List<T>,
    val nextCursor: String?,
    // INBOXFAST01_A_REFRESH_IS_COMPLETE_ONLY_SINCE_ITS_OLDER_EDGE_V1: for a
    // first page read from more than one listing (the inbox and sent), the
    // time from which every item is on it; 0 = everything, null = unknown.
    val completeSinceEpochSeconds: Long? = null,
    // INBOXORDER01_STORED_ROWS_READ_ELSEWHERE_TURN_READ_V1: on a first inbox
    // page, every item Reddit holds unread now; null when not known whole.
    val unreadItemIds: Set<String>? = null,
)

data class NetworkRateLimit(
    val remaining: Double?,
    val used: Double?,
    val resetAfterMs: Long?,
)

data class NetworkResponseMetadata(
    val statusCode: Int,
    val receivedAtEpochMs: Long,
    val rateLimit: NetworkRateLimit,
    val retryAfterMs: Long?,
)

enum class NetworkTransportFailureKind {
    DNS,
    CONNECT,
    TIMEOUT,
    TLS,
    IO,
}

enum class NetworkProtocolFailureKind {
    EMPTY_BODY,
    UNEXPECTED_CONTENT_TYPE,
    BODY_TOO_LARGE,
    MALFORMED_JSON,
}

sealed interface NetworkResult<out T> {
    data class Success<T>(
        val value: T,
        val metadata: NetworkResponseMetadata,
    ) : NetworkResult<T>

    data class HttpFailure(
        val statusCode: Int,
        val retryable: Boolean,
        val metadata: NetworkResponseMetadata,
        // ISSUE211_FORBIDDEN_REASON_IS_READ_V1 (#211): Reddit's "reason" of a
        // 403 or 404 (quarantined, private, banned …), sanitized; null otherwise.
        val providerReason: String? = null,
    ) : NetworkResult<Nothing>

    data class TransportFailure(
        val kind: NetworkTransportFailureKind,
        val retryable: Boolean,
    ) : NetworkResult<Nothing>

    data class ProtocolFailure(
        val kind: NetworkProtocolFailureKind,
        /** DIAG03_JSON_FIELD_PATH_V1: the sanitized field a JSON decode failed on. */
        val jsonPath: String? = null,
    ) : NetworkResult<Nothing>
}

inline fun <T, R> NetworkResult<T>.map(transform: (T) -> R): NetworkResult<R> = when (this) {
    is NetworkResult.Success -> NetworkResult.Success(
        value = transform(value),
        metadata = metadata,
    )
    is NetworkResult.HttpFailure -> this
    is NetworkResult.TransportFailure -> this
    is NetworkResult.ProtocolFailure -> this
}

internal fun isRetryableHttpStatus(statusCode: Int): Boolean =
    statusCode == 408 ||
        statusCode == 421 ||
        statusCode == 425 ||
        statusCode == 429 ||
        statusCode == 500 ||
        statusCode == 502 ||
        statusCode == 503 ||
        statusCode == 504

internal fun isRetryableTransportFailure(kind: NetworkTransportFailureKind): Boolean = when (kind) {
    NetworkTransportFailureKind.DNS,
    NetworkTransportFailureKind.CONNECT,
    NetworkTransportFailureKind.TIMEOUT,
    NetworkTransportFailureKind.IO,
    -> true

    NetworkTransportFailureKind.TLS -> false
}
