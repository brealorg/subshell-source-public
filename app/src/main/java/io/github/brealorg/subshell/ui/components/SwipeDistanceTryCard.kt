package io.github.brealorg.subshell.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.brealorg.subshell.R
import io.github.brealorg.subshell.ui.SubshellState

internal const val SWIPE_DISTANCE_TRY_CARD_TEST_TAG = "swipe-distance-try-card"

/**
 * SWIPEDISTANCE02_A_CARD_TO_TRY_THE_SWIPE_ON_V1 (owner 2026-11-18): a card under
 * "Swipe distance" that swipes exactly like a post (the same surface, distance and post
 * actions) so the distance can be felt without leaving Settings. Each side shows its
 * action in the feed's colour and icon (as Gmail's swipe settings show both sides), and
 * a swipe only says what it would have done: nothing is sent to Reddit.
 */
@Composable
internal fun SwipeDistanceTryCard(
    state: SubshellState,
    modifier: Modifier = Modifier,
) {
    var lastAction by remember { mutableStateOf<String?>(null) }
    val left = postSwipeActionVisual(
        action = state.postSwipeLeftAction,
        vote = 0,
        saved = false,
        hidden = false,
        enabled = true,
        voteColorPalette = state.voteColorPalette,
        voteControlMode = state.voteControlMode,
    )
    val right = postSwipeActionVisual(
        action = state.postSwipeRightAction,
        vote = 0,
        saved = false,
        hidden = false,
        enabled = true,
        voteColorPalette = state.voteColorPalette,
        voteControlMode = state.voteControlMode,
    )
    val leftLabel = left?.label?.asString()
    val rightLabel = right?.label?.asString()

    SwipeActionSurface(
        enabled = true,
        leftAction = left,
        rightAction = right,
        onLeft = { lastAction = leftLabel },
        onRight = { lastAction = rightLabel },
        reduceMotion = state.motionReduced,
        modifier = modifier.fillMaxWidth().testTag(SWIPE_DISTANCE_TRY_CARD_TEST_TAG),
    ) { contentModifier ->
        Surface(
            modifier = contentModifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.set_swipe_try_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SwipeSideHint(visual = left, label = leftLabel, arrowFirst = true)
                    SwipeSideHint(visual = right, label = rightLabel, arrowFirst = false)
                }
                Text(
                    text = lastAction
                        ?.let { action -> stringResource(R.string.set_swipe_try_result, action) }
                        ?: stringResource(R.string.set_swipe_try_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SwipeSideHint(
    visual: SwipeActionVisual?,
    label: String?,
    arrowFirst: Boolean,
) {
    val text = label ?: stringResource(R.string.post_swipe_action_none)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (arrowFirst) Text("←", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (visual != null) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = visual.containerColor,
                contentColor = visual.contentColor,
            ) {
                Icon(
                    imageVector = visual.icon,
                    contentDescription = null,
                    modifier = Modifier.padding(4.dp).size(16.dp),
                )
            }
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
        if (!arrowFirst) Text("→", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
