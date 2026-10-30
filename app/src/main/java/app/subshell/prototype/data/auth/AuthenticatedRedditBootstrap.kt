package app.subshell.prototype.data.auth

import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.RepositoryPageRequest
import app.subshell.prototype.data.RepositoryQueryKey
import app.subshell.prototype.data.RedditListingTimePeriod
import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.data.RedditFeedSort
import app.subshell.prototype.data.RedditMainFeed
import app.subshell.prototype.data.effectiveTimePeriod
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.model.Comment
import app.subshell.prototype.data.liveCommunityPostFeedQueryKey
import app.subshell.prototype.data.liveHomePostFeedQueryKey
import app.subshell.prototype.data.liveMainPostFeedQueryKey
import app.subshell.prototype.data.liveSearchPostFeedQueryKey
import app.subshell.prototype.data.normalizeRedditCommunityName
import app.subshell.prototype.data.normalizeRedditSearchQuery
import app.subshell.prototype.data.scopedToAccount
import app.subshell.prototype.data.normalizeRedditUsername
import app.subshell.prototype.data.redditUserSubmittedPostFeedQueryKey

internal enum class AuthenticatedRedditBootstrapFailureKind {
    CLIENT_CONFIGURATION,
    HTTP,
    TRANSPORT,
    PROTOCOL,
    EMPTY_FEED,
    STORAGE,
}

internal sealed interface AuthenticatedRedditBootstrapResult {
    data class Synchronized(
        val queryKey: RepositoryQueryKey,
        val networkItemCount: Int,
        val storedItemCount: Int,
    ) : AuthenticatedRedditBootstrapResult

    data class Failed(
        val kind: AuthenticatedRedditBootstrapFailureKind,
        val retryable: Boolean,
        // DIAG05_SIGN_IN_FAILURES_ARE_RECORDED_V1
        val details: FailureDetails? = null,
    ) : AuthenticatedRedditBootstrapResult
}

internal fun interface AuthenticatedRedditBootstrapper {
    suspend fun synchronize(session: StoredOAuthSession): AuthenticatedRedditBootstrapResult
}

internal enum class AuthenticatedRedditFeedUpdateFailureKind {
    CLIENT_CONFIGURATION,
    SESSION,
    HTTP,
    TRANSPORT,
    PROTOCOL,
    STORAGE,
    END_REACHED,
}

internal sealed interface AuthenticatedRedditFeedUpdateResult {
    data class Applied(
        val queryKey: RepositoryQueryKey,
        val operation: RepositoryLoadOperation,
        val networkItemCount: Int,
        val storedItemCount: Int,
        val endReached: Boolean,
    ) : AuthenticatedRedditFeedUpdateResult

    data class Failed(
        val kind: AuthenticatedRedditFeedUpdateFailureKind,
        val retryable: Boolean,
        val details: FailureDetails? = null,
    ) : AuthenticatedRedditFeedUpdateResult
}

