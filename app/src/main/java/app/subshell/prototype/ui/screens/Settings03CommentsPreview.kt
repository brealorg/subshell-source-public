package app.subshell.prototype.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.subshell.prototype.ui.CommentActionPlacement
import app.subshell.prototype.ui.CommentActionPreference
import app.subshell.prototype.ui.CommentCollapseMode
import app.subshell.prototype.ui.CommentHierarchyStyle
import app.subshell.prototype.ui.CommentTimeFormat
import app.subshell.prototype.ui.InteractionSide
import app.subshell.prototype.ui.resolve
import app.subshell.prototype.ui.components.RedditDefaultProfileAvatar
import kotlinx.coroutines.delay

/*
 * ISSUE10A4_PRODUCT_STYLE_TWO_COMMENT_PREVIEW_V1
 *
 * This is still the one existing Comments Live Preview.
 *
 * Deliberately show one parent and one direct reply. The point is not to
 * demonstrate an abstract tree; it is to make the visual relationship between
 * two real-looking Subshell comments immediately obvious.
 */
@Composable
internal fun CommentsSettingsPreview(
    highlightNewComments: Boolean,
    showCommentAvatars: Boolean,
    actionPreference: CommentActionPreference,
    interactionSide: InteractionSide,
    timeFormat: CommentTimeFormat,
    collapseMode: CommentCollapseMode,
    hierarchyStyle: CommentHierarchyStyle,
    modifier: Modifier = Modifier,
) {
    val actionPlacement = commentsPreviewResolvedPlacement(
        preference = actionPreference,
        interactionSide = interactionSide,
    )

    var replyCollapsed by rememberSaveable { mutableStateOf(false) }
    var collapseTargetDemoActive by remember { mutableStateOf(false) }
    var collapseModeDemoInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(collapseMode) {
        if (!collapseModeDemoInitialized) {
            collapseModeDemoInitialized = true
            return@LaunchedEffect
        }

        replyCollapsed = false
        collapseTargetDemoActive = true
        delay(220L)
        collapseTargetDemoActive = false
        delay(120L)
        replyCollapsed = true
        delay(440L)
        replyCollapsed = false
    }

    val headerDemoColor by animateColorAsState(
        targetValue = if (
            collapseTargetDemoActive &&
            collapseMode == CommentCollapseMode.HEADER
        ) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(140),
        label = "comment-collapse-header-demo",
    )

    val bodyDemoColor by animateColorAsState(
        targetValue = if (
            collapseTargetDemoActive &&
            collapseMode == CommentCollapseMode.COMMENT
        ) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(140),
        label = "comment-collapse-body-demo",
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = 12.dp,
                vertical = 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                "Live preview",
                style = MaterialTheme.typography.titleMedium,
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 164.dp), // LOWUI7_PREVIEWS_GROW_WITH_LARGE_TEXT_V1 (OCB-257)
                ) {
                    CommentHierarchyPreviewRail(
                        hierarchyStyle = hierarchyStyle,
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        PreviewParentComment(
                            time = timeFormat.example,
                            placement = actionPlacement,
                            showAvatar = showCommentAvatars,
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(
                                    MaterialTheme.colorScheme.outlineVariant
                                        .copy(alpha = 0.55f),
                                ),
                        )

                        PreviewReplyComment(
                            time = timeFormat.example,
                            placement = actionPlacement,
                            highlightNew = highlightNewComments,
                            showAvatar = showCommentAvatars,
                            collapsed = replyCollapsed,
                            collapseMode = collapseMode,
                            headerDemoColor = headerDemoColor,
                            bodyDemoColor = bodyDemoColor,
                            onToggle = {
                                replyCollapsed = !replyCollapsed
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewParentComment(
    time: String,
    placement: CommentActionPlacement,
    showAvatar: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 78.dp)
            .padding(
                start = 38.dp,
                top = 4.dp,
                end = 7.dp,
                bottom = 3.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        PreviewCommentHeader(
            author = "lord_nurker",
            time = time,
            avatarText = "L",
            showAvatar = showAvatar,
            avatarColor = MaterialTheme.colorScheme.primaryContainer,
            avatarContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )

        Text(
            "This is the parent comment body.",
            modifier = Modifier.padding(start = 30.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )

        PreviewCommentActions(
            score = 12,
            placement = placement,
            contentStart = 30.dp,
        )
    }
}

@Composable
private fun PreviewReplyComment(
    time: String,
    placement: CommentActionPlacement,
    highlightNew: Boolean,
    showAvatar: Boolean,
    collapsed: Boolean,
    collapseMode: CommentCollapseMode,
    headerDemoColor: Color,
    bodyDemoColor: Color,
    onToggle: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 85.dp)
            .background(
                if (highlightNew) {
                    MaterialTheme.colorScheme.primaryContainer
                        .copy(alpha = 0.08f)
                } else {
                    Color.Transparent
                },
            )
            .then(
                if (collapseMode == CommentCollapseMode.COMMENT) {
                    Modifier.clickable(onClick = onToggle)
                } else {
                    Modifier
                },
            )
            .background(
                bodyDemoColor,
                MaterialTheme.shapes.small,
            )
            .padding(
                start = 54.dp,
                top = 4.dp,
                end = 7.dp,
                bottom = 3.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        PreviewCommentHeader(
            author = "kordolene",
            time = time,
            avatarText = "K",
            showAvatar = showAvatar,
            avatarColor = MaterialTheme.colorScheme.secondaryContainer,
            avatarContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            isNew = highlightNew,
            modifier = Modifier
                .background(
                    headerDemoColor,
                    MaterialTheme.shapes.small,
                )
                .then(
                    if (collapseMode == CommentCollapseMode.HEADER) {
                        Modifier.clickable(onClick = onToggle)
                    } else {
                        Modifier
                    },
                ),
        )

        AnimatedVisibility(collapsed) {
            Text(
                "Comment collapsed",
                modifier = Modifier.padding(start = 30.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AnimatedVisibility(!collapsed) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    "This is the reply body.",
                    modifier = Modifier.padding(start = 30.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )

                PreviewCommentActions(
                    score = 5,
                    placement = placement,
                    contentStart = 30.dp,
                )
            }
        }
    }
}

@Composable
private fun PreviewCommentHeader(
    author: String,
    time: String,
    avatarText: String,
    avatarColor: Color,
    avatarContentColor: Color,
    modifier: Modifier = Modifier,
    isNew: Boolean = false,
    showAvatar: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 27.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // ISSUE22_COMMENT_AUTHOR_AVATARS_SETTING_V1: the preview has no
        // network, so "avatars on" shows the default Reddit-style profile
        // silhouette; "off" keeps the letter avatar the comment row draws.
        if (showAvatar) {
            RedditDefaultProfileAvatar(
                seed = author,
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape),
            )
        } else {
            Surface(
                modifier = Modifier.size(24.dp),
                shape = CircleShape,
                color = avatarColor,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        avatarText,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        color = avatarContentColor,
                    )
                }
            }
        }

        Text(
            "u/$author · $time",
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (isNew) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
            ) {
                Text(
                    "New",
                    modifier = Modifier.padding(
                        horizontal = 6.dp,
                        vertical = 1.dp,
                    ),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Composable
private fun PreviewCommentActions(
    score: Int,
    placement: CommentActionPlacement,
    contentStart: Dp,
) {
    val arrangement = when (placement) {
        CommentActionPlacement.LEFT -> Arrangement.Start
        CommentActionPlacement.BALANCED -> Arrangement.SpaceEvenly
        CommentActionPlacement.RIGHT -> Arrangement.End
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = contentStart,
                end = 2.dp,
            ),
        horizontalArrangement = arrangement,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PreviewAction("↩ Reply")
        PreviewAction("↑ $score ↓")
        PreviewAction("⋮")
    }
}

@Composable
private fun PreviewAction(
    label: String,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Text(
            label,
            modifier = Modifier.padding(
                horizontal = 6.dp,
                vertical = 1.dp,
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CommentHierarchyPreviewRail(
    hierarchyStyle: CommentHierarchyStyle,
) {
    val accent = MaterialTheme.colorScheme.primary

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(164.dp),
    ) {
        val stroke = 1.5.dp.toPx()
        val railX = 14.dp.toPx()

        // Parent row: 0..78
        // Divider: 78
        // Reply row: 79..164
        val parentExitY = 69.dp.toPx()
        val childEntryY = 94.dp.toPx()

        when (hierarchyStyle) {
            CommentHierarchyStyle.GUIDE_LINES -> {
                /*
                 * Current behaviour: a guide belongs visually to the reply
                 * region rather than clearly originating at its parent.
                 */
                drawLine(
                    color = accent.copy(alpha = 0.50f),
                    start = Offset(
                        railX,
                        80.dp.toPx(),
                    ),
                    end = Offset(
                        railX,
                        158.dp.toPx(),
                    ),
                    strokeWidth = stroke,
                )

                drawLine(
                    color = accent.copy(alpha = 0.62f),
                    start = Offset(
                        railX,
                        childEntryY,
                    ),
                    end = Offset(
                        45.dp.toPx(),
                        childEntryY,
                    ),
                    strokeWidth = stroke,
                )
            }

            CommentHierarchyStyle.PARENT_CONNECTORS -> {
                /*
                 * ISSUE10: the relationship leaves the parent near its lower
                 * boundary and then enters the direct reply near its header.
                 */
                drawLine(
                    color = accent.copy(alpha = 0.62f),
                    start = Offset(
                        railX,
                        parentExitY,
                    ),
                    end = Offset(
                        33.dp.toPx(),
                        parentExitY,
                    ),
                    strokeWidth = stroke,
                )

                drawLine(
                    color = accent.copy(alpha = 0.62f),
                    start = Offset(
                        railX,
                        parentExitY,
                    ),
                    end = Offset(
                        railX,
                        childEntryY,
                    ),
                    strokeWidth = stroke,
                )

                drawLine(
                    color = accent.copy(alpha = 0.62f),
                    start = Offset(
                        railX,
                        childEntryY,
                    ),
                    end = Offset(
                        45.dp.toPx(),
                        childEntryY,
                    ),
                    strokeWidth = stroke,
                )
            }
        }
    }
}

internal fun commentsPreviewResolvedPlacement(
    preference: CommentActionPreference,
    interactionSide: InteractionSide,
): CommentActionPlacement = preference.resolve(interactionSide)
