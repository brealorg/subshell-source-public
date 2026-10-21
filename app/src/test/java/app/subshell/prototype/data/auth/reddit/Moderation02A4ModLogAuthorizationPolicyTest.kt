package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.auth.AuthCapabilityAuthorizationOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Moderation02A4ModLogAuthorizationPolicyTest {
    @Test
    fun modLogUsesDedicatedIncrementalScopeAndOwner() {
        assertEquals(
            RedditOAuth.coreReadScopes + "modlog",
            RedditOAuth.scopesFor(RedditOAuthCapability.MODERATION_LOG),
        )
        // ISSUE237: sign-in asks for it too; the capability owner stays the fallback.
        assertTrue(RedditOAuth.dailyDriverScopes.contains("modlog"))
        assertEquals(
            "moderation_log",
            AuthCapabilityAuthorizationOwner.MODERATION_LOG.wireKey,
        )
        assertEquals(
            AuthCapabilityAuthorizationOwner.MODERATION_LOG,
            AuthCapabilityAuthorizationOwner.fromWireKey("moderation_log"),
        )
    }

    @Test
    fun existingPostModerationScopeRemainsSeparate() {
        assertTrue("modposts" in RedditOAuth.livePostModerationScopes)
        assertFalse("modlog" in RedditOAuth.livePostModerationScopes)
    }
}
