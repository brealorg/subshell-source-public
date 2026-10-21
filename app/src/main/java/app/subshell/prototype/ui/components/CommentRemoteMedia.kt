package app.subshell.prototype.ui.components

import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.subshell.prototype.model.RemoteCommentMedia
import app.subshell.prototype.model.RemoteCommentMediaKind
import app.subshell.prototype.model.projectCommentContent

// ISSUE19_INLINE_COMMENT_IMAGE_FULLSCREEN_V1
@Composable
internal fun CommentContent(
    source: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    onMediaClick: (RemoteCommentMedia) -> Unit = {},
    highlight: MarkdownHighlight? = null,
    selectable: Boolean = false,
) {
    val projection = remember(source) { projectCommentContent(source) }
    var mediaActionTarget by remember(source) { mutableStateOf<RemoteMediaTarget?>(null) }
    mediaActionTarget?.let { target ->
        RemoteMediaActionsSheet(
            target = target,
            onDismiss = { mediaActionTarget = null },
        )
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        if (projection.displayText.isNotBlank()) {
            MarkdownText(
                text = projection.displayText,
                color = color,
                highlight = highlight,
                // ISSUE235: only the text; the media keep their own long press.
                selectable = selectable,
            )
        }
        projection.media.forEach { media ->
            RemoteCommentMediaPreview(
                media = media,
                onImageClick = { onMediaClick(media) },
                onLongClick = { mediaActionTarget = media.actionTarget() },
            )
        }
    }
}

@Composable
internal fun RemoteCommentMediaPreviews(
    source: String,
    modifier: Modifier = Modifier,
    onMediaClick: (RemoteCommentMedia) -> Unit = {},
) {
    val media = remember(source) { projectCommentContent(source).media }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        media.forEach { item ->
            RemoteCommentMediaPreview(
                media = item,
                onImageClick = { onMediaClick(item) },
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun RemoteCommentMediaPreview(
    media: RemoteCommentMedia,
    onImageClick: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    val baseModifier = Modifier
        .fillMaxWidth()
        .clip(MaterialTheme.shapes.medium)
    when (media.kind) {
        RemoteCommentMediaKind.IMAGE -> RemoteImage(
            uri = media.uri,
            label = "Comment image",
            modifier = baseModifier.combinedClickable(
                onClickLabel = "Open comment image fullscreen",
                onLongClickLabel = "Media actions",
                onLongClick = onLongClick,
                onClick = onImageClick,
            ),
            contentScale = ContentScale.Fit,
            adaptToSourceAspectRatio = true,
        )
        RemoteCommentMediaKind.GIF -> RemoteGifMedia(
            uri = media.uri,
            label = "Comment GIF",
            playing = true,
            modifier = baseModifier.longPressOnly(label = "Media actions", onLongPress = onLongClick),
            contentScale = ContentScale.Fit,
            adaptToSourceAspectRatio = true,
        )
    }
}

/**
 * Long press only: a plain tap is left unconsumed, so it still reaches the
 * comment body around the media (tap-to-collapse). Uses the platform
 * long-press timeout; exposed to accessibility as a long-click action.
 *
 * ISSUE51_SCROLL_IS_NOT_A_LONG_PRESS_V1: waitForUpOrCancellation() also
 * returns null when the list takes the pointer for a scroll; only the
 * timeout running out is a long press (a scroll used to open the sheet and
 * stall the list).
 */
internal fun Modifier.longPressOnly(label: String, onLongPress: () -> Unit): Modifier =
    semantics {
        onLongClick(label = label) {
            onLongPress()
            true
        }
    }.pointerInput(onLongPress) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            val endedBeforeTimeout = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                waitForUpOrCancellation()
                true
            }
            if (endedBeforeTimeout == null) {
                onLongPress()
                // Swallow the rest of this gesture so the lift is not a tap.
                do {
                    val event = awaitPointerEvent()
                    event.changes.forEach { change -> change.consume() }
                } while (event.changes.any { change -> change.pressed })
            }
        }
    }
