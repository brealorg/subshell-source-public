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
import app.subshell.prototype.media.VideoPlaybackCoordinator
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
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
        )
        val picks = mutableListOf<String>()
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        composeRule.setContent {
            SubshellApp(
                videoPlaybackCoordinator = VideoPlaybackCoordinator(context),
                state = state,
                redditCommunity = CommunityEntryPoints(
                    randomNsfwCommunity = { accountId ->
                        picks += accountId
                        "nsfwexample"
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

    @Test
    fun eachPlacementShowsOnlyThere() {
        val state = SubshellState(
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
