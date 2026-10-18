package app.subshell.prototype.ui.screens

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.subshell.prototype.model.Comment
import app.subshell.prototype.model.isMoreRepliesNode
import app.subshell.prototype.model.projectCommentContent
import app.subshell.prototype.ui.components.markdownHighlightRanges
import app.subshell.prototype.ui.components.markdownSearchableText
import app.subshell.prototype.ui.components.SubshellSearchField
import app.subshell.prototype.ui.symbols.Symbols

// ISSUE29_THREAD_FIND_V1: find text in the loaded comments of a post. Only
// what the reader sees in a comment body is searched (the same rendered text
// that gets highlighted), in thread order, including replies under collapsed
// comments; stepping to such a match reveals its collapsed ancestors.

internal const val THREAD_FIND_QUERY_DEBOUNCE_MS = 250L

/** The searchable text of a comment: its rendered body, as shown. */
internal fun commentFindText(comment: Comment): String =
    markdownSearchableText(projectCommentContent(comment.body).displayText)

/** Ids of loaded comments whose text matches [query], in thread order. */
internal fun threadFindMatchIds(
    comments: List<Comment>,
    query: String,
    textOf: (Comment) -> String = ::commentFindText,
): List<String> {
    if (query.isBlank()) return emptyList()
    val seen = mutableSetOf<String>()
    val matches = mutableListOf<String>()
    fun visit(comment: Comment) {
        if (!seen.add(comment.id)) return
        if (!comment.isMoreRepliesNode && markdownHighlightRanges(textOf(comment), query).isNotEmpty()) {
            matches += comment.id
        }
        comment.children.forEach(::visit)
    }
    comments.forEach(::visit)
    return matches
}

/** The match to show after stepping from [current] (-1 = none yet), wrapping around. */
internal fun threadFindStep(current: Int, size: Int, forward: Boolean): Int? = when {
    size <= 0 -> null
    current !in 0 until size -> if (forward) 0 else size - 1
    forward -> (current + 1) % size
    else -> (current - 1 + size) % size
}

// Compact so the find field keeps its width at large text sizes; the full
// wording is the count's accessibility description.
internal fun threadFindCountLabel(active: Int, size: Int): String = when {
    size == 0 -> "0/0"
    active in 0 until size -> "${active + 1}/$size"
    else -> "–/$size"
}

internal fun threadFindCountDescription(active: Int, size: Int): String = when {
    size == 0 -> "No matches"
    active in 0 until size -> "Match ${active + 1} of $size"
    else -> "$size matches"
}

/**
 * Opening find brings up the keyboard, which covers the lower half of the
 * screen. While the post itself is still in view the comment header sits low,
 * so it has to move to the top first; once it is stuck there, leave the
 * reader where they are.
 */
internal fun threadFindNeedsHeaderAtTop(firstVisibleItemIndex: Int, commentControlsIndex: Int): Boolean =
    firstVisibleItemIndex < commentControlsIndex

internal const val THREAD_FIND_OPEN_LABEL = "Find in thread"

@Composable
internal fun ThreadFindBar(
    query: String,
    onQueryChange: (String) -> Unit,
    countLabel: String?,
    countDescription: String?,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    // OCB-280: the keyboard's Search; by default the same as Next.
    onSearch: () -> Unit = onNext,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // M3225_ONE_SEARCH_FIELD_STYLE_V1
        SubshellSearchField(
            query = query,
            onQueryChange = onQueryChange,
            placeholder = THREAD_FIND_OPEN_LABEL,
            modifier = Modifier.weight(1f),
            // LOWUI7_LABELS_SAY_WHAT_A_CONTROL_DOES_V1 (OCB-279): named also once text is typed.
            fieldModifier = Modifier
                .focusRequester(focusRequester)
                .semantics { contentDescription = THREAD_FIND_OPEN_LABEL },
            onSearch = { onSearch() },
        )
        if (countLabel != null) {
            Text(
                text = countLabel,
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .semantics { contentDescription = countDescription ?: countLabel },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        IconButton(onClick = onPrevious) {
            Icon(Symbols.KeyboardArrowUp, contentDescription = "Previous match")
        }
        IconButton(onClick = onNext) {
            Icon(Symbols.KeyboardArrowDown, contentDescription = "Next match")
        }
        IconButton(onClick = onClose) {
            Icon(Symbols.Close, contentDescription = "Close find")
        }
    }
}
