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
import app.subshell.prototype.data.error.REDDIT_BLOCK_PAGE_REASON
import app.subshell.prototype.data.error.SafeFailureText
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HOSTILEREDDIT01_EVERY_READ_ERROR_NAMES_THE_BLOCK_PAGE_V1 (2026-10-31): every
 * shared read error (feed, comments, inbox, joined communities, discover and
 * its suggestions, profile comments, community info, custom feeds) says Reddit
 * is blocking the request when its failure is Reddit's block page, whether or
 * not stored content is still shown.
 */
class HostileReddit01ReadErrorsTest {
    private val blocked = FailureDetails(
        httpStatusCode = 403,
        retryable = false,
        providerCode = SafeFailureText.fromUntrusted(REDDIT_BLOCK_PAGE_REASON),
    )

    private fun presentations(cached: Boolean): List<Pair<String, ErrorPresentation>> = listOf(
        "feed" to redditFeedErrorPresentation(RedditFeedUiFailureKind.HTTP, blocked, false, cached),
        "feed append" to redditFeedAppendErrorPresentation(RedditFeedUiFailureKind.HTTP, blocked, false),
        "custom feed" to customFeedPostsErrorPresentation(CustomFeedPostsFailureKind.HTTP, 403, false, append = cached, details = blocked),
        "comments" to redditCommentsErrorPresentation(RedditCommentUiFailureKind.HTTP, blocked, false, cached),
        "inbox" to redditInboxErrorPresentation(RedditInboxUiFailureKind.HTTP, blocked, false, cached),
        "subscriptions" to redditSubscriptionsErrorPresentation(RedditSubscriptionsUiFailureKind.HTTP, blocked, false, cached),
        "discovery" to redditCommunityDiscoveryErrorPresentation(RedditCommunityDiscoveryUiFailureKind.HTTP, blocked, false, cached),
        "discovery suggestions" to
            redditCommunityDiscoverySuggestionErrorPresentation(RedditCommunityDiscoveryUiFailureKind.HTTP, blocked, false, cached),
        "profile comments" to redditProfileCommentsErrorPresentation(RedditProfileCommentsUiFailureKind.HTTP, blocked, false, cached),
        "community info" to redditCommunityInfoErrorPresentation(RedditCommunityInfoUiFailureKind.HTTP, blocked, false, cached),
    )

    @Test
    fun everySharedReadErrorNamesTheBlockPage() {
        val silent = listOf(false, true).flatMap { cached ->
            presentations(cached)
                .filterNot { (_, presentation) -> presentation.message.contains(REDDIT_BLOCK_PAGE_MESSAGE) }
                .map { (name, presentation) -> "$name (cached=$cached): ${presentation.message}" }
        }
        assertTrue(silent.joinToString("\n"), silent.isEmpty())
    }
}
