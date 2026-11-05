package io.github.brealorg.subshell.auth

import io.github.brealorg.subshell.data.auth.AuthCapabilityAuthorizationOwner
import io.github.brealorg.subshell.data.auth.reddit.RedditOAuth
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
