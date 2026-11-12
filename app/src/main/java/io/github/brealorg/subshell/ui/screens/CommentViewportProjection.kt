package io.github.brealorg.subshell.ui.screens

import io.github.brealorg.subshell.model.Comment
import io.github.brealorg.subshell.model.isMoreRepliesNode
import io.github.brealorg.subshell.ui.commentAncestorIdsForTargets
import io.github.brealorg.subshell.ui.knownCommentIds

internal enum class CommentViewportRowKind {
    COMMENT,
    MORE_REPLIES,
}

// ISSUE205_THREAD_SAYS_MORE_CAN_LOAD_V1 (#205): while a thread is loading or
// still has replies behind a more node, its scroll position indicator must
// not reach the bottom (owner's recording: it hit the bottom at the end of
// what was loaded and jumped up as replies arrived).
internal fun commentThreadHasMoreToLoad(
    rows: List<CommentViewportRow>,
    loading: Boolean,
): Boolean = loading || rows.any { row -> row.kind == CommentViewportRowKind.MORE_REPLIES }

internal data class CommentViewportRow(
    val comment: Comment,
    val kind: CommentViewportRowKind,
) {
    val key: String
        get() = when (kind) {
            CommentViewportRowKind.COMMENT -> "comment:${comment.id}"
            CommentViewportRowKind.MORE_REPLIES -> "more:${comment.id}"
        }
}

internal fun commentHydrationMaterializedCommentIds(
    previousViewportComments: List<Comment>,
    hydratedComments: List<Comment>,
): Set<String> =
    knownCommentIds(hydratedComments) - knownCommentIds(previousViewportComments)

internal fun commentFocusedPathIds(
    comments: List<Comment>,
    focusedCommentId: String?,
): Set<String> = focusedCommentId?.let { targetId ->
    commentAncestorIdsForTargets(
        comments = comments,
        targetIds = setOf(targetId),
    ) + targetId
}.orEmpty()

internal fun visibleCommentRows(
    comments: List<Comment>,
    collapsedCommentIds: Set<String>,
): List<CommentViewportRow> = buildList {
    val seenRowKeys = linkedSetOf<String>()

    fun append(comment: Comment) {
        val kind = if (comment.isMoreRepliesNode) {
            CommentViewportRowKind.MORE_REPLIES
        } else {
            CommentViewportRowKind.COMMENT
        }
        val row = CommentViewportRow(comment = comment, kind = kind)
        if (!seenRowKeys.add(row.key)) return
        add(row)
        if (
            kind == CommentViewportRowKind.COMMENT &&
            comment.id !in collapsedCommentIds
        ) {
            comment.children.forEach(::append)
        }
    }
    comments.forEach(::append)
}

internal fun duplicateVisibleCommentRowKeys(
    comments: List<Comment>,
    collapsedCommentIds: Set<String>,
): Set<String> = buildSet {
    val seenRowKeys = linkedSetOf<String>()

    fun inspect(comment: Comment) {
        val kind = if (comment.isMoreRepliesNode) {
            CommentViewportRowKind.MORE_REPLIES
        } else {
            CommentViewportRowKind.COMMENT
        }
        val row = CommentViewportRow(comment = comment, kind = kind)
        if (!seenRowKeys.add(row.key)) {
            add(row.key)
            return
        }
        if (
            kind == CommentViewportRowKind.COMMENT &&
            comment.id !in collapsedCommentIds
        ) {
            comment.children.forEach(::inspect)
        }
    }
    comments.forEach(::inspect)
}


internal fun commentViewportLazyListIndex(
    rows: List<CommentViewportRow>,
    rowKey: String,
    addCommentVisible: Boolean,
    liveStatusVisible: Boolean,
): Int {
    val rowIndex = rows.indexOfFirst { row -> row.key == rowKey }
    if (rowIndex < 0) return -1
    val prefixItems = 3 +
        (if (addCommentVisible) 1 else 0) +
        (if (liveStatusVisible) 1 else 0)
    return prefixItems + rowIndex
}

internal fun commentViewportAutoHydrationCandidate(
    rows: List<CommentViewportRow>,
    firstVisibleLazyIndex: Int,
    lastVisibleLazyIndex: Int,
    addCommentVisible: Boolean,
    liveStatusVisible: Boolean,
    fullDiscussionLoaded: Boolean,
    fullDiscussionLoading: Boolean,
    focusedAnchorOwned: Boolean,
    automaticHydrationBudget: Int,
    loadingMoreNodeId: String?,
    failedMoreNodeId: String?,
    scrollInProgress: Boolean,
    hydrationAnchorPending: Boolean,
    // ISSUE94_FAILED_NODES_WAIT_FOR_RETRY_V1
    blockedMoreNodeIds: Set<String> = emptySet(),
): Comment? {
    if (
        !fullDiscussionLoaded ||
        fullDiscussionLoading ||
        focusedAnchorOwned ||
        automaticHydrationBudget <= 0 ||
        loadingMoreNodeId != null ||
        scrollInProgress ||
        hydrationAnchorPending ||
        firstVisibleLazyIndex < 0 ||
        lastVisibleLazyIndex < firstVisibleLazyIndex ||
        rows.isEmpty()
    ) {
        return null
    }
    val prefixItems = 3 +
        (if (addCommentVisible) 1 else 0) +
        (if (liveStatusVisible) 1 else 0)
    // LOWUI8_PREFETCH_INCLUDES_THE_FIRST_ROW_V1 (OCB-180): -1 when only the
    // header items show; row 0 is then below the screen and a candidate (it
    // was coerced to "visible" and never prefetched).
    val lastVisibleRowIndex = (lastVisibleLazyIndex - prefixItems)
        .coerceIn(-1, rows.lastIndex)
    fun loadable(row: CommentViewportRow) =
        row.kind == CommentViewportRowKind.MORE_REPLIES &&
            row.comment.id != failedMoreNodeId &&
            row.comment.id !in blockedMoreNodeIds &&
            // ISSUE3_CONTINUE_THREAD_NODE_V1: continue nodes load seamlessly too.
            row.comment.isMoreRepliesNode
    val firstCandidateRowIndex = lastVisibleRowIndex + 1
    if (firstCandidateRowIndex <= rows.lastIndex) {
        val lastCandidateRowIndex = (
            lastVisibleRowIndex + COMMENT_MORE_PREFETCH_ROW_DISTANCE
        ).coerceAtMost(rows.lastIndex)
        rows.subList(firstCandidateRowIndex, lastCandidateRowIndex + 1)
            .firstOrNull(::loadable)
            ?.let { row -> return row.comment }
    }
    // AUDIT298B_PREFETCH_ABOVE_THE_SCREEN_TOO_V1 (owner's Pixel 2026-11-12: of
    // the loads a reader saw as "Loading replies…", all but the landing's came
    // while scrolling up, where nothing was prefetched): the nearest node in
    // the rows just above the screen loads too. It is behind the reader's
    // rows, so it loads from its end and its rows go in above the screen
    // (commentMoreNodeReachedFromBelow, commentHydrationAnchor).
    val firstVisibleRowIndex = firstVisibleLazyIndex - prefixItems
    if (firstVisibleRowIndex <= 0) return null
    val highest = (firstVisibleRowIndex - COMMENT_MORE_PREFETCH_ROW_DISTANCE).coerceAtLeast(0)
    for (rowIndex in (firstVisibleRowIndex - 1).coerceAtMost(rows.lastIndex) downTo highest) {
        if (loadable(rows[rowIndex])) return rows[rowIndex].comment
    }
    return null
}

