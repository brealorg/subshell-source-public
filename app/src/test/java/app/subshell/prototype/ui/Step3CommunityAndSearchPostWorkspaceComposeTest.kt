package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.backup.SettingsBackupRepository
import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.RepositoryResult
import app.subshell.prototype.data.RepositoryPageRequest
import app.subshell.prototype.data.SubshellRepository
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (next01a1, next01a2): on an expanded window in split mode, a post
 * opened from a community or from search opens beside its list; the list pane
 * keeps the community (without its own top bar) or the search results
 * (without the search field), and a post tapped in that list replaces the open
 * one instead of stacking. Before a post is chosen the search detail names
 * what to pick for the scope; Back from the post brings the field back. On a
 * phone a tapped post opens full screen with its community or search origin.
 * The community list keeps its place when a post opens beside it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1280dp-h800dp")
class Step3CommunityAndSearchPostWorkspaceComposeTest {
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

    private val query = "boring"

    // A signed-in account reads the community from Reddit (empty here); the
    // community tests use the local account, with the signed-in one kept on the
    // device so the app does not show its sign-in onboarding.
    private val local = AccountSessionSummary(
        stableId = "local:reader",
        username = "reader",
        source = AccountSessionSource.LOCAL_FIXTURE,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Local",
    )

    private fun compose(activeLocal: Boolean = false): SubshellState {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        SettingsBackupRepository(context, appVersion = "test").dismissFolderOnboarding()
        val state = SubshellState(
            repository = CommunityPagesRepository(),
            initialAccountSessions = listOf(account, local),
            initialActiveAccountId = if (activeLocal) local.stableId else account.stableId,
            initialInteractionSideConfigured = true,
        )
        state.updateTabletLayoutMode(TabletLayoutMode.SPLIT)
        composeRule.setContent {
            SubshellApp(videoPlaybackCoordinator = VideoPlaybackCoordinator(context), state = state)
        }
        composeRule.waitForIdle()
        return state
    }

    private fun count(text: String): Int = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun nodes(matcher: SemanticsMatcher): Int = composeRule.onAllNodes(matcher).fetchSemanticsNodes().size

    /** Sample posts whose titles the screen shows right now (in the order SampleData has them). */
    private fun shownPosts(): List<Post> = SampleData.posts.filter { count(it.title) >= 1 }