internal data class AuthenticatedRedditFeedTarget(
    val mainFeed: RedditMainFeed? = RedditMainFeed.HOME,
    val communityName: String? = null,
    val sort: RedditFeedSort = RedditFeedSort.BEST,
    val timePeriod: RedditListingTimePeriod = RedditListingTimePeriod.ALL,
    val searchQuery: String? = null,
    val username: String? = null,
    // ISSUE211_SEARCH_FOLLOWS_SHOW_NSFW_V1 (#211): include_over_18 for a
    // search; not part of the query key (the UI filters NSFW itself).
    val searchIncludeOver18: Boolean? = null,
) {
    init {
        communityName?.let(::normalizeRedditCommunityName)
        searchQuery?.let(::normalizeRedditSearchQuery)
        username?.let(::normalizeRedditUsername)
        require(
            mainFeed != null ||
                communityName != null ||
                searchQuery != null ||
                username != null
        ) {
            "An authenticated Reddit feed target must select a main feed, community, search, or user"
        }
        require(mainFeed == null || communityName == null) {
            "A main feed target cannot also select a community"
        }
        require(searchQuery == null || mainFeed == null) {
            "A search target cannot also select a main feed"
        }
        require(
            username == null ||
                (mainFeed == null && communityName == null && searchQuery == null)
        ) {
            "A user target cannot also select a main feed, community, or search"
        }
        require(
            searchQuery != null ||
                mainFeed == RedditMainFeed.HOME ||
                sort != RedditFeedSort.BEST,
        ) {
            "Best is only available for the Home listing; use Hot"
        }
        require(searchQuery == null || sort == RedditFeedSort.BEST) {
            "LIVE05 search uses Reddit relevance order"
        }
        require(username == null || sort == RedditFeedSort.NEW) {
            "User submitted listings use Reddit New order"
        }
        require(username == null || timePeriod == RedditListingTimePeriod.ALL) {
            "User submitted listings do not use a listing time period"
        }
        require(timePeriod == sort.effectiveTimePeriod(timePeriod)) {
            "Only Top and Controversial support a listing time period"
        }
    }

    val normalizedCommunityName: String?
        get() = communityName?.let(::normalizeRedditCommunityName)

    val normalizedSearchQuery: String?
        get() = searchQuery?.let(::normalizeRedditSearchQuery)

    val normalizedUsername: String?
        get() = username?.let(::normalizeRedditUsername)

    val listingCommunityName: String?
        get() = mainFeed?.listingCommunityName ?: normalizedCommunityName

    val effectiveTimePeriod: RedditListingTimePeriod
        get() = sort.effectiveTimePeriod(timePeriod)

    companion object {
        val HOME_BEST = home(RedditFeedSort.BEST)

        fun home(
            sort: RedditFeedSort,
            timePeriod: RedditListingTimePeriod = RedditListingTimePeriod.ALL,
        ): AuthenticatedRedditFeedTarget = main(
            feed = RedditMainFeed.HOME,
            sort = sort,
            timePeriod = timePeriod,
        )

        fun main(
            feed: RedditMainFeed,
            sort: RedditFeedSort,
            timePeriod: RedditListingTimePeriod = RedditListingTimePeriod.ALL,
        ): AuthenticatedRedditFeedTarget = AuthenticatedRedditFeedTarget(
            mainFeed = feed,
            sort = sort,
            timePeriod = sort.effectiveTimePeriod(timePeriod),
        )

        fun community(
            name: String,
            sort: RedditFeedSort,
            timePeriod: RedditListingTimePeriod = RedditListingTimePeriod.ALL,
        ): AuthenticatedRedditFeedTarget = AuthenticatedRedditFeedTarget(
            mainFeed = null,
            communityName = normalizeRedditCommunityName(name),
            sort = sort,
            timePeriod = sort.effectiveTimePeriod(timePeriod),
        )

        fun search(
            query: String,
            communityName: String? = null,
            includeOver18: Boolean? = null,
        ): AuthenticatedRedditFeedTarget = AuthenticatedRedditFeedTarget(
            mainFeed = null,
            communityName = communityName?.let(::normalizeRedditCommunityName),
            searchQuery = normalizeRedditSearchQuery(query),
            searchIncludeOver18 = includeOver18,
        )

        fun user(
            username: String,
        ): AuthenticatedRedditFeedTarget = AuthenticatedRedditFeedTarget(
            mainFeed = null,
            sort = RedditFeedSort.NEW,
            username = normalizeRedditUsername(username),
        )
    }
}

internal fun interface AuthenticatedRedditFeedUpdater {
    suspend fun synchronize(
        accountStableId: String,
        operation: RepositoryLoadOperation,
        target: AuthenticatedRedditFeedTarget,
    ): AuthenticatedRedditFeedUpdateResult
}

internal enum class AuthenticatedRedditCommentFailureKind {
    CLIENT_CONFIGURATION,
    SESSION,
    HTTP,
    TRANSPORT,
    PROTOCOL,
    STORAGE,
}

internal sealed interface AuthenticatedRedditCommentResult {
    data class Loaded(
        val comments: List<Comment>,
        val fromCache: Boolean,
        val suggestedSort: RedditCommentSort? = null,
        // ISSUE22_COMMENT_AUTHOR_FULLNAME_V1: only for network-loaded pages.
        val authorFullnames: Map<String, String> = emptyMap(),
    ) : AuthenticatedRedditCommentResult

