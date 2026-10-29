package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.RandomNsfwCommunityPick
import app.subshell.prototype.ui.app.CommunityEntryPoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE211_RANDOM_NSFW_COMMUNITY_V1 (#211): "Random NSFW community" shows
 * only at the placement the reader chose (owner: a choice in Settings), only
 * while NSFW is shown, and opens the community it is given.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue211RandomNsfwCommunityComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val account = AccountSessionSummary(
        stableId = "reddit:test",
        username = "connected-user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    @Test
    fun theMenuOffersItOnlyWithBothSettingsAndOpensThePick() {
        val state = SubshellState(
            // ISSUE271: an install that has chosen its side shows Home, not the setup flow.
            initialInteractionSideConfigured = true,
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
        )
        val picks = mutableListOf<String>()
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        composeRule.setContent {
            SubshellApp(
                videoPlaybackCoordinator = testVideoPlaybackCoordinator(context),
                state = state,
                redditCommunity = CommunityEntryPoints(
                    randomNsfwCommunity = { accountId ->
                        picks += accountId
                        RandomNsfwCommunityPick.Found("nsfwexample")
                    },
                ),
            )
        }
        state.requestDrawer()
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithText("Random NSFW community").fetchSemanticsNodes().isEmpty())

        state.updateRandomNsfwCommunityPlacement(RandomNsfwCommunityPlacement.MENU)
        composeRule.waitForIdle()
        assertTrue("hidden while NSFW is off", composeRule.onAllNodesWithText("Random NSFW community").fetchSemanticsNodes().isEmpty())

        state.updateShowNsfwPosts(true)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Random NSFW community").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("reddit:test"), picks)
        assertEquals("nsfwexample", state.selectedSubreddit)
    }

    // ISSUE263_NO_NSFW_COMMUNITY_OFFERED_IS_NOT_A_FAILURE_V1 (#263): when
    // Reddit offers no NSFW community the notice says so and does not ask
    // the reader to try again; a failed read still does.
    @Test
    fun noneOfferedSaysRedditOfferedNoneAndAFailureAsksToTryAgain() {
        val state = SubshellState(
            // ISSUE271: an install that has chosen its side shows Home, not the setup flow.
            initialInteractionSideConfigured = true,
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
        )
        var answer: RandomNsfwCommunityPick = RandomNsfwCommunityPick.NoneOffered(popularCommunities = 49, searchedCommunities = 25)
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        composeRule.setContent {
            SubshellApp(
                videoPlaybackCoordinator = testVideoPlaybackCoordinator(context),
                state = state,
                redditCommunity = CommunityEntryPoints(randomNsfwCommunity = { answer }),
            )
        }
        state.updateRandomNsfwCommunityPlacement(RandomNsfwCommunityPlacement.MENU)
        state.updateShowNsfwPosts(true)
        state.requestDrawer()
        composeRule.waitForIdle()
        val before = state.selectedSubreddit
        composeRule.onNodeWithText("Random NSFW community").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Reddit offered no NSFW communities").assertExists()
        composeRule.onNodeWithText("trying again won't change that", substring = true).assertExists()
        assertTrue(composeRule.onAllNodesWithText("Try again in a moment", substring = true).fetchSemanticsNodes().isEmpty())
        assertEquals(before, state.selectedSubreddit)

        answer = RandomNsfwCommunityPick.Failed
        state.requestDrawer()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Random NSFW community").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Couldn't find an NSFW community").assertExists()
        composeRule.onNodeWithText("Couldn't reach Reddit. Try again in a moment.").assertExists()
        assertTrue(composeRule.onAllNodesWithText("Reddit offered no NSFW communities").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun eachPlacementShowsOnlyThere() {
        val state = SubshellState(
            // ISSUE271: an install that has chosen its side shows Home, not the setup flow.
            initialInteractionSideConfigured = true,
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
        )
        state.updateShowNsfwPosts(true)
        assertTrue(RandomNsfwCommunityPlacement.entries.none { state.offersRandomNsfwCommunity(it) })
        state.updateRandomNsfwCommunityPlacement(RandomNsfwCommunityPlacement.SEARCH)
        assertEquals(
            listOf(RandomNsfwCommunityPlacement.SEARCH),
            RandomNsfwCommunityPlacement.entries.filter { state.offersRandomNsfwCommunity(it) },
        )
        state.updateRandomNsfwCommunityPlacement(RandomNsfwCommunityPlacement.COMMUNITIES)
        assertEquals(
            listOf(RandomNsfwCommunityPlacement.COMMUNITIES),
            RandomNsfwCommunityPlacement.entries.filter { state.offersRandomNsfwCommunity(it) },
        )
        state.updateShowNsfwPosts(false)
        assertTrue(RandomNsfwCommunityPlacement.entries.none { state.offersRandomNsfwCommunity(it) })
    }
}
