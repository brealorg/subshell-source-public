package app.subshell.prototype.backup

import app.subshell.prototype.ui.SubshellSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Backup04A2CommunityMutesCodecTest {
    @Test
    fun schemaSixRoundTripsStructuredAbsoluteAndIndefiniteMutes() {
        val snapshot = snapshot(
            communityMutesByAccount = mapOf(
                " Reddit:Case " to listOf(
                    PortableCommunityMute("r/MotoGP", 30_000L),
                    PortableCommunityMute("MOTOGP", 20_000L),
                    PortableCommunityMute("android", null),
                ),
                "local:empty" to emptyList(),
            ),
        )

        val encoded = SettingsSnapshotCodec.encode(snapshot)
        assertTrue(encoded.contains("\"schemaVersion\": $SETTINGS_BACKUP_SCHEMA_VERSION"))
        assertTrue(encoded.contains("\"communityMutes\""))
        assertTrue(encoded.contains("\"communityName\": \"motogp\""))
        assertTrue(encoded.contains("\"expiresAtEpochMillis\": 30000"))
        assertTrue(encoded.contains("\"expiresAtEpochMillis\": null"))
        assertTrue(encoded.contains("\"Reddit:Case\""))

        val decoded = SettingsSnapshotCodec.decode(encoded, nowEpochMillis = 10_000L)
        assertEquals(
            listOf(
                PortableCommunityMute("android", null),
                PortableCommunityMute("motogp", 30_000L),
            ),
            decoded.communityMutesByAccount?.get("Reddit:Case"),
        )
        assertEquals(emptyList<PortableCommunityMute>(), decoded.communityMutesByAccount?.get("local:empty"))
    }

    @Test
    fun decodeDropsExpiredMalformedAndInvalidRowsWithoutRestartingDuration() {
        val encoded = SettingsSnapshotCodec.encode(
            snapshot(
                communityMutesByAccount = mapOf(
                    "CaseOwner" to listOf(
                        PortableCommunityMute("motogp", 10_000L),
                        PortableCommunityMute("android", 50_000L),
                        PortableCommunityMute("formula1", null),
                    ),
                ),
            ),
        )
        val malformed = encoded.replace(
            "\"communityName\": \"android\"",
            "\"communityName\": \"not a subreddit\"",
        )

        val decoded = SettingsSnapshotCodec.decode(malformed, nowEpochMillis = 10_000L)
        assertEquals(
            listOf(PortableCommunityMute("formula1", null)),
            decoded.communityMutesByAccount?.get("CaseOwner"),
        )
        assertFalse(decoded.communityMutesByAccount.orEmpty().containsKey("caseowner"))
    }

    @Test
    fun schemaFiveIgnoresCommunityMutePayloadAndExplicitEmptyModuleRoundTrips() {
        val encoded = SettingsSnapshotCodec.encode(
            snapshot(communityMutesByAccount = emptyMap()),
        )
        val decodedEmpty = SettingsSnapshotCodec.decode(encoded, nowEpochMillis = 1L)
        assertEquals(emptyMap<String, List<PortableCommunityMute>>(), decodedEmpty.communityMutesByAccount)

        val forgedSchemaFive = encoded.replace("\"schemaVersion\": $SETTINGS_BACKUP_SCHEMA_VERSION", "\"schemaVersion\": 5")
        val decodedLegacy = SettingsSnapshotCodec.decode(forgedSchemaFive, nowEpochMillis = 1L)
        assertNull(decodedLegacy.communityMutesByAccount)
    }

    private fun snapshot(
        communityMutesByAccount: Map<String, List<PortableCommunityMute>>?,
    ) = SettingsSnapshot(
        id = "00000000-0000-4000-8000-0000000000a2",
        name = "BACKUP04A2",
        createdAtEpochMillis = 1_000L,
        appVersion = "test",
        kind = SnapshotKind.MANUAL,
        deviceId = "device",
        settings = SubshellSettings(),
        communityMutesByAccount = communityMutesByAccount,
    )
}
