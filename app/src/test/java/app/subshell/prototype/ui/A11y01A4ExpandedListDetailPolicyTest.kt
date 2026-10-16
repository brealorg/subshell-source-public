package app.subshell.prototype.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class A11y01A4ExpandedListDetailPolicyTest {
    @Test
    fun homeTwoPaneRequiresExpandedHomeOriginAndNoCustomFeed() {
        assertFalse(
            useExpandedHomePostListDetail(
                expandedWindow = false,
                supportedOrigin = true,
                customFeedActive = false,
                tabletLayoutMode = TabletLayoutMode.SPLIT,
            ),
        )
        assertFalse(
            useExpandedHomePostListDetail(
                expandedWindow = true,
                supportedOrigin = false,
                customFeedActive = false,
                tabletLayoutMode = TabletLayoutMode.SPLIT,
            ),
        )
        assertFalse(
            useExpandedHomePostListDetail(
                expandedWindow = true,
                supportedOrigin = true,
                customFeedActive = true,
                tabletLayoutMode = TabletLayoutMode.SPLIT,
            ),
        )
        assertTrue(
            useExpandedHomePostListDetail(
                expandedWindow = true,
                supportedOrigin = true,
                customFeedActive = false,
                tabletLayoutMode = TabletLayoutMode.SPLIT,
            ),
        )
        assertFalse(
            useExpandedHomePostListDetail(
                expandedWindow = true,
                supportedOrigin = true,
                customFeedActive = false,
                tabletLayoutMode = TabletLayoutMode.SINGLE,
            ),
        )
    }

    @Test
    fun inboxTwoPaneRequiresExpandedInboxDetail() {
        assertFalse(
            useExpandedInboxListDetail(
                expandedWindow = false,
                supportedOrigin = true,
                tabletLayoutMode = TabletLayoutMode.SPLIT,
            ),
        )
        assertFalse(
            useExpandedInboxListDetail(
                expandedWindow = true,
                supportedOrigin = false,
                tabletLayoutMode = TabletLayoutMode.SPLIT,
            ),
        )
        assertTrue(
            useExpandedInboxListDetail(
                expandedWindow = true,
                supportedOrigin = true,
                tabletLayoutMode = TabletLayoutMode.SPLIT,
            ),
        )
        assertFalse(
            useExpandedInboxListDetail(
                expandedWindow = true,
                supportedOrigin = true,
                tabletLayoutMode = TabletLayoutMode.SINGLE,
            ),
        )
    }

    /** SubshellState plus the delegates it extracts into under ui/state. */
}
