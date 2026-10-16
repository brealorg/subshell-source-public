package app.subshell.prototype.auth

import app.subshell.prototype.data.auth.AuthCapabilityAuthorizationOwner
import app.subshell.prototype.data.auth.reddit.RedditOAuth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Mod01A2ModerationAuthorizationPolicyTest {
    @Test
    fun moderationActionsHaveDedicatedOwnerAndReuseModpostsScope() {
        val owner = AuthCapabilityAuthorizationOwner.MODERATION_ACTION
        assertEquals("moderation_action", owner.wireKey)
        assertEquals(owner, AuthCapabilityAuthorizationOwner.fromWireKey(owner.wireKey))
        assertTrue("modposts" in RedditOAuth.livePostModerationScopes)
    }
}
