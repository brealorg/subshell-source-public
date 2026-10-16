package app.subshell.prototype.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Next01A1CommunityPostTwoPanePolicyTest {
    @Test
    fun communityTwoPaneRequiresExpandedSupportedOriginAndSplitMode() {
        assertFalse(
            useExpandedCommunityPostListDetail(
                expandedWindow = false,
                supportedOrigin = true,
                tabletLayoutMode = TabletLayoutMode.SPLIT,
            ),
        )
        assertFalse(
            useExpandedCommunityPostListDetail(
                expandedWindow = true,
                supportedOrigin = false,
                tabletLayoutMode = TabletLayoutMode.SPLIT,
            ),
        )
        assertTrue(
            useExpandedCommunityPostListDetail(
                expandedWindow = true,
                supportedOrigin = true,
                tabletLayoutMode = TabletLayoutMode.SPLIT,
            ),
        )
        assertFalse(
            useExpandedCommunityPostListDetail(
                expandedWindow = true,
                supportedOrigin = true,
                tabletLayoutMode = TabletLayoutMode.SINGLE,
            ),
        )
    }
}
