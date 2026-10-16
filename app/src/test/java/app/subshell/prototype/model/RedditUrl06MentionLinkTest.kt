package app.subshell.prototype.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * REDDITURL06: `r/name` and `u/name` in text, and site-relative markdown
 * links, are Reddit links. They stay site-relative; the in-app link handler
 * resolves them (see [aRelativeLinkOpensInTheApp]). Every example here is
 * made up.
 */
class RedditUrl06MentionLinkTest {
    private fun links(text: String, source: String = text) =
        markdownLinkTargetsForRenderedText(source, text).map { link ->
            text.substring(link.start, link.end) to link.url
        }

    @Test
    fun plainMentionsBecomeRedditLinks() {
        assertEquals(
            listOf(
                "r/kotlin" to "/r/kotlin",
                "/u/some-one" to "/u/some-one",
            ),
            links("Ask in r/kotlin, or ping /u/some-one."),
        )
    }

    @Test
    fun pathsInsideUrlsAndWordsStayText() {
        assertEquals(
            listOf("https://example.com/r/kotlin" to "https://example.com/r/kotlin"),
            links("See https://example.com/r/kotlin today"),
        )
        assertEquals(emptyList<Pair<String, String>>(), links("the folder bar/r/x and our/u/y and 4r/abc"))
        assertEquals(emptyList<Pair<String, String>>(), links("r/a is too short"))
    }

    @Test
    fun siteRelativeMarkdownLinksPointAtReddit() {
        assertEquals(
            listOf("the FAQ" to "/r/kotlin/wiki/faq"),
            links(text = "Read the FAQ first", source = "Read [the FAQ](/r/kotlin/wiki/faq) first"),
        )
        assertEquals(
            listOf("him" to "/user/someone"),
            links(text = "Ask him", source = "Ask [him](/user/someone)"),
        )
    }

    @Test
    fun aMentionInsideALinkLabelIsNotLinkedTwice() {
        assertEquals(
            listOf("r/kotlin" to "/r/kotlin/top"),
            links(text = "Visit r/kotlin", source = "Visit [r/kotlin](/r/kotlin/top)"),
        )
    }

    @Test
    fun aRelativeLinkOpensInTheApp() {
        assertEquals(
            app.subshell.prototype.ui.RedditTarget.Community("kotlin"),
            app.subshell.prototype.ui.resolveRedditTarget("/r/kotlin"),
        )
        assertEquals(true, app.subshell.prototype.ui.isInAppRedditLink("/u/some-one"))
    }
}
