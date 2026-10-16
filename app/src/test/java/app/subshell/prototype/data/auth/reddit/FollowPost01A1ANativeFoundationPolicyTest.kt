package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.account.PostFollowRequest
import app.subshell.prototype.data.account.normalizePostFollowFullname
import app.subshell.prototype.data.account.normalizedPostFollowRequest
import app.subshell.prototype.data.network.reddit.redditPostFollowFormRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowPost01A1ANativeFoundationPolicyTest {
    @Test
    fun followIdentityIsCanonicalPostFullnameOnly() {
        assertEquals("t3_abc123", normalizePostFollowFullname("AbC123"))
        assertEquals("t3_abc123", normalizePostFollowFullname("t3_AbC123"))

        assertThrows(IllegalArgumentException::class.java) {
            normalizePostFollowFullname("t1_comment")
        }
    }

    @Test
    fun requestPinsOwnerAndAbsoluteDesiredState() {
        assertEquals(
            PostFollowRequest(
                accountStableId = "owner",
                postFullname = "t3_abc123",
                followed = true,
            ),
            PostFollowRequest(
                accountStableId = " owner ",
                postFullname = "ABC123",
                followed = true,
            ).normalizedPostFollowRequest(),
        )
    }

    @Test
    fun transportUsesDocumentedFollowPostFormForBothAbsoluteStates() {
        assertEquals(
            listOf("api", "follow_post"),
            redditPostFollowFormRequest("ABC123", true).pathSegments,
        )
        assertEquals(
            listOf(
                "follow" to "true",
                "fullname" to "t3_abc123",
            ),
            redditPostFollowFormRequest("ABC123", true).fields,
        )
        assertEquals(
            listOf(
                "follow" to "false",
                "fullname" to "t3_abc123",
            ),
            redditPostFollowFormRequest("t3_ABC123", false).fields,
        )
    }

    @Test
    fun postFollowOwnsSubscribeCapabilityWithoutWideningDailyDriverGrant() {
        assertEquals(
            RedditOAuth.coreReadScopes + "subscribe",
            RedditOAuth.livePostFollowScopes,
        )
        assertEquals(
            RedditOAuth.livePostFollowScopes,
            RedditOAuth.scopesFor(RedditOAuthCapability.POST_FOLLOW),
        )
        assertTrue("subscribe" in RedditOAuth.dailyDriverScopes)
    }
}
