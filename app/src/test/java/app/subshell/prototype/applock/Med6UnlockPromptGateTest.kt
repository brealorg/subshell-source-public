package app.subshell.prototype.applock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MED6 (OCB-575): before Android 11 the device credential screen stops
 * Subshell; the stop cleared the "shown" flag and the next resume opened the
 * prompt again after Back, so it could never be cancelled.
 */
class Med6UnlockPromptGateTest {
    @Test
    fun aCancelledPromptIsNotReopenedByTheResumeItsOwnScreenCaused() {
        val gate = AppUnlockPromptGate()

        assertTrue(gate.shouldPrompt(locked = true))
        gate.onStopped() // the credential activity covers Subshell
        gate.onPromptFinished(unlocked = false) // Back on the PIN screen

        assertFalse(gate.shouldPrompt(locked = true))
    }

    @Test
    fun leavingAndComingBackAsksAgain() {
        val gate = AppUnlockPromptGate()
        gate.shouldPrompt(locked = true)
        gate.onPromptFinished(unlocked = false)

        gate.onStopped() // Home
        assertTrue(gate.shouldPrompt(locked = true))
    }

    @Test
    fun anUnlockedAppIsNotAskedAndAnUnlockResetsTheGate() {
        val gate = AppUnlockPromptGate()
        assertFalse(gate.shouldPrompt(locked = false))

        gate.onPromptStarted()
        gate.onPromptFinished(unlocked = true)
        assertTrue(gate.shouldPrompt(locked = true))
    }
}
