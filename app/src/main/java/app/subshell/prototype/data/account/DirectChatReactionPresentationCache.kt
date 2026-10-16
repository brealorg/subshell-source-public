package app.subshell.prototype.data.account

// SUBSHELL_MESSAGES02E2A_R3D_PERSISTED_REACTION_PRESENTATION_CACHE_V1

import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Log
import app.subshell.prototype.data.auth.reddit.primitiveOrNull
import app.subshell.prototype.model.MessageBubble
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

data class DirectChatReactionCachedTarget(
    val targetEventId: String,
    val outgoing: Boolean,
    val createdEpochSeconds: Long?,
    val mediaId: String?,
    val bodySha256: String?,
) {
    init {
        require(targetEventId.startsWith('$')) {
            "Cached Direct Chat reaction target must be a Matrix event ID"
        }
        require(mediaId != null || bodySha256 != null) {
            "Cached Direct Chat reaction target needs a non-sensitive match fingerprint"
        }
        require(bodySha256 == null || bodySha256.matches(SHA256_HEX_REGEX)) {
            "Cached Direct Chat reaction text fingerprint must be SHA-256"
        }
    }
}

data class DirectChatReactionPresentationSnapshot(
    val targets: List<DirectChatReactionCachedTarget>,
    val reactions: List<DirectChatReaction>,
    val updatedAtEpochMs: Long,
) {
    init {
        require(updatedAtEpochMs >= 0L) {
            "Direct Chat reaction presentation cache timestamp cannot be negative"
        }
    }
}

internal class DirectChatReactionPresentationCache(
    private val preferences: SharedPreferences,
    private val json: Json,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    fun load(
        accountStableId: String,
        inboxItemId: String,
        participantUsername: String,
    ): DirectChatReactionPresentationSnapshot? {
        val startedAtMs = SystemClock.elapsedRealtime()
        val storageKey = directChatReactionPresentationCacheStorageKey(
            accountStableId = accountStableId,
            inboxItemId = inboxItemId,
            participantUsername = participantUsername,
        ) ?: return null

        val raw = preferences.getString(storageKey, null)
        if (raw.isNullOrBlank()) {
            Log.d(
                CACHE_LOG_TAG,
                "result=miss reason=absent load_ms=${SystemClock.elapsedRealtime() - startedAtMs}",
            )
            return null
        }

        val now = currentTimeMillis()
        val snapshot = decodeDirectChatReactionPresentationSnapshot(
            raw = raw,
            nowEpochMs = now,
            json = json,
        )
        if (snapshot == null) {
            preferences.edit().remove(storageKey).apply()
            Log.d(
                CACHE_LOG_TAG,
                "result=miss reason=expired_or_invalid " +
                    "load_ms=${SystemClock.elapsedRealtime() - startedAtMs}",
            )
            return null
        }

        Log.d(
            CACHE_LOG_TAG,
            "result=hit targets=${snapshot.targets.size} reactions=${snapshot.reactions.size} " +
                "age_ms=${(now - snapshot.updatedAtEpochMs).coerceAtLeast(0L)} " +
                "load_ms=${SystemClock.elapsedRealtime() - startedAtMs}",
        )
        return snapshot
    }

    fun save(
        accountStableId: String,
        inboxItemId: String,
        participantUsername: String,
        messages: List<MessageBubble>,
        reactions: List<DirectChatReaction>,
    ) {
        val storageKey = directChatReactionPresentationCacheStorageKey(
            accountStableId = accountStableId,
            inboxItemId = inboxItemId,
            participantUsername = participantUsername,
        ) ?: return

        val now = currentTimeMillis()
        // ACCOUNT01_REACTION_CACHE_MERGE_V1: the live window holds only the
        // recent messages; targets outside it keep their cached reactions.
        val stored = preferences.getString(storageKey, null)
            ?.let { raw -> decodeDirectChatReactionPresentationSnapshot(raw, now, json) }
        val snapshot = mergeDirectChatReactionPresentationSnapshots(
            live = directChatReactionPresentationSnapshot(
                messages = messages,
                reactions = reactions,
                updatedAtEpochMs = now,
            ),
            stored = stored,
            liveMessageIds = messages.map(MessageBubble::id).toSet(),
        )
        // LOW_DATA_REACTION_CACHE_PRUNED_HOURLY_V1 (OCB-602): pruning decodes
        // every cached chat; once an hour per process is enough for a 24 h cache.
        if (now - lastPruneEpochMs >= DIRECT_CHAT_REACTION_PRESENTATION_CACHE_PRUNE_INTERVAL_MS || now < lastPruneEpochMs) {
            lastPruneEpochMs = now
            pruneDirectChatReactionPresentationCache(preferences, now, json, keep = storageKey)
        }
        if (snapshot.targets.isEmpty() || snapshot.reactions.isEmpty()) {
            preferences.edit().remove(storageKey).apply()
            Log.d(CACHE_LOG_TAG, "result=cleared")
            return
        }

        preferences.edit()
            .putString(
                storageKey,
                encodeDirectChatReactionPresentationSnapshot(snapshot),
            )
            .apply()
        Log.d(
            CACHE_LOG_TAG,
            "result=saved targets=${snapshot.targets.size} reactions=${snapshot.reactions.size}",
        )
    }

    /**
     * ACCOUNT01_REACTION_CACHE_ACCOUNT_SCOPED_V1: removes every cached entry of
     * [accountStableId] (called when the account is logged out).
     */
    fun removeAccount(accountStableId: String) {
        val prefix = directChatReactionPresentationCacheAccountPrefix(accountStableId) ?: return
        val keys = preferences.all.keys.filter { key -> key.startsWith(prefix) }
        if (keys.isEmpty()) return
        preferences.edit().apply { keys.forEach(::remove) }.apply()
        Log.d(CACHE_LOG_TAG, "result=account_removed entries=${keys.size}")
    }

    companion object {
        private const val CACHE_LOG_TAG = "subshell-reaction-cache"

        @Volatile
        private var lastPruneEpochMs = Long.MIN_VALUE / 2

        internal fun resetPruneClockForTest() {
            lastPruneEpochMs = Long.MIN_VALUE / 2
        }
    }
}

