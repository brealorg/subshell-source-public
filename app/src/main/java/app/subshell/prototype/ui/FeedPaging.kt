package app.subshell.prototype.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import app.subshell.prototype.data.RepositoryPagingSnapshot
import app.subshell.prototype.data.RepositoryQueryKey
import app.subshell.prototype.data.RedditListingTimePeriod
import app.subshell.prototype.data.RedditFeedSort
import app.subshell.prototype.data.RedditMainFeed
import app.subshell.prototype.data.REDDIT_SEARCH_QUERY_MAX_LENGTH
import app.subshell.prototype.data.REDDIT_SEARCH_QUERY_MIN_LENGTH
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.RedditFeedUiState
import app.subshell.prototype.data.liveCommunityPostFeedQueryKey
import app.subshell.prototype.data.liveMainPostFeedQueryKey
import app.subshell.prototype.data.liveSearchPostFeedQueryKey
import app.subshell.prototype.data.scopedToAccount
import app.subshell.prototype.media.VideoVisibilityPolicy
import app.subshell.prototype.model.Post
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

internal fun mainPostFeedQueryKey(
    account: AccountSessionSummary,
    source: FeedSource,
    sort: FeedSort,
    timePeriod: RedditListingTimePeriod = RedditListingTimePeriod.ALL,
): RepositoryQueryKey = if (account.source == AccountSessionSource.AUTHENTICATED) {
    source.toRedditMainFeedOrNull()?.let { feed ->
        liveMainPostFeedQueryKey(
            accountStableId = account.stableId,
            feed = feed,
            sort = sort.toRedditFeedSort(),
            timePeriod = timePeriod,
        )
    } ?: repositoryQueryKey("main", source.name, sort.name)
        .scopedToAccount(account.stableId, account.source)
} else {
    repositoryQueryKey("main", source.name, sort.name)
        .scopedToAccount(account.stableId, account.source)
}

internal fun FeedSource.toRedditMainFeedOrNull(): RedditMainFeed? = when (this) {
    FeedSource.HOME -> RedditMainFeed.HOME
    FeedSource.POPULAR -> RedditMainFeed.POPULAR
    FeedSource.ALL -> RedditMainFeed.ALL
    FeedSource.SAVED -> null
}

internal fun FeedSource.requireRedditMainFeed(): RedditMainFeed =
    requireNotNull(toRedditMainFeedOrNull()) {
        "$label is not a live Reddit main feed"
    }

internal fun communityPostFeedQueryKey(
    account: AccountSessionSummary,
    communityName: String,
    sort: CommunitySort,
    timePeriod: RedditListingTimePeriod = RedditListingTimePeriod.ALL,
): RepositoryQueryKey = if (account.source == AccountSessionSource.AUTHENTICATED) {
    liveCommunityPostFeedQueryKey(
        accountStableId = account.stableId,
        communityName = communityName,
        sort = sort.toRedditFeedSort(),
        timePeriod = timePeriod,
    )
} else {
    repositoryQueryKey(
        "community",
        normalizeFeedScrollToken(communityName),
        sort.name,
    )
}

internal fun CommunitySort.toRedditFeedSort(): RedditFeedSort = when (this) {
    CommunitySort.HOT -> RedditFeedSort.HOT
    CommunitySort.NEW -> RedditFeedSort.NEW
    CommunitySort.RISING -> RedditFeedSort.RISING
    CommunitySort.TOP -> RedditFeedSort.TOP
    CommunitySort.CONTROVERSIAL -> RedditFeedSort.CONTROVERSIAL
}

internal fun FeedSort.toRedditFeedSort(): RedditFeedSort = when (this) {
    FeedSort.BEST -> RedditFeedSort.BEST
    FeedSort.HOT -> RedditFeedSort.HOT
    FeedSort.NEW -> RedditFeedSort.NEW
    FeedSort.RISING -> RedditFeedSort.RISING
    FeedSort.TOP -> RedditFeedSort.TOP
    FeedSort.CONTROVERSIAL -> RedditFeedSort.CONTROVERSIAL
}

internal val FeedSort.supportsTimePeriod: Boolean
    get() = this == FeedSort.TOP || this == FeedSort.CONTROVERSIAL

internal val CommunitySort.supportsTimePeriod: Boolean
    get() = this == CommunitySort.TOP || this == CommunitySort.CONTROVERSIAL

