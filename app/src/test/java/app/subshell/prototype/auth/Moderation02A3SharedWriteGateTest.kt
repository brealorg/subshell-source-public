package app.subshell.prototype.auth

import app.subshell.prototype.data.account.*
import app.subshell.prototype.data.auth.*
import app.subshell.prototype.data.auth.reddit.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class Moderation02A3SharedWriteGateTest {
    private fun request(action: RedditModeratorAction) =
        RedditModeratorActionRequest("owner", "subshell", "t3_abc", action)

    private fun coordinator(
        events: MutableList<String> = mutableListOf(),
        capability: AuthCommentWriteCapability? = null,
        write: suspend (RedditModeratorActionRequest) -> AuthenticatedRedditModerationActionResult,
    ) = ModerationActionCoordinator(
        capability,
        object : AuthenticatedRedditModerationWriter {
            override suspend fun mutate(request: RedditModeratorActionRequest) = write(request)
        },
        null,
        { "owner" },
        { events += "optimistic" },
        { events += "rollback" },
        { events += "reconcile" },
    )

    @Test
    fun contentControlsNeverUseQueueOptimismOrRollback() = runBlocking {
        val events = mutableListOf<String>()
        val c = coordinator(events) { AuthenticatedRedditModerationActionResult.Updated() }
        c.contentControlAction(request(RedditModeratorAction.LOCK)) { true }
        assertEquals(listOf("reconcile"), events)
    }

    @Test
    fun legacyModerateEntryCannotBypassFreshContentVerification() = runBlocking {
        var writes = 0
        val c = coordinator { writes++; AuthenticatedRedditModerationActionResult.Updated() }
        c.moderate(request(RedditModeratorAction.LOCK))
        assertEquals(0, writes)
        c.contentControlAction(request(RedditModeratorAction.LOCK)) { false }
        assertEquals(0, writes)
    }

    @Test
    fun reportAndContentControlsShareTheSameNonQueuingWriteGate() = runBlocking {
        withTimeout(2_000) {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val writes = mutableListOf<RedditModeratorAction>()
            val c = coordinator { req ->
                writes += req.action
                AuthenticatedRedditModerationActionResult.Updated()
            }
            val first = launch {
                c.reportAction(request(RedditModeratorAction.IGNORE_REPORTS)) {
                    entered.complete(Unit)
                    release.await()
                    true
                }
            }
            entered.await()
            val second = c.contentControlAction(request(RedditModeratorAction.LOCK)) { true }
            assertEquals(
                RedditModeratorActionFailureKind.ALREADY_MUTATING,
                (second as AuthenticatedRedditModerationActionResult.Failed).kind,
            )
            release.complete(Unit)
            first.join()
            assertEquals(listOf(RedditModeratorAction.IGNORE_REPORTS), writes)
        }
    }

    @Test
    fun retryNeverResendsAContentControl() = runBlocking {
        var writes = 0
        val c = coordinator {
            writes++
            AuthenticatedRedditModerationActionResult.Failed(
                RedditModeratorActionFailureKind.TRANSPORT,
                retryable = true,
            )
        }
        c.contentControlAction(request(RedditModeratorAction.LOCK)) { true }
        c.retry()
        assertEquals(1, writes)
        assertFalse(c.uiState.value.retryable)
    }

    @Test
    fun authorizationCompletionNeverAutomaticallyReplaysAContentControl() = runBlocking {
        var writes = 0
        val capability = object : AuthCommentWriteCapability {
            override suspend fun beginAuthorization(accountStableId: String) = "unused"
            override fun hasPendingAuthorization() = false
            override fun hasStoredCallback() = true
            override fun cancelPendingAuthorization() = Unit
            override suspend fun completePendingAuthorization() =
                AuthCommentWriteCapabilityResult.Upgraded("owner", "mod")
            override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult =
                AuthSessionResult.ReauthenticationRequired
        }
        val c = coordinator(capability = capability) {
            writes++
            AuthenticatedRedditModerationActionResult.AuthorizationRequired
        }
        c.contentControlAction(request(RedditModeratorAction.LOCK)) { true }
        c.resumeStoredAuthorizationIfPresent()
        c.retry()
        assertEquals(1, writes)
        assertEquals(RedditModeratorActionPhase.IDLE, c.uiState.value.phase)
    }
}
