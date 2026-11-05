package io.github.brealorg.subshell.ui.error

import io.github.brealorg.subshell.data.account.CustomFeedPostsFailureKind
import io.github.brealorg.subshell.data.account.RedditCommentUiFailureKind
import io.github.brealorg.subshell.data.account.RedditCommunityDiscoveryUiFailureKind
import io.github.brealorg.subshell.data.account.RedditCommunityInfoUiFailureKind
import io.github.brealorg.subshell.data.account.RedditFeedUiFailureKind
import io.github.brealorg.subshell.data.account.RedditInboxUiFailureKind
import io.github.brealorg.subshell.data.account.RedditProfileCommentsUiFailureKind
import io.github.brealorg.subshell.data.account.RedditSubscriptionsUiFailureKind
import io.github.brealorg.subshell.data.error.FailureDetails
import io.github.brealorg.subshell.data.error.REDDIT_BLOCK_PAGE_REASON
import io.github.brealorg.subshell.data.error.SafeFailureText
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
