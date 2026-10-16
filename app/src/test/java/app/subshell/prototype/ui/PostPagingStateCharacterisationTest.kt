package app.subshell.prototype.ui

import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.RedditListingTimePeriod
import app.subshell.prototype.data.RepositoryLoadState
import app.subshell.prototype.data.RepositoryPageRequest
import app.subshell.prototype.data.RepositoryResult
import app.subshell.prototype.data.SubshellRepository
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import app.subshell.prototype.model.Post
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterisation of post paging and feed scroll anchors inside
 * SubshellState as they behave today, written before they move to
 * PostPagingState. Current behaviour, not a specification.
 */
class PostPagingStateCharacterisationTest {
    @Test
    fun pagersAreCreatedOncePerQueryKeyAndKeepIndependentSnapshots() {
        val repository = CountingRepository()
        val state = localState(repository)
        val best = queryKey(state, FeedSort.BEST)
        val top = queryKey(state, FeedSort.TOP)

        // An unknown key reports the pager's initial snapshot: empty, initial load "Loading".
        assertEquals(RepositoryLoadState.Loading, state.postPagingSnapshot(best).initialLoadState)
        assertTrue(state.postPagingSnapshot(best).items.isEmpty())

        state.ensurePostPaging(best)
        assertEquals(1, repository.postLoads)
        val firstPage = state.postPagingSnapshot(best)
        assertEquals(12, firstPage.items.size)
        assertNotEquals(null, firstPage.nextCursor)

        state.ensurePostPaging(best)
        assertEquals(1, repository.postLoads)
        assertSame(firstPage, state.postPagingSnapshot(best))

        state.ensurePostPaging(top)
        assertEquals(2, repository.postLoads)
        assertEquals(12, state.postPagingSnapshot(top).items.size)
        assertSame(firstPage, state.postPagingSnapshot(best))
    }

    @Test
    fun appendLoadsTheNextPageAndRefreshRestartsFromTheFirst() {
        val repository = CountingRepository()
        val state = localState(repository)
        val key = queryKey(state, FeedSort.BEST)

        state.appendPostPaging(key)
        assertEquals(1, repository.postLoads)
        assertEquals(12, state.postPagingSnapshot(key).items.size)

        state.appendPostPaging(key)
        assertEquals(2, repository.postLoads)
        val twoPages = state.postPagingSnapshot(key)
        assertEquals(24, twoPages.items.size)
        assertEquals(24, twoPages.items.map(Post::id).toSet().size)

        state.refreshPostPaging(key)
        assertEquals(3, repository.postLoads)
        val refreshed = state.postPagingSnapshot(key)
        assertEquals(12, refreshed.items.size)
        assertEquals(twoPages.generation + 1, refreshed.generation)
        assertEquals(twoPages.items.take(12).map(Post::id), refreshed.items.map(Post::id))

        state.retryPostPaging(key)
        assertEquals(3, repository.postLoads)
        assertSame(refreshed, state.postPagingSnapshot(key))

        val unseen = queryKey(state, FeedSort.NEW)
        state.retryPostPaging(unseen)
        assertEquals(4, repository.postLoads)
        assertEquals(12, state.postPagingSnapshot(unseen).items.size)
    }

    @Test
    fun invalidateDropsSnapshotsAndPagersAndBumpsTheRevision() {
        val repository = CountingRepository()
        val state = localState(repository)
        val key = queryKey(state, FeedSort.BEST)
        state.appendPostPaging(key)
        state.appendPostPaging(key)
        assertEquals(24, state.postPagingSnapshot(key).items.size)
        val revision = state.postPagingInvalidationRevision

        state.invalidatePostPaging()

        assertEquals(revision + 1, state.postPagingInvalidationRevision)
        assertTrue(state.postPagingSnapshot(key).items.isEmpty())
        assertEquals(RepositoryLoadState.Loading, state.postPagingSnapshot(key).initialLoadState)

        state.ensurePostPaging(key)
        assertEquals(3, repository.postLoads)
        assertEquals(12, state.postPagingSnapshot(key).items.size)
        assertEquals(0, state.postPagingSnapshot(key).generation)
    }

