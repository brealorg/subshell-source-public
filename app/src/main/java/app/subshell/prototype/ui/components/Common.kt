package app.subshell.prototype.ui.components

import androidx.compose.ui.graphics.compositeOver
import app.subshell.prototype.ui.error.LocalViewportErrorHostState
import app.subshell.prototype.ui.noticeInfo
import app.subshell.prototype.ui.postFollowActionLabel
import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.subshell.prototype.R
import app.subshell.prototype.data.RedditListingTimePeriod
import app.subshell.prototype.media.RemoteImageLoader
import app.subshell.prototype.media.RemoteMediaLoadResult
import app.subshell.prototype.model.FlairRichTextSegment
import app.subshell.prototype.model.Post
import app.subshell.prototype.data.account.OwnContentType
import app.subshell.prototype.data.account.ownContentActions
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.FeedSort
import app.subshell.prototype.ui.ProfileActivityEvidence
import app.subshell.prototype.ui.PostLayout
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.MainDestination
import app.subshell.prototype.ui.SecondaryDestination
import app.subshell.prototype.ui.VoteColorPalette
import app.subshell.prototype.ui.VoteControlMode
import app.subshell.prototype.ui.copyPostLink
import app.subshell.prototype.ui.sharePostLink
import app.subshell.prototype.ui.supportsTimePeriod
import app.subshell.prototype.ui.symbols.Symbols
import app.subshell.prototype.ui.theme.SubshellOrange
import app.subshell.prototype.ui.theme.SubshellOrangeSoft
import app.subshell.prototype.ui.theme.SubshellVoteDown
import app.subshell.prototype.ui.theme.SubshellVoteDownLight
import app.subshell.prototype.ui.theme.SubshellVoteNeutralLight
import app.subshell.prototype.ui.theme.SubshellVoteUp
import app.subshell.prototype.ui.theme.SubshellVoteUpLight
import app.subshell.prototype.ui.theme.isDarkScheme
import app.subshell.prototype.ui.theme.withMinContrast
import app.subshell.prototype.ui.uiDebugLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.absoluteValue
import app.subshell.prototype.data.account.RedditReportTargetKind
import java.text.DecimalFormatSymbols
import java.util.Locale
import androidx.compose.animation.core.Animatable
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults

private val MainHeaderHeight = 60.dp
private val SubshellLogoSize = 38.dp
private val HomeHeaderTouchTargetHeight = 48.dp
private val CompactHomeTitleLineHeight = 24.sp
private val CompactHomeSortLineHeight = 16.sp

internal class ScrollPagingLoadGate {
    private var hasRequested = false
    private var lastRequestedToken: Any? = null
    private var idleContinuationArmed = false

    fun shouldRequest(
        enabled: Boolean,
        scrolling: Boolean,
        nearEnd: Boolean,
        token: Any?,
    ): Boolean {
        if (!nearEnd) {
            idleContinuationArmed = false
            return false
        }
        if (!enabled) return false
        if (hasRequested && token == lastRequestedToken) return false
        if (!scrolling && !idleContinuationArmed) return false

        hasRequested = true
        lastRequestedToken = token
        idleContinuationArmed = true
        return true
    }
}

private data class ScrollPagingViewport(
    val enabled: Boolean,
    val scrolling: Boolean,
    val nearEnd: Boolean,
    val firstVisibleIndex: Int,
    val lastVisibleIndex: Int,
    val totalItems: Int,
    val token: Any?,
)

internal enum class CaptureSafeRefreshGestureDecision {
    WAIT,
    ARM,
    CANCEL,
}

