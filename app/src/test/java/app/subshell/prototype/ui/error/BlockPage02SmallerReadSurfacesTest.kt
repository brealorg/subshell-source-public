package app.subshell.prototype.ui.error

import app.subshell.prototype.data.account.RedditModerationUiFailureKind
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.REDDIT_BLOCK_PAGE_REASON
import app.subshell.prototype.data.error.SafeFailureText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BLOCKPAGE02_SMALLER_READ_SURFACES_NAME_THE_BLOCK_PAGE_V1 (2026-10-31): the mod
 * queue, its next page, the mod log, the Wiki, people search and a replies row
 * say Reddit is blocking the request when it answered with its block page; an
 * ordinary 403 keeps each surface's own text (for the mod queue: the moderator
 * role, which the block page used to be mistaken for).
 */
class BlockPage02SmallerReadSurfacesTest {
    private val blocked = FailureDetails(
        httpStatusCode = 403,
        retryable = false,
        providerCode = SafeFailureText.fromUntrusted(REDDIT_BLOCK_PAGE_REASON),
    )
    private val forbidden = FailureDetails(httpStatusCode = 403, retryable = false)

    private fun texts(details: FailureDetails): Map<String, String> = mapOf(
        "mod queue" to redditModQueueFailureMessage(RedditModerationUiFailureKind.HTTP, 403, details),
        "mod queue next page" to redditModQueueMoreFailedLabel(details),
        "mod log" to redditModeratorLogFailureMessage("Reddit moderation history could not be loaded.", 403, details),
        "wiki" to redditWikiHttpFailureMessage(403, details),
        "people search" to redditPeopleSearchFailureMessage(details),
        "replies row" to redditMoreRepliesFailedLabel(details),
    )

    @Test
    fun theBlockPageIsNamedOnEverySmallerSurface() {
        val silent = texts(blocked).filterValues { !it.contains("Reddit is blocking") }
        assertTrue(silent.toString(), silent.isEmpty())
    }

    @Test
    fun anOrdinary403KeepsEachSurfacesOwnText() {
        assertEquals(
            mapOf(
                "mod queue" to "Reddit denied this mod queue. Your moderator role may not include the Posts permission.",
                "mod queue next page" to "Couldn't load more of the queue",
                "mod log" to "Reddit moderation history could not be loaded. HTTP 403.",
                "wiki" to "Reddit returned HTTP 403 for this Wiki request.",
                "people search" to "Couldn't load Reddit people. Submit the search again to retry.",
                "replies row" to "Couldn't load replies · Retry",
            ),
            texts(forbidden),
        )
    }
}
