package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.model.redditReadableText
import app.subshell.prototype.data.diagnostics.READ_EVENT_INBOX_TARGET_EMPTY
import app.subshell.prototype.data.diagnostics.RedditReadDiagnostics
import app.subshell.prototype.data.network.JsonGetRequest
import app.subshell.prototype.data.network.NetworkPage
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.data.network.map
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.model.MessageBubble
import app.subshell.prototype.model.Post
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

internal interface RedditInboxPageSource {
    suspend fun page(
        cursor: String?,
        limit: Int,
        currentUsername: String,
        balanceHistory: Boolean = true,
    ): NetworkResult<NetworkPage<InboxItem>>
}

internal interface RedditInboxTargetSource {
    suspend fun post(postId: String): NetworkResult<Post?>
}

internal class RedditInboxNetworkDataSource(
    private val httpClient: SubshellHttpClient,
    private val clock: EpochSecondsClock = SystemEpochSecondsClock,
    private val json: Json = SubshellNetworkJson.providerJson,
) : RedditInboxPageSource {
    override suspend fun page(
        cursor: String?,
        limit: Int,
        currentUsername: String,
        balanceHistory: Boolean,
    ): NetworkResult<NetworkPage<InboxItem>> {
        // INBOXFAST01_INBOX_AND_SENT_IN_PARALLEL_V1: the two first requests do
        // not depend on each other, so they run at the same time (Google's
        // coroutine guidance for independent calls); a refresh took one
        // Reddit round trip longer than it needed.
        val (fetchedInbox, fetchedSent, fetchedUnread) = coroutineScope {
            val inboxRequest = async {
                httpClient.getJson(
                    request = redditInboxJsonRequest(cursor, limit),
                    deserializer = RedditInboxListingDto.serializer(),
                )
            }
            val sentRequest = async {
                httpClient.getJson(
                    request = redditSentJsonRequest(REDDIT_INBOX_MAX_PAGE_SIZE),
                    deserializer = RedditInboxListingDto.serializer(),
                )
            }
            // INBOXORDER01_STORED_ROWS_READ_ELSEWHERE_TURN_READ_V1: a refresh
            // also asks which items are unread now, so stored rows below the
            // fresh page that were read elsewhere stop counting as unread.
            val unreadRequest = if (cursor == null) {
                async {
                    httpClient.getJson(
                        request = redditUnreadJsonRequest(REDDIT_INBOX_MAX_PAGE_SIZE),
                        deserializer = RedditInboxListingDto.serializer(),
                    )
                }
            } else {
                null
            }
            Triple(inboxRequest.await(), sentRequest.await(), unreadRequest?.await())
        }
        val inbox = when (val result = fetchedInbox) {
            is NetworkResult.Success -> result
            is NetworkResult.HttpFailure -> return result
            is NetworkResult.TransportFailure -> return result
            is NetworkResult.ProtocolFailure -> return result
        }

        // DATALAYER05: a later page (cursor != null) is appended to the cached
        // list, so a failed sent request only means this page's threads miss
        // your own recent replies; the inbox page itself is kept instead of
        // failing. The first page replaces the list, so it still fails: without
        // sent, threads that hold only your messages would vanish.
        val sent = if (cursor != null && fetchedSent !is NetworkResult.Success) {
            NetworkResult.Success(RedditInboxListingDto(), inbox.metadata)
        } else {
            fetchedSent
        }
        return when (sent) {
            is NetworkResult.Success -> {
                val inboxPages = mutableListOf(inbox.value)
                val sentPages = mutableListOf(sent.value)
                // INBOXFAST01_A_REFRESH_READS_ONLY_THE_FIRST_PAGES_V1: once the
                // inbox is stored, its older pages stay below a refresh
                // (INBOXSSOT01), so only the first load balances the history.
                if (cursor == null && balanceHistory) {
                    balanceInitialMessageHistory(
                        inboxPages = inboxPages,
                        sentPages = sentPages,
                        limit = limit,
                    )
                } else if (cursor != null && fetchedSent is NetworkResult.Success) {
                    extendSentHistoryToPage(inboxPage = inbox.value, sentPages = sentPages)
                }
                val page = mergeInboxAndSentListings(
                    inbox = combineRedditInboxListings(inboxPages),
                    sent = combineRedditInboxListings(sentPages),
                ).toNetworkPage(
                    nowEpochSeconds = clock.epochSeconds(),
                    currentUsername = currentUsername,
                    json = json,
                )
                NetworkResult.Success(
                    value = if (cursor == null) {
                        page.copy(
                            completeSinceEpochSeconds = redditInboxCompleteSince(inboxPages, sentPages),
                            unreadItemIds = (fetchedUnread as? NetworkResult.Success)?.value
                                ?.takeIf { listing -> listing.data.after.isNullOrBlank() }
                                ?.toNetworkPage(
                                    nowEpochSeconds = clock.epochSeconds(),
                                    currentUsername = currentUsername,
                                    json = json,
                                )
                                ?.items
                                ?.mapTo(hashSetOf(), InboxItem::id),
                        )
                    } else {
                        page
                    },
                    metadata = inbox.metadata,
                )
            }
            is NetworkResult.HttpFailure -> sent
            is NetworkResult.TransportFailure -> sent
            is NetworkResult.ProtocolFailure -> sent
        }
    }

    // INBOXSENT01_LATER_PAGES_PAGE_SENT_HISTORY_V1 (OCB-632): a later inbox
    // page was merged only with the newest 100 sent messages, so your own
    // replies in older threads never showed. Sent history is paged back until
    // it reaches the page's oldest message (same page cap as the first page);
    // a failed extra read keeps what was read. Threads already stored are
    // merged on append (INBOX04).
    private suspend fun extendSentHistoryToPage(
        inboxPage: RedditInboxListingDto,
        sentPages: MutableList<RedditInboxListingDto>,
    ) {
        val inboxOldest = listOf(inboxPage).oldestCreatedEpochSeconds()
        while (
            shouldFetchOlderRedditInboxHistoryPage(
                currentOldestEpochSeconds = sentPages.oldestCreatedEpochSeconds(),
                targetOldestEpochSeconds = inboxOldest,
                nextCursor = sentPages.last().data.after?.trim()?.takeIf(String::isNotEmpty),
                pagesLoaded = sentPages.size,
            )
        ) {
            val next = httpClient.getJson(
                request = redditSentJsonRequest(
                    limit = REDDIT_INBOX_MAX_PAGE_SIZE,
                    cursor = sentPages.last().data.after?.trim(),
                ),
                deserializer = RedditInboxListingDto.serializer(),
            )
            if (next !is NetworkResult.Success) break
            sentPages += next.value
        }
    }

    private suspend fun balanceInitialMessageHistory(
        inboxPages: MutableList<RedditInboxListingDto>,
        sentPages: MutableList<RedditInboxListingDto>,
        limit: Int,
    ) {
        while (true) {
            val inboxOldest = inboxPages.oldestCreatedEpochSeconds()
            val sentOldest = sentPages.oldestCreatedEpochSeconds()
            val inboxAfter = inboxPages.last().data.after?.trim()?.takeIf(String::isNotEmpty)
            val sentAfter = sentPages.last().data.after?.trim()?.takeIf(String::isNotEmpty)
            when {
                shouldFetchOlderRedditInboxHistoryPage(
                    currentOldestEpochSeconds = inboxOldest,
                    targetOldestEpochSeconds = sentOldest,
                    nextCursor = inboxAfter,
                    pagesLoaded = inboxPages.size,
                ) -> {
                    val next = httpClient.getJson(
                        request = redditInboxJsonRequest(inboxAfter, limit),
                        deserializer = RedditInboxListingDto.serializer(),
                    )
                    if (next !is NetworkResult.Success) break
                    inboxPages += next.value
                }
                shouldFetchOlderRedditInboxHistoryPage(
                    currentOldestEpochSeconds = sentOldest,
                    targetOldestEpochSeconds = inboxOldest,
                    nextCursor = sentAfter,
                    pagesLoaded = sentPages.size,
                ) -> {
                    val next = httpClient.getJson(
                        request = redditSentJsonRequest(
                            limit = REDDIT_INBOX_MAX_PAGE_SIZE,
                            cursor = sentAfter,
                        ),
                        deserializer = RedditInboxListingDto.serializer(),
                    )
                    if (next !is NetworkResult.Success) break
                    sentPages += next.value
                }
                else -> break
            }
        }
    }
}

