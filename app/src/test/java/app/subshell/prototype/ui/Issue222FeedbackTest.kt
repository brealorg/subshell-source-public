package app.subshell.prototype.ui

import android.os.Build
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.subshell.prototype.ui.components.InlineErrorNotice
import app.subshell.prototype.ui.components.ViewportErrorTrayHost
import app.subshell.prototype.ui.error.TrayTone
import app.subshell.prototype.ui.error.ViewportErrorHostState
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Issue #222: feedback goes to the tray, not to Toasts or dialogs; a notice
 * that is not an error has the tray's neutral tone (owner decision).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue222FeedbackTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun confirmationsAreNeutralAndFailuresAreErrors() {
        val host = ViewportErrorHostState()
        host.noticeInfo("Muted r/android", "Its posts are hidden from your feeds")
        assertEquals(TrayTone.INFO, host.currentRequest?.tone)
        host.noticeFailure("Couldn't share this post", "No app on this device can share it")
        assertEquals(TrayTone.ERROR, host.currentRequest?.tone)
        assertEquals("Couldn't share this post", host.currentRequest?.presentation?.title)
    }

    @Test
    fun theSameNoticeShowsAgainAfterItWasDismissed() {
        val host = ViewportErrorHostState()
        host.noticeInfo("Muted r/android", "Its posts are hidden from your feeds")
        host.dismissCurrent()
        host.noticeInfo("Muted r/android", "Its posts are hidden from your feeds")
        assertEquals("Muted r/android", host.currentRequest?.presentation?.title)
    }

    @Test
    fun aCopyIsConfirmedOnlyWhereAndroidDoesNotConfirmItItself() {
        val host = ViewportErrorHostState()
        host.noticeCopied("Link", sdkInt = Build.VERSION_CODES.TIRAMISU)
        assertNull(host.currentRequest)
        host.noticeCopied("Link", sdkInt = Build.VERSION_CODES.S_V2)
        assertEquals("Link copied", host.currentRequest?.presentation?.title)
        assertEquals(TrayTone.INFO, host.currentRequest?.tone)
    }

    @Test
    fun aNeutralNoticeIsDrawnWithoutTheErrorLabel() {
        val host = ViewportErrorHostState()
        host.noticeInfo("Press Back again to exit", "Back once more leaves Subshell")
        composeRule.setContent { SubshellTheme { ViewportErrorTrayHost(state = host) } }
        composeRule.onNodeWithContentDescription("Dismiss notice").assertExists()
        composeRule.onNodeWithContentDescription("Dismiss error").assertDoesNotExist()
    }

    @Test
    fun theInlineErrorNoticeShowsItsMessageAndAction() {
        var tapped = false
        composeRule.setContent {
            SubshellTheme {
                InlineErrorNotice(
                    message = "Reddit rejected the invitation.",
                    title = "Couldn't invite",
                    actionLabel = "Retry",
                    onAction = { tapped = true },
                )
            }
        }
        composeRule.onNodeWithText("Reddit rejected the invitation.").assertExists()
        composeRule.onNodeWithText("Retry").performClick()
        assertEquals(true, tapped)
    }
}
