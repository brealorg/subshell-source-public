package app.subshell.prototype.data.auth.reddit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * CHATREAD01 (OCB-485): a Direct Chat send whose answer was lost was never
 * read back. The room timeline carries unsigned.transaction_id on this
 * sender's events; the event with our transaction id is the sent one.
 */
class ChatRead01LostSendReadBackTest {
    private fun sync(vararg events: String) = """
        {"rooms":{"join":{"!room:reddit.com":{"timeline":{"events":[${events.joinToString(",")}]}}}}}
    """.trimIndent()

    private fun event(id: String, txn: String?) =
        """{"type":"m.room.message","event_id":"$id","sender":"@t2_me:reddit.com",""" +
            """"content":{"msgtype":"m.text","body":"hi"}""" +
            (txn?.let { ""","unsigned":{"transaction_id":"$it"}""" } ?: "") + "}"

    @Test
    fun theEventWithOurTransactionIdIsTheSentOne() {
        val body = sync(event("\$other", "subshell_1_a"), event("\$ours", "subshell_2_b"))

        assertEquals("\$ours", redditChatMatrixEventIdForTransaction(body, "subshell_2_b"))
    }

    @Test
    fun noMatchingEventOrAnUnreadableAnswerFindsNothing() {
        assertNull(redditChatMatrixEventIdForTransaction(sync(event("\$peer", null)), "subshell_2_b"))
        assertNull(redditChatMatrixEventIdForTransaction("not json", "subshell_2_b"))
        assertNull(redditChatMatrixEventIdForTransaction(sync(), "subshell_2_b"))
    }
}
