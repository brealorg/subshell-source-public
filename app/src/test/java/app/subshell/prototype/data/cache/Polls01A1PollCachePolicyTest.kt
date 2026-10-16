package app.subshell.prototype.data.cache

import app.subshell.prototype.model.CrosspostPreview
import app.subshell.prototype.model.Poll
import app.subshell.prototype.model.PollOption
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Polls01A1PollCachePolicyTest {
    @Test
    fun livePollProjectionRoundTripsThroughExistingPostCachePayload() {
        val post = Post(
            id = "t3_poll",
            community = "subshell",
            author = "pollster",
            age = "3h",
            title = "Cached poll",
            score = 5,
            comments = 2,
            kind = PostKind.POLL,
            poll = Poll(
                options = listOf(
                    PollOption("a", "Alpha", 9),
                    PollOption("b", "Beta", 4),
                ),
                totalVotes = 13,
                closesIn = "4h left",
                resultsVisible = true,
                isClosed = false,
            ),
            permalink = "/r/subshell/comments/poll/cached_poll/",
        )

        val restored = SubshellCacheCodec.decodePost(SubshellCacheCodec.encodePost(post))
        val poll = requireNotNull(restored.poll)

        assertEquals(PostKind.POLL, restored.kind)
        assertEquals(13, poll.totalVotes)
        assertEquals(listOf(9, 4), poll.options.map { it.votes })
        assertEquals("4h left", poll.closesIn)
        assertTrue(poll.resultsVisible)
        assertFalse(poll.isClosed)
    }

    @Test
    fun crosspostedPollRoundTripsWithoutLosingPollPayload() {
        val post = Post(
            id = "t3_outer",
            community = "subshell",
            author = "crossposter",
            age = "1h",
            title = "Outer",
            score = 1,
            comments = 0,
            kind = PostKind.CROSSPOST,
            crosspost = CrosspostPreview(
                community = "polls",
                author = "pollster",
                title = "Inner poll",
                score = 10,
                id = "t3_inner",
                kind = PostKind.POLL,
                poll = Poll(
                    options = listOf(
                        PollOption("x", "X", 5),
                        PollOption("y", "Y", 5),
                    ),
                    totalVotes = 10,
                    closesIn = "Closed",
                    resultsVisible = true,
                    isClosed = true,
                ),
            ),
        )

        val restored = SubshellCacheCodec.decodePost(SubshellCacheCodec.encodePost(post))
        val poll = requireNotNull(requireNotNull(restored.crosspost).poll)

        assertEquals(PostKind.POLL, requireNotNull(restored.crosspost).kind)
        assertEquals(10, poll.totalVotes)
        assertTrue(poll.resultsVisible)
        assertTrue(poll.isClosed)
    }
}