internal class RedditInboxTargetNetworkDataSource(
    private val httpClient: SubshellHttpClient,
    private val clock: EpochSecondsClock = SystemEpochSecondsClock,
) : RedditInboxTargetSource {
    override suspend fun post(postId: String): NetworkResult<Post?> = httpClient.getJson(
        request = redditInboxTargetJsonRequest(postId),
        deserializer = redditThreadListing(RedditListingDto.serializer(), REDDIT_THREAD_POST_LISTING),
    ).map { listing ->
        listing.toNetworkPage(clock.epochSeconds()).items.firstOrNull().also { post ->
            // ISSUE281_THREAD_READS_IN_THE_REPORT_V1: Reddit answered, but not
            // with a post this app shows; how many things it sent, of what kind.
            if (post == null) {
                val children = listing.data.children
                RedditReadDiagnostics.report(
                    READ_EVENT_INBOX_TARGET_EMPTY,
                    "children" to children.size,
                    "posts" to children.count { child -> child.kind == "t3" },
                    "untitled" to children.count { child -> child.kind == "t3" && child.data.title.isBlank() },
                )
            }
        }
    }
}

@Serializable
internal data class RedditInboxListingDto(
    val data: RedditInboxListingDataDto = RedditInboxListingDataDto(),
)

@Serializable
internal data class RedditInboxListingDataDto(
    val after: String? = null,
    val children: List<RedditInboxThingDto> = emptyList(),
)

