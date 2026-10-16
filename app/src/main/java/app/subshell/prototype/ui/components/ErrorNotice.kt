package app.subshell.prototype.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.subshell.prototype.ui.error.ErrorAction
import app.subshell.prototype.ui.error.ErrorActionKind
import app.subshell.prototype.ui.error.ErrorPresentation
import app.subshell.prototype.ui.error.ErrorPresentationKind
import app.subshell.prototype.ui.error.ReportErrorPresentationDiagnostic

@Composable
fun ErrorNotice(
    presentation: ErrorPresentation,
    modifier: Modifier = Modifier,
    diagnosticScopeKey: String? = null,
    onAction: (ErrorActionKind) -> Unit,
) {
    ReportErrorPresentationDiagnostic(
        scopeKey = diagnosticScopeKey,
        presentation = presentation,
    )

    if (presentation.kind == ErrorPresentationKind.DEGRADED) {
        DegradedErrorNotice(
            presentation = presentation,
            modifier = modifier,
            onAction = onAction,
        )
        return
    }

    val compact = presentation.kind == ErrorPresentationKind.INLINE
    val blocking = presentation.kind == ErrorPresentationKind.BLOCKING
    val containerColor = when (presentation.kind) {
        ErrorPresentationKind.BLOCKING -> MaterialTheme.colorScheme.surfaceContainerHigh
        ErrorPresentationKind.DEGRADED -> MaterialTheme.colorScheme.surfaceContainer
        ErrorPresentationKind.INLINE -> MaterialTheme.colorScheme.surfaceContainerLow
    }

    // LOWUI7_NOTICES_ARE_ANNOUNCED_V1 (OCB-132): a notice that appears is read
    // out; a blocking one interrupts.
    Surface(
        modifier = modifier.fillMaxWidth().semantics {
            liveRegion = if (blocking) LiveRegionMode.Assertive else LiveRegionMode.Polite
        },
        shape = if (compact) MaterialTheme.shapes.medium else MaterialTheme.shapes.large,
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.34f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(if (compact) 12.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(if (compact) 20.dp else 24.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = presentation.title,
                        style = if (blocking) {
                            MaterialTheme.typography.titleMedium
                        } else {
                            MaterialTheme.typography.titleSmall
                        },
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = presentation.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    presentation.technicalDetail?.let { detail ->
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            ErrorActionRow(
                primaryAction = presentation.primaryAction,
                secondaryAction = presentation.secondaryAction,
                filledPrimary = blocking,
                onAction = onAction,
            )
        }
    }
}

/**
 * ISSUE222_ONE_INLINE_ERROR_NOTICE_V1: the inline error card for screens that
 * have a title/message/action rather than an ErrorPresentation (moderation,
 * Direct Chat start). It looks like ErrorNotice's INLINE kind and is announced
 * (LOWUI7_NOTICES_ARE_ANNOUNCED_V1); the hand-built errorContainer cards it
 * replaces were silent and looked different on every screen.
 */
@Composable
internal fun InlineErrorNotice(
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.34f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    title?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (actionLabel != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onAction) {
                        Text(text = actionLabel, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun DegradedErrorNotice(
    presentation: ErrorPresentation,
    modifier: Modifier,
    onAction: (ErrorActionKind) -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.34f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = presentation.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = presentation.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                presentation.technicalDetail?.let { detail ->
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (
                presentation.primaryAction != null ||
                presentation.secondaryAction != null
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                ) {
                    presentation.secondaryAction?.let { action ->
                        TextButton(onClick = { onAction(action.kind) }) {
                            Text(action.label)
                        }
                    }
                    presentation.primaryAction?.let { action ->
                        TextButton(onClick = { onAction(action.kind) }) {
                            Text(
                                text = action.label,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorActionRow(
    primaryAction: ErrorAction?,
    secondaryAction: ErrorAction?,
    filledPrimary: Boolean,
    onAction: (ErrorActionKind) -> Unit,
) {
    if (primaryAction == null && secondaryAction == null) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        secondaryAction?.let { action ->
            TextButton(onClick = { onAction(action.kind) }) {
                Text(action.label)
            }
        }
        primaryAction?.let { action ->
            if (filledPrimary) {
                Button(
                    onClick = { onAction(action.kind) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Text(action.label)
                }
            } else {
                TextButton(onClick = { onAction(action.kind) }) {
                    Text(
                        text = action.label,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
