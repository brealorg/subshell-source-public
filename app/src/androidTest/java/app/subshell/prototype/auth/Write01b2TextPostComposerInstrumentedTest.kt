package app.subshell.prototype.auth

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.subshell.prototype.data.account.AccountSessionDirectory
import app.subshell.prototype.data.account.AccountSessionDirectorySnapshot
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.SubmittedTextPost
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
import app.subshell.prototype.data.network.NetworkRateLimit
import app.subshell.prototype.data.network.NetworkResponseMetadata
import app.subshell.prototype.data.network.reddit.RedditSubmittedTextPost
import app.subshell.prototype.ui.CommentActionPlacement
import app.subshell.prototype.ui.PostComposerDraft
import app.subshell.prototype.ui.SubshellState
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Write01b2TextPostComposerInstrumentedTest {
    @Test
    fun authorizationPinnedSubmitAndRemoteCompletionPreserveDraftBoundaries() {
        runBlocking {
            val account = authenticatedAccount(
                stableId = WRITE01B2_ACCOUNT_ID,
                username = WRITE01B2_USERNAME,
            )
            val other = authenticatedAccount(
                stableId = "reddit:redreader_compatibility:t2_other",
                username = "other",
            )
            val directory = MutableWrite01b2Directory(
                AccountSessionDirectorySnapshot(
                    accounts = listOf(account, other),
                    activeAccountId = account.stableId,
                ),
            )
            val authorization = Write01b2Capability(account)
            val submitter = Write01b2Submitter(authorization)
            val coordinator = TextPostWriteCoordinator(
                capability = authorization,
                submitter = submitter,
                accountDirectory = directory,
                clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
            )
            val request = TextPostWriteRequest(
                accountStableId = account.stableId,
                community = "subshell",
                title = "WRITE01-B2 connected composer",
                text = "Created through the authenticated composer.",
            )

            coordinator.submit(request)
            assertEquals(TextPostWritePhase.AUTHORIZATION_REQUIRED, coordinator.uiState.value.phase)
            assertEquals(request.requestKey, coordinator.uiState.value.requestKey)
            assertEquals(1, submitter.requests.size)

            val authorizedRequest = request.copy(
                text = "Updated before granting post access.",
            )
            val launch = coordinator.beginAuthorization(authorizedRequest)
            assertTrue(launch is AuthBrowserLaunchResult.Ready)
            launch as AuthBrowserLaunchResult.Ready
            assertEquals(OAuthClientMode.REDREADER_COMPATIBILITY, launch.clientMode)
            assertEquals(TextPostWritePhase.AWAITING_CALLBACK, coordinator.uiState.value.phase)
            authorization.storeCallback()
            assertTrue(coordinator.handleCallbackResultTokenIfOwned("success"))

            val success = coordinator.uiState.value
            assertEquals(TextPostWritePhase.SUCCEEDED, success.phase)
            assertEquals(1L, success.completionRevision)
            assertEquals(account.stableId, success.accountStableId)
            assertEquals(authorizedRequest.requestKey, success.requestKey)
            assertEquals("b2proof", success.submittedPost?.id)
            assertEquals(
                listOf(account.stableId, account.stableId),
                submitter.requests.map { submitted -> submitted.accountStableId },
            )
            assertEquals(authorizedRequest.text, submitter.requests.last().text)

            val matchingState = SubshellState(
                initialCommentActionPlacement = CommentActionPlacement.RIGHT,
                initialAccountSessions = listOf(account, other),
                initialActiveAccountId = account.stableId,
            )
            matchingState.openPostComposer()
            matchingState.updatePostComposerDraft(
                PostComposerDraft(
                    community = authorizedRequest.community,
                    title = authorizedRequest.title,
                    body = authorizedRequest.text,
                ),
            )
            val matchingOpened = matchingState.completeRemoteTextPostComposer(
                accountStableId = requireNotNull(success.accountStableId),
                requestKey = requireNotNull(success.requestKey),
                submittedPost = requireNotNull(success.submittedPost),
            )
            assertEquals("b2proof", matchingOpened.id)
            assertEquals(WRITE01B2_USERNAME, matchingOpened.author)
            assertEquals(matchingOpened, matchingState.selectedPost)
            assertFalse(matchingState.postComposerVisible)
            assertFalse(matchingState.postComposerDraft.hasContent)

            val editedState = SubshellState(
                initialCommentActionPlacement = CommentActionPlacement.RIGHT,
                initialAccountSessions = listOf(account, other),
                initialActiveAccountId = account.stableId,
            )
            editedState.openPostComposer()
            editedState.updatePostComposerDraft(
                PostComposerDraft(
                    community = request.community,
                    title = request.title,
                    body = "Edited after the request was sent.",
                ),
            )
            val editedOpened = editedState.completeRemoteTextPostComposer(
                accountStableId = requireNotNull(success.accountStableId),
                requestKey = requireNotNull(success.requestKey),
                submittedPost = requireNotNull(success.submittedPost),
            )
            assertEquals("b2proof", editedOpened.id)
            assertEquals(WRITE01B2_USERNAME, editedOpened.author)
            // COMPOSER03: a draft that no longer matches what was sent stays open;
            // only the composer showing the sent draft moves on to the post.
            assertEquals(null, editedState.selectedPost)
            assertTrue(editedState.postComposerVisible)
            assertEquals("Edited after the request was sent.", editedState.postComposerDraft.body)

            coordinator.reset()
            directory.selectAccount(other.stableId)
            val requestCountBeforeAccountFailure = submitter.requests.size
            coordinator.submit(request.copy(title = "Must remain local"))
            val accountFailure = coordinator.uiState.value
            assertEquals(TextPostWritePhase.FAILED, accountFailure.phase)
            assertEquals(TextPostWriteFailureKind.ACCOUNT_CHANGED, accountFailure.failureKind)
            assertEquals(requestCountBeforeAccountFailure, submitter.requests.size)

            Log.i(
                WRITE01B2_LOG_TAG,
                "PINNED_ACCOUNT=true AUTHORIZATION_REQUEST_REPINNED=true " +
                    "AUTHORIZATION_RESUMED=true AUTO_SUBMIT_AFTER_GRANT=true " +
                    "UNAMBIGUOUS_SUCCESS_OPENS_POST=true MATCHING_DRAFT_CLEAR_POLICY=true " +
                    "EDITED_DRAFT_PRESERVED=true ACCOUNT_SWITCH_FAIL_CLOSED=true " +
                    "APP_UI_LANGUAGE=ENGLISH LIVE_REDDIT_NETWORK=false REMOTE_POST_WRITES=false",
            )
            Log.i(
                WRITE01B2_LOG_TAG,
                "RESULT=SUBSHELL_WRITE01B2_TEXT_POST_COMPOSER_RUNTIME_PASS",
            )
        }
    }
}

