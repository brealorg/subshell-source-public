package app.subshell.prototype.ui.screens

import app.subshell.prototype.model.RemoteCommentMediaKind
import app.subshell.prototype.model.projectCommentContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Profile01a5PresentationPolicyTest {
    @Test
    fun standaloneDirectImageIsPreviewedWithoutRepeatingItsUrl() {
        val image = "https://i.redd.it/example.jpg"
        val projection = projectCommentContent(image)

        assertEquals("", projection.displayText)
        assertEquals(1, projection.media.size)
        assertEquals(image, projection.media.single().uri)
        assertEquals(RemoteCommentMediaKind.IMAGE, projection.media.single().kind)
    }

    @Test
    fun standaloneMarkdownImageIsConsumedByTheSharedMediaProjection() {
        val image = "https://i.redd.it/example.png"
        val projection = projectCommentContent("[photo]($image)")

        assertEquals("", projection.displayText)
        assertEquals(image, projection.media.single().uri)
    }

    @Test
    fun ordinaryWebLinksRemainVisibleAndClickableText() {
        val url = "https://example.com/thread"
        val projection = projectCommentContent("Read $url")

        assertTrue(projection.displayText.contains(url))
        assertTrue(projection.media.isEmpty())
    }

    @Test
    fun embeddedMediaLinkKeepsItsHumanLabelButNotTheRawMediaUrl() {
        val image = "https://i.redd.it/example.webp"
        val projection = projectCommentContent("See [the photo]($image) please")

        assertEquals("See the photo please", projection.displayText)
        assertFalse(projection.displayText.contains(image))
        assertEquals(image, projection.media.single().uri)
    }
}
