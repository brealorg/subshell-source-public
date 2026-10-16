package app.subshell.prototype.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.subshell.prototype.ui.ChromeBehavior
import app.subshell.prototype.ui.CommentSwipeAction
import app.subshell.prototype.ui.CommunityDiscoveryLayout
import app.subshell.prototype.ui.InteractionSide
import app.subshell.prototype.ui.NavigationLabelMode
import app.subshell.prototype.ui.PostSwipeAction
import app.subshell.prototype.ui.SubredditLabelStyle
import app.subshell.prototype.ui.TabletLayoutMode
import app.subshell.prototype.ui.VoteColorPalette
import app.subshell.prototype.ui.VoteControlMode
import app.subshell.prototype.ui.components.SwipeActionSurface
import app.subshell.prototype.ui.components.commentSwipeActionVisual
import app.subshell.prototype.ui.components.postSwipeActionVisual
import app.subshell.prototype.ui.theme.isDarkScheme
import kotlinx.coroutines.delay

internal enum class NavigationPreviewContext {
    NAVIGATION,
    TABLET_MODE,
    COMMUNITY_DISCOVERY,
    POST_SWIPE,
    COMMENT_SWIPE,
}

private val NavigationSettingsPreviewViewportHeight = 142.dp
private val NavigationSettingsPreviewStatusWidth = 88.dp