/**
 * [live] (the displayed window) wins for every message it holds; cached
 * targets outside that window keep their reactions.
 */
internal fun mergeDirectChatReactionPresentationSnapshots(
    live: DirectChatReactionPresentationSnapshot,
    stored: DirectChatReactionPresentationSnapshot?,
    liveMessageIds: Set<String>,
): DirectChatReactionPresentationSnapshot {
    if (stored == null) return live
    val liveTargetIds = live.targets.map(DirectChatReactionCachedTarget::targetEventId).toSet()
    val keptTargets = stored.targets.filter { target ->
        target.targetEventId !in liveTargetIds && target.targetEventId !in liveMessageIds
    }
    val targets = (keptTargets + live.targets)
        .distinctBy(DirectChatReactionCachedTarget::targetEventId)
        .takeLast(DIRECT_CHAT_REACTION_PRESENTATION_CACHE_MAX_TARGETS)
    val targetIds = targets.map(DirectChatReactionCachedTarget::targetEventId).toSet()
    val keptTargetIds = keptTargets.map(DirectChatReactionCachedTarget::targetEventId).toSet()
    val reactions = (stored.reactions.filter { reaction -> reaction.targetEventId in keptTargetIds } + live.reactions)
        .filter { reaction -> reaction.targetEventId in targetIds }
        .distinctBy(DirectChatReaction::reactionEventId)
        .takeLast(DIRECT_CHAT_REACTION_PRESENTATION_CACHE_MAX_REACTIONS)
    // LOW_DATA_KEPT_REACTIONS_KEEP_THEIR_AGE_V1 (OCB-463): targets carried
    // over from the stored entry keep its age, so they expire 24 h after they
    // were last confirmed instead of getting a fresh lease on every save.
    val keptAny = targets.any { target -> target.targetEventId in keptTargetIds }
    return DirectChatReactionPresentationSnapshot(
        targets = targets,
        reactions = reactions,
        updatedAtEpochMs = if (keptAny) {
            minOf(stored.updatedAtEpochMs, live.updatedAtEpochMs)
        } else {
            live.updatedAtEpochMs
        },
    )
}

