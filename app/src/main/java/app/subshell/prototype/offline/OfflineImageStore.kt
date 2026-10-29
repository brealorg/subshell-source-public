package app.subshell.prototype.offline

import android.content.Context
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** ISSUE265_SAVE_FOR_OFFLINE_V1 (owner 2026-10-27): 500 MB for saved images. */
internal const val OFFLINE_IMAGE_STORE_MAX_BYTES = 500L * 1_024L * 1_024L

/**
 * ISSUE265_SAVE_FOR_OFFLINE_V1: images saved for offline reading, one file per
 * image address, in no-backup storage so Android neither backs them up nor
 * clears them as cache. When the total goes over [maxBytes] the images read
 * least recently go first. The image loader reads here before the network.
 */
internal class OfflineImageStore(
    private val directory: File,
    private val maxBytes: Long = OFFLINE_IMAGE_STORE_MAX_BYTES,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) {
    private val lock = Any()

    fun contains(uri: String): Boolean = fileFor(uri).isFile

    /** The saved bytes, or null; a read counts as use for the size limit. */
    fun read(uri: String): ByteArray? = synchronized(lock) {
        val file = fileFor(uri)
        if (!file.isFile) return null
        try {
            file.readBytes().also { file.setLastModified(nowEpochMillis()) }
        } catch (_: IOException) {
            null
        }
    }

    /** Saves [bytes] for [uri] (replacing an older copy), then trims to the limit. */
    fun write(uri: String, bytes: ByteArray): Boolean = synchronized(lock) {
        if (bytes.isEmpty() || bytes.size > maxBytes) return false
        try {
            directory.mkdirs()
            val target = fileFor(uri)
            val temporary = File(directory, target.name + ".part")
            temporary.writeBytes(bytes)
            if (!temporary.renameTo(target)) {
                temporary.delete()
                return false
            }
            target.setLastModified(nowEpochMillis())
            trimLocked()
            true
        } catch (_: IOException) {
            false
        }
    }

    fun sizeBytes(): Long = synchronized(lock) { files().sumOf(File::length) }

    fun clear() = synchronized(lock) { files().forEach(File::delete) }

    private fun trimLocked() {
        var total = files().sumOf(File::length)
        if (total <= maxBytes) return
        files().sortedBy(File::lastModified).forEach { file ->
            if (total <= maxBytes) return
            val length = file.length()
            if (file.delete()) total -= length
        }
    }

    private fun files(): List<File> =
        directory.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") }.orEmpty()

    private fun fileFor(uri: String): File = File(directory, sha256(uri))

    companion object {
        fun forContext(context: Context): OfflineImageStore =
            OfflineImageStore(File(context.applicationContext.noBackupFilesDir, "offline-images"))

        private fun sha256(value: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte) }
    }
}
