package app.subshell.prototype.data.diagnostics

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Diag01A3UserIncidentPolicyTest {
    @Test
    fun userIncidentMarkerIsTypedContextFreeAndPrivacyBounded() {
        val event = DiagnosticEvents.userIncidentMarked(nowEpochMs = 42L)

        assertEquals(42L, event.timestampEpochMs)
        assertEquals(DiagnosticLevel.WARNING, event.level)
        assertEquals(DiagnosticSubsystem.UI, event.subsystem)
        assertEquals(DiagnosticOperation.USER_INCIDENT_MARKED, event.operation)
        assertEquals(DiagnosticOutcome.OBSERVED, event.outcome)
        assertNull(event.failure)
        assertNull(event.scopeFingerprint)
    }

    @Test
    fun userIncidentMarkerRoundTripsWithoutScopeOrFailurePayload() =
        withTempDirectory { directory ->
            val store = DiagnosticEventStore(
                directory = directory,
                maxEvents = 20,
                maxBytes = 8192,
            )

            store.recordUserIncident(nowEpochMs = 1_234L)

            val first = store.snapshot().events.single()
            assertEquals(DiagnosticOperation.USER_INCIDENT_MARKED, first.operation)
            assertNull(first.failure)
            assertNull(first.scopeFingerprint)

            val reloaded = DiagnosticEventStore(
                directory = directory,
                maxEvents = 20,
                maxBytes = 8192,
            ).snapshot().events.single()

            assertEquals(first.id, reloaded.id)
            assertEquals(1_234L, reloaded.timestampEpochMs)
            assertEquals(DiagnosticLevel.WARNING, reloaded.level)
            assertEquals(DiagnosticSubsystem.UI, reloaded.subsystem)
            assertEquals(DiagnosticOperation.USER_INCIDENT_MARKED, reloaded.operation)
            assertEquals(DiagnosticOutcome.OBSERVED, reloaded.outcome)
            assertNull(reloaded.failure)
            assertNull(reloaded.scopeFingerprint)
        }

    private inline fun withTempDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("subshell-diag01a3").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
