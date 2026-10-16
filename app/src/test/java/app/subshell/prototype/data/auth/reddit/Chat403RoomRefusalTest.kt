package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.account.MessageReplyFailureKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CHAT403 (OCB-613): a Matrix 403 M_FORBIDDEN on a room action threw away
 * the chat token and asked to sign in again; it is the room refusing.
 */
class Chat403RoomRefusalTest {
    private val forbidden = """{"errcode":"M_FORBIDDEN","error":"You are not in this room"}"""

    @Test
    fun aRoomRefusalKeepsTheSession() {
        val errcode = redditChatMatrixErrcode(forbidden)

        assertEquals("M_FORBIDDEN", errcode)
        assertTrue(redditChatMatrixRoomRefusal(403, errcode))
        assertFalse(redditChatMatrixSessionRefusal(403, errcode))
    }

    @Test
    fun tokenProblemsStillEndTheSession() {
        assertTrue(redditChatMatrixSessionRefusal(401, "M_FORBIDDEN"))
        assertTrue(redditChatMatrixSessionRefusal(403, "M_UNKNOWN_TOKEN"))
        assertTrue(redditChatMatrixSessionRefusal(403, redditChatMatrixErrcode("not json")))
        assertTrue(redditChatMatrixSessionRefusal(403, redditChatMatrixErrcode("""{"errcode":7}""")))
        assertNull(redditChatMatrixErrcode(null))
        assertFalse(redditChatMatrixSessionRefusal(404, null))
    }

    @Test
    fun theStatusOnlyRuleIsUnchangedForOtherAnswers() {
        assertEquals(
            MessageReplyFailureKind.CHAT_SESSION_REQUIRED,
            redditChatMatrixHttpFailureKind(403, CHAT_STEP_IMAGE_SEND),
        )
    }
}
