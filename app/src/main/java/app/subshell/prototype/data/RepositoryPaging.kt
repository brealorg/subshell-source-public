package app.subshell.prototype.data

import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.error.FailureDetails
import java.security.MessageDigest

@JvmInline
value class RepositoryQueryKey(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "Repository query key cannot be blank" }
        require(value.length <= MAX_LENGTH) { "Repository query key is too long" }
    }

    override fun toString(): String = value

    companion object {
        const val MAX_LENGTH = 512
        val Default = RepositoryQueryKey("default")
    }
}

internal fun RepositoryQueryKey.scopedToAccount(
    stableId: String,
    source: AccountSessionSource,
): RepositoryQueryKey {
    val normalizedAccountId = stableId
        .trim()
        .replace("%", "%25")
        .replace("|", "%7c")
    require(normalizedAccountId.isNotEmpty()) { "Account stable ID cannot be blank" }
    val sourceToken = when (source) {
        AccountSessionSource.LOCAL_FIXTURE -> "local_fixture"
        AccountSessionSource.AUTHENTICATED -> "authenticated"
    }
    return RepositoryQueryKey("account|$sourceToken|$normalizedAccountId|$value")
}

enum class RedditFeedSort {
    BEST,
    HOT,
    NEW,
    RISING,
    TOP,
    CONTROVERSIAL,
}

enum class RedditMainFeed(
    val queryToken: String,
    val listingCommunityName: String?,
) {
    HOME(queryToken = "home", listingCommunityName = null),
    POPULAR(queryToken = "popular", listingCommunityName = "popular"),
    ALL(queryToken = "all", listingCommunityName = "all"),
}

enum class RedditListingTimePeriod(
    val apiValue: String,
    val label: String,
) {
    HOUR(apiValue = "hour", label = "Past hour"),
    DAY(apiValue = "day", label = "Today"),
    WEEK(apiValue = "week", label = "This week"),
    MONTH(apiValue = "month", label = "This month"),
    YEAR(apiValue = "year", label = "This year"),
    ALL(apiValue = "all", label = "All time"),
}

internal val RedditFeedSort.supportsTimePeriod: Boolean
    get() = this == RedditFeedSort.TOP ||
        this == RedditFeedSort.CONTROVERSIAL

internal fun RedditFeedSort.effectiveTimePeriod(
    timePeriod: RedditListingTimePeriod,
): RedditListingTimePeriod = if (supportsTimePeriod) {
    timePeriod
} else {
    RedditListingTimePeriod.ALL
}

enum class RedditCommentSort(
    val apiValue: String,
    val label: String,
) {
    BEST(apiValue = "confidence", label = "Best"),
    TOP(apiValue = "top", label = "Top"),
    NEW(apiValue = "new", label = "New"),
    CONTROVERSIAL(apiValue = "controversial", label = "Controversial"),
    OLD(apiValue = "old", label = "Old"),
}

internal fun redditSuggestedCommentSort(rawValue: String?): RedditCommentSort? =
    when (rawValue?.trim()?.lowercase()) {
        "confidence" -> RedditCommentSort.BEST
        "top" -> RedditCommentSort.TOP
        "new" -> RedditCommentSort.NEW
        "controversial" -> RedditCommentSort.CONTROVERSIAL
        "old" -> RedditCommentSort.OLD
        else -> null
    }

internal fun retainRedditSuggestedCommentSort(
    previous: RedditCommentSort?,
    refreshed: RedditCommentSort?,
): RedditCommentSort? = refreshed ?: previous

internal const val COMMENTS02_MORE_BATCH_SIZE = 25

internal fun liveCommunityPostFeedQueryKey(
    accountStableId: String,
    communityName: String,
    sort: RedditFeedSort,
    timePeriod: RedditListingTimePeriod = RedditListingTimePeriod.ALL,
): RepositoryQueryKey = RepositoryQueryKey(
    buildString {
        append("live04|community|")
        append(normalizeRedditCommunityName(communityName))
        append('|')
        append(sort.name.lowercase())
        if (sort.supportsTimePeriod) {
            append('|')
            append(sort.effectiveTimePeriod(timePeriod).apiValue)
        }
    },
).scopedToAccount(
    stableId = accountStableId,
    source = AccountSessionSource.AUTHENTICATED,
)

