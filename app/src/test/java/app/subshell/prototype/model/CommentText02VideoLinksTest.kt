package app.subshell.prototype.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * COMMENTTEXT02 (#154): i.imgur.com .gifv/.mp4 links and a preview
 * `.gif?format=mp4` were classified as image media: the URL was removed from
 * the comment text and the preview failed to decode, so the reader lost the
 * link. They now stay as text links; real images still become media.
 */
class CommentText02VideoLinksTest {
    @Test
    fun videoLinksStayInTheTextAndAreNotMedia() {
        for (url in listOf(
            "https://i.imgur.com/abc123.gifv",
            "https://i.imgur.com/abc123.mp4",
            "https://i.imgur.com/abc123.webm",
            "https://preview.redd.it/clip.gif?format=mp4&s=abc",
        )) {
            val projection = projectCommentContent("lol $url")
            assertEquals(url, emptyList<RemoteCommentMedia>(), projection.media)
            assertTrue(projection.displayText, projection.displayText.contains(url))
        }
    }

    @Test
    fun imagesAndExtensionlessImageHostsStillBecomeMedia() {
        assertEquals(
            listOf(
                RemoteCommentMedia("https://i.imgur.com/abc123.jpg", RemoteCommentMediaKind.IMAGE),
                RemoteCommentMedia("https://i.imgur.com/abc123", RemoteCommentMediaKind.IMAGE),
                RemoteCommentMedia("https://i.redd.it/funny.gif", RemoteCommentMediaKind.GIF),
            ),
            projectCommentContent(
                "https://i.imgur.com/abc123.jpg https://i.imgur.com/abc123 https://i.redd.it/funny.gif",
            ).media,
        )
    }
}
