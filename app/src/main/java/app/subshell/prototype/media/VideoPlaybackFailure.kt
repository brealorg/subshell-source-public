package app.subshell.prototype.media

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.CancellationException
import javax.net.ssl.SSLException

/** Bounded, structured metadata only: no URL, response body, headers or Throwable. */
data class VideoSourceFailure(
    val mediaId: String,
    val attemptId: Long,
    val sourceIndex: Int,
    val media3ErrorCode: Int,
    val httpStatusCode: Int? = null,
    val transportKind: VideoFailureTransportKind = VideoFailureTransportKind.OTHER,
)

enum class VideoFailureTransportKind {
    TIMEOUT,
    DNS,
    TLS,
    CONNECTION,
    // ISSUE207_REDGIFS_PLAYS_IN_THE_APP_V1: what RedGIFs answered for the clip.
    CLIP_UNAVAILABLE,
    PROVIDER_OFF,
    OTHER,
}

/** One playback attempt, including the source failures preceding its terminal failure. */
data class VideoPlaybackFailure(
    val mediaId: String,
    val attemptId: Long,
    val sources: List<VideoSourceFailure>,
) {
    init {
        require(sources.isNotEmpty())
        require(sources.all { it.mediaId == mediaId && it.attemptId == attemptId })
    }

    val finalSource: VideoSourceFailure
        get() = sources.last()
}

internal fun videoFailureBelongsToCurrentPlayback(
    activeMediaId: String?,
    playerMediaId: String?,
    hasOwner: Boolean,
    playRequested: Boolean,
    isCurrentPlayerError: Boolean,
): Boolean = activeMediaId != null && activeMediaId == playerMediaId &&
    hasOwner && playRequested && isCurrentPlayerError

internal fun currentVideoPlaybackFailure(
    failure: VideoPlaybackFailure?,
    mediaId: String?,
    attemptId: Long,
): VideoPlaybackFailure? = failure?.takeIf {
    it.mediaId == mediaId && it.attemptId == attemptId
}

/**
 * Read structured causes; never parse an exception message for a guessed status.
 * The caller supplies Media3's HTTP-exception adapter so this policy stays JVM-testable.
 */
internal fun videoSourceFailure(
    mediaId: String,
    attemptId: Long,
    sourceIndex: Int,
    media3ErrorCode: Int,
    cause: Throwable?,
    httpStatusCodeOf: (Throwable) -> Int?,
): VideoSourceFailure? {
    val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    val causes = mutableListOf<Throwable>()
    var next = cause
    while (next != null && causes.size < 32 && seen.add(next)) {
        causes += next
        next = next.cause
    }
    if (causes.any { it is CancellationException || it is InterruptedException }) return null
    val httpStatus = causes.asSequence()
        .mapNotNull(httpStatusCodeOf)
        .firstOrNull { it in 100..599 }
    val transportKind = when {
        causes.any { it is VideoClipUnavailableException } -> VideoFailureTransportKind.CLIP_UNAVAILABLE
        causes.any { it is VideoProviderOffException } -> VideoFailureTransportKind.PROVIDER_OFF
        causes.any { it is SocketTimeoutException } || media3ErrorCode == 2002 ->
            VideoFailureTransportKind.TIMEOUT
        causes.any { it is UnknownHostException } -> VideoFailureTransportKind.DNS
        causes.any { it is SSLException } -> VideoFailureTransportKind.TLS
        causes.any { it is ConnectException } -> VideoFailureTransportKind.CONNECTION
        else -> VideoFailureTransportKind.OTHER
    }
    return VideoSourceFailure(
        mediaId = mediaId,
        attemptId = attemptId,
        sourceIndex = sourceIndex,
        media3ErrorCode = media3ErrorCode,
        httpStatusCode = httpStatus,
        transportKind = transportKind,
    )
}

internal data class VideoPlaybackErrorCopy(val title: String, val message: String)

internal fun videoPlaybackErrorCopy(
    errorCode: Int?,
    failure: VideoPlaybackFailure?,
): VideoPlaybackErrorCopy {
    val finalSource = failure?.finalSource
    val result = videoSourceErrorCopy(finalSource?.media3ErrorCode ?: errorCode, finalSource)
    // Do not collapse unlike errors into a claim that every source returned the last status.
    val earlierTitles = failure?.sources.orEmpty().dropLast(1)
        .map { videoSourceErrorCopy(it.media3ErrorCode, it).title }
        .distinct()
        .filter { it != result.title }
    return if (earlierTitles.isEmpty()) result else result.copy(
        message = result.message + "\nOther source attempts: " +
            earlierTitles.take(3).joinToString("; ") +
            if (earlierTitles.size > 3) "; …" else "",
    )
}

