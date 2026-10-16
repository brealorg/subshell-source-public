package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.network.SubshellOkHttpClientFactory
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RedditCommentSubmitPolicyTest {
    @Test
    fun submitRequestUsesExactFormFieldsAndStrictT1T3Parents() {
        val postReply = redditCommentSubmitFormRequest(
            parentFullname = " T3_AbC123 ",
            text = "  **Hello**  ",
        )
        assertEquals(listOf("api", "comment"), postReply.pathSegments)
        assertEquals(
            listOf(
                "api_type" to "json",
                "raw_json" to "1",
                "thing_id" to "t3_abc123",
                "text" to "**Hello**",
            ),
            postReply.fields,
        )

        val commentReply = redditCommentSubmitFormRequest(
            parentFullname = "t1_reply123",
            text = "Nested reply",
        )
        assertEquals("t1_reply123", commentReply.fields[2].second)

        listOf("", "t2_user", "t3_bad-id", "t1_").forEach { rejected ->
            assertThrows(IllegalArgumentException::class.java) {
                normalizeRedditCommentParentFullname(rejected)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            normalizeRedditCommentText(" ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            normalizeRedditCommentText("x".repeat(REDDIT_COMMENT_TEXT_MAX_LENGTH + 1))
        }
    }

    @Test
    fun mediaCommentUsesRichtextJsonWithoutLeakingTemporaryTextField() {
        val richtext = """{"document":[{"e":"img","id":"asset123"}]}"""
        val request = redditCommentSubmitFormRequest(
            parentFullname = "t3_post123",
            text = "caption that belongs in RTJSON",
            richtextJson = richtext,
        )

        assertEquals(listOf("api", "comment"), request.pathSegments)
        assertEquals(
            listOf(
                "api_type" to "json",
                "raw_json" to "1",
                "thing_id" to "t3_post123",
                "richtext_json" to richtext,
            ),
            request.fields,
        )
        assertFalse(request.fields.any { (name, _) -> name == "text" })
    }

    @Test
    fun successfulSubmitPayloadMapsOneCanonicalComment() {
        val response = RedditCommentSubmitResponseDto(
            json = RedditCommentSubmitJsonDto(
                data = RedditCommentSubmitDataDto(
                    things = listOf(
                        RedditCommentThingDto(
                            kind = "t1",
                            data = RedditCommentDataDto(
                                id = "created123",
                                name = "t1_created123",
                                author = "subshell_user",
                                body = "Created comment",
                                score = 1,
                                createdUtc = 9_940.0,
                                depth = 1,
                                parentId = "t1_parent",
                                isSubmitter = true,
                            ),
                        ),
                    ),
                ),
            ),
        )

        val payload = response.toSubmitPayload(nowEpochSeconds = 10_000L)
        assertTrue(payload is RedditCommentSubmitPayload.Created)
        payload as RedditCommentSubmitPayload.Created
        assertEquals("t1_created123", payload.comment.id)
        assertEquals("t1_parent", payload.comment.parentId)
        assertEquals("subshell_user", payload.comment.author)
        assertEquals("1m", payload.comment.age)
        assertEquals("Created comment", payload.comment.body)
        assertTrue(payload.comment.isOp)
    }

    @Test
    fun successfulMediaSubmitPayloadProjectsNativeAssetMetadataImmediately() {
        val response = RedditCommentSubmitResponseDto(
            json = RedditCommentSubmitJsonDto(
                data = RedditCommentSubmitDataDto(
                    things = listOf(
                        RedditCommentThingDto(
                            kind = "t1",
                            data = RedditCommentDataDto(
                                id = "createdgif",
                                name = "t1_createdgif",
                                author = "subshell_user",
                                body = "Caption\n\n![gif](GifAsset123)",
                                mediaMetadata = mapOf(
                                    "gifasset123" to RedditMediaMetadataDto(
                                        status = "valid",
                                        mediaKind = "AnimatedImage",
                                        mimeType = "image/gif",
                                        source = RedditMediaImageSourceDto(
                                            gifUrl = "https://i.redd.it/gifasset123.gif",
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val payload = response.toSubmitPayload(nowEpochSeconds = 10_000L)
        assertTrue(payload is RedditCommentSubmitPayload.Created)
        payload as RedditCommentSubmitPayload.Created
        assertFalse(payload.comment.body.contains("![gif](GifAsset123)"))
        assertTrue(payload.comment.body.contains("Caption"))
        assertTrue(payload.comment.body.contains("https://i.redd.it/gifasset123.gif"))
    }

    @Test
    fun apiErrorsAreTypedWithoutProviderMessages() {
        val response = RedditCommentSubmitResponseDto(
            json = RedditCommentSubmitJsonDto(
                errors = listOf(
                    JsonArray(
                        listOf(
                            JsonPrimitive("RATELIMIT"),
                            JsonPrimitive("provider message must not survive"),
                            JsonPrimitive("text"),
                        ),
                    ),
                    JsonArray(
                        listOf(
                            JsonPrimitive("THREAD_LOCKED"),
                            JsonPrimitive("another provider message"),
                            JsonPrimitive("thing_id"),
                        ),
                    ),
                ),
            ),
        )

        val payload = response.toSubmitPayload(nowEpochSeconds = 10_000L)
        assertTrue(payload is RedditCommentSubmitPayload.ApiFailure)
        payload as RedditCommentSubmitPayload.ApiFailure
        assertEquals(
            listOf(
                RedditCommentApiError(
                    kind = RedditCommentApiErrorKind.RATE_LIMIT,
                    code = "RATELIMIT",
                    field = "text",
                ),
                RedditCommentApiError(
                    kind = RedditCommentApiErrorKind.PARENT_UNAVAILABLE,
                    code = "THREAD_LOCKED",
                    field = "thing_id",
                ),
            ),
            payload.errors,
        )
        assertFalse(payload.toString().contains("provider message"))
    }

    @Test
    fun missingOrMultipleCommentThingsAreMalformedPayloads() {
        assertEquals(
            RedditCommentSubmitPayload.Malformed,
            RedditCommentSubmitResponseDto().toSubmitPayload(10_000L),
        )
        val duplicated = RedditCommentSubmitResponseDto(
            json = RedditCommentSubmitJsonDto(
                data = RedditCommentSubmitDataDto(
                    things = listOf(
                        RedditCommentThingDto(
                            kind = "t1",
                            data = RedditCommentDataDto(id = "one", body = "one"),
                        ),
                        RedditCommentThingDto(
                            kind = "t1",
                            data = RedditCommentDataDto(id = "two", body = "two"),
                        ),
                    ),
                ),
            ),
        )
        assertEquals(
            RedditCommentSubmitPayload.Malformed,
            duplicated.toSubmitPayload(10_000L),
        )
    }

    @Test
    fun commentWriteTransportDisablesAutomaticConnectionRetry() {
        assertFalse(
            SubshellOkHttpClientFactory.createNonRetryingWriteClient().retryOnConnectionFailure,
        )
    }
}
