package app.subshell.prototype.data.account

import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.normalizeRedditCommunityName

enum class RedditModmailUiFailureKind {
    CONFIGURATION,
    ACCOUNT_CHANGED,
    AUTHORIZATION,
    SESSION,
    HTTP,
    TRANSPORT,
    PROTOCOL,
}

// MODMAIL04A1_ACTIVE_ARCHIVED_LIST_MODE_V2
// MODMAIL05_TRIAGE_QUEUE_FILTERS_V1
enum class RedditModmailListMode {
    ACTIVE,
    NEW,
    IN_PROGRESS,
    FILTERED,
    HIGHLIGHTED,
    ARCHIVED,
    APPEALS,
    JOIN_REQUESTS,
    MOD_DISCUSSIONS,
    NOTIFICATIONS,
}

// MODMAIL06_AUTHORITATIVE_UNREAD_COUNTS_V1
// MODMAIL06R1_REDDIT_UNREAD_COUNT_SCHEMA_V1
data class RedditModmailUnreadCounts(
    val new: Int = 0,
    val inProgress: Int = 0,
    val filtered: Int = 0,
    val highlighted: Int = 0,
    val archived: Int = 0,
    val appeals: Int = 0,
    val joinRequests: Int = 0,
    val modDiscussions: Int = 0,
    val notifications: Int = 0,
) {
    init {
        require(
            listOf(
                new,
                inProgress,
                filtered,
                highlighted,
                archived,
                appeals,
                joinRequests,
                modDiscussions,
                notifications,
            ).all { count -> count >= 0 },
        )
    }

    /**
     * Reddit's unread-count response has no `all` field. The All/Active folder
     * is the combined view of New, In Progress, Join Requests and Notifications.
     * Highlighted is deliberately excluded because it can overlap another state.
     */
    val active: Int
        get() = (
            new.toLong() +
                inProgress.toLong() +
                joinRequests.toLong() +
                notifications.toLong()
            )
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()

    fun countFor(listMode: RedditModmailListMode): Int = when (listMode) {
        RedditModmailListMode.ACTIVE -> active
        RedditModmailListMode.NEW -> new
        RedditModmailListMode.IN_PROGRESS -> inProgress
        RedditModmailListMode.FILTERED -> filtered
        RedditModmailListMode.HIGHLIGHTED -> highlighted
        RedditModmailListMode.ARCHIVED -> archived
        RedditModmailListMode.APPEALS -> appeals
        RedditModmailListMode.JOIN_REQUESTS -> joinRequests
        RedditModmailListMode.MOD_DISCUSSIONS -> modDiscussions
        RedditModmailListMode.NOTIFICATIONS -> notifications
    }
}

enum class RedditModmailTriageAction(
    val muteHours: Int? = null,
) {
    HIGHLIGHT,
    UNHIGHLIGHT,
    MUTE_3_DAYS(72),
    MUTE_7_DAYS(168),
    MUTE_28_DAYS(672),
    UNMUTE,
}

data class RedditModmailParticipantMuteStatus(
    val muted: Boolean,
    val endEpochSeconds: Long? = null,
    val muteCount: Int? = null,
) {
    init {
        require(endEpochSeconds == null || endEpochSeconds >= 0L)
        require(muteCount == null || muteCount >= 0)
    }
}

data class RedditModmailConversation(
    val id: String,
    val communityName: String,
    val subject: String,
    val participantName: String?,
    val preview: String,
    val lastUpdatedEpochSeconds: Long?,
    val lastUnreadEpochSeconds: Long? = null,
    val latestMessageId: String? = null,
    val unread: Boolean,
    val isInternal: Boolean,
    val messageCount: Int,
    val archived: Boolean = false,
    val highlighted: Boolean = false,
) {
    init {
        require(id.matches(MODMAIL_ID_REGEX))
        require(normalizeRedditCommunityName(communityName) == communityName)
        require(subject.isNotBlank())
        require(preview.length <= MAX_MODMAIL_PREVIEW_CHARS)
        require(lastUpdatedEpochSeconds == null || lastUpdatedEpochSeconds >= 0L)
        require(lastUnreadEpochSeconds == null || lastUnreadEpochSeconds >= 0L)
        require(latestMessageId == null || latestMessageId.matches(MODMAIL_ID_REGEX))
        require(messageCount >= 0)
    }
}

