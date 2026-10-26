package app.subshell.prototype.ui

import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class A11y01A2ExpandedMainShellPolicyTest {
    @Test
    fun expandedMainShellStartsAtMaterialExpandedWidth() {
        // ISSUE247: the edge is Google's expanded window size class.
        fun expanded(widthDp: Int) = useExpandedMainLayout(
            WindowSizeClass.BREAKPOINTS_V1.computeWindowSizeClass(widthDp.toFloat(), 800f),
        )
        assertFalse(expanded(0))
        assertFalse(expanded(839))
        assertTrue(expanded(840))
        assertTrue(expanded(1200))
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