internal fun liveHomePostFeedQueryKey(
    accountStableId: String,
    sort: RedditFeedSort,
    timePeriod: RedditListingTimePeriod = RedditListingTimePeriod.ALL,
): RepositoryQueryKey = liveMainPostFeedQueryKey(
    accountStableId = accountStableId,
    feed = RedditMainFeed.HOME,
    sort = sort,
    timePeriod = timePeriod,
)

internal fun liveMainPostFeedQueryKey(
    accountStableId: String,
    feed: RedditMainFeed,
    sort: RedditFeedSort,
    timePeriod: RedditListingTimePeriod = RedditListingTimePeriod.ALL,
): RepositoryQueryKey = RepositoryQueryKey(
    buildString {
        append("main|")
        append(feed.queryToken)
        append('|')
        append(sort.name.lowercase())
        if (sort.supportsTimePeriod) {
            append('|')
            append(sort.effectiveTimePeriod(timePeriod).apiValue)
        }
    },
).scopedToAccount(
    stableId = accountStableId,
    source = AccountSessionSource.AUTHENTICATED,
)

internal fun liveSearchPostFeedQueryKey(
    accountStableId: String,
    query: String,
    communityName: String? = null,
): RepositoryQueryKey = RepositoryQueryKey(
    "live05|search|" +
        (communityName?.let(::normalizeRedditCommunityName) ?: "all") +
        "|${liveSearchQueryToken(normalizeRedditSearchQuery(query).lowercase())}",
).scopedToAccount(
    stableId = accountStableId,
    source = AccountSessionSource.AUTHENTICATED,
)

internal fun redditUserSubmittedPostFeedQueryKey(
    accountStableId: String,
    username: String,
): RepositoryQueryKey = RepositoryQueryKey(
    "profile01a1|user|" +
        encodeRepositoryQueryToken(normalizeRedditUsername(username)) +
        "|submitted",
).scopedToAccount(
    stableId = accountStableId,
    source = AccountSessionSource.AUTHENTICATED,
)

internal fun redditAccountHistoryQueryKey(
    accountStableId: String,
    username: String,
    kind: RedditAccountHistoryKind,
): RepositoryQueryKey = RepositoryQueryKey(
    "profile01a6|history|" +
        encodeRepositoryQueryToken(normalizeRedditUsername(username)) +
        "|${kind.queryToken}",
).scopedToAccount(
    stableId = accountStableId,
    source = AccountSessionSource.AUTHENTICATED,
)

internal fun redditMixedSavedHistoryQueryKey(
    accountStableId: String,
    username: String,
): RepositoryQueryKey = RepositoryQueryKey(
    "profile01a6|history|" +
        encodeRepositoryQueryToken(normalizeRedditUsername(username)) +
        "|saved-mixed",
).scopedToAccount(
    stableId = accountStableId,
    source = AccountSessionSource.AUTHENTICATED,
)

internal fun redditAccountHistoryPostQueryKey(
    accountStableId: String,
    username: String,
    kind: RedditAccountHistoryKind,
): RepositoryQueryKey {
    require(kind.isPostHistory) {
        "Post paging query identity requires a Reddit post history kind"
    }
    return redditAccountHistoryQueryKey(
        accountStableId = accountStableId,
        username = username,
        kind = kind,
    )
}

// ISSUE46_REDDIT_USERNAME_LENGTH_V1: the same rule, without throwing.
internal fun isValidRedditUsername(username: String): Boolean =
    runCatching { normalizeRedditUsername(username) }.isSuccess

