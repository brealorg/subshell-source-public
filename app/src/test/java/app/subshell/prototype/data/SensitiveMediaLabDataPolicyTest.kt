package app.subshell.prototype.data

import app.subshell.prototype.model.MediaAsset
import app.subshell.prototype.model.MediaFormat
import app.subshell.prototype.model.PostKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveMediaLabDataPolicyTest {
    @Test
    fun rawRedditListingJsonPreservesSensitiveFlagsAndMediaKinds() {
        val posts = redditSensitiveMediaLabMappedPosts(
            SensitiveMediaLabDebugFixtureProvider.rawJson(),
        ).associateBy { post -> post.id }

        assertEquals(5, posts.size)

        posts.getValue("t3_lab_ordinary_image").also { post ->
            assertEquals(PostKind.IMAGE, post.kind)
            assertFalse(post.nsfw)
            assertFalse(post.spoiler)
        }
        posts.getValue("t3_lab_spoiler_image").also { post ->
            assertEquals(PostKind.IMAGE, post.kind)
            assertFalse(post.nsfw)
            assertTrue(post.spoiler)
        }
        posts.getValue("t3_lab_nsfw_image").also { post ->
            assertEquals(PostKind.IMAGE, post.kind)
            assertTrue(post.nsfw)
            assertFalse(post.spoiler)
        }
        posts.getValue("t3_lab_nsfw_video").also { post ->
            assertEquals(PostKind.VIDEO, post.kind)
            assertTrue(post.nsfw)
            assertTrue(post.remoteVideoUri.orEmpty().startsWith("https"))
            assertTrue(post.remoteVideoUri.orEmpty().endsWith("/subshell-video/DASH_720.mp4"))
            assertEquals(1280, post.videoWidth)
            assertEquals(720, post.videoHeight)
        }
        posts.getValue("t3_lab_nsfw_gallery").also { post ->
            assertEquals(PostKind.GALLERY, post.kind)
            assertTrue(post.nsfw)
            assertEquals(2, post.gallery.size)
        }
    }

    @Test
    fun dev2LabLocalizesMappedMediaWithoutChangingSensitivePolicy() {
        val mapped = redditSensitiveMediaLabMappedPosts(
            SensitiveMediaLabDebugFixtureProvider.rawJson(),
        )
        val localized = localizeSensitiveMediaLabPosts(mapped).associateBy { post -> post.id }

        localized.getValue("t3_lab_nsfw_image").also { post ->
            assertTrue(post.nsfw)
            assertEquals(PostKind.IMAGE, post.kind)
            assertEquals(MediaAsset.HOMELAB, post.mediaAsset)
            assertNull(post.remoteImageUri)
        }
        localized.getValue("t3_lab_nsfw_video").also { post ->
            assertTrue(post.nsfw)
            assertEquals(PostKind.VIDEO, post.kind)
            assertEquals(MediaAsset.VIDEO_LAB_A, post.mediaAsset)
            assertNull(post.remoteVideoUri)
        }
        localized.getValue("t3_lab_nsfw_gallery").also { post ->
            assertTrue(post.nsfw)
            assertEquals(PostKind.GALLERY, post.kind)
            assertEquals(
                listOf(MediaAsset.ANDROID_DESK, MediaAsset.LINUX_DESK),
                post.gallery.map { item -> item.asset },
            )
            assertTrue(post.gallery.all { item -> item.format == MediaFormat.IMAGE })
            assertTrue(post.gallery.all { item -> item.remoteImageUri == null })
            assertTrue(
                post.gallery.none { item ->
                    item.asset in setOf(
                        MediaAsset.PIXEL_GALLERY_CAMPFIRE,
                        MediaAsset.PIXEL_GALLERY_TERMINAL,
                        MediaAsset.PIXEL_GALLERY_CAT,
                        MediaAsset.PIXEL_GALLERY_MOON,
                    )
                },
            )
        }
    }
}
