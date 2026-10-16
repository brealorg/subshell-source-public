package app.subshell.prototype.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Search01FoundationPolicyTest {
    @Test
    fun draftAndSubmittedQueriesHaveSeparateOwnership() {
        val submitted = SearchSessionSnapshot()
            .updateDraft("  pixel   camera  ")
            .submit()

        requireNotNull(submitted)
        assertEquals("pixel camera", submitted.draftQuery)
        assertEquals("pixel camera", submitted.submittedQuery)
        assertEquals(1L, submitted.submissionRevision)
        assertFalse(submitted.hasUnsubmittedChanges)

        val editing = submitted.updateDraft("pixel camera battery")
        assertEquals("pixel camera battery", editing.draftQuery)
        assertEquals("pixel camera", editing.submittedQuery)
        assertTrue(editing.hasUnsubmittedChanges)

        val resubmitted = editing.submit()
        requireNotNull(resubmitted)
        assertEquals("pixel camera battery", resubmitted.submittedQuery)
        assertEquals(2L, resubmitted.submissionRevision)
        assertFalse(resubmitted.hasUnsubmittedChanges)
        assertNull(SearchSessionSnapshot().updateDraft("   ").submit())
    }

    @Test
    fun canonicalPlannerRecognizesDirectRedditTargetsBeforeProviders() {
        val community = planSearchQuery(
            rawQuery = "r/android",
            area = SearchArea.ALL_SUBSHELL,
            authenticated = true,
        )
        assertEquals(listOf(SearchProviderKind.DIRECT), community.providers)
        assertEquals(RedditTarget.Community("android"), community.directAction?.target)
        assertNull(community.communityAutocompleteQuery)

        val user = planSearchQuery(
            rawQuery = "https://reddit.com/u/brealorg/",
            area = SearchArea.ALL_SUBSHELL,
            authenticated = true,
        )
        assertEquals(RedditTarget.User("brealorg"), user.directAction?.target)

        val post = planSearchQuery(
            rawQuery = "t3_1abcxyz",
            area = SearchArea.ALL_SUBSHELL,
            authenticated = true,
        )
        assertEquals(RedditTarget.Post("1abcxyz"), post.directAction?.target)
    }

    @Test
    fun textPlannerSelectsContextualProvidersAndSafeAutocomplete() {
        val global = planSearchQuery(
            rawQuery = "  volvo  ",
            area = SearchArea.ALL_SUBSHELL,
            authenticated = true,
        )
        assertEquals("volvo", global.normalizedQuery)
        assertEquals(
            listOf(
                SearchProviderKind.COMMUNITIES,
                SearchProviderKind.POSTS,
                SearchProviderKind.USERS,
                SearchProviderKind.LOCAL_CACHE,
            ),
            global.providers,
        )
        assertEquals("volvo", global.communityAutocompleteQuery)

        val currentCommunity = planSearchQuery(
            rawQuery = "battery",
            area = SearchArea.CURRENT_COMMUNITY,
            authenticated = true,
        )
        assertFalse(SearchProviderKind.COMMUNITIES in currentCommunity.providers)
        assertFalse(SearchProviderKind.USERS in currentCommunity.providers)
        assertNull(currentCommunity.communityAutocompleteQuery)

        val unsafeAutocomplete = planSearchQuery(
            rawQuery = "battery drain",
            area = SearchArea.ALL_SUBSHELL,
            authenticated = true,
        )
        assertNull(unsafeAutocomplete.communityAutocompleteQuery)
    }

    @Test
    fun localRankingPrefersExactThenPrefixThenDescription() {
        data class Candidate(val name: String, val description: String)
        val ranked = rankSearchMatches(
            rawQuery = "volvo",
            values = listOf(
                Candidate("cars", "Volvo ownership"),
                Candidate("volvocars", "News"),
                Candidate("volvo", "Exact"),
                Candidate("classic_volvo", "Restoration"),
            ),
            primaryText = Candidate::name,
            secondaryText = Candidate::description,
        )

        assertEquals(
            listOf("volvo", "volvocars", "classic_volvo", "cars"),
            ranked.map(Candidate::name),
        )
    }

    @Test
    fun accountHistoryEncodingPreservesOrderAndRemovesDuplicates() {
        val history = listOf(
            "  Volvo  ",
            "pixel   camera",
            "volvo",
            "r/android",
        )
        val encoded = encodeSearchHistory(history)
        assertFalse(encoded.contains("Volvo"))
        assertEquals(
            listOf("Volvo", "pixel camera", "r/android"),
            decodeSearchHistory(encoded),
        )
        assertEquals(
            "search_history.v1.account:one",
            searchHistoryPreferenceKey(" Account:One "),
        )
    }
}