internal fun captureSafeRefreshGestureDecision(
    eligibleAtGestureStart: Boolean,
    accumulatedX: Float,
    accumulatedY: Float,
    touchSlop: Float,
): CaptureSafeRefreshGestureDecision {
    require(touchSlop >= 0f) {
        "Touch slop cannot be negative"
    }
    if (!eligibleAtGestureStart) {
        return CaptureSafeRefreshGestureDecision.CANCEL
    }

    val horizontalDistance = accumulatedX.absoluteValue
    val verticalDistance = accumulatedY.absoluteValue

    return when {
        accumulatedY <= -touchSlop ->
            CaptureSafeRefreshGestureDecision.CANCEL

        horizontalDistance > touchSlop &&
            horizontalDistance >= verticalDistance ->
            CaptureSafeRefreshGestureDecision.CANCEL

        accumulatedY > touchSlop &&
            verticalDistance > horizontalDistance ->
            CaptureSafeRefreshGestureDecision.ARM

        else -> CaptureSafeRefreshGestureDecision.WAIT
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RefreshableContent(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    indicatorTopPadding: Dp = 0.dp,
    refreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    canPull: () -> Boolean,
    content: @Composable BoxScope.(Modifier) -> Unit,
) {
    val context = LocalContext.current
    val notices = LocalViewportErrorHostState.current
    val density = LocalDensity.current
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val refreshScope = rememberCoroutineScope()

    var previewRefreshing by remember { mutableStateOf(false) }
    var pullDistancePx by remember { mutableStateOf(0f) }

    val effectiveRefreshing =
        if (onRefresh == null) previewRefreshing else refreshing

    val thresholdPx = with(density) {
        CaptureSafeRefreshThreshold.toPx()
    }
    val maximumPullPx = with(density) {
        CaptureSafeRefreshMaximumPull.toPx()
    }

    val requestRefresh = {
        if (onRefresh != null) {
            if (!effectiveRefreshing) {
                onRefresh()
            }
        } else if (!previewRefreshing) {
            previewRefreshing = true
            refreshScope.launch {
                delay(650)
                previewRefreshing = false
                // ISSUE222_NO_TOASTS_V1
                notices.noticeInfo("Offline preview — no new data fetched", "This preview has no live data to refresh")
            }
        }
    }

    val currentCanPull by rememberUpdatedState(canPull)
    val currentRefreshing by rememberUpdatedState(effectiveRefreshing)
    val currentRequestRefresh by rememberUpdatedState(requestRefresh)

    // LONG01D_CAPTURE_SAFE_SHARED_REFRESH_V1
    //
    // This pointer detector is attached to the actual LazyColumn. It does
    // not install a nested-scroll connection, so Android scroll capture
    // continues to operate on one stable scroll node.
    val refreshGestureModifier = if (enabled) {
        Modifier.pointerInput(thresholdPx, maximumPullPx, touchSlop) {
            awaitEachGesture {
                var trackingPull = false
                var gestureCancelled = false
                var eligibleAtGestureStart: Boolean? = null
                var accumulatedX = 0f
                var accumulatedY = 0f
                var gesturePullPx = 0f

                while (true) {
                    val event = awaitPointerEvent(
                        pass = PointerEventPass.Initial,
                    )
                    val activeChange =
                        event.changes.firstOrNull { change ->
                            change.pressed
                        }

                    if (activeChange == null) {
                        val shouldRefresh =
                            trackingPull &&
                                gesturePullPx >= thresholdPx &&
                                !currentRefreshing

                        pullDistancePx = 0f

                        if (shouldRefresh) {
                            currentRequestRefresh()
                        }
                        break
                    }

                    if (eligibleAtGestureStart == null) {
                        eligibleAtGestureStart =
                            currentCanPull() && !currentRefreshing
                    }

                    val positionChange = activeChange.positionChange()
                    val deltaY = positionChange.y

                    if (!trackingPull && !gestureCancelled) {
                        accumulatedX += positionChange.x
                        accumulatedY += deltaY

                        when (
                            captureSafeRefreshGestureDecision(
                                eligibleAtGestureStart =
                                    eligibleAtGestureStart == true &&
                                        currentCanPull() &&
                                        !currentRefreshing,
                                accumulatedX = accumulatedX,
                                accumulatedY = accumulatedY,
                                touchSlop = touchSlop,
                            )
                        ) {
                            CaptureSafeRefreshGestureDecision.WAIT -> Unit

                            CaptureSafeRefreshGestureDecision.CANCEL -> {
                                gestureCancelled = true
                                pullDistancePx = 0f
                            }

                            CaptureSafeRefreshGestureDecision.ARM -> {
                                trackingPull = true
                                gesturePullPx = (
                                    (accumulatedY - touchSlop) *
                                        CaptureSafeRefreshResistance
                                    ).coerceIn(0f, maximumPullPx)
                                pullDistancePx = gesturePullPx
                                activeChange.consume()
                            }
                        }
                    } else if (trackingPull && deltaY > 0f) {
                        gesturePullPx = (
                            gesturePullPx +
                                deltaY * CaptureSafeRefreshResistance
                            ).coerceAtMost(maximumPullPx)

                        pullDistancePx = gesturePullPx
                        activeChange.consume()
                    } else if (trackingPull && deltaY < 0f) {
                        val nextDistance = (
                            gesturePullPx + deltaY
                            ).coerceAtLeast(0f)

                        if (nextDistance > 0f) {
                            gesturePullPx = nextDistance
                            pullDistancePx = gesturePullPx
                            activeChange.consume()
                        } else {
                            gesturePullPx = 0f
                            pullDistancePx = 0f
                            trackingPull = false
                            gestureCancelled = true
                        }
                    }
                }
            }
        }
    } else {
        Modifier
    }

    // M3225_PULL_TO_REFRESH_DRAWS_THE_M3_INDICATOR_V1 (#225, components-07): the
    // capture-safe gesture above stays (LONG01D); it drives M3's own indicator
    // (arrow that fills with the pull, spinner while refreshing), which eases
    // back on release instead of snapping away.
    val shownFraction = remember { Animatable(0f) }
    LaunchedEffect(pullDistancePx, effectiveRefreshing) {
        when {
            pullDistancePx > 0f -> shownFraction.snapTo(pullDistancePx / thresholdPx)
            effectiveRefreshing -> shownFraction.animateTo(1f)
            else -> shownFraction.animateTo(0f)
        }
    }
    val indicatorState = remember(shownFraction) {
        object : PullToRefreshState {
            override val distanceFraction: Float get() = shownFraction.value
            override val isAnimating: Boolean get() = shownFraction.isRunning
            override suspend fun animateToThreshold() = shownFraction.animateTo(1f).let { }
            override suspend fun animateToHidden() = shownFraction.animateTo(0f).let { }
            override suspend fun snapTo(targetValue: Float) = shownFraction.snapTo(targetValue)
        }
    }

    Box(modifier = modifier) {
        content(refreshGestureModifier)

        PullToRefreshDefaults.Indicator(
            state = indicatorState,
            isRefreshing = effectiveRefreshing,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = indicatorTopPadding),
            maxDistance = CaptureSafeRefreshThreshold,
        )
    }
}

@Composable
fun RefreshableLazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    enabled: Boolean = true,
    userScrollEnabled: Boolean = true,
    indicatorTopPadding: Dp = 0.dp,
    isRefreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    loadNextEnabled: Boolean = false,
    loadNextToken: Any? = null,
    onLoadNext: () -> Unit = {},
    // ISSUE205_PASSIVE_SCROLL_POSITION_INDICATOR_V1: off unless the setting is on.
    scrollPositionIndicator: Boolean = false,
    // A list that loads more its own way (a comment thread's more nodes) says so here.
    scrollPositionMoreToLoad: Boolean = loadNextEnabled,
    content: LazyListScope.() -> Unit,
) {
    val currentOnLoadNext by rememberUpdatedState(onLoadNext)
    val currentLoadNextEnabled by rememberUpdatedState(loadNextEnabled)
    val currentLoadNextToken by rememberUpdatedState(loadNextToken)
    val scrollPagingLoadGate = remember(state) { ScrollPagingLoadGate() }
    LaunchedEffect(state) {
        snapshotFlow {
            val layout = state.layoutInfo
            val lastVisibleIndex = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            val totalItems = layout.totalItemsCount
            ScrollPagingViewport(
                enabled = currentLoadNextEnabled,
                scrolling = state.isScrollInProgress,
                nearEnd = totalItems > 0 &&
                    lastVisibleIndex >=
                        (totalItems - LIVE_FEED_VIEWPORT_PREFETCH_ITEMS).coerceAtLeast(0),
                firstVisibleIndex = layout.visibleItemsInfo.firstOrNull()?.index ?: -1,
                lastVisibleIndex = lastVisibleIndex,
                totalItems = totalItems,
                token = currentLoadNextToken,
            )
        }
            .distinctUntilChanged()
            .collect { viewport ->
                val shouldRequest = scrollPagingLoadGate.shouldRequest(
                    enabled = viewport.enabled,
                    scrolling = viewport.scrolling,
                    nearEnd = viewport.nearEnd,
                    token = viewport.token,
                )
                if (shouldRequest) {
                    uiDebugLog(
                        LIVE_FEED_PAGING_LOG_TAG,
                        "governor viewport first=${viewport.firstVisibleIndex} " +
                            "last=${viewport.lastVisibleIndex} total=${viewport.totalItems} " +
                            "token=${viewport.token}",
                    )
                    currentOnLoadNext()
                }
            }
    }

    RefreshableContent(
        modifier = modifier,
        enabled = enabled,
        indicatorTopPadding = indicatorTopPadding,
        refreshing = isRefreshing,
        onRefresh = onRefresh,
        canPull = { !state.canScrollBackward },
    ) { refreshModifier ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = refreshModifier.fillMaxSize(),
                state = state,
                contentPadding = contentPadding,
                verticalArrangement = verticalArrangement,
                userScrollEnabled = userScrollEnabled,
                content = content,
            )
            if (scrollPositionIndicator) {
                ScrollPositionIndicator(
                    state = state,
                    moreCanLoad = scrollPositionMoreToLoad,
                    modifier = Modifier
                        .matchParentSize()
                        .padding(
                            top = contentPadding.calculateTopPadding() + 4.dp,
                            bottom = contentPadding.calculateBottomPadding() + 4.dp,
                            end = 2.dp,
                        ),
                )
            }
        }
    }
}


