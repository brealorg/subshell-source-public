package app.subshell.prototype

import android.app.Application
import android.app.KeyguardManager
import android.hardware.biometrics.BiometricManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.Lifecycle
import app.subshell.prototype.applock.AppLockSettings
import app.subshell.prototype.applock.AppLockSettingsStore
import app.subshell.prototype.applock.AppLockViewModel
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowBiometricManager

/**
 * Issue #38: the app lock on the real MainActivity. The app stays composed
 * under the lock (the connect gate is what a fresh JVM install shows) but is
 * covered and out of the semantics tree until unlock.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class AppLockActivityTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()

    /** Runs before the activity starts: the stored lock and whether the phone has a screen lock. */
    private val device = object : TestWatcher() {
        override fun starting(description: Description) {
            val context = ApplicationProvider.getApplicationContext<Application>()
            ensureWorkManagerInitialized(context)
            val method = description.methodName
            AppLockSettingsStore.write(
                context,
                AppLockSettings(
                    enabled = !method.startsWith("off"),
                    delayMinutes = if (method.contains("Immediately")) 0 else 1,
                ),
            )
            val screenLock = !method.contains("WithoutAScreenLock")
            shadowOf(context.getSystemService(KeyguardManager::class.java)).setIsDeviceSecure(screenLock)
            Shadow.extract<ShadowBiometricManager>(context.getSystemService(BiometricManager::class.java)).apply {
                setCanAuthenticate(screenLock)
                // canAuthenticate(int) only compares against this type.
                setAuthenticatorType(
                    if (screenLock) {
                        BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    } else {
                        0
                    },
                )
            }
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(device)
        .around(RetryOnRobolectricRendererFlake())
        .around(composeRule)

    /** The lock is a dialog window: its content composes a frame after the app's. */
    private fun awaitLockScreen() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("app-lock-screen").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(controller().locked)
    }

    private fun controller() =
        ViewModelProvider(composeRule.activity)[AppLockViewModel::class.java].controller(composeRule.activity)

    @Test
    fun onCoversTheAppAtLaunch() {
        awaitLockScreen()
        composeRule.onNodeWithTag("app-lock-screen").assertExists()
        composeRule.onNodeWithText("subshell is locked").assertExists()
        composeRule.onNodeWithText("Connect subshell to Reddit").assertDoesNotExist()
        assertTrue(controller().locked)
    }

    @Test
    fun onUnlockShowsTheSameAppAgain() {
        awaitLockScreen()
        composeRule.runOnIdle { controller().unlock() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("app-lock-screen").assertDoesNotExist()
        composeRule.onNodeWithText("Connect subshell to Reddit").assertIsDisplayed()
    }

    @Test
    fun onUnlockedSurvivesAConfigurationChange() {
        awaitLockScreen()
        composeRule.runOnIdle { controller().unlock() }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("app-lock-screen").assertDoesNotExist()
        composeRule.onNodeWithText("Connect subshell to Reddit").assertIsDisplayed()
    }

    @Test
    fun onImmediatelyLocksWhenLeavingButNotForATripSubshellStarted() {
        awaitLockScreen()
        composeRule.runOnIdle { controller().unlock() }
        val scenario = composeRule.activityRule.scenario

        composeRule.runOnIdle {
            composeRule.activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")))
        }
        scenario.moveToState(Lifecycle.State.CREATED)
        scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()
        assertFalse(controller().locked)

        scenario.moveToState(Lifecycle.State.CREATED)
        assertTrue(controller().locked)
    }

    @Test
    fun onOpensWithoutAScreenLockBecauseTheLockCannotBeEnforced() {
        composeRule.waitForIdle()
        assertFalse(controller().locked)
        composeRule.onNodeWithText("Connect subshell to Reddit").assertIsDisplayed()
    }

    @Test
    fun offNeverShowsTheLock() {
        composeRule.onNodeWithTag("app-lock-screen").assertDoesNotExist()
        composeRule.onNodeWithText("Connect subshell to Reddit").assertIsDisplayed()
    }
}
