package app.subshell.prototype.ui

import app.subshell.prototype.data.SampleData
import app.subshell.prototype.model.PostKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubshellRouteStackPolicyTest {
    private val post = SampleData.posts.first { candidate -> candidate.kind == PostKind.VIDEO }

    @Test
    fun canonicalStackAlwaysKeepsHomeAsRootAndPopsOneRouteAtATime() {
        var stack = SubshellRouteStack.forDestination(
            destination = MainDestination.SUBS,
            source = FeedSource.POPULAR,
            sort = FeedSort.NEW,
        )
        assertEquals(2, stack.depth)
        assertEquals(MainDestination.HOME, (stack.entries.first() as SubshellRoute.Main).destination)
        assertEquals(MainDestination.SUBS, (stack.current as SubshellRoute.Main).destination)

        stack = stack.push(
            SubshellRoute.Community(
                destination = MainDestination.SUBS,
                communityName = post.community,
                sort = CommunitySort.TOP,
            ),
        )
        stack = stack.push(
            SubshellRoute.Search(
                destination = MainDestination.SUBS,
                originCommunity = post.community,
                query = "typed stack",
                scope = SearchScope.POSTS,
                area = SearchArea.CURRENT_COMMUNITY,
            ),
        )
        assertEquals(4, stack.depth)
        assertTrue(stack.current is SubshellRoute.Search)

        stack = stack.pop()
        assertEquals(3, stack.depth)
        assertTrue(stack.current is SubshellRoute.Community)
        stack = stack.pop()
        assertEquals(2, stack.depth)
        assertTrue(stack.current is SubshellRoute.Main)
        stack = stack.pop()
        assertEquals(1, stack.depth)
        assertFalse(stack.canPop)
        assertEquals(MainDestination.HOME, (stack.current as SubshellRoute.Main).destination)
    }

    @Test
    fun stateBackUsesCanonicalStackForNestedPostUserMediaAndSearchReturn() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)
        state.navigate(MainDestination.SUBS)
        state.openSubreddit(post.community)
        state.communitySort = CommunitySort.TOP
        state.openSearch(refocus = false)
        state.updateSearchQuery("preserve exact query")
        state.openFromSearch(post)
        val postVisitId = state.postVisitId

        assertEquals(5, state.navigationRouteDepth)
        assertTrue(state.currentNavigationRoute is SubshellRoute.Post)

        state.openUser(post.author)
        assertEquals(6, state.navigationRouteDepth)
        assertTrue(state.currentNavigationRoute is SubshellRoute.User)
        assertTrue(state.navigateBack())
        assertEquals(5, state.navigationRouteDepth)
        assertEquals(post.id, state.selectedPost?.id)
        assertEquals(postVisitId, state.postVisitId)

        state.openMedia(post)
        assertEquals(6, state.navigationRouteDepth)
        assertTrue(state.currentNavigationRoute is SubshellRoute.Media)
        assertTrue(state.navigateBack())
        assertEquals(post.id, state.selectedPost?.id)

        assertTrue(state.navigateBack())
        assertTrue(state.searchVisible)
        assertEquals("preserve exact query", state.searchQuery)
        assertEquals(post.community, state.selectedSubreddit)

        assertTrue(state.navigateBack())
        assertEquals(post.community, state.selectedSubreddit)
        assertTrue(state.searchVisible)
        assertEquals("", state.searchQuery)
        assertEquals(CommunitySort.TOP, state.communitySort)

        assertTrue(state.navigateBack())
        assertEquals(post.community, state.selectedSubreddit)
        assertFalse(state.searchVisible)
        assertEquals(CommunitySort.TOP, state.communitySort)

        assertTrue(state.navigateBack())
        assertNull(state.selectedSubreddit)
        assertEquals(MainDestination.SUBS, state.destination)
        assertTrue(state.navigateBack())
        assertEquals(MainDestination.HOME, state.destination)
        assertFalse(state.canNavigateBack)
    }

    @Test
    fun mediaRouteRetainsAPostThatOnlyExistsInTheLivePagingSnapshot() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)
        val liveGallery = SampleData.posts
            .first { candidate -> candidate.kind == PostKind.GALLERY }
            .copy(id = "reddit-live-gallery")

        assertNull(state.posts.firstOrNull { candidate -> candidate.id == liveGallery.id })

        state.openMedia(liveGallery, index = 1)

        assertEquals(liveGallery, state.selectedMediaPost)
        assertEquals(1, state.selectedMediaIndex)
        assertTrue(state.currentNavigationRoute is SubshellRoute.Media)

        assertTrue(state.navigateBack())
        assertNull(state.selectedMediaPost)
        assertEquals(MainDestination.HOME, state.destination)
    }

    @Test
    fun postRouteRetainsAPostThatOnlyExistsInTheLivePagingSnapshot() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)
        val livePost = SampleData.posts.first().copy(id = "reddit-live-post")

        assertNull(state.posts.firstOrNull { candidate -> candidate.id == livePost.id })

        state.openFromMainFeed(livePost)

        assertEquals(livePost, state.selectedPost)
        assertTrue(state.currentNavigationRoute is SubshellRoute.Post)

        assertTrue(state.navigateBack())
        assertNull(state.selectedPost)
        assertEquals(MainDestination.HOME, state.destination)
    }

    @Test
    fun secondaryNavigationResetsContextAndReturnsToItsMainDestination() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)
        state.navigate(MainDestination.YOU)
        state.openUser(post.author)
        state.openSubreddit(post.community)

        state.openSecondary(SecondaryDestination.SETTINGS)

        assertEquals(3, state.navigationRouteDepth)
        assertTrue(state.currentNavigationRoute is SubshellRoute.Secondary)
        assertEquals(SecondaryDestination.SETTINGS, state.secondaryDestination)
        assertNull(state.selectedUser)
        assertNull(state.selectedSubreddit)

        assertTrue(state.navigateBack())
        assertEquals(MainDestination.YOU, state.destination)
        assertTrue(state.currentNavigationRoute is SubshellRoute.Main)
        assertEquals(2, state.navigationRouteDepth)
    }

    @Test
    fun postComposerIsAStackOverlayAndPublishingUsesTheUnderlyingCommunityOrigin() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)
        state.openSubreddit("android")
        state.openPostComposer()
        assertTrue(state.currentNavigationRoute is SubshellRoute.PostComposer)
        assertEquals(3, state.navigationRouteDepth)
        state.openPostComposer()
        assertEquals(3, state.navigationRouteDepth)

        state.updatePostComposerDraft(
            PostComposerDraft(
                community = "android",
                title = "Canonical route stack",
                body = "Publishing must pop the composer before pushing the post route.",
            ),
        )
        val published = requireNotNull(state.publishPostComposerDraft())

        assertEquals(published.id, state.selectedPost?.id)
        assertTrue(state.currentNavigationRoute is SubshellRoute.Post)
        assertEquals(3, state.navigationRouteDepth)
        state.closePost()
        assertEquals("android", state.selectedSubreddit)
        assertTrue(state.currentNavigationRoute is SubshellRoute.Community)
    }

    @Test
    fun newComposerUsesRouteContextInsteadOfAStaleOrHiddenDefaultCommunity() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)

        state.openPostComposer()
        assertEquals("", state.postComposerDraft.community)
        state.closePostComposer()

        state.openSubreddit("android")
        state.openPostComposer()
        assertEquals("android", state.postComposerDraft.community)
    }
}
