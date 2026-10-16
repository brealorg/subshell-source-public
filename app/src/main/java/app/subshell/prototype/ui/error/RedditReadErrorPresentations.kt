package app.subshell.prototype.ui.error

import app.subshell.prototype.data.account.CustomFeedPostsFailureKind
import app.subshell.prototype.data.account.RedditCommentUiFailureKind
import app.subshell.prototype.data.account.RedditCommunityDiscoveryUiFailureKind
import app.subshell.prototype.data.account.RedditCommunityInfoUiFailureKind
import app.subshell.prototype.data.account.RedditFeedUiFailureKind
import app.subshell.prototype.data.account.RedditInboxUiFailureKind
import app.subshell.prototype.data.account.RedditProfileCommentsUiFailureKind
import app.subshell.prototype.data.account.RedditSubscriptionsUiFailureKind
import app.subshell.prototype.data.error.FailureDetails

internal fun redditFeedErrorPresentation(
    failureKind: RedditFeedUiFailureKind,
    details: FailureDetails?,
    retryable: Boolean,
    showingCachedContent: Boolean,
): ErrorPresentation = readErrorPresentation(
    title = if (showingCachedContent) "Couldn't refresh the feed" else "Couldn't load the feed",
    reason = when (failureKind) {
        RedditFeedUiFailureKind.CLIENT_CONFIGURATION ->
            "Reddit feed access isn't configured for the active account."
        RedditFeedUiFailureKind.SESSION ->
            "The Reddit sign-in for this account needs attention."
        RedditFeedUiFailureKind.HTTP ->
            redditReadHttpReason(details?.httpStatusCode, "Reddit rejected the feed request.")
        RedditFeedUiFailureKind.TRANSPORT ->
            "subshell couldn't reach Reddit."
        RedditFeedUiFailureKind.PROTOCOL ->
            "Reddit returned a feed response that subshell couldn't read."
        RedditFeedUiFailureKind.STORAGE ->
            "subshell couldn't store the Reddit feed on this device."
        RedditFeedUiFailureKind.END_REACHED ->
            "There are no more posts in this listing."
    },
    cachedMessage = "Posts already loaded remain visible.",
    details = details,
    retryable = retryable && failureKind != RedditFeedUiFailureKind.END_REACHED,
    showingCachedContent = showingCachedContent,
)

internal fun redditFeedAppendErrorPresentation(
    failureKind: RedditFeedUiFailureKind,
    details: FailureDetails?,
    retryable: Boolean,
): ErrorPresentation = redditFeedErrorPresentation(
    failureKind = failureKind,
    details = details,
    retryable = retryable,
    showingCachedContent = true,
).copy(title = "Couldn't load more posts")

// FEEDS01_CUSTOM_FEED_APPEND_FAILURE_V1: a custom feed read that failed
// while posts are already shown goes to the viewport error tray.
internal fun customFeedPostsErrorPresentation(
    failureKind: CustomFeedPostsFailureKind,
    statusCode: Int?,
    retryable: Boolean,
    append: Boolean,
): ErrorPresentation = readErrorPresentation(
    title = if (append) "Couldn't load more posts" else "Couldn't refresh the feed",
    reason = when (failureKind) {
        CustomFeedPostsFailureKind.CLIENT_CONFIGURATION ->
            "Reddit feed access isn't configured for the active account."
        CustomFeedPostsFailureKind.SESSION ->
            "The Reddit sign-in for this account needs attention."
        CustomFeedPostsFailureKind.HTTP ->
            if (statusCode != null && !redditReadHttpStatusHasOwnReason(statusCode)) {
                "Reddit returned HTTP $statusCode."
            } else {
                redditReadHttpReason(statusCode, "Reddit rejected the feed request.")
            }
        CustomFeedPostsFailureKind.TRANSPORT ->
            "subshell couldn't reach Reddit."
        CustomFeedPostsFailureKind.PROTOCOL ->
            "Reddit returned a feed response that subshell couldn't read."
    },
    cachedMessage = "Loaded posts remain visible.",
    details = null,
    retryable = retryable,
    showingCachedContent = true,
)

internal fun redditCommentsErrorPresentation(
    failureKind: RedditCommentUiFailureKind,
    details: FailureDetails?,
    retryable: Boolean,
    showingCachedContent: Boolean,
): ErrorPresentation = readErrorPresentation(
    title = if (showingCachedContent) "Couldn't refresh comments" else "Couldn't load comments",
    reason = when (failureKind) {
        RedditCommentUiFailureKind.CLIENT_CONFIGURATION ->
            "Reddit comment access isn't configured for the active account."
        RedditCommentUiFailureKind.SESSION ->
            "The Reddit sign-in for this account needs attention."
        RedditCommentUiFailureKind.HTTP ->
            redditReadHttpReason(details?.httpStatusCode, "Reddit rejected the comments request.")
        RedditCommentUiFailureKind.TRANSPORT ->
            "subshell couldn't reach Reddit for comments."
        RedditCommentUiFailureKind.PROTOCOL ->
            "Reddit returned a comments response that subshell couldn't read."
        RedditCommentUiFailureKind.STORAGE ->
            "subshell couldn't read or store the local comments data."
    },
    cachedMessage = "Saved comments remain visible.",
    details = details,
    retryable = retryable,
    showingCachedContent = showingCachedContent,
)

