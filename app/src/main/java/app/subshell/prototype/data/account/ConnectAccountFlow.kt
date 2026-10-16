package app.subshell.prototype.data.account

import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.RepositoryQueryKey
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.model.Comment
import app.subshell.prototype.model.Community
import app.subshell.prototype.model.CommunityDiscoveryItem
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.model.CommunityDiscoverySort
import app.subshell.prototype.model.RedditPeopleSearchItem

enum class ConnectAccountPhase {
    UNAVAILABLE,
    IDLE,
    LAUNCHING,
    AWAITING_CALLBACK,
    EXCHANGING,
    RESOLVING_IDENTITY,
    SYNCING_REDDIT_FEED,
    CONNECTED,
    CANCELLED,
    ERROR,
}

enum class ConnectAccountFailureKind {
    CONFIGURATION_REQUIRED,
    BROWSER_LAUNCH,
    CALLBACK_REJECTED,
    CALLBACK_MISSING,
    PROVIDER,
    TOKEN_EXCHANGE,
    IDENTITY,
    REDDIT_FEED,
    ACCOUNT_CONFLICT,
    REAUTHENTICATION_ACCOUNT_MISMATCH,
    STORAGE,
}

enum class RedditFeedUiFailureKind {
    CLIENT_CONFIGURATION,
    SESSION,
    HTTP,
    TRANSPORT,
    PROTOCOL,
    STORAGE,
    END_REACHED,
}

data class RedditFeedUiState(
    val accountStableId: String? = null,
    val queryKey: RepositoryQueryKey? = null,
    val inProgressOperation: RepositoryLoadOperation? = null,
    val failedOperation: RepositoryLoadOperation? = null,
    val failureKind: RedditFeedUiFailureKind? = null,
    val failureRetryable: Boolean = false,
    val failureDetails: FailureDetails? = null,
    val lastAppliedOperation: RepositoryLoadOperation? = null,
    val contentRevision: Long = 0L,
) {
    init {
        require(accountStableId == null || accountStableId.isNotBlank()) {
            "Reddit feed account stable ID cannot be blank"
        }
        require((accountStableId == null) == (queryKey == null)) {
            "Reddit feed account and query identities must be set together"
        }
        require(contentRevision >= 0L) {
            "Reddit feed content revision cannot be negative"
        }
    }

    val refreshing: Boolean
        get() = inProgressOperation == RepositoryLoadOperation.REFRESH

    val appending: Boolean
        get() = inProgressOperation == RepositoryLoadOperation.APPEND

    val inProgress: Boolean
        get() = inProgressOperation != null

    fun matches(
        accountStableId: String,
        queryKey: RepositoryQueryKey,
    ): Boolean = this.accountStableId == accountStableId &&
        this.queryKey == queryKey
}

internal fun RedditFeedUiState.shouldStartRequest(
    accountStableId: String,
    queryKey: RepositoryQueryKey,
    operation: RepositoryLoadOperation? = null,
): Boolean = !inProgress || !matches(accountStableId, queryKey) ||
    refreshReplacesAppend(operation, inProgressOperation)

// LOW7_REFRESH_REPLACES_A_RUNNING_APPEND_V1 (OCB-440/441): a refresh (pull to
// refresh, Retry) asked for while the same list is appending replaces the
// append; any other request for a list that is already loading is a duplicate.
internal fun refreshReplacesAppend(
    requested: RepositoryLoadOperation?,
    running: RepositoryLoadOperation?,
): Boolean =
    requested == RepositoryLoadOperation.REFRESH && running == RepositoryLoadOperation.APPEND

enum class RedditInboxUiFailureKind {
    CLIENT_CONFIGURATION,
    SESSION,
    HTTP,
    TRANSPORT,
    PROTOCOL,
    STORAGE,
}

data class RedditInboxUiState(
    val accountStableId: String? = null,
    val items: List<InboxItem> = emptyList(),
    val locallySeenIds: Set<String> = emptySet(),
    val nextCursor: String? = null,
    val loading: Boolean = false,
    val appending: Boolean = false,
    val fromCache: Boolean = false,
    val loaded: Boolean = false,
    val authorizing: Boolean = false,
    val authorizationRequired: Boolean = false,
    val readMutationItemIds: Set<String> = emptySet(),
    val failureKind: RedditInboxUiFailureKind? = null,
    val failureRetryable: Boolean = false,
    val failureDetails: FailureDetails? = null,
    val contentRevision: Long = 0L,
) {
    init {
        require(accountStableId == null || accountStableId.isNotBlank()) {
            "Live inbox account stable ID cannot be blank"
        }
        require(contentRevision >= 0L) {
            "Live inbox content revision cannot be negative"
        }
    }

    val unreadCount: Int
        get() = items.count { item -> item.unread && item.id !in locallySeenIds }

    val readMutationInProgress: Boolean
        get() = readMutationItemIds.isNotEmpty()

    fun matches(accountStableId: String): Boolean =
        this.accountStableId == accountStableId
}