/**
 * UIPOST04_SEAMLESS_MORE_REPLIES_V1: the first idle "more replies" node that
 * is already on screen. [commentViewportAutoHydrationCandidate] only
 * prefetches below the screen (and never a visible node), so a node already
 * visible (a short thread, a fling, a spent budget) was never loaded. It is
 * loaded through the automatic path once nobody scrolls, without the
 * per-gesture budget; loading stays seamless, with no tap.
 */
internal fun commentViewportVisibleIdleMoreNode(
    rows: List<CommentViewportRow>,
    firstVisibleLazyIndex: Int,
    lastVisibleLazyIndex: Int,
    addCommentVisible: Boolean,
    liveStatusVisible: Boolean,
    fullDiscussionLoaded: Boolean,
    fullDiscussionLoading: Boolean,
    focusedAnchorOwned: Boolean,
    loadingMoreNodeId: String?,
    failedMoreNodeId: String?,
    scrollInProgress: Boolean,
    hydrationAnchorPending: Boolean,
    // ISSUE94_FAILED_NODES_WAIT_FOR_RETRY_V1
    blockedMoreNodeIds: Set<String> = emptySet(),
    // ISSUE298_FOCUSED_REPLIES_LOAD_ON_ARRIVAL_V1 (#298, owner: "skulle gjerne
    // ha sett kommentarene under"): while a landing holds the focused comment,
    // a node below it on screen loads at once (what it brings goes below the
    // comment, which does not move); nodes above wait for the reader's scroll.
    focusedCommentId: String? = null,
): Comment? {
    val focusedRow = if (focusedAnchorOwned && focusedCommentId != null) {
        rows.indexOfFirst { row ->
            row.kind == CommentViewportRowKind.COMMENT && row.comment.id == focusedCommentId
        }
    } else {
        -1
    }
    if (
        !fullDiscussionLoaded ||
        fullDiscussionLoading ||
        (focusedAnchorOwned && focusedRow < 0) ||
        loadingMoreNodeId != null ||
        scrollInProgress ||
        hydrationAnchorPending ||
        firstVisibleLazyIndex < 0 ||
        lastVisibleLazyIndex < firstVisibleLazyIndex ||
        rows.isEmpty()
    ) {
        return null
    }
    val prefixItems = 3 +
        (if (addCommentVisible) 1 else 0) +
        (if (liveStatusVisible) 1 else 0)
    val firstRow = (firstVisibleLazyIndex - prefixItems).coerceAtLeast(0)
    val lastRow = (lastVisibleLazyIndex - prefixItems).coerceAtMost(rows.lastIndex)
    if (firstRow > lastRow) return null
    val fromRow = if (focusedRow >= 0) maxOf(firstRow, focusedRow + 1) else firstRow
    if (fromRow > lastRow) return null
    return rows
        .subList(fromRow, lastRow + 1)
        .firstOrNull { row ->
            row.kind == CommentViewportRowKind.MORE_REPLIES &&
                row.comment.id != failedMoreNodeId &&
                row.comment.id !in blockedMoreNodeIds &&
                // ISSUE3_CONTINUE_THREAD_NODE_V1: continue nodes load seamlessly too.
                row.comment.isMoreRepliesNode
        }
        ?.comment
}

// ISSUE93_VISIBLE_RANGE_IGNORES_THE_STICKY_HEADER_V1: the pinned comment
// header is in visibleItemsInfo with an index above the first row, so taking
// the smallest visible index made the "on screen" range start at row 0 and
// loaded idle nodes the reader had flung past. Only comment and more rows count.
// ISSUE269_ROWS_UNDER_THE_HEADER_ARE_NOT_ON_SCREEN_V1 (#269): a row wholly
// covered by the stuck header is not on screen either. A jump to the next
// top-level comment leaves the previous comment's idle "more replies" row
// right above it, under the header; counted as visible it loaded as soon as
// the jump stopped and its replies pushed the comment off the screen. It
// loads when the reader scrolls up to it.
internal data class VisibleLazyCommentItem(
    val index: Int,
    val key: String,
    val offset: Int,
    val size: Int,
)

internal fun commentViewportVisibleRowLazyIndices(
    visibleItems: List<VisibleLazyCommentItem>,
): Pair<Int, Int> {
    val header = visibleItems.firstOrNull { item -> item.key == COMMENT_CONTROLS_ITEM_KEY }
    val readingLine = header?.let { it.offset + it.size } ?: Int.MIN_VALUE
    val rowIndices = visibleItems
        .filter { item -> item.key.startsWith("comment:") || item.key.startsWith("more:") }
        .filter { item -> item.offset + item.size > readingLine }
        .map(VisibleLazyCommentItem::index)
    return (rowIndices.minOrNull() ?: -1) to (rowIndices.maxOrNull() ?: -1)
}