    @Test
    fun remoteWriteSynchronisationReloadsTheLoadedRangeAndKeepsDeletedPostsOut() {
        val repository = CountingRepository()
        val state = localState(repository)
        val key = queryKey(state, FeedSort.BEST)
        state.appendPostPaging(key)
        state.appendPostPaging(key)
        val before = state.postPagingSnapshot(key)
        assertEquals(24, before.items.size)

        state.synchronizePostPagingAfterRemoteWrite(key)

        val after = state.postPagingSnapshot(key)
        assertTrue(after.items.size >= before.items.size)
        assertEquals(before.items.map(Post::id), after.items.take(before.items.size).map(Post::id))
        assertTrue(repository.postLoads > 2)

        val victim = after.items.first().id
        state.applyDeletedPost(victim)
        assertFalse(victim in state.postPagingSnapshot(key).items.map(Post::id))
        state.refreshPostPaging(key)
        assertFalse(victim in state.postPagingSnapshot(key).items.map(Post::id))
    }

    @Test
    fun profileHistoryAutoRefreshIsClaimedOncePerQueryKey() {
        val state = localState(CountingRepository())
        val a = queryKey(state, FeedSort.BEST)
        val b = queryKey(state, FeedSort.TOP)

        assertTrue(state.claimProfileHistoryAutoRefresh(a))
        assertFalse(state.claimProfileHistoryAutoRefresh(a))
        assertTrue(state.claimProfileHistoryAutoRefresh(b))
        state.invalidatePostPaging()
        // LOW_PROFILE_HISTORY_GATE_FOLLOWS_THE_FEED_GATE_V1 (OCB-327, owner): like
        // the feed gate, an invalidation (account switch) lets it refresh again.
        assertTrue(state.claimProfileHistoryAutoRefresh(a))
    }

    @Test
    fun feedEntryRefreshIsClaimedOncePerQueryKeyAndResetByInvalidation() {
        var now = 1_000L
        val state = SubshellState(
            repository = CountingRepository(),
            initialAccountSessions = localFixtureAccountSummaries(listOf("root")),
            currentTimeMillis = { now },
        )
        val a = queryKey(state, FeedSort.BEST)
        val b = queryKey(state, FeedSort.TOP)

        assertEquals(FeedEntryRefreshDecision.EXECUTE_FIRST_ENTRY, state.claimFeedEntryRefresh(a))
        assertEquals(FeedEntryRefreshDecision.SKIP_REENTRY, state.claimFeedEntryRefresh(a))
        assertEquals(FeedEntryRefreshDecision.EXECUTE_FIRST_ENTRY, state.claimFeedEntryRefresh(b))

        // Issue #40: time no longer re-arms it (refresh on Pager creation only).
        now += 24 * 60 * 60 * 1_000L
        assertEquals(FeedEntryRefreshDecision.SKIP_REENTRY, state.claimFeedEntryRefresh(a))

        // Unlike the profile-history gate, invalidation (account directory change) re-arms it.
        state.invalidatePostPaging()
        assertEquals(
            FeedEntryRefreshDecision.DEFER_DEEP,
            state.claimFeedEntryRefresh(a, deepInList = true),
        )
        assertEquals(FeedEntryRefreshDecision.EXECUTE_FIRST_ENTRY, state.claimFeedEntryRefresh(a))
        assertEquals(FeedEntryRefreshDecision.EXECUTE_FIRST_ENTRY, state.claimFeedEntryRefresh(b))
    }

