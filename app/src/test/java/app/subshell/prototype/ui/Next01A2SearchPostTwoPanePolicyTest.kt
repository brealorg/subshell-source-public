package app.subshell.prototype.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Next01A2SearchPostTwoPanePolicyTest {
    @Test
    fun searchTwoPaneRequiresExpandedSupportedOriginAndSplitMode() {
        assertFalse(
            useExpandedSearchPostListDetail(
                expandedWindow = false,
                supportedOrigin = true,
                tabletLayoutMode = TabletLayoutMode.SPLIT,
            ),
        )
        assertFalse(
            useExpandedSearchPostListDetail(
                expandedWindow = true,
                supportedOrigin = false,
                tabletLayoutMode = TabletLayoutMode.SPLIT,
            ),
        )
        assertTrue(
            useExpandedSearchPostListDetail(
                expandedWindow = true,
                supportedOrigin = true,
                tabletLayoutMode = TabletLayoutMode.SPLIT,
            ),
        )
        assertFalse(
            useExpandedSearchPostListDetail(
                expandedWindow = true,
                supportedOrigin = true,
                tabletLayoutMode = TabletLayoutMode.SINGLE,
            ),
        )
    }

    // STEP3 (next01a2): the Search workspace is split only on an expanded window
    // in split mode, before any post is chosen.
    @Test
    fun searchWorkspaceIsSplitOnlyOnAnExpandedWindowInSplitMode() {
        assertFalse(
            useExpandedSearchWorkspaceListDetail(
                expandedWindow = false,
                tabletLayoutMode = TabletLayoutMode.SPLIT,
            ),
        )
        assertTrue(
            useExpandedSearchWorkspaceListDetail(
                expandedWindow = true,
                tabletLayoutMode = TabletLayoutMode.SPLIT,
            ),
        )
        assertFalse(
            useExpandedSearchWorkspaceListDetail(
                expandedWindow = true,
                tabletLayoutMode = TabletLayoutMode.SINGLE,
            ),
        )
    }
}
