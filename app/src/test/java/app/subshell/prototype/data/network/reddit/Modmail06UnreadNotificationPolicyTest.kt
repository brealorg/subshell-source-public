package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.account.RedditModmailConversation
import app.subshell.prototype.data.account.RedditModmailListMode
import app.subshell.prototype.data.account.RedditModmailUiState
import app.subshell.prototype.data.account.RedditModmailUnreadCounts
import app.subshell.prototype.data.auth.reddit.redditModmailReadStateFormRequest
import app.subshell.prototype.inbox.MODMAIL_NOTIFICATION_ACTION_OPEN
import app.subshell.prototype.inbox.ModmailNotificationEvent
import app.subshell.prototype.inbox.ModmailNotificationTarget
import app.subshell.prototype.inbox.modmailBackgroundEventKey
import app.subshell.prototype.inbox.modmailBackgroundUnreadEventKeys
import app.subshell.prototype.inbox.modmailNotificationEvents
import app.subshell.prototype.inbox.modmailNotificationTarget
import app.subshell.prototype.inbox.reconcileInboxBackgroundEventHistory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Modmail06UnreadNotificationPolicyTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun unreadRequestsKeepThreadFetchReadOnlyAndUseExplicitWriteEndpoints() {
        val defaultList = redditModmailConversationsRequest()
        assertEquals(
            "recent",
            defaultList.queryParameters.single { (name, _) -> name == "sort" }.second,
        )
        val unreadList = redditModmailConversationsRequest(
            state = "all",
            sort = "unread",
            limit = 100,
        )
        assertEquals(
            "unread",
            unreadList.queryParameters.single { (name, _) -> name == "sort" }.second,
        )
        assertEquals(
            listOf("api", "mod", "conversations", "unread", "count"),
            redditModmailUnreadCountsRequest().pathSegments,
        )
        assertEquals(
            "false",
            redditModmailConversationRequest("abc123")
                .queryParameters
                .single { (name, _) -> name == "markRead" }
                .second,
        )

        val read = redditModmailReadStateFormRequest(
            conversationIds = listOf("ABC123", "abc123"),
            unread = false,
        )
        assertEquals(
            listOf("api", "mod", "conversations", "read"),
            read.pathSegments,
        )
        assertEquals(listOf("conversationIds" to "abc123"), read.fields)

        val unread = redditModmailReadStateFormRequest(
            conversationIds = listOf("abc123", "def456"),
            unread = true,
        )
        assertEquals(
            listOf("api", "mod", "conversations", "unread"),
            unread.pathSegments,
        )
        assertEquals(listOf("conversationIds" to "abc123,def456"), unread.fields)
    }

    @Test
    fun authoritativeUnreadCountsProjectAllSupportedQueueStates() {
        val payload = json.parseToJsonElement(
            """
            {
              "new": 2,
              "inprogress": 3,
              "filtered": 4,
              "highlighted": 5,
              "archived": 6,
              "appeals": 7,
              "joinRequests": 8,
              "mod": 9,
              "notifications": 10,
              "ignored": 999
            }
            """.trimIndent(),
        ).jsonObject
        val counts = payload.toRedditModmailUnreadCounts()

        assertEquals(2, counts.new)
        assertEquals(3, counts.inProgress)
        assertEquals(4, counts.filtered)
        assertEquals(5, counts.highlighted)
        assertEquals(6, counts.archived)
        assertEquals(7, counts.appeals)
        assertEquals(8, counts.joinRequests)
        assertEquals(9, counts.modDiscussions)
        assertEquals(10, counts.notifications)

        // Reddit's All/Active folder is New + In Progress + Join Requests +
        // Notifications. Highlighted is an overlay and must not double-count.
        assertEquals(23, counts.active)
        assertEquals(23, counts.countFor(RedditModmailListMode.ACTIVE))
        assertEquals(23, RedditModmailUiState(unreadCounts = counts).unreadCount)
        assertEquals(8, counts.countFor(RedditModmailListMode.JOIN_REQUESTS))

        val defensive = json.parseToJsonElement(
            """{"joinRequests":-3,"new":"not-a-number","join_requests":8,"all":99}""",
        ).jsonObject.toRedditModmailUnreadCounts()
        assertEquals(0, defensive.joinRequests)
        assertEquals(0, defensive.new)
        assertEquals(0, defensive.active)
    }

    @Test
    fun backgroundModmailEventsUseMessageIdentityAndBaselineExistingUnread() {
        val first = conversation(
            id = "abc123",
            latestMessageId = "msg001",
            unread = true,
            lastUnreadEpochSeconds = 100L,
        )
        assertEquals("modmail:abc123:msg001", first.modmailBackgroundEventKey())
        assertEquals(
            "modmail:abc123:msg001",
            first.copy(
                unread = false,
                lastUnreadEpochSeconds = null,
            ).modmailBackgroundEventKey(),
        )
        assertEquals(
            "modmail:abc123:msg002",
            first.copy(
                latestMessageId = "msg002",
                lastUnreadEpochSeconds = 200L,
            ).modmailBackgroundEventKey(),
        )
        assertEquals(
            listOf("modmail:abc123:msg001"),
            modmailBackgroundUnreadEventKeys(
                listOf(first, first.copy(id = "def456", unread = false)),
            ),
        )

        val baseline = reconcileInboxBackgroundEventHistory(
            initialized = false,
            previousEventKeys = emptyList(),
            currentEventKeys = listOf("modmail:abc123:msg001"),
        )
        assertTrue(baseline.newEventKeys.isEmpty())
        assertEquals(listOf("modmail:abc123:msg001"), baseline.retainedEventKeys)

        val later = reconcileInboxBackgroundEventHistory(
            initialized = true,
            previousEventKeys = baseline.retainedEventKeys,
            currentEventKeys = listOf("modmail:abc123:msg002"),
        )
        assertEquals(listOf("modmail:abc123:msg002"), later.newEventKeys)
    }

    @Test
    fun notificationEventAndTargetKeepExactConversationIdentity() {
        val conversation = conversation(
            id = "abc123",
            latestMessageId = "msg002",
            unread = true,
            lastUnreadEpochSeconds = 200L,
        )
        assertEquals(
            listOf(
                ModmailNotificationEvent(
                    eventKey = "modmail:abc123:msg002",
                    conversationId = "abc123",
                    communityName = "subshell",
                    subject = "Fixture subject",
                    preview = "Fixture preview",
                ),
            ),
            modmailNotificationEvents(
                conversations = listOf(conversation),
                newEventKeys = listOf("modmail:abc123:msg002"),
            ),
        )
        assertEquals(
            ModmailNotificationTarget(
                accountStableId = "reddit:test",
                conversationId = "abc123",
                eventKey = "modmail:abc123:msg002",
            ),
            modmailNotificationTarget(
                action = MODMAIL_NOTIFICATION_ACTION_OPEN,
                accountStableId = " reddit:test ",
                conversationId = " ModmailConversation_ABC123 ",
                eventKey = " modmail:abc123:msg002 ",
            ),
        )
        assertNull(
            modmailNotificationTarget(
                action = MODMAIL_NOTIFICATION_ACTION_OPEN,
                accountStableId = "reddit:test",
                conversationId = "../../abc123",
                eventKey = "modmail:abc123:msg002",
            ),
        )
        assertNull(
            modmailNotificationTarget(
                action = "other",
                accountStableId = "reddit:test",
                conversationId = "abc123",
                eventKey = "modmail:abc123:msg002",
            ),
        )
    }

    private fun conversation(
        id: String,
        latestMessageId: String,
        unread: Boolean,
        lastUnreadEpochSeconds: Long?,
    ) = RedditModmailConversation(
        id = id,
        communityName = "subshell",
        subject = "Fixture subject",
        participantName = "fixture_user",
        preview = "Fixture preview",
        lastUpdatedEpochSeconds = 300L,
        lastUnreadEpochSeconds = lastUnreadEpochSeconds,
        latestMessageId = latestMessageId,
        unread = unread,
        isInternal = false,
        messageCount = 2,
    )
}