private fun videoSourceErrorCopy(
    errorCode: Int?,
    failure: VideoSourceFailure?,
): VideoPlaybackErrorCopy {
    failure?.httpStatusCode?.let { status ->
        val reason = when (status) {
            400 -> "Bad Request"
            401 -> "Unauthorized"
            402 -> "Payment Required"
            403 -> "Forbidden"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            408 -> "Request Timeout"
            409 -> "Conflict"
            410 -> "Gone"
            413 -> "Content Too Large"
            414 -> "URI Too Long"
            415 -> "Unsupported Media Type"
            416 -> "Range Not Satisfiable"
            422 -> "Unprocessable Content"
            425 -> "Too Early"
            426 -> "Upgrade Required"
            429 -> "Too Many Requests"
            431 -> "Request Header Fields Too Large"
            451 -> "Unavailable For Legal Reasons"
            500 -> "Internal Server Error"
            501 -> "Not Implemented"
            502 -> "Bad Gateway"
            503 -> "Service Unavailable"
            504 -> "Gateway Timeout"
            505 -> "HTTP Version Not Supported"
            else -> null
        }
        val message = when (status) {
            401 -> "This video cannot be played.\nThe server requires authentication for this video resource."
            403 -> "Access denied by server."
            404 -> "This video cannot be played.\nThe requested video resource could not be found."
            408 -> "This video could not be loaded.\nThe server timed out while receiving the request."
            410 -> "This video is no longer available from the server."
            416 -> "This video cannot be played.\nThe server could not provide the requested part of the video."
            429 -> "This video could not be loaded right now.\nThe server received too many requests. Try again later."
            451 -> "This video cannot be played.\nThe server reports that access is restricted for legal reasons."
            500 -> "This video could not be loaded.\nThe server encountered an internal error."
            502 -> "This video could not be loaded.\nA server gateway received an invalid upstream response."
            503 -> "The video service is temporarily unavailable.\nTry again later."
            504 -> "This video could not be loaded.\nA server gateway did not receive an upstream response in time."
            else -> "This video cannot be played.\nThe video request returned an unexpected HTTP response."
        }
        return VideoPlaybackErrorCopy(
            title = "HTTP $status" + (reason?.let { " · $it" } ?: ""),
            message = message,
        )
    }
    val kind = failure?.transportKind
    return when {
        kind == VideoFailureTransportKind.CLIP_UNAVAILABLE ->
            VideoPlaybackErrorCopy("Clip not available", "This clip was removed or is no longer available.")
        kind == VideoFailureTransportKind.PROVIDER_OFF ->
            VideoPlaybackErrorCopy("RedGIFs playback is off", "Turn on Show NSFW posts to play RedGIFs clips in the app.")
        kind == VideoFailureTransportKind.TIMEOUT || errorCode == 2002 ->
            VideoPlaybackErrorCopy("Connection timed out", "The video server did not respond in time. Try again.")
        kind == VideoFailureTransportKind.DNS ->
            VideoPlaybackErrorCopy("DNS lookup failed", "The video server's address could not be resolved. Check your connection and try again.")
        kind == VideoFailureTransportKind.TLS ->
            VideoPlaybackErrorCopy("TLS connection failed", "A secure connection to the video server could not be established.")
        kind == VideoFailureTransportKind.CONNECTION ->
            VideoPlaybackErrorCopy("Connection failed", "The video server could not be reached. Check your connection and try again.")
        errorCode == 2004 -> VideoPlaybackErrorCopy(
            "Media3 2004 · HTTP request failed",
            "This video cannot be played.\nThe HTTP response status was not available in the playback error.",
        )
        errorCode == 2005 -> VideoPlaybackErrorCopy("Media3 2005 · File not found", "The requested video file could not be found.")
        errorCode == 2006 -> VideoPlaybackErrorCopy("Media3 2006 · No permission", "Permission to read this video resource was denied.")
        errorCode == 2007 -> VideoPlaybackErrorCopy("Media3 2007 · Cleartext not permitted", "This video uses an insecure connection that the app does not allow.")
        errorCode == 2008 -> VideoPlaybackErrorCopy("Media3 2008 · Read position out of range", "The requested position is outside the available video data.")
        errorCode != null && errorCode in 3000..3999 ->
            VideoPlaybackErrorCopy("Media3 $errorCode · Source parsing failed", "This video cannot be played.\nThe video source returned data the player could not parse.")
        errorCode != null && errorCode in 4000..4999 ->
            VideoPlaybackErrorCopy("Media3 $errorCode · Decoding failed", "This video cannot be played.\nThe device could not decode this video format.")
        errorCode != null && errorCode in 5000..5999 ->
            VideoPlaybackErrorCopy("Media3 $errorCode · Audio output failed", "This video cannot be played.\nPlayback failed while using the device audio output.")
        errorCode != null && errorCode in 6000..6999 ->
            VideoPlaybackErrorCopy("Media3 $errorCode · Protected playback failed", "This video cannot be played.\nThe protected video session could not be opened.")
        errorCode != null -> VideoPlaybackErrorCopy(
            "Media3 $errorCode · Playback error",
            "This video cannot be played.\nThe player could not complete playback.",
        )
        else -> VideoPlaybackErrorCopy("Playback error", "This video cannot be played.\nNo further error details were available.")
    }
}
