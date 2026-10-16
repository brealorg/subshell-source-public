package app.subshell.prototype.ui

import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.model.ActivityKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterisation of the inbox route openers (openInboxItem,
 * replaceCurrentInboxDetail, replaceCurrentNewMessageWithConversation)
 * inside SubshellState as they behave today, written before they move to
 * InboxActivityState with injected route primitives. Current behaviour,
 * not a specification.
 */
class InboxOpenerCharacterisationTest {
    private val reply = SampleData.inbox.first { it.targetPostId == "m3-terminal" && it.targetCommentId == "c2" }
    private val message = SampleData.inbox.first { it.kind == ActivityKind.MESSAGE }

    @Test
    fun aResolvablePostTargetOpensThePostFromTheInboxWithTheCommentFocused() {
        val state = SubshellState()
        state.navigate(MainDestination.INBOX)
        val depth = state.navigationRouteDepth

        state.openInboxItem(reply)
        assertEquals(depth + 1, state.navigationRouteDepth)
        val route = state.currentNavigationRoute as SubshellRoute.Post
        assertEquals("m3-terminal", state.selectedPost?.id)
        assertEquals("t1_c2", state.focusedCommentId)
        assertEquals(FeedPostOrigin.Inbox, route.postRoute.origin)
        assertTrue(reply.id in state.readInboxIds)
        assertNull(state.consumeInboxReadReceipt("m3-terminal", "c2"))
    }

    @Test
    fun aMessageOpensAConversationAndAnUnresolvableTargetNeedsTheFallback() {
        val state = SubshellState()
        state.navigate(MainDestination.INBOX)
        val depth = state.navigationRouteDepth

        state.openInboxItem(message)
        assertEquals(depth + 1, state.navigationRouteDepth)
        assertTrue(state.currentNavigationRoute is SubshellRoute.Conversation)
        assertEquals(message.id, state.selectedConversation?.id)
        assertTrue(message.id in state.readInboxIds)

        state.navigate(MainDestination.INBOX)
        val orphan = reply.copy(id = "orphan", targetPostId = "no-such-post", targetCommentId = null)
        state.openInboxItem(orphan)
        assertEquals(depth, state.navigationRouteDepth)
        assertTrue("orphan" in state.readInboxIds)

        state.openInboxItem(orphan, allowFallbackDetail = true)
        assertEquals(depth + 1, state.navigationRouteDepth)
        assertEquals("orphan", state.selectedConversation?.id)

        // A resolved post passed in wins over the lookup.
        state.navigate(MainDestination.INBOX)
        state.openInboxItem(orphan, resolvedPost = SampleData.posts.first())
        assertEquals(SampleData.posts.first().id, state.selectedPost?.id)
        assertNull(state.focusedCommentId)
    }

    @Test
    fun authenticatedSessionsLeaveReadStateToTheServerAndHandOverAReceipt() {
        val state = authenticatedState()
        state.navigate(MainDestination.INBOX)

        state.openInboxItem(reply, verifiedReadAccountStableId = "reddit:test")
        assertFalse(reply.id in state.readInboxIds)
        val receipt = state.consumeInboxReadReceipt("m3-terminal", "c2")
        assertNotNull(receipt)
        assertEquals("reddit:test", receipt?.accountStableId)
        assertEquals(reply.id, receipt?.inboxItemId)

        // Replacing the detail as the signed-in account issues a receipt for it.
        val second = reply.copy(id = "second", targetCommentId = "c3")
        assertTrue(state.replaceCurrentInboxDetail(second))
        val replaced = state.consumeInboxReadReceipt("m3-terminal", "c3")
        assertEquals("reddit:test", replaced?.accountStableId)
        assertEquals("second", replaced?.inboxItemId)
        assertFalse("second" in state.readInboxIds)

        // Without a comment target there is nothing to receipt.
        val postOnly = reply.copy(id = "post-only", targetCommentId = null)
        assertTrue(state.replaceCurrentInboxDetail(postOnly))
        assertNull(state.consumeInboxReadReceipt("m3-terminal", "c3"))
    }

    @Test
    fun localSessionsMarkReadOnReplaceAndTheReplacedDetailIsGoneAfterBack() {
        val state = SubshellState()
        state.navigate(MainDestination.INBOX)
        state.openInboxItem(message)
        val depth = state.navigationRouteDepth

        assertTrue(state.replaceCurrentInboxDetail(reply))
        assertTrue(reply.id in state.readInboxIds)
        assertEquals("m3-terminal", state.selectedPost?.id)
        assertEquals(depth, state.navigationRouteDepth)
        state.navigateBack()
        assertEquals(depth - 1, state.navigationRouteDepth)
        assertNull(state.selectedPost)
        assertNull(state.selectedConversation)
    }

    private fun authenticatedState(): SubshellState {
        val account = AccountSessionSummary(
            stableId = "reddit:test",
            username = "connected-user",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        return SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
        )
    }
}
