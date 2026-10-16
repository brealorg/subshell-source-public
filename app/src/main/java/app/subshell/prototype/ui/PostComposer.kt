package app.subshell.prototype.ui

import androidx.compose.runtime.Immutable
import app.subshell.prototype.model.Community
import app.subshell.prototype.model.GalleryItem
import app.subshell.prototype.model.MediaFormat
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import java.net.URI
import java.util.UUID

enum class ComposerPostType(val label: String) {
    TEXT("Text"),
    LINK("Link"),
    MEDIA("Media"),
    // CROSSPOST01A1C_SOURCE_BOUND_DRAFT_TYPE_V1
    CROSSPOST("Crosspost"),
    ;

    companion object {
        // Crosspost is entered from a source post, never a free-standing type toggle.
        val standaloneTypes: List<ComposerPostType> = listOf(TEXT, LINK, MEDIA)
    }
}

enum class ComposerInputMode(val label: String) {
    PLAIN("Write"),
    MARKDOWN("Markdown"),
}

enum class ComposerMediaKind(val label: String) {
    IMAGE("Photo"),
    GIF("GIF"),
    VIDEO("Video"),
}

@Immutable
data class ComposerMediaAttachment(
    val uri: String,
    val mimeType: String,
    val kind: ComposerMediaKind,
    val sourceLinkUri: String? = null,
    val caption: String = "",
    val outboundUrl: String = "",
)


internal fun ComposerMediaAttachment.isComposerVideoSource(): Boolean =
    kind == ComposerMediaKind.VIDEO ||
        (
            kind == ComposerMediaKind.GIF &&
                mimeType.trim().startsWith("video/", ignoreCase = true)
            )

internal fun ComposerMediaAttachment.isComposerVideoGif(): Boolean =
    isComposerVideoSource() && kind == ComposerMediaKind.GIF

@Immutable
data class ComposerFlairRichText(
    val elementType: String,
    val text: String? = null,
    val emojiRef: String? = null,
    val imageUrl: String? = null,
)

@Immutable
data class ComposerFlair(
    val templateId: String,
    val text: String,
    val editable: Boolean = false,
    val textColor: String? = null,
    val backgroundColor: String? = null,
    val richText: List<ComposerFlairRichText> = emptyList(),
)

internal const val COMPOSER_FLAIR_TEXT_MAX_LENGTH = 64
internal const val COMPOSER_GALLERY_MAX_ITEMS = 20
internal const val COMPOSER_GALLERY_CAPTION_MAX_LENGTH = 180
internal const val REDDIT_COMPOSER_GIF_MIME_TYPE = "image/gif"

internal fun reconcileComposerFlairSelection(
    selected: ComposerFlair,
    available: List<ComposerFlair>,
): ComposerFlair? {
    val current = available.firstOrNull { option ->
        option.templateId == selected.templateId
    } ?: return null
    val customText = selected.text.trim().takeIf { text ->
        current.editable &&
            selected.editable &&
            text.isNotEmpty() &&
            text.length <= COMPOSER_FLAIR_TEXT_MAX_LENGTH &&
            text != current.text
    }
    return if (customText == null) {
        current
    } else {
        current.copy(text = customText, richText = emptyList())
    }
}

enum class ComposerPostRequirementsStatus {
    NOT_CHECKED,
    LOADED,
    AUTHORIZATION_REQUIRED,
    UNAVAILABLE,
}

enum class ComposerSubmitPreflightAction {
    VALIDATE,
    AUTHORIZE,
    FALLBACK_TO_REDDIT,
}

