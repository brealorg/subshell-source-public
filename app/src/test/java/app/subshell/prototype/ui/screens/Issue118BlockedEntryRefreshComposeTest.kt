package app.subshell.prototype.ui.screens

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import app.subshell.prototype.data.account.RedditBlockedAccount
import app.subshell.prototype.data.account.RedditUserBlockRequest
import org.junit.Assert.assertTrue
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.RedditBlockedAccountsUiState
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE118_BLOCKED_LIST_SURVIVES_ACCOUNT_SWITCH_V1: a load still running for
 * the previous account held back the Blocked entry refresh for good.
 *
 * STEP3 (next01a4b): Unblock removes the account from the list at once and
 * asks Reddit once; a row opens the account only where the host offers that;
 * the list asks for its next page when it ends on screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue118BlockedEntryRefreshComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(RetryOnRobolectricRendererFlake())
        .around(composeRule)

    private val account = AccountSessionSummary(
        stableId = "reddit:b",
        username = "someone",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    @Test
    fun aLoadForTheOtherAccountDoesNotHoldBackThisAccountsRefresh() {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        state.selectUserProfileTab("someone", 6)
        val blocked = mutableStateOf(
            RedditBlockedAccountsUiState(
                accountStableId = "reddit:a",
                loaded = true,
                inProgress = true,
                inProgressOperation = RepositoryLoadOperation.REFRESH,
            ),
        )
        val refreshes = mutableListOf<String>()
        composeRule.setContent {
            SubshellTheme {
                UserProfileScreen(
                    username = "someone",
                    state = state,
                    blockedAccountsState = blocked.value,
                    onRefreshBlockedAccounts = { accountId -> refreshes += accountId },
                )
            }
        }
        composeRule.waitForIdle()

        assertEquals(listOf("reddit:b"), refreshes)
    }

    private val alice = RedditBlockedAccount(username = "alice", accountFullname = "t2_a")
    private val bob = RedditBlockedAccount(username = "bob", accountFullname = "t2_b")

    private fun blockedTab(
        nextCursor: String? = null,
        items: List<RedditBlockedAccount> = listOf(alice, bob),
        onOpenBlockedAccount: ((String) -> Unit)? = null,
        onAppend: (String) -> Unit = {},
    ): SubshellState {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        state.selectUserProfileTab("someone", 6)
        val loaded = RedditBlockedAccountsUiState(
            accountStableId = account.stableId,
            items = items,
            loaded = true,
            nextCursor = nextCursor,
        )
        composeRule.setContent {
            SubshellTheme {
                UserProfileScreen(
                    username = "someone",
                    state = state,
                    blockedAccountsState = loaded,
                    onOpenBlockedAccount = onOpenBlockedAccount,
                    onAppendBlockedAccounts = onAppend,
                )
            }
        }
        composeRule.waitForIdle()
        return state
    }

    private fun shown(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun unblockRemovesTheAccountAtOnceAndAsksRedditOnce() {
        val requests = mutableListOf<RedditUserBlockRequest>()
        val state = blockedTab()
        composeRule.runOnIdle {
            state.setAuthenticatedContentSafetyRequesters(
                postVisibilityRequester = {},
                reportRequester = {},
                userBlockRequester = { request -> requests += request },
            )
        }
        assertEquals(1, shown("u/alice"))

        // Rows keep Reddit's order: the first Unblock is alice's.
        composeRule.onAllNodes(hasText("Unblock") and hasClickAction()).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals(0, shown("u/alice"))
        assertEquals(1, shown("u/bob"))
        assertEquals(listOf(Triple("reddit:b", "alice", false)), requests.map { Triple(it.accountStableId, it.username, it.blocked) })
        assertTrue(state.userBlockInProgress("alice"))
    }

    @Test
    fun aRowOpensTheAccountOnlyWhereTheHostOffersIt() {
        blockedTab()
        assertEquals(0, composeRule.onAllNodes(hasText("u/bob") and hasClickAction()).fetchSemanticsNodes().size)
    }

    @Test
    fun aRowOpensTheAccountWhenTheHostOffersIt() {
        val opened = mutableListOf<String>()
        blockedTab(onOpenBlockedAccount = { opened += it })

        composeRule.onAllNodes(hasText("u/bob") and hasClickAction()).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals(listOf("bob"), opened)
    }

    @Test
    fun theBlockedListAsksForItsNextPageWhenTheReaderScrollsToItsEnd() {
        val appends = mutableListOf<String>()
        val many = (1..40).map { RedditBlockedAccount(username = "user$it", accountFullname = "t2_u$it") }
        blockedTab(nextCursor = "after1", items = many, onAppend = { appends += it })
        assertEquals(emptyList<String>(), appends)

        // The paging gate asks only once the reader scrolls (ScrollPagingLoadGate).
        // Swipe the list that holds the blocked rows until its end is on screen.
        val list = hasScrollToIndexAction() and hasAnyDescendant(hasText("user1", substring = true))
        repeat(20) {
            if (appends.isEmpty()) {
                composeRule.onAllNodes(list).onFirst().performTouchInput { swipeUp() }
                composeRule.waitForIdle()
            }
        }

        assertEquals(listOf("reddit:b"), appends)
    }
}
