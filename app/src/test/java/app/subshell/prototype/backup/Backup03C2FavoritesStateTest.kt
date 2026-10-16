package app.subshell.prototype.backup

import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import app.subshell.prototype.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Backup03C2FavoritesStateTest {
    private val sessions = localFixtureAccountSummaries(
        listOf("root", "packetmoss"),
    )
    private val root = account("root")
    private val packet = account("packetmoss")
    private val rootId = root.stableId.trim().lowercase()
    private val packetId = packet.stableId.trim().lowercase()

    @Test
    fun captureUsesStableIdsForEveryAccountAndPreservesExplicitEmptyRows() {
        val communities = mutableMapOf(
            rootId to setOf("android"),
            packetId to emptySet(),
        )
        val feeds = mutableMapOf(
            rootId to setOf("/user/root/m/dev"),
            packetId to emptySet(),
        )
        val state = state(
            communities = communities,
            feeds = feeds,
            persistFavoriteCommunities = { _, _ -> },
            persistFavoriteFeeds = { _, _ -> },
        )

        state.toggleFavoriteCommunity("subshell")
        state.toggleFavoriteCustomFeed("/user/root/m/news")

        assertEquals(
            mapOf(
                packetId to emptySet(),
                rootId to setOf("android", "subshell"),
            ),
            state.captureFavoriteCommunityNamesByAccount(),
        )
        assertEquals(
            mapOf(
                packetId to emptySet(),
                rootId to setOf("/user/root/m/dev", "/user/root/m/news"),
            ),
            state.captureFavoriteCustomFeedPathsByAccount(),
        )
    }

    @Test
    fun nullFavoriteModulesAreLegacyNoOp() {
        var seenFavoriteCommunities: Map<String, Set<String>>? = emptyMap()
        var seenFavoriteFeeds: Map<String, Set<String>>? = emptyMap()
        val state = state(
            communities = mutableMapOf(rootId to setOf("android")),
            feeds = mutableMapOf(rootId to setOf("/user/root/m/dev")),
            persistBackupRestoreState = { _, _, favoriteCommunities, favoriteFeeds, _, _, _, _, _, _ ->
                seenFavoriteCommunities = favoriteCommunities
                seenFavoriteFeeds = favoriteFeeds
            },
        )

        state.restoreBackupState(
            settings = state.captureSettings(),
            joinedCommunityIdsByAccount = null,
            favoriteCommunityNamesByAccount = null,
            favoriteCustomFeedPathsByAccount = null,
        )

        assertEquals(setOf("android"), state.favoriteCommunityNames.toSet())
        assertEquals(setOf("/user/root/m/dev"), state.favoriteCustomFeedPaths.toSet())
        assertNull(seenFavoriteCommunities)
        assertNull(seenFavoriteFeeds)
    }

    @Test
    fun explicitEmptyActiveRowsClearLiveFavoritesAfterTransaction() {
        var transactionCalls = 0
        val state = state(
            communities = mutableMapOf(rootId to setOf("android")),
            feeds = mutableMapOf(rootId to setOf("/user/root/m/dev")),
            persistBackupRestoreState = { _, _, _, _, _, _, _, _, _, _ -> transactionCalls += 1 },
        )

        state.restoreBackupState(
            settings = state.captureSettings(),
            joinedCommunityIdsByAccount = null,
            favoriteCommunityNamesByAccount = mapOf(rootId to emptySet()),
            favoriteCustomFeedPathsByAccount = mapOf(rootId to emptySet()),
        )

        assertEquals(1, transactionCalls)
        assertTrue(state.favoriteCommunityNames.isEmpty())
        assertTrue(state.favoriteCustomFeedPaths.isEmpty())
    }

    @Test
    fun emptyModulesAreAuthoritativeClearAndRemainDistinctFromNull() {
        var seenCommunities: Map<String, Set<String>>? = null
        var seenFeeds: Map<String, Set<String>>? = null
        val state = state(
            communities = mutableMapOf(rootId to setOf("android")),
            feeds = mutableMapOf(rootId to setOf("/user/root/m/dev")),
            persistBackupRestoreState = { _, _, favoriteCommunities, favoriteFeeds, _, _, _, _, _, _ ->
                seenCommunities = favoriteCommunities
                seenFeeds = favoriteFeeds
            },
        )

        state.restoreBackupState(
            settings = state.captureSettings(),
            joinedCommunityIdsByAccount = null,
            favoriteCommunityNamesByAccount = emptyMap(),
            favoriteCustomFeedPathsByAccount = emptyMap(),
        )

        assertEquals(emptyMap<String, Set<String>>(), seenCommunities)
        assertEquals(emptyMap<String, Set<String>>(), seenFeeds)
        assertTrue(state.favoriteCommunityNames.isEmpty())
        assertTrue(state.favoriteCustomFeedPaths.isEmpty())
    }

    @Test
    fun disconnectedStableIdRowsStayPinnedAndDoNotProjectIntoActiveAccount() {
        val futureId = "future-stable-id"
        var seenCommunities: Map<String, Set<String>>? = null
        var seenFeeds: Map<String, Set<String>>? = null
        val state = state(
            communities = mutableMapOf(rootId to setOf("android")),
            feeds = mutableMapOf(rootId to setOf("/user/root/m/dev")),
            persistBackupRestoreState = { _, _, favoriteCommunities, favoriteFeeds, _, _, _, _, _, _ ->
                seenCommunities = favoriteCommunities
                seenFeeds = favoriteFeeds
            },
        )

        state.restoreBackupState(
            settings = state.captureSettings(),
            joinedCommunityIdsByAccount = null,
            favoriteCommunityNamesByAccount = mapOf(futureId to setOf("subshell")),
            favoriteCustomFeedPathsByAccount =
                mapOf(futureId to setOf("/user/root/m/dev")),
        )

        assertEquals(mapOf(futureId to setOf("subshell")), seenCommunities)
        assertEquals(mapOf(futureId to setOf("/user/root/m/dev")), seenFeeds)
        assertEquals(setOf("android"), state.favoriteCommunityNames.toSet())
        assertEquals(setOf("/user/root/m/dev"), state.favoriteCustomFeedPaths.toSet())
    }

    @Test
    fun failedTransactionalPersistencePreventsAllLiveProjection() {
        val state = state(
            communities = mutableMapOf(rootId to setOf("android")),
            feeds = mutableMapOf(rootId to setOf("/user/root/m/dev")),
            persistBackupRestoreState = { _, _, _, _, _, _, _, _, _, _ ->
                error("simulated durable commit failure")
            },
        )
        val beforeSettings = state.captureSettings()
        val targetSettings = beforeSettings.copy(dynamicColor = !beforeSettings.dynamicColor)

        val result = runCatching {
            state.restoreBackupState(
                settings = targetSettings,
                joinedCommunityIdsByAccount = null,
                favoriteCommunityNamesByAccount = emptyMap(),
                favoriteCustomFeedPathsByAccount = emptyMap(),
            )
        }

        assertTrue(result.isFailure)
        assertEquals(beforeSettings, state.captureSettings())
        assertEquals(setOf("android"), state.favoriteCommunityNames.toSet())
        assertEquals(setOf("/user/root/m/dev"), state.favoriteCustomFeedPaths.toSet())
    }

    private fun state(
        communities: MutableMap<String, Set<String>>,
        feeds: MutableMap<String, Set<String>>,
        persistFavoriteCommunities: (String, Set<String>) -> Unit = { id, values ->
            communities[id.trim().lowercase()] = values.toSet()
        },
        persistFavoriteFeeds: (String, Set<String>) -> Unit = { id, values ->
            feeds[id.trim().lowercase()] = values.toSet()
        },
        persistBackupRestoreState: (
            Map<String, String>,
            Map<String, Set<String>>?,
            Map<String, Set<String>>?,
            Map<String, Set<String>>?,
            Map<String, Set<String>>?,
                    Map<String, Set<String>>?,
                    List<app.subshell.prototype.ui.PostComposerDraft>?,
                    List<app.subshell.prototype.data.moderation.ModeratorSavedResponse>?,
                    Map<String, Map<String, app.subshell.prototype.ui.UserTag>>?,
                    Map<String, List<app.subshell.prototype.ui.SavedSearch>>?,
) -> Unit = { _, _, _, _, _, _, _, _, _, _ -> },
    ): SubshellState = SubshellState(
        initialAccountSessions = sessions,
        initialActiveAccountUsername = "root",
        loadFavoriteCommunityNames = { id ->
            communities[id.trim().lowercase()].orEmpty()
        },
        persistFavoriteCommunityNames = persistFavoriteCommunities,
        loadFavoriteCustomFeedPaths = { id ->
            feeds[id.trim().lowercase()].orEmpty()
        },
        persistFavoriteCustomFeedPaths = persistFavoriteFeeds,
        persistBackupRestoreState = persistBackupRestoreState,
    )

    private fun account(username: String): AccountSessionSummary =
        sessions.first { it.username.equals(username, ignoreCase = true) }

}
