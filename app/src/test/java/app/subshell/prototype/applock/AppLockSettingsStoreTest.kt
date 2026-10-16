package app.subshell.prototype.applock

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Issue #38: the app lock setting is stored on the device only. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppLockSettingsStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun offWithOneMinuteByDefault() {
        assertEquals(
            AppLockSettings(enabled = false, delayMinutes = APP_LOCK_DEFAULT_DELAY_MINUTES),
            AppLockSettingsStore.read(context),
        )
    }

    @Test
    fun roundTripsACustomDelay() {
        val settings = AppLockSettings(enabled = true, delayMinutes = appLockDelayMinutes(2, 30))
        AppLockSettingsStore.write(context, settings)
        assertEquals(settings, AppLockSettingsStore.read(context))
    }

    @Test
    fun roundTripsHidingNotificationContentWhichOnlyCountsWhileTheLockIsOn() {
        val settings = AppLockSettings(enabled = true, delayMinutes = 5, hideNotificationContent = true)
        AppLockSettingsStore.write(context, settings)
        assertEquals(settings, AppLockSettingsStore.read(context))
        assertEquals(true, settings.hidesNotificationContent)
        assertEquals(false, settings.copy(enabled = false).hidesNotificationContent)
    }

    @Test
    fun anOutOfRangeStoredDelayIsClamped() {
        context.getSharedPreferences(APP_LOCK_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(APP_LOCK_ENABLED_KEY, true)
            .putInt(APP_LOCK_DELAY_MINUTES_KEY, -3)
            .commit()
        assertEquals(AppLockSettings(enabled = true, delayMinutes = 0), AppLockSettingsStore.read(context))
    }

    @Test
    fun staysOutOfTheSharedSettingsFile() {
        AppLockSettingsStore.write(context, AppLockSettings(enabled = true, delayMinutes = 5))
        val shared = context.getSharedPreferences("subshell_preferences", Context.MODE_PRIVATE)
        assertEquals(emptySet<String>(), shared.all.keys.filter { it.startsWith("app_lock") }.toSet())
    }
}
