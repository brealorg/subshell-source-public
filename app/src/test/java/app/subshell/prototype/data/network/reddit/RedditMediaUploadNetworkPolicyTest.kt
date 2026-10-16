package app.subshell.prototype.data.network.reddit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RedditMediaUploadNetworkPolicyTest {
    @Test
    fun prawStyleLeaseDoesNotRequireGalleryAssetIdentity() {
        val lease = RedditMediaAssetLeaseResponseDto(
            args = RedditMediaAssetLeaseArgsDto(
                action = "//reddit-uploaded-media.s3-accelerate.amazonaws.com",
                fields = listOf(
                    RedditMediaAssetLeaseFieldDto(name = "key", value = "t2_test/image.png"),
                    RedditMediaAssetLeaseFieldDto(name = "policy", value = "opaque-policy"),
                ),
            ),
        ).toLeaseOrNull()

        assertNotNull(lease)
        requireNotNull(lease)
        assertEquals("reddit-uploaded-media.s3-accelerate.amazonaws.com", lease.uploadUrl.host)
        assertEquals(
            listOf(
                "key" to "t2_test/image.png",
                "policy" to "opaque-policy",
            ),
            lease.fields,
        )
    }


    @Test
    fun galleryLeasePreservesValidatedAssetIdentity() {
        val lease = RedditMediaAssetLeaseResponseDto(
            args = RedditMediaAssetLeaseArgsDto(
                action = "//reddit-uploaded-media.s3-accelerate.amazonaws.com",
                fields = listOf(
                    RedditMediaAssetLeaseFieldDto(name = "key", value = "t2_test/gallery.png"),
                    RedditMediaAssetLeaseFieldDto(name = "policy", value = "opaque-policy"),
                ),
            ),
            asset = RedditMediaAssetLeaseAssetDto(assetId = "AbC123"),
        ).toLeaseOrNull()

        assertNotNull(lease)
        requireNotNull(lease)
        assertEquals("abc123", lease.assetId)
    }


    @Test
    fun nativeVideoLeaseAcceptsDedicatedRedditVideoBucket() {
        val lease = RedditMediaAssetLeaseResponseDto(
            args = RedditMediaAssetLeaseArgsDto(
                action = "//reddit-uploaded-video.s3-accelerate.amazonaws.com",
                fields = listOf(
                    RedditMediaAssetLeaseFieldDto(
                        name = "key",
                        value = "t2_test/video.mp4",
                    ),
                    RedditMediaAssetLeaseFieldDto(
                        name = "policy",
                        value = "opaque-policy",
                    ),
                ),
            ),
        ).toLeaseOrNull()

        assertNotNull(lease)
        requireNotNull(lease)
        assertEquals(
            "reddit-uploaded-video.s3-accelerate.amazonaws.com",
            lease.uploadUrl.host,
        )
    }

    @Test
    fun uploadLeaseRejectsForeignHostsAndDuplicateFields() {
        assertNull(
            RedditMediaAssetLeaseResponseDto(
                args = RedditMediaAssetLeaseArgsDto(
                    action = "//example.com",
                    fields = listOf(
                        RedditMediaAssetLeaseFieldDto(name = "key", value = "x"),
                    ),
                ),
            ).toLeaseOrNull(),
        )
        assertNull(
            RedditMediaAssetLeaseResponseDto(
                args = RedditMediaAssetLeaseArgsDto(
                    action = "//reddit-uploaded-media.s3-accelerate.amazonaws.com",
                    fields = listOf(
                        RedditMediaAssetLeaseFieldDto(name = "key", value = "first"),
                        RedditMediaAssetLeaseFieldDto(name = "key", value = "second"),
                    ),
                ),
            ).toLeaseOrNull(),
        )
    }
    @org.junit.Test
    fun failedMediaCompletionPreservesAndSanitizesRedditDetail() {
        val result = parseRedditMediaCompletionMessage(
            json = kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
            },
            text = """
                {
                  "type": "failed",
                  "payload": {
                    "message": "Unsupported video codec at https://reddit-uploaded-media.example/file?token=secret"
                  }
                }
            """.trimIndent(),
        )

        org.junit.Assert.assertEquals(
            RedditMediaPostCompletionResult.Failed(
                "Unsupported video codec at [media URL]",
            ),
            result,
        )
    }

    @org.junit.Test
    fun failedMediaCompletionPreservesExplicitMissingDetailState() {
        val result = parseRedditMediaCompletionMessage(
            json = kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
            },
            text = """{"type":"failed","payload":{}}""",
        )

        org.junit.Assert.assertEquals(
            RedditMediaPostCompletionResult.Failed(detail = null),
            result,
        )
    }

    @Test
    fun nonFailedCompletionAcceptsValidatedRedirectWithoutSuccessType() {
        val result = parseRedditMediaCompletionMessage(
            json = kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
            },
            text = """
                {
                  "type": "redirect",
                  "payload": {
                    "redirect": "https://www.reddit.com/r/subshell/comments/abc123/video_test/"
                  }
                }
            """.trimIndent(),
        )

        assertEquals(
            RedditMediaPostCompletionResult.Created(
                id = "abc123",
                permalink = "/r/subshell/comments/abc123/video_test/",
            ),
            result,
        )
    }

    @Test
    fun completionAcceptsValidatedRedirectWithoutTypeField() {
        val result = parseRedditMediaCompletionMessage(
            json = kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
            },
            text = """
                {
                  "payload": {
                    "redirect": "https://www.reddit.com/r/subshell/comments/def456/video_test/"
                  }
                }
            """.trimIndent(),
        )

        assertEquals(
            RedditMediaPostCompletionResult.Created(
                id = "def456",
                permalink = "/r/subshell/comments/def456/video_test/",
            ),
            result,
        )
    }

    @Test
    fun failedCompletionStillWinsOverRedirect() {
        val result = parseRedditMediaCompletionMessage(
            json = kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
            },
            text = """
                {
                  "type": "failed",
                  "payload": {
                    "reason": "processing failed",
                    "redirect": "https://www.reddit.com/r/subshell/comments/bad123/must_not_win/"
                  }
                }
            """.trimIndent(),
        )

        assertEquals(
            RedditMediaPostCompletionResult.Failed("processing failed"),
            result,
        )
    }

}
