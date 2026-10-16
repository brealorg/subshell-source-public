package app.subshell.prototype.inbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Notify01A2NotificationReadReconciliationPolicyTest {
    @Test
    fun postedRecordRoundTripPreservesAccountTargetEventAndAndroidId() {
        val record = requireNotNull(
            postedNotificationRecord(
                kind = PostedNotificationKind.INBOX,
                accountStableId = " reddit:alice ",
                targetId = " t4_thread ",
                eventKey = " message:t4_latest ",
                notificationId = 12345,
            ),
        )

        assertEquals("reddit:alice", record.accountStableId)
        assertEquals("t4_thread", record.targetId)
        assertEquals("message:t4_latest", record.eventKey)
        assertEquals(
            record,
            decodePostedNotificationRecord(
                encodePostedNotificationRecord(record),
            ),
        )

        assertNull(decodePostedNotificationRecord("broken"))
    }

    @Test
    fun sameEventAcrossAccountsHasDistinctAndroidIdentity() {
        val event = "message:t4_same"

        assertNotEquals(
            notificationAccountTag("reddit:alice"),
            notificationAccountTag("reddit:bob"),
        )
        assertNotEquals(
            notificationAccountGroupKey("reddit:alice"),
            notificationAccountGroupKey("reddit:bob"),
        )
        assertNotEquals(
            notificationPendingIntentRequestCode(
                accountStableId = "reddit:alice",
                eventKey = event,
            ),
            notificationPendingIntentRequestCode(
                accountStableId = "reddit:bob",
                eventKey = event,
            ),
        )
    }

    @Test
    fun oneInboxTargetCancelsAllSiblingEventsButNotOtherTargetsOrAccounts() {
        val records = listOf(
            record(
                PostedNotificationKind.INBOX,
                "reddit:alice",
                "t4_thread",
                "message:t4_one",
                1,
            ),
            record(
                PostedNotificationKind.INBOX,
                "reddit:alice",
                "t4_thread",
                "message:t4_two",
                2,
            ),
            record(
                PostedNotificationKind.INBOX,
                "reddit:alice",
                "t1_other",
                "activity:t1_other",
                3,
            ),
            record(
                PostedNotificationKind.INBOX,
                "reddit:bob",
                "t4_thread",
                "message:t4_one",
                4,
            ),
            record(
                PostedNotificationKind.MODMAIL,
                "reddit:alice",
                "t4_thread",
                "modmail:t4_thread:m1",
                5,
            ),
        )

        val selected = postedNotificationRecordsForTargets(
            records = records,
            kind = PostedNotificationKind.INBOX,
            accountStableId = "reddit:alice",
            targetIds = setOf("t4_thread"),
        )

        assertEquals(
            setOf("message:t4_one", "message:t4_two"),
            selected.mapTo(linkedSetOf()) { it.eventKey },
        )
    }

    @Test
    fun oneModmailConversationCancelsAllItsEventsOnlyForOwningAccount() {
        val records = listOf(
            record(
                PostedNotificationKind.MODMAIL,
                "reddit:alice",
                "abc123",
                "modmail:abc123:m1",
                10,
            ),
            record(
                PostedNotificationKind.MODMAIL,
                "reddit:alice",
                "abc123",
                "modmail:abc123:m2",
                11,
            ),
            record(
                PostedNotificationKind.MODMAIL,
                "reddit:alice",
                "def456",
                "modmail:def456:m1",
                12,
            ),
            record(
                PostedNotificationKind.MODMAIL,
                "reddit:bob",
                "abc123",
                "modmail:abc123:m1",
                13,
            ),
        )

        val selected = postedNotificationRecordsForTargets(
            records = records,
            kind = PostedNotificationKind.MODMAIL,
            accountStableId = "reddit:alice",
            targetIds = setOf("abc123"),
        )

        assertEquals(2, selected.size)
        assertTrue(selected.all { it.accountStableId == "reddit:alice" })
        assertTrue(selected.all { it.targetId == "abc123" })
    }

    @Test
    fun optimisticInboxReadDoesNotCancelUntilMutationSettles() {
        assertFalse(
            inboxNotificationIsSettledRead(
                unread = true,
                locallySeen = true,
                readMutationPending = true,
            ),
        )

        assertTrue(
            inboxNotificationIsSettledRead(
                unread = true,
                locallySeen = true,
                readMutationPending = false,
            ),
        )

        assertTrue(
            inboxNotificationIsSettledRead(
                unread = false,
                locallySeen = false,
                readMutationPending = false,
            ),
        )
    }

    private fun record(
        kind: PostedNotificationKind,
        account: String,
        target: String,
        event: String,
        id: Int,
    ): PostedNotificationRecord = requireNotNull(
        postedNotificationRecord(
            kind = kind,
            accountStableId = account,
            targetId = target,
            eventKey = event,
            notificationId = id,
        ),
    )
}
