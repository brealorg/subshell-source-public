package io.github.brealorg.subshell.ui

import io.github.brealorg.subshell.model.ActivityKind
import io.github.brealorg.subshell.model.InboxItem
import io.github.brealorg.subshell.ui.screens.InboxTabUnreadCounts
import io.github.brealorg.subshell.ui.screens.inboxTabUnreadCounts
import org.junit.Assert.assertEquals
import org.junit.Test

class InboxTabUnreadCountsTest {
    @Test
    fun unreadCountsStayIndependentAcrossActivityAndMessages() {
        val items = listOf(
            item("reply-1", ActivityKind.REPLY, unread = true),
            item("mention-1", ActivityKind.MENTION, unread = true),
            item("mod-1", ActivityKind.MOD, unread = false),
            item("message-1", ActivityKind.MESSAGE, unread = true),
            item("message-2", ActivityKind.MESSAGE, unread = true),
        )

        assertEquals(
            InboxTabUnreadCounts(activity = 2, messages = 1),
            inboxTabUnreadCounts(
                items = items,
                locallySeenIds = setOf("message-2"),
            ),
        )
    }

    @Test
    fun locallySeenActivityDoesNotClearMessagesAndViceVersa() {
        val items = listOf(
            item("reply-1", ActivityKind.REPLY, unread = true),
            item("message-1", ActivityKind.MESSAGE, unread = true),
        )

        assertEquals(
            InboxTabUnreadCounts(activity = 0, messages = 1),
            inboxTabUnreadCounts(items, locallySeenIds = setOf("reply-1")),
        )
        assertEquals(
            InboxTabUnreadCounts(activity = 1, messages = 0),
            inboxTabUnreadCounts(items, locallySeenIds = setOf("message-1")),
        )
    }

    private fun item(
        id: String,
        kind: ActivityKind,
        unread: Boolean,
    ) = InboxItem(
        id = id,
        kind = kind,
        title = id,
        sender = "sender",
        age = "now",
        preview = "preview",
        unread = unread,
    )
}
