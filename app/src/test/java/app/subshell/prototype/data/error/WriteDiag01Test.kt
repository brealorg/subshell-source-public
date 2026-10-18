package app.subshell.prototype.data.error

import app.subshell.prototype.data.network.NetworkRateLimit
import app.subshell.prototype.data.network.NetworkResponseMetadata
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * WRITEDIAG01 (owner 2026-10-18): Reddit's rate-window reset is a "retry
 * after" only for a rate limit. A report showed five 400s on votes and saves
 * carrying retry_after_ms; the submit, edit, reply and manage paths still put
 * the reset on any failure.
 */
class WriteDiag01Test {
    private fun metadata(retryAfterMs: Long? = null) = NetworkResponseMetadata(
        statusCode = 400,
        receivedAtEpochMs = 0L,
        rateLimit = NetworkRateLimit(remaining = 590.0, used = 10.0, resetAfterMs = 546_000L),
        retryAfterMs = retryAfterMs,
    )

    @Test
    fun theRateWindowResetCountsOnlyForARateLimit() {
        assertNull(redditWriteRetryAfterMs(metadata(), rateLimited = false))
        assertEquals(546_000L, redditWriteRetryAfterMs(metadata(), rateLimited = true))
    }

    @Test
    fun aRetryAfterHeaderAlwaysCounts() {
        assertEquals(30_000L, redditWriteRetryAfterMs(metadata(retryAfterMs = 30_000L), rateLimited = false))
    }

    @Test
    fun noWritePathReadsTheRateWindowResetDirectly() {
        val root = requireNotNull(File(requireNotNull(System.getProperty("user.dir"))).parentFile)
        val offenders = File(root, "app/src/main/java").walkTopDown()
            .filter { it.extension == "kt" && it.name != "RedditWriteFailureDetails.kt" }
            .filter { it.readText().contains(Regex("""rateLimit\??\.resetAfterMs""")) }
            .map { it.name }
            .toList()
        assertEquals(emptyList<String>(), offenders)
    }
}
