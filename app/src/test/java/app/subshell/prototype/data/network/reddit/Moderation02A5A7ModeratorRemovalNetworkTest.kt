package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.account.RedditModeratorRemovalRequest
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Moderation02A5A7ModeratorRemovalNetworkTest {
    @Test
    fun removalUsesCanonicalSubredditUnfriendModeratorForm() {
        val form = redditModeratorRemovalFormRequest(
            RedditModeratorRemovalRequest(
                accountStableId = "acct",
                communityName = "subshell",
                username = "target_user",
            ),
        )

        assertEquals(listOf("r", "subshell", "api", "unfriend"), form.pathSegments)
        assertEquals(
            mapOf(
                "name" to "target_user",
                "type" to "moderator",
            ),
            form.fields.toMap(),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun removalRequiresCanonicalCommunity() {
        RedditModeratorRemovalRequest(
            accountStableId = "acct",
            communityName = "SubShell",
            username = "target_user",
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun removalRequiresCanonicalUsername() {
        RedditModeratorRemovalRequest(
            accountStableId = "acct",
            communityName = "subshell",
            username = "u/target_user",
        )
    }

    @Test
    fun transportIsStatusOnlyAndUnknownOutcomeIsNotCollapsedToHttpFailure() {
        val source = File(
            "src/main/java/app/subshell/prototype/data/network/reddit/" +
                "RedditModeratorRemovalNetworkDataSource.kt",
        ).readText()

        assertTrue(source.contains("postFormStatus("))
        assertTrue(source.contains("ModeratorRemovalFailureKind.OUTCOME_UNKNOWN"))
        assertTrue(source.contains("result.statusCode == 401 || result.statusCode == 403"))
        assertTrue(source.contains("result.statusCode in 500..599"))
        assertTrue(source.contains("failureDetailsOrNull()"))
    }
}
