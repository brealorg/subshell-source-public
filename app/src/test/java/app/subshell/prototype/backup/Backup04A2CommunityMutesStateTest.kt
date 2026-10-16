package app.subshell.prototype.backup

import app.subshell.prototype.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Backup04A2CommunityMutesStateTest {
    @Test
    fun captureUsesOpaqueStableIdsIncludesEmptyRowsAndDropsExpiredEntries() {
        var now = 10_000L
        val stored = mapOf(
            "local:packetmoss" to setOf(
                "indefinite|android",
                "9000|expired",
            ),
        )
        val state = SubshellState(
            initialActiveAccountUsername = "root",
            currentTimeMillis = { now },
            loadCommunityMuteEntries = { accountId -> stored[accountId].orEmpty() },
        )
        assertTrue(state.muteCommunity("r/MotoGP", 30_000L))

        val captured = state.captureCommunityMutesByAccount()

        assertEquals(
            listOf(PortableCommunityMute("motogp", 30_000L)),
            captured["local:root"],
        )
        assertEquals(
            listOf(PortableCommunityMute("android", null)),
            captured["local:packetmoss"],
        )
        assertTrue(captured.containsKey("local:root"))
        assertTrue(captured.containsKey("local:packetmoss"))
    }

    @Test
    fun nullModuleIsNoopAndDisconnectedRowDoesNotChangeActiveMuteState() {
        var persisted: Map<String, Set<String>>? = null
        val state = SubshellState(
            initialActiveAccountUsername = "root",
            currentTimeMillis = { 10_000L },
            loadCommunityMuteEntries = { accountId ->
                if (accountId == "local:root") setOf("indefinite|motogp") else emptySet()
            },
            persistBackupRestoreState = { _, _, _, _, _, mutes, _, _, _, _ -> persisted = mutes },
        )

        state.restoreBackupState(
            settings = state.captureSettings(),
            joinedCommunityIdsByAccount = null,
            communityMutesByAccount = null,
        )
        assertNull(persisted)
        assertTrue(state.isCommunityMuted("motogp"))

        state.restoreBackupState(
            settings = state.captureSettings(),
            joinedCommunityIdsByAccount = null,
            communityMutesByAccount = mapOf(
                "local:packetmoss" to listOf(PortableCommunityMute("android", null)),
            ),
        )
        assertEquals(setOf("indefinite|android"), persisted?.get("local:packetmoss"))
        assertTrue(state.isCommunityMuted("motogp"))
        assertFalse(state.isCommunityMuted("android"))
    }

    @Test
    fun explicitEmptyModuleClearsActiveAndExpiredTimedMuteIsNeverPersisted() {
        var persisted: Map<String, Set<String>>? = null
        val state = SubshellState(
            initialActiveAccountUsername = "root",
            currentTimeMillis = { 10_000L },
            loadCommunityMuteEntries = { setOf("indefinite|motogp") },
            persistBackupRestoreState = { _, _, _, _, _, mutes, _, _, _, _ -> persisted = mutes },
        )

        state.restoreBackupState(
            settings = state.captureSettings(),
            joinedCommunityIdsByAccount = null,
            communityMutesByAccount = mapOf(
                "local:root" to listOf(
                    PortableCommunityMute("expired", 10_000L),
                    PortableCommunityMute("android", 20_000L),
                ),
            ),
        )
        assertEquals(setOf("20000|android"), persisted?.get("local:root"))
        assertFalse(state.isCommunityMuted("expired"))
        assertTrue(state.isCommunityMuted("android"))

        state.restoreBackupState(
            settings = state.captureSettings(),
            joinedCommunityIdsByAccount = null,
            communityMutesByAccount = emptyMap(),
        )
        assertEquals(emptyMap<String, Set<String>>(), persisted)
        assertTrue(state.communityMutes.isEmpty())
        assertNull(state.nextCommunityMuteExpiryEpochMillis)
    }

    @Test
    fun durableFailurePreventsLiveMuteProjection() {
        val state = SubshellState(
            initialActiveAccountUsername = "root",
            currentTimeMillis = { 10_000L },
            loadCommunityMuteEntries = { setOf("indefinite|motogp") },
            persistBackupRestoreState = { _, _, _, _, _, _, _, _, _, _ ->
                error("durable failure")
            },
        )

        val failed = runCatching {
            state.restoreBackupState(
                settings = state.captureSettings(),
                joinedCommunityIdsByAccount = null,
                communityMutesByAccount = mapOf(
                    "local:root" to listOf(PortableCommunityMute("android", null)),
                ),
            )
        }
        assertTrue(failed.isFailure)
        assertTrue(state.isCommunityMuted("motogp"))
        assertFalse(state.isCommunityMuted("android"))
    }
}
