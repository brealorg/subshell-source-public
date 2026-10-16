package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.account.RedditModeratorAction
import app.subshell.prototype.data.account.RedditModeratorActionRequest
import org.junit.Assert.assertEquals
import org.junit.Test

class Moderation02A3ContentControlNetworkTest {
    private fun request(action: RedditModeratorAction, fullname: String = "t3_abc123") =
        RedditModeratorActionRequest(
            accountStableId = "acct",
            communityName = "subshell",
            thingFullname = fullname,
            action = action,
        )

    @Test
    fun lockAndUnlockUseExactThingFullname() {
        listOf("t3_abc123", "t1_def456").forEach { fullname ->
            val lock = redditModerationActionRequest(request(RedditModeratorAction.LOCK, fullname))
            assertEquals(listOf("api", "lock"), lock.pathSegments)
            assertEquals(listOf("id" to fullname), lock.fields)

            val unlock = redditModerationActionRequest(request(RedditModeratorAction.UNLOCK, fullname))
            assertEquals(listOf("api", "unlock"), unlock.pathSegments)
            assertEquals(listOf("id" to fullname), unlock.fields)
        }
    }

    @Test
    fun distinguishAndUndistinguishUseCanonicalHowValues() {
        listOf("t3_abc123", "t1_def456").forEach { fullname ->
            val distinguish = redditModerationActionRequest(
                request(RedditModeratorAction.DISTINGUISH, fullname),
            )
            assertEquals(listOf("api", "distinguish"), distinguish.pathSegments)
            assertEquals(
                listOf("api_type" to "json", "how" to "yes", "id" to fullname),
                distinguish.fields,
            )

            val undo = redditModerationActionRequest(
                request(RedditModeratorAction.UNDISTINGUISH, fullname),
            )
            assertEquals(listOf("api", "distinguish"), undo.pathSegments)
            assertEquals(
                listOf("api_type" to "json", "how" to "no", "id" to fullname),
                undo.fields,
            )
        }
    }

    @Test
    fun predecessorFormsRemainUnchanged() {
        assertEquals(
            listOf("api", "approve"),
            redditModerationActionRequest(request(RedditModeratorAction.APPROVE)).pathSegments,
        )
        assertEquals(
            listOf("id" to "t3_abc123", "spam" to "false"),
            redditModerationActionRequest(request(RedditModeratorAction.REMOVE)).fields,
        )
        assertEquals(
            listOf("id" to "t3_abc123", "spam" to "true"),
            redditModerationActionRequest(request(RedditModeratorAction.SPAM)).fields,
        )
        assertEquals(
            listOf("api", "ignore_reports"),
            redditModerationActionRequest(request(RedditModeratorAction.IGNORE_REPORTS)).pathSegments,
        )
        assertEquals(
            listOf("api", "unignore_reports"),
            redditModerationActionRequest(request(RedditModeratorAction.UNIGNORE_REPORTS)).pathSegments,
        )
    }
}
