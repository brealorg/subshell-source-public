package app.subshell.prototype.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// ISSUE110_ONE_HYDRATION_PATH_V1: every hydration (automatic or a Retry)
// publishes while its row is on screen once nobody scrolls.
class CommentStability01VisibleHydrationCommitPolicyTest {
    private val fence = CommentHydrationCommitFence(
        moreNodeId = "more:t1_parent:children",
        sourceContentRevision = 10L,
        gestureGeneration = 3,
    )

    @Test
    fun automaticHydrationPublishesWhenVisiblePlaceholderIsIdle() {
        assertTrue(
            commentHydrationCommitCanPublish(
                fence = fence,
                currentContentRevision = 11L,
                loadingMoreNodeId = null,
                failedMoreNodeId = null,
                scrollInProgress = false,
            ),
        )
    }

    @Test
    fun automaticHydrationStillDefersDuringActiveUserScroll() {
        assertFalse(
            commentHydrationCommitCanPublish(
                fence = fence,
                currentContentRevision = 11L,
                loadingMoreNodeId = null,
                failedMoreNodeId = null,
                scrollInProgress = true,
            ),
        )
    }
}
