package app.subshell.prototype.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Messages02C6WebTokenCachePolicyTest {
    @Test
    fun cacheRequiresSameRedditSessionAndSafeDistanceFromExpiry() {
        val session = requireNotNull(redditChatSessionFingerprint("foo=1; reddit_session=abc; csrf_token=x"))
        val cached = RedditChatCachedWebToken(
            token = "jwt",
            expiresAtEpochMs = 10_000_000L,
            redditSessionFingerprint = session,
        )
        assertTrue(
            redditChatWebTokenCacheUsable(
                cached = cached,
                redditSessionFingerprint = session,
                nowEpochMs = 1_000_000L,
                expirySkewMs = 300_000L,
            ),
        )
        assertFalse(
            redditChatWebTokenCacheUsable(
                cached = cached,
                redditSessionFingerprint = session,
                nowEpochMs = 9_800_000L,
                expirySkewMs = 300_000L,
            ),
        )
    }

    @Test
    fun sessionFingerprintChangesWhenRedditSessionChangesAndIgnoresOtherCookies() {
        val first = redditChatSessionFingerprint("loid=x; reddit_session=abc; csrf_token=one")
        val sameSession = redditChatSessionFingerprint("csrf_token=two; reddit_session=abc; loid=y")
        val changed = redditChatSessionFingerprint("reddit_session=def; csrf_token=two")
        assertNotNull(first)
        assertTrue(first == sameSession)
        assertNotEquals(first, changed)
        assertFalse(
            redditChatWebTokenCacheUsable(
                cached = RedditChatCachedWebToken("jwt", 20_000_000L, requireNotNull(first)),
                redditSessionFingerprint = changed,
                nowEpochMs = 1L,
                expirySkewMs = 0L,
            ),
        )
    }
}
