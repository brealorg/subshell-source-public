package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.auth.AuthCapabilityAuthorizationOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class Wiki01A1AuthorizationPolicyTest {
    @Test
    fun wikiReadIsIncrementalAndDoesNotWidenDailyDriverGrant() {
        assertEquals(
            RedditOAuth.coreReadScopes + "wikiread",
            RedditOAuth.liveCommunityWikiReadScopes,
        )
        assertEquals(
            RedditOAuth.liveCommunityWikiReadScopes,
            RedditOAuth.scopesFor(RedditOAuthCapability.COMMUNITY_WIKI_READ),
        )
        assertFalse("wikiread" in RedditOAuth.coreReadScopes)
        assertFalse("wikiread" in RedditOAuth.dailyDriverScopes)
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
