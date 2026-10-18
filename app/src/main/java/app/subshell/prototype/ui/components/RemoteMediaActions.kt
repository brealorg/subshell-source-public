package app.subshell.prototype.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.subshell.prototype.model.MediaFormat
import app.subshell.prototype.model.RemoteCommentMedia
import app.subshell.prototype.model.RemoteCommentMediaKind
import app.subshell.prototype.ui.downloadRemoteMedia
import app.subshell.prototype.ui.error.LocalViewportErrorHostState
import app.subshell.prototype.ui.mediaActionScope
import app.subshell.prototype.ui.mediaDownloadFolder
import app.subshell.prototype.ui.shareRemoteMedia
import app.subshell.prototype.ui.symbols.Symbols
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ISSUE35_COMMENT_MEDIA_ACTIONS_V1: media without a post (a comment's image or
// GIF) gets the same Share media / Download sheet as post media, with the
// download panel and the error tray.

internal data class RemoteMediaTarget(
    val uri: String,
    val format: MediaFormat,
    val stableName: String,
)

internal fun RemoteCommentMedia.actionTarget(): RemoteMediaTarget = RemoteMediaTarget(
    uri = uri,
    format = if (kind == RemoteCommentMediaKind.GIF) MediaFormat.GIF else MediaFormat.IMAGE,
    stableName = commentMediaStableName(uri),
)

/** A file stem per comment media URL; the hash keeps two "giphy.gif" apart. */
internal fun commentMediaStableName(uri: String): String {
    val file = uri.substringBefore('?').substringAfterLast('/').substringBeforeLast('.')
    return "comment-$file-${Integer.toHexString(uri.hashCode())}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RemoteMediaActionsSheet(
    target: RemoteMediaTarget,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val errorHost = LocalViewportErrorHostState.current
    var download by remember(target) { mutableStateOf<MediaDownloadUiState?>(null) }
    // LOWUX2_COMMENT_MEDIA_SHEET_STAYS_WHILE_SAVING_V1 (OCB-308): as the post
    // media sheet (ISSUE120), a swipe or Back during a download left an
    // invisible sheet with the progress gone; it refuses to hide until saved.
    val downloading by rememberUpdatedState(download != null)
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        confirmValueChange = { value -> mediaActionSheetMayChange(value, downloading) },
    )

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = { if (download == null) onDismiss() },
    ) {
        val downloadState = download
        if (downloadState != null) {
            MediaDownloadPanel(downloadState)
        } else {
            Text(
                "Media actions",
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                content = { Text("Share media") },
                leadingContent = { Icon(Symbols.Share, contentDescription = null) },
                modifier = Modifier.clickable {
                    onDismiss()
                    mediaActionScope.launch {
                        val shared = shareRemoteMedia(context, target.uri, target.format, target.stableName)
                        if (!shared) {
                            errorHost?.reportMediaActionFailure(
                                title = "Could not share",
                                message = "This media could not be prepared for sharing",
                            )
                        }
                    }
                },
            )
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                content = { Text("Download") },
                leadingContent = { Icon(Symbols.Download, contentDescription = null) },
                modifier = Modifier.clickable {
                    download = MediaDownloadUiState.Saving(done = 0, total = 1)
                    mediaActionScope.launch {
                        val destination = downloadRemoteMedia(context, target.uri, target.format, target.stableName)
                        if (destination != null) {
                            download = MediaDownloadUiState.Saved(
                                count = 1,
                                total = 1,
                                folder = mediaDownloadFolder(destination),
                            )
                            delay(MEDIA_DOWNLOAD_SAVED_VISIBLE_MS)
                        } else {
                            errorHost?.reportMediaActionFailure(
                                title = "Could not download",
                                message = "This media could not be saved",
                            )
                        }
                        download = null
                        onDismiss()
                    }
                },
            )
        }
        Spacer(Modifier.height(18.dp))
    }
}
