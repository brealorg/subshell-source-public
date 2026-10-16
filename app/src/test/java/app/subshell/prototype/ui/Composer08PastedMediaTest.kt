package app.subshell.prototype.ui

import app.subshell.prototype.ui.screens.composerMediaWithPasted
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * COMPOSER08 (ui review round 3 P6): pasting an image that is already
 * attached must not add it twice; the gallery pager keys on the URI and
 * crashed on the duplicate key.
 */
class Composer08PastedMediaTest {
    private fun image(n: Int) =
        ComposerMediaAttachment("content://media/image/$n", "image/jpeg", ComposerMediaKind.IMAGE)

    private val video =
        ComposerMediaAttachment("content://media/video/1", "video/mp4", ComposerMediaKind.VIDEO)

    @Test
    fun theSameImagePastedTwiceIsAttachedOnce() {
        val once = composerMediaWithPasted(emptyList(), image(1))
        val twice = composerMediaWithPasted(once, image(1))
        assertEquals(listOf(image(1)), twice)
        assertEquals(twice.size, twice.map { it.uri }.distinct().size)
    }

    @Test
    fun aNewImageIsAddedUpToTwentyAndReplacesAVideo() {
        assertEquals(listOf(image(1), image(2)), composerMediaWithPasted(listOf(image(1)), image(2)))
        val full = (1..20).map(::image)
        assertEquals(full, composerMediaWithPasted(full, image(21)))
        assertEquals(listOf(image(3)), composerMediaWithPasted(listOf(video), image(3)))
    }
}
