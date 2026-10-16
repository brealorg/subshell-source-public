package app.subshell.prototype.ui

import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentFiltersTest {
    @Test
    fun entriesNormalizePrefixesDomainsAndCaseBeforeStorage() {
        val filters = ContentFilters()
            .adding(ContentFilterType.COMMUNITY, " r/Android ")
            .adding(ContentFilterType.USER, "/u/ExampleUser")
            .adding(ContentFilterType.DOMAIN, "https://www.Example.com/path")
            .adding(ContentFilterType.WORD, " GiveAway ")
            .adding(ContentFilterType.FLAIR, " News ")

        assertEquals(setOf("android"), filters.communities)
        assertEquals(setOf("exampleuser"), filters.users)
        assertEquals(setOf("example.com"), filters.domains)
        assertEquals(setOf("giveaway"), filters.words)
        assertEquals(setOf("news"), filters.flairs)
        assertEquals(filters, ContentFilters.fromStorageMap(filters.toStorageMap()))
    }

    @Test
    fun everyFilterTypeCanHideAMatchingPost() {
        val post = post(
            community = "Android",
            author = "ExampleUser",
            title = "A useful Compose guide",
            body = "Includes a small giveaway",
            domain = "news.example.com",
            flair = "News",
        )

        assertTrue(ContentFilters(communities = setOf("android")).hides(post))
        assertTrue(ContentFilters(users = setOf("exampleuser")).hides(post))
        assertTrue(ContentFilters(domains = setOf("example.com")).hides(post))
        assertTrue(ContentFilters(words = setOf("compose guide")).hides(post))
        assertTrue(ContentFilters(words = setOf("giveaway")).hides(post))
        assertTrue(ContentFilters(flairs = setOf("news")).hides(post))
        assertFalse(ContentFilters(words = setOf("unrelated")).hides(post))
    }

    @Test
    fun homeAndLiveFeedsApplyLocalFiltersWithoutReorderingSurvivors() {
        val posts = listOf(
            post(id = "first", title = "Keep first"),
            post(id = "filtered", title = "A giveaway"),
            post(id = "last", title = "Keep last"),
        )
        val filters = ContentFilters(words = setOf("giveaway"))

        val local = selectHomeFeedPosts(
            posts = posts,
            source = FeedSource.ALL,
            sort = FeedSort.NEW,
            joinedCommunityIds = emptyList(),
            savedPostIds = emptyList(),
            hiddenPostIds = emptyList(),
            contentFilters = filters,
        )
        val live = selectLiveHomePosts(
            posts = posts,
            hiddenPostIds = emptyList(),
            contentFilters = filters,
        )

        assertEquals(listOf("last", "first"), local.map(Post::id))
        assertEquals(listOf("first", "last"), live.map(Post::id))
    }

    @Test
    fun statePersistsEachNormalizedChangeAndRemoval() {
        val persisted = linkedMapOf<String, String>()
        val state = SubshellState(
            persistPreference = { key, value -> persisted[key] = value },
        )

        state.addContentFilter(ContentFilterType.COMMUNITY, "r/Android")

        assertEquals(setOf("android"), state.contentFilters.communities)
        assertEquals(
            state.contentFilters,
            ContentFilters.fromStorageMap(persisted),
        )

        state.removeContentFilter(ContentFilterType.COMMUNITY, "ANDROID")

        assertEquals(ContentFilters(), state.contentFilters)
        assertEquals("", persisted.getValue(ContentFilterType.COMMUNITY.storageKey))
    }

    private fun post(
        id: String = "post",
        community: String = "subshell",
        author: String = "author",
        title: String = id,
        body: String = "",
        domain: String? = null,
        flair: String? = null,
    ): Post = Post(
        id = id,
        community = community,
        author = author,
        age = "1h",
        title = title,
        body = body,
        score = 1,
        comments = 1,
        kind = PostKind.TEXT,
        domain = domain,
        flair = flair,
    )
}
