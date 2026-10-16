package app.subshell.prototype.ui.components

import app.subshell.prototype.model.CommunityWikiPageRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Wiki01A2R1WikiUxPolicyTest {
    @Test
    fun wikiIndexKeepsReadablePagesAheadOfConfigurationNoise() {
        val groups = communityWikiPageGroups(
            listOf(
                CommunityWikiPageRef("config/sidebar"),
                CommunityWikiPageRef("bikeracingsubs"),
                CommunityWikiPageRef("automoderator-schedule"),
                CommunityWikiPageRef("index"),
                CommunityWikiPageRef("config/stylesheet"),
            ),
        )

        assertEquals(
            listOf("index", "bikeracingsubs"),
            groups.pages.map { page -> page.path },
        )
        assertEquals(
            listOf(
                "automoderator-schedule",
                "config/sidebar",
                "config/stylesheet",
            ),
            groups.configuration.map { page -> page.path },
        )
    }

    @Test
    fun redditAtxHeadingsWithoutSpacesBecomeSemanticMarkdownBlocks() {
        val blocks = promoteMarkdownHeadings(
            parseMarkdownBlocks(
                """
                #Rules
                Intro copy.

                ##No Non-MotoGP Spoilers:
                Details.

                ### Behavior ###
                More details.
                """.trimIndent(),
            ),
        )

        val headings = blocks.filterIsInstance<MarkdownBlock.Heading>()
        assertEquals(
            listOf(
                1 to "Rules",
                2 to "No Non-MotoGP Spoilers:",
                3 to "Behavior",
            ),
            headings.map { heading -> heading.level to heading.text },
        )
        assertTrue(
            blocks.filterIsInstance<MarkdownBlock.Paragraph>()
                .any { paragraph -> paragraph.text.contains("Intro copy.") },
        )
        assertFalse(
            blocks.filterIsInstance<MarkdownBlock.Paragraph>()
                .any { paragraph -> paragraph.text.contains("#Rules") },
        )
    }
}
