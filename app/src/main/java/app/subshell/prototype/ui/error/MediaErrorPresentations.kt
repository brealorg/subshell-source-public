package app.subshell.prototype.ui.error

import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.LocalFailureKind
import app.subshell.prototype.media.isRemoteMediaGone

internal enum class RemoteMediaKind {
    IMAGE,
    GIF,
}

internal fun remoteMediaErrorPresentation(
    kind: RemoteMediaKind,
    details: FailureDetails,
    sourceUri: String? = null,
): ErrorPresentation {
    val noun = when (kind) {
        RemoteMediaKind.IMAGE -> "image"
        RemoteMediaKind.GIF -> "GIF"
    }
    // MEDIAGONE01_A_GONE_SOURCE_READS_REMOVED_V1 (owner 2026-10-18): a 404/410
    // from the source means the media was removed; it is shown as such, without
    // an action, and is not recorded as an error.
    if (details.isRemoteMediaGone()) {
        return ErrorPresentation(
            kind = ErrorPresentationKind.INLINE,
            title = if (kind == RemoteMediaKind.IMAGE) "Image removed" else "GIF removed",
            message = "It is no longer available where it was posted.",
        ).withFailureDetails(details)
    }
    val message = when {
        details.transportKind != null -> "subshell couldn't reach the $noun source."
        details.httpStatusCode != null &&
            details.httpStatusCode in 300..399 &&
            details.localKind == LocalFailureKind.SECURITY ->
            "The $noun source redirected to a location blocked by subshell's secure media policy."
        details.httpStatusCode != null && details.httpStatusCode in 300..399 ->
            "The $noun source redirected the request."
        details.httpStatusCode != null -> "The $noun source rejected the request."
        details.protocolKind != null -> "The $noun source returned data subshell couldn't use."
        details.localKind == LocalFailureKind.DECODING -> "subshell couldn't decode this $noun."
        details.localKind == LocalFailureKind.SECURITY -> "This $noun source isn't allowed by subshell's secure media policy."
        details.localKind == LocalFailureKind.VALIDATION -> "This $noun has an invalid source address."
        else -> "subshell couldn't load this $noun."
    }
    // ISSUE228_RETRY_ONLY_WHEN_IT_CAN_HELP_V1 (owner, #228): Retry only for a
    // failure that can pass (connection, a server that may answer later). A
    // blocked, invalid or undecodable source offers the browser instead
    // (ISSUE228_BLOCKED_MEDIA_OPENS_IN_THE_BROWSER_V1).
    val action = when {
        details.retryable != false -> ErrorAction(ErrorActionKind.RETRY, "Retry")
        mediaSourceHost(sourceUri) != null -> ErrorAction(ErrorActionKind.OPEN_IN_BROWSER, "Open in browser")
        else -> null
    }
    return ErrorPresentation(
        kind = ErrorPresentationKind.INLINE,
        title = "Couldn't load $noun",
        message = message,
        primaryAction = action,
    ).withFailureDetails(details)
}

/** The host a blocked media link points at, shown next to "Open in browser". */
internal fun mediaSourceHost(sourceUri: String?): String? =
    sourceUri
        ?.let { runCatching { java.net.URI(it.trim()) }.getOrNull() }
        ?.takeIf { it.scheme.equals("http", ignoreCase = true) || it.scheme.equals("https", ignoreCase = true) }
        ?.host
        ?.removePrefix("www.")
        ?.takeIf(String::isNotBlank)

internal fun videoPlaybackFailureDetails(errorCode: Int?): FailureDetails {
    val localKind = when {
        errorCode != null && errorCode in 3000..3999 -> LocalFailureKind.VALIDATION
        errorCode != null && errorCode in 4000..4999 -> LocalFailureKind.DECODING
        errorCode != null && errorCode in 5000..5999 -> LocalFailureKind.IO
        errorCode != null && errorCode in 6000..6999 -> LocalFailureKind.SECURITY
        else -> null
    }
    return FailureDetails(
        retryable = errorCode == null || errorCode !in 4000..6999,
        localKind = localKind,
    ).withMediaCode(errorCode?.let(::videoPlaybackCodeLabel))
}

private fun videoPlaybackCodeLabel(errorCode: Int): String = when (errorCode) {
    1002 -> "Behind live window"
    1003 -> "Playback timeout"
    2001 -> "Network connection failed"
    2002 -> "Network connection timeout"
    2003 -> "Invalid HTTP content type"
    2004 -> "Bad HTTP status"
    2005 -> "Media file not found"
    2006 -> "Media permission denied"
    2007 -> "Cleartext media blocked"
    2008 -> "Read position out of range"
    3001 -> "Malformed media container"
    3002 -> "Unsupported media container"
    4001 -> "Decoder initialization failed"
    4002 -> "Decoder query failed"
    4003 -> "Decoding failed"
    4004 -> "Decoding format exceeded capabilities"
    4005 -> "Decoding format unsupported"
    6002 -> "DRM license acquisition failed"
    6003 -> "DRM content error"
    6004 -> "DRM system error"
    6006 -> "DRM license expired"
    else -> "Playback error $errorCode"
}

// ISSUE11A3_ACTUAL_ERROR_THEN_EXPLANATION_V1
internal fun videoPlaybackErrorPresentation(
    state: app.subshell.prototype.media.VideoPlaybackUiState,
): ErrorPresentation {
    val failure = state.failure?.takeIf {
        state.phase == app.subshell.prototype.media.VideoPlaybackPhase.ERROR &&
            it.mediaId == state.mediaId
    }
    val copy = app.subshell.prototype.media.videoPlaybackErrorCopy(state.errorCode, failure)
    // LOWUI4_VIDEO_FAILURE_KEEPS_ITS_DETAILS_V1 (OCB-169): the diagnostics get
    // the media code and HTTP status; the visible text stays the copy above.
    val details = videoPlaybackFailureDetails(failure?.finalSource?.media3ErrorCode ?: state.errorCode)
        .copy(httpStatusCode = failure?.finalSource?.httpStatusCode)
    return ErrorPresentation(
        kind = ErrorPresentationKind.DEGRADED,
        title = copy.title,
        message = copy.message,
        technicalDetail = null,
        primaryAction = ErrorAction(ErrorActionKind.RETRY, "Retry"),
        failureDetails = details,
    )
}
