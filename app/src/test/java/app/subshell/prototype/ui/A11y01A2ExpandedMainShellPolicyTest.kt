package app.subshell.prototype.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class A11y01A2ExpandedMainShellPolicyTest {
    @Test
    fun expandedMainShellStartsAtMaterialExpandedWidth() {
        assertFalse(useExpandedMainLayoutForWidthDp(0))
        assertFalse(useExpandedMainLayoutForWidthDp(839))
        assertTrue(useExpandedMainLayoutForWidthDp(840))
        assertTrue(useExpandedMainLayoutForWidthDp(1200))
    }

    @Test
    fun railKeepsCompactSubsVisualLabelAndFullDestinationIdentity() {
        // #224 (adapt-08): the rail names the destination as the bottom bar does.
        assertEquals("Communities", navigationRailVisualLabel(MainDestination.SUBS))
        assertEquals("Home", navigationRailVisualLabel(MainDestination.HOME))
        assertEquals("Inbox", navigationRailVisualLabel(MainDestination.INBOX))
        assertEquals("You", navigationRailVisualLabel(MainDestination.YOU))
    }

    /** SubshellApp plus the composables it extracts into under ui/app, and its layout policy (#213). */
}
