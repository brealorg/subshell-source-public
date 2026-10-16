package app.subshell.prototype.data.auth.reddit

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Messages02D2BMatrixTypingPolicyTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun typingStartIncludesBoundedTimeoutAndStopOmitsTimeout() {
        val start = json.parseToJsonElement(
            redditChatMatrixTypingPayload(
                typing = true,
                timeoutMs = 15_000L,
            ),
        ).jsonObject
        assertTrue(start.getValue("typing").jsonPrimitive.content.toBoolean())
        assertTrue(start.getValue("timeout").jsonPrimitive.content.toLong() == 15_000L)

        val stop = json.parseToJsonElement(
            redditChatMatrixTypingPayload(typing = false),
        ).jsonObject
        assertFalse(stop.getValue("typing").jsonPrimitive.content.toBoolean())
        assertFalse("timeout" in stop)
    }

    @Test
    fun typingCadenceSendsTransitionsAndPeriodicKeepaliveOnly() {
        assertTrue(
            shouldSendDirectChatTypingUpdate(
                previousTyping = null,
                previousAttemptAtMs = 0L,
                desiredTyping = true,
                nowMs = 1_000L,
                keepAliveMs = 5_000L,
            ),
        )
        assertFalse(
            shouldSendDirectChatTypingUpdate(
                previousTyping = null,
                previousAttemptAtMs = 0L,
                desiredTyping = false,
                nowMs = 1_000L,
                keepAliveMs = 5_000L,
            ),
        )
        assertFalse(
            shouldSendDirectChatTypingUpdate(
                previousTyping = true,
                previousAttemptAtMs = 1_000L,
                desiredTyping = true,
                nowMs = 4_999L,
                keepAliveMs = 5_000L,
            ),
        )
        assertTrue(
            shouldSendDirectChatTypingUpdate(
                previousTyping = true,
                previousAttemptAtMs = 1_000L,
                desiredTyping = true,
                nowMs = 6_000L,
                keepAliveMs = 5_000L,
            ),
        )
        assertTrue(
            shouldSendDirectChatTypingUpdate(
                previousTyping = true,
                previousAttemptAtMs = 6_000L,
                desiredTyping = false,
                nowMs = 6_100L,
                keepAliveMs = 5_000L,
            ),
        )
        assertFalse(
            shouldSendDirectChatTypingUpdate(
                previousTyping = false,
                previousAttemptAtMs = 6_100L,
                desiredTyping = false,
                nowMs = 7_000L,
                keepAliveMs = 5_000L,
            ),
        )
    }
}
