package app.subshell.prototype.backup

import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import app.subshell.prototype.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Characterises the community backup paths the module tests leave open
 * before they move into CommunityPreferencesState: a non-empty restore
 * of favourites, custom-feed favourites and hidden communities reaches
 * the active account's live lists, and capture reads an inactive
 * account's hidden communities from storage.
 */
class CommunityBackupRestoreCharacterisationTest {
    private val sessions = localFixtureAccountSummaries(listOf("root", "packetmoss"))
    private val rootId = account("root").stableId
    private val packetId = account("packetmoss").stableId

    @Test
    fun aNonEmptyFavouritesRowReplacesTheActiveAccountsLiveFavourites() {
        val state = state(favorites = mutableMapOf(rootId to setOf("android")))

        state.restoreBackupState(
            settings = state.captureSettings(),
            joinedCommunityIdsByAccount = null,
            favoriteCommunityNamesByAccount = mapOf(rootId to setOf("kotlin", "subshell")),
        )

        assertEquals(setOf("kotlin", "subshell"), state.favoriteCommunityNames.toSet())
    }

    @Test
    fun aNonEmptyCustomFeedFavouritesRowReplacesTheActiveAccountsLiveFavourites() {
        val state = state(customFeeds = mutableMapOf(rootId to setOf("/user/root/m/old")))

        state.restoreBackupState(
            settings = state.captureSettings(),
            joinedCommunityIdsByAccount = null,
            favoriteCustomFeedPathsByAccount = mapOf(rootId to setOf("/user/root/m/reading")),
        )

        assertEquals(setOf("/user/root/m/reading"), state.favoriteCustomFeedPaths.toSet())
    }

    @Test
    fun aNonEmptyHiddenRowReplacesTheActiveAccountsLiveHiddenCommunities() {
        val state = state(hidden = mutableMapOf(rootId to setOf("android")))

        state.restoreBackupState(
            settings = state.captureSettings(),
            joinedCommunityIdsByAccount = null,
            hiddenCommunityNamesByAccount = mapOf(rootId to setOf("kotlin", "subshell")),
        )

        assertEquals(setOf("kotlin", "subshell"), state.hiddenCommunityNames.toSet())
    }

    @Test
    fun captureReadsAnInactiveAccountsHiddenCommunitiesFromStorage() {
        val state = state(
            hidden = mutableMapOf(
                rootId to setOf("android"),
                packetId to setOf("kotlin"),
            ),
        )

        assertEquals(
            mapOf(
                packetId to setOf("kotlin"),
                rootId to setOf("android"),
            ),
            state.captureHiddenCommunityNamesByAccount(),
        )
    }

    private fun state(
        favorites: MutableMap<String, Set<String>> = mutableMapOf(),
        customFeeds: MutableMap<String, Set<String>> = mutableMapOf(),
        hidden: MutableMap<String, Set<String>> = mutableMapOf(),
    ): SubshellState = SubshellState(
        initialAccountSessions = sessions,
        initialActiveAccountUsername = "root",
        loadFavoriteCommunityNames = { id -> favorites[id].orEmpty() },
        persistFavoriteCommunityNames = { id, names -> favorites[id] = names.toSet() },
        loadFavoriteCustomFeedPaths = { id -> customFeeds[id].orEmpty() },
        persistFavoriteCustomFeedPaths = { id, paths -> customFeeds[id] = paths.toSet() },
        loadHiddenCommunityNames = { id -> hidden[id].orEmpty() },
        persistHiddenCommunityNames = { id, names -> hidden[id] = names.toSet() },
    )

    private fun account(username: String): AccountSessionSummary =
        sessions.first { it.username.equals(username, ignoreCase = true) }
}
