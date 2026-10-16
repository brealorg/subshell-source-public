package app.subshell.prototype.ui.screens

import app.subshell.prototype.model.Comment
import app.subshell.prototype.ui.CommentVisitBaseline
import app.subshell.prototype.ui.knownCommentIds
import app.subshell.prototype.ui.newCommentIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Profile01a5FlattenedCommentViewportPolicyTest {
    @Test
    fun everyVisibleCommentAndMoreNodeBecomesItsOwnStableRow() {
        val comments = listOf(
            comment(
                id = "t1_root",
                depth = 0,
                children = listOf(
                    comment("t1_child", depth = 1),
                    more(
                        id = "more:t1_root:next",
                        depth = 1,
                        childIds = listOf("later"),
                    ),
                ),
            ),
        )

        val rows = visibleCommentRows(comments, collapsedCommentIds = emptySet())

        assertEquals(
            listOf("comment:t1_root", "comment:t1_child", "more:more:t1_root:next"),
            rows.map(CommentViewportRow::key),
        )
        assertEquals(
            listOf(
                CommentViewportRowKind.COMMENT,
                CommentViewportRowKind.COMMENT,
                CommentViewportRowKind.MORE_REPLIES,
            ),
            rows.map(CommentViewportRow::kind),
        )
    }

    @Test
    fun collapsedParentRemainsVisibleButItsDescendantsLeaveTheViewportProjection() {
        val comments = listOf(
            comment(
                id = "t1_root",
                depth = 0,
                children = listOf(comment("t1_child", depth = 1)),
            ),
        )

        val rows = visibleCommentRows(comments, collapsedCommentIds = setOf("t1_root"))

        assertEquals(listOf("t1_root"), rows.map { row -> row.comment.id })
        assertFalse(rows.any { row -> row.comment.id == "t1_child" })
    }

    @Test
    fun canonicalHandoffRevealsTheTargetsCanonicalPathWhenParentageChanges() {
        val canonical = listOf(
            comment(
                id = "t1_new_root",
                depth = 0,
                children = listOf(
                    comment(
                        id = "t1_new_parent",
                        depth = 1,
                        children = listOf(comment("t1_target", depth = 2)),
                    ),
                ),
            ),
        )
        val collapsedBeforeHandoff = setOf("t1_new_parent")
        val canonicalFocusedPath = commentFocusedPathIds(
            comments = canonical,
            focusedCommentId = "t1_target",
        )
        val rowsAfterCanonicalReveal = visibleCommentRows(
            comments = canonical,
            collapsedCommentIds = collapsedBeforeHandoff - canonicalFocusedPath,
        )

        assertEquals(
            setOf("t1_new_root", "t1_new_parent", "t1_target"),
            canonicalFocusedPath,
        )
        assertTrue(rowsAfterCanonicalReveal.any { row -> row.key == "comment:t1_target" })
    }

    @Test
    fun focusedTargetHasAnExactFlatRowIndexInsteadOfOnlyATopLevelBranchIndex() {
        val comments = listOf(
            comment("t1_before", depth = 0),
            comment(
                id = "t1_root",
                depth = 0,
                children = listOf(
                    comment("t1_parent", depth = 1, children = listOf(
                        comment("t1_target", depth = 2),
                    )),
                ),
            ),
        )

        val rows = visibleCommentRows(comments, collapsedCommentIds = emptySet())

        assertEquals(3, focusedCommentViewportRowIndex(rows, "t1_target"))
        assertTrue(rows[3].key == "comment:t1_target")
    }

    @Test
    fun genericRowIndexKeepsMoreRepliesInTheSingleCommentSurface() {
        val rows = visibleCommentRows(
            comments = listOf(
                comment("t1_first", depth = 0),
                more("more:t1_first:next", depth = 1, childIds = listOf("later")),
            ),
            collapsedCommentIds = emptySet(),
        )

        assertEquals(
            4,
            commentViewportLazyListIndex(
                rows = rows,
                rowKey = "more:more:t1_first:next",
                addCommentVisible = false,
                liveStatusVisible = false,
            ),
        )
    }

    @Test
    fun flattenedRowsCapVisualIndentationWithoutChangingTreeDepth() {
        assertEquals(0, commentViewportInsetDp(0))
        assertEquals(8, commentViewportInsetDp(1))
        assertEquals(16, commentViewportInsetDp(2))
        assertEquals(16, commentViewportInsetDp(3))
        assertEquals(16, commentViewportInsetDp(9))
        assertFalse(commentViewportShowsReplyContext(2))
        assertTrue(commentViewportShowsReplyContext(3))
    }

    @Test
    fun deepRepliesCanShowTheirImmediateParentWithoutOpeningANestedScreen() {
        val comments = listOf(
            comment(
                id = "t1_root",
                depth = 0,
                children = listOf(
                    comment(
                        id = "t1_parent",
                        depth = 2,
                        children = listOf(comment("t1_deep", depth = 3)),
                    ),
                ),
            ),
        )

        val connectors = commentParentConnectorVisualByCommentId(comments)

        assertEquals("author", connectors["t1_deep"]?.parentAuthor)
    }

    @Test
    fun branchAwareRailsKeepALongLinearReplyChainOnOneLogicalRail() {
        val comments = listOf(
            comment(
                id = "t1_a",
                depth = 0,
                children = listOf(
                    comment(
                        id = "t1_b",
                        depth = 1,
                        children = listOf(
                            comment(
                                id = "t1_c",
                                depth = 2,
                                children = listOf(
                                    comment(
                                        id = "t1_d",
                                        depth = 3,
                                        children = listOf(comment("t1_e", depth = 4)),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val visuals = commentBranchRailVisualByCommentId(comments)

        assertEquals(0, visuals.getValue("t1_a").branchRailCount)
        assertEquals(1, visuals.getValue("t1_b").branchRailCount)
        assertEquals(1, visuals.getValue("t1_c").branchRailCount)
        assertEquals(1, visuals.getValue("t1_d").branchRailCount)
        assertEquals(1, visuals.getValue("t1_e").branchRailCount)
        assertEquals(4, visuals.getValue("t1_e").actualDepth)
    }

    @Test
    fun branchAwareRailsAddALogicalLaneOnlyWhenAParentActuallyBranches() {
        val comments = listOf(
            comment(
                id = "t1_root",
                depth = 0,
                children = listOf(
                    comment(
                        id = "t1_left",
                        depth = 1,
                        children = listOf(
                            comment(
                                id = "t1_branch",
                                depth = 2,
                                children = listOf(
                                    comment("t1_leaf_a", depth = 3),
                                    comment("t1_leaf_b", depth = 3),
                                ),
                            ),
                        ),
                    ),
                    comment("t1_right", depth = 1),
                ),
            ),
        )

        val visuals = commentBranchRailVisualByCommentId(comments)

        assertEquals(1, visuals.getValue("t1_left").branchRailCount)
        assertTrue(visuals.getValue("t1_left").startsNewBranchLane)
        assertEquals(1, visuals.getValue("t1_branch").branchRailCount)
        assertFalse(visuals.getValue("t1_branch").startsNewBranchLane)
        assertEquals(2, visuals.getValue("t1_leaf_a").branchRailCount)
        assertEquals(2, visuals.getValue("t1_leaf_b").branchRailCount)
        assertTrue(visuals.getValue("t1_leaf_a").startsNewBranchLane)
        assertTrue(visuals.getValue("t1_leaf_b").startsNewBranchLane)
    }

    @Test
    fun duplicateLogicalCommentIdentityProjectsExactlyOneStableLazyKey() {
        val comments = listOf(
            comment(
                id = "t1_root",
                depth = 0,
                children = listOf(
                    comment("t1_duplicate", depth = 1),
                ),
            ),
            comment(
                id = "t1_duplicate",
                depth = 0,
                children = listOf(
                    comment("t1_duplicate_only_child", depth = 1),
                ),
            ),
        )

        val duplicates = duplicateVisibleCommentRowKeys(
            comments = comments,
            collapsedCommentIds = emptySet(),
        )
        val rows = visibleCommentRows(
            comments = comments,
            collapsedCommentIds = emptySet(),
        )

        assertEquals(setOf("comment:t1_duplicate"), duplicates)
        assertEquals(1, rows.count { row -> row.key == "comment:t1_duplicate" })
        assertFalse(rows.any { row -> row.comment.id == "t1_duplicate_only_child" })
        assertEquals(rows.size, rows.map(CommentViewportRow::key).toSet().size)
    }

    @Test
    fun branchRailPhysicalLanesStayAlignedWhenDeeperRowsAddLogicalRails() {
        val comments = listOf(
            comment(
                id = "t1_root",
                depth = 0,
                children = listOf(
                    comment(
                        id = "t1_level_1",
                        depth = 1,
                        children = listOf(
                            comment(
                                id = "t1_level_2",
                                depth = 2,
                                children = listOf(
                                    comment(
                                        id = "t1_level_3",
                                        depth = 3,
                                        children = listOf(
                                            comment("t1_deep", depth = 4),
                                            comment("t1_level_3_sibling", depth = 4),
                                        ),
                                    ),
                                    comment("t1_level_2_sibling", depth = 3),
                                ),
                            ),
                            comment("t1_level_1_sibling", depth = 2),
                        ),
                    ),
                    comment("t1_root_sibling", depth = 1),
                ),
            ),
        )

        val visuals = commentBranchRailVisualByCommentId(comments)
        val level1 = visuals.getValue("t1_level_1")
        val level2 = visuals.getValue("t1_level_2")
        val level3 = visuals.getValue("t1_level_3")
        val deep = visuals.getValue("t1_deep")

        assertEquals(1, level1.branchRailCount)
        assertEquals(2, level2.branchRailCount)
        assertEquals(3, level3.branchRailCount)
        assertEquals(4, deep.branchRailCount)
        assertEquals(4, level1.layoutRailCount)
        assertEquals(4, level2.layoutRailCount)
        assertEquals(4, level3.layoutRailCount)
        assertEquals(4, deep.layoutRailCount)
        assertEquals(
            commentBranchRailPositionDp(0, level1.layoutRailCount),
            commentBranchRailPositionDp(0, deep.layoutRailCount),
            0.001f,
        )
        assertEquals(
            commentBranchRailPositionDp(1, level2.layoutRailCount),
            commentBranchRailPositionDp(1, deep.layoutRailCount),
            0.001f,
        )
        assertEquals(level1.railIds.first(), level2.railIds.first())
        assertEquals(level2.railIds.take(2), level3.railIds.take(2))
    }

    @Test
    fun collapsedBranchTerminatesHiddenRailsAndExpandRestoresTheSameLaneIdentity() {
        val comments = listOf(
            comment(
                id = "t1_root",
                depth = 0,
                children = listOf(
                    comment(
                        id = "t1_left",
                        depth = 1,
                        children = listOf(
                            comment("t1_left_child", depth = 2),
                        ),
                    ),
                    comment("t1_right", depth = 1),
                ),
            ),
        )
        val expandedRows = visibleCommentRows(comments, collapsedCommentIds = emptySet())
        val collapsedRows = visibleCommentRows(comments, collapsedCommentIds = setOf("t1_left"))
        val expanded = commentBranchRailVisualByCommentId(comments, expandedRows)
        val collapsed = commentBranchRailVisualByCommentId(comments, collapsedRows)

        assertTrue("t1_left_child" in expanded)
        assertFalse("t1_left_child" in collapsed)
        assertEquals(expanded.getValue("t1_left").railIds, collapsed.getValue("t1_left").railIds)
        assertEquals(
            expanded.getValue("t1_left").layoutRailCount,
            collapsed.getValue("t1_left").layoutRailCount,
        )
        val leftRailId = collapsed.getValue("t1_left").railIds.last()
        assertFalse(leftRailId in collapsed.getValue("t1_left").continuesToNextRailIds)
        assertFalse(
            collapsed.getValue("t1_right").railIds.last() in
                collapsed.getValue("t1_right").continuesFromPreviousRailIds,
        )
        assertTrue(leftRailId in expanded.getValue("t1_left").continuesToNextRailIds)
        assertTrue(leftRailId in expanded.getValue("t1_left_child").continuesFromPreviousRailIds)
    }

    @Test
    fun branchAwareRailsHaveNoThreeRailSemanticCapAndCompressOnlyPhysicalSpacing() {
        fun nestedBranch(level: Int, maxLevel: Int): Comment {
            if (level == maxLevel) return comment("t1_path_$level", depth = level)
            return comment(
                id = "t1_path_$level",
                depth = level,
                children = listOf(
                    nestedBranch(level + 1, maxLevel),
                    comment("t1_sibling_$level", depth = level + 1),
                ),
            )
        }

        val visuals = commentBranchRailVisualByCommentId(listOf(nestedBranch(0, 8)))
        val deepest = visuals.getValue("t1_path_8")

        assertEquals(8, deepest.branchRailCount)
        assertTrue(commentBranchRailGutterDp(deepest.branchRailCount) <= COMMENT_BRANCH_RAIL_MAX_GUTTER_DP)
        assertTrue(
            commentBranchRailPositionDp(
                branchRailIndex = deepest.branchRailCount - 1,
                branchRailCount = deepest.branchRailCount,
            ) <= COMMENT_BRANCH_RAIL_MAX_GUTTER_DP.toFloat(),
        )
    }

    @Test
    fun moreTransportNodesDoNotInventVisualConversationBranches() {
        val comments = listOf(
            comment(
                id = "t1_root",
                depth = 0,
                children = listOf(
                    comment("t1_only_real_child", depth = 1),
                    more(
                        id = "more:t1_root:next",
                        depth = 1,
                        childIds = listOf("later"),
                    ),
                ),
            ),
        )

        val visuals = commentBranchRailVisualByCommentId(comments)

        assertEquals(1, visuals.getValue("t1_only_real_child").branchRailCount)
        assertFalse(visuals.getValue("t1_only_real_child").startsNewBranchLane)
    }

    @Test
    fun focusedScrollOwnershipStaysConsumedForTheSameRouteVisitButNotANewVisit() {
        val firstVisit =
            "t3_post|t1_target|41|reddit:redreader_compatibility:test|BEST"
        val nextVisit =
            "t3_post|t1_target|42|reddit:redreader_compatibility:test|BEST"

        FocusedCommentScrollOwnershipRegistry.clearForTest()

        assertFalse(FocusedCommentScrollOwnershipRegistry.isUserOwned(firstVisit))
        FocusedCommentScrollOwnershipRegistry.markUserOwned(firstVisit)
        assertTrue(FocusedCommentScrollOwnershipRegistry.isUserOwned(firstVisit))
        assertFalse(FocusedCommentScrollOwnershipRegistry.isUserOwned(nextVisit))
    }

    @Test
    fun autoHydrationSelectsTheNearestUpcomingMoreRowAndSkipsFailures() {
        val rows = visibleCommentRows(
            comments = listOf(
                comment("t1_first", depth = 0),
                more("more:t1_first:a", depth = 1, childIds = listOf("a")),
                comment("t1_middle", depth = 0),
                more("more:t1_middle:b", depth = 1, childIds = listOf("b")),
            ),
            collapsedCommentIds = emptySet(),
        )

        val first = commentViewportAutoHydrationCandidate(
            rows = rows,
            firstVisibleLazyIndex = 3,
            lastVisibleLazyIndex = 3,
            addCommentVisible = false,
            liveStatusVisible = false,
            fullDiscussionLoaded = true,
            fullDiscussionLoading = false,
            focusedAnchorOwned = false,
            automaticHydrationBudget = 1,
            loadingMoreNodeId = null,
            failedMoreNodeId = null,
            scrollInProgress = false,
            hydrationAnchorPending = false,
        )
        val afterFailure = commentViewportAutoHydrationCandidate(
            rows = rows,
            firstVisibleLazyIndex = 3,
            lastVisibleLazyIndex = 3,
            addCommentVisible = false,
            liveStatusVisible = false,
            fullDiscussionLoaded = true,
            fullDiscussionLoading = false,
            focusedAnchorOwned = false,
            automaticHydrationBudget = 1,
            loadingMoreNodeId = null,
            failedMoreNodeId = "more:t1_first:a",
            scrollInProgress = false,
            hydrationAnchorPending = false,
        )

        assertEquals("more:t1_first:a", first?.id)
        assertEquals("more:t1_middle:b", afterFailure?.id)
    }

    @Test
    fun autoHydrationWaitsForCanonicalDiscussionAndOnlyRunsOneBatchAtATime() {
        val rows = visibleCommentRows(
            comments = listOf(
                more("more:t3_post:a", depth = 0, childIds = listOf("a")),
            ),
            collapsedCommentIds = emptySet(),
        )

        assertEquals(
            null,
            commentViewportAutoHydrationCandidate(
                rows = rows,
                firstVisibleLazyIndex = 3,
                lastVisibleLazyIndex = 3,
                addCommentVisible = false,
                liveStatusVisible = false,
                fullDiscussionLoaded = false,
                fullDiscussionLoading = false,
                focusedAnchorOwned = false,
                automaticHydrationBudget = 1,
                loadingMoreNodeId = null,
                failedMoreNodeId = null,
                scrollInProgress = false,
                hydrationAnchorPending = false,
            ),
        )
        assertEquals(
            null,
            commentViewportAutoHydrationCandidate(
                rows = rows,
                firstVisibleLazyIndex = 3,
                lastVisibleLazyIndex = 3,
                addCommentVisible = false,
                liveStatusVisible = false,
                fullDiscussionLoaded = true,
                fullDiscussionLoading = false,
                focusedAnchorOwned = false,
                automaticHydrationBudget = 1,
                loadingMoreNodeId = "more:other",
                failedMoreNodeId = null,
                scrollInProgress = false,
                hydrationAnchorPending = false,
            ),
        )
        assertEquals(
            null,
            commentViewportAutoHydrationCandidate(
                rows = rows,
                firstVisibleLazyIndex = 3,
                lastVisibleLazyIndex = 3,
                addCommentVisible = false,
                liveStatusVisible = false,
                fullDiscussionLoaded = true,
                fullDiscussionLoading = true,
                focusedAnchorOwned = false,
                automaticHydrationBudget = 1,
                loadingMoreNodeId = null,
                failedMoreNodeId = null,
                scrollInProgress = false,
                hydrationAnchorPending = false,
            ),
        )
        assertEquals(
            null,
            commentViewportAutoHydrationCandidate(
                rows = rows,
                firstVisibleLazyIndex = 3,
                lastVisibleLazyIndex = 3,
                addCommentVisible = false,
                liveStatusVisible = false,
                fullDiscussionLoaded = true,
                fullDiscussionLoading = false,
                focusedAnchorOwned = true,
                automaticHydrationBudget = 1,
                loadingMoreNodeId = null,
                failedMoreNodeId = null,
                scrollInProgress = false,
                hydrationAnchorPending = false,
            ),
        )
    }


    @Test
    fun autoHydrationRequiresAUserGestureBudget() {
        val rows = visibleCommentRows(
            comments = listOf(
                more("more:t3_post:a", depth = 0, childIds = listOf("a")),
            ),
            collapsedCommentIds = emptySet(),
        )

        assertEquals(
            null,
            commentViewportAutoHydrationCandidate(
                rows = rows,
                firstVisibleLazyIndex = 3,
                lastVisibleLazyIndex = 3,
                addCommentVisible = false,
                liveStatusVisible = false,
                fullDiscussionLoaded = true,
                fullDiscussionLoading = false,
                focusedAnchorOwned = false,
                automaticHydrationBudget = 0,
                loadingMoreNodeId = null,
                failedMoreNodeId = null,
                scrollInProgress = false,
                hydrationAnchorPending = false,
            ),
        )
        assertEquals(COMMENT_AUTO_HYDRATION_MAX_PER_USER_GESTURE, 2)
    }

    @Test
    fun autoHydrationWaitsUntilUserScrollingIsIdleAndNoViewportAnchorIsPending() {
        val rows = visibleCommentRows(
            comments = listOf(
                comment("t1_first", depth = 0),
                more("more:t1_first:a", depth = 1, childIds = listOf("a")),
            ),
            collapsedCommentIds = emptySet(),
        )

        assertEquals(
            null,
            commentViewportAutoHydrationCandidate(
                rows = rows,
                firstVisibleLazyIndex = 3,
                lastVisibleLazyIndex = 3,
                addCommentVisible = false,
                liveStatusVisible = false,
                fullDiscussionLoaded = true,
                fullDiscussionLoading = false,
                focusedAnchorOwned = false,
                automaticHydrationBudget = 1,
                loadingMoreNodeId = null,
                failedMoreNodeId = null,
                scrollInProgress = true,
                hydrationAnchorPending = false,
            ),
        )
        assertEquals(
            null,
            commentViewportAutoHydrationCandidate(
                rows = rows,
                firstVisibleLazyIndex = 3,
                lastVisibleLazyIndex = 3,
                addCommentVisible = false,
                liveStatusVisible = false,
                fullDiscussionLoaded = true,
                fullDiscussionLoading = false,
                focusedAnchorOwned = false,
                automaticHydrationBudget = 1,
                loadingMoreNodeId = null,
                failedMoreNodeId = null,
                scrollInProgress = false,
                hydrationAnchorPending = true,
            ),
        )
    }

    @Test
    fun autoHydrationPrefersTheBottomEdgeInsteadOfAnOldMoreNodeNearTheTop() {
        val rows = visibleCommentRows(
            comments = listOf(
                more("more:old", depth = 0, childIds = listOf("old")),
                comment("t1_a", depth = 0),
                comment("t1_b", depth = 0),
                comment("t1_c", depth = 0),
                more("more:next", depth = 0, childIds = listOf("next")),
            ),
            collapsedCommentIds = emptySet(),
        )

        val candidate = commentViewportAutoHydrationCandidate(
            rows = rows,
            firstVisibleLazyIndex = 3,
            lastVisibleLazyIndex = 6,
            addCommentVisible = false,
            liveStatusVisible = false,
            fullDiscussionLoaded = true,
            fullDiscussionLoading = false,
            focusedAnchorOwned = false,
            automaticHydrationBudget = 1,
            loadingMoreNodeId = null,
            failedMoreNodeId = null,
            scrollInProgress = false,
            hydrationAnchorPending = false,
        )

        assertEquals("more:next", candidate?.id)
    }

    @Test
    fun autoHydrationNeverSelectsAVisibleMoreRow() {
        val rows = visibleCommentRows(
            comments = listOf(
                comment("t1_visible", depth = 0),
                more("more:visible", depth = 0, childIds = listOf("v")),
                more("more:offscreen", depth = 0, childIds = listOf("o")),
            ),
            collapsedCommentIds = emptySet(),
        )

        val candidate = commentViewportAutoHydrationCandidate(
            rows = rows,
            firstVisibleLazyIndex = 3,
            lastVisibleLazyIndex = 4,
            addCommentVisible = false,
            liveStatusVisible = false,
            fullDiscussionLoaded = true,
            fullDiscussionLoading = false,
            focusedAnchorOwned = false,
            automaticHydrationBudget = 1,
            loadingMoreNodeId = null,
            failedMoreNodeId = null,
            scrollInProgress = false,
            hydrationAnchorPending = false,
        )

        assertEquals("more:offscreen", candidate?.id)
        assertEquals(16, COMMENT_MORE_PREFETCH_ROW_DISTANCE)
    }

    @Test
    fun hydrationMaterializationDoesNotTurnNestedHistoricalRepliesIntoNewComments() {
        val before = listOf(
            comment(
                id = "t1_root",
                depth = 0,
                children = listOf(
                    more(
                        id = "more:t1_root:known",
                        depth = 1,
                        childIds = listOf("known"),
                    ),
                ),
            ),
        )
        val after = listOf(
            comment(
                id = "t1_root",
                depth = 0,
                children = listOf(
                    comment(
                        id = "t1_known",
                        depth = 1,
                        children = listOf(
                            comment("t1_nested_old", depth = 2),
                            more(
                                id = "more:t1_known:nested_deferred",
                                depth = 2,
                                childIds = listOf("nested_deferred"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val baselineIds = knownCommentIds(before)
        val hydratedIds = knownCommentIds(after)
        val materializedIds = commentHydrationMaterializedCommentIds(
            previousViewportComments = before,
            hydratedComments = after,
        )
        val rawNewIds = newCommentIds(
            baseline = CommentVisitBaseline(exists = true, commentIds = baselineIds),
            currentCommentIds = hydratedIds,
        )

        assertEquals(setOf("t1_nested_old", "t1_nested_deferred"), materializedIds)
        assertEquals(materializedIds, rawNewIds)
        assertEquals(emptySet<String>(), rawNewIds - materializedIds)
        assertTrue("t1_known" in baselineIds)
    }

    @Test
    fun focusedCommentPathIsProtectedFromAutomaticSiblingCollapse() {
        val comments = listOf(
            comment(
                id = "t1_root",
                depth = 0,
                children = listOf(
                    comment(
                        id = "t1_target_branch",
                        depth = 1,
                        children = listOf(comment("t1_target", depth = 2)),
                    ),
                    comment("t1_other_branch", depth = 1),
                ),
            ),
        )

        assertEquals(
            setOf("t1_root", "t1_target_branch", "t1_target"),
            commentFocusedPathIds(comments, "t1_target"),
        )
        assertEquals(emptySet<String>(), commentFocusedPathIds(comments, null))
    }

    @Test
    fun hydrationCommitPublishesOnlyAfterNetworkAdvanceWhileIdle() {
        val fence = CommentHydrationCommitFence(
            moreNodeId = "more:t1_parent:child",
            sourceContentRevision = 41L,
            gestureGeneration = 7,
        )

        assertFalse(
            commentHydrationCommitCanPublish(
                fence = fence,
                currentContentRevision = 41L,
                loadingMoreNodeId = null,
                failedMoreNodeId = null,
                scrollInProgress = false,
            ),
        )
        assertFalse(
            commentHydrationCommitCanPublish(
                fence = fence,
                currentContentRevision = 42L,
                loadingMoreNodeId = fence.moreNodeId,
                failedMoreNodeId = null,
                scrollInProgress = false,
            ),
        )
        assertFalse(
            commentHydrationCommitCanPublish(
                fence = fence,
                currentContentRevision = 42L,
                loadingMoreNodeId = null,
                failedMoreNodeId = null,
                scrollInProgress = true,
            ),
        )
        assertTrue(
            commentHydrationCommitCanPublish(
                fence = fence,
                currentContentRevision = 42L,
                loadingMoreNodeId = null,
                failedMoreNodeId = null,
                scrollInProgress = false,
            ),
        )
    }

    @Test
    fun transportMoreCountsStayHiddenUntilLoadingOrFailureNeedsProductCopy() {
        assertFalse(
            commentTransportMoreRowVisible(
                fullDiscussionReady = true,
                fullDiscussionLoading = false,
                loading = false,
                failed = false,
            ),
        )
        assertTrue(
            commentTransportMoreRowVisible(
                fullDiscussionReady = true,
                fullDiscussionLoading = false,
                loading = true,
                failed = false,
            ),
        )
        assertTrue(
            commentTransportMoreRowVisible(
                fullDiscussionReady = true,
                fullDiscussionLoading = false,
                loading = false,
                failed = true,
            ),
        )
    }

    @Test
    fun boundaryConstrainedLandingAcceptsTheNearestFullyVisiblePosition() {
        assertTrue(
            focusedCommentBoundaryConstrainedLandingVerified(
                itemOffset = 526,
                itemSize = 320,
                viewportStartOffset = -11,
                viewportEndOffset = 2198,
                requestedDeltaPx = 96,
                canScrollBackward = true,
                canScrollForward = false,
            ),
        )
        assertFalse(
            focusedCommentBoundaryConstrainedLandingVerified(
                itemOffset = 526,
                itemSize = 320,
                viewportStartOffset = -11,
                viewportEndOffset = 2198,
                requestedDeltaPx = 96,
                canScrollBackward = true,
                canScrollForward = true,
            ),
        )
    }

    private fun comment(
        id: String,
        depth: Int,
        children: List<Comment> = emptyList(),
    ): Comment = Comment(
        id = id,
        author = "author",
        age = "1m",
        body = id,
        score = 1,
        depth = depth,
        accent = 0L,
        children = children,
    )

    private fun more(
        id: String,
        depth: Int,
        childIds: List<String>,
    ): Comment = Comment(
        id = id,
        author = "More replies",
        age = "",
        body = "",
        score = 0,
        depth = depth,
        accent = 0L,
        moreChildIds = childIds,
        moreCount = childIds.size,
    )
}