internal data class CommentHydrationCommitFence(
    val moreNodeId: String,
    val sourceContentRevision: Long,
    val gestureGeneration: Int,
    // ISSUE298_MORE_LOADS_FROM_THE_END_THE_READER_COMES_FROM_V1: the node was
    // reached from below; the rows below it hold still when its rows go in.
    val reachedFromBelow: Boolean = false,
    // AUDIT298C_LOADING_ROW_STAYS_QUIET_V1: a Retry the reader tapped shows its
    // "Loading replies…" at once; automatic loads only when they take long.
    val userInitiated: Boolean = false,
)

/** AUDIT298C_LOADING_ROW_STAYS_QUIET_V1: how long an automatic load stays a 1dp row. */
internal const val COMMENT_LOADING_ROW_QUIET_MS = 500L

internal fun commentHydrationCommitCanPublish(
    fence: CommentHydrationCommitFence,
    currentContentRevision: Long,
    loadingMoreNodeId: String?,
    failedMoreNodeId: String?,
    scrollInProgress: Boolean,
): Boolean =
    currentContentRevision > fence.sourceContentRevision &&
        loadingMoreNodeId == null &&
        failedMoreNodeId != fence.moreNodeId &&
        !scrollInProgress

internal fun commentTransportMoreRowVisible(
    fullDiscussionReady: Boolean,
    fullDiscussionLoading: Boolean,
    loading: Boolean,
    failed: Boolean,
): Boolean =
    !fullDiscussionReady || fullDiscussionLoading || loading || failed

internal data class CommentBranchRailVisual(
    val actualDepth: Int,
    val railIds: List<String>,
    val railAccents: List<Long>,
    val startsNewBranchLane: Boolean,
    val parentAuthor: String?,
    val layoutRailCount: Int,
    val continuesFromPreviousRailIds: Set<String>,
    val continuesToNextRailIds: Set<String>,
    // ISSUE285_THREAD_COLOURS_V1: the next comment on screen starts another thread (or there is none).
    val endsThread: Boolean = false,
    // REPLYCONTEXT03_RAIL_STARTS_IN_THE_PARENT_V1 (reader idea, r/subshell 1xsnbqe): the lines
    // a reply starts begin in the middle of the comment that was answered, not at the reply.
    // Indices (in the next row's rails) of the rails the next row, a direct reply, starts.
    val startsInNextRowRailIndices: List<Int> = emptyList(),
    // The rails of this row that the previous row, its parent, started in its middle.
    val startedByPreviousRowRailIds: Set<String> = emptySet(),
) {
    val branchRailCount: Int
        get() = railAccents.size
}

internal fun commentBranchRailVisualByCommentId(
    comments: List<Comment>,
    visibleRows: List<CommentViewportRow> = visibleCommentRows(
        comments = comments,
        collapsedCommentIds = emptySet(),
    ),
): Map<String, CommentBranchRailVisual> {
    val rawVisuals = buildMap<String, CommentBranchRailVisual> {
        val seenCommentIds = linkedSetOf<String>()

        fun realChildren(comment: Comment): List<Comment> =
            comment.children.filterNot { child -> child.isMoreRepliesNode }

        fun visit(
            node: Comment,
            railIds: List<String>,
            railAccents: List<Long>,
            parent: Comment?,
            startsNewBranchLane: Boolean,
        ) {
            if (node.isMoreRepliesNode || !seenCommentIds.add(node.id)) return
            put(
                node.id,
                CommentBranchRailVisual(
                    actualDepth = node.depth,
                    railIds = railIds,
                    railAccents = railAccents,
                    startsNewBranchLane = startsNewBranchLane,
                    parentAuthor = parent?.author,
                    layoutRailCount = railAccents.size,
                    continuesFromPreviousRailIds = emptySet(),
                    continuesToNextRailIds = emptySet(),
                ),
            )

            val children = realChildren(node)
            val branchesHere = children.size > 1
            children.forEach { child ->
                val nextRailIds = when {
                    railIds.isEmpty() && branchesHere -> listOf(child.id)
                    railIds.isEmpty() -> listOf(node.id)
                    branchesHere -> railIds + child.id
                    else -> railIds
                }
                val nextRailAccents = when {
                    railAccents.isEmpty() && branchesHere -> listOf(child.accent)
                    railAccents.isEmpty() -> listOf(node.accent)
                    branchesHere -> railAccents + child.accent
                    else -> railAccents
                }
                visit(
                    node = child,
                    railIds = nextRailIds,
                    railAccents = nextRailAccents,
                    parent = node,
                    startsNewBranchLane = branchesHere,
                )
            }
        }

        comments
            .filterNot { comment -> comment.isMoreRepliesNode }
            .forEach { root ->
                visit(
                    node = root,
                    railIds = emptyList(),
                    railAccents = emptyList(),
                    parent = null,
                    startsNewBranchLane = false,
                )
            }
    }

    val layoutRailCount = rawVisuals.values.maxOfOrNull(CommentBranchRailVisual::branchRailCount) ?: 0
    val visibleCommentIds = visibleRows
        .asSequence()
        .filter { row -> row.kind == CommentViewportRowKind.COMMENT }
        .map { row -> row.comment.id }
        .filter(rawVisuals::containsKey)
        .toList()

    return buildMap {
        visibleCommentIds.forEachIndexed { index, commentId ->
            val visual = rawVisuals.getValue(commentId)
            val previousRailIds = visibleCommentIds
                .getOrNull(index - 1)
                ?.let(rawVisuals::get)
                ?.railIds
                .orEmpty()
                .toSet()
            val nextRailIds = visibleCommentIds
                .getOrNull(index + 1)
                ?.let(rawVisuals::get)
                ?.railIds
                .orEmpty()
                .toSet()
            val previousVisual = visibleCommentIds.getOrNull(index - 1)?.let(rawVisuals::get)
            val nextVisual = visibleCommentIds.getOrNull(index + 1)?.let(rawVisuals::get)
            put(
                commentId,
                visual.copy(
                    startsInNextRowRailIndices = if (
                        nextVisual != null && nextVisual.actualDepth == visual.actualDepth + 1
                    ) {
                        nextVisual.railIds.indices.filter { railIndex ->
                            nextVisual.railIds[railIndex] !in visual.railIds
                        }
                    } else {
                        emptyList()
                    },
                    startedByPreviousRowRailIds = if (
                        previousVisual != null && visual.actualDepth == previousVisual.actualDepth + 1
                    ) {
                        visual.railIds.filterTo(linkedSetOf()) { railId ->
                            railId !in previousVisual.railIds
                        }
                    } else {
                        emptySet()
                    },
                    layoutRailCount = layoutRailCount,
                    continuesFromPreviousRailIds = visual.railIds
                        .filterTo(linkedSetOf()) { railId -> railId in previousRailIds },
                    continuesToNextRailIds = visual.railIds
                        .filterTo(linkedSetOf()) { railId -> railId in nextRailIds },
                    endsThread = visibleCommentIds
                        .getOrNull(index + 1)
                        ?.let(rawVisuals::get)
                        ?.let { next -> next.actualDepth == 0 } ?: true,
                ),
            )
        }
    }
}

