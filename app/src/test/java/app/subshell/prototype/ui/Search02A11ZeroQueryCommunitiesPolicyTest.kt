package app.subshell.prototype.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Search02A11ZeroQueryCommunitiesPolicyTest {
    @Test
    fun authenticatedZeroQueryRejectsGenericCatalogPrefixWithoutPersonalSignals() {
        val result = selectZeroQueryCommunitySuggestions(
            authenticated = true,
            candidates = listOf(
                ZeroQueryCommunitySuggestion(name = "AbsoluteUnits"),
                ZeroQueryCommunitySuggestion(name = "abstractgames"),
                ZeroQueryCommunitySuggestion(name = "Achievements"),
            ),
            recentCommunityNames = emptySet(),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun authenticatedSuggestionsPreferFavoritesThenInteractionsThenJoined() {
        val result = selectZeroQueryCommunitySuggestions(
            authenticated = true,
            candidates = listOf(
                ZeroQueryCommunitySuggestion(name = "joined-only", joined = true),
                ZeroQueryCommunitySuggestion(
                    name = "interaction",
                    interactionBoost = 70,
                ),
                ZeroQueryCommunitySuggestion(name = "favorite", favorite = true),
                ZeroQueryCommunitySuggestion(name = "generic"),
            ),
            recentCommunityNames = emptySet(),
        )

        assertEquals(
            listOf("favorite", "interaction", "joined-only"),
            result.map { suggestion -> suggestion.name },
        )
    }

    @Test
    fun recentCommunitiesAreExcludedAndCaseVariantsCollapseIntoOneSuggestion() {
        val result = selectZeroQueryCommunitySuggestions(
            authenticated = true,
            candidates = listOf(
                ZeroQueryCommunitySuggestion(
                    name = "r/Achievements",
                    joined = true,
                ),
                ZeroQueryCommunitySuggestion(
                    name = "achievements",
                    favorite = true,
                    interactionBoost = 30,
                ),
                ZeroQueryCommunitySuggestion(
                    name = "Plex",
                    favorite = true,
                ),
            ),
            recentCommunityNames = setOf("r/plex"),
        )

        assertEquals(1, result.size)
        assertTrue(result.single().name.equals("Achievements", ignoreCase = true))
        assertTrue(result.single().favorite)
        assertTrue(result.single().joined)
        assertEquals(30, result.single().interactionBoost)
    }

    @Test
    fun unauthenticatedZeroQueryKeepsBoundedGenericFallbackButStillDeduplicates() {
        val result = selectZeroQueryCommunitySuggestions(
            authenticated = false,
            candidates = listOf(
                ZeroQueryCommunitySuggestion(name = "Alpha"),
                ZeroQueryCommunitySuggestion(name = "alpha"),
                ZeroQueryCommunitySuggestion(name = "Beta"),
                ZeroQueryCommunitySuggestion(name = "Gamma"),
                ZeroQueryCommunitySuggestion(name = "Delta"),
            ),
            recentCommunityNames = setOf("beta"),
            limit = 3,
        )

        assertEquals(listOf("Alpha", "Gamma", "Delta"), result.map { it.name })
    }
}