@Immutable
data class ComposerCommunityPolicy(
    val community: String,
    val allowedPostTypes: Set<ComposerPostType> = ComposerPostType.standaloneTypes.toSet(),
    val titleMinLength: Int = 1,
    val titleMaxLength: Int = 300,
    val bodyAllowed: Boolean = true,
    val bodyRequired: Boolean = false,
    val bodyMinLength: Int = 0,
    val bodyMaxLength: Int = 40_000,
    val titleRequiredStrings: List<String> = emptyList(),
    val titleBlockedStrings: List<String> = emptyList(),
    val bodyRequiredStrings: List<String> = emptyList(),
    val bodyBlockedStrings: List<String> = emptyList(),
    val domainWhitelist: List<String> = emptyList(),
    val domainBlacklist: List<String> = emptyList(),
    val allowImages: Boolean = true,
    val allowGalleries: Boolean = true,
    val allowVideo: Boolean = true,
    val allowVideoGifs: Boolean = true,
    val flairRequired: Boolean = false,
    val flairOptions: List<ComposerFlair> = emptyList(),
    val flairOptionsLoaded: Boolean = false,
    val flairAuthorizationRequired: Boolean = false,
    val postRequirementsStatus: ComposerPostRequirementsStatus =
        ComposerPostRequirementsStatus.NOT_CHECKED,
    val loadedFromReddit: Boolean = false,
    // CROSSPOST01A1C_ACCOUNT_BOUND_DESTINATION_POLICY_V1
    val crosspostDestination: ComposerCrosspostDestination? = null,
) {
    fun allows(type: ComposerPostType): Boolean = type in allowedPostTypes

    companion object {
        fun local(community: String) = ComposerCommunityPolicy(community = community)
    }
}

enum class ComposerPolicyStatus {
    IDLE,
    LOADING,
    LOADED,
    UNAVAILABLE,
}

internal const val COMPOSER_BODY_NOT_ALLOWED_MESSAGE =
    "This community doesn't allow body text for this post"

@Immutable
data class ComposerPolicySnapshot(
    val community: String? = null,
    val status: ComposerPolicyStatus = ComposerPolicyStatus.IDLE,
    val policy: ComposerCommunityPolicy? = null,
    val revision: Long = 0L,
)

internal fun composerSubmitPreflightAction(
    policy: ComposerCommunityPolicy,
): ComposerSubmitPreflightAction = when (policy.postRequirementsStatus) {
    ComposerPostRequirementsStatus.LOADED -> ComposerSubmitPreflightAction.VALIDATE
    ComposerPostRequirementsStatus.AUTHORIZATION_REQUIRED ->
        ComposerSubmitPreflightAction.AUTHORIZE
    ComposerPostRequirementsStatus.NOT_CHECKED,
    ComposerPostRequirementsStatus.UNAVAILABLE,
    -> ComposerSubmitPreflightAction.FALLBACK_TO_REDDIT
}

enum class ComposerCommunitySearchStatus {
    IDLE,
    LOADING,
    LOADED,
    UNAVAILABLE,
}

enum class ComposerValidationField {
    COMMUNITY,
    TITLE,
    FLAIR,
    BODY,
    LINK,
    MEDIA,
    GENERAL,
}

@Immutable
data class ComposerValidationIssue(
    val field: ComposerValidationField,
    val message: String,
)


@Immutable
data class ComposerCommunityOption(
    val name: String,
    val title: String = "",
    val members: Long? = null,
    val iconImageUri: String? = null,
    val joined: Boolean = false,
    val favorite: Boolean = false,
)

// COMPOSER19_PICKER_OWN_COMMUNITIES_V1: with an empty search the picker shows
// the account's own communities (favourites, then joined); every cached
// community Subshell has merely seen (Popular, links) only shows as a match
// for what is typed. Signed out, the local list is shown whole as before.
// The picker's known list: the active account's Reddit subscriptions (joined)
// and every cached community, once each; local accounts keep their own
// joined ids. Names and icons come from the identity cache.
internal fun composerKnownCommunityOptions(
    cached: List<Community>,
    subscribed: List<Community>,
    joinedIds: Collection<String>,
    isFavorite: (String) -> Boolean,
    identity: (String) -> Community,
): List<ComposerCommunityOption> {
    val subscribedNames = subscribed.map { it.name.lowercase() }.toSet()
    return (subscribed + cached)
        .distinctBy { community -> community.name.lowercase() }
        .map { community ->
            val known = identity(community.name)
            ComposerCommunityOption(
                name = known.name,
                title = known.description,
                iconImageUri = known.iconImageUri,
                joined = community.name.lowercase() in subscribedNames || community.id in joinedIds,
                favorite = isFavorite(known.name),
            )
        }
}

