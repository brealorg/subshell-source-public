package app.subshell.prototype.auth

import app.subshell.prototype.data.RedditAccountHistoryKind
import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.account.RedditAccountHistoryUiFailureKind
import app.subshell.prototype.data.auth.AuthAccountIdentity
import app.subshell.prototype.data.auth.AuthCapabilityAuthorizationOwner
import app.subshell.prototype.data.auth.AuthCapabilityAuthorizationResult
import app.subshell.prototype.data.auth.AuthContextualCapabilityAuthorizer
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.AuthenticatedRedditAccountHistoryUpdateResult
import app.subshell.prototype.data.auth.AuthenticatedRedditAccountHistoryUpdater
import app.subshell.prototype.data.auth.AuthenticatedRedditSavedHistoryLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditSavedHistoryResult
import app.subshell.prototype.data.auth.AuthenticatedRedditSavedHistorySnapshot
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.OAuthProvider
import app.subshell.prototype.data.auth.OAuthTokenSet
import app.subshell.prototype.data.auth.StoredOAuthSession
import app.subshell.prototype.data.auth.profileHistoryAuthorizationContextKey
import app.subshell.prototype.data.redditAccountHistoryQueryKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * STEP3 (profile01a6, savedcomments01a2): a profile-history authorization
 * that comes back upgraded for the same active account and the profile-history
 * owner reads both open histories again; one that comes back for another
 * owner, or after the active account changed, reads nothing and keeps asking.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Step3ProfileHistoryAuthorizationCompletionTest {
    private val account = "reddit:me"
    private val user = "someone"
    private val contextKey = profileHistoryAuthorizationContextKey(user)
    private var activeAccount: String? = account

    private val session = StoredOAuthSession(
        provider = OAuthProvider.REDDIT,
        tokens = OAuthTokenSet("new", "refresh", "bearer", "identity mysubreddits read history", Long.MAX_VALUE),
        identity = AuthAccountIdentity(providerAccountId = "t2me", username = "me_user"),
    )

    private val updaterCalls = mutableListOf<RepositoryLoadOperation>()
    private val savedCalls = mutableListOf<String>()

    private val updater = AuthenticatedRedditAccountHistoryUpdater { accountId, username, kind, operation ->
        updaterCalls += operation
        AuthenticatedRedditAccountHistoryUpdateResult.Applied(
            queryKey = redditAccountHistoryQueryKey(accountId, username, kind),
            operation = operation,
            networkItemCount = 0,
            storedItemCount = 0,
            endReached = true,
        )
    }

    private val savedLoader = object : AuthenticatedRedditSavedHistoryLoader {
        private val empty = AuthenticatedRedditSavedHistoryResult.Loaded(
            AuthenticatedRedditSavedHistorySnapshot(emptyList(), null),
            fromCache = false,
        )

        override suspend fun cached(accountStableId: String, username: String) =
            AuthenticatedRedditSavedHistoryResult.NotCached

        override suspend fun synchronize(accountStableId: String, username: String): AuthenticatedRedditSavedHistoryResult {
            savedCalls += "synchronize:$accountStableId"
            return empty
        }

        override suspend fun append(accountStableId: String, username: String, after: String) = empty
    }

    private inner class Authorizer(
        private val complete: () -> AuthCapabilityAuthorizationResult,
    ) : AuthContextualCapabilityAuthorizer {
        override suspend fun beginAuthorization(
            accountStableId: String,
            owner: AuthCapabilityAuthorizationOwner,
            contextKey: String?,
        ) = "https://example.test/authorize"

        override fun hasPendingAuthorization(owner: AuthCapabilityAuthorizationOwner, contextKey: String?) = true
        override fun hasStoredCallback(owner: AuthCapabilityAuthorizationOwner, contextKey: String?) = true
        override fun cancelPendingAuthorization(owner: AuthCapabilityAuthorizationOwner, contextKey: String?) = Unit
        override suspend fun completePendingAuthorization(
            owner: AuthCapabilityAuthorizationOwner,
            contextKey: String?,
        ): AuthCapabilityAuthorizationResult = complete()

        override suspend fun beginAuthorization(accountStableId: String, owner: AuthCapabilityAuthorizationOwner) =
            beginAuthorization(accountStableId, owner, null)
        override fun hasPendingAuthorization(owner: AuthCapabilityAuthorizationOwner) = true
        override fun hasStoredCallback(owner: AuthCapabilityAuthorizationOwner) = true
        override fun cancelPendingAuthorization(owner: AuthCapabilityAuthorizationOwner) = Unit
        override suspend fun completePendingAuthorization(owner: AuthCapabilityAuthorizationOwner) =
            completePendingAuthorization(owner, null)
        override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult = error("unused")
    }

    private class Harness(
        val history: RedditAccountHistoryRequestCoordinator,
        val saved: RedditSavedHistoryRequestCoordinator,
        val coordinator: ProfileCommentsCoordinator,
    )

    private suspend fun TestScope.openHistoriesAndAuthorize(complete: () -> AuthCapabilityAuthorizationResult): Harness {
        val history = RedditAccountHistoryRequestCoordinator(scope = this, updater = updater)
        val saved = RedditSavedHistoryRequestCoordinator(scope = this, loader = savedLoader)
        val coordinator = ProfileCommentsCoordinator(
            profileHistoryAuthorizer = Authorizer(complete),
            profileHistoryClientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
            redditAccountHistoryCoordinator = history,
            redditSavedHistoryCoordinator = saved,
            activeAuthenticatedAccountStableId = { activeAccount },
        )
        history.update(account, user, RedditAccountHistoryKind.UPVOTED, RepositoryLoadOperation.REFRESH)
        saved.update(account, user, RepositoryLoadOperation.REFRESH)
        advanceUntilIdle()
        updaterCalls.clear()
        savedCalls.clear()

        coordinator.beginProfileHistoryAuthorization(account, user)
        assertTrue(history.uiState.value.authorizing)
        coordinator.completeProfileHistoryAuthorization("callback", contextKey)
        advanceUntilIdle()
        return Harness(history, saved, coordinator)
    }

    @Test
    fun anUpgradeForTheActiveAccountReadsBothHistoriesAgain() = runTest(StandardTestDispatcher()) {
        val harness = openHistoriesAndAuthorize {
            AuthCapabilityAuthorizationResult.Upgraded(account, "me_user", AuthCapabilityAuthorizationOwner.PROFILE_HISTORY, session)
        }

        assertEquals(listOf(RepositoryLoadOperation.REFRESH), updaterCalls)
        assertEquals(listOf("synchronize:$account"), savedCalls)
        assertFalse(harness.history.uiState.value.authorizationRequired)
        assertFalse(harness.saved.uiState.value.authorizationRequired)
    }

    @Test
    fun anAccountSwitchDuringTheAuthorizationReadsNothing() = runTest(StandardTestDispatcher()) {
        val harness = openHistoriesAndAuthorize {
            activeAccount = "reddit:other"
            AuthCapabilityAuthorizationResult.Upgraded(account, "me_user", AuthCapabilityAuthorizationOwner.PROFILE_HISTORY, session)
        }

        assertEquals(emptyList<RepositoryLoadOperation>(), updaterCalls)
        assertEquals(emptyList<String>(), savedCalls)
        assertTrue(harness.history.uiState.value.authorizationRequired)
        assertEquals(RedditAccountHistoryUiFailureKind.SESSION, harness.history.uiState.value.failureKind)
        assertTrue(harness.saved.uiState.value.authorizationRequired)
    }

    @Test
    fun anUpgradeForAnotherOwnerReadsNothing() = runTest(StandardTestDispatcher()) {
        val harness = openHistoriesAndAuthorize {
            AuthCapabilityAuthorizationResult.Upgraded(account, "me_user", AuthCapabilityAuthorizationOwner.COMMUNITY_WIKI_READ, session)
        }

        assertEquals(emptyList<RepositoryLoadOperation>(), updaterCalls)
        assertEquals(emptyList<String>(), savedCalls)
        assertTrue(harness.history.uiState.value.authorizationRequired)
        assertEquals(RedditAccountHistoryUiFailureKind.SESSION, harness.history.uiState.value.failureKind)
    }
}