private class MutableWrite01b2Directory(
    private var current: AccountSessionDirectorySnapshot,
) : AccountSessionDirectory {
    override fun snapshot(): AccountSessionDirectorySnapshot = current

    override fun selectAccount(stableId: String): AccountSessionDirectorySnapshot {
        require(current.accounts.any { account -> account.stableId == stableId })
        current = current.copy(activeAccountId = stableId)
        return current
    }
}

private class Write01b2Capability(
    private val account: AccountSessionSummary,
) : AuthCommentWriteCapability {
    var authorized: Boolean = false
        private set
    private var pending = false
    private var storedCallback = false

    override suspend fun beginAuthorization(accountStableId: String): String {
        require(accountStableId == account.stableId)
        pending = true
        return "https://www.reddit.com/api/v1/authorize?state=write01b2"
    }

    override fun hasPendingAuthorization(): Boolean = pending

    override fun hasStoredCallback(): Boolean = storedCallback

    override fun cancelPendingAuthorization() {
        pending = false
        storedCallback = false
    }

    override suspend fun completePendingAuthorization(): AuthCommentWriteCapabilityResult {
        check(storedCallback)
        pending = false
        storedCallback = false
        authorized = true
        return AuthCommentWriteCapabilityResult.Upgraded(
            accountStableId = account.stableId,
            username = account.username,
        )
    }

    override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult =
        AuthSessionResult.ReauthenticationRequired

    fun storeCallback() {
        check(pending)
        pending = false
        storedCallback = true
    }
}

private class Write01b2Submitter(
    private val capability: Write01b2Capability,
) : AuthenticatedTextPostSubmitter {
    val requests = CopyOnWriteArrayList<AuthenticatedTextPostSubmitRequest>()

    override suspend fun submit(
        request: AuthenticatedTextPostSubmitRequest,
    ): AuthenticatedTextPostSubmitResult {
        requests += request
        if (!capability.authorized) {
            return AuthenticatedTextPostSubmitResult.Failed(
                AuthenticatedTextPostSubmitFailureKind.AUTHORIZATION_REQUIRED,
            )
        }
        return AuthenticatedTextPostSubmitResult.Submitted(
            accountStableId = request.accountStableId,
            post = RedditSubmittedTextPost(
                fullname = "t3_b2proof",
                id = "b2proof",
                community = request.community,
                title = request.title,
                text = request.text,
                permalink = "/r/subshell/comments/b2proof/write01b2_connected_composer/",
            ),
            metadata = NetworkResponseMetadata(
                statusCode = 200,
                receivedAtEpochMs = 0L,
                rateLimit = NetworkRateLimit(
                    remaining = null,
                    used = null,
                    resetAfterMs = null,
                ),
                retryAfterMs = null,
            ),
        )
    }
}

private fun authenticatedAccount(
    stableId: String,
    username: String,
): AccountSessionSummary = AccountSessionSummary(
    stableId = stableId,
    username = username,
    source = AccountSessionSource.AUTHENTICATED,
    state = AccountSessionState.CONNECTED,
    providerLabel = "Reddit",
)

private const val WRITE01B2_LOG_TAG = "subshell-write01b2"
private const val WRITE01B2_ACCOUNT_ID = "reddit:redreader_compatibility:t2_write01b2"
private const val WRITE01B2_USERNAME = "brealorg"
