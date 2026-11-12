package io.github.brealorg.subshell.ui.screens

import io.github.brealorg.subshell.model.Comment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** ISSUE298_ROWS_LOADING_ABOVE_THE_READER_STAY_ABOVE_V1: which row holds still. */
class Issue298HydrationAnchorPolicyTest {
    private fun row(id: String, more: Boolean = false) = CommentViewportRow(
        comment = Comment(
            id = id,
            author = "a",
            age = "1h",
            body = id,
            score = 1,
            depth = 0,
            accent = 0,
            moreChildIds = if (more) listOf("x$id") else emptyList(),
            moreCount = if (more) 1 else 0,
        ),
        kind = if (more) CommentViewportRowKind.MORE_REPLIES else CommentViewportRowKind.COMMENT,
    )

    private fun item(key: String, index: Int, offset: Int, size: Int = 160) =
        CommentViewportVisibleItem(key, index, offset, size)

    // Lazy index = row index + 3 (header items).
    private val old = listOf(row("t1"), row("t2"), row("node", more = true), row("mine"), row("reply"))
    private val new = listOf(row("t1"), row("t2")) +
        (1..300).map { row("new$it") } +
        listOf(row("node", more = true), row("mine"), row("reply"))

    // The Pixel case: t2 at the top, the node's 1dp row not reported, mine and reply below.
    private val screen = listOf(
        item("comment:t2", 4, -365, 500), // 500 of it visible
        item("comment:mine", 6, 135, 300),
        item("comment:reply", 7, 435, 600),
    )

    @Test
    fun aNodeReachedFromBelowHoldsTheRowsBelowIt() {
        // The Pixel case: the node's 1dp row is not reported; mine and reply below.
        val anchor = commentHydrationAnchor(screen, old, new, holdBelow = true)
        assertEquals(CommentHydrationAnchor("comment:mine", 6, 135), anchor)
        assertEquals(306, commentHydrationAnchorIndexAfter(anchor!!, old, new))
    }

    @Test
    fun aNodeReachedFromAboveLetsComposeHoldTheTopWhateverFillsTheScreen() {
        // The second Pixel jump: rows below filled more of the screen, and
        // holding them pushed up what the reader was reading.
        assertNull(commentHydrationAnchor(screen, old, new, holdBelow = false))
        val mostlyAbove = listOf(item("comment:t2", 4, 0, 1500), item("comment:mine", 6, 1500, 200))
        assertNull(commentHydrationAnchor(mostlyAbove, old, new))
    }

    @Test
    fun theFocusedReplyBelowTheInsertionStaysStillWhateverFillsTheScreen() {
        val mostlyAbove = listOf(item("comment:t2", 4, 0, 1500), item("comment:reply", 7, 1500, 200))
        val anchor = commentHydrationAnchor(mostlyAbove, old, new, focusedKey = "comment:reply")
        assertEquals(CommentHydrationAnchor("comment:reply", 7, 1500), anchor)
    }

    @Test
    fun rowsGoingInAboveTheScreenHoldTheFirstVisibleRow() {
        // Compose finds its first visible key only within ~100 items.
        val belowOnly = listOf(item("comment:mine", 6, 0), item("comment:reply", 7, 300))
        assertEquals("comment:mine", commentHydrationAnchor(belowOnly, old, new)?.key)
    }

    @Test
    fun rowsGoingInBelowTheScreenNeedNoAnchor() {
        val aboveOnly = listOf(item("comment:t1", 3, 0), item("comment:t2", 4, 300))
        assertNull(commentHydrationAnchor(aboveOnly, old, new))
        assertNull(commentHydrationAnchor(screen, old, old))
    }

    @Test
    fun aNodeAboveTheScreenOrUnderTheReadersRowsIsReachedFromBelow() {
        // The Pixel case: the node right above mine, mine and reply fill the screen.
        val pixel = listOf(item("comment:t2", 4, -365, 135), item("comment:mine", 6, 135, 300), item("comment:reply", 7, 435, 1500))
        assertEquals(true, commentMoreNodeReachedFromBelow(pixel, moreLazyIndex = 5))
        assertEquals(true, commentMoreNodeReachedFromBelow(pixel, moreLazyIndex = 2))
        assertEquals(false, commentMoreNodeReachedFromBelow(pixel, moreLazyIndex = 9))
        // Reading down: the node near the bottom of the screen.
        val readingDown = listOf(item("comment:t1", 3, 0, 1500), item("comment:t2", 4, 1500, 300))
        assertEquals(false, commentMoreNodeReachedFromBelow(readingDown, moreLazyIndex = 5))
        // The focused reply below the node wins.
        val focusBelow = listOf(item("comment:t1", 3, 0, 1500), item("comment:reply", 7, 1500, 300))
        assertEquals(true, commentMoreNodeReachedFromBelow(focusBelow, 5, focusedKey = "comment:reply"))
    }

    @Test
    fun whileTheLandingHoldsTheFocusedCommentOnlyNodesBelowItLoad() {
        // ISSUE298_FOCUSED_REPLIES_LOAD_ON_ARRIVAL_V1
        val rows = listOf(row("above", more = true), row("focus"), row("below", more = true))
        fun candidate(owned: Boolean, focus: String?) = commentViewportVisibleIdleMoreNode(
            rows = rows,
            firstVisibleLazyIndex = 3,
            lastVisibleLazyIndex = 5,
            addCommentVisible = false,
            liveStatusVisible = false,
            fullDiscussionLoaded = true,
            fullDiscussionLoading = false,
            focusedAnchorOwned = owned,
            loadingMoreNodeId = null,
            failedMoreNodeId = null,
            scrollInProgress = false,
            hydrationAnchorPending = false,
            focusedCommentId = focus,
        )?.id
        assertEquals("below", candidate(owned = true, focus = "focus"))
        assertEquals("above", candidate(owned = false, focus = "focus"))
        assertNull(candidate(owned = true, focus = null))
        // What a node below the focused comment brings goes below it.
        val focusAbove = listOf(item("comment:focus", 4, 400, 600), item("comment:x", 6, 1001, 1000))
        assertEquals(false, commentMoreNodeReachedFromBelow(focusAbove, 5, focusedKey = "comment:focus"))
    }
}
