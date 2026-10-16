package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.cache.SubshellCacheCodec
import app.subshell.prototype.model.PostKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Crosspost01ProjectionTest {
    @Test
    fun liveCrosspostParentListProjectsRichSourceIntoExistingCrosspostModel() {
        val page = RedditListingDto(
            data = RedditListingDataDto(
                children = listOf(
                    RedditThingDto(
                        kind = "t3",
                        data = RedditPostDataDto(
                            id = "wrapper",
                            name = "t3_wrapper",
                            subreddit = "subshell",
                            subredditNamePrefixed = "r/subshell",
                            author = "wrapper_author",
                            createdUtc = 1_700_000_000.0,
                            title = "Wrapper title",
                            score = 1,
                            numComments = 0,
                            postHint = "link",
                            domain = "reddit.com",
                            destinationUrl =
                                "https://www.reddit.com/r/source/comments/source/source_title/",
                            permalink =
                                "/r/subshell/comments/wrapper/wrapper_title/",
                            crosspostParent = "t3_source",
                            crosspostParentList = listOf(
                                RedditCrosspostParentDto(
                                    id = "source",
                                    name = "t3_source",
                                    subreddit = "source",
                                    subredditNamePrefixed = "r/source",
                                    author = "source_author",
                                    createdUtc = 1_699_900_000.0,
                                    title = "Source title",
                                    selftext = "Original **Markdown** body with enough context to preview.",
                                    score = 108,
                                    numComments = 135,
                                    linkFlairText = "Guide",
                                    permalink =
                                        "/r/source/comments/source/source_title/",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ).toNetworkPage(nowEpochSeconds = 1_700_000_600L)

        val post = page.items.single()
        val source = post.crosspost

        assertEquals("t3_wrapper", post.id)
        assertEquals(PostKind.CROSSPOST, post.kind)
        assertEquals("Wrapper title", post.title)
        assertEquals("subshell", post.community)
        assertEquals(1, post.score)
        assertEquals(0, post.comments)
        assertEquals("", post.mediaLabel)

        assertEquals("t3_source", source?.id)
        assertEquals("source", source?.community)
        assertEquals("source_author", source?.author)
        assertEquals("Source title", source?.title)
        assertEquals("Original **Markdown** body with enough context to preview.", source?.body)
        assertEquals(108, source?.score)
        assertEquals(135, source?.comments)
        assertEquals(PostKind.TEXT, source?.kind)
        assertEquals("Guide", source?.flair)
        assertEquals("/r/source/comments/source/source_title/", source?.permalink)
        assertEquals("1d", source?.age)

        assertEquals(
            post,
            SubshellCacheCodec.decodePost(SubshellCacheCodec.encodePost(post)),
        )
    }

    @Test
    fun sourceImageMediaUsesTheNormalRedditMediaProjection() {
        val page = RedditListingDto(
            data = RedditListingDataDto(
                children = listOf(
                    RedditThingDto(
                        kind = "t3",
                        data = RedditPostDataDto(
                            id = "wrapper",
                            name = "t3_wrapper",
                            subreddit = "pics",
                            author = "wrapper_author",
                            title = "Wrapper image crosspost",
                            crosspostParent = "t3_source",
                            crosspostParentList = listOf(
                                RedditCrosspostParentDto(
                                    id = "source",
                                    name = "t3_source",
                                    subreddit = "wallpapers",
                                    author = "source_author",
                                    title = "Source image",
                                    score = 500,
                                    numComments = 42,
                                    postHint = "image",
                                    domain = "i.redd.it",
                                    destinationUrl = "https://i.redd.it/source-image.jpg",
                                    permalink = "/r/wallpapers/comments/source/source_image/",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ).toNetworkPage(nowEpochSeconds = 1_700_000_600L)

        val source = page.items.single().crosspost

        assertEquals(PostKind.IMAGE, source?.kind)
        assertEquals("https://i.redd.it/source-image.jpg", source?.remoteImageUri)
        assertEquals("Image", source?.mediaLabel)
        assertEquals(42, source?.comments)
    }

    @Test
    fun incompleteParentMetadataFallsBackWithoutFabricatingCrosspostPreview() {
        val page = RedditListingDto(
            data = RedditListingDataDto(
                children = listOf(
                    RedditThingDto(
                        kind = "t3",
                        data = RedditPostDataDto(
                            id = "wrapper",
                            name = "t3_wrapper",
                            subreddit = "android",
                            author = "wrapper_author",
                            title = "Wrapper title",
                            postHint = "link",
                            domain = "reddit.com",
                            destinationUrl =
                                "https://www.reddit.com/r/source/comments/source/title/",
                            crosspostParent = "t3_source",
                            crosspostParentList = listOf(
                                RedditCrosspostParentDto(
                                    id = "source",
                                    name = "t3_source",
                                    subreddit = "source",
                                    author = "source_author",
                                    title = "",
                                    score = 55,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ).toNetworkPage(nowEpochSeconds = 1_700_000_600L)

        val post = page.items.single()

        assertEquals(PostKind.LINK, post.kind)
        assertNull(post.crosspost)
    }
}
