package app.subshell.prototype.ui.screens

import app.subshell.prototype.data.network.reddit.focusedCommentContextForTarget
import app.subshell.prototype.data.network.reddit.mergeFocusedCommentContext
import app.subshell.prototype.model.Comment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Profile01a5TargetFirstNavigationPolicyTest {
    @Test
    fun offsetDeltaTargetsTwentyPercentOfLazyViewport() {
        assertEquals(
            300,
            focusedCommentAnchorOffsetPx(
                viewportStartOffset = 100,
                viewportEndOffset = 1100,
            ),
        )
        assertEquals(
            350,
            focusedCommentOffsetDeltaPx(
                itemOffset = 650,
                viewportStartOffset = 100,
                viewportEndOffset = 1100,
            ),
        )
    }

    @Test
    fun acquireBaselineDoesNotCompareRevisionsAcrossDifferentQueries() {
        assertEquals(
            -1L,
            focusedCommentAcquireBaselineRevision(
                stateMatchesTargetQuery = false,
                contentRevision = 42L,
            ),
        )
        assertEquals(
            42L,
            focusedCommentAcquireBaselineRevision(
                stateMatchesTargetQuery = true,
                contentRevision = 42L,
            ),
        )
        assertTrue(
            focusedCommentStateAdvanceCompleted(
                previousRevision = -1L,
                stateMatchesTargetQuery = true,
                loading = false,
                loadingMoreNodeId = null,
                contentRevision = 1L,
                hasFailure = false,
                failedMoreNodeId = null,
            ),
        )
        assertFalse(
            focusedCommentStateAdvanceCompleted(
                previousRevision = 42L,
                stateMatchesTargetQuery = true,
                loading = false,
                loadingMoreNodeId = null,
                contentRevision = 1L,
                hasFailure = false,
                failedMoreNodeId = null,
            ),
        )
    }

    @Test
    fun staleTargetFromAnotherSortCannotSatisfyFocusedAcquire() {
        assertFalse(
            focusedCommentTargetMatchesQuery(
                stateMatchesTargetQuery = false,
                targetPresent = true,
            ),
        )
        assertTrue(
            focusedCommentTargetMatchesQuery(
                stateMatchesTargetQuery = true,
                targetPresent = true,
            ),
        )
    }

    @Test
    fun requestScrollOffsetTargetsAnchorInSingleRemeasure() {
        assertEquals(
            -300,
            focusedCommentRequestScrollOffsetPx(
                viewportStartOffset = 100,
                viewportEndOffset = 1100,
            ),
        )
        assertEquals(
            -430,
            focusedCommentRequestScrollOffsetPx(
                viewportStartOffset = -11,
                viewportEndOffset = 2198,
            ),
        )
    }

    @Test
    fun tallFocusedCommentsUseACompactTwelvePercentAnchor() {
        assertEquals(
            0.12f,
            focusedCommentLandingAnchorFraction(
                itemSizePx = 1389,
                viewportStartOffset = -11,
                viewportEndOffset = 2198,
            ),
        )
        assertEquals(
            0.20f,
            focusedCommentLandingAnchorFraction(
                itemSizePx = 1200,
                viewportStartOffset = -11,
                viewportEndOffset = 2198,
            ),
        )
        assertEquals(
            -254,
            focusedCommentRequestScrollOffsetPx(
                viewportStartOffset = -11,
                viewportEndOffset = 2198,
                anchorFraction = 0.12f,
            ),
        )
    }

    @Test
    fun layoutIdentityRejectsAStaleVisibleTargetFromThePreviousLayout() {
        assertTrue(
            focusedCommentLayoutIdentityMatches(
                focusedCommentId = "t1_target",
                expectedIndex = 155,
                observedTargetIndex = 155,
                observedKeyAtExpectedIndex = "comment:t1_target",
            ),
        )
        assertFalse(
            focusedCommentLayoutIdentityMatches(
                focusedCommentId = "t1_target",
                expectedIndex = 155,
                observedTargetIndex = 154,
                observedKeyAtExpectedIndex = "comment:t1_other",
            ),
        )
        assertFalse(
            focusedCommentLayoutIdentityMatches(
                focusedCommentId = "t1_target",
                expectedIndex = 155,
                observedTargetIndex = 154,
                observedKeyAtExpectedIndex = "comment:t1_target",
            ),
        )
    }

    @Test
    fun landingVerificationUsesRealLazyItemOffsetAndTolerance() {
        assertTrue(
            focusedCommentOffsetVerified(
                itemOffset = 200,
                viewportStartOffset = 0,
                viewportEndOffset = 1000,
                tolerancePx = 24,
            ),
        )
        assertFalse(
            focusedCommentOffsetVerified(
                itemOffset = 250,
                viewportStartOffset = 0,
                viewportEndOffset = 1000,
                tolerancePx = 24,
            ),
        )
    }

    @Test
    fun canonicalRefreshCanRecoverFocusedPathFromPreRefreshCacheSnapshot() {
        val cachedBeforeRefresh = listOf(
            comment(
                id = "t1_root",
                children = listOf(
                    comment(
                        id = "t1_parent",
                        children = listOf(comment("t1_target")),
                    ),
                ),
            ),
        )
        val canonicalRefresh = listOf(
            comment(
                id = "t1_root",
                children = listOf(comment("t1_fresh_sibling")),
            ),
        )

        val cachedFocusedContext =
            focusedCommentContextForTarget(cachedBeforeRefresh, "t1_target")
        val merged = mergeFocusedCommentContext(
            current = canonicalRefresh,
            focused = cachedFocusedContext,
        )

        assertTrue(
            focusedCommentContextForTarget(merged, "t1_target").isNotEmpty(),
        )
        assertEquals(
            listOf("t1_fresh_sibling", "t1_parent"),
            merged.single().children.map(Comment::id),
        )
    }

    @Test
    fun refreshPreservationKeepsOnlyTheTargetPathFromFocusedContext() {
        val current = listOf(
            comment(
                id = "t1_root",
                children = listOf(
                    comment("t1_stale_sibling"),
                    comment(
                        id = "t1_parent",
                        children = listOf(comment("t1_target")),
                    ),
                ),
            ),
        )
        val refreshed = listOf(
            comment(
                id = "t1_root",
                children = listOf(comment("t1_fresh_sibling")),
            ),
        )

        val targetContext = focusedCommentContextForTarget(current, "t1_target")
        val merged = mergeFocusedCommentContext(refreshed, targetContext)
        val root = merged.single()

        assertEquals(listOf("t1_parent"), targetContext.single().children.map(Comment::id))
        assertEquals(
            listOf("t1_fresh_sibling", "t1_parent"),
            root.children.map(Comment::id),
        )
        assertTrue(root.children.any { child -> child.id == "t1_parent" })
        assertFalse(root.children.any { child -> child.id == "t1_stale_sibling" })
    }

    private fun comment(
        id: String,
        children: List<Comment> = emptyList(),
    ): Comment = Comment(
        id = id,
        author = "author",
        age = "1m",
        body = id,
        score = 1,
        depth = 0,
        accent = 0L,
        children = children,
    )
}
