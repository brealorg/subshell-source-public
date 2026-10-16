package app.subshell.prototype.data.cache

import app.subshell.prototype.data.SampleData
import app.subshell.prototype.model.Comment
import app.subshell.prototype.model.GalleryItem
import app.subshell.prototype.model.MediaFormat
import app.subshell.prototype.model.PostKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SubshellCacheCodecPolicyTest {
    @Test
    fun postsRoundTripWithoutLosingNestedMediaPollGalleryOrCrosspostState() {
        val decoded = SampleData.posts.map { post ->
            SubshellCacheCodec.decodePost(
                SubshellCacheCodec.encodePost(post),
            )
        }

        assertEquals(SampleData.posts, decoded)
        assertEquals(SampleData.posts.map { it.id }, decoded.map { it.id })

        val localMediaPost = SampleData.posts.first().copy(
            id = "local-media-post",
            kind = PostKind.GIF,
            localMediaUri = "content://subshell.test/local.gif",
            localMediaMimeType = "image/gif",
        )
        assertEquals(
            localMediaPost,
            SubshellCacheCodec.decodePost(
                SubshellCacheCodec.encodePost(localMediaPost),
            ),
        )
    }

    @Test
    fun remoteImageAndRemoteGalleryRoundTripWhilePreviousPayloadRemainsReadable() {
        val remoteImagePost = SampleData.posts.first().copy(
            id = "remote-image-post",
            kind = PostKind.IMAGE,
            remoteImageUri = "https://i.redd.it/subshell.png",
        )
        assertEquals(
            remoteImagePost,
            SubshellCacheCodec.decodePost(SubshellCacheCodec.encodePost(remoteImagePost)),
        )

        val galleryFixture = SampleData.posts.first { it.kind == PostKind.GALLERY }
        val remoteGalleryPost = galleryFixture.copy(
            id = "remote-gallery-post",
            gallery = galleryFixture.gallery.mapIndexed { index, item ->
                item.copy(
                    asset = null,
                    remoteImageUri = "https://preview.redd.it/gallery-$index.jpg",
                    caption = "Caption $index",
                    outboundUrl = "https://example.com/gallery-$index",
                )
            },
        )
        assertEquals(
            remoteGalleryPost,
            SubshellCacheCodec.decodePost(SubshellCacheCodec.encodePost(remoteGalleryPost)),
        )

        val withoutRemoteImage = remoteImagePost.copy(remoteImageUri = null)
        val previousPayload = SubshellCacheCodec.encodePost(withoutRemoteImage)
            // USERFLAIR01A2_LEGACY_CACHE_FIXTURE_BASELINE_V1
            // USERFLAIR01A3A_LEGACY_CACHE_FIXTURE_BASELINE_V1
            .removeSuffix("0:")
            .removeSuffix("-1:-1:-1:")
            .removeSuffix("-1:-1:")
        assertEquals(
            withoutRemoteImage,
            SubshellCacheCodec.decodePost(previousPayload),
        )
    }


    @Test
    fun postLinkTargetsRoundTripWhileTwentyEightFieldPayloadRemainsReadable() {
        val source = SampleData.posts.first().copy(
            id = "link-target-cache",
            kind = PostKind.LINK,
            externalUrl = "https://example.com/article",
            permalink = "/r/subshell/comments/linktarget/example/",
        )
        assertEquals(
            source,
            SubshellCacheCodec.decodePost(SubshellCacheCodec.encodePost(source)),
        )

        val previous = source.copy(externalUrl = null, permalink = null)
        val previousPayload = SubshellCacheCodec.encodePost(previous)
            // USERFLAIR01A3A_LEGACY_CACHE_FIXTURE_BASELINE_V1
            .removeSuffix("0:")
            .removeSuffix("-1:-1:-1:")
            .removeSuffix("-1:-1:")
        assertEquals(previous, SubshellCacheCodec.decodePost(previousPayload))
    }

    @Test
    fun sevenFieldGalleryPayloadRoundTripsWithoutDiscardingLocalMediaIdentity() {
        val source = SampleData.posts.first { it.kind == PostKind.GALLERY }.copy(
            id = "seven-field-gallery-cache",
            gallery = listOf(
                GalleryItem(
                    id = "local-gallery-item",
                    label = "Local gallery image",
                    format = MediaFormat.IMAGE,
                    localMediaUri = "content://subshell.test/gallery/local-image",
                    localMediaMimeType = "image/png",
                ),
            ),
        )

        assertEquals(
            source,
            SubshellCacheCodec.decodePost(SubshellCacheCodec.encodePost(source)),
        )
    }

    @Test
    fun remoteImageUriRoundTripsWhileLegacyPostPayloadRemainsReadable() {
        val liveImage = SampleData.posts.first().copy(
            remoteImageUri = "https://i.redd.it/subshell.png",
        )
        assertEquals(
            liveImage,
            SubshellCacheCodec.decodePost(SubshellCacheCodec.encodePost(liveImage)),
        )

        val withoutRemoteImage = liveImage.copy(remoteImageUri = null)
        val legacyPayload = SubshellCacheCodec.encodePost(withoutRemoteImage)
            // USERFLAIR01A3A_LEGACY_CACHE_FIXTURE_BASELINE_V1
            .removeSuffix("0:")
            .removeSuffix("-1:-1:-1:")
            .removeSuffix("-1:-1:")
        assertEquals(
            withoutRemoteImage,
            SubshellCacheCodec.decodePost(legacyPayload),
        )
    }

    @Test
    fun redditVideoDimensionsRoundTripWhileThePreviousPayloadRemainsReadable() {
        val video = SampleData.posts.first { it.kind == PostKind.VIDEO }.copy(
            remoteVideoUri = "https://v.redd.it/example/DASHPlaylist.mpd",
            videoWidth = 720,
            videoHeight = 1280,
        )
        assertEquals(video, SubshellCacheCodec.decodePost(SubshellCacheCodec.encodePost(video)))

        val withoutDimensions = video.copy(videoWidth = null, videoHeight = null)
        val previousPayload = SubshellCacheCodec.encodePost(withoutDimensions)
            // USERFLAIR01A3A_LEGACY_CACHE_FIXTURE_BASELINE_V1
            .removeSuffix("0:")
            .removeSuffix("-1:-1:-1:")
            .removeSuffix("-1:-1:")
        assertEquals(withoutDimensions, SubshellCacheCodec.decodePost(previousPayload))
    }

    @Test
    fun redditVideoAlternativeSourcesRoundTripWhileThirtyFieldPayloadRemainsReadable() {
        val video = SampleData.posts.first { it.kind == PostKind.VIDEO }.copy(
            remoteVideoUri = "https://v.redd.it/example/DASHPlaylist.mpd",
            remoteVideoHlsUri = "https://v.redd.it/example/HLSPlaylist.m3u8",
            remoteVideoFallbackUri = "https://v.redd.it/example/DASH_720.mp4",
        )

        assertEquals(
            video,
            SubshellCacheCodec.decodePost(SubshellCacheCodec.encodePost(video)),
        )

        val previous = video.copy(
            remoteVideoHlsUri = null,
            remoteVideoFallbackUri = null,
        )
        val thirtyFieldPayload = SubshellCacheCodec
            .encodePost(previous)
            // USERFLAIR01A3A_LEGACY_CACHE_FIXTURE_BASELINE_V1
            .removeSuffix("0:")
            .removeSuffix("-1:-1:-1:")
            .removeSuffix("-1:-1:")

        assertEquals(
            previous,
            SubshellCacheCodec.decodePost(thirtyFieldPayload),
        )
    }

    @Test
    fun commentsCommunitiesUsersInboxAndProfileCommentsRoundTrip() {
        assertEquals(
            SampleData.comments,
            SampleData.comments.map { comment ->
                SubshellCacheCodec.decodeComment(
                    SubshellCacheCodec.encodeComment(comment),
                )
            },
        )
        val localMediaComment = SampleData.comments.first().copy(
            id = "local-media-comment",
            body = "",
            localMediaUri = "content://subshell.test/comment.png",
            localMediaMimeType = "image/png",
        )
        assertEquals(
            localMediaComment,
            SubshellCacheCodec.decodeComment(
                SubshellCacheCodec.encodeComment(localMediaComment),
            ),
        )
        assertEquals(
            SampleData.communities,
            SampleData.communities.map { community ->
                SubshellCacheCodec.decodeCommunity(
                    SubshellCacheCodec.encodeCommunity(community),
                )
            },
        )
        assertEquals(
            SampleData.users,
            SampleData.users.map { user ->
                SubshellCacheCodec.decodeUser(
                    SubshellCacheCodec.encodeUser(user),
                )
            },
        )
        assertEquals(
            SampleData.inbox,
            SampleData.inbox.map { item ->
                SubshellCacheCodec.decodeInboxItem(
                    SubshellCacheCodec.encodeInboxItem(item),
                )
            },
        )
        assertEquals(
            SampleData.profileComments,
            SampleData.profileComments.map { comment ->
                SubshellCacheCodec.decodeProfileComment(
                    SubshellCacheCodec.encodeProfileComment(comment),
                )
            },
        )
    }

    @Test
    fun exactCommentTimeRoundTripsWhileLegacyRelativeOnlyPayloadRemainsReadable() {
        val exact = SampleData.comments.first().copy(
            children = emptyList(),
            createdEpochSeconds = 1_775_000_000L,
            vote = 1,
        )
        assertEquals(
            exact,
            SubshellCacheCodec.decodeComment(SubshellCacheCodec.encodeComment(exact)),
        )

        val relativeOnly = exact.copy(createdEpochSeconds = null, vote = 0)
        val legacyPayload = SubshellCacheCodec.encodeComment(relativeOnly)
            // USERFLAIR01A3A_LEGACY_CACHE_FIXTURE_BASELINE_V1
            .removeSuffix("0:")
            .removeSuffix("-1:-1:-1:")
            .removeSuffix("1:0")
            .removeSuffix("-1:0:1:0")
            .removeSuffix("-1:")
        assertEquals(
            relativeOnly,
            SubshellCacheCodec.decodeComment(legacyPayload),
        )
    }

    @Test
    fun manualMoreCommentNodeRoundTripsWithItsRemainingChildIds() {
        val more = Comment(
            id = "more:t1_parent:first",
            author = "More replies",
            age = "",
            body = "",
            score = 0,
            depth = 2,
            accent = 0xFF7AB8FF,
            parentId = "t1_parent",
            moreChildIds = listOf("first", "second"),
            moreCount = 42,
        )
        assertEquals(
            more,
            SubshellCacheCodec.decodeComment(SubshellCacheCodec.encodeComment(more)),
        )
    }

    @Test
    fun identityImageUrlsRoundTripWhileSixFieldPayloadsRemainReadable() {
        val community = SampleData.communities.first().copy(
            iconImageUri = "https://styles.redditmedia.com/community-icon.png",
            bannerImageUri = "https://styles.redditmedia.com/community-banner.png",
        )
        assertEquals(
            community,
            SubshellCacheCodec.decodeCommunity(SubshellCacheCodec.encodeCommunity(community)),
        )

        val user = SampleData.users.first().copy(
            avatarImageUri = "https://i.redd.it/snoovatar.png",
            bannerImageUri = "https://styles.redditmedia.com/profile-banner.png",
        )
        assertEquals(
            user,
            SubshellCacheCodec.decodeUser(SubshellCacheCodec.encodeUser(user)),
        )

        val previousCommunity = community.copy(iconImageUri = null, bannerImageUri = null)
        val previousCommunityPayload = SubshellCacheCodec.encodeCommunity(previousCommunity)
            .removeSuffix("-1:-1:")
        assertEquals(previousCommunity, SubshellCacheCodec.decodeCommunity(previousCommunityPayload))

        val previousUser = user.copy(avatarImageUri = null, bannerImageUri = null)
        val previousUserPayload = SubshellCacheCodec.encodeUser(previousUser)
            .removeSuffix("-1:-1:")
        assertEquals(previousUser, SubshellCacheCodec.decodeUser(previousUserPayload))
    }

    @Test
    fun legacyRedditPrefixesAreRemovedWhenCommunityCachePayloadsAreRead() {
        val legacyPost = SampleData.posts.first().copy(community = "r/subshell")
        val legacyCommunity = SampleData.communities.first().copy(name = "/r/subshell")

        assertEquals(
            "subshell",
            SubshellCacheCodec.decodePost(
                SubshellCacheCodec.encodePost(legacyPost),
            ).community,
        )
        assertEquals(
            "subshell",
            SubshellCacheCodec.decodeCommunity(
                SubshellCacheCodec.encodeCommunity(legacyCommunity),
            ).name,
        )
    }

    @Test
    fun lengthPrefixedMutationPayloadPreservesDelimitersAndNulls() {
        val encoded = SubshellCacheCodec.encodeMutationFields(
            "post|id:1",
            "body\nwith|delimiters:and unicode λ",
            null,
        )
        val decoded = SubshellCacheCodec.decodeMutationFields(encoded, 3)

        assertEquals("post|id:1", decoded[0])
        assertEquals("body\nwith|delimiters:and unicode λ", decoded[1])
        assertEquals(null, decoded[2])
        assertNotEquals(decoded[0], decoded[1])
    }
}
