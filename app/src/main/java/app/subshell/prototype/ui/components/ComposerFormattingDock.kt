package app.subshell.prototype.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.AppBarMenuState
import androidx.compose.material3.AppBarRow
import androidx.compose.material3.AppBarRowScope
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.subshell.prototype.ui.MarkdownAction
import app.subshell.prototype.ui.ReadingMaxWidth
import app.subshell.prototype.ui.symbols.Symbols

private data class ComposerFormatAction(
    val action: MarkdownAction,
    val label: String,
    val icon: ImageVector,
)

private val composerFormatActions = listOf(
    ComposerFormatAction(MarkdownAction.BOLD, "Bold", Symbols.FormatBold),
    ComposerFormatAction(MarkdownAction.ITALIC, "Italic", Symbols.FormatItalic),
    ComposerFormatAction(MarkdownAction.LINK, "Link", Symbols.Link),
    ComposerFormatAction(MarkdownAction.CODE_BLOCK, "Code block", Symbols.Code),
    ComposerFormatAction(MarkdownAction.QUOTE, "Quote", Symbols.FormatQuote),
    ComposerFormatAction(
        MarkdownAction.BULLET_LIST,
        "Bulleted list",
        Symbols.FormatListBulleted,
    ),
    ComposerFormatAction(
        MarkdownAction.NUMBERED_LIST,
        "Numbered list",
        Symbols.FormatListNumbered,
    ),
    ComposerFormatAction(
        MarkdownAction.STRIKETHROUGH,
        "Strikethrough",
        Symbols.StrikethroughS,
    ),
    ComposerFormatAction(MarkdownAction.INLINE_CODE, "Inline code", Symbols.Code),
    ComposerFormatAction(MarkdownAction.SPOILER, "Spoiler", Symbols.VisibilityOff),
)

/** Undo and redo, then the first four formats; the rest go to "More formatting". */
internal const val COMPOSER_FORMATTING_VISIBLE_ITEMS = 6

/**
 * COMPOSER232_FORMATTING_IS_AN_APP_BAR_ROW_V1 (#232): the formatting actions are
 * one M3 AppBarRow item set, shared by the quick reply's Aa row, the long reply
 * and the docked bar of the edit screens. AppBarRow moves what does not fit
 * into the overflow menu by itself; each icon has its tooltip.
 */
@OptIn(ExperimentalFoundationApi::class)
internal fun AppBarRowScope.composerFormattingItems(
    state: TextFieldState,
    enabled: Boolean,
    onFormat: (MarkdownAction) -> Unit,
    onReturnToEditor: () -> Unit,
) {
    clickableItem(
        onClick = {
            onReturnToEditor()
            state.undoState.undo()
        },
        icon = { Icon(Symbols.Undo, contentDescription = "Undo") },
        label = "Undo",
        enabled = enabled && state.undoState.canUndo,
    )
    clickableItem(
        onClick = {
            onReturnToEditor()
            state.undoState.redo()
        },
        icon = { Icon(Symbols.Redo, contentDescription = "Redo") },
        label = "Redo",
        enabled = enabled && state.undoState.canRedo,
    )
    composerFormatActions.forEach { item ->
        clickableItem(
            onClick = {
                onReturnToEditor()
                onFormat(item.action)
            },
            icon = { Icon(item.icon, contentDescription = item.label) },
            label = item.label,
            enabled = enabled,
        )
    }
}

@Composable
internal fun ComposerFormattingOverflow(menuState: AppBarMenuState, enabled: Boolean = true) {
    IconButton(enabled = enabled, onClick = { menuState.show() }) {
        Icon(Symbols.MoreHoriz, contentDescription = "More formatting")
    }
}

/**
 * Canonical formatting dock shared by Create Post, the edit screens and the
 * moderator removal message. The quick reply shows the same items behind Aa.
 */
@Composable
fun ComposerFormattingDock(
    state: TextFieldState,
    onFormat: (MarkdownAction) -> Unit,
    onReturnToEditor: () -> Unit,
    modifier: Modifier = Modifier,
    // POSTEDIT02_EDITOR_LOCKED_WHILE_IN_FLIGHT_V1: off while the text belongs
    // to a request in flight (undo/redo edit the state directly).
    enabled: Boolean = true,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        // M3225_DOCKED_BARS_ARE_FLAT_SURFACE_CONTAINERS_V1 (#225, components-13,
        // settings-15): a docked bar is surfaceContainer and flat, as M3's
        // bottom app bar, not the legacy background role with a 10 dp shadow.
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        AppBarRow(
            modifier = Modifier
                .fillMaxWidth()
                // Clear of the gesture/navigation bar while the keyboard is hidden.
                .navigationBarsPadding()
                // M3224_READING_WIDTH_V1: the actions stay together on a wide
                // window instead of spreading edge to edge.
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = ReadingMaxWidth)
                .padding(horizontal = 4.dp, vertical = 3.dp),
            overflowIndicator = { menuState -> ComposerFormattingOverflow(menuState, enabled) },
            maxItemCount = COMPOSER_FORMATTING_VISIBLE_ITEMS + 1,
        ) {
            composerFormattingItems(
                state = state,
                enabled = enabled,
                onFormat = onFormat,
                onReturnToEditor = onReturnToEditor,
            )
        }
    }
}
