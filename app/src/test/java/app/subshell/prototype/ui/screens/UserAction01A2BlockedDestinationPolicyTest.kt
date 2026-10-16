package app.subshell.prototype.ui.screens

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse

class UserAction01A2BlockedDestinationPolicyTest {
    @Test
    fun authenticatedOwnProfileExposesBlockedBeforeDrafts() {
        assertEquals(
            listOf("Posts", "Comments", "Saved", "Upvotes", "Downvotes", "Hidden", "Blocked", "Drafts"),
            profileTabsForSession(
                username = "shelluser",
                activeAccountUsername = "shelluser",
                authenticated = true,
            ),
        )
    }

    @Test
    fun otherProfilesNeverExposeBlockedDestination() {
        assertFalse(
            profileTabsForSession(
                username = "someoneelse",
                activeAccountUsername = "shelluser",
                authenticated = true,
            ).contains("Blocked"),
        )
    }
}
