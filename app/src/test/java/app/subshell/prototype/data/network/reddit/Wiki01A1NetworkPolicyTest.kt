package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.model.normalizeCommunityWikiPagePath
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class Wiki01A1NetworkPolicyTest {
    @Test
    fun wikiRequestsUseCanonicalReadOnlyPaths() {
        assertEquals(
            listOf("r", "norge", "wiki", "pages"),
            redditCommunityWikiIndexRequest("/r/Norge").pathSegments,
        )
        assertEquals(
            listOf("r", "norge", "wiki", "faq", "posting"),
            redditCommunityWikiPageRequest(
                communityName = "/r/Norge",
                pagePath = "/faq/posting/",
            ).pathSegments,
        )
    }

    @Test
    fun wikiIndexIsBoundedNormalizedAndCaseInsensitiveDeduplicated() {
        val index = RedditCommunityWikiPagesDto(
            data = listOf(
                "index",
                " FAQ ",
                "faq",
                "guides/posting",
                "../escape",
                "",
                null,
            ),
        ).toCommunityWikiIndex("/r/Norge")

        assertEquals("norge", index.communityName)
        assertEquals(
            listOf("index", "FAQ", "guides/posting"),
            index.pages.map { it.path },
        )
    }

    @Test
    fun wikiPagePreservesBoundedMarkdownAndRevisionMetadata() {
        val page = RedditCommunityWikiPageDto(
            data = RedditCommunityWikiPageDataDto(
                contentMarkdown = "  # FAQ\n\nHello.  ",
                revisionId = " revision-123 ",
                revisionDate = 1_725_000_001.75,
                revisionBy = RedditCommunityWikiRevisionByDto(
                    data = RedditCommunityWikiRevisionByDataDto(
                        name = " WikiMod ",
                    ),
                ),
                reason = " Documentation refresh ",
            ),
        ).toCommunityWikiPage(
            communityName = "/r/Norge",
            pagePath = "faq",
        )

        assertEquals("norge", page.communityName)
        assertEquals("faq", page.path)
        assertEquals("# FAQ\n\nHello.", page.markdown)
        assertEquals("revision-123", page.revisionId)
        assertEquals(1_725_000_001L, page.revisionDateEpochSeconds)
        assertEquals("WikiMod", page.revisionAuthor)
        assertEquals("Documentation refresh", page.revisionReason)
    }

    @Test
    fun wikiPagePathRejectsTraversalQueryAndFragmentShapes() {
        assertIllegalArgument {
            normalizeCommunityWikiPagePath("../config")
        }
        assertIllegalArgument {
            normalizeCommunityWikiPagePath("index?raw=1")
        }
        assertIllegalArgument {
            normalizeCommunityWikiPagePath("index#section")
        }
    }

    private fun assertIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
