package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.network.FormPostRequest
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem

internal const val REDDIT_INBOX_READ_MAX_IDS = 100
// SUBSHELL_INBOXM3A2R2C3_REDDIT_25_ID_READ_BATCHING_V1
internal const val REDDIT_INBOX_READ_REQUEST_MAX_IDS = 25

internal interface RedditInboxReadMutationSource {
    suspend fun markRead(fullnames: List<String>): NetworkResult<Unit>
}

internal class RedditInboxReadMutationNetworkDataSource(
    private val httpClient: SubshellHttpClient,
) : RedditInboxReadMutationSource {
    override suspend fun markRead(fullnames: List<String>): NetworkResult<Unit> =
        httpClient.postFormStatus(redditMarkInboxReadRequest(fullnames))
}

internal fun redditMarkInboxReadRequest(fullnames: List<String>): FormPostRequest =
    FormPostRequest(
        pathSegments = listOf("api", "read_message"),
        fields = listOf(
            "id" to normalizeRedditInboxReadRequestFullnames(fullnames).joinToString(","),
        ),
    )

// DATALAYER04: only incoming messages can be unread, and a long thread keeps
// its newest ones (the conversation is oldest first) within Reddit's bound.
// Before, every message of a merged thread was a target, so a thread with
// more than REDDIT_INBOX_READ_MAX_IDS messages could never be marked read,
// and it made mark-all fail for every other item too.
internal fun InboxItem.redditReadTargetFullnames(): List<String> {
    val candidates = if (kind == ActivityKind.MESSAGE) {
        buildList {
            add(id)
            conversation
                .filterNot { message -> message.outgoing }
                .map { message -> message.id }
                .filter(REDDIT_INBOX_READ_FULLNAME_REGEX::matches)
                .distinct()
                .filterNot { messageId -> messageId == id }
                .takeLast(REDDIT_INBOX_READ_MAX_IDS - 1)
                .forEach(::add)
        }
    } else {
        listOf(id)
    }
    return normalizeInboxReadFullnames(candidates)
}

internal fun redditReadTargetFullnameRequestBatches(
    fullnames: List<String>,
): List<List<String>> =
    normalizeInboxReadFullnames(fullnames)
        .chunked(REDDIT_INBOX_READ_REQUEST_MAX_IDS)

internal fun normalizeRedditInboxReadRequestFullnames(
    fullnames: List<String>,
): List<String> {
    val normalized = normalizeInboxReadFullnames(fullnames)
    require(normalized.size <= REDDIT_INBOX_READ_REQUEST_MAX_IDS) {
        "Reddit inbox read request exceeds the 25-item request bound"
    }
    return normalized
}

internal fun normalizeInboxReadFullnames(fullnames: List<String>): List<String> {
    val normalized = fullnames
        .asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .filter(REDDIT_INBOX_READ_FULLNAME_REGEX::matches)
        .distinct()
        .take(REDDIT_INBOX_READ_MAX_IDS + 1)
        .toList()
    require(normalized.isNotEmpty()) { "Inbox read mutation requires at least one fullname" }
    require(normalized.size <= REDDIT_INBOX_READ_MAX_IDS) {
        "Inbox read mutation exceeds the bounded fullname count"
    }
    return normalized
}

private val REDDIT_INBOX_READ_FULLNAME_REGEX = Regex("^t[14]_[A-Za-z0-9]+$")