internal fun commentBranchRailGutterDp(
    branchRailCount: Int,
    layoutRailCount: Int = branchRailCount,
): Int {
    if (branchRailCount <= 0) return 0
    require(layoutRailCount >= branchRailCount)
    val trailingPadding = COMMENT_BRANCH_RAIL_BASE_GUTTER_DP - COMMENT_BRANCH_RAIL_EDGE_PADDING_DP
    val lastRailX = commentBranchRailPositionDp(
        branchRailIndex = branchRailCount - 1,
        branchRailCount = layoutRailCount,
    )
    return (lastRailX + trailingPadding.toFloat() + 0.999f)
        .toInt()
        .coerceAtMost(COMMENT_BRANCH_RAIL_MAX_GUTTER_DP)
}

internal fun commentBranchRailPositionDp(
    branchRailIndex: Int,
    branchRailCount: Int,
): Float {
    require(branchRailCount > 0)
    require(branchRailIndex in 0 until branchRailCount)
    val start = COMMENT_BRANCH_RAIL_EDGE_PADDING_DP.toFloat()
    if (branchRailCount == 1) return start
    val trailingPadding = COMMENT_BRANCH_RAIL_BASE_GUTTER_DP - COMMENT_BRANCH_RAIL_EDGE_PADDING_DP
    val maxRailX = (
        COMMENT_BRANCH_RAIL_MAX_GUTTER_DP - trailingPadding
        ).toFloat().coerceAtLeast(start)
    val stableSpacing = minOf(
        COMMENT_BRANCH_RAIL_PREFERRED_SPACING_DP.toFloat(),
        (maxRailX - start) / (branchRailCount - 1).toFloat(),
    )
    return start + (stableSpacing * branchRailIndex.toFloat())
}


internal data class CommentParentConnectorLane(
    val connectorDepth: Int,
    val accent: Long,
)

internal data class CommentParentConnectorVisual(
    val actualDepth: Int,
    val structuralDepth: Int,
    val parentAuthor: String?,
    val incomingLane: CommentParentConnectorLane?,
    val ancestorContinuationLanes: List<CommentParentConnectorLane>,
    val incomingContinuesToNextSibling: Boolean,
    val outgoingLane: CommentParentConnectorLane?,
) {
    val gutterDp: Int
        get() = commentParentConnectorGutterDp(structuralDepth)
}

/**
 * ISSUE10 parent-edge projection.
 *
 * Unlike branch guide rails, these lanes represent actual parent -> child
 * edges. A parent's lane is kept alive through an earlier sibling subtree
 * when another direct sibling follows, then terminates at the final child.
 */
// LOWUI4 (OCB-181): the reply context's parent author comes from these
// connector visuals (and the branch rails); the old commentParentAuthorByCommentId
// had no production caller and is gone.
internal fun commentParentConnectorVisualByCommentId(
    comments: List<Comment>,
    visibleRows: List<CommentViewportRow> = visibleCommentRows(
        comments = comments,
        collapsedCommentIds = emptySet(),
    ),
): Map<String, CommentParentConnectorVisual> {
    val visibleCommentIds = visibleRows
        .asSequence()
        .filter { row -> row.kind == CommentViewportRowKind.COMMENT }
        .map { row -> row.comment.id }
        .toSet()

    val visuals = linkedMapOf<String, CommentParentConnectorVisual>()

    fun visibleRealChildren(comment: Comment): List<Comment> =
        comment.children.filter { child ->
            !child.isMoreRepliesNode && child.id in visibleCommentIds
        }

    fun visitSiblings(
        nodes: List<Comment>,
        parent: Comment?,
        structuralDepth: Int,
        ancestorContinuationLanes: List<CommentParentConnectorLane>,
    ) {
        val visibleNodes = nodes.filter { node ->
            !node.isMoreRepliesNode && node.id in visibleCommentIds
        }

        visibleNodes.forEachIndexed { index, node ->
            val hasNextVisibleSibling = index < visibleNodes.lastIndex

            val incomingLane = if (
                parent != null &&
                structuralDepth in 1..COMMENT_VISUAL_DEPTH_CAP
            ) {
                CommentParentConnectorLane(
                    connectorDepth = structuralDepth - 1,
                    accent = parent.accent,
                )
            } else {
                null
            }

            val children = visibleRealChildren(node)

            val outgoingLane = if (
                children.isNotEmpty() &&
                structuralDepth < COMMENT_VISUAL_DEPTH_CAP
            ) {
                CommentParentConnectorLane(
                    connectorDepth = structuralDepth,
                    accent = node.accent,
                )
            } else {
                null
            }

            visuals[node.id] = CommentParentConnectorVisual(
                actualDepth = node.depth,
                structuralDepth = structuralDepth,
                parentAuthor = parent?.author,
                incomingLane = incomingLane,
                ancestorContinuationLanes = ancestorContinuationLanes,
                incomingContinuesToNextSibling = hasNextVisibleSibling,
                outgoingLane = outgoingLane,
            )

            val descendantContinuations = buildList {
                addAll(ancestorContinuationLanes)
                if (incomingLane != null && hasNextVisibleSibling) {
                    add(incomingLane)
                }
            }.distinctBy(CommentParentConnectorLane::connectorDepth)

            visitSiblings(
                nodes = children,
                parent = node,
                structuralDepth = structuralDepth + 1,
                ancestorContinuationLanes = descendantContinuations,
            )
        }
    }

    visitSiblings(
        nodes = comments,
        parent = null,
        structuralDepth = 0,
        ancestorContinuationLanes = emptyList(),
    )

    return visuals
}

