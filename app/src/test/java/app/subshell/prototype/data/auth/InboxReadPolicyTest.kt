package app.subshell.prototype.data.auth

import app.subshell.prototype.data.auth.reddit.RedditOAuth
import app.subshell.prototype.data.auth.reddit.RedditOAuthCapability
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InboxReadPolicyTest {
    @Test
    fun inboxScopeIsExplicitWithoutWideningBaseReadOnlyGrant() {
        assertTrue(RedditOAuth.liveInboxReadScopes.containsAll(RedditOAuth.coreReadScopes))
        assertTrue("privatemessages" in RedditOAuth.liveInboxReadScopes)
        assertFalse("privatemessages" in RedditOAuth.coreReadScopes)
        assertFalse("privatemessages" in RedditOAuth.liveCustomFeedWriteScopes)
    }

    @Test
    fun capabilityUpgradePreservesPreviouslyGrantedOptionalScopes() {
        val storedSelection = RedditOAuth.redReaderCompatibilitySelection("client-id")

        val inbox = requireNotNull(
            RedditOAuth.selectRuntimeConfiguration(
                storedSelection = storedSelection,
                nativeConfigured = false,
                nativeClientId = "",
                nativeRedirectUri = "",
                nativeUserAgent = "unused",
                capability = RedditOAuthCapability.INBOX_READ,
                preservedScopes = setOf("subscribe"),
            ),
        )
        assertTrue("privatemessages" in inbox.configuration.scopes)
        assertTrue("subscribe" in inbox.configuration.scopes)

        val customFeed = requireNotNull(
            RedditOAuth.selectRuntimeConfiguration(
                storedSelection = storedSelection,
                nativeConfigured = false,
                nativeClientId = "",
                nativeRedirectUri = "",
                nativeUserAgent = "unused",
                capability = RedditOAuthCapability.CUSTOM_FEED_WRITE,
                preservedScopes = setOf("privatemessages"),
            ),
        )
        assertTrue("subscribe" in customFeed.configuration.scopes)
        assertTrue("privatemessages" in customFeed.configuration.scopes)
    }
}
