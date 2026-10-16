package app.subshell.prototype.ui

import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.ui.screens.ConversationDetailKind
import app.subshell.prototype.ui.screens.conversationDetailKind
import app.subshell.prototype.ui.screens.conversationParticipantUsername
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Messages02BConversationDetailPolicyTest {
    @Test
    fun directChatSubjectUsesConversationalDetail() {
        val item = inboxItem(title = "[direct chat room]")
        assertEquals(
            ConversationDetailKind.DIRECT_CHAT,
            conversationDetailKind(item),
        )
    }

    @Test
    fun normalRedditPmUsesThreadDetail() {
        val item = inboxItem(title = "Icons in boost?")
        assertEquals(
            ConversationDetailKind.PRIVATE_MESSAGE_THREAD,
            conversationDetailKind(item),
        )
    }

    @Test
    fun onlyUserParticipantsAreIdentityHydrationCandidates() {
        assertEquals("example", conversationParticipantUsername("example"))
        assertEquals("example", conversationParticipantUsername("u/example"))
        assertNull(conversationParticipantUsername("r/MorpheApp"))
        assertNull(conversationParticipantUsername("#MorpheApp"))
        assertNull(conversationParticipantUsername("reddit"))
    }

    private fun inboxItem(title: String) = InboxItem(
        id = "t4_thread",
        kind = ActivityKind.MESSAGE,
        title = title,
        sender = "example",
        age = "1d",
        preview = "Hello",
    )
}
