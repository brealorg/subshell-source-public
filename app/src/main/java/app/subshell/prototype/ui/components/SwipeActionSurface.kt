package app.subshell.prototype.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.subshell.prototype.model.Comment
import app.subshell.prototype.model.Post
import app.subshell.prototype.ui.CommentSwipeAction
import app.subshell.prototype.ui.PostSwipeAction
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.VoteColorPalette
import app.subshell.prototype.ui.VoteControlMode
import app.subshell.prototype.ui.symbols.Symbols
import app.subshell.prototype.ui.theme.isDarkScheme
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

// ISSUE16_GOOGLE_M3_SWIPE_TO_DISMISS_V1
internal enum class PhysicalSwipeSide {
    LEFT,
    RIGHT,
}

internal fun physicalSwipeSide(
    value: SwipeToDismissBoxValue,
    layoutDirection: LayoutDirection,
): PhysicalSwipeSide? = when (value) {
    SwipeToDismissBoxValue.Settled -> null
    SwipeToDismissBoxValue.StartToEnd ->
        if (layoutDirection == LayoutDirection.Ltr) PhysicalSwipeSide.RIGHT
        else PhysicalSwipeSide.LEFT
    SwipeToDismissBoxValue.EndToStart ->
        if (layoutDirection == LayoutDirection.Ltr) PhysicalSwipeSide.LEFT
        else PhysicalSwipeSide.RIGHT
}

internal fun startToEndUsesRightAction(layoutDirection: LayoutDirection): Boolean =
    layoutDirection == LayoutDirection.Ltr

internal data class SwipeActionVisual(
    val label: String,
    val icon: ImageVector,
    val containerColor: Color,
    val contentColor: Color,
    val enabled: Boolean,
)

internal fun postSwipeActionLabel(
    action: PostSwipeAction,
    vote: Int,
    saved: Boolean,
    hidden: Boolean,
): String = when (action) {
    PostSwipeAction.NONE -> "None"
    PostSwipeAction.UPVOTE -> if (vote == 1) "Remove upvote" else "Upvote"
    PostSwipeAction.DOWNVOTE -> if (vote == -1) "Remove downvote" else "Downvote"
    PostSwipeAction.SAVE -> if (saved) "Unsave" else "Save"
    PostSwipeAction.HIDE -> if (hidden) "Unhide" else "Hide"
}

internal fun commentSwipeActionLabel(
    action: CommentSwipeAction,
    vote: Int,
): String = when (action) {
    CommentSwipeAction.NONE -> "None"
    CommentSwipeAction.UPVOTE -> if (vote == 1) "Remove upvote" else "Upvote"
    CommentSwipeAction.DOWNVOTE -> if (vote == -1) "Remove downvote" else "Downvote"
}

// ISSUE39_SWIPE_VOTE_FOLLOWS_VOTE_SETTINGS_V1
// Vote swipes take colour and icon from the same helpers as the vote bar, so
// they follow "Vote colours" and "Vote controls" and look the same in every
// theme. The dark-theme vote tints are light, so their content is dark.
// ISSUE218_VOTE_TINTS_PER_THEME_V1: the light-theme tints are dark, so their
// content is white (both pairs reach 4.5:1).
internal val VoteSwipeContentColor = Color(0xFF1A1A1A)

internal fun voteSwipeContentColor(darkTheme: Boolean): Color =
    if (darkTheme) VoteSwipeContentColor else Color.White

internal fun voteSwipeActionVisual(
    direction: Int,
    label: String,
    palette: VoteColorPalette,
    mode: VoteControlMode,
    enabled: Boolean,
    darkTheme: Boolean = true,
): SwipeActionVisual = SwipeActionVisual(
    label = label,
    icon = voteDirectionIcon(direction, mode),
    containerColor = voteDirectionTint(direction, palette, darkTheme),
    contentColor = voteSwipeContentColor(darkTheme),
    enabled = enabled,
)

@Composable
internal fun postSwipeActionVisual(
    action: PostSwipeAction,
    vote: Int,
    saved: Boolean,
    hidden: Boolean,
    enabled: Boolean,
    voteColorPalette: VoteColorPalette,
    voteControlMode: VoteControlMode,
    darkTheme: Boolean = isDarkScheme(),
): SwipeActionVisual? = when (action) {
    PostSwipeAction.NONE -> null
    PostSwipeAction.UPVOTE -> voteSwipeActionVisual(
        direction = 1,
        label = postSwipeActionLabel(action, vote, saved, hidden),
        palette = voteColorPalette,
        mode = voteControlMode,
        enabled = enabled,
        darkTheme = darkTheme,
    )
    PostSwipeAction.DOWNVOTE -> voteSwipeActionVisual(
        direction = -1,
        label = postSwipeActionLabel(action, vote, saved, hidden),
        palette = voteColorPalette,
        mode = voteControlMode,
        enabled = enabled,
        darkTheme = darkTheme,
    )
    PostSwipeAction.SAVE -> SwipeActionVisual(
        label = postSwipeActionLabel(action, vote, saved, hidden),
        icon = if (saved) Symbols.Bookmark else Symbols.BookmarkFilled,
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        enabled = enabled,
    )
    PostSwipeAction.HIDE -> SwipeActionVisual(
        label = postSwipeActionLabel(action, vote, saved, hidden),
        icon = if (hidden) Symbols.Visibility else Symbols.VisibilityOff,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        enabled = enabled,
    )
}

