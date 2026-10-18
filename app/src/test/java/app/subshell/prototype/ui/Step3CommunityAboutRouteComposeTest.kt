package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.RedditCommunityInfoUiState
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.CommunityInfoSnapshot
import app.subshell.prototype.model.CommunityModerator
import app.subshell.prototype.model.CommunityRule
import app.subshell.prototype.ui.app.CommunityEntryPoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (communityinfo01a3, a31): the app hands the community information and
 * its loader through to the community screen's About sheet, and a moderator
 * opened from the sheet returns, on Back, to the community with the sheet
 * still open on the Moderators tab.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3CommunityAboutRouteComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val account = AccountSessionSummary(
        stableId = "reddit:test",
        username = "connected-user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val loads = mutableListOf<Triple<String, String, Boolean>>()

    private fun show(): SubshellState {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        val info = RedditCommunityInfoUiState(
            accountStableId = account.stableId,
            communityName = "homelab",
            snapshot = CommunityInfoSnapshot(
                communityName = "homelab",
                rules = listOf(CommunityRule("No spam")),
                moderators = listOf(CommunityModerator("mod_one")),
            ),
            loaded = true,
        )
        composeRule.setContent {
            SubshellApp(
                videoPlaybackCoordinator = testVideoPlaybackCoordinator(context),
                state = state,
                redditCommunity = CommunityEntryPoints(
                    redditCommunityInfoState = info,
                    onLoadRedditCommunityInfo = { accountId, community, force -> loads += Triple(accountId, community, force) },
                ),
            )
        }
        composeRule.runOnIdle { state.openSubreddit("homelab") }
        composeRule.waitForIdle()
        return state
    }

    private fun click(matcher: SemanticsMatcher) {
        composeRule.onAllNodes(matcher).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun count(text: String) = composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    @Test
    fun theAppHandsTheInformationAndItsLoaderToTheCommunity() {
        show()

        click(hasContentDescription("About r/homelab"))
        click(hasText("Rules") and hasClickAction())

        assertEquals(listOf(Triple("reddit:test", "homelab", false)), loads)
        assertTrue(count("No spam") >= 1)
    }

    @Test
    fun aModeratorsProfileReturnsToTheOpenSheet() {
        val state = show()
        click(hasContentDescription("About r/homelab"))
        click(hasText("Moderators") and hasClickAction())

        click(hasText("u/mod_one") and hasClickAction())
        assertTrue(state.currentNavigationRoute.toString(), state.currentNavigationRoute is SubshellRoute.User)

        composeRule.runOnIdle { state.navigateBack() }
        composeRule.waitForIdle()

        assertTrue(state.currentNavigationRoute is SubshellRoute.Community)
        assertTrue(count("About r/homelab") >= 1)
        assertTrue(count("u/mod_one") >= 1)
    }
}
