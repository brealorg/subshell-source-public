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
import app.subshell.prototype.ui.symbols.Symbols
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ListItem
import app.subshell.prototype.ui.error.LocalViewportErrorHostState
import app.subshell.prototype.ui.noticeInfo
import androidx.compose.foundation.clickable
import androidx.compose.material3.Tab
import androidx.compose.material3.SecondaryTabRow
import app.subshell.prototype.ui.PortableBackupState
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
    // ISSUE270_BACKUP_NOTICES_USE_THE_TRAY_V1: an answer to an action goes to the
    // app's one message surface (ISSUE222), which shows wherever the reader is.
    val notices = LocalViewportErrorHostState.current
    fun showNotice(notice: BackupNotice) = notices.noticeInfo(notice.title, notice.detail)
    var failureOperation by remember { mutableStateOf<BackupFailureOperation?>(null) }
    var failureDetails by remember { mutableStateOf<FailureDetails?>(null) }
    var saveDialogVisible by remember { mutableStateOf(false) }
    var saveName by remember { mutableStateOf("My backup") }
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
    var backupTab by rememberSaveable { mutableStateOf(BackupListTab.YOURS) }

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
        failureOperation = operation
        failureDetails = error.toBackupFailureDetails()
        refresh()
    }

    fun runInBackground(
        operation: BackupFailureOperation,
        action: () -> BackupNotice,
    ) {
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching(action) }
            result.onSuccess { notice ->
                refresh()
                failureOperation = null
                failureDetails = null
                showNotice(notice)
            }.onFailure { error ->
                reportFailure(operation, error)
            }
        }
    }

    fun recordPortableChange(currentPortable: PortableBackupState) {
        repository.recordSettingsChange(
            settings = currentPortable.settings,
            joinedCommunityIdsByAccount = currentPortable.joinedCommunityIdsByAccount,
            favoriteCommunityNamesByAccount = currentPortable.favoriteCommunityNamesByAccount,
            favoriteCustomFeedPathsByAccount = currentPortable.favoriteCustomFeedPathsByAccount,
            hiddenCommunityNamesByAccount = currentPortable.hiddenCommunityNamesByAccount,
            communityMutesByAccount = currentPortable.communityMutesByAccount,
            postComposerDraftsByAccount = currentPortable.postComposerDraftsByAccount,
            moderatorSavedResponses = currentPortable.moderatorSavedResponses,
            userTagsByAccount = currentPortable.userTagsByAccount,
            savedSearchesByAccount = currentPortable.savedSearchesByAccount,
            watchedCommunityNamesByAccount = currentPortable.watchedCommunityNamesByAccount,
        )
    }

    // A privacy-sensitive switch also decides whether a restore applies the data.
    fun recordBackupChoice(enabled: Boolean, subject: String) {
        val notice = if (enabled) {
            BackupNotice("$subject will be backed up", "New backups hold them, and a restore brings them back.")
        } else {
            BackupNotice("$subject are left out", "New backups leave them out, and a restore leaves yours as they are.")
        }
        if (automaticHistory) {
            val currentPortable = state.capturePortableBackupState()
            runInBackground(BackupFailureOperation.SAVE) {
                recordPortableChange(currentPortable)
                notice
            }
        } else {
            showNotice(notice)
        }
    }

    fun pendingRestoreOf(snapshot: SettingsSnapshot) = PendingRestore(
        snapshot = snapshot,
        currentSettings = state.captureSettings(),
        currentJoinedCommunityIdsByAccount = state.captureJoinedCommunityIdsByAccount(),
        currentFavoriteCommunityNamesByAccount = state.captureFavoriteCommunityNamesByAccount(),
        currentFavoriteCustomFeedPathsByAccount = state.captureFavoriteCustomFeedPathsByAccount(),
        currentHiddenCommunityNamesByAccount = state.captureHiddenCommunityNamesByAccount(),
        currentCommunityMutesByAccount = state.captureCommunityMutesByAccount(),
        currentPostComposerDraftsByAccount = state.capturePostComposerDraftsByAccount(),
        currentModeratorSavedResponses = state.captureModeratorSavedResponses(),
        currentUserTagsByAccount = state.captureUserTagsByAccount(),
        currentSavedSearchesByAccount = state.captureSavedSearchesByAccount(),
        currentWatchedCommunityNamesByAccount = state.captureWatchedCommunityNamesByAccount(),
    )

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
                        BackupNotice("Backup folder connected", "Found ${connection.importedSnapshots} backups in it.")
                    connection.isProtected -> BackupNotice("Backup folder connected", "New backups are copied to it.")
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
                BackupNotice("Backup exported", "${snapshotName.orEmpty()} is saved as a file.")
            }
        } else if (uri != null) {
            // The picker made the file but nothing says what to write: remove it.
            runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri) }
            reportFailure(
                BackupFailureOperation.EXPORT,
                IllegalStateException("The backup to export was lost; export it again."),
            )
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            // ISSUE270_BACKUP_SCREEN_LEADS_WITH_ITS_ACTIONS_V1: the file joins
            // Your backups and its restore preview opens; Cancel keeps it there.
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val json = requireNotNull(context.contentResolver.openInputStream(uri)) {
                            "Could not open the selected backup"
                        }.bufferedReader().use {
                            it.readBackupTextBounded(BACKUP_IMPORT_MAX_CHARACTERS)
                        }
                        repository.importSnapshot(json)
                    }
                }
                result.onSuccess { imported ->
                    refresh()
                    failureOperation = null
                    failureDetails = null
                    backupTab = BackupListTab.YOURS
                    showNotice(BackupNotice("Added ${imported.displayName()}", "It's under Your backups."))
                    restoreTarget = pendingRestoreOf(imported)
                }.onFailure { error ->
                    reportFailure(BackupFailureOperation.IMPORT, error)
                }
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
                    Icon(Symbols.ArrowBack, contentDescription = "Back")
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
                                BackupNotice("Backup folder is up to date", "Every backup is copied to it.")
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
                            BackupNotice("Stopped copying to the backup folder", "The backups already in it were kept.")
                        }
                    },
                )
            }

            // ISSUE270_BACKUP_SCREEN_LEADS_WITH_ITS_ACTIONS_V1 (owner 2026-10-27):
            // back up and restore come first; "Restore from file" opens the restore
            // preview of the file it read.
            item("actions") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column {
                        BackupActionRow(
                            icon = Symbols.Save,
                            title = "Back up now",
                            description = "Keep your current setup as a backup",
                            onClick = { saveDialogVisible = true },
                        )
                        BackupActionRow(
                            icon = Symbols.UploadFile,
                            title = "Restore from file",
                            description = "Choose a backup file to restore",
                            onClick = {
                                importLauncher.launch(
                                    arrayOf("application/json", "text/plain", "application/octet-stream"),
                                )
                            },
                        )
                    }
                }
            }

            // ISSUE270_BACKUP_SCREEN_SAYS_WHAT_IS_BACKED_UP_V1: what every backup
            // holds, then the privacy-sensitive choices, then what never goes in.
            item("contents-title") { BackupSectionTitle("What's backed up") }
            item("contents") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column {
                        BackupIncludedRow("Settings", "Everything you choose in Settings")
                        BackupIncludedRow(
                            "Communities",
                            "Joined, favorites and favorite custom feeds, for each account",
                        )
                        BackupIncludedRow("Saved searches and new-post notifications", "For each account")
                    }
                }
            }

            item("privacy-sensitive-title") { BackupSubsectionTitle("Privacy-sensitive backup data") }
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
                                recordBackupChoice(enabled, "Hidden communities")
                            },
                            title = "Hidden communities",
                        )
                        BackupSwitchRow(
                            checked = includeCommunityMutes,
                            onCheckedChange = { enabled ->
                                includeCommunityMutes = enabled
                                repository.setCommunityMutesBackupEnabled(enabled)
                                recordBackupChoice(enabled, "Muted communities")
                            },
                            title = "Muted communities",
                            description = "Timed mutes keep their end time",
                        )
                        BackupSwitchRow(
                            checked = includePostComposerDrafts,
                            onCheckedChange = { enabled ->
                                includePostComposerDrafts = enabled
                                repository.setPostComposerDraftsBackupEnabled(enabled)
                                recordBackupChoice(enabled, "Post drafts")
                            },
                            title = "Post drafts",
                            description = "Text and link drafts; images stay on this device",
                        )
                        // BACKUP04A4_MODERATOR_SAVED_RESPONSES_SCHEMA_V8_OPT_IN_V1
                        BackupSwitchRow(
                            checked = includeModeratorSavedResponses,
                            onCheckedChange = { enabled ->
                                includeModeratorSavedResponses = enabled
                                repository.setModeratorSavedResponsesBackupEnabled(enabled)
                                recordBackupChoice(enabled, "Saved moderator responses")
                            },
                            title = "Saved moderator responses",
                            description = "Shared by every account on this device",
                        )
                        // BACKUP04A5_USER_TAGS_SCHEMA_V9_OPT_IN_V1
                        BackupSwitchRow(
                            checked = includeUserTags,
                            onCheckedChange = { enabled ->
                                includeUserTags = enabled
                                repository.setUserTagsBackupEnabled(enabled)
                                recordBackupChoice(enabled, "Private notes & tags")
                            },
                            title = "Private notes & tags",
                            description = "The notes and tags you put on users",
                        )
                    }
                }
            }

            item("never-backed-up") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        Symbols.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        "Never backed up: sign-ins and your Reddit Client ID (sign in again after " +
                            "restoring), cached posts, history and posts saved for offline.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ISSUE270_BACKUPS_ARE_ONE_LIST_WITH_THREE_TABS_V1: each kind keeps its
            // own switch and limit under its tab.
            item("backups-title") { BackupSectionTitle("Backups") }
            item("backups-tabs") {
                // Three equal tabs; each label fits a third of a phone.
                SecondaryTabRow(
                    selectedTabIndex = backupTab.ordinal,
                    containerColor = Color.Transparent,
                ) {
                    BackupListTab.entries.forEach { tab ->
                        Tab(
                            selected = tab == backupTab,
                            onClick = { backupTab = tab },
                            text = { Text(tab.label, maxLines = 1, softWrap = false) },
                        )
                    }
                }
            }
            when (backupTab) {
                BackupListTab.YOURS -> item("yours-note") {
                    BackupListNote("Kept until you delete them.")
                }
                BackupListTab.AUTOMATIC -> {
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
                                            recordPortableChange(currentPortable)
                                            BackupNotice("Automatic backups are on", "A backup is made after you change a setting.")
                                        }
                                    } else {
                                        showNotice(BackupNotice("Automatic backups are off", "The ones already made were kept."))
                                    }
                                },
                                title = "Back up automatically",
                                description = "After you change a setting",
                            )
                        }
                    }
                    item("automatic-retention") {
                        BackupRetentionRow(
                            title = "Keep the newest",
                            retention = automaticHistoryRetention,
                            onRetentionSelected = { retention ->
                                if (retention.isStricterThan(automaticHistoryRetention)) {
                                    retentionChangeTarget = PendingRetentionChange(
                                        kind = RetentionKind.AUTOMATIC_HISTORY,
                                        target = retention,
                                    )
                                } else {
                                    runInBackground(BackupFailureOperation.DELETE) {
                                        repository.setAutomaticHistoryRetention(retention)
                                        BackupNotice("Keeping the newest ${retention.label.lowercase()}", "Of the backups made automatically.")
                                    }
                                }
                            },
                        )
                    }
                }
                BackupListTab.BEFORE_RESTORE -> {
                    item("before-restore-note") {
                        BackupListNote(
                            "Before every restore, subshell backs up your current setup here, so you can undo it.",
                        )
                    }
                    item("before-restore-retention") {
                        BackupRetentionRow(
                            title = "Keep the newest",
                            retention = safetySnapshotRetention,
                            onRetentionSelected = { retention ->
                                if (retention.isStricterThan(safetySnapshotRetention)) {
                                    retentionChangeTarget = PendingRetentionChange(
                                        kind = RetentionKind.SAFETY_SNAPSHOTS,
                                        target = retention,
                                    )
                                } else {
                                    runInBackground(BackupFailureOperation.DELETE) {
                                        repository.setSafetySnapshotRetention(retention)
                                        BackupNotice("Keeping the newest ${retention.label.lowercase()}", "Of the backups made before a restore.")
                                    }
                                }
                            },
                        )
                    }
                }
            }
            val shownSnapshots = when (backupTab) {
                BackupListTab.YOURS -> manualSnapshots
                BackupListTab.AUTOMATIC -> historySnapshots
                BackupListTab.BEFORE_RESTORE -> safetySnapshots
            }
            if (loadedSnapshots == null) {
                item("backups-loading") {
                    EmptySnapshots(
                        icon = Symbols.History,
                        title = "Loading backups…",
                        description = "Reading the backups on this device.",
                    )
                }
            } else if (shownSnapshots.isEmpty()) {
                item("backups-empty") {
                    when (backupTab) {
                        BackupListTab.YOURS -> EmptySnapshots(
                            icon = Symbols.Backup,
                            title = "No backups yet",
                            description = "Back up now to keep a backup until you delete it.",
                        )
                        BackupListTab.AUTOMATIC -> EmptySnapshots(
                            icon = Symbols.History,
                            title = "No automatic backups yet",
                            description = "A backup is made after you change a setting.",
                        )
                        BackupListTab.BEFORE_RESTORE -> EmptySnapshots(
                            icon = Symbols.History,
                            title = "Nothing here yet",
                            description = "A backup is made here before every restore.",
                        )
                    }
                }
            } else {
                items(shownSnapshots, key = SettingsSnapshot::id) { snapshot ->
                    SnapshotCard(
                        snapshot = snapshot,
                        onRestore = { restoreTarget = pendingRestoreOf(snapshot) },
                        onExport = {
                            pendingExportId = snapshot.id
                            pendingExportName = snapshot.displayName()
                            exportLauncher.launch(snapshot.exportFileName())
                        },
                        onRename = if (snapshot.kind == SnapshotKind.MANUAL) {
                            {
                                renameTarget = snapshot
                                renameValue = snapshot.name
                            }
                        } else {
                            null
                        },
                        onDelete = { deleteTarget = snapshot },
                    )
                }
            }

            if (snapshots.isNotEmpty()) {
                item("delete-all") {
                    OutlinedButton(
                        onClick = { deleteAllDialogVisible = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(Symbols.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Delete all backups")
                    }
                }
            }
        }
    }

    if (saveDialogVisible) {
        NameDialog(
            title = "Back up now",
            value = saveName,
            confirmLabel = "Back up",
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
                val currentWatchedCommunities = state.captureWatchedCommunityNamesByAccount()
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
                        currentWatchedCommunities,
                    )
                    BackupNotice("Backed up", "It's under Your backups as ${saved.name}.")
                }
                saveDialogVisible = false
                saveName = "My backup"
            },
        )
    }

    renameTarget?.let { snapshot ->
        NameDialog(
            title = "Rename backup",
            value = renameValue,
            confirmLabel = "Rename",
            onValueChange = { renameValue = it },
            onDismiss = { renameTarget = null },
            onConfirm = {
                val name = renameValue
                runInBackground(BackupFailureOperation.RENAME) {
                    val renamed = repository.renameSnapshot(snapshot.id, name)
                    BackupNotice("Renamed", "It's now called ${renamed.name}.")
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
        // ISSUE239_WATCHED_COMMUNITIES_SCHEMA_V11_V1: always restored with the backup.
        val watchedCommunityDiffs = portableValuesRestoreDiff(
            current = request.currentWatchedCommunityNamesByAccount,
            target = request.snapshot.watchedCommunityNamesByAccount,
        )
        val legacyWatchedCommunities = request.snapshot.watchedCommunityNamesByAccount == null

        AlertDialog(
            onDismissRequest = { restoreTarget = null },
            title = { Text("Restore ${request.snapshot.displayName()}?") },
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
                            "In this backup, but turned off under Privacy-sensitive backup data, so it stays as it is.",
                    )
                    Spacer(Modifier.size(8.dp))
                    PortableValuesRestorePreview(
                        accountLabel = restoreAccountLabel,
                        title = "Muted communities · privacy-sensitive",
                        legacy = legacyCommunityMutes,
                        diffs = communityMuteDiffs,
                        enabled = includeCommunityMutes,
                        disabledMessage =
                            "In this backup, but turned off under Privacy-sensitive backup data, so it stays as it is.",
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
                            "In this backup, but turned off under Privacy-sensitive backup data, so it stays as it is.",
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
                            "In this backup, but turned off under Privacy-sensitive backup data, so it stays as it is.",
                    )
                    Spacer(Modifier.size(8.dp))
                    PortableValuesRestorePreview(
                        accountLabel = restoreAccountLabel,
                        title = "Private notes & tags · privacy-sensitive",
                        legacy = legacyUserTags,
                        diffs = userTagDiffs,
                        enabled = includeUserTags,
                        disabledMessage =
                            "In this backup, but turned off under Privacy-sensitive backup data, so it stays as it is.",
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
                    Spacer(Modifier.size(8.dp))
                    PortableValuesRestorePreview(
                        accountLabel = restoreAccountLabel,
                        title = "New-post notifications",
                        legacy = legacyWatchedCommunities,
                        diffs = watchedCommunityDiffs,
                        enabled = true,
                        disabledMessage = "",
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(
                        "subshell backs up your current setup first, so you can undo this.",
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
                        scope.launch(start = CoroutineStart.UNDISPATCHED) {
                            withContext(NonCancellable) {
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
                                            pending.currentWatchedCommunityNamesByAccount,
                                        )
                                    }
                                }
                                result.onSuccess { target ->
                                    runCatching {
                                        // COMMIT01_BACKUP_RESTORE_WRITES_OFF_MAIN_V1 (#243)
                                        val prepared = state.prepareBackupRestore(
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
                                            watchedCommunityNamesByAccount = target.watchedCommunityNamesByAccount,
                                        )
                                        withContext(Dispatchers.IO) { state.persistBackupRestore(prepared) }
                                        state.applyBackupRestore(prepared)
                                    }.onSuccess {
                                        refresh()
                                        failureOperation = null
                                        failureDetails = null
                                        showNotice(BackupNotice("Restored ${target.displayName()}", "Your setup before it is under Undo."))
                                    }.onFailure { error ->
                                        reportFailure(BackupFailureOperation.RESTORE, error)
                                    }
                                }.onFailure { error ->
                                    reportFailure(BackupFailureOperation.RESTORE, error)
                                }
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
            RetentionKind.AUTOMATIC_HISTORY -> "automatic backups"
            RetentionKind.SAFETY_SNAPSHOTS -> "backups made before a restore"
        }
        val unaffected = when (request.kind) {
            RetentionKind.AUTOMATIC_HISTORY -> "Your backups and the ones made before a restore stay."
            RetentionKind.SAFETY_SNAPSHOTS -> "Your backups and automatic backups stay."
        }
        AlertDialog(
            onDismissRequest = { retentionChangeTarget = null },
            title = { Text("Keep only ${request.target.label.lowercase()}?") },
            text = {
                Text(
                    "Older $subject over this limit are deleted from this device and " +
                        "the backup folder. $unaffected",
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
                            BackupNotice("Keeping the newest ${pending.target.label.lowercase()}", "Of the backups ${pending.kind.messageLabel}.")
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
            " New automatic backups are made after your next settings change."
        } else {
            " Automatic backups are off."
        }
        AlertDialog(
            onDismissRequest = { deleteAllDialogVisible = false },
            title = { Text("Delete all $total backups?") },
            text = {
                Text(
                    "This removes your backups, automatic backups and the ones made before a restore " +
                        "from this device and the backup folder." +
                        automaticHistoryNote,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteAllDialogVisible = false
                        runInBackground(BackupFailureOperation.DELETE) {
                            val deleted = repository.deleteAllSnapshots()
                            BackupNotice(if (deleted == 1) "Deleted 1 backup" else "Deleted $deleted backups", "They're gone from this device and the backup folder.")
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
            title = { Text("Delete ${snapshot.displayName()}?") },
            text = { Text("It's removed from this device and the backup folder.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        runInBackground(BackupFailureOperation.DELETE) {
                            if (repository.deleteSnapshot(snapshot.id)) {
                                BackupNotice("Deleted ${snapshot.displayName()}", "It's gone from this device and the backup folder.")
                            } else {
                                BackupNotice("Nothing to delete", "That backup no longer exists.")
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
        BackupProtection.PROTECTED -> Symbols.CloudDone
        BackupProtection.WAITING -> Symbols.SyncProblem
        BackupProtection.INTERNAL_ONLY -> Symbols.CloudOff
    }
    val title = when (status.protection) {
        BackupProtection.PROTECTED -> "Copied to your backup folder"
        BackupProtection.WAITING -> "Backup folder isn't reachable"
        BackupProtection.INTERNAL_ONLY -> "Only on this device"
    }
    val lastSync = status.lastSuccessfulSyncEpochMillis?.let { timestamp ->
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))
    }
    val description = when {
        isProtected -> lastSync?.let { "Last copy $it" } ?: "Backup folder connected"
        waiting -> status.failureDetails?.toSafeTechnicalDetail()?.let { detail ->
            "Backups are still kept on this device. $detail"
        } ?: "Backups are still kept on this device."
        else -> "Backups are lost if subshell is uninstalled or its data is cleared."
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
                        Symbols.Folder,
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
                        Icon(Symbols.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy now")
                    }
                    TextButton(onClick = onChooseFolder) {
                        Icon(Symbols.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Change")
                    }
                    Box {
                        IconButton(onClick = { folderMenuExpanded = true }) {
                            Icon(Symbols.MoreVert, contentDescription = "More backup folder options")
                        }
                        DropdownMenu(
                            expanded = folderMenuExpanded,
                            onDismissRequest = { folderMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Stop copying to this folder") },
                                onClick = {
                                    folderMenuExpanded = false
                                    onDisconnect()
                                },
                                leadingIcon = { Icon(Symbols.CloudOff, contentDescription = null) },
                            )
                        }
                    }
                }
            } else {
                OutlinedButton(onClick = onChooseFolder, modifier = Modifier.padding(start = 36.dp)) {
                    Icon(Symbols.Folder, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Choose backup folder")
                }
                Text(
                    "For example, create Documents/subshell Backups in the folder picker.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 36.dp),
                )
            }
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
                                Symbols.Check,
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
private fun BackupSectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    )
}

@Composable
private fun BackupSubsectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
    )
}

/** ISSUE270_BACKUP_SCREEN_LEADS_WITH_ITS_ACTIONS_V1: an action is a list row, like the rest. */
@Composable
private fun BackupActionRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    ListItem(
        content = { Text(title) },
        supportingContent = { Text(description) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Icon(Symbols.ChevronRight, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

@Composable
private fun BackupListNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

/** ISSUE270_BACKUP_SCREEN_SAYS_WHAT_IS_BACKED_UP_V1: always in every backup. */
@Composable
private fun BackupIncludedRow(title: String, description: String) {
    ListItem(
        content = { Text(title) },
        supportingContent = { Text(description) },
        leadingContent = {
            Icon(Symbols.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

private data class BackupNotice(val title: String, val detail: String)

private enum class BackupListTab(val label: String) {
    YOURS("Yours"),
    AUTOMATIC("Automatic"),
    BEFORE_RESTORE("Undo"),
}

// Automatic backups were named "Automatic settings history" when they were made.
private fun SettingsSnapshot.displayName(): String =
    if (kind == SnapshotKind.CHANGE) "Automatic backup" else name

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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    when (snapshot.kind) {
                        SnapshotKind.MANUAL -> Symbols.Save
                        SnapshotKind.CHANGE -> Symbols.History
                        SnapshotKind.SAFETY -> Symbols.History
                    },
                    contentDescription = null,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        snapshot.displayName(),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "$formattedDate · subshell ${snapshot.appVersion}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Symbols.MoreVert, contentDescription = "More actions")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        if (onRename != null) {
                            SnapshotMenuItem(Symbols.Edit, "Rename") {
                                menuExpanded = false
                                onRename()
                            }
                        }
                        SnapshotMenuItem(Symbols.Delete, "Delete") {
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
                    Icon(Symbols.History, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Restore")
                }
                OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                    Icon(Symbols.Download, contentDescription = null)
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
    AUTOMATIC_HISTORY("made automatically"),
    SAFETY_SNAPSHOTS("made before a restore"),
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
    val currentWatchedCommunityNamesByAccount: Map<String, Set<String>>,
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
    modifier: Modifier = Modifier,
    description: String? = null,
    overline: String? = null,
    icon: ImageVector? = null,
) {
    ListItem(
        overlineContent = overline?.let { text -> { Text(text) } },
        content = { Text(title) },
        supportingContent = description?.let { text -> { Text(text) } },
        leadingContent = icon?.let { vector -> { Icon(vector, contentDescription = null) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    )
}
