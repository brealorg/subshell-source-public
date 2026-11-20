package io.github.brealorg.subshell.ui.components

import io.github.brealorg.subshell.ui.UiText
import io.github.brealorg.subshell.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.brealorg.subshell.model.Comment
import io.github.brealorg.subshell.model.Post
import io.github.brealorg.subshell.ui.CommentSwipeAction
import io.github.brealorg.subshell.ui.PostSwipeAction
import io.github.brealorg.subshell.ui.SubshellState
import io.github.brealorg.subshell.ui.VoteColorPalette
import io.github.brealorg.subshell.ui.VoteControlMode
import io.github.brealorg.subshell.ui.symbols.Symbols
import io.github.brealorg.subshell.ui.theme.isDarkScheme
import kotlin.math.absoluteValue
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

// SWIPEACTION02_ANCHORED_DRAGGABLE_V1 (owner 2026-11-19): the row is moved by
// Compose Foundation's AnchoredDraggable (Google's own anchor primitive, the
// replacement Material 3 names for SwipeToDismissBox's deprecated veto
// callback). Sides are physical: the drag offset is never mirrored
// (reverseDirection = false), so a finger moving right is the right action in
// every layout direction.
internal enum class PhysicalSwipeSide {
    LEFT,
    RIGHT,
}

internal enum class SwipeAnchor {
    LEFT,
    SETTLED,
    RIGHT,
}

internal fun physicalSwipeSide(anchor: SwipeAnchor): PhysicalSwipeSide? = when (anchor) {
    SwipeAnchor.LEFT -> PhysicalSwipeSide.LEFT
    SwipeAnchor.RIGHT -> PhysicalSwipeSide.RIGHT
    SwipeAnchor.SETTLED -> null
}

internal fun swipeSideForOffset(offsetPx: Float): PhysicalSwipeSide? = when {
    offsetPx > 0f -> PhysicalSwipeSide.RIGHT
    offsetPx < 0f -> PhysicalSwipeSide.LEFT
    else -> null
}

internal data class SwipeActionVisual(
    val label: UiText,
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
): UiText = UiText.Resource(
    when (action) {
        PostSwipeAction.NONE -> R.string.post_swipe_action_none
        PostSwipeAction.UPVOTE -> if (vote == 1) R.string.feed_action_remove_upvote else R.string.post_swipe_action_upvote
        PostSwipeAction.DOWNVOTE -> if (vote == -1) R.string.feed_action_remove_downvote else R.string.post_swipe_action_downvote
        PostSwipeAction.SAVE -> if (saved) R.string.feed_action_unsave else R.string.sw_save
        PostSwipeAction.HIDE -> if (hidden) R.string.swipe_unhide else R.string.post_swipe_action_hide
    },
)

internal fun commentSwipeActionLabel(
    action: CommentSwipeAction,
    vote: Int,
): UiText = UiText.Resource(
    when (action) {
        CommentSwipeAction.NONE -> R.string.post_swipe_action_none
        CommentSwipeAction.UPVOTE -> if (vote == 1) R.string.feed_action_remove_upvote else R.string.comment_swipe_action_upvote
        CommentSwipeAction.DOWNVOTE -> if (vote == -1) R.string.feed_action_remove_downvote else R.string.comment_swipe_action_downvote
    },
)

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
    label: UiText,
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

