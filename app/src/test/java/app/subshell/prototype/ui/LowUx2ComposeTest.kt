package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.RepositoryLoadState
import app.subshell.prototype.data.RepositoryPagingSnapshot
import app.subshell.prototype.data.RepositoryQueryKey
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.network.NetworkTransportFailureKind
import app.subshell.prototype.model.Post
import app.subshell.prototype.ui.components.LocalUriMedia
import app.subshell.prototype.ui.components.RepositoryPagingFooter
import app.subshell.prototype.ui.components.communityMuteUntilTimeStart
import java.io.File
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * LOWUX2: LOWUX2_PAGING_FOOTER_NAMES_ITS_LOAD_V1 (OCB-158),
 * LOWUX2_UNDECODABLE_LOCAL_MEDIA_SAYS_SO_V1 (OCB-139),
 * LOWUX2_MUTE_UNTIL_TIME_STARTS_IN_THE_FUTURE_V1 (OCB-130).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class LowUx2ComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val key = RepositoryQueryKey("lowux2|test")

    private fun count(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun aFailedFirstLoadIsNotCalledLoadMore() {
        composeRule.setContent {
            RepositoryPagingFooter(
                snapshot = RepositoryPagingSnapshot<Post>(
                    queryKey = key,
                    initialLoadState = RepositoryLoadState.Error("Reddit did not answer"),
                ),
                onRetry = {},
            )
        }
        composeRule.waitForIdle()

        assertEquals(1, count("Could not load"))
        assertEquals(0, count("Could not load more"))
    }

    // STEP3 (error01a5): the footer says why a page failed.
    @Test
    fun aFailedPageSaysWhyInTheFooter() {
        composeRule.setContent {
            RepositoryPagingFooter(
                snapshot = RepositoryPagingSnapshot<Post>(
                    queryKey = key,
                    initialLoadState = RepositoryLoadState.Error(
                        "Reddit did not answer",
                        failureDetails = FailureDetails(retryable = true, transportKind = NetworkTransportFailureKind.TIMEOUT),
                    ),
                ),
                onRetry = {},
            )
        }
        composeRule.waitForIdle()

        assertEquals(1, composeRule.onAllNodesWithText("Network: Timeout", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun aRefreshWithItemsIsCalledRefreshing() {
        composeRule.setContent {
            RepositoryPagingFooter(
                snapshot = RepositoryPagingSnapshot(
                    queryKey = key,
                    items = listOf(app.subshell.prototype.data.SampleData.posts.first()),
                    refreshLoadState = RepositoryLoadState.Loading,
                ),
                onRetry = {},
            )
        }
        composeRule.waitForIdle()

        assertEquals(1, count("Refreshing…"))
        assertEquals(0, count("Loading more…"))
    }

    @Test
    fun aLocalVideoWithoutAFrameSaysPreviewUnavailable() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val empty = File(context.cacheDir, "lowux2-empty.mp4").apply { writeBytes(ByteArray(0)) }
        composeRule.setContent {
            LocalUriMedia(uri = "file://${empty.absolutePath}", mimeType = "video/mp4", contentDescription = "Picked video")
        }
        composeRule.waitUntil(10_000) { count("Preview unavailable") == 1 }
    }

    @Test
    fun todaysUntilTimeStartsAnHourAhead() {
        val now = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 8, 14, 30, 0) }.timeInMillis
        val today = communityMuteUntilTimeStart(now, 2026, Calendar.OCTOBER, 8)
        val later = communityMuteUntilTimeStart(now, 2026, Calendar.OCTOBER, 9)

        assertEquals(15, today.get(Calendar.HOUR_OF_DAY))
        assertTrue(today.timeInMillis > now)
        assertEquals(14, later.get(Calendar.HOUR_OF_DAY))
    }
}
