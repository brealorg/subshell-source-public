package app.subshell.prototype.data.account

// SUBSHELL_MESSAGES02C7_FOREGROUND_MATRIX_DIRECT_CHAT_LIVE_READ_V1

import app.subshell.prototype.model.MessageBubble
import kotlin.math.abs

enum class DirectChatLiveFailureKind {
    SESSION,
    ROOM,
    HTTP,
    TRANSPORT,
    PROTOCOL,
}

// SUBSHELL_MESSAGES02D3B_DELETE_OWN_DIRECT_CHAT_MESSAGE_V1
enum class DirectChatDeleteFailureKind {
    SESSION,
    ROOM,
    HTTP,
    OUTCOME_UNKNOWN,
    PROTOCOL,
    NOT_OWNER,
    UNAVAILABLE,
}

// SUBSHELL_MESSAGES02E2A_DIRECT_CHAT_REACTION_WRITE_V1
enum class DirectChatReactionFailureKind {
    SESSION,
    ROOM,
    HTTP,
    OUTCOME_UNKNOWN,
    PROTOCOL,
    TARGET,
    UNAVAILABLE,
}

data class DirectChatReaction(
    val reactionEventId: String,
    val targetEventId: String,
    val key: String,
    val senderIsSelf: Boolean,
) {
    init {
        require(reactionEventId.startsWith('$')) {
            "Direct Chat reaction event ID must be a Matrix event ID"
        }
        require(targetEventId.startsWith('$')) {
            "Direct Chat reaction target ID must be a Matrix event ID"
        }
        require(key.isNotBlank()) {
            "Direct Chat reaction key cannot be blank"
        }
    }
}

data class DirectChatReactionSummary(
    val key: String,
    val count: Int,
    val self: Boolean,
) {
    init {
        require(key.isNotBlank()) { "Direct Chat reaction summary key cannot be blank" }
        require(count > 0) { "Direct Chat reaction summary count must be positive" }
    }
}

data class DirectChatLiveUiState(
    val accountStableId: String? = null,
    val inboxItemId: String? = null,
    val participantUsername: String? = null,
    val connecting: Boolean = false,
    val live: Boolean = false,
    // SUBSHELL_MESSAGES02D2A_INBOUND_TYPING_UI_V1
    val peerTyping: Boolean = false,
    val messages: List<MessageBubble> = emptyList(),
    val redactedMessageIds: Set<String> = emptySet(),
    val deletingEventId: String? = null,
    val deleteFailureKind: DirectChatDeleteFailureKind? = null,
    val reactions: List<DirectChatReaction> = emptyList(),
    // SUBSHELL_MESSAGES02E2A_R3D_PERSISTED_REACTION_PRESENTATION_CACHE_V1
    val cachedReactionTargets: List<DirectChatReactionCachedTarget> = emptyList(),
    val reactingEventId: String? = null,
    val reactionFailureKind: DirectChatReactionFailureKind? = null,
    val failureKind: DirectChatLiveFailureKind? = null,
    val contentRevision: Long = 0L,
) {
    init {
        require(accountStableId == null || accountStableId.isNotBlank()) {
            "Direct Chat live account stable ID cannot be blank"
        }
        require(inboxItemId == null || inboxItemId.isNotBlank()) {
            "Direct Chat live inbox item ID cannot be blank"
        }
        require(participantUsername == null || participantUsername.isNotBlank()) {
            "Direct Chat live participant username cannot be blank"
        }
        require(contentRevision >= 0L) {
            "Direct Chat live content revision cannot be negative"
        }
    }

    fun matches(
        accountStableId: String,
        inboxItemId: String,
        participantUsername: String? = null,
    ): Boolean =
        this.accountStableId == accountStableId &&
            this.inboxItemId == inboxItemId &&
            (
                participantUsername == null ||
                    this.participantUsername.equals(participantUsername, ignoreCase = true)
                )
}

