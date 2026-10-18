package app.subshell.prototype.data.error

import app.subshell.prototype.data.diagnostics.diagnosticExceptionClass
import app.subshell.prototype.data.network.NetworkProtocolFailureKind
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.JSON_FIELD_PATH_MAX_CHARS
import app.subshell.prototype.data.network.NetworkTransportFailureKind
import app.subshell.prototype.data.network.sanitizeJsonFieldPath
import kotlin.math.ceil

enum class LocalFailureKind {
    STORAGE,
    IO,
    PERMISSION,
    VALIDATION,
    CONFIGURATION,
    SECURITY,
    DECODING,
    UNKNOWN,
}

@JvmInline
value class SafeFailureText private constructor(
    val value: String,
) {
    companion object {
        internal fun fromUntrusted(
            raw: String?,
            maxLength: Int = DEFAULT_SAFE_FAILURE_TEXT_LIMIT,
        ): SafeFailureText? = sanitizeFailureText(raw, maxLength)
            ?.let(::SafeFailureText)
    }
}

data class FailureDetails(
    val httpStatusCode: Int? = null,
    val retryAfterMs: Long? = null,
    val retryable: Boolean? = null,
    val transportKind: NetworkTransportFailureKind? = null,
    val protocolKind: NetworkProtocolFailureKind? = null,
    val providerCode: SafeFailureText? = null,
    val providerMessage: SafeFailureText? = null,
    val mediaCode: SafeFailureText? = null,
    val localKind: LocalFailureKind? = null,
    /** DIAG03_JSON_FIELD_PATH_V1: the response field a decode failed on. */
    val jsonPath: SafeFailureText? = null,
    /**
     * DIAG04_EXCEPTION_CLASS_V1: for a local failure of unknown kind, the
     * exception's simple class name (never its message); diagnostics only.
     */
    val exceptionClass: String? = null,
) {
    init {
        require(httpStatusCode == null || isHttpStatusCodeInRange(httpStatusCode))
        require(retryAfterMs == null || retryAfterMs >= 0L)
    }

    fun withProviderDetails(
        code: String? = null,
        message: String? = null,
    ): FailureDetails = copy(
        providerCode = SafeFailureText.fromUntrusted(
            raw = code,
            maxLength = SAFE_PROVIDER_CODE_LIMIT,
        ),
        providerMessage = SafeFailureText.fromUntrusted(message),
    )

    fun withExceptionClass(error: Throwable): FailureDetails =
        copy(exceptionClass = error.diagnosticExceptionClass())

    fun withMediaCode(code: String?): FailureDetails = copy(
        mediaCode = SafeFailureText.fromUntrusted(
            raw = code,
            maxLength = SAFE_PROVIDER_CODE_LIMIT,
        ),
    )
}

internal fun isHttpStatusCodeInRange(code: Int): Boolean = code in 100..599

fun NetworkResult<*>.failureDetailsOrNull(): FailureDetails? = when (this) {
    is NetworkResult.Success -> null
    is NetworkResult.HttpFailure -> FailureDetails(
        httpStatusCode = statusCode.takeIf(::isHttpStatusCodeInRange),
        retryAfterMs = metadata.retryAfterMs?.coerceAtLeast(0L),
        retryable = retryable,
        // ISSUE211_FORBIDDEN_REASON_IS_READ_V1
        providerCode = SafeFailureText.fromUntrusted(providerReason),
    )
    is NetworkResult.TransportFailure -> FailureDetails(
        retryable = retryable,
        transportKind = kind,
    )
    is NetworkResult.ProtocolFailure -> FailureDetails(
        retryable = false,
        protocolKind = kind,
        jsonPath = safeJsonPathText(jsonPath),
    )
}

internal fun safeJsonPathText(path: String?): SafeFailureText? =
    path?.let(::sanitizeJsonFieldPath)?.let { SafeFailureText.fromUntrusted(it, JSON_FIELD_PATH_MAX_CHARS) }

fun FailureDetails.toSafeTechnicalDetail(): String? = buildList {
    httpStatusCode?.let { add("HTTP $it") }
    transportKind?.let { add("Network: ${it.userFacingLabel()}") }
    protocolKind?.let { add("Response: ${it.userFacingLabel()}") }
    jsonPath?.value?.let { add("Field $it") }
    localKind?.let { add("Local: ${it.userFacingLabel()}") }
    providerCode?.value?.let { add("Provider code $it") }
    providerMessage?.value?.let(::add)
    mediaCode?.value?.let { add("Media: $it") }
    retryAfterMs?.let { add(formatRetryAfter(it)) }
}.distinct().joinToString(separator = " · ").ifBlank { null }

