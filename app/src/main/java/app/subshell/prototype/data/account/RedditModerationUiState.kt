package app.subshell.prototype.data.account

import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.normalizeRedditCommunityName

enum class RedditModerationUiFailureKind {
    CLIENT_CONFIGURATION,
    SESSION,
    HTTP,
    TRANSPORT,
    PROTOCOL,
}

enum class RedditModeratorQueueKind {
    POST,
    COMMENT,
}

data class RedditModeratorQueueItem(
    val fullname: String,
    val kind: RedditModeratorQueueKind,
    val communityName: String,
    val author: String,
    val summary: String,
    val reportCount: Int = 0,
    val permalink: String? = null,
    val reportDetails: RedditModeratorReportDetails = RedditModeratorReportDetails(),
    val contentState: RedditModeratorContentState = RedditModeratorContentState(),
) {
    init {
        require(fullname.matches(Regex("t[13]_[a-z0-9]+")))
        require(communityName.isNotBlank())
        require(author.isNotBlank())
        require(summary.isNotBlank())
        require(reportCount >= 0)
    }
}

// MOD01A22_EXACT_MODERATION_TARGET_V1
data class RedditModeratorQueueTarget(
    val postId: String,
    val commentFullname: String? = null,
) {
    init {
        require(postId.matches(Regex("[a-z0-9]+")))
        require(
            commentFullname == null ||
                commentFullname.matches(Regex("t1_[a-z0-9]+")),
        )
    }
}

internal fun RedditModeratorQueueItem.canonicalModerationTarget():
    RedditModeratorQueueTarget? {
    return when (kind) {
        RedditModeratorQueueKind.POST -> {
            val postId = fullname
                .removePrefix("t3_")
                .takeIf { candidate -> candidate.matches(THING_ID_REGEX) }
                ?: return null
            RedditModeratorQueueTarget(postId = postId)
        }

        RedditModeratorQueueKind.COMMENT -> {
            val postId = permalink
                ?.let(MODERATION_PERMALINK_REGEX::find)
                ?.groupValues
                ?.getOrNull(1)
                ?.lowercase()
                ?.takeIf { candidate -> candidate.matches(THING_ID_REGEX) }
                ?: return null
            RedditModeratorQueueTarget(
                postId = postId,
                commentFullname = fullname,
            )
        }
    }
}

// MOD01A1_ACCOUNT_SCOPED_MODERATOR_DIRECTORY_AND_QUEUE_V1
// MOD01A22_CONDITIONAL_INBOX_MOD_TAB_UNIFIED_QUEUE_V1
data class RedditModerationUiState(
    val accountStableId: String? = null,
    val moderatedCommunities: Set<String> = emptySet(),
    val directoryLoading: Boolean = false,
    val directoryLoaded: Boolean = false,
    val directoryFailureKind: RedditModerationUiFailureKind? = null,
    val directoryFailureRetryable: Boolean = false,
    val directoryFailureDetails: FailureDetails? = null,
    val directoryFailureHttpStatusCode: Int? = null,
    val queueCommunityName: String? = null,
    val queueItems: List<RedditModeratorQueueItem> = emptyList(),
    val queueLoading: Boolean = false,
    val queueLoaded: Boolean = false,
    val queueNextCursor: String? = null,
    val queueFailureKind: RedditModerationUiFailureKind? = null,
    val queueFailureRetryable: Boolean = false,
    val queueFailureDetails: FailureDetails? = null,
    val queueFailureHttpStatusCode: Int? = null,
    val contentRevision: Long = 0L,
) {
    init {
        require(accountStableId == null || accountStableId.isNotBlank())
        require(queueCommunityName == null || queueCommunityName.isNotBlank())
        require(directoryFailureHttpStatusCode == null || directoryFailureHttpStatusCode in 100..599)
        require(queueFailureHttpStatusCode == null || queueFailureHttpStatusCode in 100..599)
        require(contentRevision >= 0L)
    }

    fun matchesAccount(accountStableId: String): Boolean =
        this.accountStableId == accountStableId

    fun moderates(rawCommunityName: String): Boolean {
        val normalized = runCatching {
            normalizeRedditCommunityName(rawCommunityName)
        }.getOrNull() ?: return false
        return normalized in moderatedCommunities
    }

    fun queueMatches(accountStableId: String, rawCommunityName: String): Boolean {
        val normalized = runCatching {
            normalizeRedditCommunityName(rawCommunityName)
        }.getOrNull() ?: return false
        return this.accountStableId == accountStableId &&
            queueCommunityName == normalized
    }

    fun aggregateQueueMatches(accountStableId: String): Boolean =
        queueMatches(accountStableId, REDDIT_MODERATION_AGGREGATE_QUEUE)

    fun queueContainsContext(
        accountStableId: String,
        rawCommunityName: String,
    ): Boolean {
        val normalized = runCatching {
            normalizeRedditCommunityName(rawCommunityName)
        }.getOrNull() ?: return false
        return this.accountStableId == accountStableId &&
            (
                queueCommunityName == normalized ||
                    queueCommunityName == REDDIT_MODERATION_AGGREGATE_QUEUE
                )
    }
}

internal const val REDDIT_MODERATION_AGGREGATE_QUEUE = "mod"
private val THING_ID_REGEX = Regex("[a-z0-9]+")
private val MODERATION_PERMALINK_REGEX = Regex(
    pattern = "(?:^|/)comments/([a-z0-9]+)(?:/[^/?#]*)?(?:/[a-z0-9]+)?(?:[/?#]|$)",
    option = RegexOption.IGNORE_CASE,
)
