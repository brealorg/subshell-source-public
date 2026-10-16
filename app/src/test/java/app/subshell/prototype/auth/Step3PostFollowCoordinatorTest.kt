package app.subshell.prototype.auth

import app.subshell.prototype.data.account.PostFollowFailureKind
import app.subshell.prototype.data.account.PostFollowPhase
import app.subshell.prototype.data.account.PostFollowRequest
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityResult
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostFollowResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostFollowWriter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * STEP3 (followpost01a1b): the follow coordinator reports what Reddit
 * answered, keeps a request waiting for authorization and sends it once the
 * same account is authorized, retries only what may be retried, and sends
 * nothing for an account that is no longer active or a target that is not a
 * post.
 */
class Step3PostFollowCoordinatorTest {
    private var active: String? = "reddit:me"
    private val sent = mutableListOf<PostFollowRequest>()
    private val answers = ArrayDeque<AuthenticatedPostFollowResult>()
    private val writer = AuthenticatedPostFollowWriter { request ->
        sent += request
        answers.removeFirstOrNull() ?: AuthenticatedPostFollowResult.Updated(request)
    }
    private val capability = AuthorizingCapability()

    private fun coordinator() = PostFollowCoordinator(
        capability = capability,
        writer = writer,
        clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
        activeAccountStableId = { active },
    )

    private val follow = PostFollowRequest("reddit:me", "t3_x", followed = true)

    @Test
    fun aConfirmedFollowSucceedsWithTheNewState() = runBlocking {
        val coordinator = coordinator()

        coordinator.setFollowed(follow)

        val state = coordinator.uiState.value
        assertEquals(PostFollowPhase.SUCCEEDED, state.phase)
        assertEquals(true, state.followed)
        assertEquals("t3_x", state.postFullname)
        assertEquals(1L, state.completionRevision)
        assertEquals(listOf(follow), sent)
    }

    @Test
    fun aFollowWaitingForAuthorizationIsSentOnceTheSameAccountIsAuthorized() = runBlocking {
        answers += AuthenticatedPostFollowResult.AuthorizationRequired
        val coordinator = coordinator()

        coordinator.setFollowed(follow)
        assertEquals(PostFollowPhase.AUTHORIZATION_REQUIRED, coordinator.uiState.value.phase)
        assertEquals(1, sent.size)

        capability.storedCallback = true
        capability.completion = AuthCommentWriteCapabilityResult.Upgraded("reddit:me", "me")
        assertTrue(coordinator.handleCallbackResultTokenIfOwned("granted"))

        assertEquals(listOf(follow, follow), sent)
        assertEquals(PostFollowPhase.SUCCEEDED, coordinator.uiState.value.phase)
    }

    @Test
    fun onlyARetryableFailureIsSentAgain() = runBlocking {
        answers += AuthenticatedPostFollowResult.Failed(PostFollowFailureKind.HTTP, retryable = false, statusCode = 403)
        val refused = coordinator()
        refused.setFollowed(follow)
        assertEquals(PostFollowPhase.FAILED, refused.uiState.value.phase)
        refused.retry()
        assertEquals("a refusal is not sent again", 1, sent.size)

        sent.clear()
        answers += AuthenticatedPostFollowResult.Failed(PostFollowFailureKind.TRANSPORT, retryable = true)
        val lost = coordinator()
        lost.setFollowed(follow)
        lost.retry()
        assertEquals("a retryable failure is sent once more", listOf(follow, follow), sent)
        assertEquals(PostFollowPhase.SUCCEEDED, lost.uiState.value.phase)
    }

    @Test
    fun anotherActiveAccountSendsNothing() = runBlocking {
        active = "reddit:other"
        val coordinator = coordinator()

        coordinator.setFollowed(follow)

        assertTrue(sent.isEmpty())
        assertEquals(PostFollowFailureKind.ACCOUNT_CHANGED, coordinator.uiState.value.failureKind)
    }

    @Test
    fun aTargetThatIsNotAPostIsRefusedBeforeTheWriter() = runBlocking {
        val coordinator = coordinator()

        coordinator.setFollowed(PostFollowRequest("reddit:me", "t1_comment", followed = true))

        assertTrue(sent.isEmpty())
        assertEquals(PostFollowFailureKind.VALIDATION, coordinator.uiState.value.failureKind)
    }

    private class AuthorizingCapability : AuthCommentWriteCapability {
        var storedCallback = false
        var completion: AuthCommentWriteCapabilityResult =
            AuthCommentWriteCapabilityResult.Upgraded("reddit:me", "me")

        override suspend fun beginAuthorization(accountStableId: String): String = "https://reddit.test/authorize"
        override fun hasPendingAuthorization() = false
        override fun hasStoredCallback() = storedCallback
        override fun cancelPendingAuthorization() = Unit
        override suspend fun completePendingAuthorization(): AuthCommentWriteCapabilityResult = completion
        override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult =
            AuthSessionResult.NoPendingCallback
    }
}
