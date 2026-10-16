package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.auth.AuthCapabilityAuthorizationOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Safety01A2AuthorizationPolicyTest {
    @Test
    fun contentSafetyUsesReportHistoryAndUserBlockScopesWithoutModeration() {
        assertEquals(
            RedditOAuth.coreReadScopes + setOf(
                "account",
                "history",
                "privatemessages",
                "report",
            ),
            RedditOAuth.liveContentSafetyScopes,
        )
        assertEquals(
            RedditOAuth.liveContentSafetyScopes,
            RedditOAuth.scopesFor(RedditOAuthCapability.CONTENT_SAFETY),
        )
        assertTrue(RedditOAuth.dailyDriverScopes.contains("account"))
        assertTrue(RedditOAuth.dailyDriverScopes.contains("report"))
        assertTrue(RedditOAuth.dailyDriverScopes.contains("history"))
        assertTrue(RedditOAuth.dailyDriverScopes.contains("privatemessages"))
        assertTrue(
            RedditOAuth.dailyDriverScopes.containsAll(
                RedditOAuth.liveContentSafetyScopes,
            ),
        )
    }

    @Test
    fun contentSafetyHasItsOwnPersistedAuthorizationOwner() {
        val owner = AuthCapabilityAuthorizationOwner.CONTENT_SAFETY
        assertEquals("content_safety", owner.wireKey)
        assertEquals(
            owner,
            AuthCapabilityAuthorizationOwner.fromWireKey(owner.wireKey),
        )
    }
}
