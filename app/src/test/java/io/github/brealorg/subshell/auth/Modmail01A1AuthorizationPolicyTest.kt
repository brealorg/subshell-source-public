package io.github.brealorg.subshell.auth

import io.github.brealorg.subshell.data.auth.AuthCapabilityAuthorizationOwner
import io.github.brealorg.subshell.data.auth.reddit.RedditOAuth
import io.github.brealorg.subshell.data.auth.reddit.RedditOAuthCapability
import org.junit.Assert.assertEquals
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
        // ISSUE237: sign-in asks for it too; the capability owner stays the fallback.
        assertTrue(RedditOAuth.dailyDriverScopes.contains("modmail"))
    }

}
