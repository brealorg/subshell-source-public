package app.subshell.prototype.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.RedditCommunityDiscoveryUiState
import app.subshell.prototype.data.search.TrendingSearchCache
import app.subshell.prototype.data.search.TrendingSearchSeed
import app.subshell.prototype.data.search.encodeTrendingSearchCache
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.Community
import app.subshell.prototype.model.CommunityDiscoveryItem
import app.subshell.prototype.model.CommunityDiscoverySort
import app.subshell.prototype.ui.SearchIndexEntry
import app.subshell.prototype.ui.SearchIndexKind
import app.subshell.prototype.ui.SubshellRoute
import app.subshell.prototype.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (search01a1, search01b, search01c1, search02a1, search02a11, search02a12):
 * typing asks Reddit for communities once the reader pauses, hiding NSFW ones
 * when NSFW is off; a submitted search asks for the best match and shows its
 * communities, and editing the text afterwards asks only for suggestions; the Posts/Communities filters
 * appear only for a submitted search; "Open r/x" opens the community at once;
 * opening a result is remembered for this account and the newest people come
 * back on the empty search page, where Clear forgets them; a trending topic or
 * a recent search submits it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3SearchWorkspaceComposeTest {
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
    private val loads = mutableListOf<String>()
    private var now = 1_700_000_000_000L
    private val history = mutableMapOf<String, List<String>>()
    private val index = mutableMapOf<String, List<SearchIndexEntry>>()
    private var trending = ""

    private fun state(nsfw: Boolean = false) = SubshellState(
        repository = FakeSubshellRepository(),
        initialAccountSessions = listOf(account),
        initialActiveAccountId = account.stableId,
        initialInteractionSideConfigured = true,
        initialShowNsfwPosts = nsfw,
        loadSearchHistory = { history[it].orEmpty() },
        persistSearchHistory = { id, list -> history[id] = list },
        loadSearchIndex = { index[it].orEmpty() },
        persistSearchIndex = { id, list -> index[id] = list },
        loadTrendingSearchCache = { trending },
        persistTrendingSearchCache = { trending = it },
        currentTimeMillis = { now },
    ).apply {
        openSearch(refocus = false)
    }

    private fun show(state: SubshellState, discovery: RedditCommunityDiscoveryUiState = RedditCommunityDiscoveryUiState()) {
        val coordinator = VideoPlaybackCoordinator(ApplicationProvider.getApplicationContext<Application>())
        composeRule.setContent {
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                SearchScreen(
                    state = state,
                    redditCommunityDiscoveryState = discovery,
                    onLoadRedditCommunityDiscovery = { _, query, sort, over18, _ -> loads += "$query|$sort|$over18" },
                    onBack = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun click(matcher: SemanticsMatcher) {
        val lists = composeRule.onAllNodes(hasScrollToNodeAction())
        for (i in 0 until lists.fetchSemanticsNodes().size) {
            if (runCatching { lists[i].performScrollToNode(matcher) }.isSuccess) break
        }
        composeRule.onAllNodes(matcher).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun count(text: String) = composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    private fun chips() = composeRule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)).fetchSemanticsNodes().size

    private fun community(name: String, nsfw: Boolean = false) = CommunityDiscoveryItem(
        community = Community(id = "t5_$name", name = name, description = "", members = "1k", accent = 0xFF336699),
        nsfw = nsfw,
    )

    @Test
    fun typingAsksForCommunitiesOnceTheReaderPauses() {
        val state = state()
        show(state)
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { state.updateSearchQuery("ca") }
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.runOnIdle { state.updateSearchQuery("cats") }
        composeRule.mainClock.advanceTimeBy(100)
        assertEquals(emptyList<String>(), loads)

        composeRule.mainClock.advanceTimeBy(300)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        // Communities (with the NSFW choice) and people, once each.
        assertEquals(setOf("cats|null|false", "cats|null|null"), loads.toSet())
        assertEquals(2, loads.size)
    }

    @Test
    fun nsfwCommunitiesAreHiddenWhenNsfwIsOff() {
        val answer = RedditCommunityDiscoveryUiState(
            accountStableId = account.stableId,
            searchQuery = "cats",
            searchSort = null,
            autocompleteIncludeOver18 = false,
            items = listOf(community("catsfans"), community("catsgonewild", nsfw = true)),
            loaded = true,
            contentRevision = 1L,
        )
        val state = state().apply { updateSearchQuery("cats") }
        show(state, answer)

        assertTrue(count("catsfans") >= 1)
        assertEquals(0, count("catsgonewild"))
    }

    @Test
    fun aSubmittedSearchAsksForTheBestMatchAndEditingAsksOnlyForSuggestions() {
        val answer = RedditCommunityDiscoveryUiState(
            accountStableId = account.stableId,
            searchQuery = "cats",
            searchSort = CommunityDiscoverySort.RELEVANCE,
            items = listOf(community("catsfromreddit")),
            loaded = true,
            contentRevision = 1L,
        )
        val state = state().apply {
            updateSearchQuery("cats")
            submitSearch()
        }
        show(state, answer)
        assertTrue(loads.toString(), "cats|RELEVANCE|null" in loads)
        assertTrue(chips() >= 2)
        assertTrue(count("catsfromreddit") >= 1)

        composeRule.runOnIdle { state.updateSearchQuery("dogs") }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()

        assertTrue(loads.toString(), "dogs|null|false" in loads)
        assertTrue(loads.none { it.startsWith("dogs|RELEVANCE") })
    }

    @Test
    fun filtersAppearOnlyForASubmittedSearch() {
        val state = state().apply { updateSearchQuery("cats") }
        show(state)
        assertEquals(0, chips())

        composeRule.runOnIdle { state.submitSearch() }
        composeRule.waitForIdle()
        assertTrue(chips() >= 2)
    }

    @Test
    fun openRxOpensTheCommunityAtOnce() {
        val state = state().apply { updateSearchQuery("r/android") }
        show(state)

        click(hasText("Open r/android") and hasClickAction())

        assertEquals("android", (state.currentNavigationRoute as SubshellRoute.Community).communityName)
        assertEquals("r/android", state.recentSearches.first())
    }

    @Test
    fun openingAResultIsRememberedAndTheNewestPeopleComeBack() {
        val state = state()
        repeat(5) { i ->
            now += 1_000
            state.recordSearchSelection(SearchIndexKind.USER, "person$i", "u/person$i")
        }
        state.recordSearchSelection(SearchIndexKind.COMMUNITY, "kotlin", "r/kotlin")
        show(state)

        assertEquals(1, count("Recent people"))
        assertEquals(0, count("person0"))
        assertTrue(count("person4") >= 1)

        click(hasText("Clear") and hasClickAction())
        assertEquals(listOf(SearchIndexKind.COMMUNITY), state.searchIndexEntries.map { it.kind })
        assertEquals(listOf("kotlin"), index[account.stableId].orEmpty().map { it.key })
    }

    @Test
    fun aTrendingTopicSubmitsIt() {
        trending = encodeTrendingSearchCache(TrendingSearchCache(fetchedAtEpochMs = now - 60_000L, seeds = listOf(TrendingSearchSeed("Kotlin 2.3"))))
        val state = state()
        show(state)

        assertEquals(1, count("Trending now"))
        assertTrue(count("Google Trends") >= 1)
        click(hasText("Kotlin 2.3") and hasClickAction())
        assertEquals("Kotlin 2.3", state.searchSubmittedQuery)
    }

    @Test
    fun aRecentSearchSubmitsIt() {
        history[account.stableId] = listOf("volvo")
        val state = state()
        show(state)

        click(hasText("volvo") and hasClickAction())

        assertEquals("volvo", state.searchSubmittedQuery)
    }

    // STEP3 (search01a1, search01c1): a submitted search for everything comes
    // back as it was after the process is recreated.
    @Test
    fun aSubmittedSearchForEverythingSurvivesProcessRestore() {
        val state = state().apply {
            updateSearchQuery("cats")
            submitSearch()
        }

        val restored = SubshellState(
            repository = FakeSubshellRepository(),
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialProcessSnapshotToken = app.subshell.prototype.ui.encodeSubshellProcessSnapshot(state.processStateSnapshot()),
        )

        val route = restored.currentNavigationRoute as SubshellRoute.Search
        assertEquals(app.subshell.prototype.ui.SearchScope.ALL, route.scope)
        assertEquals("cats", restored.searchSubmittedQuery)
    }
}
