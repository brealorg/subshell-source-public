package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.auth.AuthCapabilityAuthorizationOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Moderation02A5A2AuthorizationPolicyTest {
    @Test
    fun mutedUsersReuseIncrementalModcontributorsOwner() {
        assertEquals(
            RedditOAuth.coreReadScopes + "modcontributors",
            RedditOAuth.scopesFor(RedditOAuthCapability.MODERATION_USERS),
        )
        // ISSUE237: sign-in asks for it too; the capability owner stays the fallback.
        assertTrue(RedditOAuth.dailyDriverScopes.contains("modcontributors"))
        assertEquals("moderation_users", AuthCapabilityAuthorizationOwner.MODERATION_USERS.wireKey)
    }
}
