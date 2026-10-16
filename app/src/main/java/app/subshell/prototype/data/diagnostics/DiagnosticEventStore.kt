package app.subshell.prototype.data.diagnostics

import android.content.Context
import java.io.File
import java.io.IOException
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import app.subshell.prototype.data.error.FailureDetails

internal class DiagnosticEventStore internal constructor(
    private val directory: File,
    private val maxEvents: Int = DEFAULT_MAX_DIAGNOSTIC_EVENTS,
    private val maxBytes: Int = DEFAULT_MAX_DIAGNOSTIC_BYTES,
) {
    constructor(context: Context) : this(
        directory = File(context.applicationContext.noBackupFilesDir, DIAGNOSTIC_DIRECTORY_NAME),
    )

    init {
        require(maxEvents in 1..MAX_CONFIGURABLE_DIAGNOSTIC_EVENTS)
        require(maxBytes in MIN_CONFIGURABLE_DIAGNOSTIC_BYTES..MAX_CONFIGURABLE_DIAGNOSTIC_BYTES)
    }

    fun recordViewportFailure(
        scopeKey: String,
        details: FailureDetails?,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): DiagnosticSnapshot = recordingSafely {
        require(scopeKey.isNotBlank())
        recordLocked(
            DiagnosticEvents.viewportFailure(
                subsystem = diagnosticSubsystemForScope(scopeKey),
                scopeFingerprint = scopeFingerprint(scopeKey),
                details = details,
                nowEpochMs = nowEpochMs,
            ),
        )
    }

    fun recordPresentedFailure(
        scopeKey: String,
        presentationKind: DiagnosticPresentationKind,
        details: FailureDetails?,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): DiagnosticSnapshot = recordingSafely {
        require(scopeKey.isNotBlank())
        recordLocked(
            DiagnosticEvents.presentedFailure(
                subsystem = diagnosticSubsystemForScope(scopeKey),
                scopeFingerprint = scopeFingerprint(scopeKey),
                presentationKind = presentationKind,
                details = details,
                nowEpochMs = nowEpochMs,
            ),
        )
    }

    fun recordBackgroundFailure(
        scopeKey: String,
        details: FailureDetails?,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): DiagnosticSnapshot = recordingSafely {
        require(scopeKey.isNotBlank())
        recordLocked(
            DiagnosticEvents.backgroundFailure(
                subsystem = diagnosticSubsystemForScope(scopeKey),
                scopeFingerprint = scopeFingerprint(scopeKey),
                details = details,
                nowEpochMs = nowEpochMs,
            ),
        )
    }

    fun recordBackgroundAuthorizationRequired(
        scopeKey: String,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): DiagnosticSnapshot = recordingSafely {
        require(scopeKey.isNotBlank())
        recordLocked(
            DiagnosticEvents.backgroundAuthorizationRequired(
                subsystem = diagnosticSubsystemForScope(scopeKey),
                scopeFingerprint = scopeFingerprint(scopeKey),
                nowEpochMs = nowEpochMs,
            ),
        )
    }

    fun recordNotificationPostFailure(
        scopeKey: String,
        details: FailureDetails?,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): DiagnosticSnapshot = recordingSafely {
        require(scopeKey.isNotBlank())
        recordLocked(
            DiagnosticEvents.notificationPostFailure(
                subsystem = diagnosticSubsystemForScope(scopeKey),
                scopeFingerprint = scopeFingerprint(scopeKey),
                details = details,
                nowEpochMs = nowEpochMs,
            ),
        )
    }

    fun recordUserIncident(
        nowEpochMs: Long = System.currentTimeMillis(),
    ): DiagnosticSnapshot = recordingSafely {
        recordLocked(DiagnosticEvents.userIncidentMarked(nowEpochMs))
    }

    fun record(event: DiagnosticEvent): DiagnosticSnapshot = recordingSafely {
        recordLocked(event)
    }

    private fun recordLocked(event: DiagnosticEvent): DiagnosticSnapshot {
        val loaded = readState()
        val previous = loaded.events.lastOrNull()
        if (
            event.operation in PRESENTATION_DEDUPE_OPERATIONS &&
            previous?.operation == event.operation &&
            previous.dedupeKey() == event.dedupeKey() &&
            event.timestampEpochMs >= previous.timestampEpochMs &&
            event.timestampEpochMs - previous.timestampEpochMs <= VIEWPORT_DEDUPE_WINDOW_MS
        ) {
            return loaded.toSnapshot(
                fileBytes = dataFile.length(),
                maxEvents = maxEvents,
                maxBytes = maxBytes,
            )
        }

        // LOW_DIAG_EVENTS_ARE_APPENDED_V1 (OCB-623): while the buffer has room
        // and reads back cleanly, a new event is one appended line, not a
        // rewrite of the whole file.
        val line = (encodeEvent(event) + "\n").toByteArray(Charsets.UTF_8)
        val fileBytes = dataFile.length()
        if (
            fileBytes > 0L &&
            loaded.invalidRecordCount == 0 &&
            loaded.events.size + 1 <= maxEvents &&
            fileBytes + line.size <= maxBytes
        ) {
            FileOutputStream(dataFile, true).use { output ->
                output.write(line)
                output.fd.sync()
            }
            return loaded.copy(events = loaded.events + event).toSnapshot(
                fileBytes = fileBytes + line.size,
                maxEvents = maxEvents,
                maxBytes = maxBytes,
            )
        }

        val events = loaded.events.toMutableList().apply { add(event) }
        var dropped = loaded.droppedEventCount + loaded.invalidRecordCount

        while (events.size > maxEvents) {
            events.removeAt(0)
            dropped += 1L
        }

        var encoded = encodeState(dropped, events)
        while (encoded.size > maxBytes && events.isNotEmpty()) {
            events.removeAt(0)
            dropped += 1L
            encoded = encodeState(dropped, events)
        }

        writeEncodedState(encoded)
        return DiagnosticSnapshot(
            events = events.toList(),
            droppedEventCount = dropped,
            invalidRecordCount = 0,
            bytesUsed = encoded.size.toLong(),
            maxEvents = maxEvents,
            maxBytes = maxBytes,
        )
    }

    fun snapshot(): DiagnosticSnapshot = synchronized(PROCESS_LOCK) {
        snapshotLocked()
    }

    private fun snapshotLocked(): DiagnosticSnapshot =
        readState().toSnapshot(
            fileBytes = dataFile.length(),
            maxEvents = maxEvents,
            maxBytes = maxBytes,
        )

    // DIAG02_RECORD_NEVER_THROWS_V1 (#104): a diagnostic write that fails (disk
    // full, directory or buffer that cannot be created or replaced) must not
    // take the app down, least of all while the user opens Diagnostics to
    // report a problem. The event is lost and the readable history is returned.
    private inline fun recordingSafely(
        record: () -> DiagnosticSnapshot,
    ): DiagnosticSnapshot = synchronized(PROCESS_LOCK) {
        try {
            record()
        } catch (_: IOException) {
            snapshotLocked()
        } catch (_: IllegalStateException) {
            snapshotLocked()
        } catch (_: SecurityException) {
            snapshotLocked()
        }
    }

    fun clear() = synchronized(PROCESS_LOCK) {
        runCatching { dataFile.delete() }
        runCatching { temporaryFile.delete() }
    }

    private val dataFile: File
        get() = File(directory, DIAGNOSTIC_FILE_NAME)

    private val temporaryFile: File
        get() = File(directory, "$DIAGNOSTIC_FILE_NAME.tmp")

    private val saltFile: File
        get() = File(directory, DIAGNOSTIC_SALT_FILE_NAME)

    private fun scopeFingerprint(scopeKey: String): String {
        val salt = readOrCreateInstallSalt()
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        return mac.doFinal(scopeKey.toByteArray(Charsets.UTF_8))
            .take(SCOPE_FINGERPRINT_BYTES)
            .joinToString(separator = "") { byte ->
                "%02x".format(byte.toInt() and 0xff)
            }
    }

    private fun readOrCreateInstallSalt(): ByteArray {
        val existing = saltFile
            .takeIf { it.isFile && it.length() == DIAGNOSTIC_SALT_BYTES.toLong() }
            ?.readBytes()
        if (existing != null && existing.size == DIAGNOSTIC_SALT_BYTES) return existing

        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException("Unable to create diagnostics directory")
        }
        val salt = ByteArray(DIAGNOSTIC_SALT_BYTES).also(SecureRandom()::nextBytes)
        val temp = File(directory, "$DIAGNOSTIC_SALT_FILE_NAME.tmp")
        FileOutputStream(temp).use { output ->
            output.write(salt)
            output.fd.sync()
        }
        // LOW_DIAG_ATOMIC_REPLACE_V1 (OCB-624): rename replaces the target in
        // one step; deleting it first could leave no file at all.
        if (!temp.renameTo(saltFile)) {
            temp.delete()
            throw IllegalStateException("Unable to commit diagnostics salt")
        }
        return salt
    }

    private fun readState(): LoadedDiagnosticState {
        val target = dataFile
        if (!target.isFile || target.length() <= 0L) return LoadedDiagnosticState()
        if (target.length() > MAX_DIAGNOSTIC_READ_BYTES) {
            return LoadedDiagnosticState(
                invalidRecordCount = 1,
            )
        }

        return runCatching {
            val lines = target.readLines(Charsets.UTF_8)
            if (lines.isEmpty()) return@runCatching LoadedDiagnosticState()

            val header = decodeHeader(lines.first())
                ?: return@runCatching LoadedDiagnosticState(invalidRecordCount = lines.size)
            var invalid = 0
            val events = lines.drop(1).mapNotNull { line ->
                val event = decodeEvent(line)
                if (event == null) invalid += 1
                event
            }
            LoadedDiagnosticState(
                droppedEventCount = header,
                invalidRecordCount = invalid,
                events = events,
            )
        }.getOrElse {
            LoadedDiagnosticState(invalidRecordCount = 1)
        }
    }

    private fun writeEncodedState(encoded: ByteArray) {
        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException("Unable to create diagnostics directory")
        }
        val temp = temporaryFile
        FileOutputStream(temp).use { output ->
            output.write(encoded)
            output.fd.sync()
        }
        val target = dataFile
        if (!temp.renameTo(target)) {
            temp.delete()
            throw IllegalStateException("Unable to commit diagnostics buffer")
        }
    }

    private fun encodeState(
        droppedEventCount: Long,
        events: List<DiagnosticEvent>,
    ): ByteArray = buildString {
        append(DIAGNOSTIC_HEADER_PREFIX)
        append('|')
        append(droppedEventCount.coerceAtLeast(0L))
        append('\n')
        events.forEach { event ->
            append(encodeEvent(event))
            append('\n')
        }
    }.toByteArray(Charsets.UTF_8)

    private fun decodeHeader(line: String): Long? {
        val parts = line.split('|')
        if (parts.size != 2 || parts[0] != DIAGNOSTIC_HEADER_PREFIX) return null
        return parts[1].toLongOrNull()?.takeIf { it >= 0L }
    }

    private fun encodeEvent(event: DiagnosticEvent): String = listOf(
        EVENT_PREFIX,
        event.id,
        event.timestampEpochMs.toString(),
        event.level.name,
        event.subsystem.name,
        event.operation.name,
        event.outcome.name,
        event.failure?.httpStatusCode?.toString() ?: NULL_TOKEN,
        event.failure?.retryAfterMs?.toString() ?: NULL_TOKEN,
        event.failure?.retryable?.let { if (it) TRUE_TOKEN else FALSE_TOKEN } ?: NULL_TOKEN,
        event.failure?.transportKind ?: NULL_TOKEN,
        event.failure?.protocolKind ?: NULL_TOKEN,
        event.failure?.providerCode?.let(::encodeSafeText) ?: NULL_TOKEN,
        event.failure?.mediaCode?.let(::encodeSafeText) ?: NULL_TOKEN,
        event.failure?.localKind ?: NULL_TOKEN,
        event.scopeFingerprint ?: NULL_TOKEN,
        // DIAG03_JSON_FIELD_PATH_V1: appended last; a line without it is the
        // older 16-field shape and still reads (no path).
        event.failure?.jsonPath?.let(::encodeSafeText) ?: NULL_TOKEN,
    ).joinToString(separator = "|")

    private fun decodeEvent(line: String): DiagnosticEvent? {
        if (line.length > MAX_DIAGNOSTIC_RECORD_CHARS) return null
        val parts = line.split('|')
        if (parts.size !in LEGACY_EVENT_FIELD_COUNT..EVENT_FIELD_COUNT || parts[0] != EVENT_PREFIX) return null
        return runCatching {
            val failure = DiagnosticFailureSnapshot(
                httpStatusCode = parts[7].nullableInt(),
                retryAfterMs = parts[8].nullableLong(),
                retryable = parts[9].nullableBoolean(),
                transportKind = parts[10].nullableToken(),
                protocolKind = parts[11].nullableToken(),
                providerCode = parts[12].nullableSafeText(),
                mediaCode = parts[13].nullableSafeText(),
                localKind = parts[14].nullableToken(),
                jsonPath = parts.getOrNull(16)?.nullableSafeText(),
            ).takeUnless { it == DiagnosticFailureSnapshot() }

            DiagnosticEvent(
                id = parts[1],
                timestampEpochMs = parts[2].toLong(),
                level = DiagnosticLevel.valueOf(parts[3]),
                subsystem = DiagnosticSubsystem.valueOf(parts[4]),
                operation = DiagnosticOperation.valueOf(parts[5]),
                outcome = DiagnosticOutcome.valueOf(parts[6]),
                failure = failure,
                scopeFingerprint = parts[15].takeUnless { it == NULL_TOKEN },
            )
        }.getOrNull()
    }

    private fun String.nullableInt(): Int? =
        takeUnless { it == NULL_TOKEN }?.toInt()

    private fun String.nullableLong(): Long? =
        takeUnless { it == NULL_TOKEN }?.toLong()

    private fun String.nullableBoolean(): Boolean? = when (this) {
        NULL_TOKEN -> null
        TRUE_TOKEN -> true
        FALSE_TOKEN -> false
        else -> error("Invalid boolean token")
    }

    private fun String.nullableToken(): String? =
        takeUnless { it == NULL_TOKEN }

    private fun String.nullableSafeText(): String? =
        takeUnless { it == NULL_TOKEN }?.let(::decodeSafeText)

    companion object {
        private val PROCESS_LOCK = Any()
    }
}

