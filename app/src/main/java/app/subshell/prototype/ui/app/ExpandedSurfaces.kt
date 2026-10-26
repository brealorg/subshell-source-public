package app.subshell.prototype.ui.app

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.subshell.prototype.ui.MainDestination
import app.subshell.prototype.ui.NavItem
import app.subshell.prototype.ui.NavigationLabelMode
import app.subshell.prototype.ui.ReadingMaxWidth
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.navItems
import app.subshell.prototype.ui.navigationRailVisualLabel

private val ExpandedListPaneWidth = 360.dp

/**
 * M3224_READING_WIDTH_V1: centres a single column at the reading width; on a
 * narrower window it is the full width.
 */
internal fun Modifier.readingColumn(maxWidth: Dp = ReadingMaxWidth): Modifier =
    this
        .fillMaxSize()
        .wrapContentWidth(Alignment.CenterHorizontally)
        .widthIn(max = maxWidth)
        .fillMaxHeight()

// A11Y01A4_EXPANDED_LIST_DETAIL_SURFACE_V1
@Composable
internal fun ExpandedListDetailSurface(
    scaffoldPadding: PaddingValues,
    listTitle: String,
    listSubtitle: String?,
    listContent: @Composable (Modifier) -> Unit,
    detailContent: @Composable (Modifier) -> Unit,
    // NEXT01A6_OPTIONAL_LIST_PANE_HEADER_V1
    showListHeader: Boolean = true,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(scaffoldPadding)
            // DOCKINSETS01_ROUTES_SEE_THE_ROOT_PADDING_AS_CONSUMED_V1: the padding
            // already leaves out the gesture bar; a bar inside that pads for the
            // gesture bar or the keyboard must not count it again.
            .consumeWindowInsets(scaffoldPadding),
        contentAlignment = Alignment.TopCenter,
    ) {
        // M3224_ONE_LIST_DETAIL_GEOMETRY_V1 (#224): one geometry on every window
        // and route: a 360 dp list and the detail filling the rest, its content
        // centred at the reading width (the old 1200 dp cap never applied).
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(),
        ) {
            Surface(
                modifier = Modifier
                    .width(ExpandedListPaneWidth)
                    .fillMaxHeight(),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(Modifier.fillMaxSize()) {
                    if (showListHeader) {
                        Column(
                            modifier = Modifier.padding(
                                horizontal = 16.dp,
                                vertical = 12.dp,
                            ),
                        ) {
                            Text(
                                text = listTitle,
                                style = MaterialTheme.typography.titleLarge,
                            )
                            if (!listSubtitle.isNullOrBlank()) {
                                Text(
                                    text = listSubtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                    Box(Modifier.weight(1f)) {
                        listContent(Modifier.fillMaxSize())
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight(),
                color = MaterialTheme.colorScheme.outlineVariant,
            ) {}

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                detailContent(Modifier.readingColumn())
            }
        }
    }
}

// A11Y01A3_EXPANDED_CONTEXT_SURFACE_V1
@Composable
internal fun ExpandedContextSurface(
    scaffoldPadding: PaddingValues,
    expanded: Boolean,
    maxWidth: Dp,
    content: @Composable (Modifier) -> Unit,
) {
    if (!expanded) {
        // M3224_READING_WIDTH_V1: a medium window (or a phone on its side) gets the
        // same reading width; on a phone it is the full width.
        content(
            Modifier
                .padding(scaffoldPadding)
                // DOCKINSETS01_ROUTES_SEE_THE_ROOT_PADDING_AS_CONSUMED_V1
                .consumeWindowInsets(scaffoldPadding)
                .readingColumn(maxWidth),
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(scaffoldPadding)
            // DOCKINSETS01_ROUTES_SEE_THE_ROOT_PADDING_AS_CONSUMED_V1
            .consumeWindowInsets(scaffoldPadding),
        contentAlignment = Alignment.TopCenter,
    ) {
        content(
            Modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .fillMaxHeight(),
        )
    }
}

@Composable
internal fun ExpandedMainNavigationRail(
    state: SubshellState,
    inboxUnreadCount: Int,
    navInteractionSources: Map<MainDestination, MutableInteractionSource>,
    suppressYouClick: Boolean,
    onSuppressYouClickChange: (Boolean) -> Unit,
    onNavigate: (MainDestination) -> Unit,
    onOpenAccountSwitcher: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // ISSUE247_WIDE_NAVIGATION_RAIL_V1 (#247): M3 Expressive's rail, collapsed
    // as the rail before it (the expanded form is a separate decision).
    WideNavigationRail(
        modifier = modifier,
        // M3223_CUTOUT_IS_SAFE_V1: clear of a side display cutout too.
        windowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Vertical + WindowInsetsSides.Start),
        colors = WideNavigationRailDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Spacer(Modifier.height(8.dp))
        navItems.forEach { item ->
            val isYou = item.destination == MainDestination.YOU
            val accessibilityLabel = item.destination.label
            val visualLabel = navigationRailVisualLabel(item.destination)
            WideNavigationRailItem(
                modifier = if (isYou) {
                    Modifier.semantics {
                        onLongClick(label = "Switch account") {
                            onOpenAccountSwitcher()
                            true
                        }
                    }
                } else {
                    Modifier
                },
                selected = state.destination == item.destination,
                railExpanded = false,
                onClick = {
                    if (isYou && suppressYouClick) {
                        onSuppressYouClickChange(false)
                    } else {
                        onNavigate(item.destination)
                    }
                },
                icon = {
                    if (item.destination == MainDestination.INBOX && inboxUnreadCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge {
                                    Text(
                                        if (inboxUnreadCount > 99) {
                                            "99+"
                                        } else {
                                            inboxUnreadCount.toString()
                                        },
                                    )
                                }
                            },
                        ) {
                            Icon(
                                if (state.destination == item.destination) item.selectedIcon else item.icon,
                                contentDescription =
                                    if (
                                        state.navigationLabelMode ==
                                        NavigationLabelMode.OFF
                                    ) {
                                        accessibilityLabel
                                    } else {
                                        null
                                    },
                            )
                        }
                    } else {
                        Icon(
                            if (state.destination == item.destination) item.selectedIcon else item.icon,
                            contentDescription =
                                if (
                                    state.navigationLabelMode ==
                                    NavigationLabelMode.OFF
                                ) {
                                    accessibilityLabel
                                } else {
                                    null
                                },
                        )
                    }
                },
                // PARTIAL names only the selected destination, as alwaysShowLabel did.
                label = if (
                    state.navigationLabelMode == NavigationLabelMode.OFF ||
                    (state.navigationLabelMode == NavigationLabelMode.PARTIAL && state.destination != item.destination)
                ) {
                    null
                } else {
                    {
                        Text(
                            text = visualLabel,
                            modifier = Modifier.clearAndSetSemantics {
                                contentDescription = accessibilityLabel
                            },
                            maxLines = 1,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = FontFamily.SansSerif,
                            ),
                        )
                    }
                },
                interactionSource =
                    navInteractionSources.getValue(item.destination),
            )
        }
    }
}

// A11Y01A2_BOUNDED_EXPANDED_MAIN_CONTENT_V1
internal fun mainContentPadding(
    scaffoldPadding: PaddingValues,
    layoutDirection: LayoutDirection,
    horizontal: Dp,
    topExtra: Dp,
    bottomExtra: Dp,
): PaddingValues = PaddingValues(
    start = scaffoldPadding.calculateStartPadding(layoutDirection) + horizontal,
    top = scaffoldPadding.calculateTopPadding() + topExtra,
    end = scaffoldPadding.calculateEndPadding(layoutDirection) + horizontal,
    bottom = bottomExtra,
)