internal fun normalizeRedditUsername(username: String): String {
    val trimmed = username.trim()
    val withoutPrefix = when {
        trimmed.startsWith("/u/", ignoreCase = true) -> trimmed.drop(3)
        trimmed.startsWith("u/", ignoreCase = true) -> trimmed.drop(2)
        else -> trimmed
    }
    require(withoutPrefix.length in 3..20) {
        "Reddit username length is outside the supported range"
    }
    require(withoutPrefix.all { character ->
        character.isLetterOrDigit() || character == '_' || character == '-'
    }) {
        "Reddit username contains unsupported characters"
    }
    return withoutPrefix.lowercase()
}

internal fun normalizeRedditSearchQuery(query: String): String {
    val normalized = query.trim().replace(Regex("\\s+"), " ")
    require(normalized.length in REDDIT_SEARCH_QUERY_MIN_LENGTH..REDDIT_SEARCH_QUERY_MAX_LENGTH) {
        "Reddit search query length is outside the supported range"
    }
    return normalized
}

internal fun normalizeRedditCommunityName(communityName: String): String {
    val trimmed = communityName.trim()
    val withoutPrefix = when {
        trimmed.startsWith("/r/", ignoreCase = true) -> trimmed.drop(3)
        trimmed.startsWith("r/", ignoreCase = true) -> trimmed.drop(2)
        else -> trimmed
    }
    require(withoutPrefix.length in 1..REDDIT_COMMUNITY_NAME_MAX_LENGTH) {
        "Reddit community name length is outside the supported range"
    }
    require(withoutPrefix.all { character ->
        character.isLetterOrDigit() || character == '_'
    }) {
        "Reddit community name contains unsupported characters"
    }
    return withoutPrefix.lowercase()
}

internal val RepositoryQueryKey.isAuthenticatedAccountScoped: Boolean
    get() = value.startsWith(AUTHENTICATED_ACCOUNT_QUERY_PREFIX)

private const val AUTHENTICATED_ACCOUNT_QUERY_PREFIX = "account|authenticated|"
private const val REDDIT_COMMUNITY_NAME_MAX_LENGTH = 100
internal const val REDDIT_SEARCH_QUERY_MIN_LENGTH = 2
internal const val REDDIT_SEARCH_QUERY_MAX_LENGTH = 200

private fun encodeRepositoryQueryToken(value: String): String = value
    .replace("%", "%25")
    .replace("|", "%7c")

// LOWCRASH01_LONG_SEARCH_KEY_HASHED_V1 (OCB-462): escaping triples every '%'
// and '|', so a pasted 200-character query of them made a key past
// RepositoryQueryKey.MAX_LENGTH and SearchScreen's composition threw. A token
// longer than this is a SHA-256 of the query instead; shorter keys keep their
// readable form, so existing cached searches are unchanged.
internal const val LIVE_SEARCH_QUERY_TOKEN_MAX_LENGTH = 240

private fun liveSearchQueryToken(normalizedQuery: String): String {
    val encoded = encodeRepositoryQueryToken(normalizedQuery)
    if (encoded.length <= LIVE_SEARCH_QUERY_TOKEN_MAX_LENGTH) return encoded
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(normalizedQuery.toByteArray(Charsets.UTF_8))
    return "sha256:" + digest.joinToString("") { byte -> "%02x".format(byte) }
}

enum class RepositoryLoadOperation {
    INITIAL,
    REFRESH,
    APPEND,
}

sealed interface RepositoryLoadState {
    data object Idle : RepositoryLoadState
    data object Loading : RepositoryLoadState

    data class Offline(
        val message: String = "Offline",
        val failureDetails: FailureDetails? = null,
    ) : RepositoryLoadState

    data class Error(
        val message: String,
        val failureDetails: FailureDetails? = null,
    ) : RepositoryLoadState
}

