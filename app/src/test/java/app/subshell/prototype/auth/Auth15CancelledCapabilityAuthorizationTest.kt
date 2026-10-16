package app.subshell.prototype.auth

import app.subshell.prototype.data.account.AccountSessionDirectory
import app.subshell.prototype.data.account.AccountSessionDirectorySnapshot
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.auth.AUTH_CALLBACK_CANCELLED_TOKEN
import app.subshell.prototype.data.auth.AuthBrowserLaunchResult
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityResult
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.OAuthClientMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AUTH15 (#129): the composer flair and post-requirements coordinators only
 * begin capability authorizations and stay IDLE. A cancelled login (Back in the
 * login window, a dismissed AuthTab) reached them, but the phase-gated cancel
 * returned early, so the pending record stayed and every later Authorize did
 * nothing. The cancel now drops an owned pending record whatever the phase.
 */
class Auth15CancelledCapabilityAuthorizationTest {
    private val account = AccountSessionSummary(
        stableId = "reddit:redreader_compatibility:t2_writer", username = "writer",
        source = AccountSessionSource.AUTHENTICATED, state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    @Test
    fun aCancelledFlairAuthorizationCanBeStartedAgain() = runBlocking {
        val capability = Capability()
        val flair = TextPostWriteCoordinator(capability, null, Directory(account), OAuthClientMode.REDREADER_COMPATIBILITY)

        assertTrue(flair.beginCapabilityAuthorization(account.stableId) is AuthBrowserLaunchResult.Ready)
        assertTrue(flair.handleCallbackResultTokenIfOwned(AUTH_CALLBACK_CANCELLED_TOKEN))

        assertFalse(capability.hasPendingAuthorization())
        assertTrue(flair.beginCapabilityAuthorization(account.stableId) is AuthBrowserLaunchResult.Ready)
    }

    private class Directory(private val account: AccountSessionSummary) : AccountSessionDirectory {
        override fun snapshot() = AccountSessionDirectorySnapshot(listOf(account), account.stableId)
        override fun selectAccount(stableId: String) = snapshot()
    }

    private class Capability : AuthCommentWriteCapability {
        private var pending = false
        override suspend fun beginAuthorization(accountStableId: String): String {
            pending = true
            return "https://www.reddit.com/api/v1/authorize?state=auth15-fixture"
        }
        override fun hasPendingAuthorization(): Boolean = pending
        override fun hasStoredCallback(): Boolean = false
        override fun cancelPendingAuthorization() { pending = false }
        override suspend fun completePendingAuthorization(): AuthCommentWriteCapabilityResult =
            throw UnsupportedOperationException()
        override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult =
            AuthSessionResult.ReauthenticationRequired
    }
}
