package app.subshell.prototype.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ISSUE120_MEDIA_SHEET_STAYS_WHILE_BUSY_V1 (#120): Back or a swipe while a
 * media share or download ran hid the sheet, the dismiss was ignored, and
 * the invisible sheet swallowed every touch. While busy it refuses to hide.
 */
@OptIn(ExperimentalMaterial3Api::class)
class Issue120MediaSheetBusyTest {
    @Test
    fun whileBusyTheSheetCannotBeHiddenButCanStillMove() {
        assertFalse(mediaActionSheetMayChange(SheetValue.Hidden, busy = true))
        assertTrue(mediaActionSheetMayChange(SheetValue.Expanded, busy = true))
        assertTrue(mediaActionSheetMayChange(SheetValue.PartiallyExpanded, busy = true))
    }

    @Test
    fun whenIdleTheSheetHidesAsBefore() {
        assertTrue(mediaActionSheetMayChange(SheetValue.Hidden, busy = false))
    }
}
