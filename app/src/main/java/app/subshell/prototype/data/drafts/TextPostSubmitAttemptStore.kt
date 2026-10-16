package app.subshell.prototype.data.drafts

import android.content.SharedPreferences
import app.subshell.prototype.data.account.TextPostWriteGalleryItem
import app.subshell.prototype.data.account.TextPostWriteRequest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

data class PendingTextPostSubmitAttempt(
    val request: TextPostWriteRequest,
    val dispatchedAtEpochMs: Long?,
)

// COMPOSER02_SUBMIT_ATTEMPT_PER_ACCOUNT_V1: one unresolved attempt per account.
// An attempt of one account never blocks another account's posts. The value
// under KEY is a JSON array; a pre-COMPOSER02 value is a single object and is
// read as that account's attempt.
class TextPostSubmitAttemptStore(
    private val preferences: SharedPreferences,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(accountStableId: String): PendingTextPostSubmitAttempt? = synchronized(LOCK) {
        loadAll().firstOrNull { attempt -> attempt.request.accountStableId == accountStableId }
    }

    fun stage(request: TextPostWriteRequest) = synchronized(LOCK) {
        persist(
            loadAll().filterNot { attempt ->
                attempt.request.accountStableId == request.accountStableId
            } + PendingTextPostSubmitAttempt(
                request = request,
                dispatchedAtEpochMs = null,
            ),
        )
    }

    // The submitter sends one post at a time, so at most one staged attempt
    // is waiting for its dispatch mark.
    // LOW_DRAFTS_DISPATCH_MARK_PER_ACCOUNT_V1 (OCB-530): the request key holds
    // only the content, so the same post staged by another account (left
    // undispatched) was marked too; the mark matches the account as well.
    fun markDispatched(accountStableId: String, requestKey: String) = synchronized(LOCK) {
        val attempts = loadAll()
        fun PendingTextPostSubmitAttempt.waiting() =
            request.accountStableId == accountStableId &&
                request.requestKey == requestKey &&
                dispatchedAtEpochMs == null
        if (attempts.none { attempt -> attempt.waiting() }) {
            return@synchronized
        }
        val dispatchedAtEpochMs = nowEpochMs()
        persist(
            attempts.map { attempt ->
                if (attempt.waiting()) {
                    attempt.copy(dispatchedAtEpochMs = dispatchedAtEpochMs)
                } else {
                    attempt
                }
            },
        )
    }

    fun clear(accountStableId: String, requestKey: String? = null) = synchronized(LOCK) {
        val attempts = loadAll()
        val remaining = attempts.filterNot { attempt ->
            attempt.request.accountStableId == accountStableId &&
                (requestKey == null || attempt.request.requestKey == requestKey)
        }
        if (remaining.size != attempts.size) persist(remaining)
    }

    private fun loadAll(): List<PendingTextPostSubmitAttempt> = readStored().attempts

    // DATALOSS01_ATTEMPT_ENTRIES_READ_ONE_BY_ONE_V1 (OCB-625): one entry that
    // no longer passes TextPostWriteRequest's checks used to read the whole
    // list as empty, and the next write dropped every account's unconfirmed
    // post. Entries are read one by one; an unreadable one is written back
    // as it was, and an unreadable value is copied aside before it is replaced.
    private fun readStored(): StoredAttempts {
        val encoded = preferences.getString(KEY, null) ?: return StoredAttempts()
        val elements = when (val root = runCatching { json.parseToJsonElement(encoded) }.getOrNull()) {
            is JsonArray -> root.toList()
            is JsonObject -> listOf(root)
            else -> return StoredAttempts(unparseable = encoded)
        }
        val attempts = mutableListOf<PendingTextPostSubmitAttempt>()
        val unreadable = mutableListOf<JsonElement>()
        elements.forEach { element ->
            runCatching {
                json.decodeFromJsonElement(StoredTextPostSubmitAttempt.serializer(), element).toAttempt()
            }.onSuccess(attempts::add).onFailure { unreadable += element }
        }
        return StoredAttempts(attempts, unreadable)
    }

    private fun persist(attempts: List<PendingTextPostSubmitAttempt>) {
        val stored = readStored()
        val editor = preferences.edit()
        if (stored.unparseable != null && !preferences.contains(UNREADABLE_KEY)) {
            editor.putString(UNREADABLE_KEY, stored.unparseable)
        }
        val rows = attempts.map { attempt ->
            json.encodeToJsonElement(StoredTextPostSubmitAttempt.serializer(), StoredTextPostSubmitAttempt.from(attempt))
        } + stored.unreadable
        if (rows.isEmpty()) {
            check(editor.remove(KEY).commit()) {
                "Could not clear pending text-post submit attempt"
            }
            return
        }
        check(editor.putString(KEY, JsonArray(rows).toString()).commit()) {
            "Could not persist pending text-post submit attempt"
        }
    }

    private class StoredAttempts(
        val attempts: List<PendingTextPostSubmitAttempt> = emptyList(),
        val unreadable: List<JsonElement> = emptyList(),
        val unparseable: String? = null,
    )

    private companion object {
        const val KEY = "composer09_text_post_submit_attempt_v1"
        const val UNREADABLE_KEY = "composer09_text_post_submit_attempt_v1.unreadable"

        // Two store instances (runtime and view model) share one preferences file.
        val LOCK = Any()
    }
}