private fun sanitizeFailureText(raw: String?, maxLength: Int): String? {
    require(maxLength in 1..MAX_SAFE_FAILURE_TEXT_LIMIT)

    val normalized = raw
        ?.replace(CONTROL_CHARACTER_PATTERN, " ")
        ?.replace(WHITESPACE_PATTERN, " ")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: return null

    val redacted = SENSITIVE_AUTHORIZATION_PATTERN.replace(
        normalized,
        "Authorization: [redacted]",
    ).let { value ->
        SENSITIVE_BEARER_PATTERN.replace(value, "Bearer [redacted]")
    }.let { value ->
        SENSITIVE_COOKIE_HEADER_PATTERN.replace(value) { match ->
            "${match.groupValues[1]}: [redacted]"
        }
    }.let { value ->
        SENSITIVE_KEY_VALUE_PATTERN.replace(value) { match ->
            "${match.groupValues[1]}=[redacted]"
        }
    }

    return redacted.take(maxLength).trim().takeIf { it.isNotEmpty() }
}

private fun formatRetryAfter(retryAfterMs: Long): String = when {
    retryAfterMs <= 0L -> "Retry available now"
    retryAfterMs < 60_000L -> {
        val seconds = ceil(retryAfterMs / 1_000.0).toLong().coerceAtLeast(1L)
        "Retry after $seconds s"
    }
    else -> {
        val minutes = ceil(retryAfterMs / 60_000.0).toLong().coerceAtLeast(1L)
        "Retry after $minutes min"
    }
}

private fun NetworkTransportFailureKind.userFacingLabel(): String = when (this) {
    NetworkTransportFailureKind.DNS -> "DNS"
    NetworkTransportFailureKind.CONNECT -> "Connection"
    NetworkTransportFailureKind.TIMEOUT -> "Timeout"
    NetworkTransportFailureKind.TLS -> "TLS"
    NetworkTransportFailureKind.IO -> "I/O"
}

private fun NetworkProtocolFailureKind.userFacingLabel(): String = when (this) {
    NetworkProtocolFailureKind.EMPTY_BODY -> "Empty response"
    NetworkProtocolFailureKind.UNEXPECTED_CONTENT_TYPE -> "Unexpected content type"
    NetworkProtocolFailureKind.BODY_TOO_LARGE -> "Response too large"
    NetworkProtocolFailureKind.MALFORMED_JSON -> "Malformed response"
}

private fun LocalFailureKind.userFacingLabel(): String = when (this) {
    LocalFailureKind.STORAGE -> "Storage"
    LocalFailureKind.IO -> "I/O"
    LocalFailureKind.PERMISSION -> "Permission"
    LocalFailureKind.VALIDATION -> "Validation"
    LocalFailureKind.CONFIGURATION -> "Configuration"
    LocalFailureKind.SECURITY -> "Security"
    LocalFailureKind.DECODING -> "Decode"
    LocalFailureKind.UNKNOWN -> "Unknown local failure"
}

private const val DEFAULT_SAFE_FAILURE_TEXT_LIMIT = 240
private const val SAFE_PROVIDER_CODE_LIMIT = 80
private const val MAX_SAFE_FAILURE_TEXT_LIMIT = 512

private val CONTROL_CHARACTER_PATTERN = Regex("[\\p{Cc}\\p{Cf}]")
private val WHITESPACE_PATTERN = Regex("\\s+")
private val SENSITIVE_AUTHORIZATION_PATTERN = Regex(
    pattern = "(?i)\\bauthorization\\s*:\\s*[^\\s]+(?:\\s+[^\\s]+)?",
)
private val SENSITIVE_BEARER_PATTERN = Regex(
    pattern = "(?i)\\bbearer\\s+[A-Za-z0-9._~+/=-]+",
)
private val SENSITIVE_COOKIE_HEADER_PATTERN = Regex(
    pattern = "(?i)\\b(cookie|set-cookie)\\s*:\\s*[^\\s]+",
)
private val SENSITIVE_KEY_VALUE_PATTERN = Regex(
    pattern = "(?i)\\b(access_token|refresh_token|id_token|client_secret|authorization_code|oauth_state|oauth_token|session_cookie|cookie|code|state)\\b\\s*=\\s*([^\\s&,;]+)",
)
