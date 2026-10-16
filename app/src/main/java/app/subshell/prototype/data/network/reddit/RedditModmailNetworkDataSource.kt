package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.account.RedditModmailConversation
import app.subshell.prototype.data.account.RedditModmailMessage
import app.subshell.prototype.data.account.RedditModmailParticipantMuteStatus
import app.subshell.prototype.data.account.RedditModmailThread
import app.subshell.prototype.data.account.RedditModmailUnreadCounts
import app.subshell.prototype.data.account.normalizeRedditModmailId
import app.subshell.prototype.data.network.JsonGetRequest
import app.subshell.prototype.data.network.NetworkPage
import app.subshell.prototype.data.network.NetworkProtocolFailureKind
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.map
import app.subshell.prototype.data.normalizeRedditCommunityName
import java.time.Instant
import java.time.OffsetDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

internal interface RedditModmailSource {
    suspend fun mailCommunities(): NetworkResult<Set<String>>

    suspend fun conversations(
        after: String? = null,
        limit: Int = MODMAIL_CONVERSATION_PAGE_LIMIT,
    ): NetworkResult<NetworkPage<RedditModmailConversation>>

    suspend fun conversationsByState(
        after: String? = null,
        limit: Int = MODMAIL_CONVERSATION_PAGE_LIMIT,
        state: String = "all",
    ): NetworkResult<NetworkPage<RedditModmailConversation>> =
        conversations(
            after = after,
            limit = limit,
        )

    suspend fun unreadConversations(
        limit: Int = MODMAIL_CONVERSATION_MAX_LIMIT,
    ): NetworkResult<NetworkPage<RedditModmailConversation>>

    suspend fun unreadCounts(): NetworkResult<RedditModmailUnreadCounts>

    suspend fun conversation(
        conversationId: String,
    ): NetworkResult<RedditModmailThread>
}

// MODMAIL01A1_NEW_MODMAIL_READ_TRANSPORT_V1
// MODMAIL01A1R2_CONVERSATION_OWNER_SCHEMA_V1
// MODMAIL01A1R3_TOP_LEVEL_THREAD_MESSAGES_V1
// MODMAIL06_AUTHORITATIVE_UNREAD_NETWORK_V1
// MODMAIL06R1_OFFICIAL_UNREAD_COUNT_RESPONSE_V1
internal class RedditModmailNetworkDataSource(
    private val httpClient: SubshellHttpClient,
) : RedditModmailSource {
    override suspend fun mailCommunities(): NetworkResult<Set<String>> =
        httpClient.getJson(
            request = redditModmailSubredditsRequest(),
            deserializer = RedditModmailSubredditsResponseDto.serializer(),
        ).map { response ->
            response.subreddits.values
                .asSequence()
                .mapNotNull(RedditModmailSubredditAccessDto::normalizedName)
                .toCollection(linkedSetOf())
        }

    override suspend fun conversations(
        after: String?,
        limit: Int,
    ): NetworkResult<NetworkPage<RedditModmailConversation>> =
        conversationsByState(
            after = after,
            limit = limit,
            state = "all",
        )

    override suspend fun conversationsByState(
        after: String?,
        limit: Int,
        state: String,
    ): NetworkResult<NetworkPage<RedditModmailConversation>> =
        httpClient.getJson(
            request = redditModmailConversationsRequest(
                after = after,
                limit = limit,
                state = state,
            ),
            deserializer = RedditModmailConversationsDto.serializer(),
        ).map { response -> response.toConversationPage(limit) }

    override suspend fun unreadConversations(
        limit: Int,
    ): NetworkResult<NetworkPage<RedditModmailConversation>> =
        httpClient.getJson(
            request = redditModmailConversationsRequest(
                limit = limit,
                state = "all",
                sort = "unread",
            ),
            deserializer = RedditModmailConversationsDto.serializer(),
        ).map { response -> response.toConversationPage(limit) }

    override suspend fun unreadCounts(): NetworkResult<RedditModmailUnreadCounts> =
        httpClient.getJson(
            request = redditModmailUnreadCountsRequest(),
            deserializer = JsonObject.serializer(),
        ).map(JsonObject::toRedditModmailUnreadCounts)

    // DATALAYER03: an incomplete conversation payload is a protocol failure,
    // not an exception thrown out of the caller's coroutine.
    override suspend fun conversation(
        conversationId: String,
    ): NetworkResult<RedditModmailThread> {
        val id = normalizeRedditModmailId(conversationId)
        return when (
            val result = httpClient.getJson(
                request = redditModmailConversationRequest(id),
                deserializer = RedditModmailConversationResponseDto.serializer(),
            )
        ) {
            is NetworkResult.Success -> result.value.conversation
                ?.toThreadOrNull(
                    fallbackId = id,
                    topLevelMessages = result.value.messages,
                    participantUser = result.value.user,
                )
                ?.let { thread -> NetworkResult.Success(thread, result.metadata) }
                ?: NetworkResult.ProtocolFailure(NetworkProtocolFailureKind.MALFORMED_JSON)
            is NetworkResult.HttpFailure -> result
            is NetworkResult.TransportFailure -> result
            is NetworkResult.ProtocolFailure -> result
        }
    }
}

