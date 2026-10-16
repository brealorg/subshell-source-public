package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.network.reddit.RedditFlairRichTextDto
import org.junit.Assert.assertEquals
import org.junit.Test

class UserFlair01A3SelectorRichTextPolicyTest {
    @Test
    fun selectorCarriesEmojiImageAndTextForCurrentAndOptions() {
        val choice = RedditUserFlairChoiceDto(
            templateId = "jack-miller",
            text = ":JackMiller: Jack Miller",
            richText = listOf(
                RedditFlairRichTextDto(
                    elementType = "emoji",
                    emojiRef = ":JackMiller:",
                    imageUrl =
                        "https://emoji.redditmedia.com/jackmiller.png",
                ),
                RedditFlairRichTextDto(
                    elementType = "text",
                    text = "Jack Miller",
                ),
            ),
        )

        val snapshot = RedditUserFlairSelectorDto(
            current = choice,
            choices = listOf(choice),
        ).toSnapshot("r/MotoGP")

        assertEquals("motogp", snapshot.communityName)
        assertEquals(
            listOf("emoji", "text"),
            snapshot.currentRichText.map { it.elementType },
        )
        assertEquals(
            ":JackMiller:",
            snapshot.currentRichText.first().emojiRef,
        )
        assertEquals(
            "https://emoji.redditmedia.com/jackmiller.png",
            snapshot.currentRichText.first().imageUrl,
        )
        assertEquals(
            snapshot.currentRichText,
            snapshot.options.single().richText,
        )
    }

    @Test
    fun userFlairV2RequestTargetsReadOnlyTemplateEndpoint() {
        val request = redditUserFlairTemplatesV2Request("r/MotoGP")

        assertEquals(
            listOf("r", "motogp", "api", "user_flair_v2"),
            request.pathSegments,
        )
        assertEquals(
            listOf("raw_json" to "1"),
            request.queryParameters,
        )
    }

    @Test
    fun selectorUsesV2TemplateRichTextWhenLegacySelectorOmitsIt() {
        val selector = RedditUserFlairSelectorDto(
            current = RedditUserFlairChoiceDto(
                templateId = "jack-miller",
                text = ":JackMiller: Jack Miller",
            ),
            choices = listOf(
                RedditUserFlairChoiceDto(
                    templateId = "jack-miller",
                    text = ":JackMiller: Jack Miller",
                ),
            ),
        )

        val template = RedditUserFlairTemplateV2Dto(
            id = "jack-miller",
            text = "Jack Miller",
            richtext = listOf(
                RedditFlairRichTextDto(
                    elementType = "emoji",
                    emojiRef = ":JackMiller:",
                    imageUrl =
                        "https://emoji.redditmedia.com/jackmiller.png",
                ),
                RedditFlairRichTextDto(
                    elementType = "text",
                    text = "Jack Miller",
                ),
            ),
        )

        val snapshot = selector.toSnapshot(
            community = "motogp",
            templatesV2 = listOf(template),
        )

        assertEquals(
            listOf("emoji", "text"),
            snapshot.currentRichText.map { it.elementType },
        )
        assertEquals(
            listOf("emoji", "text"),
            snapshot.options.single().richText.map {
                it.elementType
            },
        )
        assertEquals(
            "https://emoji.redditmedia.com/jackmiller.png",
            snapshot.options
                .single()
                .richText
                .first()
                .imageUrl,
        )
    }
}
