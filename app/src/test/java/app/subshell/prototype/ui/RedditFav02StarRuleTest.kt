package app.subshell.prototype.ui

import app.subshell.prototype.ui.state.FavoriteToggleAction
import app.subshell.prototype.ui.state.RedditFavoriteSyncSnapshot
import app.subshell.prototype.ui.state.favoriteToggleAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * REDDITFAV01_SHOW_REDDIT_FAVORITES_V1 / REDDITFAV02_STAR_RULE_V1: Reddit's
 * favorites count as favorites while sync is on for the active account, and
 * the star sends a change to Reddit where Reddit can hold it.
 */
class RedditFav02StarRuleTest {
    private val writes = mutableListOf<Triple<String, String, Boolean>>()
    private val localWrites = mutableListOf<Set<String>>()

    private fun state(local: Set<String> = emptySet()): SubshellState = SubshellState(
        initialActiveAccountUsername = "root",
        loadFavoriteCommunityNames = { local },
        persistFavoriteCommunityNames = { _, names -> localWrites += names },
    ).also { state ->
        state.redditFavoriteWriter = { account, name, favorite -> writes += Triple(account, name, favorite) }
    }

    private fun sync(
        enabled: Boolean = true,
        account: String = "local:root",
        favorites: Set<String> = setOf("norge", "subshell"),
        joined: Set<String> = setOf("norge", "subshell", "Android"),
        pending: Map<String, Boolean> = emptyMap(),
    ) = RedditFavoriteSyncSnapshot(enabled, account, favorites, joined, pending)

    @Test
    fun redditFavoritesShowOnlyWithSyncOnForTheActiveAccount() {
        val state = state(local = setOf("linux"))

        state.redditFavoriteSync = sync(enabled = false)
        assertFalse(state.isCommunityFavorite("norge"))

        state.redditFavoriteSync = sync(account = "reddit:other")
        assertFalse(state.isCommunityFavorite("norge"))

        state.redditFavoriteSync = sync()
        assertTrue(state.isCommunityFavorite("r/norge"))
        assertEquals(listOf("linux", "norge", "subshell"), state.effectiveFavoriteCommunityNames)
        // The local list (and so the backup) is unchanged.
        assertEquals(listOf("linux"), state.favoriteCommunityNames.toList())
    }

    @Test
    fun starOnARedditFavoriteRemovesItOnRedditNotLocally() {
        val state = state()
        state.redditFavoriteSync = sync()

        state.toggleFavoriteCommunity("norge")

        assertEquals(listOf(Triple("local:root", "norge", false)), writes)
        assertTrue(localWrites.isEmpty())
    }

    @Test
    fun starOnAJoinedCommunityAddsItOnReddit_otherwiseLocally() {
        val state = state()
        state.redditFavoriteSync = sync()

        state.toggleFavoriteCommunity("Android")
        state.toggleFavoriteCommunity("linux")

        assertEquals(listOf(Triple("local:root", "android", true)), writes)
        assertEquals(listOf(setOf("linux")), localWrites)
    }

    @Test
    fun aSentChangeShowsUntilReadBack() {
        val state = state()
        state.redditFavoriteSync = sync(pending = mapOf("norge" to false, "android" to true))

        assertFalse(state.isCommunityFavorite("norge"))
        assertTrue(state.isCommunityFavorite("Android"))
        assertEquals(listOf("Android", "subshell"), state.effectiveFavoriteCommunityNames)
    }

    @Test
    fun withSyncOffTheStarStaysLocal() {
        val state = state()
        state.redditFavoriteSync = sync(enabled = false)

        state.toggleFavoriteCommunity("Android")

        assertTrue(writes.isEmpty())
        assertEquals(listOf(setOf("android")), localWrites)
    }

    @Test
    fun toggleRuleTable() {
        fun action(local: Boolean, reddit: Boolean, active: Boolean = true, joined: Boolean = true) =
            favoriteToggleAction(local, reddit, active, joined)

        assertEquals(FavoriteToggleAction.ADD_LOCAL, action(local = false, reddit = false, active = false))
        assertEquals(FavoriteToggleAction.REMOVE_LOCAL, action(local = true, reddit = true, active = false))
        assertEquals(FavoriteToggleAction.REMOVE_ON_REDDIT, action(local = true, reddit = true))
        assertEquals(FavoriteToggleAction.REMOVE_ON_REDDIT, action(local = false, reddit = true, joined = false))
        assertEquals(FavoriteToggleAction.REMOVE_LOCAL, action(local = true, reddit = false))
        assertEquals(FavoriteToggleAction.ADD_ON_REDDIT, action(local = false, reddit = false))
        assertEquals(FavoriteToggleAction.ADD_LOCAL, action(local = false, reddit = false, joined = false))
    }

    // REDDITFAV04_CUSTOM_FEED_FAVORITES_V1
    @Test
    fun ownCustomFeedsFollowTheSameStarRule() {
        val feedWrites = mutableListOf<Triple<String, String, Boolean>>()
        val state = state()
        state.redditCustomFeedFavoriteWriter = { account, path, favorite -> feedWrites += Triple(account, path, favorite) }
        state.redditFavoriteSync = sync().copy(
            customFeedPaths = setOf("/user/root/m/news/", "/user/root/m/music/"),
            favoriteCustomFeedPaths = setOf("/user/root/m/news/"),
        )

        assertTrue(state.isCustomFeedFavorite("/user/root/m/news"))
        assertFalse(state.isCustomFeedFavorite("/user/root/m/music"))

        state.toggleFavoriteCustomFeed("/user/root/m/news")
        state.toggleFavoriteCustomFeed("/user/root/m/music/")
        state.toggleFavoriteCustomFeed("/user/other/m/stuff")

        assertEquals(
            listOf(
                Triple("local:root", "/user/root/m/news/", false),
                Triple("local:root", "/user/root/m/music/", true),
            ),
            feedWrites,
        )
        assertEquals(listOf("/user/other/m/stuff"), state.favoriteCustomFeedPaths.toList())
    }
}