internal fun redditInboxErrorPresentation(
    failureKind: RedditInboxUiFailureKind,
    details: FailureDetails?,
    retryable: Boolean,
    showingCachedContent: Boolean,
): ErrorPresentation = readErrorPresentation(
    title = if (showingCachedContent) "Couldn't refresh Inbox" else "Couldn't load Inbox",
    reason = when (failureKind) {
        RedditInboxUiFailureKind.CLIENT_CONFIGURATION ->
            "Reddit Inbox access isn't configured for the active account."
        RedditInboxUiFailureKind.SESSION ->
            "The Reddit sign-in for this account needs attention before Inbox can load."
        RedditInboxUiFailureKind.HTTP ->
            redditReadHttpReason(details?.httpStatusCode, "Reddit rejected the Inbox request.")
        RedditInboxUiFailureKind.TRANSPORT ->
            "subshell couldn't reach Reddit for Inbox."
        RedditInboxUiFailureKind.PROTOCOL ->
            "Reddit returned an Inbox response that subshell couldn't read."
        RedditInboxUiFailureKind.STORAGE ->
            "subshell couldn't read or store the local Inbox data."
    },
    cachedMessage = "Saved Inbox items remain visible.",
    details = details,
    retryable = retryable,
    showingCachedContent = showingCachedContent,
)

internal fun redditSubscriptionsErrorPresentation(
    failureKind: RedditSubscriptionsUiFailureKind,
    details: FailureDetails?,
    retryable: Boolean,
    showingCachedContent: Boolean,
): ErrorPresentation = readErrorPresentation(
    title = if (showingCachedContent) "Couldn't refresh communities" else "Couldn't load communities",
    reason = when (failureKind) {
        RedditSubscriptionsUiFailureKind.CLIENT_CONFIGURATION ->
            "Reddit community access isn't configured for the active account."
        RedditSubscriptionsUiFailureKind.SESSION ->
            "The Reddit sign-in for this account needs attention."
        RedditSubscriptionsUiFailureKind.HTTP ->
            redditReadHttpReason(details?.httpStatusCode, "Reddit rejected the community list request.")
        RedditSubscriptionsUiFailureKind.TRANSPORT ->
            "subshell couldn't reach Reddit for the community list."
        RedditSubscriptionsUiFailureKind.PROTOCOL ->
            "Reddit returned a community list response that subshell couldn't read."
        RedditSubscriptionsUiFailureKind.STORAGE ->
            "subshell couldn't read or store the saved community list."
    },
    cachedMessage = "Your saved community list remains visible.",
    details = details,
    retryable = retryable,
    showingCachedContent = showingCachedContent,
)

internal fun redditCommunityDiscoveryErrorPresentation(
    failureKind: RedditCommunityDiscoveryUiFailureKind,
    details: FailureDetails?,
    retryable: Boolean,
    showingCachedContent: Boolean,
): ErrorPresentation = readErrorPresentation(
    title = if (showingCachedContent) "Couldn't refresh Explore" else "Couldn't load Explore",
    reason = when (failureKind) {
        RedditCommunityDiscoveryUiFailureKind.CLIENT_CONFIGURATION ->
            "Reddit community discovery isn't configured for the active account."
        RedditCommunityDiscoveryUiFailureKind.SESSION ->
            "The Reddit sign-in for this account needs attention."
        RedditCommunityDiscoveryUiFailureKind.HTTP ->
            redditReadHttpReason(details?.httpStatusCode, "Reddit rejected the community discovery request.")
        RedditCommunityDiscoveryUiFailureKind.TRANSPORT ->
            "subshell couldn't reach Reddit for community discovery."
        RedditCommunityDiscoveryUiFailureKind.PROTOCOL ->
            "Reddit returned a community response that subshell couldn't read."
        RedditCommunityDiscoveryUiFailureKind.STORAGE ->
            "subshell couldn't read or store the saved Explore results."
    },
    cachedMessage = "Saved Explore results remain visible.",
    details = details,
    retryable = retryable,
    showingCachedContent = showingCachedContent,
)

internal fun redditProfileCommentsErrorPresentation(
    failureKind: RedditProfileCommentsUiFailureKind,
    details: FailureDetails?,
    retryable: Boolean,
    showingCachedContent: Boolean,
): ErrorPresentation = readErrorPresentation(
    title = if (showingCachedContent) {
        "Couldn't refresh profile comments"
    } else {
        "Couldn't load profile comments"
    },
    reason = when (failureKind) {
        RedditProfileCommentsUiFailureKind.CLIENT_CONFIGURATION ->
            "Reddit profile comments aren't configured for the active account."
        RedditProfileCommentsUiFailureKind.SESSION ->
            "The Reddit sign-in for this account needs attention before profile comments can load."
        RedditProfileCommentsUiFailureKind.HTTP ->
            redditReadHttpReason(details?.httpStatusCode, "Reddit rejected the profile comments request.")
        RedditProfileCommentsUiFailureKind.TRANSPORT ->
            "subshell couldn't reach Reddit for profile comments."
        RedditProfileCommentsUiFailureKind.PROTOCOL ->
            "Reddit returned a profile comments response that subshell couldn't read."
        RedditProfileCommentsUiFailureKind.STORAGE ->
            "subshell couldn't read or store the local profile comments data."
    },
    cachedMessage = "Saved profile comments remain visible.",
    details = details,
    retryable = retryable,
    showingCachedContent = showingCachedContent,
)

