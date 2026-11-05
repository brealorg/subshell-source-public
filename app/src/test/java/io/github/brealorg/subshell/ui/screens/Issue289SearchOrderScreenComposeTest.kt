package io.github.brealorg.subshell.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.RedditSearchOrder
import io.github.brealorg.subshell.data.RedditSearchSort
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.harness.RetryOnRobolectricRendererFlake
import io.github.brealorg.subshell.harness.ensureWorkManagerInitialized
import io.github.brealorg.subshell.harness.testVideoPlaybackCoordinator
import io.github.brealorg.subshell.media.LocalVideoPlaybackCoordinator
import io.github.brealorg.subshell.ui.SearchScope
import io.github.brealorg.subshell.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #289: a new order is a new search for the same words. Choosing one from the menu beside the
 * results loads the search with that order at once (found on the Pixel 6: the list stayed empty
 * with "Refresh from Reddit" until a tap). ISSUE289_SEARCH_RESULTS_CAN_BE_SORTED_V1.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue289SearchOrderScreenComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(RetryOnRobolectricRendererFlake())
        .around(composeRule)

    @Test
    fun choosingAnOrderLoadsTheSearchWithIt() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val account = AccountSessionSummary(
            stableId = "reddit:me",
            username = "me_user",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        ).apply {
            openSearch(refocus = false)
            updateSearchQuery("rust async")
            updateSearchScope(SearchScope.POSTS)
            submitSearch()
        }
        val loads = mutableListOf<Pair<String, RedditSearchOrder>>()
        val coordinator = testVideoPlaybackCoordinator(context)
        composeRule.setContent {
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                SearchScreen(
                    state = state,
                    onBack = {},
                    onRefreshRedditSearch = { _, query, _, order -> loads += query to order },
                )
            }
        }
        composeRule.waitForIdle()
        loads.clear()

        composeRule.onNodeWithContentDescription("Sort posts: Relevance").performClick()
        composeRule.onNodeWithText("New").performClick()
        composeRule.waitForIdle()

        assertEquals(RedditSearchOrder(RedditSearchSort.NEW), state.searchOrder)
        assertEquals(listOf("rust async" to RedditSearchOrder(RedditSearchSort.NEW)), loads)

        // Choosing the order that is already chosen loads nothing again.
        loads.clear()
        composeRule.onNodeWithContentDescription("Sort posts: New").performClick()
        composeRule.onNodeWithText("New").performClick()
        composeRule.waitForIdle()
        assertEquals(emptyList<Pair<String, RedditSearchOrder>>(), loads)
    }
}