enum class RedditSubscriptionsUiFailureKind {
    CLIENT_CONFIGURATION,
    SESSION,
    HTTP,
    TRANSPORT,
    PROTOCOL,
    STORAGE,
}

data class RedditSubscriptionsUiState(
    val accountStableId: String? = null,
    val communities: List<Community> = emptyList(),
    val loading: Boolean = false,
    val fromCache: Boolean = false,
    val loaded: Boolean = false,
    val failureKind: RedditSubscriptionsUiFailureKind? = null,
    val failureRetryable: Boolean = false,
    val failureDetails: FailureDetails? = null,
    val contentRevision: Long = 0L,
    // REDDITFAV01_SUBSCRIPTIONS_CARRY_REDDIT_FAVORITES_V1: the communities
    // Reddit marks as favorites, as read with [communities].
    val favoriteCommunityNames: Set<String> = emptySet(),
) {
    init {
        require(accountStableId == null || accountStableId.isNotBlank()) {
            "Live subscriptions account stable ID cannot be blank"
        }
        require(contentRevision >= 0L) {
            "Live subscriptions content revision cannot be negative"
        }
    }

    fun matches(accountStableId: String): Boolean =
        this.accountStableId == accountStableId
}

enum class RedditCommunityDiscoveryUiFailureKind {
    CLIENT_CONFIGURATION,
    SESSION,
    HTTP,
    TRANSPORT,
    PROTOCOL,
    STORAGE,
}

data class RedditPeopleSearchUiState(
    val accountStableId: String? = null,
    val searchQuery: String? = null,
    val items: List<RedditPeopleSearchItem> = emptyList(),
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val failureKind: RedditCommunityDiscoveryUiFailureKind? = null,
    val failureRetryable: Boolean = false,
    val failureDetails: FailureDetails? = null,
    val failureHttpStatusCode: Int? = null,
    val contentRevision: Long = 0L,
) {
    init {
        require(accountStableId == null || accountStableId.isNotBlank()) {
            "Live Reddit people search account stable ID cannot be blank"
        }
        require(searchQuery == null || searchQuery.isNotBlank()) {
            "Live Reddit people search query cannot be blank"
        }
        require(failureHttpStatusCode == null || failureHttpStatusCode in 100..599) {
            "Live Reddit people search HTTP status must be valid"
        }
        require(contentRevision >= 0L) {
            "Live Reddit people search content revision cannot be negative"
        }
    }

    fun matches(
        accountStableId: String,
        searchQuery: String,
    ): Boolean =
        this.accountStableId == accountStableId &&
            this.searchQuery == searchQuery
}

data class RedditCommunityDiscoveryUiState(
    val accountStableId: String? = null,
    val searchQuery: String? = null,
    val searchSort: CommunityDiscoverySort? = null,
    val autocompleteIncludeOver18: Boolean? = null,
    val items: List<CommunityDiscoveryItem> = emptyList(),
    val itemsAreAutocompleteFallback: Boolean = false,
    val loading: Boolean = false,
    val fromCache: Boolean = false,
    val loaded: Boolean = false,
    val failureKind: RedditCommunityDiscoveryUiFailureKind? = null,
    val failureRetryable: Boolean = false,
    val failureDetails: FailureDetails? = null,
    val failureHttpStatusCode: Int? = null,
    val contentRevision: Long = 0L,
    val peopleSearch: RedditPeopleSearchUiState = RedditPeopleSearchUiState(),
) {
    init {
        require(accountStableId == null || accountStableId.isNotBlank()) {
            "Live community discovery account stable ID cannot be blank"
        }
        require(searchQuery == null || searchQuery.isNotBlank()) {
            "Live community discovery search query cannot be blank"
        }
        when {
            searchQuery == null -> require(
                searchSort == null && autocompleteIncludeOver18 == null,
            ) {
                "Popular community discovery cannot include search parameters"
            }
            autocompleteIncludeOver18 != null -> require(searchSort == null) {
                "Community autocomplete cannot include a listing sort"
            }
            else -> require(searchSort != null) {
                "Full community search requires a sort"
            }
        }
        require(!itemsAreAutocompleteFallback || searchQuery != null) {
            "Autocomplete fallback items require a search query"
        }
        require(failureHttpStatusCode == null || failureHttpStatusCode in 100..599) {
            "Live community discovery HTTP status must be valid"
        }
        require(contentRevision >= 0L) {
            "Live community discovery content revision cannot be negative"
        }
    }

    val isAutocomplete: Boolean
        get() = autocompleteIncludeOver18 != null

    fun matches(
        accountStableId: String,
        searchQuery: String?,
        searchSort: CommunityDiscoverySort?,
        autocompleteIncludeOver18: Boolean?,
    ): Boolean =
        this.accountStableId == accountStableId &&
            this.searchQuery == searchQuery &&
            this.searchSort == searchSort &&
            this.autocompleteIncludeOver18 == autocompleteIncludeOver18
}