internal fun composerCommunityPickerOptions(
    known: List<ComposerCommunityOption>,
    remoteMatches: List<ComposerCommunityOption>,
    query: String,
    authenticated: Boolean,
): List<ComposerCommunityOption> {
    val localMatches = known
        .filter { option ->
            if (query.isBlank()) {
                !authenticated || option.favorite || option.joined
            } else {
                option.name.contains(query, ignoreCase = true) ||
                    option.title.contains(query, ignoreCase = true)
            }
        }
        .sortedWith(
            compareByDescending<ComposerCommunityOption> { it.favorite }
                .thenByDescending { it.joined }
                .thenBy { it.name.lowercase() },
        )
    return (remoteMatches + localMatches).distinctBy { option -> option.name.lowercase() }
}

@Immutable
data class ComposerCommunitySearchSnapshot(
    val query: String = "",
    val status: ComposerCommunitySearchStatus = ComposerCommunitySearchStatus.IDLE,
    val options: List<ComposerCommunityOption> = emptyList(),
)

@Immutable
data class PostComposerDraft(
    val id: String = newPostComposerDraftId(),
    val accountStableId: String? = null,
    val community: String = "subshell",
    val type: ComposerPostType = ComposerPostType.TEXT,
    val title: String = "",
    val body: String = "",
    val linkUrl: String = "",
    val inputMode: ComposerInputMode = ComposerInputMode.MARKDOWN,
    val media: List<ComposerMediaAttachment> = emptyList(),
    val flair: ComposerFlair? = null,
    val nsfw: Boolean = false,
    val spoiler: Boolean = false,
    val sendReplies: Boolean = true,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = createdAtEpochMs,
    // CROSSPOST01A1C_SOURCE_CAPSULE_V1
    val crosspostSource: ComposerCrosspostSource? = null,
) {
    val hasContent: Boolean
        get() = crosspostSource != null || title.isNotBlank() ||
            body.isNotBlank() ||
            linkUrl.isNotBlank() ||
            media.isNotEmpty() ||
            flair != null ||
            nsfw || spoiler || !sendReplies

    fun touched(nowEpochMs: Long = System.currentTimeMillis()): PostComposerDraft =
        copy(updatedAtEpochMs = nowEpochMs.coerceAtLeast(createdAtEpochMs))
}

fun newPostComposerDraftId(): String = UUID.randomUUID().toString()

enum class MarkdownAction {
    BOLD,
    ITALIC,
    STRIKETHROUGH,
    QUOTE,
    BULLET_LIST,
    NUMBERED_LIST,
    LINK,
    INLINE_CODE,
    CODE_BLOCK,
    SPOILER,
}

@Immutable
data class MarkdownEdit(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int,
)

fun applyMarkdownAction(
    text: String,
    selectionStart: Int,
    selectionEnd: Int,
    action: MarkdownAction,
): MarkdownEdit {
    val start = selectionStart.coerceIn(0, text.length)
    val end = selectionEnd.coerceIn(start, text.length)
    return when (action) {
        MarkdownAction.BOLD -> wrapMarkdown(text, start, end, "**", "**", "bold text")
        MarkdownAction.ITALIC -> wrapMarkdown(text, start, end, "_", "_", "italic text")
        MarkdownAction.STRIKETHROUGH -> wrapMarkdown(text, start, end, "~~", "~~", "struck text")
        MarkdownAction.INLINE_CODE -> wrapMarkdown(text, start, end, "`", "`", "code")
        MarkdownAction.CODE_BLOCK -> prefixMarkdownLines(
            text,
            start,
            end,
            prefix = "    ",
            placeholder = "code",
        )
        MarkdownAction.QUOTE -> prefixMarkdownLines(text, start, end, prefix = "> ", placeholder = "quote")
        MarkdownAction.BULLET_LIST -> prefixMarkdownLines(text, start, end, prefix = "- ", placeholder = "list item")
        MarkdownAction.NUMBERED_LIST -> prefixNumberedMarkdownLines(text, start, end)
        MarkdownAction.LINK -> linkMarkdown(text, start, end)
        MarkdownAction.SPOILER -> wrapMarkdown(text, start, end, ">!", "!<", "spoiler")
    }
}

