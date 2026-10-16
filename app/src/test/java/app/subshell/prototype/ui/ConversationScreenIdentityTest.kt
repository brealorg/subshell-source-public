package app.subshell.prototype.ui

import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.ui.screens.conversationParticipantLabel
import app.subshell.prototype.ui.screens.resolveConversationRefreshItem
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationScreenIdentityTest {
    @Test
    fun communityAndUserParticipantsUseCanonicalPrefixes() {
        assertEquals("r/MorpheApp", conversationParticipantLabel("r/MorpheApp"))
        assertEquals("r/MorpheApp", conversationParticipantLabel("#MorpheApp"))
        assertEquals("u/example", conversationParticipantLabel("example"))
        assertEquals("reddit", conversationParticipantLabel("reddit"))
    }

    @Test
    fun liveRefreshRebindsOpenConversationByStableThreadId() {
        val selected = conversationItem(preview = "Old snapshot")
        val refreshed = conversationItem(preview = "Fresh from Reddit")

        assertEquals(
            refreshed,
            resolveConversationRefreshItem(
                selected = selected,
                inboxItemId = selected.id,
                liveItems = listOf(refreshed),
            ),
        )
        assertEquals(
            selected,
            resolveConversationRefreshItem(
                selected = selected,
                inboxItemId = selected.id,
                liveItems = emptyList(),
            ),
        )
    }

    private fun conversationItem(preview: String) = InboxItem(
        id = "t4_thread",
        kind = ActivityKind.MESSAGE,
        title = "Thread",
        sender = "example",
        age = "now",
        preview = preview,
    )
}
