package app.subshell.prototype

import android.app.Application
import android.os.Build
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.applock.APP_LOCK_ENABLED_KEY
import app.subshell.prototype.applock.APP_LOCK_PREFERENCES
import app.subshell.prototype.auth.AuthRuntimePreparation
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/**
 * SECAUDIT10 (security audit #10): below Android 13 the recents card showed
 * the last screen while the app lock's delay ran. With the app lock on, the
 * window is secure while Subshell is in the background and plain on return.
 * (MainActivity cannot start under Robolectric's API 30, whose merged test
 * manifest lacks androidx.core's receiver permission, so it starts on 36 and
 * the pause runs with SDK_INT set to 30.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class SecAudit10RecentsCardTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun prepare() {
        ensureWorkManagerInitialized(application)
        AuthRuntimePreparation.clearForTests()
        runBlocking { runCatching { AuthRuntimePreparation.prepare(application).await() } }
    }

    @After
    fun forgetTheAuthRuntime() {
        AuthRuntimePreparation.clearForTests()
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 36)
    }

    private fun onAndroid11() = ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 30)

    private fun appLock(enabled: Boolean) {
        application.getSharedPreferences(APP_LOCK_PREFERENCES, 0).edit()
            .putBoolean(APP_LOCK_ENABLED_KEY, enabled).commit()
    }

    private fun MainActivity.secure() =
        window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0

    @Test
    fun theWindowIsSecureOnlyWhileInTheBackground() {
        appLock(enabled = true)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        assertFalse(controller.get().secure())
        onAndroid11()

        controller.pause()
        assertTrue(controller.get().secure())

        controller.resume()
        assertFalse(controller.get().secure())
        controller.pause().stop().destroy()
    }

    @Test
    fun withoutTheAppLockTheWindowStaysPlain() {
        appLock(enabled = false)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        onAndroid11()

        controller.pause()
        assertFalse(controller.get().secure())
        controller.stop().destroy()
    }

    @Test
    fun android13AndPictureInPictureNeedNoSecureWindow() {
        assertTrue(appLockHidesRecentsWithSecureFlag(sdkInt = 32, appLockEnabled = true, inPictureInPicture = false))
        assertFalse(appLockHidesRecentsWithSecureFlag(sdkInt = 33, appLockEnabled = true, inPictureInPicture = false))
        assertFalse(appLockHidesRecentsWithSecureFlag(sdkInt = 30, appLockEnabled = true, inPictureInPicture = true))
        assertEquals(false, appLockHidesRecentsWithSecureFlag(sdkInt = 26, appLockEnabled = false, inPictureInPicture = false))
    }
}
