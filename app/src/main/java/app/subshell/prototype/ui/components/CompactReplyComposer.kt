package app.subshell.prototype.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.AppBarRow
import androidx.compose.material3.AppBarRowScope
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.subshell.prototype.ui.ComposerMediaAttachment
import app.subshell.prototype.ui.MarkdownAction
import app.subshell.prototype.ui.applyMarkdownAction
import app.subshell.prototype.ui.symbols.Symbols

/** Lines the quick reply grows to before it scrolls; Expand opens the long reply. */
internal const val QUICK_REPLY_MAX_LINES = 4

internal const val COMPACT_REPLY_COMPOSER_TEST_TAG = "compact-reply-composer"

/**
 * Shared reply composer for Messages, modmail and comments.
 *
 * COMPOSER232_QUICK_REPLY_IS_ONE_CARD_V1 (#232, owner: as Google's Jetchat): one
 * docked card — what is answered, the field (1–4 lines, Markdown shown as its
 * style) and one row of actions with Send on the reader's side. Aa swaps the
 * attachment actions for the formatting actions (COMPOSER232_AA_TOGGLES_FORMATTING_V1);
 * Expand opens the same draft as a long reply (COMPOSER232_EXPAND_OPENS_THE_LONG_REPLY_V1).
 * The card has no keyboard padding of its own: its host fits it above the
 * keyboard (COMPOSER232_COMPOSER_SITS_ON_THE_KEYBOARD_V1).
 */
