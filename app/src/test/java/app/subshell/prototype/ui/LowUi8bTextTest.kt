package app.subshell.prototype.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import app.subshell.prototype.ui.components.compactNumber
import app.subshell.prototype.ui.components.markdownHighlightRanges
import app.subshell.prototype.ui.components.markdownSearchableText
import app.subshell.prototype.ui.components.markdownStyledAnnotatedString
import app.subshell.prototype.ui.screens.mediaPreviewCaptionVisible
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LOWUI8b: LOWUI8_COMPACT_NUMBERS_FOLLOW_THE_LOCALE_V1 (OCB-127),
 * LOWUI8_EMOTE_ALT_TEXT_IS_NOT_SEARCHED_V1 (OCB-136),
 * LOWUI8_MEDIA_PREVIEW_CAPTIONS_FOLLOW_THE_VIEWER_V1 (OCB-260).
 */
class LowUi8bTextTest {
    private fun <T> inLocale(locale: Locale, block: () -> T): T {
        val previous = Locale.getDefault()
        Locale.setDefault(locale)
        return try {
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun compactNumbersUseTheLocaleSeparatorAndDropAZeroDecimal() {
        val norwegian = Locale.forLanguageTag("nb-NO")
        inLocale(norwegian) {
            assertEquals("999", compactNumber(999))
            assertEquals("1K", compactNumber(1_000))
            assertEquals("1,2K", compactNumber(1_234))
            assertEquals("-1,5K", compactNumber(-1_500))
            assertEquals("12K", compactNumber(12_040))
            assertEquals("1,3M", compactNumber(1_250_000))
        }
        inLocale(Locale.US) {
            assertEquals("1.2K", compactNumber(1_234))
            assertEquals("2M", compactNumber(2_000_000))
        }
    }

    @Test
    fun theUnitIsChosenAfterRounding() {
        inLocale(Locale.US) {
            assertEquals("1M", compactNumber(999_950))
            assertEquals("999.9K", compactNumber(999_940))
            assertEquals("-2147.5M", compactNumber(Int.MIN_VALUE))
        }
    }

    @Test
    fun anEmotesAlternateTextIsNotSearchedAndOffsetsStayAligned() {
        val source = "hi ![img](emote|t5_x|1 \"subshell-media https://emoji.redditmedia.com/x.png 20x20\") there"
        val searchable = markdownSearchableText(source)
        val rendered = markdownStyledAnnotatedString(
            source = source,
            linkColor = Color.Unspecified,
            codeBackground = Color.Unspecified,
            codeFontFamily = FontFamily.Default,
        ).text

        assertEquals(rendered.length, searchable.length)
        assertTrue(markdownHighlightRanges(searchable, "emote").isEmpty())
        assertTrue(markdownHighlightRanges(searchable, ":").isEmpty())
        assertEquals(rendered.indexOf("there"), markdownHighlightRanges(searchable, "there").single().first)
    }

    @Test
    fun theMediaPreviewShowsCaptionsWhereTheViewerDoes() {
        fun visible(placement: MediaCaptionMode, mode: MediaCaptionMode, fullscreen: FullscreenCaptionMode, controls: Boolean) =
            mediaPreviewCaptionVisible(placement, mode, fullscreen, controls)

        // Hidden shows nothing, even with the controls on.
        MediaCaptionMode.entries.forEach { placement ->
            assertFalse(visible(placement, MediaCaptionMode.HIDDEN, FullscreenCaptionMode.WITH_CONTROLS, true))
        }
        // Below media is not an overlay, and waits for the controls.
        assertFalse(visible(MediaCaptionMode.OVERLAY, MediaCaptionMode.BELOW_MEDIA, FullscreenCaptionMode.WITH_CONTROLS, true))
        assertFalse(visible(MediaCaptionMode.BELOW_MEDIA, MediaCaptionMode.BELOW_MEDIA, FullscreenCaptionMode.WITH_CONTROLS, false))
        assertTrue(visible(MediaCaptionMode.BELOW_MEDIA, MediaCaptionMode.BELOW_MEDIA, FullscreenCaptionMode.WITH_CONTROLS, true))
        // Overlay follows the fullscreen mode.
        assertFalse(visible(MediaCaptionMode.OVERLAY, MediaCaptionMode.OVERLAY, FullscreenCaptionMode.WITH_CONTROLS, false))
        assertTrue(visible(MediaCaptionMode.OVERLAY, MediaCaptionMode.OVERLAY, FullscreenCaptionMode.ALWAYS_VISIBLE, false))
    }
}
