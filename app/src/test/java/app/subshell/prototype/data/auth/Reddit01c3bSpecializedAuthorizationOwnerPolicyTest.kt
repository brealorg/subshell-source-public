package app.subshell.prototype.data.auth

import app.subshell.prototype.data.auth.reddit.RedditOAuth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class Reddit01c3bSpecializedAuthorizationOwnerPolicyTest {
    @Test
    fun specializedAuthorizationOwnersHaveStableDistinctWireKeys() {
        val inbox = AuthCapabilityAuthorizationOwner.INBOX_READ
        val customFeed = AuthCapabilityAuthorizationOwner.CUSTOM_FEED_WRITE

        assertEquals("inbox_read", inbox.wireKey)
        assertEquals("custom_feed_write", customFeed.wireKey)
        assertFalse(inbox.wireKey == customFeed.wireKey)
        assertEquals(inbox, AuthCapabilityAuthorizationOwner.fromWireKey(inbox.wireKey))
        assertEquals(
            customFeed,
            AuthCapabilityAuthorizationOwner.fromWireKey(customFeed.wireKey),
        )
    }

    @Test
    fun specializedScopeSetsRemainDistinct() {
        assertEquals(
            RedditOAuth.coreReadScopes + "privatemessages",
            RedditOAuth.liveInboxReadScopes,
        )
        assertEquals(
            RedditOAuth.coreReadScopes + "subscribe",
            RedditOAuth.liveCustomFeedWriteScopes,
        )
        assertFalse(
            RedditOAuth.liveInboxReadScopes ==
                RedditOAuth.liveCustomFeedWriteScopes,
        )
    }
}