internal fun commentSwipeActionVisual(
    action: CommentSwipeAction,
    vote: Int,
    enabled: Boolean,
    voteColorPalette: VoteColorPalette,
    voteControlMode: VoteControlMode,
    darkTheme: Boolean = true,
): SwipeActionVisual? = when (action) {
    CommentSwipeAction.NONE -> null
    CommentSwipeAction.UPVOTE -> voteSwipeActionVisual(
        direction = 1,
        label = commentSwipeActionLabel(action, vote),
        palette = voteColorPalette,
        mode = voteControlMode,
        enabled = enabled,
        darkTheme = darkTheme,
    )
    CommentSwipeAction.DOWNVOTE -> voteSwipeActionVisual(
        direction = -1,
        label = commentSwipeActionLabel(action, vote),
        palette = voteColorPalette,
        mode = voteControlMode,
        enabled = enabled,
        darkTheme = darkTheme,
    )
}

// ISSUE39_SWIPE_LABEL_INSIDE_REVEALED_AREA_V1
// The label is shown only once the revealed strip holds edge padding, icon,
// gap and the whole label; until then the icon stands alone at the edge.
internal fun swipeActionLabelFits(
    revealedPx: Int,
    edgePaddingPx: Int,
    iconPx: Int,
    gapPx: Int,
    labelPx: Int,
): Boolean = revealedPx >= edgePaddingPx * 2 + iconPx + gapPx + labelPx

internal fun postSwipeActionEnabled(
    state: SubshellState,
    post: Post,
    action: PostSwipeAction,
): Boolean = when (action) {
    PostSwipeAction.NONE -> false
    PostSwipeAction.UPVOTE,
    PostSwipeAction.DOWNVOTE,
    -> state.postVoteControlEnabled(post)
    PostSwipeAction.SAVE ->
        state.postSaveControlEnabled(post)
    PostSwipeAction.HIDE -> state.postVisibilityControlEnabled(post)
}

internal fun commentSwipeActionEnabled(
    state: SubshellState,
    comment: Comment,
    action: CommentSwipeAction,
): Boolean = when (action) {
    CommentSwipeAction.NONE -> false
    CommentSwipeAction.UPVOTE,
    CommentSwipeAction.DOWNVOTE,
    -> state.commentVoteControlEnabled(comment)
}

internal fun dispatchPostSwipeAction(
    state: SubshellState,
    post: Post,
    authoritativeHiddenHint: Boolean,
    action: PostSwipeAction,
): Boolean {
    if (!postSwipeActionEnabled(state, post, action)) return false
    when (action) {
        PostSwipeAction.NONE -> return false
        PostSwipeAction.UPVOTE -> state.vote(post, 1)
        PostSwipeAction.DOWNVOTE -> state.vote(post, -1)
        PostSwipeAction.SAVE -> state.toggleSaved(post)
        PostSwipeAction.HIDE -> state.togglePostHidden(post, authoritativeHiddenHint)
    }
    return true
}

internal fun dispatchCommentSwipeAction(
    state: SubshellState,
    comment: Comment,
    action: CommentSwipeAction,
): Boolean {
    if (!commentSwipeActionEnabled(state, comment, action)) return false
    when (action) {
        CommentSwipeAction.NONE -> return false
        CommentSwipeAction.UPVOTE -> state.vote(comment, 1)
        CommentSwipeAction.DOWNVOTE -> state.vote(comment, -1)
    }
    return true
}

