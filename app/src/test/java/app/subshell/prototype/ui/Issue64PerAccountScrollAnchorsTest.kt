package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE64_FEED_SCROLL_ANCHORS_PER_ACCOUNT_V1: feed positions were shared by
 * every account, so B opened Home at A's row and A's deep position held back
 * B's first automatic refresh. Each account now keeps its own.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue64PerAccountScrollAnchorsTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(RetryOnRobolectricRendererFlake())
        .around(composeRule)

    private fun account(id: String) = AccountSessionSummary(
        stableId = "reddit:$id",
        username = id,
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    private val home = mainFeedScrollKey(FeedSource.HOME, FeedSort.BEST)

    private fun twoAccounts(): SubshellState {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        return SubshellState(
            initialAccountSessions = listOf(account("alice"), account("bob")),
            initialActiveAccountId = "reddit:alice",
            initialInteractionSideConfigured = true,
        )
    }

    @Test
    fun eachAccountKeepsItsOwnPosition() {
        val state = twoAccounts()
        state.updateFeedScrollAnchor(home, itemKey = "post-80", fallbackIndex = 80, pixelOffset = 12)
        assertTrue(state.feedScrollAnchor(home).isDeepForAutomaticRefresh())

        state.switchAccount("reddit:bob")
        assertEquals(FeedScrollAnchor.Start, state.feedScrollAnchor(home))
        assertFalse(state.feedScrollAnchor(home).isDeepForAutomaticRefresh())
        state.updateFeedScrollAnchor(home, itemKey = "post-3", fallbackIndex = 3, pixelOffset = 0)

        state.switchAccount("reddit:alice")
        assertEquals(80, state.feedScrollAnchor(home).fallbackIndex)
        state.switchAccount("reddit:bob")
        assertEquals(3, state.feedScrollAnchor(home).fallbackIndex)
    }

    @Test
    fun aProcessSnapshotCarriesTheActiveAccountsAnchors() {
        val state = twoAccounts()
        state.updateFeedScrollAnchor(home, itemKey = "post-40", fallbackIndex = 40, pixelOffset = 0)
        val restored = SubshellState(
            initialAccountSessions = listOf(account("alice"), account("bob")),
            initialActiveAccountId = "reddit:alice",
            initialInteractionSideConfigured = true,
            initialProcessSnapshotToken = encodeSubshellProcessSnapshot(state.processStateSnapshot()),
        )

        assertEquals(40, restored.feedScrollAnchor(home).fallbackIndex)
        restored.switchAccount("reddit:bob")
        assertEquals(FeedScrollAnchor.Start, restored.feedScrollAnchor(home))
    }

    @Test
    fun theFeedListStartsFromTheNewAccountsOwnPosition() {
        val state = twoAccounts()
        val items = (0 until 200).map { index -> "post-$index" }
        state.updateFeedScrollAnchor(home, itemKey = "post-80", fallbackIndex = 80, pixelOffset = 0)
        var listState: LazyListState? = null
        composeRule.setContent {
            listState = rememberFeedScrollState(state = state, scrollKey = home, itemKeys = items)
        }
        composeRule.waitForIdle()
        assertEquals(80, requireNotNull(listState).firstVisibleItemIndex)

        composeRule.runOnIdle { state.switchAccount("reddit:bob") }
        composeRule.waitForIdle()

        assertEquals(0, requireNotNull(listState).firstVisibleItemIndex)
    }

    // The owner's device check: the feed reloads after a switch, so the list
    // is empty for a moment; that empty list used to overwrite A's row 80.
    @Test
    fun aReloadAfterSwitchingBackKeepsTheFirstAccountsRow() {
        val state = twoAccounts()
        val full = (0 until 200).map { index -> "post-$index" }
        var items by mutableStateOf(full)
        state.updateFeedScrollAnchor(home, itemKey = "post-80", fallbackIndex = 80, pixelOffset = 0)
        var listState: LazyListState? = null
        composeRule.setContent {
            val feed = rememberFeedScrollState(state = state, scrollKey = home, itemKeys = items)
            listState = feed
            LazyColumn(state = feed, modifier = Modifier.fillMaxSize()) {
                items(items, key = { it }) { key -> Text(key, Modifier.height(48.dp)) }
            }
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            items = emptyList()
            state.switchAccount("reddit:bob")
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { items = full }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            items = emptyList()
            state.switchAccount("reddit:alice")
        }
        composeRule.waitForIdle()
        assertEquals(80, state.feedScrollAnchor(home).fallbackIndex)

        composeRule.runOnIdle { items = full }
        composeRule.waitForIdle()

        assertEquals(80, requireNotNull(listState).firstVisibleItemIndex)
        assertEquals(80, state.feedScrollAnchor(home).fallbackIndex)
    }
}