internal fun commentParentConnectorGutterDp(
    structuralDepth: Int,
): Int {
    val railCount = structuralDepth.coerceIn(0, COMMENT_VISUAL_DEPTH_CAP)
    return commentBranchRailGutterDp(
        branchRailCount = railCount,
        layoutRailCount = COMMENT_VISUAL_DEPTH_CAP,
    )
}

internal fun commentParentConnectorPositionDp(
    connectorDepth: Int,
): Float {
    require(connectorDepth in 0 until COMMENT_VISUAL_DEPTH_CAP)
    return commentBranchRailPositionDp(
        branchRailIndex = connectorDepth,
        branchRailCount = COMMENT_VISUAL_DEPTH_CAP,
    )
}


internal fun focusedCommentViewportRowIndex(
    rows: List<CommentViewportRow>,
    focusedCommentId: String?,
): Int = if (focusedCommentId == null) {
    -1
} else {
    rows.indexOfFirst { row ->
        row.kind == CommentViewportRowKind.COMMENT &&
            row.comment.id == focusedCommentId
    }
}

internal fun focusedCommentLazyListIndex(
    rows: List<CommentViewportRow>,
    focusedCommentId: String?,
    addCommentVisible: Boolean,
    liveStatusVisible: Boolean,
): Int {
    val targetId = focusedCommentId ?: return -1
    return commentViewportLazyListIndex(
        rows = rows,
        rowKey = "comment:$targetId",
        addCommentVisible = addCommentVisible,
        liveStatusVisible = liveStatusVisible,
    )
}

internal fun focusedCommentAnchorOffsetPx(
    viewportStartOffset: Int,
    viewportEndOffset: Int,
    anchorFraction: Float = FOCUSED_COMMENT_NORMAL_ANCHOR_FRACTION,
): Int {
    require(anchorFraction in 0f..1f)
    val viewportSize = (viewportEndOffset - viewportStartOffset).coerceAtLeast(0)
    return viewportStartOffset + (viewportSize * anchorFraction).toInt()
}


internal fun focusedCommentAcquireBaselineRevision(
    stateMatchesTargetQuery: Boolean,
    contentRevision: Long,
): Long = if (stateMatchesTargetQuery) contentRevision else -1L

internal fun focusedCommentTargetMatchesQuery(
    stateMatchesTargetQuery: Boolean,
    targetPresent: Boolean,
): Boolean = stateMatchesTargetQuery && targetPresent

internal fun focusedCommentStateAdvanceCompleted(
    previousRevision: Long,
    stateMatchesTargetQuery: Boolean,
    loading: Boolean,
    loadingMoreNodeId: String?,
    contentRevision: Long,
    hasFailure: Boolean,
    failedMoreNodeId: String?,
    // AUDIT298C_RETRY_DOES_NOT_REUSE_THE_OLD_FAILURE_V1: false while the
    // screen still shows the state from before the request (its "loading" not
    // drawn yet); a failure in it is the previous one, not this request's answer.
    failureIsFresh: Boolean = true,
): Boolean =
    stateMatchesTargetQuery &&
        !loading &&
        loadingMoreNodeId == null &&
        (
            previousRevision < 0L ||
                contentRevision > previousRevision ||
                (failureIsFresh && (hasFailure || failedMoreNodeId != null))
            )

internal fun focusedCommentRequestScrollOffsetPx(
    viewportStartOffset: Int,
    viewportEndOffset: Int,
    anchorFraction: Float = FOCUSED_COMMENT_NORMAL_ANCHOR_FRACTION,
): Int = -focusedCommentAnchorOffsetPx(
    viewportStartOffset = viewportStartOffset,
    viewportEndOffset = viewportEndOffset,
    anchorFraction = anchorFraction,
)

internal fun focusedCommentOffsetDeltaPx(
    itemOffset: Int,
    viewportStartOffset: Int,
    viewportEndOffset: Int,
    anchorFraction: Float = FOCUSED_COMMENT_NORMAL_ANCHOR_FRACTION,
): Int = itemOffset - focusedCommentAnchorOffsetPx(
    viewportStartOffset = viewportStartOffset,
    viewportEndOffset = viewportEndOffset,
    anchorFraction = anchorFraction,
)

internal fun focusedCommentLayoutIdentityMatches(
    focusedCommentId: String,
    expectedIndex: Int,
    observedTargetIndex: Int?,
    observedKeyAtExpectedIndex: String?,
): Boolean {
    if (expectedIndex < 0) return false
    val targetKey = "comment:$focusedCommentId"
    return observedTargetIndex == expectedIndex &&
        observedKeyAtExpectedIndex == targetKey
}

internal fun focusedCommentOffsetVerified(
    itemOffset: Int,
    viewportStartOffset: Int,
    viewportEndOffset: Int,
    tolerancePx: Int,
    anchorFraction: Float = FOCUSED_COMMENT_NORMAL_ANCHOR_FRACTION,
): Boolean {
    if (tolerancePx < 0) return false
    return kotlin.math.abs(
        focusedCommentOffsetDeltaPx(
            itemOffset = itemOffset,
            viewportStartOffset = viewportStartOffset,
            viewportEndOffset = viewportEndOffset,
            anchorFraction = anchorFraction,
        ),
    ) <= tolerancePx
}

