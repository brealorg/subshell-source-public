package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.auth.AuthCapabilityAuthorizationOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class Moderation02A5A2AuthorizationPolicyTest {
    @Test
    fun mutedUsersReuseIncrementalModcontributorsOwner() {
        assertEquals(
            RedditOAuth.coreReadScopes + "modcontributors",
            RedditOAuth.scopesFor(RedditOAuthCapability.MODERATION_USERS),
        )
        assertFalse(RedditOAuth.dailyDriverScopes.contains("modcontributors"))
        assertEquals("moderation_users", AuthCapabilityAuthorizationOwner.MODERATION_USERS.wireKey)
    }
}