@Composable
internal fun NavigationSettingsPreview(
    topBarBehavior: ChromeBehavior,
    bottomBarBehavior: ChromeBehavior,
    interactionSide: InteractionSide,
    drawerSide: InteractionSide,
    navigationLabelMode: NavigationLabelMode,
    subredditLabelStyle: SubredditLabelStyle,
    tabletLayoutMode: TabletLayoutMode,
    communityDiscoveryLayout: CommunityDiscoveryLayout,
    postSwipeLeftAction: PostSwipeAction,
    postSwipeRightAction: PostSwipeAction,
    commentSwipeLeftAction: CommentSwipeAction,
    commentSwipeRightAction: CommentSwipeAction,
    voteColorPalette: VoteColorPalette,
    voteControlMode: VoteControlMode,
    reduceMotion: Boolean,
    previewContext: NavigationPreviewContext,
    pageScrolling: Boolean,
    modifier: Modifier = Modifier,
    // LOWUX3_SWIPE_PREVIEW_FOLLOWS_THE_SWIPE_SETTING_V1 (OCB-263): with swipe
    // actions off the preview still swiped, as if they worked in the feeds.
    swipeActionsEnabled: Boolean = true,
) {
    var demoScrolling by remember { mutableStateOf(false) }
    var replayToken by remember { mutableIntStateOf(0) }
    var initialized by remember { mutableStateOf(false) }
    var playedReplayToken by remember { mutableIntStateOf(0) }

    LaunchedEffect(topBarBehavior, bottomBarBehavior, replayToken) {
        if (!initialized) {
            initialized = true
            return@LaunchedEffect
        }
        // LOWUI7_PREVIEW_DEMO_RESPECTS_REDUCE_MOTION_V1 (OCB-262): with Reduce
        // motion the demo no longer plays by itself on a setting change; only
        // the reader's own "Replay preview" tap plays it.
        val replayRequested = replayToken != playedReplayToken
        playedReplayToken = replayToken
        if (reduceMotion && !replayRequested) return@LaunchedEffect
        demoScrolling = false
        delay(180)
        demoScrolling = true
        delay(1_050)
        demoScrolling = false
    }

    val scrolling = navigationPreviewScrolling(
        pageScrolling = pageScrolling,
        demoScrolling = demoScrolling,
    )

    Surface(
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
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.70f)),
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
                        // A11Y01A411_STABLE_PREVIEW_CHROME_V1
                        Text(
                            "Live preview",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            when (previewContext) {
                                NavigationPreviewContext.NAVIGATION ->
                                    "Scroll this page or tap the preview to replay the change."
                                NavigationPreviewContext.TABLET_MODE ->
                                    "Switch modes to compare the same fixed preview frame."
                                NavigationPreviewContext.COMMUNITY_DISCOVERY ->
                                    "Switch layouts to compare the same fixed preview frame."
                                NavigationPreviewContext.POST_SWIPE ->
                                    "Swipe the card to preview the configured post actions."
                                NavigationPreviewContext.COMMENT_SWIPE ->
                                    "Swipe the card to preview the configured comment actions."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Surface(
                        modifier = Modifier.width(NavigationSettingsPreviewStatusWidth),
                        shape = MaterialTheme.shapes.small,
                        color = if (
                            previewContext != NavigationPreviewContext.NAVIGATION ||
                            scrolling
                        ) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        border = BorderStroke(
                            1.dp,
                            if (
                                previewContext != NavigationPreviewContext.NAVIGATION ||
                                scrolling
                            ) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                            } else {
                                Color.Transparent
                            },
                        ),
                    ) {
                        Crossfade(
                            targetState = when (previewContext) {
                                NavigationPreviewContext.TABLET_MODE -> tabletLayoutMode.label
                                NavigationPreviewContext.COMMUNITY_DISCOVERY ->
                                    communityDiscoveryLayout.label
                                NavigationPreviewContext.NAVIGATION ->
                                    if (scrolling) "Scrolling" else "At rest"
                                NavigationPreviewContext.POST_SWIPE -> "Post"
                                NavigationPreviewContext.COMMENT_SWIPE -> "Comment"
                            },
                            animationSpec = tween(180),
                            label = "preview-state-label",
                        ) { label ->
                            Text(
                                text = label,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge,
                                textAlign = TextAlign.Center,
                                color =
                                    if (
                                        previewContext != NavigationPreviewContext.NAVIGATION ||
                                        scrolling
                                    ) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                            )
                        }
                    }
                }

                Crossfade(
                    targetState = previewContext,
                    animationSpec = tween(220),
                    label = "navigation-preview-context",
                ) { context ->
                    when (context) {
                        NavigationPreviewContext.NAVIGATION ->
                            NavigationPhoneSnapshot(
                                scrolling = scrolling,
                                topBarBehavior = topBarBehavior,
                                bottomBarBehavior = bottomBarBehavior,
                                interactionSide = interactionSide,
                                drawerSide = drawerSide,
                                navigationLabelMode = navigationLabelMode,
                                subredditLabelStyle = subredditLabelStyle,
                                onReplay = { replayToken += 1 },
                            )

                        NavigationPreviewContext.TABLET_MODE ->
                            NavigationTabletSnapshot(
                                mode = tabletLayoutMode,
                            )

                        NavigationPreviewContext.COMMUNITY_DISCOVERY ->
                            CommunityDiscoveryPreviewSnapshot(
                                layout = communityDiscoveryLayout,
                            )

                        NavigationPreviewContext.POST_SWIPE,
                        NavigationPreviewContext.COMMENT_SWIPE ->
                            NavigationSwipePreviewSnapshot(
                                target = previewContext,
                                postLeftAction = postSwipeLeftAction,
                                postRightAction = postSwipeRightAction,
                                commentLeftAction = commentSwipeLeftAction,
                                commentRightAction = commentSwipeRightAction,
                                voteColorPalette = voteColorPalette,
                                voteControlMode = voteControlMode,
                                reduceMotion = reduceMotion,
                                swipeActionsEnabled = swipeActionsEnabled,
                            )
                    }
                }
            }
        }
    }
}