internal fun focusedCommentBoundaryConstrainedLandingVerified(
    itemOffset: Int,
    itemSize: Int,
    viewportStartOffset: Int,
    viewportEndOffset: Int,
    requestedDeltaPx: Int,
    canScrollBackward: Boolean,
    canScrollForward: Boolean,
): Boolean {
    val viewportSize = (viewportEndOffset - viewportStartOffset).coerceAtLeast(0)
    if (viewportSize == 0 || itemSize <= 0 || requestedDeltaPx == 0) return false
    val itemEnd = itemOffset + itemSize
    val visibleEnough = if (itemSize <= viewportSize) {
        itemOffset >= viewportStartOffset && itemEnd <= viewportEndOffset
    } else {
        itemOffset <= viewportEndOffset && itemEnd >= viewportStartOffset
    }
    if (!visibleEnough) return false
    return when {
        requestedDeltaPx > 0 -> !canScrollForward
        requestedDeltaPx < 0 -> !canScrollBackward
        else -> false
    }
}

internal fun commentViewportInsetDp(depth: Int): Int =
    depth.coerceIn(0, COMMENT_VISUAL_DEPTH_CAP) * COMMENT_THREAD_INSET_DP

// ISSUE285_DEEP_REPLIES_SAY_WHO_THEY_ANSWER_V1 (#285, owner chose sketch 1):
// from the third level a reply names the comment it answers; that is where
// the lines on the left start to need counting (before: only past the indent cap).
internal fun commentViewportShowsReplyContext(depth: Int): Boolean =
    depth >= COMMENT_VISUAL_DEPTH_CAP

// REPLYCONTEXT02_REPLIES_INDENT_WITH_DEPTH_V1 (reader feedback, r/subshell
// 1xsnbqe): with Guide lines a rail is only added where a thread branches, so
// a chain of single replies kept the same left edge. Content now also steps
// right by a small amount per level, up to a deeper cap than the old two
// levels; the coloured lines stay. "↳ name" is for replies past that cap.
internal fun commentDepthIndentDp(
    depth: Int,
    stepDp: Int = COMMENT_DEPTH_INDENT_STEP_DP,
): Int = depth.coerceIn(0, COMMENT_DEPTH_INDENT_CAP) * stepDp

// With no indent the levels are not shown by position, so the reply line starts
// where #285 first put it (the third level).
internal fun commentDepthShowsReplyContext(
    depth: Int,
    stepDp: Int = COMMENT_DEPTH_INDENT_STEP_DP,
): Boolean = if (stepDp <= 0) {
    depth >= COMMENT_VISUAL_DEPTH_CAP
} else {
    depth > COMMENT_DEPTH_INDENT_CAP
}

internal const val COMMENT_DEPTH_INDENT_STEP_DP = 6
internal const val COMMENT_DEPTH_INDENT_CAP = 6

internal fun focusedCommentLandingAnchorFraction(
    itemSizePx: Int,
    viewportStartOffset: Int,
    viewportEndOffset: Int,
): Float {
    val viewportSize = (viewportEndOffset - viewportStartOffset).coerceAtLeast(0)
    if (viewportSize == 0 || itemSizePx <= 0) return FOCUSED_COMMENT_NORMAL_ANCHOR_FRACTION
    val itemFraction = itemSizePx.toFloat() / viewportSize.toFloat()
    return if (itemFraction >= FOCUSED_COMMENT_TALL_ITEM_THRESHOLD_FRACTION) {
        FOCUSED_COMMENT_TALL_ANCHOR_FRACTION
    } else {
        FOCUSED_COMMENT_NORMAL_ANCHOR_FRACTION
    }
}

internal const val FOCUSED_COMMENT_NORMAL_ANCHOR_FRACTION = 0.20f
internal const val FOCUSED_COMMENT_TALL_ANCHOR_FRACTION = 0.12f
internal const val FOCUSED_COMMENT_TALL_ITEM_THRESHOLD_FRACTION = 0.60f
internal const val COMMENT_THREAD_INSET_DP = 8
internal const val COMMENT_VISUAL_DEPTH_CAP = 2
internal const val COMMENT_MORE_PREFETCH_ROW_DISTANCE = 16
internal const val COMMENT_AUTO_HYDRATION_MAX_PER_USER_GESTURE = 2
internal const val COMMENT_BRANCH_RAIL_BASE_GUTTER_DP = 12
internal const val COMMENT_BRANCH_RAIL_PREFERRED_SPACING_DP = 7
internal const val COMMENT_BRANCH_RAIL_MAX_GUTTER_DP = 44
internal const val COMMENT_BRANCH_RAIL_EDGE_PADDING_DP = 3

/**
 * REPLYCONTEXT01_REPLY_SCROLLS_THE_TARGET_ABOVE_THE_COMPOSER_V1: how far the list
 * must scroll (positive = forward) so the comment being answered is fully
 * visible above the composer. [itemTopPx] is the item's offset in the list
 * viewport, [availableBottomPx] where the composer starts in the same
 * coordinates. A comment taller than the free space keeps its top visible.
 */
internal fun replyTargetScrollDeltaPx(
    itemTopPx: Int,
    itemSizePx: Int,
    viewportStartPx: Int,
    availableBottomPx: Int,
    marginPx: Int,
): Int {
    val limit = availableBottomPx - marginPx
    val bottom = itemTopPx + itemSizePx
    return when {
        itemTopPx < viewportStartPx -> itemTopPx - viewportStartPx
        bottom <= limit -> 0
        else -> minOf(bottom - limit, itemTopPx - viewportStartPx)
    }
}

/**
 * ISSUE298_ROWS_LOADING_ABOVE_THE_READER_STAY_ABOVE_V1 (#298): the row the
 * list holds still while loaded comments go in. Compose keeps only the first
 * visible item, and finds it by key only within about a hundred items of
 * where it was. A reply opened from the Inbox sits after a top-level more
 * node, so that node's comments (62 in one commit on the Pixel) went in
 * between the top of the screen and the reader's own comment and pushed it
 * far away. Where the new rows go is read from the lists themselves (the
 * first row that differs), not from whether the more row is on screen: a
 * loaded node is often not (it loads next, or its row is 1dp).
 *
 * - new rows go in below the screen: nothing to do (null);
 * - above the screen: hold the first visible row explicitly (Compose loses
 *   it past ~100 inserted items);
 * - on screen: the focused comment (a reply opened from the Inbox) stays
 *   still when it is on screen below the insertion; otherwise the side the
 *   node was reached from ([holdBelow], decided once when the load started,
 *   see [commentMoreNodeReachedFromBelow]). Deciding again here by what fills
 *   more of the screen moved the rows of a reader who had scrolled on past a
 *   node reached from above (a jump on the Pixel). The direction of the last
 *   scroll was tried first and failed too: a fling up often ends with a
 *   small move down.
 *
 * [CommentViewportVisibleItem.sizePx] is the part of the row inside the
 * viewport.
 */
