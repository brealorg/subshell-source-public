package app.subshell.prototype.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.hasMeaningfulExpandedDetails
import app.subshell.prototype.data.error.toSafeDetailItems
import app.subshell.prototype.ui.error.ErrorActionKind
import app.subshell.prototype.ui.error.TrayTone
import app.subshell.prototype.ui.error.ViewportErrorHostState

@Composable
internal fun ViewportErrorTrayHost(
    state: ViewportErrorHostState,
    modifier: Modifier = Modifier,
) {
    val request = state.currentRequest
    AnimatedVisibility(
        visible = request != null,
        modifier = modifier,
        enter = slideInVertically(initialOffsetY = { height -> height }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { height -> height }) + fadeOut(),
    ) {
        val current = request ?: return@AnimatedVisibility
        val retryAction = current.presentation.primaryAction?.takeIf { action ->
            action.kind == ErrorActionKind.RETRY && current.onRetry != null
        }
        val showDetails = current.failureDetails.hasMeaningfulExpandedDetails()
        // ISSUE222_TRAY_HAS_A_NEUTRAL_TONE_V1: a notice that is not an error is
        // drawn without the error colour and icon.
        val info = current.tone == TrayTone.INFO
        val accent = if (info) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 3.dp,
            border = BorderStroke(
                width = 1.dp,
                color = if (info) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.error.copy(alpha = 0.42f),
            ),
        ) {
            Column(
                modifier = Modifier.padding(start = 14.dp, top = 10.dp, end = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (info) Icons.Rounded.Info else Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = accent,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = current.presentation.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (info) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // ISSUE219_SMALL_ICON_BUTTONS_KEEP_48_DP_V1: no size override.
                    IconButton(
                        onClick = state::dismissCurrent,
                    ) {
                        if (info) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Dismiss notice",
                                modifier = Modifier.size(18.dp),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Dismiss error",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                Text(
                    text = current.presentation.message,
                    modifier = Modifier.padding(start = 30.dp, end = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                current.presentation.technicalDetail?.let { detail ->
                    Text(
                        text = detail,
                        modifier = Modifier.padding(start = 30.dp, end = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showDetails) {
                        TextButton(onClick = state::openDetails) {
                            Text("Details")
                        }
                    }
                    retryAction?.let { action ->
                        TextButton(onClick = state::retryCurrent) {
                            Text(
                                text = action.label,
                                color = accent,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ViewportErrorDetailsSheet(
    state: ViewportErrorHostState,
) {
    val request = state.currentRequest ?: return
    if (!state.detailsVisible || !request.failureDetails.hasMeaningfulExpandedDetails()) return

    ModalBottomSheet(
        onDismissRequest = state::closeDetails,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = "Error details",
                    style = MaterialTheme.typography.titleLarge,
                )
            }

            HorizontalDivider()

            FailureDetailsTable(
                details = request.failureDetails,
                retryAvailable = request.onRetry != null,
            )

            if (request.onRetry != null) {
                Button(
                    onClick = {
                        state.closeDetails()
                        state.retryCurrent()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Retry")
                }
            }
        }
    }
}

@Composable
private fun FailureDetailsTable(
    details: FailureDetails?,
    retryAvailable: Boolean,
) {
    val rows = buildList {
        details?.toSafeDetailItems()?.forEach { item ->
            add(item.label to item.value)
        }
        if (details?.retryable == null) {
            add("Retryable" to if (retryAvailable) "Yes" else "Not available")
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { (label, value) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = label,
                    modifier = Modifier.weight(0.42f),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    modifier = Modifier.weight(0.58f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
