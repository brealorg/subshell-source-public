package app.subshell.prototype.data

enum class RedditAccountHistoryContentKind {
    POST,
    COMMENT,
}

enum class RedditAccountHistoryKind(
    val pathSegment: String,
    val contentKind: RedditAccountHistoryContentKind,
    val typeFilter: String? = null,
    val queryToken: String,
) {
    COMMENTS(
        pathSegment = "comments",
        contentKind = RedditAccountHistoryContentKind.COMMENT,
        queryToken = "comments",
    ),
    UPVOTED(
        pathSegment = "upvoted",
        contentKind = RedditAccountHistoryContentKind.POST,
        typeFilter = "links",
        queryToken = "upvoted",
    ),
    DOWNVOTED(
        pathSegment = "downvoted",
        contentKind = RedditAccountHistoryContentKind.POST,
        typeFilter = "links",
        queryToken = "downvoted",
    ),
    HIDDEN(
        pathSegment = "hidden",
        contentKind = RedditAccountHistoryContentKind.POST,
        typeFilter = "links",
        queryToken = "hidden",
    ),
    SAVED_POSTS(
        pathSegment = "saved",
        contentKind = RedditAccountHistoryContentKind.POST,
        typeFilter = "links",
        queryToken = "saved-posts",
    ),
    SAVED_COMMENTS(
        pathSegment = "saved",
        contentKind = RedditAccountHistoryContentKind.COMMENT,
        typeFilter = "comments",
        queryToken = "saved-comments",
    ),
    ;

    val isPostHistory: Boolean
        get() = contentKind == RedditAccountHistoryContentKind.POST
}

internal val LIVE_PROFILE_ACTIVITY_HISTORY_KINDS: List<RedditAccountHistoryKind> =
    listOf(
        RedditAccountHistoryKind.UPVOTED,
        RedditAccountHistoryKind.DOWNVOTED,
        RedditAccountHistoryKind.HIDDEN,
    )
