package app.subshell.prototype.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import app.subshell.prototype.data.RepositoryPager
import app.subshell.prototype.data.RepositoryPagingSnapshot
import app.subshell.prototype.data.RepositoryQueryKey
import app.subshell.prototype.data.SubshellRepository
import app.subshell.prototype.model.Post
import app.subshell.prototype.ui.FeedEntryRefreshDecision
import app.subshell.prototype.ui.FeedEntryRefreshGate
import app.subshell.prototype.ui.FeedReturnAnchor
import app.subshell.prototype.ui.FeedScrollAnchor
import app.subshell.prototype.ui.FeedScrollAnchorStore
import app.subshell.prototype.ui.FeedScrollKey
import app.subshell.prototype.ui.ProfileHistoryEntryRefreshGate

/**
 * Post paging (one RepositoryPager per query key, their snapshots, the
 * invalidation revision, the profile-history and feed entry refresh gates) and the feed
 * scroll/return anchor API over the store SubshellState owns for process
 * state. Extracted verbatim from SubshellState, which holds one instance and
 * delegates to it; behaviour is unchanged.
 */
internal class PostPagingState(
    private val repository: SubshellRepository,
    private val feedScrollAnchorStore: FeedScrollAnchorStore,
    private val isPostDeleted: (String) -> Boolean,
    private val feedEntryRefreshGate: FeedEntryRefreshGate = FeedEntryRefreshGate(),
    // PERF02_PAGER_LOADS_OFF_MAIN_V1 (#69): Inline keeps every operation on the
    // caller; the app passes a background executor so Room reads leave Main.
    private val executor: PostPagingExecutor = PostPagingExecutor.Inline,
) {
    internal var postPagingInvalidationRevision by mutableIntStateOf(0)
        private set
    internal val postPagingSnapshots =
        mutableStateMapOf<RepositoryQueryKey, RepositoryPagingSnapshot<Post>>()
    private val postPagers = linkedMapOf<RepositoryQueryKey, PostPagerEntry>()

    // A pager and whether its first load has run. The flag is read and written
    // only inside executor.execute, in order, so it stays in step with the pager
    // (the published snapshot arrives later on Main).
    private class PostPagerEntry(val pager: RepositoryPager<Post>) {
        var initialLoadDone = false
    }

    // Results of pagers from before invalidatePostPaging() are dropped.
    private var pagingGeneration = 0
    private val requestedInitialLoads = mutableSetOf<RepositoryQueryKey>()

    private val profileHistoryEntryRefreshGate = ProfileHistoryEntryRefreshGate()

    internal fun feedScrollAnchor(key: FeedScrollKey): FeedScrollAnchor =
        feedScrollAnchorStore.anchor(key)

    internal fun updateFeedScrollAnchor(
        key: FeedScrollKey,
        itemKey: String?,
        fallbackIndex: Int,
        pixelOffset: Int,
    ) {
        feedScrollAnchorStore.update(
            key = key,
            anchor = FeedScrollAnchor(
                itemKey = itemKey,
                fallbackIndex = fallbackIndex.coerceAtLeast(0),
                pixelOffset = pixelOffset.coerceAtLeast(0),
            ),
        )
    }

    internal fun captureFeedScrollAnchor(
        key: FeedScrollKey,
        itemKey: String?,
        fallbackIndex: Int,
        pixelOffset: Int,
    ) {
        feedScrollAnchorStore.capture(
            key = key,
            anchor = FeedScrollAnchor(
                itemKey = itemKey,
                fallbackIndex = fallbackIndex.coerceAtLeast(0),
                pixelOffset = pixelOffset.coerceAtLeast(0),
            ),
        )
    }

    internal fun releaseFeedScrollAnchor(key: FeedScrollKey) {
        feedScrollAnchorStore.release(key)
    }

    internal fun captureFeedReturnAnchor(
        key: FeedScrollKey,
        itemKey: String,
        itemOffset: Int,
    ) {
        feedScrollAnchorStore.captureReturn(
            key = key,
            anchor = FeedReturnAnchor(
                itemKey = itemKey,
                itemOffset = itemOffset,
            ),
        )
    }

    internal fun feedReturnAnchor(key: FeedScrollKey): FeedReturnAnchor? =
        feedScrollAnchorStore.returnAnchor(key)

    internal fun releaseFeedReturnAnchor(key: FeedScrollKey) {
        feedScrollAnchorStore.releaseReturn(key)
    }

    internal fun feedScrollAnchorSnapshot(): Map<FeedScrollKey, FeedScrollAnchor> =
        feedScrollAnchorStore.snapshot()

    internal fun postPagingSnapshot(
        queryKey: RepositoryQueryKey,
    ): RepositoryPagingSnapshot<Post> =
        postPagingSnapshots[queryKey] ?: RepositoryPagingSnapshot.initial(queryKey)

    // ISSUE257_HOME_SAYS_LOADING_UNTIL_ITS_STORE_IS_READ_V1: false while the
    // feed has no posts and its read of the store has not finished (no
    // snapshot yet, or the first read in flight), so an unread feed is not
    // taken for an empty one.
    internal fun postPagingStoreRead(queryKey: RepositoryQueryKey): Boolean {
        val snapshot = postPagingSnapshots[queryKey] ?: return false
        return snapshot.items.isNotEmpty() || !(snapshot.initialLoading || snapshot.refreshing)
    }

    internal fun ensurePostPaging(queryKey: RepositoryQueryKey) {
        if (queryKey in postPagingSnapshots || !requestedInitialLoads.add(queryKey)) return
        runPaging(queryKey) { entry ->
            if (!entry.initialLoadDone) entry.loadInitial()
        }
    }

    internal fun refreshPostPaging(queryKey: RepositoryQueryKey) {
        runPaging(queryKey) { entry ->
            entry.pager.refresh()
            entry.initialLoadDone = true
        }
    }

    internal fun synchronizePostPagingAfterRemoteWrite(
        queryKey: RepositoryQueryKey,
    ) {
        runPaging(queryKey) { entry ->
            entry.pager.synchronizeAfterRemotePageWrite()
            entry.initialLoadDone = true
        }
    }

    /**
     * FEEDSSOT01_FEEDS_FOLLOW_THE_STORE_V1: every feed already loaded reads its
     * window from the store again (the read the remote-write push uses), on
     * the paging executor, in order with its other loads.
     */
    internal fun synchronizeLoadedPostPaging() {
        postPagingSnapshots.keys.toList().forEach { queryKey ->
            runPaging(queryKey) { entry ->
                if (entry.initialLoadDone) entry.pager.synchronizeAfterRemotePageWrite()
            }
        }
    }

    internal fun claimProfileHistoryAutoRefresh(
        queryKey: RepositoryQueryKey,
    ): Boolean = profileHistoryEntryRefreshGate.claim(queryKey)

    // ISSUE17_AUTHENTICATED_FEED_ENTRY_REVALIDATION_V1
    internal fun claimFeedEntryRefresh(
        queryKey: RepositoryQueryKey,
        deepInList: Boolean = false,
    ): FeedEntryRefreshDecision =
        feedEntryRefreshGate.claim(queryKey, deepInList)

    internal fun appendPostPaging(queryKey: RepositoryQueryKey) {
        runPaging(queryKey) { entry ->
            if (!entry.initialLoadDone) entry.loadInitial() else entry.pager.append()
        }
    }

    internal fun retryPostPaging(queryKey: RepositoryQueryKey) {
        runPaging(queryKey) { entry ->
            if (!entry.initialLoadDone) entry.loadInitial() else entry.pager.retry()
        }
    }

    internal fun invalidatePostPaging() {
        postPagers.clear()
        postPagingSnapshots.clear()
        requestedInitialLoads.clear()
        pagingGeneration += 1
        feedEntryRefreshGate.reset()
        profileHistoryEntryRefreshGate.reset()
        postPagingInvalidationRevision += 1
    }

    private fun PostPagerEntry.loadInitial() {
        pager.loadInitial()
        initialLoadDone = true
    }

    private fun runPaging(queryKey: RepositoryQueryKey, operation: (PostPagerEntry) -> Unit) {
        val entry = postPager(queryKey)
        executor.execute { operation(entry) }
    }

    private fun postPager(queryKey: RepositoryQueryKey): PostPagerEntry =
        postPagers.getOrPut(queryKey) {
            val generation = pagingGeneration
            PostPagerEntry(
                RepositoryPager(
                    queryKey = queryKey,
                    pageSize = DATA02_POST_PAGE_SIZE,
                    stableId = Post::id,
                    loadPage = repository::posts,
                    onSnapshot = { snapshot ->
                        executor.publish {
                            if (generation == pagingGeneration) {
                                postPagingSnapshots[queryKey] = snapshot.copy(
                                    items = snapshot.items.filterNot { post -> isPostDeleted(post.id) },
                                )
                            }
                        }
                    },
                ),
            )
        }
}

private const val DATA02_POST_PAGE_SIZE = 12
