package app.subshell.prototype.backup

import app.subshell.prototype.data.normalizeRedditCommunityName
import app.subshell.prototype.ui.ContentFilterType
import app.subshell.prototype.ui.SubshellSettings
import app.subshell.prototype.ui.UserTag
import app.subshell.prototype.ui.storedColorName
import app.subshell.prototype.ui.userTagColorFromStoredName
import app.subshell.prototype.ui.normalizeUserTags
import app.subshell.prototype.ui.SavedSearch
import app.subshell.prototype.ui.normalizeSavedSearches
import app.subshell.prototype.communityposts.normalizeWatchedCommunityNames

// BACKUP03C1_FAVORITES_SCHEMA_V4_CODEC_STORE_V1
// BACKUP04A1_HIDDEN_COMMUNITIES_SCHEMA_V5_OPT_IN_V1
// BACKUP04A2_COMMUNITY_MUTES_SCHEMA_V6_OPT_IN_V1
// BACKUP04A3_POST_COMPOSER_DRAFTS_SCHEMA_V7_OPT_IN_V1
// BACKUP04A4_MODERATOR_SAVED_RESPONSES_SCHEMA_V8_OPT_IN_V1
// BACKUP04A5_USER_TAGS_SCHEMA_V9_OPT_IN_V1
// ISSUE206_SAVED_SEARCHES_SCHEMA_V10_V1
// ISSUE239_WATCHED_COMMUNITIES_SCHEMA_V11_V1
const val SETTINGS_BACKUP_SCHEMA_VERSION = 11

const val RECENT_CHANGE_SNAPSHOT_LIMIT = 20
const val DAILY_HISTORY_DAYS = 30
const val WEEKLY_HISTORY_DAYS = 365
const val SAFETY_SNAPSHOT_LIMIT = 10

enum class BackupRetention(
    val label: String,
    val limit: Int?,
) {
    FIVE("5 backups", 5),
    TEN("10 backups", 10),
    TWENTY("20 backups", 20),
    UNLIMITED("Unlimited", null);

    fun isStricterThan(other: BackupRetention): Boolean = when {
        limit == null -> false
        other.limit == null -> true
        else -> limit < other.limit
    }

    companion object {
        fun fromStored(value: String?, default: BackupRetention): BackupRetention =
            entries.firstOrNull { it.name == value } ?: default
    }
}

data class BackupRetentionPolicy(
    val automaticHistory: BackupRetention = BackupRetention.TEN,
    val safetySnapshots: BackupRetention = BackupRetention.FIVE,
)

enum class SnapshotKind {
    MANUAL,
    CHANGE,
    SAFETY,
}

data class PortableCommunityMute(
    val communityName: String,
    val expiresAtEpochMillis: Long? = null,
)


data class PortableComposerFlairRichText(
    val elementType: String,
    val text: String? = null,
    val emojiRef: String? = null,
    val imageUrl: String? = null,
)

data class PortableComposerFlair(
    val templateId: String,
    val text: String,
    val editable: Boolean = false,
    val textColor: String? = null,
    val backgroundColor: String? = null,
    val richText: List<PortableComposerFlairRichText> = emptyList(),
)

