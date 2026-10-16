package app.subshell.prototype.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Next01A4AYouContentWorkspacePolicyTest {
    @Test
    fun sixContentDestinationsUseExpandedSplit() {
        (0..5).forEach { tab ->
            assertTrue(
                useExpandedYouContentWorkspaceListDetail(
                    expandedWindow = true,
                    supportedOrigin = true,
                    selectedTab = tab,
                    tabletLayoutMode = TabletLayoutMode.SPLIT,
                ),
            )
        }
    }

    @Test
    fun blockedAndDraftsAreNotPostDetailWorkspaces() {
        listOf(6, 7).forEach { tab ->
            assertFalse(
                useExpandedYouContentWorkspaceListDetail(
                    expandedWindow = true,
                    supportedOrigin = true,
                    selectedTab = tab,
                    tabletLayoutMode = TabletLayoutMode.SPLIT,
                ),
            )
        }
    }

    @Test
    fun compactAndTabletSingleRemainUnchanged() {
        assertFalse(
            useExpandedYouContentWorkspaceListDetail(
                expandedWindow = false,
                supportedOrigin = true,
                selectedTab = 0,
                tabletLayoutMode = TabletLayoutMode.SPLIT,
            ),
        )
        assertFalse(
            useExpandedYouContentWorkspaceListDetail(
                expandedWindow = true,
                supportedOrigin = true,
                selectedTab = 0,
                tabletLayoutMode = TabletLayoutMode.SINGLE,
            ),
        )
    }

    @Test
    fun unrelatedUserOriginsAreNotClaimed() {
        assertFalse(
            useExpandedYouContentWorkspaceListDetail(
                expandedWindow = true,
                supportedOrigin = false,
                selectedTab = 0,
                tabletLayoutMode = TabletLayoutMode.SPLIT,
            ),
        )
    }

    @Test
    fun placeholdersReflectContentKind() {
        assertEquals("Select a post", youContentWorkspacePlaceholder(0))
        assertEquals("Select a comment", youContentWorkspacePlaceholder(1))
        assertEquals("Select a saved item", youContentWorkspacePlaceholder(2))
        assertEquals("Select a post", youContentWorkspacePlaceholder(5))
    }
}
