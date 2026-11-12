package io.github.brealorg.subshell.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** CLEANURL01_TRACKING_PARAMETERS_V1: only known tracking parameters go; everything else stays. */
class CleanUrl01TrackingParametersTest {
    private val etsy =
        "https://www.etsy.com/no-en/listing/4320800571/dog-show-bow-line-pet-hair-bow-yorkie" +
            "?ls=s&ga_order=most_relevant&ga_search_type=all&ga_view_type=gallery" +
            "&ga_search_query=dog+show+bows+top+knot&ref=sr_gallery-1-4&organic_search_click=1&sts=1" +
            "&content_source=52b0b778-659a-4364-a89e-eb3d83270df3%253ALTe6997fabb1ff217c91707c91482e6975e64c4318" +
            "&logging_key=52b0b778-659a-4364-a89e-eb3d83270df3%3ALTe6997fabb1ff217c91707c91482e6975e64c4318" +
            "&variation0=5495919706"

    @Test
    fun etsyLinkKeepsOnlyTheVariation() {
        assertEquals(
            "https://www.etsy.com/no-en/listing/4320800571/dog-show-bow-line-pet-hair-bow-yorkie?variation0=5495919706",
            UrlCleaner.clean(etsy),
        )
    }

    @Test
    fun globalTrackingParametersGoOnAnySite() {
        assertEquals(
            "https://example.org/a?id=7",
            UrlCleaner.clean("https://example.org/a?utm_source=x&id=7&fbclid=abc&gclid=1"),
        )
    }

    @Test
    fun allTrackingLeavesNoQuestionMarkAndKeepsTheFragment() {
        assertEquals("https://example.org/a#top", UrlCleaner.clean("https://example.org/a?utm_medium=x&utm_campaign=y#top"))
    }

    @Test
    fun siteSpecificNamesAreNotRemovedElsewhere() {
        val other = "https://example.org/a?ref=sr_1&ls=s&ga_order=x"
        assertEquals(other, UrlCleaner.clean(other))
    }

    @Test
    fun parametersThatPickContentAreKept() {
        val youtube = "https://www.youtube.com/watch?v=abc123&t=42s&list=PL1"
        assertEquals(youtube, UrlCleaner.clean(youtube))
        assertEquals(
            "https://www.youtube.com/watch?v=abc123&t=42s",
            UrlCleaner.clean("https://www.youtube.com/watch?v=abc123&si=zzz&t=42s&feature=share"),
        )
    }

    @Test
    fun linksWithoutQueryAndNonWebTextAreReturnedUnchanged() {
        assertEquals("https://example.org/a", UrlCleaner.clean("https://example.org/a"))
        assertEquals("/r/kotlin?utm_source=x", UrlCleaner.clean("/r/kotlin?utm_source=x"))
    }

    @Test
    fun longLinkGetsAShortLabelAndShortLinkDoesNot() {
        val label = UrlCleaner.shortLabel(etsy)!!
        assertTrue(label, label.startsWith("etsy.com/no-en/listing/"))
        assertTrue(label, label.endsWith("…"))
        assertTrue(label.length <= 48)
        assertNull(UrlCleaner.shortLabel("https://example.org/short"))
        assertNull(UrlCleaner.shortLabel("https://en.wikipedia.org/wiki/Python_(programming_language)"))
    }
}
