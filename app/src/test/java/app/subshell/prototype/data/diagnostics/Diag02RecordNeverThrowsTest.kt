package app.subshell.prototype.data.diagnostics

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test

/**
 * DIAG02 (#104): the Diagnostics screen records "diagnostics opened" without a
 * catch, so a failed write (disk full, a directory or buffer that cannot be
 * created or replaced) crashed the app exactly when the user opened
 * Diagnostics to report a problem. A failed record returns the readable
 * history instead of throwing.
 */
class Diag02RecordNeverThrowsTest {
    @Test
    fun aDirectoryThatCannotBeCreatedReturnsAnEmptyHistory() {
        val parent = Files.createTempDirectory("diag02").toFile()
        try {
            // A file where the directory should be: mkdirs fails.
            val blocked = File(parent, "diagnostics").apply { writeText("not a directory") }
            val store = DiagnosticEventStore(directory = blocked, maxEvents = 20, maxBytes = 8192)

            val opened = store.record(DiagnosticEvents.diagnosticsOpened())
            val scoped = store.recordViewportFailure("home", details = null)

            assertTrue(opened.events.isEmpty())
            assertTrue(scoped.events.isEmpty())
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun aBufferThatCannotBeWrittenKeepsTheReadableHistory() {
        val directory = Files.createTempDirectory("diag02").toFile()
        try {
            val store = DiagnosticEventStore(directory = directory, maxEvents = 20, maxBytes = 8192)
            store.recordUserIncident(nowEpochMs = 1_000L)
            // LOW_DIAG_EVENTS_ARE_APPENDED_V1: an event is appended to the
            // buffer file, which needs no directory write; the file is
            // read-only too, so the write fails.
            directory.listFiles().orEmpty().forEach { file -> file.setWritable(false) }
            directory.setWritable(false)
            // A process that can write anyway (root) cannot show the failure.
            assumeFalse(File(directory, "probe").let { runCatching { it.createNewFile() }.getOrDefault(false) })

            val opened = store.record(DiagnosticEvents.diagnosticsOpened())

            assertEquals(
                listOf(DiagnosticOperation.USER_INCIDENT_MARKED),
                opened.events.map { it.operation },
            )
        } finally {
            directory.setWritable(true)
            directory.deleteRecursively()
        }
    }
}
