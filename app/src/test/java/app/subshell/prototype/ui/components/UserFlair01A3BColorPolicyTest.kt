package app.subshell.prototype.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserFlair01A3BColorPolicyTest {
    @Test
    fun sixDigitBackgroundGetsOpaqueAlphaAndLightTextGetsWhite() {
        assertEquals(
            RedditFlairServerColors(
                backgroundArgb = 0xFFFF4500L,
                foregroundArgb = 0xFFFFFFFFL,
            ),
            redditFlairServerColors(
                backgroundColor = " #ff4500 ",
                textColor = " LIGHT ",
            ),
        )
    }

    @Test
    fun eightDigitArgbBackgroundIsPreservedAndDarkTextGetsBlack() {
        assertEquals(
            RedditFlairServerColors(
                backgroundArgb = 0x80123456L,
                foregroundArgb = 0xFF000000L,
            ),
            redditFlairServerColors(
                backgroundColor = "#80123456",
                textColor = "dark",
            ),
        )
    }

    @Test
    fun incompleteOrMalformedServerStyleFallsBackToMaterial() {
        assertNull(
            redditFlairServerColors(
                backgroundColor = null,
                textColor = "light",
            ),
        )
        assertNull(
            redditFlairServerColors(
                backgroundColor = "#fff",
                textColor = "light",
            ),
        )
        assertNull(
            redditFlairServerColors(
                backgroundColor = "#123456",
                textColor = null,
            ),
        )
        assertNull(
            redditFlairServerColors(
                backgroundColor = "#123456",
                textColor = "automatic",
            ),
        )
        assertNull(
            redditFlairServerColors(
                backgroundColor = "transparent",
                textColor = "dark",
            ),
        )
    }
}
