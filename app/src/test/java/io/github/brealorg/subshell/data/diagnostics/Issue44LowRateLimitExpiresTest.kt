package io.github.brealorg.subshell.data.diagnostics

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Issue44LowRateLimitExpiresTest {
    @After
    fun reset() = RedditReadDiagnostics.installForTest(null)

    @Test
    fun aLowReadingStopsCountingAtItsResetTime() {
        RedditReadDiagnostics.installForTest(null)
        // 9 left, window resets in 120 s, seen at t = 1 000 000.
        RedditReadDiagnostics.observeRateLimit(9.0, 291.0, 120_000L, 1_000_000L)

        assertTrue(RedditReadDiagnostics.rateLimitIsLow(nowEpochMs = 1_060_000L))
        assertFalse(RedditReadDiagnostics.rateLimitIsLow(nowEpochMs = 1_120_000L))
        assertFalse(RedditReadDiagnostics.rateLimitIsLow(nowEpochMs = 5_000_000L))
    }

    @Test
    fun aLowReadingWithoutAResetTimeExpiresAfterAMinute() {
        RedditReadDiagnostics.installForTest(null)
        RedditReadDiagnostics.observeRateLimit(2.0, null, null, 1_000_000L)

        assertTrue(RedditReadDiagnostics.rateLimitIsLow(nowEpochMs = 1_059_000L))
        assertFalse(RedditReadDiagnostics.rateLimitIsLow(nowEpochMs = 1_060_000L))
    }
}
