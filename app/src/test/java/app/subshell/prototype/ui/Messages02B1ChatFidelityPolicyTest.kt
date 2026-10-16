package app.subshell.prototype.ui

import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.ui.screens.inboxMessageSubjectLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class Messages02B1ChatFidelityPolicyTest {
    @Test
    fun directChatTransportSubjectIsNeverExposedInMessagesList() {
        val item = InboxItem(
            id = "t4_chat",
            kind = ActivityKind.MESSAGE,
            title = "[direct chat room]",
            sender = "peer",
            age = "1m",
            preview = "hello",
        )
        assertEquals("Direct chat", inboxMessageSubjectLabel(item))
    }

    @Test
    fun normalPrivateMessageSubjectRemainsUnchanged() {
        val item = InboxItem(
            id = "t4_pm",
            kind = ActivityKind.MESSAGE,
            title = "Icons in boost?",
            sender = "peer",
            age = "1d",
            preview = "hello",
        )
        assertEquals("Icons in boost?", inboxMessageSubjectLabel(item))
    }
}