internal fun redditModmailSubredditsRequest(): JsonGetRequest = JsonGetRequest(
    pathSegments = listOf("api", "mod", "conversations", "subreddits"),
    queryParameters = listOf("raw_json" to "1"),
)

internal fun redditModmailConversationsRequest(
    after: String? = null,
    limit: Int = MODMAIL_CONVERSATION_PAGE_LIMIT,
    state: String = "all",
    sort: String = "recent",
): JsonGetRequest {
    require(limit in 1..MODMAIL_CONVERSATION_MAX_LIMIT)
    require(state in REDDIT_MODMAIL_CONVERSATION_STATES)
    require(sort in REDDIT_MODMAIL_CONVERSATION_SORTS)
    return JsonGetRequest(
        pathSegments = listOf("api", "mod", "conversations"),
        queryParameters = buildList {
            add("raw_json" to "1")
            add("state" to state)
            add("sort" to sort)
            add("limit" to limit.toString())
            after
                ?.let(::normalizeRedditModmailId)
                ?.let { cursor -> add("after" to cursor) }
        },
    )
}

internal fun redditModmailUnreadCountsRequest(): JsonGetRequest = JsonGetRequest(
    pathSegments = listOf("api", "mod", "conversations", "unread", "count"),
    queryParameters = listOf("raw_json" to "1"),
)

internal fun JsonObject.toRedditModmailUnreadCounts(): RedditModmailUnreadCounts =
    RedditModmailUnreadCounts(
        new = nonNegativeModmailCount("new"),
        inProgress = nonNegativeModmailCount("inprogress"),
        filtered = nonNegativeModmailCount("filtered"),
        highlighted = nonNegativeModmailCount("highlighted"),
        archived = nonNegativeModmailCount("archived"),
        appeals = nonNegativeModmailCount("appeals"),
        joinRequests = nonNegativeModmailCount("joinRequests"),
        modDiscussions = nonNegativeModmailCount("mod"),
        notifications = nonNegativeModmailCount("notifications"),
    )

private fun JsonObject.nonNegativeModmailCount(key: String): Int =
    get(key)
        ?.jsonPrimitive
        ?.intOrNull
        ?.coerceAtLeast(0)
        ?: 0

internal fun redditModmailConversationRequest(conversationId: String): JsonGetRequest =
    JsonGetRequest(
        pathSegments = listOf(
            "api",
            "mod",
            "conversations",
            normalizeRedditModmailId(conversationId),
        ),
        queryParameters = listOf(
            "raw_json" to "1",
            "markRead" to "false",
        ),
    )

@Serializable
internal data class RedditModmailConversationsDto(
    val conversationIds: List<String> = emptyList(),
    val conversations: Map<String, RedditModmailConversationDto> = emptyMap(),
    // MODMAIL_LIST_TOP_LEVEL_MESSAGES_V1 (OCB-553): Reddit's listing keeps the
    // messages here, referenced by each conversation's objIds (as the thread
    // endpoint does), not inside the conversation.
    val messages: Map<String, RedditModmailMessageDto> = emptyMap(),
    val viewerId: String? = null,
)

