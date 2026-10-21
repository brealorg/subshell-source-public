package app.subshell.prototype.backup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import app.subshell.prototype.ui.SubshellSettings
import app.subshell.prototype.ui.SavedSearch
import app.subshell.prototype.data.search.savedSearchesFromJson
import app.subshell.prototype.data.search.savedSearchesToJson
import java.io.File
import java.io.StringReader
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

class SettingsBackupStore(
    private val directory: File,
    private val appVersion: String,
    private val deviceId: String,
    private val now: () -> Long = System::currentTimeMillis,
    private var retentionPolicy: BackupRetentionPolicy = BackupRetentionPolicy(),
) {
    // LOW_BACKUP_DECODED_SNAPSHOTS_CACHED_V1 (OCB-454): one settings change
    // listed and decoded every snapshot several times. A file is decoded again
    // only when its size or time changed, or after a minute (decoding drops
    // mutes that expired meanwhile).
    private class DecodedFile(
        val length: Long,
        val lastModified: Long,
        val decodedAtEpochMillis: Long,
        val snapshot: SettingsSnapshot?,
    )

    private val decodedFiles = HashMap<String, DecodedFile>()

    fun listSnapshots(): List<SettingsSnapshot> = synchronized(decodedFiles) {
        val files = directory
            .listFiles { file -> file.isFile && file.extension == "json" }
            .orEmpty()
        val nowEpochMillis = now()
        decodedFiles.keys.retainAll(files.mapTo(hashSetOf(), File::getName))
        files
            .mapNotNull { file ->
                val length = file.length()
                val lastModified = file.lastModified()
                val cached = decodedFiles[file.name]
                if (
                    cached != null &&
                    cached.length == length &&
                    cached.lastModified == lastModified &&
                    nowEpochMillis - cached.decodedAtEpochMillis in 0..DECODED_SNAPSHOT_REUSE_MILLIS
                ) {
                    return@mapNotNull cached.snapshot
                }
                val snapshot = runCatching {
                    SettingsSnapshotCodec.decode(file.readText(), nowEpochMillis = nowEpochMillis)
                }.getOrNull()
                decodedFiles[file.name] = DecodedFile(length, lastModified, nowEpochMillis, snapshot)
                snapshot
            }
            .sortedByDescending(SettingsSnapshot::createdAtEpochMillis)
    }

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
    ): SettingsSnapshot = createSnapshot(
        name = validateSnapshotName(name),
        kind = SnapshotKind.MANUAL,
        settings = settings,
        joinedCommunityIdsByAccount = joinedCommunityIdsByAccount,
        favoriteCommunityNamesByAccount = favoriteCommunityNamesByAccount,
        favoriteCustomFeedPathsByAccount = favoriteCustomFeedPathsByAccount,
        hiddenCommunityNamesByAccount = hiddenCommunityNamesByAccount,
        communityMutesByAccount = communityMutesByAccount,
        postComposerDraftsByAccount = postComposerDraftsByAccount,
        moderatorSavedResponses = moderatorSavedResponses,
        userTagsByAccount = userTagsByAccount,
        savedSearchesByAccount = savedSearchesByAccount,
        watchedCommunityNamesByAccount = watchedCommunityNamesByAccount,
    )

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
        val normalizedCommunities =
            joinedCommunityIdsByAccount?.let(::normalizeJoinedCommunityIdsByAccount)
        val normalizedFavoriteCommunities =
            favoriteCommunityNamesByAccount?.let(::normalizePortableStableIdValuesByAccount)
        val normalizedFavoriteCustomFeeds =
            favoriteCustomFeedPathsByAccount?.let(::normalizePortableStableIdValuesByAccount)
        val normalizedHiddenCommunities =
            hiddenCommunityNamesByAccount?.let(::normalizePortableOpaqueStableIdValuesByAccount)
        val normalizedCommunityMutes = communityMutesByAccount?.let { raw ->
            normalizePortableCommunityMutesByAccount(raw, nowEpochMillis = now())
        }
        val normalizedPostComposerDrafts =
            postComposerDraftsByAccount?.let(::normalizePortablePostComposerDraftsByAccount)
        val normalizedModeratorSavedResponses =
            moderatorSavedResponses?.let(::normalizePortableModeratorSavedResponses)
        val normalizedUserTags =
            userTagsByAccount?.let(::normalizePortableUserTagsByAccount)
        val normalizedSavedSearches =
            savedSearchesByAccount?.let(::normalizeSavedSearchesByAccount)
        val normalizedWatchedCommunities =
            watchedCommunityNamesByAccount?.let(::normalizeWatchedCommunityNamesByAccount)
        val latest = listSnapshots().firstOrNull { it.deviceId == deviceId }
        if (
            latest?.settings == settings &&
            latest.joinedCommunityIdsByAccount == normalizedCommunities &&
            latest.favoriteCommunityNamesByAccount == normalizedFavoriteCommunities &&
            latest.favoriteCustomFeedPathsByAccount == normalizedFavoriteCustomFeeds &&
            latest.hiddenCommunityNamesByAccount == normalizedHiddenCommunities &&
            latest.communityMutesByAccount == normalizedCommunityMutes &&
            latest.postComposerDraftsByAccount == normalizedPostComposerDrafts &&
            latest.moderatorSavedResponses == normalizedModeratorSavedResponses &&
            latest.userTagsByAccount == normalizedUserTags &&
            latest.savedSearchesByAccount == normalizedSavedSearches &&
            latest.watchedCommunityNamesByAccount == normalizedWatchedCommunities
        ) {
            return null
        }
        return createSnapshot(
            name = "Automatic settings history",
            kind = SnapshotKind.CHANGE,
            settings = settings,
            joinedCommunityIdsByAccount = normalizedCommunities,
            favoriteCommunityNamesByAccount = normalizedFavoriteCommunities,
            favoriteCustomFeedPathsByAccount = normalizedFavoriteCustomFeeds,
            hiddenCommunityNamesByAccount = normalizedHiddenCommunities,
            communityMutesByAccount = normalizedCommunityMutes,
            postComposerDraftsByAccount = normalizedPostComposerDrafts,
            moderatorSavedResponses = normalizedModeratorSavedResponses,
            userTagsByAccount = normalizedUserTags,
            savedSearchesByAccount = normalizedSavedSearches,
            watchedCommunityNamesByAccount = normalizedWatchedCommunities,
        )
    }

    fun importSnapshot(json: String): SettingsSnapshot {
        require(json.length <= MAX_IMPORT_CHARACTERS) { "Backup file is too large" }
        val imported = SettingsSnapshotCodec.decode(json, nowEpochMillis = now())
        return createSnapshot(
            name = imported.name,
            kind = SnapshotKind.MANUAL,
            settings = imported.settings,
            joinedCommunityIdsByAccount = imported.joinedCommunityIdsByAccount,
            favoriteCommunityNamesByAccount = imported.favoriteCommunityNamesByAccount,
            favoriteCustomFeedPathsByAccount = imported.favoriteCustomFeedPathsByAccount,
            hiddenCommunityNamesByAccount = imported.hiddenCommunityNamesByAccount,
            communityMutesByAccount = imported.communityMutesByAccount,
            postComposerDraftsByAccount = imported.postComposerDraftsByAccount,
            moderatorSavedResponses = imported.moderatorSavedResponses,
            userTagsByAccount = imported.userTagsByAccount,
            savedSearchesByAccount = imported.savedSearchesByAccount,
            watchedCommunityNamesByAccount = imported.watchedCommunityNamesByAccount,
            sourceAppVersion = imported.appVersion,
            sourceDeviceId = imported.deviceId,
            // CONTENTFILTER07 / BACKUP07: an imported file keeps saying nothing
            // about settings it lacks, and keeps what this build cannot read.
            settingsKeysIncluded = imported.settingsKeysIncluded,
            unreadSettings = imported.unreadSettings,
        )
    }

    /**
     * BACKUP09: merges another copy of the history (the shared folder).
     * Tombstoned ids are removed here and never written back; for an id both
     * sides hold, the later change (a rename) wins.
     */
    fun mergeSnapshots(
        snapshots: List<SettingsSnapshot>,
        tombstones: Map<String, Long> = emptyMap(),
        pruneAfterMerge: Boolean = true,
    ) {
        val allTombstones = mergeTombstones(tombstones)
        val local = listSnapshots().associateBy(SettingsSnapshot::id)
        local.keys.filter(allTombstones::containsKey).forEach { id -> snapshotFile(id).delete() }
        snapshots
            .filterNot { it.id in allTombstones }
            .filter { incoming ->
                val existing = local[incoming.id]
                existing == null || incoming.lastChangedAtEpochMillis > existing.lastChangedAtEpochMillis
            }
            .forEach(::writeSnapshot)
        if (pruneAfterMerge) pruneHistory()
    }

    fun renameSnapshot(id: String, newName: String): SettingsSnapshot {
        val current = requireSnapshot(id)
        require(current.kind == SnapshotKind.MANUAL) { "Automatic history cannot be renamed" }
        val renamed = current.copy(name = validateSnapshotName(newName), updatedAtEpochMillis = now())
        writeSnapshot(renamed)
        return renamed
    }

    fun deleteSnapshot(id: String): Boolean {
        mergeTombstones(mapOf(id to now()))
        return snapshotFile(id).delete()
    }

    /** Deleted backup ids (BACKUP09 tombstones), newest first, bounded. */
    fun tombstones(): Map<String, Long> =
        tombstoneFile.takeIf(File::isFile)
            ?.let { file -> decodeBackupTombstones(file.readText()) }
            .orEmpty()

    private fun mergeTombstones(additional: Map<String, Long>): Map<String, Long> {
        val current = tombstones()
        val merged = mergeBackupTombstones(current, additional)
        if (merged != current) {
            check(directory.exists() || directory.mkdirs()) { "Could not create backup storage" }
            val temporary = File(directory, "${tombstoneFile.name}.${UUID.randomUUID()}.tmp")
            temporary.writeText(encodeBackupTombstones(merged))
            // LOW_BACKUP_TOMBSTONES_REPLACED_ATOMICALLY_V1 (OCB-599): as
            // writeSnapshot; a plain replace unlinks the target first.
            try {
                Files.move(
                    temporary.toPath(),
                    tombstoneFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), tombstoneFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
        return merged
    }

    private val tombstoneFile: File
        get() = File(directory, BACKUP_TOMBSTONE_FILE_NAME)

    fun deleteAllSnapshots(): Int {
        mergeTombstones(listSnapshots().associate { it.id to now() })
        val snapshotFiles = directory
            .listFiles { file -> file.isFile && file.extension == "json" }
            .orEmpty()
        snapshotFiles.forEach { file ->
            check(file.delete() || !file.exists()) {
                "Could not delete backup ${file.name}"
            }
        }
        directory
            .listFiles { file -> file.isFile && file.extension == "tmp" }
            .orEmpty()
            .forEach(File::delete)
        return snapshotFiles.size
    }

    fun exportSnapshot(id: String): String = SettingsSnapshotCodec.encode(requireSnapshot(id))

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
        val target = requireSnapshot(id)
        createSnapshot(
            name = safetySnapshotName(target.name),
            kind = SnapshotKind.SAFETY,
            settings = currentSettings,
            joinedCommunityIdsByAccount = currentJoinedCommunityIdsByAccount,
            favoriteCommunityNamesByAccount = currentFavoriteCommunityNamesByAccount,
            favoriteCustomFeedPathsByAccount = currentFavoriteCustomFeedPathsByAccount,
            hiddenCommunityNamesByAccount = currentHiddenCommunityNamesByAccount,
            communityMutesByAccount = currentCommunityMutesByAccount,
            postComposerDraftsByAccount = currentPostComposerDraftsByAccount,
            moderatorSavedResponses = currentModeratorSavedResponses,
            userTagsByAccount = currentUserTagsByAccount,
            savedSearchesByAccount = currentSavedSearchesByAccount,
            watchedCommunityNamesByAccount = currentWatchedCommunityNamesByAccount,
        )
        return target
    }

    fun setRetentionPolicy(policy: BackupRetentionPolicy) {
        retentionPolicy = policy
    }

    fun pruneHistory(): Int {
        val ids = snapshotIdsToPrune(listSnapshots(), retentionPolicy, ownDeviceId = deviceId, nowEpochMillis = now())
        if (ids.isNotEmpty()) mergeTombstones(ids.associateWith { now() })
        ids.forEach { id ->
            val file = snapshotFile(id)
            check(file.delete() || !file.exists()) {
                "Could not trim backup ${file.name}"
            }
        }
        return ids.size
    }

    private fun createSnapshot(
        name: String,
        kind: SnapshotKind,
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
        sourceAppVersion: String = appVersion,
        sourceDeviceId: String = deviceId,
        settingsKeysIncluded: Set<String> = SubshellSettings.storageKeys,
        unreadSettings: Map<String, String> = emptyMap(),
    ): SettingsSnapshot {
        val snapshot = SettingsSnapshot(
            id = UUID.randomUUID().toString(),
            name = validateSnapshotName(name),
            createdAtEpochMillis = now(),
            appVersion = sourceAppVersion.ifBlank { appVersion },
            kind = kind,
            deviceId = sourceDeviceId.ifBlank { deviceId },
            settings = settings,
            joinedCommunityIdsByAccount =
                joinedCommunityIdsByAccount?.let(::normalizeJoinedCommunityIdsByAccount),
            favoriteCommunityNamesByAccount =
                favoriteCommunityNamesByAccount?.let(::normalizePortableStableIdValuesByAccount),
            favoriteCustomFeedPathsByAccount =
                favoriteCustomFeedPathsByAccount?.let(::normalizePortableStableIdValuesByAccount),
            hiddenCommunityNamesByAccount =
                hiddenCommunityNamesByAccount?.let(::normalizePortableOpaqueStableIdValuesByAccount),
            communityMutesByAccount = communityMutesByAccount?.let { raw ->
                normalizePortableCommunityMutesByAccount(raw, nowEpochMillis = now())
            },
            postComposerDraftsByAccount =
                postComposerDraftsByAccount?.let(::normalizePortablePostComposerDraftsByAccount),
            moderatorSavedResponses =
                moderatorSavedResponses?.let(::normalizePortableModeratorSavedResponses),
            userTagsByAccount =
                userTagsByAccount?.let(::normalizePortableUserTagsByAccount),
            savedSearchesByAccount =
                savedSearchesByAccount?.let(::normalizeSavedSearchesByAccount),
            watchedCommunityNamesByAccount =
                watchedCommunityNamesByAccount?.let(::normalizeWatchedCommunityNamesByAccount),
            settingsKeysIncluded = settingsKeysIncluded,
            unreadSettings = unreadSettings,
        )
        writeSnapshot(snapshot)
        pruneHistory()
        return snapshot
    }

    private fun requireSnapshot(id: String): SettingsSnapshot {
        val file = snapshotFile(id)
        require(file.isFile) { "Saved setup no longer exists" }
        return SettingsSnapshotCodec.decode(file.readText(), nowEpochMillis = now())
    }

    private fun snapshotFile(id: String): File {
        require(SNAPSHOT_ID_PATTERN.matches(id)) { "Invalid saved setup id" }
        return File(directory, "$id.json")
    }

    private fun writeSnapshot(snapshot: SettingsSnapshot) {
        check(directory.exists() || directory.mkdirs()) { "Could not create backup storage" }
        val target = snapshotFile(snapshot.id)
        val temporary = File(directory, "${snapshot.id}.tmp")
        try {
            temporary.outputStream().bufferedWriter().use { writer ->
                writer.write(SettingsSnapshotCodec.encode(snapshot))
            }
            try {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temporary.delete()
        }
    }

    private companion object {
        const val MAX_IMPORT_CHARACTERS = BACKUP_IMPORT_MAX_CHARACTERS
    }
}

object SettingsSnapshotCodec {
    private val json = Json {
        prettyPrint = true
    }

    fun encode(snapshot: SettingsSnapshot): String {
        val root = buildJsonObject {
            put("schemaVersion", JsonPrimitive(SETTINGS_BACKUP_SCHEMA_VERSION))
            put("id", JsonPrimitive(snapshot.id))
            put("name", JsonPrimitive(snapshot.name))
            put("createdAtEpochMillis", JsonPrimitive(snapshot.createdAtEpochMillis))
            if (snapshot.updatedAtEpochMillis > 0L) {
                put("updatedAtEpochMillis", JsonPrimitive(snapshot.updatedAtEpochMillis))
            }
            put("appVersion", JsonPrimitive(snapshot.appVersion))
            put("kind", JsonPrimitive(snapshot.kind.name))
            put("deviceId", JsonPrimitive(snapshot.deviceId))
            put("automatic", JsonPrimitive(snapshot.automatic))
            put(
                "settings",
                buildJsonObject {
                    // A snapshot stays without the settings it never had when it is
                    // written again (import, rename), and keeps what this build
                    // could not read, so neither turns into a changed value.
                    (
                        snapshot.settings.toStorageMap()
                            .filterKeys(snapshot.settingsKeysIncluded::contains) +
                            snapshot.unreadSettings
                    ).forEach { (key, value) ->
                        put(key, JsonPrimitive(value))
                    }
                },
            )
            snapshot.joinedCommunityIdsByAccount?.let { rawCommunities ->
                put(
                    "joinedCommunities",
                    buildJsonObject {
                        normalizeJoinedCommunityIdsByAccount(rawCommunities)
                            .forEach { (account, ids) ->
                                put(
                                    account,
                                    buildJsonArray {
                                        ids.forEach { id -> add(JsonPrimitive(id)) }
                                    },
                                )
                            }
                    },
                )
            }
            snapshot.favoriteCommunityNamesByAccount?.let { rawFavorites ->
                put(
                    "favoriteCommunities",
                    buildJsonObject {
                        normalizePortableStableIdValuesByAccount(rawFavorites)
                            .forEach { (accountStableId, values) ->
                                put(
                                    accountStableId,
                                    buildJsonArray {
                                        values.forEach { value -> add(JsonPrimitive(value)) }
                                    },
                                )
                            }
                    },
                )
            }
            snapshot.favoriteCustomFeedPathsByAccount?.let { rawFavorites ->
                put(
                    "favoriteCustomFeeds",
                    buildJsonObject {
                        normalizePortableStableIdValuesByAccount(rawFavorites)
                            .forEach { (accountStableId, values) ->
                                put(
                                    accountStableId,
                                    buildJsonArray {
                                        values.forEach { value -> add(JsonPrimitive(value)) }
                                    },
                                )
                            }
                    },
                )
            }
            snapshot.hiddenCommunityNamesByAccount?.let { rawFavorites ->
                put(
                    "hiddenCommunities",
                    buildJsonObject {
                        normalizePortableOpaqueStableIdValuesByAccount(rawFavorites)
                            .forEach { (accountStableId, values) ->
                                put(
                                    accountStableId,
                                    buildJsonArray {
                                        values.forEach { value -> add(JsonPrimitive(value)) }
                                    },
                                )
                            }
                    },
                )
            }

            snapshot.communityMutesByAccount?.let { rawMutes ->
                put(
                    "communityMutes",
                    buildJsonObject {
                        normalizePortableCommunityMutesByAccount(rawMutes)
                            .forEach { (accountStableId, mutes) ->
                                put(
                                    accountStableId,
                                    buildJsonArray {
                                        mutes.forEach { mute ->
                                            add(
                                                buildJsonObject {
                                                    put(
                                                        "communityName",
                                                        JsonPrimitive(mute.communityName),
                                                    )
                                                    val expiry = mute.expiresAtEpochMillis
                                                    if (expiry == null) {
                                                        put("expiresAtEpochMillis", JsonNull)
                                                    } else {
                                                        put(
                                                            "expiresAtEpochMillis",
                                                            JsonPrimitive(expiry),
                                                        )
                                                    }
                                                },
                                            )
                                        }
                                    },
                                )
                            }
                    },
                )
            }

            snapshot.postComposerDraftsByAccount?.let { rawDrafts ->
                put(
                    "postComposerDrafts",
                    buildJsonObject {
                        normalizePortablePostComposerDraftsByAccount(rawDrafts)
                            .forEach { (accountStableId, drafts) ->
                                put(
                                    accountStableId,
                                    buildJsonArray {
                                        drafts.forEach { draft ->
                                            add(
                                                buildJsonObject {
                                                    put("id", JsonPrimitive(draft.id))
                                                    put("community", JsonPrimitive(draft.community))
                                                    put("type", JsonPrimitive(draft.type))
                                                    put("title", JsonPrimitive(draft.title))
                                                    put("body", JsonPrimitive(draft.body))
                                                    put("linkUrl", JsonPrimitive(draft.linkUrl))
                                                    put("inputMode", JsonPrimitive(draft.inputMode))
                                                    val flair = draft.flair
                                                    if (flair == null) {
                                                        put("flair", JsonNull)
                                                    } else {
                                                        put(
                                                            "flair",
                                                            buildJsonObject {
                                                                put("templateId", JsonPrimitive(flair.templateId))
                                                                put("text", JsonPrimitive(flair.text))
                                                                put("editable", JsonPrimitive(flair.editable))
                                                                flair.textColor?.let {
                                                                    put("textColor", JsonPrimitive(it))
                                                                }
                                                                flair.backgroundColor?.let {
                                                                    put("backgroundColor", JsonPrimitive(it))
                                                                }
                                                                put(
                                                                    "richText",
                                                                    buildJsonArray {
                                                                        flair.richText.forEach { item ->
                                                                            add(
                                                                                buildJsonObject {
                                                                                    put(
                                                                                        "elementType",
                                                                                        JsonPrimitive(item.elementType),
                                                                                    )
                                                                                    item.text?.let {
                                                                                        put("text", JsonPrimitive(it))
                                                                                    }
                                                                                    item.emojiRef?.let {
                                                                                        put("emojiRef", JsonPrimitive(it))
                                                                                    }
                                                                                    item.imageUrl?.let {
                                                                                        put("imageUrl", JsonPrimitive(it))
                                                                                    }
                                                                                },
                                                                            )
                                                                        }
                                                                    },
                                                                )
                                                            },
                                                        )
                                                    }
                                                    put("nsfw", JsonPrimitive(draft.nsfw))
                                                    put("spoiler", JsonPrimitive(draft.spoiler))
                                                    put("sendReplies", JsonPrimitive(draft.sendReplies))
                                                    put(
                                                        "createdAtEpochMs",
                                                        JsonPrimitive(draft.createdAtEpochMs),
                                                    )
                                                    put(
                                                        "updatedAtEpochMs",
                                                        JsonPrimitive(draft.updatedAtEpochMs),
                                                    )
                                                },
                                            )
                                        }
                                    },
                                )
                            }
                    },
                )
            }

            // BACKUP04A4_MODERATOR_SAVED_RESPONSES_SCHEMA_V8_OPT_IN_V1
            snapshot.moderatorSavedResponses?.let { rawResponses ->
                put(
                    "moderatorSavedResponses",
                    buildJsonArray {
                        normalizePortableModeratorSavedResponses(rawResponses).forEach { response ->
                            add(
                                buildJsonObject {
                                    put("id", JsonPrimitive(response.id))
                                    put("title", JsonPrimitive(response.title))
                                    put("body", JsonPrimitive(response.body))
                                    put("updatedAtEpochMs", JsonPrimitive(response.updatedAtEpochMs))
                                },
                            )
                        }
                    },
                )
            }

            // BACKUP04A5_USER_TAGS_SCHEMA_V9_OPT_IN_V1
            snapshot.userTagsByAccount?.let { rawTags ->
                put(
                    "userTags",
                    buildJsonObject {
                        normalizePortableUserTagsByAccount(rawTags).forEach { (account, tags) ->
                            put(
                                account,
                                buildJsonObject {
                                    tags.forEach { (username, tag) ->
                                        put(
                                            username,
                                            buildJsonObject {
                                                put("label", JsonPrimitive(tag.label))
                                                put("color", JsonPrimitive(tag.color))
                                                put("note", JsonPrimitive(tag.note))
                                            },
                                        )
                                    }
                                },
                            )
                        }
                    },
                )
            }

            // ISSUE206_SAVED_SEARCHES_SCHEMA_V10_V1
            snapshot.savedSearchesByAccount?.let { rawSearches ->
                put(
                    "savedSearches",
                    buildJsonObject {
                        normalizeSavedSearchesByAccount(rawSearches).forEach { (account, searches) ->
                            put(account, savedSearchesToJson(searches))
                        }
                    },
                )
            }

            // ISSUE239_WATCHED_COMMUNITIES_SCHEMA_V11_V1
            snapshot.watchedCommunityNamesByAccount?.let { rawWatched ->
                put(
                    "watchedCommunities",
                    buildJsonObject {
                        normalizeWatchedCommunityNamesByAccount(rawWatched).forEach { (account, names) ->
                            put(account, buildJsonArray { names.forEach { name -> add(JsonPrimitive(name)) } })
                        }
                    },
                )
            }
        }
        return json.encodeToString(JsonElement.serializer(), root)
    }

    fun decode(
        rawJson: String,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): SettingsSnapshot {
        val root = runCatching { json.parseToJsonElement(rawJson) }
            .getOrElse { error ->
                throw IllegalArgumentException("Backup JSON is invalid", error)
            } as? JsonObject
            ?: throw IllegalArgumentException("Backup JSON root is invalid")

        val decodedSchemaVersion = root.intValue("schemaVersion")
            ?: throw IllegalArgumentException("Unsupported backup version: missing")
        require(decodedSchemaVersion in 1..SETTINGS_BACKUP_SCHEMA_VERSION) {
            "Unsupported backup version: $decodedSchemaVersion"
        }

        val legacyAutomatic = root.booleanValue("automatic") ?: false
        val settingsMap = root["settings"]?.settingsMapOrNull()
            ?: throw IllegalArgumentException("Backup settings are missing")
        val (readableSettings, unreadSettings) =
            SubshellSettings.splitBackupSettingsValues(settingsMap)
        val joinedCommunities = if (decodedSchemaVersion >= 3) {
            root.joinedCommunityIdsByAccountOrNull()
        } else {
            null
        }
        val favoriteCommunities = if (decodedSchemaVersion >= 4) {
            root.portableStableIdValuesByAccountOrNull("favoriteCommunities")
        } else {
            null
        }
        val favoriteCustomFeeds = if (decodedSchemaVersion >= 4) {
            root.portableStableIdValuesByAccountOrNull("favoriteCustomFeeds")
        } else {
            null
        }
        val hiddenCommunities = if (decodedSchemaVersion >= 5) {
            root.portableOpaqueStableIdValuesByAccountOrNull("hiddenCommunities")
        } else {
            null
        }
        val communityMutes = if (decodedSchemaVersion >= 6) {
            root.portableCommunityMutesByAccountOrNull(
                name = "communityMutes",
                nowEpochMillis = nowEpochMillis,
            )
        } else {
            null
        }
        val postComposerDrafts = if (decodedSchemaVersion >= 7) {
            root.portablePostComposerDraftsByAccountOrNull("postComposerDrafts")
        } else {
            null
        }
        val moderatorSavedResponses = if (decodedSchemaVersion >= 8) {
            root.portableModeratorSavedResponsesOrNull("moderatorSavedResponses")
        } else {
            null
        }
        val userTags = if (decodedSchemaVersion >= 9) {
            root.portableUserTagsByAccountOrNull("userTags")
        } else {
            null
        }
        val savedSearches = if (decodedSchemaVersion >= 10) {
            root.savedSearchesByAccountOrNull("savedSearches")
        } else {
            null
        }
        // ISSUE239_WATCHED_COMMUNITIES_SCHEMA_V11_V1
        val watchedCommunities = if (decodedSchemaVersion >= 11) {
            root.watchedCommunityNamesByAccountOrNull("watchedCommunities")
        } else {
            null
        }

        return SettingsSnapshot(
            id = root.stringValue("id")?.takeIf(SNAPSHOT_ID_PATTERN::matches)
                ?: throw IllegalArgumentException("Backup id is invalid"),
            name = validateSnapshotName(root.stringValue("name").orEmpty()),
            createdAtEpochMillis = root.longValue("createdAtEpochMillis")?.takeIf { it > 0L }
                ?: throw IllegalArgumentException("Backup date is invalid"),
            updatedAtEpochMillis = root.longValue("updatedAtEpochMillis")?.takeIf { it > 0L } ?: 0L,
            appVersion = root.stringValue("appVersion")?.takeIf(String::isNotBlank)
                ?: throw IllegalArgumentException("Backup app version is missing"),
            kind = when (decodedSchemaVersion) {
                1 -> if (legacyAutomatic) SnapshotKind.SAFETY else SnapshotKind.MANUAL
                else -> root.stringValue("kind")
                    ?.let { value -> runCatching { SnapshotKind.valueOf(value) }.getOrNull() }
                    ?: throw IllegalArgumentException("Backup history type is invalid")
            },
            deviceId = root.stringValue("deviceId")?.takeIf(String::isNotBlank) ?: "legacy",
            settings = SubshellSettings.fromStorageMap(
                readableSettings.ifEmpty { SubshellSettings().toStorageMap() },
            ),
            joinedCommunityIdsByAccount = joinedCommunities,
            favoriteCommunityNamesByAccount = favoriteCommunities,
            favoriteCustomFeedPathsByAccount = favoriteCustomFeeds,
            hiddenCommunityNamesByAccount = hiddenCommunities,
            communityMutesByAccount = communityMutes,
            postComposerDraftsByAccount = postComposerDrafts,
            moderatorSavedResponses = moderatorSavedResponses,
            userTagsByAccount = userTags,
            savedSearchesByAccount = savedSearches,
            watchedCommunityNamesByAccount = watchedCommunities,
            settingsKeysIncluded = SubshellSettings.includedStorageKeys(readableSettings),
            unreadSettings = unreadSettings,
        )
    }

    // BACKUP04A5_USER_TAGS_SCHEMA_V9_OPT_IN_V1
    // Missing module = null (leave the target unchanged); present but empty
    // = empty map (authoritatively empty). Malformed accounts and rows are
    // dropped. BACKUP06_MALFORMED_MODULE_IS_MISSING_V1: a module that is not
    // an object is corrupt, not empty, and leaves the target unchanged, as
    // every other module does.
    private fun JsonObject.portableUserTagsByAccountOrNull(
        name: String,
    ): Map<String, Map<String, PortableUserTag>>? {
        if (!containsKey(name)) return null
        val raw = get(name)
        if (raw == null || raw === JsonNull) return null
        val accounts = raw as? JsonObject ?: return null
        return normalizePortableUserTagsByAccount(
            accounts.mapNotNull { (account, rawTags) ->
                val tags = rawTags as? JsonObject ?: return@mapNotNull null
                account to tags.mapNotNull { (username, rawTag) ->
                    val tag = rawTag as? JsonObject ?: return@mapNotNull null
                    username to PortableUserTag(
                        label = tag.stringValue("label").orEmpty(),
                        color = tag.stringValue("color").orEmpty(),
                        note = tag.stringValue("note").orEmpty(),
                    )
                }.toMap()
            }.toMap(),
        )
    }

    // ISSUE206_SAVED_SEARCHES_SCHEMA_V10_V1
    // Missing module = null (leave the target unchanged); present but empty
    // = empty map (authoritatively empty). A module that is not an object is
    // corrupt and leaves the target unchanged (BACKUP06); an account whose
    // value is not a list, and unreadable rows, are dropped.
    // ISSUE239_WATCHED_COMMUNITIES_SCHEMA_V11_V1
    // Missing module = null (leave the target unchanged); present but empty
    // = empty map (authoritatively empty). A module that is not an object is
    // corrupt and leaves the target unchanged (BACKUP06); an account whose
    // value is not a list, and names that are not valid, are dropped.
    private fun JsonObject.watchedCommunityNamesByAccountOrNull(
        name: String,
    ): Map<String, Set<String>>? {
        if (!containsKey(name)) return null
        val raw = get(name)
        if (raw == null || raw === JsonNull) return null
        val accounts = raw as? JsonObject ?: return null
        return normalizeWatchedCommunityNamesByAccount(
            accounts.mapNotNull { (account, rawNames) ->
                if (rawNames !is JsonArray) return@mapNotNull null
                account to rawNames.mapNotNullTo(linkedSetOf()) { value ->
                    (value as? JsonPrimitive)?.takeIf { it.isString }?.content
                }
            }.toMap(),
        )
    }

    private fun JsonObject.savedSearchesByAccountOrNull(
        name: String,
    ): Map<String, List<SavedSearch>>? {
        if (!containsKey(name)) return null
        val raw = get(name)
        if (raw == null || raw === JsonNull) return null
        val accounts = raw as? JsonObject ?: return null
        return normalizeSavedSearchesByAccount(
            accounts.mapNotNull { (account, rawSearches) ->
                if (rawSearches !is JsonArray) return@mapNotNull null
                account to savedSearchesFromJson(rawSearches)
            }.toMap(),
        )
    }

    // BACKUP04A4_MODERATOR_SAVED_RESPONSES_SCHEMA_V8_OPT_IN_V1
    // Missing module = null (leave the target unchanged); present but empty
    // = empty list (authoritatively empty). BACKUP06: a module that is not an
    // array, or whose rows are all unreadable, is corrupt and leaves the
    // target unchanged.
    private fun JsonObject.portableModeratorSavedResponsesOrNull(
        name: String,
    ): List<PortableModeratorSavedResponse>? {
        if (!containsKey(name)) return null
        val raw = get(name)
        if (raw == null || raw === JsonNull) return null
        val rows = raw as? JsonArray ?: return null
        val readable =
            rows.mapNotNull { rawRow ->
                val row = rawRow as? JsonObject ?: return@mapNotNull null
                PortableModeratorSavedResponse(
                    id = row.stringValue("id") ?: return@mapNotNull null,
                    title = row.stringValue("title") ?: return@mapNotNull null,
                    body = row.stringValue("body") ?: return@mapNotNull null,
                    updatedAtEpochMs = row.longValue("updatedAtEpochMs") ?: return@mapNotNull null,
                )
            }
        if (rows.isNotEmpty() && readable.isEmpty()) return null
        return normalizePortableModeratorSavedResponses(readable)
    }

    private fun JsonObject.stringValue(name: String): String? =
        (this[name] as? JsonPrimitive)
            ?.takeIf { primitive -> primitive !== JsonNull && primitive.isString }
            ?.content

    private fun JsonObject.intValue(name: String): Int? =
        (this[name] as? JsonPrimitive)
            ?.takeIf { primitive -> primitive !== JsonNull }
            ?.intOrNull

    private fun JsonObject.longValue(name: String): Long? =
        (this[name] as? JsonPrimitive)
            ?.takeIf { primitive -> primitive !== JsonNull }
            ?.longOrNull

    private fun JsonObject.booleanValue(name: String): Boolean? =
        (this[name] as? JsonPrimitive)
            ?.takeIf { primitive -> primitive !== JsonNull }
            ?.booleanOrNull

    private fun JsonElement.settingsMapOrNull(): Map<String, String>? {
        val objectValue = this as? JsonObject ?: return null
        return buildMap {
            objectValue.forEach { (key, rawValue) ->
                val primitive = rawValue as? JsonPrimitive
                    ?: throw IllegalArgumentException("Backup settings are invalid")
                require(primitive !== JsonNull) { "Backup settings are invalid" }
                put(key, primitive.content)
            }
        }
    }

    private fun JsonObject.portableStableIdValuesByAccountOrNull(
        name: String,
    ): Map<String, Set<String>>? {
        if (!containsKey(name)) return null
        val rawValue = this[name]
        if (rawValue == null || rawValue === JsonNull) return null
        val objectValue = rawValue as? JsonObject ?: return null

        val raw = linkedMapOf<String, Set<String>>()
        objectValue.forEach { (accountStableId, rawValues) ->
            val valuesArray = rawValues as? JsonArray ?: return@forEach
            val values = linkedSetOf<String>()
            valuesArray.forEach { rawItem ->
                val primitive = rawItem as? JsonPrimitive
                if (
                    primitive != null &&
                    primitive !== JsonNull &&
                    primitive.isString
                ) {
                    values += primitive.content
                }
            }
            raw[accountStableId] = values
        }
        return normalizePortableStableIdValuesByAccount(raw)
    }

    private fun JsonObject.portablePostComposerDraftsByAccountOrNull(
        name: String,
    ): Map<String, List<PortablePostComposerDraft>>? {
        if (!containsKey(name)) return null
        val rawByAccount = this[name]
        if (rawByAccount == null || rawByAccount === JsonNull) return null
        val byAccount = rawByAccount as? JsonObject ?: return null
        val decoded = linkedMapOf<String, List<PortablePostComposerDraft>>()
        byAccount.forEach { (accountStableId, rawDrafts) ->
            val drafts = rawDrafts as? JsonArray ?: return@forEach
            decoded[accountStableId] = drafts.mapNotNull { rawDraft ->
                val item = rawDraft as? JsonObject ?: return@mapNotNull null
                val flair = when (val rawFlair = item["flair"]) {
                    null, JsonNull -> null
                    is JsonObject -> {
                        val templateId = rawFlair.stringValue("templateId")
                            ?: return@mapNotNull null
                        val text = rawFlair.stringValue("text")
                            ?: return@mapNotNull null
                        val editable = rawFlair.booleanValue("editable")
                            ?: return@mapNotNull null
                        val rawRichText = rawFlair["richText"]
                        val richText = if (rawRichText is JsonArray) {
                            rawRichText.mapNotNull { rawEntry ->
                                val entry = rawEntry as? JsonObject ?: return@mapNotNull null
                                val elementType = entry.stringValue("elementType")
                                    ?: return@mapNotNull null
                                PortableComposerFlairRichText(
                                    elementType = elementType,
                                    text = entry.stringValue("text"),
                                    emojiRef = entry.stringValue("emojiRef"),
                                    imageUrl = entry.stringValue("imageUrl"),
                                )
                            }
                        } else {
                            emptyList()
                        }
                        PortableComposerFlair(
                            templateId = templateId,
                            text = text,
                            editable = editable,
                            textColor = rawFlair.stringValue("textColor"),
                            backgroundColor = rawFlair.stringValue("backgroundColor"),
                            richText = richText,
                        )
                    }
                    else -> return@mapNotNull null
                }
                PortablePostComposerDraft(
                    id = item.stringValue("id") ?: return@mapNotNull null,
                    community = item.stringValue("community") ?: return@mapNotNull null,
                    type = item.stringValue("type") ?: return@mapNotNull null,
                    title = item.stringValue("title") ?: return@mapNotNull null,
                    body = item.stringValue("body") ?: return@mapNotNull null,
                    linkUrl = item.stringValue("linkUrl") ?: return@mapNotNull null,
                    inputMode = item.stringValue("inputMode") ?: return@mapNotNull null,
                    flair = flair,
                    nsfw = item.booleanValue("nsfw") ?: return@mapNotNull null,
                    spoiler = item.booleanValue("spoiler") ?: return@mapNotNull null,
                    sendReplies = item.booleanValue("sendReplies") ?: return@mapNotNull null,
                    createdAtEpochMs =
                        item.longValue("createdAtEpochMs") ?: return@mapNotNull null,
                    updatedAtEpochMs =
                        item.longValue("updatedAtEpochMs") ?: return@mapNotNull null,
                )
            }
        }
        return normalizePortablePostComposerDraftsByAccount(decoded)
    }

    private fun JsonObject.portableCommunityMutesByAccountOrNull(
        name: String,
        nowEpochMillis: Long,
    ): Map<String, List<PortableCommunityMute>>? {
        if (!containsKey(name)) return null
        val rawValuesByAccount = this[name]
        if (rawValuesByAccount == null || rawValuesByAccount === JsonNull) return null
        val valuesByAccount = rawValuesByAccount as? JsonObject ?: return null

        val raw = linkedMapOf<String, List<PortableCommunityMute>>()
        valuesByAccount.forEach { (accountStableId, rawValues) ->
            val values = rawValues as? JsonArray ?: return@forEach
            raw[accountStableId] = values.mapNotNull { rawValue ->
                val item = rawValue as? JsonObject ?: return@mapNotNull null
                val communityName = item.stringValue("communityName")
                    ?: return@mapNotNull null
                if (!item.containsKey("expiresAtEpochMillis")) return@mapNotNull null
                val expiryValue = item["expiresAtEpochMillis"]
                val expiry = when {
                    expiryValue === JsonNull -> null
                    expiryValue is JsonPrimitive ->
                        expiryValue.longOrNull?.takeIf { it > 0L }
                            ?: return@mapNotNull null
                    else -> return@mapNotNull null
                }
                PortableCommunityMute(
                    communityName = communityName,
                    expiresAtEpochMillis = expiry,
                )
            }
        }
        return normalizePortableCommunityMutesByAccount(
            value = raw,
            nowEpochMillis = nowEpochMillis,
        )
    }

    private fun JsonObject.portableOpaqueStableIdValuesByAccountOrNull(
        name: String,
    ): Map<String, Set<String>>? {
        if (!containsKey(name)) return null
        val rawValuesByAccount = this[name]
        if (rawValuesByAccount == null || rawValuesByAccount === JsonNull) return null
        val valuesByAccount = rawValuesByAccount as? JsonObject ?: return null

        val raw = linkedMapOf<String, Set<String>>()
        valuesByAccount.forEach { (accountStableId, rawValues) ->
            val values =
                rawValues as? kotlinx.serialization.json.JsonArray ?: return@forEach
            raw[accountStableId] = values
                .mapNotNull { rawValue ->
                    (rawValue as? JsonPrimitive)
                        ?.takeIf { primitive ->
                            primitive !== JsonNull && primitive.isString
                        }
                        ?.content
                }
                .toSet()
        }
        return normalizePortableOpaqueStableIdValuesByAccount(raw)
    }

    private fun JsonObject.joinedCommunityIdsByAccountOrNull(): Map<String, Set<String>>? {
        if (!containsKey("joinedCommunities")) return null
        val rawCommunities = this["joinedCommunities"]
        if (rawCommunities == null || rawCommunities === JsonNull) return null
        val communities = rawCommunities as? JsonObject ?: return null

        val raw = linkedMapOf<String, Set<String>>()
        communities.forEach { (account, rawIds) ->
            val idsArray = rawIds as? JsonArray ?: return@forEach
            val ids = linkedSetOf<String>()
            idsArray.forEach { rawId ->
                val primitive = rawId as? JsonPrimitive
                if (
                    primitive != null &&
                    primitive !== JsonNull &&
                    primitive.isString
                ) {
                    ids += primitive.content
                }
            }
            raw[account] = ids
        }
        return normalizeJoinedCommunityIdsByAccount(raw)
    }
}

// BACKUP10_SAFETY_NAME_FITS_V1 (#131): the safety snapshot is named after the
// backup being restored; a name of 64-80 characters (or a restored safety
// snapshot) made it longer than the limit, and the restore failed before it
// began. The backup's name is shortened with an ellipsis to fit.
internal fun safetySnapshotName(targetName: String): String {
    val prefix = "Before restoring "
    val name = targetName.trim()
    val room = MAX_SNAPSHOT_NAME_LENGTH - prefix.length
    return prefix + if (name.length <= room) name else name.take(room - 1).trimEnd() + "…"
}

internal fun validateSnapshotName(value: String): String {
    val name = value.trim()
    require(name.isNotEmpty()) { "Give the saved setup a name" }
    require(name.length <= MAX_SNAPSHOT_NAME_LENGTH) {
        "Name must be $MAX_SNAPSHOT_NAME_LENGTH characters or fewer"
    }
    return name
}

private const val MAX_SNAPSHOT_NAME_LENGTH = 80
internal val SNAPSHOT_ID_PATTERN = Regex("[a-zA-Z0-9-]{1,80}")
// Not a .json file, so listSnapshots never reads it as a backup.
private const val DECODED_SNAPSHOT_REUSE_MILLIS = 60_000L
private const val BACKUP_TOMBSTONE_FILE_NAME = "deleted-snapshots.tombstones"
