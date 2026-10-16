package app.subshell.prototype.ui.screens

import app.subshell.prototype.data.network.reddit.RedditCommentDataDto
import app.subshell.prototype.data.network.reddit.RedditMediaImageSourceDto
import app.subshell.prototype.data.network.reddit.RedditMediaMetadataDto
import app.subshell.prototype.data.network.reddit.bodyWithRedditMediaMetadata
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentInlineMediaEditPolicyTest {
    @Test
    fun localMediaBlocksPlainTextEdit() {
        assertTrue(
            commentContainsUnsupportedEditableInlineMedia(
                body = "plain text",
                hasLocalMedia = true,
            ),
        )
    }

    // ISSUE8B_RICHTEXT_MEDIA_EDIT_V1: Reddit's own placeholders (Giphy, images,
    // emotes, resolved "Processing img" media) no longer block an edit; it is
    // sent as rich text.
    @Test
    fun redditMediaPlaceholdersAreEditedAsRichText() {
        val image = RedditMediaMetadataDto(
            status = "valid",
            mediaKind = "Image",
            mimeType = "image/jpeg",
            source = RedditMediaImageSourceDto(url = "https://i.redd.it/pic123.jpg"),
        )
        listOf(
            RedditCommentDataDto(body = "Undertest\n\n![gif](giphy|KD5SK9O4hgZoI)\n\nEdit: test"),
            RedditCommentDataDto(body = "before ![img](emote|t5_example|123) after"),
            RedditCommentDataDto(body = "look ![img](pic123)", mediaMetadata = mapOf("pic123" to image)),
            RedditCommentDataDto(body = "*Processing img pic123...*", mediaMetadata = mapOf("pic123" to image)),
        ).forEach { dto ->
            assertFalse(
                dto.body,
                commentContainsUnsupportedEditableInlineMedia(
                    body = dto.bodyWithRedditMediaMetadata(),
                        hasLocalMedia = false,
                ),
            )
        }
    }

    @Test
    fun ordinaryTextRemainsEditable() {
        assertFalse(
            commentContainsUnsupportedEditableInlineMedia(
                body = "subshell WRITE02B plain comment",
                hasLocalMedia = false,
            ),
        )
    }
}
