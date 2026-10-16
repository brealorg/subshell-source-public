package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.account.CommentWriteFailureKind
import app.subshell.prototype.data.account.CommentWritePhase
import app.subshell.prototype.data.account.CommentWriteUiState
import app.subshell.prototype.data.network.reddit.RedditCommentApiError
import app.subshell.prototype.data.network.reddit.RedditCommentApiErrorKind
import app.subshell.prototype.ui.error.commentWriteErrorPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * COMPOSER09: Reddit's META_MEDIA_TYPE_NOT_ALLOWED (the community does not
 * allow this media in comments) is its own failure with a plain message,
 * not "Reddit rejected the comment" plus the raw provider code.
 */
class Composer09CommentMediaNotAllowedTest {
    @Test
    fun redditsMediaTypeRefusalIsRecognised() {
        assertTrue(
            redditCommentMediaTypeNotAllowed(
                listOf(RedditCommentApiError(RedditCommentApiErrorKind.UNKNOWN, code = "META_MEDIA_TYPE_NOT_ALLOWED")),
            ),
        )
        assertFalse(
            redditCommentMediaTypeNotAllowed(
                listOf(RedditCommentApiError(RedditCommentApiErrorKind.UNKNOWN, code = "THREAD_LOCKED")),
            ),
        )
    }

    @Test
    fun theNoticeSaysWhatToDoAndOffersNoResend() {
        val presentation = commentWriteErrorPresentation(
            CommentWriteUiState(
                phase = CommentWritePhase.FAILED,
                failureKind = CommentWriteFailureKind.MEDIA_NOT_ALLOWED,
            ),
        )
        assertEquals(
            "This community doesn't allow this kind of image in comments. Remove it to send the text.",
            presentation.message,
        )
        assertEquals(null, presentation.primaryAction)
    }
}