fun validatePostComposerDraftIssue(
    draft: PostComposerDraft,
    communityNames: Collection<String>,
    policy: ComposerCommunityPolicy = ComposerCommunityPolicy.local(draft.community),
): ComposerValidationIssue? {
    if (communityNames.none { it.equals(draft.community, ignoreCase = true) }) {
        return ComposerValidationIssue(ComposerValidationField.COMMUNITY, "Choose a community")
    }
    return validatePostComposerContentIssue(draft, policy, remote = false)
}

fun validatePostComposerDraft(
    draft: PostComposerDraft,
    communityNames: Collection<String>,
    policy: ComposerCommunityPolicy = ComposerCommunityPolicy.local(draft.community),
): String? = validatePostComposerDraftIssue(draft, communityNames, policy)?.message

fun validateRemoteTextPostComposerDraftIssue(
    draft: PostComposerDraft,
    policy: ComposerCommunityPolicy = ComposerCommunityPolicy.local(draft.community),
): ComposerValidationIssue? {
    val community = draft.community.trim().removePrefix("/r/").removePrefix("r/")
    if (
        community.length !in 1..100 ||
        community.any { !it.isLetterOrDigit() && it != '_' && it != '-' }
    ) {
        return ComposerValidationIssue(ComposerValidationField.COMMUNITY, "Choose a community")
    }
    return validatePostComposerContentIssue(draft, policy, remote = true)
}

fun validateRemoteTextPostComposerDraft(
    draft: PostComposerDraft,
    policy: ComposerCommunityPolicy = ComposerCommunityPolicy.local(draft.community),
): String? = validateRemoteTextPostComposerDraftIssue(draft, policy)?.message

