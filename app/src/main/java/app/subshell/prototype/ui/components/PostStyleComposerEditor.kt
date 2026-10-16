package app.subshell.prototype.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.contextmenu.builder.item
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Density
import app.subshell.prototype.ui.ComposerMediaAttachment
import app.subshell.prototype.ui.MarkdownAction

private data object SharedBoldContextKey
private data object SharedItalicContextKey
private data object SharedLinkContextKey
private data object SharedCodeBlockContextKey
private data object SharedInlineCodeContextKey

/**
 * Canonical post-style Markdown editor surface.
 *
 * Create Post and compact reply surfaces intentionally share this exact field implementation;
 * callers only choose the line limits and placeholder appropriate to their surface.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PostStyleComposerEditor(
    state: TextFieldState,
    placeholder: String,
    focusRequester: FocusRequester,
    onFormat: (MarkdownAction) -> Unit,
    onMediaReceived: (ComposerMediaAttachment) -> Unit,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    maxLines: Int = 5,
    // COMPOSER01_INPUT_LOCKED_WHILE_SENDING_V1: text, paste and shortcuts are
    // locked while the text already belongs to a request in flight.
    inputLocked: Boolean = false,
    // COMPOSER17_BODY_FOCUS_REVEAL_V1: lets Create Post find the cursor.
    onTextLayout: (Density.(getResult: () -> TextLayoutResult?) -> Unit)? = null,
    // COMPOSER232_QUICK_REPLY_IS_ONE_CARD_V1: inside the quick-reply card the
    // field is filled and borderless, as in Google's Messages and Jetchat.
    embedded: Boolean = false,
) {
    val context = LocalContext.current
    val currentOnMediaReceived by rememberUpdatedState(onMediaReceived)
    val currentInputLocked by rememberUpdatedState(inputLocked)
    val receiveContentListener = remember(context) {
        ReceiveContentListener { transferableContent ->
            if (currentInputLocked) {
                transferableContent
            } else if (transferableContent.hasMediaType(MediaType.Image)) {
                val sourceLinkUri = transferableContent.platformTransferableContent
                    ?.linkUri
                    ?.toString()
                transferableContent.consume { item ->
                    item.uri?.let { uri ->
                        currentOnMediaReceived(
                            composerMediaAttachment(
                                context = context,
                                uri = uri,
                                fallbackMimeType = "image/*",
                                sourceLinkUri = sourceLinkUri,
                            ),
                        )
                    }
                    item.uri != null
                }
            } else {
                transferableContent
            }
        }
    }

    val markdownStyling = rememberComposerMarkdownStyling()
    val fieldModifier = modifier
        .focusRequester(focusRequester)
        .contentReceiver(receiveContentListener)
        .appendTextContextMenuComponents {
            separator()
            item(key = SharedBoldContextKey, label = "Bold") {
                onFormat(MarkdownAction.BOLD)
                close()
            }
            item(key = SharedItalicContextKey, label = "Italic") {
                onFormat(MarkdownAction.ITALIC)
                close()
            }
            item(key = SharedLinkContextKey, label = "Link") {
                onFormat(MarkdownAction.LINK)
                close()
            }
            item(key = SharedCodeBlockContextKey, label = "Code block") {
                onFormat(MarkdownAction.CODE_BLOCK)
                close()
            }
            item(key = SharedInlineCodeContextKey, label = "Inline code") {
                onFormat(MarkdownAction.INLINE_CODE)
                close()
            }
            separator()
        }
        .onPreviewKeyEvent { event ->
            if (inputLocked || event.type != KeyEventType.KeyDown || !event.isCtrlPressed) {
                false
            } else {
                when {
                    event.key == Key.Z && event.isShiftPressed -> {
                        state.undoState.redo()
                        true
                    }
                    event.key == Key.Z -> {
                        state.undoState.undo()
                        true
                    }
                    event.key == Key.B -> {
                        onFormat(MarkdownAction.BOLD)
                        true
                    }
                    event.key == Key.I -> {
                        onFormat(MarkdownAction.ITALIC)
                        true
                    }
                    else -> false
                }
            }
        }
    val lineLimits = TextFieldLineLimits.MultiLine(
        minHeightInLines = minLines,
        maxHeightInLines = maxLines,
    )
    val keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
    val inputTransformation = LockedComposerInput.takeIf { inputLocked }
    if (embedded) {
        TextField(
            state = state,
            inputTransformation = inputTransformation,
            outputTransformation = markdownStyling,
            modifier = fieldModifier,
            placeholder = { Text(placeholder) },
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = keyboardOptions,
            lineLimits = lineLimits,
            shape = MaterialTheme.shapes.extraLarge,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
            onTextLayout = onTextLayout,
        )
    } else {
        OutlinedTextField(
            state = state,
            inputTransformation = inputTransformation,
            outputTransformation = markdownStyling,
            modifier = fieldModifier,
            placeholder = { Text(placeholder) },
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = keyboardOptions,
            lineLimits = lineLimits,
            shape = MaterialTheme.shapes.large,
            onTextLayout = onTextLayout,
        )
    }
}

// COMPOSER01_INPUT_LOCKED_WHILE_SENDING_V1: keeps focus and look, rejects edits.
private val LockedComposerInput = InputTransformation { revertAllChanges() }