data class PortablePostComposerDraft(
    val id: String,
    val community: String,
    val type: String,
    val title: String,
    val body: String,
    val linkUrl: String,
    val inputMode: String,
    val flair: PortableComposerFlair? = null,
    val nsfw: Boolean,
    val spoiler: Boolean,
    val sendReplies: Boolean,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

// BACKUP04A4_MODERATOR_SAVED_RESPONSES_SCHEMA_V8_OPT_IN_V1
// Device-wide (not per account): moderator response templates are text.
data class PortableModeratorSavedResponse(
    val id: String,
    val title: String,
    val body: String,
    val updatedAtEpochMs: Long,
)

data class SettingsSnapshot(
    val id: String,
    val name: String,
    val createdAtEpochMillis: Long,
    val appVersion: String,
    val kind: SnapshotKind,
    val deviceId: String,
    val settings: SubshellSettings,
    val joinedCommunityIdsByAccount: Map<String, Set<String>>? = null,
    val favoriteCommunityNamesByAccount: Map<String, Set<String>>? = null,
    val favoriteCustomFeedPathsByAccount: Map<String, Set<String>>? = null,
    val hiddenCommunityNamesByAccount: Map<String, Set<String>>? = null,
    val communityMutesByAccount: Map<String, List<PortableCommunityMute>>? = null,
    val postComposerDraftsByAccount: Map<String, List<PortablePostComposerDraft>>? = null,
    val moderatorSavedResponses: List<PortableModeratorSavedResponse>? = null,
    val userTagsByAccount: Map<String, Map<String, PortableUserTag>>? = null,
    // ISSUE206_SAVED_SEARCHES_SCHEMA_V10_V1: null = module missing (an older
    // backup), empty = authoritatively no saved searches.
    val savedSearchesByAccount: Map<String, List<SavedSearch>>? = null,
    // ISSUE239_WATCHED_COMMUNITIES_SCHEMA_V11_V1 (owner: backed up per
    // account, like favourites): null = module missing (an older backup),
    // empty = authoritatively no community watched.
    val watchedCommunityNamesByAccount: Map<String, Set<String>>? = null,
    // BACKUP07_ONLY_BACKED_UP_SETTINGS_OVERWRITE_V1 (generalises
    // CONTENTFILTER07_SCOPED_FILTERS_MISSING_KEEPS_CURRENT_V1): the settings
    // keys this backup actually contains. A backup from an older build lacks
    // the keys added since, so it says nothing about them and restore leaves
    // them as they are; a present value, even an empty list, still applies.
    val settingsKeysIncluded: Set<String> = SubshellSettings.storageKeys,
    // Keys or values this build could not read (written by a newer build);
    // kept verbatim when the snapshot is written again.
    val unreadSettings: Map<String, String> = emptyMap(),
    // BACKUP09: when the snapshot was last changed (a rename); 0 = never.
    // When two devices hold different copies, the later change wins.
    val updatedAtEpochMillis: Long = 0L,
) {
    val lastChangedAtEpochMillis: Long
        get() = maxOf(createdAtEpochMillis, updatedAtEpochMillis)

    val automatic: Boolean
        get() = kind != SnapshotKind.MANUAL

    val scopedContentFiltersIncluded: Boolean
        get() = ContentFilterType.entries.any { it.scopedStorageKey in settingsKeysIncluded }

    /** The settings a restore applies over [current]: only what the backup contains. */
    fun settingsRestoredOver(current: SubshellSettings): SubshellSettings =
        if (settingsKeysIncluded.containsAll(SubshellSettings.storageKeys)) {
            settings
        } else {
            SubshellSettings.fromStorageMap(
                current.toStorageMap() +
                    settings.toStorageMap().filterKeys(settingsKeysIncluded::contains),
            )
        }
}

internal fun normalizeJoinedCommunityIdsByAccount(
    value: Map<String, Set<String>>,
): Map<String, Set<String>> = buildMap {
    value.entries
        .sortedBy { (rawAccount, _) -> rawAccount.trim().lowercase() }
        .forEach { (rawAccount, rawIds) ->
            val account = rawAccount.trim().lowercase().takeIf(String::isNotEmpty)
                ?: return@forEach
            val normalizedIds = rawIds
                .asSequence()
                .map { id -> id.trim() }
                .filter(String::isNotEmpty)
                .map { id -> id.lowercase() }
                .toSortedSet()
            put(
                account,
                (get(account).orEmpty() + normalizedIds).toSortedSet(),
            )
        }
}

fun snapshotIdsToPrune(
    snapshots: List<SettingsSnapshot>,
    retentionPolicy: BackupRetentionPolicy = BackupRetentionPolicy(),
    ownDeviceId: String? = null,
    nowEpochMillis: Long? = null,
): Set<String> {
    val keepIds = linkedSetOf<String>()
    snapshots
        .filter { it.kind == SnapshotKind.MANUAL }
        .mapTo(keepIds, SettingsSnapshot::id)
    // BACKUP09_OWNER_TRIMS_ITS_OWN_HISTORY_V1: a device trims only the
    // automatic history it made itself; another device's history is that
    // device's to trim (its deletions arrive as tombstones).
    // BACKUP2_ORPHANED_HISTORY_IS_TRIMMED_V1 (OCB-450, owner decision
    // 2026-10-07): a device id gets a new value on a reinstall or a new phone,
    // so the old id's automatic history had no owner left to trim it. Once a
    // device has written nothing for 30 days, any device removes its
    // automatic history; manual backups are never removed this way.
    val orphanedDevices = if (ownDeviceId != null && nowEpochMillis != null) {
        snapshots
            .groupBy(SettingsSnapshot::deviceId)
            .filter { (device, written) ->
                device != ownDeviceId &&
                    nowEpochMillis - written.maxOf(SettingsSnapshot::createdAtEpochMillis) >
                    BACKUP_ORPHANED_DEVICE_AGE_MILLIS
            }
            .keys
    } else {
        emptySet()
    }
    if (ownDeviceId != null) {
        snapshots
            .filter { it.deviceId != ownDeviceId && it.deviceId !in orphanedDevices }
            .mapTo(keepIds, SettingsSnapshot::id)
    }
    val retainable = snapshots.filterNot { it.deviceId in orphanedDevices }

    keepNewest(
        snapshots = retainable,
        kind = SnapshotKind.CHANGE,
        retention = retentionPolicy.automaticHistory,
        keepIds = keepIds,
    )
    keepNewest(
        snapshots = retainable,
        kind = SnapshotKind.SAFETY,
        retention = retentionPolicy.safetySnapshots,
        keepIds = keepIds,
    )

    return snapshots
        .asSequence()
        .map(SettingsSnapshot::id)
        .filterNot(keepIds::contains)
        .toCollection(linkedSetOf())
}

internal const val BACKUP_ORPHANED_DEVICE_AGE_MILLIS = 30L * 24L * 60L * 60L * 1_000L

// BACKUP08_RETENTION_PER_DEVICE_V1: automatic history and safety snapshots
// are trimmed per device. A shared folder holds every device's history, and
// one device's short retention must not delete the others' snapshots.
private fun keepNewest(
    snapshots: List<SettingsSnapshot>,
    kind: SnapshotKind,
    retention: BackupRetention,
    keepIds: MutableSet<String>,
) {
    snapshots
        .filter { it.kind == kind }
        .groupBy(SettingsSnapshot::deviceId)
        .values
        .forEach { deviceSnapshots ->
            keepNewestOfOneDevice(deviceSnapshots, retention, keepIds)
        }
}

private fun keepNewestOfOneDevice(
    snapshots: List<SettingsSnapshot>,
    retention: BackupRetention,
    keepIds: MutableSet<String>,
) {
    val matching = snapshots
        .asSequence()
        .sortedByDescending(SettingsSnapshot::createdAtEpochMillis)
        .toList()
    val retained = retention.limit?.let { limit -> matching.take(limit) } ?: matching
    retained.mapTo(keepIds, SettingsSnapshot::id)
}

internal const val BACKUP03C_MAX_PORTABLE_ACCOUNT_ROWS = 64
internal const val BACKUP03C_MAX_PORTABLE_VALUES_PER_ACCOUNT = 500
private const val BACKUP03C_MAX_PORTABLE_ACCOUNT_ID_LENGTH = 256
private const val BACKUP03C_MAX_PORTABLE_VALUE_LENGTH = 512

// Stable account IDs are opaque ownership keys. They are normalized only for
// surrounding whitespace/case and are never remapped through usernames.
internal fun normalizePortableStableIdValuesByAccount(
    value: Map<String, Set<String>>,
): Map<String, Set<String>> = buildMap {
    value.entries
        .sortedBy { (rawAccount, _) -> rawAccount.trim().lowercase() }
        .forEach { (rawAccount, rawValues) ->
            val account = rawAccount
                .trim()
                .lowercase()
                .takeIf {
                    it.isNotEmpty() &&
                        it.length <= BACKUP03C_MAX_PORTABLE_ACCOUNT_ID_LENGTH
                }
                ?: return@forEach

            if (!containsKey(account) && size >= BACKUP03C_MAX_PORTABLE_ACCOUNT_ROWS) {
                return@forEach
            }

            val normalizedValues = (get(account).orEmpty() + rawValues)
                .asSequence()
                .map(String::trim)
                .filter {
                    it.isNotEmpty() &&
                        it.length <= BACKUP03C_MAX_PORTABLE_VALUE_LENGTH
                }
                .groupBy { it.lowercase() }
                .toSortedMap()
                .values
                .asSequence()
                .mapNotNull { variants -> variants.minOrNull() }
                .take(BACKUP03C_MAX_PORTABLE_VALUES_PER_ACCOUNT)
                .toCollection(linkedSetOf())

            put(account, normalizedValues)
        }
}
// Hidden-community persistence uses the stable account ID as an opaque native key.
// Unlike favorite preference keys, this key is not case-folded.
internal fun normalizePortableOpaqueStableIdValuesByAccount(
    value: Map<String, Set<String>>,
): Map<String, Set<String>> = buildMap {
    value.entries
        .sortedBy { (rawAccount, _) -> rawAccount.trim() }
        .forEach { (rawAccount, rawValues) ->
            val account = rawAccount
                .trim()
                .takeIf {
                    it.isNotEmpty() &&
                        it.length <= BACKUP03C_MAX_PORTABLE_ACCOUNT_ID_LENGTH
                }
                ?: return@forEach

            if (!containsKey(account) && size >= BACKUP03C_MAX_PORTABLE_ACCOUNT_ROWS) {
                return@forEach
            }

            val normalizedValues = (get(account).orEmpty() + rawValues)
                .asSequence()
                .map(String::trim)
                .filter {
                    it.isNotEmpty() &&
                        it.length <= BACKUP03C_MAX_PORTABLE_VALUE_LENGTH
                }
                .groupBy { it.lowercase() }
                .toSortedMap()
                .values
                .asSequence()
                .mapNotNull { variants -> variants.minOrNull() }
                .take(BACKUP03C_MAX_PORTABLE_VALUES_PER_ACCOUNT)
                .toCollection(linkedSetOf())

            put(account, normalizedValues)
        }
}

// Community-mute ownership uses the same opaque stable account ID semantics as hidden communities.
// Timed expiries are absolute wall-clock epoch milliseconds; expired entries are never restored.
internal fun normalizePortableCommunityMutesByAccount(
    value: Map<String, List<PortableCommunityMute>>,
    nowEpochMillis: Long? = null,
): Map<String, List<PortableCommunityMute>> = buildMap {
    value.entries
        .sortedBy { (rawAccount, _) -> rawAccount.trim() }
        .forEach { (rawAccount, rawMutes) ->
            val account = rawAccount
                .trim()
                .takeIf {
                    it.isNotEmpty() &&
                        it.length <= BACKUP03C_MAX_PORTABLE_ACCOUNT_ID_LENGTH
                }
                ?: return@forEach

            if (!containsKey(account) && size >= BACKUP03C_MAX_PORTABLE_ACCOUNT_ROWS) {
                return@forEach
            }

            val incoming = rawMutes
                .asSequence()
                .mapNotNull { mute ->
                    val communityName = runCatching {
                        normalizeRedditCommunityName(mute.communityName)
                    }.getOrNull() ?: return@mapNotNull null
                    val expiry = mute.expiresAtEpochMillis
                    if (expiry != null && expiry <= 0L) return@mapNotNull null
                    if (
                        expiry != null &&
                        nowEpochMillis != null &&
                        expiry <= nowEpochMillis
                    ) {
                        return@mapNotNull null
                    }
                    PortableCommunityMute(
                        communityName = communityName,
                        expiresAtEpochMillis = expiry,
                    )
                }
            // LOW_BACKUP_SAME_ACCOUNT_KEYS_MERGE_V1 (OCB-451): merged with what an
            // earlier key that trims to this account gave.
            val normalized = (get(account).orEmpty().asSequence() + incoming)
                .groupBy { mute -> mute.communityName.lowercase() }
                .toSortedMap()
                .values
                .asSequence()
                .mapNotNull { sameCommunity ->
                    sameCommunity.firstOrNull { mute -> mute.expiresAtEpochMillis == null }
                        ?: sameCommunity.maxByOrNull { mute ->
                            mute.expiresAtEpochMillis ?: Long.MAX_VALUE
                        }
                }
                .sortedWith(
                    compareBy(
                        String.CASE_INSENSITIVE_ORDER,
                        PortableCommunityMute::communityName,
                    ),
                )
                .take(BACKUP03C_MAX_PORTABLE_VALUES_PER_ACCOUNT)
                .toList()

            put(account, normalized)
        }
}

internal const val BACKUP04A3_MAX_PORTABLE_DRAFTS_TOTAL = 100
internal const val BACKUP04A3_MAX_PORTABLE_DRAFT_CONTENT_CHARS = 600_000
private const val BACKUP04A3_MAX_DRAFT_ID_LENGTH = 256
private const val BACKUP04A3_MAX_COMMUNITY_LENGTH = 100
private const val BACKUP04A3_MAX_TITLE_LENGTH = 300
private const val BACKUP04A3_MAX_BODY_LENGTH = 40_000
private const val BACKUP04A3_MAX_LINK_LENGTH = 8_192
private const val BACKUP04A3_MAX_FLAIR_TEMPLATE_ID_LENGTH = 256
private const val BACKUP04A3_MAX_FLAIR_TEXT_LENGTH = 64
private const val BACKUP04A3_MAX_FLAIR_COLOR_LENGTH = 64
private const val BACKUP04A3_MAX_FLAIR_RICH_TEXT_ITEMS = 20
private const val BACKUP04A3_MAX_RICH_TEXT_FIELD_LENGTH = 512
private const val BACKUP04A3_MAX_RICH_TEXT_IMAGE_URL_LENGTH = 2_048

private fun String.backup04a3Valid(maxLength: Int): Boolean =
    length <= maxLength && '\u0000' !in this

private fun PortableComposerFlair.normalizedForBackup04a3(): PortableComposerFlair? {
    val normalizedTemplateId = templateId.trim()
        .takeIf {
            it.isNotEmpty() &&
                it.backup04a3Valid(BACKUP04A3_MAX_FLAIR_TEMPLATE_ID_LENGTH)
        }
        ?: return null
    if (!text.backup04a3Valid(BACKUP04A3_MAX_FLAIR_TEXT_LENGTH)) return null
    val normalizedTextColor = textColor
        ?.trim()
        ?.takeIf { it.backup04a3Valid(BACKUP04A3_MAX_FLAIR_COLOR_LENGTH) }
    val normalizedBackground = backgroundColor
        ?.trim()
        ?.takeIf { it.backup04a3Valid(BACKUP04A3_MAX_FLAIR_COLOR_LENGTH) }
    val normalizedRichText = richText
        .asSequence()
        .mapNotNull { item ->
            val elementType = item.elementType.trim()
                .takeIf {
                    it.isNotEmpty() &&
                        it.backup04a3Valid(BACKUP04A3_MAX_RICH_TEXT_FIELD_LENGTH)
                }
                ?: return@mapNotNull null
            val normalizedText = item.text
                ?.takeIf { it.backup04a3Valid(BACKUP04A3_MAX_RICH_TEXT_FIELD_LENGTH) }
            val emojiRef = item.emojiRef
                ?.takeIf { it.backup04a3Valid(BACKUP04A3_MAX_RICH_TEXT_FIELD_LENGTH) }
            val imageUrl = item.imageUrl
                ?.trim()
                ?.takeIf {
                    it.length <= BACKUP04A3_MAX_RICH_TEXT_IMAGE_URL_LENGTH &&
                        it.startsWith("https://", ignoreCase = true)
                }
            PortableComposerFlairRichText(
                elementType = elementType,
                text = normalizedText,
                emojiRef = emojiRef,
                imageUrl = imageUrl,
            )
        }
        .take(BACKUP04A3_MAX_FLAIR_RICH_TEXT_ITEMS)
        .toList()
    return PortableComposerFlair(
        templateId = normalizedTemplateId,
        text = text,
        editable = editable,
        textColor = normalizedTextColor,
        backgroundColor = normalizedBackground,
        richText = normalizedRichText,
    )
}

private fun PortablePostComposerDraft.normalizedForBackup04a3(): PortablePostComposerDraft? {
    val normalizedId = id.trim()
        .takeIf {
            it.isNotEmpty() &&
                it.backup04a3Valid(BACKUP04A3_MAX_DRAFT_ID_LENGTH)
        }
        ?: return null
    val normalizedType = type.trim().uppercase()
        .takeIf { it == "TEXT" || it == "LINK" }
        ?: return null
    val normalizedInputMode = inputMode.trim().uppercase()
        .takeIf { it.isNotEmpty() && it.length <= 64 }
        ?: return null
    if (!community.backup04a3Valid(BACKUP04A3_MAX_COMMUNITY_LENGTH)) return null
    if (!title.backup04a3Valid(BACKUP04A3_MAX_TITLE_LENGTH)) return null
    if (!body.backup04a3Valid(BACKUP04A3_MAX_BODY_LENGTH)) return null
    if (!linkUrl.backup04a3Valid(BACKUP04A3_MAX_LINK_LENGTH)) return null
    if (createdAtEpochMs <= 0L || updatedAtEpochMs < createdAtEpochMs) return null

    val normalizedFlair = flair?.normalizedForBackup04a3()
    val hasContent =
        title.isNotBlank() ||
            body.isNotBlank() ||
            linkUrl.isNotBlank() ||
            normalizedFlair != null ||
            nsfw ||
            spoiler ||
            !sendReplies
    if (!hasContent) return null

    return copy(
        id = normalizedId,
        type = normalizedType,
        inputMode = normalizedInputMode,
        flair = normalizedFlair,
    )
}

private fun PortablePostComposerDraft.backup04a3CharacterCost(): Int =
    id.length +
        community.length +
        type.length +
        title.length +
        body.length +
        linkUrl.length +
        inputMode.length +
        (flair?.let { value ->
            value.templateId.length +
                value.text.length +
                value.textColor.orEmpty().length +
                value.backgroundColor.orEmpty().length +
                value.richText.sumOf { item ->
                    item.elementType.length +
                        item.text.orEmpty().length +
                        item.emojiRef.orEmpty().length +
                        item.imageUrl.orEmpty().length
                }
        } ?: 0)

internal fun normalizePortablePostComposerDraftsByAccount(
    value: Map<String, List<PortablePostComposerDraft>>,
): Map<String, List<PortablePostComposerDraft>> {
    val rows = linkedMapOf<String, List<PortablePostComposerDraft>>()
    value.entries
        .sortedBy { (rawAccount, _) -> rawAccount.trim() }
        .forEach { (rawAccount, rawDrafts) ->
            val account = rawAccount
                .trim()
                .takeIf {
                    it.isNotEmpty() &&
                        it.length <= BACKUP03C_MAX_PORTABLE_ACCOUNT_ID_LENGTH
                }
                ?: return@forEach
            if (!rows.containsKey(account) && rows.size >= BACKUP03C_MAX_PORTABLE_ACCOUNT_ROWS) {
                return@forEach
            }
            // LOW_BACKUP_SAME_ACCOUNT_KEYS_MERGE_V1 (OCB-451): two keys that trim
            // to one account merge instead of the later dropping the earlier.
            rows[account] = (rows[account].orEmpty() + rawDrafts
                .mapNotNull(PortablePostComposerDraft::normalizedForBackup04a3))
                .sortedWith(
                    compareByDescending<PortablePostComposerDraft> { it.updatedAtEpochMs }
                        .thenBy { it.id },
                )
        }

    val acceptedById = linkedMapOf<String, Pair<String, PortablePostComposerDraft>>()
    var characterBudget = 0
    rows.entries
        .flatMap { (account, drafts) -> drafts.map { account to it } }
        .sortedWith(
            compareByDescending<Pair<String, PortablePostComposerDraft>> { it.second.updatedAtEpochMs }
                .thenBy { it.second.id }
                .thenBy { it.first },
        )
        .forEach { (account, draft) ->
            if (acceptedById.size >= BACKUP04A3_MAX_PORTABLE_DRAFTS_TOTAL) return@forEach
            if (acceptedById.containsKey(draft.id)) return@forEach
            val cost = draft.backup04a3CharacterCost()
            if (characterBudget + cost > BACKUP04A3_MAX_PORTABLE_DRAFT_CONTENT_CHARS) {
                return@forEach
            }
            acceptedById[draft.id] = account to draft
            characterBudget += cost
        }

    return buildMap {
        rows.forEach { (account, _) ->
            put(
                account,
                acceptedById.values
                    .asSequence()
                    .filter { (owner, _) -> owner == account }
                    .map { (_, draft) -> draft }
                    .sortedWith(
                        compareByDescending<PortablePostComposerDraft> { it.updatedAtEpochMs }
                            .thenBy { it.id },
                    )
                    .toList(),
            )
        }
    }
}

// BACKUP04A4_MODERATOR_SAVED_RESPONSES_SCHEMA_V8_OPT_IN_V1
internal const val BACKUP04A4_MAX_SAVED_RESPONSES = 50
internal const val BACKUP04A4_MAX_TITLE_CHARS = 60
internal const val BACKUP04A4_MAX_BODY_CHARS = 10_000

internal fun normalizePortableModeratorSavedResponses(
    value: List<PortableModeratorSavedResponse>,
): List<PortableModeratorSavedResponse> = value
    .mapNotNull { response ->
        val id = response.id.trim().takeIf { it.isNotEmpty() && it.length <= 64 } ?: return@mapNotNull null
        val title = response.title.trim().take(BACKUP04A4_MAX_TITLE_CHARS).takeIf(String::isNotEmpty)
            ?: return@mapNotNull null
        val body = response.body.trim().take(BACKUP04A4_MAX_BODY_CHARS).takeIf(String::isNotEmpty)
            ?: return@mapNotNull null
        if (response.updatedAtEpochMs < 0L) return@mapNotNull null
        PortableModeratorSavedResponse(id = id, title = title, body = body, updatedAtEpochMs = response.updatedAtEpochMs)
    }
    .sortedWith(compareByDescending<PortableModeratorSavedResponse> { it.updatedAtEpochMs }.thenBy { it.id })
    .distinctBy(PortableModeratorSavedResponse::id)
    .sortedWith(compareBy<PortableModeratorSavedResponse> { it.title.lowercase() }.thenByDescending { it.updatedAtEpochMs })
    .take(BACKUP04A4_MAX_SAVED_RESPONSES)

// BACKUP04A5_USER_TAGS_SCHEMA_V9_OPT_IN_V1
// Private notes and tags on Reddit usernames (issue #34), per signed-in
// account. Keyed by the opaque stable account ID (not case-folded, like
// hidden communities), then by the lowercased username. The colour travels
// as its name; an unknown colour shows as grey and keeps its name (TAGCOLOR01).
data class PortableUserTag(
    val label: String,
    val color: String,
    val note: String,
)

internal const val BACKUP04A5_MAX_TAGS_PER_ACCOUNT = 2_000

internal fun PortableUserTag.toUserTag(): UserTag {
    val (known, unknownColorName) = userTagColorFromStoredName(color)
    return UserTag(label = label, color = known, note = note, unknownColorName = unknownColorName)
}

internal fun UserTag.toPortableUserTag(): PortableUserTag =
    PortableUserTag(label = label, color = storedColorName, note = note)

// ISSUE206_SAVED_SEARCHES_V1 (#206): per-account saved searches, the same
// account-id rules as the other per-account modules; each account's list
// valid, distinct, newest first and bounded.
// ISSUE239_WATCHED_COMMUNITIES_SCHEMA_V11_V1: per-account watched communities,
// the same account-id rules as the other per-account modules; each account's
// names valid, distinct and at most MAX_WATCHED_COMMUNITIES.
internal fun normalizeWatchedCommunityNamesByAccount(
    value: Map<String, Set<String>>,
): Map<String, Set<String>> = buildMap {
    value.entries
        .sortedBy { (rawAccount, _) -> rawAccount.trim() }
        .forEach { (rawAccount, rawNames) ->
            val account = rawAccount
                .trim()
                .takeIf {
                    it.isNotEmpty() &&
                        it.length <= BACKUP03C_MAX_PORTABLE_ACCOUNT_ID_LENGTH
                }
                ?: return@forEach
            if (!containsKey(account) && size >= BACKUP03C_MAX_PORTABLE_ACCOUNT_ROWS) {
                return@forEach
            }
            put(account, normalizeWatchedCommunityNames(get(account).orEmpty() + rawNames).toSet())
        }
}

internal fun normalizeSavedSearchesByAccount(
    value: Map<String, List<SavedSearch>>,
): Map<String, List<SavedSearch>> = buildMap {
    value.entries
        .sortedBy { (rawAccount, _) -> rawAccount.trim() }
        .forEach { (rawAccount, rawSearches) ->
            val account = rawAccount
                .trim()
                .takeIf {
                    it.isNotEmpty() &&
                        it.length <= BACKUP03C_MAX_PORTABLE_ACCOUNT_ID_LENGTH
                }
                ?: return@forEach
            if (!containsKey(account) && size >= BACKUP03C_MAX_PORTABLE_ACCOUNT_ROWS) {
                return@forEach
            }
            put(account, normalizeSavedSearches(get(account).orEmpty() + rawSearches))
        }
}

internal fun normalizePortableUserTagsByAccount(
    value: Map<String, Map<String, PortableUserTag>>,
): Map<String, Map<String, PortableUserTag>> = buildMap {
    value.entries
        .sortedBy { (rawAccount, _) -> rawAccount.trim() }
        .forEach { (rawAccount, rawTags) ->
            val account = rawAccount
                .trim()
                .takeIf {
                    it.isNotEmpty() &&
                        it.length <= BACKUP03C_MAX_PORTABLE_ACCOUNT_ID_LENGTH
                }
                ?: return@forEach
            if (!containsKey(account) && size >= BACKUP03C_MAX_PORTABLE_ACCOUNT_ROWS) {
                return@forEach
            }
            val merged = get(account).orEmpty().mapValues { (_, tag) -> tag.toUserTag() } +
                rawTags.mapValues { (_, tag) -> tag.toUserTag() }
            put(
                account,
                normalizeUserTags(merged)
                    .toSortedMap()
                    .entries
                    .take(BACKUP04A5_MAX_TAGS_PER_ACCOUNT)
                    .associate { (username, tag) -> username to tag.toPortableUserTag() },
            )
        }
}
