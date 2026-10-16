package app.subshell.prototype.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * COMMENTTEXT01 (#153): emotes are shielded from the URL passes with
 * private-use markers. The author's own text may contain those characters
 * (they can be pasted), and was read back as a shield: ten or more digits
 * crashed every reader of the thread (toInt), a short index spliced the raw
 * text of a real emote into the author's text.
 */
class CommentText01EmoteShieldTest {
    private val emote =
        "![img](emote|t5_2qh1i|8217 \"subshell-media " +
            "https://reddit-econ-prod-assets-permanent.s3.amazonaws.com/asset-manager/t5_2qh1i/8217.png 20x20\")"

    @Test
    fun authorTextShapedLikeAShieldWithAHugeIndexDoesNotCrash() {
        val authorText = "" + "99999999999" + ""
        val body = "nice $emote and $authorText"

        val projection = projectCommentContent(body)

        assertEquals(body, projection.displayText)
    }

    @Test
    fun authorTextShapedLikeAShieldIsNotReplacedByAnEmote() {
        val authorText = "" + "0" + ""
        val body = "$authorText then $emote"

        val projection = projectCommentContent(body)

        assertEquals(body, projection.displayText)
    }

    @Test
    fun emotesStillSurviveTheUrlPasses() {
        val body = "a $emote b https://example.com/page c"

        val projection = projectCommentContent(body)

        // The emote's own image address is not lifted out as comment media.
        assertEquals(body, projection.displayText)
        assertEquals(emptyList<String>(), projection.media.map { it.uri })
    }
}
