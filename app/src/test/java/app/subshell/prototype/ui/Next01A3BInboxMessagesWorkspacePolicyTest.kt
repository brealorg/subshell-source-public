package app.subshell.prototype.ui

import app.subshell.prototype.ui.screens.InboxPrimaryTab
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Next01A3BInboxMessagesWorkspacePolicyTest {
    @Test
    fun messagesWorkspaceRequiresExpandedSplit() {
        assertFalse(
            useExpandedInboxMessagesWorkspaceListDetail(
                expandedWindow = false,
                selectedTab = InboxPrimaryTab.MESSAGES,
                tabletLayoutMode = TabletLayoutMode.SPLIT,
            ),
        )

        assertFalse(
            useExpandedInboxMessagesWorkspaceListDetail(
                expandedWindow = true,
                selectedTab = InboxPrimaryTab.MESSAGES,
                tabletLayoutMode = TabletLayoutMode.SINGLE,
            ),
        )

        assertTrue(
            useExpandedInboxMessagesWorkspaceListDetail(
                expandedWindow = true,
                selectedTab = InboxPrimaryTab.MESSAGES,
                tabletLayoutMode = TabletLayoutMode.SPLIT,
            ),
        )
    }

    @Test
    fun a3bDoesNotClaimActivityOrMod() {
        assertFalse(
            useExpandedInboxMessagesWorkspaceListDetail(
                expandedWindow = true,
                selectedTab = InboxPrimaryTab.ACTIVITY,
                tabletLayoutMode = TabletLayoutMode.SPLIT,
            ),
        )

        assertFalse(
            useExpandedInboxMessagesWorkspaceListDetail(
                expandedWindow = true,
                selectedTab = InboxPrimaryTab.MOD,
                tabletLayoutMode = TabletLayoutMode.SPLIT,
            ),
        )
    }
}