private fun validatePostComposerContentIssue(
    draft: PostComposerDraft,
    policy: ComposerCommunityPolicy,
    remote: Boolean,
): ComposerValidationIssue? {
    fun issue(field: ComposerValidationField, message: String) =
        ComposerValidationIssue(field = field, message = message)

    // CROSSPOST01A1C_NO_GENERIC_PUBLISH_OR_LOCAL_DOWNGRADE_V1
    // A1D will call the source-bound crosspost gate from its explicit review flow.
    if (draft.type == ComposerPostType.CROSSPOST || draft.crosspostSource != null) {
        return issue(ComposerValidationField.GENERAL, "Open this crosspost through its source review flow")
    }
    val title = draft.title.trim()
    if (title.isEmpty()) return issue(ComposerValidationField.TITLE, "Add a title")
    if ('\u0000' in title || '\n' in title || '\r' in title) {
        return issue(ComposerValidationField.TITLE, "Keep the title on one line")
    }
    if (title.length < policy.titleMinLength) {
        return issue(
            ComposerValidationField.TITLE,
            "Use at least ${policy.titleMinLength} characters in the title",
        )
    }
    if (title.length > policy.titleMaxLength) {
        return issue(
            ComposerValidationField.TITLE,
            "Keep the title under ${policy.titleMaxLength} characters",
        )
    }
    if (policy.titleRequiredStrings.any { it.isNotBlank() && !title.contains(it, true) }) {
        return issue(
            ComposerValidationField.TITLE,
            "The title is missing text required by this community",
        )
    }
    if (policy.titleBlockedStrings.any { it.isNotBlank() && title.contains(it, true) }) {
        return issue(
            ComposerValidationField.TITLE,
            "The title contains text blocked by this community",
        )
    }
    if (!policy.allows(draft.type)) {
        return issue(
            ComposerValidationField.GENERAL,
            "This community doesn't allow ${draft.type.label.lowercase()} posts",
        )
    }
    if (policy.flairRequired && draft.flair == null) {
        return issue(ComposerValidationField.FLAIR, "Post flair is required")
    }
    val normalizedBody = draft.body.replace("\r\n", "\n").replace('\r', '\n').trim()
    if (!policy.bodyAllowed && normalizedBody.isNotEmpty()) {
        return issue(ComposerValidationField.BODY, COMPOSER_BODY_NOT_ALLOWED_MESSAGE)
    }
    if (policy.bodyRequired && normalizedBody.isEmpty()) {
        return issue(ComposerValidationField.BODY, "Add post body text")
    }
    if ('\u0000' in draft.body) {
        return issue(
            ComposerValidationField.BODY,
            "Remove the unsupported character from the post body",
        )
    }
    if (normalizedBody.isNotEmpty() && normalizedBody.length < policy.bodyMinLength) {
        return issue(
            ComposerValidationField.BODY,
            "Use at least ${policy.bodyMinLength} characters in the post body",
        )
    }
    if (normalizedBody.length > policy.bodyMaxLength) {
        return issue(
            ComposerValidationField.BODY,
            "Keep the post body under ${policy.bodyMaxLength} characters",
        )
    }
    if (policy.bodyRequiredStrings.any {
            it.isNotBlank() && !normalizedBody.contains(it, true)
        }
    ) {
        return issue(
            ComposerValidationField.BODY,
            "The post body is missing text required by this community",
        )
    }
    if (policy.bodyBlockedStrings.any { it.isNotBlank() && normalizedBody.contains(it, true) }) {
        return issue(
            ComposerValidationField.BODY,
            "The post body contains text blocked by this community",
        )
    }

    return when (draft.type) {
        ComposerPostType.CROSSPOST -> issue(
            ComposerValidationField.GENERAL,
            "Crossposts require source review",
        )
        ComposerPostType.TEXT -> if (draft.media.isNotEmpty()) {
            issue(ComposerValidationField.MEDIA, "Remove media or switch the post type to Media")
        } else {
            null
        }
        ComposerPostType.LINK -> {
            val uri = draft.linkUrl.trim().asWebUri()
            val host = uri?.webHost()?.lowercase()?.removePrefix("www.")
            when {
                host.isNullOrBlank() -> issue(
                    ComposerValidationField.LINK,
                    "Add a complete web link including its scheme",
                )
                policy.domainWhitelist.isNotEmpty() &&
                    policy.domainWhitelist.none { host.matchesDomainRule(it) } -> issue(
                    ComposerValidationField.LINK,
                    "This community doesn't allow links from that domain",
                )
                policy.domainBlacklist.any { host.matchesDomainRule(it) } -> issue(
                    ComposerValidationField.LINK,
                    "This community blocks links from that domain",
                )
                else -> null
            }
        }
        ComposerPostType.MEDIA -> when {
            draft.media.isEmpty() -> issue(ComposerValidationField.MEDIA, "Add a photo or video")
            draft.media.size == 1 &&
                !draft.media.single().isComposerVideoSource() &&
                !policy.allowImages -> issue(
                ComposerValidationField.MEDIA,
                "This community doesn't allow image posts",
            )
            draft.media.size == 1 &&
                draft.media.single().isComposerVideoGif() &&
                !policy.allowVideoGifs -> issue(
                ComposerValidationField.MEDIA,
                "This community doesn't allow GIF-style video posts",
            )
            draft.media.size == 1 &&
                draft.media.single().isComposerVideoSource() &&
                !draft.media.single().isComposerVideoGif() &&
                !policy.allowVideo -> issue(
                ComposerValidationField.MEDIA,
                "This community doesn't allow video posts",
            )
            draft.media.size > COMPOSER_GALLERY_MAX_ITEMS -> issue(
                ComposerValidationField.MEDIA,
                "A gallery can contain up to $COMPOSER_GALLERY_MAX_ITEMS items",
            )
            draft.media.size > 1 && !policy.allowGalleries -> issue(
                ComposerValidationField.MEDIA,
                "This community doesn't allow gallery posts",
            )
            draft.media.size > 1 && draft.media.any { it.isComposerVideoSource() } ->
                issue(ComposerValidationField.MEDIA, "A gallery can contain photos and GIFs, not video")
            draft.media.size > 1 && draft.media.any {
                it.kind == ComposerMediaKind.IMAGE && !it.mimeType.isSupportedRedditStaticImageMimeType() ||
                    it.kind == ComposerMediaKind.GIF && !it.mimeType.equals(
                        REDDIT_COMPOSER_GIF_MIME_TYPE,
                        ignoreCase = true,
                    )
            } -> issue(
                ComposerValidationField.MEDIA,
                "Choose JPEG, PNG, WebP, or GIF files for this gallery",
            )
            draft.media.size > 1 && draft.media.any { it.caption.length > COMPOSER_GALLERY_CAPTION_MAX_LENGTH } ->
                issue(
                    ComposerValidationField.MEDIA,
                    "Keep each gallery caption under $COMPOSER_GALLERY_CAPTION_MAX_LENGTH characters",
                )
            draft.media.size > 1 && draft.media.any { item ->
                item.outboundUrl.isNotBlank() && item.outboundUrl.asWebUri() == null
            } -> issue(
                ComposerValidationField.MEDIA,
                "Use a complete web link for each gallery item",
            )
            remote && draft.media.size == 1 &&
                draft.media.single().isComposerVideoSource() &&
                draft.media.single().mimeType.trim().lowercase() !in setOf(
                    "video/mp4",
                    "video/quicktime",
                    "video/webm",
                ) -> issue(
                ComposerValidationField.MEDIA,
                "Choose an MP4, MOV, or WebM video",
            )
            remote && draft.media.size == 1 &&
                draft.media.single().kind == ComposerMediaKind.GIF &&
                !draft.media.single().isComposerVideoSource() &&
                !draft.media.single().mimeType.equals(
                    REDDIT_COMPOSER_GIF_MIME_TYPE,
                    ignoreCase = true,
                ) -> issue(
                ComposerValidationField.MEDIA,
                "Choose a GIF image",
            )
            remote && draft.media.size == 1 &&
                draft.media.single().kind == ComposerMediaKind.IMAGE &&
                !draft.media.single().mimeType.isSupportedRedditStaticImageMimeType() -> issue(
                ComposerValidationField.MEDIA,
                "Choose a JPEG, PNG, or WebP image",
            )
            else -> null
        }
    }
}

