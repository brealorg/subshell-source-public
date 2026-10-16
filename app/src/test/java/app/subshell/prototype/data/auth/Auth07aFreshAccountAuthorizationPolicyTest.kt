package app.subshell.prototype.data.auth

import app.subshell.prototype.data.account.AccountSessionDirectory
import app.subshell.prototype.data.account.AccountSessionDirectorySnapshot
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.ui.accountSessionForPersistence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Auth07aFreshAccountAuthorizationPolicyTest {
    @Test
    fun accountConnectionRequestsFreshBrowserSession() {
        val launch = freshAccountAuthorizationLaunch(
            authorizationUrl =
                "https://example.test/oauth",
            clientMode =
                OAuthClientMode.REDREADER_COMPATIBILITY,
        )

        assertTrue(launch.freshAccountSession)
    }

    @Test
    fun capabilityAuthorizationDefaultsToReusableSession() {
        val launch = AuthBrowserLaunchResult.Ready(
            authorizationUrl =
                "https://example.test/oauth",
            clientMode =
                OAuthClientMode.REDREADER_COMPATIBILITY,
        )

        assertFalse(launch.freshAccountSession)
    }

    @Test
    fun newlyRegisteredDirectoryAccountOverridesStaleFallbackCatalog() {
        val existing = authenticatedAccount(
            stableId =
                "reddit:redreader_compatibility:existing",
            username = "brealorg",
        )
        val added = authenticatedAccount(
            stableId =
                "reddit:redreader_compatibility:added",
            username = "subshell_dev",
        )

        var snapshot = AccountSessionDirectorySnapshot(
            accounts = listOf(existing),
            activeAccountId = existing.stableId,
        )

        val directory = object : AccountSessionDirectory {
            override fun snapshot():
                AccountSessionDirectorySnapshot =
                snapshot

            override fun selectAccount(
                stableId: String,
            ): AccountSessionDirectorySnapshot {
                snapshot = AccountSessionDirectorySnapshot(
                    accounts = snapshot.accounts,
                    activeAccountId = stableId,
                )
                return snapshot
            }
        }

        val staleFallback = snapshot.accounts

        snapshot = AccountSessionDirectorySnapshot(
            accounts = listOf(existing, added),
            activeAccountId = added.stableId,
        )

        assertEquals(
            added,
            accountSessionForPersistence(
                accountId = added.stableId,
                accountSessionDirectory = directory,
                fallbackAccounts = staleFallback,
            ),
        )

        assertEquals(
            existing,
            accountSessionForPersistence(
                accountId = existing.stableId,
                accountSessionDirectory = null,
                fallbackAccounts = staleFallback,
            ),
        )
    }

    private fun authenticatedAccount(
        stableId: String,
        username: String,
    ): AccountSessionSummary =
        AccountSessionSummary(
            stableId = stableId,
            username = username,
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
}
