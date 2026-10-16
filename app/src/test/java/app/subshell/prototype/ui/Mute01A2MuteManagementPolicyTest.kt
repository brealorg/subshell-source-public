package app.subshell.prototype.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Mute01A2MuteManagementPolicyTest {
    @Test
    fun managementOrdersNearestTimedExpiryBeforeIndefiniteMutes() {
        val ordered = communityMutesForManagement(
            mutes = listOf(
                CommunityMute("zebra", null),
                CommunityMute("motogp", 50_000L),
                CommunityMute("android", 20_000L),
                CommunityMute("expired", 9_999L),
                CommunityMute("apple", null),
            ),
            nowEpochMillis = 10_000L,
        )

        assertEquals(
            listOf("android", "motogp", "apple", "zebra"),
            ordered.map(CommunityMute::communityName),
        )
    }

    @Test
    fun exactCommunityLookupIsCaseInsensitiveAndExpiryAware() {
        val mutes = listOf(
            CommunityMute("MotoGP", 20_000L),
            CommunityMute("formula1", null),
        )

        assertEquals(
            CommunityMute("motogp", 20_000L),
            communityMuteFor(mutes, "r/motogp", 19_999L),
        )
        assertNull(communityMuteFor(mutes, "MOTOGP", 20_000L))
        assertNull(communityMuteFor(mutes, "motogpnews", 19_999L))
    }

    @Test
    fun stateManagementProjectionIsAccountScopedAndSorted() {
        val stored = mapOf(
            "local:root" to setOf(
                "indefinite|formula1",
                "30000|motogp",
                "20000|android",
            ),
        )
        val state = SubshellState(
            initialActiveAccountUsername = "root",
            currentTimeMillis = { 10_000L },
            loadCommunityMuteEntries = { accountId -> stored[accountId].orEmpty() },
        )

        assertEquals(
            listOf("android", "motogp", "formula1"),
            state.activeCommunityMuteEntries().map(CommunityMute::communityName),
        )
        assertEquals(
            CommunityMute("motogp", 30_000L),
            state.communityMuteFor("r/MotoGP"),
        )
    }
}
