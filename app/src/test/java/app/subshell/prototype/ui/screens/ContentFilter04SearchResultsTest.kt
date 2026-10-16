package app.subshell.prototype.ui.screens

import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.ContentFilters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Issue #30 / CONTENTFILTER02+04: search results after filters and the count row. */
class ContentFilter04SearchResultsTest {
    private fun post(id: String, title: String, nsfw: Boolean = false) = Post(
        id = "t3_$id",
        community = "kotlin",
        author = "someone",
        age = "1h",
        title = title,
        score = 1,
        comments = 0,
        kind = PostKind.TEXT,
        nsfw = nsfw,
    )

    private val matching = listOf(
        post("a", "Flows explained"),
        post("b", "A spoiler about flows"),
        post("c", "Adult flows", nsfw = true),
    )
    private val filters = ContentFilters(words = setOf("spoiler"))
    private val showsPost: (Post) -> Boolean = { post -> !post.nsfw }

    @Test
    fun filteredResultsAreHiddenAndCountedEvenWithoutTheRow() {
        val results = searchPostResults(matching, showsPost, filters, summaryEnabled = false, revealed = false)

        assertEquals(listOf("t3_a"), results.posts.map(Post::id))
        assertEquals(1, results.filteredCount)
        assertFalse(results.summaryVisible)
    }

    @Test
    fun withTheSettingOnTheRowShowsAndRevealLetsFilteredResultsThrough() {
        val hidden = searchPostResults(matching, showsPost, filters, summaryEnabled = true, revealed = false)
        val shown = searchPostResults(matching, showsPost, filters, summaryEnabled = true, revealed = true)

        assertTrue(hidden.summaryVisible)
        assertEquals(listOf("t3_a"), hidden.posts.map(Post::id))
        assertEquals(listOf("t3_a", "t3_b"), shown.posts.map(Post::id))
    }

    @Test
    fun revealNeverOverridesTheNsfwSettingAndNoFilterMeansNoRow() {
        val shown = searchPostResults(matching, showsPost, filters, summaryEnabled = true, revealed = true)
        val none = searchPostResults(matching, showsPost, ContentFilters(), summaryEnabled = true, revealed = true)

        assertFalse("t3_c" in shown.posts.map(Post::id))
        assertFalse(none.summaryVisible)
        assertEquals(0, none.filteredCount)
    }

    // CONTENTFILTER06: a search restricted to one community exempts that
    // community's own filter, like the community page.
    @Test
    fun aSearchInsideAFilteredCommunityKeepsItsPosts() {
        val communityFilter = ContentFilters(communities = setOf("kotlin"), words = setOf("spoiler"))

        val everywhere = searchPostResults(matching, showsPost, communityFilter, summaryEnabled = false, revealed = false)
        val inCommunity = searchPostResults(
            matching,
            showsPost,
            communityFilter,
            summaryEnabled = false,
            revealed = false,
            communityPage = "r/Kotlin",
        )

        assertTrue(everywhere.posts.isEmpty())
        assertEquals(2, everywhere.filteredCount)
        assertEquals(listOf("t3_a"), inCommunity.posts.map(Post::id))
        assertEquals(1, inCommunity.filteredCount)
    }
}
