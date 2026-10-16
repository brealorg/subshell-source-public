package app.subshell.prototype.data.account

import app.subshell.prototype.data.RedditCommentSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentEditFlowPolicyTest {
    @Test
    fun requestRequiresCanonicalCommentIdentity() {
        val request = CommentEditRequest(
            accountStableId = "reddit:redreader_compatibility:t2_me",
            postId = "post123",
            commentFullname = "t1_abc123",
            commentAuthor = "brealorg",
            text = "edited",
            sort = RedditCommentSort.BEST,
        )

        assertEquals("t1_abc123", request.commentFullname)
    }

    @Test
    fun ownCommentPolicyAllowsEdit() {
        assertTrue(
            ownContentActions(
                activeUsername = "brealorg",
                author = "u/BRealOrg",
                contentType = OwnContentType.COMMENT,
            ).canEdit,
        )
    }
}