/** SWIPEDISTANCE01_THE_READER_PICKS_HOW_FAR_A_SWIPE_GOES_V1 */
internal val LocalSwipeDistance =
    androidx.compose.runtime.compositionLocalOf { io.github.brealorg.subshell.ui.SwipeDistance.STANDARD }

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
    val haptic = LocalHapticFeedback.current
    val density = androidx.compose.ui.platform.LocalDensity.current

    val state = remember { AnchoredDraggableState(initialValue = SwipeAnchor.SETTLED) }
    // SWIPEACTION02_ANCHORS_DO_NOT_FOLLOW_A_WRITE_IN_FLIGHT_V1 (Pixel 11, 2026-11-19): a
    // vote switches its action off while Reddit answers, right as the row heads back;
    // removing the anchor then broke the return and the row stayed out. A side keeps its
    // anchor while the row is on that side.
    // SWIPEACTION03_A_ROW_WITHOUT_AN_ENABLED_ACTION_IS_NOT_DRAGGED_V1 (review 2026-11-19):
    // otherwise a side is anchored only while its action is enabled, so a row with none
    // (signed out, archived, locked) leaves the horizontal gesture to the drawer or pager.
    val rowOffset by androidx.compose.runtime.remember(state) {
        androidx.compose.runtime.derivedStateOf { state.offset.takeUnless { it.isNaN() } ?: 0f }
    }
    val leftAnchored = enabled && leftAction != null && (leftAction.enabled || rowOffset < 0f)
    val rightAnchored = enabled && rightAction != null && (rightAction.enabled || rowOffset > 0f)

    // Read the latest actions through State holders: optimistic vote/save/hide
    // state recomposes the row while a swipe is in flight.
    val latestLeftAction = rememberUpdatedState(leftAction)
    val latestRightAction = rememberUpdatedState(rightAction)
    val latestOnLeft = rememberUpdatedState(onLeft)
    val latestOnRight = rememberUpdatedState(onRight)
    val latestReduceMotion = rememberUpdatedState(reduceMotion)

    // SWIPEDISTANCE01_THE_READER_PICKS_HOW_FAR_A_SWIPE_GOES_V1
    val chosenThresholdPx = with(density) { LocalSwipeDistance.current.thresholdDp.dp.toPx() }

    var widthPx by remember { mutableIntStateOf(0) }

    // The action anchors sit where the row can be pulled to; the fling behaviour's
    // positional threshold (the reader's distance) decides, on release, whether the row
    // settles there (action) or back at rest.
    // SWIPEDISTANCE03_THE_ROW_ALWAYS_HAS_ROOM_FOR_ITS_LABEL_V1 (Pixel 11, 2026-11-19): at
    // twice a short threshold the row stopped before "Downvote" (or "Remove upvote")
    // fitted. The row can always be pulled half its width; the threshold alone decides
    // the action, through the fling behaviour.
    val reach = widthPx / 2f
    // SWIPEDISTANCE04_THE_THRESHOLD_STAYS_WITHIN_REACH_V1 (review): on a narrow row half the
    // width can be less than the chosen distance; a slow swipe must still be able to pass it.
    val thresholdPx = if (reach > 0f) minOf(chosenThresholdPx, reach * 0.8f) else chosenThresholdPx
    val latestThresholdPx = rememberUpdatedState(thresholdPx)
    LaunchedEffect(state, reach, leftAnchored, rightAnchored) {
        state.updateAnchors(
            DraggableAnchors {
                SwipeAnchor.SETTLED at 0f
                if (rightAnchored && reach > 0f) SwipeAnchor.RIGHT at reach
                if (leftAnchored && reach > 0f) SwipeAnchor.LEFT at -reach
            },
        )
    }

    // SWIPEACTION02_THE_ACTION_RUNS_ON_RELEASE_AND_THE_ROW_SPRINGS_BACK_V1: released past the
    // threshold or flung, AnchoredDraggable settles the row at an action anchor (Google's
    // fling behaviour decides from distance and speed). Then the action runs and the row
    // goes straight back; nothing slides off the screen.
    // SWIPEACTION02_A_GRAB_ON_THE_WAY_BACK_NEVER_STRANDS_THE_ROW_V1 (Pixel 11, 2026-11-19):
    // a second swipe while the row was on its way back interrupted the return
    // (MutationInterruptedException) and the old one-shot listener died, leaving the row
    // out and later swipes dead. One loop now owns the return: it waits for the row to
    // rest at an action anchor with no finger on it, runs the action only if a new drag
    // got it there (so an interrupted return never votes twice), and retries the return.
    val dragInteractions = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val dragged = dragInteractions.collectIsDraggedAsState()
    val freshDrag = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    LaunchedEffect(dragInteractions) {
        dragInteractions.interactions.collect { interaction ->
            if (interaction is androidx.compose.foundation.interaction.DragInteraction.Start) freshDrag.set(true)
        }
    }
    LaunchedEffect(state) {
        while (true) {
            val anchor = androidx.compose.runtime.snapshotFlow {
                val offset = state.offset
                val right = state.anchors.positionOf(SwipeAnchor.RIGHT)
                val left = state.anchors.positionOf(SwipeAnchor.LEFT)
                when {
                    dragged.value || offset.isNaN() -> null
                    !right.isNaN() && offset >= right - 0.5f -> SwipeAnchor.RIGHT
                    !left.isNaN() && offset <= left + 0.5f -> SwipeAnchor.LEFT
                    else -> null
                }
            }.first { it != null }!!
            val side = physicalSwipeSide(anchor)!!
            if (freshDrag.getAndSet(false)) {
                val visual = when (side) {
                    PhysicalSwipeSide.LEFT -> latestLeftAction.value
                    PhysicalSwipeSide.RIGHT -> latestRightAction.value
                }
                if (visual?.enabled == true) {
                    when (side) {
                        PhysicalSwipeSide.LEFT -> latestOnLeft.value()
                        PhysicalSwipeSide.RIGHT -> latestOnRight.value()
                    }
                }
            }
            try {
                if (latestReduceMotion.value) {
                    state.snapTo(SwipeAnchor.SETTLED)
                } else {
                    state.animateTo(SwipeAnchor.SETTLED)
                }
            } catch (interrupted: kotlinx.coroutines.CancellationException) {
                // A finger took the row (or the anchors moved): the loop decides again.
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
            }
        }
    }

    // Haptic at the activation point, while the finger drags, on a side whose action can
    // run (SWIPEACTION03_THE_HAPTIC_MARKS_ONLY_A_LIVE_THRESHOLD_V1, review: it read the first
    // composition's distance and also fired during the settle and on disabled sides).
    val latestHaptic = rememberUpdatedState(haptic)
    LaunchedEffect(state) {
        androidx.compose.runtime.snapshotFlow {
            val offset = state.offset
            val visual = when (swipeSideForOffset(if (offset.isNaN()) 0f else offset)) {
                PhysicalSwipeSide.LEFT -> latestLeftAction.value
                PhysicalSwipeSide.RIGHT -> latestRightAction.value
                null -> null
            }
            dragged.value && visual?.enabled == true &&
                !offset.isNaN() && offset.absoluteValue >= latestThresholdPx.value
        }.collect { past -> if (past) latestHaptic.value.performHapticFeedback(HapticFeedbackType.LongPress) }
    }

    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .clip(shape)
            .onSizeChanged { size -> widthPx = size.width },
    ) {
        // Behind the row: the action for the side the row is pulled toward.
        androidx.compose.foundation.layout.Box(Modifier.matchParentSize()) {
            // Changes only when the row crosses rest, not on every drag frame.
            val side by androidx.compose.runtime.remember(state) {
                androidx.compose.runtime.derivedStateOf { swipeSideForOffset(rowOffset) }
            }
            val visual = when (side) {
                PhysicalSwipeSide.LEFT -> leftAction
                PhysicalSwipeSide.RIGHT -> rightAction
                null -> null
            }
            if (visual != null && visual.enabled) {
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
                            text = visual.label.asString(),
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
                    val revealed = state.offset.takeUnless { it.isNaN() }?.absoluteValue?.roundToInt() ?: 0
                    val showLabel = swipeActionLabelFits(
                        revealedPx = revealed,
                        edgePaddingPx = edgePadding,
                        iconPx = icon.width,
                        gapPx = gap,
                        labelPx = label.width,
                    )
                    val rowWidth = icon.width + if (showLabel) gap + label.width else 0
                    val rowStart = when (side) {
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
        }
        content(
            Modifier
                .fillMaxWidth()
                // Physical: never mirrored in RTL, like the drag (reverseDirection = false).
                .absoluteOffset { IntOffset(state.offset.takeUnless { it.isNaN() }?.roundToInt() ?: 0, 0) }
                .anchoredDraggable(
                    state = state,
                    reverseDirection = false,
                    orientation = Orientation.Horizontal,
                    enabled = leftAnchored || rightAnchored,
                    interactionSource = dragInteractions,
                    flingBehavior = AnchoredDraggableDefaults.flingBehavior(
                        state = state,
                        // The fling behaviour keeps its lambda: read the current distance.
                        positionalThreshold = { latestThresholdPx.value },
                        // SWIPEACTION02_A_STIFF_SETTLE_MAKES_THE_ACTION_PROMPT_V1 (Pixel 11):
                        // with the default spring the row took ~280 ms from release to the
                        // action anchor; a stiff spring without bounce gets there sooner.
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessHigh,
                        ),
                    ),
                ),
        )
    }
}
