package app.subshell.prototype.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class A11y01A11BottomNavAndCommentTargetPolicyTest {
    @Test
    fun communitiesUsesCompactVisualLabelOnlyAtLargeText() {
        assertEquals("Communities", navigationVisualLabel(MainDestination.SUBS, 1.0f))
        assertEquals("Communities", navigationVisualLabel(MainDestination.SUBS, 1.29f))
        assertEquals("Subs", navigationVisualLabel(MainDestination.SUBS, 1.30f))
        assertEquals("Subs", navigationVisualLabel(MainDestination.SUBS, 2.0f))
        assertEquals("Home", navigationVisualLabel(MainDestination.HOME, 2.0f))
        assertEquals("Inbox", navigationVisualLabel(MainDestination.INBOX, 2.0f))
        assertEquals("You", navigationVisualLabel(MainDestination.YOU, 2.0f))
    }
}
