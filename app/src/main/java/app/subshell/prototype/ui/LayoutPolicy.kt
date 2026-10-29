package app.subshell.prototype.ui

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.window.core.layout.WindowSizeClass
import app.subshell.prototype.ui.theme.LocalUiScale
import app.subshell.prototype.ui.screens.InboxModSection
import app.subshell.prototype.ui.screens.InboxPrimaryTab
import app.subshell.prototype.ui.symbols.Symbols

// #213: stateless layout policy moved verbatim out of SubshellApp.kt
// (navigation items and bar sizes, the expanded list-detail rules).

// M3223_ONE_SCREEN_MARGIN_V1 (#223, owner chose 16 dp = Material 3's compact
// margin): every screen's main list starts its content this far from the edge,
// so the same card sits in the same place in feeds, posts, search and settings.
// List-detail panes keep their own 8 dp.
internal val ScreenMargin: Dp = 16.dp

internal data class NavItem(
    val destination: MainDestination,
    val icon: ImageVector,
    // MATERIALSYMBOLS01: the selected destination shows the filled symbol.
    val selectedIcon: ImageVector,
)

internal val navItems = listOf(
    NavItem(MainDestination.HOME, Symbols.Home, Symbols.HomeFilled),
    NavItem(MainDestination.SUBS, Symbols.Groups, Symbols.GroupsFilled),
    NavItem(MainDestination.INBOX, Symbols.Inbox, Symbols.InboxFilled),
    NavItem(MainDestination.YOU, Symbols.Person, Symbols.PersonFilled),
)

private val CompactNavigationBarHeight = 64.dp
private val LargeTextNavigationBarHeight = 88.dp
private const val LargeTextNavigationBarFontScaleThreshold = 1.3f
// M3224_NAVIGATION_RAIL_FROM_MEDIUM_WIDTH_V1 (#224, owner): from Material's
// medium width class the rail replaces the bottom bar, on every route. Two
// panes start at the expanded class.
// ISSUE247_LAYOUT_FOLLOWS_WINDOW_SIZE_CLASSES_V1 (#247): the classes are
// Google's (currentWindowAdaptiveInfoV2().windowSizeClass, 600 / 840 dp) and
// come from the window's own size, not hard-coded edges over screenWidthDp.
// M3224_READING_WIDTH_V1 (#224, owner chose 640 dp): posts, comments, profiles,
// feeds and settings never run wider than a comfortable reading line.
internal val ReadingMaxWidth = 640.dp
internal val ExpandedContextReadingMaxWidth = ReadingMaxWidth
internal val ExpandedContextUtilityMaxWidth = ReadingMaxWidth
internal val ExpandedContextDiscoveryMaxWidth = 960.dp

// A11Y01A1_LARGE_TEXT_NAVIGATION_HEIGHT_V1
internal fun navigationBarBaseHeightForFontScale(fontScale: Float): Dp =
    if (fontScale >= LargeTextNavigationBarFontScaleThreshold) {
        LargeTextNavigationBarHeight
    } else {
        CompactNavigationBarHeight
    }

// A11Y01A11_LARGE_TEXT_BOTTOM_NAV_LABEL_V1
internal fun navigationVisualLabel(
    destination: MainDestination,
    fontScale: Float,
): String =
    if (
        destination == MainDestination.SUBS &&
        fontScale >= LargeTextNavigationBarFontScaleThreshold
    ) {
        "Subs"
    } else {
        destination.label
    }

// A11Y01A2_EXPANDED_MAIN_BREAKPOINT_V1
internal fun useExpandedMainLayout(windowSizeClass: WindowSizeClass): Boolean =
    windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)

internal fun useNavigationRail(windowSizeClass: WindowSizeClass): Boolean =
    windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

// M3224_NAVIGATION_RAIL_FROM_MEDIUM_WIDTH_V1: the rail names the destination
// as the bottom bar does ("Communities", not "Subs").
internal fun navigationRailVisualLabel(destination: MainDestination): String = destination.label

internal fun useExpandedHomePostListDetail(
    expandedWindow: Boolean,
    supportedOrigin: Boolean,
    customFeedActive: Boolean,
    tabletLayoutMode: TabletLayoutMode,
): Boolean =
    expandedWindow &&
        supportedOrigin &&
        !customFeedActive &&
        tabletLayoutMode == TabletLayoutMode.SPLIT

