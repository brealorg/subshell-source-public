package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.network.reddit.RedditProfileCommentDataDto
import app.subshell.prototype.data.network.reddit.RedditProfileCommentThingDto
import app.subshell.prototype.data.network.reddit.RedditProfileCommentsListingDataDto
import app.subshell.prototype.data.network.reddit.RedditProfileCommentsListingDto
import app.subshell.prototype.data.network.reddit.redditProfileCommentsJsonRequest
import app.subshell.prototype.data.network.reddit.toProfileCommentsPage
import app.subshell.prototype.model.ThreadPlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Profile01a4aProfileCommentsPolicyTest {
    @Test
    fun profileHistoryScopeAddsOnlyHistoryToCoreRead() {
        assertEquals(
            RedditOAuth.coreReadScopes + "history",
            RedditOAuth.liveProfileHistoryScopes,
        )

        assertTrue(
            RedditOAuth.dailyDriverScopes.containsAll(
                RedditOAuth.liveProfileHistoryScopes,
            ),
        )

        assertFalse(
            RedditOAuth.coreReadScopes.contains("history"),
        )

        assertFalse(
            RedditOAuth.liveProfileHistoryScopes.contains("save"),
        )

        assertFalse(
            RedditOAuth.liveProfileHistoryScopes.contains("vote"),
        )
    }

    @Test
    fun profileCommentCacheIdentityIsAccountAndUsernameScoped() {
        assertEquals(
            "account|reddit:test:t2_one|profile-comments|brealorg",
            redditProfileCommentsCacheKey(
                accountStableId = "reddit:test:t2_one",
                username = "u/BRealOrg",
            ),
        )

        assertTrue(
            redditProfileCommentsCacheKey(
                accountStableId = "reddit:test:t2_one",
                username = "brealorg",
            ) !=
                redditProfileCommentsCacheKey(
                    accountStableId = "reddit:test:t2_two",
                    username = "brealorg",
                ),
        )
    }

    @Test
    fun commentsListingUsesCanonicalNewHistoryEndpointAndOpaqueCursor() {
        val request =
            redditProfileCommentsJsonRequest(
                username = "u/BRealOrg",
                cursor = "t1_afterCursor",
                limit = 25,
            )

        assertEquals(
            listOf(
                "user",
                "BRealOrg",
                "comments",
            ),
            request.pathSegments,
        )

        assertEquals(
            listOf(
                "raw_json" to "1",
                "sort" to "new",
                "limit" to "25",
                "after" to "t1_afterCursor",
            ),
            request.queryParameters,
        )
    }

    @Test
    fun flatT1ListingMapsToExistingProfileCommentProjection() {
        val listing =
            RedditProfileCommentsListingDto(
                data =
                    RedditProfileCommentsListingDataDto(
                        after = "t1_next",
                        children =
                            listOf(
                                RedditProfileCommentThingDto(
                                    kind = "t1",
                                    data =
                                        RedditProfileCommentDataDto(
                                            id = "AbC123",
                                            name = "t1_AbC123",
                                            author = "brealorg",
                                            body = "Profile comment body",
                                            score = 42L,
                                            createdUtc = 900.0,
                                            linkId = "t3_Post456",
                                        ),
                                ),
                                RedditProfileCommentThingDto(
                                    kind = "t3",
                                    data =
                                        RedditProfileCommentDataDto(
                                            id = "ignored",
                                            body = "not a comment",
                                            linkId = "t3_post",
                                        ),
                                ),
                            ),
                    ),
            )

        val page =
            listing.toProfileCommentsPage(
                nowEpochSeconds = 1_000L,
                limit = 25,
            )

        assertEquals(
            "t1_next",
            page.nextCursor,
        )

        assertEquals(
            1,
            page.items.size,
        )

        val comment = page.items.single()

        assertEquals(
            "t1_abc123",
            comment.id,
        )

        assertEquals(
            "brealorg",
            comment.author,
        )

        assertEquals(
            "post456",
            comment.postId,
        )

        assertEquals(
            "Profile comment body",
            comment.body,
        )

        assertEquals(
            42,
            comment.score,
        )

        assertEquals(
            ThreadPlacement.TOP,
            comment.placement,
        )
    }
}