enum class RedditCommentUiFailureKind {
    CLIENT_CONFIGURATION,
    SESSION,
    HTTP,
    TRANSPORT,
    PROTOCOL,
    STORAGE,
}

data class RedditCommentsUiState(
    val accountStableId: String? = null,
    val postId: String? = null,
    val sort: RedditCommentSort = RedditCommentSort.BEST,
    val suggestedSort: RedditCommentSort? = null,
    val comments: List<Comment> = emptyList(),
    val loading: Boolean = false,
    val fullDiscussionLoading: Boolean = false,
    val fullDiscussionLoaded: Boolean = false,
    val loadingMoreNodeId: String? = null,
    val fromCache: Boolean = false,
    val failureKind: RedditCommentUiFailureKind? = null,
    val failureRetryable: Boolean = false,
    val failureDetails: FailureDetails? = null,
    val failedMoreNodeId: String? = null,
    val contentRevision: Long = 0L,
    // ISSUE22_COMMENT_AUTHOR_FULLNAME_V1: accumulated from network-loaded
    // pages of this post; drives the batch avatar lookup. Never cached.
    val authorFullnames: Map<String, String> = emptyMap(),
) {
    init {
        require(accountStableId == null || accountStableId.isNotBlank()) {
            "Live comment account stable ID cannot be blank"
        }
        require(postId == null || postId.isNotBlank()) {
            "Live comment post ID cannot be blank"
        }
        require((accountStableId == null) == (postId == null)) {
            "Live comment account and post identities must be set together"
        }
        require(contentRevision >= 0L) {
            "Live comment content revision cannot be negative"
        }
    }

    fun matches(
        accountStableId: String,
        postId: String,
        sort: RedditCommentSort = RedditCommentSort.BEST,
    ): Boolean = this.accountStableId == accountStableId &&
        this.postId == postId &&
        this.sort == sort
}

internal fun RedditCommentsUiState.beginSortLoad(
    accountStableId: String,
    postId: String,
    sort: RedditCommentSort,
): RedditCommentsUiState {
    val previousForPost = takeIf { state ->
        state.accountStableId == accountStableId && state.postId == postId
    }
    return RedditCommentsUiState(
        accountStableId = accountStableId,
        postId = postId,
        sort = sort,
        comments = previousForPost?.comments.orEmpty(),
        suggestedSort = previousForPost?.suggestedSort,
        loading = true,
        fullDiscussionLoading = false,
        fullDiscussionLoaded = previousForPost?.let { previous ->
            previous.sort == sort && previous.fullDiscussionLoaded
        } == true,
        fromCache = previousForPost?.comments?.isNotEmpty() == true,
        contentRevision = previousForPost?.contentRevision ?: 0L,
        // ACCOUNT02_SORT_KEEPS_AUTHOR_FULLNAMES_V1: the same comments stay on
        // screen, so their avatar lookups stay too (ISSUE22).
        authorFullnames = previousForPost?.authorFullnames.orEmpty(),
    )
}

data class ConnectAccountUiState(
    val phase: ConnectAccountPhase,
    val providerLabel: String,
    val username: String? = null,
    val failureKind: ConnectAccountFailureKind? = null,
    val retryable: Boolean = false,
    val directoryRevision: Long = 0L,
) {
    init {
        require(providerLabel.isNotBlank()) { "Connect-account provider label cannot be blank" }
        require(username == null || username.isNotBlank()) {
            "Connected account username cannot be blank"
        }
        require(directoryRevision >= 0L) {
            "Account directory revision cannot be negative"
        }
    }

    val inProgress: Boolean
        get() = phase in setOf(
            ConnectAccountPhase.LAUNCHING,
            ConnectAccountPhase.AWAITING_CALLBACK,
            ConnectAccountPhase.EXCHANGING,
            ConnectAccountPhase.RESOLVING_IDENTITY,
            ConnectAccountPhase.SYNCING_REDDIT_FEED,
        )

    companion object {
        fun unavailable(providerLabel: String = "Reddit"): ConnectAccountUiState =
            ConnectAccountUiState(
                phase = ConnectAccountPhase.UNAVAILABLE,
                providerLabel = providerLabel,
                failureKind = ConnectAccountFailureKind.CONFIGURATION_REQUIRED,
            )
    }
}