/**
 * Drops expired or unreadable entries and entries in the key format before
 * ACCOUNT01 (no account segment, so logout could not find them). [keep] is
 * the entry being written.
 */
internal fun pruneDirectChatReactionPresentationCache(
    preferences: SharedPreferences,
    nowEpochMs: Long,
    json: Json,
    keep: String? = null,
) {
    val stale = preferences.all.entries
        .filter { (key, _) -> key.startsWith(DIRECT_CHAT_REACTION_PRESENTATION_CACHE_KEY_PREFIX) && key != keep }
        .filter { (key, value) ->
            !DIRECT_CHAT_REACTION_PRESENTATION_CACHE_KEY_REGEX.matches(key) ||
                (value as? String)?.let { raw ->
                    decodeDirectChatReactionPresentationSnapshot(raw, nowEpochMs, json)
                } == null
        }
        .map { (key, _) -> key }
    if (stale.isEmpty()) return
    preferences.edit().apply { stale.forEach(::remove) }.apply()
}

internal fun directChatReactionPresentationSnapshot(
    messages: List<MessageBubble>,
    reactions: List<DirectChatReaction>,
    updatedAtEpochMs: Long,
): DirectChatReactionPresentationSnapshot {
    val boundedReactions = reactions
        .asSequence()
        .filter { reaction -> redditDirectChatReactionAsset(reaction.key) != null }
        .distinctBy(DirectChatReaction::reactionEventId)
        .toList()
        .takeLast(DIRECT_CHAT_REACTION_PRESENTATION_CACHE_MAX_REACTIONS)

    val targetIds = boundedReactions
        .asSequence()
        .map(DirectChatReaction::targetEventId)
        .distinct()
        .toList()
        .takeLast(DIRECT_CHAT_REACTION_PRESENTATION_CACHE_MAX_TARGETS)

    val messageById = messages
        .asSequence()
        .filter { message -> message.id in targetIds && message.id.startsWith('$') }
        .distinctBy(MessageBubble::id)
        .associateBy(MessageBubble::id)

    val targets = targetIds.mapNotNull { targetEventId ->
        val message = messageById[targetEventId] ?: return@mapNotNull null
        directChatReactionCachedTarget(message)
    }

    val cachedTargetIds = targets.map(DirectChatReactionCachedTarget::targetEventId).toSet()
    return DirectChatReactionPresentationSnapshot(
        targets = targets,
        reactions = boundedReactions.filter { reaction ->
            reaction.targetEventId in cachedTargetIds
        },
        updatedAtEpochMs = updatedAtEpochMs,
    )
}

internal fun directChatCachedReactionTargetEventId(
    displayed: MessageBubble,
    cachedTargets: List<DirectChatReactionCachedTarget>,
): String? {
    // LOW_DATA_NO_HASHING_WITHOUT_CACHED_TARGETS_V1 (OCB-464): called per
    // displayed message on every recomposition.
    if (cachedTargets.isEmpty()) return null
    val displayedMediaId = redditMatrixMediaIdFromMessageBody(displayed.body)
    val displayedBodySha256 = if (displayedMediaId == null) {
        directChatReactionCacheTextSha256(displayed.body)
    } else {
        null
    }

    val candidates = cachedTargets
        .asSequence()
        .filter { cached -> cached.outgoing == displayed.outgoing }
        .filter { cached ->
            if (displayed.id == cached.targetEventId) {
                true
            } else if (
                displayedMediaId != null &&
                cached.mediaId != null &&
                displayedMediaId == cached.mediaId
            ) {
                true
            } else {
                val displayedEpoch = displayed.createdEpochSeconds
                displayedBodySha256 != null &&
                    cached.bodySha256 == displayedBodySha256 &&
                    displayedEpoch != null &&
                    cached.createdEpochSeconds != null &&
                    abs(displayedEpoch - cached.createdEpochSeconds) <=
                        DIRECT_CHAT_REACTION_CACHE_TEXT_WINDOW_SECONDS
            }
        }
        .map(DirectChatReactionCachedTarget::targetEventId)
        .distinct()
        .toList()

    return candidates.singleOrNull()
}

