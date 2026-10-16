package app.subshell.prototype.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.subshell.prototype.ui.error.ErrorPresentation
import app.subshell.prototype.ui.error.ErrorPresentationKind
import app.subshell.prototype.ui.error.ViewportErrorHostState

// ISSUE35_MEDIA_DOWNLOAD_V1: download progress and the saved confirmation
// are shown where the download was started (the media actions sheet, or the
// fullscreen viewer's chrome), failures in the viewport error tray. No Toast.

internal sealed interface MediaDownloadUiState {
    data class Saving(val done: Int, val total: Int) : MediaDownloadUiState
    data class Saved(val count: Int, val total: Int, val folder: String) : MediaDownloadUiState
}

/** How long the saved confirmation stays before the sheet closes. */
internal const val MEDIA_DOWNLOAD_SAVED_VISIBLE_MS = 1_500L

internal fun mediaDownloadTitle(state: MediaDownloadUiState): String = when (state) {
    is MediaDownloadUiState.Saving ->
        if (state.total > 1) "Saving ${state.done} of ${state.total}" else "Saving"
    is MediaDownloadUiState.Saved -> when {
        state.total <= 1 -> "Saved"
        state.count == state.total -> "Saved ${state.count} items"
        else -> "Saved ${state.count} of ${state.total}"
    }
}

internal fun mediaDownloadDetail(state: MediaDownloadUiState): String = when (state) {
    is MediaDownloadUiState.Saving -> "Keep this open until it is done"
    is MediaDownloadUiState.Saved -> buildString {
        append("In ${state.folder}")
        val failed = state.total - state.count
        if (failed > 0) append(" · $failed could not be saved")
    }
}

@Composable
internal fun MediaDownloadPanel(
    state: MediaDownloadUiState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.size(72.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (state is MediaDownloadUiState.Saved) Icons.Rounded.CheckCircle else Icons.Rounded.Download,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        Text(
            mediaDownloadTitle(state),
            // LOWUI7_NOTICES_ARE_ANNOUNCED_V1 (OCB-145)
            modifier = Modifier
                .padding(top = 18.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            mediaDownloadDetail(state),
            modifier = Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val progressModifier = Modifier
            .fillMaxWidth(0.88f)
            .padding(top = 20.dp)
            .height(6.dp)
            .clip(CircleShape)
        when (state) {
            is MediaDownloadUiState.Saving ->
                if (state.total > 1) {
                    LinearProgressIndicator(
                        progress = { state.done.toFloat() / state.total },
                        modifier = progressModifier,
                    )
                } else {
                    LinearProgressIndicator(modifier = progressModifier)
                }
            is MediaDownloadUiState.Saved ->
                LinearProgressIndicator(progress = { 1f }, modifier = progressModifier)
        }
    }
}

internal const val MEDIA_ACTION_ERROR_SCOPE = "media-actions"

/** Shows a media share/download failure in the viewport error tray. */
internal fun ViewportErrorHostState.reportMediaActionFailure(title: String, message: String) {
    clear(MEDIA_ACTION_ERROR_SCOPE)
    present(
        scopeKey = MEDIA_ACTION_ERROR_SCOPE,
        presentation = ErrorPresentation(
            kind = ErrorPresentationKind.DEGRADED,
            title = title,
            message = message,
        ),
        failureDetails = null,
        onRetry = null,
    )
}