@Serializable
internal data class RedditInboxThingDto(
    val kind: String? = null,
    val data: RedditInboxDataDto = RedditInboxDataDto(),
)

@Serializable
internal data class RedditInboxDataDto(
    val id: String? = null,
    val name: String? = null,
    val author: String? = null,
    val dest: String? = null,
    val subject: String? = null,
    val body: String? = null,
    @SerialName("new") val unread: Boolean = false,
    @SerialName("created_utc") val createdUtc: Double? = null,
    @SerialName("was_comment") val wasComment: Boolean = false,
    @SerialName("link_id") val linkId: String? = null,
    @SerialName("parent_id") val parentId: String? = null,
    @SerialName("first_message_name") val firstMessageName: String? = null,
    @SerialName("link_title") val linkTitle: String? = null,
    @SerialName("subreddit_name_prefixed") val subredditNamePrefixed: String? = null,
    val subreddit: String? = null,
    val context: String? = null,
    val distinguished: String? = null,
    val replies: JsonElement? = null,
    // ISSUE8_INLINE_REDDIT_MEDIA_V1: comment replies can carry media.
    @SerialName("media_metadata") val mediaMetadata: Map<String, RedditMediaMetadataDto> = emptyMap(),
)



internal fun combineRedditInboxListings(
    pages: List<RedditInboxListingDto>,
): RedditInboxListingDto {
    require(pages.isNotEmpty()) { "Reddit inbox page collection cannot be empty" }
    return RedditInboxListingDto(
        data = RedditInboxListingDataDto(
            after = pages.last().data.after,
            children = pages.flatMap { page -> page.data.children },
        ),
    )
}

/**
 * INBOXFAST01_A_REFRESH_IS_COMPLETE_ONLY_SINCE_ITS_OLDER_EDGE_V1: the inbox
 * and sent pages reach back to different times (you send rarely, so the sent
 * page reaches months further). Every item newer than the later of the two
 * edges is on the page; older ones may be missing. A listing read to its end
 * limits nothing (0); an edge without times is unknown (null).
 */
internal fun redditInboxCompleteSince(
    inboxPages: List<RedditInboxListingDto>,
    sentPages: List<RedditInboxListingDto>,
): Long? {
    val edges = listOf(inboxPages, sentPages).map { pages ->
        if (pages.last().data.after.isNullOrBlank()) 0L else pages.oldestCreatedEpochSeconds()
    }
    if (edges.any { edge -> edge == null }) return null
    return edges.maxOf { edge -> edge ?: 0L }
}

internal fun shouldFetchOlderRedditInboxHistoryPage(
    currentOldestEpochSeconds: Long?,
    targetOldestEpochSeconds: Long?,
    nextCursor: String?,
    pagesLoaded: Int,
): Boolean =
    currentOldestEpochSeconds != null &&
        targetOldestEpochSeconds != null &&
        currentOldestEpochSeconds > targetOldestEpochSeconds &&
        !nextCursor.isNullOrBlank() &&
        pagesLoaded < REDDIT_INBOX_HISTORY_BALANCE_MAX_PAGES