    data class Failed(
        val kind: AuthenticatedRedditCommentFailureKind,
        val retryable: Boolean,
        val details: FailureDetails? = null,
    ) : AuthenticatedRedditCommentResult
}

internal interface AuthenticatedRedditCommentLoader {
    suspend fun cached(
        accountStableId: String,
        postId: String,
        sort: RedditCommentSort,
    ): AuthenticatedRedditCommentResult

    suspend fun synchronize(
        accountStableId: String,
        postId: String,
        sort: RedditCommentSort,
    ): AuthenticatedRedditCommentResult

    suspend fun synchronizeFocused(
        accountStableId: String,
        postId: String,
        sort: RedditCommentSort,
        focusedCommentId: String,
        context: Int,
    ): AuthenticatedRedditCommentResult =
        synchronize(
            accountStableId = accountStableId,
            postId = postId,
            sort = sort,
        )

    suspend fun loadMore(
        accountStableId: String,
        postId: String,
        sort: RedditCommentSort,
        moreNodeId: String,
    ): AuthenticatedRedditCommentResult

    suspend fun cacheSubmittedComment(
        accountStableId: String,
        postId: String,
        sort: RedditCommentSort,
        parentFullname: String,
        comment: Comment,
    ): AuthenticatedRedditCommentResult

    // COMMENTCACHE01_SUBMIT_MERGES_DISPLAYED_THREAD_V1 (#138): merge a posted
    // comment into the thread the reader sees ([currentComments]; null = the
    // cached thread) and cache that result.
    suspend fun cacheSubmittedCommentOnto(
        accountStableId: String,
        postId: String,
        sort: RedditCommentSort,
        parentFullname: String,
        comment: Comment,
        currentComments: List<Comment>?,
    ): AuthenticatedRedditCommentResult =
        cacheSubmittedComment(accountStableId, postId, sort, parentFullname, comment)

    /**
     * COMMENTSSOT01_THE_THREAD_FOLLOWS_ITS_CACHE_V1 (#244): the stored thread
     * each time it changes, whoever wrote it. An empty list is a thread that is
     * not stored (never read, or evicted), not one without comments.
     */
    fun observeStoredThread(
        accountStableId: String,
        postId: String,
        sort: RedditCommentSort,
    ): kotlinx.coroutines.flow.Flow<List<Comment>> = kotlinx.coroutines.flow.emptyFlow()

    /**
     * COMMENTSSOT01_LOCAL_WRITES_REACH_THE_CACHE_V1: applies a confirmed local
     * change (edit, delete, moderation) to every stored sort of the thread.
     */
    suspend fun updateStoredThread(
        accountStableId: String,
        postId: String,
        transform: (List<Comment>) -> List<Comment>?,
    ) = Unit

    // COMPOSER11_MEDIA_COMMENT_READ_BACK_V1: the current body of one comment
    // (media resolved), or null when it cannot be read.
    suspend fun readCommentBody(
        accountStableId: String,
        postId: String,
        commentFullname: String,
    ): String? = null

    // COMMENTREADBACK01_ACCOUNT_HISTORY_ABSENCE_V1 (#123): the account's
    // newest comments, or null when they cannot be read.
    suspend fun readAccountRecentComments(
        accountStableId: String,
        username: String,
    ): app.subshell.prototype.data.network.reddit.RedditAccountCommentHistory? = null
}

internal interface AuthenticatedRedditCommentSnapshotLoader {
    suspend fun loadMoreFromSnapshot(
        accountStableId: String,
        postId: String,
        sort: RedditCommentSort,
        moreNodeId: String,
        currentComments: List<Comment>,
    ): AuthenticatedRedditCommentResult
}

internal interface AuthenticatedRedditFocusedCommentContinuityLoader {
    suspend fun synchronizePreservingFocusedTarget(
        accountStableId: String,
        postId: String,
        sort: RedditCommentSort,
        focusedCommentId: String,
        currentComments: List<Comment>,
    ): AuthenticatedRedditCommentResult
}

internal fun auth06cInitialFeedRequest(accountStableId: String): RepositoryPageRequest =
    RepositoryPageRequest(
        cursor = null,
        limit = AUTH06C_INITIAL_FEED_LIMIT,
        queryKey = auth06cHomeBestQueryKey(accountStableId),
        operation = RepositoryLoadOperation.REFRESH,
    )