// NEXT01A1_COMMUNITY_POST_TWO_PANE_POLICY_V1
internal fun useExpandedCommunityPostListDetail(
    expandedWindow: Boolean,
    supportedOrigin: Boolean,
    tabletLayoutMode: TabletLayoutMode,
): Boolean =
    expandedWindow &&
        supportedOrigin &&
        tabletLayoutMode == TabletLayoutMode.SPLIT

// NEXT01A2_SEARCH_POST_TWO_PANE_POLICY_V1
internal fun useExpandedSearchPostListDetail(
    expandedWindow: Boolean,
    supportedOrigin: Boolean,
    tabletLayoutMode: TabletLayoutMode,
): Boolean =
    expandedWindow &&
        supportedOrigin &&
        tabletLayoutMode == TabletLayoutMode.SPLIT

// NEXT01A2R1_SEARCH_WORKSPACE_TWO_PANE_POLICY_V1
internal fun useExpandedSearchWorkspaceListDetail(
    expandedWindow: Boolean,
    tabletLayoutMode: TabletLayoutMode,
): Boolean =
    expandedWindow &&
        tabletLayoutMode == TabletLayoutMode.SPLIT

// NEXT01A3A_INBOX_ACTIVITY_WORKSPACE_POLICY_V1
internal fun useExpandedInboxActivityWorkspaceListDetail(
    expandedWindow: Boolean,
    selectedTab: InboxPrimaryTab,
    tabletLayoutMode: TabletLayoutMode,
): Boolean =
    expandedWindow &&
        selectedTab == InboxPrimaryTab.ACTIVITY &&
        tabletLayoutMode == TabletLayoutMode.SPLIT

// NEXT01A3B_INBOX_MESSAGES_WORKSPACE_POLICY_V1
internal fun useExpandedInboxMessagesWorkspaceListDetail(
    expandedWindow: Boolean,
    selectedTab: InboxPrimaryTab,
    tabletLayoutMode: TabletLayoutMode,
): Boolean =
    expandedWindow &&
        selectedTab == InboxPrimaryTab.MESSAGES &&
        tabletLayoutMode == TabletLayoutMode.SPLIT

// NEXT01A3C_INBOX_MOD_QUEUE_WORKSPACE_POLICY_V1
internal fun useExpandedInboxModQueueWorkspaceListDetail(
    expandedWindow: Boolean,
    selectedTab: InboxPrimaryTab,
    selectedModSection: InboxModSection,
    tabletLayoutMode: TabletLayoutMode,
): Boolean =
    expandedWindow &&
        selectedTab == InboxPrimaryTab.MOD &&
        selectedModSection == InboxModSection.QUEUE &&
        tabletLayoutMode == TabletLayoutMode.SPLIT

// NEXT01A3D_INBOX_MODMAIL_WORKSPACE_POLICY_V1
internal fun useExpandedInboxModmailWorkspaceListDetail(
    expandedWindow: Boolean,
    selectedTab: InboxPrimaryTab,
    selectedModSection: InboxModSection,
    tabletLayoutMode: TabletLayoutMode,
): Boolean =
    expandedWindow &&
        selectedTab == InboxPrimaryTab.MOD &&
        selectedModSection == InboxModSection.MODMAIL &&
        tabletLayoutMode == TabletLayoutMode.SPLIT

internal fun useExpandedInboxListDetail(
    expandedWindow: Boolean,
    supportedOrigin: Boolean,
    tabletLayoutMode: TabletLayoutMode,
): Boolean =
    expandedWindow &&
        supportedOrigin &&
        tabletLayoutMode == TabletLayoutMode.SPLIT

// NEXT01A6_PUBLIC_USER_ORIGIN_BOUNDARY_V1
internal fun supportsExpandedPublicUserContentOrigin(
    username: String,
    activeAccountUsername: String,
    blockedWorkspace: Boolean,
): Boolean =
    !username.equals(activeAccountUsername, ignoreCase = true) &&
        !blockedWorkspace

// NEXT01A6_PUBLIC_USER_CONTENT_WORKSPACE_POLICY_V1
internal fun useExpandedPublicUserContentWorkspaceListDetail(
    expandedWindow: Boolean,
    supportedOrigin: Boolean,
    selectedTab: Int,
    tabletLayoutMode: TabletLayoutMode,
): Boolean =
    expandedWindow &&
        supportedOrigin &&
        selectedTab in 0..1 &&
        tabletLayoutMode == TabletLayoutMode.SPLIT

