package app.subshell.prototype.ui.screens

import app.subshell.prototype.data.LIVE_PROFILE_ACTIVITY_HISTORY_KINDS
import app.subshell.prototype.data.RedditAccountHistoryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class Profile01a6AccountHistoryUiPolicyTest {
    @Test
    fun authenticatedOwnProfileUsesInlinePrivateHistoryDestinations() {
        assertEquals(
            listOf("Posts", "Comments", "Saved", "Upvotes", "Downvotes", "Hidden", "Blocked", "Drafts"),
            profileTabsForSession(
                username = "brealorg",
                activeAccountUsername = "BREALORG",
                authenticated = true,
            ),
        )
        assertEquals(RedditAccountHistoryKind.SAVED_POSTS, profileHistoryKindForTab(2))
        assertEquals(RedditAccountHistoryKind.UPVOTED, profileHistoryKindForTab(3))
        assertEquals(RedditAccountHistoryKind.DOWNVOTED, profileHistoryKindForTab(4))
        assertEquals(RedditAccountHistoryKind.HIDDEN, profileHistoryKindForTab(5))
        assertNull(profileHistoryKindForTab(6))
    }

    @Test
    fun authenticatedOtherProfileDoesNotExposePrivateDestinations() {
        val tabs = profileTabsForSession(
            username = "someone_else",
            activeAccountUsername = "brealorg",
            authenticated = true,
        )
        assertEquals(listOf("Posts", "Comments"), tabs)
        assertFalse(tabs.contains("Saved"))
        assertFalse(tabs.contains("Drafts"))
    }

    @Test
    fun legacyActivityKindMappingRemainsDeterministicInsideTheEngine() {
        assertEquals(
            listOf(
                RedditAccountHistoryKind.UPVOTED,
                RedditAccountHistoryKind.DOWNVOTED,
                RedditAccountHistoryKind.HIDDEN,
            ),
            LIVE_PROFILE_ACTIVITY_HISTORY_KINDS,
        )
        assertEquals(RedditAccountHistoryKind.UPVOTED, authenticatedProfileActivityHistoryKind(0))
        assertEquals(RedditAccountHistoryKind.DOWNVOTED, authenticatedProfileActivityHistoryKind(1))
        assertEquals(RedditAccountHistoryKind.HIDDEN, authenticatedProfileActivityHistoryKind(2))
        assertNull(authenticatedProfileActivityHistoryKind(3))
    }

    @Test
    fun localOwnProfileUsesTheSameInlinePrivateBoundary() {
        assertEquals(
            listOf("Posts", "Comments", "Saved", "Upvotes", "Downvotes", "Hidden", "Blocked", "Drafts"),
            profileTabsForSession(
                username = "root",
                activeAccountUsername = "ROOT",
                authenticated = false,
            ),
        )
    }
}
