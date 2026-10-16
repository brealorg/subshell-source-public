package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.network.SubshellNetworkJson
import org.junit.Assert.assertEquals
import org.junit.Test

class UserFlair01A3AuthorRichTextProjectionPolicyTest {
    @Test
    fun postAuthorRichTextProjectsEmojiAndText() {
        val post = RedditListingDto(
            data = RedditListingDataDto(
                children = listOf(
                    RedditThingDto(
                        kind = "t3",
                        data = RedditPostDataDto(
                            id = "rich-flair-post",
                            subreddit = "motogp",
                            author = "rider",
                            title = "Rich flair post",
                            authorFlairText =
                                ":MarcMarquez: Marc Márquez",
                            authorFlairRichText = listOf(
                                RedditFlairRichTextDto(
                                    elementType = "emoji",
                                    emojiRef = ":MarcMarquez:",
                                    imageUrl =
                                        "https://emoji.redditmedia.com/marc.png",
                                ),
                                RedditFlairRichTextDto(
                                    elementType = "text",
                                    text = "Marc Márquez",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ).toNetworkPage(
            nowEpochSeconds = 10_000L,
        ).items.single()

        assertEquals(
            listOf("emoji", "text"),
            post.authorFlairRichText.map { it.elementType },
        )
        assertEquals(
            ":MarcMarquez:",
            post.authorFlairRichText.first().emojiRef,
        )
    }

    @Test
    fun commentAuthorRichTextProjectsEmojiAndText() {
        val comments = listOf(
            RedditCommentListingDto(),
            RedditCommentListingDto(
                data = RedditCommentListingDataDto(
                    children = listOf(
                        RedditCommentThingDto(
                            kind = "t1",
                            data = RedditCommentDataDto(
                                id = "rich-flair-comment",
                                name = "t1_rich-flair-comment",
                                author = "fan",
                                body = "Comment",
                                authorFlairText =
                                    ":Suzuki: Suzuki",
                                authorFlairRichText = listOf(
                                    RedditFlairRichTextDto(
                                        elementType = "emoji",
                                        emojiRef = ":Suzuki:",
                                        imageUrl =
                                            "https://emoji.redditmedia.com/suzuki.png",
                                    ),
                                    RedditFlairRichTextDto(
                                        elementType = "text",
                                        text = "Suzuki",
                                    ),
                                ),
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
            listOf("emoji", "text"),
            comment.authorFlairRichText.map { it.elementType },
        )
        assertEquals(
            "Suzuki",
            comment.authorFlairRichText.last().text,
        )
    }
}