    /** Taps a post in a list as a reader does: its card (or its title when the title is the target). */
    private fun tapPost(post: Post) {
        val direct = hasText(post.title) and hasClickAction()
        val matcher = if (nodes(direct) > 0) direct else hasClickAction() and hasAnyDescendant(hasText(post.title))
        composeRule.onAllNodes(matcher).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    // homelab's posts a reader sees by title: NSFW posts are hidden and a link
    // card does not show its title as plain text.
    private val homelab = SampleData.posts.filter { it.community == "homelab" && !it.nsfw && it.kind != PostKind.LINK }

    /**
     * Swipes the community's list (the scrollable node holding one of its
     * posts, looked up again each time) until [target] is on screen.
     */
    private fun scrollCommunityListTo(target: Post) {
        val holdsAPost = homelab.map { hasAnyDescendant(hasText(it.title)) }.reduce { a, b -> a or b }
        repeat(30) {
            if (count(target.title) >= 1) return
            composeRule.onAllNodes(hasScrollToNodeAction() and holdsAPost).onFirst().performTouchInput { swipeUp() }
            composeRule.waitForIdle()
        }
    }

    private fun openCommunity(state: SubshellState, name: String) {
        composeRule.runOnIdle { state.openSubreddit(name) }
        // The community's posts load off the main thread.
        // Post cards are tall; at least the first one is on screen.
        composeRule.waitUntil(10_000) { shownPosts().isNotEmpty() }
        composeRule.waitForIdle()
    }

    private fun submitSearch(state: SubshellState, scope: SearchScope = SearchScope.ALL) {
        composeRule.runOnIdle {
            state.openSearch()
            state.updateSearchScope(scope)
            state.updateSearchQuery(query)
            state.submitSearch()
        }
        composeRule.waitForIdle()
    }

    private fun postOrigin(state: SubshellState): FeedPostOrigin? =
        (state.currentNavigationRoute as? SubshellRoute.Post)?.postRoute?.origin

    private val searchField get() = hasSetTextAction() and hasText(query)

    @Test
    fun aCommunityPostOpensBesideTheCommunityAndAnotherReplacesIt() {
        val state = compose(activeLocal = true)
        openCommunity(state, "homelab")
        val communitySearch = "Search ${state.subredditLabel("homelab")}"
        assertEquals("the community's own top bar", 1, nodes(hasContentDescription(communitySearch)))
        val first = homelab[0]
        val second = homelab[1]
        assertTrue("homelab shows its first post", count(first.title) >= 1)

        composeRule.runOnIdle { state.openFromCommunity(first, "homelab") }
        composeRule.waitForIdle()
        val depth = state.navigationRouteDepth
        assertEquals("homelab", (postOrigin(state) as? FeedPostOrigin.Community)?.communityName)
        // The list pane keeps the community but not its top bar.
        assertEquals(0, nodes(hasContentDescription(communitySearch)))
        // The list pane (the first lazy list) still holds the community's posts.
        scrollCommunityListTo(second)
        composeRule.waitForIdle()
        assertTrue("the community list stays beside the post", count(second.title) >= 1)

        tapPost(second)

        assertEquals(depth, state.navigationRouteDepth)
        assertEquals(second.id, state.selectedPost?.id)
        assertEquals("homelab", (postOrigin(state) as? FeedPostOrigin.Community)?.communityName)
    }

    @Test
    fun beforeAPostIsChosenTheSearchDetailNamesWhatToPickForTheScope() {
        val state = compose()
        composeRule.runOnIdle { state.openSearch() }
        composeRule.waitForIdle()
        assertEquals(1, count("Search for a post"))

        submitSearch(state)
        assertEquals(1, count("Select a post"))

        composeRule.runOnIdle { state.updateSearchScope(SearchScope.COMMUNITIES) }
        composeRule.waitForIdle()
        assertEquals(SearchScope.COMMUNITIES, state.searchScope)
        assertEquals(1, count("Select a community"))
        assertEquals(0, count("Select a post"))

        composeRule.runOnIdle { state.updateSearchScope(SearchScope.USERS) }
        composeRule.waitForIdle()
        assertEquals(SearchScope.USERS, state.searchScope)
        assertEquals(1, count("Select a person"))
    }

    @Test
    fun aSearchResultOpensBesideTheResultsWithoutTheSearchFieldAndAnotherReplacesIt() {
        val state = compose()
        submitSearch(state, SearchScope.POSTS)
        assertTrue("the search field holds the query", nodes(searchField) >= 1)
        val listed = shownPosts()
        assertTrue("search shows at least two sample posts: $listed", listed.size >= 2)
        val first = listed[0]
        val second = listed[1]

        composeRule.runOnIdle { state.openFromSearch(first) }
        composeRule.waitForIdle()
        val depth = state.navigationRouteDepth
        assertEquals(query, (postOrigin(state) as? FeedPostOrigin.Search)?.query)
        // The results stay as the list pane, frozen: no search field to edit.
        assertEquals(0, nodes(searchField))
        assertTrue("the results stay beside the post", count(second.title) >= 1)

        tapPost(second)

        assertEquals(depth, state.navigationRouteDepth)
        assertEquals(second.id, state.selectedPost?.id)
        assertEquals(query, (postOrigin(state) as? FeedPostOrigin.Search)?.query)
    }

    @Test
    fun backFromASearchResultBringsTheFieldAndThePlaceholderBack() {
        val state = compose()
        submitSearch(state, SearchScope.POSTS)
        val first = shownPosts().first()
        composeRule.runOnIdle { state.openFromSearch(first) }
        composeRule.waitForIdle()
        assertEquals(0, nodes(searchField))

        composeRule.runOnIdle { state.closePost() }
        composeRule.waitForIdle()

        assertTrue(state.currentNavigationRoute is SubshellRoute.Search)
        assertTrue(nodes(searchField) >= 1)
        assertEquals(1, count("Select a post"))
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun onAPhoneATappedCommunityPostOpensFullScreenWithItsCommunityOrigin() {
        val state = compose(activeLocal = true)
        openCommunity(state, "homelab")
        val communityDepth = state.navigationRouteDepth
        val inCommunity = shownPosts()
        assertTrue("homelab shows a sample post", inCommunity.isNotEmpty())

        tapPost(inCommunity.first())

        assertEquals(communityDepth + 1, state.navigationRouteDepth)
        assertEquals("homelab", (postOrigin(state) as? FeedPostOrigin.Community)?.communityName)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun onAPhoneATappedSearchResultOpensFullScreenWithItsSearchOrigin() {
        val state = compose()
        submitSearch(state, SearchScope.POSTS)
        val searchDepth = state.navigationRouteDepth
        val inSearch = shownPosts()
        assertTrue("search shows a sample post", inSearch.isNotEmpty())

        tapPost(inSearch.first())

        assertEquals(searchDepth + 1, state.navigationRouteDepth)
        assertEquals(query, (postOrigin(state) as? FeedPostOrigin.Search)?.query)
    }

    @Test
    fun theCommunityListKeepsItsPlaceWhenAPostOpensBesideIt() {
        val state = compose(activeLocal = true)
        openCommunity(state, "homelab")
        val far = homelab.last()
        scrollCommunityListTo(far)
        composeRule.waitForIdle()
        assertTrue("scrolled down to the last homelab post", count(far.title) >= 1)
        val beside = homelab.first()

        composeRule.runOnIdle { state.openFromCommunity(beside, "homelab") }
        composeRule.waitForIdle()

        // The list pane is still down at the last post, not back at the top.
        assertTrue("the far post is still in the list pane", count(far.title) >= 1)
    }
}

/**
 * Pages SampleData like FakeSubshellRepository, but a community's first page
 * holds that community's own posts (the shared page starts with tall
 * video-lab cards from another community).
 */
private class CommunityPagesRepository(
    private val delegate: FakeSubshellRepository = FakeSubshellRepository(),
) : SubshellRepository by delegate {
    override fun posts(request: RepositoryPageRequest): RepositoryResult<List<Post>> {
        val result = delegate.posts(request)
        val key = request.queryKey.value
        return if (result is RepositoryResult.Content && "homelab" in key) {
            result.copy(value = SampleData.posts.filter { it.community == "homelab" }, nextCursor = null)
        } else {
            result
        }
    }
}
