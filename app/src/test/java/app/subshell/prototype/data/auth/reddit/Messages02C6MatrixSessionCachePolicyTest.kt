package app.subshell.prototype.data.auth.reddit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Messages02C6MatrixSessionCachePolicyTest {
    @Test
    fun identityAndRoomAreScopedToTokenAccountAndParticipant() {
        var now = 1_000L
        val cache = RedditChatMatrixSessionCache(nowEpochMs = { now }, roomTtlMs = 5_000L)
        cache.rememberIdentity("token-a", "jlmpi0", "@t2_jlmpi0:reddit.com")
        cache.rememberRoom(
            token = "token-a",
            providerAccountId = "jlmpi0",
            participantUsername = "Subshell_Dev",
            roomId = "!room:reddit.com",
            tokenExpiresAtEpochMs = 500_000L,
        )

        assertEquals("@t2_jlmpi0:reddit.com", cache.identityFor("token-a", "jlmpi0"))
        assertEquals("!room:reddit.com", cache.roomFor("token-a", "jlmpi0", "subshell_dev"))
        assertNull(cache.roomFor("token-a", "other", "subshell_dev"))
        assertNull(cache.roomFor("token-a", "jlmpi0", "someone_else"))
        assertNull(cache.identityFor("token-b", "jlmpi0"))

        now = 6_001L
        assertNull(cache.roomFor("token-a", "jlmpi0", "subshell_dev"))
    }

    @Test
    fun roomLifetimeIsBoundedByTokenExpirySkew() {
        var now = 1_000L
        val cache = RedditChatMatrixSessionCache(nowEpochMs = { now }, roomTtlMs = 60_000L)
        cache.rememberRoom(
            token = "token-a",
            providerAccountId = "jlmpi0",
            participantUsername = "subshell_dev",
            roomId = "!room:reddit.com",
            tokenExpiresAtEpochMs = 302_000L,
        )
        assertEquals("!room:reddit.com", cache.roomFor("token-a", "jlmpi0", "subshell_dev"))
        now = 2_000L
        assertNull(cache.roomFor("token-a", "jlmpi0", "subshell_dev"))
    }

    @Test
    fun invalidatingFailedTokenClearsOnlyThatTokenAndNeverStoresBearer() {
        val cache = RedditChatMatrixSessionCache(nowEpochMs = { 1_000L })
        cache.rememberIdentity("secret-token-a", "jlmpi0", "@t2_jlmpi0:reddit.com")
        cache.rememberRoom(
            token = "secret-token-a",
            providerAccountId = "jlmpi0",
            participantUsername = "subshell_dev",
            roomId = "!room:reddit.com",
            tokenExpiresAtEpochMs = 50_000L,
        )
        cache.invalidateToken("other-token")
        assertEquals("@t2_jlmpi0:reddit.com", cache.identityFor("secret-token-a", "jlmpi0"))
        cache.invalidateToken("secret-token-a")
        assertNull(cache.identityFor("secret-token-a", "jlmpi0"))
        assertNull(cache.roomFor("secret-token-a", "jlmpi0", "subshell_dev"))
        assertTrue(redditChatMatrixTokenFingerprint("secret-token-a").length == 64)
    }
}
