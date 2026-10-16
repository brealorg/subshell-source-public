package app.subshell.prototype.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class Profile01a2LiveUserPostsUiPolicyTest {
    @Test
    fun localActiveProfileUsesInlinePrivateDestinations() {
        assertEquals(
            listOf("Posts", "Comments", "Saved", "Upvotes", "Downvotes", "Hidden", "Blocked", "Drafts"),
            profileTabsForSession(
                username = "root",
                activeAccountUsername = "ROOT",
                authenticated = false,
            ),
        )
    }

    @Test
    fun localOtherProfileKeepsExistingTwoTabSurface() {
        assertEquals(
            listOf("Posts", "Comments"),
            profileTabsForSession(
                username = "other_user",
                activeAccountUsername = "root",
                authenticated = false,
            ),
        )
    }

    @Test
    fun authenticatedOwnProfileUsesInlinePrivateDestinations() {
        assertEquals(
            listOf("Posts", "Comments", "Saved", "Upvotes", "Downvotes", "Hidden", "Blocked", "Drafts"),
            profileTabsForSession(
                username = "reddit_user",
                activeAccountUsername = "REDDIT_USER",
                authenticated = true,
            ),
        )
    }

    @Test
    fun authenticatedOtherProfileUsesSameRemoteProfileTabBoundary() {
        assertEquals(
            listOf("Posts", "Comments"),
            profileTabsForSession(
                username = "other_user",
                activeAccountUsername = "reddit_user",
                authenticated = true,
            ),
        )
    }
}
