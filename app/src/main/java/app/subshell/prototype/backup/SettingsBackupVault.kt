package app.subshell.prototype.backup

import android.content.ContentResolver
import android.net.Uri
import android.util.JsonWriter
import java.io.StringWriter
import java.util.UUID

/**
 * Syncs the local backup history with a user-chosen folder, possibly shared
 * by several devices.
 *
 * BACKUP08_SAFE_VAULT_SYNC_V1:
 * - a file is written to a temporary name and then renamed over the old
 *   one, so an interrupted write never leaves a truncated backup (providers
 *   that cannot rename are written in place, as before); temporary files
 *   left by an interrupted sync are removed after an hour;
 * - a file this build cannot read (truncated, or written by a newer build)
 *   no longer stops the sync: it is counted and left untouched;
 * - only files this device read and then removed locally are deleted, never
 *   a file another device added after the read, nor an unreadable one.
 */
class SettingsBackupVault internal constructor(
    private val folder: BackupVaultFolder,
    private val now: () -> Long = System::currentTimeMillis,
) {
    constructor(resolver: ContentResolver, treeUri: Uri) : this(SafBackupVaultFolder(resolver, treeUri))

    fun readSnapshots(): VaultSnapshotRead {
        val snapshots = mutableListOf<SettingsSnapshot>()
        var unreadableFiles = 0
        folder.list()
            .filter { document -> document.name.endsWith(SNAPSHOT_FILE_SUFFIX) }
            .forEach { document ->
                val snapshot = runCatching {
                    require(document.size <= 0L || document.size <= MAX_SNAPSHOT_BYTES) {
                        "${document.name} is too large"
                    }
                    SettingsSnapshotCodec.decode(folder.read(document, MAX_SNAPSHOT_BYTES.toInt()))
                }.getOrNull()
                if (snapshot == null) unreadableFiles += 1 else snapshots += snapshot
            }
        val tombstoneDocument = folder.list().firstOrNull { it.name == TOMBSTONE_FILE_NAME }
        val tombstones = tombstoneDocument?.let { document ->
            runCatching { decodeBackupTombstonesOrNull(folder.read(document, MAX_SNAPSHOT_BYTES.toInt())) }
                .getOrNull()
        }
        return VaultSnapshotRead(
            snapshots = snapshots,
            unreadableFiles = unreadableFiles + if (tombstoneDocument != null && tombstones == null) 1 else 0,
            tombstones = tombstones.orEmpty(),
            tombstonesUnreadable = tombstoneDocument != null && tombstones == null,
        )
    }

    /**
     * LOW_BACKUP_UNCHANGED_FOLDER_IS_NOT_REREAD_V1 (OCB-454): names, sizes and
     * times of the folder's files; equal to the last read means nothing to
     * merge, so every file need not be read and decoded again.
     */
    fun listingSignature(): String = folder.list()
        .sortedBy(VaultDocument::name)
        .joinToString("\n") { document -> "${document.name}|${document.size}|${document.lastModifiedEpochMillis}" }

    fun sync(
        snapshots: List<SettingsSnapshot>,
        existingSnapshots: Map<String, SettingsSnapshot>,
        generatedAtEpochMillis: Long,
        tombstones: Map<String, Long> = emptyMap(),
        // OCB-456: the folder's deletion list could not be read; it is left
        // as it is and no deletion is applied from this device's copy alone.
        vaultTombstonesUnreadable: Boolean = false,
    ) {
        val documents = folder.list()
        removeStaleTemporaryFiles(documents)
        val documentsByName = documents.associateBy(VaultDocument::name)
        snapshots.forEach { snapshot ->
            val name = snapshotFileName(snapshot.id)
            if (documentsByName[name] == null || existingSnapshots[snapshot.id] != snapshot) {
                writeReplacing(
                    existing = documentsByName[name],
                    name = name,
                    content = SettingsSnapshotCodec.encode(snapshot),
                )
            }
        }

        val localIds = snapshots.mapTo(hashSetOf(), SettingsSnapshot::id)
        documents
            .filter { document -> document.name.endsWith(SNAPSHOT_FILE_SUFFIX) }
            .filter { document ->
                val id = document.name.removeSuffix(SNAPSHOT_FILE_SUFFIX)
                // BACKUP09: a tombstoned id is a known deletion, whoever made it.
                (id in existingSnapshots || id in tombstones) && id !in localIds
            }
            .filterNot { vaultTombstonesUnreadable }
            .forEach(folder::delete)

        if (tombstones.isNotEmpty() && !vaultTombstonesUnreadable) {
            writeReplacing(
                existing = folder.list().firstOrNull { it.name == TOMBSTONE_FILE_NAME },
                name = TOMBSTONE_FILE_NAME,
                content = encodeBackupTombstones(tombstones),
            )
        }

        writeReplacing(
            existing = folder.list().firstOrNull { it.name == MANIFEST_FILE_NAME },
            name = MANIFEST_FILE_NAME,
            content = encodeManifest(snapshots, generatedAtEpochMillis),
        )
    }

    private fun writeReplacing(existing: VaultDocument?, name: String, content: String) {
        val temporary = folder.create("$name$TEMPORARY_MARKER${UUID.randomUUID()}", SNAPSHOT_MIME_TYPE)
        try {
            folder.write(temporary, content)
        } catch (error: Exception) {
            runCatching { folder.delete(temporary) }
            throw error
        }
        if (existing != null) folder.delete(existing)
        if (folder.rename(temporary, name) == null) {
            // The provider cannot rename: write in place, then drop the copy.
            folder.write(folder.create(name, SNAPSHOT_MIME_TYPE), content)
            runCatching { folder.delete(temporary) }
        }
    }

    private fun removeStaleTemporaryFiles(documents: List<VaultDocument>) {
        val cutoff = now() - STALE_TEMPORARY_FILE_MILLIS
        documents
            .filter { document -> TEMPORARY_MARKER in document.name }
            .filter { document -> document.lastModifiedEpochMillis in 1 until cutoff }
            .forEach { document -> runCatching { folder.delete(document) } }
    }

    private fun encodeManifest(snapshots: List<SettingsSnapshot>, generatedAtEpochMillis: Long): String {
        val output = StringWriter()
        JsonWriter(output).use { writer ->
            writer.setIndent("  ")
            writer.beginObject()
            writer.name("schemaVersion").value(SETTINGS_BACKUP_SCHEMA_VERSION.toLong())
            writer.name("generatedAtEpochMillis").value(generatedAtEpochMillis)
            writer.name("snapshots").beginArray()
            snapshots.sortedByDescending(SettingsSnapshot::createdAtEpochMillis).forEach { snapshot ->
                writer.beginObject()
                writer.name("id").value(snapshot.id)
                writer.name("name").value(snapshot.name)
                writer.name("createdAtEpochMillis").value(snapshot.createdAtEpochMillis)
                writer.name("appVersion").value(snapshot.appVersion)
                writer.name("kind").value(snapshot.kind.name)
                writer.name("deviceId").value(snapshot.deviceId)
                writer.name("file").value(snapshotFileName(snapshot.id))
                writer.endObject()
            }
            writer.endArray()
            writer.endObject()
        }
        return output.toString()
    }

    private companion object {
        const val SNAPSHOT_MIME_TYPE = "application/json"
        const val SNAPSHOT_FILE_SUFFIX = ".subshell-settings.json"
        const val MANIFEST_FILE_NAME = "manifest.json"
        const val TOMBSTONE_FILE_NAME = "deleted-backups.json"
        // MED5_READ_LIMIT_FITS_WHAT_BACKUPS_HOLD_V1 (OCB-455)
        const val MAX_SNAPSHOT_BYTES = 8_000_000L
        const val TEMPORARY_MARKER = ".tmp-"
        const val STALE_TEMPORARY_FILE_MILLIS = 60L * 60L * 1000L

        fun snapshotFileName(id: String) = "$id$SNAPSHOT_FILE_SUFFIX"
    }
}

data class VaultSnapshotRead(
    val snapshots: List<SettingsSnapshot>,
    val unreadableFiles: Int,
    val tombstones: Map<String, Long> = emptyMap(),
    val tombstonesUnreadable: Boolean = false,
)