@Serializable
internal data class RedditModmailConversationResponseDto(
    val conversation: RedditModmailConversationDto? = null,
    val messages: Map<String, RedditModmailMessageDto> = emptyMap(),
    val user: RedditModmailConversationUserDto? = null,
)

@Serializable
internal data class RedditModmailConversationDto(
    val id: String? = null,
    val subject: String? = null,
    val participant: RedditModmailParticipantDto? = null,
    val owner: RedditModmailSubredditDto? = null,
    val subreddit: RedditModmailSubredditDto? = null,
    val objIds: List<RedditModmailObjectIdDto> = emptyList(),
    val messages: Map<String, RedditModmailMessageDto> = emptyMap(),
    val lastUpdated: String? = null,
    val lastUnread: String? = null,
    val numMessages: Int? = null,
    val isInternal: Boolean = false,
    val conversationType: String? = null,
    val state: Int? = null,
    val isHighlighted: Boolean = false,
)

@Serializable
internal data class RedditModmailObjectIdDto(
    val id: String? = null,
    val key: String? = null,
)
@Serializable
internal data class RedditModmailMessageDto(
    val id: String? = null,
    val author: RedditModmailParticipantDto? = null,
    val body: String? = null,
    val bodyMarkdown: String? = null,
    val date: String? = null,
    val isInternal: Boolean = false,
    val participatingAs: String? = null,
)

@Serializable
internal data class RedditModmailParticipantDto(
    val name: String? = null,
    val isDeleted: Boolean = false,
    val isMod: Boolean = false,
    val isHidden: Boolean = false,
)

@Serializable
internal data class RedditModmailConversationUserDto(
    val muteStatus: RedditModmailMuteStatusDto? = null,
)

@Serializable
internal data class RedditModmailMuteStatusDto(
    val isMuted: Boolean = false,
    val endDate: String? = null,
    val muteCount: Int? = null,
)

@Serializable
internal data class RedditModmailSubredditsResponseDto(
    val subreddits: Map<String, RedditModmailSubredditAccessDto> = emptyMap(),
)

@Serializable
internal data class RedditModmailSubredditAccessDto(
    @SerialName("display_name")
    val displayName: String? = null,
    val name: String? = null,
    val lastUpdated: String? = null,
)

@Serializable
internal data class RedditModmailSubredditDto(
    val displayName: String? = null,
    val name: String? = null,
)

private fun RedditModmailSubredditAccessDto.normalizedName(): String? =
    normalizeModmailSubredditName(displayName = displayName, name = name)

private fun RedditModmailSubredditDto.normalizedName(): String? =
    normalizeModmailSubredditName(displayName = displayName, name = name)

private fun normalizeModmailSubredditName(
    displayName: String?,
    name: String?,
): String? {
    val raw = displayName
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: name
            ?.trim()
            ?.takeIf { candidate ->
                candidate.isNotEmpty() && !candidate.startsWith("t5_", ignoreCase = true)
            }
        ?: return null
    return runCatching { normalizeRedditCommunityName(raw.removePrefix("r/")) }.getOrNull()
}

internal fun RedditModmailConversationsDto.toConversationPage(
    limit: Int,
): NetworkPage<RedditModmailConversation> {
    val orderedIds = conversationIds
        .asSequence()
        .mapNotNull { raw -> runCatching { normalizeRedditModmailId(raw) }.getOrNull() }
        .distinct()
        .toList()
    val byNormalizedId: Map<String, RedditModmailConversationDto> = conversations.entries
        .mapNotNull { (key, value) ->
            runCatching { normalizeRedditModmailId(value.id ?: key) }
                .getOrNull()
                ?.let { id -> id to value }
        }
        .toMap()

    val ordered = if (orderedIds.isNotEmpty()) {
        orderedIds.mapNotNull { id -> byNormalizedId[id]?.let { dto -> id to dto } }
    } else {
        byNormalizedId.entries
            .sortedByDescending { (_, dto) -> parseModmailEpochSeconds(dto.lastUpdated) ?: 0L }
            .map { (id, dto) -> id to dto }
    }
    // CORRECT01_MODMAIL_PAGING_ON_SERVER_IDS_V1 (OCB-636): whether there is
    // more, and the cursor, come from the conversations Reddit sent, not from
    // those that parsed, so one unreadable conversation no longer ends paging.
    val page = ordered.take(limit)
    val items = page
        .asSequence()
        .mapNotNull { (id, dto) -> dto.toSummary(fallbackId = id, topLevelMessages = messages) }
        .distinctBy(RedditModmailConversation::id)
        .toList()
    val nextCursor = page.lastOrNull()?.first.takeIf { page.size >= limit }
    return NetworkPage(items = items, nextCursor = nextCursor)
}

