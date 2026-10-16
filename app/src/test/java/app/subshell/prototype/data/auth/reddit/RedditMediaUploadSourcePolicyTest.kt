package app.subshell.prototype.data.auth.reddit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RedditMediaUploadSourcePolicyTest {
    @Test
    fun staticImageMimePolicyAcceptsOnlyComposer06aFormats() {
        assertEquals("image/jpeg", redditStaticImageMimeTypeOrNull("image/jpeg"))
        assertEquals("image/jpeg", redditStaticImageMimeTypeOrNull(" IMAGE/JPG "))
        assertEquals("image/png", redditStaticImageMimeTypeOrNull("image/png"))
        assertEquals("image/webp", redditStaticImageMimeTypeOrNull("image/webp"))
        assertNull(redditStaticImageMimeTypeOrNull("image/gif"))
        assertNull(redditStaticImageMimeTypeOrNull("image/heic"))
        assertNull(redditStaticImageMimeTypeOrNull("video/mp4"))
    }


    @Test
    fun galleryMimePolicyAddsGifWithoutWeakeningSingleImagePolicy() {
        assertEquals("image/jpeg", redditGalleryImageMimeTypeOrNull("image/jpeg"))
        assertEquals("image/png", redditGalleryImageMimeTypeOrNull("image/png"))
        assertEquals("image/webp", redditGalleryImageMimeTypeOrNull("image/webp"))
        assertEquals("image/gif", redditGalleryImageMimeTypeOrNull(" IMAGE/GIF "))
        assertNull(redditStaticImageMimeTypeOrNull("image/gif"))
        assertNull(redditGalleryImageMimeTypeOrNull("image/heic"))
        assertNull(redditGalleryImageMimeTypeOrNull("video/mp4"))
    }

    @Test
    fun staticImageUploadLimitIsTwentyMebibytes() {
        assertEquals(20L * 1024L * 1024L, REDDIT_IMAGE_UPLOAD_MAX_BYTES)
    }
    @Test
    fun videoMimePolicyAcceptsNativeComposer07aFormats() {
        assertEquals("video/mp4", redditVideoMimeTypeOrNull(" VIDEO/MP4 "))
        assertEquals("video/quicktime", redditVideoMimeTypeOrNull("video/quicktime"))
        assertEquals("video/webm", redditVideoMimeTypeOrNull("video/webm"))
        assertNull(redditVideoMimeTypeOrNull("image/jpeg"))
        assertNull(redditVideoMimeTypeOrNull("application/octet-stream"))
    }

    @Test
    fun videoStagingHasExplicitOneBillionByteSafetyBound() {
        assertEquals(1_000_000_000L, SUBSHELL_VIDEO_STAGING_MAX_BYTES)
    }

    @Test
    fun videoPosterDimensionsApplyDisplayRotationAndRejectInvalidValues() {
        assertEquals(
            RedditVideoDimensions(width = 720, height = 1600),
            redditVideoDisplayDimensionsOrNull(
                width = 720,
                height = 1600,
                rotationDegrees = 0,
            ),
        )
        assertEquals(
            RedditVideoDimensions(width = 1600, height = 720),
            redditVideoDisplayDimensionsOrNull(
                width = 720,
                height = 1600,
                rotationDegrees = 90,
            ),
        )
        assertEquals(
            RedditVideoDimensions(width = 1600, height = 720),
            redditVideoDisplayDimensionsOrNull(
                width = 720,
                height = 1600,
                rotationDegrees = 270,
            ),
        )
        assertNull(
            redditVideoDisplayDimensionsOrNull(
                width = 0,
                height = 1600,
                rotationDegrees = 0,
            ),
        )
    }

}
