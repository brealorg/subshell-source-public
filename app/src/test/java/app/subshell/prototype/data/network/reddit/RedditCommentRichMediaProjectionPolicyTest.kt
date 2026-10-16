package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.model.RemoteCommentMediaKind
import app.subshell.prototype.model.projectCommentContent
import app.subshell.prototype.model.redditSourceMarkdown
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedditCommentRichMediaProjectionPolicyTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun nativeGifMetadataBecomesRenderableRemoteMediaWithoutRawAssetPlaceholder() {
        val comments = listOf(
            // Reddit /comments returns [post listing, comment listing]. toComments() reads index 1.
            RedditCommentListingDto(),
            RedditCommentListingDto(
                data = RedditCommentListingDataDto(
                    children = listOf(
                        RedditCommentThingDto(
                            kind = "t1",
                            data = RedditCommentDataDto(
                                id = "gifcomment",
                                name = "t1_gifcomment",
                                author = "subshell_user",
                                body = "Caption\n\n![gif](AbC123)",
                                mediaMetadata = mapOf(
                                    "abc123" to RedditMediaMetadataDto(
                                        status = "valid",
                                        mediaKind = "AnimatedImage",
                                        mimeType = "image/gif",
                                        source = RedditMediaImageSourceDto(
                                            gifUrl = "https://i.redd.it/abc123.gif",
                                            mp4Url = "https://preview.redd.it/abc123.gif?format=mp4",
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ).toComments(json = json, nowEpochSeconds = 10_000L, limit = 10, maxDepth = 4)

        val comment = comments.single()
        assertFalse(comment.body.contains("![gif](AbC123)"))
        assertTrue(comment.body.contains("https://i.redd.it/abc123.gif"))
        val projection = projectCommentContent(comment.body)
        assertEquals("Caption", projection.displayText)
        assertEquals(1, projection.media.size)
        assertEquals(RemoteCommentMediaKind.GIF, projection.media.single().kind)
        assertEquals("https://i.redd.it/abc123.gif", projection.media.single().uri)
    }

    @Test
    fun durableRedditGifUrlAlreadyInBodyIsNotDuplicatedByMetadataFallback() {
        val comments = listOf(
            // Reddit /comments returns [post listing, comment listing]. toComments() reads index 1.
            RedditCommentListingDto(),
            RedditCommentListingDto(
                data = RedditCommentListingDataDto(
                    children = listOf(
                        RedditCommentThingDto(
                            kind = "t1",
                            data = RedditCommentDataDto(
                                id = "durablegif",
                                body = "comment with a gif\n\nhttps://i.redd.it/1h1tlly742ch1.gif",
                                mediaMetadata = mapOf(
                                    "1h1tlly742ch1" to RedditMediaMetadataDto(
                                        status = "valid",
                                        mediaKind = "AnimatedImage",
                                        mimeType = "image/gif",
                                        source = RedditMediaImageSourceDto(
                                            gifUrl = "https://i.redd.it/1h1tlly742ch1.gif",
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ).toComments(json = json, nowEpochSeconds = 10_000L, limit = 10, maxDepth = 4)

        val comment = comments.single()
        assertEquals(
            1,
            Regex(Regex.escape("https://i.redd.it/1h1tlly742ch1.gif"))
                .findAll(comment.body)
                .count(),
        )
        assertEquals(RemoteCommentMediaKind.GIF, projectCommentContent(comment.body).media.single().kind)
    }

    @Test
    fun mediaOnlyCommentIsNotDroppedWhenRedditProvidesMediaMetadata() {
        val comments = listOf(
            // Reddit /comments returns [post listing, comment listing]. toComments() reads index 1.
            RedditCommentListingDto(),
            RedditCommentListingDto(
                data = RedditCommentListingDataDto(
                    children = listOf(
                        RedditCommentThingDto(
                            kind = "t1",
                            data = RedditCommentDataDto(
                                id = "imagecomment",
                                body = "![img](image123)",
                                mediaMetadata = mapOf(
                                    "image123" to RedditMediaMetadataDto(
                                        status = "valid",
                                        mediaKind = "Image",
                                        mimeType = "image/jpeg",
                                        source = RedditMediaImageSourceDto(
                                            url = "https://i.redd.it/image123.jpg",
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ).toComments(json = json, nowEpochSeconds = 10_000L, limit = 10, maxDepth = 4)

        val comment = comments.single()
        // ISSUE8_INLINE_REDDIT_MEDIA_V1: the placeholder stays (with its address).
        assertEquals("![img](image123)", comment.body.redditSourceMarkdown())
        assertEquals("", projectCommentContent(comment.body).displayText)
        assertEquals("https://i.redd.it/image123.jpg", projectCommentContent(comment.body).media.single().uri)
        assertEquals(RemoteCommentMediaKind.IMAGE, projectCommentContent(comment.body).media.single().kind)
    }
    @Test
    fun processingImageFallbackIsRemovedOnlyWhenMatchingAuthoritativeMetadataExists() {
        val comments = listOf(
            RedditCommentListingDto(),
            RedditCommentListingDto(
                data = RedditCommentListingDataDto(
                    children = listOf(
                        RedditCommentThingDto(
                            kind = "t1",
                            data = RedditCommentDataDto(
                                id = "processinggif",
                                body = "*Processing img 6oilw76g2slh1...*",
                                mediaMetadata = mapOf(
                                    "6oilw76g2slh1" to RedditMediaMetadataDto(
                                        status = "valid",
                                        mediaKind = "AnimatedImage",
                                        mimeType = "image/gif",
                                        source = RedditMediaImageSourceDto(
                                            gifUrl = "https://i.redd.it/6oilw76g2slh1.gif",
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ).toComments(json = json, nowEpochSeconds = 10_000L, limit = 10, maxDepth = 4)

        val comment = comments.single()
        assertFalse(comment.body.contains("Processing img"))
        // ISSUE8_INLINE_REDDIT_MEDIA_V1: the resolved line becomes a real
        // placeholder in its place, so an edit sends Reddit a valid one.
        assertEquals("![img](6oilw76g2slh1)", comment.body.redditSourceMarkdown())
        assertEquals("https://i.redd.it/6oilw76g2slh1.gif", projectCommentContent(comment.body).media.single().uri)
        assertEquals(RemoteCommentMediaKind.GIF, projectCommentContent(comment.body).media.single().kind)
    }

    @Test
    fun processingImageTextWithNonMatchingMetadataIsPreserved() {
        val dto = RedditCommentDataDto(
            body = "*Processing img keepme123...*",
            mediaMetadata = mapOf(
                "different123" to RedditMediaMetadataDto(
                    status = "valid",
                    mediaKind = "Image",
                    mimeType = "image/jpeg",
                    source = RedditMediaImageSourceDto(url = "https://i.redd.it/different123.jpg"),
                ),
            ),
        )
        val body = dto.bodyWithRedditMediaMetadata()
        assertTrue(body.contains("Processing img keepme123"))
        assertTrue(body.contains("https://i.redd.it/different123.jpg"))
    }

}
