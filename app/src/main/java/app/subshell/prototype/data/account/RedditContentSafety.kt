package app.subshell.prototype.data.account

enum class RedditContentSafetyThingKind(
    val fullnamePrefix: String,
) {
    POST("t3_"),
    COMMENT("t1_"),
}

data class RedditPostVisibilityRequest(
    val accountStableId: String,
    val postFullname: String,
    val hidden: Boolean,
) {
    init {
        require(accountStableId.isNotBlank()) {
            "Post-visibility account ID cannot be blank"
        }
        require(normalizeRedditSafetyPostFullname(postFullname) == postFullname) {
            "Post-visibility target must be a canonical t3 fullname"
        }
    }
}

// USERACTION01A1_TYPED_USER_BLOCK_BOUNDARY_V1
data class RedditUserBlockRequest(
    val accountStableId: String,
    val username: String,
    val blocked: Boolean,
) {
    init {
        require(accountStableId.isNotBlank()) {
            "User-block account ID cannot be blank"
        }
        require(normalizeRedditSafetyUsername(username) == username) {
            "User-block target must be a canonical Reddit username"
        }
    }
}

internal fun RedditUserBlockRequest.normalizedRedditUserBlockRequest():
    RedditUserBlockRequest =
    RedditUserBlockRequest(
        accountStableId = accountStableId.trim(),
        username = normalizeRedditSafetyUsername(username),
        blocked = blocked,
    )

internal fun normalizeRedditSafetyUsername(value: String): String {
    val normalized = value
        .trim()
        .lowercase()
        .removePrefix("u/")
        .trim()
    require(normalized.length in 1..REDDIT_USERNAME_MAX_CHARS) {
        "Reddit username length is outside the supported range"
    }
    require(normalized.all { character ->
        character.isLetterOrDigit() || character == '_' || character == '-'
    }) {
        "Reddit username contains unsupported characters"
    }
    return normalized
}

internal fun normalizeRedditSafetyAccountFullname(value: String): String {
    val normalized = value
        .trim()
        .lowercase()
        .removePrefix("t2_")
    require(normalized.length in 1..REDDIT_ACCOUNT_ID_MAX_CHARS) {
        "Reddit account ID length is outside the supported range"
    }
    require(normalized.all(Char::isLetterOrDigit)) {
        "Reddit account ID contains unsupported characters"
    }
    return "t2_$normalized"
}

data class RedditContentReportRequest(
    val accountStableId: String,
    val thingFullname: String,
    val reason: String,
    // DEAD05_REPORT_SENDS_REASON_ONLY_V1 (OCB-465): the report dialog sends
    // one reason (plus the community); the never-set rule/other/site reason,
    // additional info and custom text fields are gone.
    val communityName: String? = null,
) {
    init {
        require(accountStableId.isNotBlank()) {
            "Content-report account ID cannot be blank"
        }
        require(normalizeRedditSafetyThingFullname(thingFullname) == thingFullname) {
            "Content-report target must be a canonical t1 or t3 fullname"
        }
        require(reason.isNotBlank() && reason.length <= REPORT_REASON_MAX_CHARS) {
            "Content-report reason must contain 1..$REPORT_REASON_MAX_CHARS characters"
        }
        validateOptionalReportField("community name", communityName, REPORT_COMMUNITY_MAX_CHARS)
    }

    val thingKind: RedditContentSafetyThingKind
        get() = if (thingFullname.startsWith(RedditContentSafetyThingKind.COMMENT.fullnamePrefix)) {
            RedditContentSafetyThingKind.COMMENT
        } else {
            RedditContentSafetyThingKind.POST
        }
}

internal fun RedditPostVisibilityRequest.normalizedRedditPostVisibilityRequest():
    RedditPostVisibilityRequest =
    RedditPostVisibilityRequest(
        accountStableId = accountStableId.trim(),
        postFullname = normalizeRedditSafetyPostFullname(postFullname),
        hidden = hidden,
    )

internal fun RedditContentReportRequest.normalizedRedditContentReportRequest():
    RedditContentReportRequest =
    RedditContentReportRequest(
        accountStableId = accountStableId.trim(),
        thingFullname = normalizeRedditSafetyThingFullname(thingFullname),
        reason = reason.trim(),
        communityName = normalizeOptionalReportField(communityName, REPORT_COMMUNITY_MAX_CHARS),
    )

// SAFETY01A1_TYPED_T3_VISIBILITY_T1_T3_REPORT_BOUNDARY_V1
internal fun normalizeRedditSafetyPostFullname(value: String): String {
    val normalized = value.trim().lowercase()
    require(!normalized.startsWith(RedditContentSafetyThingKind.COMMENT.fullnamePrefix)) {
        "Post-visibility target cannot be a comment fullname"
    }
    val identifier = normalized.removePrefix(RedditContentSafetyThingKind.POST.fullnamePrefix)
    return normalizeSafetyIdentifier(
        identifier = identifier,
        prefix = RedditContentSafetyThingKind.POST.fullnamePrefix,
        maxChars = POST_THING_ID_MAX_CHARS,
    )
}

internal fun normalizeRedditSafetyCommentFullname(value: String): String {
    val normalized = value.trim().lowercase()
    require(!normalized.startsWith(RedditContentSafetyThingKind.POST.fullnamePrefix)) {
        "Comment-report target cannot be a post fullname"
    }
    val identifier = normalized.removePrefix(RedditContentSafetyThingKind.COMMENT.fullnamePrefix)
    return normalizeSafetyIdentifier(
        identifier = identifier,
        prefix = RedditContentSafetyThingKind.COMMENT.fullnamePrefix,
        maxChars = COMMENT_THING_ID_MAX_CHARS,
    )
}

internal fun normalizeRedditSafetyThingFullname(value: String): String {
    val normalized = value.trim().lowercase()
    return when {
        normalized.startsWith(RedditContentSafetyThingKind.POST.fullnamePrefix) ->
            normalizeRedditSafetyPostFullname(normalized)
        normalized.startsWith(RedditContentSafetyThingKind.COMMENT.fullnamePrefix) ->
            normalizeRedditSafetyCommentFullname(normalized)
        else ->
            throw IllegalArgumentException(
                "Content-report target must include an explicit t1 or t3 fullname prefix",
            )
    }
}

private fun normalizeSafetyIdentifier(
    identifier: String,
    prefix: String,
    maxChars: Int,
): String {
    require(identifier.length in 1..maxChars) {
        "Safety target ID length is outside the supported range"
    }
    require(identifier.all(Char::isLetterOrDigit)) {
        "Safety target ID contains unsupported characters"
    }
    return "$prefix$identifier"
}

private fun validateOptionalReportField(
    label: String,
    value: String?,
    maxChars: Int,
) {
    value ?: return
    require(value.length <= maxChars) {
        "Content-report $label exceeds $maxChars characters"
    }
}

private fun normalizeOptionalReportField(
    value: String?,
    maxChars: Int,
): String? =
    value
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.also { normalized ->
            require(normalized.length <= maxChars) {
                "Content-report field exceeds $maxChars characters"
            }
        }

internal const val REPORT_REASON_MAX_CHARS = 100
internal const val REPORT_COMMUNITY_MAX_CHARS = 1_000
private const val REDDIT_USERNAME_MAX_CHARS = 20
private const val REDDIT_ACCOUNT_ID_MAX_CHARS = 32
private const val POST_THING_ID_MAX_CHARS = 16
private const val COMMENT_THING_ID_MAX_CHARS = 32