    @Test
    fun scrollAnchorsAreStoredAndReleasedPerFeedKey() {
        val state = localState(CountingRepository())
        val home = mainFeedScrollKey(FeedSource.HOME, FeedSort.BEST)
        val popular = mainFeedScrollKey(FeedSource.POPULAR, FeedSort.BEST)
        val community = communityFeedScrollKey("homelab", CommunitySort.HOT)

        assertEquals(FeedScrollAnchor.Start, state.feedScrollAnchor(home))

        state.captureFeedScrollAnchor(home, itemKey = "post-7", fallbackIndex = 7, pixelOffset = 40)
        state.updateFeedScrollAnchor(popular, itemKey = null, fallbackIndex = -3, pixelOffset = -1)
        state.captureFeedScrollAnchor(community, itemKey = "post-2", fallbackIndex = 2, pixelOffset = 5)

        assertEquals(FeedScrollAnchor("post-7", 7, 40), state.feedScrollAnchor(home))
        assertEquals(FeedScrollAnchor(null, 0, 0), state.feedScrollAnchor(popular))
        assertEquals(FeedScrollAnchor("post-2", 2, 5), state.feedScrollAnchor(community))
        assertEquals(
            mapOf(
                home to FeedScrollAnchor("post-7", 7, 40),
                popular to FeedScrollAnchor(null, 0, 0),
                community to FeedScrollAnchor("post-2", 2, 5),
            ),
            state.feedScrollAnchorSnapshot(),
        )

        state.updateFeedScrollAnchor(home, itemKey = "post-9", fallbackIndex = 9, pixelOffset = 0)
        // A captured anchor is locked: updates are ignored until it is released;
        // release keeps the value and only unlocks it.
        assertEquals(FeedScrollAnchor("post-7", 7, 40), state.feedScrollAnchor(home))
        state.releaseFeedScrollAnchor(home)
        assertEquals(FeedScrollAnchor("post-7", 7, 40), state.feedScrollAnchor(home))
        state.updateFeedScrollAnchor(home, itemKey = "post-9", fallbackIndex = 9, pixelOffset = 0)
        assertEquals(FeedScrollAnchor("post-9", 9, 0), state.feedScrollAnchor(home))
        assertEquals(FeedScrollAnchor("post-2", 2, 5), state.feedScrollAnchor(community))

        assertNull(state.feedReturnAnchor(community))
        state.captureFeedReturnAnchor(community, itemKey = "post-2", itemOffset = 12)
        assertEquals(FeedReturnAnchor("post-2", 12), state.feedReturnAnchor(community))
        assertNull(state.feedReturnAnchor(home))
        state.releaseFeedReturnAnchor(community)
        assertNull(state.feedReturnAnchor(community))
        assertEquals(FeedScrollAnchor("post-2", 2, 5), state.feedScrollAnchor(community))
    }

    /**
     * The encoded process snapshot for a fixed setup. The literal below was
     * captured from the current code; after the extraction the same setup
     * must encode to the same bytes.
     */
    @Test
    fun processSnapshotOfAFixedSetupEncodesToTheSameBytes() {
        val state = fixedSetup()

        val encoded = encodeSubshellProcessSnapshot(state.processStateSnapshot())

        assertEquals(FIXED_SETUP_ENCODED, encoded)
        val restored = SubshellState(
            repository = FakeSubshellRepository(),
            initialAccountSessions = localFixtureAccountSummaries(listOf("root")),
            initialProcessSnapshotToken = encoded,
        )
        assertEquals(state.feedScrollAnchorSnapshot(), restored.feedScrollAnchorSnapshot())
        assertEquals(encoded, encodeSubshellProcessSnapshot(restored.processStateSnapshot()))
    }

