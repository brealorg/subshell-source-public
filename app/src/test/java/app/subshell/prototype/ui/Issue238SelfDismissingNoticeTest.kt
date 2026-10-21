package app.subshell.prototype.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.subshell.prototype.ui.components.ViewportErrorTrayHost
import app.subshell.prototype.ui.error.TrayTone
import app.subshell.prototype.ui.error.ViewportErrorHostState
import app.subshell.prototype.ui.error.presentNotice
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Issue #238 (owner): "Muted r/x" and the other confirmations leave the tray
 * by themselves after Google's short snackbar time (4 s, longer when an
 * accessibility service asks for it); failures stay until they are closed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue238SelfDismissingNoticeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun showTray(host: ViewportErrorHostState, accessibility: AccessibilityManager? = null) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            SubshellTheme {
                if (accessibility == null) {
                    ViewportErrorTrayHost(state = host)
                } else {
                    CompositionLocalProvider(LocalAccessibilityManager provides accessibility) {
                        ViewportErrorTrayHost(state = host)
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun advance(millis: Long) {
        composeRule.mainClock.advanceTimeBy(millis)
        composeRule.waitForIdle()
    }

    @Test
    fun aConfirmationLeavesTheTrayAfterFourSeconds() {
        val host = ViewportErrorHostState()
        host.noticeInfo("Muted r/android", "Its posts are hidden from your feeds")
        showTray(host)

        advance(3_500)
        assertEquals("Muted r/android", host.currentRequest?.presentation?.title)
        advance(1_000)
        assertNull(host.currentRequest)
    }

    @Test
    fun aFailureStaysUntilItIsClosed() {
        val host = ViewportErrorHostState()
        host.noticeFailure("Couldn't share this post", "No app on this device can share it")
        showTray(host)

        advance(30_000)
        assertEquals("Couldn't share this post", host.currentRequest?.presentation?.title)
    }

    @Test
    fun aConfirmationBehindAFailureWaitsUntilItIsShown() {
        val host = ViewportErrorHostState()
        host.noticeInfo("Muted r/android", "Its posts are hidden from your feeds")
        host.presentNotice("share", "Couldn't share this post", "No app on this device can share it", TrayTone.ERROR)
        showTray(host)

        advance(10_000)
        composeRule.runOnIdle { host.clear("share") }
        // The tray draws the confirmation in the next frame; its time starts there.
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeByFrame()
        advance(3_500)
        assertEquals("Muted r/android", host.currentRequest?.presentation?.title)
        advance(1_000)
        assertNull(host.currentRequest)
    }

    @Test
    fun anAccessibilityServiceGetsTheTimeAndroidRecommends() {
        val host = ViewportErrorHostState()
        host.noticeInfo("Muted r/android", "Its posts are hidden from your feeds")
        val talkBack = object : AccessibilityManager {
            override fun calculateRecommendedTimeoutMillis(
                originalTimeoutMillis: Long,
                containsIcons: Boolean,
                containsText: Boolean,
                containsControls: Boolean,
            ): Long = 12_000L
        }
        showTray(host, talkBack)

        advance(10_000)
        assertEquals("Muted r/android", host.currentRequest?.presentation?.title)
        advance(2_500)
        assertNull(host.currentRequest)
    }
}
