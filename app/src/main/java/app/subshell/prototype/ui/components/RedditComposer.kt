package app.subshell.prototype.ui.components

import app.subshell.prototype.share.copySharedImage
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import app.subshell.prototype.ui.error.LocalViewportErrorHostState
import app.subshell.prototype.ui.symbols.Symbols
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.subshell.prototype.ui.ComposerMediaAttachment
import app.subshell.prototype.ui.ComposerMediaKind

data class ComposerMediaPickerActions(
    val pickImage: () -> Unit,
    val pickGif: () -> Unit,
)

@Composable
fun ComposerMediaReplacementDialog(
    currentMedia: ComposerMediaAttachment,
    replacementMedia: ComposerMediaAttachment,
    onReplace: () -> Unit,
    onKeepExisting: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onKeepExisting,
        title = {
            Text("Replace existing ${currentMedia.kind.label.lowercase()}?")
        },
        text = {
            Text(
                "For now, this draft supports one inline image or GIF. " +
                    "Replace it with the new ${replacementMedia.kind.label.lowercase()}?",
            )
        },
        confirmButton = {
            TextButton(onClick = onReplace) {
                Text("Replace")
            }
        },
        dismissButton = {
            TextButton(onClick = onKeepExisting) {
                Text("Keep existing")
            }
        },
    )
}

@Composable
fun rememberComposerMediaPicker(
    onMediaPicked: (ComposerMediaAttachment) -> Unit,
): ComposerMediaPickerActions {
    val context = LocalContext.current
    val currentOnMediaPicked by rememberUpdatedState(onMediaPicked)
    val scope = rememberCoroutineScope()
    val noticeHost = LocalViewportErrorHostState.current
    // LOWUI2_PICKED_MEDIA_READ_OFF_MAIN_V1 (OCB-307): the picked image's type
    // and persistable grant are provider calls; they run on IO. A pasted image
    // keeps the synchronous read: its grant is only sure during the paste.
    val imagePicker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        uri?.let {
            // ISSUE90_SENDABLE_IMAGE_V1
            scope.launch {
                val picked = withContext(Dispatchers.IO) {
                    composerMediaAttachment(
                        context = context,
                        uri = it,
                        fallbackMimeType = "image/*",
                    )
                }
                sendableImageAttachment(context, picked)
                    ?.let { sendable -> currentOnMediaPicked(sendable) }
                    ?: noticeHost?.presentImageNotSendable()
            }
        }
    }
    val gifPicker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        uri?.let {
            scope.launch {
                currentOnMediaPicked(
                    withContext(Dispatchers.IO) {
                        composerMediaAttachment(
                            context = context,
                            uri = it,
                            fallbackMimeType = "image/gif",
                        )
                    },
                )
            }
        }
    }

    return remember(imagePicker, gifPicker) {
        ComposerMediaPickerActions(
            pickImage = {
                imagePicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
            },
            pickGif = {
                gifPicker.launch(
                    PickVisualMediaRequest(PickVisualMedia.SingleMimeType("image/gif")),
                )
            },
        )
    }
}

@Composable
internal fun ComposerInlineMedia(
    media: ComposerMediaAttachment,
    onRemove: (() -> Unit)?,
    interactionActionsAtStart: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp)
            .aspectRatio(16f / 9f),
    ) {
        LocalUriMedia(
            uri = media.uri,
            mimeType = media.mimeType,
            contentDescription = media.kind.label,
            modifier = Modifier.fillMaxSize(),
        )
        if (onRemove != null) {
            val removeAlignment = if (interactionActionsAtStart) {
                Alignment.TopStart
            } else {
                Alignment.TopEnd
            }
            Surface(
                modifier = Modifier
                    .align(removeAlignment)
                    .padding(8.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
            ) {
                IconButton(onClick = onRemove) {
                    Icon(
                        Symbols.Delete,
                        contentDescription = "Remove inline media",
                    )
                }
            }
        }
    }
}

internal fun composerMediaAttachment(
    context: Context,
    uri: Uri,
    fallbackMimeType: String,
    sourceLinkUri: String? = null,
): ComposerMediaAttachment {
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }
    val mimeType = context.contentResolver.getType(uri)
        ?.takeIf(String::isNotBlank)
        ?: fallbackMimeType
    val isGif = mimeType.equals("image/gif", ignoreCase = true) ||
        uri.lastPathSegment?.endsWith(".gif", ignoreCase = true) == true
    return ComposerMediaAttachment(
        uri = uri.toString(),
        mimeType = mimeType,
        kind = if (isGif) ComposerMediaKind.GIF else ComposerMediaKind.IMAGE,
        sourceLinkUri = sourceLinkUri,
    )
}

// COMPOSER16_PASTED_IMAGE_COPIED_V1: a pasted image (keyboard or clipboard)
// comes with a temporary read grant that ends with the paste or the process,
// so a draft kept only its address and could not be posted after a restart
// ("can no longer be read"). It is copied into app storage at once, like a
// shared image (#33); if the copy fails the pasted address is kept as before.
internal fun pastedImageKeptInApp(
    context: Context,
    received: ComposerMediaAttachment,
): ComposerMediaAttachment =
    runCatching { copySharedImage(context, Uri.parse(received.uri)) }
        .getOrNull()
        ?.copy(sourceLinkUri = received.sourceLinkUri)
        ?: received