internal fun directChatReactionCachedTarget(
    message: MessageBubble,
): DirectChatReactionCachedTarget? {
    if (!message.id.startsWith('$')) return null
    val mediaId = redditMatrixMediaIdFromMessageBody(message.body)
    val bodySha256 = if (mediaId == null) {
        directChatReactionCacheTextSha256(message.body)
    } else {
        null
    }
    if (mediaId == null && bodySha256 == null) return null
    return DirectChatReactionCachedTarget(
        targetEventId = message.id,
        outgoing = message.outgoing,
        createdEpochSeconds = message.createdEpochSeconds,
        mediaId = mediaId,
        bodySha256 = bodySha256,
    )
}

internal fun directChatReactionCacheTextSha256(body: String): String? {
    val normalized = body.trim()
    if (normalized.isEmpty()) return null
    return sha256Hex(normalized)
}

internal fun directChatReactionPresentationCacheStorageKey(
    accountStableId: String,
    inboxItemId: String,
    participantUsername: String,
): String? {
    val account = accountStableId.trim()
    val inbox = inboxItemId.trim()
    val participant = participantUsername.trim().lowercase(Locale.ROOT)
    if (account.isEmpty() || inbox.isEmpty() || participant.isEmpty()) return null
    val accountPrefix = directChatReactionPresentationCacheAccountPrefix(account) ?: return null
    return accountPrefix + sha256Hex("$account\u0000$inbox\u0000$participant")
}

/** ACCOUNT01_REACTION_CACHE_ACCOUNT_SCOPED_V1: every key of one account starts with this. */
internal fun directChatReactionPresentationCacheAccountPrefix(accountStableId: String): String? {
    val account = accountStableId.trim()
    if (account.isEmpty()) return null
    return DIRECT_CHAT_REACTION_PRESENTATION_CACHE_KEY_PREFIX +
        sha256Hex("account\u0000$account") + "_"
}

internal fun encodeDirectChatReactionPresentationSnapshot(
    snapshot: DirectChatReactionPresentationSnapshot,
): String = buildJsonObject {
    put("version", DIRECT_CHAT_REACTION_PRESENTATION_CACHE_VERSION)
    put("updated_at_ms", snapshot.updatedAtEpochMs)
    put(
        "targets",
        buildJsonArray {
            snapshot.targets.forEach { target ->
                add(
                    buildJsonObject {
                        put("target_event_id", target.targetEventId)
                        put("outgoing", target.outgoing)
                        target.createdEpochSeconds?.let { put("created_epoch_seconds", it) }
                        target.mediaId?.let { put("media_id", it) }
                        target.bodySha256?.let { put("body_sha256", it) }
                    },
                )
            }
        },
    )
    put(
        "reactions",
        buildJsonArray {
            snapshot.reactions.forEach { reaction ->
                add(
                    buildJsonObject {
                        put("reaction_event_id", reaction.reactionEventId)
                        put("target_event_id", reaction.targetEventId)
                        put("key", reaction.key)
                        put("self", reaction.senderIsSelf)
                    },
                )
            }
        },
    )
}.toString()