@Composable
fun CompactReplyComposer(
    state: TextFieldState,
    media: ComposerMediaAttachment?,
    placeholder: String,
    sendEnabled: Boolean,
    onSend: () -> Unit,
    onMediaReceived: (ComposerMediaAttachment) -> Unit,
    onMediaRemoved: () -> Unit,
    onPickImage: () -> Unit,
    onPickGif: () -> Unit,
    mediaEnabled: Boolean,
    // COMPOSER10_COMMENT_MEDIA_RULES_V1: the Image and GIF actions follow the
    // community's comment media rules separately.
    imageEnabled: Boolean = mediaEnabled,
    gifEnabled: Boolean = mediaEnabled,
    showMediaActions: Boolean = true,
    interactionActionsAtStart: Boolean,
    modifier: Modifier = Modifier,
    contextLabel: String? = null,
    onDismissContext: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    maxCharacters: Int = 10_000,
    sendContentDescription: String = "Send",
    inputLocked: Boolean = false,
    // ISSUE229_MENTION_AUTOCOMPLETE_V1: people to suggest after `@` / `u/`.
    mentions: ComposerMentions? = null,
    // The long reply's title and what it answers (shown above the field there).
    longReplyTitle: String = contextLabel ?: placeholder,
    quote: String? = null,
    sendLabel: String = "Send",
) {
    val fallbackFocusRequester = remember { FocusRequester() }
    val editorFocusRequester = focusRequester ?: fallbackFocusRequester
    val overLimit = state.text.length > maxCharacters
    var formatting by rememberSaveable { mutableStateOf(false) }
    var longReply by rememberSaveable { mutableStateOf(false) }

    fun applyFormat(action: MarkdownAction) {
        if (inputLocked) return
        val edit = applyMarkdownAction(
            text = state.text.toString(),
            selectionStart = state.selection.start,
            selectionEnd = state.selection.end,
            action = action,
        )
        state.edit {
            replace(0, length, edit.text)
            selection = TextRange(edit.selectionStart, edit.selectionEnd)
        }
    }

    val sendButton: @Composable () -> Unit = {
        // SUBSHELL_COMPOSE02_PRIMARY_ACTIONS_V1: Send is the filled primary action.
        FilledIconButton(
            enabled = sendEnabled && !overLimit,
            onClick = onSend,
        ) {
            Icon(Symbols.Send, contentDescription = sendContentDescription)
        }
    }

    // Expand stays beside Send whatever Aa shows, so a long reply is one tap
    // away with the formatting actions open too.
    val expandButton: @Composable () -> Unit = {
        IconButton(onClick = { longReply = true }) {
            Icon(Symbols.OpenInFull, contentDescription = "Expand")
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(COMPACT_REPLY_COMPOSER_TEST_TAG),
        // M3225_DOCKED_BARS_ARE_FLAT_SURFACE_CONTAINERS_V1: a docked bar is
        // surfaceContainer and flat, as M3's bottom app bar.
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (!contextLabel.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // COMPOSER232_CONTEXT_LINE_IS_BODY_TEXT_V1: not the monospace label.
                    Text(
                        text = contextLabel,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (onDismissContext != null) {
                        // ISSUE219_SMALL_ICON_BUTTONS_KEEP_48_DP_V1: no size override.
                        IconButton(onClick = onDismissContext) {
                            Icon(Symbols.Close, contentDescription = "Close composer")
                        }
                    }
                }
            }

            ComposerAttachments(
                state = state,
                media = media,
                mentions = mentions,
                inputLocked = inputLocked,
                interactionActionsAtStart = interactionActionsAtStart,
                onMediaRemoved = onMediaRemoved,
            )

            PostStyleComposerEditor(
                state = state,
                placeholder = placeholder,
                focusRequester = editorFocusRequester,
                onFormat = ::applyFormat,
                onMediaReceived = onMediaReceived,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                minLines = 1,
                maxLines = QUICK_REPLY_MAX_LINES,
                inputLocked = inputLocked,
                embedded = true,
            )

            if (overLimit) {
                OverLimitNote(maxCharacters, Modifier.padding(horizontal = 16.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (interactionActionsAtStart) {
                    sendButton()
                    expandButton()
                }
                AppBarRow(
                    modifier = Modifier.weight(1f),
                    overflowIndicator = { menuState ->
                        ComposerFormattingOverflow(menuState, enabled = !inputLocked)
                    },
                ) {
                    formattingToggleItem(formatting, { on -> formatting = on }, enabled = !inputLocked)
                    if (formatting) {
                        composerFormattingItems(
                            state = state,
                            enabled = !inputLocked,
                            onFormat = ::applyFormat,
                            onReturnToEditor = { editorFocusRequester.requestFocus() },
                        )
                    } else {
                        if (showMediaActions) {
                            composerMediaItems(
                                imageEnabled = mediaEnabled && imageEnabled && !inputLocked,
                                gifEnabled = mediaEnabled && gifEnabled && !inputLocked,
                                onPickImage = onPickImage,
                                onPickGif = onPickGif,
                            )
                        }
                    }
                }
                if (!interactionActionsAtStart) {
                    expandButton()
                    sendButton()
                }
            }
        }
    }

    if (longReply) {
        ComposerLongReply(
            state = state,
            title = longReplyTitle,
            quote = quote,
            media = media,
            mentions = mentions,
            placeholder = placeholder,
            sendLabel = sendLabel,
            sendEnabled = sendEnabled && !overLimit,
            overLimit = overLimit,
            maxCharacters = maxCharacters,
            inputLocked = inputLocked,
            showMediaActions = showMediaActions,
            imageEnabled = mediaEnabled && imageEnabled && !inputLocked,
            gifEnabled = mediaEnabled && gifEnabled && !inputLocked,
            interactionActionsAtStart = interactionActionsAtStart,
            formatting = formatting,
            onFormattingChange = { on -> formatting = on },
            onFormat = ::applyFormat,
            onMediaReceived = onMediaReceived,
            onMediaRemoved = onMediaRemoved,
            onPickImage = onPickImage,
            onPickGif = onPickGif,
            onCollapse = {
                longReply = false
                editorFocusRequester.requestFocus()
            },
            onSend = {
                longReply = false
                onSend()
            },
        )
    }
}

private fun AppBarRowScope.composerMediaItems(
    imageEnabled: Boolean,
    gifEnabled: Boolean,
    onPickImage: () -> Unit,
    onPickGif: () -> Unit,
) {
    clickableItem(
        onClick = onPickImage,
        icon = { Icon(Symbols.Image, contentDescription = "Image") },
        label = "Image",
        enabled = imageEnabled,
    )
    clickableItem(
        onClick = onPickGif,
        icon = { Icon(Symbols.GifBox, contentDescription = "GIF") },
        label = "GIF",
        enabled = gifEnabled,
    )
}

@Composable
private fun ColumnScope.ComposerAttachments(
    state: TextFieldState,
    media: ComposerMediaAttachment?,
    mentions: ComposerMentions?,
    inputLocked: Boolean,
    interactionActionsAtStart: Boolean,
    onMediaRemoved: () -> Unit,
) {
    if (mentions != null && !inputLocked) {
        MentionSuggestions(
            state = state,
            mentions = mentions,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
    if (media != null) {
        ComposerInlineMedia(
            media = media,
            onRemove = onMediaRemoved.takeUnless { inputLocked },
            interactionActionsAtStart = interactionActionsAtStart,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun OverLimitNote(maxCharacters: Int, modifier: Modifier = Modifier) {
    Text(
        "$maxCharacters character limit",
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.error,
    )
}

/**
 * COMPOSER232_EXPAND_OPENS_THE_LONG_REPLY_V1: the same draft in an M3
 * full-screen dialog (a ≤ 560 dp dialog on wide windows): close returns to the
 * quick reply with everything kept, what is answered sits above the field and
 * Aa and the media actions float above the keyboard as in the quick reply,
 * and Preview shows the reply as the thread will.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ComposerLongReply(
    state: TextFieldState,
    title: String,
    quote: String?,
    media: ComposerMediaAttachment?,
    mentions: ComposerMentions?,
    placeholder: String,
    sendLabel: String,
    sendEnabled: Boolean,
    overLimit: Boolean,
    maxCharacters: Int,
    inputLocked: Boolean,
    showMediaActions: Boolean,
    imageEnabled: Boolean,
    gifEnabled: Boolean,
    interactionActionsAtStart: Boolean,
    formatting: Boolean,
    onFormattingChange: (Boolean) -> Unit,
    onFormat: (MarkdownAction) -> Unit,
    onMediaReceived: (ComposerMediaAttachment) -> Unit,
    onMediaRemoved: () -> Unit,
    onPickImage: () -> Unit,
    onPickGif: () -> Unit,
    onCollapse: () -> Unit,
    onSend: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    var preview by rememberSaveable { mutableStateOf(false) }
    AdaptiveFullScreenDialog(
        title = title,
        onDismissRequest = onCollapse,
        closeLabel = "Back to quick reply",
        actions = {
            // COMPOSER232_LONG_REPLY_PREVIEW_V1 (owner): the reply as it will
            // read in the thread, markers gone; a second tap returns to editing.
            IconToggleButton(checked = preview, onCheckedChange = { on -> preview = on }) {
                if (preview) {
                    Icon(Symbols.EditNote, contentDescription = "Edit")
                } else {
                    Icon(Symbols.Visibility, contentDescription = "Preview")
                }
            }
            TextButton(enabled = sendEnabled, onClick = onSend) { Text(sendLabel) }
        },
    ) {
        if (!quote.isNullOrBlank()) {
            ComposerQuote(quote, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        if (preview) {
            ComposerPreview(
                text = state.text.toString(),
                media = media,
                interactionActionsAtStart = interactionActionsAtStart,
            )
            return@AdaptiveFullScreenDialog
        }
        ComposerAttachments(
            state = state,
            media = media,
            mentions = mentions,
            inputLocked = inputLocked,
            interactionActionsAtStart = interactionActionsAtStart,
            onMediaRemoved = onMediaRemoved,
        )
        PostStyleComposerEditor(
            state = state,
            placeholder = placeholder,
            focusRequester = focusRequester,
            onFormat = onFormat,
            onMediaReceived = onMediaReceived,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            minLines = 1,
            maxLines = Int.MAX_VALUE,
            inputLocked = inputLocked,
            embedded = true,
        )
        if (overLimit) {
            OverLimitNote(maxCharacters, Modifier.padding(horizontal = 16.dp))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            HorizontalFloatingToolbar(
                expanded = true,
                colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
            ) {
                // COMPOSER232_LONG_REPLY_KEEPS_AA_V1 (owner): the same Aa as the
                // quick reply, and the choice carries over both ways.
                AppBarRow(
                    overflowIndicator = { menuState ->
                        ComposerFormattingOverflow(menuState, enabled = !inputLocked)
                    },
                ) {
                    formattingToggleItem(formatting, onFormattingChange, enabled = !inputLocked)
                    if (formatting) {
                        composerFormattingItems(
                            state = state,
                            enabled = !inputLocked,
                            onFormat = onFormat,
                            onReturnToEditor = { focusRequester.requestFocus() },
                        )
                    } else if (showMediaActions) {
                        composerMediaItems(
                            imageEnabled = imageEnabled,
                            gifEnabled = gifEnabled,
                            onPickImage = onPickImage,
                            onPickGif = onPickGif,
                        )
                    }
                }
            }
        }
    }
    LaunchedEffect(preview) { if (!preview) focusRequester.requestFocus() }
}

/** The reply rendered as the thread will show it; nothing here edits it. */
@Composable
private fun ColumnScope.ComposerPreview(
    text: String,
    media: ComposerMediaAttachment?,
    interactionActionsAtStart: Boolean,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (media != null) {
            ComposerInlineMedia(media = media, onRemove = null, interactionActionsAtStart = interactionActionsAtStart)
        }
        if (text.isBlank()) {
            Text(
                "Nothing to preview yet",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            CommentContent(source = text)
        }
    }
}

private fun AppBarRowScope.formattingToggleItem(
    formatting: Boolean,
    onFormattingChange: (Boolean) -> Unit,
    enabled: Boolean,
) {
    toggleableItem(
        checked = formatting,
        onCheckedChange = onFormattingChange,
        icon = { Icon(Symbols.TextFormat, contentDescription = "Formatting") },
        label = "Formatting",
        enabled = enabled,
    )
}

/** What the long reply answers: three lines, the whole text on a tap. */
@Composable
private fun ComposerQuote(text: String, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Text(
            text = text,
            modifier = Modifier
                .clickable(role = Role.Button, onClickLabel = if (open) "Show less" else "Show all") {
                    open = !open
                }
                .semantics { stateDescription = if (open) "Expanded" else "Collapsed" }
                .padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (open) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
