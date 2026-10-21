package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.auth.AuthCapabilityAuthorizationOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Wiki01A1AuthorizationPolicyTest {
    @Test
    fun wikiReadHasItsOwnCapabilityAndSignInAsksForIt() {
        assertEquals(
            RedditOAuth.coreReadScopes + "wikiread",
            RedditOAuth.liveCommunityWikiReadScopes,
        )
        assertEquals(
            RedditOAuth.liveCommunityWikiReadScopes,
            RedditOAuth.scopesFor(RedditOAuthCapability.COMMUNITY_WIKI_READ),
        )
        assertFalse("wikiread" in RedditOAuth.coreReadScopes)
        // ISSUE237: sign-in asks for it too; the capability owner stays the fallback.
        assertTrue("wikiread" in RedditOAuth.dailyDriverScopes)
    }

    @Test
    fun communityWikiReadHasStablePersistedAuthorizationOwner() {
        val owner = AuthCapabilityAuthorizationOwner.COMMUNITY_WIKI_READ
        assertEquals("community_wiki_read", owner.wireKey)
        assertEquals(
            owner,
            AuthCapabilityAuthorizationOwner.fromWireKey(owner.wireKey),
        )
    }
}