internal fun searchPostFeedQueryKey(
    account: AccountSessionSummary,
    destination: MainDestination,
    originCommunity: String?,
    query: String,
    scope: SearchScope,
    area: SearchArea,
): RepositoryQueryKey = if (
    account.source == AccountSessionSource.AUTHENTICATED &&
    scope in listOf(SearchScope.ALL, SearchScope.POSTS) &&
    query.trim().length in REDDIT_SEARCH_QUERY_MIN_LENGTH..REDDIT_SEARCH_QUERY_MAX_LENGTH
) {
    liveSearchPostFeedQueryKey(
        accountStableId = account.stableId,
        query = query,
        communityName = originCommunity.takeIf { area == SearchArea.CURRENT_COMMUNITY },
    )
} else {
    repositoryQueryKey(
        "search",
        destination.name,
        originCommunity?.let(::normalizeFeedScrollToken).orEmpty(),
        normalizeFeedScrollToken(query),
        scope.name,
        area.name,
    )
}

internal fun userPostFeedQueryKey(
    username: String,
    tab: Int,
    activityTab: Int,
): RepositoryQueryKey = repositoryQueryKey(
    "user",
    normalizeFeedScrollToken(username),
    tab.coerceAtLeast(0).toString(),
    if (tab == 2) activityTab.coerceAtLeast(0).toString() else "0",
)

internal fun canAppendRedditFeed(
    live: Boolean,
    nextCursor: String?,
    state: RedditFeedUiState,
): Boolean = live &&
    nextCursor != null &&
    !state.inProgress &&
    state.failureKind == null

internal enum class LiveFeedAutoAppendReason {
    FILTERED_EMPTY,
    VIEWPORT_UNDERFILLED,
}

internal fun communityFeedPostsInDisplayOrder(
    posts: List<Post>,
    sort: CommunitySort,
    authenticatedRedditFeed: Boolean,
): List<Post> = if (authenticatedRedditFeed) {
    posts
} else {
    sortCommunityPosts(posts, sort)
}

// FEEDS01_SEAMLESS_VIEWPORT_FILL_V1: the viewport fills itself. There is no
// "paused" state and no tap-to-load control: the first
// LIVE_FEED_AUTO_APPEND_BUDGET requests go out back to back; after that each
// further request that follows no newly visible post waits a growing delay
// (LIVE_FEED_AUTO_APPEND_BACKOFF_MS). A page that adds visible posts resets
// the delay. The listing's end (no cursor) stops it.
internal sealed interface LiveFeedAutoAppendDecision {
    data object None : LiveFeedAutoAppendDecision

    data class Request(
        val reason: LiveFeedAutoAppendReason,
        val requestNumber: Int,
        val requestBudget: Int,
        val delayMillis: Long = 0L,
    ) : LiveFeedAutoAppendDecision
}

internal class LiveFeedAutoAppendGovernor(
    private val requestBudget: Int = LIVE_FEED_AUTO_APPEND_BUDGET,
    private val backoffMillis: List<Long> = LIVE_FEED_AUTO_APPEND_BACKOFF_MS,
) {
    private var requestCount = 0
    private var requestsWithoutProgress = 0
    private var visibleItemCountAtLastRequest = -1
    private var lastRequestedContentToken: String? = null

    init {
        require(requestBudget > 0) { "Live feed auto-append budget must be positive" }
        require(backoffMillis.isNotEmpty()) { "Live feed auto-append backoff must not be empty" }
    }

    fun evaluate(
        appendEnabled: Boolean,
        contentToken: String?,
        loadedItemCount: Int,
        visibleItemCount: Int,
        viewportUnderfilled: Boolean,
    ): LiveFeedAutoAppendDecision {
        if (!appendEnabled || contentToken == null || loadedItemCount <= 0) {
            return LiveFeedAutoAppendDecision.None
        }
        val reason = when {
            visibleItemCount == 0 -> LiveFeedAutoAppendReason.FILTERED_EMPTY
            viewportUnderfilled -> LiveFeedAutoAppendReason.VIEWPORT_UNDERFILLED
            else -> return LiveFeedAutoAppendDecision.None
        }
        if (contentToken == lastRequestedContentToken) {
            return LiveFeedAutoAppendDecision.None
        }
        val progressed = visibleItemCount > visibleItemCountAtLastRequest
        val pendingWithoutProgress = if (progressed) 0 else requestsWithoutProgress
        val delayMillis = if (pendingWithoutProgress < requestBudget) {
            0L
        } else {
            backoffMillis[(pendingWithoutProgress - requestBudget).coerceAtMost(backoffMillis.lastIndex)]
        }
        return LiveFeedAutoAppendDecision.Request(
            reason = reason,
            requestNumber = requestCount + 1,
            requestBudget = requestBudget,
            delayMillis = delayMillis,
        )
    }

    /** Records that the request [evaluate] returned for [contentToken] was sent. */
    fun markRequested(contentToken: String, visibleItemCount: Int) {
        requestsWithoutProgress = if (visibleItemCount > visibleItemCountAtLastRequest) {
            1
        } else {
            requestsWithoutProgress + 1
        }
        requestCount += 1
        visibleItemCountAtLastRequest = visibleItemCount
        lastRequestedContentToken = contentToken
    }
}

