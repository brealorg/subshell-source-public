package app.subshell.prototype.ui

import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.screens.ContentFilterEntryView
import app.subshell.prototype.ui.screens.filterEntryViews
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Issue #30 / CONTENTFILTER03: filters that apply only in one community. */
class ContentFilter03ScopedTest {
    private fun post(title: String, community: String, domain: String? = null, flair: String? = null) = Post(
        id = "t3_${title.hashCode()}",
        community = community,
        author = "someone",
        age = "1h",
        title = title,
        score = 1,
        comments = 0,
        kind = PostKind.TEXT,
        domain = domain,
        flair = flair,
    )

    @Test
    fun aScopedRuleHidesOnlyInItsCommunity() {
        val filters = ContentFilters()
            .addingScoped(ContentFilterType.WORD, "Spoiler", "r/Movies")
            .addingScoped(ContentFilterType.FLAIR, "Meme", "tv")
            .addingScoped(ContentFilterType.DOMAIN, "www.example.com", "/r/news")

        assertTrue(filters.hides(post("A spoiler here", "movies")))
        assertFalse(filters.hides(post("A spoiler here", "books")))
        assertTrue(filters.hides(post("Funny", "tv", flair = "meme")))
        assertFalse(filters.hides(post("Funny", "movies", flair = "meme")))
        assertTrue(filters.hides(post("Story", "news", domain = "sub.example.com")))
        assertFalse(filters.hides(post("Story", "world", domain = "example.com")))
    }

    @Test
    fun aScopedRuleAlsoAppliesOnItsCommunitysOwnPage() {
        val filters = ContentFilters().addingScoped(ContentFilterType.WORD, "spoiler", "movies")

        assertTrue(filters.hidesOnCommunityPage(post("A spoiler here", "movies"), "movies"))
    }

    @Test
    fun aCommunityRuleCannotBeScoped() {
        assertEquals(
            ContentFilters(communities = setOf("tv")),
            ContentFilters().addingScoped(ContentFilterType.COMMUNITY, "tv", "movies"),
        )
    }

    // CONTENTFILTER06: a scoped rule whose community cannot match is refused,
    // never widened to a global rule (it used to become global when blank).
    @Test
    fun aScopedRuleWithAnUnusableCommunityIsRefusedNotMadeGlobal() {
        for (community in listOf("  ", "r/", "/r/", "mo\tvies", "two words", "movies.tv")) {
            assertEquals(
                community,
                ContentFilters(),
                ContentFilters().addingScoped(ContentFilterType.WORD, "spoiler", community),
            )
        }
    }

    @Test
    fun pastedCommunityLinksAndTrailingSlashesAreNormalised() {
        for (community in listOf("r/Movies/", "https://www.reddit.com/r/movies/", "reddit.com/r/movies/comments/abc/x")) {
            val filters = ContentFilters().addingScoped(ContentFilterType.WORD, "spoiler", community)
            assertEquals(community, setOf(ScopedContentFilter(ContentFilterType.WORD, "spoiler", "movies")), filters.scoped)
            assertTrue(filters.hides(post("A spoiler here", "movies")))
        }
        assertEquals(
            ContentFilters(communities = setOf("movies")),
            ContentFilters().adding(ContentFilterType.COMMUNITY, "https://reddit.com/r/Movies/"),
        )
        assertEquals(ContentFilters(), ContentFilters().adding(ContentFilterType.COMMUNITY, "not a community"))
    }

    @Test
    fun theDraftErrorNamesTheFieldThatCannotBeUsed() {
        assertEquals(null, contentFilterDraftError(ContentFilterType.WORD, "spoiler", ""))
        assertEquals(null, contentFilterDraftError(ContentFilterType.WORD, "spoiler", "r/movies"))
        assertEquals(
            ContentFilterDraftError.COMMUNITY,
            contentFilterDraftError(ContentFilterType.WORD, "spoiler", "r/"),
        )
        assertEquals(
            ContentFilterDraftError.VALUE,
            contentFilterDraftError(ContentFilterType.COMMUNITY, "two words", ""),
        )
        // The community field does not exist for community rules.
        assertEquals(null, contentFilterDraftError(ContentFilterType.COMMUNITY, "tv", "r/"))
    }

    @Test
    fun scopedRulesRoundTripThroughStorageAndOldStorageStillReads() {
        val filters = ContentFilters(words = setOf("giveaway"))
            .addingScoped(ContentFilterType.WORD, "spoiler", "movies")
            .addingScoped(ContentFilterType.USER, "u/bot", "tv")
        val stored = filters.toStorageMap()

        assertEquals("movies\tspoiler", stored.getValue("content_filter_words_scoped"))
        assertEquals("giveaway", stored.getValue("content_filter_words"))
        assertEquals(filters, ContentFilters.fromStorageMap(stored))
        // A payload from before scoped rules has no scoped keys.
        assertEquals(
            ContentFilters(words = setOf("giveaway")),
            ContentFilters.fromStorageMap(mapOf("content_filter_words" to "giveaway")),
        )
        // Broken lines are skipped, not fatal.
        assertEquals(
            setOf(ScopedContentFilter(ContentFilterType.WORD, "ok", "movies")),
            ContentFilters.fromStorageMap(
                mapOf("content_filter_words_scoped" to "no-tab\n\tempty-community\nmovies\t\nMovies\tOK"),
            ).scoped,
        )
    }

    @Test
    fun scopedKeysAreSettingsKeysSoTheyTravelInBackups() {
        ContentFilterType.entries.forEach { type ->
            assertTrue(type.scopedStorageKey, type.scopedStorageKey in SubshellSettings.storageKeys)
        }
    }

    @Test
    fun theStatePersistsBothKeysOfTheTypeAndRemovesScopedRules() {
        val persisted = mutableMapOf<String, String>()
        val state = SubshellState(persistPreference = { key, value -> persisted[key] = value })

        state.addContentFilter(ContentFilterType.WORD, "spoiler", "r/movies")
        assertEquals("movies\tspoiler", persisted["content_filter_words_scoped"])
        assertEquals("", persisted["content_filter_words"])

        state.removeContentFilter(ContentFilterType.WORD, "spoiler", "movies")
        assertEquals("", persisted["content_filter_words_scoped"])
        assertEquals(ContentFilters(), state.contentFilters)
    }

    @Test
    fun theListsShowScopedRulesWithTheirCommunity() {
        val filters = ContentFilters(words = setOf("giveaway")).addingScoped(ContentFilterType.WORD, "spoiler", "movies")

        assertEquals(
            listOf(ContentFilterEntryView("giveaway"), ContentFilterEntryView("spoiler", "movies")),
            filterEntryViews(filters, ContentFilterType.WORD),
        )
        assertEquals("spoiler in r/movies", filterEntryViews(filters, ContentFilterType.WORD)[1].label)
    }

    @Test
    fun scopedRulesSurviveTheSettingsRoundTripThatBackupsUse() {
        val filters = ContentFilters().addingScoped(ContentFilterType.WORD, "spoiler", "movies")
        val settings = SubshellSettings(contentFilters = filters)

        val restored = SubshellSettings.fromStorageMap(settings.toStorageMap())

        assertEquals(filters, restored.contentFilters)
    }
}