fun buildLocalPost(
    draft: PostComposerDraft,
    author: String,
    id: String,
): Post {
    require(draft.type != ComposerPostType.CROSSPOST && draft.crosspostSource == null) {
        "A source-bound crosspost cannot become a local post"
    }
    val linkUri = draft.linkUrl.trim().asWebUri()
    val firstMedia = draft.media.firstOrNull()
    val kind = when (draft.type) {
        ComposerPostType.CROSSPOST -> error("Crossposts require an authenticated create result")
        ComposerPostType.TEXT -> PostKind.TEXT
        ComposerPostType.LINK -> PostKind.LINK
        ComposerPostType.MEDIA -> when {
            draft.media.size > 1 -> PostKind.GALLERY
            firstMedia?.isComposerVideoSource() == true -> PostKind.VIDEO
            firstMedia?.kind == ComposerMediaKind.GIF -> PostKind.GIF
            else -> PostKind.IMAGE
        }
    }
    return Post(
        id = id,
        community = draft.community,
        author = author,
        age = "0m",
        title = draft.title.trim(),
        body = draft.body.trim(),
        score = 1,
        comments = 0,
        kind = kind,
        mediaLabel = when {
            draft.type == ComposerPostType.LINK -> draft.linkUrl.trim()
            draft.media.size > 1 -> "${draft.media.size} items"
            firstMedia != null -> firstMedia.kind.label
            else -> ""
        },
        localMediaUri = firstMedia?.uri?.takeIf { draft.media.size == 1 },
        localMediaMimeType = firstMedia?.mimeType?.takeIf { draft.media.size == 1 },
        gallery = if (draft.media.size > 1) {
            draft.media.mapIndexed { index, media ->
                GalleryItem(
                    id = "$id-media-$index",
                    label = media.caption.ifBlank { media.kind.label },
                    format = when (media.kind) {
                        ComposerMediaKind.IMAGE -> MediaFormat.IMAGE
                        ComposerMediaKind.GIF -> MediaFormat.GIF
                        ComposerMediaKind.VIDEO -> MediaFormat.VIDEO
                    },
                    localMediaUri = media.uri,
                    localMediaMimeType = media.mimeType,
                )
            }
        } else {
            emptyList()
        },
        domain = linkUri?.webHost()?.removePrefix("www."),
        flair = draft.flair?.text,
        spoiler = draft.spoiler,
        nsfw = draft.nsfw,
    )
}

