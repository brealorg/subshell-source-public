package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.ui.screens.HomeScreen
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (mute01a1, mute01a2): Home leaves out a muted community's posts, says
 * so when mutes leave nothing, and a timed mute ends by itself while Home is
 * open (no background scheduler, no refresh).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3MuteHomeComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private var now = 1_800_000_000_000L
    private val state = SubshellState(currentTimeMillis = { now })

    private fun showHome() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val coordinator = VideoPlaybackCoordinator(context)
        composeRule.setContent {
            SubshellTheme {
                CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                    HomeScreen(state = state)
                }
            }
        }
    }

    private fun shown(title: String) = composeRule.onAllNodesWithText(title).fetchSemanticsNodes().size

    private val firstPost get() = state.posts.first()
    private val firstCommunity get() = firstPost.community.removePrefix("r/")

    @Test
    fun aMutedCommunitysPostsLeaveHome() {
        showHome()
        composeRule.waitForIdle()
        assertTrue(shown(firstPost.title) > 0)

        state.muteCommunity(firstCommunity, null)
        composeRule.waitForIdle()

        assertEquals(0, shown(firstPost.title))
    }

    // The mute projection is logged in debug builds with counts only; a post
    // title never reaches the log.
    @Test
    fun theMuteLogNamesNoPostTitles() {
        org.robolectric.shadows.ShadowLog.clear()
        showHome()
        composeRule.waitForIdle()
        state.muteCommunity(firstCommunity, null)
        composeRule.waitForIdle()

        val lines = org.robolectric.shadows.ShadowLog.getLogsForTag("SubshellMute").map { it.msg }
        assertTrue(lines.isNotEmpty())
        val titles = state.posts.map { it.title }
        lines.forEach { line -> titles.forEach { title -> assertTrue(line, !line.contains(title)) } }
    }

    @Test
    fun whenMutesLeaveNothingHomeSaysSo() {
        showHome()
        composeRule.waitForIdle()

        state.posts.map { it.community.removePrefix("r/") }.distinct().forEach { name ->
            state.muteCommunity(name, null)
        }
        composeRule.waitForIdle()

        assertTrue(shown("Muted communities fill this page") > 0)
    }

    @Test
    fun aTimedMuteEndsByItselfWhileHomeIsOpen() {
        state.muteCommunity(firstCommunity, now + 1_000L)
        composeRule.mainClock.autoAdvance = false
        showHome()
        composeRule.mainClock.advanceTimeBy(100)
        assertEquals(0, shown(firstPost.title))

        now += 2_000L
        composeRule.mainClock.advanceTimeBy(1_200)

        assertTrue(shown(firstPost.title) > 0)
    }
}
