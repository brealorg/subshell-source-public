package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.network.reddit.RedditFlairRichTextDto

import org.junit.Assert.assertEquals
import org.junit.Test

class UserFlair01A3BSelectorColorPolicyTest {
    @Test
    fun v2TemplateColorsEnrichLegacySelectorCurrentAndOption() {
        val selector = RedditUserFlairSelectorDto(
            current = RedditUserFlairChoiceDto(
                templateId = "ducati",
                text = "Ducati Lenovo Team",
            ),
            choices = listOf(
                RedditUserFlairChoiceDto(
                    templateId = "ducati",
                    text = "Ducati Lenovo Team",
                ),
            ),
        )

        val template = RedditUserFlairTemplateV2Dto(
            id = "ducati",
            text = "Ducati Lenovo Team",
            textColor = "light",
            backgroundColor = "#cc0000",
        )

        val snapshot = selector.toSnapshot(
            community = "motogp",
            templatesV2 = listOf(template),
        )

        assertEquals(
            "light",
            snapshot.currentTextColor,
        )
        assertEquals(
            "#cc0000",
            snapshot.currentBackgroundColor,
        )
        assertEquals(
            "light",
            snapshot.options.single().textColor,
        )
        assertEquals(
            "#cc0000",
            snapshot.options.single().backgroundColor,
        )
    }

    @Test
    fun selectorColorsRemainAuthoritativeWhenLegacyEndpointProvidesThem() {
        val selector = RedditUserFlairSelectorDto(
            current = RedditUserFlairChoiceDto(
                templateId = "factory",
                text = "Factory",
                textColor = "dark",
                backgroundColor = "#eeeeee",
            ),
            choices = listOf(
                RedditUserFlairChoiceDto(
                    templateId = "factory",
                    text = "Factory",
                    textColor = "dark",
                    backgroundColor = "#eeeeee",
                ),
            ),
        )

        val template = RedditUserFlairTemplateV2Dto(
            id = "factory",
            text = "Factory",
            textColor = "light",
            backgroundColor = "#111111",
        )

        val snapshot = selector.toSnapshot(
            community = "motogp",
            templatesV2 = listOf(template),
        )

        assertEquals(
            "dark",
            snapshot.currentTextColor,
        )
        assertEquals(
            "#eeeeee",
            snapshot.currentBackgroundColor,
        )
        assertEquals(
            "dark",
            snapshot.options.single().textColor,
        )
        assertEquals(
            "#eeeeee",
            snapshot.options.single().backgroundColor,
        )
    }
    // USERFLAIR01A3B_CURRENT_OPTION_TEXT_MATCH_V1
    @Test
    fun currentWithoutTemplateIdInheritsOneUnambiguousMatchingOption() {
        val selector = RedditUserFlairSelectorDto(
            current = RedditUserFlairChoiceDto(
                text = "Marc Márquez",
            ),
            choices = listOf(
                RedditUserFlairChoiceDto(
                    templateId = "marc-current",
                    text = "Marc Márquez",
                ),
            ),
        )

        val template = RedditUserFlairTemplateV2Dto(
            id = "marc-current",
            text = "Marc Márquez",
            textColor = "light",
            backgroundColor = "#cc0000",
            richtext = listOf(
                RedditFlairRichTextDto(
                    elementType = "text",
                    text = "Marc Márquez",
                ),
            ),
        )

        val snapshot = selector.toSnapshot(
            community = "motogp",
            templatesV2 = listOf(template),
        )

        assertEquals(
            "marc-current",
            snapshot.currentTemplateId,
        )
        assertEquals(
            "light",
            snapshot.currentTextColor,
        )
        assertEquals(
            "#cc0000",
            snapshot.currentBackgroundColor,
        )
        assertEquals(
            listOf("text"),
            snapshot.currentRichText.map { it.elementType },
        )
    }

    @Test
    fun duplicateCurrentTextDoesNotGuessTemplateIdentity() {
        val selector = RedditUserFlairSelectorDto(
            current = RedditUserFlairChoiceDto(
                text = "Factory",
            ),
            choices = listOf(
                RedditUserFlairChoiceDto(
                    templateId = "factory-a",
                    text = "Factory",
                ),
                RedditUserFlairChoiceDto(
                    templateId = "factory-b",
                    text = "Factory",
                ),
            ),
        )

        val snapshot = selector.toSnapshot(
            community = "motogp",
            templatesV2 = listOf(
                RedditUserFlairTemplateV2Dto(
                    id = "factory-a",
                    text = "Factory",
                    textColor = "light",
                    backgroundColor = "#111111",
                ),
                RedditUserFlairTemplateV2Dto(
                    id = "factory-b",
                    text = "Factory",
                    textColor = "dark",
                    backgroundColor = "#eeeeee",
                ),
            ),
        )

        assertEquals(null, snapshot.currentTemplateId)
        assertEquals(null, snapshot.currentTextColor)
        assertEquals(null, snapshot.currentBackgroundColor)
    }

}
