package app.subshell.prototype.data.account

import app.subshell.prototype.data.error.FailureDetails

enum class PostSavePhase {
    IDLE,
    AUTHORIZATION_REQUIRED,
    AUTHORIZING,
    AWAITING_CALLBACK,
    SENDING,
    FAILED,
    SUCCEEDED,
}

enum class PostSaveFailureKind {
    CONFIGURATION,
    ACCOUNT_CHANGED,
    BROWSER_LAUNCH,
    CALLBACK,
    AUTHORIZATION,
    SESSION,
    VALIDATION,
    HTTP,
    TRANSPORT,
    PROTOCOL,
}

data class PostSaveRequest(
    val accountStableId: String,
    val postFullname: String,
    val saved: Boolean,
) {
    init {
        require(accountStableId.isNotBlank()) {
            "Post-save account ID cannot be blank"
        }
        require(normalizePostSaveFullname(postFullname) == postFullname) {
            "Post-save target must be a canonical t1 or t3 fullname"
        }
    }
}

data class PostSaveUiState(
    val phase: PostSavePhase = PostSavePhase.IDLE,
    val accountStableId: String? = null,
    val postFullname: String? = null,
    val saved: Boolean? = null,
    val failureKind: PostSaveFailureKind? = null,
    val retryable: Boolean = false,
    val statusCode: Int? = null,
    val failureDetails: FailureDetails? = null,
    val completionRevision: Long = 0L,
) {
    val inProgress: Boolean
        get() = phase in setOf(
            PostSavePhase.AUTHORIZING,
            PostSavePhase.AWAITING_CALLBACK,
            PostSavePhase.SENDING,
        )
}

internal fun PostSaveRequest.normalizedPostSaveRequest(): PostSaveRequest =
    PostSaveRequest(
        accountStableId = accountStableId.trim(),
        postFullname = normalizePostSaveFullname(postFullname),
        saved = saved,
    )

// COMMENTSAVE01A1_COMMENT_CAPABLE_SAVE_FULLNAME_V1
// Legacy HOMEACTION01 callers use this normalizer for posts. Canonical t1 comment
// fullnames are also preserved so the existing Reddit save transport can carry
// either supported thing kind without introducing a parallel mutation path.
internal fun normalizePostSaveFullname(value: String): String {
    val normalized = value.trim().lowercase()
    val prefix = when {
        normalized.startsWith(COMMENT_SAVE_FULLNAME_PREFIX) ->
            COMMENT_SAVE_FULLNAME_PREFIX
        normalized.startsWith(POST_SAVE_FULLNAME_PREFIX) ->
            POST_SAVE_FULLNAME_PREFIX
        else -> POST_SAVE_FULLNAME_PREFIX
    }
    // One prefix only: `t1_t3_abc` is malformed, not `t1_abc`.
    val identifier = normalized.removePrefix(prefix)
    return normalizeSaveIdentifier(
        identifier = identifier,
        prefix = prefix,
        maxChars = if (prefix == COMMENT_SAVE_FULLNAME_PREFIX) {
            COMMENT_SAVE_THING_ID_MAX_CHARS
        } else {
            POST_SAVE_THING_ID_MAX_CHARS
        },
    )
}

internal fun normalizeCommentSaveFullname(value: String): String {
    val normalized = value.trim().lowercase()
    require(!normalized.startsWith(POST_SAVE_FULLNAME_PREFIX)) {
        "Comment-save target cannot be a post fullname"
    }
    val identifier = normalized.removePrefix(COMMENT_SAVE_FULLNAME_PREFIX)
    return normalizeSaveIdentifier(
        identifier = identifier,
        prefix = COMMENT_SAVE_FULLNAME_PREFIX,
        maxChars = COMMENT_SAVE_THING_ID_MAX_CHARS,
    )
}

// ACCOUNT02_SAVE_TARGET_KIND_V1: only an explicit `t1_` is a comment; a bare
// id is a post, as normalizePostSaveFullname reads it.
internal fun isCommentSaveFullname(value: String): Boolean =
    value.trim().lowercase().startsWith(COMMENT_SAVE_FULLNAME_PREFIX) &&
        runCatching { normalizeCommentSaveFullname(value) }.isSuccess

private fun normalizeSaveIdentifier(
    identifier: String,
    prefix: String,
    maxChars: Int,
): String {
    require(identifier.length in 1..maxChars) {
        "Save target ID length is outside the supported range"
    }
    require(identifier.all(Char::isLetterOrDigit)) {
        "Save target ID contains unsupported characters"
    }
    return "$prefix$identifier"
}

private const val POST_SAVE_THING_ID_MAX_CHARS = 16
private const val COMMENT_SAVE_THING_ID_MAX_CHARS = 32
private const val POST_SAVE_FULLNAME_PREFIX = "t3_"
private const val COMMENT_SAVE_FULLNAME_PREFIX = "t1_"
