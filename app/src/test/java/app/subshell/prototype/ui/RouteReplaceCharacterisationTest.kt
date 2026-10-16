package app.subshell.prototype.ui

import app.subshell.prototype.data.SampleData
import app.subshell.prototype.model.ActivityKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterisation of the five in-place route replacements (post, user,
 * community, inbox detail, new-message handoff) inside SubshellState as
 * they behave today, written before they share one private primitive.
 * Current behaviour, not a specification.
 */
class RouteReplaceCharacterisationTest {
    @Test
    fun postSelectionReplacesTheCurrentPostRouteWithoutGrowingTheStack() {
        val state = SubshellState()
        val first = SampleData.posts[0]
        val second = SampleData.posts[1]
        assertFalse(state.replaceCurrentPostSelection(second))

        state.open(first)
        val depth = state.navigationRouteDepth
        val visit = state.postVisitId
        val origin = (state.currentNavigationRoute as SubshellRoute.Post).postRoute.origin

        assertTrue(state.replaceCurrentPostSelection(second))
        assertEquals(depth, state.navigationRouteDepth)
        assertEquals(second.id, state.selectedPost?.id)
        assertEquals(visit + 1, state.postVisitId)
        assertEquals(origin, (state.currentNavigationRoute as SubshellRoute.Post).postRoute.origin)

        state.navigateBack()
        assertEquals(depth - 1, state.navigationRouteDepth)
        assertNull(state.selectedPost)
    }

    @Test
    fun userSelectionReplacesOnlyAUserRouteAndIgnoresTheSameName() {
        val state = SubshellState()
        assertFalse(state.replaceCurrentUserSelection("bob"))

        state.openUser("alice")
        val depth = state.navigationRouteDepth
        assertEquals("alice", state.selectedUser)
        assertTrue(state.replaceCurrentUserSelection(" ALICE "))
        assertEquals("alice", state.selectedUser)
        assertFalse(state.replaceCurrentUserSelection("   "))

        assertTrue(state.replaceCurrentUserSelection("bob"))
        assertEquals("bob", state.selectedUser)
        assertEquals(depth, state.navigationRouteDepth)
        state.navigateBack()
        assertNull(state.selectedUser)
        assertEquals(depth - 1, state.navigationRouteDepth)
    }

    @Test
    fun communitySelectionReplacesOnlyASubscriptionsCommunityRoute() {
        val state = SubshellState()
        val first = SampleData.communities[0].name
        val second = SampleData.communities[1].name
        assertFalse(state.replaceCurrentCommunitySelection(second))

        state.navigate(MainDestination.SUBS)
        state.openSubscriptionsCommunitySelection(first)
        val depth = state.navigationRouteDepth
        assertEquals(first, state.selectedSubreddit)

        assertTrue(state.replaceCurrentCommunitySelection(first.uppercase()))
        assertEquals(first, state.selectedSubreddit)
        assertTrue(state.replaceCurrentCommunitySelection(second))
        assertEquals(second, state.selectedSubreddit)
        assertEquals(depth, state.navigationRouteDepth)

        // openSubscriptionsCommunitySelection rekeys when possible …
        state.openSubscriptionsCommunitySelection(first)
        assertEquals(first, state.selectedSubreddit)
        assertEquals(depth, state.navigationRouteDepth)
        assertTrue(state.currentSubscriptionsCommunitySelectionMatches(first.uppercase()))
        assertFalse(state.currentSubscriptionsCommunitySelectionMatches(second))

        // … and a rekey applies the target community's stored sort preference.
        val preferred = CommunitySort.entries.first { it != state.communitySort }
        state.updateCommunitySort(preferred)
        assertEquals(preferred, state.communitySort)
        assertTrue(state.replaceCurrentCommunitySelection(second))
        assertNotEquals(preferred, state.communitySort)
        assertTrue(state.replaceCurrentCommunitySelection(first))
        assertEquals(preferred, state.communitySort)

        // … and a community opened from Home is not eligible.
        state.navigate(MainDestination.HOME)
        state.openSubreddit(first)
        assertFalse(state.replaceCurrentCommunitySelection(second))
        assertEquals(first, state.selectedSubreddit)
    }

