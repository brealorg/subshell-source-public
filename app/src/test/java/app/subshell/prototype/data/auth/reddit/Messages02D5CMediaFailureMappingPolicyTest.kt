package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.account.DirectChatStartFailureKind
import app.subshell.prototype.data.account.MessageReplyFailureKind
import org.junit.Assert.assertEquals
import org.junit.Test

class Messages02D5CMediaFailureMappingPolicyTest {
    @Test
    fun mediaFailureMappingIsExhaustiveAndFailClosed() {
        val expected = mapOf(
            MessageReplyFailureKind.VALIDATION to DirectChatStartFailureKind.VALIDATION,
            MessageReplyFailureKind.CHAT_SESSION_REQUIRED to DirectChatStartFailureKind.SESSION,
            MessageReplyFailureKind.CHAT_ROOM_UNAVAILABLE to DirectChatStartFailureKind.ROOM,
            MessageReplyFailureKind.MEDIA_CAPTION_UNSUPPORTED to
                DirectChatStartFailureKind.VALIDATION,
            MessageReplyFailureKind.MEDIA_UNSUPPORTED to
                DirectChatStartFailureKind.MEDIA_UNSUPPORTED,
            MessageReplyFailureKind.MEDIA_TOO_LARGE to
                DirectChatStartFailureKind.MEDIA_TOO_LARGE,
            MessageReplyFailureKind.MEDIA_UNAVAILABLE to
                DirectChatStartFailureKind.MEDIA_UNAVAILABLE,
            MessageReplyFailureKind.MEDIA_AUTHORIZATION_REQUIRED to
                DirectChatStartFailureKind.SESSION,
            MessageReplyFailureKind.AUTHORIZATION_REQUIRED to DirectChatStartFailureKind.SESSION,
            MessageReplyFailureKind.RATE_LIMIT to DirectChatStartFailureKind.HTTP,
            MessageReplyFailureKind.API to DirectChatStartFailureKind.PROTOCOL,
            MessageReplyFailureKind.HTTP to DirectChatStartFailureKind.HTTP,
            MessageReplyFailureKind.TRANSPORT to DirectChatStartFailureKind.TRANSPORT,
            MessageReplyFailureKind.SESSION to DirectChatStartFailureKind.SESSION,
            MessageReplyFailureKind.OUTCOME_UNKNOWN to
                DirectChatStartFailureKind.OUTCOME_UNKNOWN,
            MessageReplyFailureKind.UNAVAILABLE to DirectChatStartFailureKind.UNAVAILABLE,
        )

        assertEquals(MessageReplyFailureKind.values().toSet(), expected.keys)
        expected.forEach { (replyKind, startKind) ->
            assertEquals(startKind, directChatStartFailureKindForMediaReply(replyKind))
        }
    }
}