internal data class CommentHydrationAnchor(
    val key: String,
    val lazyIndex: Int,
    val offsetPx: Int,
)

internal data class CommentViewportVisibleItem(
    val key: String,
    val lazyIndex: Int,
    val offsetPx: Int,
    val sizePx: Int = 0,
)

internal fun commentHydrationAnchor(
    visibleItems: List<CommentViewportVisibleItem>,
    oldRows: List<CommentViewportRow>,
    newRows: List<CommentViewportRow>,
    focusedKey: String? = null,
    holdBelow: Boolean = false,
): CommentHydrationAnchor? {
    val firstChanged = oldRows.indices.firstOrNull { index ->
        index >= newRows.size || oldRows[index].key != newRows[index].key
    } ?: return null
    val oldRowIndex = oldRows.withIndex().associate { (index, row) -> row.key to index }
    val newKeys = newRows.mapTo(HashSet()) { row -> row.key }
    val visibleRows = visibleItems.mapNotNull { item ->
        oldRowIndex[item.key]?.let { rowIndex -> item to rowIndex }
    }
    val above = visibleRows.filter { (_, rowIndex) -> rowIndex < firstChanged }
    val below = visibleRows
        .filter { (item, rowIndex) -> rowIndex >= firstChanged && item.key in newKeys }
        .sortedWith(compareBy({ (item, _) -> !item.key.startsWith("comment:") }, { (item, _) -> item.lazyIndex }))
    if (below.isEmpty()) return null
    below.firstOrNull { (item, _) -> item.key == focusedKey }?.let { (item, _) ->
        return CommentHydrationAnchor(key = item.key, lazyIndex = item.lazyIndex, offsetPx = item.offsetPx)
    }
    // The focused comment above the new rows: they go below it; Compose's
    // first visible row (above it too) keeps it still.
    if (focusedKey != null && above.any { (item, _) -> item.key == focusedKey }) return null
    val keepBelow = holdBelow || above.isEmpty()
    if (!keepBelow) return null
    val chosen = below.first().first
    return CommentHydrationAnchor(key = chosen.key, lazyIndex = chosen.lazyIndex, offsetPx = chosen.offsetPx)
}

/** The anchor's lazy index once [newRows] replace [oldRows]; -1 when it left. */
internal fun commentHydrationAnchorIndexAfter(
    anchor: CommentHydrationAnchor,
    oldRows: List<CommentViewportRow>,
    newRows: List<CommentViewportRow>,
): Int {
    val oldRow = oldRows.indexOfFirst { row -> row.key == anchor.key }
    val newRow = newRows.indexOfFirst { row -> row.key == anchor.key }
    if (oldRow < 0 || newRow < 0) return -1
    return anchor.lazyIndex + (newRow - oldRow)
}


/**
 * ISSUE298_MORE_LOADS_FROM_THE_END_THE_READER_COMES_FROM_V1 (#298): whether a
 * more node is reached from below, so it loads its last comments (placed
 * between it and the rows below). A node above the screen is behind a reader
 * who came up; on screen, the focused comment below it, or rows below it that
 * fill more of the screen than the rows above, mean the same. A node below
 * the screen loads from its start, as before.
 */
internal fun commentMoreNodeReachedFromBelow(
    visibleItems: List<CommentViewportVisibleItem>,
    moreLazyIndex: Int,
    focusedKey: String? = null,
): Boolean {
    if (moreLazyIndex < 0) return false
    val rows = visibleItems.filter { item ->
        item.key.startsWith("comment:") || item.key.startsWith("more:")
    }
    if (rows.isEmpty()) return false
    if (moreLazyIndex < rows.minOf { item -> item.lazyIndex }) return true
    if (moreLazyIndex > rows.maxOf { item -> item.lazyIndex }) return false
    val below = rows.filter { item -> item.lazyIndex > moreLazyIndex }
    val above = rows.filter { item -> item.lazyIndex < moreLazyIndex }
    if (focusedKey != null && below.any { item -> item.key == focusedKey }) return true
    if (focusedKey != null && above.any { item -> item.key == focusedKey }) return false
    return below.sumOf { item -> item.sizePx } > above.sumOf { item -> item.sizePx }
}

/**
 * AUDIT298B_DELETING_THE_LINKED_REPLY_KEEPS_ITS_PARENT_V1: the parent comment
 * of [commentId] in [comments] (a "t1_" fullname), or null for a top-level
 * comment or one not in the tree.
 */
internal fun commentLinkParentToKeep(comments: List<Comment>, commentId: String): String? {
    fun bare(id: String) = id.trim().removePrefix("t1_")
    fun find(nodes: List<Comment>, parent: Comment?): Comment? {
        for (node in nodes) {
            if (bare(node.id) == bare(commentId)) return parent
            find(node.children, node)?.let { return it }
        }
        return null
    }
    return find(comments, null)?.id?.takeIf { it.startsWith("t1_") }
}

/**
 * AUDIT298C_SMALL_NODES_LOAD_TOGETHER_V1 (owner: "veldig mye loading"): small
 * more nodes (at most [COMMENT_SMALL_NODE_IDS] hidden replies) on screen or in
 * the prefetch rows below it, other than [moreNodeId], that can load in the
 * same round; at most [COMMENT_SMALL_NODES_PER_ROUND] - 1 of them. Continue
 * nodes (a whole thread each) and failed nodes are left out.
 */
internal const val COMMENT_SMALL_NODE_IDS = 3
internal const val COMMENT_SMALL_NODES_PER_ROUND = 4