// ISSUE16_SINGLE_SHARED_NAVIGATION_SWIPE_PREVIEW_V1
@Composable
private fun NavigationSwipePreviewSnapshot(
    target: NavigationPreviewContext,
    postLeftAction: PostSwipeAction,
    postRightAction: PostSwipeAction,
    commentLeftAction: CommentSwipeAction,
    commentRightAction: CommentSwipeAction,
    voteColorPalette: VoteColorPalette,
    voteControlMode: VoteControlMode,
    reduceMotion: Boolean,
    swipeActionsEnabled: Boolean = true,
) {
    val leftVisual = when (target) {
        NavigationPreviewContext.POST_SWIPE -> postSwipeActionVisual(
            postLeftAction,
            vote = 0,
            saved = false,
            hidden = false,
            enabled = true,
            voteColorPalette = voteColorPalette,
            voteControlMode = voteControlMode,
        )
        NavigationPreviewContext.COMMENT_SWIPE ->
            commentSwipeActionVisual(
                commentLeftAction,
                vote = 0,
                enabled = true,
                voteColorPalette = voteColorPalette,
                voteControlMode = voteControlMode,
                darkTheme = isDarkScheme(),
            )
        else -> return
    }

    // leftVisual's guard narrows target to POST_SWIPE or COMMENT_SWIPE here.
    val rightVisual = when (target) {
        NavigationPreviewContext.POST_SWIPE -> postSwipeActionVisual(
            postRightAction,
            vote = 0,
            saved = false,
            hidden = false,
            enabled = true,
            voteColorPalette = voteColorPalette,
            voteControlMode = voteControlMode,
        )
        NavigationPreviewContext.COMMENT_SWIPE ->
            commentSwipeActionVisual(
                commentRightAction,
                vote = 0,
                enabled = true,
                voteColorPalette = voteColorPalette,
                voteControlMode = voteControlMode,
                darkTheme = isDarkScheme(),
            )
    }

    SwipeActionSurface(
        enabled = swipeActionsEnabled,
        leftAction = leftVisual,
        rightAction = rightVisual,
        onLeft = {},
        onRight = {},
        reduceMotion = reduceMotion,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .height(NavigationSettingsPreviewViewportHeight),
    ) { contentModifier ->
        Surface(
            modifier = contentModifier.fillMaxSize(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
            ),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = if (target == NavigationPreviewContext.POST_SWIPE) {
                        "r/android · 2h"
                    } else {
                        "u/subshell_user · 2h"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (target == NavigationPreviewContext.POST_SWIPE) {
                        "Swipe preview post"
                    } else {
                        "This is a comment swipe preview."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "Preview only · no Reddit action is performed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NavigationTabletSnapshot(
    mode: TabletLayoutMode,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(NavigationSettingsPreviewViewportHeight),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Crossfade(
            targetState = mode,
            animationSpec = tween(220),
            label = "tablet-layout-preview",
        ) { currentMode ->
            when (currentMode) {
                TabletLayoutMode.SPLIT -> Row(Modifier.fillMaxSize()) {
                    TabletPreviewListPane(
                        modifier = Modifier
                            .weight(0.38f)
                            .fillMaxHeight(),
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                    TabletPreviewPostPane(
                        modifier = Modifier
                            .weight(0.62f)
                            .fillMaxHeight(),
                    )
                }

                TabletLayoutMode.SINGLE -> Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.weight(0.18f))
                    TabletPreviewPostPane(
                        modifier = Modifier
                            .weight(0.64f)
                            .fillMaxHeight()
                            .padding(vertical = 7.dp),
                    )
                    Spacer(Modifier.weight(0.18f))
                }
            }
        }
    }
}

@Composable
private fun TabletPreviewListPane(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            "Home",
            style = MaterialTheme.typography.labelLarge,
        )
        repeat(3) { index ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                color = if (index == 0) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = when (index) {
                            0 -> "r/plex"
                            1 -> "r/android"
                            else -> "r/subshell"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                    PreviewLine(if (index == 0) 0.88f else 0.72f)
                    PreviewLine(if (index == 1) 0.56f else 0.64f)
                }
            }
        }
    }
}

@Composable
private fun TabletPreviewPostPane(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "r/plex",
            style = MaterialTheme.typography.labelLarge,
        )
        Surface(
            shape = MaterialTheme.shapes.extraSmall,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Text(
                "Discussion",
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Text(
            "Why does Plex still have no API keys in settings?",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
            ),
            maxLines = 2,
        )
        PreviewLine(0.96f)
        PreviewLine(0.86f)
        PreviewLine(0.70f)
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("↑ 66", style = MaterialTheme.typography.labelSmall)
            Text("□ 11", style = MaterialTheme.typography.labelSmall)
        }
    }
}

// SUBSHELL_SUBS05A1_DISCOVERY_SETTINGS_PREVIEW_V2
@Composable
private fun CommunityDiscoveryPreviewSnapshot(
    layout: CommunityDiscoveryLayout,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(NavigationSettingsPreviewViewportHeight),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Discover communities",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                )
                if (layout == CommunityDiscoveryLayout.CAROUSEL) {
                    Text(
                        "See all",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(25.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (layout == CommunityDiscoveryLayout.CAROUSEL) {
                            "⌕  Search communities"
                        } else {
                            "⌕  Search by name or topic"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Crossfade(
                targetState = layout,
                animationSpec = tween(180),
                label = "community-discovery-layout-preview",
            ) { currentLayout ->
                when (currentLayout) {
                    CommunityDiscoveryLayout.CAROUSEL ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            DiscoveryPreviewCard(
                                name = "r/android",
                                modifier = Modifier.weight(1f),
                            )
                            DiscoveryPreviewCard(
                                name = "r/plex",
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.weight(0.35f))
                        }

                    CommunityDiscoveryLayout.LIST ->
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            DiscoveryPreviewRow("r/android")
                            DiscoveryPreviewRow("r/plex")
                        }
                }
            }
        }
    }
}