internal fun isLiveFeedViewportUnderfilled(
    totalItemsCount: Int,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    canScrollForward: Boolean,
): Boolean = totalItemsCount > 0 &&
    firstVisibleItemIndex == 0 &&
    firstVisibleItemScrollOffset == 0 &&
    !canScrollForward

@Composable
internal fun ObserveLiveFeedAutoAppend(
    listState: LazyListState,
    sessionKey: String,
    appendEnabled: Boolean,
    contentToken: String?,
    loadedItemCount: Int,
    visibleItemCount: Int,
    onAppend: () -> Unit,
) {
    val governor = remember(sessionKey) { LiveFeedAutoAppendGovernor() }
    val currentOnAppend by rememberUpdatedState(onAppend)

    LaunchedEffect(
        listState,
        sessionKey,
        appendEnabled,
        contentToken,
        loadedItemCount,
        visibleItemCount,
    ) {
        snapshotFlow {
            isLiveFeedViewportUnderfilled(
                totalItemsCount = listState.layoutInfo.totalItemsCount,
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
                canScrollForward = listState.canScrollForward,
            )
        }
            .distinctUntilChanged()
            .collectLatest { viewportUnderfilled ->
                val decision = governor.evaluate(
                    appendEnabled = appendEnabled,
                    contentToken = contentToken,
                    loadedItemCount = loadedItemCount,
                    visibleItemCount = visibleItemCount,
                    viewportUnderfilled = viewportUnderfilled,
                )
                if (decision !is LiveFeedAutoAppendDecision.Request || contentToken == null) {
                    return@collectLatest
                }
                // A scroll that fills the viewport during the delay cancels
                // this request (collectLatest) before it is recorded.
                if (decision.delayMillis > 0L) delay(decision.delayMillis)
                governor.markRequested(contentToken, visibleItemCount)
                uiDebugLog(
                    LIVE_FEED_LOG_TAG,
                    "governor automatic reason=${decision.reason.name.lowercase()} " +
                        "request=${decision.requestNumber} delay=${decision.delayMillis}ms " +
                        "loaded=$loadedItemCount visible=$visibleItemCount",
                )
                currentOnAppend()
            }
    }
}

// FEEDS02_TERMINAL_VIDEO_TRACKS_FEED_END_V1: recomputed when the listing
// ends or a refresh reopens it, not only when the video ids change.
@Composable
internal fun rememberTerminalVideoId(
    videoPostIds: List<String>,
    feedExhausted: Boolean,
): String? = remember(videoPostIds, feedExhausted) {
    VideoVisibilityPolicy.selectTerminalVideoId(
        orderedVideoIds = videoPostIds,
        feedExhausted = feedExhausted,
    )
}

internal fun libraryPostFeedQueryKey(tab: Int): RepositoryQueryKey =
    repositoryQueryKey("library", tab.coerceAtLeast(0).toString())

private fun repositoryQueryKey(
    surface: String,
    vararg tokens: String,
): RepositoryQueryKey = RepositoryQueryKey(
    buildString {
        append(surface)
        tokens.forEach { token ->
            append('|')
            append(
                token
                    .trim()
                    .lowercase()
                    .replace("|", "%7c"),
            )
        }
    },
)

internal val RepositoryPagingSnapshot<Post>.pagingFooterKey: String?
    get() = when {
        initialLoadState is app.subshell.prototype.data.RepositoryLoadState.Loading ->
            "data02-initial-loading"
        initialLoadState is app.subshell.prototype.data.RepositoryLoadState.Error ->
            "data02-initial-error"
        initialLoadState is app.subshell.prototype.data.RepositoryLoadState.Offline ->
            "data02-initial-offline"
        appendLoadState is app.subshell.prototype.data.RepositoryLoadState.Loading ->
            "data02-append-loading"
        appendLoadState is app.subshell.prototype.data.RepositoryLoadState.Error ->
            "data02-append-error"
        appendLoadState is app.subshell.prototype.data.RepositoryLoadState.Offline ->
            "data02-append-offline"
        refreshLoadState is app.subshell.prototype.data.RepositoryLoadState.Error ->
            "data02-refresh-error"
        refreshLoadState is app.subshell.prototype.data.RepositoryLoadState.Offline ->
            "data02-refresh-offline"
        else -> null
    }

internal const val LIVE_FEED_AUTO_APPEND_BUDGET = 2
internal val LIVE_FEED_AUTO_APPEND_BACKOFF_MS = listOf(1_000L, 2_000L, 4_000L, 8_000L)
private const val LIVE_FEED_LOG_TAG = "subshell-feed"
