package app.subshell.prototype.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class UserFlair01A3PresentationPolicyTest {
    @Test
    fun fallbackRemovesLeadingRedditEmojiTokensButKeepsPlainText() {
        assertEquals(
            "Marc Márquez",
            redditFlairFallbackText(
                ":MarcMarquez: Marc Márquez",
            ),
        )
        assertEquals(
            "Fabio Quartararo",
            redditFlairFallbackText(
                ":French: Fabio Quartararo",
            ),
        )
        assertEquals(
            "XC90",
            redditFlairFallbackText("XC90"),
        )
        assertEquals(
            "",
            redditFlairFallbackText(":JackMiller:"),
        )
    }
}
