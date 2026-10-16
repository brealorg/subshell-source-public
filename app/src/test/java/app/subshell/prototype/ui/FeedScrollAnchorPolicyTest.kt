package app.subshell.prototype.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import app.subshell.prototype.ui.screens.clampZoomTranslation
import app.subshell.prototype.data.RedditListingTimePeriod
import app.subshell.prototype.data.SampleData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedScrollAnchorPolicyTest {
    @Test
    fun timeFilteredListingsKeepIndependentScrollAnchors() {
        assertTrue(
            mainFeedScrollKey(
                FeedSource.HOME,
                FeedSort.TOP,
                RedditListingTimePeriod.ALL,
            ) != mainFeedScrollKey(
                FeedSource.HOME,
                FeedSort.TOP,
                RedditListingTimePeriod.WEEK,
            ),
        )
        assertEquals(
            mainFeedScrollKey(FeedSource.HOME, FeedSort.HOT),
            mainFeedScrollKey(
                FeedSource.HOME,
                FeedSort.HOT,
                RedditListingTimePeriod.WEEK,
            ),
        )
    }

    @Test
    fun stableItemIdentityWinsAndFallbackIndexHandlesRemovedItems() {
        val anchor = FeedScrollAnchor(
            itemKey = "post-b",
            fallbackIndex = 4,
            pixelOffset = 37,
        )
        assertEquals(1, anchor.resolveIndex(listOf("post-a", "post-b", "post-c")))
        assertEquals(2, anchor.resolveIndex(listOf("post-a", "post-c", "post-d")))
        assertEquals(37, anchor.resolveOffset(listOf("post-a")))
        assertEquals(0, anchor.resolveOffset(emptyList()))
    }

    @Test
    fun everyFeedSurfaceAndSortOwnsAnIndependentAnchor() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)
        val homeBest = mainFeedScrollKey(FeedSource.HOME, FeedSort.BEST)
        val homeNew = mainFeedScrollKey(FeedSource.HOME, FeedSort.NEW)
        val popularBest = mainFeedScrollKey(FeedSource.POPULAR, FeedSort.BEST)
        val community = communityFeedScrollKey(" Android ", CommunitySort.TOP)
        val search = searchFeedScrollKey(
            destination = MainDestination.SUBS,
            originCommunity = " Android ",
            query = "  Pixel   Camera ",
            scope = SearchScope.POSTS,
            area = SearchArea.CURRENT_COMMUNITY,
        )
        val user = userFeedScrollKey(" ShellUser ", tab = 2, activityTab = 1)
        val you = youFeedScrollKey(tab = 2)

        listOf(homeBest, homeNew, popularBest, community, search, user, you)
            .forEachIndexed { index, key ->
                state.updateFeedScrollAnchor(
                    key = key,
                    itemKey = "item-$index",
                    fallbackIndex = index,
                    pixelOffset = index * 11,
                )
            }

        assertEquals(7, state.feedScrollAnchorSnapshot().size)
        assertNotEquals(state.feedScrollAnchor(homeBest), state.feedScrollAnchor(homeNew))
        assertEquals("android", (community as FeedScrollKey.Community).communityName)
        assertEquals("pixel camera", (search as FeedScrollKey.Search).query)
        assertEquals("shelluser", (user as FeedScrollKey.User).username)
    }

    @Test
    fun exactReturnAnchorPersistsUntilTheMatchingRowCanRestoreIt() {
        val store = FeedScrollAnchorStore()
        val key = userFeedScrollKey("brealorg", tab = 1, activityTab = 0)
        val anchor = FeedReturnAnchor(
            itemKey = "profile-comment-t1_target",
            itemOffset = 417,
        )

        store.captureReturn(key, anchor)

        assertEquals(anchor, store.returnAnchor(key))
        assertEquals(anchor, store.returnAnchor(key))
        store.releaseReturn(key)
        assertEquals(null, store.returnAnchor(key))
    }

    @Test
    fun profileAndLibrarySelectionsSurvivePostRoutePushAndPop() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)
        val post = SampleData.posts.first()
        state.navigate(MainDestination.YOU)
        state.selectUserProfileTab(state.activeAccountUsername, 2)
        state.updateFeedScrollAnchor(
            key = userFeedScrollKey(state.activeAccountUsername, tab = 2, activityTab = 1),
            itemKey = "profile-activity-1-${post.id}",
            fallbackIndex = 3,
            pixelOffset = 24,
        )

        state.openFromUser(post, state.activeAccountUsername)
        state.closePost()
        assertEquals(2, state.userProfileTab(state.activeAccountUsername))
        assertEquals(
            24,
            state.feedScrollAnchor(
                userFeedScrollKey(state.activeAccountUsername, tab = 2, activityTab = 1),
            ).pixelOffset,
        )
    }
    @Test
    fun homeScrollToTopRequestIsConsumedOnceAcrossPostBackReentry() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)
        val post = SampleData.posts.first()

        assertFalse(state.consumeHomeScrollToTopRequest())

        state.navigate(MainDestination.HOME)
        assertEquals(1, state.homeScrollToTopRequest)
        assertTrue(state.consumeHomeScrollToTopRequest())
        assertFalse(state.consumeHomeScrollToTopRequest())

        state.openFromMainFeed(post)
        state.closePost()
        assertFalse(state.consumeHomeScrollToTopRequest())

        state.navigate(MainDestination.HOME)
        assertEquals(2, state.homeScrollToTopRequest)
        assertTrue(state.consumeHomeScrollToTopRequest())
        assertFalse(state.consumeHomeScrollToTopRequest())
    }

    @Test
    fun commentsEntryTargetIsConsumedExactlyOncePerPostVisit() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)
        val post = SampleData.posts.first()

        state.openFromMainFeed(post, FeedPostEntryTarget.COMMENTS)
        val commentsVisit = state.postVisitId
        assertTrue(
            state.consumePostEntryTarget(
                commentsVisit,
                FeedPostEntryTarget.COMMENTS,
            ),
        )
        assertFalse(
            state.consumePostEntryTarget(
                commentsVisit,
                FeedPostEntryTarget.COMMENTS,
            ),
        )
        state.closePost()

        state.openFromMainFeed(post)
        assertFalse(
            state.consumePostEntryTarget(
                state.postVisitId,
                FeedPostEntryTarget.COMMENTS,
            ),
        )
    }

    @Test
    fun fullscreenZoomTranslationIsBoundedByTheViewport() {
        assertEquals(
            Offset(50f, -100f),
            clampZoomTranslation(
                translation = Offset(500f, -800f),
                scale = 2f,
                viewportSize = Size(100f, 200f),
            ),
        )
        assertEquals(
            Offset.Zero,
            clampZoomTranslation(
                translation = Offset(40f, 60f),
                scale = 1f,
                viewportSize = Size(100f, 200f),
            ),
        )
    }

}
