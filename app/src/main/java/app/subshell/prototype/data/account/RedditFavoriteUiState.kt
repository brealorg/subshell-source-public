package app.subshell.prototype.data.account

/**
 * REDDITFAV02_FAVORITE_WRITE_V1: the favorite changes sent to Reddit for one
 * account. [pending] maps a lowercased community name to the state asked
 * for, until the subscriptions read back shows Reddit's answer.
 */
data class RedditFavoriteUiState(
    val accountStableId: String? = null,
    val pending: Map<String, Boolean> = emptyMap(),
    // REDDITFAV04: the same for custom feeds, keyed by lowercased path.
    val pendingCustomFeeds: Map<String, Boolean> = emptyMap(),
    val failure: RedditFavoriteUiFailure? = null,
    val failureRevision: Long = 0L,
    // REDDITFAV03: the browser is open for the favorites authorization.
    val authorizing: Boolean = false,
) {
    fun matches(accountStableId: String): Boolean =
        this.accountStableId == accountStableId
}

data class RedditFavoriteUiFailure(
    val communityName: String,
    val makeFavorite: Boolean,
    val kind: RedditFavoriteUiFailureKind,
    // REDDITFAV04: [communityName] is then the custom feed's display name.
    val customFeed: Boolean = false,
)

enum class RedditFavoriteUiFailureKind {
    /** Reddit refused it, or the request could not be built or sent. */
    FAILED,

    /** The stored grant does not cover favorites (no `subscribe`); Authorize asks for it. */
    AUTHORIZATION,

    /** Sent, but Reddit's list did not show the change when read back. */
    NOT_CONFIRMED,
}
