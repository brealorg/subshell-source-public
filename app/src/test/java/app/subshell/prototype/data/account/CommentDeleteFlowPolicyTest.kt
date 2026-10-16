package app.subshell.prototype.data.account

import app.subshell.prototype.data.RedditCommentSort
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class CommentDeleteFlowPolicyTest {
    @Test
    fun deleteRequestRequiresCanonicalCommentFullname() {
        val request = CommentDeleteRequest(
            accountStableId = "reddit:rr:user",
            postId = "post",
            commentFullname = "t1_abc123",
            commentAuthor = "brealorg",
            sort = RedditCommentSort.BEST,
        )

        assertEquals("t1_abc123", request.commentFullname)

        try {
            request.copy(commentFullname = "abc123")
            fail("Expected non-canonical comment fullname to be rejected")
        } catch (_: IllegalArgumentException) {
        }
    }
}
