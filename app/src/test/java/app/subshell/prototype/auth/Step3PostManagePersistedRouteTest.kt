package app.subshell.prototype.auth

import app.subshell.prototype.data.account.AccountSessionDirectory
import app.subshell.prototype.data.account.AccountSessionDirectorySnapshot
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.PostManageFailureKind
import app.subshell.prototype.data.account.PostManageMutationRequest
import app.subshell.prototype.data.account.PostManageOperation
import app.subshell.prototype.data.account.PostManagePhase
import app.subshell.prototype.data.account.PostManageTarget
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityResult
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostManageFailureKind
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostManageLoadResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostManageMutationResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * STEP3 (reddit01c3c2): a Manage post authorization that comes back after the
 * process was gone sends the waiting change only when it is for the same
 * context (edit / flair / moderation) and the same account; with nothing
 * waiting, nothing is sent, and an unknown context is ignored.
 */
class Step3PostManagePersistedRouteTest {
    private val account = AccountSessionSummary(
        stableId = "reddit:me",
        username = "me",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    /** Asks for authorization once, then accepts every change. */
    private class AuthorizeOnceManager : AuthenticatedPostManager {
        var mutations = 0
        override suspend fun load(target: PostManageTarget): AuthenticatedPostManageLoadResult =
            error("not reached in this test")
        override suspend fun mutate(request: PostManageMutationRequest): AuthenticatedPostManageMutationResult {
            mutations += 1
            return if (mutations == 1) {
                AuthenticatedPostManageMutationResult.Failed(AuthenticatedPostManageFailureKind.AUTHORIZATION_REQUIRED)
            } else {
                AuthenticatedPostManageMutationResult.Updated
            }
        }
    }

    private class CallbackCapability(private val upgradedAccount: String) : AuthCommentWriteCapability {
        var completions = 0
        override suspend fun beginAuthorization(accountStableId: String): String = "https://example.test/authorize"
        override fun hasPendingAuthorization() = true
        override fun hasStoredCallback() = true
        override fun cancelPendingAuthorization() = Unit
        override suspend fun completePendingAuthorization(): AuthCommentWriteCapabilityResult {
            completions += 1
            return AuthCommentWriteCapabilityResult.Upgraded(accountStableId = upgradedAccount, username = "me")
        }
        override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult =
            AuthSessionResult.ReauthenticationRequired
    }

    private val manager = AuthorizeOnceManager()

    private fun coordinator(upgradedAccount: String = account.stableId) = PostManageCoordinator(
        manager = manager,
        accountDirectory = object : AccountSessionDirectory {
            override fun snapshot() = AccountSessionDirectorySnapshot(listOf(account), account.stableId)
            override fun selectAccount(stableId: String) = snapshot()
        },
        editCapability = CallbackCapability(upgradedAccount),
        editClientMode = null,
        flairCapability = CallbackCapability(upgradedAccount),
        flairClientMode = null,
        moderationCapability = CallbackCapability(upgradedAccount),
        moderationClientMode = null,
    )

    private val nsfwOn = PostManageMutationRequest(
        target = PostManageTarget(account.stableId, "t3_abc", "me", "kotlin"),
        operation = PostManageOperation.SET_NSFW,
        enabled = true,
    )

    @Test
    fun withNothingWaitingACallbackSendsNothing() = runBlocking {
        val coordinator = coordinator()

        assertTrue(coordinator.handlePersistedAuthorizationRoute("token", "moderation"))

        assertEquals(0, manager.mutations)
    }

    @Test
    fun aCallbackForTheSameContextSendsTheWaitingChangeOnce() = runBlocking {
        val coordinator = coordinator()
        coordinator.mutate(nsfwOn)
        assertEquals(PostManagePhase.AUTHORIZATION_REQUIRED, coordinator.uiState.value.phase)

        coordinator.handlePersistedAuthorizationRoute("token", "moderation")

        assertEquals(2, manager.mutations)
        assertEquals(PostManagePhase.SUCCEEDED, coordinator.uiState.value.phase)
    }

    @Test
    fun aCallbackForAnotherContextSendsNothingAndSaysSo() = runBlocking {
        val coordinator = coordinator()
        coordinator.mutate(nsfwOn)

        coordinator.handlePersistedAuthorizationRoute("token", "flair")

        assertEquals(1, manager.mutations)
        assertEquals(PostManageFailureKind.CALLBACK, coordinator.uiState.value.failureKind)
    }

    @Test
    fun anUnknownContextIsIgnored() = runBlocking {
        val coordinator = coordinator()
        coordinator.mutate(nsfwOn)
        val before = coordinator.uiState.value

        assertTrue(coordinator.handlePersistedAuthorizationRoute("token", "not-a-context"))

        assertEquals(before, coordinator.uiState.value)
        assertEquals(1, manager.mutations)
    }

    @Test
    fun anAuthorizationForAnotherAccountSendsNothing() = runBlocking {
        val coordinator = coordinator(upgradedAccount = "reddit:someone_else")
        coordinator.mutate(nsfwOn)

        coordinator.handlePersistedAuthorizationRoute("token", "moderation")

        assertEquals(1, manager.mutations)
        assertEquals(PostManageFailureKind.ACCOUNT_CHANGED, coordinator.uiState.value.failureKind)
    }
}
