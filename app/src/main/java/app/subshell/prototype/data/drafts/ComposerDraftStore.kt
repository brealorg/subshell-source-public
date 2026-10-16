package app.subshell.prototype.data.drafts

import android.content.SharedPreferences
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.ComposerCrosspostSource
import app.subshell.prototype.ui.ComposerFlair
import app.subshell.prototype.ui.ComposerFlairRichText
import app.subshell.prototype.ui.ComposerInputMode
import app.subshell.prototype.ui.ComposerMediaAttachment
import app.subshell.prototype.ui.ComposerMediaKind
import app.subshell.prototype.ui.ComposerPostType
import app.subshell.prototype.ui.PostComposerDraft
import app.subshell.prototype.ui.isValidSourceBoundCrosspostDraft
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement

class ComposerDraftStore(
    private val preferences: SharedPreferences,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // CROSSPOST01A1C_NATIVE_DRAFT_DOWNGRADE_ISOLATION_V1
    // Old app versions only know KEY. Never put a CROSSPOST there: they map unknown types to TEXT.
    fun load(): List<PostComposerDraft> = (
        loadRows(CROSSPOST_KEY, crossposts = true) + loadRows(KEY, crossposts = false)
        )
        .filter(PostComposerDraft::hasContent)
        .distinctBy(PostComposerDraft::id)
        .sortedByDescending(PostComposerDraft::updatedAtEpochMs)
        .take(MAX_DRAFTS)

    private fun loadRows(key: String, crossposts: Boolean): List<PostComposerDraft> {
        val rows = runCatching {
            val encoded = preferences.getString(key, null) ?: return emptyList()
            json.parseToJsonElement(encoded) as? JsonArray
        }.getOrNull() ?: return emptyList()
        return rows.take(MAX_DRAFTS).mapNotNull { row ->
            runCatching {
                json.decodeFromJsonElement<StoredComposerDraft>(row).toDraft()
            }.getOrNull()?.takeIf { draft ->
                if (crossposts) draft.isValidSourceBoundCrosspostDraft()
                else draft.type != ComposerPostType.CROSSPOST && draft.crosspostSource == null
            }
        }
    }

    fun save(drafts: List<PostComposerDraft>) {
        val editor = preferences.edit()
        stageRestore(editor, drafts)
        editor.apply()
    }

    // BACKUP04A3_NATIVE_DRAFT_JSON_SHARED_EDITOR_STAGE_V1
    internal fun stageRestore(
        editor: SharedPreferences.Editor,
        drafts: List<PostComposerDraft>,
    ) {
        // Validate before staging either key, so malformed sources cannot become ordinary drafts.
        require(drafts.all { draft ->
            if (draft.type == ComposerPostType.CROSSPOST || draft.crosspostSource != null) {
                draft.isValidSourceBoundCrosspostDraft()
            } else true
        }) { "Invalid source-bound crosspost draft" }
        val normalized = drafts
            .filter(PostComposerDraft::hasContent)
            .distinctBy(PostComposerDraft::id)
            .sortedByDescending(PostComposerDraft::updatedAtEpochMs)
            .take(MAX_DRAFTS)
        val ordinary = encode(normalized.filter { it.type != ComposerPostType.CROSSPOST })
        val crossposts = encode(normalized.filter { it.type == ComposerPostType.CROSSPOST })
        editor.putString(KEY, ordinary)
        editor.putString(CROSSPOST_KEY, crossposts)
    }

    private fun encode(drafts: List<PostComposerDraft>): String =
        json.encodeToString(drafts.map(StoredComposerDraft::fromDraft))

    private companion object {
        const val KEY = "post_composer_drafts.v1"
        const val CROSSPOST_KEY = "post_composer_crosspost_drafts.v1"
        const val MAX_DRAFTS = 100
    }
}

