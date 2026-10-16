package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.network.SubshellNetworkJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * ISSUE56_POST_REQUIREMENTS_ACCEPT_NULL_V1: Reddit's post_requirements sends
 * null for every rule a community has not set. The answer below is r/subshell's
 * with "Body text: Not allowed", as logged on the device (2026-10-02).
 */
class Issue56PostRequirementsDecodeTest {
    private val subshellNoBody = """
        {"title_regexes":null,"body_blacklisted_strings":null,"title_blacklisted_strings":null,
        "body_text_max_length":null,"title_required_strings":null,"guidelines_text":null,
        "gallery_min_items":null,"domain_blacklist":null,"domain_whitelist":null,
        "title_text_max_length":null,"body_restriction_policy":"notAllowed",
        "link_restriction_policy":"none","guidelines_display_policy":null,
        "body_required_strings":null,"title_text_min_length":null,
        "gallery_captions_requirement":"none","is_flair_required":false,
        "gallery_max_items":null}
    """.trimIndent()

    private fun decode(json: String) = SubshellNetworkJson.providerJson
        .decodeFromString(RedditPostRequirementsResponse.serializer(), json)

    @Test
    fun anAnswerWithUnsetRulesAsNullIsReadAndKeepsItsBodyPolicy() {
        val requirements = decode(subshellNoBody)

        assertEquals("notAllowed", requirements.bodyRestrictionPolicy)
        assertFalse(requireNotNull(requirements.isFlairRequired))
        assertNull(requirements.bodyBlacklistedStrings)
        assertEquals(emptyList<String>(), requirements.titleRequiredStrings.orEmpty())
        assertNull(requirements.titleTextMaxLength)
    }

    @Test
    fun setRulesAreStillRead() {
        val requirements = decode(
            """{"title_required_strings":["[Q]"],"domain_blacklist":["example.com"],
            "body_restriction_policy":"required","title_text_max_length":120}""",
        )

        assertEquals(listOf("[Q]"), requirements.titleRequiredStrings)
        assertEquals(listOf("example.com"), requirements.domainBlacklist)
        assertEquals("required", requirements.bodyRestrictionPolicy)
        assertEquals(120, requirements.titleTextMaxLength)
    }
}
