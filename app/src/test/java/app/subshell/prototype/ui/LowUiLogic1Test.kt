package app.subshell.prototype.ui

import app.subshell.prototype.model.CrosspostPreview
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UILOGIC1 (low ui/ logic findings taken over from dev1): crosspost sources,
 * whole-word and user-form filters (OCB-080/081/082), web links (087), post
 * and community links (088/089), Unicode search words (091), shared URL
 * brackets (092), grapheme-safe tags (105) and byte sizes (106).
 */
class LowUiLogic1Test {
    private fun post(title: String = "Title", crosspost: CrosspostPreview? = null) = Post(
        id = "t3_p",
        community = "r/here",
        author = "poster",
        age = "1h",
        title = title,
        score = 1,
        comments = 0,
        kind = if (crosspost != null) PostKind.CROSSPOST else PostKind.TEXT,
        crosspost = crosspost,
    )

    @Test
    fun aCrosspostOfAFilteredSourceIsHidden() {
        val source = CrosspostPreview(community = "r/blocked", author = "troll", title = "x", score = 1)
        assertTrue(ContentFilters(communities = setOf("blocked")).hides(post(crosspost = source)))
        assertTrue(ContentFilters(users = setOf("troll")).hides(post(crosspost = source)))
        assertFalse(ContentFilters(users = setOf("someoneelse")).hides(post(crosspost = source)))
    }

    @Test
    fun wordFiltersMatchWholeWordsOnly() {
        val filters = ContentFilters().adding(ContentFilterType.WORD, "cat")
        assertTrue(filters.hides(post("A cat, again")))
        assertFalse(filters.hides(post("Category of vacation")))
        assertTrue(ContentFilters().adding(ContentFilterType.WORD, "new york").hides(post("New York today")))
    }

    @Test
    fun userFiltersAcceptEveryProfileForm() {
        listOf("https://www.reddit.com/user/Troll/", "/user/troll", "user/troll", "u/troll", "@troll", "reddit.com/u/troll")
            .forEach { form ->
                assertEquals(form, setOf("troll"), ContentFilters().adding(ContentFilterType.USER, form).users)
            }
        assertEquals(ContentFilterDraftError.VALUE, contentFilterDraftError(ContentFilterType.USER, "not a name!", ""))
        assertNull(contentFilterDraftError(ContentFilterType.USER, "u/troll", ""))
    }

    @Test
    fun webLinksAcceptAnyCaseSchemeAndUnderscoreHosts() {
        fun issue(link: String) = validatePostComposerDraftIssue(
            PostComposerDraft(type = ComposerPostType.LINK, title = "A link", linkUrl = link),
            communityNames = listOf("subshell"),
        )
        assertNull(issue("Https://example.com/a"))
        assertNull(issue("https://my_host.example.com/a"))
        assertTrue(issue("https:example.com") != null)
    }

    @Test
    fun postAndCommunityLinksNamedSearchOrCommentsOpen() {
        assertEquals(RedditTarget.Post("abc123"), resolveRedditTarget("https://www.reddit.com/r/foo/comments/abc123/search/"))
        assertEquals("comments", (resolveRedditTarget("https://www.reddit.com/r/comments") as RedditTarget.Community).name)
        assertEquals("comments", (resolveRedditTarget("https://www.reddit.com/u/comments") as RedditTarget.User).username)
    }

    @Test
    fun searchWordsIncludeNonAsciiLetters() {
        assertEquals(780, searchMatchScore("øl", "Godt øl i Bergen"))
    }

    @Test
    fun sharedUrlsKeepTheirOwnBrackets() {
        assertEquals("https://en.wikipedia.org/wiki/Mercury_(planet)", trimSharedUrlEnd("https://en.wikipedia.org/wiki/Mercury_(planet)"))
        assertEquals("https://example.com/a", trimSharedUrlEnd("https://example.com/a)."))
    }

    @Test
    fun tagsAreCutAtWholeCharacters() {
        val flag = "🇳🇴"
        val tag = normalizedUserTagOrNull(UserTag(label = "a".repeat(22) + flag))
        assertEquals("a".repeat(22), tag?.label)
    }

    @Test
    fun byteSizesSwitchUnitAfterRounding() {
        assertEquals("10 MB", formatVideoDownloadBytes(10_480_000L))
        assertEquals("1 GB", formatVideoDownloadBytes(1_073_700_000L))
        assertEquals("1 MB", formatVideoDownloadBytes(1_048_575L))
        assertEquals("5 KB", formatVideoDownloadBytes(5_000L))
    }
}
