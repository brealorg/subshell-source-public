package app.subshell.prototype.ui.app

import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.subshell.prototype.data.account.RedditAccessRefusal
import app.subshell.prototype.data.account.RedditAccessRefusalState
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.ui.error.ViewportErrorHostState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE32_REDDIT_REFUSED_ACCESS_IS_ONE_NOTICE_V1 (#32): while Reddit refuses
 * the app's Client ID the tray carries one notice; Try again lets one check
 * through and asks Reddit; the notice leaves when access is back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue32RedditAccessRefusedNoticeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    @Before
    fun noRefusal() {
        RedditAccessRefusal.resetForTest()
    }

    @After
    fun clearRefusal() {
        RedditAccessRefusal.resetForTest()
    }

    @Test
    fun theNoticeShowsWhileRefusedTryAgainChecksAndItLeavesWhenAccessIsBack() {
        val host = ViewportErrorHostState()
        var checks = 0
        composeRule.setContent {
            RedditAccessRefusedNotice(noticeHost = host, checkAccess = { checks += 1 })
        }
        composeRule.waitForIdle()
        assertNull(host.currentRequest)

        RedditAccessRefusal.resetForTest(RedditAccessRefusalState(1L, "fingerprint"))
        // The check of this process is used up, as after a refused answer.
        RedditAccessRefusal.markRefused("fingerprint", 1L)
        composeRule.waitForIdle()
        assertEquals(REDDIT_ACCESS_REFUSED_SCOPE, host.currentRequest?.scopeKey)
        assertEquals("Reddit has shut out this app's Client ID", host.currentRequest?.presentation?.title)
        assertEquals(true, RedditAccessRefusal.blocks("fingerprint"))

        host.retryCurrent()
        composeRule.waitForIdle()
        assertEquals(1, checks)
        // Try again let one check through.
        assertEquals(false, RedditAccessRefusal.blocks("fingerprint"))

        RedditAccessRefusal.markAllowed()
        composeRule.waitForIdle()
        assertNull(host.currentRequest)
    }
}
