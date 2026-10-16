package app.subshell.prototype.data.auth.reddit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Messages02D3BMatrixRedactionWriterPolicyTest {
    @Test
    fun matrixRedactionTargetRequiresARealEventIdShape() {
        assertEquals("${'$'}abc", redditChatMatrixRedactionEventIdOrNull("  ${'$'}abc  "))
        assertNull(redditChatMatrixRedactionEventIdOrNull(""))
        assertNull(redditChatMatrixRedactionEventIdOrNull("t4_legacy"))
        assertNull(redditChatMatrixRedactionEventIdOrNull("${'$'}bad\nvalue"))
    }

    @Test
    fun matrixRedactionPathIsRoomTargetAndTransactionScoped() {
        assertEquals(
            listOf(
                "_matrix",
                "client",
                "v3",
                "rooms",
                "!room:reddit.com",
                "redact",
                "${'$'}target",
                "txn-1",
            ),
            redditChatMatrixRedactionPathSegments(
                roomId = "!room:reddit.com",
                targetEventId = "${'$'}target",
                transactionId = "txn-1",
            ),
        )
    }
}
