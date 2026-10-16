package app.subshell.prototype.auth

import app.subshell.prototype.data.account.RedditModeratorAction
import app.subshell.prototype.data.account.RedditModeratorActionRequest
import app.subshell.prototype.data.account.RedditModeratorQueueItem
import app.subshell.prototype.data.account.RedditModeratorQueueKind
import app.subshell.prototype.data.auth.AuthenticatedRedditModQueueResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratedCommunitiesResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModerationLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditModerationTargetResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * STEP3 (mod01a1, mod01a2, mod01a22): acting on an item of the combined mod
 * queue takes it out of that list at once and puts it back in the same place
 * if the action is rolled back; the read-back after an action reloads the
 * combined queue, not the item's own community; an action for another
 * community or another account leaves the shown queue alone.
 */
class Step3ModerationQueueCoordinatorTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val loader = ScriptedLoader()
    private val coordinator = ModerationQueueCoordinator(moderationLoader = loader, scope = scope)

    private val subshellPost = RedditModeratorQueueItem(
        fullname = "t3_s",
        kind = RedditModeratorQueueKind.POST,
        communityName = "subshell",
        author = "poster",
        summary = "A reported post",
    )
    private val androidComment = RedditModeratorQueueItem(
        fullname = "t1_a",
        kind = RedditModeratorQueueKind.COMMENT,
        communityName = "android",
        author = "commenter",
        summary = "A reported comment",
    )

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun request(community: String, fullname: String, account: String = "acct") =
        RedditModeratorActionRequest(account, community, fullname, RedditModeratorAction.REMOVE)

    private fun items() = coordinator.redditModerationUiState.value.queueItems.map { it.fullname }

    @Test
    fun anItemOfTheCombinedQueueLeavesAtOnceAndComesBackInItsPlace() {
        loader.items = listOf(subshellPost, androidComment)
        coordinator.loadRedditModQueue("acct", "mod")
        assertEquals(listOf("t3_s", "t1_a"), items())

        coordinator.optimisticallyRemoveModerationQueueItem(request("android", "t1_a"))
        assertEquals(listOf("t3_s"), items())

        coordinator.rollbackModerationQueueItem(request("android", "t1_a"))
        assertEquals(listOf("t3_s", "t1_a"), items())
    }

    @Test
    fun theReadBackAfterAnActionReloadsTheCombinedQueue() {
        loader.items = listOf(subshellPost, androidComment)
        coordinator.loadRedditModQueue("acct", "mod")
        assertEquals(listOf("mod"), loader.queueLoads)

        coordinator.reconcileModerationQueue(request("android", "t1_a"))

        assertEquals(listOf("mod", "mod"), loader.queueLoads)
        assertEquals("mod", coordinator.redditModerationUiState.value.queueCommunityName)
    }

    @Test
    fun anActionForAnotherCommunityOrAccountLeavesTheShownQueueAlone() {
        loader.items = listOf(subshellPost)
        coordinator.loadRedditModQueue("acct", "subshell")

        coordinator.optimisticallyRemoveModerationQueueItem(request("android", "t3_s"))
        assertEquals(listOf("t3_s"), items())

        coordinator.optimisticallyRemoveModerationQueueItem(request("subshell", "t3_s", account = "other"))
        assertEquals(listOf("t3_s"), items())
    }

    private class ScriptedLoader : AuthenticatedRedditModerationLoader {
        var items: List<RedditModeratorQueueItem> = emptyList()
        val queueLoads = mutableListOf<String>()

        override suspend fun loadModeratorCommunities(accountStableId: String): AuthenticatedRedditModeratedCommunitiesResult =
            AuthenticatedRedditModeratedCommunitiesResult.Loaded(setOf("subshell", "android"))

        override suspend fun loadModQueue(
            accountStableId: String,
            rawCommunityName: String,
            after: String?,
        ): AuthenticatedRedditModQueueResult {
            queueLoads += rawCommunityName
            return AuthenticatedRedditModQueueResult.Loaded(rawCommunityName, items, null)
        }

        override suspend fun resolveTargetPost(accountStableId: String, postId: String): AuthenticatedRedditModerationTargetResult =
            AuthenticatedRedditModerationTargetResult.NotFound
    }
}
