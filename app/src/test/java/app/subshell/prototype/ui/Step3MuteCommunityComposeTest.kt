package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.ui.screens.CommunityScreen
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
 * STEP3 (mute01a2, mute01a3): a muted community says it is hidden from the
 * mixed feeds and opens its mute settings; in about-only mode the posts are
 * replaced by "Posts hidden while muted", in show-posts mode they are not.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3MuteCommunityComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val alice = AccountSessionSummary(
        stableId = "reddit:alice",
        username = "alice",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    private fun state() = SubshellState(
        initialAccountSessions = localFixtureAccountSummaries(listOf("root")) + alice,
        initialActiveAccountId = alice.stableId,
    )

    private fun show(state: SubshellState) {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val coordinator = testVideoPlaybackCoordinator(context)
        composeRule.setContent {
            SubshellTheme {
                CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                    CommunityScreen(
                        communityName = "pics",
                        state = state,
                        onRefreshRedditFeed = { _, _, _, _ -> },
                        onAppendRedditFeed = { _, _, _, _ -> },
                        onBack = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun count(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun aMutedCommunitySaysItIsHiddenAndOpensItsMuteSettings() {
        val state = state()
        state.muteCommunity("pics", null)
        show(state)

        composeRule.onNodeWithText("Hidden from Home, Popular and All").performClick()
        composeRule.waitForIdle()

        assertTrue(count("Change duration") > 0)
    }

    @Test
    fun aboutOnlyModeReplacesThePostsAndShowPostsModeDoesNot() {
        val aboutOnly = state()
        aboutOnly.updateMutedCommunityDirectViewMode(MutedCommunityDirectViewMode.ABOUT_ONLY)
        aboutOnly.muteCommunity("pics", null)
        show(aboutOnly)

        assertTrue(count("Posts hidden while muted") > 0)
    }

    @Test
    fun showPostsModeKeepsThePosts() {
        val showPosts = state()
        showPosts.muteCommunity("pics", null)
        show(showPosts)

        assertEquals(0, count("Posts hidden while muted"))
    }

    @Test
    fun theDirectViewModeIsSaved() {
        val saved = mutableListOf<String>()
        val state = SubshellState(persistMutedCommunityDirectViewMode = { saved += it })

        state.updateMutedCommunityDirectViewMode(MutedCommunityDirectViewMode.ABOUT_ONLY)

        assertEquals(listOf("ABOUT_ONLY"), saved)
        assertEquals(MutedCommunityDirectViewMode.ABOUT_ONLY, state.mutedCommunityDirectViewMode)
    }
}
