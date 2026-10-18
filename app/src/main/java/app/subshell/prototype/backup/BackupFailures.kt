package app.subshell.prototype.backup

import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.LocalFailureKind
import java.io.FileNotFoundException
import java.io.IOException

internal class BackupFailureException(
    val failureDetails: FailureDetails,
) : RuntimeException()

internal enum class BackupFailureOperation {
    CONNECT_FOLDER,
    SYNC,
    IMPORT,
    EXPORT,
    SAVE,
    RESTORE,
    RENAME,
    DELETE,
}

internal fun Throwable.toBackupFailureDetails(): FailureDetails {
    if (this is BackupFailureException) return failureDetails
    return FailureDetails(
        retryable = true,
        localKind = when (this) {
        is SecurityException -> LocalFailureKind.PERMISSION
        is FileNotFoundException -> LocalFailureKind.IO
        is IOException -> LocalFailureKind.IO
        is IllegalArgumentException -> LocalFailureKind.VALIDATION
        is IllegalStateException -> LocalFailureKind.STORAGE
            else -> LocalFailureKind.UNKNOWN
        },
    ).let { details ->
        // DIAG04_EXCEPTION_CLASS_V1
        if (details.localKind == LocalFailureKind.UNKNOWN) details.withExceptionClass(this) else details
    }
}

internal fun storedBackupFailureDetails(kindName: String?): FailureDetails? {
    val kind = kindName
        ?.let { raw -> runCatching { LocalFailureKind.valueOf(raw) }.getOrNull() }
        ?: return null
    return FailureDetails(
        retryable = true,
        localKind = kind,
    )
}
