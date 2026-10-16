package app.subshell.prototype.ui

import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class Auth07b1AccountSessionPresentationPolicyTest {
    @Test
    fun connectedAccountCopyHidesOAuthTransport() {
        val connected = account(
            AccountSessionState.CONNECTED,
        )

        val selected =
            accountSwitcherSessionLabel(
                account = connected,
                selected = true,
            )
        val inactive =
            accountSwitcherSessionLabel(
                account = connected,
                selected = false,
            )

        assertEquals(
            "Current account",
            selected,
        )
        assertEquals(
            "Connected",
            inactive,
        )

        listOf(
            selected,
            inactive,
        ).forEach { label ->
            assertFalse(
                label.contains(
                    "RedReader",
                    ignoreCase = true,
                ),
            )
            assertFalse(
                label.contains(
                    "Connected via",
                    ignoreCase = true,
                ),
            )
        }
    }

    @Test
    fun reauthenticationCopyRemainsExplicit() {
        val account = account(
            AccountSessionState.REAUTHENTICATION_REQUIRED,
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

    private fun account(
        state: AccountSessionState,
    ): AccountSessionSummary =
        AccountSessionSummary(
            stableId =
                "reddit:redreader_compatibility:"
                    + "t2_auth07b1",
            username = "auth07b1",
            source =
                AccountSessionSource.AUTHENTICATED,
            state = state,
            providerLabel =
                "Reddit · RedReader compatibility",
        )
}
