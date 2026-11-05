package io.github.brealorg.subshell.data.network.reddit

import io.github.brealorg.subshell.data.account.RedditModeratorAction
import io.github.brealorg.subshell.data.account.RedditModeratorActionRequest
import org.junit.Assert.assertEquals
import org.junit.Test

class Mod01A2ModerationActionNetworkPolicyTest {
    @Test
    fun approveUsesCanonicalClassicEndpointForPostsAndComments() {
        listOf("t3_abc123", "t1_def456").forEach { fullname ->
            val request = redditModerationActionRequest(
                RedditModeratorActionRequest(
                    accountStableId = "acct",
                    communityName = "subshell",
                    thingFullname = fullname,
                    action = RedditModeratorAction.APPROVE,
                ),
            )
            assertEquals(listOf("api", "approve"), request.pathSegments)
            assertEquals(listOf("id" to fullname), request.fields)
        }
    }

    @Test
    fun removeUsesCanonicalEndpointAndDoesNotClassifySpamInA2() {
        val request = redditModerationActionRequest(
            RedditModeratorActionRequest(
                accountStableId = "acct",
                communityName = "subshell",
                thingFullname = "t3_abc123",
                action = RedditModeratorAction.REMOVE,
            ),
        )
        assertEquals(listOf("api", "remove"), request.pathSegments)
        assertEquals(
            listOf("id" to "t3_abc123", "spam" to "false"),
            request.fields,
        )
    }
}