@Serializable
private data class StoredComposerDraft(
    val id: String,
    val accountStableId: String? = null,
    val community: String,
    val type: String,
    val title: String,
    val body: String,
    val linkUrl: String,
    val inputMode: String,
    val media: List<StoredComposerMedia>,
    val flair: StoredComposerFlair? = null,
    val nsfw: Boolean,
    val spoiler: Boolean,
    val sendReplies: Boolean,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val crosspostSource: StoredCrosspostSource? = null,
) {
    fun toDraft(): PostComposerDraft = PostComposerDraft(
        id = id,
        accountStableId = accountStableId,
        community = community,
        type = ComposerPostType.entries.firstOrNull { it.name == type } ?: ComposerPostType.TEXT,
        title = title,
        body = body,
        linkUrl = linkUrl,
        inputMode = ComposerInputMode.entries.firstOrNull { it.name == inputMode }
            ?: ComposerInputMode.MARKDOWN,
        media = media.map(StoredComposerMedia::toMedia),
        flair = flair?.toFlair(),
        nsfw = nsfw,
        spoiler = spoiler,
        sendReplies = sendReplies,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        crosspostSource = crosspostSource?.toSource(),
    )

    companion object {
        fun fromDraft(draft: PostComposerDraft) = StoredComposerDraft(
            id = draft.id,
            accountStableId = draft.accountStableId,
            community = draft.community,
            type = draft.type.name,
            title = draft.title,
            body = draft.body,
            linkUrl = draft.linkUrl,
            inputMode = draft.inputMode.name,
            media = draft.media.map(StoredComposerMedia::fromMedia),
            flair = draft.flair?.let(StoredComposerFlair::fromFlair),
            nsfw = draft.nsfw,
            spoiler = draft.spoiler,
            sendReplies = draft.sendReplies,
            createdAtEpochMs = draft.createdAtEpochMs,
            updatedAtEpochMs = draft.updatedAtEpochMs,
            crosspostSource = draft.crosspostSource?.let(StoredCrosspostSource::fromSource),
        )
    }
}

@Serializable
private data class StoredComposerMedia(
    val uri: String,
    val mimeType: String,
    val kind: String,
    val sourceLinkUri: String? = null,
    val caption: String = "",
    val outboundUrl: String = "",
) {
    fun toMedia() = ComposerMediaAttachment(
        uri = uri,
        mimeType = mimeType,
        kind = ComposerMediaKind.entries.firstOrNull { it.name == kind }
            ?: ComposerMediaKind.IMAGE,
        sourceLinkUri = sourceLinkUri,
        caption = caption,
        outboundUrl = outboundUrl,
    )

    companion object {
        fun fromMedia(media: ComposerMediaAttachment) = StoredComposerMedia(
            uri = media.uri,
            mimeType = media.mimeType,
            kind = media.kind.name,
            sourceLinkUri = media.sourceLinkUri,
            caption = media.caption,
            outboundUrl = media.outboundUrl,
        )
    }
}

@Serializable
private data class StoredComposerFlair(
    val templateId: String,
    val text: String,
    val editable: Boolean,
    val textColor: String? = null,
    val backgroundColor: String? = null,
    val richText: List<StoredComposerFlairRichText> = emptyList(),
) {
    fun toFlair() = ComposerFlair(
        templateId = templateId,
        text = text,
        editable = editable,
        textColor = textColor,
        backgroundColor = backgroundColor,
        richText = richText.map(StoredComposerFlairRichText::toRichText),
    )

    companion object {
        fun fromFlair(flair: ComposerFlair) = StoredComposerFlair(
            templateId = flair.templateId,
            text = flair.text,
            editable = flair.editable,
            textColor = flair.textColor,
            backgroundColor = flair.backgroundColor,
            richText = flair.richText.map(StoredComposerFlairRichText::fromRichText),
        )
    }
}

@Serializable
private data class StoredComposerFlairRichText(
    val elementType: String,
    val text: String? = null,
    val emojiRef: String? = null,
    val imageUrl: String? = null,
) {
    fun toRichText() = ComposerFlairRichText(
        elementType = elementType,
        text = text,
        emojiRef = emojiRef,
        imageUrl = imageUrl,
    )

    companion object {
        fun fromRichText(value: ComposerFlairRichText) = StoredComposerFlairRichText(
            elementType = value.elementType,
            text = value.text,
            emojiRef = value.emojiRef,
            imageUrl = value.imageUrl,
        )
    }
}

// CROSSPOST01A1C_SOURCE_CAPSULE_JSON_V1
@Serializable
private data class StoredCrosspostSource(
    val fullname: String,
    val community: String,
    val author: String,
    val title: String,
    val kind: String,
    val nsfw: Boolean = false,
    val spoiler: Boolean = false,
) {
    fun toSource(): ComposerCrosspostSource = ComposerCrosspostSource(
        fullname = fullname,
        community = community,
        author = author,
        title = title,
        kind = PostKind.valueOf(kind),
        nsfw = nsfw,
        spoiler = spoiler,
    )

    companion object {
        fun fromSource(source: ComposerCrosspostSource) = StoredCrosspostSource(
            fullname = source.fullname,
            community = source.community,
            author = source.author,
            title = source.title,
            kind = source.kind.name,
            nsfw = source.nsfw,
            spoiler = source.spoiler,
        )
    }
}
