package io.github.brealorg.subshell.auth

import io.github.brealorg.subshell.data.account.RedditModeratorQueueItem
import io.github.brealorg.subshell.data.account.RedditModeratorQueueKind
import io.github.brealorg.subshell.data.account.RedditModeratorQueueList
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditModQueueResult
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditModeratedCommunitiesResult
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditModerationLoader
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditModerationTargetResult
import io.github.brealorg.subshell.data.network.reddit.redditModQueueRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #295 part 3: Mod tools shows a community's moderator lists (Queue, Removed,
 * Edited, Unmoderated) through the same queue state. ISSUE295_REMOVED_CONTENT_LISTS_V1.
 */
class Issue295RemovedContentListsTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun item(fullname: String) = RedditModeratorQueueItem(
        fullname = fullname,
        kind = RedditModeratorQueueKind.POST,
        communityName = "subshell",
        author = "someone",
        summary = "A post",
    )

    private class ListLoader : AuthenticatedRedditModerationLoader {
        val loads = mutableListOf<Triple<String, RedditModeratorQueueList, String?>>()
        val byList = mutableMapOf<RedditModeratorQueueList, List<RedditModeratorQueueItem>>()
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun loadModeratorCommunities(accountStableId: String) =
            AuthenticatedRedditModeratedCommunitiesResult.Loaded(setOf("subshell"))

        override suspend fun loadModQueue(accountStableId: String, rawCommunityName: String, after: String?) =
            loadModList(accountStableId, rawCommunityName, RedditModeratorQueueList.QUEUE, after)

        override suspend fun loadModList(
            accountStableId: String,
            rawCommunityName: String,
            list: RedditModeratorQueueList,
            after: String?,
        ): AuthenticatedRedditModQueueResult {
            loads += Triple(rawCommunityName, list, after)
            gate?.await()
            return AuthenticatedRedditModQueueResult.Loaded(
                communityName = rawCommunityName,
                items = byList[list].orEmpty(),
                nextCursor = if (after == null) "c1" else null,
            )
        }

        override suspend fun resolveTargetPost(accountStableId: String, postId: String) =
            AuthenticatedRedditModerationTargetResult.NotFound
    }

    @Test
    fun eachListAsksRedditForItsOwnListing() {
        assertEquals(listOf("r", "subshell", "about", "modqueue"), redditModQueueRequest("subshell").pathSegments)
        assertEquals(
            listOf("r", "subshell", "about", "spam"),
            redditModQueueRequest("subshell", list = RedditModeratorQueueList.REMOVED).pathSegments,
        )
        assertEquals(
            listOf("r", "subshell", "about", "edited"),
            redditModQueueRequest("subshell", list = RedditModeratorQueueList.EDITED).pathSegments,
        )
        assertEquals(
            listOf("r", "subshell", "about", "unmoderated"),
            redditModQueueRequest("subshell", list = RedditModeratorQueueList.UNMODERATED).pathSegments,
        )
    }

    @Test
    fun choosingAListLoadsItAndMorePagesAndReloadsStayOnIt() {
        val loader = ListLoader().apply {
            byList[RedditModeratorQueueList.QUEUE] = listOf(item("t3_q"))
            byList[RedditModeratorQueueList.REMOVED] = listOf(item("t3_r"))
        }
        val coordinator = ModerationQueueCoordinator(moderationLoader = loader, scope = scope)

        coordinator.loadRedditModQueue("acct", "subshell")
        coordinator.selectRedditModQueueList("acct", "subshell", RedditModeratorQueueList.REMOVED)

        val state = coordinator.redditModerationUiState.value
        assertEquals(RedditModeratorQueueList.REMOVED, state.queueList)
        assertEquals(listOf("t3_r"), state.queueItems.map { it.fullname })

        coordinator.appendRedditModQueue("acct", "subshell")
        coordinator.loadRedditModQueue("acct", "subshell", forceRefresh = true)
        assertEquals(
            listOf(
                Triple("subshell", RedditModeratorQueueList.QUEUE, null),
                Triple("subshell", RedditModeratorQueueList.REMOVED, null),
                Triple("subshell", RedditModeratorQueueList.REMOVED, "c1"),
                Triple("subshell", RedditModeratorQueueList.REMOVED, null),
            ),
            loader.loads,
        )

        // Choosing the list already shown asks nothing.
        coordinator.selectRedditModQueueList("acct", "subshell", RedditModeratorQueueList.REMOVED)
        assertEquals(4, loader.loads.size)
    }

    @Test
    fun anotherCommunityStartsOnTheQueue() {
        val loader = ListLoader()
        val coordinator = ModerationQueueCoordinator(moderationLoader = loader, scope = scope)
        coordinator.selectRedditModQueueList("acct", "subshell", RedditModeratorQueueList.EDITED)

        coordinator.loadRedditModQueue("acct", "android")

        assertEquals(RedditModeratorQueueList.QUEUE, coordinator.redditModerationUiState.value.queueList)
        assertEquals(Triple("android", RedditModeratorQueueList.QUEUE, null), loader.loads.last())
    }

    @Test
    fun aLateAnswerForTheListLeftBehindIsDropped() {
        val loader = ListLoader().apply {
            byList[RedditModeratorQueueList.QUEUE] = listOf(item("t3_q"))
            byList[RedditModeratorQueueList.UNMODERATED] = listOf(item("t3_u"))
        }
        val coordinator = ModerationQueueCoordinator(moderationLoader = loader, scope = scope)
        val slow = CompletableDeferred<Unit>()
        loader.gate = slow
        coordinator.loadRedditModQueue("acct", "subshell")
        assertTrue(coordinator.redditModerationUiState.value.queueLoading)

        loader.gate = null
        coordinator.selectRedditModQueueList("acct", "subshell", RedditModeratorQueueList.UNMODERATED)
        slow.complete(Unit)

        val state = coordinator.redditModerationUiState.value
        assertEquals(RedditModeratorQueueList.UNMODERATED, state.queueList)
        assertEquals(listOf("t3_u"), state.queueItems.map { it.fullname })
        assertFalse(state.queueLoading)
    }
}
