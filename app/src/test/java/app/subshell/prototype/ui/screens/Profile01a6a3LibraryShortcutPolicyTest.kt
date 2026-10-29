package app.subshell.prototype.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class Profile01a6a3LibraryShortcutPolicyTest {
    @Test
    fun successorOwnProfileNavigationUsesInlineDestinations() {
        val tabs = profileTabsForSession(
            username = "brealorg",
            activeAccountUsername = "brealorg",
            authenticated = true,
        )
        assertEquals(
            listOf("Posts", "Comments", "Saved", "Upvotes", "Downvotes", "Hidden", "Blocked", "Drafts", "Reading"),
            tabs,
        )
        assertFalse(tabs.contains("Library"))
    }
}
