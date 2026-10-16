package app.subshell.prototype.auth

import app.subshell.prototype.data.account.RedditModeratorAction
import app.subshell.prototype.data.account.RedditModeratorActionRequest
import app.subshell.prototype.data.account.RedditModeratorUserBanRequest
import app.subshell.prototype.data.auth.AuthenticatedRedditBannedUsersReader
import app.subshell.prototype.data.auth.AuthenticatedRedditBannedUsersResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModQueueResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModerationLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditModerationTargetResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratedCommunitiesResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratorUserMutationResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratorUserWriter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * LOW11: a list read that came in during a ban was dropped for good (OCB-591),
 * and the read-back after a moderator action was skipped while an older queue
 * load ran (OCB-426).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Low11AuthRestTest {
    @Test
    fun aListReadSkippedDuringABanRunsAfterIt() = runBlocking<Unit> {
        var reads = 0
        val writeGate = CompletableDeferred<Unit>()
        val coordinator = ModeratorUserManagementCoordinator(
            capability = null,
            reader = object : AuthenticatedRedditBannedUsersReader {
                override suspend fun load(accountStableId: String, rawCommunityName: String, after: String?, username: String?): AuthenticatedRedditBannedUsersResult {
                    if (username == null) reads++
                    return AuthenticatedRedditBannedUsersResult.Loaded(rawCommunityName, emptyList(), null)
                }
            },
            writer = object : AuthenticatedRedditModeratorUserWriter {
                override suspend fun mutate(request: RedditModeratorUserBanRequest): AuthenticatedRedditModeratorUserMutationResult {
                    writeGate.await()
                    return AuthenticatedRedditModeratorUserMutationResult.Acknowledged
                }
            },
            clientMode = null,
            activeAccountStableId = { "acct" },
            activeAccountUsername = { "me" },
        )
        coordinator.open("acct", "subshell")
        val ban = launch(Dispatchers.Unconfined, CoroutineStart.UNDISPATCHED) {
            coordinator.mutate(RedditModeratorUserBanRequest("acct", "subshell", "target_user", banned = true))
        }
        coordinator.load("acct")
        assertEquals("skipped while the ban runs", 0, reads)

        writeGate.complete(Unit)
        ban.join()
        assertEquals(1, reads)
    }

    @Test
    fun theReadBackAfterAModeratorActionReplacesAnOlderQueueLoad() {
        val loader = QueueLoader()
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Unconfined)
        val coordinator = ModerationQueueCoordinator(moderationLoader = loader, scope = scope)
        coordinator.loadRedditModQueue("acct", "subshell")
        assertEquals(1, loader.queueLoads)

        coordinator.reconcileModerationQueue(
            RedditModeratorActionRequest("acct", "subshell", "t3_abc", RedditModeratorAction.APPROVE),
        )

        assertEquals(2, loader.queueLoads)
        kotlinx.coroutines.runBlocking { scope.coroutineContext[kotlinx.coroutines.Job]?.cancel() }
    }

    private class QueueLoader : AuthenticatedRedditModerationLoader {
        var queueLoads = 0
        override suspend fun loadModeratorCommunities(accountStableId: String): AuthenticatedRedditModeratedCommunitiesResult =
            CompletableDeferred<AuthenticatedRedditModeratedCommunitiesResult>().await()
        override suspend fun loadModQueue(accountStableId: String, rawCommunityName: String, after: String?): AuthenticatedRedditModQueueResult {
            queueLoads++
            return CompletableDeferred<AuthenticatedRedditModQueueResult>().await()
        }
        override suspend fun resolveTargetPost(accountStableId: String, postId: String): AuthenticatedRedditModerationTargetResult =
            AuthenticatedRedditModerationTargetResult.NotFound
    }
}
