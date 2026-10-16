package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.account.CommunityMembershipAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RedditCommunityMembershipNetworkPolicyTest {
    @Test
    fun joinUsesSubscribeEndpointAndSuppressesInitialDefaults() {
        val request = redditCommunityMembershipFormRequest(
            communityName = "r/norge",
            action = CommunityMembershipAction.JOIN,
        )

        assertEquals(
            listOf("api", "subscribe"),
            request.pathSegments,
        )
        assertEquals(
            listOf(
                "action" to "sub",
                "sr_name" to "norge",
                "skip_initial_defaults" to "true",
            ),
            request.fields,
        )
    }

    @Test
    fun leaveUsesUnsubAndNeverSendsSkipInitialDefaults() {
        val request = redditCommunityMembershipFormRequest(
            communityName = "android",
            action = CommunityMembershipAction.LEAVE,
        )

        assertEquals(
            listOf("api", "subscribe"),
            request.pathSegments,
        )
        assertEquals(
            listOf(
                "action" to "unsub",
                "sr_name" to "android",
            ),
            request.fields,
        )
        assertFalse(
            request.fields.any {
                it.first == "skip_initial_defaults"
            },
        )
    }
}