data class RepositoryPagingSnapshot<T>(
    val queryKey: RepositoryQueryKey,
    val items: List<T> = emptyList(),
    val nextCursor: String? = null,
    val initialLoadState: RepositoryLoadState = RepositoryLoadState.Idle,
    val refreshLoadState: RepositoryLoadState = RepositoryLoadState.Idle,
    val appendLoadState: RepositoryLoadState = RepositoryLoadState.Idle,
    val generation: Int = 0,
    val stale: Boolean = false,
    val lastFailedOperation: RepositoryLoadOperation? = null,
) {
    init {
        require(generation >= 0) { "Paging generation cannot be negative" }
    }

    val refreshing: Boolean
        get() = refreshLoadState == RepositoryLoadState.Loading

    val appending: Boolean
        get() = appendLoadState == RepositoryLoadState.Loading

    val initialLoading: Boolean
        get() = items.isEmpty() && initialLoadState == RepositoryLoadState.Loading

    val endReached: Boolean
        get() = nextCursor == null

    // PAGING01_FAILED_REFRESH_KEEPS_APPEND_V1 (#203): a refresh that failed
    // (offline, error) keeps the items and their cursor, so the list can still
    // load more; only a refresh in flight blocks it.
    val canAppend: Boolean
        get() = items.isNotEmpty() &&
            nextCursor != null &&
            initialLoadState != RepositoryLoadState.Loading &&
            refreshLoadState != RepositoryLoadState.Loading &&
            appendLoadState != RepositoryLoadState.Loading

    val hasFailure: Boolean
        get() = lastFailedOperation != null

    companion object {
        fun <T> initial(queryKey: RepositoryQueryKey): RepositoryPagingSnapshot<T> =
            RepositoryPagingSnapshot(
                queryKey = queryKey,
                initialLoadState = RepositoryLoadState.Loading,
            )
    }
}

/**
 * Synchronous paging state machine used by the local fake backend.
 *
 * The callback is invoked for both the loading transition and the terminal result. A future
 * asynchronous repository can keep the same state contract while moving page execution to a
 * coroutine-backed owner.
 */
