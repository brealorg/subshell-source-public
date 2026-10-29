package app.subshell.prototype.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class Library02A1NestedYouLibraryPolicyTest {
    @Test
    fun ownProfileUsesThreePrimaryDestinations() {
        assertEquals(
            listOf("Posts", "Comments", "Library"),
            PROFILE_PRIMARY_DESTINATION_LABELS,
        )
    }

    @Test
    fun libraryPreservesAllExistingPrivateDestinations() {
        assertEquals(
            listOf("Saved", "Upvotes", "Downvotes", "Hidden", "Blocked", "Drafts", "Reading"),
            PROFILE_LIBRARY_DESTINATION_LABELS,
        )
    }

    @Test
    fun everyPrivateChildSelectsLibraryAtThePrimaryLevel() {
        assertEquals(0, profilePrimarySectionForTab(0))
        assertEquals(1, profilePrimarySectionForTab(1))
        for (tab in 2..7) {
            assertEquals(2, profilePrimarySectionForTab(tab))
        }
    }

    @Test
    fun libraryEntryRestoresLastValidChildAndFallsBackToSaved() {
        assertEquals(6, profilePrimaryTargetTab(section = 2, lastLibraryTab = 6))
        assertEquals(2, profilePrimaryTargetTab(section = 2, lastLibraryTab = 99))
        assertEquals(0, profilePrimaryTargetTab(section = 0, lastLibraryTab = 6))
        assertEquals(1, profilePrimaryTargetTab(section = 1, lastLibraryTab = 6))
    }
}