internal fun commentSmallNodesNear(
    rows: List<CommentViewportRow>,
    visibleKeys: Set<String>,
    moreNodeId: String,
    excludedIds: Set<String> = emptySet(),
): List<String> {
    val visibleRows = rows.indices.filter { index -> rows[index].key in visibleKeys }
    if (visibleRows.isEmpty()) return emptyList()
    val last = (visibleRows.max() + COMMENT_MORE_PREFETCH_ROW_DISTANCE).coerceAtMost(rows.lastIndex)
    return (visibleRows.min()..last)
        .map { index -> rows[index].comment }
        .filter { comment ->
            comment.id != moreNodeId &&
                comment.id !in excludedIds &&
                comment.moreChildIds.size in 1..COMMENT_SMALL_NODE_IDS
        }
        .take(COMMENT_SMALL_NODES_PER_ROUND - 1)
        .map { comment -> comment.id }
}

// AUDIT298C_RECREATION_DOES_NOT_LAND_AGAIN_V1: an arrival pulses once; the
// generation already played (saved with the screen) never plays again.
internal fun focusedArrivalPulseShouldPlay(generation: Int, played: Int): Boolean =
    generation > 0 && generation > played

/**
 * AUDIT298C_REPLY_TARGET_NOT_UNDER_THE_HEADER_V1: where reading starts in the
 * comment list: below the sticky sort header when it is on screen, else the
 * viewport's top.
 */
internal fun commentReadingTopPx(viewportStartOffset: Int, headerBottomPx: Int?): Int =
    maxOf(viewportStartOffset, headerBottomPx ?: viewportStartOffset)

/**
 * AUDIT298C_EVERY_TREE_CHANGE_HOLDS_THE_READING_ROW_V1 (audit 2026-11-12):
 * the row a tree change that is not a hydration (sync, pull to refresh, the
 * stored thread, a posted reply, new comments unfolding) holds still: the
 * first comment or more row the reader sees below the sticky header that is
 * still there after the change. Compose holds only its first visible item, and
 * only within about a hundred items; it lost the place when many rows went in
 * above, or when that item itself went away. Null while the post or anything
 * before the comment header is the first thing on screen (Compose's own hold
 * keeps that still, and new comments appear below it), and when nothing
 * changes. [CommentViewportVisibleItem.sizePx] is the row's full size here.
 */
internal fun commentReadingAnchor(
    visibleItems: List<CommentViewportVisibleItem>,
    oldRows: List<CommentViewportRow>,
    newRows: List<CommentViewportRow>,
    readingTopPx: Int,
    headerKey: String,
): CommentHydrationAnchor? {
    if (oldRows.map { row -> row.key } == newRows.map { row -> row.key }) return null
    val first = visibleItems.minByOrNull { item -> item.lazyIndex } ?: return null
    fun isRow(key: String) = key.startsWith("comment:") || key.startsWith("more:")
    if (first.key != headerKey && !isRow(first.key)) return null
    val oldKeys = oldRows.mapTo(HashSet()) { row -> row.key }
    val newKeys = newRows.mapTo(HashSet()) { row -> row.key }
    val chosen = commentReadingRow(
        visibleItems.filter { item -> isRow(item.key) && item.key in oldKeys && item.key in newKeys },
        readingTopPx,
    ) ?: return null
    return CommentHydrationAnchor(key = chosen.key, lazyIndex = chosen.lazyIndex, offsetPx = chosen.offsetPx)
}

/**
 * The row that marks the reader's place among [rows]: the first one whose top
 * is on screen below [readingTopPx], else the first that reaches below it. A
 * row's top is held, not a point inside it: a tall row above (an image not
 * loaded yet after a restart) can change height without moving the place
 * (the Pixel restored 600 px off from an offset 927 px into an image comment).
 */
internal fun commentReadingRow(
    rows: List<CommentViewportVisibleItem>,
    readingTopPx: Int,
): CommentViewportVisibleItem? =
    rows.filter { item -> item.offsetPx >= readingTopPx }.minByOrNull { item -> item.lazyIndex }
        ?: rows.filter { item -> item.offsetPx + item.sizePx > readingTopPx }.minByOrNull { item -> item.lazyIndex }

internal const val FOCUSED_COMMENT_LANDING_SPACE_KEY = "focused-comment-landing-space"

/** AUDIT298C_LANDING_SPACE_SHRINKS_OUT_OF_SIGHT_V1: the part of the space inside the viewport. */
internal fun focusedLandingSpaceOnScreenPx(itemOffsetPx: Int, itemSizePx: Int, viewportEndOffset: Int): Int =
    (viewportEndOffset - itemOffsetPx).coerceIn(0, itemSizePx.coerceAtLeast(0))

/**
 * AUDIT298C_LANDING_SPACE_SHRINKS_OUT_OF_SIGHT_V1: while the link holds its
 * comment, the space kept later is what is on screen now; after that it only
 * shrinks, to what is still on screen (the rest is below the screen, so
 * giving it up moves nothing the reader sees).
 */
internal fun focusedLandingSpaceToKeepPx(anchorOwned: Boolean, keptPx: Int, onScreenPx: Int): Int =
    if (anchorOwned) onScreenPx else minOf(keptPx, onScreenPx)

/** AUDIT298C_POSITION_IS_SAVED_BY_KEY_V1: how long a saved row may take to come back. */
internal const val READING_ROW_RESTORE_WAIT_MS = 15_000L

/**
 * AUDIT298C_POSITION_IS_SAVED_BY_KEY_V1: the row to save as the reader's
 * place: the first comment or more row below the sticky header, when the
 * comments fill the top of the screen; null while the post or anything else
 * before the comment header is the first thing on screen (its index does not
 * change, so the saved index is enough there).
 */
internal fun commentReadingRowToSave(
    visibleItems: List<CommentViewportVisibleItem>,
    readingTopPx: Int,
    headerKey: String,
): CommentViewportVisibleItem? {
    val first = visibleItems.minByOrNull { item -> item.lazyIndex } ?: return null
    fun isRow(key: String) = key.startsWith("comment:") || key.startsWith("more:")
    if (first.key != headerKey && !isRow(first.key)) return null
    return commentReadingRow(visibleItems.filter { item -> isRow(item.key) }, readingTopPx)
}

/** AUDIT298C_POSITION_IS_SAVED_BY_KEY_V1: how long a restored row is held while images load. */
internal const val READING_ROW_SETTLE_MS = 4_000L
