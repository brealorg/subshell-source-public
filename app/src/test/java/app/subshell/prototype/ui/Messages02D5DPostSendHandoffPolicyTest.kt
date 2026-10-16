package app.subshell.prototype.ui

import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.model.MessageBubble
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Messages02D5DPostSendHandoffPolicyTest {
    @Test
    fun authoritativeDirectChatMatchesSelectedParticipant() {
        val expected = directChat(id = "t4_expected", sender = "example")
        val unrelated = directChat(id = "t4_other", sender = "someone_else")

        assertEquals(
            expected,
            resolveDirectChatPostSendHandoffItem(
                participantUsername = "u/Example",
                items = listOf(unrelated, expected),
            ),
        )
    }

    @Test
    fun authoritativePeerCanComeFromIncomingConversationMessage() {
        val expected = directChat(
            id = "t4_thread",
            sender = "fallback",
            conversation = listOf(
                MessageBubble(
                    id = "outgoing",
                    sender = "me",
                    age = "now",
                    body = "hello",
                    outgoing = true,
                ),
                MessageBubble(
                    id = "incoming",
                    sender = "u/TargetUser",
                    age = "now",
                    body = "reply",
                    outgoing = false,
                ),
            ),
        )

        assertEquals(
            expected,
            resolveDirectChatPostSendHandoffItem(
                participantUsername = "targetuser",
                items = listOf(expected),
            ),
        )
    }

    @Test
    fun normalPrivateMessageWithSameSenderIsNotDirectChatHandoff() {
        val privateMessage = InboxItem(
            id = "t4_pm",
            kind = ActivityKind.MESSAGE,
            title = "Normal private message",
            sender = "example",
            age = "now",
            preview = "hello",
        )

        assertNull(
            resolveDirectChatPostSendHandoffItem(
                participantUsername = "example",
                items = listOf(privateMessage),
            ),
        )
    }

    @Test
    fun nonMessageDirectChatShapedItemIsRejected() {
        val notMessage = InboxItem(
            id = "activity",
            kind = ActivityKind.MOD,
            title = "[direct chat room]",
            sender = "example",
            age = "now",
            preview = "hello",
        )

        assertNull(
            resolveDirectChatPostSendHandoffItem(
                participantUsername = "example",
                items = listOf(notMessage),
            ),
        )
    }

    @Test
    fun newMessageRouteIsReplacedByAuthoritativeConversationAndBackReturnsToInbox() {
        val state = SubshellState(
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
        )
        state.navigate(MainDestination.INBOX)
        state.openSecondary(SecondaryDestination.NEW_MESSAGE)
        val item = directChat(id = "t4_authoritative", sender = "example")

        assertTrue(state.currentNavigationRoute is SubshellRoute.Secondary)
        assertTrue(state.replaceCurrentNewMessageWithConversation(item))

        val route = state.currentNavigationRoute
        assertTrue(route is SubshellRoute.Conversation)
        assertEquals(
            item.id,
            (route as SubshellRoute.Conversation).inboxItemId,
        )
        assertEquals(item, state.selectedConversation)
        assertEquals(MainDestination.INBOX, state.destination)

        assertTrue(state.navigateBack())
        assertTrue(state.currentNavigationRoute is SubshellRoute.Main)
        assertEquals(MainDestination.INBOX, state.destination)
        assertNull(state.selectedConversation)
    }

    @Test
    fun handoffRefusesNonNewMessageRouteAndNonMessageItem() {
        val state = SubshellState(
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
        )
        state.navigate(MainDestination.INBOX)
        val item = directChat(id = "t4_authoritative", sender = "example")

        assertFalse(state.replaceCurrentNewMessageWithConversation(item))

        state.openSecondary(SecondaryDestination.NEW_MESSAGE)
        val notMessage = item.copy(kind = ActivityKind.MOD)
        assertFalse(state.replaceCurrentNewMessageWithConversation(notMessage))
        assertTrue(state.currentNavigationRoute is SubshellRoute.Secondary)
    }

    private fun directChat(
        id: String,
        sender: String,
        conversation: List<MessageBubble> = emptyList(),
    ) = InboxItem(
        id = id,
        kind = ActivityKind.MESSAGE,
        title = "[direct chat room]",
        sender = sender,
        age = "now",
        preview = "hello",
        conversation = conversation,
    )
}