    private fun fixedSetup(): SubshellState {
        // The composer draft gets a random id and clock timestamps at
        // construction, so the setup restores a fixed draft from a token.
        val token = encodeSubshellProcessSnapshot(
            SubshellProcessSnapshot(
                routeStack = SubshellRouteStack.forDestination(
                    destination = MainDestination.HOME,
                    source = FeedSource.HOME,
                    sort = FeedSort.BEST,
                ),
                scrollAnchors = emptyMap(),
                userProfileTabs = emptyMap(),
                postComposerDraft = PostComposerDraft(
                    id = "fixed-draft",
                    createdAtEpochMs = 1_700_000_000_000L,
                    updatedAtEpochMs = 1_700_000_000_000L,
                ),
                feedVideoMuted = false,
                backgroundResumeMediaId = null,
                backgroundResumeWasManual = false,
            ),
        )
        val state = SubshellState(
            repository = CountingRepository(),
            initialAccountSessions = localFixtureAccountSummaries(listOf("root")),
            initialProcessSnapshotToken = token,
        )
        state.captureFeedScrollAnchor(
            mainFeedScrollKey(FeedSource.HOME, FeedSort.BEST),
            itemKey = "post-7",
            fallbackIndex = 7,
            pixelOffset = 40,
        )
        state.captureFeedScrollAnchor(
            mainFeedScrollKey(FeedSource.POPULAR, FeedSort.TOP, RedditListingTimePeriod.WEEK),
            itemKey = null,
            fallbackIndex = 3,
            pixelOffset = 0,
        )
        state.captureFeedScrollAnchor(
            communityFeedScrollKey("homelab", CommunitySort.NEW),
            itemKey = "post-2",
            fallbackIndex = 2,
            pixelOffset = 5,
        )
        state.captureFeedReturnAnchor(
            communityFeedScrollKey("homelab", CommunitySort.NEW),
            itemKey = "post-2",
            itemOffset = 12,
        )
        state.ensurePostPaging(queryKey(state, FeedSort.BEST))
        return state
    }

    /** Construction itself loads one post page outside the pagers; counting starts after it. */
    private fun localState(repository: CountingRepository): SubshellState =
        SubshellState(
            repository = repository,
            initialAccountSessions = localFixtureAccountSummaries(listOf("root")),
        ).also { repository.postLoads = 0 }

    private fun queryKey(state: SubshellState, sort: FeedSort) = mainPostFeedQueryKey(
        account = state.activeAccountSession,
        source = FeedSource.HOME,
        sort = sort,
    )

    private companion object {
        const val FIXED_SETUP_ENCODED =
            "VERSION|sdjE\n" +
            "ROUTE|sTUFJTg|sSE9NRQ|sSE9NRQ|sQkVTVA\n" +
            "ANCHOR|sQ09NTVVOSVRZ|saG9tZWxhYg|sTkVX|sQUxM|scG9zdC0y|sMg|sNQ\n" +
            "ANCHOR|sTUFJTg|sSE9NRQ|sQkVTVA|sQUxM|scG9zdC03|sNw|sNDA\n" +
            "ANCHOR|sTUFJTg|sUE9QVUxBUg|sVE9Q|sV0VFSw|n|sMw|sMA\n" +
            // LOWUI4_UNREAD_TAB_STATE_RETIRED_V1: no LIBRARY_TAB/YOU_TAB records.
            "COMPOSER|saWQ9Zml4ZWQtZHJhZnQ|sYWNjb3VudD0|sc3Vic2hlbGw|sVEVYVA|s|s|s|sbW9kZT1NQVJLRE9XTg|sZmxhaXJJZD0|sZmxhaXJUZXh0PQ|sZmxhaXJFZGl0YWJsZT1mYWxzZQ|sZmxhaXJUZXh0Q29sb3I9|sZmxhaXJCYWNrZ3JvdW5kQ29sb3I9|sbnNmdz1mYWxzZQ|sc3BvaWxlcj1mYWxzZQ|sc2VuZFJlcGxpZXM9dHJ1ZQ|sY3JlYXRlZD0xNzAwMDAwMDAwMDAw|sdXBkYXRlZD0xNzAwMDAwMDAwMDAw\n" +
            "MEDIA_STATE|sZmFsc2U|n|sZmFsc2U"
    }
}

/** FakeSubshellRepository that counts post page loads. */
private class CountingRepository(
    private val delegate: FakeSubshellRepository = FakeSubshellRepository(),
) : SubshellRepository by delegate {
    var postLoads = 0

    override fun posts(request: RepositoryPageRequest): RepositoryResult<List<Post>> {
        postLoads += 1
        return delegate.posts(request)
    }
}
