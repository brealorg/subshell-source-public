package app.subshell.prototype.ui.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import app.subshell.prototype.ui.SecondaryDestination
import app.subshell.prototype.ui.SubshellState

/**
 * The Scaffold's floating action button: "Create feed" while the custom
 * feeds list offers it, otherwise the animated Create / Message button for
 * the surfaces that compose a post or a new message. It travels and fades
 * with the bottom bar.
 *
 * Moved out of SubshellApp verbatim. The hidden fraction arrives as the
 * animated State, so it is still read in the graphics layer only.
 */
@Composable
internal fun AppFloatingActionButton(
    state: SubshellState,
    customFeedFabVisible: Boolean,
    createButtonVisible: Boolean,
    createPostButtonVisible: Boolean,
    fabTravelPx: Float,
    bottomBarHiddenFractionState: State<Float>,
    customFeedCreateRequestedState: MutableState<Boolean>,
) {
    val bottomBarHiddenFraction by bottomBarHiddenFractionState
    var customFeedCreateRequested by customFeedCreateRequestedState
    // ISSUE73_HIDDEN_FAB_LEAVES_THE_SCREEN_V1: the fade and a fixed 120 dp
    // move left the button on screen, invisible but still tappable and
    // focusable (a tap on a post's vote opened the composer). Once the bottom
    // chrome is fully hidden the button is gone; derived so scrolling does not
    // recompose it every frame.
    val chromeFullyHidden by remember(bottomBarHiddenFractionState) {
        derivedStateOf { bottomBarHiddenFractionState.value >= 1f }
    }
    // VIDEO03_FAB_STEPS_ASIDE_FOR_INLINE_CONTROLS_V1: not over a seek bar in use.
    val videoControlsInUse = state.inlineVideoControlsMediaId != null
    if (customFeedFabVisible && !chromeFullyHidden && !videoControlsInUse) {
        ExtendedFloatingActionButton(
            modifier = Modifier.graphicsLayer {
                translationY = fabTravelPx * bottomBarHiddenFraction
                alpha = 1f - bottomBarHiddenFraction
            },
            onClick = { customFeedCreateRequested = true },
            icon = {
                Icon(Icons.Rounded.Add, contentDescription = null)
            },
            text = { Text("Create feed") },
        )
    } else if (!customFeedFabVisible) {
        AnimatedVisibility(visible = createButtonVisible && !chromeFullyHidden && !videoControlsInUse) {
        ExtendedFloatingActionButton(
            modifier = Modifier.graphicsLayer {
                translationY = fabTravelPx * bottomBarHiddenFraction
                alpha = 1f - bottomBarHiddenFraction
            },
            onClick = {
                if (createPostButtonVisible) {
                    state.openPostComposer()
                } else {
                    state.openSecondary(
                        SecondaryDestination.NEW_MESSAGE,
                    )
                }
            },
            icon = {
                Icon(
                    if (createPostButtonVisible) Icons.Rounded.Add else Icons.AutoMirrored.Rounded.Send,
                    contentDescription = null,
                )
            },
            text = { Text(if (createPostButtonVisible) "Create" else "Message") },
        )
    }
    }
}
