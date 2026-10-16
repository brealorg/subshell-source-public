package app.subshell.prototype.auth

import app.subshell.prototype.data.account.RedditRemovalReasonPhase
import app.subshell.prototype.data.auth.AUTH_CALLBACK_CANCELLED_TOKEN
import app.subshell.prototype.data.auth.AuthCapabilityAuthorizationOwner
import app.subshell.prototype.data.auth.AuthCapabilityAuthorizationResult
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityResult
import app.subshell.prototype.data.auth.AuthContextualCapabilityAuthorizer
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityWikiLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityWikiResult
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityWikiTarget
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditRemovalReasonLoader
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditRemovalReasonResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.OAuthProvider
import app.subshell.prototype.data.auth.OAuthTokenSet
import app.subshell.prototype.data.auth.StoredOAuthSession
import app.subshell.prototype.data.auth.communityWikiAuthorizationContextKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LOW9: authorization flows that ended in the wrong place: a cancelled
 * removal-reason authorization read as a failure (OCB-444), a comment-delete
 * callback after process death was never consumed (OCB-407), a wiki page asked
 * for before authorization was dropped for the index (OCB-411), and one failed
 * read of the client selection disabled sign-in for the process (OCB-397).
 */
class Low9AuthorizationFlowTest {
    @Test
    fun cancellingTheRemovalReasonAuthorizationAsksAgainInsteadOfFailing() = runBlocking<Unit> {
        val coordinator = RemovalReasonCoordinator(
            capability = RecordingCapability(),
            loader = object : AuthenticatedRedditRemovalReasonLoader {
                override suspend fun load(accountStableId: String, communityName: String) =
                    AuthenticatedRedditRemovalReasonResult.AuthorizationRequired
            },
            clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
            activeAccountStableId = { "acct" },
        )
        coordinator.load("acct", "subshell")
        coordinator.beginAuthorization()

        coordinator.handleCallbackResultTokenIfOwned(AUTH_CALLBACK_CANCELLED_TOKEN)

        val state = coordinator.uiState.value
        assertEquals(RedditRemovalReasonPhase.AUTHORIZATION_REQUIRED, state.phase)
        assertEquals("subshell", state.communityName)
    }

    @Test
    fun aCommentDeleteCallbackAfterProcessDeathIsConsumed() = runBlocking<Unit> {
        val capability = RecordingCapability(storedCallback = true)
        // A new process: no request in memory, the owner-bound callback stored.
        val coordinator = CommentDeleteCoordinator(
            capability = capability,
            deleter = null,
            accountDirectory = null,
            clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
        )

        val owned = coordinator.handleCallbackResultTokenIfOwned("accepted")

        assertTrue(owned)
        assertEquals(1, capability.completions)
    }

    @Test
    fun theWikiPageAskedForIsReloadedAfterAuthorization() = runBlocking<Unit> {
        val loads = mutableListOf<AuthenticatedRedditCommunityWikiTarget>()
        var authorized = false
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val coordinator = CommunityWikiCoordinator(
            wikiLoader = object : AuthenticatedRedditCommunityWikiLoader {
                override suspend fun load(accountStableId: String, target: AuthenticatedRedditCommunityWikiTarget): AuthenticatedRedditCommunityWikiResult {
                    loads += target
                    return if (authorized) {
                        AuthenticatedRedditCommunityWikiResult.Failed(
                            kind = app.subshell.prototype.data.auth.AuthenticatedRedditCommunityWikiFailureKind.TRANSPORT,
                            retryable = true,
                        )
                    } else {
                        AuthenticatedRedditCommunityWikiResult.AuthorizationRequired
                    }
                }
            },
            wikiAuthorizer = UpgradingAuthorizer { authorized = true },
            wikiClientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
            activeAuthenticatedAccountStableId = { "acct" },
            scope = scope,
        )
        coordinator.loadRedditCommunityWikiPage("acct", "subshell", "rules")
        coordinator.beginRedditCommunityWikiAuthorization("acct", "subshell")

        coordinator.completeCommunityWikiAuthorization("accepted", communityWikiAuthorizationContextKey("subshell"))

        assertEquals(listOf("rules", "rules"), loads.map { it.pagePath })
        scope.cancel()
    }

    @Test
    fun theClientSelectionReadIsRetriedBeforeGivingUp() {
        var calls = 0
        val recovered = readWithRetries(pauseMillis = 0L) {
            calls++
            if (calls < 3) error("transient decrypt failure") else "selection"
        }
        assertEquals("selection", recovered.getOrNull())
        assertEquals(3, calls)

        var failing = 0
        val failed = readWithRetries(pauseMillis = 0L) { failing++; error("still broken") }
        assertTrue(failed.isFailure)
        assertEquals(3, failing)
    }

    private class RecordingCapability(private val storedCallback: Boolean = false) : AuthCommentWriteCapability {
        var completions = 0
        override suspend fun beginAuthorization(accountStableId: String): String = "https://example.test/authorize"
        override fun hasPendingAuthorization() = true
        override fun hasStoredCallback() = storedCallback
        override fun cancelPendingAuthorization() = Unit
        override suspend fun completePendingAuthorization(): AuthCommentWriteCapabilityResult {
            completions++
            return AuthCommentWriteCapabilityResult.Upgraded(accountStableId = "acct", username = "me")
        }
        override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult = AuthSessionResult.ReauthenticationRequired
    }

    private class UpgradingAuthorizer(private val onUpgrade: () -> Unit) : AuthContextualCapabilityAuthorizer {
        override suspend fun beginAuthorization(accountStableId: String, owner: AuthCapabilityAuthorizationOwner, contextKey: String?) =
            "https://example.test/authorize"
        override fun hasPendingAuthorization(owner: AuthCapabilityAuthorizationOwner, contextKey: String?) = true
        override fun hasStoredCallback(owner: AuthCapabilityAuthorizationOwner, contextKey: String?) = true
        override fun cancelPendingAuthorization(owner: AuthCapabilityAuthorizationOwner, contextKey: String?) = Unit
        override suspend fun completePendingAuthorization(owner: AuthCapabilityAuthorizationOwner, contextKey: String?): AuthCapabilityAuthorizationResult {
            onUpgrade()
            return AuthCapabilityAuthorizationResult.Upgraded(
                accountStableId = "acct",
                username = "me",
                owner = AuthCapabilityAuthorizationOwner.COMMUNITY_WIKI_READ,
                session = StoredOAuthSession(
                    provider = OAuthProvider.REDDIT,
                    tokens = OAuthTokenSet("access", "refresh", "bearer", "wikiread", Long.MAX_VALUE),
                ),
            )
        }
        override suspend fun beginAuthorization(accountStableId: String, owner: AuthCapabilityAuthorizationOwner) =
            beginAuthorization(accountStableId, owner, null)
        override fun hasPendingAuthorization(owner: AuthCapabilityAuthorizationOwner) = true
        override fun hasStoredCallback(owner: AuthCapabilityAuthorizationOwner) = true
        override fun cancelPendingAuthorization(owner: AuthCapabilityAuthorizationOwner) = Unit
        override suspend fun completePendingAuthorization(owner: AuthCapabilityAuthorizationOwner) =
            completePendingAuthorization(owner, null)
        override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult = AuthSessionResult.ReauthenticationRequired
    }
}
