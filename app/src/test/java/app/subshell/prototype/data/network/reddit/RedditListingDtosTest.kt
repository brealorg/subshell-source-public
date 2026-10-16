package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.model.PostKind
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class RedditListingDtosTest {
    @Test
    fun listingCommunityIsStoredWithoutDisplayPrefix() {
        val listing = RedditListingDto(
            data = RedditListingDataDto(
                children = listOf(
                    RedditThingDto(
                        kind = "t3",
                        data = RedditPostDataDto(
                            id = "post-1",
                            subreddit = "subshell",
                            subredditNamePrefixed = "r/subshell",
                            title = "Identity normalization",
                        ),
                    ),
                ),
            ),
        )

        val post = listing.toNetworkPage(nowEpochSeconds = 0L).items.single()

        assertEquals("subshell", post.community)
        assertEquals("Volvo", "/r/Volvo".normalizedRedditCommunityName())
    }

    @Test
    fun redditVideoPrefersAdaptiveManifestWithAudioOverSilentFallback() {
        val listing = RedditListingDto(
            data = RedditListingDataDto(
                children = listOf(
                    RedditThingDto(
                        kind = "t3",
                        data = RedditPostDataDto(
                            id = "video-1",
                            subreddit = "subshell",
                            title = "Adaptive Reddit video",
                            isVideo = true,
                            preview = RedditPreviewDto(
                                images = listOf(
                                    RedditPreviewImageDto(
                                        source = RedditPreviewImageSourceDto(
                                            url = "https://preview.redd.it/example.png",
                                            width = 1280,
                                            height = 720,
                                        ),
                                    ),
                                ),
                            ),
                            secureMedia = RedditSecureMediaDto(
                                redditVideo = RedditVideoDto(
                                    dashUrl = "https://v.redd.it/example/DASHPlaylist.mpd?source=fallback",
                                    hlsUrl = "https://v.redd.it/example/HLSPlaylist.m3u8",
                                    fallbackUrl = "https://v.redd.it/example/DASH_720.mp4",
                                    width = 720,
                                    height = 1280,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val post = listing.toNetworkPage(nowEpochSeconds = 0L).items.single()

        assertEquals(
            "https://v.redd.it/example/DASHPlaylist.mpd?source=fallback",
            post.remoteVideoUri,
        )
        assertEquals(
            "https://v.redd.it/example/HLSPlaylist.m3u8",
            post.remoteVideoHlsUri,
        )
        assertEquals(
            "https://v.redd.it/example/DASH_720.mp4",
            post.remoteVideoFallbackUri,
        )
        assertEquals(1280, post.videoWidth)
        assertEquals(720, post.videoHeight)
    }

    @Test
    fun externalLinkWithBodyRemainsLinkAndKeepsRedditPreview() {
        val destination = "https://arstechnica.com/gadgets/story/"
        val preview = "https://external-preview.redd.it/article.jpg"
        val listing = RedditListingDto(
            data = RedditListingDataDto(
                children = listOf(
                    RedditThingDto(
                        kind = "t3",
                        data = RedditPostDataDto(
                            id = "link-body-1",
                            subreddit = "subshell",
                            title = "Link with context",
                            selftext = "Body text belongs to the link post.",
                            postHint = "link",
                            domain = "arstechnica.com",
                            destinationUrl = destination,
                            preview = RedditPreviewDto(
                                images = listOf(
                                    RedditPreviewImageDto(
                                        source = RedditPreviewImageSourceDto(
                                            url = preview,
                                            width = 1200,
                                            height = 675,
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val post = listing.toNetworkPage(nowEpochSeconds = 0L).items.single()

        assertEquals(PostKind.LINK, post.kind)
        assertEquals("Body text belongs to the link post.", post.body)
        assertEquals(destination, post.mediaLabel)
        assertEquals(preview, post.remoteImageUri)
        assertEquals("arstechnica.com", post.domain)
    }

    @Test
    fun selfPostWithBodyAndNoExternalDestinationRemainsText() {
        val listing = RedditListingDto(
            data = RedditListingDataDto(
                children = listOf(
                    RedditThingDto(
                        kind = "t3",
                        data = RedditPostDataDto(
                            id = "self-1",
                            subreddit = "subshell",
                            title = "Text post",
                            selftext = "Body",
                        ),
                    ),
                ),
            ),
        )

        val post = listing.toNetworkPage(nowEpochSeconds = 0L).items.single()

        assertEquals(PostKind.TEXT, post.kind)
    }
    @Test
    fun redditVideoGifKeepsVideoPlaybackKindWithGifIdentity() {
        val listing = RedditListingDto(
            data = RedditListingDataDto(
                children = listOf(
                    RedditThingDto(
                        kind = "t3",
                        data = RedditPostDataDto(
                            id = "videogif1",
                            subreddit = "subshell",
                            title = "Native Reddit GIF",
                            isVideo = true,
                            preview = RedditPreviewDto(
                                images = listOf(
                                    RedditPreviewImageDto(
                                        source = RedditPreviewImageSourceDto(
                                            url = "https://preview.redd.it/videogif.jpg",
                                            width = 720,
                                            height = 1280,
                                        ),
                                    ),
                                ),
                            ),
                            secureMedia = RedditSecureMediaDto(
                                redditVideo = RedditVideoDto(
                                    dashUrl = "https://v.redd.it/videogif/DASHPlaylist.mpd",
                                    fallbackUrl = "https://v.redd.it/videogif/DASH_720.mp4",
                                    isGif = true,
                                    width = 720,
                                    height = 1280,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val post = listing.toNetworkPage(nowEpochSeconds = 0L).items.single()

        assertEquals(app.subshell.prototype.model.PostKind.VIDEO, post.kind)
        assertEquals("GIF", post.mediaLabel)
        assertEquals(
            "https://v.redd.it/videogif/DASHPlaylist.mpd",
            post.remoteVideoUri,
        )
        assertEquals(720, post.videoWidth)
        assertEquals(1280, post.videoHeight)
    }

    @Test
    fun redditVideoGifReadbackAcceptsPreviewSignalAndKeepsVideoPlaybackKind() {
        val listing = RedditListingDto(
            data = RedditListingDataDto(
                children = listOf(
                    RedditThingDto(
                        kind = "t3",
                        data = RedditPostDataDto(
                            id = "previewgif1",
                            subreddit = "subshell",
                            title = "Preview GIF signal",
                            isVideo = true,
                            preview = RedditPreviewDto(
                                images = listOf(
                                    RedditPreviewImageDto(
                                        source = RedditPreviewImageSourceDto(
                                            url = "https://preview.redd.it/previewgif.jpg",
                                            width = 720,
                                            height = 1280,
                                        ),
                                    ),
                                ),
                                redditVideoPreview = RedditVideoDto(
                                    isGif = true,
                                    width = 720,
                                    height = 1280,
                                ),
                            ),
                            secureMedia = RedditSecureMediaDto(
                                redditVideo = RedditVideoDto(
                                    dashUrl = "https://v.redd.it/previewgif/DASHPlaylist.mpd",
                                    fallbackUrl = "https://v.redd.it/previewgif/DASH_720.mp4",
                                    isGif = false,
                                    width = 720,
                                    height = 1280,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val post = listing.toNetworkPage(nowEpochSeconds = 0L).items.single()

        assertEquals(app.subshell.prototype.model.PostKind.VIDEO, post.kind)
        assertEquals("GIF", post.mediaLabel)
        assertEquals(
            "https://v.redd.it/previewgif/DASHPlaylist.mpd",
            post.remoteVideoUri,
        )
    }

    @Test
    fun normalRedditVideoUsesProviderNeutralVideoLabel() {
        val listing = RedditListingDto(
            data = RedditListingDataDto(
                children = listOf(
                    RedditThingDto(
                        kind = "t3",
                        data = RedditPostDataDto(
                            id = "video2",
                            subreddit = "subshell",
                            title = "Normal Reddit video",
                            isVideo = true,
                            secureMedia = RedditSecureMediaDto(
                                redditVideo = RedditVideoDto(
                                    dashUrl = "https://v.redd.it/video2/DASHPlaylist.mpd",
                                    isGif = false,
                                    width = 1280,
                                    height = 720,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val post = listing.toNetworkPage(nowEpochSeconds = 0L).items.single()

        assertEquals(app.subshell.prototype.model.PostKind.VIDEO, post.kind)
        assertEquals("Video", post.mediaLabel)
    }

    // Issue #42: a title-only self post has an empty selftext and no
    // destination; it must not fall through to a link card of "self.X".
    @Test
    fun titleOnlySelfPostIsTextNotALinkToItsSelfDomain() {
        val json = Json { ignoreUnknownKeys = true }
        val listing = json.decodeFromString<RedditListingDto>(
            """
            {"data":{"children":[{"kind":"t3","data":{
              "id":"1abcde","name":"t3_1abcde","subreddit":"AskReddit",
              "subreddit_name_prefixed":"r/AskReddit",
              "title":"What's a perfect real-life example of this?",
              "selftext":"","is_self":true,"domain":"self.AskReddit",
              "url":"https://www.reddit.com/r/AskReddit/comments/1abcde/x/",
              "permalink":"/r/AskReddit/comments/1abcde/x/"
            }}]}}
            """.trimIndent(),
        )

        val post = listing.toNetworkPage(nowEpochSeconds = 0L).items.single()

        assertEquals(PostKind.TEXT, post.kind)
        assertEquals("", post.body)
        assertEquals("", post.mediaLabel)
        assertEquals(null, post.externalUrl)
    }

    @Test
    fun aNonSelfPostWithoutBodyOrDestinationStillFallsBackToLink() {
        val post = RedditListingDto(
            data = RedditListingDataDto(
                children = listOf(
                    RedditThingDto(
                        kind = "t3",
                        data = RedditPostDataDto(
                            id = "link-2",
                            subreddit = "subshell",
                            title = "No body, not self",
                            isSelf = false,
                        ),
                    ),
                ),
            ),
        ).toNetworkPage(nowEpochSeconds = 0L).items.single()

        assertEquals(PostKind.LINK, post.kind)
    }
}
