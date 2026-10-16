package app.subshell.prototype.applock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Issue #38: when the app lock engages. */
class AppLockControllerTest {
    private val minute = 60_000L

    private fun unlockedController(delayMinutes: Int = 1): AppLockController =
        AppLockController(AppLockSettings(enabled = true, delayMinutes = delayMinutes))
            .also { it.unlock() }

    /** Leaves the app at [leftAt] and comes back at [returnedAt]. */
    private fun AppLockController.away(leftAt: Long, returnedAt: Long) {
        onStopped(leftAt, changingConfigurations = false)
        onStarted(returnedAt)
    }

    @Test
    fun offNeverLocks() {
        val controller = AppLockController(AppLockSettings(enabled = false, delayMinutes = 0))
        assertFalse(controller.locked)
        controller.away(leftAt = 0, returnedAt = 10 * 60 * minute)
        assertFalse(controller.locked)
    }

    @Test
    fun onStartsLocked() {
        assertTrue(AppLockController(AppLockSettings(enabled = true)).locked)
    }

    @Test
    fun locksOnlyWhenAwayForTheDelay() {
        val controller = unlockedController(delayMinutes = 5)
        controller.away(leftAt = 0, returnedAt = 5 * minute - 1)
        assertFalse(controller.locked)
        controller.away(leftAt = 10 * minute, returnedAt = 15 * minute)
        assertTrue(controller.locked)
    }

    @Test
    fun immediatelyLocksOnEveryReturn() {
        val controller = unlockedController(delayMinutes = 0)
        controller.away(leftAt = 1_000, returnedAt = 1_000)
        assertTrue(controller.locked)
    }

    @Test
    fun immediatelyLocksAlreadyWhenLeaving() {
        val controller = unlockedController(delayMinutes = 0)
        controller.onStopped(1_000, changingConfigurations = false)
        assertTrue(controller.locked)
    }

    @Test
    fun leavingWhileLockedDoesNotLockAgainAfterUnlock() {
        val controller = AppLockController(AppLockSettings(enabled = true, delayMinutes = 0))
        controller.onStopped(0, changingConfigurations = false)
        controller.unlock()
        controller.onStarted(1_000)
        assertFalse(controller.locked)
    }

    @Test
    fun startWithoutStopDoesNotLock() {
        val controller = unlockedController(delayMinutes = 0)
        controller.onStarted(5 * minute)
        assertFalse(controller.locked)
    }

    @Test
    fun rotationIsNotLeavingTheApp() {
        val controller = unlockedController(delayMinutes = 0)
        controller.onStopped(0, changingConfigurations = true)
        controller.onStarted(1_000)
        assertFalse(controller.locked)
    }

    @Test
    fun aClockThatWentBackwardsLocks() {
        val controller = unlockedController(delayMinutes = 60)
        controller.away(leftAt = 10 * minute, returnedAt = 5 * minute)
        assertTrue(controller.locked)
    }

    @Test
    fun ownTripOutDoesNotLockWithinTheGrace() {
        val controller = unlockedController(delayMinutes = 0)
        controller.onExternalTripStarted(0)
        controller.away(leftAt = 100, returnedAt = APP_LOCK_EXTERNAL_TRIP_GRACE_MS)
        assertFalse(controller.locked)
    }

    @Test
    fun ownTripOutLongerThanTheGraceFollowsTheDelay() {
        val controller = unlockedController(delayMinutes = 0)
        controller.onExternalTripStarted(0)
        controller.away(leftAt = 100, returnedAt = APP_LOCK_EXTERNAL_TRIP_GRACE_MS + 1)
        assertTrue(controller.locked)
    }

    @Test
    fun theExemptionCoversOneTripOnly() {
        val controller = unlockedController(delayMinutes = 0)
        controller.onExternalTripStarted(0)
        controller.away(leftAt = 100, returnedAt = 200)
        controller.away(leftAt = 300, returnedAt = 400)
        assertTrue(controller.locked)
    }

    @Test
    fun aTripThatNeverLeftTheAppEndsOnResume() {
        val controller = unlockedController(delayMinutes = 0)
        controller.onExternalTripStarted(0)
        controller.onResumed()
        controller.away(leftAt = 100, returnedAt = 200)
        assertTrue(controller.locked)
    }

    @Test
    fun aTripStartedWhileLockedIsNotExempt() {
        val controller = AppLockController(AppLockSettings(enabled = true, delayMinutes = 0))
        controller.onExternalTripStarted(0)
        controller.unlock()
        controller.away(leftAt = 100, returnedAt = 200)
        assertTrue(controller.locked)
    }

    @Test
    fun turningOffUnlocksAndForgetsTheBackgroundTime() {
        val controller = AppLockController(AppLockSettings(enabled = true, delayMinutes = 0))
        controller.onStopped(0, changingConfigurations = false)
        controller.updateSettings(AppLockSettings(enabled = false, delayMinutes = 0))
        assertFalse(controller.locked)
        controller.updateSettings(AppLockSettings(enabled = true, delayMinutes = 0))
        controller.onStarted(1_000)
        assertFalse(controller.locked)
    }

    @Test
    fun turningOnDoesNotLockNow() {
        val controller = AppLockController(AppLockSettings(enabled = false))
        controller.updateSettings(AppLockSettings(enabled = true, delayMinutes = 0))
        assertFalse(controller.locked)
    }

    @Test
    fun delayLabels() {
        assertEquals("Immediately", appLockDelayLabel(0))
        assertEquals("1 minute", appLockDelayLabel(1))
        assertEquals("15 minutes", appLockDelayLabel(15))
        assertEquals("1 hour", appLockDelayLabel(60))
        assertEquals("24 hours", appLockDelayLabel(APP_LOCK_MAX_DELAY_MINUTES))
        assertEquals("2 h 30 min", appLockDelayLabel(150))
    }

    @Test
    fun delaysAreClampedAndCustomDelaysCombineHoursAndMinutes() {
        assertEquals(0, normalizeAppLockDelayMinutes(-5))
        assertEquals(APP_LOCK_MAX_DELAY_MINUTES, normalizeAppLockDelayMinutes(10_000))
        assertEquals(150, appLockDelayMinutes(hours = 2, minutes = 30))
        assertEquals(59, appLockDelayMinutes(hours = -1, minutes = 99))
        assertEquals(APP_LOCK_MAX_DELAY_MINUTES, appLockDelayMinutes(hours = 30, minutes = 59))
        assertEquals(
            APP_LOCK_MAX_DELAY_MINUTES,
            AppLockController(AppLockSettings(enabled = true, delayMinutes = 99_999))
                .settings.delayMinutes,
        )
    }
}
