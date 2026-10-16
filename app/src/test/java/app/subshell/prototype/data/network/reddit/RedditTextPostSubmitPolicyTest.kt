package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.network.SubshellOkHttpClientFactory
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RedditTextPostSubmitPolicyTest {
    @Test
    fun linkBodyAndFlairUseHybridSubmitFields() {
        val request = redditTextPostSubmitFormRequest(
            community = "subshell",
            title = "Composer rewrite",
            text = "  Context for the shared link.  ",
            linkUrl = "https://example.com/path",
            flairId = "flair-template-id",
            flairText = "Support",
            nsfw = true,
        )

        assertEquals("link", request.fields.toMap()["kind"])
        assertEquals("https://example.com/path", request.fields.toMap()["url"])
        assertEquals("Context for the shared link.", request.fields.toMap()["text"])
        assertEquals("flair-template-id", request.fields.toMap()["flair_id"])
        assertEquals("Support", request.fields.toMap()["flair_text"])
        assertEquals("true", request.fields.toMap()["nsfw"])
    }

    @Test
    fun imagePostUsesNativeImageSubmitFieldsAndKeepsOptionalBodyAndFlair() {
        val request = redditTextPostSubmitFormRequest(
            community = "subshell",
            title = "Native image",
            text = "Context below the image",
            mediaUrl = "https://reddit-uploaded-media.s3-accelerate.amazonaws.com/key/photo.jpg",
            flairId = "flair-template-id",
            flairText = "Photo",
            spoiler = true,
        )
        val fields = request.fields.toMap()

        assertEquals("image", fields["kind"])
        assertEquals(
            "https://reddit-uploaded-media.s3-accelerate.amazonaws.com/key/photo.jpg",
            fields["url"],
        )
        assertEquals("Context below the image", fields["text"])
        assertEquals("flair-template-id", fields["flair_id"])
        assertEquals("Photo", fields["flair_text"])
        assertEquals("true", fields["spoiler"])
        assertEquals("true", fields["resubmit"])
        assertEquals("true", fields["validate_on_submit"])
    }

    @Test
    fun gallerySubmitUsesOrderedMediaIdsCaptionsLinksBodyAndFlair() {
        val request = redditGallerySubmitRequest(
            community = " /r/SubShell ",
            title = "  Native gallery  ",
            text = "  Context below the gallery  ",
            items = listOf(
                RedditGallerySubmitItem(
                    mediaId = "First123",
                    caption = "First image",
                    outboundUrl = "https://example.com/first",
                ),
                RedditGallerySubmitItem(
                    mediaId = "Second456",
                    caption = "",
                    outboundUrl = "",
                ),
            ),
            flairId = "flair-template-id",
            flairText = "Gallery",
            nsfw = true,
            spoiler = true,
        )

        assertEquals("json", request.apiType)
        assertEquals("subshell", request.sr)
        assertEquals("Native gallery", request.title)
        assertEquals("Context below the gallery", request.text)
        assertEquals("flair-template-id", request.flairId)
        assertEquals("Gallery", request.flairText)
        assertTrue(request.nsfw)
        assertTrue(request.spoiler)
        assertTrue(request.validateOnSubmit)
        assertTrue(request.showErrorList)
        assertEquals(
            listOf(
                RedditGallerySubmitItemDto(
                    caption = "First image",
                    mediaId = "first123",
                    outboundUrl = "https://example.com/first",
                ),
                RedditGallerySubmitItemDto(
                    caption = "",
                    mediaId = "second456",
                    outboundUrl = "",
                ),
            ),
            request.items,
        )
    }

    @Test
    fun gallerySubmitRejectsInvalidCountCaptionAndOutboundUrl() {
        assertThrows(IllegalArgumentException::class.java) {
            redditGallerySubmitRequest(
                community = "subshell",
                title = "Gallery",
                text = "",
                items = listOf(RedditGallerySubmitItem(mediaId = "onlyone")),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            redditGallerySubmitRequest(
                community = "subshell",
                title = "Gallery",
                text = "",
                items = listOf(
                    RedditGallerySubmitItem(
                        mediaId = "one",
                        caption = "x".repeat(REDDIT_GALLERY_CAPTION_MAX_LENGTH + 1),
                    ),
                    RedditGallerySubmitItem(mediaId = "two"),
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            redditGallerySubmitRequest(
                community = "subshell",
                title = "Gallery",
                text = "",
                items = listOf(
                    RedditGallerySubmitItem(mediaId = "one", outboundUrl = "not a URL"),
                    RedditGallerySubmitItem(mediaId = "two"),
                ),
            )
        }
    }

    @Test
    fun textPostRequestUsesExactSubmitFieldsAndCanonicalNormalization() {
        val request = redditTextPostSubmitFormRequest(
            community = " /r/SubShell ",
            title = "  WRITE01-B1 title  ",
            text = "  first\r\nsecond  ",
            sendReplies = true,
            nsfw = false,
            spoiler = true,
        )

        assertEquals(listOf("api", "submit"), request.pathSegments)
        assertEquals(
            listOf(
                "api_type" to "json",
                "raw_json" to "1",
                "kind" to "self",
                "sr" to "subshell",
                "title" to "WRITE01-B1 title",
                "text" to "first\nsecond",
                "sendreplies" to "true",
                "nsfw" to "false",
                "spoiler" to "true",
            ),
            request.fields,
        )

        assertEquals("", normalizeRedditTextPostBody(" \r\n "))
        listOf("", "r/", "bad community", "r/bad/name").forEach { rejected ->
            assertThrows(IllegalArgumentException::class.java) {
                normalizeRedditTextPostCommunity(rejected)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            normalizeRedditTextPostTitle("line one\nline two")
        }
        assertThrows(IllegalArgumentException::class.java) {
            normalizeRedditTextPostTitle("x".repeat(REDDIT_TEXT_POST_TITLE_MAX_LENGTH + 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            normalizeRedditTextPostBody(
                "x".repeat(REDDIT_TEXT_POST_BODY_MAX_LENGTH + 1),
            )
        }
    }

    @Test
    fun successfulPayloadRequiresOneConsistentCanonicalT3Identity() {
        val response = RedditTextPostSubmitResponseDto(
            json = RedditTextPostSubmitJsonDto(
                data = RedditTextPostSubmitDataDto(
                    id = "Created123",
                    name = "t3_created123",
                    url = "https://www.reddit.com/r/subshell/comments/created123/title/?utm_source=test",
                ),
            ),
        )

        val payload = response.toTextPostSubmitPayload(
            community = "subshell",
            title = "WRITE01-B1 title",
            text = "Markdown body",
        )
        assertTrue(payload is RedditTextPostSubmitPayload.Created)
        payload as RedditTextPostSubmitPayload.Created
        assertEquals("t3_created123", payload.post.fullname)
        assertEquals("created123", payload.post.id)
        assertEquals("subshell", payload.post.community)
        assertEquals("WRITE01-B1 title", payload.post.title)
        assertEquals("Markdown body", payload.post.text)
        assertEquals(
            "/r/subshell/comments/created123/title/",
            payload.post.permalink,
        )

        val mismatched = RedditTextPostSubmitResponseDto(
            json = RedditTextPostSubmitJsonDto(
                data = RedditTextPostSubmitDataDto(
                    id = "one",
                    name = "t3_two",
                ),
            ),
        )
        assertEquals(
            RedditTextPostSubmitPayload.Malformed,
            mismatched.toTextPostSubmitPayload("subshell", "Title", "Body"),
        )
    }

    @Test
    fun gallerySubmitPayloadDerivesCanonicalIdentityFromRedditUrl() {
        val response = RedditTextPostSubmitResponseDto(
            json = RedditTextPostSubmitJsonDto(
                data = RedditTextPostSubmitDataDto(
                    url = "https://www.reddit.com/r/subshell/comments/gallery123/native_gallery/?utm_source=test",
                ),
            ),
        )

        val payload = response.toGallerySubmitPayload(
            community = "subshell",
            title = "Native gallery",
            text = "Body",
        )
        assertTrue(payload is RedditTextPostSubmitPayload.Created)
        payload as RedditTextPostSubmitPayload.Created
        assertEquals("t3_gallery123", payload.post.fullname)
        assertEquals("gallery123", payload.post.id)
        assertEquals("/r/subshell/comments/gallery123/native_gallery/", payload.post.permalink)
    }

    @Test
    fun imageSubmitCanReturnSanitizedWebSocketCompletionHandle() {
        val response = RedditTextPostSubmitResponseDto(
            json = RedditTextPostSubmitJsonDto(
                data = RedditTextPostSubmitDataDto(
                    webSocketUrl = "wss://media.redditmedia.com/submit/status-token",
                ),
            ),
        )

        assertEquals(
            RedditTextPostSubmitPayload.AwaitMediaCompletion(
                "wss://media.redditmedia.com/submit/status-token",
            ),
            response.toTextPostSubmitPayload(
                community = "subshell",
                title = "Native image",
                text = "Body",
                mediaSubmission = true,
            ),
        )
        assertEquals(
            RedditTextPostSubmitPayload.Malformed,
            response.toTextPostSubmitPayload(
                community = "subshell",
                title = "Native image",
                text = "Body",
                mediaSubmission = false,
            ),
        )
    }

    @Test
    fun mediaCompletionWebSocketWinsOverProvisionalSubmitIdentity() {
        val response = RedditTextPostSubmitResponseDto(
            json = RedditTextPostSubmitJsonDto(
                data = RedditTextPostSubmitDataDto(
                    id = "provisional",
                    name = "t3_provisional",
                    url = "https://www.reddit.com/r/subshell/comments/provisional/native_image/",
                    webSocketUrl = "wss://media.redditmedia.com/completion/123",
                ),
            ),
        )

        val payload = response.toTextPostSubmitPayload(
            community = "subshell",
            title = "Native image",
            text = "Body",
            mediaSubmission = true,
        )

        assertTrue(payload is RedditTextPostSubmitPayload.AwaitMediaCompletion)
        assertEquals(
            "wss://media.redditmedia.com/completion/123",
            (payload as RedditTextPostSubmitPayload.AwaitMediaCompletion).webSocketUrl,
        )
    }

    @Test
    fun apiErrorsAreTypedWithoutProviderMessages() {
        val response = RedditTextPostSubmitResponseDto(
            json = RedditTextPostSubmitJsonDto(
                errors = listOf(
                    JsonArray(
                        listOf(
                            JsonPrimitive("RATELIMIT"),
                            JsonPrimitive("provider message must not survive"),
                            JsonPrimitive("title"),
                        ),
                    ),
                    JsonArray(
                        listOf(
                            JsonPrimitive("SUBREDDIT_NOPOST"),
                            JsonPrimitive("another provider message"),
                            JsonPrimitive("sr"),
                        ),
                    ),
                    JsonArray(
                        listOf(
                            JsonPrimitive("BAD_CAPTCHA"),
                            JsonPrimitive("challenge details"),
                            JsonPrimitive("captcha"),
                        ),
                    ),
                ),
            ),
        )

        val payload = response.toTextPostSubmitPayload("subshell", "Title", "Body")
        assertTrue(payload is RedditTextPostSubmitPayload.ApiFailure)
        payload as RedditTextPostSubmitPayload.ApiFailure
        assertEquals(
            listOf(
                RedditTextPostApiError(
                    kind = RedditTextPostApiErrorKind.RATE_LIMIT,
                    code = "RATELIMIT",
                    field = "title",
                ),
                RedditTextPostApiError(
                    kind = RedditTextPostApiErrorKind.SUBMISSION_RESTRICTED,
                    code = "SUBREDDIT_NOPOST",
                    field = "sr",
                ),
                RedditTextPostApiError(
                    kind = RedditTextPostApiErrorKind.CHALLENGE_REQUIRED,
                    code = "BAD_CAPTCHA",
                    field = "captcha",
                ),
            ),
            payload.errors,
        )
        assertFalse(payload.toString().contains("provider message"))
        assertFalse(payload.toString().contains("challenge details"))
    }

    @Test
    fun submitValidationFlairRequiredPreservesOnlySanitizedCodeAndField() {
        val response = RedditTextPostSubmitResponseDto(
            json = RedditTextPostSubmitJsonDto(
                errors = listOf(
                    JsonArray(
                        listOf(
                            JsonPrimitive("SUBMIT_VALIDATION_FLAIR_REQUIRED"),
                            JsonPrimitive("Post must contain post flair."),
                            JsonPrimitive("flair"),
                        ),
                    ),
                ),
            ),
        )

        val payload = response.toTextPostSubmitPayload("subshell", "Title", "Body")
        assertTrue(payload is RedditTextPostSubmitPayload.ApiFailure)
        payload as RedditTextPostSubmitPayload.ApiFailure
        assertEquals(
            listOf(
                RedditTextPostApiError(
                    kind = RedditTextPostApiErrorKind.VALIDATION,
                    code = "SUBMIT_VALIDATION_FLAIR_REQUIRED",
                    field = "flair",
                ),
            ),
            payload.errors,
        )
        assertFalse(payload.toString().contains("Post must contain post flair."))
    }

    @Test
    fun absentIdentityIsMalformedAndForeignUrlsAreDiscarded() {
        assertEquals(
            RedditTextPostSubmitPayload.Malformed,
            RedditTextPostSubmitResponseDto()
                .toTextPostSubmitPayload("subshell", "Title", "Body"),
        )

        val foreignUrl = RedditTextPostSubmitResponseDto(
            json = RedditTextPostSubmitJsonDto(
                data = RedditTextPostSubmitDataDto(
                    id = "created123",
                    url = "https://example.invalid/not-a-reddit-permalink",
                ),
            ),
        ).toTextPostSubmitPayload("subshell", "Title", "Body")
        assertTrue(foreignUrl is RedditTextPostSubmitPayload.Created)
        foreignUrl as RedditTextPostSubmitPayload.Created
        assertNull(foreignUrl.post.permalink)
    }

    @Test
    fun textPostWriteTransportDisablesAutomaticConnectionRetry() {
        assertFalse(
            SubshellOkHttpClientFactory.createNonRetryingWriteClient()
                .retryOnConnectionFailure,
        )
        assertFalse(
            SubshellOkHttpClientFactory.createNonRetryingMediaWriteClient()
                .retryOnConnectionFailure,
        )
    }
    @Test
    fun videoPostUsesNativeVideoSubmitFieldsPosterBodyAndFlair() {
        val request = redditTextPostSubmitFormRequest(
            community = "subshell",
            title = "Native video",
            text = "Context below the video",
            mediaUrl = "https://reddit-uploaded-media.s3-accelerate.amazonaws.com/key/video.mp4",
            mediaKind = RedditSingleMediaSubmitKind.VIDEO,
            videoPosterUrl = "https://reddit-uploaded-media.s3-accelerate.amazonaws.com/key/poster.jpg",
            flairId = "flair-template-id",
            flairText = "Video",
            nsfw = true,
            spoiler = true,
        )
        val fields = request.fields.toMap()

        assertEquals("video", fields["kind"])
        assertEquals(
            "https://reddit-uploaded-media.s3-accelerate.amazonaws.com/key/video.mp4",
            fields["url"],
        )
        assertEquals(
            "https://reddit-uploaded-media.s3-accelerate.amazonaws.com/key/poster.jpg",
            fields["video_poster_url"],
        )
        assertEquals("Context below the video", fields["text"])
        assertEquals("flair-template-id", fields["flair_id"])
        assertEquals("Video", fields["flair_text"])
        assertEquals("true", fields["nsfw"])
        assertEquals("true", fields["spoiler"])
        assertEquals("true", fields["resubmit"])
        assertEquals("true", fields["validate_on_submit"])
    }

    @Test
    fun videoApiErrorsPreserveSanitizedVideoCodeAndField() {
        val response = RedditTextPostSubmitResponseDto(
            json = RedditTextPostSubmitJsonDto(
                errors = listOf(
                    JsonArray(
                        listOf(
                            JsonPrimitive("BAD_VIDEO"),
                            JsonPrimitive("provider detail must not cross boundary"),
                            JsonPrimitive("video_poster_url"),
                        ),
                    ),
                ),
            ),
        )

        val payload = response.toTextPostSubmitPayload(
            community = "subshell",
            title = "Video",
            text = "",
            mediaSubmission = true,
        )

        require(payload is RedditTextPostSubmitPayload.ApiFailure)
        assertEquals(
            listOf(
                RedditTextPostApiError(
                    kind = RedditTextPostApiErrorKind.VALIDATION,
                    code = "BAD_VIDEO",
                    field = "video_poster_url",
                ),
            ),
            payload.errors,
        )
        assertFalse(payload.toString().contains("provider detail"))
    }

    @Test
    fun videoGifPostUsesNativeVideogifSubmitFieldsAndPoster() {
        val request = redditTextPostSubmitFormRequest(
            community = "subshell",
            title = "Native GIF",
            text = "Context below the GIF",
            mediaUrl = "https://reddit-uploaded-video.s3-accelerate.amazonaws.com/key/video.mp4",
            mediaKind = RedditSingleMediaSubmitKind.VIDEOGIF,
            videoPosterUrl = "https://reddit-uploaded-media.s3-accelerate.amazonaws.com/key/poster.jpg",
        )
        val fields = request.fields.toMap()

        assertEquals("videogif", fields["kind"])
        assertEquals(
            "https://reddit-uploaded-video.s3-accelerate.amazonaws.com/key/video.mp4",
            fields["url"],
        )
        assertEquals(
            "https://reddit-uploaded-media.s3-accelerate.amazonaws.com/key/poster.jpg",
            fields["video_poster_url"],
        )
        assertEquals("Context below the GIF", fields["text"])
        assertEquals("true", fields["resubmit"])
        assertEquals("true", fields["validate_on_submit"])
    }

    @Test
    fun gifImagePostUsesNativeImageSubmitFields() {
        val mediaUrl =
            "https://reddit-uploaded-media.s3-accelerate.amazonaws.com/t2_test/native.gif"
        val request = redditTextPostSubmitFormRequest(
            community = "subshell",
            title = "Native GIF",
            text = "Context below the GIF",
            mediaUrl = mediaUrl,
            mediaKind = RedditSingleMediaSubmitKind.IMAGE,
        )
        val fields = request.fields.toMap()

        assertEquals("image", fields["kind"])
        assertEquals(mediaUrl, fields["url"])
        assertEquals("Context below the GIF", fields["text"])
        assertEquals("true", fields["validate_on_submit"])
    }

}
