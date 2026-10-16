package app.subshell.prototype.ui.screens

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.RedditCommunityDiscoveryUiState
import app.subshell.prototype.data.account.RedditSubscriptionsUiState
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.model.CommunityDiscoveryItem
import app.subshell.prototype.model.CommunityDiscoverySort
import app.subshell.prototype.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (subs04a1): community discovery starts on Reddit's popular
 * communities; typing gives suggestions and says that search finds all; a
 * submitted search asks for best match first and only then offers the
 * Best match / Most active choice, which asks Reddit again in that order.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3CommunityDiscoveryComposeTest {
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
    private val requests = mutableListOf<Pair<String?, CommunityDiscoverySort?>>()
    private val discovery = mutableStateOf(RedditCommunityDiscoveryUiState(accountStableId = "reddit:me"))

    private fun show() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        val items = SampleData.communities.map { CommunityDiscoveryItem(community = it, nsfw = false) }
        composeRule.setContent {
            ExploreCommunitiesScreen(
                state = state,
                redditSubscriptionsState = RedditSubscriptionsUiState(accountStableId = account.stableId, loaded = true),
                redditCommunityDiscoveryState = discovery.value,
                onLoadRedditCommunityDiscovery = { accountId, query, sort, over18, _ ->
                    if (sort != null || query == null) requests += query to sort
                    discovery.value = RedditCommunityDiscoveryUiState(
                        accountStableId = accountId,
                        searchQuery = query,
                        searchSort = sort,
                        autocompleteIncludeOver18 = over18,
                        items = items,
                        loaded = true,
                    )
                },
                onBack = {},
            )
        }
        composeRule.waitForIdle()
    }

    private fun shown(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun discoveryStartsOnRedditsPopularCommunities() {
        show()

        assertEquals(listOf<Pair<String?, CommunityDiscoverySort?>>(null to null), requests)
        assertEquals(1, shown("Popular on Reddit"))
        assertEquals(0, shown("Best match"))
    }

    @Test
    fun aSubmittedSearchAsksForBestMatchAndThenOffersTheSortChoice() {
        show()
        val field = composeRule.onNode(hasSetTextAction())

        field.performTextInput("lin")
        composeRule.waitForIdle()
        assertEquals(1, shown("Suggestions update as you type. Press search for all communities."))
        assertEquals(0, shown("Best match"))

        field.performImeAction()
        composeRule.waitForIdle()
        assertEquals("lin" to CommunityDiscoverySort.RELEVANCE, requests.last())
        assertEquals(1, shown("Best match"))

        composeRule.onNode(hasClickAction() and hasText("Most active"))
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals("lin" to CommunityDiscoverySort.ACTIVITY, requests.last())
    }
}
