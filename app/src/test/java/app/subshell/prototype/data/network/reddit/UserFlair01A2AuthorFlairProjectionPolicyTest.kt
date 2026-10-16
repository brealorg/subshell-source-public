package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.network.SubshellNetworkJson
import org.junit.Assert.assertEquals
import org.junit.Test

class UserFlair01A2AuthorFlairProjectionPolicyTest {
    @Test
    fun postListingCarriesAuthorFlairIntoDomain() {
        val post = RedditListingDto(
            data = RedditListingDataDto(
                children = listOf(
                    RedditThingDto(
                        kind = "t3",
                        data = RedditPostDataDto(
                            id = "authorflairpost",
                            subreddit = "norge",
                            author = "someone",
                            title = "Post with community author flair",
                            authorFlairText = "  Innlandet  ",
                            authorFlairTextColor = " dark ",
                            authorFlairBackgroundColor = " #d6e4ff ",
                        ),
                    ),
                ),
            ),
        ).toNetworkPage(
            nowEpochSeconds = 10_000L,
        ).items.single()

        assertEquals(
            "Innlandet",
            post.authorFlairText,
        )
        assertEquals(
            "dark",
            post.authorFlairTextColor,
        )
        assertEquals(
            "#d6e4ff",
            post.authorFlairBackgroundColor,
        )
    }

    @Test
    fun commentListingCarriesAuthorFlairIntoDomain() {
        val comments = listOf(
            RedditCommentListingDto(),
            RedditCommentListingDto(
                data = RedditCommentListingDataDto(
                    children = listOf(
                        RedditCommentThingDto(
                            kind = "t1",
                            data = RedditCommentDataDto(
                                id = "authorflaircomment",
                                name = "t1_authorflaircomment",
                                author = "commenter",
                                body = "Comment with flair",
                                authorFlairText = "  Oslo  ",
                                authorFlairTextColor = " light ",
                                authorFlairBackgroundColor =
                                    " #123456 ",
                            ),
                        ),
                    ),
                ),
            ),
        ).toComments(
            json = SubshellNetworkJson.providerJson,
            nowEpochSeconds = 10_000L,
            limit = 10,
            maxDepth = 4,
        )

        val comment = comments.single()

        assertEquals(
            "Oslo",
            comment.authorFlairText,
        )
        assertEquals(
            "light",
            comment.authorFlairTextColor,
        )
        assertEquals(
            "#123456",
            comment.authorFlairBackgroundColor,
        )
    }
}
