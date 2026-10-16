package app.subshell.prototype.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.subshell.prototype.backup.BackupFailureOperation
import app.subshell.prototype.backup.SettingsBackupRepository
import app.subshell.prototype.backup.toBackupFailureDetails
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.LocalFailureKind
import app.subshell.prototype.ui.error.backupErrorPresentation
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.unit.dp

@Composable
fun BackupFolderOnboarding(
    repository: SettingsBackupRepository,
    onVisibilityChanged: (Boolean) -> Unit = {},
) {
    var visible by remember(repository) { mutableStateOf(repository.shouldShowFolderOnboarding()) }
    var failureDetails by remember { mutableStateOf<FailureDetails?>(null) }

    // UPDATE01A1B1_BACKUP_ONBOARDING_VISIBILITY_OWNER_V1
    LaunchedEffect(visible) {
        onVisibilityChanged(visible)
    }
    val scope = rememberCoroutineScope()
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) {
            repository.dismissFolderOnboarding()
            visible = false
        } else {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) { repository.connectFolder(uri) }
                }.onSuccess { connection ->
                    if (connection.isProtected) {
                        visible = false
                    } else {
                        failureDetails = connection.failureDetails ?: FailureDetails(
                            retryable = true,
                            localKind = LocalFailureKind.UNKNOWN,
                        )
                    }
                }.onFailure { error ->
                    // LOWUI6_CANCELLATION_IS_RETHROWN_V1 (OCB-173): leaving the
                    // onboarding is not a folder failure.
                    if (error is CancellationException) throw error
                    failureDetails = error.toBackupFailureDetails()
                }
            }
        }
    }

    if (!visible) return
    val failurePresentation = failureDetails?.let { details ->
        backupErrorPresentation(BackupFailureOperation.CONNECT_FOLDER, details)
    }
    AlertDialog(
        onDismissRequest = {
            repository.dismissFolderOnboarding()
            visible = false
        },
        title = { Text(failurePresentation?.title ?: "Protect your settings") },
        text = {
            if (failurePresentation == null) {
                Text(
                    "subshell already keeps automatic settings history on this device. " +
                        "Choose a folder outside the app so it survives uninstalling or clearing app data. " +
                        "We recommend creating a folder named subshell Backups in Documents.",
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(failurePresentation.message)
                    failurePresentation.technicalDetail?.let { Text(it) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { folderLauncher.launch(null) }) {
                Text(if (failurePresentation == null) "Choose backup folder" else "Try again")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    repository.dismissFolderOnboarding()
                    visible = false
                },
            ) { Text("Later") }
        },
    )
}
