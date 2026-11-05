package io.github.brealorg.subshell.ui

import io.github.brealorg.subshell.model.ActivityKind
import io.github.brealorg.subshell.model.InboxItem
import io.github.brealorg.subshell.ui.screens.conversationParticipantLabel
import io.github.brealorg.subshell.ui.screens.resolveConversationRefreshItem
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
