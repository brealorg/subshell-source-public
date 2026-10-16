package app.subshell.prototype.data.auth.reddit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RedditComposerPolicyLoaderTest {
    @Test
    fun policyRequestsUseCanonicalCommunityEndpoints() {
        val about = redditComposerAboutRequest("r/SubShell")
        val requirements = redditComposerPostRequirementsRequest("SubShell")
        val flair = redditComposerFlairRequest("/r/SubShell")

        assertEquals(listOf("r", "subshell", "about"), about.pathSegments)
        assertEquals(mapOf("raw_json" to "1"), about.queryParameters.toMap())
        assertEquals(
            listOf("api", "v1", "subshell", "post_requirements"),
            requirements.pathSegments,
        )
        assertTrue(requirements.queryParameters.isEmpty())
        assertEquals(
            listOf("r", "subshell", "api", "link_flair_v2"),
            flair.pathSegments,
        )
        assertEquals(mapOf("raw_json" to "1"), flair.queryParameters.toMap())
    }

    @Test
    fun postRequirementsAreAuthoritativeForRequiredFlairAndBodyLengths() {
        val requirements = RedditPostRequirementsResponse(
            isFlairRequired = true,
            bodyTextMinLength = 25,
            bodyTextMaxLength = 2_000,
        )

        assertTrue(resolveRedditComposerFlairRequired(requirements, aboutRequired = false))
        assertEquals(25, requirements.bodyTextMinLength)
        assertEquals(2_000, requirements.bodyTextMaxLength)
        assertTrue(resolveRedditComposerFlairRequired(null, aboutRequired = true))
    }

    @Test
    fun flairDtoPreservesEditableStyleAndRichTextButRejectsMissingTemplateId() {
        val mapped = RedditPostFlairResponse(
            id = " template-id ",
            text = "News",
            textEditable = true,
            textColor = "dark",
            backgroundColor = "#ffffff",
            richtext = listOf(
                RedditPostFlairRichText(elementType = "text", text = "News"),
                RedditPostFlairRichText(
                    elementType = "emoji",
                    emojiRef = ":news:",
                    imageUrl = "https://emoji.redditmedia.com/news.png",
                ),
            ),
        ).toComposerFlair()

        requireNotNull(mapped)
        assertEquals("template-id", mapped.templateId)
        assertEquals("News", mapped.text)
        assertTrue(mapped.editable)
        assertEquals("dark", mapped.textColor)
        assertEquals("#ffffff", mapped.backgroundColor)
        assertEquals(listOf("text", "emoji"), mapped.richText.map { it.elementType })
        assertNull(RedditPostFlairResponse(id = " ").toComposerFlair())
    }
}
