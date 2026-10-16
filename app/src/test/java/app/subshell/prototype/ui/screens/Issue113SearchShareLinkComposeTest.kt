package app.subshell.prototype.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.error.LocalViewportErrorHostState
import app.subshell.prototype.ui.error.ViewportErrorHostState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE113_SEARCH_SHARE_LINK_TAKES_THE_LINK_PATH_V1: "Open shared Reddit
 * link" in search did nothing; only the link path asks Reddit where a share
 * link leads (and says so when it cannot), so search hands the link to it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue113SearchShareLinkComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(RetryOnRobolectricRendererFlake())
        .around(composeRule)

    private val alice = AccountSessionSummary(
        stableId = "reddit:alice",
        username = "alice",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val noticeHost = ViewportErrorHostState()
    private val openedLinks = mutableListOf<String>()

    @Test
    fun aShareLinkInSearchTakesTheLinkPath() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val state = SubshellState(
            initialAccountSessions = listOf(alice),
            initialActiveAccountId = alice.stableId,
            initialInteractionSideConfigured = true,
        ).apply {
            openSearch(refocus = false)
            updateSearchQuery("https://www.reddit.com/r/Android/s/AbC123")
        }
        val coordinator = VideoPlaybackCoordinator(context)
        val uriHandler = object : UriHandler {
            override fun openUri(uri: String) {
                openedLinks += uri
            }
        }
        composeRule.setContent {
            CompositionLocalProvider(
                LocalVideoPlaybackCoordinator provides coordinator,
                LocalViewportErrorHostState provides noticeHost,
                LocalUriHandler provides uriHandler,
            ) {
                SearchScreen(state = state, onBack = {})
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Open shared Reddit link").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("https://www.reddit.com/r/Android/s/AbC123"), openedLinks)
        assertNull(noticeHost.currentRequest)
    }
}
