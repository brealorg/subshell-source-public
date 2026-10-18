package app.subshell.prototype.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.indication
import androidx.compose.material3.ripple
import androidx.compose.runtime.remember
import app.subshell.prototype.ui.symbols.Symbols

// ISSUE28_TOP_LEVEL_COMMENT_JUMP_V1: the post screen's jump button moves
// between top-level rows of the visible comment rows. Collapsed replies are
// not in those rows, so collapse is respected for free. A top-level "more"
// node is a target too: since UIPOST04 it is an invisible 1dp idle row, so the
// last jump lands on it and the automatic on-screen loader fetches it (the
// button itself never loads). (LOWUI4, OCB-281: this comment was stale.)

internal enum class TopLevelCommentJumpDirection { NEXT, PREVIOUS }

/**
 * Where the reader is among the comment rows: the first row whose bottom is
 * below the reading line (the bottom of the stuck comment header), and
 * whether its top is already scrolled past that line. [rowIndex] is -1 above
 * the comments and `rows.size` below them.
 */
internal data class CommentReadingPosition(
    val rowIndex: Int,
    val scrolledIntoRow: Boolean,
)

/** The parts of a visible lazy-list item the reading position needs. */
internal data class VisibleCommentListItem(
    val key: Any,
    val offset: Int,
    val size: Int,
)

internal const val COMMENT_CONTROLS_ITEM_KEY = "comment-controls"
internal const val FOCUSED_COMMENT_LANDING_SPACE_ITEM_KEY = "focused-comment-landing-space"

internal fun commentReadingPosition(
    rows: List<CommentViewportRow>,
    visibleItems: List<VisibleCommentListItem>,
    viewportStartOffset: Int,
): CommentReadingPosition {
    val header = visibleItems.firstOrNull { item -> item.key == COMMENT_CONTROLS_ITEM_KEY }
    // A header that is not stuck to the top means the post itself is still
    // in view: the reader is above the comments.
    if (header == null || header.offset > viewportStartOffset) {
        val belowComments = header == null &&
            visibleItems.any { item -> item.key == FOCUSED_COMMENT_LANDING_SPACE_ITEM_KEY } &&
            visibleItems.none { item -> item.key is String && rowKeyIsComment(item.key) }
        return CommentReadingPosition(if (belowComments) rows.size else -1, scrolledIntoRow = false)
    }
    val readingLine = header.offset + header.size
    val rowIndexByKey: Map<Any, Int> = rows.withIndex().associate { (index, row) -> row.key to index }
    val current = visibleItems.firstOrNull { item ->
        item.key in rowIndexByKey && item.offset + item.size > readingLine
    } ?: return CommentReadingPosition(rows.size, scrolledIntoRow = false)
    return CommentReadingPosition(
        rowIndex = rowIndexByKey.getValue(current.key),
        scrolledIntoRow = current.offset < readingLine,
    )
}

/**
 * The row index to jump to, or null when there is none. Next goes to the
 * next top-level row; previous goes to the top of the thread being read, or
 * to the thread before when that top is already at the reading line.
 */
internal fun topLevelCommentJumpTarget(
    rows: List<CommentViewportRow>,
    position: CommentReadingPosition,
    direction: TopLevelCommentJumpDirection,
): Int? {
    val targets = rows.indices.filter { index -> rows[index].comment.depth <= 0 }
    return when (direction) {
        TopLevelCommentJumpDirection.NEXT ->
            targets.firstOrNull { index -> index > position.rowIndex }
        TopLevelCommentJumpDirection.PREVIOUS ->
            targets.lastOrNull { index ->
                index < position.rowIndex ||
                    (index == position.rowIndex && position.scrolledIntoRow)
            }
    }
}

private fun rowKeyIsComment(key: String): Boolean =
    key.startsWith("comment:") || key.startsWith("more:")

internal const val TOP_LEVEL_COMMENT_JUMP_NEXT_LABEL = "Next top-level comment"
internal const val TOP_LEVEL_COMMENT_JUMP_PREVIOUS_LABEL = "Previous top-level comment"

/** Small FAB-styled button: tap jumps to the next top-level comment, long press to the previous one. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TopLevelCommentJumpButton(
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // UIPOST05: the round button stays 40dp but its touch target is 48dp
    // (the minimum interactive size); the click lives on the outer target.
    // LOWUI7_LABELS_SAY_WHAT_A_CONTROL_DOES_V1 (OCB-282): a button; "Previous"
    // is offered once, as the long-press action.
    // M3225_JUMP_RIPPLE_FOLLOWS_THE_FAB_SHAPE_V1 (#225, reading-16): the 48 dp
    // target takes the press, the ripple is drawn inside the 40 dp FAB shape.
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(48.dp)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = TOP_LEVEL_COMMENT_JUMP_NEXT_LABEL,
                onLongClickLabel = TOP_LEVEL_COMMENT_JUMP_PREVIOUS_LABEL,
                onLongClick = onPrevious,
                onClick = onNext,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = FloatingActionButtonDefaults.smallShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shadowElevation = 6.dp,
        ) {
            Box(
                modifier = Modifier.fillMaxSize().indication(interactionSource, ripple()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Symbols.KeyboardArrowDown,
                    contentDescription = TOP_LEVEL_COMMENT_JUMP_NEXT_LABEL,
                )
            }
        }
    }
}