    @Test
    fun inboxDetailReplacesAConversationOrAnInboxPostAndDropsTheReplacedPost() {
        val state = SubshellState()
        val message = SampleData.inbox.first { it.kind == ActivityKind.MESSAGE }
        // The fixture has one message; a second conversation is a copy with its own id.
        val messages = listOf(message, message.copy(id = message.id + "-second"))
        val postItem = SampleData.inbox.first { it.targetPostId != null && it.kind != ActivityKind.MESSAGE }
        assertFalse(state.replaceCurrentInboxDetail(messages[0]))

        state.navigate(MainDestination.INBOX)
        state.openInboxItem(messages[0])
        val depth = state.navigationRouteDepth
        assertEquals(messages[0].id, state.selectedConversation?.id)

        assertTrue(state.replaceCurrentInboxDetail(messages[1]))
        assertEquals(messages[1].id, state.selectedConversation?.id)
        assertEquals(depth, state.navigationRouteDepth)

        // Conversation → inbox post → conversation, all in place.
        assertTrue(state.replaceCurrentInboxDetail(postItem))
        assertEquals(postItem.targetPostId, state.selectedPost?.id)
        assertNull(state.selectedConversation)
        assertEquals(depth, state.navigationRouteDepth)
        assertTrue(state.replaceCurrentInboxDetail(messages[0]))
        assertNull(state.selectedPost)
        assertEquals(messages[0].id, state.selectedConversation?.id)

        // A post opened from Home is not an inbox detail.
        state.navigate(MainDestination.HOME)
        state.open(SampleData.posts[0])
        assertFalse(state.replaceCurrentInboxDetail(messages[0]))
    }

    @Test
    fun newMessageHandoffReplacesTheComposerSurfaceWithTheConversation() {
        val state = SubshellState()
        val message = SampleData.inbox.first { it.kind == ActivityKind.MESSAGE }
        val other = SampleData.inbox.first { it.kind != ActivityKind.MESSAGE }
        assertFalse(state.replaceCurrentNewMessageWithConversation(message))

        state.navigate(MainDestination.INBOX)
        state.openSecondary(SecondaryDestination.NEW_MESSAGE)
        val depth = state.navigationRouteDepth
        assertEquals(SecondaryDestination.NEW_MESSAGE, state.secondaryDestination)
        assertFalse(state.replaceCurrentNewMessageWithConversation(other))
        assertEquals(SecondaryDestination.NEW_MESSAGE, state.secondaryDestination)

        assertTrue(state.replaceCurrentNewMessageWithConversation(message))
        assertTrue(state.currentNavigationRoute is SubshellRoute.Conversation)
        assertEquals(message.id, state.selectedConversation?.id)
        assertNull(state.secondaryDestination)
        assertEquals(depth, state.navigationRouteDepth)
        assertNotEquals(MainDestination.HOME, state.destination)
    }

    // STEP3 (next01a4a): replacing the You workspace's open post keeps the
    // comment the reader tapped, and a plain post replacement clears it; the
    // stack does not grow either way.
    @Test
    fun userContentSelectionKeepsTheFocusedComment() {
        val state = SubshellState()
        val first = SampleData.posts[0]
        val second = SampleData.posts[1]
        state.openFromUser(first, "connected-user")
        val depth = state.navigationRouteDepth

        assertTrue(state.replaceCurrentUserContentSelection(second, "connected-user", commentId = "t1_focus"))
        assertEquals(depth, state.navigationRouteDepth)
        assertEquals(second.id, state.selectedPost?.id)
        val focused = state.currentNavigationRoute as SubshellRoute.Post
        assertEquals("t1_focus", focused.postRoute.focusedCommentId)

        assertTrue(state.replaceCurrentUserContentSelection(first, "connected-user"))
        assertEquals(depth, state.navigationRouteDepth)
        assertNull((state.currentNavigationRoute as SubshellRoute.Post).postRoute.focusedCommentId)
        // Another user's list does not own this selection.
        assertFalse(state.replaceCurrentUserContentSelection(second, "someone-else"))
    }
}