internal fun mergeDirectChatLiveMessages(
    authoritative: List<MessageBubble>,
    live: List<MessageBubble>,
): List<MessageBubble> {
    if (live.isEmpty()) return authoritative
    if (authoritative.isEmpty()) return live.distinctBy(MessageBubble::id).sortedConversationTimeline()

    val consumedAuthoritative = mutableSetOf<Int>()
    val additions = mutableListOf<MessageBubble>()

    live
        .distinctBy(MessageBubble::id)
        .sortedConversationTimeline()
        .forEach { liveMessage ->
            val exactId = authoritative.indexOfFirst { message -> message.id == liveMessage.id }
            if (exactId >= 0) {
                consumedAuthoritative += exactId
                return@forEach
            }

            val liveMediaId = redditMatrixMediaIdFromMessageBody(liveMessage.body)
            val mediaMatch = if (liveMediaId == null) {
                -1
            } else {
                authoritative.indices.firstOrNull { index ->
                    index !in consumedAuthoritative &&
                        redditMatrixMediaIdFromMessageBody(authoritative[index].body) == liveMediaId
                } ?: -1
            }
            if (mediaMatch >= 0) {
                consumedAuthoritative += mediaMatch
                return@forEach
            }

            val textMatch = closestConversationTextMatch(
                authoritative = authoritative,
                liveMessage = liveMessage,
                consumedAuthoritative = consumedAuthoritative,
            )
            if (textMatch >= 0) {
                consumedAuthoritative += textMatch
            } else {
                additions += liveMessage
            }
        }

    if (additions.isEmpty()) return authoritative
    return (authoritative + additions).sortedConversationTimeline()
}

