package app.subshell.prototype.ui

import app.subshell.prototype.ui.screens.InboxPrimaryTab
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Next01A3AInboxActivityWorkspacePolicyTest {
    @Test
    fun activityWorkspaceRequiresExpandedSplit() {
        assertFalse(
            useExpandedInboxActivityWorkspaceListDetail(
                expandedWindow = false,
                selectedTab = InboxPrimaryTab.ACTIVITY,
                tabletLayoutMode = TabletLayoutMode.SPLIT,
            ),
        )

        assertFalse(
            useExpandedInboxActivityWorkspaceListDetail(
                expandedWindow = true,
                selectedTab = InboxPrimaryTab.ACTIVITY,
                tabletLayoutMode = TabletLayoutMode.SINGLE,
            ),
        )

        assertTrue(
            useExpandedInboxActivityWorkspaceListDetail(
                expandedWindow = true,
                selectedTab = InboxPrimaryTab.ACTIVITY,
                tabletLayoutMode = TabletLayoutMode.SPLIT,
            ),
        )
    }

    @Test
    fun messagesAndModAreNotPromotedByA3A() {
        assertFalse(
            useExpandedInboxActivityWorkspaceListDetail(
                expandedWindow = true,
                selectedTab = InboxPrimaryTab.MESSAGES,
                tabletLayoutMode = TabletLayoutMode.SPLIT,
            ),
        )

        assertFalse(
            useExpandedInboxActivityWorkspaceListDetail(
                expandedWindow = true,
                selectedTab = InboxPrimaryTab.MOD,
                tabletLayoutMode = TabletLayoutMode.SPLIT,
            ),
        )
    }
}
