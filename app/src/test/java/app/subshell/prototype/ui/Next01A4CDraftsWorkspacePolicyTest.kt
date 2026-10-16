package app.subshell.prototype.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Next01A4CDraftsWorkspacePolicyTest {
    @Test
    fun draftsWorkspaceRequiresExpandedSplitSupportedOriginAndTabSeven() {
        assertTrue(
            useExpandedYouDraftsWorkspaceListDetail(
                expandedWindow = true,
                supportedOrigin = true,
                selectedTab = 7,
                tabletLayoutMode = TabletLayoutMode.SPLIT,
            ),
        )
    }

    @Test
    fun contentAndBlockedTabsRemainOutsideDraftsWorkspace() {
        (0..6).forEach { tab ->
            assertFalse(
                useExpandedYouDraftsWorkspaceListDetail(
                    expandedWindow = true,
                    supportedOrigin = true,
                    selectedTab = tab,
                    tabletLayoutMode = TabletLayoutMode.SPLIT,
                ),
            )
        }
    }

    @Test
    fun draftsWorkspaceRejectsCompactWindows() {
        assertFalse(
            useExpandedYouDraftsWorkspaceListDetail(
                expandedWindow = false,
                supportedOrigin = true,
                selectedTab = 7,
                tabletLayoutMode = TabletLayoutMode.SPLIT,
            ),
        )
    }

    @Test
    fun draftsWorkspaceRejectsSingleAndUnsupportedOrigins() {
        assertFalse(
            useExpandedYouDraftsWorkspaceListDetail(
                expandedWindow = true,
                supportedOrigin = true,
                selectedTab = 7,
                tabletLayoutMode = TabletLayoutMode.SINGLE,
            ),
        )

        assertFalse(
            useExpandedYouDraftsWorkspaceListDetail(
                expandedWindow = true,
                supportedOrigin = false,
                selectedTab = 7,
                tabletLayoutMode = TabletLayoutMode.SPLIT,
            ),
        )
    }

    @Test
    fun leavingDraftsTabClosesSelectedDraftDetail() {
        assertFalse(
            shouldCloseYouDraftsDetailOnTabSelection(7),
        )

        (0..6).forEach { tab ->
            assertTrue(
                shouldCloseYouDraftsDetailOnTabSelection(tab),
            )
        }
    }

    @Test
    fun draftsWorkspacePlaceholderIsStable() {
        assertEquals(
            "Select a draft",
            YOU_DRAFTS_WORKSPACE_PLACEHOLDER,
        )
    }
}