internal data class DiagnosticSnapshot(
    val events: List<DiagnosticEvent> = emptyList(),
    val droppedEventCount: Long = 0L,
    val invalidRecordCount: Int = 0,
    val bytesUsed: Long = 0L,
    val maxEvents: Int = DEFAULT_MAX_DIAGNOSTIC_EVENTS,
    val maxBytes: Int = DEFAULT_MAX_DIAGNOSTIC_BYTES,
)

private data class LoadedDiagnosticState(
    val droppedEventCount: Long = 0L,
    val invalidRecordCount: Int = 0,
    val events: List<DiagnosticEvent> = emptyList(),
) {
    fun toSnapshot(
        fileBytes: Long,
        maxEvents: Int,
        maxBytes: Int,
    ): DiagnosticSnapshot = DiagnosticSnapshot(
        events = events,
        droppedEventCount = droppedEventCount,
        invalidRecordCount = invalidRecordCount,
        bytesUsed = fileBytes.coerceAtLeast(0L),
        maxEvents = maxEvents,
        maxBytes = maxBytes,
    )
}

private fun encodeSafeText(value: String): String =
    value.toByteArray(Charsets.UTF_8).joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }

private fun decodeSafeText(value: String): String {
    require(value.length % 2 == 0)
    require(value.length <= MAX_ENCODED_SAFE_TEXT_CHARS)
    val bytes = ByteArray(value.length / 2)
    var index = 0
    while (index < value.length) {
        val byte = value.substring(index, index + 2).toInt(16)
        bytes[index / 2] = byte.toByte()
        index += 2
    }
    return bytes.toString(Charsets.UTF_8)
}

