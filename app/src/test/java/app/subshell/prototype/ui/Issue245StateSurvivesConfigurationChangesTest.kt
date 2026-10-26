package app.subshell.prototype.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.AccountSessionDirectory
import app.subshell.prototype.data.account.AccountSessionDirectorySnapshot
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.PostVotePhase
import app.subshell.prototype.data.account.PostVoteUiState
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #245: a configuration change (dark mode, font size, language, keyboard)
 * recreates the activity but not its ViewModelStore, so SubshellState stays
 * the same object with everything in it, also what the process snapshot
 * leaves out. A new process (a new store) still builds it from the snapshot
 * (Recreate01ConfigurationChangeTest).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue245StateSurvivesConfigurationChangesTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val account = AccountSessionSummary(
        stableId = "reddit:reader",
        username = "reader",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val otherAccount = account.copy(stableId = "reddit:other", username = "other")
    private val post = SampleData.posts.first().copy(id = "aaa111", score = 10, vote = 0)

    private class Accounts(private val accounts: List<AccountSessionSummary>) : AccountSessionDirectory {
        var activeAccountId = accounts.first().stableId
        override fun snapshot() = AccountSessionDirectorySnapshot(accounts, activeAccountId)
        override fun selectAccount(stableId: String) = snapshot().also { activeAccountId = stableId }
    }

    private class Store : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }

    private val directory = Accounts(listOf(account, otherAccount))
    private val repository = FakeSubshellRepository()
    private var store = Store()
    private var state: SubshellState? = null
    private val restoration = StateRestorationTester(composeRule)

    private fun start() {
        restoration.setContent {
            CompositionLocalProvider(LocalSubshellStateStoreOwner provides store) {
                state = rememberSubshellState(repository = repository, accountSessionDirectory = directory)
            }
        }
        composeRule.waitForIdle()
    }

    private fun current(): SubshellState = requireNotNull(state)

    /** The activity is recreated; its ViewModelStore is kept. */
    private fun recreateActivity(): SubshellState {
        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()
        return current()
    }

    @Test
    fun aRecreatedActivityKeepsTheSameState() {
        start()
        val before = current()

        assertSame(before, recreateActivity())
    }

    // A vote in flight is left out of the snapshot (so a later failure still
    // rolls back); it used to vanish until the coordinator's next answer.
    @Test
    fun whatTheSnapshotLeavesOutIsStillThere() {
        start()
        composeRule.runOnIdle {
            current().setAuthenticatedPostVoteRequester { }
            current().vote(post, 1)
            current().synchronizePostVoteState(
                PostVoteUiState(
                    phase = PostVotePhase.SENDING,
                    accountStableId = account.stableId,
                    postFullname = "t3_aaa111",
                    direction = 1,
                ),
            )
        }

        val after = recreateActivity()

        composeRule.runOnIdle { assertEquals(1, after.effectivePostVote(post)) }
    }

    @Test
    fun anAccountSwitchAfterTheRecreationReachesTheKeptState() {
        start()
        val after = recreateActivity()

        composeRule.runOnIdle { after.switchAccount(otherAccount.stableId) }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals(otherAccount.stableId, after.activeAccountId)
            assertEquals(otherAccount.stableId, directory.activeAccountId)
        }
    }

    @Test
    fun aNewProcessBuildsTheStateAgain() {
        start()
        val before = current()

        store = Store()
        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()

        assertNotSame(before, current())
    }

    @Test
    fun aStateIsHeldOnlyForItsOwnRepositoryAndDirectory() {
        val holder = SubshellStateHolder()
        val held = SubshellState()
        holder.hold(repository, directory, held)

        assertSame(held, holder.stateFor(repository, directory))
        assertNull(holder.stateFor(FakeSubshellRepository(), directory))
        assertNull(holder.stateFor(repository, Accounts(listOf(account))))
    }
}