private val CaptureSafeRefreshThreshold = 72.dp
private val CaptureSafeRefreshMaximumPull = 124.dp
private const val CaptureSafeRefreshResistance = 0.55f

private const val LIVE_FEED_VIEWPORT_PREFETCH_ITEMS = 6
private const val LIVE_FEED_PAGING_LOG_TAG = "subshell-feed"

@Composable
fun SubshellMark(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_subshell_in_app),
        contentDescription = null,
        modifier = modifier,
    )
}

@Composable
fun SubshellWordmark(modifier: Modifier = Modifier, subtitle: String? = null) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        SubshellMark(Modifier.size(SubshellLogoSize))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = "subshell",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun SnooAvatar(modifier: Modifier = Modifier) {
    val snoo = Color.White
    val face = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
    ) {
        drawCircle(color = snoo, radius = size.minDimension * 0.34f, center = center)
        drawCircle(color = face, radius = size.minDimension * 0.035f, center = Offset(size.width * 0.41f, size.height * 0.49f))
        drawCircle(color = face, radius = size.minDimension * 0.035f, center = Offset(size.width * 0.59f, size.height * 0.49f))
        drawArc(
            color = face,
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(size.width * 0.41f, size.height * 0.51f),
            size = Size(size.width * 0.18f, size.height * 0.15f),
            style = Stroke(size.minDimension * 0.035f, cap = StrokeCap.Round),
        )
        drawLine(
            color = snoo,
            start = Offset(size.width * 0.56f, size.height * 0.19f),
            end = Offset(size.width * 0.67f, size.height * 0.09f),
            strokeWidth = size.minDimension * 0.045f,
            cap = StrokeCap.Round,
        )
        drawCircle(color = snoo, radius = size.minDimension * 0.055f, center = Offset(size.width * 0.70f, size.height * 0.075f))
    }
}

@Composable
fun RedditDefaultProfileAvatar(
    seed: String,
    modifier: Modifier = Modifier,
) {
    val accent = accentFor(seed)
    val background = accent.copy(alpha = 0.34f)
    val silhouette = accent.copy(alpha = 0.82f)
    Canvas(
        modifier = modifier
            .clip(CircleShape)
            .background(background),
    ) {
        val unit = size.minDimension
        drawCircle(
            color = silhouette,
            radius = unit * 0.23f,
            center = Offset(size.width * 0.50f, size.height * 0.47f),
        )
        drawOval(
            color = silhouette,
            topLeft = Offset(size.width * 0.24f, size.height * 0.66f),
            size = Size(size.width * 0.52f, size.height * 0.48f),
        )
        drawLine(
            color = silhouette,
            start = Offset(size.width * 0.55f, size.height * 0.24f),
            end = Offset(size.width * 0.66f, size.height * 0.13f),
            strokeWidth = unit * 0.045f,
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = silhouette,
            radius = unit * 0.052f,
            center = Offset(size.width * 0.70f, size.height * 0.095f),
        )
    }
}

