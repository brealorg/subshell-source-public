package app.subshell.prototype.auth

import app.subshell.prototype.data.auth.AuthCapabilityAuthorizationOwner
import app.subshell.prototype.data.auth.reddit.RedditOAuth
import app.subshell.prototype.data.auth.reddit.RedditOAuthCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Modmail01A1AuthorizationPolicyTest {
    @Test
    fun modmailUsesDedicatedIncrementalOwnerAndScope() {
        val owner = AuthCapabilityAuthorizationOwner.MODMAIL_READ
        assertEquals("modmail_read", owner.wireKey)
        assertEquals(owner, AuthCapabilityAuthorizationOwner.fromWireKey(owner.wireKey))
        assertEquals(
            RedditOAuth.coreReadScopes + "modmail",
            RedditOAuth.liveModmailReadScopes,
        )
        assertTrue(
            RedditOAuth.scopesFor(RedditOAuthCapability.MODMAIL_READ)
                .contains("modmail"),
        )
        assertFalse(RedditOAuth.dailyDriverScopes.contains("modmail"))
    }

}
