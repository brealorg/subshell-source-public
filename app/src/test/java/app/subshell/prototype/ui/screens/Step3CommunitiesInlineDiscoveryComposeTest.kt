package app.subshell.prototype.ui.screens

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertAll
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.CommunityMembershipAction
import app.subshell.prototype.data.account.RedditCommunityDiscoveryUiState
import app.subshell.prototype.data.account.RedditSubscriptionsUiState
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.Community
import app.subshell.prototype.model.CommunityDiscoveryItem
import app.subshell.prototype.ui.CommunityDiscoveryLayout
import app.subshell.prototype.ui.MainDestination
import app.subshell.prototype.ui.SecondaryDestination
import app.subshell.prototype.ui.SubshellApp
import app.subshell.prototype.ui.SubshellRoute
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.app.CommunityEntryPoints
import app.subshell.prototype.ui.app.FeedEntryPoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (subs04b, subs04c): the Joined tab of Communities starts with an
 * inline "Discover communities" surface above the joined list: a search bar
 * that opens Discover with the field focused, See all that opens Discover
 * without it, at most three popular communities in the list layout (six in
 * the carousel) that leave out joined, NSFW (with NSFW off) and repeated
 * names, a Join that asks only once the joined list is live, and the hidden
 * communities entry with its count. The app host hands discovery and
 * membership to the screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3CommunitiesInlineDiscoveryComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val account = AccountSessionSummary(
        stableId = "reddit:me",
        username = "me_user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val memberships = mutableListOf<Triple<String, String, CommunityMembershipAction>>()

    private fun community(name: String) = Community(
        id = "t5_$name",
        name = name,
        description = "About $name",
        members = "1k",
        accent = 0xFF336699,
    )

    private val homelab = community("homelab")
    private val discovery = RedditCommunityDiscoveryUiState(
        accountStableId = "reddit:me",
        loaded = true,
        items = listOf(
            CommunityDiscoveryItem(homelab, nsfw = false),
            CommunityDiscoveryItem(community("kotlin"), nsfw = false),
            CommunityDiscoveryItem(community("nsfwthing"), nsfw = true),
            CommunityDiscoveryItem(community("KOTLIN"), nsfw = false),
            CommunityDiscoveryItem(community("rust"), nsfw = false),
            CommunityDiscoveryItem(community("golang"), nsfw = false),
            CommunityDiscoveryItem(community("zig"), nsfw = false),
        ),
    )

    private fun signedIn(layout: CommunityDiscoveryLayout = CommunityDiscoveryLayout.LIST): SubshellState {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        return SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        ).apply {
            updateCommunityDiscoveryLayout(layout)
            navigate(MainDestination.SUBS)
        }
    }

    private fun show(state: SubshellState, fromCache: Boolean = false) {
        composeRule.setContent {
            CommunitiesScreen(
                state = state,
                redditSubscriptionsState = RedditSubscriptionsUiState(
                    accountStableId = account.stableId,
                    communities = listOf(homelab),
                    loaded = true,
                    fromCache = fromCache,
                ),
                redditCommunityDiscoveryState = discovery,
                onChangeCommunityMembership = { accountId, name, action ->
                    memberships += Triple(accountId, name, action)
                },
                onRequestHiddenSubscriptionsUnlock = { onResult -> onResult(true) },
            )
        }
        composeRule.waitForIdle()
    }

    private fun count(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun top(matcher: SemanticsMatcher): Float =
        composeRule.onAllNodes(matcher).onFirst().fetchSemanticsNode().boundsInRoot.top

    private fun click(matcher: SemanticsMatcher) {
        composeRule.onAllNodes(matcher).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    // Joined rows put a word joiner after "r/"; the discovery cards do not.
    private val joinedRow = "r/⁠homelab"

    @Test
    fun theListLayoutShowsThreeNewCommunitiesAboveTheJoinedList() {
        val state = signedIn()
        show(state)

        val discover = top(hasText("Discover communities"))
        val search = top(hasText("Search communities by name or topic"))
        val firstCard = top(hasText("r/kotlin"))
        val joinedTitle = top(hasText("Joined communities"))
        val hidden = top(hasText("Hidden communities"))
        assertTrue(discover < search)
        assertTrue(search < firstCard)
        assertTrue(firstCard < joinedTitle)
        assertTrue(joinedTitle < hidden)

        assertEquals(1, count("r/kotlin"))
        assertEquals(1, count("r/rust"))
        assertEquals(1, count("r/golang"))
        assertEquals("only three in the list layout", 0, count("r/zig"))
        assertEquals("NSFW is off", 0, count("r/nsfwthing"))
        assertEquals("a joined community is not offered again", 0, count("r/homelab"))
        assertEquals(1, count(joinedRow))
    }

    @Test
    fun theCarouselLayoutHasItsOwnSearchBar() {
        val state = signedIn(CommunityDiscoveryLayout.CAROUSEL)
        show(state)

        assertEquals(1, count("Search communities"))
        assertEquals(0, count("Search communities by name or topic"))
        // The LazyRow composes only the cards that fit, so only the first is asserted.
        assertEquals(1, count("r/kotlin"))
        assertEquals(0, count("r/homelab"))
    }

    @Test
    fun theSearchBarOpensDiscoverWithTheFieldFocusedAndSeeAllWithout() {
        val state = signedIn()
        show(state)

        click(hasText("Search communities by name or topic") and hasClickAction())
        val searchRoute = state.currentNavigationRoute as SubshellRoute.Secondary
        assertEquals(SecondaryDestination.COMMUNITY_EXPLORE, searchRoute.secondaryDestination)
        assertEquals(1, state.communityExploreSearchFocusRequest)

        state.consumeCommunityExploreSearchFocusRequest(1)
        state.closeSecondary()
        composeRule.waitForIdle()

        click(hasText("See all") and hasClickAction())
        val seeAllRoute = state.currentNavigationRoute as SubshellRoute.Secondary
        assertEquals(SecondaryDestination.COMMUNITY_EXPLORE, seeAllRoute.secondaryDestination)
        assertEquals(0, state.communityExploreSearchFocusRequest)
    }

    @Test
    fun joinAsksRedditForThatCommunityOnTheActiveAccount() {
        val state = signedIn()
        show(state)

        click(hasText("Join") and hasClickAction())

        assertEquals(listOf(Triple("reddit:me", "kotlin", CommunityMembershipAction.JOIN)), memberships)
    }

    @Test
    fun joinWaitsForTheLiveJoinedList() {
        val state = signedIn()
        show(state, fromCache = true)

        val joins = composeRule.onAllNodes(hasText("Join") and hasClickAction())
        assertEquals(3, joins.fetchSemanticsNodes().size)
        joins.assertAll(isNotEnabled())
    }

    @Test
    fun theHiddenEntryCountsHiddenCommunitiesAndOpensThem() {
        val state = signedIn()
        state.toggleHiddenCommunity("homelab")
        show(state)

        assertEquals(1, count("1 hidden"))
        assertEquals(1, count("0 shown"))
        assertEquals(0, count(joinedRow))

        click(hasText("Hidden communities") and hasClickAction())

        assertEquals(1, count(joinedRow))
        assertEquals(1, count("Hidden locally"))
    }

    @Test
    fun theAppHandsDiscoveryAndMembershipToTheJoinedTab() {
        val state = signedIn()
        val context = ApplicationProvider.getApplicationContext<Application>()
        composeRule.setContent {
            SubshellApp(
                videoPlaybackCoordinator = VideoPlaybackCoordinator(context),
                state = state,
                redditFeed = FeedEntryPoints(
                    redditSubscriptionsState = RedditSubscriptionsUiState(
                        accountStableId = account.stableId,
                        communities = listOf(homelab),
                        loaded = true,
                    ),
                ),
                redditCommunity = CommunityEntryPoints(
                    redditCommunityDiscoveryState = discovery,
                    onChangeCommunityMembership = { accountId, name, action ->
                        memberships += Triple(accountId, name, action)
                    },
                ),
            )
        }
        composeRule.waitForIdle()
        assertEquals(1, count("r/kotlin"))

        click(hasText("Join") and hasClickAction())

        assertEquals(listOf(Triple("reddit:me", "kotlin", CommunityMembershipAction.JOIN)), memberships)
    }
}
