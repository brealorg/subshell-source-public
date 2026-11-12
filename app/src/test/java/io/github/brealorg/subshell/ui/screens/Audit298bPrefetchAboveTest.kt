package io.github.brealorg.subshell.ui.screens

import io.github.brealorg.subshell.model.Comment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * AUDIT298B_PREFETCH_ABOVE_THE_SCREEN_TOO_V1 (owner's Pixel 2026-11-12): the
 * nodes a reader saw load ("Loading replies…") came while scrolling up, where
 * nothing was prefetched. Rows just above the screen are prefetched too; below
 * the screen still comes first.
 */
class Audit298bPrefetchAboveTest {
    private fun row(id: String, more: Boolean = false) = CommentViewportRow(
        comment = Comment(
            id = id, author = "a", age = "1h", body = id, score = 1, depth = 0, accent = 0,
            moreChildIds = if (more) listOf("x$id") else emptyList(), moreCount = if (more) 1 else 0,
        ),
        kind = if (more) CommentViewportRowKind.MORE_REPLIES else CommentViewportRowKind.COMMENT,
    )

    // Prefix items: 3 (no add-comment, no live status), so row i is lazy index i + 3.
    private fun candidate(rows: List<CommentViewportRow>, firstRow: Int, lastRow: Int, budget: Int = 2) =
        commentViewportAutoHydrationCandidate(
            rows = rows,
            firstVisibleLazyIndex = firstRow + 3,
            lastVisibleLazyIndex = lastRow + 3,
            addCommentVisible = false,
            liveStatusVisible = false,
            fullDiscussionLoaded = true,
            fullDiscussionLoading = false,
            focusedAnchorOwned = false,
            automaticHydrationBudget = budget,
            loadingMoreNodeId = null,
            failedMoreNodeId = null,
            scrollInProgress = false,
            hydrationAnchorPending = false,
        )?.id

    @Test
    fun aNodeJustAboveTheScreenIsPrefetchedWhenNothingWaitsBelow() {
        val rows = listOf(row("far", more = true)) + (1..20).map { row("c$it") } +
            listOf(row("near", more = true)) + (21..30).map { row("d$it") }
        // Screen shows rows 25..30 (d24..d29); "near" is row 21, "far" row 0 (too far).
        assertEquals("near", candidate(rows, firstRow = 25, lastRow = 30))
    }

    @Test
    fun belowTheScreenStillComesFirst() {
        val rows = listOf(row("above", more = true), row("c1"), row("c2"), row("below", more = true))
        assertEquals("below", candidate(rows, firstRow = 1, lastRow = 2))
    }

    @Test
    fun nothingAboveTheFirstRowAndNoBudgetMeansNothing() {
        val rows = listOf(row("c0"), row("c1"), row("above", more = true)) + (2..10).map { row("d$it") }
        assertNull(candidate(rows, firstRow = 0, lastRow = 5))
        assertNull(candidate(listOf(row("above", more = true), row("c1")), firstRow = 1, lastRow = 1, budget = 0))
    }

    @Test
    fun smallNodesNearbyComeAlongButNotContinueNodesFailedOnesOrFarOnes() {
        // AUDIT298C_SMALL_NODES_LOAD_TOGETHER_V1
        fun big(id: String) = CommentViewportRow(
            comment = Comment(id = id, author = "", age = "", body = "", score = 0, depth = 0, accent = 0,
                moreChildIds = (1..10).map { "y$it" }, moreCount = 10),
            kind = CommentViewportRowKind.MORE_REPLIES,
        )
        val rows = listOf(row("c0"), row("first", more = true), row("c1"), row("s1", more = true), big("big"),
            row("s2", more = true), row("failed", more = true)) + (1..30).map { row("d$it") } + row("far", more = true)
        val near = commentSmallNodesNear(
            rows = rows,
            visibleKeys = setOf("comment:c0", "more:first", "comment:c1"),
            moreNodeId = "first",
            excludedIds = setOf("failed"),
        )
        assertEquals(listOf("s1", "s2"), near)
    }
}
