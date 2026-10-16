package app.subshell.prototype.ui.screens

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.RedditAccountHistoryKind
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.RedditAccountHistoryUiState
import app.subshell.prototype.data.account.RedditSavedHistoryUiState
import app.subshell.prototype.data.redditAccountHistoryQueryKey
import app.subshell.prototype.data.redditMixedSavedHistoryQueryKey
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.theme.SubshellTheme
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
 * ISSUE58_HISTORY_REENTRY_RELOADS_STATE_ONLY_DATA_V1: Comments (and the mixed
 * Saved list) live only in one coordinator state that another tab, user or
 * account replaces; re-entry was fenced off, so the tab showed "Loading
 * comments…" forever.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue58ProfileHistoryReentryComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(RetryOnRobolectricRendererFlake())
        .around(composeRule)

    private val account = AccountSessionSummary(
        stableId = "reddit:someone",
        username = "someone",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val shown = mutableStateOf(true)
    private val history = mutableStateOf(RedditAccountHistoryUiState())
    private val saved = mutableStateOf(RedditSavedHistoryUiState())
    private val historyRefreshes = mutableListOf<RedditAccountHistoryKind>()
    private var savedRefreshes = 0

    private fun loadedHistory(kind: RedditAccountHistoryKind, accountId: String = account.stableId) =
        RedditAccountHistoryUiState(
            accountStableId = accountId,
            username = "someone",
            kind = kind,
            queryKey = redditAccountHistoryQueryKey(accountId, "someone", kind),
            loaded = true,
        )

    private fun loadedSaved(accountId: String = account.stableId) = RedditSavedHistoryUiState(
        accountStableId = accountId,
        username = "someone",
        queryKey = redditMixedSavedHistoryQueryKey(accountId, "someone"),
        loaded = true,
    )

    private fun ownProfileOn(tab: Int) {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        state.selectUserProfileTab("someone", tab)
        composeRule.setContent {
            SubshellTheme {
                if (shown.value) {
                    UserProfileScreen(
                        username = "someone",
                        state = state,
                        redditAccountHistoryState = history.value,
                        onRefreshRedditAccountHistory = { _, _, kind ->
                            historyRefreshes += kind
                            history.value = loadedHistory(kind)
                        },
                        redditSavedHistoryState = saved.value,
                        onRefreshRedditSavedHistory = { _, _ ->
                            savedRefreshes += 1
                            saved.value = loadedSaved()
                        },
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun leaveAndReturn(meanwhile: () -> Unit = {}) {
        shown.value = false
        composeRule.waitForIdle()
        meanwhile()
        shown.value = true
        composeRule.waitForIdle()
    }

    @Test
    fun commentsAreFetchedAgainAfterAnotherTabReplacedThem() {
        ownProfileOn(tab = 1)
        assertEquals(listOf(RedditAccountHistoryKind.COMMENTS), historyRefreshes)

        leaveAndReturn { history.value = loadedHistory(RedditAccountHistoryKind.UPVOTED) }

        assertEquals(
            listOf(RedditAccountHistoryKind.COMMENTS, RedditAccountHistoryKind.COMMENTS),
            historyRefreshes,
        )
    }

    @Test
    fun commentsStillLoadedAreNotFetchedAgain() {
        ownProfileOn(tab = 1)

        leaveAndReturn()

        assertEquals(listOf(RedditAccountHistoryKind.COMMENTS), historyRefreshes)
    }

    @Test
    fun savedIsFetchedAgainAfterAnotherAccountReplacedIt() {
        ownProfileOn(tab = 2)
        assertEquals(1, savedRefreshes)

        leaveAndReturn { saved.value = loadedSaved(accountId = "reddit:other") }

        assertEquals(2, savedRefreshes)
    }

    @Test
    fun theRule() {
        assertTrue(profileHistoryEntryRefreshNeeded(firstEntry = true, dataLivesOnlyInState = false, stateMatches = true))
        assertTrue(profileHistoryEntryRefreshNeeded(firstEntry = false, dataLivesOnlyInState = true, stateMatches = false))
        assertFalse(profileHistoryEntryRefreshNeeded(firstEntry = false, dataLivesOnlyInState = true, stateMatches = true))
        // Post histories keep the paging cache: re-entry never refetches them.
        assertFalse(profileHistoryEntryRefreshNeeded(firstEntry = false, dataLivesOnlyInState = false, stateMatches = false))
    }
}
