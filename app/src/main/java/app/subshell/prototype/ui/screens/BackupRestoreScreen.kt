package app.subshell.prototype.ui.screens

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.ui.SavedSearch
import app.subshell.prototype.ui.ScreenMargin
import app.subshell.prototype.ui.components.RouteTopBar
import app.subshell.prototype.ui.components.rememberRouteTopBarScrollBehavior
import app.subshell.prototype.ui.identityKey
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SyncProblem
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import app.subshell.prototype.backup.BACKUP_IMPORT_MAX_CHARACTERS
import app.subshell.prototype.backup.readBackupTextBounded
import app.subshell.prototype.backup.BackupFailureException
import app.subshell.prototype.backup.BackupFailureOperation
import app.subshell.prototype.backup.BackupRetention
import app.subshell.prototype.backup.BackupProtection
import app.subshell.prototype.backup.BackupStatus
import app.subshell.prototype.backup.SettingsBackupRepository
import app.subshell.prototype.backup.PortableCommunityMute
import app.subshell.prototype.backup.PortableModeratorSavedResponse
import app.subshell.prototype.backup.PortableUserTag
import app.subshell.prototype.backup.PortablePostComposerDraft
import app.subshell.prototype.backup.SettingsSnapshot
import app.subshell.prototype.backup.SnapshotKind
import app.subshell.prototype.backup.toBackupFailureDetails
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.LocalFailureKind
import app.subshell.prototype.data.error.toSafeTechnicalDetail
import app.subshell.prototype.ui.SubshellSettings
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.error.ReportViewportError
import app.subshell.prototype.ui.error.backupErrorPresentation
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    state: SubshellState,
    repository: SettingsBackupRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // LOWUX3_RESTORE_PREVIEW_NAMES_ACCOUNTS_V1 (OCB-176): per-account modules
    // are keyed by stable id ("reddit:native_production:…"); the preview names
    // the account instead.
    val restoreAccountLabel: (String) -> String = { key -> restorePreviewAccountLabel(key, state.accountSessions) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // ISSUE89_BACKUP_SCREEN_READS_OFF_MAIN_V1: listSnapshots() decodes every
    // snapshot under the repository monitor, which a vault sync holds on IO for
    // its whole run, and status() may ask the documents provider once; both ran
    // in composition and after every action, so the main thread waited for a
    // sync (jank, ANR risk). They are read on IO; null until the first read.
    var loadedSnapshots by remember(repository) { mutableStateOf<List<SettingsSnapshot>?>(null) }
    val snapshots = loadedSnapshots.orEmpty()
    var loadedStatus by remember(repository) { mutableStateOf<BackupStatus?>(null) }
    var readRevision by remember(repository) { mutableIntStateOf(0) }
    LaunchedEffect(repository, readRevision) {
        val (listed, current) = withContext(Dispatchers.IO) {
            repository.listSnapshots() to repository.status()
        }
        loadedSnapshots = listed
        loadedStatus = current
    }
    var automaticHistory by remember(repository) { mutableStateOf(repository.automaticHistoryEnabled()) }
    var includeHiddenCommunities by remember(repository) {
        mutableStateOf(repository.hiddenCommunitiesBackupEnabled())
    }
    var includeCommunityMutes by remember(repository) {
        mutableStateOf(repository.communityMutesBackupEnabled())
    }
    var includePostComposerDrafts by remember(repository) {
        mutableStateOf(repository.postComposerDraftsBackupEnabled())
    }
    // BACKUP04A4_MODERATOR_SAVED_RESPONSES_SCHEMA_V8_OPT_IN_V1
    var includeModeratorSavedResponses by remember(repository) {
        mutableStateOf(repository.moderatorSavedResponsesBackupEnabled())
    }
    // BACKUP04A5_USER_TAGS_SCHEMA_V9_OPT_IN_V1
    var includeUserTags by remember(repository) {
        mutableStateOf(repository.userTagsBackupEnabled())
    }
    var automaticHistoryRetention by remember(repository) {
        mutableStateOf(repository.automaticHistoryRetention())
    }
    var safetySnapshotRetention by remember(repository) {
        mutableStateOf(repository.safetySnapshotRetention())
    }
    var message by remember { mutableStateOf<String?>(null) }
    var failureOperation by remember { mutableStateOf<BackupFailureOperation?>(null) }
    var failureDetails by remember { mutableStateOf<FailureDetails?>(null) }
    var saveDialogVisible by remember { mutableStateOf(false) }
    var saveName by remember { mutableStateOf("My setup") }
    var renameTarget by remember { mutableStateOf<SettingsSnapshot?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<SettingsSnapshot?>(null) }
    var deleteAllDialogVisible by remember { mutableStateOf(false) }
    var restoreTarget by remember { mutableStateOf<PendingRestore?>(null) }
    var retentionChangeTarget by remember { mutableStateOf<PendingRetentionChange?>(null) }
    // LOWUI6_PENDING_EXPORT_SURVIVES_RECREATION_V1 (OCB-174): the picker
    // creates the file before it returns; a recreation while it was open lost
    // which setup to write and left an empty file. The id and name are saved.
    var pendingExportId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingExportName by rememberSaveable { mutableStateOf<String?>(null) }

    fun refresh() {
        readRevision += 1
        automaticHistory = repository.automaticHistoryEnabled()
        includeHiddenCommunities = repository.hiddenCommunitiesBackupEnabled()
        includeCommunityMutes = repository.communityMutesBackupEnabled()
        includePostComposerDrafts = repository.postComposerDraftsBackupEnabled()
        includeModeratorSavedResponses = repository.moderatorSavedResponsesBackupEnabled()
        includeUserTags = repository.userTagsBackupEnabled()
        automaticHistoryRetention = repository.automaticHistoryRetention()
        safetySnapshotRetention = repository.safetySnapshotRetention()
    }

    fun reportFailure(
        operation: BackupFailureOperation,
        error: Throwable,
    ) {
        message = null
        failureOperation = operation
        failureDetails = error.toBackupFailureDetails()
        refresh()
    }

    fun runInBackground(
        operation: BackupFailureOperation,
        action: () -> String,
    ) {
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching(action) }
            result.onSuccess { successMessage ->
                refresh()
                failureOperation = null
                failureDetails = null
                message = successMessage
            }.onFailure { error ->
                reportFailure(operation, error)
            }
        }
    }

    ReportViewportError(
        scopeKey = "backup-restore",
        presentation = if (failureOperation != null && failureDetails != null) {
            backupErrorPresentation(
                operation = requireNotNull(failureOperation),
                details = requireNotNull(failureDetails),
            )
        } else {
            null
        },
        failureDetails = failureDetails,
        onRetry = null,
    )

    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runInBackground(BackupFailureOperation.CONNECT_FOLDER) {
                val connection = repository.connectFolder(uri)
                when {
                    connection.isProtected && connection.importedSnapshots > 0 ->
                        "Backup folder connected. Restored ${connection.importedSnapshots} saved states."
                    connection.isProtected -> "Backup folder connected and protected."
                    else -> throw BackupFailureException(
                        connection.failureDetails ?: FailureDetails(
                            retryable = true,
                            localKind = LocalFailureKind.UNKNOWN,
                        ),
                    )
                }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val snapshotId = pendingExportId
        val snapshotName = pendingExportName
        pendingExportId = null
        pendingExportName = null
        if (uri != null && snapshotId != null) {
            runInBackground(BackupFailureOperation.EXPORT) {
                val json = repository.exportSnapshot(snapshotId)
                requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) {
                    "Could not open the selected location"
                }.bufferedWriter().use { it.write(json) }
                "Exported ${snapshotName.orEmpty()}"
            }
        } else if (uri != null) {
            // The picker made the file but nothing says what to write: remove it.
            runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri) }
            reportFailure(
                BackupFailureOperation.EXPORT,
                IllegalStateException("The setup to export was lost; export it again."),
            )
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runInBackground(BackupFailureOperation.IMPORT) {
                val json = requireNotNull(context.contentResolver.openInputStream(uri)) {
                    "Could not open the selected backup"
                }.bufferedReader().use {
                    it.readBackupTextBounded(BACKUP_IMPORT_MAX_CHARACTERS)
                }
                val imported = repository.importSnapshot(json)
                "Imported ${imported.name}. Restore it when you are ready."
            }
        }
    }

    val manualSnapshots = snapshots.filter { it.kind == SnapshotKind.MANUAL }
    val historySnapshots = snapshots.filter { it.kind == SnapshotKind.CHANGE }
    val safetySnapshots = snapshots.filter { it.kind == SnapshotKind.SAFETY }

    val routeTopBarScroll = rememberRouteTopBarScrollBehavior()
    Column(modifier.fillMaxSize().nestedScroll(routeTopBarScroll.nestedScrollConnection)) {
        RouteTopBar(
            title = { Text("Backup & restore") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
            },
            scrollBehavior = routeTopBarScroll,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = ScreenMargin, top = 10.dp, end = ScreenMargin, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item("protection") {
                val status = loadedStatus ?: return@item
                BackupProtectionCard(
                    status = status,
                    folderConnected = repository.folderConnected(),
                    onChooseFolder = { folderLauncher.launch(null) },
                    onSync = {
                        runInBackground(BackupFailureOperation.SYNC) {
                            if (repository.syncNow()) {
                                "Backup folder is up to date."
                            } else {
                                throw BackupFailureException(
                                    repository.status().failureDetails ?: FailureDetails(
                                        retryable = true,
                                        localKind = LocalFailureKind.UNKNOWN,
                                    ),
                                )
                            }
                        }
                    },
                    onDisconnect = {
                        runInBackground(BackupFailureOperation.SYNC) {
                            repository.disconnectFolder()
                            "External folder disconnected. Existing backup files were kept."
                        }
                    },
                )
            }

            item("automatic-history") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    BackupSwitchRow(
                        checked = automaticHistory,
                        onCheckedChange = { enabled ->
                            automaticHistory = enabled
                            repository.setAutomaticHistoryEnabled(enabled)
                            if (enabled) {
                                val currentPortable = state.capturePortableBackupState()
                                runInBackground(BackupFailureOperation.SAVE) {
                                    repository.recordSettingsChange(
                                        settings = currentPortable.settings,
                                        joinedCommunityIdsByAccount =
                                            currentPortable.joinedCommunityIdsByAccount,
                                        favoriteCommunityNamesByAccount =
                                            currentPortable.favoriteCommunityNamesByAccount,
                                        favoriteCustomFeedPathsByAccount =
                                            currentPortable.favoriteCustomFeedPathsByAccount,
                                        hiddenCommunityNamesByAccount =
                                            currentPortable.hiddenCommunityNamesByAccount,
                                        communityMutesByAccount =
                                            currentPortable.communityMutesByAccount,
                                        postComposerDraftsByAccount =
                                            currentPortable.postComposerDraftsByAccount,
                                        moderatorSavedResponses =
                                            currentPortable.moderatorSavedResponses,
                                        userTagsByAccount =
                                            currentPortable.userTagsByAccount,
                                        savedSearchesByAccount =
                                            currentPortable.savedSearchesByAccount,
                                    )
                                    "Automatic settings history enabled."
                                }
                            } else {
                                message = "Automatic settings history paused. Saved history was kept."
                            }
                        },
                        title = "Automatic settings history",
                        description = "Captures changed settings states. Retention is controlled separately below.",
                        icon = Icons.Rounded.History,
                    )
                }
            }

            item("privacy-sensitive-backup") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column {
                        BackupSwitchRow(
                            checked = includeHiddenCommunities,
                            onCheckedChange = { enabled ->
                                includeHiddenCommunities = enabled
                                repository.setHiddenCommunitiesBackupEnabled(enabled)
                                val currentPortable = state.capturePortableBackupState()
                                if (automaticHistory) {
                                    runInBackground(BackupFailureOperation.SAVE) {
                                        repository.recordSettingsChange(
                                            settings = currentPortable.settings,
                                            joinedCommunityIdsByAccount =
                                                currentPortable.joinedCommunityIdsByAccount,
                                            favoriteCommunityNamesByAccount =
                                                currentPortable.favoriteCommunityNamesByAccount,
                                            favoriteCustomFeedPathsByAccount =
                                                currentPortable.favoriteCustomFeedPathsByAccount,
                                            hiddenCommunityNamesByAccount =
                                                currentPortable.hiddenCommunityNamesByAccount,
                                            communityMutesByAccount =
                                                currentPortable.communityMutesByAccount,
                                            postComposerDraftsByAccount =
                                                currentPortable.postComposerDraftsByAccount,
                                            moderatorSavedResponses =
                                                currentPortable.moderatorSavedResponses,
                                            userTagsByAccount =
                                                currentPortable.userTagsByAccount,
                                            savedSearchesByAccount =
                                                currentPortable.savedSearchesByAccount,
                                        )
                                        if (enabled) {
                                            "Hidden communities will be included in new backups and may be restored."
                                        } else {
                                            "Hidden communities are excluded from new backups and restore."
                                        }
                                    }
                                } else {
                                    message = if (enabled) {
                                        "Hidden communities will be included in new saved setups and may be restored."
                                    } else {
                                        "Hidden communities are excluded from new backups and restore."
                                    }
                                }
                            },
                            overline = "Privacy-sensitive backup data",
                            title = "Include hidden communities in backup & restore",
                            description = "Off by default. Hidden community names are added to new backups only while enabled. Existing backups are not rewritten when this is turned off.",
                        )

                        BackupSwitchRow(
                            checked = includeCommunityMutes,
                            onCheckedChange = { enabled ->
                                includeCommunityMutes = enabled
                                repository.setCommunityMutesBackupEnabled(enabled)
                                val currentPortable = state.capturePortableBackupState()
                                if (automaticHistory) {
                                    runInBackground(BackupFailureOperation.SAVE) {
                                        repository.recordSettingsChange(
                                            settings = currentPortable.settings,
                                            joinedCommunityIdsByAccount =
                                                currentPortable.joinedCommunityIdsByAccount,
                                            favoriteCommunityNamesByAccount =
                                                currentPortable.favoriteCommunityNamesByAccount,
                                            favoriteCustomFeedPathsByAccount =
                                                currentPortable.favoriteCustomFeedPathsByAccount,
                                            hiddenCommunityNamesByAccount =
                                                currentPortable.hiddenCommunityNamesByAccount,
                                            communityMutesByAccount =
                                                currentPortable.communityMutesByAccount,
                                            postComposerDraftsByAccount =
                                                currentPortable.postComposerDraftsByAccount,
                                            moderatorSavedResponses =
                                                currentPortable.moderatorSavedResponses,
                                            userTagsByAccount =
                                                currentPortable.userTagsByAccount,
                                            savedSearchesByAccount =
                                                currentPortable.savedSearchesByAccount,
                                        )
                                        if (enabled) {
                                            "Muted communities will be included in new backups and may be restored."
                                        } else {
                                            "Muted communities are excluded from new backups and restore."
                                        }
                                    }
                                } else {
                                    message = if (enabled) {
                                        "Muted communities will be included in new saved setups and may be restored."
                                    } else {
                                        "Muted communities are excluded from new backups and restore."
                                    }
                                }
                            },
                            title = "Include muted communities in backup & restore",
                            description = "Off by default. Includes timed and indefinite mutes. Timed mutes keep their original end time; already expired mutes are ignored on restore.",
                        )

                        BackupSwitchRow(
                            checked = includePostComposerDrafts,
                            onCheckedChange = { enabled ->
                                includePostComposerDrafts = enabled
                                repository.setPostComposerDraftsBackupEnabled(enabled)
                                val currentPortable = state.capturePortableBackupState()
                                if (automaticHistory) {
                                    runInBackground(BackupFailureOperation.SAVE) {
                                        repository.recordSettingsChange(
                                            settings = currentPortable.settings,
                                            joinedCommunityIdsByAccount =
                                                currentPortable.joinedCommunityIdsByAccount,
                                            favoriteCommunityNamesByAccount =
                                                currentPortable.favoriteCommunityNamesByAccount,
                                            favoriteCustomFeedPathsByAccount =
                                                currentPortable.favoriteCustomFeedPathsByAccount,
                                            hiddenCommunityNamesByAccount =
                                                currentPortable.hiddenCommunityNamesByAccount,
                                            communityMutesByAccount =
                                                currentPortable.communityMutesByAccount,
                                            postComposerDraftsByAccount =
                                                currentPortable.postComposerDraftsByAccount,
                                            moderatorSavedResponses =
                                                currentPortable.moderatorSavedResponses,
                                            userTagsByAccount =
                                                currentPortable.userTagsByAccount,
                                            savedSearchesByAccount =
                                                currentPortable.savedSearchesByAccount,
                                        )
                                        if (enabled) {
                                            "Text and link post drafts will be included in new backups and may be restored."
                                        } else {
                                            "Post drafts are excluded from new backups and restore."
                                        }
                                    }
                                } else {
                                    message = if (enabled) {
                                        "Text and link post drafts will be included in new saved setups and may be restored."
                                    } else {
                                        "Post drafts are excluded from new backups and restore."
                                    }
                                }
                            },
                            title = "Include post drafts in backup & restore",
                            description = "Off by default. Includes unpublished text and link post drafts. Media drafts and local attachments stay only on this device.",
                        )
                        // BACKUP04A4_MODERATOR_SAVED_RESPONSES_SCHEMA_V8_OPT_IN_V1
                        BackupSwitchRow(
                            checked = includeModeratorSavedResponses,
                            onCheckedChange = { enabled ->
                                includeModeratorSavedResponses = enabled
                                repository.setModeratorSavedResponsesBackupEnabled(enabled)
                                val currentPortable = state.capturePortableBackupState()
                                if (automaticHistory) {
                                    runInBackground(BackupFailureOperation.SAVE) {
                                        repository.recordSettingsChange(
                                            settings = currentPortable.settings,
                                            joinedCommunityIdsByAccount =
                                                currentPortable.joinedCommunityIdsByAccount,
                                            favoriteCommunityNamesByAccount =
                                                currentPortable.favoriteCommunityNamesByAccount,
                                            favoriteCustomFeedPathsByAccount =
                                                currentPortable.favoriteCustomFeedPathsByAccount,
                                            hiddenCommunityNamesByAccount =
                                                currentPortable.hiddenCommunityNamesByAccount,
                                            communityMutesByAccount =
                                                currentPortable.communityMutesByAccount,
                                            postComposerDraftsByAccount =
                                                currentPortable.postComposerDraftsByAccount,
                                            moderatorSavedResponses =
                                                currentPortable.moderatorSavedResponses,
                                            userTagsByAccount =
                                                currentPortable.userTagsByAccount,
                                            savedSearchesByAccount =
                                                currentPortable.savedSearchesByAccount,
                                        )
                                        if (enabled) {
                                            "Saved moderator responses will be included in new backups and may be restored."
                                        } else {
                                            "Saved moderator responses are excluded from new backups and restore."
                                        }
                                    }
                                } else {
                                    message = if (enabled) {
                                        "Saved moderator responses will be included in new saved setups and may be restored."
                                    } else {
                                        "Saved moderator responses are excluded from new backups and restore."
                                    }
                                }
                            },
                            title = "Include saved moderator responses in backup & restore",
                            description = "Off by default. Includes your reusable removal-response templates; they are shared by every account on this device.",
                        )
                        // BACKUP04A5_USER_TAGS_SCHEMA_V9_OPT_IN_V1
                        BackupSwitchRow(
                            checked = includeUserTags,
                            onCheckedChange = { enabled ->
                                includeUserTags = enabled
                                repository.setUserTagsBackupEnabled(enabled)
                                val currentPortable = state.capturePortableBackupState()
                                if (automaticHistory) {
                                    runInBackground(BackupFailureOperation.SAVE) {
                                        repository.recordSettingsChange(
                                            settings = currentPortable.settings,
                                            joinedCommunityIdsByAccount =
                                                currentPortable.joinedCommunityIdsByAccount,
                                            favoriteCommunityNamesByAccount =
                                                currentPortable.favoriteCommunityNamesByAccount,
                                            favoriteCustomFeedPathsByAccount =
                                                currentPortable.favoriteCustomFeedPathsByAccount,
                                            hiddenCommunityNamesByAccount =
                                                currentPortable.hiddenCommunityNamesByAccount,
                                            communityMutesByAccount =
                                                currentPortable.communityMutesByAccount,
                                            postComposerDraftsByAccount =
                                                currentPortable.postComposerDraftsByAccount,
                                            moderatorSavedResponses =
                                                currentPortable.moderatorSavedResponses,
                                            userTagsByAccount =
                                                currentPortable.userTagsByAccount,
                                            savedSearchesByAccount =
                                                currentPortable.savedSearchesByAccount,
                                        )
                                        if (enabled) {
                                            "Private notes & tags will be included in new backups and may be restored."
                                        } else {
                                            "Private notes & tags are excluded from new backups and restore."
                                        }
                                    }
                                } else {
                                    message = if (enabled) {
                                        "Private notes & tags will be included in new saved setups and may be restored."
                                    } else {
                                        "Private notes & tags are excluded from new backups and restore."
                                    }
                                }
                            },
                            title = "Include private notes & tags in backup & restore",
                            description = "Off by default. Includes the notes and tags you put on Reddit usernames, per signed-in account.",
                        )
                    }
                }
            }

            item("retention") {
                BackupRetentionCard(
                    automaticHistoryRetention = automaticHistoryRetention,
                    safetySnapshotRetention = safetySnapshotRetention,
                    onAutomaticHistoryRetention = { retention ->
                        if (retention.isStricterThan(automaticHistoryRetention)) {
                            retentionChangeTarget = PendingRetentionChange(
                                kind = RetentionKind.AUTOMATIC_HISTORY,
                                target = retention,
                            )
                        } else {
                            runInBackground(BackupFailureOperation.DELETE) {
                                repository.setAutomaticHistoryRetention(retention)
                                "Automatic history retention set to ${retention.label}."
                            }
                        }
                    },
                    onSafetySnapshotRetention = { retention ->
                        if (retention.isStricterThan(safetySnapshotRetention)) {
                            retentionChangeTarget = PendingRetentionChange(
                                kind = RetentionKind.SAFETY_SNAPSHOTS,
                                target = retention,
                            )
                        } else {
                            runInBackground(BackupFailureOperation.DELETE) {
                                repository.setSafetySnapshotRetention(retention)
                                "Safety snapshot retention set to ${retention.label}."
                            }
                        }
                    },
                )
            }

            item("save") {
                Button(
                    onClick = { saveDialogVisible = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save current setup")
                }
            }
            item("import") {
                OutlinedButton(
                    onClick = {
                        importLauncher.launch(
                            arrayOf("application/json", "text/plain", "application/octet-stream"),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.UploadFile, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Import backup file")
                }
            }

            if (snapshots.isNotEmpty()) {
                item("delete-all") {
                    OutlinedButton(
                        onClick = { deleteAllDialogVisible = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Delete all backups")
                    }
                }
            }

            message?.let { currentMessage ->
                item("message") {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                    ) {
                        Text(
                            currentMessage,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }

            item("saved-title") {
                SnapshotSectionTitle("Saved setups", manualSnapshots.size)
            }
            if (loadedSnapshots == null) {
                item("manual-loading") {
                    EmptySnapshots(
                        icon = Icons.Rounded.History,
                        title = "Loading saved setups…",
                        description = "Reading the backups on this device.",
                    )
                }
            } else if (manualSnapshots.isEmpty()) {
                item("manual-empty") {
                    EmptySnapshots(
                        icon = Icons.Rounded.Backup,
                        title = "No named setups yet",
                        description = "Save a setup you want to keep until you delete it.",
                    )
                }
            } else {
                items(manualSnapshots, key = SettingsSnapshot::id) { snapshot ->
                    SnapshotCard(
                        snapshot = snapshot,
                        onRestore = {
                            restoreTarget = PendingRestore(
                                snapshot = snapshot,
                                currentSettings = state.captureSettings(),

                                currentJoinedCommunityIdsByAccount =
                                    state.captureJoinedCommunityIdsByAccount(),
                                currentFavoriteCommunityNamesByAccount =
                                    state.captureFavoriteCommunityNamesByAccount(),
                                currentFavoriteCustomFeedPathsByAccount =
                                    state.captureFavoriteCustomFeedPathsByAccount(),
                                currentHiddenCommunityNamesByAccount =
                                    state.captureHiddenCommunityNamesByAccount(),
                                currentCommunityMutesByAccount =
                                    state.captureCommunityMutesByAccount(),
                                currentPostComposerDraftsByAccount =
                                    state.capturePostComposerDraftsByAccount(),
                                currentModeratorSavedResponses =
                                    state.captureModeratorSavedResponses(),
                                currentUserTagsByAccount =
                                    state.captureUserTagsByAccount(),
                                currentSavedSearchesByAccount =
                                    state.captureSavedSearchesByAccount(),
                            )
                        },
                        onExport = {
                            pendingExportId = snapshot.id
                            pendingExportName = snapshot.name
                            exportLauncher.launch(snapshot.exportFileName())
                        },
                        onRename = {
                            renameTarget = snapshot
                            renameValue = snapshot.name
                        },
                        onDelete = { deleteTarget = snapshot },
                    )
                }
            }

            item("history-title") {
                SnapshotSectionTitle("Automatic history", historySnapshots.size)
            }
            if (loadedSnapshots == null) {
                item("history-loading") {
                    EmptySnapshots(
                        icon = Icons.Rounded.History,
                        title = "Loading saved setups…",
                        description = "Reading the backups on this device.",
                    )
                }
            } else if (historySnapshots.isEmpty()) {
                item("history-empty") {
                    EmptySnapshots(
                        icon = Icons.Rounded.History,
                        title = "No settings changes captured yet",
                        description = "A changed state is saved after settings settle, and again on the next launch if needed.",
                    )
                }
            } else {
                items(historySnapshots, key = SettingsSnapshot::id) { snapshot ->
                    SnapshotCard(
                        snapshot = snapshot,
                        onRestore = {
                            restoreTarget = PendingRestore(
                                snapshot = snapshot,
                                currentSettings = state.captureSettings(),

                                currentJoinedCommunityIdsByAccount =
                                    state.captureJoinedCommunityIdsByAccount(),
                                currentFavoriteCommunityNamesByAccount =
                                    state.captureFavoriteCommunityNamesByAccount(),
                                currentFavoriteCustomFeedPathsByAccount =
                                    state.captureFavoriteCustomFeedPathsByAccount(),
                                currentHiddenCommunityNamesByAccount =
                                    state.captureHiddenCommunityNamesByAccount(),
                                currentCommunityMutesByAccount =
                                    state.captureCommunityMutesByAccount(),
                                currentPostComposerDraftsByAccount =
                                    state.capturePostComposerDraftsByAccount(),
                                currentModeratorSavedResponses =
                                    state.captureModeratorSavedResponses(),
                                currentUserTagsByAccount =
                                    state.captureUserTagsByAccount(),
                                currentSavedSearchesByAccount =
                                    state.captureSavedSearchesByAccount(),
                            )
                        },
                        onExport = {
                            pendingExportId = snapshot.id
                            pendingExportName = snapshot.name
                            exportLauncher.launch(snapshot.exportFileName())
                        },
                        onRename = null,
                        onDelete = { deleteTarget = snapshot },
                    )
                }
            }

            item("safety-title") {
                SnapshotSectionTitle("Safety snapshots", safetySnapshots.size)
            }
            if (loadedSnapshots == null) {
                item("safety-loading") {
                    EmptySnapshots(
                        icon = Icons.Rounded.History,
                        title = "Loading saved setups…",
                        description = "Reading the backups on this device.",
                    )
                }
            } else if (safetySnapshots.isEmpty()) {
                item("safety-empty") {
                    EmptySnapshots(
                        icon = Icons.Rounded.Restore,
                        title = "No safety snapshots yet",
                        description = "A safety snapshot is created automatically before each restore.",
                    )
                }
            } else {
                items(safetySnapshots, key = SettingsSnapshot::id) { snapshot ->
                    SnapshotCard(
                        snapshot = snapshot,
                        onRestore = {
                            PendingRestore(
                                snapshot = snapshot,
                                currentSettings = state.captureSettings(),

                                currentJoinedCommunityIdsByAccount =
                                    state.captureJoinedCommunityIdsByAccount(),
                                currentFavoriteCommunityNamesByAccount =
                                    state.captureFavoriteCommunityNamesByAccount(),
                                currentFavoriteCustomFeedPathsByAccount =
                                    state.captureFavoriteCustomFeedPathsByAccount(),
                                currentHiddenCommunityNamesByAccount =
                                    state.captureHiddenCommunityNamesByAccount(),
                                currentCommunityMutesByAccount =
                                    state.captureCommunityMutesByAccount(),
                                currentPostComposerDraftsByAccount =
                                    state.capturePostComposerDraftsByAccount(),
                                currentModeratorSavedResponses =
                                    state.captureModeratorSavedResponses(),
                                currentUserTagsByAccount =
                                    state.captureUserTagsByAccount(),
                                currentSavedSearchesByAccount =
                                    state.captureSavedSearchesByAccount(),
                            ).also { pending -> restoreTarget = pending }
                        },
                        onExport = {
                            pendingExportId = snapshot.id
                            pendingExportName = snapshot.name
                            exportLauncher.launch(snapshot.exportFileName())
                        },
                        onRename = null,
                        onDelete = { deleteTarget = snapshot },
                    )
                }
            }
        }
    }

    if (saveDialogVisible) {
        NameDialog(
            title = "Save current setup",
            value = saveName,
            confirmLabel = "Save",
            onValueChange = { saveName = it },
            onDismiss = { saveDialogVisible = false },
            onConfirm = {
                val currentSettings = state.captureSettings()
                val currentJoinedCommunities = state.captureJoinedCommunityIdsByAccount()
                val currentFavoriteCommunities = state.captureFavoriteCommunityNamesByAccount()
                val currentFavoriteCustomFeeds = state.captureFavoriteCustomFeedPathsByAccount()
                val currentHiddenCommunities = state.captureHiddenCommunityNamesByAccount()
                val currentCommunityMutes = state.captureCommunityMutesByAccount()
                val currentPostComposerDrafts = state.capturePostComposerDraftsByAccount()
                val currentModeratorSavedResponses = state.captureModeratorSavedResponses()
                val currentUserTags = state.captureUserTagsByAccount()
                val currentSavedSearches = state.captureSavedSearchesByAccount()
                val name = saveName
                runInBackground(BackupFailureOperation.SAVE) {
                    val saved = repository.saveCurrent(
                        name,
                        currentSettings,
                        currentJoinedCommunities,
                        currentFavoriteCommunities,
                        currentFavoriteCustomFeeds,
                        currentHiddenCommunities,
                        currentCommunityMutes,
                        currentPostComposerDrafts,
                        currentModeratorSavedResponses,
                        currentUserTags,
                        currentSavedSearches,
                    )
                    "Saved ${saved.name}"
                }
                saveDialogVisible = false
                saveName = "My setup"
            },
        )
    }

    renameTarget?.let { snapshot ->
        NameDialog(
            title = "Rename saved setup",
            value = renameValue,
            confirmLabel = "Rename",
            onValueChange = { renameValue = it },
            onDismiss = { renameTarget = null },
            onConfirm = {
                val name = renameValue
                runInBackground(BackupFailureOperation.RENAME) {
                    val renamed = repository.renameSnapshot(snapshot.id, name)
                    "Renamed to ${renamed.name}"
                }
                renameTarget = null
            },
        )
    }

    restoreTarget?.let { request ->
        val changedSettings = settingsRestoreChangeCount(
            current = request.currentSettings,
            target = request.snapshot.settingsRestoredOver(request.currentSettings),
        )
        val changedLabel = when (changedSettings) {
            0 -> "Settings will stay unchanged."
            1 -> "1 setting will change."
            else -> "$changedSettings settings will change."
        }
        val communityDiffs = joinedCommunitiesRestoreDiff(
            current = request.currentJoinedCommunityIdsByAccount,
            target = request.snapshot.joinedCommunityIdsByAccount,
        )
        val legacyCommunities =
            request.snapshot.joinedCommunityIdsByAccount == null
        val favoriteCommunityDiffs = portableValuesRestoreDiff(
            current = request.currentFavoriteCommunityNamesByAccount,
            target = request.snapshot.favoriteCommunityNamesByAccount,
        )
        val favoriteCustomFeedDiffs = portableValuesRestoreDiff(
            current = request.currentFavoriteCustomFeedPathsByAccount,
            target = request.snapshot.favoriteCustomFeedPathsByAccount,
        )
        val legacyFavoriteCommunities =
            request.snapshot.favoriteCommunityNamesByAccount == null
        val legacyFavoriteCustomFeeds =
            request.snapshot.favoriteCustomFeedPathsByAccount == null
        val hiddenCommunityDiffs = portableValuesRestoreDiff(
            current = request.currentHiddenCommunityNamesByAccount,
            target = request.snapshot.hiddenCommunityNamesByAccount,
        )
        val legacyHiddenCommunities =
            request.snapshot.hiddenCommunityNamesByAccount == null
        val communityMuteDiffs = portableCommunityMutesRestoreDiff(
            current = request.currentCommunityMutesByAccount,
            target = request.snapshot.communityMutesByAccount,
        )
        val legacyCommunityMutes = request.snapshot.communityMutesByAccount == null
        val postComposerDraftDiffs = portablePostComposerDraftsRestoreDiff(
            current = request.currentPostComposerDraftsByAccount,
            target = request.snapshot.postComposerDraftsByAccount,
        )
        val legacyPostComposerDrafts = request.snapshot.postComposerDraftsByAccount == null
        val moderatorSavedResponseDiffs = portableModeratorSavedResponsesRestoreDiff(
            current = request.currentModeratorSavedResponses,
            target = request.snapshot.moderatorSavedResponses,
        )
        val legacyModeratorSavedResponses = request.snapshot.moderatorSavedResponses == null
        val userTagDiffs = portableValuesRestoreDiff(
            current = portableUserTagValuesByAccount(request.currentUserTagsByAccount),
            target = request.snapshot.userTagsByAccount?.let(::portableUserTagValuesByAccount),
        )
        val legacyUserTags = request.snapshot.userTagsByAccount == null
        // ISSUE206_SAVED_SEARCHES_SCHEMA_V10_V1: always restored with the backup.
        val savedSearchDiffs = portableValuesRestoreDiff(
            current = savedSearchValuesByAccount(request.currentSavedSearchesByAccount),
            target = request.snapshot.savedSearchesByAccount?.let(::savedSearchValuesByAccount),
        )
        val legacySavedSearches = request.snapshot.savedSearchesByAccount == null

        AlertDialog(
            onDismissRequest = { restoreTarget = null },
            title = { Text("Restore ${request.snapshot.name}?") },
            text = {
                Column {
                    Text(changedLabel)
                    Spacer(Modifier.size(8.dp))

                    when {
                        legacyCommunities -> {
                            Text(
                                "Joined communities are not included in this older backup and will be kept unchanged.",
                            )
                        }
                        communityDiffs.isEmpty() -> {
                            Text("Joined communities will stay unchanged.")
                        }
                        else -> {
                            Text(
                                "Joined communities",
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.size(4.dp))
                            communityDiffs.forEach { diff ->
                                val detail = when {
                                    diff.clearsAllCurrent ->
                                        "u/${diff.account}: +${diff.added} / -${diff.removed} — will end at 0 joined communities."
                                    diff.establishesExplicitEmpty ->
                                        "u/${diff.account}: set to 0 joined communities."
                                    else ->
                                        "u/${diff.account}: +${diff.added} / -${diff.removed} → ${diff.targetCount} joined."
                                }
                                Text(
                                    detail,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (diff.clearsAllCurrent) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }

                    Spacer(Modifier.size(10.dp))
                    PortableValuesRestorePreview(
                        accountLabel = restoreAccountLabel,
                        title = "Favorite communities",
                        legacy = legacyFavoriteCommunities,
                        diffs = favoriteCommunityDiffs,
                    )
                    Spacer(Modifier.size(8.dp))
                    PortableValuesRestorePreview(
                        accountLabel = restoreAccountLabel,
                        title = "Favorite custom feeds",
                        legacy = legacyFavoriteCustomFeeds,
                        diffs = favoriteCustomFeedDiffs,
                    )
                    Spacer(Modifier.size(8.dp))
                    PortableValuesRestorePreview(
                        accountLabel = restoreAccountLabel,
                        title = "Hidden communities · privacy-sensitive",
                        legacy = legacyHiddenCommunities,
                        diffs = hiddenCommunityDiffs,
                        enabled = includeHiddenCommunities,
                        disabledMessage =
                            "This backup contains hidden-community state, but privacy-sensitive backup & restore is disabled on this device.",
                    )
                    Spacer(Modifier.size(8.dp))
                    PortableValuesRestorePreview(
                        accountLabel = restoreAccountLabel,
                        title = "Muted communities · privacy-sensitive",
                        legacy = legacyCommunityMutes,
                        diffs = communityMuteDiffs,
                        enabled = includeCommunityMutes,
                        disabledMessage =
                            "This backup contains muted-community state, but muted-community backup & restore is disabled on this device.",
                    )
                    if (!legacyCommunityMutes && includeCommunityMutes) {
                        Text(
                            "Expired timed mutes are ignored; future timed mutes keep their original end time.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.size(8.dp))
                    PortableValuesRestorePreview(
                        accountLabel = restoreAccountLabel,
                        title = "Post drafts · privacy-sensitive",
                        legacy = legacyPostComposerDrafts,
                        diffs = postComposerDraftDiffs,
                        enabled = includePostComposerDrafts,
                        disabledMessage =
                            "This backup contains text/link post drafts, but post-draft backup & restore is disabled on this device.",
                    )
                    if (!legacyPostComposerDrafts && includePostComposerDrafts) {
                        Text(
                            "Only text and link post drafts are restored. Media drafts and ownerless legacy drafts on this device stay unchanged.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.size(8.dp))
                    PortableValuesRestorePreview(
                        accountLabel = restoreAccountLabel,
                        title = "Saved moderator responses",
                        legacy = legacyModeratorSavedResponses,
                        diffs = moderatorSavedResponseDiffs,
                        enabled = includeModeratorSavedResponses,
                        disabledMessage =
                            "This backup contains saved moderator responses, but their backup & restore is disabled on this device.",
                    )
                    Spacer(Modifier.size(8.dp))
                    PortableValuesRestorePreview(
                        accountLabel = restoreAccountLabel,
                        title = "Private notes & tags · privacy-sensitive",
                        legacy = legacyUserTags,
                        diffs = userTagDiffs,
                        enabled = includeUserTags,
                        disabledMessage =
                            "This backup contains private notes & tags, but their backup & restore is disabled on this device.",
                    )
                    Spacer(Modifier.size(8.dp))
                    PortableValuesRestorePreview(
                        accountLabel = restoreAccountLabel,
                        title = "Saved searches",
                        legacy = legacySavedSearches,
                        diffs = savedSearchDiffs,
                        enabled = true,
                        disabledMessage = "",
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(
                        "A safety snapshot of your current setup will be created before restore.",
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val pending = request
                        restoreTarget = null
                        // LOWUI6_RESTORE_FINISHES_ONCE_STARTED_V1 (OCB-175): once the
                        // safety snapshot is written, leaving the screen must not
                        // skip applying the restore.
                        scope.launch(NonCancellable) {
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    repository.prepareRestore(
                                        pending.snapshot.id,
                                        pending.currentSettings,
                                        pending.currentJoinedCommunityIdsByAccount,
                                        pending.currentFavoriteCommunityNamesByAccount,
                                        pending.currentFavoriteCustomFeedPathsByAccount,
                                        pending.currentHiddenCommunityNamesByAccount,
                                        pending.currentCommunityMutesByAccount,
                                        pending.currentPostComposerDraftsByAccount,
                                        pending.currentModeratorSavedResponses,
                                        pending.currentUserTagsByAccount,
                                        pending.currentSavedSearchesByAccount,
                                    )
                                }
                            }
                            result.onSuccess { target ->
                                runCatching {
                                    state.restoreBackupState(
                                        settings = target.settingsRestoredOver(
                                            pending.currentSettings,
                                        ),
                                        joinedCommunityIdsByAccount =
                                            target.joinedCommunityIdsByAccount,
                                        favoriteCommunityNamesByAccount =
                                            target.favoriteCommunityNamesByAccount,
                                        favoriteCustomFeedPathsByAccount =
                                            target.favoriteCustomFeedPathsByAccount,
                                        hiddenCommunityNamesByAccount =
                                            target.hiddenCommunityNamesByAccount.takeIf {
                                                includeHiddenCommunities
                                            },
                                        communityMutesByAccount =
                                            target.communityMutesByAccount.takeIf {
                                                includeCommunityMutes
                                            },
                                        postComposerDraftsByAccount =
                                            target.postComposerDraftsByAccount.takeIf {
                                                includePostComposerDrafts
                                            },
                                        moderatorSavedResponses =
                                            target.moderatorSavedResponses.takeIf {
                                                includeModeratorSavedResponses
                                            },
                                        userTagsByAccount =
                                            target.userTagsByAccount.takeIf {
                                                includeUserTags
                                            },
                                        savedSearchesByAccount = target.savedSearchesByAccount,
                                    )
                                }.onSuccess {
                                    refresh()
                                    failureOperation = null
                                    failureDetails = null
                                    message = "Restored ${target.name}. A safety snapshot was saved first."
                                }.onFailure { error ->
                                    reportFailure(BackupFailureOperation.RESTORE, error)
                                }
                            }.onFailure { error ->
                                reportFailure(BackupFailureOperation.RESTORE, error)
                            }
                        }
                    },
                ) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { restoreTarget = null }) { Text("Cancel") }
            },
        )
    }

    retentionChangeTarget?.let { request ->
        val subject = when (request.kind) {
            RetentionKind.AUTOMATIC_HISTORY -> "automatic history"
            RetentionKind.SAFETY_SNAPSHOTS -> "safety snapshots"
        }
        val unaffected = when (request.kind) {
            RetentionKind.AUTOMATIC_HISTORY -> "Saved setups and safety snapshots are unaffected."
            RetentionKind.SAFETY_SNAPSHOTS -> "Saved setups and automatic history are unaffected."
        }
        AlertDialog(
            onDismissRequest = { retentionChangeTarget = null },
            title = { Text("Keep only ${request.target.label.lowercase()}?") },
            text = {
                Text(
                    "Older $subject backups above this limit will be deleted from this device and " +
                        "the connected backup folder. $unaffected",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val pending = request
                        retentionChangeTarget = null
                        runInBackground(BackupFailureOperation.DELETE) {
                            when (pending.kind) {
                                RetentionKind.AUTOMATIC_HISTORY ->
                                    repository.setAutomaticHistoryRetention(pending.target)
                                RetentionKind.SAFETY_SNAPSHOTS ->
                                    repository.setSafetySnapshotRetention(pending.target)
                            }
                            "${pending.kind.messageLabel} retention set to ${pending.target.label}."
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { retentionChangeTarget = null }) { Text("Cancel") }
            },
        )
    }

    if (deleteAllDialogVisible) {
        val total = snapshots.size
        val automaticHistoryNote = if (automaticHistory) {
            " Automatic history will start collecting new snapshots again after future settings changes."
        } else {
            " Automatic history is currently paused."
        }
        AlertDialog(
            onDismissRequest = { deleteAllDialogVisible = false },
            title = { Text("Delete all $total backups?") },
            text = {
                Text(
                    "This permanently removes all saved setups, automatic history and safety snapshots " +
                        "from this device and the connected backup folder." +
                        automaticHistoryNote,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteAllDialogVisible = false
                        runInBackground(BackupFailureOperation.DELETE) {
                            val deleted = repository.deleteAllSnapshots()
                            if (deleted == 1) "Deleted 1 backup." else "Deleted $deleted backups."
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Delete all") }
            },
            dismissButton = {
                TextButton(onClick = { deleteAllDialogVisible = false }) { Text("Cancel") }
            },
        )
    }

    deleteTarget?.let { snapshot ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete ${snapshot.name}?") },
            text = { Text("This removes the state from internal history and the connected backup folder.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        runInBackground(BackupFailureOperation.DELETE) {
                            if (repository.deleteSnapshot(snapshot.id)) {
                                "Deleted ${snapshot.name}"
                            } else {
                                "That saved state no longer exists"
                            }
                        }
                        deleteTarget = null
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun BackupProtectionCard(
    status: BackupStatus,
    folderConnected: Boolean,
    onChooseFolder: () -> Unit,
    onSync: () -> Unit,
    onDisconnect: () -> Unit,
) {
    var folderMenuExpanded by remember { mutableStateOf(false) }
    val isProtected = status.protection == BackupProtection.PROTECTED
    val waiting = status.protection == BackupProtection.WAITING
    val icon = when (status.protection) {
        BackupProtection.PROTECTED -> Icons.Rounded.CloudDone
        BackupProtection.WAITING -> Icons.Rounded.SyncProblem
        BackupProtection.INTERNAL_ONLY -> Icons.Rounded.CloudOff
    }
    val title = when (status.protection) {
        BackupProtection.PROTECTED -> "Protected outside the app"
        BackupProtection.WAITING -> "Backup folder is waiting"
        BackupProtection.INTERNAL_ONLY -> "Only saved on this device"
    }
    val lastSync = status.lastSuccessfulSyncEpochMillis?.let { timestamp ->
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))
    }
    val description = when {
        isProtected -> lastSync?.let { "Last protected $it" } ?: "Backup folder connected"
        waiting -> status.failureDetails?.toSafeTechnicalDetail()?.let { detail ->
            "Internal history continues while subshell waits for the folder. $detail"
        } ?: "Internal history continues while subshell waits for the folder."
        else -> "Internal history is automatic, but it disappears if subshell is uninstalled or app data is cleared."
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = when (status.protection) {
            BackupProtection.PROTECTED -> MaterialTheme.colorScheme.primaryContainer
            BackupProtection.WAITING -> MaterialTheme.colorScheme.tertiaryContainer
            BackupProtection.INTERNAL_ONLY -> MaterialTheme.colorScheme.secondaryContainer
        },
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = when (status.protection) {
                        BackupProtection.PROTECTED -> MaterialTheme.colorScheme.onPrimaryContainer
                        BackupProtection.WAITING -> MaterialTheme.colorScheme.onTertiaryContainer
                        BackupProtection.INTERNAL_ONLY -> MaterialTheme.colorScheme.onSecondaryContainer
                    },
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                ) {
                    Text(title, fontWeight = FontWeight.SemiBold)
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (folderConnected) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 10.dp),
                    ) {
                        Text(
                            "Backup folder",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            status.folderLocation ?: "Selected folder",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 26.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onSync) {
                        Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Sync")
                    }
                    TextButton(onClick = onChooseFolder) {
                        Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Change")
                    }
                    Box {
                        IconButton(onClick = { folderMenuExpanded = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "More backup folder options")
                        }
                        DropdownMenu(
                            expanded = folderMenuExpanded,
                            onDismissRequest = { folderMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Stop using this folder") },
                                onClick = {
                                    folderMenuExpanded = false
                                    onDisconnect()
                                },
                                leadingIcon = { Icon(Icons.Rounded.CloudOff, contentDescription = null) },
                            )
                        }
                    }
                }
            } else {
                Button(onClick = onChooseFolder, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Folder, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Choose backup folder")
                }
                Text(
                    "Recommended: create Documents/subshell Backups in the folder picker.",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun BackupRetentionCard(
    automaticHistoryRetention: BackupRetention,
    safetySnapshotRetention: BackupRetention,
    onAutomaticHistoryRetention: (BackupRetention) -> Unit,
    onSafetySnapshotRetention: (BackupRetention) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Backup retention", fontWeight = FontWeight.SemiBold)
            Text(
                "Saved setups are never removed automatically. Lowering a limit trims older backups immediately.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
            )
            BackupRetentionRow(
                title = "Automatic history",
                description = "Newest changed settings states",
                retention = automaticHistoryRetention,
                onRetentionSelected = onAutomaticHistoryRetention,
            )
            Spacer(Modifier.size(4.dp))
            BackupRetentionRow(
                title = "Safety snapshots",
                description = "Created before restore",
                retention = safetySnapshotRetention,
                onRetentionSelected = onSafetySnapshotRetention,
            )
        }
    }
}

// M3225_RETENTION_IS_AN_EXPOSED_DROPDOWN_V1 (#225, settings-08): a select field
// is M3's exposed dropdown (a non-editable field with an anchored menu), not a button
// with a hand-placed menu.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupRetentionRow(
    title: String,
    description: String,
    retention: BackupRetention,
    onRetentionSelected: (BackupRetention) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = retention.label,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(title) },
            supportingText = { Text(description) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            BackupRetention.entries.forEach { option ->
                DropdownMenuItem(
                    // ISSUE219_RETENTION_MENU_SAYS_WHICH_IS_SELECTED_V1: the check icon
                    // has no description; the item itself carries the selection.
                    modifier = Modifier.semantics { selected = option == retention },
                    text = { Text(option.label) },
                    trailingIcon = {
                        if (option == retention) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        if (option != retention) onRetentionSelected(option)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Composable
private fun SnapshotSectionTitle(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptySnapshots(icon: ImageVector, title: String, description: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
            Text(title, modifier = Modifier.padding(top = 8.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SnapshotCard(
    snapshot: SettingsSnapshot,
    onRestore: () -> Unit,
    onExport: () -> Unit,
    onRename: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val formattedDate = remember(snapshot.createdAtEpochMillis) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(snapshot.createdAtEpochMillis))
    }
    val label = when (snapshot.kind) {
        SnapshotKind.MANUAL -> "Saved setup"
        SnapshotKind.CHANGE -> "Automatic settings history"
        SnapshotKind.SAFETY -> "Safety snapshot before restore"
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    when (snapshot.kind) {
                        SnapshotKind.MANUAL -> Icons.Rounded.Save
                        SnapshotKind.CHANGE -> Icons.Rounded.History
                        SnapshotKind.SAFETY -> Icons.Rounded.Restore
                    },
                    contentDescription = null,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        snapshot.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "$formattedDate · subshell ${snapshot.appVersion}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "More actions")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        if (onRename != null) {
                            SnapshotMenuItem(Icons.Rounded.Edit, "Rename") {
                                menuExpanded = false
                                onRename()
                            }
                        }
                        SnapshotMenuItem(Icons.Rounded.Delete, "Delete") {
                            menuExpanded = false
                            onDelete()
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onRestore, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Restore, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Restore")
                }
                OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Download, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Export")
                }
            }
        }
    }
}

@Composable
private fun SnapshotMenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}

@Composable
private fun NameDialog(
    title: String,
    value: String,
    confirmLabel: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                label = { Text("Name") },
                supportingText = { Text("${value.length}/80") },
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = value.isNotBlank() && value.length <= 80,
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private enum class RetentionKind(val messageLabel: String) {
    AUTOMATIC_HISTORY("Automatic history"),
    SAFETY_SNAPSHOTS("Safety snapshot"),
}

private data class PendingRetentionChange(
    val kind: RetentionKind,
    val target: BackupRetention,
)

private data class PendingRestore(
    val snapshot: SettingsSnapshot,
    val currentSettings: SubshellSettings,
    val currentJoinedCommunityIdsByAccount: Map<String, Set<String>>,
    val currentFavoriteCommunityNamesByAccount: Map<String, Set<String>>,
    val currentFavoriteCustomFeedPathsByAccount: Map<String, Set<String>>,
    val currentHiddenCommunityNamesByAccount: Map<String, Set<String>>,
    val currentCommunityMutesByAccount: Map<String, List<PortableCommunityMute>>,
    val currentPostComposerDraftsByAccount: Map<String, List<PortablePostComposerDraft>>,
    val currentModeratorSavedResponses: List<PortableModeratorSavedResponse>,
    val currentUserTagsByAccount: Map<String, Map<String, PortableUserTag>>,
    val currentSavedSearchesByAccount: Map<String, List<SavedSearch>>,
)

// BACKUP04A4: device-wide module, shown as one row.
internal fun portableModeratorSavedResponsesRestoreDiff(
    current: List<PortableModeratorSavedResponse>,
    target: List<PortableModeratorSavedResponse>?,
): List<PortableValuesRestoreAccountDiff> = portableValuesRestoreDiff(
    current = mapOf(
        "This device" to current.mapTo(linkedSetOf()) { response ->
            "${response.id}|${response.updatedAtEpochMs}|${response.title}|${response.body}"
        },
    ),
    target = target?.let { responses ->
        mapOf(
            "This device" to responses.mapTo(linkedSetOf()) { response ->
                "${response.id}|${response.updatedAtEpochMs}|${response.title}|${response.body}"
            },
        )
    },
)

internal fun portablePostComposerDraftsRestoreDiff(
    current: Map<String, List<PortablePostComposerDraft>>,
    target: Map<String, List<PortablePostComposerDraft>>?,
): List<PortableValuesRestoreAccountDiff> = portableValuesRestoreDiff(
    current = current.mapValues { (_, drafts) ->
        drafts.mapTo(linkedSetOf()) { draft ->
            "${draft.id}|${draft.updatedAtEpochMs}|${draft.type}|${draft.title}|${draft.body}|${draft.linkUrl}"
        }
    },
    target = target?.mapValues { (_, drafts) ->
        drafts.mapTo(linkedSetOf()) { draft ->
            "${draft.id}|${draft.updatedAtEpochMs}|${draft.type}|${draft.title}|${draft.body}|${draft.linkUrl}"
        }
    },
)

internal fun portableCommunityMutesRestoreDiff(
    current: Map<String, List<PortableCommunityMute>>,
    target: Map<String, List<PortableCommunityMute>>?,
): List<PortableValuesRestoreAccountDiff> = portableValuesRestoreDiff(
    current = current.mapValues { (_, mutes) ->
        mutes.mapTo(linkedSetOf()) { mute ->
            "${mute.expiresAtEpochMillis ?: "indefinite"}|${mute.communityName.lowercase()}"
        }
    },
    target = target?.mapValues { (_, mutes) ->
        mutes.mapTo(linkedSetOf()) { mute ->
            "${mute.expiresAtEpochMillis ?: "indefinite"}|${mute.communityName.lowercase()}"
        }
    },
)

// BACKUP04A5: one value per tagged username, so an edited tag counts as -1/+1.
internal fun portableUserTagValuesByAccount(
    value: Map<String, Map<String, PortableUserTag>>,
): Map<String, Set<String>> = value.mapValues { (_, tags) ->
    tags.entries.mapTo(linkedSetOf()) { (username, tag) ->
        listOf(username, tag.label, tag.color, tag.note).joinToString("\u0000")
    }
}

// ISSUE206: one value per saved search.
internal fun savedSearchValuesByAccount(
    value: Map<String, List<SavedSearch>>,
): Map<String, Set<String>> = value.mapValues { (_, searches) ->
    searches.mapTo(linkedSetOf()) { search -> search.identityKey() }
}

internal data class PortableValuesRestoreAccountDiff(
    val account: String,
    val added: Int,
    val removed: Int,
    val currentCount: Int?,
    val targetCount: Int,
) {
    val clearsAllCurrent: Boolean
        get() = currentCount != null && currentCount > 0 && targetCount == 0

    val establishesExplicitEmpty: Boolean
        get() = currentCount == null && targetCount == 0
}

internal fun portableValuesRestoreDiff(
    current: Map<String, Set<String>>,
    target: Map<String, Set<String>>?,
): List<PortableValuesRestoreAccountDiff> {
    val restored = target ?: return emptyList()
    val effectiveTarget = if (restored.isEmpty()) {
        current.keys.associateWith { emptySet<String>() }
    } else {
        restored
    }
    return effectiveTarget.entries
        .sortedBy { (account, _) -> account }
        .mapNotNull { (account, targetValues) ->
            val hasCurrentRow = current.containsKey(account)
            val currentValues = current[account].orEmpty()
            if (hasCurrentRow && currentValues == targetValues) {
                null
            } else {
                PortableValuesRestoreAccountDiff(
                    account = account,
                    added = (targetValues - currentValues).size,
                    removed = (currentValues - targetValues).size,
                    currentCount = currentValues.size.takeIf { hasCurrentRow },
                    targetCount = targetValues.size,
                )
            }
        }
}

@Composable
private fun PortableValuesRestorePreview(
    title: String,
    legacy: Boolean,
    diffs: List<PortableValuesRestoreAccountDiff>,
    enabled: Boolean = true,
    disabledMessage: String? = null,
    accountLabel: (String) -> String = { account -> account },
) {
    Text(title, fontWeight = FontWeight.SemiBold)
    when {
        legacy -> Text(
            "Not included in this older backup; current values will be kept unchanged.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        !enabled -> Text(
            disabledMessage
                ?: "This privacy-sensitive module is disabled and will be kept unchanged.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        diffs.isEmpty() -> Text(
            "Will stay unchanged.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> {
            diffs.take(6).forEach { diff ->
                val summary = when {
                    diff.clearsAllCurrent ->
                        "${accountLabel(diff.account)}: clear ${diff.currentCount} current"
                    diff.establishesExplicitEmpty ->
                        "${accountLabel(diff.account)}: establish explicit empty"
                    else ->
                        "${accountLabel(diff.account)}: +${diff.added} / -${diff.removed}"
                }
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (diff.clearsAllCurrent) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (diffs.size > 6) {
                Text(
                    "…and ${diffs.size - 6} more accounts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

internal fun settingsRestoreChangeCount(
    current: SubshellSettings,
    target: SubshellSettings,
): Int {
    val currentValues = current.toStorageMap()
    val targetValues = target.toStorageMap()
    return (currentValues.keys + targetValues.keys)
        .count { key -> currentValues[key] != targetValues[key] }
}

// BACKUP02A3_RICHER_RESTORE_PREVIEW_V1
internal data class JoinedCommunitiesRestoreAccountDiff(
    val account: String,
    val added: Int,
    val removed: Int,
    val currentCount: Int?,
    val targetCount: Int,
) {
    val clearsAllCurrent: Boolean
        get() = currentCount != null && currentCount > 0 && targetCount == 0

    val establishesExplicitEmpty: Boolean
        get() = currentCount == null && targetCount == 0
}

internal fun joinedCommunitiesRestoreDiff(
    current: Map<String, Set<String>>,
    target: Map<String, Set<String>>?,
): List<JoinedCommunitiesRestoreAccountDiff> {
    val restored = target ?: return emptyList()
    return restored.entries
        .sortedBy { (account, _) -> account }
        .mapNotNull { (account, targetIds) ->
            val hasCurrentRow = current.containsKey(account)
            val currentIds = current[account].orEmpty()
            if (hasCurrentRow && currentIds == targetIds) {
                null
            } else {
                JoinedCommunitiesRestoreAccountDiff(
                    account = account,
                    added = (targetIds - currentIds).size,
                    removed = (currentIds - targetIds).size,
                    currentCount = currentIds.size.takeIf { hasCurrentRow },
                    targetCount = targetIds.size,
                )
            }
        }
}

internal fun joinedCommunitiesRestoreChangeCount(
    current: Map<String, Set<String>>,
    target: Map<String, Set<String>>?,
): Int = joinedCommunitiesRestoreDiff(current, target).size

private fun SettingsSnapshot.exportFileName(): String {
    val safeName = name
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .take(48)
        .ifBlank { "subshell-settings" }
    return "$safeName.subshell-settings.json"
}

/** "u/name" for an account key (stable id or username), "another account" when unknown here. */
internal fun restorePreviewAccountLabel(key: String, sessions: List<AccountSessionSummary>): String {
    sessions.firstOrNull { session -> session.stableId == key }?.let { session -> return "u/${session.username}" }
    sessions.firstOrNull { session -> session.username.equals(key, ignoreCase = true) }
        ?.let { session -> return "u/${session.username}" }
    return if (':' in key) "another account" else "u/$key"
}

// ISSUE219_BACKUP_SWITCH_ROWS_ARE_LABELLED_V1: the whole row is the switch
// (as Settings' ToggleRow, LOWUI7), so TalkBack reads the row's text with the
// switch state and a tap anywhere on the row toggles it.
// M3225_SETTINGS_ROWS_ARE_LIST_ITEMS_V1: an M3 ListItem inside its card.
@Composable
private fun BackupSwitchRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    overline: String? = null,
    icon: ImageVector? = null,
) {
    ListItem(
        overlineContent = overline?.let { text -> { Text(text) } },
        content = { Text(title) },
        supportingContent = { Text(description) },
        leadingContent = icon?.let { vector -> { Icon(vector, contentDescription = null) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    )
}
