package app.subshell.prototype.data.network

import app.subshell.prototype.data.network.reddit.RedditPreviewImageVariantDto
import app.subshell.prototype.data.network.reddit.RedditPreviewImageVariantsDto

import app.subshell.prototype.data.RepositoryPageRequest
import app.subshell.prototype.data.cache.SubshellCacheCodec
import app.subshell.prototype.data.COMMENTS02_MORE_BATCH_SIZE
import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.data.RedditListingTimePeriod
import app.subshell.prototype.data.network.reddit.RedditListingDataDto
import app.subshell.prototype.data.network.reddit.RedditListingDto
import app.subshell.prototype.data.network.reddit.RedditListingNetworkDataSource
import app.subshell.prototype.data.network.reddit.RedditListingSort
import app.subshell.prototype.data.network.reddit.RedditCommunityAboutDataDto
import app.subshell.prototype.data.network.reddit.RedditCommunityAboutDto
import app.subshell.prototype.data.network.reddit.RedditGalleryDataDto
import app.subshell.prototype.data.network.reddit.RedditGalleryItemDto
import app.subshell.prototype.data.network.reddit.RedditMediaImageSourceDto
import app.subshell.prototype.data.network.reddit.RedditMediaMetadataDto
import app.subshell.prototype.data.network.reddit.RedditPostDataDto
import app.subshell.prototype.data.network.reddit.RedditPreviewDto
import app.subshell.prototype.data.network.reddit.RedditPreviewImageDto
import app.subshell.prototype.data.network.reddit.RedditPreviewImageSourceDto
import app.subshell.prototype.data.network.reddit.RedditSecureMediaDto
import app.subshell.prototype.data.network.reddit.RedditVideoDto
import app.subshell.prototype.data.network.reddit.RedditThingDto
import app.subshell.prototype.data.network.reddit.RedditUserAboutDataDto
import app.subshell.prototype.data.network.reddit.RedditUserAboutDto
import app.subshell.prototype.data.network.reddit.RedditUserProfileSubredditDto
import app.subshell.prototype.data.network.reddit.redditRelativeAgeLabel
import app.subshell.prototype.data.network.reddit.redditSearchJsonRequest
import app.subshell.prototype.data.network.reddit.redditListingJsonRequest
import app.subshell.prototype.data.network.reddit.RedditCommentDataDto
import app.subshell.prototype.data.network.reddit.RedditCommentListingDataDto
import app.subshell.prototype.data.network.reddit.RedditCommentListingDto
import app.subshell.prototype.data.network.reddit.RedditCommentNetworkDataSource
import app.subshell.prototype.data.network.reddit.RedditCommentThingDto
import app.subshell.prototype.data.network.reddit.redditCommentsJsonRequest
import app.subshell.prototype.data.network.reddit.redditMoreChildrenJsonRequest
import app.subshell.prototype.data.network.reddit.mergeExpandedComments
import app.subshell.prototype.data.network.reddit.toNetworkPage
import app.subshell.prototype.data.network.reddit.toComments
import app.subshell.prototype.data.network.reddit.toCommunity
import app.subshell.prototype.data.network.reddit.toUserFixture
import app.subshell.prototype.model.Community
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.model.Comment
import app.subshell.prototype.model.UserFixture
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkPolicyTest {
    @Test
    fun redditListingPreservesVisibleMetadataThroughCacheRoundTrip() {
        val listing = RedditListingDto(
            data = RedditListingDataDto(
                after = "t3_feed04_next",
                children = listOf(
                    RedditThingDto(
                        kind = "t3",
                        data = RedditPostDataDto(
                            id = "feed04",
                            name = "t3_feed04",
                            subredditNamePrefixed = "r/subshell",
                            author = "listing-author",
                            createdUtc = 9_100.0,
                            title = "Listing fidelity",
                            selftext = "Every visible field survives.",
                            score = 12_345,
                            numComments = 678,
                            isVideo = true,
                            domain = "v.redd.it",
                            preview = RedditPreviewDto(
                                images = listOf(
                                    RedditPreviewImageDto(
                                        source = RedditPreviewImageSourceDto(
                                            url = "https://preview.redd.it/feed04.jpg?width=1080&amp;format=pjpg",
                                            width = 1_080,
                                            height = 1_920,
                                        ),
                                    ),
                                ),
                            ),
                            secureMedia = RedditSecureMediaDto(
                                redditVideo = RedditVideoDto(
                                    fallbackUrl = "https://v.redd.it/feed04/DASH_720.mp4?source=fallback",
                                    width = 1_080,
                                    height = 1_920,
                                ),
                            ),
                            linkFlairText = "DEV",
                            stickied = true,
                            distinguished = "moderator",
                            spoiler = true,
                            over18 = true,
                            saved = true,
                            likes = false,
                        ),
                    ),
                ),
            ),
        )

        val page = listing.toNetworkPage(nowEpochSeconds = 10_000L)
        val mapped = page.items.single()
        val restored = SubshellCacheCodec.decodePost(
            SubshellCacheCodec.encodePost(mapped),
        )

        assertEquals("t3_feed04_next", page.nextCursor)
        assertEquals(mapped, restored)
        assertEquals("subshell", restored.community)
        assertEquals("listing-author", restored.author)
        assertEquals("15m", restored.age)
        assertEquals("Listing fidelity", restored.title)
        assertEquals("Every visible field survives.", restored.body)
        assertEquals(12_345, restored.score)
        assertEquals(678, restored.comments)
        assertEquals(PostKind.VIDEO, restored.kind)
        assertEquals("Video", restored.mediaLabel)
        assertEquals("https://v.redd.it/feed04/DASH_720.mp4?source=fallback", restored.remoteVideoUri)
        assertEquals("https://preview.redd.it/feed04.jpg?width=1080&format=pjpg", restored.remoteImageUri)
        assertEquals("v.redd.it", restored.domain)
        assertEquals("DEV", restored.flair)
        assertTrue(restored.pinned)
        assertTrue(restored.moderatorPost)
        assertTrue(restored.spoiler)
        assertTrue(restored.nsfw)
        assertTrue(restored.saved)
        assertEquals(-1, restored.vote)
        assertEquals(1_080, restored.videoWidth)
        assertEquals(1_920, restored.videoHeight)
    }

    @Test
    fun endpointRejectsCleartextCredentialsQueryAndFragment() {
        assertThrows(IllegalArgumentException::class.java) {
            NetworkEndpoint.https("http://example.test/")
        }
        assertThrows(IllegalArgumentException::class.java) {
            NetworkEndpoint.https("https://user:secret@example.test/")
        }
        assertThrows(IllegalArgumentException::class.java) {
            NetworkEndpoint.https("https://example.test/?source=test")
        }
        assertThrows(IllegalArgumentException::class.java) {
            NetworkEndpoint.https("https://example.test/#fragment")
        }
    }

    @Test
    fun endpointEncodesPathAndQuerySegmentsWithoutStringConcatenation() {
        val endpoint = NetworkEndpoint.https("https://example.test/api/")
        val url = endpoint.url(
            pathSegments = listOf("r", "android dev", "hot"),
            queryParameters = listOf("after" to "t3/a+b"),
        )
        assertEquals("/api/r/android%20dev/hot", url.encodedPath)
        assertEquals("t3/a+b", url.queryParameter("after"))
    }

    @Test
    fun redditListingMapperUsesDtosAndIgnoresNonPostChildren() {
        val listing = RedditListingDto(
            data = RedditListingDataDto(
                after = "t3_next",
                children = listOf(
                    RedditThingDto(
                        kind = "t3",
                        data = RedditPostDataDto(
                            id = "abc",
                            name = "t3_abc",
                            subredditNamePrefixed = "r/android",
                            author = "subshell-user",
                            createdUtc = 9_100.0,
                            title = "A post",
                            selftext = "Body",
                            score = 42,
                            numComments = 7,
                            saved = true,
                            likes = true,
                        ),
                    ),
                    RedditThingDto(kind = "t1"),
                ),
            ),
        )

        val page = listing.toNetworkPage(nowEpochSeconds = 10_000L)
        assertEquals("t3_next", page.nextCursor)
        assertEquals(1, page.items.size)
        assertEquals("t3_abc", page.items.single().id)
        assertEquals(PostKind.TEXT, page.items.single().kind)
        assertEquals("15m", page.items.single().age)
        assertTrue(page.items.single().saved)
        assertEquals(1, page.items.single().vote)
    }

    @Test
    fun redditRelativeAgesStayCompactForOldPostsAndComments() {
        val now = 200_000_000L

        assertEquals("29d", redditRelativeAgeLabel(now, now - 29L * 86_400L))
        assertEquals("3mo", redditRelativeAgeLabel(now, now - 90L * 86_400L))
        assertEquals("5y", redditRelativeAgeLabel(now, now - 5L * 31_536_000L))
        assertEquals("—", redditRelativeAgeLabel(now, null))
    }

    @Test
    fun redditMediaMapperUsesStructuredHttpsValidation() {
        fun mappedPost(fallbackUrl: String) =
            RedditListingDto(
                data = RedditListingDataDto(
                    children = listOf(
                        RedditThingDto(
                            kind = "t3",
                            data = RedditPostDataDto(
                                id = "media",
                                title = "Media post",
                                secureMedia = RedditSecureMediaDto(
                                    redditVideo = RedditVideoDto(
                                        fallbackUrl = fallbackUrl,
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ).toNetworkPage(nowEpochSeconds = 10_000L).items.single()

        val valid = mappedPost("https://cdn.example.test/video.mp4?token=abc")
        assertEquals(PostKind.VIDEO, valid.kind)
        assertEquals(
            "https://cdn.example.test/video.mp4?token=abc",
            valid.remoteVideoUri,
        )

        listOf(
            "http://cdn.example.test/video.mp4",
            "https://",
            "https://user:secret@cdn.example.test/video.mp4",
        ).forEach { rejected ->
            val mapped = mappedPost(rejected)
            assertEquals(PostKind.LINK, mapped.kind)
            assertEquals(null, mapped.remoteVideoUri)
        }
    }

    @Test
    fun redditImageMapperPreservesDirectAndPreviewHttpsUrls() {
        fun mappedPost(data: RedditPostDataDto) =
            RedditListingDto(
                data = RedditListingDataDto(
                    children = listOf(RedditThingDto(kind = "t3", data = data)),
                ),
            ).toNetworkPage(nowEpochSeconds = 10_000L).items.single()

        val direct = mappedPost(
            RedditPostDataDto(
                id = "direct-image",
                title = "Direct image",
                postHint = "image",
                domain = "i.redd.it",
                destinationUrl = "https://i.redd.it/direct.png",
            ),
        )
        assertEquals(PostKind.IMAGE, direct.kind)
        assertEquals("https://i.redd.it/direct.png", direct.remoteImageUri)

        val preview = mappedPost(
            RedditPostDataDto(
                id = "link-preview",
                title = "Link preview",
                postHint = "link",
                destinationUrl = "https://example.test/story",
                preview = RedditPreviewDto(
                    images = listOf(
                        RedditPreviewImageDto(
                            source = RedditPreviewImageSourceDto(
                                url = "https://preview.redd.it/image.jpg?width=1080&amp;format=pjpg",
                            ),
                        ),
                    ),
                ),
            ),
        )
        assertEquals(PostKind.LINK, preview.kind)
        assertEquals(
            "https://preview.redd.it/image.jpg?width=1080&format=pjpg",
            preview.remoteImageUri,
        )

        listOf(
            "http://i.redd.it/image.png",
            "https://user:secret@i.redd.it/image.png",
        ).forEach { rejected ->
            val mapped = mappedPost(
                RedditPostDataDto(
                    id = rejected,
                    title = "Rejected image",
                    postHint = "image",
                    destinationUrl = rejected,
                ),
            )
            assertEquals(PostKind.LINK, mapped.kind)
            assertEquals(null, mapped.remoteImageUri)
        }
    }


    @Test
    fun issue1TransformedPreviewGifPrefersRedditMp4Variant() {
        val transformed =
            "https://preview.redd.it/" +
                "70-years-of-demographic-change-population-mortality-and-" +
                "v0-o06lbnp8bxjh1.gif?" +
                "crop=smart&amp;format=png8&amp;s=2ea421fe0130a5cd95cfec0a887722a30f17fae7&amp;width=640"
        val mp4 =
            "https://preview.redd.it/" +
                "70-years-of-demographic-change-population-mortality-and-" +
                "v0-o06lbnp8bxjh1.gif?" +
                "format=mp4&s=issue1"

        val mapped = RedditListingDto(
            data = RedditListingDataDto(
                children = listOf(
                    RedditThingDto(
                        kind = "t3",
                        data = RedditPostDataDto(
                            id = "1vqqofh",
                            title = "70 years of demographic change",
                            postHint = "image",
                            domain = "preview.redd.it",
                            destinationUrl = transformed,
                            preview = RedditPreviewDto(
                                images = listOf(
                                    RedditPreviewImageDto(
                                        source = RedditPreviewImageSourceDto(
                                            url = transformed,
                                            width = 640,
                                            height = 360,
                                        ),
                                        variants = RedditPreviewImageVariantsDto(
                                            mp4 = RedditPreviewImageVariantDto(
                                                source = RedditPreviewImageSourceDto(
                                                    url = mp4,
                                                    width = 640,
                                                    height = 360,
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ).toNetworkPage(nowEpochSeconds = 10_000L).items.single()

        assertEquals(PostKind.VIDEO, mapped.kind)
        assertEquals("GIF", mapped.mediaLabel)
        assertEquals(mp4, mapped.remoteVideoUri)
        assertEquals(transformed.replace("&amp;", "&"), mapped.remoteImageUri)
    }

    @Test
    fun redditGifMapperUsesOriginalAnimatedAssetAndGifIdentity() {
        val mapped = RedditListingDto(
            data = RedditListingDataDto(
                children = listOf(
                    RedditThingDto(
                        kind = "t3",
                        data = RedditPostDataDto(
                            id = "native-gif",
                            title = "Native GIF",
                            postHint = "image",
                            domain = "i.redd.it",
                            destinationUrl = "https://i.redd.it/native.gif?source=subshell",
                        ),
                    ),
                ),
            ),
        ).toNetworkPage(nowEpochSeconds = 10_000L).items.single()

        assertEquals(PostKind.GIF, mapped.kind)
        assertEquals("GIF", mapped.mediaLabel)
        assertEquals(
            "https://i.redd.it/native.gif?source=subshell",
            mapped.remoteImageUri,
        )
    }

    @Test
    fun redditGalleryMapperKeepsApiOrderAndWinsOverSelfText() {
        val post = RedditListingDto(
            data = RedditListingDataDto(
                children = listOf(
                    RedditThingDto(
                        kind = "t3",
                        data = RedditPostDataDto(
                            id = "rich-gallery",
                            title = "Gallery with write-up",
                            selftext = "A long installation write-up.",
                            isGallery = true,
                            galleryData = RedditGalleryDataDto(
                                items = listOf(
                                    RedditGalleryItemDto(
                                        mediaId = "first",
                                        caption = "First caption",
                                        outboundUrl = "https://example.com/first?x=1&amp;y=2",
                                    ),
                                    RedditGalleryItemDto(
                                        mediaId = "second",
                                        caption = "Second caption",
                                    ),
                                    RedditGalleryItemDto(mediaId = "rejected"),
                                ),
                            ),
                            mediaMetadata = mapOf(
                                "second" to RedditMediaMetadataDto(
                                    status = "valid",
                                    mediaKind = "Image",
                                    mimeType = "image/jpeg",
                                    source = RedditMediaImageSourceDto(
                                        url = "https://preview.redd.it/second.jpg?width=2048&amp;format=pjpg",
                                    ),
                                ),
                                "first" to RedditMediaMetadataDto(
                                    status = "valid",
                                    mediaKind = "Image",
                                    mimeType = "image/jpeg",
                                    source = RedditMediaImageSourceDto(
                                        url = "https://preview.redd.it/first.jpg",
                                    ),
                                ),
                                "rejected" to RedditMediaMetadataDto(
                                    status = "valid",
                                    mediaKind = "Image",
                                    mimeType = "image/jpeg",
                                    source = RedditMediaImageSourceDto(
                                        url = "http://preview.redd.it/rejected.jpg",
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ).toNetworkPage(nowEpochSeconds = 10_000L).items.single()

        assertEquals(PostKind.GALLERY, post.kind)
        assertEquals("A long installation write-up.", post.body)
        assertEquals(listOf("first", "second"), post.gallery.map { it.id })
        assertEquals(
            listOf(
                "https://preview.redd.it/first.jpg",
                "https://preview.redd.it/second.jpg?width=2048&format=pjpg",
            ),
            post.gallery.map { it.remoteImageUri },
        )
        assertEquals(
            listOf("First caption", "Second caption"),
            post.gallery.map { it.caption },
        )
        assertEquals(
            listOf("https://example.com/first?x=1&y=2", ""),
            post.gallery.map { it.outboundUrl },
        )
        assertEquals(
            listOf("First caption", "Second caption"),
            post.gallery.map { it.label },
        )
        assertEquals("2 images", post.mediaLabel)
    }

    @Test
    fun redditUserIdentityMapperPrefersSnoovatarAndKeepsSafeFallbacks() {
        val fallback = UserFixture(
            username = "subshell-user",
            description = "Fallback description",
            karma = "—",
        )
        val mapped = RedditUserAboutDto(
            data = RedditUserAboutDataDto(
                name = "subshell-user",
                createdUtc = 0.0,
                totalKarma = 1_250,
                iconImage = "https://styles.redditmedia.com/icon.png",
                snoovatarImage = "https://i.redd.it/snoovatar.png",
                subreddit = RedditUserProfileSubredditDto(
                    publicDescription = "Public profile",
                    subscribers = 42,
                    bannerImage = "https://styles.redditmedia.com/profile-banner.png",
                ),
            ),
        ).toUserFixture(fallback, nowEpochSeconds = 63_072_000L)

        assertEquals("https://i.redd.it/snoovatar.png", mapped.avatarImageUri)
        assertEquals("https://styles.redditmedia.com/profile-banner.png", mapped.bannerImageUri)
        assertEquals("1.3k karma", mapped.karma)
        assertEquals("2y", mapped.accountAge)
        assertEquals("42", mapped.followers)

        val unsafeSnoovatar = RedditUserAboutDto(
            data = RedditUserAboutDataDto(
                iconImage = "https://styles.redditmedia.com/safe-icon.png",
                snoovatarImage = "http://i.redd.it/unsafe.png",
            ),
        ).toUserFixture(fallback, nowEpochSeconds = 100L)
        assertEquals("https://styles.redditmedia.com/safe-icon.png", unsafeSnoovatar.avatarImageUri)
    }

    @Test
    fun redditCommunityIdentityMapperUsesMobileBannerIconAndColorPriority() {
        val fallback = Community(
            id = "fallback",
            name = "r/subshell",
            description = "Fallback description",
            members = "—",
            accent = 0xFF6750A4L,
        )
        val mapped = RedditCommunityAboutDto(
            data = RedditCommunityAboutDataDto(
                name = "t5_subshell",
                publicDescription = "Community description",
                subscribers = 12_500,
                communityIcon = "https://styles.redditmedia.com/community-icon.png?width=256&amp;format=png",
                iconImage = "https://styles.redditmedia.com/legacy-icon.png",
                mobileBannerImage = "https://styles.redditmedia.com/mobile-banner.png",
                bannerBackgroundImage = "https://styles.redditmedia.com/desktop-banner.png",
                headerImage = "https://styles.redditmedia.com/legacy-header.png",
                primaryColor = "#123456",
            ),
        ).toCommunity(fallback)

        assertEquals("fallback", mapped.id)
        assertEquals("r/subshell", mapped.name)
        assertEquals("12.5k", mapped.members)
        assertEquals(0xFF123456L, mapped.accent)
        assertEquals(
            "https://styles.redditmedia.com/community-icon.png?width=256&format=png",
            mapped.iconImageUri,
        )
        assertEquals("https://styles.redditmedia.com/mobile-banner.png", mapped.bannerImageUri)
    }

    @Test
    fun retryClassificationIsExplicitAndTlsIsNotBlindlyRetried() {
        assertTrue(isRetryableHttpStatus(429))
        assertTrue(isRetryableHttpStatus(503))
        assertFalse(isRetryableHttpStatus(401))
        assertTrue(isRetryableTransportFailure(NetworkTransportFailureKind.TIMEOUT))
        assertFalse(isRetryableTransportFailure(NetworkTransportFailureKind.TLS))
    }

    @Test
    fun redditListingLimitIsCappedByContract() {
        assertEquals(100, RedditListingNetworkDataSource.REDDIT_MAX_LISTING_LIMIT)
        assertThrows(IllegalArgumentException::class.java) {
            RepositoryPageRequest(limit = 101).also { request ->
                require(request.limit <= RedditListingNetworkDataSource.REDDIT_MAX_LISTING_LIMIT)
            }
        }
    }

    @Test
    fun redditTimeFilteredListingsSendExplicitPeriodOnlyToSupportedEndpoints() {
        val top = redditListingJsonRequest(
            community = "android",
            sort = RedditListingSort.TOP,
            timePeriod = RedditListingTimePeriod.WEEK,
            request = RepositoryPageRequest(cursor = "t3_after", limit = 25),
        )
        assertEquals(listOf("r", "android", "top"), top.pathSegments)
        assertEquals(
            listOf(
                "raw_json" to "1",
                "limit" to "25",
                "t" to "week",
                "after" to "t3_after",
            ),
            top.queryParameters,
        )

        val hot = redditListingJsonRequest(
            sort = RedditListingSort.HOT,
            timePeriod = RedditListingTimePeriod.WEEK,
            request = RepositoryPageRequest(limit = 25),
        )
        assertFalse(hot.queryParameters.any { (name, _) -> name == "t" })

        val popular = redditListingJsonRequest(
            community = "popular",
            sort = RedditListingSort.HOT,
            request = RepositoryPageRequest(limit = 25),
        )
        assertEquals(listOf("r", "popular", "hot"), popular.pathSegments)

        val all = redditListingJsonRequest(
            community = "all",
            sort = RedditListingSort.CONTROVERSIAL,
            timePeriod = RedditListingTimePeriod.YEAR,
            request = RepositoryPageRequest(limit = 25),
        )
        assertEquals(listOf("r", "all", "controversial"), all.pathSegments)
        assertTrue(all.queryParameters.contains("t" to "year"))
    }

    @Test
    fun redditSearchUsesReadOnlyLinkListingWithExplicitCommunityAndCursor() {
        val request = redditSearchJsonRequest(
            query = "  Valentino   Rossi ",
            community = "/r/MotoGP",
            request = RepositoryPageRequest(
                cursor = "t3_after",
                limit = 10,
            ),
        )

        assertEquals(listOf("r", "MotoGP", "search"), request.pathSegments)
        assertEquals(
            listOf(
                "raw_json" to "1",
                "q" to "Valentino Rossi",
                "type" to "link",
                "sort" to "relevance",
                "t" to "all",
                "restrict_sr" to "true",
                "limit" to "10",
                "after" to "t3_after",
            ),
            request.queryParameters,
        )
        val global = redditSearchJsonRequest(
            query = "android",
            request = RepositoryPageRequest(limit = 10),
        )
        assertEquals(listOf("search"), global.pathSegments)
        assertFalse(global.queryParameters.any { it.first == "restrict_sr" })
    }

    @Test
    fun redditCommentMapperBuildsBoundedTreeAndPreservesMoreChildren() {
        val nestedListing = RedditCommentListingDto(
            data = RedditCommentListingDataDto(
                children = listOf(
                    RedditCommentThingDto(
                        kind = "t1",
                        data = RedditCommentDataDto(
                            id = "child",
                            author = "reply-user",
                            body = "Nested reply",
                            score = 4,
                            createdUtc = 9_700.0,
                            depth = 1,
                            distinguished = "moderator",
                        ),
                    ),
                    RedditCommentThingDto(
                        kind = "t1",
                        data = RedditCommentDataDto(
                            id = "budget-cut",
                            body = "This must be outside the global budget",
                        ),
                    ),
                ),
            ),
        )
        val json = SubshellNetworkJson.providerJson
        val listings = listOf(
            RedditCommentListingDto(),
            RedditCommentListingDto(
                data = RedditCommentListingDataDto(
                    children = listOf(
                        RedditCommentThingDto(
                            kind = "t1",
                            data = RedditCommentDataDto(
                                id = "root",
                                name = "t1_root",
                                author = "op-user",
                                body = "Top-level comment",
                                score = 42,
                                createdUtc = 9_100.0,
                                isSubmitter = true,
                                totalAwardsReceived = 2,
                                replies = json.encodeToJsonElement(
                                    RedditCommentListingDto.serializer(),
                                    nestedListing,
                                ),
                            ),
                        ),
                        RedditCommentThingDto(
                            kind = "more",
                            data = RedditCommentDataDto(
                                id = "more",
                                parentId = "t3_post",
                                count = 60,
                                children = listOf("next1", "next2"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val comments = listings.toComments(
            json = json,
            nowEpochSeconds = 10_000L,
            limit = 2,
            maxDepth = 8,
        )
        assertEquals(2, comments.size)
        val root = comments.first()
        assertEquals("t1_root", root.id)
        assertEquals("15m", root.age)
        assertEquals(9_100L, root.createdEpochSeconds)
        assertTrue(root.isOp)
        assertEquals("2 awards", root.award)
        assertEquals(1, root.children.size)
        assertTrue(root.children.single().isModerator)
        assertEquals("reply-user", root.children.single().author)
        val more = comments.last()
        assertEquals("t3_post", more.parentId)
        assertEquals(listOf("next1", "next2"), more.moreChildIds)
        assertEquals(60, more.moreCount)
    }

    @Test
    fun redditCommentRequestsUseServerSortAndBoundManualMoreBatch() {
        assertEquals(
            listOf("confidence", "top", "new", "controversial", "old"),
            RedditCommentSort.entries.map(RedditCommentSort::apiValue),
        )
        val comments = redditCommentsJsonRequest(
            postId = "t3_abc123",
            sort = RedditCommentSort.TOP,
        )
        assertEquals(listOf("comments", "abc123"), comments.pathSegments)
        assertTrue(comments.queryParameters.contains("sort" to "top"))

        val childIds = (1..COMMENTS02_MORE_BATCH_SIZE).map { index -> "child$index" }
        val more = redditMoreChildrenJsonRequest(
            postId = "abc123",
            childIds = childIds,
            sort = RedditCommentSort.NEW,
        )
        assertEquals(listOf("api", "morechildren"), more.pathSegments)
        assertTrue(more.queryParameters.contains("link_id" to "t3_abc123"))
        assertTrue(more.queryParameters.contains("sort" to "new"))
        assertTrue(more.queryParameters.contains("limit_children" to "true"))
        assertTrue(more.queryParameters.contains("children" to childIds.joinToString(",")))
        assertThrows(IllegalArgumentException::class.java) {
            redditMoreChildrenJsonRequest(
                postId = "abc123",
                childIds = childIds + "overflow",
                sort = RedditCommentSort.BEST,
            )
        }
    }

    @Test
    fun manualMoreMergeReplacesOnlyRequestedNodeAndKeepsRemainder() {
        val marker = Comment(
            id = "more:t1_parent:first",
            author = "More replies",
            age = "",
            body = "",
            score = 0,
            depth = 1,
            accent = 1L,
            parentId = "t1_parent",
            moreChildIds = listOf("first", "second"),
            moreCount = 2,
        )
        val parent = Comment(
            id = "t1_parent",
            author = "parent",
            age = "1h",
            body = "Parent",
            score = 1,
            depth = 0,
            accent = 1L,
            children = listOf(marker),
        )
        val reply = Comment(
            id = "t1_first",
            author = "reply",
            age = "1h",
            body = "Loaded",
            score = 2,
            depth = 1,
            accent = 2L,
            parentId = "t1_parent",
        )

        val merged = mergeExpandedComments(
            current = listOf(parent),
            moreNodeId = marker.id,
            expanded = listOf(reply),
            requestedChildCount = 1,
        )
        assertEquals(listOf("t1_first", marker.id), merged.single().children.map { it.id })
        assertEquals(listOf("second"), merged.single().children.last().moreChildIds)
        assertEquals(1, merged.single().children.last().moreCount)
    }

    @Test
    fun redditCommentBoundsAreExplicit() {
        assertEquals(100, RedditCommentNetworkDataSource.REDDIT_MAX_COMMENT_LIMIT)
        assertEquals(10, RedditCommentNetworkDataSource.REDDIT_MAX_COMMENT_DEPTH)
        assertThrows(IllegalArgumentException::class.java) {
            listOf(RedditCommentListingDto()).toComments(
                json = SubshellNetworkJson.providerJson,
                nowEpochSeconds = 10_000L,
                limit = 101,
                maxDepth = 8,
            )
        }
    }
}