private fun RedditModmailConversationDto.toSummary(
    fallbackId: String? = null,
    topLevelMessages: Map<String, RedditModmailMessageDto> = emptyMap(),
): RedditModmailConversation? {
    val conversationId = runCatching {
        normalizeRedditModmailId(id ?: fallbackId.orEmpty())
    }.getOrNull() ?: return null
    val community = (owner ?: subreddit)?.normalizedName() ?: return null
    val normalizedSubject = subject
        ?.trim()
        ?.replace(Regex("\\s+"), " ")
        ?.takeIf(String::isNotEmpty)
        ?.take(MAX_MODMAIL_SUBJECT_CHARS)
        ?: "Modmail conversation"
    val referencedIds = objIds
        .filter { ref -> ref.key.equals("messages", ignoreCase = true) }
        .mapNotNull(RedditModmailObjectIdDto::id)
        .toSet()
    val ownMessages = topLevelMessages
        .filterKeys { id -> id in referencedIds }
        .ifEmpty { messages }
    val latestEntry = ownMessages.entries.maxByOrNull { (_, message) ->
        parseModmailEpochSeconds(message.date) ?: 0L
    }
    val latest = latestEntry?.value
    val latestMessageId = latestEntry
        ?.let { (key, message) ->
            runCatching { normalizeRedditModmailId(message.id ?: key) }.getOrNull()
        }
    val preview = latest
        ?.let(RedditModmailMessageDto::normalizedBody)
        ?.replace(Regex("\\s+"), " ")
        ?.take(MAX_MODMAIL_PREVIEW_CHARS)
        .orEmpty()
    val participantName = participant?.name
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.take(MAX_MODMAIL_PARTICIPANT_CHARS)
    return RedditModmailConversation(
        id = conversationId,
        communityName = community,
        subject = normalizedSubject,
        participantName = participantName,
        preview = preview,
        lastUpdatedEpochSeconds = parseModmailEpochSeconds(lastUpdated),
        lastUnreadEpochSeconds = parseModmailEpochSeconds(lastUnread),
        latestMessageId = latestMessageId,
        unread = !lastUnread.isNullOrBlank(),
        isInternal = isInternal || conversationType.equals("internal", ignoreCase = true),
        messageCount = (numMessages ?: ownMessages.size).coerceAtLeast(0),
        archived = state == REDDIT_MODMAIL_ARCHIVED_STATE,
        highlighted = isHighlighted,
    )
}

internal fun RedditModmailConversationDto.toThread(
    fallbackId: String,
    topLevelMessages: Map<String, RedditModmailMessageDto> = emptyMap(),
    participantUser: RedditModmailConversationUserDto? = null,
): RedditModmailThread = requireNotNull(
    toThreadOrNull(fallbackId, topLevelMessages, participantUser),
) {
    "Reddit modmail conversation metadata is incomplete"
}