internal const val DEFAULT_MAX_DIAGNOSTIC_EVENTS = 1000
internal const val DEFAULT_MAX_DIAGNOSTIC_BYTES = 1024 * 1024
private const val DIAGNOSTIC_DIRECTORY_NAME = "diagnostics"
private const val DIAGNOSTIC_FILE_NAME = "events-v1.log"
private const val DIAGNOSTIC_SALT_FILE_NAME = "scope-salt-v1.bin"
private const val DIAGNOSTIC_SALT_BYTES = 32
private const val SCOPE_FINGERPRINT_BYTES = 8
private const val DIAGNOSTIC_HEADER_PREFIX = "SUBSHELL_DIAGNOSTICS_V1"
private const val EVENT_PREFIX = "E"
private const val LEGACY_EVENT_FIELD_COUNT = 16
private const val EVENT_FIELD_COUNT = 17
private const val NULL_TOKEN = "-"
private const val TRUE_TOKEN = "1"
private const val FALSE_TOKEN = "0"
private const val VIEWPORT_DEDUPE_WINDOW_MS = 5_000L
private val PRESENTATION_DEDUPE_OPERATIONS = setOf(
    DiagnosticOperation.VIEWPORT_ERROR_PRESENTED,
    DiagnosticOperation.BLOCKING_ERROR_PRESENTED,
    DiagnosticOperation.DEGRADED_ERROR_PRESENTED,
    DiagnosticOperation.INLINE_ERROR_PRESENTED,
    DiagnosticOperation.DIALOG_ERROR_PRESENTED,
)
private const val MAX_DIAGNOSTIC_RECORD_CHARS = 4096
private const val MAX_ENCODED_SAFE_TEXT_CHARS = 1024
private const val MAX_DIAGNOSTIC_READ_BYTES = 2L * 1024L * 1024L
private const val MAX_CONFIGURABLE_DIAGNOSTIC_EVENTS = 10_000
private const val MIN_CONFIGURABLE_DIAGNOSTIC_BYTES = 128
private const val MAX_CONFIGURABLE_DIAGNOSTIC_BYTES = 8 * 1024 * 1024
