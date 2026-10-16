package app.subshell.prototype.ui.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.subshell.prototype.model.Post
import app.subshell.prototype.ui.ExpandedContextReadingMaxWidth
import app.subshell.prototype.ui.MainDestination
import app.subshell.prototype.ui.ScreenMargin
import app.subshell.prototype.ui.SubshellRoute
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.app.ExpandedContextSurface
import app.subshell.prototype.ui.app.ExpandedListDetailSurface
import app.subshell.prototype.ui.shouldCloseYouBlockedDetailOnTabSelection
import app.subshell.prototype.ui.supportsExpandedPublicUserContentOrigin
import app.subshell.prototype.ui.useExpandedPublicUserContentWorkspaceListDetail
import app.subshell.prototype.ui.useExpandedYouBlockedWorkspaceListDetail
import app.subshell.prototype.ui.youContentWorkspaceLabel
import app.subshell.prototype.ui.youContentWorkspacePlaceholder

@Composable
internal fun UserRouteHost(
    state: SubshellState,
    route: SubshellRoute.User,
    padding: PaddingValues,
    expandedWindowLayout: Boolean,
    screenStateHolder: SaveableStateHolder,
    mainYouProfileContent: @Composable (String, Modifier, PaddingValues, ((app.subshell.prototype.model.Post, String?) -> Unit)?, ((Int) -> Unit)?, ((String) -> Unit)?) -> Unit,
    publicUserProfileContent: @Composable (String, Modifier, PaddingValues, (() -> Unit)?, Boolean, ((app.subshell.prototype.model.Post, String?) -> Unit)?, ((Int) -> Unit)?) -> Unit,
) {
    screenStateHolder.SaveableStateProvider(
        if (
            useExpandedPublicUserContentWorkspaceListDetail(
                expandedWindow = expandedWindowLayout,
                supportedOrigin =
                    supportsExpandedPublicUserContentOrigin(
                        username = route.username,
                        activeAccountUsername =
                            state.activeAccountUsername,
                        blockedWorkspace =
                            useExpandedYouBlockedWorkspaceListDetail(
                                expandedWindow =
                                    expandedWindowLayout,
                                supportedOrigin =
                                    route.destination ==
                                        MainDestination.YOU,
                                selectedTab =
                                    state.userProfileTab(
                                        state.activeAccountUsername,
                                    ),
                                tabletLayoutMode =
                                    state.tabletLayoutMode,
                            ),
                    ),
                selectedTab =
                    state.userProfileTab(route.username),
                tabletLayoutMode =
                    state.tabletLayoutMode,
            )
        ) {
            "public-user-workspace-${route.username.lowercase()}"
        } else {
            "user-${route.username}"
        },
    ) {
        // NEXT01A6_PUBLIC_USER_ROUTE_WORKSPACE_V1
        val selectedBlockedTab =
            state.userProfileTab(
                state.activeAccountUsername,
            )
        val blockedUserWorkspace =
            useExpandedYouBlockedWorkspaceListDetail(
                expandedWindow =
                    expandedWindowLayout,
                supportedOrigin =
                    route.destination ==
                        MainDestination.YOU,
                selectedTab =
                    selectedBlockedTab,
                tabletLayoutMode =
                    state.tabletLayoutMode,
            )

        val selectedPublicUserTab =
            state.userProfileTab(route.username)
        val publicUserWorkspace =
            useExpandedPublicUserContentWorkspaceListDetail(
                expandedWindow =
                    expandedWindowLayout,
                supportedOrigin =
                    supportsExpandedPublicUserContentOrigin(
                        username = route.username,
                        activeAccountUsername =
                            state.activeAccountUsername,
                        blockedWorkspace =
                            blockedUserWorkspace,
                    ),
                selectedTab =
                    selectedPublicUserTab,
                tabletLayoutMode =
                    state.tabletLayoutMode,
            )

        // NEXT01A4B_SHARED_EXISTING_USER_DETAIL_V1
        // NEXT01A6_REUSE_SHARED_CONTEXTUAL_USER_PROFILE_HOST_V1
        val userDetail:
            @Composable (Modifier) -> Unit =
            { detailModifier ->
                publicUserProfileContent(
                    route.username,
                    detailModifier,
                    PaddingValues(
                        start = ScreenMargin,
                        top = 4.dp,
                        end = ScreenMargin,
                        bottom = 32.dp,
                    ),
                    state::closeUser,
                    true,
                    null,
                    null,
                )
            }

        if (publicUserWorkspace) {
            // NEXT01A6_PUBLIC_USER_ROUTE_TWO_PANE_V1
            ExpandedListDetailSurface(
                scaffoldPadding = padding,
                listTitle = "u/${route.username}",
                listSubtitle =
                    youContentWorkspaceLabel(
                        selectedPublicUserTab,
                    ),
                listContent = { listModifier ->
                    publicUserProfileContent(
                        route.username,
                        listModifier,
                        PaddingValues(
                            horizontal = 8.dp,
                            vertical = 8.dp,
                        ),
                        state::closeUser,
                        false,
                        null,
                        null,
                    )
                },
                detailContent = { detailModifier ->
                    Box(
                        modifier =
                            detailModifier.fillMaxSize(),
                        contentAlignment =
                            Alignment.Center,
                    ) {
                        Text(
                            text =
                                youContentWorkspacePlaceholder(
                                    selectedPublicUserTab,
                                ),
                            style =
                                MaterialTheme.typography
                                    .titleMedium,
                            color =
                                MaterialTheme.colorScheme
                                    .onSurfaceVariant,
                        )
                    }
                },
                showListHeader = false,
            )
        } else if (blockedUserWorkspace) {
            // NEXT01A4B_BLOCKED_USER_TWO_PANE_V1
            ExpandedListDetailSurface(
                scaffoldPadding = padding,
                listTitle = "You",
                listSubtitle = "Blocked",
                listContent = { listModifier ->
                    mainYouProfileContent(
                        state.activeAccountUsername,
                        listModifier,
                        PaddingValues(
                            horizontal = 8.dp,
                            vertical = 8.dp,
                        ),
                        null,
                        { selectedTab ->
                            if (
                                shouldCloseYouBlockedDetailOnTabSelection(
                                    selectedTab,
                                )
                            ) {
                                state.closeUser()
                            }
                        },
                        { selectedUsername ->
                            state.replaceCurrentUserSelection(
                                selectedUsername,
                            )
                        },
                    )
                },
                detailContent = userDetail,
            )
        } else {
            ExpandedContextSurface(
                scaffoldPadding = padding,
                expanded = expandedWindowLayout,
                maxWidth = ExpandedContextReadingMaxWidth,
            ) { contextModifier ->
                userDetail(contextModifier)
            }
        }
    }
}