@Composable
private fun DiscoveryPreviewCard(
    name: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(63.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                name,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                maxLines = 1,
            )
            PreviewLine(0.72f)
            Spacer(Modifier.weight(1f))
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Text(
                    "+ Join",
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun DiscoveryPreviewRow(
    name: String,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(29.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                modifier = Modifier.size(16.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {}
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    name,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    maxLines = 1,
                )
                PreviewLine(0.46f)
            }
            Text(
                "+ Join",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun NavigationPhoneSnapshot(
    scrolling: Boolean,
    topBarBehavior: ChromeBehavior,
    bottomBarBehavior: ChromeBehavior,
    interactionSide: InteractionSide,
    drawerSide: InteractionSide,
    navigationLabelMode: NavigationLabelMode,
    subredditLabelStyle: SubredditLabelStyle,
    onReplay: () -> Unit,
) {
    val topVisible = navigationPreviewChromeVisible(topBarBehavior, scrolling)
    val bottomVisible = navigationPreviewChromeVisible(bottomBarBehavior, scrolling)
    val interactionVisible = navigationPreviewInteractionVisible(scrolling)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(NavigationSettingsPreviewViewportHeight)
            // LOWUI7_LABELS_SAY_WHAT_A_CONTROL_DOES_V1 (OCB-264): one button that
            // says what it shows and what a tap does; the drawing is decoration.
            .clickable(onClickLabel = "Replay preview", role = Role.Button, onClick = onReplay)
            .clearAndSetSemantics { contentDescription = "Navigation preview" },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            AnimatedDrawerPositionIndicator(
                side = drawerSide,
                containerWidth = maxWidth,
            )

            AnimatedVisibility(
                visible = topVisible,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 9.dp, vertical = 6.dp),
                enter = slideInVertically(
                    animationSpec = tween(260),
                    initialOffsetY = { -it },
                ) + fadeIn(animationSpec = tween(180)),
                exit = slideOutVertically(
                    animationSpec = tween(240),
                    targetOffsetY = { -it },
                ) + fadeOut(animationSpec = tween(160)),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(27.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Crossfade(
                            targetState = subredditLabelStyle.example,
                            animationSpec = tween(180),
                            label = "preview-subreddit-label",
                        ) { example ->
                            Text(
                                example.replace("linux", "android"),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 36.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PreviewLine(0.82f)
                PreviewLine(1f)
                PreviewLine(0.64f)
                Spacer(Modifier.height(3.dp))
                PreviewLine(0.90f)
                PreviewLine(0.70f)
            }

            val interactionX = navigationPreviewInteractionX(
                side = interactionSide,
                containerWidth = maxWidth,
            )
            val interactionBottom = navigationPreviewInteractionBottom(
                bottomBarVisible = bottomVisible,
            )
            val animatedInteractionX by animateDpAsState(
                targetValue = interactionX,
                animationSpec = tween(260),
                label = "preview-interaction-x",
            )
            val animatedInteractionBottom by animateDpAsState(
                targetValue = interactionBottom,
                animationSpec = tween(240),
                label = "preview-interaction-bottom",
            )

            AnimatedVisibility(
                visible = interactionVisible,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(
                        x = animatedInteractionX,
                        y = -animatedInteractionBottom,
                    ),
                enter = fadeIn(animationSpec = tween(180)),
                exit = fadeOut(animationSpec = tween(140)),
            ) {
                InteractionDot()
            }

            AnimatedVisibility(
                visible = bottomVisible,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 9.dp, vertical = 6.dp),
                enter = slideInVertically(
                    animationSpec = tween(260),
                    initialOffsetY = { it },
                ) + fadeIn(animationSpec = tween(180)),
                exit = slideOutVertically(
                    animationSpec = tween(240),
                    targetOffsetY = { it },
                ) + fadeOut(animationSpec = tween(160)),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PreviewNavigationItem(
                            label = "Home",
                            selected = true,
                            showLabel = navigationPreviewLabelVisible(
                                navigationLabelMode,
                                selected = true,
                            ),
                        )
                        PreviewNavigationItem(
                            label = "Subs",
                            selected = false,
                            showLabel = navigationPreviewLabelVisible(
                                navigationLabelMode,
                                selected = false,
                            ),
                        )
                        PreviewNavigationItem(
                            label = "You",
                            selected = false,
                            showLabel = navigationPreviewLabelVisible(
                                navigationLabelMode,
                                selected = false,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.AnimatedDrawerPositionIndicator(
    side: InteractionSide,
    containerWidth: Dp,
) {
    val indicatorWidth = 27.dp
    val targetX = navigationPreviewDrawerIndicatorX(
        side = side,
        containerWidth = containerWidth,
        indicatorWidth = indicatorWidth,
    )
    val x by animateDpAsState(
        targetValue = targetX,
        animationSpec = tween(280),
        label = "preview-drawer-position-x",
    )

    Surface(
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset(
                x = x,
                y = navigationPreviewDrawerIndicatorTop(),
            )
            .width(indicatorWidth)
            .height(navigationPreviewDrawerIndicatorHeight()),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f),
        border = BorderStroke(
            1.5.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.78f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            repeat(3) { index ->
                Box(
                    modifier = Modifier
                        .width(if (index == 1) 14.dp else 10.dp)
                        .height(2.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onPrimaryContainer),
                )
                if (index < 2) Spacer(Modifier.height(3.dp))
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (side == InteractionSide.LEFT) "›" else "‹",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

internal fun navigationPreviewDrawerIndicatorX(
    side: InteractionSide,
    containerWidth: Dp,
    indicatorWidth: Dp,
): Dp = when (side) {
    InteractionSide.LEFT -> 0.dp
    InteractionSide.RIGHT -> containerWidth - indicatorWidth
}

internal fun navigationPreviewDrawerIndicatorTop(): Dp = 34.dp

internal fun navigationPreviewDrawerIndicatorHeight(): Dp = 52.dp

internal fun navigationPreviewBottomNavigationTop(): Dp = 98.dp

@Composable
private fun InteractionDot() {
    Surface(
        modifier = Modifier.size(28.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                "+",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

internal fun navigationPreviewInteractionX(
    side: InteractionSide,
    containerWidth: Dp,
): Dp {
    val dotSize = 28.dp
    val edgePadding = navigationPreviewInteractionEdgePadding()
    return when (side) {
        InteractionSide.LEFT -> edgePadding
        InteractionSide.RIGHT -> containerWidth - edgePadding - dotSize
    }
}

internal fun navigationPreviewInteractionEdgePadding(): Dp = 42.dp

internal fun navigationPreviewInteractionBottom(
    bottomBarVisible: Boolean,
): Dp = if (bottomBarVisible) 48.dp else 11.dp

@Composable
private fun PreviewLine(widthFraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(6.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f),
            ),
    )
}

@Composable
private fun RowScope.PreviewNavigationItem(
    label: String,
    selected: Boolean,
    showLabel: Boolean,
) {
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Box(
            modifier = Modifier
                .size(if (selected) 9.dp else 7.dp)
                .clip(CircleShape)
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
                    },
                ),
        )
        AnimatedVisibility(
            visible = showLabel,
            enter = fadeIn(animationSpec = tween(180)),
            exit = fadeOut(animationSpec = tween(140)),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
            )
        }
    }
}

internal fun navigationPreviewChromeVisible(
    behavior: ChromeBehavior,
    scrolling: Boolean,
): Boolean = !scrolling || behavior == ChromeBehavior.ALWAYS_SHOW

internal fun navigationPreviewLabelVisible(
    mode: NavigationLabelMode,
    selected: Boolean,
): Boolean = when (mode) {
    NavigationLabelMode.ON -> true
    NavigationLabelMode.PARTIAL -> selected
    NavigationLabelMode.OFF -> false
}

internal fun navigationPreviewScrolling(
    pageScrolling: Boolean,
    demoScrolling: Boolean,
): Boolean = pageScrolling || demoScrolling

internal fun navigationPreviewInteractionVisible(
    scrolling: Boolean,
): Boolean = !scrolling
