package app.subshell.prototype.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ISSUE277_DISCOVERY_STRIPS_THE_R_PREFIX_V1: Discover communities searched
 * Reddit for "r/celebs" and found nothing; the search and the joined-community
 * match use the bare name, as the composer and flair searches do.
 */
class Issue277DiscoveryRPrefixTest {
    @Test
    fun aLeadingRSlashIsLeftOut() {
        assertEquals("celebs", normalizeDiscoveryDraft("r/celebs"))
        assertEquals("celebs", normalizeDiscoveryDraft("/r/celebs"))
        assertEquals("Celebs", normalizeDiscoveryDraft("  R/Celebs "))
        assertEquals("", normalizeDiscoveryDraft("r/"))
    }

    @Test
    fun otherQueriesStayAsTheyWere() {
        assertEquals("rust", normalizeDiscoveryDraft("rust"))
        assertEquals("rust lang", normalizeDiscoveryDraft(" rust   lang "))
        assertEquals("cats r/dogs", normalizeDiscoveryDraft("cats r/dogs"))
    }
}
