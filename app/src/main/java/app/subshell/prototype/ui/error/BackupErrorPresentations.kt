package app.subshell.prototype.ui.error

import app.subshell.prototype.backup.BackupFailureOperation
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.LocalFailureKind
import app.subshell.prototype.data.error.toSafeTechnicalDetail

internal fun backupErrorPresentation(
    operation: BackupFailureOperation,
    details: FailureDetails,
): ErrorPresentation {
    val title = when (operation) {
        BackupFailureOperation.CONNECT_FOLDER -> "Couldn't connect backup folder"
        BackupFailureOperation.SYNC -> "Couldn't sync backup"
        BackupFailureOperation.IMPORT -> "Couldn't import backup"
        BackupFailureOperation.EXPORT -> "Couldn't export backup"
        BackupFailureOperation.SAVE -> "Couldn't save backup"
        BackupFailureOperation.RESTORE -> "Couldn't restore settings"
        BackupFailureOperation.RENAME -> "Couldn't rename saved setup"
        BackupFailureOperation.DELETE -> "Couldn't delete saved setup"
    }
    val message = when (details.localKind) {
        LocalFailureKind.PERMISSION -> "Android didn't grant subshell access to the selected backup location."
        LocalFailureKind.VALIDATION -> "The selected backup data isn't valid for this operation."
        LocalFailureKind.IO -> "subshell couldn't read or write the backup data."
        LocalFailureKind.STORAGE -> "The local backup store couldn't complete the operation."
        LocalFailureKind.SECURITY -> "The backup operation was blocked by a security check."
        LocalFailureKind.CONFIGURATION -> "The backup configuration isn't valid."
        LocalFailureKind.DECODING -> "The backup data couldn't be decoded."
        LocalFailureKind.UNKNOWN,
        null,
        -> "The backup operation failed before it could finish."
    }
    return ErrorPresentation(
        kind = ErrorPresentationKind.DEGRADED,
        title = title,
        message = message,
        technicalDetail = details.toSafeTechnicalDetail(),
    )
}
