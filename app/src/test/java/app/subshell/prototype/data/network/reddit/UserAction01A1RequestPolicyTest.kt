package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.account.RedditUserBlockRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserAction01A1RequestPolicyTest {
    @Test
    fun blockAndUnblockUseCanonicalRedditAccountRelationshipRequests() {
        val block = redditUserBlockFormRequest(
            request = RedditUserBlockRequest("acct", "target_user", blocked = true),
            currentUserFullname = "t2_current1",
        )
        assertEquals(listOf("api", "block_user"), block.pathSegments)
        assertEquals(
            mapOf("api_type" to "json", "name" to "target_user"),
            block.fields.toMap(),
        )

        val unblock = redditUserBlockFormRequest(
            request = RedditUserBlockRequest("acct", "target_user", blocked = false),
            currentUserFullname = "current1",
        )
        assertEquals(listOf("api", "unfriend"), unblock.pathSegments)
        assertEquals("t2_current1", unblock.fields.toMap()["container"])
        assertEquals("target_user", unblock.fields.toMap()["name"])
        assertEquals("enemy", unblock.fields.toMap()["type"])
    }

    @Test
    fun userBlockBoundaryNormalizesAndRejectsUnsupportedTargets() {
        assertEquals(
            "mixed_case",
            app.subshell.prototype.data.account.normalizeRedditSafetyUsername(
                " u/Mixed_Case ",
            ),
        )
        assertTrue(
            runCatching {
                RedditUserBlockRequest("acct", "bad user", blocked = true)
            }.isFailure,
        )
    }
}
