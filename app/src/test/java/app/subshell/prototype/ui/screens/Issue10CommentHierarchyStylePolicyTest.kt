package app.subshell.prototype.ui.screens

import app.subshell.prototype.model.Comment
import app.subshell.prototype.ui.CommentHierarchyStyle
import app.subshell.prototype.ui.SubshellSettings
import app.subshell.prototype.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Issue10CommentHierarchyStylePolicyTest {
    @Test
    fun guideLinesRemainTheBackwardCompatibleDefault() {
        assertEquals(
            CommentHierarchyStyle.GUIDE_LINES,
            SubshellSettings().commentHierarchyStyle,
        )

        assertEquals(
            listOf("Guide lines", "Parent connectors"),
            CommentHierarchyStyle.entries.map(
                CommentHierarchyStyle::label,
            ),
        )

        CommentHierarchyStyle.entries.forEach { style ->
            val settings =
                SubshellSettings(commentHierarchyStyle = style)

            assertEquals(
                style,
                SubshellSettings.fromStorageMap(
                    settings.toStorageMap(),
                ).commentHierarchyStyle,
            )
        }

        val legacy =
            SubshellSettings().toStorageMap() -
                SubshellSettings.COMMENT_HIERARCHY_STYLE

        assertEquals(
            CommentHierarchyStyle.GUIDE_LINES,
            SubshellSettings.fromStorageMap(
                legacy,
            ).commentHierarchyStyle,
        )
    }

    @Test
    fun statePersistsHierarchyStyle() {
        val persisted = linkedMapOf<String, String>()

        val state = SubshellState(
            persistPreference = { key, value ->
                persisted[key] = value
            },
        )

        state.updateCommentHierarchyStyle(
            CommentHierarchyStyle.PARENT_CONNECTORS,
        )

        assertEquals(
            CommentHierarchyStyle.PARENT_CONNECTORS,
            state.commentHierarchyStyle,
        )

        assertEquals(
            CommentHierarchyStyle.PARENT_CONNECTORS.name,
            persisted[
                SubshellSettings.COMMENT_HIERARCHY_STYLE
            ],
        )
    }

    // STEP3 (issue10): older backups keep restoring the style, so the key's
    // spelling is part of the backup format.
    @Test
    fun theBackupKeyKeepsItsSpelling() {
        assertEquals("comment_hierarchy_style", SubshellSettings.COMMENT_HIERARCHY_STYLE)
        assertEquals(
            CommentHierarchyStyle.PARENT_CONNECTORS.name,
            SubshellSettings(commentHierarchyStyle = CommentHierarchyStyle.PARENT_CONNECTORS)
                .toStorageMap()["comment_hierarchy_style"],
        )
    }

    // STEP3 (issue10): parent connectors follow real parent -> child edges. A
    // parent's lane runs on past an earlier child's subtree while another of
    // its children follows, and ends at its last child.
    @Test
    fun parentConnectorLanesFollowRealEdges() {
        val c = comment("c", depth = 2)
        val b1 = comment("b1", depth = 1, children = listOf(c))
        val b2 = comment("b2", depth = 1)
        val a = comment("a", depth = 0, children = listOf(b1, b2))

        val visuals = commentParentConnectorVisualByCommentId(listOf(a))

        val root = visuals.getValue("a")
        assertNull(root.incomingLane)
        assertEquals(0, root.outgoingLane?.connectorDepth)

        val first = visuals.getValue("b1")
        assertEquals(0, first.incomingLane?.connectorDepth)
        assertTrue(first.incomingContinuesToNextSibling)
        assertEquals(1, first.outgoingLane?.connectorDepth)

        val grandchild = visuals.getValue("c")
        assertEquals(1, grandchild.incomingLane?.connectorDepth)
        // a's lane continues past b1's subtree down to b2.
        assertEquals(listOf(0), grandchild.ancestorContinuationLanes.map { it.connectorDepth })
        assertFalse(grandchild.incomingContinuesToNextSibling)
        assertNull(grandchild.outgoingLane)

        val last = visuals.getValue("b2")
        assertEquals(0, last.incomingLane?.connectorDepth)
        assertFalse(last.incomingContinuesToNextSibling)
        assertTrue(last.ancestorContinuationLanes.isEmpty())
    }

    // STEP3 (issue10): a collapsed comment's replies draw no connectors, and
    // the collapsed comment has no lane going down.
    @Test
    fun aCollapsedCommentsRepliesHaveNoConnectors() {
        val c = comment("c", depth = 2)
        val b1 = comment("b1", depth = 1, children = listOf(c))
        val a = comment("a", depth = 0, children = listOf(b1))

        val visuals = commentParentConnectorVisualByCommentId(
            comments = listOf(a),
            visibleRows = visibleCommentRows(comments = listOf(a), collapsedCommentIds = setOf("b1")),
        )

        assertFalse("c" in visuals)
        assertNull(visuals.getValue("b1").outgoingLane)
    }

    private fun comment(
        id: String,
        depth: Int,
        children: List<Comment> = emptyList(),
    ): Comment = Comment(
        id = id,
        author = "author-$id",
        age = "1m",
        body = id,
        score = 1,
        depth = depth,
        accent = 0L,
        children = children,
    )
}