internal fun redditMatrixMediaIdFromMessageBody(body: String): String? {
    val normalized = body.trim()
    MATRIX_MEDIA_ID_PATTERNS.forEach { pattern ->
        pattern.find(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf(String::isNotBlank)
            ?.let { return it }
    }
    return null
}

private fun closestConversationTextMatch(
    authoritative: List<MessageBubble>,
    liveMessage: MessageBubble,
    consumedAuthoritative: Set<Int>,
): Int {
    val liveBody = liveMessage.body.trim()
    if (liveBody.isEmpty()) return -1
    val liveEpoch = liveMessage.createdEpochSeconds ?: return -1

    return authoritative.indices
        .asSequence()
        .filter { it !in consumedAuthoritative }
        .filter { index ->
            val candidate = authoritative[index]
            candidate.outgoing == liveMessage.outgoing &&
                candidate.body.trim() == liveBody &&
                candidate.createdEpochSeconds != null
        }
        .map { index ->
            index to abs(requireNotNull(authoritative[index].createdEpochSeconds) - liveEpoch)
        }
        .filter { (_, deltaSeconds) -> deltaSeconds <= TEXT_DEDUPE_WINDOW_SECONDS }
        .minByOrNull { (_, deltaSeconds) -> deltaSeconds }
        ?.first
        ?: -1
}

private fun List<MessageBubble>.sortedConversationTimeline(): List<MessageBubble> =
    withIndex()
        .sortedWith(
            compareBy<IndexedValue<MessageBubble>> { indexed ->
                indexed.value.createdEpochSeconds ?: Long.MAX_VALUE
            }.thenBy { indexed -> indexed.index },
        )
        .map { indexed -> indexed.value }

// D3B keeps authoritative Reddit inbox bubbles and live Matrix bubbles separate. A delete target
// is permitted only when one displayed own bubble maps to exactly one live Matrix event.
internal fun directChatMessagesReferToSameMatrixEvent(
    displayed: MessageBubble,
    live: MessageBubble,
): Boolean {
    if (displayed.id == live.id) return true
    if (displayed.outgoing != live.outgoing) return false

    val displayedMediaId = redditMatrixMediaIdFromMessageBody(displayed.body)
    val liveMediaId = redditMatrixMediaIdFromMessageBody(live.body)
    if (displayedMediaId != null && displayedMediaId == liveMediaId) return true

    if (displayed.body.trim() != live.body.trim()) return false
    val displayedTime = displayed.createdEpochSeconds ?: return false
    val liveTime = live.createdEpochSeconds ?: return false
    return kotlin.math.abs(displayedTime - liveTime) <= TEXT_DEDUPE_WINDOW_SECONDS
}

internal fun directChatMatrixDeleteEventId(
    displayed: MessageBubble,
    live: List<MessageBubble>,
    redactedEventIds: Set<String>,
): String? {
    if (!displayed.outgoing) return null
    val candidates = live.asSequence()
        .filter { candidate -> candidate.outgoing }
        .filter { candidate -> candidate.id.startsWith('$') }
        .filterNot { candidate -> candidate.id in redactedEventIds }
        .filter { candidate -> directChatMessagesReferToSameMatrixEvent(displayed, candidate) }
        .map(MessageBubble::id)
        .distinct()
        .toList()
    return candidates.singleOrNull()
}

internal fun directChatMatrixReactionTargetEventId(
    displayed: MessageBubble,
    live: List<MessageBubble>,
    redactedEventIds: Set<String>,
): String? {
    val candidates = live.asSequence()
        .filter { candidate -> candidate.id.startsWith('$') }
        .filterNot { candidate -> candidate.id in redactedEventIds }
        .filter { candidate -> directChatMessagesReferToSameMatrixEvent(displayed, candidate) }
        .map(MessageBubble::id)
        .distinct()
        .toList()
    return candidates.singleOrNull()
}

internal fun directChatReactionSummaries(
    targetEventId: String,
    reactions: List<DirectChatReaction>,
    redactedEventIds: Set<String>,
): List<DirectChatReactionSummary> {
    if (!targetEventId.startsWith('$')) return emptyList()
    return reactions
        .asSequence()
        .filter { reaction -> reaction.targetEventId == targetEventId }
        .filterNot { reaction -> reaction.reactionEventId in redactedEventIds }
        .groupBy(DirectChatReaction::key)
        .map { (key, matching) ->
            DirectChatReactionSummary(
                key = key,
                count = matching.size,
                self = matching.any(DirectChatReaction::senderIsSelf),
            )
        }
}

// SUBSHELL_MESSAGES02E2A_R3B_AUTHORITATIVE_REACTION_HYDRATION_V1
internal fun reconcileDirectChatAuthoritativeReactions(
    current: List<DirectChatReaction>,
    authoritativeTargetEventIds: Set<String>,
    authoritative: List<DirectChatReaction>,
    preserveReactionEventIds: Set<String>,
    redactedEventIds: Set<String>,
    maxEvents: Int,
): List<DirectChatReaction> {
    require(maxEvents > 0) { "Direct Chat reaction state bound must be positive" }
    if (authoritativeTargetEventIds.isEmpty()) {
        return current
            .filterNot { reaction ->
                reaction.reactionEventId in redactedEventIds ||
                    reaction.targetEventId in redactedEventIds
            }
            .distinctBy(DirectChatReaction::reactionEventId)
            .takeLast(maxEvents)
    }

    val retained = current.filter { reaction ->
        reaction.targetEventId !in authoritativeTargetEventIds ||
            reaction.reactionEventId in preserveReactionEventIds
    }
    return (retained + authoritative)
        .filterNot { reaction ->
            reaction.reactionEventId in redactedEventIds ||
                reaction.targetEventId in redactedEventIds
        }
        .distinctBy(DirectChatReaction::reactionEventId)
        .takeLast(maxEvents)
}


internal fun suppressRedactedDirectChatAuthoritativeMessages(
    authoritative: List<MessageBubble>,
    live: List<MessageBubble>,
    redactedEventIds: Set<String>,
): List<MessageBubble> {
    if (authoritative.isEmpty() || live.isEmpty() || redactedEventIds.isEmpty()) return authoritative

    val redactedLive = live.filter { message -> message.id in redactedEventIds }
    if (redactedLive.isEmpty()) return authoritative

    val consumedAuthoritative = mutableSetOf<Int>()
    redactedLive.forEach { redacted ->
        val candidates = authoritative.indices.filter { index ->
            index !in consumedAuthoritative &&
                directChatMessagesReferToSameMatrixEvent(authoritative[index], redacted)
        }
        if (candidates.size == 1) consumedAuthoritative += candidates.single()
    }
    return authoritative.filterIndexed { index, _ -> index !in consumedAuthoritative }
}

private const val TEXT_DEDUPE_WINDOW_SECONDS = 8L
private val MATRIX_MEDIA_ID_PATTERNS = listOf(
    // ACCOUNT01_MATRIX_MEDIA_ID_V1: in a raw string `\s` is already the
    // whitespace class; `\\s` excluded a backslash and the letter "s" and cut
    // every media ID at its first "s".
    Regex("""mxc://reddit\.com/([^/\s?#)]+)""", RegexOption.IGNORE_CASE),
    Regex(
        """matrix\.redditspace\.com/_matrix/(?:media/v3|client/v1/media)/download/reddit\.com/([^/\s?#)]+)""",
        RegexOption.IGNORE_CASE,
    ),
)
