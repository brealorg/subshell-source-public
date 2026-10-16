package app.subshell.prototype.ui

import app.subshell.prototype.model.Comment
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * #229: the mention being typed before the cursor is found after `@` or
 * `u/` at the start of a word; picking a name writes `u/name ` in its place;
 * the thread's people come in the order a reader means them; thread names
 * that start with the typed letters lead, Reddit's results follow from two
 * letters on, each name once.
 */
class Issue229MentionAutocompleteTest {
    @Test
    fun aMentionStartsAWordWithAtOrUSlash() {
        assertEquals(MentionQuery(3, 6, "bo"), mentionQueryAt("Hi @bo", 6))
        assertEquals(MentionQuery(3, 7, "bo"), mentionQueryAt("Hi u/bo", 7))
        assertEquals(MentionQuery(3, 7, "bo"), mentionQueryAt("Hi U/bo", 7))
        assertEquals(MentionQuery(0, 1, ""), mentionQueryAt("@", 1))
        assertEquals(MentionQuery(1, 4, "al"), mentionQueryAt("(@al", 4))
        // Only the text before the cursor counts.
        assertEquals(MentionQuery(0, 3, "bo"), mentionQueryAt("@bob is here", 3))
    }

    @Test
    fun anEmailAddressALinkOrAFinishedWordIsNotAMention() {
        assertNull(mentionQueryAt("mail me@bob", 11))
        assertNull(mentionQueryAt("see reddit.com/u/bob", 20))
        assertNull(mentionQueryAt("@bob ", 5))
        assertNull(mentionQueryAt("plain text", 10))
        assertNull(mentionQueryAt("@" + "a".repeat(21), 22))
    }

    @Test
    fun pickingANameWritesUSlashNameAndASpace() {
        val edit = applyMention("Hi @bo", MentionQuery(3, 6, "bo"), "bob")
        assertEquals("Hi u/bob ", edit.text)
        assertEquals(9, edit.selectionStart)
        assertEquals(9, edit.selectionEnd)

        // Text after the cursor stays, and an existing space is not doubled.
        val middle = applyMention("@al thanks", MentionQuery(0, 3, "al"), "alice")
        assertEquals("u/alice thanks", middle.text)
        assertEquals(8, middle.selectionStart)
    }

    @Test
    fun theThreadsPeopleComeInTheOrderAReaderMeansThem() {
        val post = Post("t3_p", "homelab", "op_author", "1h", "Title", score = 1, comments = 3, kind = PostKind.TEXT)
        val comments = listOf(
            comment("old_one", created = 100, children = listOf(comment("newest", created = 300))),
            comment("[deleted]", created = 250),
            comment("Middle", created = 200),
            comment("me", created = 400),
            comment("OP_AUTHOR", created = 500),
        )

        assertEquals(
            listOf("replied_to", "op_author", "newest", "Middle", "old_one"),
            threadMentionCandidates(post, comments, replyingTo = "replied_to", ownUsername = "Me"),
        )
    }

    @Test
    fun threadNamesThatStartWithTheLettersLeadAndRedditFollowsFromTwoLetters() {
        val thread = listOf("alice", "malia", "bob")

        assertEquals(listOf("alice", "malia", "bob"), mentionSuggestions("", thread, remote = listOf("zed")))
        assertEquals(listOf("alice", "malia"), mentionSuggestions("a", thread, remote = listOf("amy")))
        assertEquals(
            listOf("alice", "malia", "aliens"),
            mentionSuggestions("ali", thread, remote = listOf("ALICE", "aliens")),
        )
        assertEquals(2, mentionSuggestions("al", thread, remote = listOf("al1", "al2", "al3"), limit = 2).size)
    }

    private fun comment(author: String, created: Long, children: List<Comment> = emptyList()) = Comment(
        id = "t1_$author$created",
        author = author,
        age = "1h",
        body = "text",
        score = 1,
        depth = 0,
        accent = 0L,
        children = children,
        createdEpochSeconds = created,
    )
}
