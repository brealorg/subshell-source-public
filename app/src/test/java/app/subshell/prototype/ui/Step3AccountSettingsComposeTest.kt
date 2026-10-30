package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.backup.SettingsBackupRepository
import app.subshell.prototype.data.account.AccountSessionDirectory
import app.subshell.prototype.data.account.AccountSessionDirectorySnapshot
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.ConnectAccountPhase
import app.subshell.prototype.data.account.ConnectAccountUiState
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.ui.app.AccountConnectEntryPoints
import app.subshell.prototype.ui.components.AccountSettingsRuntime
import app.subshell.prototype.ui.screens.ACCOUNT_ROW_ARROW_TAG
import app.subshell.prototype.ui.screens.ACCOUNT_ROW_CURRENT_TAG
import app.subshell.prototype.ui.screens.SettingsDestination
import app.subshell.prototype.ui.screens.accountRowTag
import app.subshell.prototype.ui.screens.SettingsScreen
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (auth08a1): Settings › Accounts logs out only after the reader
 * confirms (Cancel sends nothing) and closes the question once it is done;
 * "Add Reddit account" starts a sign-in; after a logout the app takes
 * the account list from the account directory, so the account disappears.
 *
 * STEP3 (profile01a3): the Accounts list shows only Reddit accounts (no
 * local fixture account); tapping another account's row switches to it,
 * and a row that needs signing in again starts that sign-in.
 *
 * ISSUE280_ACCOUNT_ROWS_ARE_A_PICKER_V1: the rows carry no arrow (they lead
 * nowhere); the current account has a check mark and no tap action; only a
 * row that needs signing in again keeps the arrow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3AccountSettingsComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val me = AccountSessionSummary(
        stableId = "reddit:me",
        username = "me_user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val other = me.copy(stableId = "reddit:other", username = "other_user")
    private val events = mutableListOf<String>()

    private fun signedIn() = SubshellState(
        initialAccountSessions = listOf(me, other),
        initialActiveAccountId = me.stableId,
        initialInteractionSideConfigured = true,
    )

    private fun showAccounts(state: SubshellState, logoutClears: Boolean) {
        ensureWorkManagerInitialized(context)
        val repository = SettingsBackupRepository(context, "test")
        composeRule.setContent {
            SubshellTheme {
                SettingsScreen(
                    state = state,
                    backupRepository = repository,
                    onBack = {},
                    initialDestinationName = SettingsDestination.ACCOUNTS.name,
                    accountSettingsRuntime = AccountSettingsRuntime(
                        connectAccountState = ConnectAccountUiState(phase = ConnectAccountPhase.IDLE, providerLabel = "Reddit"),
                        onConnectAccount = { events += "connect" },
                        onResetConnectAccount = { events += "reset" },
                        onReconnectAccount = { stableId -> events += "reconnect:$stableId" },
                        onLogoutActiveAccount = { onComplete ->
                            events += "logout"
                            onComplete(logoutClears)
                        },
                    ),
                )
            }
        }
        composeRule.waitForIdle()
    }

    /** SubshellApp has more than one list (the drawer too); scroll the one holding [target]. */
    private fun scrollTo(target: androidx.compose.ui.test.SemanticsMatcher) {
        val lists = composeRule.onAllNodes(hasScrollAction())
        val count = lists.fetchSemanticsNodes().size
        for (index in 0 until count) {
            if (runCatching { lists[index].performScrollToNode(target) }.isSuccess) {
                composeRule.waitForIdle()
                return
            }
        }
        error("no list holds the target")
    }

    private fun openLogoutQuestion() {
        val row = hasText("Log out") and hasText("u/me_user")
        scrollTo(row)
        composeRule.onNode(row).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun answer(button: String) {
        composeRule.onNode(hasText(button) and hasClickAction() and hasAnyAncestor(isDialog()))
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun questions() = composeRule.onAllNodesWithText("Log out of u/me_user?").fetchSemanticsNodes().size

    @Test
    fun logoutHappensOnlyAfterTheReaderConfirmsAndTheQuestionClosesWhenDone() {
        showAccounts(signedIn(), logoutClears = true)

        openLogoutQuestion()
        assertEquals(1, questions())
        answer("Cancel")
        assertEquals(emptyList<String>(), events)
        assertEquals(0, questions())

        openLogoutQuestion()
        answer("Log out")
        assertEquals(listOf("logout"), events)
        assertEquals(0, questions())
    }

    @Test
    fun addRedditAccountStartsASignIn() {
        showAccounts(signedIn(), logoutClears = true)
        val add = hasText("Add Reddit account")
        composeRule.onNode(hasScrollAction()).performScrollToNode(add)

        composeRule.onAllNodes(add and hasClickAction()).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals("connect", events.last())
    }

    @Test
    fun afterALogoutTheAppTakesTheAccountListFromTheDirectory() {
        ensureWorkManagerInitialized(context)
        val state = signedIn()
        var loggedOut = false
        val directory = object : AccountSessionDirectory {
            override fun snapshot() = if (loggedOut) {
                AccountSessionDirectorySnapshot(listOf(other), other.stableId)
            } else {
                AccountSessionDirectorySnapshot(listOf(me, other), me.stableId)
            }
            override fun selectAccount(stableId: String) = snapshot()
        }
        composeRule.setContent {
            SubshellApp(
                videoPlaybackCoordinator = testVideoPlaybackCoordinator(context),
                state = state,
                accountConnect = AccountConnectEntryPoints(
                    accountSessionDirectory = directory,
                    onLogoutActiveAccount = { onComplete ->
                        loggedOut = true
                        onComplete(true)
                    },
                ),
            )
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { state.openSecondary(SecondaryDestination.SETTINGS) }
        composeRule.waitForIdle()
        val accounts = hasText(SettingsDestination.ACCOUNTS.title) and hasClickAction()
        scrollTo(accounts)
        composeRule.onAllNodes(accounts).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        openLogoutQuestion()
        answer("Log out")

        assertEquals(listOf(other.stableId), state.accountSessions.map { it.stableId })
    }

    @Test
    fun localAccountsAreNotListedAndARowSwitches() {
        val stale = me.copy(
            stableId = "reddit:stale",
            username = "stale_user",
            state = AccountSessionState.REAUTHENTICATION_REQUIRED,
        )
        val state = SubshellState(
            initialAccountSessions = localFixtureAccountSummaries(listOf("root")) + me + other + stale,
            initialActiveAccountId = me.stableId,
            initialInteractionSideConfigured = true,
        )
        showAccounts(state, logoutClears = false)

        assertEquals(0, composeRule.onAllNodesWithText("u/root").fetchSemanticsNodes().size)

        val otherRow = hasText("u/other_user") and hasClickAction()
        scrollTo(otherRow)
        composeRule.onAllNodes(otherRow).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(other.stableId, state.activeAccountId)
        assertEquals(emptyList<String>(), events)

        val staleRow = hasText("u/stale_user") and hasClickAction()
        scrollTo(staleRow)
        composeRule.onAllNodes(staleRow).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(listOf("reset", "reconnect:${stale.stableId}"), events)
    }

    @Test
    fun accountRowsPickAnAccountAndOnlyASignInAgainRowHasAnArrow() {
        val stale = me.copy(
            stableId = "reddit:stale",
            username = "stale_user",
            state = AccountSessionState.REAUTHENTICATION_REQUIRED,
        )
        val state = SubshellState(
            initialAccountSessions = listOf(me, other, stale),
            initialActiveAccountId = me.stableId,
            initialInteractionSideConfigured = true,
        )
        showAccounts(state, logoutClears = false)

        fun row(username: String) = composeRule.onNode(hasTestTag(accountRowTag(username)), useUnmergedTree = true)
        fun count(matcher: androidx.compose.ui.test.SemanticsMatcher) =
            composeRule.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().size

        // One arrow, on the sign-in-again row; one check mark, on the current account.
        assertEquals(1, count(hasTestTag(ACCOUNT_ROW_ARROW_TAG)))
        assertEquals(1, count(hasTestTag(ACCOUNT_ROW_CURRENT_TAG)))
        row("stale_user").assert(hasAnyDescendant(hasTestTag(ACCOUNT_ROW_ARROW_TAG)))
        row("me_user").assert(hasAnyDescendant(hasTestTag(ACCOUNT_ROW_CURRENT_TAG)))
        row("other_user").assert(hasAnyDescendant(hasTestTag(ACCOUNT_ROW_ARROW_TAG)).not())

        // The current account has no tap action; the others do.
        row("me_user").assert(hasClickAction().not())
        row("other_user").assert(hasClickAction())
        row("stale_user").assert(hasClickAction())
    }
}
