package app.subshell.prototype.ui

import app.subshell.prototype.ui.screens.InboxModSection
import app.subshell.prototype.ui.screens.InboxPrimaryTab
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Next01A3DInboxModmailWorkspacePolicyTest {
    @Test
    fun modmailWorkspaceRequiresExpandedSplit() {
        assertFalse(
            useExpandedInboxModmailWorkspaceListDetail(
                expandedWindow = false,
                selectedTab = InboxPrimaryTab.MOD,
                selectedModSection = InboxModSection.MODMAIL,
                tabletLayoutMode = TabletLayoutMode.SPLIT,
            ),
        )

        assertFalse(
            useExpandedInboxModmailWorkspaceListDetail(
                expandedWindow = true,
                selectedTab = InboxPrimaryTab.MOD,
                selectedModSection = InboxModSection.MODMAIL,
                tabletLayoutMode = TabletLayoutMode.SINGLE,
            ),
        )

        assertTrue(
            useExpandedInboxModmailWorkspaceListDetail(
                expandedWindow = true,
                selectedTab = InboxPrimaryTab.MOD,
                selectedModSection = InboxModSection.MODMAIL,
                tabletLayoutMode = TabletLayoutMode.SPLIT,
            ),
        )
    }

    @Test
    fun queueAndOtherPrimaryTabsAreNotClaimedByA3D() {
        assertFalse(
            useExpandedInboxModmailWorkspaceListDetail(
                expandedWindow = true,
                selectedTab = InboxPrimaryTab.MOD,
                selectedModSection = InboxModSection.QUEUE,
                tabletLayoutMode = TabletLayoutMode.SPLIT,
            ),
        )

        assertFalse(
            useExpandedInboxModmailWorkspaceListDetail(
                expandedWindow = true,
                selectedTab = InboxPrimaryTab.ACTIVITY,
                selectedModSection = InboxModSection.MODMAIL,
                tabletLayoutMode = TabletLayoutMode.SPLIT,
            ),
        )

        assertFalse(
            useExpandedInboxModmailWorkspaceListDetail(
                expandedWindow = true,
                selectedTab = InboxPrimaryTab.MESSAGES,
                selectedModSection = InboxModSection.MODMAIL,
                tabletLayoutMode = TabletLayoutMode.SPLIT,
            ),
        )
    }
}
