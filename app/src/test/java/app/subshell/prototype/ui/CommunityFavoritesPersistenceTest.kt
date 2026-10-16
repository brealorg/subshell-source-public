package app.subshell.prototype.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityFavoritesPersistenceTest {
    @Test
    fun togglePersistsNormalizedCommunityNameForStableAccountId() {
        val writes = mutableListOf<Pair<String, Set<String>>>()
        val state = SubshellState(
            initialActiveAccountUsername = "root",
            loadFavoriteCommunityNames = { emptySet() },
            persistFavoriteCommunityNames = { accountId, names ->
                writes += accountId to names
            },
        )

        state.toggleFavoriteCommunity("r/Android")

        assertEquals(listOf("android"), state.favoriteCommunityNames)
        assertEquals(
            "local:root" to setOf("android"),
            writes.single(),
        )
        assertTrue(state.isCommunityFavorite("/r/android"))
    }

    @Test
    fun accountSwitchLoadsIndependentFavoriteSetByStableId() {
        val stored = mutableMapOf(
            "local:root" to setOf("android"),
            "local:packetmoss" to setOf("linux"),
        )
        val state = SubshellState(
            initialActiveAccountUsername = "root",
            loadFavoriteCommunityNames = { accountId -> stored[accountId].orEmpty() },
            persistFavoriteCommunityNames = { accountId, names -> stored[accountId] = names },
        )

        assertEquals(listOf("android"), state.favoriteCommunityNames)

        state.switchAccount("packetmoss")
        assertEquals(listOf("linux"), state.favoriteCommunityNames)

        state.toggleFavoriteCommunity("homelab")
        state.switchAccount("root")

        assertEquals(listOf("android"), state.favoriteCommunityNames)
        assertEquals(setOf("homelab", "linux"), stored.getValue("local:packetmoss"))
    }

    @Test
    fun invalidCommunityNameIsIgnoredWithoutPersistenceWrite() {
        var writes = 0
        val state = SubshellState(
            initialActiveAccountUsername = "root",
            loadFavoriteCommunityNames = { emptySet() },
            persistFavoriteCommunityNames = { _, _ -> writes += 1 },
        )

        state.toggleFavoriteCommunity("not a subreddit")

        assertTrue(state.favoriteCommunityNames.isEmpty())
        assertEquals(0, writes)
        assertFalse(state.isCommunityFavorite("not a subreddit"))
    }

    @Test
    fun communityPickerPlacesFavoritesFirstThenSortsAlphabetically() {
        val state = SubshellState(
            initialActiveAccountUsername = "root",
            loadFavoriteCommunityNames = { setOf("linux", "android") },
        )

        val ordered = state.communityPickerCommunities().map { community -> community.name }
        val favoritePrefix = ordered.takeWhile(state::isCommunityFavorite)

        assertEquals(listOf("android", "linux"), favoritePrefix)
        assertTrue(ordered.drop(favoritePrefix.size).none(state::isCommunityFavorite))
    }

    @Test
    fun preferenceKeyIsStableAndAccountScoped() {
        assertEquals(
            "favorite_community_names.reddit:t2_example",
            favoriteCommunitiesPreferenceKey(" Reddit:T2_Example "),
        )
    }
}