@Serializable
private data class StoredTextPostSubmitAttempt(
    val accountStableId: String,
    val community: String,
    val title: String,
    val text: String,
    val sendReplies: Boolean,
    val nsfw: Boolean,
    val spoiler: Boolean,
    val linkUrl: String?,
    val linkPreviewImageUrl: String?,
    val mediaUri: String?,
    val mediaMimeType: String?,
    val galleryItems: List<StoredTextPostSubmitGalleryItem>,
    val flairId: String?,
    val flairText: String?,
    val mediaAsGif: Boolean,
    val dispatchedAtEpochMs: Long?,
    // CROSSPOST01A1A_PENDING_SOURCE_PERSISTENCE_V1
    val crosspostFullname: String? = null,
) {
    fun toAttempt(): PendingTextPostSubmitAttempt = PendingTextPostSubmitAttempt(
        request = TextPostWriteRequest(
            accountStableId = accountStableId,
            community = community,
            title = title,
            text = text,
            sendReplies = sendReplies,
            nsfw = nsfw,
            spoiler = spoiler,
            linkUrl = linkUrl,
            linkPreviewImageUrl = linkPreviewImageUrl,
            mediaUri = mediaUri,
            mediaMimeType = mediaMimeType,
            galleryItems = galleryItems.map { item -> item.toItem() },
            flairId = flairId,
            flairText = flairText,
            mediaAsGif = mediaAsGif,
            crosspostFullname = crosspostFullname,
        ),
        dispatchedAtEpochMs = dispatchedAtEpochMs,
    )

    companion object {
        fun from(attempt: PendingTextPostSubmitAttempt): StoredTextPostSubmitAttempt =
            StoredTextPostSubmitAttempt(
                accountStableId = attempt.request.accountStableId,
                community = attempt.request.community,
                title = attempt.request.title,
                text = attempt.request.text,
                sendReplies = attempt.request.sendReplies,
                nsfw = attempt.request.nsfw,
                spoiler = attempt.request.spoiler,
                linkUrl = attempt.request.linkUrl,
                linkPreviewImageUrl = attempt.request.linkPreviewImageUrl,
                mediaUri = attempt.request.mediaUri,
                mediaMimeType = attempt.request.mediaMimeType,
                galleryItems = attempt.request.galleryItems.map { item ->
                    StoredTextPostSubmitGalleryItem.from(item)
                },
                flairId = attempt.request.flairId,
                flairText = attempt.request.flairText,
                mediaAsGif = attempt.request.mediaAsGif,
                dispatchedAtEpochMs = attempt.dispatchedAtEpochMs,
                crosspostFullname = attempt.request.crosspostFullname,
            )
    }
}

@Serializable
private data class StoredTextPostSubmitGalleryItem(
    val mediaUri: String,
    val mediaMimeType: String,
    val caption: String,
    val outboundUrl: String,
) {
    fun toItem(): TextPostWriteGalleryItem = TextPostWriteGalleryItem(
        mediaUri = mediaUri,
        mediaMimeType = mediaMimeType,
        caption = caption,
        outboundUrl = outboundUrl,
    )

    companion object {
        fun from(item: TextPostWriteGalleryItem): StoredTextPostSubmitGalleryItem =
            StoredTextPostSubmitGalleryItem(
                mediaUri = item.mediaUri,
                mediaMimeType = item.mediaMimeType,
                caption = item.caption,
                outboundUrl = item.outboundUrl,
            )
    }
}
