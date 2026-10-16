package app.subshell.prototype.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.chat.redditChatTokenExpiryEpochMs
import app.subshell.prototype.data.account.TextPostWriteRequest
import app.subshell.prototype.data.diagnostics.DiagnosticFailureSnapshot
import app.subshell.prototype.data.drafts.TextPostSubmitAttemptStore
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.redditWriteHttpFailureDetails
import app.subshell.prototype.data.network.NetworkRateLimit
import app.subshell.prototype.data.network.NetworkResponseMetadata
import app.subshell.prototype.data.search.TrendingSearchCache
import app.subshell.prototype.data.search.TrendingSearchSeed
import app.subshell.prototype.data.search.freshTrendingSearchSeeds
import app.subshell.prototype.data.search.normalizeTrendingSearchSeeds
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SMALL1 (low findings in small areas): an empty diagnostic snapshot is none
 * (OCB-529), the dispatch mark is per account (OCB-530), the rate window is a
 * retry-after only for a 429 (OCB-532), a trend cache from the future is stale
 * (OCB-556) and its titles keep whole characters (OCB-641), and the chat token
 * expiry is read in seconds, milliseconds or from the JWT (OCB-600).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LowSmall1Test {
    @Test
    fun aSnapshotWithNothingStoredIsNone() {
        assertNull(DiagnosticFailureSnapshot.from(FailureDetails().withProviderDetails(message = "only a message")))
        assertNotNull(DiagnosticFailureSnapshot.from(FailureDetails(httpStatusCode = 500)))
    }

    @Test
    fun theDispatchMarkMatchesTheAccount() {
        val preferences = ApplicationProvider.getApplicationContext<Application>()
            .getSharedPreferences("small1-attempts", Context.MODE_PRIVATE)
        val store = TextPostSubmitAttemptStore(preferences) { 5_000L }
        fun request(account: String) = TextPostWriteRequest(
            accountStableId = account,
            community = "subshell",
            title = "Same post",
            text = "Same body",
        )
        store.stage(request("reddit:redreader_compatibility:t2_a"))
        store.stage(request("reddit:redreader_compatibility:t2_b"))

        store.markDispatched("reddit:redreader_compatibility:t2_b", request("reddit:redreader_compatibility:t2_b").requestKey)

        assertNull(store.load("reddit:redreader_compatibility:t2_a")?.dispatchedAtEpochMs)
        assertEquals(5_000L, store.load("reddit:redreader_compatibility:t2_b")?.dispatchedAtEpochMs)
    }

    @Test
    fun theRateWindowIsARetryAfterOnlyForA429() {
        val metadata = NetworkResponseMetadata(
            statusCode = 500,
            receivedAtEpochMs = 0L,
            rateLimit = NetworkRateLimit(remaining = 590.0, used = 10.0, resetAfterMs = 420_000L),
            retryAfterMs = null,
        )
        assertNull(redditWriteHttpFailureDetails(500, retryable = true, metadata = metadata).retryAfterMs)
        assertEquals(420_000L, redditWriteHttpFailureDetails(429, retryable = true, metadata = metadata).retryAfterMs)
    }

    @Test
    fun aTrendCacheFromTheFutureIsStaleAndTitlesKeepWholeCharacters() {
        val cache = TrendingSearchCache(fetchedAtEpochMs = 2_000_000L, seeds = listOf(TrendingSearchSeed("x")))
        assertTrue(freshTrendingSearchSeeds(cache, nowEpochMs = 1_000_000L).isEmpty())
        assertFalse(freshTrendingSearchSeeds(cache, nowEpochMs = 2_000_001L).isEmpty())

        val title = "a".repeat(119) + "😀"
        val normalized = normalizeTrendingSearchSeeds(listOf(TrendingSearchSeed(title))).single().title
        assertFalse(normalized.last().isHighSurrogate())
        assertEquals("a".repeat(119), normalized)
    }

    @Test
    fun theChatTokenExpiryIsReadInAnyUnit() {
        assertEquals(1_800_000_000_000L, redditChatTokenExpiryEpochMs(1_800_000_000L, "t"))
        assertEquals(1_800_000_000_123L, redditChatTokenExpiryEpochMs(1_800_000_000_123L, "t"))
        assertEquals(1_800_000_000_000L, redditChatTokenExpiryEpochMs("1800000000", "t"))
        val payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"exp":1800000000}""".toByteArray())
        assertEquals(1_800_000_000_000L, redditChatTokenExpiryEpochMs(null, "h.$payload.s"))
        assertNull(redditChatTokenExpiryEpochMs(null, "not-a-jwt"))
    }
}
