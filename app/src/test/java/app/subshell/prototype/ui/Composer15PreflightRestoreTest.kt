package app.subshell.prototype.ui

import app.subshell.prototype.ui.screens.ComposerProcessToken
import app.subshell.prototype.ui.screens.restoredPreflightMinimumRevision
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * COMPOSER15_PREFLIGHT_SURVIVES_PROCESS_DEATH_V1 (ui review round 3 P5): the
 * policy revision a submit check waits for counts in the process that saved
 * it; restored in a new process (whose policy counter starts again at 0) the
 * check waits for the first fresh policy instead of staying on "Checking…".
 */
class Composer15PreflightRestoreTest {
    @Test
    fun aCheckRestoredInANewProcessWaitsForAnyFreshPolicy() {
        // Saved at revision 3 in the old process; the new one starts at 0.
        assertEquals(0L, restoredPreflightMinimumRevision("old-process", ComposerProcessToken, 3L))
    }

    @Test
    fun aCheckRecreatedInTheSameProcessKeepsItsRevision() {
        assertEquals(3L, restoredPreflightMinimumRevision(ComposerProcessToken, ComposerProcessToken, 3L))
    }
}
