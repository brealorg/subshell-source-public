package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.account.RedditModeratorUserApprovalRequest
import org.junit.Assert.assertEquals
import org.junit.Test

class Moderation02A5A3ApprovedUserNetworkTest {
    @Test
    fun approvedListUsesCanonicalContributorRelationshipEndpointAndOpaqueCursor() {
        val request = redditApprovedUsersRequest("SubShell", "t2_AbC-123")
        assertEquals(
            listOf("r", "subshell", "about", "contributors"),
            request.pathSegments,
        )
        assertEquals(
            listOf("limit" to "100", "raw_json" to "1", "after" to "t2_AbC-123"),
            request.queryParameters,
        )
    }

    @Test
    fun approveAndUnapproveUseContributorFriendRelationshipForms() {
        val approve = redditModeratorApproveUserFormRequest(request(true))
        assertEquals(listOf("r", "subshell", "api", "friend"), approve.pathSegments)
        assertEquals(
            listOf(
                "api_type" to "json",
                "name" to "target_user",
                "type" to "contributor",
            ),
            approve.fields,
        )

        val unapprove = redditModeratorUnapproveUserFormRequest(request(false))
        assertEquals(listOf("r", "subshell", "api", "unfriend"), unapprove.pathSegments)
        assertEquals(
            listOf(
                "api_type" to "json",
                "name" to "target_user",
                "type" to "contributor",
            ),
            unapprove.fields,
        )
    }

    private fun request(approved: Boolean) = RedditModeratorUserApprovalRequest(
        accountStableId = "acct",
        communityName = "subshell",
        username = "target_user",
        approved = approved,
    )
}