@Composable
fun MainScreenHeader(
    title: String,
    state: SubshellState,
    modifier: Modifier = Modifier,
    feedSort: FeedSort? = null,
    feedSortOptions: List<FeedSort> = FeedSort.entries,
    onFeedSortSelected: (FeedSort) -> Unit = {},
    listingTimePeriod: RedditListingTimePeriod = RedditListingTimePeriod.ALL,
    onListingTimePeriodSelected: (RedditListingTimePeriod) -> Unit = {},
    hideReadPosts: Boolean? = null,
    hasReadPosts: Boolean = false,
    onHideReadPostsChanged: (Boolean) -> Unit = {},
    onResetReadPosts: () -> Unit = {},
    onExploreClick: (() -> Unit)? = null,
    onSearchClick: (() -> Unit)? = null,
    searchContentDescription: String = "Search",
    // ISSUE234_LAYOUT_CHOICE_BESIDE_THE_FEED_V1
    postLayoutButton: Boolean = false,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }
    var timePeriodExpanded by remember { mutableStateOf(false) }
    val drawerAtEnd = state.resolvedDrawerSide == app.subshell.prototype.ui.InteractionSide.RIGHT

    Row(
        modifier = modifier
            .fillMaxWidth()
            // A11Y01A1_LARGE_TEXT_HEADER_REFLOW_V1
            .heightIn(min = MainHeaderHeight)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (drawerAtEnd) {
            HeaderAccountMenu(
                expanded = menuExpanded,
                onExpandedChange = { menuExpanded = it },
                state = state,
            )
            HeaderSearchButton(onSearchClick, searchContentDescription)
        } else {
            HeaderDrawerButton(state)
            Spacer(Modifier.width(6.dp))
        }

        if (feedSort == null) {
            Text(
                title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                Surface(
                    onClick = { sortExpanded = true },
                    modifier = Modifier.heightIn(min = HomeHeaderTouchTargetHeight),
                    shape = RectangleShape,
                    color = Color.Transparent,
                ) {
                    Column(
                        modifier = Modifier.padding(end = 8.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                lineHeight = CompactHomeTitleLineHeight,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (
                                    state.activeAccountIsAuthenticated &&
                                    feedSort.supportsTimePeriod
                                ) {
                                    "${feedSort.label} · ${listingTimePeriod.label}"
                                } else {
                                    feedSort.label
                                },
                                style = MaterialTheme.typography.labelLarge.copy(
                                    lineHeight = CompactHomeSortLineHeight,
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                            Spacer(Modifier.width(2.dp))
                            Icon(
                                Symbols.KeyboardArrowDown,
                                contentDescription = "Choose feed sorting",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                DropdownMenu(
                    expanded = sortExpanded,
                    onDismissRequest = { sortExpanded = false },
                ) {
                    feedSortOptions.forEach { sort ->
                        DropdownMenuItem(
                            text = { Text(sort.label) },
                            trailingIcon = if (sort == feedSort) {
                                { Icon(Symbols.Check, contentDescription = null) }
                            } else {
                                null
                            },
                            onClick = {
                                onFeedSortSelected(sort)
                                sortExpanded = false
                            },
                        )
                    }
                    if (
                        state.activeAccountIsAuthenticated &&
                        feedSort.supportsTimePeriod
                    ) {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Time period · ${listingTimePeriod.label}") },
                            trailingIcon = {
                                Icon(
                                    Symbols.KeyboardArrowDown,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                sortExpanded = false
                                timePeriodExpanded = true
                            },
                        )
                    }
                    if (hideReadPosts != null) {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = {
                                Text(if (hideReadPosts) "Show read posts" else "Hide read posts")
                            },
                            trailingIcon = if (hideReadPosts) {
                                { Icon(Symbols.Check, contentDescription = null) }
                            } else {
                                null
                            },
                            enabled = hasReadPosts,
                            onClick = {
                                onHideReadPostsChanged(!hideReadPosts)
                                sortExpanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Reset read markers") },
                            leadingIcon = {
                                Icon(Symbols.Refresh, contentDescription = null)
                            },
                            enabled = hasReadPosts,
                            onClick = {
                                onResetReadPosts()
                                sortExpanded = false
                            },
                        )
                    }
                }
                DropdownMenu(
                    expanded = timePeriodExpanded,
                    onDismissRequest = { timePeriodExpanded = false },
                ) {
                    RedditListingTimePeriod.entries.forEach { timePeriod ->
                        DropdownMenuItem(
                            text = { Text(timePeriod.label) },
                            trailingIcon = if (timePeriod == listingTimePeriod) {
                                { Icon(Symbols.Check, contentDescription = null) }
                            } else {
                                null
                            },
                            onClick = {
                                onListingTimePeriodSelected(timePeriod)
                                timePeriodExpanded = false
                            },
                        )
                    }
                }
            }
        }
        if (postLayoutButton) {
            PostLayoutMenuButton(state)
        }
        if (onExploreClick != null) {
            IconButton(onClick = onExploreClick) {
                Icon(
                    Symbols.Explore,
                    contentDescription = "Explore communities",
                )
            }
        }

        if (drawerAtEnd) {
            Spacer(Modifier.width(6.dp))
            HeaderDrawerButton(state)
        } else {
            HeaderSearchButton(onSearchClick, searchContentDescription)
            HeaderAccountMenu(
                expanded = menuExpanded,
                onExpandedChange = { menuExpanded = it },
                state = state,
            )
        }
    }
}

@Composable
private fun HeaderDrawerButton(state: SubshellState) {
    IconButton(onClick = state::requestDrawer) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            SubshellMark(Modifier.size(SubshellLogoSize))
            Icon(
                Symbols.KeyboardArrowDown,
                contentDescription = "Open navigation",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HeaderSearchButton(
    onSearchClick: (() -> Unit)?,
    searchContentDescription: String = "Search",
) {
    if (onSearchClick != null) {
        IconButton(onClick = onSearchClick) {
            Icon(Symbols.Search, contentDescription = searchContentDescription)
        }
    }
}

@Composable
private fun HeaderAccountMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    state: SubshellState,
) {
    Box {
        IconButton(onClick = { onExpandedChange(true) }) {
            SnooAvatar(
                Modifier
                    .size(34.dp)
                    .semantics { contentDescription = "Account menu" },
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            DropdownMenuItem(
                text = { Text("Switch account") },
                leadingIcon = { Icon(Symbols.SwapHoriz, contentDescription = null) },
                onClick = {
                    onExpandedChange(false)
                    state.requestAccountSwitcher()
                },
            )
            DropdownMenuItem(
                text = { Text("View profile") },
                leadingIcon = { Icon(Symbols.Person, contentDescription = null) },
                onClick = {
                    onExpandedChange(false)
                    state.navigate(MainDestination.YOU)
                },
            )
            DropdownMenuItem(
                text = { Text("Settings") },
                leadingIcon = { Icon(Symbols.Settings, contentDescription = null) },
                onClick = {
                    onExpandedChange(false)
                    state.openSecondary(SecondaryDestination.SETTINGS)
                },
            )
            DropdownMenuItem(
                text = { Text("Help & about") },
                leadingIcon = {
                    Icon(Symbols.Help, contentDescription = null)
                },
                onClick = {
                    onExpandedChange(false)
                    state.openSecondary(SecondaryDestination.HELP)
                },
            )
        }
    }
}

@Composable
fun CommunityAvatar(
    name: String,
    accent: Color,
    modifier: Modifier = Modifier,
    iconImageUri: String? = null,
) {
    // ISSUE218_LETTER_AVATARS_KEEP_THEIR_CONTRAST_V1: the letter is the accent
    // moved until it reads 4.5:1 on its own tinted background (pastel accents
    // were 1.3–2.2:1 in light theme); the accent itself is unchanged.
    val avatarBackground = accent.copy(alpha = 0.18f).compositeOver(MaterialTheme.colorScheme.surface)
    RemoteIdentityImage(
        uri = iconImageUri,
        contentDescription = "Icon for r/$name",
        modifier = modifier
            .clip(CircleShape)
            .background(avatarBackground),
    ) {
        Text(
            text = name.firstOrNull()?.uppercase() ?: "/",
            color = accent.withMinContrast(avatarBackground, 4.5f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ISSUE22_COMMENT_AUTHOR_AVATARS_SETTING_V1
/**
 * A comment author's avatar: the resolved Reddit avatar when [avatarUri] is
 * known, otherwise the same letter avatar the row has always drawn.
 */
@Composable
fun CommentAuthorAvatar(
    author: String,
    accent: Color,
    avatarUri: String?,
    modifier: Modifier = Modifier,
) {
    // ISSUE218_LETTER_AVATARS_KEEP_THEIR_CONTRAST_V1 (as CommunityAvatar)
    val avatarBackground = accent.copy(alpha = 0.18f).compositeOver(MaterialTheme.colorScheme.surface)
    RemoteIdentityImage(
        uri = avatarUri,
        contentDescription = "Avatar for u/$author",
        modifier = modifier
            .clip(CircleShape)
            .background(avatarBackground),
    ) {
        Text(
            text = author.firstOrNull()?.uppercase() ?: "/",
            color = accent.withMinContrast(avatarBackground, 4.5f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun RemoteIdentityImage(
    uri: String?,
    // Null for a decorative image beside its visible name (ISSUE219, LOWUI7).
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallback: @Composable BoxScope.() -> Unit,
) {
    val bitmap by produceState<Bitmap?>(
        initialValue = null,
        key1 = uri,
    ) {
        value = uri?.let { imageUri ->
            when (val result = RemoteImageLoader.load(imageUri)) {
                is RemoteMediaLoadResult.Ready -> result.value
                is RemoteMediaLoadResult.Failed -> null
            }
        }
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val resolved = bitmap
        if (resolved == null) {
            fallback()
        } else {
            Image(
                bitmap = resolved.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

internal enum class PostBodyEditEntryMode {
    CANONICAL,
    PROBE,
}

internal fun postBodyEditEntryMode(
    kind: PostKind,
    body: String,
    canonicalEditAllowed: Boolean,
    ownerDeleteAllowed: Boolean,
): PostBodyEditEntryMode? = when {
    kind == PostKind.TEXT && canonicalEditAllowed ->
        PostBodyEditEntryMode.CANONICAL
    kind != PostKind.TEXT && body.isNotBlank() && ownerDeleteAllowed ->
        PostBodyEditEntryMode.PROBE
    else -> null
}

@Composable
fun PostCard(
    post: Post,
    state: SubshellState,
    onOpen: () -> Unit,
    onComments: (() -> Unit)? = null,
    beforeNavigation: () -> Unit = {},
    modifier: Modifier = Modifier,
    bodyMaxLines: Int = 4,
    onEditPost: (() -> Unit)? = null,
    onManagePost: (() -> Unit)? = null,
    onDeletePost: (() -> Unit)? = null,
    onRefreshComments: (() -> Unit)? = null,
    knownHidden: Boolean = false,
    suppressWhenHiddenStateDiffers: Boolean = true,
    swipeActionsEnabled: Boolean = true,
    inPostDetail: Boolean = false,
    layout: PostLayout = PostLayout.LARGE,
) {
    if (state.isPostDeleted(post.id)) return
    val vote = state.effectivePostVote(post)
    val saved = state.effectivePostSaved(post)
    val postHidden = state.effectivePostHidden(post, knownHidden)
    // SAFETY01A32_IMMEDIATE_HIDE_UNHIDE_LIST_PROJECTION_V1
    if (suppressWhenHiddenStateDiffers && postHidden != knownHidden) return
    val read = post.id in state.readPostIds
    val ownPostActions = ownContentActions(
        activeUsername = state.activeAccountUsername,
        author = post.author,
        contentType = if (post.kind == PostKind.TEXT) {
            OwnContentType.SELF_POST
        } else {
            OwnContentType.OTHER_POST
        },
    )
    val canonicalSelfPostEdit =
        post.kind == PostKind.TEXT && ownPostActions.canEdit
    val bodyEditEntryMode = postBodyEditEntryMode(
        kind = post.kind,
        body = post.body,
        canonicalEditAllowed = canonicalSelfPostEdit,
        ownerDeleteAllowed = ownPostActions.canDelete,
    )
    val communityIdentity = state.communityFor(post.community)
    val communityAccent = communityIdentity?.let { community -> Color(community.accent) }
        ?: accentFor(post.community)
    val context = LocalContext.current
    val notices = LocalViewportErrorHostState.current
    var overflowExpanded by remember(post.id) { mutableStateOf(false) }
    var reportDialogVisible by remember(post.id) { mutableStateOf(false) }
    var muteSheetVisible by remember(post.id) { mutableStateOf(false) }
    // ISSUE34_USER_TAGS_V1
    val authorTag = state.userTagFor(post.author)
    var userTagEditorVisible by remember(post.id) { mutableStateOf(false) }
    if (userTagEditorVisible) {
        UserTagEditorDialog(
            username = post.author,
            existing = authorTag,
            onDismiss = { userTagEditorVisible = false },
            onSave = { tag ->
                state.setUserTag(post.author, tag)
                userTagEditorVisible = false
            },
        )
    }
    val currentCommunityMute = state.communityMuteFor(post.community)
    val communityMuted = currentCommunityMute != null
    LaunchedEffect(post.id, knownHidden, state.activeAccountIsAuthenticated) {
        if (knownHidden) state.noteAuthoritativeHiddenPost(post.id)
    }
    // ISSUE234_FEED_POST_LAYOUTS_V1: every layout shares the same post actions menu.
    val postOverflowMenu: @Composable () -> Unit = {
        Box {
            IconButton(onClick = { overflowExpanded = true }) {
                Icon(Symbols.MoreVert, contentDescription = "Post actions")
            }
            DropdownMenu(
                expanded = overflowExpanded,
                onDismissRequest = { overflowExpanded = false },
            ) {
                // CROSSPOST01A1E_SHARED_POST_ACTION_V1
                if (state.canOpenCrosspostComposer(post)) {
                    DropdownMenuItem(
                        text = { Text("Crosspost") },
                        onClick = {
                            overflowExpanded = false
                            beforeNavigation()
                            state.openCrosspostComposer(post)
                        },
                    )
                }
                // FOLLOWPOST01A1B_SHARED_POST_OVERFLOW_V1
                if (state.activeAccountIsAuthenticated) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                postFollowActionLabel(
                                    state.effectivePostFollowed(
                                        post,
                                    ),
                                ),
                            )
                        },
                        enabled =
                            state.postFollowControlEnabled(
                                post,
                            ),
                        onClick = {
                            overflowExpanded = false
                            state.togglePostFollow(post)
                        },
                    )
                }

                if (
                    state.activeAccountIsAuthenticated &&
                    bodyEditEntryMode != null
                ) {
                    DropdownMenuItem(
                        text = {
                        if (bodyEditEntryMode == PostBodyEditEntryMode.PROBE) {
                            Text("Edit body (test)")
                        } else {
                            Text("Edit post")
                        }
                    },
                        onClick = {
                            overflowExpanded = false
                            if (onEditPost != null) {
                                onEditPost()
                            } else {
                                state.requestPostEdit(post.id)
                                beforeNavigation()
                                onOpen()
                            }
                        },
                    )
                }
                if (
                    state.activeAccountIsAuthenticated &&
                    ownPostActions.canDelete
                ) {
                    DropdownMenuItem(
                        text = { Text("Manage post") },
                        onClick = {
                            overflowExpanded = false
                            if (onManagePost != null) {
                                onManagePost()
                            } else {
                                state.requestPostManage(post.id)
                                beforeNavigation()
                                onOpen()
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Delete post",
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            overflowExpanded = false
                            if (onDeletePost != null) {
                                onDeletePost()
                            } else {
                                state.requestPostDelete(post.id)
                                beforeNavigation()
                                onOpen()
                            }
                        },
                    )
                }
                if (onRefreshComments != null) {
                    DropdownMenuItem(
                        text = { Text("Refresh comments") },
                        onClick = {
                            overflowExpanded = false
                            onRefreshComments()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Go to ${state.subredditLabel(post.community)}") },
                    onClick = {
                        overflowExpanded = false
                        beforeNavigation()
                        state.openSubreddit(post.community)
                    },
                )
                DropdownMenuItem(
                    text = { Text("About u/${post.author}") },
                    onClick = {
                        overflowExpanded = false
                        beforeNavigation()
                        state.openUser(post.author, ProfileActivityEvidence.POST)
                    },
                )
                if (state.canTagUser(post.author)) {
                    DropdownMenuItem(
                        text = { Text("Private note on u/${post.author}") },
                        onClick = {
                            overflowExpanded = false
                            userTagEditorVisible = true
                        },
                    )
                }
                // MODERATION02A2_POST_REPORT_REOPEN_OUTSIDE_QUEUE_V1
                ModeratorReportMenuItem(
                    target = app.subshell.prototype.data.account.moderatorReportTargetOrNull(
                        if (post.id.startsWith("t3_")) post.id else "t3_${post.id}",
                        post.community,
                    ),
                    onDismissMenu = { overflowExpanded = false },
                )
                // MODERATION02A3_POST_CONTENT_CONTROLS_REOPEN_V1
                ModeratorContentControlsMenuItem(
                    target = app.subshell.prototype.data.account.moderatorReportTargetOrNull(
                        if (post.id.startsWith("t3_")) post.id else "t3_${post.id}",
                        post.community,
                    ),
                    onDismissMenu = { overflowExpanded = false },
                )
                // SAFETY01A3_REAL_POST_HIDE_UNHIDE_REPORT_UI_V1
                DropdownMenuItem(
                    text = { Text(if (postHidden) "Unhide post" else "Hide post") },
                    enabled = state.postVisibilityControlEnabled(post),
                    onClick = {
                        overflowExpanded = false
                        state.togglePostHidden(post, knownHidden)
                    },
                )
                if (state.activeAccountIsAuthenticated) {
                    DropdownMenuItem(
                        text = { Text("Report") },
                        enabled = state.contentReportControlEnabled(),
                        onClick = {
                            overflowExpanded = false
                            reportDialogVisible = true
                        },
                    )
                }
                DropdownMenuItem(
                    text = {
                        Text(
                            if (communityMuted) {
                                "Unmute ${state.subredditLabel(post.community)}"
                            } else {
                                "Mute ${state.subredditLabel(post.community)}…"
                            },
                        )
                    },
                    onClick = {
                        overflowExpanded = false
                        if (communityMuted) {
                            state.unmuteCommunity(post.community)
                            notices.noticeInfo("Unmuted ${state.subredditLabel(post.community)}", "Its posts show in your feeds again")
                        } else {
                            muteSheetVisible = true
                        }
                    },
                )
                DropdownMenuItem(
                    text = { Text("Copy link") },
                    onClick = {
                        overflowExpanded = false
                        copyPostLink(context, post, notices)
                    },
                )
            }
        }
    }
    // ISSUE16_POST_SWIPE_ACTION_SURFACE_V1
    SwipeActionSurface(
        modifier = modifier.fillMaxWidth(),
        // ISSUE234_FEED_POST_LAYOUTS_V1: a half-width grid tile has no room to swipe.
        enabled = state.swipeActionsEnabled && swipeActionsEnabled && layout != PostLayout.GRID,
        reduceMotion = state.motionReduced,
        leftAction = postSwipeActionVisual(
            action = state.postSwipeLeftAction,
            vote = vote,
            saved = saved,
            hidden = postHidden,
            enabled = postSwipeActionEnabled(state, post, state.postSwipeLeftAction),
            voteColorPalette = state.voteColorPalette,
            voteControlMode = state.voteControlMode,
        ),
        rightAction = postSwipeActionVisual(
            action = state.postSwipeRightAction,
            vote = vote,
            saved = saved,
            hidden = postHidden,
            enabled = postSwipeActionEnabled(state, post, state.postSwipeRightAction),
            voteColorPalette = state.voteColorPalette,
            voteControlMode = state.voteControlMode,
        ),
        onLeft = {
            dispatchPostSwipeAction(
                state = state,
                post = post,
                authoritativeHiddenHint = knownHidden,
                action = state.postSwipeLeftAction,
            )
        },
        onRight = {
            dispatchPostSwipeAction(
                state = state,
                post = post,
                authoritativeHiddenHint = knownHidden,
                action = state.postSwipeRightAction,
            )
        },
    ) { swipeContentModifier ->
    if (layout != PostLayout.LARGE) {
        FeedPostLayoutContent(
            post = post,
            state = state,
            layout = layout,
            vote = vote,
            saved = saved,
            read = read,
            onOpen = {
                beforeNavigation()
                onOpen()
            },
            onComments = {
                beforeNavigation()
                (onComments ?: onOpen).invoke()
            },
            onOpenCommunity = {
                beforeNavigation()
                state.openSubreddit(post.community)
            },
            overflowMenu = postOverflowMenu,
            modifier = swipeContentModifier,
        )
    } else Card(
        modifier = swipeContentModifier
            .fillMaxWidth()
            .clickable {
                beforeNavigation()
                onOpen()
            },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column {
            Column(Modifier.padding(start = 14.dp, top = 12.dp, end = 10.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RemoteIdentityImage(
                            uri = communityIdentity?.iconImageUri,
                            contentDescription = "Icon for ${state.subredditLabel(post.community)}",
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                // LOWUI7_LABELS_SAY_WHAT_A_CONTROL_DOES_V1 (OCB-126): sizes stay
                                // (owner); each target says what it opens.
                                .clickable(onClickLabel = "Open community", role = Role.Button) {
                                    beforeNavigation()
                                    state.openSubreddit(post.community)
                                },
                        ) {
                            CommunityAvatar(
                                name = post.community,
                                accent = communityAccent,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(
                                text = state.subredditLabel(post.community),
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable(onClickLabel = "Open community", role = Role.Button) {
                                        beforeNavigation()
                                        state.openSubreddit(post.community)
                                    }
                                    .padding(vertical = 1.dp),
                            )
                            Row(
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable(onClickLabel = "Open profile", role = Role.Button) {
                                        beforeNavigation()
                                        state.openUser(post.author, ProfileActivityEvidence.POST)
                                    }
                                    .padding(vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // The author and age keep their width; the flair takes
                                // what is left and ellipsizes (it used to squeeze the
                                // author down to "@…").
                                Text(
                                    text = "@${post.author}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (authorTag != null) {
                                    Spacer(Modifier.width(5.dp))
                                    UserTagChip(
                                        tag = authorTag,
                                        modifier = Modifier
                                            .weight(1f, fill = false)
                                            .hiddenWhenNarrowerThan(AuthorFlairMinVisibleWidth),
                                    )
                                }
                                if (
                                    post.authorFlairText != null ||
                                    post.authorFlairRichText.isNotEmpty()
                                ) {
                                    Spacer(Modifier.width(5.dp))
                                    AuthorFlairBadge(
                                        text = post.authorFlairText.orEmpty(),
                                        richText = post.authorFlairRichText,
                                        textColor =
                                            post.authorFlairTextColor,
                                        backgroundColor =
                                            post.authorFlairBackgroundColor,
                                        modifier = Modifier
                                            .weight(1f, fill = false)
                                            .hiddenWhenNarrowerThan(AuthorFlairMinVisibleWidth),
                                    )
                                }
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = "· ${post.age}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                                if (shouldShowUnreadMarker(state.showUnreadMarker, read)) {
                                    Spacer(Modifier.width(6.dp))
                                    Box(
                                        Modifier
                                            .size(6.dp)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                                            .semantics { contentDescription = "Unread post" },
                                    )
                                }
                            }
                        }
                    }
                    postOverflowMenu()
                }
                if (post.pinned || post.moderatorPost || post.flair != null || post.spoiler || post.nsfw) {
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                    ) {
                        if (post.pinned) MetadataChip("PINNED")
                        if (post.moderatorPost) MetadataChip("MOD")
                        post.flair?.let { PostFlairChip(text = it, richText = post.flairRichText) }
                        if (post.spoiler) {
                            MetadataChip(
                                label = "SPOILER",
                                tone = MetadataChipTone.SPOILER,
                            )
                        }
                        if (post.nsfw) {
                            MetadataChip(
                                label = "NSFW",
                                tone = MetadataChipTone.SENSITIVE,
                            )
                        }
                    }
                }
                Text(
                    text = post.title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp, bottom = if (post.body.isNotBlank()) 5.dp else 2.dp),
                )
                if (post.body.isNotBlank() && bodyMaxLines == Int.MAX_VALUE) {
                    // ISSUE8_INLINE_REDDIT_MEDIA_V1: the open post shows a self
                    // post's inline images and Giphy like a comment's.
                    CommentContent(
                        source = post.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        onMediaClick = { media ->
                            state.openCommentMedia(commentId = post.id, media = media)
                        },
                    )
                } else if (post.body.isNotBlank()) {
                    MarkdownText(
                        text = post.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = resolvedPostBodyMaxLines(post.kind, bodyMaxLines),
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            PostMedia(post = post, state = state, inPostDetail = inPostDetail)

            PostActionRow(
                score = state.effectivePostScore(post),
                comments = post.comments,
                vote = vote,
                saved = saved,
                voteColorPalette = state.voteColorPalette,
                voteControlMode = state.voteControlMode,
                writeActionsEnabled = state.localMutationsAllowed,
                voteActionsEnabled = state.postVoteControlEnabled(post),
                onUpvote = { state.vote(post, 1) },
                onDownvote = { state.vote(post, -1) },
                onComments = {
                    beforeNavigation()
                    (onComments ?: onOpen).invoke()
                },
                onSave = { state.toggleSaved(post) },
                onShare = { sharePostLink(context, post, notices) },
                saveActionsEnabled = state.postSaveControlEnabled(post),
            )
        }
    }
    }
    if (reportDialogVisible) {
        RedditContentReportDialog(
            targetLabel = "post",
            communityName = post.community,
            targetKind = RedditReportTargetKind.POST,
            state = state,
            onDismiss = { reportDialogVisible = false },
            onReport = { reason ->
                reportDialogVisible = false
                state.report(post, reason)
            },
        )
    }
    if (muteSheetVisible) {
        CommunityMuteSheet(
            communityLabel = state.subredditLabel(post.community),
            nowEpochMillis = System.currentTimeMillis(),
            onDismiss = { muteSheetVisible = false },
            onMute = { expiresAtEpochMillis ->
                if (state.muteCommunity(post.community, expiresAtEpochMillis)) {
                    notices.noticeInfo("Muted ${state.subredditLabel(post.community)}", "Its posts are hidden from your feeds")
                }
            },
            currentMute = currentCommunityMute,
            onUnmute = if (currentCommunityMute != null) {
                {
                    if (state.unmuteCommunity(post.community)) {
                        notices.noticeInfo("Unmuted ${state.subredditLabel(post.community)}", "Its posts show in your feeds again")
                    }
                }
            } else {
                null
            },
        )
    }

}

internal fun shouldShowUnreadMarker(showUnreadMarker: Boolean, read: Boolean): Boolean =
    showUnreadMarker && !read

internal fun resolvedPostBodyMaxLines(kind: PostKind, requestedMaxLines: Int): Int =
    when {
        requestedMaxLines == Int.MAX_VALUE -> Int.MAX_VALUE
        kind == PostKind.TEXT -> requestedMaxLines
        else -> minOf(requestedMaxLines, 2)
    }

@Composable
private fun MetadataChip(
    label: String,
    tone: MetadataChipTone = MetadataChipTone.DEFAULT,
) {
    val containerColor = when (tone) {
        MetadataChipTone.DEFAULT -> MaterialTheme.colorScheme.secondaryContainer
        MetadataChipTone.SENSITIVE -> MaterialTheme.colorScheme.errorContainer
            MetadataChipTone.SPOILER -> MaterialTheme.colorScheme.surfaceContainerHighest
}
    val contentColor = when (tone) {
        MetadataChipTone.DEFAULT -> MaterialTheme.colorScheme.onSecondaryContainer
        MetadataChipTone.SENSITIVE -> MaterialTheme.colorScheme.onErrorContainer
            MetadataChipTone.SPOILER -> MaterialTheme.colorScheme.onSurfaceVariant
}
    Surface(
        shape = CircleShape,
        color = containerColor,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ISSUE44_POST_FLAIR_RICHTEXT_V1: a flair with Reddit rich text shows its
// emoji; a plain flair is the same MetadataChip as before.
@Composable
internal fun PostFlairChip(
    text: String,
    richText: List<FlairRichTextSegment>,
) {
    if (richText.isEmpty()) {
        MetadataChip(redditFlairFallbackText(text).ifBlank { text })
        return
    }
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        FlairRichTextLabel(
            text = text,
            richText = richText,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            textStyle = MaterialTheme.typography.labelSmall.copy(
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Bold,
            ),
            emojiSize = 14.dp,
        )
    }
}

private enum class MetadataChipTone {
    DEFAULT,
    SENSITIVE,
    SPOILER,
}

@Composable
fun PostActionRow(
    score: Int,
    comments: Int,
    vote: Int,
    saved: Boolean,
    onUpvote: () -> Unit,
    onDownvote: () -> Unit,
    onComments: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    writeActionsEnabled: Boolean = true,
    voteActionsEnabled: Boolean = writeActionsEnabled,
    voteColorPalette: VoteColorPalette = VoteColorPalette.NEUTRAL,
    voteControlMode: VoteControlMode = VoteControlMode.COMFORTABLE,
    saveActionsEnabled: Boolean = writeActionsEnabled,
    onShare: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VoteControl(
            score = score,
            vote = vote,
            mode = voteControlMode,
            palette = voteColorPalette,
            enabled = voteActionsEnabled,
            onUpvote = onUpvote,
            onDownvote = onDownvote,
        )
        if (voteControlMode == VoteControlMode.COMPACT) {
            Text(
                text = compactNumber(score),
                modifier = Modifier.padding(start = 4.dp, end = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = voteSelectedTint(
                    vote = vote,
                    palette = voteColorPalette,
                    darkTheme = isDarkScheme(),
                ) ?: MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        CompactAction(
            icon = Symbols.ChatBubble,
            label = compactNumber(comments),
            onClick = onComments,
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSave, enabled = saveActionsEnabled) {
            Icon(
                if (saved) Symbols.BookmarkFilled else Symbols.Bookmark,
                contentDescription = if (saved) "Unsave" else "Save",
                tint = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onShare) {
            Icon(Symbols.Share, contentDescription = "Share")
        }
    }
}

@Composable
internal fun VoteControl(
    score: Int,
    vote: Int,
    mode: VoteControlMode,
    palette: VoteColorPalette,
    enabled: Boolean,
    onUpvote: () -> Unit,
    onDownvote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val darkTheme = isDarkScheme()
    val upvoteSelectedTint = voteDirectionTint(1, palette, darkTheme)
    val downvoteSelectedTint = voteDirectionTint(-1, palette, darkTheme)
    val inactiveTint = MaterialTheme.colorScheme.onSurfaceVariant
    val disabledTint = inactiveTint.copy(alpha = 0.38f)
    val upvoteTint by animateColorAsState(
        targetValue = when {
            vote == 1 -> upvoteSelectedTint
            !enabled -> disabledTint
            else -> inactiveTint
        },
        label = "vote up tint",
    )
    val downvoteTint by animateColorAsState(
        targetValue = when {
            vote == -1 -> downvoteSelectedTint
            !enabled -> disabledTint
            else -> inactiveTint
        },
        label = "vote down tint",
    )
    val iconSize = if (mode == VoteControlMode.COMFORTABLE) 19.dp else 18.dp
    // A11Y01A1_MINIMUM_POST_ACTION_TARGET_V1
    val buttonSize = 48.dp

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = if (mode == VoteControlMode.COMFORTABLE) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
        } else {
            Color.Transparent
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onUpvote,
                enabled = enabled,
                modifier = Modifier.size(buttonSize),
            ) {
                Icon(
                    imageVector = voteStateIcon(1, mode, voted = vote == 1),
                    contentDescription = if (vote == 1) "Remove upvote" else "Upvote",
                    modifier = Modifier.size(iconSize),
                    tint = upvoteTint,
                )
            }
            if (mode == VoteControlMode.COMFORTABLE) {
                Text(
                    text = compactNumber(score),
                    modifier = Modifier.padding(horizontal = 3.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = voteSelectedTint(vote, palette, darkTheme) ?: inactiveTint,
                )
            }
            IconButton(
                onClick = onDownvote,
                enabled = enabled,
                modifier = Modifier.size(buttonSize),
            ) {
                Icon(
                    imageVector = voteStateIcon(-1, mode, voted = vote == -1),
                    contentDescription = if (vote == -1) "Remove downvote" else "Downvote",
                    modifier = Modifier.size(iconSize),
                    tint = downvoteTint,
                )
            }
        }
    }
}

private fun voteSelectedTint(
    vote: Int,
    palette: VoteColorPalette,
    darkTheme: Boolean,
): Color? = when (vote) {
    1 -> voteDirectionTint(1, palette, darkTheme)
    -1 -> voteDirectionTint(-1, palette, darkTheme)
    else -> null
}

// M3221_ONE_FAVORITE_STAR_COLOUR_V1 (#221): every favourite star (communities,
// custom feeds, the community header) is primary when set, as Material's
// checked icon toggle is.
@Composable
internal fun favoriteStarTint(favorite: Boolean): Color =
    if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

// ISSUE39_SHARED_VOTE_STYLE_V1
// The vote bar and the vote swipe actions read colour and icon from here.
// ISSUE218_VOTE_TINTS_PER_THEME_V1: each palette has a light-theme and a
// dark-theme tone (the dark ones are the original #39 tints), and a vote is
// also shown by the icon's shape, not by colour alone.
internal fun voteDirectionTint(
    direction: Int,
    palette: VoteColorPalette,
    darkTheme: Boolean,
): Color = when (palette) {
    VoteColorPalette.NEUTRAL -> if (darkTheme) SubshellOrangeSoft else SubshellVoteNeutralLight
    VoteColorPalette.RED_BLUE -> when {
        direction > 0 -> if (darkTheme) SubshellVoteUp else SubshellVoteUpLight
        else -> if (darkTheme) SubshellVoteDown else SubshellVoteDownLight
    }
}

internal fun voteDirectionIcon(
    direction: Int,
    mode: VoteControlMode,
): androidx.compose.ui.graphics.vector.ImageVector = when (mode) {
    VoteControlMode.COMFORTABLE ->
        if (direction > 0) Symbols.ThumbUp else Symbols.ThumbDown
    VoteControlMode.COMPACT ->
        if (direction > 0) Symbols.ArrowUpward else Symbols.ArrowDownward
}

/**
 * ISSUE218_VOTE_TINTS_PER_THEME_V1: the vote bar's icon also shows whether
 * the vote is cast (filled) or not (outlined), so the state is not colour
 * only. Swipe actions keep [voteDirectionIcon].
 */
internal fun voteStateIcon(
    direction: Int,
    mode: VoteControlMode,
    voted: Boolean,
): androidx.compose.ui.graphics.vector.ImageVector = when (mode) {
    VoteControlMode.COMFORTABLE -> when {
        direction > 0 -> if (voted) Symbols.ThumbUpFilled else Symbols.ThumbUp
        else -> if (voted) Symbols.ThumbDownFilled else Symbols.ThumbDown
    }
    VoteControlMode.COMPACT -> when {
        direction > 0 -> if (voted) Symbols.ArrowCircleUpFilled else Symbols.ArrowUpward
        else -> if (voted) Symbols.ArrowCircleDownFilled else Symbols.ArrowDownward
    }
}

@Composable
private fun CompactAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    selected: Boolean = false,
    selectedTint: Color? = null,
    enabled: Boolean = true,
) {
    val tint by animateColorAsState(
        when {
            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            selected -> selectedTint ?: MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "action tint",
    )
    // M3225_LIST_ROWS_ARE_LIST_ITEMS_V1 (components-15): an icon + label action is
    // an M3 text button with a leading icon.
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
        colors = ButtonDefaults.textButtonColors(contentColor = tint, disabledContentColor = tint),
    ) {
        Icon(
            icon,
            contentDescription = "Comments",
            modifier = Modifier.size(ButtonDefaults.IconSize),
        )
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun LinkPanel(post: Post) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = post.mediaLabel,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Icon(Symbols.KeyboardArrowDown, contentDescription = null)
        }
    }
}

// LOWUI8_COMPACT_NUMBERS_FOLLOW_THE_LOCALE_V1 (OCB-127, owner chose the
// device language): one decimal with the locale's separator, a zero decimal
// dropped ("1,2K" / "1K" in Norwegian, "1.2K" in English), and the unit
// chosen after rounding, so 999 950 reads "1M", not "1000K".
fun compactNumber(value: Int, locale: Locale = Locale.getDefault()): String {
    val magnitude = value.toLong().absoluteValue
    if (magnitude < 1_000L) return value.toString()
    val thousandTenths = (magnitude + 50L) / 100L
    val (tenths, suffix) = if (thousandTenths >= 10_000L) {
        (magnitude + 50_000L) / 100_000L to "M"
    } else {
        thousandTenths to "K"
    }
    val separator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
    val digits = if (tenths % 10L == 0L) "${tenths / 10L}" else "${tenths / 10L}$separator${tenths % 10L}"
    return (if (value < 0) "-" else "") + digits + suffix
}

fun accentFor(seed: String): Color {
    val colors = listOf(
        SubshellOrange,
        Color(0xFF78C7FF),
        Color(0xFF7ED6A5),
        Color(0xFFC5A7FF),
        Color(0xFFFFC765),
    )
    // ISSUE76_ACCENT_INDEX_NEVER_NEGATIVE_V1: Int.MIN_VALUE.absoluteValue is
    // still negative ("polygenelubricants" hashes to it) and indexing with it
    // crashed in composition. Taken as a Long it is positive; every other seed
    // keeps its colour.
    return colors[accentIndex(seed, colors.size)]
}

// ISSUE76_ACCENT_INDEX_NEVER_NEGATIVE_V1
internal fun accentIndex(seed: String, size: Int): Int =
    (seed.hashCode().toLong().absoluteValue % size).toInt()