class RepositoryPager<T>(
    val queryKey: RepositoryQueryKey,
    private val pageSize: Int = RepositoryPageRequest.DEFAULT_LIMIT,
    private val stableId: (T) -> String,
    private val loadPage: (RepositoryPageRequest) -> RepositoryResult<List<T>>,
    private val onSnapshot: (RepositoryPagingSnapshot<T>) -> Unit = {},
) {
    init {
        require(pageSize in 1..RepositoryPageRequest.MAX_LIMIT) {
            "Repository pager page size must be between 1 and ${RepositoryPageRequest.MAX_LIMIT}"
        }
    }

    var snapshot: RepositoryPagingSnapshot<T> =
        RepositoryPagingSnapshot(queryKey = queryKey)
        private set

    fun loadInitial(): RepositoryPagingSnapshot<T> =
        perform(RepositoryLoadOperation.INITIAL)

    fun refresh(): RepositoryPagingSnapshot<T> =
        perform(RepositoryLoadOperation.REFRESH)

    fun append(): RepositoryPagingSnapshot<T> {
        if (snapshot.nextCursor == null || snapshot.appendLoadState == RepositoryLoadState.Loading) {
            return snapshot
        }
        return perform(RepositoryLoadOperation.APPEND)
    }

    fun retry(): RepositoryPagingSnapshot<T> =
        snapshot.lastFailedOperation
            ?.let(::perform)
            ?: snapshot

    internal fun synchronizeAfterRemotePageWrite(): RepositoryPagingSnapshot<T> {
        val previous = snapshot
        val targetItemCount = snapshot.items.size + pageSize
        val replacement = RepositoryPager(
            queryKey = queryKey,
            pageSize = pageSize,
            stableId = stableId,
            loadPage = loadPage,
        )

        replacement.loadInitial()
        // PERF01_ACCOUNT_FEED_SYNC_SINGLE_READ_V1 (#69): an account-scoped feed
        // loads its whole cached snapshot whatever the cursor
        // (PersistentSubshellRepository.posts), so an append here only decoded
        // the whole feed a second time on the main thread and never grew it.
        while (
            !queryKey.isAuthenticatedAccountScoped &&
            replacement.snapshot.items.size < targetItemCount &&
            replacement.snapshot.canAppend
        ) {
            val beforeCount = replacement.snapshot.items.size
            replacement.append()
            if (replacement.snapshot.items.size <= beforeCount) break
        }

        val candidate = replacement.snapshot
        if (
            candidate.hasFailure &&
            previous.items.isNotEmpty() &&
            candidate.items.size < previous.items.size
        ) {
            return snapshot
        }

        update(candidate)
        return snapshot
    }

    private fun perform(operation: RepositoryLoadOperation): RepositoryPagingSnapshot<T> {
        if (isLoading(operation)) return snapshot
        if (operation == RepositoryLoadOperation.APPEND && snapshot.nextCursor == null) {
            return snapshot
        }

        val requestCursor = when (operation) {
            RepositoryLoadOperation.INITIAL,
            RepositoryLoadOperation.REFRESH,
            -> null
            RepositoryLoadOperation.APPEND -> snapshot.nextCursor
        }
        val nextGeneration = when (operation) {
            RepositoryLoadOperation.REFRESH -> snapshot.generation + 1
            else -> snapshot.generation
        }

        update(
            snapshot
                .withLoadState(operation, RepositoryLoadState.Loading)
                .copy(
                    generation = nextGeneration,
                    lastFailedOperation = null,
                ),
        )

        val result = loadPage(
            RepositoryPageRequest(
                cursor = requestCursor,
                limit = pageSize,
                queryKey = queryKey,
                operation = operation,
            ),
        )
        update(applyResult(operation, result))
        return snapshot
    }

    private fun applyResult(
        operation: RepositoryLoadOperation,
        result: RepositoryResult<List<T>>,
    ): RepositoryPagingSnapshot<T> = when (result) {
        RepositoryResult.Loading -> snapshot.withLoadState(
            operation,
            RepositoryLoadState.Loading,
        )

        is RepositoryResult.Content -> {
            val mergedItems = when (operation) {
                RepositoryLoadOperation.APPEND -> mergeStable(snapshot.items, result.value)
                RepositoryLoadOperation.INITIAL,
                RepositoryLoadOperation.REFRESH,
                -> mergeStable(emptyList(), result.value)
            }
            snapshot
                .withLoadState(operation, RepositoryLoadState.Idle)
                .copy(
                    items = mergedItems,
                    nextCursor = result.nextCursor,
                    initialLoadState = when (operation) {
                        RepositoryLoadOperation.INITIAL,
                        RepositoryLoadOperation.REFRESH,
                        -> RepositoryLoadState.Idle
                        RepositoryLoadOperation.APPEND -> snapshot.initialLoadState
                    },
                    // PAGING01_FAILED_REFRESH_KEEPS_APPEND_V1: a page that loaded
                    // settles an earlier refresh failure (its Retry would do nothing).
                    refreshLoadState = settledRefreshLoadState(operation),
                    generation = result.generation ?: snapshot.generation,
                    stale = result.stale,
                    lastFailedOperation = null,
                )
        }

        is RepositoryResult.Empty -> {
            val items = when (operation) {
                RepositoryLoadOperation.APPEND -> snapshot.items
                RepositoryLoadOperation.INITIAL,
                RepositoryLoadOperation.REFRESH,
                -> emptyList()
            }
            snapshot
                .withLoadState(operation, RepositoryLoadState.Idle)
                .copy(
                    items = items,
                    nextCursor = null,
                    initialLoadState = when (operation) {
                        RepositoryLoadOperation.INITIAL,
                        RepositoryLoadOperation.REFRESH,
                        -> RepositoryLoadState.Idle
                        RepositoryLoadOperation.APPEND -> snapshot.initialLoadState
                    },
                    // PAGING01_FAILED_REFRESH_KEEPS_APPEND_V1: a page that loaded
                    // settles an earlier refresh failure (its Retry would do nothing).
                    refreshLoadState = settledRefreshLoadState(operation),
                    stale = false,
                    lastFailedOperation = null,
                )
        }

        is RepositoryResult.Offline -> applyFailure(
            operation = operation,
            cached = result.cached,
            nextCursor = result.nextCursor,
            generation = result.generation,
            state = RepositoryLoadState.Offline(result.message, result.failureDetails),
        )

        is RepositoryResult.Error -> applyFailure(
            operation = operation,
            cached = result.cached,
            nextCursor = result.nextCursor,
            generation = result.generation,
            state = RepositoryLoadState.Error(result.message, result.failureDetails),
        )
    }

    private fun settledRefreshLoadState(operation: RepositoryLoadOperation): RepositoryLoadState =
        when {
            operation == RepositoryLoadOperation.REFRESH -> RepositoryLoadState.Idle
            snapshot.refreshLoadState == RepositoryLoadState.Loading -> RepositoryLoadState.Loading
            else -> RepositoryLoadState.Idle
        }

    private fun applyFailure(
        operation: RepositoryLoadOperation,
        cached: List<T>?,
        nextCursor: String?,
        generation: Int?,
        state: RepositoryLoadState,
    ): RepositoryPagingSnapshot<T> {
        val cachedItems = cached.orEmpty()
        val items = when (operation) {
            RepositoryLoadOperation.INITIAL -> {
                if (cachedItems.isEmpty()) snapshot.items else mergeStable(emptyList(), cachedItems)
            }
            RepositoryLoadOperation.REFRESH -> {
                if (snapshot.items.isEmpty() && cachedItems.isNotEmpty()) {
                    mergeStable(emptyList(), cachedItems)
                } else {
                    snapshot.items
                }
            }
            RepositoryLoadOperation.APPEND -> snapshot.items
        }
        val resolvedCursor = when {
            operation == RepositoryLoadOperation.APPEND -> snapshot.nextCursor
            operation == RepositoryLoadOperation.REFRESH && snapshot.items.isNotEmpty() ->
                snapshot.nextCursor
            nextCursor != null -> nextCursor
            snapshot.items.isNotEmpty() -> snapshot.nextCursor
            else -> null
        }
        return snapshot
            .withLoadState(operation, state)
            .copy(
                items = items,
                nextCursor = resolvedCursor,
                generation = generation ?: snapshot.generation,
                stale = items.isNotEmpty(),
                lastFailedOperation = operation,
            )
    }

    private fun isLoading(operation: RepositoryLoadOperation): Boolean =
        snapshot.loadState(operation) == RepositoryLoadState.Loading

    private fun update(value: RepositoryPagingSnapshot<T>) {
        snapshot = value
        onSnapshot(value)
    }

    private fun mergeStable(
        existing: List<T>,
        incoming: List<T>,
    ): List<T> {
        val merged = linkedMapOf<String, T>()
        existing.forEach { item ->
            merged.putIfAbsent(requireStableId(item), item)
        }
        incoming.forEach { item ->
            merged.putIfAbsent(requireStableId(item), item)
        }
        return merged.values.toList()
    }

    private fun requireStableId(item: T): String =
        stableId(item)
            .trim()
            .also { id -> require(id.isNotEmpty()) { "Repository item id cannot be blank" } }
}

private fun <T> RepositoryPagingSnapshot<T>.loadState(
    operation: RepositoryLoadOperation,
): RepositoryLoadState = when (operation) {
    RepositoryLoadOperation.INITIAL -> initialLoadState
    RepositoryLoadOperation.REFRESH -> refreshLoadState
    RepositoryLoadOperation.APPEND -> appendLoadState
}

private fun <T> RepositoryPagingSnapshot<T>.withLoadState(
    operation: RepositoryLoadOperation,
    state: RepositoryLoadState,
): RepositoryPagingSnapshot<T> = when (operation) {
    RepositoryLoadOperation.INITIAL -> copy(initialLoadState = state)
    RepositoryLoadOperation.REFRESH -> copy(refreshLoadState = state)
    RepositoryLoadOperation.APPEND -> copy(appendLoadState = state)
}
