package io.github.brealorg.subshell.data.auth.reddit

import io.github.brealorg.subshell.data.auth.AuthCapabilityAuthorizationOwner
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
