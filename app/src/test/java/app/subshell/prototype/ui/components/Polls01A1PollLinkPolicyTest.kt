package app.subshell.prototype.ui.components

import app.subshell.prototype.model.Poll
import app.subshell.prototype.model.PollOption
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Polls01A1PollLinkPolicyTest {
    @Test
    fun livePollUsesCanonicalRedditPollPage() {
        val post = pollPost(id = "t3_abc123")

        assertEquals(
            "https://www.reddit.com/poll/abc123",
            redditPollWebUrl(post),
        )
    }

    @Test
    fun malformedOrSyntheticPostIdsDoNotCreatePollTargets() {
        assertNull(redditPollWebUrl(pollPost(id = "subshell-poll")))
        assertNull(redditPollWebUrl(pollPost(id = "t3_")))
        assertNull(redditPollWebUrl(pollPost(id = "https://reddit.com/poll/abc123")))
    }

    @Test
    fun authenticatedPollChoiceUsesExternalVoteHandoff() {
        assertTrue(
            shouldOpenRedditPollOptionExternally(
                localMutationsAllowed = false,
                canOpenOnReddit = true,
            ),
        )
        assertFalse(
            shouldOpenRedditPollOptionExternally(
                localMutationsAllowed = false,
                canOpenOnReddit = false,
            ),
        )
        assertFalse(
            shouldOpenRedditPollOptionExternally(
                localMutationsAllowed = true,
                canOpenOnReddit = true,
            ),
        )
    }

    private fun pollPost(id: String): Post = Post(
        id = id,
        community = "subshell",
        author = "pollster",
        age = "now",
        title = "Poll",
        score = 1,
        comments = 0,
        kind = PostKind.POLL,
        poll = Poll(
            options = listOf(
                PollOption("1", "A", 1),
                PollOption("2", "B", 1),
            ),
            totalVotes = 2,
            closesIn = "1h left",
        ),
    )
}