/** Null when the conversation metadata is incomplete (see toSummary). */
internal fun RedditModmailConversationDto.toThreadOrNull(
    fallbackId: String,
    topLevelMessages: Map<String, RedditModmailMessageDto> = emptyMap(),
    participantUser: RedditModmailConversationUserDto? = null,
): RedditModmailThread? {
    val summary = toSummary(fallbackId, topLevelMessages) ?: return null
    val availableMessages = if (topLevelMessages.isNotEmpty()) {
        topLevelMessages
    } else {
        messages
    }
    val messageObjectIds = objIds
        .asSequence()
        .filter { ref -> ref.key.equals("messages", ignoreCase = true) }
        .mapNotNull(RedditModmailObjectIdDto::id)
        .distinct()
        .toList()
    val projectedMessages = if (messageObjectIds.isNotEmpty()) {
        messageObjectIds.mapNotNull { messageId ->
            availableMessages[messageId]?.toDomain(messageId)
        }
    } else {
        availableMessages.entries
            .mapNotNull { (key, value) -> value.toDomain(key) }
            .sortedWith(
                compareBy<RedditModmailMessage> { message ->
                    message.createdEpochSeconds ?: 0L
                }.thenBy(RedditModmailMessage::id),
            )
    }
    return RedditModmailThread(
        conversation = summary.copy(
            preview = projectedMessages.lastOrNull()?.body
                ?.replace(Regex("\\s+"), " ")
                ?.take(MAX_MODMAIL_PREVIEW_CHARS)
                .orEmpty(),
            messageCount = maxOf(summary.messageCount, projectedMessages.size),
        ),
        messages = projectedMessages,
        participantMuteStatus = participantUser?.muteStatus?.toDomain(),
    )
}

private fun RedditModmailMuteStatusDto.toDomain(): RedditModmailParticipantMuteStatus =
    RedditModmailParticipantMuteStatus(
        muted = isMuted,
        endEpochSeconds = parseModmailEpochSeconds(endDate),
        muteCount = muteCount?.coerceAtLeast(0),
    )

private fun RedditModmailMessageDto.toDomain(fallbackId: String): RedditModmailMessage? {
    val messageId = runCatching {
        normalizeRedditModmailId(id ?: fallbackId)
    }.getOrNull() ?: return null
    val normalizedBody = normalizedBody().takeIf(String::isNotEmpty) ?: return null
    val authorName = when {
        author?.isDeleted == true -> "[deleted]"
        !author?.name.isNullOrBlank() -> author.name.trim()
        isInternal -> "Moderator"
        else -> "reddit"
    }.take(MAX_MODMAIL_PARTICIPANT_CHARS)
    return RedditModmailMessage(
        id = messageId,
        authorName = authorName,
        body = normalizedBody.take(MAX_MODMAIL_BODY_CHARS),
        createdEpochSeconds = parseModmailEpochSeconds(date),
        isInternal = isInternal,
        participatingAs = participatingAs
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.take(MAX_MODMAIL_PARTICIPATING_AS_CHARS),
        authorHidden = author?.isHidden == true,
    )
}

private fun RedditModmailMessageDto.normalizedBody(): String =
    bodyMarkdown
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: body.orEmpty().trim()

internal fun parseModmailEpochSeconds(raw: String?): Long? {
    val value = raw?.trim()?.takeIf(String::isNotEmpty) ?: return null
    return runCatching { Instant.parse(value).epochSecond }
        .recoverCatching { OffsetDateTime.parse(value).toInstant().epochSecond }
        .getOrNull()
        ?.coerceAtLeast(0L)
}

internal const val MODMAIL_CONVERSATION_PAGE_LIMIT = 50
internal const val MODMAIL_CONVERSATION_MAX_LIMIT = 100
private const val MAX_MODMAIL_SUBJECT_CHARS = 200
private const val MAX_MODMAIL_PREVIEW_CHARS = 500
private const val MAX_MODMAIL_PARTICIPANT_CHARS = 100
private const val MAX_MODMAIL_BODY_CHARS = 20_000
private const val MAX_MODMAIL_PARTICIPATING_AS_CHARS = 64
private const val REDDIT_MODMAIL_ARCHIVED_STATE = 2
private val REDDIT_MODMAIL_CONVERSATION_SORTS = setOf(
    "recent",
    "mod",
    "user",
    "unread",
)
private val REDDIT_MODMAIL_CONVERSATION_STATES = setOf(
    "all",
    "appeals",
    "notifications",
    "inbox",
    "filtered",
    "inprogress",
    "mod",
    "archived",
    "default",
    "highlighted",
    "admin",
    "join_requests",
    "new",
)
