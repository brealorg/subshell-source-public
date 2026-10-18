package app.subshell.prototype.ui

import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.RepositoryResult
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.SubshellRepository
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.screens.commentTimeLabel
import app.subshell.prototype.ui.screens.commentThreadInsetDp
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedPostRoutePolicyTest {
    private val post = SampleData.posts.first()

    @Test
    fun nestedCommentInsetAdvancesOneConstantStepPerRecursiveLevel() {
        assertEquals(0, commentThreadInsetDp(0))
        assertEquals(8, commentThreadInsetDp(1))
        assertEquals(8, commentThreadInsetDp(6))
    }

    @Test
    fun commentTimesAdaptFromLocalClockToDateAndYearWithoutGrowingTheHeader() {
        val oslo = ZoneId.of("Europe/Oslo")
        val nowEpochSeconds = 1_785_528_000L
        val todayEpochSeconds = 1_785_522_000L
        val currentYearEpochSeconds = 1_782_842_400L
        val previousYearEpochSeconds = 1_751_306_400L

        assertEquals(
            "2h",
            commentTimeLabel(
                "2h",
                todayEpochSeconds,
                CommentTimeFormat.RELATIVE,
                oslo,
                nowEpochSeconds,
                Locale.ENGLISH,
            ),
        )
        assertEquals(
            "20:20",
            commentTimeLabel(
                "2h",
                todayEpochSeconds,
                CommentTimeFormat.LOCAL_24_HOUR,
                oslo,
                nowEpochSeconds,
                Locale.ENGLISH,
            ),
        )
        assertEquals(
            "8:20 PM",
            commentTimeLabel(
                "2h",
                todayEpochSeconds,
                CommentTimeFormat.LOCAL_12_HOUR,
                oslo,
                nowEpochSeconds,
                Locale.ENGLISH,
            ),
        )
        assertEquals(
            "30 Jun",
            commentTimeLabel(
                "1mo",
                currentYearEpochSeconds,
                CommentTimeFormat.LOCAL_24_HOUR,
                oslo,
                nowEpochSeconds,
                Locale.ENGLISH,
            ),
        )
        assertEquals(
            "30 Jun 2025",
            commentTimeLabel(
                "1y",
                previousYearEpochSeconds,
                CommentTimeFormat.LOCAL_12_HOUR,
                oslo,
                nowEpochSeconds,
                Locale.ENGLISH,
            ),
        )
        assertEquals(
            "2h",
            commentTimeLabel(
                "2h",
                null,
                CommentTimeFormat.LOCAL_24_HOUR,
                oslo,
                nowEpochSeconds,
                Locale.ENGLISH,
            ),
        )
    }

    @Test
    fun commentDatesAndTwelveHourTimesFollowTheDeviceLanguage() {
        // LOCALE01_DATES_FOLLOW_THE_DEVICE_LANGUAGE_V1
        val oslo = ZoneId.of("Europe/Oslo")
        val now = java.time.ZonedDateTime.of(2026, 7, 30, 22, 20, 0, 0, oslo).toEpochSecond()
        val evening = java.time.ZonedDateTime.of(2026, 7, 30, 20, 20, 0, 0, oslo).toEpochSecond()
        val june = java.time.ZonedDateTime.of(2026, 6, 30, 12, 0, 0, 0, oslo).toEpochSecond()
        val norwegian = Locale.forLanguageTag("nb-NO")
        val german = Locale.GERMANY

        assertEquals("30 juni", commentTimeLabel("1mo", june, CommentTimeFormat.LOCAL_24_HOUR, oslo, now, norwegian).trimEnd('.'))
        assertEquals("30 Juni", commentTimeLabel("1mo", june, CommentTimeFormat.LOCAL_24_HOUR, oslo, now, german).trimEnd('.'))
        assertEquals("20:20", commentTimeLabel("2h", evening, CommentTimeFormat.LOCAL_24_HOUR, oslo, now, norwegian))
        assertTrue(
            "12-hour time names the period in the device language",
            !commentTimeLabel("2h", evening, CommentTimeFormat.LOCAL_12_HOUR, oslo, now, norwegian).endsWith("PM"),
        )
    }

    @Test
    fun everyOriginRoundTripsThroughSaveableToken() {
        val origins = listOf(
            FeedPostOrigin.MainFeed(MainDestination.HOME, FeedSource.POPULAR, FeedSort.NEW),
            FeedPostOrigin.Community(MainDestination.SUBS, post.community, CommunitySort.TOP),
            FeedPostOrigin.Search(
                destination = MainDestination.SUBS,
                originCommunity = post.community,
                query = "video | encoded query",
                scope = SearchScope.POSTS,
                area = SearchArea.CURRENT_COMMUNITY,
            ),
            FeedPostOrigin.User(MainDestination.YOU, post.author),
            FeedPostOrigin.Inbox,
            FeedPostOrigin.Secondary(MainDestination.HOME, SecondaryDestination.HELP),
        )

        origins.forEach { origin ->
            assertEquals(origin, decodeFeedPostOrigin(encodeFeedPostOrigin(origin)))
        }
        assertNull(decodeFeedPostOrigin(null))
        assertNull(decodeFeedPostOrigin("not-a-route"))
    }

    @Test
    fun explicitOriginsRestoreEveryPostSurface() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)

        state.feedSource = FeedSource.POPULAR
        state.feedSort = FeedSort.NEW
        state.openFromMainFeed(post)
        assertNull(state.selectedSubreddit)
        state.closePost()
        assertEquals(MainDestination.HOME, state.destination)
        assertEquals(FeedSource.POPULAR, state.feedSource)
        assertEquals(FeedSort.NEW, state.feedSort)

        state.navigate(MainDestination.SUBS)
        state.openSubreddit(post.community)
        state.communitySort = CommunitySort.TOP
        state.openFromCommunity(post, post.community)
        assertNull(state.selectedSubreddit)
        state.closePost()
        assertEquals(post.community, state.selectedSubreddit)
        assertEquals(CommunitySort.TOP, state.communitySort)

        state.openSearch(refocus = false)
        state.updateSearchQuery("typed origin")
        state.updateSearchScope(SearchScope.POSTS)
        state.openFromSearch(post)
        assertFalse(state.searchVisible)
        state.closePost()
        assertTrue(state.searchVisible)
        assertEquals("typed origin", state.searchQuery)

        state.closeSearch()
        state.openUser(post.author)
        state.openFromUser(post, post.author)
        assertNull(state.selectedUser)
        state.closePost()
        assertEquals(post.author, state.selectedUser)

        state.closeUser()
        state.navigate(MainDestination.YOU)
        state.openFromYou(post)
        state.closePost()
        assertEquals(MainDestination.YOU, state.destination)

        val inboxItem = SampleData.inbox.first { it.targetPostId != null }
        state.navigate(MainDestination.INBOX)
        state.openInboxItem(inboxItem)
        assertNotNull(state.selectedPost)
        state.closePost()
        assertEquals(MainDestination.INBOX, state.destination)
    }

    @Test
    fun nestedUserAndCommunityRoutesReturnToSamePostRoute() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)
        state.feedSource = FeedSource.SAVED
        state.feedSort = FeedSort.TOP
        state.openFromMainFeed(post)
        val visitId = state.postVisitId

        state.openUser(post.author)
        assertNull(state.selectedPost)
        state.closeUser()
        assertEquals(post.id, state.selectedPost?.id)
        assertEquals(visitId, state.postVisitId)

        state.openSubreddit(post.community)
        assertNull(state.selectedPost)
        state.closeSubreddit()
        assertEquals(post.id, state.selectedPost?.id)
        assertEquals(visitId, state.postVisitId)

        state.closePost()
        assertEquals(FeedSource.SAVED, state.feedSource)
        assertEquals(FeedSort.TOP, state.feedSort)
    }

    @Test
    fun processRestoreSnapshotPreservesSearchOriginAndFocusedComment() {
        val commentId = SampleData.comments.first().id
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)
        state.openSubreddit(post.community)
        state.openSearch(refocus = false)
        state.updateSearchQuery("process restore")
        state.open(post, commentId)
        val snapshot = navigationRouteRestoreSnapshot(state)

        val restored = SubshellState(
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
            initialDestination = MainDestination.HOME,
            initialSelectedPostId = snapshot.selectedPostId,
            initialPostOriginToken = snapshot.postOriginToken,
            initialFocusedCommentId = snapshot.focusedCommentId,
            initialPostVisitId = snapshot.postVisitId,
        )

        assertEquals(post.id, restored.selectedPost?.id)
        assertEquals(commentId, restored.focusedCommentId)
        assertNotNull(restored.activeFeedPostRoute)
        restored.closePost()
        assertTrue(restored.searchVisible)
        assertEquals("process restore", restored.searchQuery)
        assertEquals(post.community, restored.searchOriginCommunity)
    }

    @Test
    fun repositoryOnlyLivePostsResolveForPostAndMediaRoutes() {
        val backing = FakeSubshellRepository()
        val livePost = post.copy(id = "t3_live_room_post")
        val liveMediaPost = SampleData.posts
            .first { candidate -> candidate.kind == PostKind.IMAGE }
            .copy(id = "t3_live_room_media")
        val repositoryOnlyPosts = listOf(livePost, liveMediaPost)
        val repository = object : SubshellRepository by backing {
            override fun post(postId: String): RepositoryResult<Post> =
                repositoryOnlyPosts
                    .firstOrNull { candidate -> candidate.id == postId }
                    ?.let { candidate -> RepositoryResult.Content(candidate) }
                    ?: backing.post(postId)
        }
        val state = SubshellState(repository = repository)

        assertTrue(state.posts.none { candidate -> candidate.id == livePost.id })
        state.openFromMainFeed(livePost)
        assertEquals(livePost.id, state.selectedPost?.id)

        state.closePost()
        assertTrue(state.posts.none { candidate -> candidate.id == liveMediaPost.id })
        state.openMedia(liveMediaPost)
        assertEquals(liveMediaPost.id, state.selectedMediaPost?.id)
    }

    @Test
    fun liveRouteKeepsTheExactFeedPostInsteadOfAShorterRepositoryVariant() {
        val backing = FakeSubshellRepository()
        val fullBody = "First paragraph\n\nSecond paragraph\n\nThird paragraph"
        val feedPost = post.copy(
            id = "t3_live_full_body",
            body = fullBody,
        )
        val repository = object : SubshellRepository by backing {
            override fun post(postId: String): RepositoryResult<Post> =
                if (postId == feedPost.id) {
                    RepositoryResult.Content(feedPost.copy(body = "First paragraph…"))
                } else {
                    backing.post(postId)
                }
        }
        val state = SubshellState(repository = repository)

        state.openFromCommunity(feedPost, feedPost.community)
        assertEquals(fullBody, state.selectedPost?.body)

        state.openUser(feedPost.author)
        state.closeUser()
        assertEquals(fullBody, state.selectedPost?.body)
    }
}
