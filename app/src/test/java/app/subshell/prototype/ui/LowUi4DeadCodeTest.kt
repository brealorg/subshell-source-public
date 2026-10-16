package app.subshell.prototype.ui

import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LOWUI4_LOCAL_HIDE_ONLY_WITHOUT_AN_ACCOUNT_V1 (OCB-288): hidePost added a
 * signed-in account's post to the hidden list locally, without asking Reddit,
 * against the rule that this list is Reddit's (SAFETY01A3).
 */
class LowUi4DeadCodeTest {
    @Test
    fun aLocalHideChangesNothingForASignedInAccount() {
        val account = AccountSessionSummary(
            stableId = "reddit:alice",
            username = "alice",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val signedIn = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
        )

        signedIn.hidePost("abc123")

        assertFalse("abc123" in signedIn.hiddenPostIds)
    }

    @Test
    fun aLocalHideStillWorksWithoutAnAccount() {
        val local = SubshellState()

        local.hidePost("abc123")

        assertTrue("abc123" in local.hiddenPostIds)
    }
}
