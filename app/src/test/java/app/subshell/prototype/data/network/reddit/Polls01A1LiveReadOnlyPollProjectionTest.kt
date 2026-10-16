package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.model.PostKind
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Polls01A1LiveReadOnlyPollProjectionTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun liveRedditPollDataProjectsOptionsResultsAndCloseState() {
        val nowEpochSeconds = 1_800_000_000L
        val listing = json.decodeFromString<RedditListingDto>(
            """
            {
              "kind": "Listing",
              "data": {
                "children": [
                  {
                    "kind": "t3",
                    "data": {
                      "id": "abc123",
                      "name": "t3_abc123",
                      "subreddit": "subshell",
                      "title": "Which shell?",
                      "selftext": "Pick one.\n\n[View Poll](https://www.reddit.com/poll/abc123)",
                      "permalink": "/r/subshell/comments/abc123/which_shell/",
                      "poll_data": {
                        "prediction_status": null,
                        "user_selection": null,
                        "voting_end_timestamp": 1800172800000,
                        "total_vote_count": 10,
                        "options": [
                          {"id":"1001","text":"bash","vote_count":7},
                          {"id":"1002","text":"zsh","vote_count":3}
                        ]
                      }
                    }
                  }
                ]
              }
            }
            """.trimIndent(),
        )

        val post = listing.toNetworkPage(nowEpochSeconds).items.single()
        assertNotNull(post.poll)
        val poll = requireNotNull(post.poll)

        assertEquals(PostKind.POLL, post.kind)
        assertEquals("t3_abc123", post.id)
        assertEquals("Pick one.", post.body)
        assertEquals(2, poll.options.size)
        assertEquals("1001", poll.options[0].id)
        assertEquals("bash", poll.options[0].label)
        assertEquals(7, poll.options[0].votes)
        assertEquals(10, poll.totalVotes)
        assertEquals("2d left", poll.closesIn)
        assertTrue(poll.resultsVisible)
        assertFalse(poll.isClosed)
    }

    @Test
    fun livePollWithoutPerOptionCountsDoesNotFabricateResults() {
        val listing = json.decodeFromString<RedditListingDto>(
            """
            {
              "data": {
                "children": [
                  {
                    "kind": "t3",
                    "data": {
                      "id": "def456",
                      "subreddit": "subshell",
                      "title": "Results hidden",
                      "poll_data": {
                        "voting_end_timestamp": 1800003600000,
                        "total_vote_count": 42,
                        "options": [
                          {"id":"2001","text":"A"},
                          {"id":"2002","text":"B"}
                        ]
                      }
                    }
                  }
                ]
              }
            }
            """.trimIndent(),
        )

        val poll = requireNotNull(
            listing.toNetworkPage(nowEpochSeconds = 1_800_000_000L)
                .items.single().poll,
        )

        assertEquals(listOf(0, 0), poll.options.map { it.votes })
        assertEquals(42, poll.totalVotes)
        assertEquals("1h left", poll.closesIn)
        assertFalse(poll.resultsVisible)
        assertFalse(poll.isClosed)
    }

    @Test
    fun pollEndTimestampAcceptsLegacySecondsAndMarksClosedPolls() {
        val open = RedditPollDataDto(
            options = listOf(
                RedditPollOptionDto(id = "a", text = "A", voteCount = 2),
                RedditPollOptionDto(id = "b", text = "B", voteCount = 1),
            ),
            totalVoteCount = 3,
            votingEndTimestamp = 1_800_003_600L,
        )
        val closed = open.copy(votingEndTimestamp = 1_799_999_999L)

        val openPost = RedditListingDto(
            data = RedditListingDataDto(
                children = listOf(
                    RedditThingDto(
                        kind = "t3",
                        data = RedditPostDataDto(
                            id = "ghi789",
                            subreddit = "subshell",
                            title = "Open",
                            pollData = open,
                        ),
                    ),
                ),
            ),
        ).toNetworkPage(nowEpochSeconds = 1_800_000_000L).items.single()
        val closedPost = RedditListingDto(
            data = RedditListingDataDto(
                children = listOf(
                    RedditThingDto(
                        kind = "t3",
                        data = RedditPostDataDto(
                            id = "jkl012",
                            subreddit = "subshell",
                            title = "Closed",
                            pollData = closed,
                        ),
                    ),
                ),
            ),
        ).toNetworkPage(nowEpochSeconds = 1_800_000_000L).items.single()

        assertEquals("1h left", requireNotNull(openPost.poll).closesIn)
        assertFalse(requireNotNull(openPost.poll).isClosed)
        assertEquals("Closed", requireNotNull(closedPost.poll).closesIn)
        assertTrue(requireNotNull(closedPost.poll).isClosed)
    }

    @Test
    fun crosspostedPollKeepsNativePollProjection() {
        val sourcePoll = RedditPollDataDto(
            options = listOf(
                RedditPollOptionDto(id = "x", text = "X", voteCount = 8),
                RedditPollOptionDto(id = "y", text = "Y", voteCount = 5),
            ),
            totalVoteCount = 13,
            votingEndTimestamp = 1_800_086_400_000L,
        )
        val listing = RedditListingDto(
            data = RedditListingDataDto(
                children = listOf(
                    RedditThingDto(
                        kind = "t3",
                        data = RedditPostDataDto(
                            id = "mno345",
                            subreddit = "subshell",
                            title = "Crossposted poll",
                            crosspostParentList = listOf(
                                RedditCrosspostParentDto(
                                    id = "pqr678",
                                    name = "t3_pqr678",
                                    subreddit = "polls",
                                    title = "Source poll",
                                    permalink = "/r/polls/comments/pqr678/source_poll/",
                                    pollData = sourcePoll,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val post = listing.toNetworkPage(nowEpochSeconds = 1_800_000_000L).items.single()
        val crosspost = requireNotNull(post.crosspost)

        assertEquals(PostKind.CROSSPOST, post.kind)
        assertEquals(PostKind.POLL, crosspost.kind)
        assertEquals(13, requireNotNull(crosspost.poll).totalVotes)
        assertTrue(requireNotNull(crosspost.poll).resultsVisible)
    }

    @Test
    fun invalidProviderOptionDoesNotHideValidResults() {
        val pollData = RedditPollDataDto(
            options = listOf(
                RedditPollOptionDto(id = "", text = "broken", voteCount = null),
                RedditPollOptionDto(id = "1", text = "A", voteCount = 4),
                RedditPollOptionDto(id = "2", text = "B", voteCount = 6),
            ),
            totalVoteCount = 10,
            votingEndTimestamp = 1_800_003_600_000L,
        )
        val post = RedditListingDto(
            data = RedditListingDataDto(
                children = listOf(
                    RedditThingDto(
                        kind = "t3",
                        data = RedditPostDataDto(
                            id = "stu901",
                            subreddit = "subshell",
                            title = "Malformed extra option",
                            pollData = pollData,
                        ),
                    ),
                ),
            ),
        ).toNetworkPage(nowEpochSeconds = 1_800_000_000L).items.single()
        val poll = requireNotNull(post.poll)

        assertEquals(listOf("1", "2"), poll.options.map { it.id })
        assertEquals(listOf(4, 6), poll.options.map { it.votes })
        assertTrue(poll.resultsVisible)
    }

}
