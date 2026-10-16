package app.subshell.prototype.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.subshell.prototype.ui.CodeFont
import app.subshell.prototype.ui.VoteColorPalette
import app.subshell.prototype.ui.VoteControlMode
import app.subshell.prototype.ui.components.voteDirectionTint
import app.subshell.prototype.ui.components.voteStateIcon
import app.subshell.prototype.ui.fontFamily
import app.subshell.prototype.ui.theme.SubshellTheme
import app.subshell.prototype.ui.theme.ThemeMode
import app.subshell.prototype.ui.theme.isDarkScheme

@Composable
internal fun AppearanceSettingsPreview(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    pureBlack: Boolean = false,
    voteControlMode: VoteControlMode,
    voteColorPalette: VoteColorPalette,
    codeFont: CodeFont,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    var motionAtEnd by remember { mutableStateOf(false) }

    LaunchedEffect(reduceMotion) {
        motionAtEnd = !motionAtEnd
    }

    Surface(
        onClick = { motionAtEnd = !motionAtEnd },
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(2.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.70f),
            ),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            "Live preview",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "Theme, voting, code typeface and motion. Tap to replay the preview.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    AppearanceStateBadge(
                        themeMode = themeMode,
                        reduceMotion = reduceMotion,
                        pureBlack = pureBlack,
                    )
                }

                SubshellTheme(
                    mode = themeMode,
                    dynamicColor = dynamicColor,
                    pureBlack = pureBlack,
                    // LOWUI8_PREVIEW_TEXT_SIZE_APPLIED_ONCE_V1 (OCB-254): the app's
                    // own SubshellTheme already scales the text; the preview's
                    // nested theme scaled it a second time.
                    codeFontFamily = codeFont.fontFamily,
                    manageSystemBarContrast = false,
                ) {
                    AppearancePhoneSnapshot(
                        voteControlMode = voteControlMode,
                        voteColorPalette = voteColorPalette,
                        codeFont = codeFont,
                        reduceMotion = reduceMotion,
                        motionAtEnd = motionAtEnd,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppearanceStateBadge(
    themeMode: ThemeMode,
    reduceMotion: Boolean,
    pureBlack: Boolean = false,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                // LOWUX3_PREVIEW_BADGE_NAMES_PURE_BLACK_V1 (OCB-255)
                appearanceThemeLabel(themeMode, pureBlack, isSystemInDarkTheme()),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                if (reduceMotion) "Reduced motion" else "Motion on",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AppearancePhoneSnapshot(
    voteControlMode: VoteControlMode,
    voteColorPalette: VoteColorPalette,
    codeFont: CodeFont,
    reduceMotion: Boolean,
    motionAtEnd: Boolean,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            // Grows with the text size instead of clipping it (ISSUE32_APP_TEXT_SIZE_V1).
            .heightIn(min = 166.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "r/android",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AppearancePaletteSwatches()
            }

            Text(
                "A compact appearance preview",
                style = MaterialTheme.typography.titleMedium,
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Text(
                    text = "val client = \"subshell\"",
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = codeFont.fontFamily,
                    ),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Crossfade(
                    targetState = voteControlMode,
                    animationSpec = if (reduceMotion) snap() else tween(180),
                    label = "appearance-vote-mode",
                ) { mode ->
                    AppearanceVoteControls(
                        mode = mode,
                        palette = voteColorPalette,
                        selectedDirection = appearancePreviewSelectedVoteDirection(
                            motionAtEnd = motionAtEnd,
                        ),
                    )
                }

                AppearanceMotionTrack(
                    reduceMotion = reduceMotion,
                    motionAtEnd = motionAtEnd,
                )
            }
        }
    }
}

@Composable
private fun AppearancePaletteSwatches() {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.tertiary,
        ).forEach { color ->
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@Composable
private fun AppearanceVoteControls(
    mode: VoteControlMode,
    palette: VoteColorPalette,
    selectedDirection: Int,
) {
    val upTone = appearanceVoteTone(
        direction = 1,
        selectedDirection = selectedDirection,
        palette = palette,
    )
    val downTone = appearanceVoteTone(
        direction = -1,
        selectedDirection = selectedDirection,
        palette = palette,
    )
    val selectedTone = appearanceVoteTone(
        direction = selectedDirection,
        selectedDirection = selectedDirection,
        palette = palette,
    )
    val up = appearanceVoteTint(upTone)
    val down = appearanceVoteTint(downTone)
    val score = appearanceVoteTint(selectedTone)

    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            // M3221_VOTE_PREVIEW_USES_THE_BAR_ICONS_V1 (#221): the preview draws
            // the vote bar's own icons (filled when cast), not copies of them.
            Icon(
                voteStateIcon(direction = 1, mode = mode, voted = selectedDirection > 0),
                contentDescription = null,
                tint = up,
                modifier = Modifier.size(18.dp),
            )
            Text(
                if (selectedDirection > 0) "128" else "126",
                style = MaterialTheme.typography.labelLarge,
                color = score,
            )
            Icon(
                voteStateIcon(direction = -1, mode = mode, voted = selectedDirection < 0),
                contentDescription = null,
                tint = down,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

internal enum class AppearanceVoteTone {
    NEUTRAL,
    THEME_ACCENT,
    UPVOTE,
    DOWNVOTE,
}

internal fun appearanceVoteTone(
    direction: Int,
    selectedDirection: Int,
    palette: VoteColorPalette,
): AppearanceVoteTone {
    if (direction != selectedDirection) return AppearanceVoteTone.NEUTRAL
    return when (palette) {
        VoteColorPalette.NEUTRAL -> AppearanceVoteTone.THEME_ACCENT
        VoteColorPalette.RED_BLUE -> if (direction > 0) {
            AppearanceVoteTone.UPVOTE
        } else {
            AppearanceVoteTone.DOWNVOTE
        }
    }
}

internal fun appearancePreviewSelectedVoteDirection(
    motionAtEnd: Boolean,
): Int = if (motionAtEnd) -1 else 1

@Composable
private fun appearanceVoteTint(
    tone: AppearanceVoteTone,
): Color = when (tone) {
    AppearanceVoteTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    // LOWUX3_VOTE_PREVIEW_USES_THE_REAL_ACCENT_V1 (OCB-256): the vote bar
    // tints a Theme accent vote with voteDirectionTint, not the theme's
    // primary; the preview showed another colour.
    // ISSUE218_VOTE_TINTS_PER_THEME_V1: the preview reads the same helper as
    // the vote bar (per theme) instead of copies of its colours.
    AppearanceVoteTone.THEME_ACCENT -> voteDirectionTint(1, VoteColorPalette.NEUTRAL, isDarkScheme())
    AppearanceVoteTone.UPVOTE -> voteDirectionTint(1, VoteColorPalette.RED_BLUE, isDarkScheme())
    AppearanceVoteTone.DOWNVOTE -> voteDirectionTint(-1, VoteColorPalette.RED_BLUE, isDarkScheme())
}

@Composable
private fun AppearanceMotionTrack(
    reduceMotion: Boolean,
    motionAtEnd: Boolean,
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            if (reduceMotion) "Reduced" else "Motion",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BoxWithConstraints(
            modifier = Modifier
                .width(52.dp)
                .height(12.dp),
        ) {
            val dotSize = 12.dp
            val targetX = appearanceMotionTargetX(
                atEnd = motionAtEnd,
                trackWidth = maxWidth,
                dotSize = dotSize,
            )
            val x by animateDpAsState(
                targetValue = targetX,
                animationSpec = if (reduceMotion) snap() else tween(420),
                label = "appearance-motion-dot",
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
            Box(
                modifier = Modifier
                    .offset(x = x)
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

internal fun appearanceThemeLabel(
    mode: ThemeMode,
    pureBlack: Boolean = false,
    systemDark: Boolean = false,
): String = when (mode) {
    ThemeMode.SYSTEM -> if (pureBlack && systemDark) "System · Pure black" else "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> if (pureBlack) "Pure black" else "Dark"
}

internal fun appearanceMotionTargetX(
    atEnd: Boolean,
    trackWidth: Dp,
    dotSize: Dp,
): Dp = if (atEnd) trackWidth - dotSize else 0.dp
