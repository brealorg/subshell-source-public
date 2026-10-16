package app.subshell.prototype.auth

import app.subshell.prototype.model.Comment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Messages02C4R3CommentMediaReadbackPolicyTest {
    @Test
    fun freshMatchingMediaCommentWinsAuthoritativeReadback() {
        val comments = listOf(
            comment(
                id = "t1_old",
                author = "brealorg",
                parentId = "t3_post123",
                body = "Mohahahah\n\nhttps://i.redd.it/old.gif",
                created = 900L,
            ),
            comment(
                id = "t1_new",
                author = "brealorg",
                parentId = "t3_post123",
                body = "Mohahahah\n\n*Processing img abc123...*\n\nhttps://i.redd.it/abc123.gif",
                created = 1_005L,
            ),
        )

        val matched = findAuthoritativeMediaCommentAfterUnknownOutcome(
            comments = comments,
            username = "brealorg",
            parentFullname = "t3_post123",
            submittedText = "Mohahahah",
            mediaMimeType = "image/gif",
            earliestCreatedEpochSeconds = 1_000L,
        )

        assertEquals("t1_new", matched?.id)
    }

    @Test
    fun mediaOnlyGifCanReconcileFromProcessingMarker() {
        val comments = listOf(
            comment(
                id = "t1_media",
                author = "brealorg",
                parentId = "t1_parent",
                body = "*Processing img 8lurvpf69nlh1...*",
                created = 2_000L,
            ),
        )

        val matched = findAuthoritativeMediaCommentAfterUnknownOutcome(
            comments = comments,
            username = "BREALORG",
            parentFullname = "t1_parent",
            submittedText = "   ",
            mediaMimeType = "image/gif",
            earliestCreatedEpochSeconds = 1_995L,
        )

        assertEquals("t1_media", matched?.id)
    }

    @Test
    fun nestedReplyIsEligibleButWrongAuthorParentOrOldDuplicateIsNot() {
        val nested = comment(
            id = "t1_parent",
            author = "someone",
            parentId = "t3_post123",
            body = "Parent",
            created = 2_000L,
            children = listOf(
                comment(
                    id = "t1_reply",
                    author = "brealorg",
                    parentId = "t1_parent",
                    body = "Reply text\n\nhttps://i.redd.it/reply.png",
                    created = 2_010L,
                ),
            ),
        )

        val matched = findAuthoritativeMediaCommentAfterUnknownOutcome(
            comments = listOf(nested),
            username = "brealorg",
            parentFullname = "t1_parent",
            submittedText = "Reply text",
            mediaMimeType = "image/png",
            earliestCreatedEpochSeconds = 2_005L,
        )
        assertEquals("t1_reply", matched?.id)

        assertNull(
            findAuthoritativeMediaCommentAfterUnknownOutcome(
                comments = listOf(nested),
                username = "other_user",
                parentFullname = "t1_parent",
                submittedText = "Reply text",
                mediaMimeType = "image/png",
                earliestCreatedEpochSeconds = 2_005L,
            ),
        )
        assertNull(
            findAuthoritativeMediaCommentAfterUnknownOutcome(
                comments = listOf(nested),
                username = "brealorg",
                parentFullname = "t1_other",
                submittedText = "Reply text",
                mediaMimeType = "image/png",
                earliestCreatedEpochSeconds = 2_005L,
            ),
        )
        assertNull(
            findAuthoritativeMediaCommentAfterUnknownOutcome(
                comments = listOf(nested),
                username = "brealorg",
                parentFullname = "t1_parent",
                submittedText = "Reply text",
                mediaMimeType = "image/png",
                earliestCreatedEpochSeconds = 2_011L,
            ),
        )
    }

    private fun comment(
        id: String,
        author: String,
        parentId: String,
        body: String,
        created: Long,
        children: List<Comment> = emptyList(),
    ): Comment = Comment(
        id = id,
        author = author,
        age = "0m",
        body = body,
        score = 1,
        depth = if (parentId.startsWith("t1_")) 1 else 0,
        accent = 0xFFFF8A50L,
        createdEpochSeconds = created,
        parentId = parentId,
        children = children,
    )
}
