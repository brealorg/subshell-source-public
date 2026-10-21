package app.subshell.prototype.backup

import app.subshell.prototype.ui.SavedSearch
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.subshell.prototype.BuildConfig
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.ui.SubshellSettings
import java.io.File
import java.util.UUID

enum class BackupProtection {
    PROTECTED,
    WAITING,
    INTERNAL_ONLY,
}

data class BackupStatus(
    val protection: BackupProtection,
    val lastSuccessfulSyncEpochMillis: Long?,
    val failureDetails: FailureDetails?,
    val folderLocation: String?,
)

data class BackupFolderConnection(
    val importedSnapshots: Int,
    val isProtected: Boolean,
    val failureDetails: FailureDetails?,
)

class SettingsBackupRepository(
    context: Context,
    appVersion: String,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val applicationContext = context.applicationContext
    private val resolver = applicationContext.contentResolver
    private val preferences = applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val deviceId = preferences.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also { id ->
        preferences.edit { putString(KEY_DEVICE_ID, id) }
    }
    private val store = SettingsBackupStore(
        directory = File(applicationContext.filesDir, "settings_snapshots"),
        appVersion = appVersion,
        deviceId = deviceId,
        now = now,
        retentionPolicy = retentionPolicy(),
    )
    private var externalSnapshotsById: Map<String, SettingsSnapshot> = emptyMap()
    private var vaultTombstonesUnreadable = false
    // OCB-454: the folder listing at the last read or push; null forces a read.
    private var lastVaultSignature: String? = null

    // BACKUP11_LIST_WITHOUT_SYNC_LOCK_V1 (#130): the Saved setups screen lists
    // on the main thread, and this monitor is held through whole folder
    // syncs (SAF reads and writes), so opening the screen during a slow sync
    // froze the app. The store writes each snapshot by an atomic move and
    // skips a file it cannot read, so a list needs no lock.
    fun listSnapshots(): List<SettingsSnapshot> = store.listSnapshots()

    @Synchronized
    fun saveCurrent(
        name: String,
        settings: SubshellSettings,
        joinedCommunityIdsByAccount: Map<String, Set<String>>? = null,
        favoriteCommunityNamesByAccount: Map<String, Set<String>>? = null,
        favoriteCustomFeedPathsByAccount: Map<String, Set<String>>? = null,
        hiddenCommunityNamesByAccount: Map<String, Set<String>>? = null,
        communityMutesByAccount: Map<String, List<PortableCommunityMute>>? = null,
        postComposerDraftsByAccount: Map<String, List<PortablePostComposerDraft>>? = null,
        moderatorSavedResponses: List<PortableModeratorSavedResponse>? = null,
        userTagsByAccount: Map<String, Map<String, PortableUserTag>>? = null,
        savedSearchesByAccount: Map<String, List<SavedSearch>>? = null,
        watchedCommunityNamesByAccount: Map<String, Set<String>>? = null,
    ): SettingsSnapshot {
        val externalReady = refreshFromVault()
        return store
            .saveCurrent(
                name = name,
                settings = settings,
                joinedCommunityIdsByAccount = joinedCommunityIdsByAccount,
                favoriteCommunityNamesByAccount = favoriteCommunityNamesByAccount,
                favoriteCustomFeedPathsByAccount = favoriteCustomFeedPathsByAccount,
                hiddenCommunityNamesByAccount =
                    hiddenCommunityNamesByAccount.takeIf { hiddenCommunitiesBackupEnabled() },
                communityMutesByAccount =
                    communityMutesByAccount.takeIf { communityMutesBackupEnabled() },
                postComposerDraftsByAccount =
                    postComposerDraftsByAccount.takeIf { postComposerDraftsBackupEnabled() },
                moderatorSavedResponses =
                    moderatorSavedResponses.takeIf { moderatorSavedResponsesBackupEnabled() },
                userTagsByAccount =
                    userTagsByAccount.takeIf { userTagsBackupEnabled() },
                // ISSUE206: saved searches go into every backup (owner decision).
                savedSearchesByAccount = savedSearchesByAccount,
                watchedCommunityNamesByAccount = watchedCommunityNamesByAccount,
            )
            .also { if (externalReady) pushToVault() }
    }

    @Synchronized
    fun recordSettingsChange(
        settings: SubshellSettings,
        joinedCommunityIdsByAccount: Map<String, Set<String>>? = null,
        favoriteCommunityNamesByAccount: Map<String, Set<String>>? = null,
        favoriteCustomFeedPathsByAccount: Map<String, Set<String>>? = null,
        hiddenCommunityNamesByAccount: Map<String, Set<String>>? = null,
        communityMutesByAccount: Map<String, List<PortableCommunityMute>>? = null,
        postComposerDraftsByAccount: Map<String, List<PortablePostComposerDraft>>? = null,
        moderatorSavedResponses: List<PortableModeratorSavedResponse>? = null,
        userTagsByAccount: Map<String, Map<String, PortableUserTag>>? = null,
        savedSearchesByAccount: Map<String, List<SavedSearch>>? = null,
        watchedCommunityNamesByAccount: Map<String, Set<String>>? = null,
    ): SettingsSnapshot? {
        val externalReady = refreshFromVault()
        val snapshot = if (automaticHistoryEnabled()) {
            store.recordSettingsChange(
                settings = settings,
                joinedCommunityIdsByAccount = joinedCommunityIdsByAccount,
                favoriteCommunityNamesByAccount = favoriteCommunityNamesByAccount,
                favoriteCustomFeedPathsByAccount = favoriteCustomFeedPathsByAccount,
                hiddenCommunityNamesByAccount =
                    hiddenCommunityNamesByAccount.takeIf { hiddenCommunitiesBackupEnabled() },
                communityMutesByAccount =
                    communityMutesByAccount.takeIf { communityMutesBackupEnabled() },
                postComposerDraftsByAccount =
                    postComposerDraftsByAccount.takeIf { postComposerDraftsBackupEnabled() },
                moderatorSavedResponses =
                    moderatorSavedResponses.takeIf { moderatorSavedResponsesBackupEnabled() },
                userTagsByAccount =
                    userTagsByAccount.takeIf { userTagsBackupEnabled() },
                // ISSUE206: saved searches go into every backup (owner decision).
                savedSearchesByAccount = savedSearchesByAccount,
                watchedCommunityNamesByAccount = watchedCommunityNamesByAccount,
            )
        } else {
            null
        }
        if (externalReady && (snapshot != null || !vaultMatchesLocal())) pushToVault()
        return snapshot
    }

    @Synchronized
    fun importSnapshot(json: String): SettingsSnapshot {
        val externalReady = refreshFromVault()
        return store.importSnapshot(json).also { if (externalReady) pushToVault() }
    }

    @Synchronized
    fun renameSnapshot(id: String, name: String): SettingsSnapshot {
        val externalReady = refreshFromVault()
        return store.renameSnapshot(id, name).also { if (externalReady) pushToVault() }
    }

    @Synchronized
    fun deleteSnapshot(id: String): Boolean {
        val externalReady = refreshFromVault()
        return store.deleteSnapshot(id).also { if (externalReady) pushToVault() }
    }

    @Synchronized
    fun deleteAllSnapshots(): Int {
        check(refreshFromVault()) {
            "The connected backup folder must be available before deleting all backups"
        }
        val deleted = store.deleteAllSnapshots()
        check(pushToVault()) {
            "Backups were deleted on this device, but the connected backup folder could not be updated"
        }
        return deleted
    }

    @Synchronized
    fun exportSnapshot(id: String): String = store.exportSnapshot(id)

    @Synchronized
    fun prepareRestore(
        id: String,
        currentSettings: SubshellSettings,
        currentJoinedCommunityIdsByAccount: Map<String, Set<String>>? = null,
        currentFavoriteCommunityNamesByAccount: Map<String, Set<String>>? = null,
        currentFavoriteCustomFeedPathsByAccount: Map<String, Set<String>>? = null,
        currentHiddenCommunityNamesByAccount: Map<String, Set<String>>? = null,
        currentCommunityMutesByAccount: Map<String, List<PortableCommunityMute>>? = null,
        currentPostComposerDraftsByAccount: Map<String, List<PortablePostComposerDraft>>? = null,
        currentModeratorSavedResponses: List<PortableModeratorSavedResponse>? = null,
        currentUserTagsByAccount: Map<String, Map<String, PortableUserTag>>? = null,
        currentSavedSearchesByAccount: Map<String, List<SavedSearch>>? = null,
        currentWatchedCommunityNamesByAccount: Map<String, Set<String>>? = null,
    ): SettingsSnapshot {
        val externalReady = refreshFromVault()
        return store
            .prepareRestore(
                id = id,
                currentSettings = currentSettings,
                currentJoinedCommunityIdsByAccount = currentJoinedCommunityIdsByAccount,
                currentFavoriteCommunityNamesByAccount = currentFavoriteCommunityNamesByAccount,
                currentFavoriteCustomFeedPathsByAccount = currentFavoriteCustomFeedPathsByAccount,
                currentHiddenCommunityNamesByAccount =
                    currentHiddenCommunityNamesByAccount.takeIf {
                        hiddenCommunitiesBackupEnabled()
                    },
                currentCommunityMutesByAccount =
                    currentCommunityMutesByAccount.takeIf {
                        communityMutesBackupEnabled()
                    },
                currentPostComposerDraftsByAccount =
                    currentPostComposerDraftsByAccount.takeIf {
                        postComposerDraftsBackupEnabled()
                    },
                currentModeratorSavedResponses =
                    currentModeratorSavedResponses.takeIf { moderatorSavedResponsesBackupEnabled() },
                currentUserTagsByAccount =
                    currentUserTagsByAccount.takeIf { userTagsBackupEnabled() },
                currentSavedSearchesByAccount = currentSavedSearchesByAccount,
                currentWatchedCommunityNamesByAccount = currentWatchedCommunityNamesByAccount,
            )
            .also { if (externalReady) pushToVault() }
    }

    @Synchronized
    fun connectFolder(uri: Uri): BackupFolderConnection {
        val previous = selectedTreeUri()
        lastVaultSignature = null
        resolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        preferences.edit {
            putString(KEY_TREE_URI, uri.toString())
            putString(KEY_FOLDER_LOCATION, describeFolderLocation(uri))
            putBoolean(KEY_ONBOARDING_COMPLETE, true)
            remove(KEY_LAST_ERROR)
            remove(KEY_LAST_ERROR_KIND)
        }
        // LOW_BACKUP_CHANGED_FOLDER_RELEASES_THE_OLD_GRANT_V1 (OCB-452): Change
        // kept the old folder's persisted grant (the platform caps them).
        if (previous != null && previous != uri) {
            runCatching {
                resolver.releasePersistableUriPermission(
                    previous,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        val before = store.listSnapshots().mapTo(hashSetOf(), SettingsSnapshot::id)
        val isProtected = syncNow()
        val imported = store.listSnapshots().count { it.id !in before }
        return BackupFolderConnection(
            importedSnapshots = imported,
            isProtected = isProtected,
            failureDetails = storedBackupFailureDetails(
                preferences.getString(KEY_LAST_ERROR_KIND, null),
            ),
        )
    }

    @Synchronized
    fun disconnectFolder() {
        lastVaultSignature = null
        selectedTreeUri()?.let { uri ->
            runCatching {
                resolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        preferences.edit {
            remove(KEY_TREE_URI)
            remove(KEY_FOLDER_LOCATION)
            remove(KEY_LAST_ERROR)
            remove(KEY_LAST_ERROR_KIND)
            remove(KEY_LAST_SYNC)
            remove(KEY_UNREADABLE_VAULT_FILES)
        }
        externalSnapshotsById = emptyMap()
    }

    @Synchronized
    fun syncNow(): Boolean {
        if (selectedTreeUri() == null) return false
        val refreshed = refreshFromVault()
        return if (refreshed) pushToVault() else false
    }

    // BACKUP04A1_DEVICE_LOCAL_PRIVACY_OPT_IN_V1
    fun hiddenCommunitiesBackupEnabled(): Boolean =
        preferences.getBoolean(KEY_HIDDEN_COMMUNITIES_BACKUP, false)

    fun setHiddenCommunitiesBackupEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_HIDDEN_COMMUNITIES_BACKUP, enabled) }
    }

    // BACKUP04A2_COMMUNITY_MUTES_DEVICE_LOCAL_PRIVACY_OPT_IN_V1
    fun communityMutesBackupEnabled(): Boolean =
        preferences.getBoolean(KEY_COMMUNITY_MUTES_BACKUP, false)

    fun setCommunityMutesBackupEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_COMMUNITY_MUTES_BACKUP, enabled) }
    }

    // BACKUP04A5_USER_TAGS_DEVICE_LOCAL_PRIVACY_OPT_IN_V1
    fun userTagsBackupEnabled(): Boolean =
        preferences.getBoolean(KEY_USER_TAGS_BACKUP, false)

    fun setUserTagsBackupEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_USER_TAGS_BACKUP, enabled) }
    }

    // BACKUP04A4_MODERATOR_SAVED_RESPONSES_DEVICE_LOCAL_OPT_IN_V1
    fun moderatorSavedResponsesBackupEnabled(): Boolean =
        preferences.getBoolean(KEY_MODERATOR_SAVED_RESPONSES_BACKUP, false)

    fun setModeratorSavedResponsesBackupEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_MODERATOR_SAVED_RESPONSES_BACKUP, enabled) }
    }

    // BACKUP04A3_POST_COMPOSER_DRAFTS_DEVICE_LOCAL_PRIVACY_OPT_IN_V1
    fun postComposerDraftsBackupEnabled(): Boolean =
        preferences.getBoolean(KEY_POST_COMPOSER_DRAFTS_BACKUP, false)

    fun setPostComposerDraftsBackupEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_POST_COMPOSER_DRAFTS_BACKUP, enabled) }
    }

    fun automaticHistoryEnabled(): Boolean = preferences.getBoolean(KEY_AUTOMATIC_HISTORY, true)

    fun setAutomaticHistoryEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_AUTOMATIC_HISTORY, enabled) }
    }

    fun automaticHistoryRetention(): BackupRetention = BackupRetention.fromStored(
        value = preferences.getString(KEY_AUTOMATIC_HISTORY_RETENTION, null),
        default = BackupRetention.TEN,
    )

    fun safetySnapshotRetention(): BackupRetention = BackupRetention.fromStored(
        value = preferences.getString(KEY_SAFETY_SNAPSHOT_RETENTION, null),
        default = BackupRetention.FIVE,
    )

    @Synchronized
    fun setAutomaticHistoryRetention(retention: BackupRetention) {
        setRetention(
            key = KEY_AUTOMATIC_HISTORY_RETENTION,
            retention = retention,
            unavailableMessage = "The connected backup folder must be available before trimming automatic history",
        )
    }

    @Synchronized
    fun setSafetySnapshotRetention(retention: BackupRetention) {
        setRetention(
            key = KEY_SAFETY_SNAPSHOT_RETENTION,
            retention = retention,
            unavailableMessage = "The connected backup folder must be available before trimming safety snapshots",
        )
    }

    fun shouldShowFolderOnboarding(): Boolean =
        !preferences.getBoolean(KEY_ONBOARDING_COMPLETE, false) && selectedTreeUri() == null

    fun dismissFolderOnboarding() {
        preferences.edit { putBoolean(KEY_ONBOARDING_COMPLETE, true) }
    }

    fun folderConnected(): Boolean = selectedTreeUri() != null

    fun status(): BackupStatus {
        val uri = selectedTreeUri()
        val storedFailure = storedBackupFailureDetails(
            preferences.getString(KEY_LAST_ERROR_KIND, null),
        ) ?: preferences.getString(KEY_LAST_ERROR, null)?.let {
            FailureDetails(
                retryable = true,
                localKind = app.subshell.prototype.data.error.LocalFailureKind.UNKNOWN,
            )
        } ?: FailureDetails(
            retryable = false,
            localKind = app.subshell.prototype.data.error.LocalFailureKind.DECODING,
        ).takeIf { uri != null && preferences.getInt(KEY_UNREADABLE_VAULT_FILES, 0) > 0 }
        val lastSync = preferences.getLong(KEY_LAST_SYNC, 0L).takeIf { it > 0L }
        val folderLocation = uri?.let(::cachedFolderLocation)
        return when {
            uri == null -> BackupStatus(BackupProtection.INTERNAL_ONLY, null, null, null)
            storedFailure != null -> BackupStatus(
                BackupProtection.WAITING,
                lastSync,
                storedFailure,
                folderLocation,
            )
            lastSync != null -> BackupStatus(BackupProtection.PROTECTED, lastSync, null, folderLocation)
            else -> BackupStatus(
                BackupProtection.WAITING,
                null,
                null,
                folderLocation,
            )
        }
    }

    private fun setRetention(
        key: String,
        retention: BackupRetention,
        unavailableMessage: String,
    ) {
        check(refreshFromVault(pruneAfterMerge = false)) { unavailableMessage }
        preferences.edit { putString(key, retention.name) }
        store.setRetentionPolicy(retentionPolicy())
        store.pruneHistory()
        check(pushToVault()) {
            "Backup retention changed on this device, but the connected backup folder could not be updated"
        }
    }

    private fun retentionPolicy(): BackupRetentionPolicy = BackupRetentionPolicy(
        automaticHistory = automaticHistoryRetention(),
        safetySnapshots = safetySnapshotRetention(),
    )

    private fun selectedTreeUri(): Uri? = preferences.getString(KEY_TREE_URI, null)?.let(Uri::parse)

    // MED5_FOLDER_LOCATION_CACHED_V1 (OCB-453): status() runs during
    // composition on the main thread; describing a cloud provider's folder is
    // ContentProvider and PackageManager IPC. The description is worked out
    // when the folder is connected (or once, for a folder connected before)
    // and read from preferences afterwards.
    private fun cachedFolderLocation(uri: Uri): String =
        preferences.getString(KEY_FOLDER_LOCATION, null)
            ?: describeFolderLocation(uri).also { location ->
                preferences.edit { putString(KEY_FOLDER_LOCATION, location) }
            }

    private fun describeFolderLocation(uri: Uri): String {
        val treeDocumentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
        if (uri.authority == EXTERNAL_STORAGE_AUTHORITY && treeDocumentId != null) {
            describeExternalStorageTree(treeDocumentId)?.let { return it }
        }

        val folderName = runCatching {
            val documentUri = DocumentsContract.buildDocumentUriUsingTree(uri, treeDocumentId ?: return@runCatching null)
            resolver.query(
                documentUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()?.takeIf(String::isNotBlank)

        val providerName = runCatching {
            val authority = uri.authority ?: return@runCatching null
            val provider = applicationContext.packageManager.resolveContentProvider(authority, 0)
                ?: return@runCatching null
            provider.loadLabel(applicationContext.packageManager).toString()
        }.getOrNull()?.takeIf(String::isNotBlank)

        return listOfNotNull(providerName, folderName)
            .distinct()
            .joinToString(" / ")
            .ifBlank { "Selected folder" }
    }

    private fun refreshFromVault(pruneAfterMerge: Boolean = true): Boolean {
        val uri = selectedTreeUri() ?: return true
        return runCatching {
            val vault = SettingsBackupVault(resolver, uri)
            val signature = vault.listingSignature()
            // The signature is kept only after a refresh that succeeded, and a
            // failed push clears it, so no stored error waits to be cleared here.
            if (signature == lastVaultSignature) return@runCatching
            val vaultRead = vault.readSnapshots()
            externalSnapshotsById = vaultRead.snapshots.associateBy(SettingsSnapshot::id)
            vaultTombstonesUnreadable = vaultRead.tombstonesUnreadable
            store.mergeSnapshots(
                snapshots = vaultRead.snapshots,
                tombstones = vaultRead.tombstones,
                pruneAfterMerge = pruneAfterMerge,
            )
            // BACKUP08: an unreadable file (truncated, or from a newer build)
            // no longer stops every later push; it is left untouched and
            // reported in the status instead.
            preferences.edit {
                remove(KEY_LAST_ERROR)
                remove(KEY_LAST_ERROR_KIND)
                putInt(KEY_UNREADABLE_VAULT_FILES, vaultRead.unreadableFiles)
            }
            lastVaultSignature = signature
        }.onFailure { lastVaultSignature = null }.onFailure(::recordSyncFailure).isSuccess
    }

    private fun pushToVault(): Boolean {
        val uri = selectedTreeUri() ?: return true
        return runCatching {
            val timestamp = now()
            val localSnapshots = store.listSnapshots()
            val vault = SettingsBackupVault(resolver, uri)
            vault.sync(
                snapshots = localSnapshots,
                existingSnapshots = externalSnapshotsById,
                generatedAtEpochMillis = timestamp,
                tombstones = store.tombstones(),
                vaultTombstonesUnreadable = vaultTombstonesUnreadable,
            )
            externalSnapshotsById = localSnapshots.associateBy(SettingsSnapshot::id)
            // What this push wrote is known; the next refresh need not reread it.
            lastVaultSignature = runCatching { vault.listingSignature() }.getOrNull()
            preferences.edit {
                putLong(KEY_LAST_SYNC, timestamp)
                remove(KEY_LAST_ERROR)
                remove(KEY_LAST_ERROR_KIND)
            }
        }.onFailure { lastVaultSignature = null }.onFailure(::recordSyncFailure).isSuccess
    }

    private fun recordSyncFailure(error: Throwable) {
        val details = error.toBackupFailureDetails()
        preferences.edit {
            remove(KEY_LAST_ERROR)
            putString(KEY_LAST_ERROR_KIND, details.localKind?.name)
        }
    }

    private fun vaultMatchesLocal(): Boolean =
        store.listSnapshots().associateBy(SettingsSnapshot::id) == externalSnapshotsById

    private companion object {
        const val PREFERENCES_NAME = "subshell_backup_preferences"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_TREE_URI = "tree_uri"
        const val KEY_FOLDER_LOCATION = "folder_location"
        const val KEY_LAST_SYNC = "last_sync"
        const val KEY_LAST_ERROR = "last_error"
        const val KEY_LAST_ERROR_KIND = "last_error_kind"
        const val KEY_UNREADABLE_VAULT_FILES = "unreadable_vault_files"
        const val KEY_AUTOMATIC_HISTORY = "automatic_history"
        const val KEY_HIDDEN_COMMUNITIES_BACKUP = "privacy_hidden_communities_backup"
        const val KEY_COMMUNITY_MUTES_BACKUP = "privacy_community_mutes_backup"
        const val KEY_POST_COMPOSER_DRAFTS_BACKUP = "privacy_post_composer_drafts_backup"
        const val KEY_MODERATOR_SAVED_RESPONSES_BACKUP = "privacy_moderator_saved_responses_backup"
        const val KEY_USER_TAGS_BACKUP = "privacy_user_tags_backup"
        const val KEY_AUTOMATIC_HISTORY_RETENTION = "automatic_history_retention"
        const val KEY_SAFETY_SNAPSHOT_RETENTION = "safety_snapshot_retention"
        const val KEY_ONBOARDING_COMPLETE = "folder_onboarding_complete"
        const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
    }
}

internal fun describeExternalStorageTree(documentId: String): String? {
    val separator = documentId.indexOf(':')
    if (separator < 0) return null
    val volume = documentId.substring(0, separator)
    val path = documentId.substring(separator + 1)
    val root = if (volume == "primary") "Internal storage" else volume
    return buildString {
        append(root)
        path.split('/').filter(String::isNotBlank).forEach { segment ->
            append(" / ")
            append(segment)
        }
    }
}

@Composable
fun rememberSettingsBackupRepository(): SettingsBackupRepository {
    val context = LocalContext.current
    return remember(context) { processSettingsBackupRepository(context) }
}

// BACKUP12_ONE_REPOSITORY_PER_PROCESS_V1 (OCB-598): remember(context) made a new
// repository for every Activity (a uiMode change recreates it), so two
// instances' @Synchronized monitors could overlap on the same files. One
// instance per Application is shared; a new Application (tests) gets its own.
private object ProcessSettingsBackupRepository {
    var application: Context? = null
    var repository: SettingsBackupRepository? = null
}

internal fun processSettingsBackupRepository(context: Context): SettingsBackupRepository =
    synchronized(ProcessSettingsBackupRepository) {
        val application = context.applicationContext
        val existing = ProcessSettingsBackupRepository.repository
        if (existing != null && ProcessSettingsBackupRepository.application === application) {
            existing
        } else {
            SettingsBackupRepository(context = application, appVersion = BuildConfig.VERSION_NAME).also { created ->
                ProcessSettingsBackupRepository.application = application
                ProcessSettingsBackupRepository.repository = created
            }
        }
    }
