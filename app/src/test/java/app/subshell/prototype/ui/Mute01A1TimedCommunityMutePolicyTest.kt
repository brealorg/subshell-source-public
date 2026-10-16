package app.subshell.prototype.ui

import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Mute01A1TimedCommunityMutePolicyTest {
    @Test
    fun storageRoundTripsNormalizedTimedAndIndefiniteMutes() {
        val mutes = listOf(
            CommunityMute("r/MotoGP", expiresAtEpochMillis = 20_000L),
            CommunityMute("android", expiresAtEpochMillis = null),
            CommunityMute("MOTOGP", expiresAtEpochMillis = 30_000L),
        )

        val decoded = decodeCommunityMutes(encodeCommunityMutes(mutes))

        assertEquals(
            listOf(
                CommunityMute("android", null),
                CommunityMute("motogp", 30_000L),
            ),
            decoded,
        )
    }

    @Test
    fun expirationBoundaryIsImmediateAndIndefiniteMuteRemainsActive() {
        val timed = CommunityMute("motogp", expiresAtEpochMillis = 5_000L)
        val indefinite = CommunityMute("formula1", expiresAtEpochMillis = null)

        assertTrue(timed.activeAt(4_999L))
        assertFalse(timed.activeAt(5_000L))
        assertTrue(indefinite.activeAt(Long.MAX_VALUE))
        assertEquals(
            setOf("formula1"),
            activeMutedCommunityNamesAt(listOf(timed, indefinite), 5_000L),
        )
    }

    @Test
    fun mixedFeedsSuppressMutedCommunitiesWithoutReorderingSurvivors() {
        val posts = listOf(
            post("first", "android"),
            post("spoiler", "MotoGP"),
            post("last", "space"),
        )

        val home = filterMutedCommunityPosts(
            posts = posts,
            source = FeedSource.HOME,
            mutedCommunityNames = setOf("motogp"),
        )
        val popular = filterMutedCommunityPosts(
            posts = posts,
            source = FeedSource.POPULAR,
            mutedCommunityNames = setOf("r/MotoGP"),
        )
        val saved = filterMutedCommunityPosts(
            posts = posts,
            source = FeedSource.SAVED,
            mutedCommunityNames = setOf("motogp"),
        )

        assertEquals(listOf("first", "last"), home.map(Post::id))
        assertEquals(listOf("first", "last"), popular.map(Post::id))
        assertEquals(listOf("first", "spoiler", "last"), saved.map(Post::id))
    }

    @Test
    fun statePersistsPerAccountAndPrunesExpiredMuteOnClockRefresh() {
        var now = 1_000L
        val stored = mutableMapOf<String, Set<String>>()
        val state = SubshellState(
            initialActiveAccountUsername = "root",
            currentTimeMillis = { now },
            loadCommunityMuteEntries = { accountId -> stored[accountId].orEmpty() },
            persistCommunityMuteEntries = { accountId, entries -> stored[accountId] = entries },
        )

        assertTrue(state.muteCommunity("r/MotoGP", expiresAtEpochMillis = 2_000L))
        assertTrue(state.isCommunityMuted("motogp"))
        assertEquals(
            setOf("2000|motogp"),
            stored.getValue("local:root"),
        )

        state.switchAccount("packetmoss")
        assertFalse(state.isCommunityMuted("motogp"))
        assertTrue(state.muteCommunity("android", expiresAtEpochMillis = null))

        state.switchAccount("root")
        assertTrue(state.isCommunityMuted("MOTOGP"))
        assertFalse(state.isCommunityMuted("android"))

        now = 2_000L
        state.refreshCommunityMuteClock()

        assertFalse(state.isCommunityMuted("motogp"))
        assertTrue(state.communityMutes.isEmpty())
        assertEquals(emptySet<String>(), stored.getValue("local:root"))
        assertEquals(
            setOf("indefinite|android"),
            stored.getValue("local:packetmoss"),
        )
        assertNull(state.nextCommunityMuteExpiryEpochMillis)
    }

    @Test
    fun invalidAndPastMuteRequestsDoNotPersist() {
        var writes = 0
        val state = SubshellState(
            initialActiveAccountUsername = "root",
            currentTimeMillis = { 10_000L },
            persistCommunityMuteEntries = { _, _ -> writes += 1 },
        )

        assertFalse(state.muteCommunity("not a subreddit", null))
        assertFalse(state.muteCommunity("motogp", 10_000L))
        assertFalse(state.muteCommunity("motogp", 9_999L))
        assertEquals(0, writes)
        assertTrue(state.communityMutes.isEmpty())
    }

    private fun post(id: String, community: String): Post = Post(
        id = id,
        community = community,
        author = "author",
        age = "1h",
        title = id,
        body = "",
        score = 1,
        comments = 1,
        kind = PostKind.TEXT,
    )
}
