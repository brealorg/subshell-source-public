package app.subshell.prototype.ui

import androidx.compose.runtime.Immutable
import app.subshell.prototype.model.Comment
import app.subshell.prototype.model.Post

@Immutable
data class CommentReplyTarget(
    val postId: String,
    val parentCommentId: String? = null,
    val parentFullname: String? = null,
    val parentAuthor: String? = null,
) {
    val isPostTarget: Boolean
        get() = parentCommentId == null

    val stableKey: String
        get() = buildString {
            append(postId)
            append('|')
            append(parentCommentId ?: "post")
        }
}

@Immutable
data class CommentComposerDraft(
    val target: CommentReplyTarget,
    val body: String = "",
    val inputMode: ComposerInputMode = ComposerInputMode.PLAIN,
    val media: ComposerMediaAttachment? = null,
    // UIPOST02_COMMENT_DRAFT_KEYED_ON_ACCOUNT_V1: the account the draft was
    // written with (null for a draft restored from before this field). A
    // draft is never sent from another account.
    val accountStableId: String? = null,
) {
    val hasContent: Boolean
        get() = body.isNotBlank() || media != null
}

/** True when [draft] was written with an account other than the active one. */
internal fun commentDraftBelongsToAnotherAccount(
    draft: CommentComposerDraft,
    activeAccountId: String?,
): Boolean = draft.accountStableId != null && draft.accountStableId != activeAccountId

fun commentReplyTarget(
    post: Post,
    comment: Comment? = null,
): CommentReplyTarget = restoredCommentReplyTarget(
    postId = post.id,
    parentCommentId = comment?.id,
    parentFullname = canonicalCommentParentFullname(
        thingId = comment?.id ?: post.id,
        expectedPrefix = if (comment == null) "t3_" else "t1_",
    ),
    parentAuthor = comment?.author,
) ?: error("A comment composer target requires a stable post ID")

internal fun restoredCommentReplyTarget(
    postId: String?,
    parentCommentId: String?,
    parentFullname: String?,
    parentAuthor: String?,
): CommentReplyTarget? {
    val normalizedPostId = postId
        ?.trim()
        ?.takeIf { value -> value.isNotEmpty() && value.length <= COMMENT_TARGET_ID_MAX_CHARS }
        ?: return null
    val normalizedCommentId = parentCommentId
        ?.trim()
        ?.takeIf { value -> value.isNotEmpty() && value.length <= COMMENT_TARGET_ID_MAX_CHARS }
    val expectedPrefix = if (normalizedCommentId == null) "t3_" else "t1_"
    val normalizedFullname = parentFullname
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.let { candidate ->
            runCatching { normalizeCommentReplyParentFullname(candidate) }
                .getOrNull()
                ?.takeIf { fullname -> fullname.startsWith(expectedPrefix) }
        }
    val normalizedAuthor = parentAuthor
        ?.trim()
        ?.takeIf { value -> value.isNotEmpty() && value.length <= COMMENT_TARGET_AUTHOR_MAX_CHARS }

    return CommentReplyTarget(
        postId = normalizedPostId,
        parentCommentId = normalizedCommentId,
        parentFullname = normalizedFullname,
        parentAuthor = normalizedAuthor,
    )
}

private fun canonicalCommentParentFullname(
    thingId: String,
    expectedPrefix: String,
): String? {
    val normalizedId = thingId.trim().lowercase()
    if (normalizedId.isEmpty()) return null
    val candidate = when {
        normalizedId.startsWith(expectedPrefix) -> normalizedId
        normalizedId.startsWith("t1_") || normalizedId.startsWith("t3_") -> return null
        else -> "$expectedPrefix$normalizedId"
    }
    return runCatching { normalizeCommentReplyParentFullname(candidate) }.getOrNull()
}

private fun normalizeCommentReplyParentFullname(value: String): String {
    val normalized = value.trim().lowercase()
    val prefix = when {
        normalized.startsWith(COMMENT_REPLY_COMMENT_FULLNAME_PREFIX) ->
            COMMENT_REPLY_COMMENT_FULLNAME_PREFIX

        normalized.startsWith(COMMENT_REPLY_POST_FULLNAME_PREFIX) ->
            COMMENT_REPLY_POST_FULLNAME_PREFIX

        else -> throw IllegalArgumentException(
            "Comment reply parent must be a t1 or t3 fullname",
        )
    }
    val identifier = normalized.removePrefix(prefix)
    require(identifier.length in 1..COMMENT_REPLY_THING_ID_MAX_CHARS) {
        "Comment reply parent ID length is outside the supported range"
    }
    require(identifier.all(Char::isLetterOrDigit)) {
        "Comment reply parent ID contains unsupported characters"
    }
    return "$prefix$identifier"
}

private const val COMMENT_REPLY_THING_ID_MAX_CHARS = 16
private const val COMMENT_REPLY_COMMENT_FULLNAME_PREFIX = "t1_"
private const val COMMENT_REPLY_POST_FULLNAME_PREFIX = "t3_"

private const val COMMENT_TARGET_ID_MAX_CHARS = 256
private const val COMMENT_TARGET_AUTHOR_MAX_CHARS = 128
