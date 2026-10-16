package app.subshell.prototype.ui

import app.subshell.prototype.data.RedditFeedSort
import app.subshell.prototype.data.RedditListingTimePeriod
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.components.shouldShowUnreadMarker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFeedSelectionTest {
    @Test
    fun liveListingMatrixUsesOneDistinctQueryKeyPerRedditListing() {
        val account = AccountSessionSummary(
            stableId = "reddit:redreader_compatibility:t2_runtime",
            username = "runtime",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )

        assertEquals(
            listOf(
                RedditFeedSort.BEST,
                RedditFeedSort.HOT,
                RedditFeedSort.NEW,
                RedditFeedSort.RISING,
                RedditFeedSort.TOP,
                RedditFeedSort.CONTROVERSIAL,
            ),
            FeedSort.entries.map(FeedSort::toRedditFeedSort),
        )
        assertEquals(
            FeedSort.entries.size,
            FeedSort.entries.map { sort ->
                mainPostFeedQueryKey(account, FeedSource.HOME, sort)
            }.distinct().size,
        )
        assertEquals(
            listOf(
                RedditFeedSort.HOT,
                RedditFeedSort.NEW,
                RedditFeedSort.RISING,
                RedditFeedSort.TOP,
                RedditFeedSort.CONTROVERSIAL,
            ),
            CommunitySort.entries.map(CommunitySort::toRedditFeedSort),
        )
        assertEquals(
            CommunitySort.entries.size,
            CommunitySort.entries.map { sort ->
                communityPostFeedQueryKey(account, "subshell", sort)
            }.distinct().size,
        )

        assertEquals(
            "account|authenticated|${account.stableId}|main|popular|hot",
            mainPostFeedQueryKey(account, FeedSource.POPULAR, FeedSort.HOT).value,
        )
        assertEquals(
            "account|authenticated|${account.stableId}|main|all|top|year",
            mainPostFeedQueryKey(
                account,
                FeedSource.ALL,
                FeedSort.TOP,
                RedditListingTimePeriod.YEAR,
            ).value,
        )
    }

    @Test
    fun popularAndAllExcludeBestAndNormalizeAnExistingBestSelectionToHot() {
        assertEquals(FeedSort.entries, availableFeedSorts(FeedSource.HOME))
        assertEquals(FeedSort.entries, availableFeedSorts(FeedSource.SAVED))
        assertEquals(
            FeedSort.entries.filterNot { sort -> sort == FeedSort.BEST },
            availableFeedSorts(FeedSource.POPULAR),
        )
        assertEquals(
            FeedSort.entries.filterNot { sort -> sort == FeedSort.BEST },
            availableFeedSorts(FeedSource.ALL),
        )
        assertEquals(FeedSort.HOT, normalizedFeedSort(FeedSource.POPULAR, FeedSort.BEST))
        assertEquals(FeedSort.HOT, normalizedFeedSort(FeedSource.ALL, FeedSort.BEST))
        assertEquals(FeedSort.NEW, normalizedFeedSort(FeedSource.POPULAR, FeedSort.NEW))
    }

    @Test
    fun globalTimePeriodSeparatesOnlyTimeFilteredLiveListings() {
        val account = AccountSessionSummary(
            stableId = "reddit:redreader_compatibility:t2_time",
            username = "runtime",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )

        val topAll = mainPostFeedQueryKey(
            account,
            FeedSource.HOME,
            FeedSort.TOP,
            RedditListingTimePeriod.ALL,
        )
        val topWeek = mainPostFeedQueryKey(
            account,
            FeedSource.HOME,
            FeedSort.TOP,
            RedditListingTimePeriod.WEEK,
        )
        assertTrue(topAll != topWeek)
        assertTrue(topWeek.value.endsWith("|top|week"))

        val hotAll = communityPostFeedQueryKey(
            account,
            "subshell",
            CommunitySort.HOT,
            RedditListingTimePeriod.ALL,
        )
        val hotWeek = communityPostFeedQueryKey(
            account,
            "subshell",
            CommunitySort.HOT,
            RedditListingTimePeriod.WEEK,
        )
        assertEquals(hotAll, hotWeek)
    }

    @Test
    fun unreadMarkerIsOptInAndDisappearsAfterOpening() {
        assertEquals(false, shouldShowUnreadMarker(showUnreadMarker = false, read = false))
        assertEquals(true, shouldShowUnreadMarker(showUnreadMarker = true, read = false))
        assertEquals(false, shouldShowUnreadMarker(showUnreadMarker = true, read = true))
    }

    @Test
    fun homeIncludesJoinedVideoLabAndExcludesUnjoinedCommunities() {
        val result = selectHomeFeedPosts(
            posts = listOf(
                post("video-a", "subshell_video_lab", score = 100),
                post("subshell-a", "subshell", score = 50),
                post("selfhosted-a", "selfhosted", score = 200),
            ),
            source = FeedSource.HOME,
            sort = FeedSort.TOP,
            joinedCommunityIds = listOf("subshell", "subshell_video_lab"),
            savedPostIds = emptyList(),
            hiddenPostIds = emptyList(),
        )

        assertEquals(listOf("video-a", "subshell-a"), result.map(Post::id))
    }

    @Test
    fun leavingCommunityRemovesItsPostsFromHome() {
        val posts = listOf(
            post("video-a", "subshell_video_lab"),
            post("subshell-a", "subshell"),
        )

        val joined = selectHomeFeedPosts(
            posts = posts,
            source = FeedSource.HOME,
            sort = FeedSort.NEW,
            joinedCommunityIds = listOf("subshell", "subshell_video_lab"),
            savedPostIds = emptyList(),
            hiddenPostIds = emptyList(),
        )
        val left = selectHomeFeedPosts(
            posts = posts,
            source = FeedSource.HOME,
            sort = FeedSort.NEW,
            joinedCommunityIds = listOf("subshell"),
            savedPostIds = emptyList(),
            hiddenPostIds = emptyList(),
        )

        assertEquals(setOf("video-a", "subshell-a"), joined.mapTo(mutableSetOf(), Post::id))
        assertEquals(listOf("subshell-a"), left.map(Post::id))
    }

    @Test
    fun allIncludesPostsOutsideSubscriptions() {
        val result = selectHomeFeedPosts(
            posts = listOf(
                post("joined", "subshell"),
                post("unjoined", "selfhosted"),
            ),
            source = FeedSource.ALL,
            sort = FeedSort.NEW,
            joinedCommunityIds = listOf("subshell"),
            savedPostIds = emptyList(),
            hiddenPostIds = emptyList(),
        )

        assertEquals(setOf("joined", "unjoined"), result.mapTo(mutableSetOf(), Post::id))
    }

    @Test
    fun savedIsIndependentOfSubscriptionAndHiddenPostsStayHidden() {
        val result = selectHomeFeedPosts(
            posts = listOf(
                post("saved-unjoined", "selfhosted"),
                post("saved-hidden", "subshell"),
                post("not-saved", "subshell"),
            ),
            source = FeedSource.SAVED,
            sort = FeedSort.BEST,
            joinedCommunityIds = listOf("subshell"),
            savedPostIds = listOf("saved-unjoined", "saved-hidden"),
            hiddenPostIds = listOf("saved-hidden"),
        )

        assertEquals(listOf("saved-unjoined"), result.map(Post::id))
    }

    @Test
    fun duplicatePostIdsAreCollapsedBeforeRendering() {
        val result = selectHomeFeedPosts(
            posts = listOf(
                post("same", "subshell", score = 10),
                post("same", "subshell_video_lab", score = 999),
            ),
            source = FeedSource.ALL,
            sort = FeedSort.TOP,
            joinedCommunityIds = emptyList(),
            savedPostIds = emptyList(),
            hiddenPostIds = emptyList(),
        )

        assertEquals(1, result.size)
        assertEquals("subshell", result.single().community)
    }

    @Test
    fun liveHomeSortsPreserveRedditOrderAndOnlyApplyLocalHiding() {
        val result = selectLiveHomePosts(
            posts = listOf(
                post("reddit-first", "unjoined", score = 1),
                post("reddit-hidden", "other", score = 999),
                post("reddit-last", "third", score = 500),
                post("reddit-first", "duplicate", score = 10_000),
            ),
            hiddenPostIds = listOf("reddit-hidden"),
        )

        assertEquals(listOf("reddit-first", "reddit-last"), result.map(Post::id))
        assertEquals("unjoined", result.first().community)
    }

    @Test
    fun hideReadFiltersLocalAndLiveFeedsWithoutChangingTheirOrder() {
        val posts = listOf(
            post("first", "subshell", score = 30),
            post("read", "subshell", score = 20),
            post("last", "subshell", score = 10),
        )

        val local = selectHomeFeedPosts(
            posts = posts,
            source = FeedSource.ALL,
            sort = FeedSort.TOP,
            joinedCommunityIds = emptyList(),
            savedPostIds = emptyList(),
            hiddenPostIds = emptyList(),
            readPostIds = listOf("read"),
            hideReadPosts = true,
        )
        val live = selectLiveHomePosts(
            posts = posts,
            hiddenPostIds = emptyList(),
            readPostIds = listOf("read"),
            hideReadPosts = true,
        )

        assertEquals(listOf("first", "last"), local.map(Post::id))
        assertEquals(listOf("first", "last"), live.map(Post::id))
    }

    private fun post(
        id: String,
        community: String,
        score: Int = 1,
        comments: Int = 1,
    ): Post = Post(
        id = id,
        community = community,
        author = "fixture",
        age = "1h",
        title = id,
        score = score,
        comments = comments,
        kind = PostKind.TEXT,
    )
}
