package io.github.brealorg.subshell.auth

import kotlinx.coroutines.runBlocking
import io.github.brealorg.subshell.data.account.RedditCommunityWikiUiFailureKind
import io.github.brealorg.subshell.data.auth.AuthBrowserLaunchResult
import io.github.brealorg.subshell.data.auth.AuthCapabilityAuthorizationOwner
import io.github.brealorg.subshell.data.auth.AuthCapabilityAuthorizationResult
import io.github.brealorg.subshell.data.auth.AuthContextualCapabilityAuthorizer
import io.github.brealorg.subshell.data.auth.AuthSessionResult
import io.github.brealorg.subshell.data.auth.OAuthClientMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AUTHKEY01 (OCB-605): a wiki or profile-history authorization for a name
 * whose hex key is over 64 characters threw out of the click handler.
 */
class AuthKey01OversizedContextKeyTest {
    private val account = "reddit:me"
    private val authorizer = RecordingAuthorizer()
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Test
    fun aWikiForAVeryLongCommunityNameIsUnavailableInsteadOfACrash() = runBlocking<Unit> {
        val coordinator = CommunityWikiCoordinator(
            wikiLoader = null,
            wikiAuthorizer = authorizer,
            wikiClientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
            activeAuthenticatedAccountStableId = { account },
            scope = scope,
        )

        val result = coordinator.beginRedditCommunityWikiAuthorization(account, "a".repeat(40))

        assertEquals(AuthBrowserLaunchResult.NotStarted, result)
        assertTrue(authorizer.begun.isEmpty())
        assertEquals(
            RedditCommunityWikiUiFailureKind.UNAVAILABLE,
            coordinator.redditCommunityWikiUiState.value.failureKind,
        )
    }

    @Test
    fun profileHistoryForANonLatinUsernameIsNotStartedInsteadOfACrash() = runBlocking<Unit> {
        val history = RedditAccountHistoryRequestCoordinator(scope = scope, updater = null)
        val coordinator = ProfileCommentsCoordinator(
            profileHistoryAuthorizer = authorizer,
            profileHistoryClientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
            redditAccountHistoryCoordinator = history,
            redditSavedHistoryCoordinator = RedditSavedHistoryRequestCoordinator(scope = scope, loader = null),
            activeAuthenticatedAccountStableId = { account },
        )

        val result = coordinator.beginProfileHistoryAuthorization(account, "ø".repeat(20))

        assertEquals(AuthBrowserLaunchResult.NotStarted, result)
        assertTrue(authorizer.begun.isEmpty())
    }

    private class RecordingAuthorizer : AuthContextualCapabilityAuthorizer {
        val begun = mutableListOf<String?>()

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
        override fun cancelPendingAuthorization(owner: AuthCapabilityAuthorizationOwner, contextKey: String?) = Unit
        override suspend fun completePendingAuthorization(
            owner: AuthCapabilityAuthorizationOwner,
            contextKey: String?,
        ): AuthCapabilityAuthorizationResult = error("unused")
        override suspend fun beginAuthorization(accountStableId: String, owner: AuthCapabilityAuthorizationOwner) =
            beginAuthorization(accountStableId, owner, null)
        override fun hasPendingAuthorization(owner: AuthCapabilityAuthorizationOwner) = false
        override fun hasStoredCallback(owner: AuthCapabilityAuthorizationOwner) = false
        override fun cancelPendingAuthorization(owner: AuthCapabilityAuthorizationOwner) = Unit
        override suspend fun completePendingAuthorization(owner: AuthCapabilityAuthorizationOwner) =
            completePendingAuthorization(owner, null)
        override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult = error("unused")
    }
}
