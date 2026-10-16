package app.subshell.prototype.backup

import app.subshell.prototype.data.moderation.ModeratorSavedResponse
import app.subshell.prototype.ui.SubshellSettings
import app.subshell.prototype.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Backup04A4ModeratorSavedResponsesTest {
    @Test
    fun schemaEightRoundTripsAndOlderPayloadsCarryNoModule() {
        val snapshot = snapshot(
            listOf(
                response("b", "Zeta", "Body Z", 5L),
                response("a", "alpha", "Body A", 1L),
                response("a", "alpha dup", "dup", 9L),
            ),
        )
        val encoded = SettingsSnapshotCodec.encode(snapshot)
        assertTrue(encoded.contains("\"schemaVersion\": $SETTINGS_BACKUP_SCHEMA_VERSION"))
        assertTrue(encoded.contains("\"moderatorSavedResponses\""))

        val decoded = SettingsSnapshotCodec.decode(encoded)
        assertEquals(
            listOf(
                PortableModeratorSavedResponse("a", "alpha dup", "dup", 9L),
                PortableModeratorSavedResponse("b", "Zeta", "Body Z", 5L),
            ),
            decoded.moderatorSavedResponses,
        )

        // Present-but-empty is authoritative; a schema 7 payload has no module.
        val empty = SettingsSnapshotCodec.encode(snapshot(emptyList()))
        assertEquals(emptyList<PortableModeratorSavedResponse>(), SettingsSnapshotCodec.decode(empty).moderatorSavedResponses)
        val schemaSeven = encoded.replace("\"schemaVersion\": $SETTINGS_BACKUP_SCHEMA_VERSION", "\"schemaVersion\": 7")
        assertNull(SettingsSnapshotCodec.decode(schemaSeven).moderatorSavedResponses)
        val absent = SettingsSnapshotCodec.encode(snapshot(null))
        assertFalse(absent.contains("moderatorSavedResponses"))
        assertNull(SettingsSnapshotCodec.decode(absent).moderatorSavedResponses)
    }

    @Test
    fun normalizationBoundsAndDropsInvalidRows() {
        val normalized = normalizePortableModeratorSavedResponses(
            listOf(
                response("", "no id", "body", 1L),
                response("x", "   ", "body", 1L),
                response("y", "title", "   ", 1L),
                response("z", "neg", "body", -1L),
                response("ok", "  Keep  ", "  Body  ", 2L),
            ) + (1..60).map { index -> response("many-$index", "T$index", "b", 3L) },
        )
        assertEquals(BACKUP04A4_MAX_SAVED_RESPONSES, normalized.size)
        assertTrue(normalized.any { it.id == "ok" && it.title == "Keep" && it.body == "Body" })
        assertTrue(normalized.none { it.id in setOf("", "x", "y", "z") })
    }

    @Test
    fun restoreLeavesMissingModuleAloneAndClearsOnExplicitEmpty() {
        var persisted: List<ModeratorSavedResponse>? = null
        var persistCalls = 0
        val state = SubshellState(
            initialModeratorSavedResponses = listOf(
                ModeratorSavedResponse("keep", "Keep", "Body", 1L),
            ),
            persistBackupRestoreState = { _, _, _, _, _, _, _, responses, _, _ ->
                persistCalls += 1
                persisted = responses
            },
        )

        state.restoreBackupState(settings = state.captureSettings(), joinedCommunityIdsByAccount = null)
        assertNull("missing module: nothing staged", persisted)
        assertEquals(listOf("Keep"), state.moderatorSavedResponses.map { it.title })

        state.restoreBackupState(
            settings = state.captureSettings(),
            joinedCommunityIdsByAccount = null,
            moderatorSavedResponses = listOf(PortableModeratorSavedResponse("new", "New", "N", 5L)),
        )
        assertEquals(listOf("New"), persisted?.map { it.title })
        assertEquals(listOf("New"), state.moderatorSavedResponses.map { it.title })

        state.restoreBackupState(
            settings = state.captureSettings(),
            joinedCommunityIdsByAccount = null,
            moderatorSavedResponses = emptyList(),
        )
        assertEquals(emptyList<ModeratorSavedResponse>(), persisted)
        assertTrue(state.moderatorSavedResponses.isEmpty())
        assertEquals(3, persistCalls)

        assertEquals(
            listOf(PortableModeratorSavedResponse("r", "R", "B", 7L)),
            SubshellState(
                initialModeratorSavedResponses = listOf(ModeratorSavedResponse("r", "R", "B", 7L)),
            ).captureModeratorSavedResponses(),
        )
    }

    @Test
    fun liveStateOnlyChangesAfterTheDurableCommit() {
        val state = SubshellState(
            initialModeratorSavedResponses = listOf(ModeratorSavedResponse("keep", "Keep", "Body", 1L)),
            persistBackupRestoreState = { _, _, _, _, _, _, _, _, _, _ -> error("simulated durable commit failure") },
        )
        val failed = runCatching {
            state.restoreBackupState(
                settings = state.captureSettings(),
                joinedCommunityIdsByAccount = null,
                moderatorSavedResponses = emptyList(),
            )
        }
        assertTrue(failed.isFailure)
        assertEquals(listOf("Keep"), state.moderatorSavedResponses.map { it.title })
    }

    private fun response(id: String, title: String, body: String, updated: Long) =
        PortableModeratorSavedResponse(id = id, title = title, body = body, updatedAtEpochMs = updated)

    private fun snapshot(responses: List<PortableModeratorSavedResponse>?) = SettingsSnapshot(
        id = "00000000-0000-4000-8000-0000000000a4",
        name = "BACKUP04A4",
        createdAtEpochMillis = 1_000L,
        appVersion = "test",
        kind = SnapshotKind.MANUAL,
        deviceId = "device",
        settings = SubshellSettings(),
        moderatorSavedResponses = responses,
    )
}
