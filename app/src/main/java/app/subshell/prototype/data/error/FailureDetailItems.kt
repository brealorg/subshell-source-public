package app.subshell.prototype.data.error

import app.subshell.prototype.data.network.NetworkProtocolFailureKind
import app.subshell.prototype.data.network.NetworkTransportFailureKind
import kotlin.math.ceil

data class FailureDetailItem(
    val label: String,
    val value: String,
) {
    init {
        require(label.isNotBlank())
        require(value.isNotBlank())
    }
}


fun FailureDetails?.hasMeaningfulExpandedDetails(): Boolean = this != null && (
    httpStatusCode != null ||
        protocolKind != null ||
        jsonPath != null ||
        providerCode != null ||
        providerMessage != null ||
        mediaCode != null ||
        retryAfterMs != null
)

fun FailureDetails.toSafeDetailItems(): List<FailureDetailItem> = buildList {
    when {
        httpStatusCode != null -> add(FailureDetailItem("Failure", "HTTP"))
        transportKind != null -> add(FailureDetailItem("Failure", "Network"))
        protocolKind != null -> add(FailureDetailItem("Failure", "Response"))
        localKind != null -> add(FailureDetailItem("Failure", "Local"))
        providerCode != null || providerMessage != null ->
            add(FailureDetailItem("Failure", "Provider"))
        mediaCode != null -> add(FailureDetailItem("Failure", "Media"))
    }
    httpStatusCode?.let { add(FailureDetailItem("HTTP status", it.toString())) }
    transportKind?.let { add(FailureDetailItem("Network", it.detailLabel())) }
    protocolKind?.let { add(FailureDetailItem("Response", it.detailLabel())) }
    jsonPath?.value?.let { add(FailureDetailItem("Response field", it)) }
    localKind?.let { add(FailureDetailItem("Local", it.detailLabel())) }
    providerCode?.value?.let { add(FailureDetailItem("Provider code", it)) }
    providerMessage?.value?.let { add(FailureDetailItem("Provider message", it)) }
    mediaCode?.value?.let { add(FailureDetailItem("Media code", it)) }
    retryAfterMs?.let { add(FailureDetailItem("Retry after", formatRetryAfterDetail(it))) }
    retryable?.let { add(FailureDetailItem("Retryable", if (it) "Yes" else "No")) }
}

private fun NetworkTransportFailureKind.detailLabel(): String = when (this) {
    NetworkTransportFailureKind.DNS -> "DNS"
    NetworkTransportFailureKind.CONNECT -> "Connection"
    NetworkTransportFailureKind.TIMEOUT -> "Timeout"
    NetworkTransportFailureKind.TLS -> "TLS"
    NetworkTransportFailureKind.IO -> "I/O"
}

private fun NetworkProtocolFailureKind.detailLabel(): String = when (this) {
    NetworkProtocolFailureKind.EMPTY_BODY -> "Empty response"
    NetworkProtocolFailureKind.UNEXPECTED_CONTENT_TYPE -> "Unexpected content type"
    NetworkProtocolFailureKind.BODY_TOO_LARGE -> "Response too large"
    NetworkProtocolFailureKind.MALFORMED_JSON -> "Malformed response"
}

private fun LocalFailureKind.detailLabel(): String = when (this) {
    LocalFailureKind.STORAGE -> "Storage"
    LocalFailureKind.IO -> "I/O"
    LocalFailureKind.PERMISSION -> "Permission"
    LocalFailureKind.VALIDATION -> "Validation"
    LocalFailureKind.CONFIGURATION -> "Configuration"
    LocalFailureKind.SECURITY -> "Security"
    LocalFailureKind.DECODING -> "Decode"
    LocalFailureKind.UNKNOWN -> "Unknown local failure"
}

private fun formatRetryAfterDetail(retryAfterMs: Long): String = when {
    retryAfterMs <= 0L -> "Available now"
    retryAfterMs < 60_000L ->
        "${ceil(retryAfterMs / 1_000.0).toLong().coerceAtLeast(1L)} seconds"
    else ->
        "${ceil(retryAfterMs / 60_000.0).toLong().coerceAtLeast(1L)} minutes"
}
