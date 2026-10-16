package app.subshell.prototype.ui

import app.subshell.prototype.data.account.CustomFeedSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomFeedFavoritesPersistenceTest {
    @Test
    fun togglePersistsCanonicalFeedPathForStableAccountId() {
        val writes = mutableListOf<Pair<String, Set<String>>>()
        val state = SubshellState(
            initialActiveAccountUsername = "root",
            persistFavoriteCustomFeedPaths = { accountId, paths ->
                writes += accountId to paths
            },
        )

        state.toggleFavoriteCustomFeed("/user/root/m/android/")

        assertEquals(
            listOf("/user/root/m/android"),
            state.favoriteCustomFeedPaths,
        )
        assertEquals(
            "local:root" to setOf("/user/root/m/android"),
            writes.single(),
        )
        assertTrue(state.isCustomFeedFavorite("/user/root/m/android"))
    }

    @Test
    fun accountSwitchReloadsIndependentFavoriteFeedPaths() {
        val stored = mutableMapOf(
            "local:root" to setOf("/user/root/m/android"),
            "local:packetmoss" to setOf("/user/packetmoss/m/linux"),
        )
        val state = SubshellState(
            initialActiveAccountUsername = "root",
            loadFavoriteCustomFeedPaths = { accountId ->
                stored[accountId].orEmpty()
            },
            persistFavoriteCustomFeedPaths = { accountId, paths ->
                stored[accountId] = paths
            },
        )

        assertEquals(
            listOf("/user/root/m/android"),
            state.favoriteCustomFeedPaths,
        )

        state.switchAccount("packetmoss")
        assertEquals(
            listOf("/user/packetmoss/m/linux"),
            state.favoriteCustomFeedPaths,
        )

        state.toggleFavoriteCustomFeed("/user/packetmoss/m/homelab")
        state.switchAccount("root")

        assertEquals(
            listOf("/user/root/m/android"),
            state.favoriteCustomFeedPaths,
        )
        assertEquals(
            setOf(
                "/user/packetmoss/m/homelab",
                "/user/packetmoss/m/linux",
            ),
            stored.getValue("local:packetmoss"),
        )
    }

    @Test
    fun invalidFeedPathIsIgnoredWithoutPersistenceWrite() {
        var writes = 0
        val state = SubshellState(
            initialActiveAccountUsername = "root",
            persistFavoriteCustomFeedPaths = { _, _ -> writes += 1 },
        )

        state.toggleFavoriteCustomFeed("/r/android")

        assertTrue(state.favoriteCustomFeedPaths.isEmpty())
        assertEquals(0, writes)
        assertFalse(state.isCustomFeedFavorite("/r/android"))
    }

    @Test
    fun favoriteFeedsResolveServerSummariesAndSortByDisplayName() {
        val state = SubshellState(
            initialActiveAccountUsername = "root",
            loadFavoriteCustomFeedPaths = {
                setOf(
                    "/user/root/m/zeta",
                    "/user/root/m/android",
                )
            },
        )
        val feeds = listOf(
            feed("/user/root/m/zeta", "Zeta"),
            feed("/user/root/m/android", "Android"),
            feed("/user/root/m/other", "Other"),
        )

        assertEquals(
            listOf("Android", "Zeta"),
            state.favoriteCustomFeeds(feeds).map(CustomFeedSummary::displayName),
        )
    }

    private fun feed(path: String, name: String): CustomFeedSummary =
        CustomFeedSummary(
            path = path,
            displayName = name,
            description = "",
            visibility = "private",
            subredditNames = emptyList(),
        )
}
