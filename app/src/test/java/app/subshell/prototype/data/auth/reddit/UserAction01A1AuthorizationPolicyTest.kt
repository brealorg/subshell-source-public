package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.auth.AuthCapabilityAuthorizationOwner
import org.junit.Assert.assertTrue
import org.junit.Test

class UserAction01A1AuthorizationPolicyTest {
    @Test
    fun contentSafetyOwnerCarriesBothBlockAndEnemyRemovalScopes() {
        val scopes = RedditOAuth.scopesFor(RedditOAuthCapability.CONTENT_SAFETY)
        assertTrue("account" in scopes)
        assertTrue("privatemessages" in scopes)
        assertTrue("report" in scopes)
        assertTrue("account" in RedditOAuth.dailyDriverScopes)
        assertTrue(
            AuthCapabilityAuthorizationOwner.CONTENT_SAFETY.wireKey ==
                "content_safety",
        )
    }
}
