package app.subshell.prototype.data.account

import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.data.auth.reddit.mergeSubmittedCommentIntoThread
import app.subshell.prototype.model.Comment
import app.subshell.prototype.ui.screens.commentWriteStatusMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentWriteFlowPolicyTest {
    @Test
    fun requestRequiresCanonicalPinnedTextTarget() {
        val request = CommentWriteRequest(
            accountStableId = "reddit:redreader_compatibility:t2_user",
            postId = "t3_post123",
            targetStableKey = "t3_post123|post",
            parentFullname = "t3_post123",
            text = "Comment body",
            sort = RedditCommentSort.NEW,
        )

        assertEquals("t3_post123", request.parentFullname)
        assertThrows(IllegalArgumentException::class.java) {
            CommentWriteRequest(
                accountStableId = request.accountStableId,
                postId = request.postId,
                targetStableKey = request.targetStableKey,
                parentFullname = "T3_POST123",
                text = request.text,
                sort = request.sort,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            request.copy(text = "   ", media = null)
        }
    }

    @Test
    fun mediaOnlyRequestIsValidAndUnsupportedMediaIsRejected() {
        val mediaOnly = CommentWriteRequest(
            accountStableId = "reddit:redreader_compatibility:t2_user",
            postId = "t3_post123",
            targetStableKey = "t3_post123|post",
            parentFullname = "t3_post123",
            text = "   ",
            sort = RedditCommentSort.NEW,
            media = CommentWriteMediaAttachment(
                uri = "content://media/external/images/42",
                mimeType = "image/gif",
            ),
        )
        assertEquals("image/gif", mediaOnly.media?.mimeType)

        assertThrows(IllegalArgumentException::class.java) {
            mediaOnly.copy(
                media = CommentWriteMediaAttachment(
                    uri = "content://media/external/video/42",
                    mimeType = "video/mp4",
                ),
            )
        }
    }

    @Test
    fun submittedCommentMergesIntoTopLevelAndNestedRoomThreads() {
        val parent = comment(
            id = "t1_parent",
            body = "Parent",
            depth = 0,
            parentId = "t3_post123",
        )
        val topLevel = comment(
            id = "t1_top",
            body = "Top",
            depth = 0,
            parentId = "t3_post123",
        )
        val submittedReply = comment(
            id = "t1_created",
            body = "Created reply",
            depth = 1,
            parentId = "t1_parent",
        )

        val nested = requireNotNull(
            mergeSubmittedCommentIntoThread(
                current = listOf(parent, topLevel),
                parentFullname = "t1_parent",
                submitted = submittedReply,
            ),
        )
        assertEquals("t1_created", nested.first().children.first().id)
        assertEquals(1, nested.first().children.first().depth)

        val submittedTopLevel = submittedReply.copy(
            id = "t1_createdtop",
            depth = 4,
            parentId = "t3_post123",
        )
        val top = requireNotNull(
            mergeSubmittedCommentIntoThread(
                current = nested,
                parentFullname = "t3_post123",
                submitted = submittedTopLevel,
            ),
        )
        assertEquals("t1_createdtop", top.first().id)
        assertEquals(0, top.first().depth)
        assertFalse(top.drop(1).any { it.id == "t1_createdtop" })
    }

    @Test
    fun outcomeUnknownMessageRequiresManualDuplicateReview() {
        val state = CommentWriteUiState(
            phase = CommentWritePhase.OUTCOME_UNKNOWN,
            accountStableId = "reddit:redreader_compatibility:t2_user",
            postId = "t3_post123",
            targetStableKey = "t3_post123|post",
            failureKind = CommentWriteFailureKind.API,
        )

        val message = commentWriteStatusMessage(state)
        assertNotNull(message)
        assertTrue(message!!.contains("may have received"))
        assertTrue(message.contains("avoid a duplicate"))
        assertFalse(state.retryable)
    }

    private fun comment(
        id: String,
        body: String,
        depth: Int,
        parentId: String,
    ): Comment = Comment(
        id = id,
        author = "write01a3b_user",
        age = "0m",
        body = body,
        score = 1,
        depth = depth,
        accent = 0xFFFF8A50L,
        parentId = parentId,
    )
}
