package app.subshell.prototype.data.cache

import app.subshell.prototype.data.account.PostSaveFailureKind
import app.subshell.prototype.data.account.PostSaveUiState
import app.subshell.prototype.model.Comment
import app.subshell.prototype.ui.error.postSaveErrorPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * STEP3 (commentsave01a1, commentsave01a2): a saved comment stays saved in
 * the cache, also in a 21-field payload from before author flair; a 20-field
 * payload from before comment saves reads as not saved and keeps its vote;
 * and a failed save names a comment as a comment and a post as a post.
 */
class Step3CommentSavedCacheTest {
    private val comment = Comment(
        id = "t1_c1", author = "someone", age = "1h", body = "Body", score = 3, depth = 0, accent = 0L,
        vote = 1, saved = true,
    )

    /** The codec's length-prefixed fields ("n:value", or "-1:" for null). */
    private fun fields(payload: String): List<String?> {
        val values = mutableListOf<String?>()
        var cursor = 0
        while (cursor < payload.length) {
            val separator = payload.indexOf(':', cursor)
            val length = payload.substring(cursor, separator).toInt()
            if (length < 0) {
                values += null
                cursor = separator + 1
            } else {
                values += payload.substring(separator + 1, separator + 1 + length)
                cursor = separator + 1 + length
            }
        }
        return values
    }

    private fun pack(values: List<String?>) = values.joinToString("") { value -> value?.let { "${it.length}:$it" } ?: "-1:" }

    private fun firstFields(count: Int) = pack(fields(SubshellCacheCodec.encodeComment(comment)).take(count))

    @Test
    fun aSavedCommentStaysSavedAlsoInAnOlderPayload() {
        assertTrue(SubshellCacheCodec.decodeComment(SubshellCacheCodec.encodeComment(comment)).saved)
        assertTrue(SubshellCacheCodec.decodeComment(firstFields(21)).saved)
    }

    @Test
    fun aPayloadFromBeforeCommentSavesReadsAsNotSavedAndKeepsItsVote() {
        val decoded = SubshellCacheCodec.decodeComment(firstFields(20))

        assertFalse(decoded.saved)
        assertEquals(1, decoded.vote)
        assertEquals("Body", decoded.body)
    }

    @Test
    fun aFailedSaveNamesACommentAsACommentAndAPostAsAPost() {
        val comment = postSaveErrorPresentation(PostSaveUiState(postFullname = "t1_abc", failureKind = PostSaveFailureKind.VALIDATION))
        val configuration = postSaveErrorPresentation(PostSaveUiState(postFullname = "t1_abc", failureKind = PostSaveFailureKind.CONFIGURATION))
        val post = postSaveErrorPresentation(PostSaveUiState(postFullname = "t3_abc", failureKind = PostSaveFailureKind.VALIDATION))

        assertEquals("Couldn't update saved comment", comment.title)
        assertEquals("This comment can't be saved.", comment.message)
        assertEquals("Saving comments isn't configured for this account.", configuration.message)
        assertEquals("Couldn't update saved post", post.title)
        assertEquals("This post can't be saved.", post.message)
    }
}
