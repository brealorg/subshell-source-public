package app.subshell.prototype.ui

import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.ConnectAccountPhase
import app.subshell.prototype.data.account.ConnectAccountUiState
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import org.junit.Assert.assertEquals
import org.junit.Test

class Profile01a3AccountSwitcherPolicyTest {
    @Test
    fun productSwitcherHidesLocalFixtures() {
        val local =
            localFixtureAccountSummaries(
                listOf("root", "packetmoss"),
            )

        val reddit = authenticatedAccount()

        assertEquals(
            listOf(reddit),
            accountSwitcherSessions(
                local + reddit,
            ),
        )
    }

    @Test
    fun idleWithAuthenticatedAccountUsesAdd() {
        val presentation =
            accountConnectPresentation(
                connectAccountState =
                    connectState(
                        ConnectAccountPhase.IDLE,
                    ),
                hasAuthenticatedAccounts = true,
            )

        assertEquals(
            "Add Reddit account",
            presentation.headline,
        )
        assertEquals(
            "Sign in with another Reddit account.",
            presentation.supporting,
        )
        assertEquals(
            AccountConnectAction.CONNECT,
            presentation.action,
        )
        assertEquals(
            "Add",
            presentation.actionLabel,
        )
    }

    @Test
    fun connectedFlowStillUsesAdd() {
        val presentation =
            accountConnectPresentation(
                connectAccountState =
                    connectState(
                        ConnectAccountPhase.CONNECTED,
                    ),
                hasAuthenticatedAccounts = true,
            )

        assertEquals(
            "Add Reddit account",
            presentation.headline,
        )
        assertEquals(
            "Add",
            presentation.actionLabel,
        )
    }

    @Test
    fun emptyAuthenticatedDirectoryUsesConnect() {
        val presentation =
            accountConnectPresentation(
                connectAccountState =
                    connectState(
                        ConnectAccountPhase.IDLE,
                    ),
                hasAuthenticatedAccounts = false,
            )

        assertEquals(
            "Connect Reddit account",
            presentation.headline,
        )
        assertEquals(
            "Connect",
            presentation.actionLabel,
        )
    }

    @Test
    fun reauthenticationRemainsVisible() {
        val account =
            authenticatedAccount(
                AccountSessionState
                    .REAUTHENTICATION_REQUIRED,
            )

        assertEquals(
            "Current account · Sign in again",
            accountSwitcherSessionLabel(
                account = account,
                selected = true,
            ),
        )

        assertEquals(
            "Sign in again",
            accountSwitcherSessionLabel(
                account = account,
                selected = false,
            ),
        )
    }

    private fun authenticatedAccount(
        state: AccountSessionState =
            AccountSessionState.CONNECTED,
    ): AccountSessionSummary =
        AccountSessionSummary(
            stableId =
                "reddit:redreader_compatibility:"
                    + "t2_brealorg",
            username = "brealorg",
            source =
                AccountSessionSource.AUTHENTICATED,
            state = state,
            providerLabel = "Reddit",
        )

    private fun connectState(
        phase: ConnectAccountPhase,
    ): ConnectAccountUiState =
        ConnectAccountUiState(
            phase = phase,
            providerLabel = "Reddit",
        )
}