@Composable
internal fun SwipeActionSurface(
    enabled: Boolean,
    leftAction: SwipeActionVisual?,
    rightAction: SwipeActionVisual?,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
    shape: Shape = MaterialTheme.shapes.medium,
    content: @Composable (Modifier) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val leftAvailable = enabled && leftAction?.enabled == true
    val rightAvailable = enabled && rightAction?.enabled == true
    val startToEndAvailable =
        if (startToEndUsesRightAction(layoutDirection)) rightAvailable else leftAvailable
    val endToStartAvailable =
        if (startToEndUsesRightAction(layoutDirection)) leftAvailable else rightAvailable

    val swipeState = rememberSwipeToDismissBoxState(
        positionalThreshold = SwipeToDismissBoxDefaults.positionalThreshold,
    )

    LaunchedEffect(swipeState.targetValue) {
        if (swipeState.targetValue != SwipeToDismissBoxValue.Settled) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    fun actionFor(value: SwipeToDismissBoxValue): Pair<SwipeActionVisual?, (() -> Unit)?> =
        when (physicalSwipeSide(value, layoutDirection)) {
            PhysicalSwipeSide.LEFT -> leftAction to onLeft
            PhysicalSwipeSide.RIGHT -> rightAction to onRight
            null -> null to null
        }

    // ISSUE16_M3_STABLE_ONDISMISS_CALLBACK_V1
    // Material3 keys its internal dismiss LaunchedEffect with onDismiss.
    // Keep this callback identity stable while optimistic vote/save/hide state
    // recomposes the row; read the latest action values through State holders.
    val latestLeftAction = rememberUpdatedState(leftAction)
    val latestRightAction = rememberUpdatedState(rightAction)
    val latestOnLeft = rememberUpdatedState(onLeft)
    val latestOnRight = rememberUpdatedState(onRight)
    val latestLayoutDirection = rememberUpdatedState(layoutDirection)
    val latestReduceMotion = rememberUpdatedState(reduceMotion)

    val stableOnDismiss = remember(swipeState) {
        { direction: SwipeToDismissBoxValue ->
            val side = physicalSwipeSide(
                value = direction,
                layoutDirection = latestLayoutDirection.value,
            )
            val visual = when (side) {
                PhysicalSwipeSide.LEFT -> latestLeftAction.value
                PhysicalSwipeSide.RIGHT -> latestRightAction.value
                null -> null
            }
            val action = when (side) {
                PhysicalSwipeSide.LEFT -> latestOnLeft.value
                PhysicalSwipeSide.RIGHT -> latestOnRight.value
                null -> null
            }

            if (visual?.enabled == true && action != null) {
                action()
            }

            scope.launch {
                if (latestReduceMotion.value) {
                    swipeState.snapTo(SwipeToDismissBoxValue.Settled)
                } else {
                    swipeState.reset()
                }
            }
            Unit
        }
    }

    SwipeToDismissBox(
        state = swipeState,
        modifier = modifier.clip(shape),
        gesturesEnabled = enabled && (leftAvailable || rightAvailable),
        enableDismissFromStartToEnd = startToEndAvailable,
        enableDismissFromEndToStart = endToStartAvailable,
        backgroundContent = {
            val direction = swipeState.dismissDirection
            val (visual, _) = actionFor(direction)
            if (visual != null && visual.enabled) {
                val swipeSide = physicalSwipeSide(direction, layoutDirection)
                Layout(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(visual.containerColor),
                    content = {
                        Icon(
                            imageVector = visual.icon,
                            contentDescription = null,
                            tint = visual.contentColor,
                            modifier = Modifier.size(22.dp),
                        )
                        Text(
                            text = visual.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = visual.contentColor,
                            maxLines = 1,
                            softWrap = false,
                        )
                    },
                ) { measurables, constraints ->
                    val loose = constraints.copy(minWidth = 0, minHeight = 0)
                    val icon = measurables[0].measure(loose)
                    val label = measurables[1].measure(
                        loose.copy(maxWidth = Constraints.Infinity),
                    )
                    val width = constraints.maxWidth
                    val height = constraints.maxHeight
                    val edgePadding = 18.dp.roundToPx()
                    val gap = 8.dp.roundToPx()
                    // Read in the layout phase: dragging relayouts, it does not recompose.
                    val revealed = try {
                        swipeState.requireOffset().absoluteValue.roundToInt()
                    } catch (_: IllegalStateException) {
                        0
                    }
                    val showLabel = swipeActionLabelFits(
                        revealedPx = revealed,
                        edgePaddingPx = edgePadding,
                        iconPx = icon.width,
                        gapPx = gap,
                        labelPx = label.width,
                    )
                    val rowWidth = icon.width + if (showLabel) gap + label.width else 0
                    val rowStart = when (swipeSide) {
                        PhysicalSwipeSide.LEFT -> width - edgePadding - rowWidth
                        PhysicalSwipeSide.RIGHT -> edgePadding
                        null -> (width - rowWidth) / 2
                    }
                    layout(width, height) {
                        // Physical sides: place, not placeRelative.
                        icon.place(rowStart, (height - icon.height) / 2)
                        if (showLabel) {
                            label.place(
                                rowStart + icon.width + gap,
                                (height - label.height) / 2,
                            )
                        }
                    }
                }
            }
        },
        onDismiss = stableOnDismiss,
    ) {
        content(Modifier.fillMaxWidth())
    }
}
