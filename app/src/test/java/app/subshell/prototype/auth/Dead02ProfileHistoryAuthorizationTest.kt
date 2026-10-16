package app.subshell.prototype.auth

import app.subshell.prototype.data.auth.AUTH_CALLBACK_CANCELLED_TOKEN
import app.subshell.prototype.data.auth.AuthCapabilityAuthorizationOwner
import app.subshell.prototype.data.auth.AuthCapabilityAuthorizationResult
import app.subshell.prototype.data.auth.AuthContextualCapabilityAuthorizer
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.profileHistoryAuthorizationContextKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * DEAD02 (OCB-437/520): the dead profile comments state was also where a
 * profile-history authorization begun with no history surface open remembered
 * its account and username. That identity now lives in the coordinator itself.
 */
class Dead02ProfileHistoryAuthorizationTest {
    private val account = "reddit:me"
    private val contextKey = profileHistoryAuthorizationContextKey("someone")
    private val authorizer = RecordingAuthorizer()
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val coordinator = ProfileCommentsCoordinator(
        profileHistoryAuthorizer = authorizer,
        profileHistoryClientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
        redditAccountHistoryCoordinator = RedditAccountHistoryRequestCoordinator(scope = scope, updater = null),
        redditSavedHistoryCoordinator = RedditSavedHistoryRequestCoordinator(scope = scope, loader = null),
        activeAuthenticatedAccountStableId = { account },
    )

    @Test
    fun aBrowserThatCannotOpenCancelsTheAuthorizationJustBegun() = runBlocking<Unit> {
        coordinator.beginProfileHistoryAuthorization(account, "Someone")

        coordinator.profileHistoryAuthorizationBrowserLaunchFailed()

        assertEquals(listOf(contextKey), authorizer.cancelled)
    }

    @Test
    fun aSettledAuthorizationIsNotCancelledAgainByALateLaunchFailure() = runBlocking {
        coordinator.beginProfileHistoryAuthorization(account, "someone")
        coordinator.completeProfileHistoryAuthorization(AUTH_CALLBACK_CANCELLED_TOKEN, contextKey)
        assertEquals(listOf(contextKey), authorizer.cancelled)

        coordinator.profileHistoryAuthorizationBrowserLaunchFailed()

        assertEquals(listOf(contextKey), authorizer.cancelled)
        assertEquals(contextKey, authorizer.begun.single())
    }

    private class RecordingAuthorizer : AuthContextualCapabilityAuthorizer {
        val begun = mutableListOf<String?>()
        val cancelled = mutableListOf<String?>()

        override suspend fun beginAuthorization(
            accountStableId: String,
            owner: AuthCapabilityAuthorizationOwner,
            contextKey: String?,
        ): String {
            begun += contextKey
            return "https://example.test/authorize"
        }

        override fun hasPendingAuthorization(owner: AuthCapabilityAuthorizationOwner, contextKey: String?) = false
        override fun hasStoredCallback(owner: AuthCapabilityAuthorizationOwner, contextKey: String?) = false
        override fun cancelPendingAuthorization(owner: AuthCapabilityAuthorizationOwner, contextKey: String?) {
            cancelled += contextKey
        }

        override suspend fun completePendingAuthorization(
            owner: AuthCapabilityAuthorizationOwner,
            contextKey: String?,
        ): AuthCapabilityAuthorizationResult = error("unused")

        override suspend fun beginAuthorization(accountStableId: String, owner: AuthCapabilityAuthorizationOwner) =
            beginAuthorization(accountStableId, owner, null)
        override fun hasPendingAuthorization(owner: AuthCapabilityAuthorizationOwner) = false
        override fun hasStoredCallback(owner: AuthCapabilityAuthorizationOwner) = false
        override fun cancelPendingAuthorization(owner: AuthCapabilityAuthorizationOwner) =
            cancelPendingAuthorization(owner, null)
        override suspend fun completePendingAuthorization(owner: AuthCapabilityAuthorizationOwner) =
            completePendingAuthorization(owner, null)
        override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult = error("unused")
    }
}
