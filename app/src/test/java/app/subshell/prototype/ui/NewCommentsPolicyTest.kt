package app.subshell.prototype.ui

import app.subshell.prototype.model.Comment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewCommentsPolicyTest {
    @Test
    fun firstVisitEstablishesBaselineWithoutFalseNewMarkers() {
        val current = setOf("t1_parent", "t1_reply")

        assertEquals(
            emptySet<String>(),
            newCommentIds(CommentVisitBaseline(exists = false, emptySet()), current),
        )
    }

    @Test
    fun revisitFindsOnlyUnseenRemoteCommentIds() {
        val current = setOf("t1_old", "t1_new", "local-comment-post-2")

        assertEquals(
            setOf("t1_new"),
            newCommentIds(
                CommentVisitBaseline(exists = true, commentIds = setOf("t1_old")),
                current,
            ),
        )
    }

    @Test
    fun deferredMoreIdsBelongToTheBaselineBeforeTheyAreLoaded() {
        val comments = listOf(
            comment(
                id = "t1_parent",
                children = listOf(
                    comment(
                        id = "more-t1_parent",
                        moreChildIds = listOf("deferreda", "deferredb"),
                        moreCount = 2,
                    ),
                ),
            ),
        )

        assertEquals(
            setOf("t1_parent", "t1_deferreda", "t1_deferredb"),
            knownCommentIds(comments),
        )
    }

    @Test
    fun onlyTheNewReplyPathOpensAndOldSiblingBranchesCollapse() {
        val comments = discussionTree()
        val newIds = setOf("t1_new", "t1_hiddennew")
        val ancestors = commentAncestorIdsForTargets(comments, newIds)

        assertEquals(setOf("t1_root", "t1_path"), ancestors)
        assertEquals(
            setOf("t1_old_sibling", "t1_path_old_sibling"),
            commentSiblingBranchesToCollapse(
                comments = comments,
                targetIds = newIds,
                autoExpandedAncestorIds = setOf("t1_root"),
            ),
        )
    }

    @Test
    fun visitHistoryIsPerAccountAndPostAndPreservesAnEmptyBaseline() {
        val encoded = CommentVisitHistory()
            .recording("account-a", "post-1", setOf("t1_a", "t1_b"))
            .recording("account-a", "post-empty", emptySet())
            .recording("account-b", "post-1", setOf("t1_other"))
            .encode()
        val restored = CommentVisitHistory.decode(encoded)

        assertEquals(setOf("t1_a", "t1_b"), restored.baseline("account-a", "post-1"))
        assertEquals(emptySet<String>(), restored.baseline("account-a", "post-empty"))
        assertEquals(setOf("t1_other"), restored.baseline("account-b", "post-1"))
        assertNull(restored.baseline("account-a", "never-visited"))
    }

    @Test
    fun manualCollapseWinsForTheRestOfTheVisit() {
        val state = SubshellState()
        state.collapsedCommentIds.addAll(listOf("t1_root", "t1_path"))

        state.revealNewCommentPaths(
            ancestorIds = setOf("t1_root", "t1_path"),
            siblingBranchIds = setOf("t1_old_sibling"),
            manuallyCollapsedIds = setOf("t1_root"),
        )

        assertTrue("t1_root" in state.collapsedCommentIds)
        assertFalse("t1_path" in state.collapsedCommentIds)
        assertTrue("t1_old_sibling" in state.collapsedCommentIds)
    }

    @Test
    fun statePersistsVisitHistoryButKeepsItOutsideSettingsBackup() {
        val persisted = linkedMapOf<String, String>()
        val state = SubshellState(
            persistPreference = { key, value -> persisted[key] = value },
        )

        state.recordCommentVisit("account-a", "post-1", setOf("t1_a"))

        assertTrue(SubshellSettings.COMMENT_VISIT_HISTORY in persisted)
        assertFalse(SubshellSettings.COMMENT_VISIT_HISTORY in SubshellSettings.storageKeys)
        val recreated = SubshellState(
            initialCommentVisitHistoryToken = persisted.getValue(
                SubshellSettings.COMMENT_VISIT_HISTORY,
            ),
        )
        assertEquals(setOf("t1_a"), recreated.commentVisitBaseline("account-a", "post-1"))
    }

    private fun discussionTree(): List<Comment> = listOf(
        comment(
            id = "t1_root",
            children = listOf(
                comment(id = "t1_old_sibling"),
                comment(
                    id = "t1_path",
                    depth = 1,
                    children = listOf(
                        comment(id = "t1_path_old_sibling", depth = 2),
                        comment(id = "t1_new", depth = 2),
                        comment(
                            id = "more-t1_path",
                            depth = 2,
                            moreChildIds = listOf("hiddennew", "hiddenold"),
                            moreCount = 2,
                        ),
                    ),
                ),
            ),
        ),
    )

    private fun comment(
        id: String,
        depth: Int = 0,
        children: List<Comment> = emptyList(),
        moreChildIds: List<String> = emptyList(),
        moreCount: Int = 0,
    ): Comment = Comment(
        id = id,
        author = "tester",
        age = "now",
        body = id,
        score = 1,
        depth = depth,
        accent = 0xff000000L,
        children = children,
        moreChildIds = moreChildIds,
        moreCount = moreCount,
    )
}
