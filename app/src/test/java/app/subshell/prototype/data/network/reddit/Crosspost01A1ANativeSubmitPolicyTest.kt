package app.subshell.prototype.data.network.reddit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Crosspost01A1ANativeSubmitPolicyTest {
    @Test
    fun crosspostUsesNativeSubmitFieldsNotALinkOrCopiedBody() {
        val request = redditTextPostSubmitFormRequest(
            community = " /r/SubShell ",
            title = "  Shared original  ",
            text = "",
            crosspostFullname = " T3_Source123 ",
            sendReplies = false,
            nsfw = true,
            spoiler = true,
            flairId = "destination-flair",
            flairText = "Crosspost",
        )
        assertEquals(listOf("api", "submit"), request.pathSegments)
        assertEquals(listOf(
            "api_type" to "json",
            "raw_json" to "1",
            "kind" to "crosspost",
            "sr" to "subshell",
            "title" to "Shared original",
            "crosspost_fullname" to "t3_source123",
            "sendreplies" to "false",
            "nsfw" to "true",
            "spoiler" to "true",
            "flair_id" to "destination-flair",
            "flair_text" to "Crosspost",
        ), request.fields)
        val fields = request.fields.toMap()
        listOf("url", "text", "media", "items", "video_poster_url", "resubmit").forEach {
            assertFalse(fields.containsKey(it))
        }
    }

    @Test
    fun invalidSourcesFailInsteadOfFallingBackToSelfPost() {
        listOf("", "source123", "t1_source123", "local-post-1", "t3_á").forEach {
            assertThrows(IllegalArgumentException::class.java) {
                redditTextPostSubmitFormRequest(
                    community = "subshell", title = "Shared original", text = "", crosspostFullname = it,
                )
            }
        }
    }

    @Test
    fun mutuallyExclusiveSubmitKindsAndUnboundFlairAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            redditTextPostSubmitFormRequest(
                "subshell", "Title", "Body", crosspostFullname = "t3_original",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            redditTextPostSubmitFormRequest(
                "subshell", "Title", "", crosspostFullname = "t3_original",
                linkUrl = "https://example.org/story",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            redditTextPostSubmitFormRequest(
                "subshell", "Title", "", crosspostFullname = "t3_original",
                mediaUrl = "https://example.org/photo.png",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            redditTextPostSubmitFormRequest(
                "subshell", "Title", "", crosspostFullname = "t3_original",
                videoPosterUrl = "https://example.org/poster.png",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            redditTextPostSubmitFormRequest(
                "subshell", "Title", "", crosspostFullname = "t3_original", flairText = "News",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            redditTextPostSubmitFormRequest(
                "subshell", "Title", "", crosspostFullname = "t3_original",
                flairId = "flair1", flairText = "x".repeat(65),
            )
        }
    }

    @Test
    fun oldSelfAndLinkFormsHaveNoCrosspostField() {
        val self = redditTextPostSubmitFormRequest("subshell", "Title", "Body").fields.toMap()
        val link = redditTextPostSubmitFormRequest(
            "subshell", "Title", "Context", linkUrl = "https://example.org/story",
        ).fields.toMap()
        assertEquals("self", self["kind"])
        assertEquals("Body", self["text"])
        assertEquals("link", link["kind"])
        assertEquals("https://example.org/story", link["url"])
        assertEquals("Context", link["text"])
        assertFalse(self.containsKey("crosspost_fullname"))
        assertFalse(link.containsKey("crosspost_fullname"))
    }

    @Test
    fun sourceIdentityIsNotMistakenForCreatedWrapperIdentity() {
        assertTrue(isCrosspostCreateResponseIdentityValid("t3_original", "t3_created"))
        assertFalse(isCrosspostCreateResponseIdentityValid("t3_original", "T3_ORIGINAL"))
        assertFalse(isCrosspostCreateResponseIdentityValid("t3_original", "t1_created"))
        assertFalse(isCrosspostCreateResponseIdentityValid("", "t3_created"))
        assertTrue(isCrosspostCreateResponseIdentityValid(null, "t3_created"))
    }
}
