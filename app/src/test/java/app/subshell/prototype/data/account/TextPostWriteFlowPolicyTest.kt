package app.subshell.prototype.data.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextPostWriteFlowPolicyTest {
    @Test
    fun requestKeyNormalizesCommunityTitleAndLineEndings() {
        val first = TextPostWriteRequest(
            accountStableId = "reddit:redreader_compatibility:t2_writer",
            community = "r/SubShell",
            title = "  B2 title  ",
            text = "Line one\r\nLine two\r\n",
        )
        val second = TextPostWriteRequest(
            accountStableId = "reddit:redreader_compatibility:t2_writer",
            community = "subshell",
            title = "B2 title",
            text = "Line one\nLine two",
        )
        val changed = second.copy(text = "Different body")

        assertEquals(first.requestKey, second.requestKey)
        assertNotEquals(first.requestKey, changed.requestKey)
        assertEquals(64, first.requestKey.length)
    }

    @Test
    fun uiStateKeepsSuccessAndFailurePayloadsSeparated() {
        val request = TextPostWriteRequest(
            accountStableId = "reddit:redreader_compatibility:t2_writer",
            community = "subshell",
            title = "B2 title",
            text = "Body",
        )
        val sending = TextPostWriteUiState(
            phase = TextPostWritePhase.SENDING,
            accountStableId = request.accountStableId,
            requestKey = request.requestKey,
        )
        val failed = TextPostWriteUiState(
            phase = TextPostWritePhase.FAILED,
            accountStableId = request.accountStableId,
            requestKey = request.requestKey,
            failureKind = TextPostWriteFailureKind.RATE_LIMIT,
            retryable = true,
            retryAfterMs = 12_000L,
        )
        val submitted = SubmittedTextPost(
            fullname = "t3_b2proof",
            id = "b2proof",
            community = "subshell",
            title = "B2 title",
            text = "Body",
            permalink = "/r/subshell/comments/b2proof/b2_title/",
        )
        val succeeded = TextPostWriteUiState(
            phase = TextPostWritePhase.SUCCEEDED,
            accountStableId = request.accountStableId,
            requestKey = request.requestKey,
            submittedPost = submitted,
            completionRevision = 1L,
        )

        assertTrue(sending.inProgress)
        assertFalse(failed.inProgress)
        assertTrue(failed.matchesDraft(request.requestKey))
        assertEquals(12_000L, failed.retryAfterMs)
        assertNull(failed.submittedPost)
        assertEquals(submitted, succeeded.submittedPost)
        assertNull(succeeded.failureKind)
    }

    @Test
    fun previewImageSurvivesRequestWithoutChangingSubmissionIdentity() {
        val base = TextPostWriteRequest(
            accountStableId = "reddit:redreader_compatibility:t2_writer",
            community = "subshell",
            title = "Link",
            text = "Context",
            linkUrl = "https://example.com/story",
        )
        val enriched = base.copy(
            linkPreviewImageUrl = "https://cdn.example.com/story.jpg",
        )

        assertEquals(base.requestKey, enriched.requestKey)
        assertEquals("https://cdn.example.com/story.jpg", enriched.linkPreviewImageUrl)
    }
}