internal fun decodeDirectChatReactionPresentationSnapshot(
    raw: String,
    nowEpochMs: Long,
    json: Json,
): DirectChatReactionPresentationSnapshot? {
    // LOWCRASH01_REACTION_CACHE_SAFE_PRIMITIVES_V1 (OCB-603): an entry whose
    // field is an object or array is unreadable (null), not a throw from
    // load() and every later save's prune.
    val root = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
        ?: return null
    val version = root["version"]?.primitiveOrNull?.longOrNull ?: return null
    if (version != DIRECT_CHAT_REACTION_PRESENTATION_CACHE_VERSION.toLong()) return null

    val updatedAt = root["updated_at_ms"]?.primitiveOrNull?.longOrNull ?: return null
    if (
        updatedAt < 0L ||
        nowEpochMs < updatedAt ||
        nowEpochMs - updatedAt > DIRECT_CHAT_REACTION_PRESENTATION_CACHE_MAX_AGE_MS
    ) {
        return null
    }

    val targets = (root["targets"] as? JsonArray)
        ?.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val targetEventId = obj["target_event_id"]
                ?.primitiveOrNull
                ?.contentOrNull
                ?.takeIf { it.startsWith('$') }
                ?: return@mapNotNull null
            val outgoing = obj["outgoing"]?.primitiveOrNull?.booleanOrNull
                ?: return@mapNotNull null
            val created = obj["created_epoch_seconds"]?.primitiveOrNull?.longOrNull
            val mediaId = obj["media_id"]?.primitiveOrNull?.contentOrNull
                ?.takeIf(String::isNotBlank)
            val bodySha256 = obj["body_sha256"]?.primitiveOrNull?.contentOrNull
                ?.takeIf { it.matches(SHA256_HEX_REGEX) }
            runCatching {
                DirectChatReactionCachedTarget(
                    targetEventId = targetEventId,
                    outgoing = outgoing,
                    createdEpochSeconds = created,
                    mediaId = mediaId,
                    bodySha256 = bodySha256,
                )
            }.getOrNull()
        }
        ?.distinctBy(DirectChatReactionCachedTarget::targetEventId)
        ?.takeLast(DIRECT_CHAT_REACTION_PRESENTATION_CACHE_MAX_TARGETS)
        ?: return null

    val targetIds = targets.map(DirectChatReactionCachedTarget::targetEventId).toSet()
    val reactions = (root["reactions"] as? JsonArray)
        ?.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val reactionEventId = obj["reaction_event_id"]
                ?.primitiveOrNull
                ?.contentOrNull
                ?.takeIf { it.startsWith('$') }
                ?: return@mapNotNull null
            val targetEventId = obj["target_event_id"]
                ?.primitiveOrNull
                ?.contentOrNull
                ?.takeIf { it in targetIds }
                ?: return@mapNotNull null
            val key = obj["key"]
                ?.primitiveOrNull
                ?.contentOrNull
                ?.let(::redditDirectChatReactionAsset)
                ?.key
                ?: return@mapNotNull null
            val self = obj["self"]?.primitiveOrNull?.booleanOrNull ?: return@mapNotNull null
            runCatching {
                DirectChatReaction(
                    reactionEventId = reactionEventId,
                    targetEventId = targetEventId,
                    key = key,
                    senderIsSelf = self,
                )
            }.getOrNull()
        }
        ?.distinctBy(DirectChatReaction::reactionEventId)
        ?.takeLast(DIRECT_CHAT_REACTION_PRESENTATION_CACHE_MAX_REACTIONS)
        ?: return null

    return DirectChatReactionPresentationSnapshot(
        targets = targets,
        reactions = reactions,
        updatedAtEpochMs = updatedAt,
    )
}

private fun sha256Hex(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    val hex = CharArray(digest.size * 2)
    digest.forEachIndexed { index, byte ->
        val unsigned = byte.toInt() and 0xff
        hex[index * 2] = HEX_DIGITS[unsigned ushr 4]
        hex[index * 2 + 1] = HEX_DIGITS[unsigned and 0x0f]
    }
    return String(hex)
}

private val HEX_DIGITS = "0123456789abcdef".toCharArray()

private const val DIRECT_CHAT_REACTION_PRESENTATION_CACHE_VERSION = 1
internal const val DIRECT_CHAT_REACTION_PRESENTATION_CACHE_PRUNE_INTERVAL_MS = 60L * 60L * 1_000L
private const val DIRECT_CHAT_REACTION_PRESENTATION_CACHE_MAX_TARGETS = 12
private const val DIRECT_CHAT_REACTION_PRESENTATION_CACHE_MAX_REACTIONS = 200
private const val DIRECT_CHAT_REACTION_CACHE_TEXT_WINDOW_SECONDS = 8L
private const val DIRECT_CHAT_REACTION_PRESENTATION_CACHE_MAX_AGE_MS = 86_400_000L
private const val DIRECT_CHAT_REACTION_PRESENTATION_CACHE_KEY_PREFIX =
    "direct_chat_reaction_presentation_"
private val SHA256_HEX_REGEX = Regex("^[0-9a-f]{64}$")
private val DIRECT_CHAT_REACTION_PRESENTATION_CACHE_KEY_REGEX =
    Regex("^direct_chat_reaction_presentation_[0-9a-f]{64}_[0-9a-f]{64}$")