private fun List<RedditInboxListingDto>.oldestCreatedEpochSeconds(): Long? =
    asSequence()
        .flatMap { page -> page.data.children.asSequence() }
        .mapNotNull { thing -> thing.data.createdUtc?.toLong()?.coerceAtLeast(0L) }
        .filter { created -> created > 0L }
        .minOrNull()

internal fun mergeInboxAndSentListings(
    inbox: RedditInboxListingDto,
    sent: RedditInboxListingDto,
): RedditInboxListingDto = RedditInboxListingDto(
    data = RedditInboxListingDataDto(
        after = inbox.data.after,
        children = inbox.data.children + sent.data.children,
    ),
)

private data class TimedMessageBubble(
    val bubble: MessageBubble,
    val createdEpochSeconds: Long,
    val unread: Boolean,
)

private data class InboxProjection(
    val item: InboxItem,
    val sortEpochSeconds: Long,
    val messageTimeline: List<TimedMessageBubble> = emptyList(),
)

internal fun RedditInboxListingDto.toNetworkPage(
    nowEpochSeconds: Long,
    currentUsername: String,
    json: Json = SubshellNetworkJson.providerJson,
): NetworkPage<InboxItem> {
    val projected = data.children.mapNotNull { thing ->
        thing.toInboxProjection(
            nowEpochSeconds = nowEpochSeconds,
            currentUsername = currentUsername,
            json = json,
        )
    }
    val merged = projected
        .groupBy { projection -> projection.item.id }
        .values
        .map { group ->
            if (group.size == 1 || group.first().item.kind != ActivityKind.MESSAGE) {
                group.maxBy(InboxProjection::sortEpochSeconds)
            } else {
                mergeMessageThreadProjections(
                    group = group,
                    nowEpochSeconds = nowEpochSeconds,
                )
            }
        }
        .sortedByDescending(InboxProjection::sortEpochSeconds)
    return NetworkPage(
        items = merged.map(InboxProjection::item),
        nextCursor = data.after?.trim()?.takeIf(String::isNotEmpty),
    )
}

