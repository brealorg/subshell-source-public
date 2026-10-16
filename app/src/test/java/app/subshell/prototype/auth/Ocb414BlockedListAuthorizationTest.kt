package app.subshell.prototype.auth

import app.subshell.prototype.data.account.ContentSafetyFailureKind
import app.subshell.prototype.data.account.RedditBlockedAccount
import app.subshell.prototype.data.account.RedditContentReportRequest
import app.subshell.prototype.data.account.RedditPostVisibilityRequest
import app.subshell.prototype.data.auth.AUTH_CALLBACK_CANCELLED_TOKEN
import app.subshell.prototype.data.auth.AuthBrowserLaunchResult
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityResult
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.reddit.AuthenticatedBlockedAccountsResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedContentSafetyResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedContentSafetyWriter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * OCB-414: a blocked-accounts read that needs authorization offered only
 * Retry, which failed the same way. The list can start the authorization; after
 * it the list is read again, and a cancel leaves Authorize on offer.
 */
class Ocb414BlockedListAuthorizationTest {
    private val account = "reddit:me"

    @Test
    fun theBlockedListAuthorizesAndReadsItselfAgain() = runBlocking<Unit> {
        val writer = BlockedListWriter()
        val capability = Capability(storedCallback = true)
        val coordinator = coordinator(writer, capability)
        coordinator.refreshBlockedAccounts(account)
        assertEquals(ContentSafetyFailureKind.AUTHORIZATION, coordinator.uiState.value.blockedAccounts.failureKind)

        val launch = coordinator.beginBlockedAccountsAuthorization(account)
        assertTrue(launch is AuthBrowserLaunchResult.Ready)
        assertTrue(coordinator.uiState.value.blockedAccounts.authorizing)

        writer.authorized = true
        coordinator.handleCallbackResultTokenIfOwned("accepted")

        val blocked = coordinator.uiState.value.blockedAccounts
        assertEquals(2, writer.loads)
        assertFalse(blocked.authorizing)
        assertEquals(listOf("someone"), blocked.items.map { it.username })
    }

    @Test
    fun aCancelledAuthorizationOffersAuthorizeAgain() = runBlocking<Unit> {
        val writer = BlockedListWriter()
        val capability = Capability(storedCallback = false)
        val coordinator = coordinator(writer, capability)
        coordinator.refreshBlockedAccounts(account)
        coordinator.beginBlockedAccountsAuthorization(account)

        coordinator.handleCallbackResultTokenIfOwned(AUTH_CALLBACK_CANCELLED_TOKEN)

        val blocked = coordinator.uiState.value.blockedAccounts
        assertFalse(blocked.authorizing)
        assertEquals(ContentSafetyFailureKind.AUTHORIZATION, blocked.failureKind)
        assertTrue(coordinator.beginBlockedAccountsAuthorization(account) is AuthBrowserLaunchResult.Ready)
    }

    private fun coordinator(writer: AuthenticatedContentSafetyWriter, capability: AuthCommentWriteCapability) =
        ContentSafetyCoordinator(
            capability = capability,
            writer = writer,
            clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
            activeAccountStableId = { account },
        )

    private class BlockedListWriter : AuthenticatedContentSafetyWriter {
        var loads = 0
        var authorized = false
        override suspend fun setPostHidden(request: RedditPostVisibilityRequest): AuthenticatedContentSafetyResult = error("unused")
        override suspend fun report(request: RedditContentReportRequest): AuthenticatedContentSafetyResult = error("unused")
        override suspend fun loadBlockedAccounts(accountStableId: String, after: String?): AuthenticatedBlockedAccountsResult {
            loads++
            return if (authorized) {
                AuthenticatedBlockedAccountsResult.Loaded(
                    accountStableId = accountStableId,
                    items = listOf(RedditBlockedAccount(username = "someone", accountFullname = "t2_someone")),
                    nextCursor = null,
                )
            } else {
                AuthenticatedBlockedAccountsResult.AuthorizationRequired
            }
        }
    }

    private inner class Capability(private val storedCallback: Boolean) : AuthCommentWriteCapability {
        override suspend fun beginAuthorization(accountStableId: String): String = "https://example.test/authorize"
        override fun hasPendingAuthorization() = true
        override fun hasStoredCallback() = storedCallback
        override fun cancelPendingAuthorization() = Unit
        override suspend fun completePendingAuthorization(): AuthCommentWriteCapabilityResult =
            AuthCommentWriteCapabilityResult.Upgraded(accountStableId = account, username = "me")
        override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult = AuthSessionResult.ReauthenticationRequired
    }
}
