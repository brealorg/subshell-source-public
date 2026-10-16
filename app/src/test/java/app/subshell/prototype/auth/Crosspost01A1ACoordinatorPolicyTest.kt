package app.subshell.prototype.auth

import app.subshell.prototype.data.account.AccountSessionDirectory
import app.subshell.prototype.data.account.AccountSessionDirectorySnapshot
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.TextPostWriteFailureKind
import app.subshell.prototype.data.account.TextPostWritePhase
import app.subshell.prototype.data.account.TextPostWriteRequest
import app.subshell.prototype.data.auth.AuthBrowserLaunchResult
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityResult
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.reddit.AuthenticatedTextPostSubmitFailureKind
import app.subshell.prototype.data.auth.reddit.AuthenticatedTextPostSubmitRequest
import app.subshell.prototype.data.auth.reddit.AuthenticatedTextPostSubmitResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedTextPostSubmitter
import app.subshell.prototype.data.drafts.Crosspost01A1AMemoryPreferences
import app.subshell.prototype.data.drafts.TextPostSubmitAttemptStore
import app.subshell.prototype.data.network.NetworkRateLimit
import app.subshell.prototype.data.network.NetworkResponseMetadata
import app.subshell.prototype.data.network.reddit.RedditSubmittedTextPost
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Crosspost01A1ACoordinatorPolicyTest {
    private val account = account("crossposter")
    private val other = account("otherwriter")
    private fun request() = TextPostWriteRequest(
        accountStableId = account.stableId, community = "subshell",
        title = "Shared original", text = "", crosspostFullname = "t3_source123",
    )

    @Test
    fun authorizationCompletionDoesNotAutomaticallyPublishCrosspost() = runBlocking {
        val directory = Directory(account, other)
        val capability = Capability(account)
        val submitter = Submitter(capability)
        val coordinator = TextPostWriteCoordinator(
            capability, submitter, directory, OAuthClientMode.REDREADER_COMPATIBILITY,
        )
        val request = request()
        coordinator.submit(request)
        assertEquals(TextPostWritePhase.AUTHORIZATION_REQUIRED, coordinator.uiState.value.phase)
        assertEquals(1, submitter.requests.size)
        assertTrue(coordinator.beginAuthorization(request) is AuthBrowserLaunchResult.Ready)
        capability.storeCallback()
        assertTrue(coordinator.handleCallbackResultTokenIfOwned("success"))
        assertEquals(TextPostWritePhase.IDLE, coordinator.uiState.value.phase)
        assertEquals(1, submitter.requests.size)
        assertEquals(0L, coordinator.uiState.value.completionRevision)

        coordinator.submit(request)
        assertEquals(TextPostWritePhase.SUCCEEDED, coordinator.uiState.value.phase)
        assertEquals(2, submitter.requests.size)
        assertEquals(account.stableId, submitter.requests.last().accountStableId)
        assertEquals("t3_source123", submitter.requests.last().crosspostFullname)
        assertEquals(request.requestKey, submitter.requests.last().requestKey)
        assertEquals("t3_created123", coordinator.uiState.value.submittedPost?.fullname)
        assertEquals("t3_source123", coordinator.uiState.value.submittedPost?.crosspostFullname)
    }

    @Test
    fun textPostAuthorizationBehaviorIsUnchanged() = runBlocking {
        val capability = Capability(account)
        val submitter = Submitter(capability)
        val coordinator = TextPostWriteCoordinator(
            capability, submitter, Directory(account, other), OAuthClientMode.REDREADER_COMPATIBILITY,
        )
        val request = request().copy(crosspostFullname = null, text = "Ordinary text post")
        coordinator.submit(request)
        coordinator.beginAuthorization(request)
        capability.storeCallback()
        coordinator.handleCallbackResultTokenIfOwned("success")
        assertEquals(2, submitter.requests.size)
        assertEquals(TextPostWritePhase.SUCCEEDED, coordinator.uiState.value.phase)
    }

    @Test
    fun accountSwitchDuringAuthorizationDoesNotPublish() = runBlocking {
        val directory = Directory(account, other)
        val capability = Capability(account)
        val submitter = Submitter(capability)
        val coordinator = TextPostWriteCoordinator(
            capability, submitter, directory, OAuthClientMode.REDREADER_COMPATIBILITY,
        )
        val request = request()
        coordinator.submit(request)
        coordinator.beginAuthorization(request)
        directory.selectAccount(other.stableId)
        capability.storeCallback()
        coordinator.handleCallbackResultTokenIfOwned("success")
        assertEquals(1, submitter.requests.size)
        assertEquals(TextPostWriteFailureKind.ACCOUNT_CHANGED, coordinator.uiState.value.failureKind)
        coordinator.submit(request)
        assertEquals(1, submitter.requests.size)
    }

    @Test
    fun uncertainCrosspostSurvivesRestartAndBlocksAutomaticOrManualDuplicateDispatch() = runBlocking {
        val fixture = Crosspost01A1AMemoryPreferences()
        val store = TextPostSubmitAttemptStore(fixture.preferences) { 42_000L }
        val capability = Capability(account).apply { authorized = true }
        val submitter = Submitter(capability, store, outcomeUnknown = true)
        val directory = Directory(account, other)
        val coordinator = TextPostWriteCoordinator(
            capability, submitter, directory, OAuthClientMode.REDREADER_COMPATIBILITY, store,
        )
        val request = request()
        coordinator.submit(request)
        assertEquals(TextPostWritePhase.OUTCOME_UNKNOWN, coordinator.uiState.value.phase)
        assertEquals(42_000L, store.load(account.stableId)?.dispatchedAtEpochMs)
        assertEquals("t3_source123", store.load(account.stableId)?.request?.crosspostFullname)
        coordinator.submit(request)
        assertEquals(1, submitter.requests.size)
        coordinator.submit(request.copy(crosspostFullname = "t3_different"))
        assertEquals(1, submitter.requests.size)
        // COMPOSER02: the other draft is told which post blocks it (was ALREADY_SUBMITTING).
        assertEquals(TextPostWriteFailureKind.EARLIER_POST_UNCONFIRMED, coordinator.uiState.value.failureKind)

        val recreated = TextPostWriteCoordinator(
            capability, submitter, directory, OAuthClientMode.REDREADER_COMPATIBILITY,
            TextPostSubmitAttemptStore(fixture.preferences),
        )
        recreated.restorePendingSubmitState()
        assertEquals(TextPostWritePhase.OUTCOME_UNKNOWN, recreated.uiState.value.phase)
        assertEquals(1, submitter.requests.size)
        recreated.reconcilePendingSubmit()
        assertEquals(1, submitter.requests.size)
        assertEquals("t3_source123", submitter.reconciliations.single().crosspostFullname)
        assertEquals(TextPostWritePhase.OUTCOME_UNKNOWN, recreated.uiState.value.phase)
    }

    private class Directory(vararg accounts: AccountSessionSummary) : AccountSessionDirectory {
        private var current = AccountSessionDirectorySnapshot(
            accounts = accounts.toList(), activeAccountId = accounts.first().stableId,
        )
        override fun snapshot(): AccountSessionDirectorySnapshot = current
        override fun selectAccount(stableId: String): AccountSessionDirectorySnapshot {
            require(current.accounts.any { it.stableId == stableId })
            current = current.copy(activeAccountId = stableId)
            return current
        }
    }

    private class Capability(private val account: AccountSessionSummary) : AuthCommentWriteCapability {
        var authorized = false
        private var pending = false
        private var callback = false
        override suspend fun beginAuthorization(accountStableId: String): String {
            require(accountStableId == account.stableId)
            pending = true
            return "https://www.reddit.com/api/v1/authorize?state=crosspost01a1a-fixture"
        }
        override fun hasPendingAuthorization(): Boolean = pending
        override fun hasStoredCallback(): Boolean = callback
        override fun cancelPendingAuthorization() { pending = false; callback = false }
        override suspend fun completePendingAuthorization(): AuthCommentWriteCapabilityResult {
            check(callback)
            pending = false
            callback = false
            authorized = true
            return AuthCommentWriteCapabilityResult.Upgraded(
                accountStableId = account.stableId,
                username = account.username,
            )
        }
        override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult =
            AuthSessionResult.ReauthenticationRequired
        fun storeCallback() { check(pending); pending = false; callback = true }
    }

    private class Submitter(
        private val capability: Capability,
        private val store: TextPostSubmitAttemptStore? = null,
        private val outcomeUnknown: Boolean = false,
    ) : AuthenticatedTextPostSubmitter {
        val requests = mutableListOf<AuthenticatedTextPostSubmitRequest>()
        val reconciliations = mutableListOf<AuthenticatedTextPostSubmitRequest>()
        override suspend fun submit(request: AuthenticatedTextPostSubmitRequest): AuthenticatedTextPostSubmitResult {
            requests += request
            if (!capability.authorized) return AuthenticatedTextPostSubmitResult.Failed(
                AuthenticatedTextPostSubmitFailureKind.AUTHORIZATION_REQUIRED,
            )
            store?.markDispatched(request.accountStableId, request.requestKey)
            if (outcomeUnknown) return AuthenticatedTextPostSubmitResult.Failed(
                AuthenticatedTextPostSubmitFailureKind.OUTCOME_UNKNOWN,
            )
            return AuthenticatedTextPostSubmitResult.Submitted(
                accountStableId = request.accountStableId,
                post = RedditSubmittedTextPost(
                    fullname = "t3_created123", id = "created123",
                    community = request.community, title = request.title, text = request.text,
                    permalink = "/r/subshell/comments/created123/",
                ),
                metadata = NetworkResponseMetadata(
                    statusCode = 200, receivedAtEpochMs = 0L,
                    rateLimit = NetworkRateLimit(remaining = null, used = null, resetAfterMs = null),
                    retryAfterMs = null,
                ),
            )
        }
        override suspend fun reconcile(
            request: AuthenticatedTextPostSubmitRequest,
            username: String,
            dispatchedAtEpochMs: Long,
        ): AuthenticatedTextPostSubmitResult {
            reconciliations += request
            return AuthenticatedTextPostSubmitResult.Failed(AuthenticatedTextPostSubmitFailureKind.OUTCOME_UNKNOWN)
        }
    }

    private fun account(name: String) = AccountSessionSummary(
        stableId = "reddit:redreader_compatibility:t2_$name", username = name,
        source = AccountSessionSource.AUTHENTICATED, state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
}
