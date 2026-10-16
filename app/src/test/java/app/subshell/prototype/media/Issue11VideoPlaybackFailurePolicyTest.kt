package app.subshell.prototype.media

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.CancellationException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Issue11VideoPlaybackFailurePolicyTest {
    private class HttpFailure(val status: Int) : RuntimeException("Do not display this URL/token")

    private fun source(status: Int?, index: Int = 0, attempt: Long = 1) = VideoSourceFailure(
        mediaId = "t3_issue11", attemptId = attempt, sourceIndex = index,
        media3ErrorCode = 2004, httpStatusCode = status,
    )

    private fun terminal(vararg sources: VideoSourceFailure) = VideoPlaybackFailure(
        mediaId = "t3_issue11", attemptId = sources.first().attemptId, sources = sources.toList(),
    )

    private fun extract(cause: Throwable, code: Int = 2004): VideoSourceFailure? = videoSourceFailure(
        mediaId = "t3_issue11", attemptId = 4, sourceIndex = 2,
        media3ErrorCode = code, cause = cause,
        httpStatusCodeOf = { (it as? HttpFailure)?.status },
    )

    @Test fun nestedHttpCausePreservesActual403AndSourceIdentity() {
        val result = requireNotNull(extract(RuntimeException("outer", HttpFailure(403))))
        assertEquals(403, result.httpStatusCode)
        assertEquals("t3_issue11", result.mediaId)
        assertEquals(4L, result.attemptId)
        assertEquals(2, result.sourceIndex)
        assertFalse(result.toString().contains("URL/token"))
    }

    @Test fun exceptionMessageIsNeverUsedToGuessHttpStatus() {
        assertNull(extract(RuntimeException("Response code: 403"))?.httpStatusCode)
    }

    @Test fun invalidHttpNumberIsNotInventedAsAStatus() {
        assertNull(extract(HttpFailure(999))?.httpStatusCode)
    }

    @Test fun cyclicCauseChainIsBounded() {
        val first = RuntimeException("first")
        val second = RuntimeException("second")
        first.initCause(second)
        second.initCause(first)
        assertNotNull(extract(first))
    }

    @Test fun cancellationDoesNotBecomeUnavailableVideo() {
        assertNull(extract(RuntimeException("outer", CancellationException("cancelled"))))
        assertNull(extract(InterruptedException("navigation")))
    }

    @Test fun all403HasExactlyTheAcceptedCopyWithoutDuplicateTechnicalLayer() {
        val copy = videoPlaybackErrorCopy(2004, terminal(source(403), source(403, 1), source(403, 2)))
        assertEquals("HTTP 403 · Forbidden", copy.title)
        assertEquals("Access denied by server.", copy.message)
        assertFalse(copy.message.contains("Other source"))
        assertFalse(copy.message.contains("deleted", ignoreCase = true))
    }

    @Test fun http404IsNotAnAssertionThatThePostWasDeleted() {
        val copy = videoPlaybackErrorCopy(2004, terminal(source(404)))
        assertEquals("HTTP 404 · Not Found", copy.title)
        assertTrue(copy.message.contains("video resource could not be found"))
        assertFalse(copy.message.contains("deleted", ignoreCase = true))
    }

    @Test fun http410CanDescribeTheServerResourceAsNoLongerAvailable() {
        val copy = videoPlaybackErrorCopy(2004, terminal(source(410)))
        assertEquals("HTTP 410 · Gone", copy.title)
        assertEquals("This video is no longer available from the server.", copy.message)
    }

    @Test fun http429And503KeepDistinctNamesAndTemporaryGuidance() {
        val limited = videoPlaybackErrorCopy(2004, terminal(source(429)))
        val unavailable = videoPlaybackErrorCopy(2004, terminal(source(503)))
        assertEquals("HTTP 429 · Too Many Requests", limited.title)
        assertTrue(limited.message.contains("Try again later"))
        assertEquals("HTTP 503 · Service Unavailable", unavailable.title)
        assertTrue(unavailable.message.contains("temporarily"))
    }

    @Test fun unfamiliarStatusKeepsItsActualNumberWithoutInventedReason() {
        assertEquals("HTTP 499", videoPlaybackErrorCopy(2004, terminal(source(499))).title)
    }

    @Test fun mixedSourceFailuresDoNotHideEarlierDifferentErrors() {
        val copy = videoPlaybackErrorCopy(2004, terminal(source(403), source(404, 1), source(503, 2)))
        assertEquals("HTTP 503 · Service Unavailable", copy.title)
        assertTrue(copy.message.contains("HTTP 403 · Forbidden"))
        assertTrue(copy.message.contains("HTTP 404 · Not Found"))
        assertFalse(copy.message.contains("deleted", ignoreCase = true))
    }

    @Test fun timeoutDnsTlsAndConnectionHaveConcreteNonHttpTitles() {
        val cases = listOf(
            SocketTimeoutException() to "Connection timed out",
            UnknownHostException() to "DNS lookup failed",
            SSLHandshakeException("private server info") to "TLS connection failed",
            ConnectException() to "Connection failed",
        )
        for ((cause, title) in cases) {
            val failure = requireNotNull(extract(cause, 2001))
            val copy = videoPlaybackErrorCopy(2001, terminal(failure))
            assertEquals(title, copy.title)
            assertFalse(copy.title.contains("HTTP"))
            assertFalse(copy.message.contains("private server info"))
        }
    }

    @Test fun media3TimeoutWithoutNestedCauseRemainsReadable() {
        assertEquals("Connection timed out", videoPlaybackErrorCopy(2002, null).title)
    }

    @Test fun missingHttpCauseDoesNotGuess403FromMedia3Code2004() {
        val copy = videoPlaybackErrorCopy(2004, null)
        assertEquals("Media3 2004 · HTTP request failed", copy.title)
        assertFalse(copy.title.contains("403"))
        assertFalse(copy.title.contains("Bad HTTP status"))
    }

    @Test fun codecAndUnknownErrorsKeepTheirNumericalMedia3Code() {
        assertEquals("Media3 4001 · Decoding failed", videoPlaybackErrorCopy(4001, null).title)
        assertEquals("Media3 9999 · Playback error", videoPlaybackErrorCopy(9999, null).title)
    }

    @Test fun terminalFailureCannotLeakToAnotherMediaOrRetryAttempt() {
        val failure = terminal(source(403))
        assertEquals(failure, currentVideoPlaybackFailure(failure, "t3_issue11", 1))
        assertNull(currentVideoPlaybackFailure(failure, "t3_other", 1))
        assertNull(currentVideoPlaybackFailure(failure, "t3_issue11", 2))
        assertNull(currentVideoPlaybackFailure(failure, null, 1))
    }

    @Test fun onlyCurrentOwnedRequestedPlayerFailureIsAccepted() {
        fun accepted(media: String? = "t3_issue11", player: String? = "t3_issue11",
                     owner: Boolean = true, requested: Boolean = true, current: Boolean = true) =
            videoFailureBelongsToCurrentPlayback(media, player, owner, requested, current)
        assertTrue(accepted())
        assertFalse(accepted(media = null))
        assertFalse(accepted(player = "t3_other"))
        assertFalse(accepted(owner = false))
        assertFalse(accepted(requested = false))
        assertFalse(accepted(current = false))
    }
}
