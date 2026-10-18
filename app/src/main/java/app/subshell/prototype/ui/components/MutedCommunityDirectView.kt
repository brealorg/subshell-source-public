package app.subshell.prototype.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.subshell.prototype.ui.MutedCommunityDirectViewMode
import app.subshell.prototype.ui.symbols.Symbols

@Composable
fun MutedCommunityDirectViewSetting(
    mode: MutedCommunityDirectViewMode,
    onModeChange: (MutedCommunityDirectViewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    // M3225_SHORT_SINGLE_CHOICE_IS_A_SEGMENTED_ROW_V1
    SettingsSegmentedChoice(
        title = "When opening a muted community",
        description = "Choose whether direct visits can show posts while the community is muted.",
        options = MutedCommunityDirectViewMode.entries,
        selected = mode,
        label = { it.label },
        onSelect = onModeChange,
        modifier = modifier,
    )
}

@Composable
fun MutedCommunityDirectViewLivePreview(
    mode: MutedCommunityDirectViewMode,
    modifier: Modifier = Modifier,
) {
    // LOWUI7_PREVIEWS_ARE_ONE_DESCRIBED_IMAGE_V1 (OCB-156): the mock screen
    // is read as one description, not as fake content.
    Surface(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = "Preview: ${mode.description}"
        },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "‹   r/example",
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    "⋮",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    modifier = Modifier.size(34.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "r/",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                }

                Column(Modifier.weight(1f)) {
                    Text(
                        "r/example",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        "123k members",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        "✓ Joined",
                        modifier = Modifier.padding(
                            horizontal = 8.dp,
                            vertical = 4.dp,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor =
                    MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = 8.dp,
                        vertical = 5.dp,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Symbols.VolumeOff,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        "Muted",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                }
            }

            AnimatedContent(
                targetState = mode,
                modifier = Modifier.fillMaxWidth(),
                label = "muted-community-direct-view-preview",
            ) { targetMode ->
                when (targetMode) {
                    MutedCommunityDirectViewMode.SHOW_POSTS -> {
                        Column(
                            verticalArrangement =
                                Arrangement.spacedBy(4.dp),
                        ) {
                            MutedCommunityPreviewPost(
                                age = "2h",
                                title = "Community post",
                            )
                        }
                    }

                    MutedCommunityDirectViewMode.ABOUT_ONLY -> {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color =
                                MaterialTheme.colorScheme.secondaryContainer,
                            contentColor =
                                MaterialTheme.colorScheme.onSecondaryContainer,
                        ) {
                            Column(
                                modifier = Modifier.padding(
                                    horizontal = 9.dp,
                                    vertical = 7.dp,
                                ),
                                verticalArrangement =
                                    Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    "Posts hidden while muted",
                                    style =
                                        MaterialTheme.typography.labelLarge
                                            .copy(
                                                fontWeight =
                                                    FontWeight.SemiBold,
                                            ),
                                )
                                Text(
                                    "Community information remains available.",
                                    style =
                                        MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MutedCommunityPreviewPost(
    age: String,
    title: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                "r/example · $age",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
fun MutedCommunityPostsHiddenCard(
    communityLabel: String,
    onManageMute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Symbols.VolumeOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Posts hidden while muted",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "$communityLabel is available for community information and mute controls.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(
                onClick = onManageMute,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Manage mute")
            }
        }
    }
}