// NEXT01A5_SUBSCRIPTIONS_COMMUNITY_WORKSPACE_POLICY_V1
internal fun useExpandedSubscriptionsCommunityListDetail(
    expandedWindow: Boolean,
    supportedOrigin: Boolean,
    tabletLayoutMode: TabletLayoutMode,
): Boolean =
    expandedWindow &&
        supportedOrigin &&
        tabletLayoutMode == TabletLayoutMode.SPLIT

// NEXT01A4A_YOU_CONTENT_WORKSPACE_POLICY_V1
internal fun useExpandedYouContentWorkspaceListDetail(
    expandedWindow: Boolean,
    supportedOrigin: Boolean,
    selectedTab: Int,
    tabletLayoutMode: TabletLayoutMode,
): Boolean =
    expandedWindow &&
        supportedOrigin &&
        selectedTab in 0..5 &&
        tabletLayoutMode == TabletLayoutMode.SPLIT

// NEXT01A4B_YOU_BLOCKED_WORKSPACE_POLICY_V1
internal fun useExpandedYouBlockedWorkspaceListDetail(
    expandedWindow: Boolean,
    supportedOrigin: Boolean,
    selectedTab: Int,
    tabletLayoutMode: TabletLayoutMode,
): Boolean =
    expandedWindow &&
        supportedOrigin &&
        selectedTab == 6 &&
        tabletLayoutMode == TabletLayoutMode.SPLIT

internal fun shouldCloseYouBlockedDetailOnTabSelection(
    selectedTab: Int,
): Boolean = selectedTab != 6

internal const val YOU_BLOCKED_WORKSPACE_PLACEHOLDER =
    "Select an account"

// NEXT01A4C_YOU_DRAFTS_WORKSPACE_POLICY_V1
internal fun useExpandedYouDraftsWorkspaceListDetail(
    expandedWindow: Boolean,
    supportedOrigin: Boolean,
    selectedTab: Int,
    tabletLayoutMode: TabletLayoutMode,
): Boolean =
    expandedWindow &&
        supportedOrigin &&
        selectedTab == 7 &&
        tabletLayoutMode == TabletLayoutMode.SPLIT

internal fun shouldCloseYouDraftsDetailOnTabSelection(
    selectedTab: Int,
): Boolean = selectedTab != 7

internal const val YOU_DRAFTS_WORKSPACE_PLACEHOLDER =
    "Select a draft"

internal fun youContentWorkspaceLabel(selectedTab: Int): String =
    when (selectedTab) {
        0 -> "Posts"
        1 -> "Comments"
        2 -> "Saved"
        3 -> "Upvotes"
        4 -> "Downvotes"
        5 -> "Hidden"
        else -> "You"
    }

internal fun youContentWorkspacePlaceholder(selectedTab: Int): String =
    when (selectedTab) {
        1 -> "Select a comment"
        2 -> "Select a saved item"
        else -> "Select a post"
    }

/**
 * ISSUE278_LAYOUT_IGNORES_THE_UI_SIZE_V1: the window size class of the window
 * as Android measures it. A smaller UI size makes every dp smaller, so read
 * through the scaled density a phone would look wider and could switch to the
 * rail or two panes; the class is read with the unscaled density instead.
 */
@Composable
internal fun currentUnscaledWindowSizeClass(): WindowSizeClass {
    val scale = LocalUiScale.current
    if (scale == 1f) return currentWindowAdaptiveInfoV2().windowSizeClass
    val density = LocalDensity.current
    var sizeClass: WindowSizeClass? = null
    CompositionLocalProvider(LocalDensity provides Density(density.density / scale, density.fontScale)) {
        sizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    }
    return requireNotNull(sizeClass)
}

/**
 * ISSUE278_LARGE_TEXT_IS_JUDGED_AS_SEEN_V1: how large text looks on screen, the
 * font scale times the UI size. The bars' large-text layout (a taller bar, the
 * short "Subs" label) follows this, so a smaller UI with large text gets the
 * normal bar once the text is no longer large on screen.
 */
@Composable
internal fun visibleFontScale(): Float = LocalDensity.current.fontScale * LocalUiScale.current
