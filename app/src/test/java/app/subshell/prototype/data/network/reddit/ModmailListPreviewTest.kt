package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.network.SubshellNetworkJson
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * OCB-553: Reddit's GET api/mod/conversations keeps message bodies in a
 * top-level `messages` map referenced by each conversation's objIds (the
 * shape PRAW reads). The list read them from inside the conversation, so
 * every preview was empty and latestMessageId null.
 */
class ModmailListPreviewTest {
    @Test
    fun theListTakesPreviewsFromTheTopLevelMessages() {
        val page = decode(
            """{"conversationIds":["abc1","abc2"],
                "conversations":{
                  "abc1":{"id":"abc1","subject":"Ban appeal","owner":{"displayName":"kotlin"},
                    "objIds":[{"id":"m1","key":"messages"},{"id":"m2","key":"messages"}],
                    "lastUpdated":"2026-10-03T10:00:00.000000+00:00"},
                  "abc2":{"id":"abc2","subject":"Question","owner":{"displayName":"kotlin"},
                    "objIds":[{"id":"m3","key":"messages"}],
                    "lastUpdated":"2026-10-03T09:00:00.000000+00:00"}},
                "messages":{
                  "m1":{"id":"m1","bodyMarkdown":"First","date":"2026-10-03T08:00:00.000000+00:00"},
                  "m2":{"id":"m2","bodyMarkdown":"Latest reply","date":"2026-10-03T10:00:00.000000+00:00"},
                  "m3":{"id":"m3","bodyMarkdown":"How do I post?","date":"2026-10-03T09:00:00.000000+00:00"}}}""",
        )

        assertEquals(listOf("Latest reply", "How do I post?"), page.items.map { it.preview })
        assertEquals(listOf("m2", "m3"), page.items.map { it.latestMessageId })
    }

    @Test
    fun messagesInsideTheConversationStillWork() {
        val page = decode(
            """{"conversationIds":["abc1"],
                "conversations":{"abc1":{"id":"abc1","subject":"Hi","owner":{"displayName":"kotlin"},
                  "messages":{"m1":{"id":"m1","bodyMarkdown":"Nested","date":"2026-10-03T08:00:00.000000+00:00"}},
                  "lastUpdated":"2026-10-03T10:00:00.000000+00:00"}}}""",
        )

        assertEquals(listOf("Nested"), page.items.map { it.preview })
    }

    private fun decode(raw: String) = SubshellNetworkJson.providerJson
        .decodeFromString(RedditModmailConversationsDto.serializer(), raw)
        .toConversationPage(limit = 25)
}
