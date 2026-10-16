package app.subshell.prototype.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.IdentityMetadataRepository
import app.subshell.prototype.data.RepositoryResult
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.RedditCommunityDiscoveryUiState
import app.subshell.prototype.data.account.RedditPeopleSearchUiState
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.Community
import app.subshell.prototype.model.CommunityDiscoveryItem
import app.subshell.prototype.model.CommunityDiscoverySort
import app.subshell.prototype.model.RedditPeopleSearchItem
import app.subshell.prototype.model.UserFixture
import app.subshell.prototype.ui.SubshellRoute
import app.subshell.prototype.ui.SubshellState
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role

/**
 * STEP3 (search01c2): a signed-in search for everything lists communities,
 * then people, then posts, previewing three communities and two people;
 * tapping a person opens that profile without looking up every row's
 * identity; and the top field's Search button needs two characters, with
 * no separate Clear.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3SearchResultsComposeTest {
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
    private val askedUsers = CopyOnWriteArrayList<String>()
    private val identities = object : IdentityMetadataRepository {
        override fun cachedCommunityIdentity(name: String): Community? = null
        override suspend fun refreshCommunityIdentity(name: String, fallback: Community) = RepositoryResult.Content(fallback)
        override fun cachedUserIdentity(username: String): UserFixture? = null
        override suspend fun refreshUserIdentity(username: String, fallback: UserFixture): RepositoryResult<UserFixture> {
            askedUsers += username
            return RepositoryResult.Content(fallback)
        }
    }

    private fun community(index: Int) = CommunityDiscoveryItem(
        community = Community(id = "t5_cat$index", name = "catcommunity$index", description = "Cats $index", members = "1k", accent = 0xFF336699),
        nsfw = false,
    )

    private val answer = RedditCommunityDiscoveryUiState(
        accountStableId = account.stableId,
        searchQuery = "cats",
        searchSort = CommunityDiscoverySort.RELEVANCE,
        items = (1..4).map(::community),
        loaded = true,
        contentRevision = 1L,
        peopleSearch = RedditPeopleSearchUiState(
            accountStableId = account.stableId,
            searchQuery = "cats",
            items = (1..3).map { RedditPeopleSearchItem(username = "catperson$it", title = "", description = "") },
            loaded = true,
            contentRevision = 1L,
        ),
    )

    private fun searchScreen(query: String, submit: Boolean): SubshellState {
        val state = SubshellState(
            identityMetadataRepository = identities,
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        ).apply {
            openSearch(refocus = false)
            updateSearchQuery(query)
            if (submit) submitSearch()
        }
        val coordinator = VideoPlaybackCoordinator(ApplicationProvider.getApplicationContext<Application>())
        composeRule.setContent {
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                SearchScreen(state = state, redditCommunityDiscoveryState = answer, onBack = {})
            }
        }
        composeRule.waitForIdle()
        return state
    }

    private fun count(text: String) = composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    // The scope chips at the top carry the same words; a section heading is
    // the lowest node with that text.
    private fun top(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().maxOf { it.boundsInRoot.top }

    @Test
    fun communitiesComeBeforePeopleBeforePostsWithThreeAndTwoPreviewed() {
        searchScreen("cats", submit = true)
        composeRule.onNode(androidx.compose.ui.test.hasScrollToNodeAction())
            .performScrollToNode(hasText("catperson1", substring = true))
        composeRule.waitForIdle()

        val where = listOf("Communities", "People", "Posts").associateWith { label ->
            composeRule.onAllNodesWithText(label).fetchSemanticsNodes().map { it.boundsInRoot.top }
        }
        assertTrue(where.toString(), top("Communities") < top("People"))
        assertTrue(top("People") < top("Posts"))
        assertEquals(3, count("catcommunity"))
        assertEquals(2, count("catperson"))
    }

    @Test
    fun tappingAPersonOpensTheirProfileWithoutLookingUpEveryRow() {
        val state = searchScreen("cats", submit = true)
        composeRule.waitForIdle()
        assertEquals(emptyList<String>(), askedUsers.toList())

        composeRule.onAllNodes(hasText("catperson2", substring = true) and hasClickAction()).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals("catperson2", (state.currentNavigationRoute as SubshellRoute.User).username)
    }

    @Test
    fun theSearchButtonNeedsTwoCharactersAndThereIsNoClear() {
        val state = searchScreen("c", submit = false)
        // M3225_ONE_SEARCH_FIELD_STYLE_V1: M3's search input field is itself
        // described "Search"; the submit button is the one with the button role.
        val button = hasContentDescription("Search") and hasClickAction() and
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

        composeRule.onNode(button).assertIsNotEnabled()
        assertEquals(0, composeRule.onAllNodesWithContentDescription("Clear search", substring = true).fetchSemanticsNodes().size)

        composeRule.runOnIdle { state.updateSearchQuery("ca") }
        composeRule.waitForIdle()
        composeRule.onNode(button).assertIsEnabled()
    }
}
