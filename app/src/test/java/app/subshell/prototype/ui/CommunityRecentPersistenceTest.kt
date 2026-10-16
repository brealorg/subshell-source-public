package app.subshell.prototype.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import app.subshell.prototype.ui.screens.formatRecentCommunityAge

class CommunityRecentPersistenceTest {
    @Test
    fun openingCommunityRecordsNewestFirstAndPersistsByStableAccountId() {
        var now = 100L
        val writes = mutableListOf<Pair<String, List<RecentCommunityVisit>>>()
        val state = SubshellState(
            initialActiveAccountUsername = "root",
            loadRecentCommunityVisits = { emptyList() },
            persistRecentCommunityVisits = { accountId, visits ->
                writes += accountId to visits
            },
            currentTimeMillis = { now },
        )

        state.openSubreddit("r/Android")
        now = 200L
        state.openSubreddit("r/linux")

        assertEquals(
            listOf("linux", "android"),
            state.recentCommunityVisits.map(RecentCommunityVisit::communityName),
        )
        assertEquals("local:root", writes.last().first)
        assertEquals(
            listOf("linux", "android"),
            writes.last().second.map(RecentCommunityVisit::communityName),
        )
    }

    @Test
    fun reopeningCommunityDedupesAndRefreshesItsPosition() {
        var now = 10L
        val state = SubshellState(
            initialActiveAccountUsername = "root",
            currentTimeMillis = { now },
        )

        state.openSubreddit("android")
        now = 20L
        state.openSubreddit("linux")
        now = 30L
        state.navigate(MainDestination.HOME)
        state.openSubreddit("android")

        assertEquals(
            listOf("android", "linux"),
            state.recentCommunityVisits.map(RecentCommunityVisit::communityName),
        )
        assertEquals(30L, state.recentCommunityVisits.first().visitedAtEpochMillis)
    }

    @Test
    fun historyNormalizesDedupesAndKeepsTwentyNewest() {
        val normalized = normalizeRecentCommunityVisits(
            (0..24).map { index ->
                RecentCommunityVisit(
                    communityName = "community_$index",
                    visitedAtEpochMillis = index.toLong(),
                )
            } + listOf(
                RecentCommunityVisit("r/community_24", 100L),
                RecentCommunityVisit("not a subreddit", 999L),
            ),
        )

        assertEquals(RECENT_COMMUNITY_HISTORY_LIMIT, normalized.size)
        assertEquals("community_24", normalized.first().communityName)
        assertEquals(100L, normalized.first().visitedAtEpochMillis)
        assertFalse(normalized.any { it.communityName == "not a subreddit" })
    }

    @Test
    fun recentHistoryIncludesFavoritesBecauseItIsVisitHistory() {
        val state = SubshellState(
            initialActiveAccountUsername = "root",
            loadFavoriteCommunityNames = { setOf("android") },
            loadRecentCommunityVisits = {
                listOf(
                    RecentCommunityVisit("android", 200L),
                    RecentCommunityVisit("linux", 100L),
                )
            },
        )

        assertEquals(
            listOf("android", "linux"),
            state.recentCommunities().map { community -> community.name },
        )
        assertEquals(
            listOf("android", "linux"),
            state.recentCommunityVisits.map(RecentCommunityVisit::communityName),
        )
    }

    @Test
    fun accountSwitchLoadsIndependentRecentHistory() {
        val stored = mutableMapOf(
            "local:root" to listOf(RecentCommunityVisit("android", 100L)),
            "local:packetmoss" to listOf(RecentCommunityVisit("linux", 200L)),
        )
        val state = SubshellState(
            initialActiveAccountUsername = "root",
            loadRecentCommunityVisits = { accountId -> stored[accountId].orEmpty() },
            persistRecentCommunityVisits = { accountId, visits -> stored[accountId] = visits },
            currentTimeMillis = { 300L },
        )

        assertEquals("android", state.recentCommunityVisits.single().communityName)

        state.switchAccount("packetmoss")
        assertEquals("linux", state.recentCommunityVisits.single().communityName)

        state.openSubreddit("homelab")
        state.switchAccount("root")

        assertEquals("android", state.recentCommunityVisits.single().communityName)
        assertEquals(
            listOf("homelab", "linux"),
            stored.getValue("local:packetmoss").map(RecentCommunityVisit::communityName),
        )
    }

    @Test
    fun clearPersistsEmptyHistoryAndPreferenceKeyIsAccountScoped() {
        var persisted: List<RecentCommunityVisit>? = null
        val state = SubshellState(
            initialActiveAccountUsername = "root",
            loadRecentCommunityVisits = {
                listOf(RecentCommunityVisit("android", 100L))
            },
            persistRecentCommunityVisits = { _, visits -> persisted = visits },
        )

        state.clearRecentCommunities()

        assertTrue(state.recentCommunityVisits.isEmpty())
        assertEquals(emptyList<RecentCommunityVisit>(), persisted)
        assertEquals(
            "recent_community_visits.reddit:t2_example",
            recentCommunitiesPreferenceKey(" Reddit:T2_Example "),
        )
    }

    @Test
    fun drawerRecentSettingDefaultsOffAndRoundTripsThroughSettingsStorage() {
        val defaults = SubshellSettings()
        assertFalse(defaults.drawerRecentCommunities)

        val restored = SubshellSettings.fromStorageMap(
            defaults.copy(drawerRecentCommunities = true).toStorageMap(),
        )
        assertTrue(restored.drawerRecentCommunities)
    }
    @Test
    fun recentAgeUsesCompactRelativeLabels() {
        val minute = 60_000L
        val now = 10L * 24L * 60L * minute

        assertEquals("Just now", formatRecentCommunityAge(now - 30_000L, now))
        assertEquals("18 min ago", formatRecentCommunityAge(now - 18L * minute, now))
        assertEquals("3 h ago", formatRecentCommunityAge(now - 3L * 60L * minute, now))
        assertEquals("Yesterday", formatRecentCommunityAge(now - 30L * 60L * minute, now))
        assertEquals("3 d ago", formatRecentCommunityAge(now - 3L * 24L * 60L * minute, now))
    }

}
