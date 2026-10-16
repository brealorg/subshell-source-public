package app.subshell.prototype.ui

import app.subshell.prototype.data.linkpreview.LinkPreviewMetadata
import app.subshell.prototype.ui.screens.toV4RemoteRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * ISSUE100_POST_REQUEST_FAILURE_VISIBLE_V1 (#100): Post silently did nothing
 * when the request rejected the draft after the composer's validation passed.
 * A preview image the request rejects is dropped; anything else is reported.
 */
class Issue100PostRequestFailureTest {
    private val linkDraft = PostComposerDraft(
        community = "subshell",
        title = "A link",
        type = ComposerPostType.LINK,
        linkUrl = "https://example.com/article",
    )

    @Test
    fun aPreviewImageJavaUriRejectsIsDroppedAndThePostStillGoes() {
        val preview = LinkPreviewMetadata(
            displayHost = "example.com",
            title = "Article",
            imageUrl = "https://cdn.example.com/a.jpg?crop=0|0|1200|630",
        )
        val request = linkDraft.toV4RemoteRequest("reddit:alice", preview)
        assertNotNull(request)
        assertEquals("https://example.com/article", request?.linkUrl)
        assertNull(request?.linkPreviewImageUrl)
    }

    @Test
    fun aValidPreviewImageIsKept() {
        val preview = LinkPreviewMetadata(
            displayHost = "example.com",
            title = "Article",
            imageUrl = "https://cdn.example.com/a.jpg",
        )
        assertEquals(
            "https://cdn.example.com/a.jpg",
            linkDraft.toV4RemoteRequest("reddit:alice", preview)?.linkPreviewImageUrl,
        )
    }

    @Test
    fun aDraftTheRequestRejectsForAnotherReasonHasAMessage() {
        val image = ComposerMediaAttachment("content://media/1", "image/png", ComposerMediaKind.IMAGE)
        val duplicateGallery = PostComposerDraft(
            community = "subshell",
            title = "Gallery",
            type = ComposerPostType.MEDIA,
            media = listOf(image, image),
        )
        assertNull(duplicateGallery.toV4RemoteRequest("reddit:alice", null))
        assertEquals(
            "This post can't be sent as it is. Check its link and attachments.",
            app.subshell.prototype.ui.screens.COMPOSER_REQUEST_UNBUILDABLE_MESSAGE,
        )
    }
}