internal fun live02FeedPageRequest(
    accountStableId: String,
    operation: RepositoryLoadOperation,
    remoteCursor: String?,
    target: AuthenticatedRedditFeedTarget = AuthenticatedRedditFeedTarget.HOME_BEST,
): RepositoryPageRequest {
    require(operation in LIVE02_USER_INITIATED_OPERATIONS) {
        "LIVE02 supports only user-initiated refresh and append"
    }
    val cursor = when (operation) {
        RepositoryLoadOperation.REFRESH -> null
        RepositoryLoadOperation.APPEND -> requireNotNull(
            remoteCursor?.trim()?.takeIf(String::isNotEmpty),
        ) {
            "LIVE02 append requires the stored remote cursor"
        }
        RepositoryLoadOperation.INITIAL -> error("Unreachable LIVE02 operation")
    }
    return RepositoryPageRequest(
        cursor = cursor,
        limit = LIVE02_FEED_PAGE_LIMIT,
        queryKey = authenticatedRedditFeedQueryKey(accountStableId, target),
        operation = operation,
    )
}

internal fun authenticatedRedditFeedQueryKey(
    accountStableId: String,
    target: AuthenticatedRedditFeedTarget,
): RepositoryQueryKey = target.normalizedUsername?.let { username ->
    redditUserSubmittedPostFeedQueryKey(
        accountStableId = accountStableId,
        username = username,
    )
} ?: target.normalizedSearchQuery?.let { query ->
    liveSearchPostFeedQueryKey(
        accountStableId = accountStableId,
        query = query,
        communityName = target.normalizedCommunityName,
    )
} ?: target.normalizedCommunityName?.let { communityName ->
    liveCommunityPostFeedQueryKey(
        accountStableId = accountStableId,
        communityName = communityName,
        sort = target.sort,
        timePeriod = target.effectiveTimePeriod,
    )
} ?: requireNotNull(target.mainFeed).let { feed ->
    liveMainPostFeedQueryKey(
        accountStableId = accountStableId,
        feed = feed,
        sort = target.sort,
        timePeriod = target.effectiveTimePeriod,
    )
}

internal fun auth06cHomeBestQueryKey(accountStableId: String): RepositoryQueryKey =
    liveHomePostFeedQueryKey(accountStableId, RedditFeedSort.BEST)

internal fun live03CommentCacheKey(
    accountStableId: String,
    postId: String,
    sort: RedditCommentSort = RedditCommentSort.BEST,
): String = RepositoryQueryKey(
    buildString {
        append("live03|comments|")
        append(normalizeRedditPostFullname(postId))
        if (sort != RedditCommentSort.BEST) {
            append('|')
            append(sort.apiValue)
        }
    },
).scopedToAccount(
    stableId = accountStableId,
    source = AccountSessionSource.AUTHENTICATED,
).value

internal fun normalizeRedditPostFullname(postId: String): String {
    val identifier = postId
        .trim()
        .removePrefix(REDDIT_POST_FULLNAME_PREFIX)
    require(identifier.length in 1..REDDIT_POST_ID_MAX_LENGTH) {
        "Reddit post ID length is outside the supported range"
    }
    require(identifier.all(Char::isLetterOrDigit)) {
        "Reddit post ID contains unsupported characters"
    }
    return "$REDDIT_POST_FULLNAME_PREFIX$identifier"
}

internal val AUTH06C_LEGACY_HOME_BEST_QUERY_KEY = RepositoryQueryKey("main|home|best")
internal const val AUTH06C_INITIAL_FEED_LIMIT = 25
internal const val LIVE02_FEED_PAGE_LIMIT = 25
internal const val LIVE03_COMMENT_LIMIT = 100
internal const val LIVE03_COMMENT_DEPTH = 8
internal val LIVE02_USER_INITIATED_OPERATIONS = setOf(
    RepositoryLoadOperation.REFRESH,
    RepositoryLoadOperation.APPEND,
)

private const val REDDIT_POST_FULLNAME_PREFIX = "t3_"
private const val REDDIT_POST_ID_MAX_LENGTH = 16
