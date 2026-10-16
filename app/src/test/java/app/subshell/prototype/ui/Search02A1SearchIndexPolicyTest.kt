package app.subshell.prototype.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Search02A1SearchIndexPolicyTest {
    @Test
    fun indexEncodingIsBoundedNormalizedAndRoundTripsIdentityMetadata() {
        val source = (0 until SEARCH_INDEX_LIMIT + 12).map { index ->
            SearchIndexEntry(
                kind = if (index % 2 == 0) {
                    SearchIndexKind.COMMUNITY
                } else {
                    SearchIndexKind.USER
                },
                key = if (index % 2 == 0) "r/Target$index" else "u/Target$index",
                primaryText = "Target$index",
                secondaryText = "Description $index",
                imageUri = "https://example.invalid/$index.png",
                selectionCount = index + 1,
                lastSelectedEpochMs = index.toLong(),
            )
        }

        val normalized = normalizeSearchIndex(source)
        val encoded = encodeSearchIndex(source)
        val decoded = decodeSearchIndex(encoded)

        assertEquals(SEARCH_INDEX_LIMIT, normalized.size)
        assertEquals(normalized, decoded)
        assertEquals("target${SEARCH_INDEX_LIMIT + 11}", decoded.first().key)
        assertFalse(encoded.contains("Description"))
        assertEquals(
            "search_index.v1.reddit:account",
            searchIndexPreferenceKey(" Reddit:Account "),
        )
    }

    @Test
    fun repeatedSelectionsUpdateOneEntryAndProvideBoundedPersonalBoost() {
        val first = recordSearchIndexSelection(
            entries = emptyList(),
            kind = SearchIndexKind.USER,
            key = "u/Spez",
            primaryText = "Spez",
            secondaryText = "Reddit profile",
            selectedAtEpochMs = 1_000L,
        )
        val second = recordSearchIndexSelection(
            entries = first,
            kind = SearchIndexKind.USER,
            key = "spez",
            primaryText = "spez",
            secondaryText = "Updated profile",
            selectedAtEpochMs = 2_000L,
        )

        assertEquals(1, second.size)
        assertEquals(2, second.single().selectionCount)
        assertEquals(2_000L, second.single().lastSelectedEpochMs)
        assertEquals("Updated profile", second.single().secondaryText)
        assertTrue(
            searchIndexBoost(
                entries = second,
                kind = SearchIndexKind.USER,
                key = "SPEZ",
                nowEpochMs = 2_000L,
            ) in 1..80,
        )
    }

    @Test
    fun providerRerankingPreservesFuzzyResultsThatDoNotLocallyMatch() {
        val values = listOf("spez", "spelling-correction")
        val ranked = rerankSearchProviderResults(
            rawQuery = "spezz",
            values = values,
            primaryText = { value -> value },
            boost = { value -> if (value == "spez") 20 else 0 },
        )

        assertEquals(2, ranked.size)
        assertTrue("spelling-correction" in ranked)
    }

    @Test
    fun interactionBoostReordersEqualMatchesWithoutDefeatingExactMatch() {
        val entries = listOf(
            SearchIndexEntry(
                kind = SearchIndexKind.COMMUNITY,
                key = "android_beta",
                primaryText = "android_beta",
                selectionCount = 20,
                lastSelectedEpochMs = 10_000L,
            ),
        )
        val values = listOf("androiddev", "android_beta", "android")
        val ranked = rankSearchMatches(
            rawQuery = "android",
            values = values,
            primaryText = { value -> value },
            boost = { value ->
                searchIndexBoost(
                    entries = entries,
                    kind = SearchIndexKind.COMMUNITY,
                    key = value,
                    nowEpochMs = 10_000L,
                )
            },
        )

        assertEquals("android", ranked.first())
        assertEquals("android_beta", ranked[1])
        assertEquals("androiddev", ranked[2])
    }
}
