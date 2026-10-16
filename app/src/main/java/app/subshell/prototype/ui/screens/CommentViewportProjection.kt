package app.subshell.prototype.ui.screens

import app.subshell.prototype.model.Comment
import app.subshell.prototype.model.isMoreRepliesNode
import app.subshell.prototype.ui.commentAncestorIdsForTargets
import app.subshell.prototype.ui.knownCommentIds

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
    val firstCandidateRowIndex = lastVisibleRowIndex + 1
    if (firstCandidateRowIndex > rows.lastIndex) return null
    val lastCandidateRowIndex = (
        lastVisibleRowIndex + COMMENT_MORE_PREFETCH_ROW_DISTANCE
    ).coerceAtMost(rows.lastIndex)
    return rows
        .subList(firstCandidateRowIndex, lastCandidateRowIndex + 1)
        .firstOrNull { row ->
            row.kind == CommentViewportRowKind.MORE_REPLIES &&
                row.comment.id != failedMoreNodeId &&
                row.comment.id !in blockedMoreNodeIds &&
                // ISSUE3_CONTINUE_THREAD_NODE_V1: continue nodes load seamlessly too.
                row.comment.isMoreRepliesNode
        }
        ?.comment
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
): Comment? {
    if (
        !fullDiscussionLoaded ||
        fullDiscussionLoading ||
        focusedAnchorOwned ||
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
    return rows
        .subList(firstRow, lastRow + 1)
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
internal fun commentViewportVisibleRowLazyIndices(
    visibleItems: List<Pair<Int, String>>,
): Pair<Int, Int> {
    val rowIndices = visibleItems
        .filter { (_, key) -> key.startsWith("comment:") || key.startsWith("more:") }
        .map { (index, _) -> index }
    return (rowIndices.minOrNull() ?: -1) to (rowIndices.maxOrNull() ?: -1)
}

internal data class CommentHydrationCommitFence(
    val moreNodeId: String,
    val sourceContentRevision: Long,
    val gestureGeneration: Int,
)

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
            put(
                commentId,
                visual.copy(
                    layoutRailCount = layoutRailCount,
                    continuesFromPreviousRailIds = visual.railIds
                        .filterTo(linkedSetOf()) { railId -> railId in previousRailIds },
                    continuesToNextRailIds = visual.railIds
                        .filterTo(linkedSetOf()) { railId -> railId in nextRailIds },
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
): Boolean =
    stateMatchesTargetQuery &&
        !loading &&
        loadingMoreNodeId == null &&
        (
            previousRevision < 0L ||
                contentRevision > previousRevision ||
                hasFailure ||
                failedMoreNodeId != null
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

internal fun commentViewportShowsReplyContext(depth: Int): Boolean =
    depth > COMMENT_VISUAL_DEPTH_CAP

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
