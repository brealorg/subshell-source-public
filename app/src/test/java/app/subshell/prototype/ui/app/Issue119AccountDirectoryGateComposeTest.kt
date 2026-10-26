package app.subshell.prototype.ui.app

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import app.subshell.prototype.data.account.AccountSessionDirectory
import app.subshell.prototype.data.account.AccountSessionDirectorySnapshot
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE119_COMPOSE_AFTER_THE_ACCOUNT_DIRECTORY_V1: SubshellApp was composed
 * from the placeholder directory (only the local account) and later
 * "switched" to the stored account. It now waits until the stored accounts
 * are read.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue119AccountDirectoryGateComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(RetryOnRobolectricRendererFlake())
        .around(composeRule)

    private val local = localFixtureAccountSummaries(listOf("local")).single()
    private val stored = AccountSessionSummary(
        stableId = "reddit:stored",
        username = "stored",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    private class SlowDirectory(
        private val placeholder: AccountSessionDirectorySnapshot,
        private val loaded: AccountSessionDirectorySnapshot,
    ) : AccountSessionDirectory {
        val gate = CompletableDeferred<Unit>()
        var done = false

        override val isHydrated: Boolean get() = done

        override fun snapshot(): AccountSessionDirectorySnapshot = if (done) loaded else placeholder

        override fun selectAccount(stableId: String): AccountSessionDirectorySnapshot = snapshot()

        override suspend fun hydratedSnapshot(): AccountSessionDirectorySnapshot {
            gate.await()
            done = true
            return loaded
        }
    }

    @Test
    fun theAppIsComposedOnlyAfterTheStoredAccountsAreRead() {
        val directory = SlowDirectory(
            placeholder = AccountSessionDirectorySnapshot(listOf(local), local.stableId),
            loaded = AccountSessionDirectorySnapshot(listOf(local, stored), stored.stableId),
        )
        val activeAtFirstComposition = mutableListOf<String>()
        composeRule.setContent {
            AccountDirectoryGate(directory, preferencesLoaded = { true }, awaitPreferences = {}) {
                activeAtFirstComposition += directory.snapshot().activeAccountId
                Text("the app")
            }
        }
        composeRule.waitForIdle()
        assertEquals(0, composeRule.onAllNodesWithText("the app").fetchSemanticsNodes().size)
        assertEquals(emptyList<String>(), activeAtFirstComposition)

        composeRule.runOnIdle { directory.gate.complete(Unit) }
        composeRule.waitForIdle()

        assertEquals(1, composeRule.onAllNodesWithText("the app").fetchSemanticsNodes().size)
        assertEquals(stored.stableId, activeAtFirstComposition.first())
    }

    @Test
    fun anAlreadyReadDirectoryComposesAtOnce() {
        val directory = SlowDirectory(
            placeholder = AccountSessionDirectorySnapshot(listOf(local), local.stableId),
            loaded = AccountSessionDirectorySnapshot(listOf(local, stored), stored.stableId),
        ).apply { done = true }
        composeRule.setContent {
            AccountDirectoryGate(directory, preferencesLoaded = { true }, awaitPreferences = {}) { Text("the app") }
        }
        composeRule.waitForIdle()

        assertEquals(1, composeRule.onAllNodesWithText("the app").fetchSemanticsNodes().size)
    }

    // STRICTMODE02_PREFERENCES_LOAD_BEFORE_THE_FIRST_FRAME_V1 (#243): the app is
    // composed only once the startup preference files are in memory, so its
    // first reads on Main do not wait for the disk.
    @Test
    fun theAppWaitsForTheStartupPreferences() {
        val directory = SlowDirectory(
            placeholder = AccountSessionDirectorySnapshot(listOf(local), local.stableId),
            loaded = AccountSessionDirectorySnapshot(listOf(local, stored), stored.stableId),
        ).apply {
            done = true
            gate.complete(Unit)
        }
        val preferences = CompletableDeferred<Unit>()
        composeRule.setContent {
            AccountDirectoryGate(
                directory = directory,
                preferencesLoaded = { preferences.isCompleted },
                awaitPreferences = { preferences.await() },
            ) { Text("the app") }
        }
        composeRule.waitForIdle()
        assertEquals(0, composeRule.onAllNodesWithText("the app").fetchSemanticsNodes().size)

        composeRule.runOnIdle { preferences.complete(Unit) }
        composeRule.waitForIdle()

        assertEquals(1, composeRule.onAllNodesWithText("the app").fetchSemanticsNodes().size)
    }
}
