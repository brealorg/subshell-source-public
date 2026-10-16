package app.subshell.prototype.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Modmail01A1InboxPolicyTest {
    @Test
    fun modSurfaceHasQueueAndModmailSecondaryDestinations() {
        assertEquals(listOf("Queue", "Modmail"), InboxModSection.entries.map { it.label })
        assertEquals("Modmail for 1 community", modmailCommunityCountLabel(1))
        assertEquals("Modmail for 3 communities", modmailCommunityCountLabel(3))
    }

}