internal fun redditCommunityDiscoverySuggestionErrorPresentation(
    failureKind: RedditCommunityDiscoveryUiFailureKind,
    details: FailureDetails?,
    retryable: Boolean,
    showingFallbackResults: Boolean,
): ErrorPresentation {
    val reason = when (failureKind) {
        RedditCommunityDiscoveryUiFailureKind.CLIENT_CONFIGURATION ->
            "Reddit community discovery isn't configured for the active account."
        RedditCommunityDiscoveryUiFailureKind.SESSION ->
            "The Reddit sign-in for this account needs attention."
        RedditCommunityDiscoveryUiFailureKind.HTTP ->
            redditReadHttpReason(details?.httpStatusCode, "Reddit rejected the community discovery request.")
        RedditCommunityDiscoveryUiFailureKind.TRANSPORT ->
            "subshell couldn't reach Reddit for community suggestions."
        RedditCommunityDiscoveryUiFailureKind.PROTOCOL ->
            "Reddit returned community suggestions that subshell couldn't read."
        RedditCommunityDiscoveryUiFailureKind.STORAGE ->
            "subshell couldn't read the saved community suggestions."
    }
    return ErrorPresentation(
        kind = if (showingFallbackResults) {
            ErrorPresentationKind.DEGRADED
        } else {
            ErrorPresentationKind.INLINE
        },
        title = if (showingFallbackResults) {
            "Broader search unavailable"
        } else {
            "Couldn't update suggestions"
        },
        message = if (showingFallbackResults) {
            "$reason Quick suggestions remain visible."
        } else {
            reason
        },
        primaryAction = if (retryable) {
            ErrorAction(ErrorActionKind.RETRY, "Retry")
        } else {
            null
        },
    ).withFailureDetails(details)
}

// ISSUE208_SERVER_FAILURES_ARE_NOT_REJECTIONS_V1: a 5xx is a problem on
// Reddit's side and a 429 its request limit; only other statuses are a
// rejection of the request.
internal const val REDDIT_READ_SERVER_PROBLEM_REASON =
    "Reddit had a server problem. Try again in a moment."
internal const val REDDIT_READ_RATE_LIMIT_REASON = "Reddit's request limit was reached."

internal fun redditReadHttpStatusHasOwnReason(statusCode: Int?): Boolean =
    statusCode == 429 || statusCode in 500..599

internal fun redditReadHttpReason(statusCode: Int?, rejected: String): String = when {
    statusCode == 429 -> REDDIT_READ_RATE_LIMIT_REASON
    statusCode != null && statusCode in 500..599 -> REDDIT_READ_SERVER_PROBLEM_REASON
    else -> rejected
}

private fun readErrorPresentation(
    title: String,
    reason: String,
    cachedMessage: String,
    details: FailureDetails?,
    retryable: Boolean,
    showingCachedContent: Boolean,
): ErrorPresentation = ErrorPresentation(
    kind = if (showingCachedContent) {
        ErrorPresentationKind.DEGRADED
    } else {
        ErrorPresentationKind.BLOCKING
    },
    title = title,
    message = if (showingCachedContent) "$reason $cachedMessage" else reason,
    primaryAction = if (retryable) {
        ErrorAction(
            kind = ErrorActionKind.RETRY,
            label = "Retry",
        )
    } else {
        null
    },
).withFailureDetails(details)


internal fun redditCommunityInfoErrorPresentation(
    failureKind: RedditCommunityInfoUiFailureKind,
    details: FailureDetails?,
    retryable: Boolean,
    showingPartialContent: Boolean,
): ErrorPresentation = readErrorPresentation(
    title = if (showingPartialContent) {
        "Couldn't refresh community info"
    } else {
        "Couldn't load community info"
    },
    reason = when (failureKind) {
        RedditCommunityInfoUiFailureKind.CLIENT_CONFIGURATION ->
            "Reddit community information isn't configured for the active account."
        RedditCommunityInfoUiFailureKind.SESSION ->
            "The Reddit sign-in for this account needs attention."
        RedditCommunityInfoUiFailureKind.HTTP ->
            redditReadHttpReason(details?.httpStatusCode, "Reddit rejected the community information request.")
        RedditCommunityInfoUiFailureKind.TRANSPORT ->
            "subshell couldn't reach Reddit for community information."
        RedditCommunityInfoUiFailureKind.PROTOCOL ->
            "Reddit returned community information that subshell couldn't read."
    },
    cachedMessage = "Community details already loaded remain visible.",
    details = details,
    retryable = retryable,
    showingCachedContent = showingPartialContent,
)