private fun RedditInboxThingDto.toInboxProjection(
    nowEpochSeconds: Long,
    currentUsername: String,
    json: Json,
): InboxProjection? {
    val rawId = data.name
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: data.id
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { id ->
                when {
                    kind == REDDIT_COMMENT_KIND -> "t1_$id"
                    kind == REDDIT_MESSAGE_KIND -> "t4_$id"
                    else -> id
                }
            }
        ?: return null
    val body = data.annotatedInboxBody().normalizedInboxBody()
    val author = data.normalizedMessageSender()
    val subject = data.subject.normalizedInboxLabel().takeIf(String::isNotEmpty)
    val commentActivity = kind == REDDIT_COMMENT_KIND || data.wasComment
    val standaloneRedditSystemMessage = kind == REDDIT_MESSAGE_KIND &&
        data.author?.trim()?.equals("reddit", ignoreCase = true) == true &&
        data.subredditNamePrefixed.isNullOrBlank() &&
        data.subreddit.isNullOrBlank() &&
        data.firstMessageName.isNullOrBlank()
    val activityKind = when {
        commentActivity && subject?.contains("mention", ignoreCase = true) == true ->
            ActivityKind.MENTION
        commentActivity && data.parentId?.trim()?.startsWith("t3_") == true ->
            ActivityKind.POST_REPLY
        commentActivity -> ActivityKind.REPLY
        standaloneRedditSystemMessage -> ActivityKind.MOD
        kind == REDDIT_MESSAGE_KIND -> ActivityKind.MESSAGE
        else -> ActivityKind.MOD
    }
    val title = when (activityKind) {
        ActivityKind.REPLY,
        ActivityKind.POST_REPLY,
        ActivityKind.MENTION,
        -> data.linkTitle.normalizedInboxLabel().takeIf(String::isNotEmpty)
            ?: subject
            ?: "Reddit activity"
        ActivityKind.MESSAGE,
        ActivityKind.MOD,
        -> subject ?: "Reddit message"
    }
    val targetPostId = resolvePostId()
    val targetCommentId = resolveCommentId(rawId, commentActivity)
    val createdEpochSeconds = data.createdUtc?.toLong()?.coerceAtLeast(0L) ?: 0L
    val messageTimeline = if (activityKind == ActivityKind.MESSAGE) {
        buildList {
            add(
                data.toTimedMessageBubble(
                    fallbackId = rawId,
                    currentUsername = currentUsername,
                    nowEpochSeconds = nowEpochSeconds,
                ),
            )
            addAll(
                data.replies.flattenReplyBubbles(
                    json = json,
                    currentUsername = currentUsername,
                    nowEpochSeconds = nowEpochSeconds,
                ),
            )
        }
            .distinctBy { message -> message.bubble.id }
            .sortedWith(
                compareBy<TimedMessageBubble> { message -> message.createdEpochSeconds }
                    .thenBy { message -> message.bubble.id },
            )
    } else {
        emptyList()
    }
    val latestMessage = messageTimeline.lastOrNull()
    val threadId = if (activityKind == ActivityKind.MESSAGE) {
        data.firstMessageName
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: rawId
    } else {
        rawId
    }
    val peer = if (activityKind == ActivityKind.MESSAGE) {
        messageTimeline
            .asSequence()
            .map { message -> message.bubble.sender }
            .firstOrNull { sender -> !sender.equals(currentUsername, ignoreCase = true) }
            ?: data.dest?.trim()?.takeIf { destination ->
                destination.isNotEmpty() && !destination.equals(currentUsername, ignoreCase = true)
            }?.let(::normalizedMessageDestination)
            ?: author
    } else {
        author
    }
    val latestEpochSeconds = latestMessage?.createdEpochSeconds ?: createdEpochSeconds
    val preview = if (activityKind == ActivityKind.MESSAGE) {
        latestMessage?.bubble?.body.orEmpty().toInboxPreview().ifBlank {
            body.toInboxPreview().ifBlank { subject.orEmpty() }
        }
    } else {
        body.toInboxPreview().ifBlank { subject.orEmpty() }
    }
    return InboxProjection(
        item = InboxItem(
            id = threadId,
            kind = activityKind,
            title = title,
            sender = peer,
            age = redditRelativeAgeLabel(nowEpochSeconds, latestEpochSeconds.takeIf { it > 0L }),
            preview = preview,
            unread = if (activityKind == ActivityKind.MESSAGE) {
                data.unread || messageTimeline.any(TimedMessageBubble::unread)
            } else {
                data.unread
            },
            targetPostId = targetPostId,
            targetCommentId = targetCommentId,
            conversation = messageTimeline.map { message -> message.bubble },
            createdEpochSeconds = latestEpochSeconds.takeIf { it > 0L },
            subreddit = data.normalizedInboxSubreddit(),
            postTitle = data.linkTitle.normalizedInboxLabel().takeIf(String::isNotEmpty),
            contextPermalink = data.context?.trim()?.takeIf(String::isNotEmpty),
        ),
        sortEpochSeconds = latestEpochSeconds,
        messageTimeline = messageTimeline,
    )
}

private fun mergeMessageThreadProjections(
    group: List<InboxProjection>,
    nowEpochSeconds: Long,
): InboxProjection {
    val latestProjection = group.maxBy(InboxProjection::sortEpochSeconds)
    val timeline = group
        .asSequence()
        .flatMap { projection -> projection.messageTimeline.asSequence() }
        .distinctBy { message -> message.bubble.id }
        .sortedWith(
            compareBy<TimedMessageBubble> { message -> message.createdEpochSeconds }
                .thenBy { message -> message.bubble.id },
        )
        .toList()
    val latestMessage = timeline.lastOrNull()
    val latestEpochSeconds = latestMessage?.createdEpochSeconds
        ?: latestProjection.sortEpochSeconds
    val peer = timeline
        .firstOrNull { message -> !message.bubble.outgoing }
        ?.bubble
        ?.sender
        ?: latestProjection.item.sender
    return latestProjection.copy(
        item = latestProjection.item.copy(
            sender = peer,
            age = redditRelativeAgeLabel(
                nowEpochSeconds,
                latestEpochSeconds.takeIf { it > 0L },
            ),
            preview = latestMessage?.bubble?.body.orEmpty().toInboxPreview().ifBlank {
                latestProjection.item.preview
            },
            unread = group.any { projection -> projection.item.unread } ||
                timeline.any(TimedMessageBubble::unread),
            conversation = timeline.map { message -> message.bubble },
        ),
        sortEpochSeconds = latestEpochSeconds,
        messageTimeline = timeline,
    )
}

