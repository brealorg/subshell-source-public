package app.subshell.prototype.ui

import app.subshell.prototype.ui.screens.InboxModSection
import app.subshell.prototype.ui.screens.InboxPrimaryTab
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Next01A3CInboxModQueueWorkspacePolicyTest {
    @Test
    fun modQueueRequiresExpandedSplit() {
        assertFalse(
            useExpandedInboxModQueueWorkspaceListDetail(
                expandedWindow = false,
                selectedTab = InboxPrimaryTab.MOD,
                selectedModSection = InboxModSection.QUEUE,
                tabletLayoutMode = TabletLayoutMode.SPLIT,
            ),
        )

        assertFalse(
            useExpandedInboxModQueueWorkspaceListDetail(
                expandedWindow = true,
                selectedTab = InboxPrimaryTab.MOD,
                selectedModSection = InboxModSection.QUEUE,
                tabletLayoutMode = TabletLayoutMode.SINGLE,
            ),
        )

        assertTrue(
            useExpandedInboxModQueueWorkspaceListDetail(
                expandedWindow = true,
                selectedTab = InboxPrimaryTab.MOD,
                selectedModSection = InboxModSection.QUEUE,
                tabletLayoutMode = TabletLayoutMode.SPLIT,
            ),
        )
    }

    @Test
    fun modmailIsExplicitlyNotClaimedByA3C() {
        assertFalse(
            useExpandedInboxModQueueWorkspaceListDetail(
                expandedWindow = true,
                selectedTab = InboxPrimaryTab.MOD,
                selectedModSection = InboxModSection.MODMAIL,
                tabletLayoutMode = TabletLayoutMode.SPLIT,
            ),
        )
    }

    @Test
    fun activityAndMessagesAreNotClaimedByA3C() {
        assertFalse(
            useExpandedInboxModQueueWorkspaceListDetail(
                expandedWindow = true,
                selectedTab = InboxPrimaryTab.ACTIVITY,
                selectedModSection = InboxModSection.QUEUE,
                tabletLayoutMode = TabletLayoutMode.SPLIT,
            ),
        )

        assertFalse(
            useExpandedInboxModQueueWorkspaceListDetail(
                expandedWindow = true,
                selectedTab = InboxPrimaryTab.MESSAGES,
                selectedModSection = InboxModSection.QUEUE,
                tabletLayoutMode = TabletLayoutMode.SPLIT,
            ),
        )
    }
}