data class RedditModmailMessage(
    val id: String,
    val authorName: String,
    val body: String,
    val createdEpochSeconds: Long?,
    val isInternal: Boolean,
    val participatingAs: String?,
    val authorHidden: Boolean = false,
) {
    init {
        require(id.matches(MODMAIL_ID_REGEX))
        require(authorName.isNotBlank())
        require(body.isNotBlank())
        require(createdEpochSeconds == null || createdEpochSeconds >= 0L)
    }
}

data class RedditModmailThread(
    val conversation: RedditModmailConversation,
    val messages: List<RedditModmailMessage>,
    val participantMuteStatus: RedditModmailParticipantMuteStatus? = null,
)

// MODMAIL01A1_ACCOUNT_SCOPED_READ_STATE_V1
// MODMAIL02_REPLY_INTERNAL_NOTE_STATE_V1
data class RedditModmailUiState(
    val accountStableId: String? = null,
    val mailCommunities: Set<String> = emptySet(),
    val conversations: List<RedditModmailConversation> = emptyList(),
    val nextCursor: String? = null,
    val loading: Boolean = false,
    val appending: Boolean = false,
    val loaded: Boolean = false,
    val authorizationRequired: Boolean = false,
    val authorizing: Boolean = false,
    val selectedConversationId: String? = null,
    val thread: RedditModmailThread? = null,
    val threadLoading: Boolean = false,
    val sendInProgress: Boolean = false,
    val sendOutcomeUnknown: Boolean = false,
    val sendRevision: Long = 0L,
    val listMode: RedditModmailListMode = RedditModmailListMode.ACTIVE,
    val conversationActionInProgress: Boolean = false,
    val conversationActionOutcomeUnknown: Boolean = false,
    val unreadCounts: RedditModmailUnreadCounts = RedditModmailUnreadCounts(),
    val readStateMutationInProgress: Boolean = false,
    val readStateOutcomeUnknown: Boolean = false,
    val failureKind: RedditModmailUiFailureKind? = null,
    val failureRetryable: Boolean = false,
    val failureHttpStatusCode: Int? = null,
    val failureDetails: FailureDetails? = null,
    val contentRevision: Long = 0L,
) {
    init {
        require(accountStableId == null || accountStableId.isNotBlank())
        require(nextCursor == null || nextCursor.matches(MODMAIL_ID_REGEX))
        require(selectedConversationId == null || selectedConversationId.matches(MODMAIL_ID_REGEX))
        require(failureHttpStatusCode == null || failureHttpStatusCode in 100..599)
        require(contentRevision >= 0L)
        require(sendRevision >= 0L)
    }

    fun matches(accountStableId: String): Boolean =
        this.accountStableId == accountStableId

    val inProgress: Boolean
        get() =
            loading ||
                appending ||
                authorizing ||
                threadLoading ||
                sendInProgress ||
                conversationActionInProgress ||
                readStateMutationInProgress

    val unreadCount: Int
        get() = unreadCounts.active
}

internal fun normalizeRedditModmailId(raw: String): String {
    val normalized = raw
        .trim()
        .removePrefix("ModmailConversation_")
        .removePrefix("ModmailMessage_")
        .lowercase()
    require(normalized.matches(MODMAIL_ID_REGEX)) { "Invalid Reddit modmail ID" }
    return normalized
}

private val MODMAIL_ID_REGEX = Regex("[a-z0-9]+")
private const val MAX_MODMAIL_PREVIEW_CHARS = 500
