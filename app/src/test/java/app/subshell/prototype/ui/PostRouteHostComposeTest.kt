package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.PostKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Characterisation of the Post route branch of SubshellApp's route dispatch
 * in the expanded window, written before it moves to PostRouteHost. Current
 * behaviour, not a specification: a post opened from a community is the
 * detail pane of a two-pane workspace titled after that community, a post
 * opened from search is the detail of the Search workspace, a post opened
 * from the Home feed keeps the Home list with its source · sort header
 * (ExpandedSurfacesComposeTest), and closing returns to the origin.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1280dp-h800dp")
class PostRouteHostComposeTest {
    private val composeRule = createComposeRule()

    // The search route trips a Robolectric renderer flake now and then; see the rule.
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private fun count(text: String): Int = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun postsOpenedFromACommunityAndFromSearchAreTwoPaneDetails() {
        val account = AccountSessionSummary(
            stableId = "reddit:test",
            username = "connected-user",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        composeRule.setContent {
            SubshellApp(videoPlaybackCoordinator = VideoPlaybackCoordinator(context), state = state)
        }
        composeRule.waitForIdle()
        val post = SampleData.posts.first { it.kind == PostKind.TEXT }

        // From a community: the community is the list pane title.
        state.openSubreddit("homelab")
        composeRule.waitForIdle()
        val label = state.subredditLabel("homelab")
        val labelsInCommunity = count(label)
        state.openFromCommunity(post, "homelab")
        composeRule.waitForIdle()
        assertTrue(state.currentNavigationRoute is SubshellRoute.Post)
        assertTrue(count(post.title) >= 1)
        assertTrue(count(label) >= labelsInCommunity)
        state.closePost()
        composeRule.waitForIdle()
        assertTrue(state.currentNavigationRoute is SubshellRoute.Community)
        state.closeSubreddit()
        composeRule.waitForIdle()

        // From search: the Search workspace stays as the list pane.
        state.openSearch()
        composeRule.waitForIdle()
        val searchTitles = count("Search")
        state.openFromSearch(post)
        composeRule.waitForIdle()
        assertTrue(state.currentNavigationRoute is SubshellRoute.Post)
        assertEquals(searchTitles, count("Search"))
        assertTrue(count(post.title) >= 1)
        assertEquals(0, count("Search for a post"))
        state.closePost()
        composeRule.waitForIdle()
        assertTrue(state.currentNavigationRoute is SubshellRoute.Search)
    }
}
