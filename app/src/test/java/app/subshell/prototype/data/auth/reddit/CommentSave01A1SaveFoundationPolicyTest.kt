package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.account.normalizeCommentSaveFullname
import app.subshell.prototype.data.account.normalizePostSaveFullname
import app.subshell.prototype.data.cache.SubshellCacheCodec
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.data.network.reddit.RedditCommentDataDto
import app.subshell.prototype.data.network.reddit.RedditCommentListingDataDto
import app.subshell.prototype.data.network.reddit.RedditCommentListingDto
import app.subshell.prototype.data.network.reddit.RedditCommentThingDto
import app.subshell.prototype.data.network.reddit.redditPostSaveFormRequest
import app.subshell.prototype.data.network.reddit.toComments
import app.subshell.prototype.model.Comment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentSave01A1SaveFoundationPolicyTest {
    @Test
    fun existingSaveTransportAcceptsCanonicalCommentFullnamesWithoutChangingPostDefault() {
        assertEquals("t3_abc123", normalizePostSaveFullname("AbC123"))
        assertEquals("t3_abc123", normalizePostSaveFullname("t3_AbC123"))
        assertEquals("t1_def456", normalizePostSaveFullname("t1_DeF456"))
        assertEquals("t1_def456", normalizeCommentSaveFullname("DeF456"))
        assertEquals("t1_def456", normalizeCommentSaveFullname("t1_DeF456"))
        assertEquals(
            listOf("id" to "t1_def456"),
            redditPostSaveFormRequest("t1_DeF456", true).fields,
        )
        assertEquals(
            listOf("api", "unsave"),
            redditPostSaveFormRequest("t1_DeF456", false).pathSegments,
        )
        assertThrows(IllegalArgumentException::class.java) {
            normalizeCommentSaveFullname("t3_post")
        }
    }

    @Test
    fun authenticatedCommentProjectionCarriesRedditSavedState() {
        // COMMENTSAVE01A1_REDDIT_COMMENTS_RESPONSE_FIXTURE_V2
        // Reddit /comments returns [post listing, comment listing]; toComments()
        // intentionally consumes the second listing. Keep the fixture canonical.
        val comments = listOf(
            RedditCommentListingDto(),
            RedditCommentListingDto(
                data = RedditCommentListingDataDto(
                    children = listOf(
                        RedditCommentThingDto(
                            kind = "t1",
                            data = RedditCommentDataDto(
                                id = "CommentOne",
                                name = "t1_CommentOne",
                                author = "commenter",
                                body = "Saved comment",
                                score = 7,
                                saved = true,
                            ),
                        ),
                        RedditCommentThingDto(
                            kind = "t1",
                            data = RedditCommentDataDto(
                                id = "CommentTwo",
                                name = "t1_CommentTwo",
                                author = "commenter",
                                body = "Unsaved comment",
                                score = 3,
                                saved = false,
                            ),
                        ),
                    ),
                ),
            ),
        ).toComments(
            json = SubshellNetworkJson.providerJson,
            nowEpochSeconds = 1_000L,
            limit = 10,
            maxDepth = 10,
        )

        assertEquals(2, comments.size)
        assertTrue(comments[0].saved)
        assertFalse(comments[1].saved)
    }

    @Test
    fun commentSavedStateRoundTripsThroughBackwardCompatibleCacheCodec() {
        val original = Comment(
            id = "t1_cached",
            author = "user",
            age = "now",
            body = "Cached saved comment",
            score = 4,
            depth = 0,
            accent = 1L,
            vote = 1,
            saved = true,
        )
        val decoded = SubshellCacheCodec.decodeComment(
            SubshellCacheCodec.encodeComment(original),
        )
        assertEquals(original, decoded)
        assertTrue(decoded.saved)
    }

    @Test
    fun existingDailyDriverSaveGrantNeedsNoOauthExpansion() {
        assertEquals(
            RedditOAuth.coreReadScopes + "save",
            RedditOAuth.livePostSaveScopes,
        )
        assertTrue("save" in RedditOAuth.dailyDriverScopes)
    }
}
