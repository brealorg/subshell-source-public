package app.subshell.prototype.ui

import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.RedditModeratorQueueItem
import app.subshell.prototype.data.account.RedditModeratorQueueKind
import app.subshell.prototype.model.ActivityKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterisation of the moderator queue route openers
 * (openModeratorQueueItem, replaceCurrentModeratorQueueDetail) inside
 * SubshellState as they behave today, written before they move to
 * InboxActivityState with the injected route primitives. Current
 * behaviour, not a specification.
 */
class ModeratorQueueOpenerCharacterisationTest {
    private val post = SampleData.posts.first().copy(id = "abc123")
    private val otherPost = SampleData.posts.first().copy(id = "zzz999")
    private val postItem = RedditModeratorQueueItem(
        fullname = "t3_abc123",
        kind = RedditModeratorQueueKind.POST,
        communityName = "homelab",
        author = "someone",
        summary = "Reported post",
    )
    private val commentItem = RedditModeratorQueueItem(
        fullname = "t1_c0ffee",
        kind = RedditModeratorQueueKind.COMMENT,
        communityName = "homelab",
        author = "someone",
        summary = "Reported comment",
        permalink = "/r/homelab/comments/abc123/title/c0ffee/",
    )
    private val commentWithoutPermalink = commentItem.copy(permalink = null)

    @Test
    fun openingRequiresATargetThatMatchesTheResolvedPost() {
        val state = SubshellState()
        state.navigate(MainDestination.INBOX)
        val depth = state.navigationRouteDepth

        state.openModeratorQueueItem(postItem, otherPost)
        assertEquals(depth, state.navigationRouteDepth)
        state.openModeratorQueueItem(commentWithoutPermalink, post)
        assertEquals(depth, state.navigationRouteDepth)

        // An upper-case "T3_" prefix is not stripped, so it never matches.
        state.openModeratorQueueItem(postItem, post.copy(id = "T3_ABC123"))
        assertEquals(depth, state.navigationRouteDepth)

        state.openModeratorQueueItem(postItem, post.copy(id = "t3_ABC123"))
        assertEquals(depth + 1, state.navigationRouteDepth)
        val route = state.currentNavigationRoute as SubshellRoute.Post
        assertEquals(FeedPostOrigin.Inbox, route.postRoute.origin)
        assertNull(state.focusedCommentId)
        assertEquals("t3_ABC123", state.selectedPost?.id)

        state.navigate(MainDestination.INBOX)
        state.openModeratorQueueItem(commentItem, post)
        assertEquals("t1_c0ffee", state.focusedCommentId)
        assertEquals("abc123", state.selectedPost?.id)
    }

    @Test
    fun openingClearsAPendingInboxReadReceipt() {
        val state = SubshellState()
        val reply = SampleData.inbox.first { it.targetPostId == "m3-terminal" && it.targetCommentId == "c2" }
        state.navigate(MainDestination.INBOX)
        state.openInboxItem(reply, verifiedReadAccountStableId = "acct-a")
        state.navigate(MainDestination.INBOX)

        state.openModeratorQueueItem(postItem, post)
        assertNull(state.consumeInboxReadReceipt("m3-terminal", "c2"))
    }

    @Test
    fun replacementNeedsAnInboxDetailUnderneathAndAMatchingTarget() {
        val state = SubshellState()
        val message = SampleData.inbox.first { it.kind == ActivityKind.MESSAGE }
        assertFalse(state.replaceCurrentModeratorQueueDetail(postItem, post))

        // A post opened from Home is not an inbox detail.
        state.open(SampleData.posts.first())
        assertFalse(state.replaceCurrentModeratorQueueDetail(postItem, post))

        state.navigate(MainDestination.INBOX)
        state.openInboxItem(message)
        val depth = state.navigationRouteDepth
        assertFalse(state.replaceCurrentModeratorQueueDetail(postItem, otherPost))
        assertFalse(state.replaceCurrentModeratorQueueDetail(commentWithoutPermalink, post))
        assertEquals(message.id, state.selectedConversation?.id)

        assertTrue(state.replaceCurrentModeratorQueueDetail(commentItem, post))
        assertEquals(depth, state.navigationRouteDepth)
        assertEquals("abc123", state.selectedPost?.id)
        assertEquals("t1_c0ffee", state.focusedCommentId)
        assertNull(state.selectedConversation)

        // An inbox-origin post underneath is eligible too.
        assertTrue(state.replaceCurrentModeratorQueueDetail(postItem, post))
        assertNull(state.focusedCommentId)
        assertEquals(depth, state.navigationRouteDepth)
        state.navigateBack()
        assertEquals(depth - 1, state.navigationRouteDepth)
        assertNull(state.selectedPost)
    }
}
