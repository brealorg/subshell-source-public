package app.subshell.prototype.chat

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SECAUDIT12 (security audit #12): the chat token bridge is reachable from any
 * reddit.com frame in its WebView. Each mint's injected script carries a fresh
 * nonce, and the bridge ignores a call without it.
 */
class SecAudit12ChatBridgeNonceTest {
    @Test
    fun onlyTheMintsOwnNonceIsAccepted() {
        val nonce = UUID.randomUUID().toString()

        assertTrue(redditChatBridgeCallIsOwn(nonce, nonce))
        assertFalse(redditChatBridgeCallIsOwn(nonce, null))
        assertFalse(redditChatBridgeCallIsOwn(nonce, ""))
        assertFalse(redditChatBridgeCallIsOwn(nonce, UUID.randomUUID().toString()))
    }

    @Test
    fun theScriptHandsItsNonceToEveryBridgeCall() {
        val nonce = UUID.randomUUID().toString()
        val script = redditChatTokenFetchScript(nonce)

        assertTrue(script.contains("var nonce = '$nonce';"))
        val calls = Regex("""SubshellChatTokenBridge\.(onToken|onError)\(([^,)]*)""").findAll(script).toList()
        assertEquals(3, calls.size)
        calls.forEach { call -> assertEquals("nonce", call.groupValues[2].trim()) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun aNonceThatCouldBreakOutOfTheScriptIsRefused() {
        redditChatTokenFetchScript("x'); alert(1); ('")
    }
}
