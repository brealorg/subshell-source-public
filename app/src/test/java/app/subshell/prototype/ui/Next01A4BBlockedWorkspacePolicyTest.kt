package app.subshell.prototype.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class Next01A4BBlockedWorkspacePolicyTest {
    @Test
    fun blockedWorkspaceRequiresExpandedSplitSupportedOriginAndTabSix() {
        assertTrue(
            useExpandedYouBlockedWorkspaceListDetail(
                expandedWindow = true,
                supportedOrigin = true,
                selectedTab = 6,
                tabletLayoutMode = TabletLayoutMode.SPLIT,
            ),
        )
    }

    @Test
    fun blockedWorkspaceRejectsCompactSingleAndUnsupportedOrigins() {
        assertFalse(
            useExpandedYouBlockedWorkspaceListDetail(
                expandedWindow = false,
                supportedOrigin = true,
                selectedTab = 6,
                tabletLayoutMode = TabletLayoutMode.SPLIT,
            ),
        )
        assertFalse(
            useExpandedYouBlockedWorkspaceListDetail(
                expandedWindow = true,
                supportedOrigin = true,
                selectedTab = 6,
                tabletLayoutMode = TabletLayoutMode.SINGLE,
            ),
        )
        assertFalse(
            useExpandedYouBlockedWorkspaceListDetail(
                expandedWindow = true,
                supportedOrigin = false,
                selectedTab = 6,
                tabletLayoutMode = TabletLayoutMode.SPLIT,
            ),
        )
    }

    @Test
    fun contentTabsAndDraftsRemainOutsideBlockedWorkspace() {
        (0..5).forEach { tab ->
            assertFalse(
                useExpandedYouBlockedWorkspaceListDetail(
                    expandedWindow = true,
                    supportedOrigin = true,
                    selectedTab = tab,
                    tabletLayoutMode = TabletLayoutMode.SPLIT,
                ),
            )
        }

        assertFalse(
            useExpandedYouBlockedWorkspaceListDetail(
                expandedWindow = true,
                supportedOrigin = true,
                selectedTab = 7,
                tabletLayoutMode = TabletLayoutMode.SPLIT,
            ),
        )
    }

    @Test
    fun leavingBlockedTabClosesSelectedAccountDetail() {
        assertFalse(
            shouldCloseYouBlockedDetailOnTabSelection(6),
        )

        listOf(0, 1, 2, 3, 4, 5, 7).forEach { tab ->
            assertTrue(
                shouldCloseYouBlockedDetailOnTabSelection(tab),
            )
        }
    }

    @Test
    fun blockedWorkspacePlaceholderIsStable() {
        assertEquals(
            "Select an account",
            YOU_BLOCKED_WORKSPACE_PLACEHOLDER,
        )
    }
}