private fun RedditInboxThingDto.resolvePostId(): String? {
    val direct = data.linkId
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.removePrefix("t3_")
        ?.takeIf(String::isNotEmpty)
    if (direct != null) return direct
    val context = data.context
        ?.let(REDDIT_COMMENT_CONTEXT_REGEX::find)
        ?.groupValues
        ?.getOrNull(1)
        ?.takeIf(String::isNotBlank)
    if (context != null) return context
    return data.parentId
        ?.trim()
        ?.takeIf { parent -> parent.startsWith("t3_") }
        ?.removePrefix("t3_")
        ?.takeIf(String::isNotEmpty)
}

private fun RedditInboxThingDto.resolveCommentId(
    rawId: String,
    commentActivity: Boolean,
): String? {
    if (!commentActivity) return null
    rawId.trim()
        .takeIf { id -> id.startsWith("t1_") && id.length > 3 }
        ?.let { return it.lowercase() }
    data.context
        ?.let(REDDIT_COMMENT_CONTEXT_REGEX::find)
        ?.groupValues
        ?.getOrNull(2)
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.let { return "t1_${it.removePrefix("t1_").lowercase()}" }
    return data.parentId
        ?.trim()
        ?.takeIf { parent -> parent.startsWith("t1_") && parent.length > 3 }
        ?.lowercase()
}

private fun RedditInboxDataDto.normalizedInboxSubreddit(): String? =
    subredditNamePrefixed
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: subreddit
            ?.trim()
            ?.removePrefix("r/")
            ?.takeIf(String::isNotEmpty)
            ?.let { name -> "r/$name" }


// LOW_NET_MODMAIL_DESTINATION_IS_A_COMMUNITY_V1 (OCB-549): a message to a
// community's moderators has dest "#name"; a thread with only sent messages
// shows it as r/name, like a sender "#name".
internal fun normalizedMessageDestination(destination: String): String =
    if (destination.startsWith("#")) {
        destination.removePrefix("#").takeIf(String::isNotEmpty)?.let { name -> "r/$name" } ?: destination
    } else {
        destination
    }

private fun RedditInboxDataDto.normalizedMessageSender(): String {
    val community = subredditNamePrefixed
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: subreddit
            ?.trim()
            ?.removePrefix("r/")
            ?.takeIf(String::isNotEmpty)
            ?.let { name -> "r/$name" }
    val rawAuthor = author?.trim()?.takeIf(String::isNotEmpty)
    return when {
        rawAuthor?.startsWith("#") == true ->
            community ?: rawAuthor.removePrefix("#")
                .takeIf(String::isNotEmpty)
                ?.let { name -> "r/$name" }
                ?: "reddit"
        rawAuthor != null -> rawAuthor
        community != null -> community
        else -> "reddit"
    }
}

private fun RedditInboxDataDto.toTimedMessageBubble(
    fallbackId: String,
    currentUsername: String,
    nowEpochSeconds: Long,
): TimedMessageBubble {
    val createdEpochSeconds = createdUtc?.toLong()?.coerceAtLeast(0L) ?: 0L
    return TimedMessageBubble(
        bubble = MessageBubble(
            id = name?.trim()?.takeIf(String::isNotEmpty) ?: fallbackId,
            sender = normalizedMessageSender(),
            age = redditRelativeAgeLabel(
                nowEpochSeconds,
                createdEpochSeconds.takeIf { it > 0L },
            ),
            body = annotatedInboxBody().normalizedInboxBody().ifBlank {
                subject.normalizedInboxLabel()
            },
            outgoing = author?.equals(currentUsername, ignoreCase = true) == true,
            createdEpochSeconds = createdEpochSeconds.takeIf { it > 0L },
        ),
        createdEpochSeconds = createdEpochSeconds,
        unread = unread,
    )
}

private fun JsonElement?.flattenReplyBubbles(
    json: Json,
    currentUsername: String,
    nowEpochSeconds: Long,
    depth: Int = 0,
): List<TimedMessageBubble> {
    if (depth >= REDDIT_INBOX_MAX_REPLY_DEPTH) return emptyList()
    val root = this as? JsonObject ?: return emptyList()
    val listing = runCatching {
        json.decodeFromJsonElement(RedditInboxListingDto.serializer(), root)
    }.getOrNull() ?: return emptyList()
    return listing.data.children
        .asSequence()
        .take(REDDIT_INBOX_MAX_REPLIES)
        .flatMap { child ->
            sequence {
                val fallbackId = child.data.id
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?.let { id -> "${child.kind ?: REDDIT_MESSAGE_KIND}_$id" }
                    ?: "reply-${child.hashCode()}"
                yield(
                    child.data.toTimedMessageBubble(
                        fallbackId = fallbackId,
                        currentUsername = currentUsername,
                        nowEpochSeconds = nowEpochSeconds,
                    ),
                )
                yieldAll(
                    child.data.replies.flattenReplyBubbles(
                        json = json,
                        currentUsername = currentUsername,
                        nowEpochSeconds = nowEpochSeconds,
                        depth = depth + 1,
                    ),
                )
            }
        }
        .take(REDDIT_INBOX_MAX_REPLIES)
        .toList()
}

