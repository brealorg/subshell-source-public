package app.subshell.prototype.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Issue23DrawerGesturePolicyTest {
    @Test
    fun closedDrawerGivesUpDragOnlyWhileRowSwipesOwnHorizontalDrag() {
        assertTrue(drawerGesturesEnabled(inContext = false, swipeActionsEnabled = false, drawerOpen = false))
        assertFalse(drawerGesturesEnabled(inContext = false, swipeActionsEnabled = true, drawerOpen = false))
    }

    @Test
    fun openDrawerAlwaysKeepsScrimTapAndDragToCloseOutsideContext() {
        // Material 3 gates the scrim tap on gesturesEnabled; an open drawer
        // covers the rows, so there is no gesture to arbitrate against.
        assertTrue(drawerGesturesEnabled(inContext = false, swipeActionsEnabled = true, drawerOpen = true))
        assertTrue(drawerGesturesEnabled(inContext = false, swipeActionsEnabled = false, drawerOpen = true))
    }

    @Test
    fun contextualRoutesNeverExposeDrawerGestures() {
        assertFalse(drawerGesturesEnabled(inContext = true, swipeActionsEnabled = false, drawerOpen = false))
        assertFalse(drawerGesturesEnabled(inContext = true, swipeActionsEnabled = true, drawerOpen = true))
    }
}
