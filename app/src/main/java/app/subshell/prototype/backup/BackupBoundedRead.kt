package app.subshell.prototype.backup

import java.io.Reader

/** The largest backup file Subshell imports. */
// MED5_READ_LIMIT_FITS_WHAT_BACKUPS_HOLD_V1 (OCB-455): with the drafts and
// saved-responses opt-ins (up to 600 000 + 500 000 characters, more as UTF-8
// bytes, plus tags) a backup this build writes could pass the old 1 000 000
// limit and then never read back or import. The limit is 8 000 000; reads
// stay bounded.
internal const val BACKUP_IMPORT_MAX_CHARACTERS = 8_000_000

/**
 * BACKUP05_BOUNDED_BACKUP_READS_V1
 * Reads at most [maxChars] characters and fails as soon as there are more,
 * so a large file picked for import (or a vault file whose provider reports
 * no size) is refused instead of being loaded whole into memory.
 */
internal fun Reader.readBackupTextBounded(maxChars: Int): String {
    require(maxChars > 0)
    val output = StringBuilder()
    val buffer = CharArray(8 * 1024)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        if (output.length + count > maxChars) {
            throw IllegalArgumentException("Backup file is too large")
        }
        output.appendRange(buffer, 0, count)
    }
    return output.toString()
}