internal fun redditInboxJsonRequest(
    cursor: String?,
    limit: Int,
): JsonGetRequest {
    require(limit in 1..REDDIT_INBOX_MAX_PAGE_SIZE) {
        "Reddit inbox limit must be between 1 and $REDDIT_INBOX_MAX_PAGE_SIZE"
    }
    return JsonGetRequest(
        pathSegments = listOf("message", "inbox"),
        queryParameters = buildList {
            add("raw_json" to "1")
            add("mark" to "false")
            add("max_replies" to REDDIT_INBOX_MAX_REPLIES.toString())
            add("limit" to limit.toString())
            cursor
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.let { after -> add("after" to after) }
        },
    )
}


internal fun redditSentJsonRequest(
    limit: Int,
    cursor: String? = null,
): JsonGetRequest {
    require(limit in 1..REDDIT_INBOX_MAX_PAGE_SIZE) {
        "Reddit sent-message limit must be between 1 and $REDDIT_INBOX_MAX_PAGE_SIZE"
    }
    return JsonGetRequest(
        pathSegments = listOf("message", "sent"),
        queryParameters = buildList {
            add("raw_json" to "1")
            add("mark" to "false")
            add("max_replies" to "0")
            add("limit" to limit.toString())
            cursor
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.let { after -> add("after" to after) }
        },
    )
}

internal fun redditUnreadJsonRequest(limit: Int): JsonGetRequest {
    require(limit in 1..REDDIT_INBOX_MAX_PAGE_SIZE) {
        "Reddit unread limit must be between 1 and $REDDIT_INBOX_MAX_PAGE_SIZE"
    }
    return JsonGetRequest(
        pathSegments = listOf("message", "unread"),
        queryParameters = listOf(
            "raw_json" to "1",
            // Reading the list must not mark it read.
            "mark" to "false",
            "max_replies" to "0",
            "limit" to limit.toString(),
        ),
    )
}

// ISSUE281_READS_AVOID_API_INFO_V1: the post comes from its thread.
internal fun redditInboxTargetJsonRequest(postId: String): JsonGetRequest = redditPostReadRequest(postId)

private fun String?.normalizedInboxBody(): String = orEmpty()
    .replace("\r\n", "\n")
    .trim()
    .take(REDDIT_INBOX_MAX_BODY_LENGTH)

private fun String?.normalizedInboxLabel(): String = orEmpty()
    .trim()
    .replace(Regex("\\s+"), " ")
    .take(REDDIT_INBOX_MAX_LABEL_LENGTH)

private fun RedditInboxDataDto.annotatedInboxBody(): String =
    body.orEmpty().withRedditMediaAnnotations(mediaMetadata, appendUnplaced = true)

private fun String.toInboxPreview(): String = redditReadableText()
    .replace(Regex("\\s+"), " ")
    .trim()
    .take(REDDIT_INBOX_MAX_PREVIEW_LENGTH)

internal const val REDDIT_INBOX_MAX_PAGE_SIZE = 100
internal const val REDDIT_INBOX_MAX_REPLIES = 25
internal const val REDDIT_INBOX_HISTORY_BALANCE_MAX_PAGES = 4
private const val REDDIT_INBOX_MAX_REPLY_DEPTH = 8
private const val REDDIT_INBOX_MAX_BODY_LENGTH = 20_000
private const val REDDIT_INBOX_MAX_LABEL_LENGTH = 500
private const val REDDIT_INBOX_MAX_PREVIEW_LENGTH = 500
private const val REDDIT_COMMENT_KIND = "t1"
private const val REDDIT_MESSAGE_KIND = "t4"
private val REDDIT_COMMENT_CONTEXT_REGEX = Regex(
    pattern = "(?:^|/)comments/([a-z0-9]+)/[^/?#]*(?:/([a-z0-9]+))?",
    option = RegexOption.IGNORE_CASE,
)
