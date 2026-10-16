package app.subshell.prototype.ui.app

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.subshell.prototype.ui.MainDestination
import app.subshell.prototype.ui.NavigationLabelMode
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.navItems
import app.subshell.prototype.ui.navigationVisualLabel

/**
 * The Scaffold's bottom bar: the compact navigation bar with the inbox
 * unread badge, the label mode, and the You item's long-press account
 * switch. Hidden inside a route, in the expanded layout and while a modmail
 * thread is open.
 *
 * Moved out of SubshellApp verbatim. The hidden fraction arrives as the
 * animated State, so it is still read in the graphics layer only.
 */
@Composable
internal fun AppBottomBar(
    state: SubshellState,
    inContext: Boolean,
    expandedMainLayout: Boolean,
    modmailThreadOpen: Boolean,
    compactNavigationBarHeight: Dp,
    bottomBarHiddenFractionState: State<Float>,
    density: Density,
    inboxUnreadCount: Int,
    navInteractionSources: Map<MainDestination, MutableInteractionSource>,
    accountSwitcherVisibleState: MutableState<Boolean>,
    suppressYouClickState: MutableState<Boolean>,
    activeCustomFeedPathState: MutableState<String?>,
) {
    val bottomBarHiddenFraction by bottomBarHiddenFractionState
    var accountSwitcherVisible by accountSwitcherVisibleState
    var suppressYouClick by suppressYouClickState
    var activeCustomFeedPath by activeCustomFeedPathState
    if (!inContext && !expandedMainLayout && !modmailThreadOpen) {
        // M3223_SHORT_NAVIGATION_BAR_V1 (#223): Material's 64 dp ShortNavigationBar
        // instead of the 80 dp NavigationBar squeezed to 64 dp; large text keeps 88 dp
        // (A11Y01A1_LARGE_TEXT_NAVIGATION_HEIGHT_V1).
        ShortNavigationBar(
            modifier = Modifier
                .testTag("app-bottom-bar") // STEP3_APP_BOTTOM_BAR_TEST_TAG_V1: a test measures the bar's height
                .height(compactNavigationBarHeight)
                .graphicsLayer {
                    translationY = size.height * bottomBarHiddenFraction
                },
        ) {
            navItems.forEach { item ->
                val isYou = item.destination == MainDestination.YOU
                val navigationAccessibilityLabel = item.destination.label
                val navigationVisualLabel =
                    navigationVisualLabel(
                        destination = item.destination,
                        fontScale = density.fontScale,
                    )
                // "Partial" labels only the selected destination (the short bar has
                // no alwaysShowLabel); an unlabelled icon carries the name instead.
                val labelShown = when (state.navigationLabelMode) {
                    NavigationLabelMode.ON -> true
                    NavigationLabelMode.PARTIAL -> state.destination == item.destination
                    NavigationLabelMode.OFF -> false
                }
                ShortNavigationBarItem(
                    modifier = if (isYou) {
                        Modifier.semantics {
                            onLongClick(label = "Switch account") {
                                accountSwitcherVisible = true
                                true
                            }
                        }
                    } else {
                        Modifier
                    },
                    selected = state.destination == item.destination,
                    onClick = {
                        if (isYou && suppressYouClick) {
                            suppressYouClick = false
                        } else {
                            if (item.destination == MainDestination.HOME) {
                                activeCustomFeedPath = null
                            }
                            state.navigate(item.destination)
                        }
                    },
                    icon = {
                        if (item.destination == MainDestination.INBOX && inboxUnreadCount > 0) {
                            BadgedBox(
                                badge = {
                                    Badge {
                                        Text(if (inboxUnreadCount > 99) "99+" else inboxUnreadCount.toString())
                                    }
                                },
                            ) {
                                Icon(
                                    item.icon,
                                    contentDescription =
                                        if (labelShown) null else navigationAccessibilityLabel,
                                )
                            }
                        } else {
                            Icon(
                                item.icon,
                                contentDescription =
                                    if (labelShown) null else navigationAccessibilityLabel,
                            )
                        }
                    },
                    label = if (!labelShown) {
                        null
                    } else {
                        {
                            Text(
                                text = navigationVisualLabel,
                                modifier = Modifier.clearAndSetSemantics {
                                    contentDescription =
                                        navigationAccessibilityLabel
                                },
                                maxLines = 1,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = FontFamily.SansSerif,
                                ),
                            )
                        }
                    },
                    interactionSource = navInteractionSources.getValue(item.destination),
                )
            }
        }
    }
}
