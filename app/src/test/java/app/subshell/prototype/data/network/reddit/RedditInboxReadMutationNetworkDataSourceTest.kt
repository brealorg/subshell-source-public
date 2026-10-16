package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.model.MessageBubble
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedditInboxReadMutationNetworkDataSourceTest {
    @Test
    fun markReadRequestUsesDocumentedPrivateMessageEndpointAndCommaSeparatedFullnames() {
        val request = redditMarkInboxReadRequest(
            listOf("t4_root", " t4_reply ", "t4_root"),
        )

        assertEquals(listOf("api", "read_message"), request.pathSegments)
        assertEquals(listOf("id" to "t4_root,t4_reply"), request.fields)
    }

    @Test
    // DATALAYER04: own outgoing messages are not read targets (they were
    // before; only incoming messages can be unread).
    fun messageThreadReadTargetsAreRootAndIncomingMessageFullnames() {
        val item = InboxItem(
            id = "t4_root",
            kind = ActivityKind.MESSAGE,
            title = "Thread",
            sender = "peer",
            age = "1m",
            preview = "Latest",
            unread = true,
            conversation = listOf(
                MessageBubble(
                    id = "t4_root",
                    sender = "peer",
                    age = "2m",
                    body = "Root",
                ),
                MessageBubble(
                    id = "t4_mine",
                    sender = "me",
                    age = "1m",
                    body = "Reply",
                    outgoing = true,
                ),
                MessageBubble(
                    id = "reply-not-a-fullname",
                    sender = "peer",
                    age = "1m",
                    body = "Ignored malformed id",
                ),
            ),
        )

        assertEquals(
            listOf("t4_root"),
            item.redditReadTargetFullnames(),
        )
    }

    // DATALAYER04: a thread longer than Reddit's bound keeps its newest
    // incoming messages instead of failing, so it can be marked read and
    // mark-all no longer fails for every other item because of it.
    @Test
    fun aLongThreadKeepsItsNewestIncomingMessagesWithinTheBound() {
        val incoming = (1..150).map { index ->
            MessageBubble(id = "t4_in$index", sender = "peer", age = "1m", body = "in $index")
        }
        val outgoing = (1..80).map { index ->
            MessageBubble(id = "t4_out$index", sender = "me", age = "1m", body = "out $index", outgoing = true)
        }
        val item = InboxItem(
            id = "t4_in1",
            kind = ActivityKind.MESSAGE,
            title = "Long thread",
            sender = "peer",
            age = "1m",
            preview = "Latest",
            unread = true,
            conversation = (incoming + outgoing).shuffled(kotlin.random.Random(7))
                .sortedBy { bubble -> bubble.id.removePrefix("t4_in").removePrefix("t4_out").toInt() },
        )

        val targets = item.redditReadTargetFullnames()

        assertEquals(REDDIT_INBOX_READ_MAX_IDS, targets.size)
        assertEquals("t4_in1", targets.first())
        assertEquals("t4_in150", targets.last())
        assertTrue(targets.none { target -> target.startsWith("t4_out") })

        val other = InboxItem(
            id = "t1_other",
            kind = ActivityKind.REPLY,
            title = "Reply",
            sender = "peer",
            age = "1m",
            preview = "Body",
            unread = true,
        )
        assertEquals(listOf("t1_other"), other.redditReadTargetFullnames())
    }

    @Test
    fun activityReadTargetPreservesCommentFullname() {
        val item = InboxItem(
            id = "t1_comment",
            kind = ActivityKind.REPLY,
            title = "Reply",
            sender = "peer",
            age = "1m",
            preview = "Body",
            unread = true,
        )

        assertEquals(listOf("t1_comment"), item.redditReadTargetFullnames())
    }

    @Test
    fun readUsesDocumentedEndpointAndNoUndocumentedUnreadEndpointExists() {
        // ISSUE106: read_all_messages is gone; only read_message remains.
        val source = redditMarkInboxReadRequest(listOf("t4_root")).pathSegments.joinToString("/")
        assertEquals("api/read_message", source)
        assertFalse(source.contains("unread_message"))
        assertFalse(source.contains("read_all_messages"))
    }

    @Test
    fun singleReadTargetsAreBatchedAtRedditTwentyFiveItemRequestLimit() {
        val valid =
            (1..REDDIT_INBOX_READ_MAX_IDS).map { index -> "t4_$index" }

        val batches = redditReadTargetFullnameRequestBatches(valid)

        assertEquals(4, batches.size)
        assertTrue(
            batches.all { batch ->
                batch.size <= REDDIT_INBOX_READ_REQUEST_MAX_IDS
            },
        )
        assertEquals(valid, batches.flatten())
        assertEquals(100, REDDIT_INBOX_READ_MAX_IDS)
        assertEquals(25, REDDIT_INBOX_READ_REQUEST_MAX_IDS)
        val oversizedRequest = (1..26).map { index -> "t4_req$index" }
        assertTrue(
            runCatching { redditMarkInboxReadRequest(oversizedRequest) }.isFailure,
        )
    }

    @Test
    fun readMutationTargetsAreStrictlyBounded() {
        val valid = (1..REDDIT_INBOX_READ_MAX_IDS).map { index -> "t4_$index" }
        assertEquals(REDDIT_INBOX_READ_MAX_IDS, normalizeInboxReadFullnames(valid).size)
        val tooMany = valid + "t4_extra"
        assertTrue(
            runCatching { normalizeInboxReadFullnames(tooMany) }.isFailure,
        )
    }
}