private fun wrapMarkdown(
    text: String,
    start: Int,
    end: Int,
    prefix: String,
    suffix: String,
    placeholder: String,
): MarkdownEdit {
    val selected = text.substring(start, end)
    val content = selected.ifEmpty { placeholder }
    val replacement = prefix + content + suffix
    val updated = text.replaceRange(start, end, replacement)
    val contentStart = start + prefix.length
    val contentEnd = contentStart + content.length
    val cursor = start + replacement.length
    return if (selected.isEmpty()) MarkdownEdit(updated, contentStart, contentEnd)
    else MarkdownEdit(updated, cursor, cursor)
}

private fun prefixMarkdownLines(
    text: String,
    start: Int,
    end: Int,
    prefix: String,
    placeholder: String,
): MarkdownEdit {
    val selected = text.substring(start, end).ifEmpty { placeholder }
    val replacement = selected.lineSequence().joinToString("\n") { line -> "$prefix$line" }
    val updated = text.replaceRange(start, end, replacement)
    val cursor = start + replacement.length
    return if (start == end) MarkdownEdit(updated, start + prefix.length, cursor)
    else MarkdownEdit(updated, cursor, cursor)
}

private fun prefixNumberedMarkdownLines(text: String, start: Int, end: Int): MarkdownEdit {
    val selected = text.substring(start, end).ifEmpty { "list item" }
    val replacement = selected.lineSequence()
        .mapIndexed { index, line -> "${index + 1}. $line" }
        .joinToString("\n")
    val updated = text.replaceRange(start, end, replacement)
    val cursor = start + replacement.length
    return if (start == end) MarkdownEdit(updated, start + 3, cursor)
    else MarkdownEdit(updated, cursor, cursor)
}

private fun linkMarkdown(text: String, start: Int, end: Int): MarkdownEdit {
    val selected = text.substring(start, end).ifEmpty { "link text" }
    val prefix = "[$selected]("
    val url = "https:" + "//"
    val replacement = "$prefix$url)"
    val updated = text.replaceRange(start, end, replacement)
    val urlStart = start + prefix.length
    return MarkdownEdit(updated, urlStart, urlStart + url.length)
}

// LOWUI_WEB_LINKS_ACCEPT_ANY_SCHEME_CASE_AND_UNDERSCORE_HOSTS_V1 (OCB-087):
// "Https://…" (keyboard auto-capitalisation) and hosts with '_' are web links;
// java.net.URI keeps the scheme as typed and has no host for '_'. The link
// itself is not rewritten. A link needs a host ("https:example.com" has none).
private fun String.asWebUri(): URI? = runCatching {
    URI(this).takeIf {
        (it.scheme.equals("http", ignoreCase = true) || it.scheme.equals("https", ignoreCase = true)) &&
            it.webHost() != null
    }
}.getOrNull()

internal fun URI.webHost(): String? = (
    host ?: rawAuthority
        ?.substringAfterLast('@')
        ?.substringBefore(':')
    )?.takeIf { candidate -> candidate.isNotBlank() && '/' !in candidate }


private fun String.isSupportedRedditStaticImageMimeType(): Boolean = when (trim().lowercase()) {
    "image/jpeg",
    "image/jpg",
    "image/png",
    "image/webp",
    "image/*",
    -> true
    else -> false
}

private fun String.matchesDomainRule(rule: String): Boolean {
    val normalized = rule.trim().lowercase().removePrefix("www.").removePrefix("*.")
    return normalized.isNotEmpty() && (this == normalized || endsWith(".$normalized"))
}
