package app.subshell.prototype.data.network.reddit

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Modmail04A1ArchivePolicyTest {
    @Test
    fun archivedConversationStateProjectsAuthoritatively() {
        val page = RedditModmailConversationsDto(
            conversationIds = listOf("abc123"),
            conversations = mapOf(
                "abc123" to RedditModmailConversationDto(
                    id = "abc123",
                    subject = "Archive fixture",
                    owner = RedditModmailSubredditDto(displayName = "subshell"),
                    state = 2,
                ),
            ),
        ).toConversationPage(limit = 50)

        assertTrue(page.items.single().archived)
    }

    @Test
    fun activeConversationStateDoesNotProjectArchived() {
        val page = RedditModmailConversationsDto(
            conversationIds = listOf("abc124"),
            conversations = mapOf(
                "abc124" to RedditModmailConversationDto(
                    id = "abc124",
                    subject = "Active fixture",
                    owner = RedditModmailSubredditDto(displayName = "subshell"),
                    state = 1,
                ),
            ),
        ).toConversationPage(limit = 50)

        assertFalse(page.items.single().archived)
    }

}
