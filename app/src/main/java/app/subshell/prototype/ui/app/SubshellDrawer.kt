package app.subshell.prototype.ui.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.selected
import app.subshell.prototype.ui.components.OneLineAutoSizeLabel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.subshell.prototype.data.account.CustomFeedsUiState
import app.subshell.prototype.data.account.RedditSubscriptionsUiState
import app.subshell.prototype.model.Community
import app.subshell.prototype.ui.DrawerCommunitiesMode
import app.subshell.prototype.ui.DrawerDynamicGroup
import app.subshell.prototype.ui.DrawerDynamicGroupExpansion
import app.subshell.prototype.ui.FeedSource
import app.subshell.prototype.ui.MainDestination
import app.subshell.prototype.ui.RECENT_COMMUNITY_DRAWER_LIMIT
import app.subshell.prototype.ui.SecondaryDestination
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.components.CommunityAvatar
import app.subshell.prototype.ui.components.ScrollPositionIndicator
import app.subshell.prototype.ui.components.SubshellWordmark
import app.subshell.prototype.ui.ThemeChoice
import app.subshell.prototype.ui.drawerThemeChoices
import app.subshell.prototype.ui.toggleDrawerDynamicGroup
import kotlinx.coroutines.delay

@Composable
internal fun SubshellDrawer(
    state: SubshellState,
    redditFeed: FeedEntryPoints,
    customFeeds: CustomFeedsEntryPoints,
    activeCustomFeedPath: String?,
    onSelectFeedSource: (FeedSource) -> Unit,
    onOpenCustomFeed: (String) -> Unit,
    onOpenMutedCommunities: () -> Unit,
    onEditCommunityMute: (String) -> Unit,
    // ISSUE211_RANDOM_NSFW_COMMUNITY_V1: shown with its setting while NSFW is.
    onRandomNsfwCommunity: (() -> Unit)? = null,
    onAction: (() -> Unit) -> Unit,
) {
    val customFeedsState = customFeeds.customFeedsState
    val onLoadCustomFeeds = customFeeds.onLoadCustomFeeds
    val redditSubscriptionsState = redditFeed.redditSubscriptionsState
    // SUBS01C2_SINGLE_COMMUNITIES_OWNER_AND_SCROLL_AFFORDANCE_V1
    var communitiesExpanded by rememberSaveable(state.activeAccountId) {
        mutableStateOf(false)
    }
    var customFeedsExpanded by rememberSaveable(state.activeAccountId) {
        mutableStateOf(false)
    }
    val drawerListState = rememberLazyListState()
    val authenticatedCommunities = redditSubscriptionsState
        .takeIf { subscriptions -> subscriptions.matches(state.activeAccountId) }
        ?.communities
        .orEmpty()
    val joinedCommunities = if (state.activeAccountIsAuthenticated) {
        authenticatedCommunities
    } else {
        state.communities.filter { community -> community.id in state.joinedCommunityIds }
    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { community -> community.name })

    val drawerCustomFeeds = if (state.activeAccountIsAuthenticated) {
        customFeedsState
            .takeIf { feeds ->
                feeds.accountStableId.isBlank() ||
                    feeds.accountStableId == state.activeAccountId
            }
            ?.feeds
            .orEmpty()
    } else {
        emptyList()
    }

    val activeCommunityMutes = state.activeCommunityMuteEntries()

    LaunchedEffect(state.activeAccountId, state.activeAccountIsAuthenticated) {
        if (state.activeAccountIsAuthenticated) {
            onLoadCustomFeeds(state.activeAccountId, false)
        }
    }

    LaunchedEffect(communitiesExpanded, state.drawerCommunitiesMode) {
        if (state.drawerCommunitiesMode == DrawerCommunitiesMode.COLLAPSIBLE &&
            communitiesExpanded
        ) {
            delay(60L)
            val headerIndex = drawerListState.layoutInfo.visibleItemsInfo
                .firstOrNull { item -> item.key == "drawer-communities-header" }
                ?.index
            if (headerIndex != null) {
                if (state.reduceMotion) {
                    drawerListState.scrollToItem(headerIndex)
                } else {
                    drawerListState.animateScrollToItem(headerIndex)
                }
            }
        }
    }

    LaunchedEffect(customFeedsExpanded) {
        if (customFeedsExpanded) {
            delay(60L)
            val headerIndex = drawerListState.layoutInfo.visibleItemsInfo
                .firstOrNull { item -> item.key == "drawer-custom-feeds-header" }
                ?.index
            if (headerIndex != null) {
                if (state.reduceMotion) {
                    drawerListState.scrollToItem(headerIndex)
                } else {
                    drawerListState.animateScrollToItem(headerIndex)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(horizontal = 10.dp),
    ) {
        SubshellWordmark(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 14.dp),
            subtitle = "Your Reddit, your way",
        )

        // M3223_DRAWER_ROWS_ARE_MATERIAL_DRAWER_ITEMS_V1: the drawer uses the app's
        // one scroll indicator (#205, the reader's setting) instead of its own.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            LazyColumn(
                state = drawerListState,
                modifier = Modifier.fillMaxSize(),
            ) {
                item("drawer-feeds-title") {
                    DrawerSectionLabel("Feeds")
                }
                items(
                    items = listOf(
                        Triple(FeedSource.HOME, Icons.Rounded.Home, "Home"),
                        Triple(FeedSource.POPULAR, Icons.AutoMirrored.Rounded.TrendingUp, "Popular"),
                        Triple(FeedSource.ALL, Icons.Rounded.Public, "All"),
                    ),
                    key = { (source, _, _) -> "drawer-feed-${source.name}" },
                ) { (source, icon, label) ->
                    CompactDrawerItem(
                        label = label,
                        icon = icon,
                        selected = activeCustomFeedPath == null &&
                            state.destination == MainDestination.HOME &&
                            state.feedSource == source,
                        onClick = {
                            onAction {
                                onSelectFeedSource(source)
                            }
                        },
                    )
                }

                if (drawerCustomFeeds.isNotEmpty()) {
                    item("drawer-custom-feeds-header") {
                        CompactDrawerItem(
                            label = "Custom feeds · ${drawerCustomFeeds.size}",
                            icon = Icons.AutoMirrored.Rounded.ViewList,
                            // LOWUX3_CUSTOM_FEEDS_HEADER_SELECTED_ONLY_ON_HOME_V1 (OCB-123):
                            // a remembered feed kept it lit next to Inbox or Profile.
                            selected = activeCustomFeedPath != null &&
                                state.destination == MainDestination.HOME && !customFeedsExpanded,
                            onClick = {
                                val next = toggleDrawerDynamicGroup(
                                    current = DrawerDynamicGroupExpansion(
                                        communitiesExpanded = communitiesExpanded,
                                        customFeedsExpanded = customFeedsExpanded,
                                    ),
                                    group = DrawerDynamicGroup.CUSTOM_FEEDS,
                                )
                                communitiesExpanded = next.communitiesExpanded
                                customFeedsExpanded = next.customFeedsExpanded
                            },
                            trailing = {
                                Icon(
                                    imageVector = if (customFeedsExpanded) {
                                        Icons.Rounded.KeyboardArrowDown
                                    } else {
                                        Icons.AutoMirrored.Rounded.KeyboardArrowRight
                                    },
                                    contentDescription = if (customFeedsExpanded) {
                                        "Collapse custom feeds"
                                    } else {
                                        "Expand custom feeds"
                                    },
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                        )
                    }
                    if (customFeedsExpanded) {
                        items(
                            items = drawerCustomFeeds,
                            key = { feed -> "drawer-custom-feed-${feed.path}" },
                        ) { feed ->
                            CompactDrawerItem(
                                modifier = Modifier.padding(start = 20.dp),
                                label = feed.displayName.ifBlank {
                                    feed.path.substringAfterLast('/')
                                },
                                icon = Icons.AutoMirrored.Rounded.ViewList,
                                selected = state.destination == MainDestination.HOME &&
                                    activeCustomFeedPath == feed.path,
                                onClick = {
                                    onAction {
                                        onOpenCustomFeed(feed.path)
                                    }
                                },
                            )
                        }
                    }
                }

                item("drawer-communities-divider") {
                    DrawerSectionDivider()
                }

                when (state.drawerCommunitiesMode) {
                    DrawerCommunitiesMode.CURRENT -> {
                        item("drawer-communities-current") {
                            CompactDrawerItem(
                                label = "Communities",
                                icon = Icons.Rounded.Groups,
                                selected = state.destination == MainDestination.SUBS &&
                                    state.secondaryDestination == null &&
                                    state.selectedSubreddit == null,
                                onClick = { onAction { state.navigate(MainDestination.SUBS) } },
                            )
                        }
                    }
                    DrawerCommunitiesMode.COLLAPSIBLE -> {
                        item("drawer-communities-header") {
                            CompactDrawerItem(
                                label = "Communities",
                                icon = Icons.Rounded.Groups,
                                selected = state.destination == MainDestination.SUBS &&
                                    state.secondaryDestination == null &&
                                    state.selectedSubreddit == null,
                                onClick = { onAction { state.navigate(MainDestination.SUBS) } },
                                trailing = {
                                    IconButton(
                                        onClick = {
                                            val next = toggleDrawerDynamicGroup(
                                                current = DrawerDynamicGroupExpansion(
                                                    communitiesExpanded = communitiesExpanded,
                                                    customFeedsExpanded = customFeedsExpanded,
                                                ),
                                                group = DrawerDynamicGroup.COMMUNITIES,
                                            )
                                            communitiesExpanded = next.communitiesExpanded
                                            customFeedsExpanded = next.customFeedsExpanded
                                        },
                                    ) {
                                        Icon(
                                            imageVector = if (communitiesExpanded) {
                                                Icons.Rounded.KeyboardArrowDown
                                            } else {
                                                Icons.AutoMirrored.Rounded.KeyboardArrowRight
                                            },
                                            contentDescription = if (communitiesExpanded) {
                                                "Collapse communities"
                                            } else {
                                                "Expand communities"
                                            },
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                },
                            )
                        }
                        if (communitiesExpanded) {
                            drawerCommunityGroups(
                                state = state,
                                communities = joinedCommunities,
                                onAction = onAction,
                                onEditCommunityMute = onEditCommunityMute,
                            )
                        }
                    }
                    DrawerCommunitiesMode.FULL -> {
                        item("drawer-communities-header") {
                            CompactDrawerItem(
                                label = "Communities",
                                icon = Icons.Rounded.Groups,
                                selected = state.destination == MainDestination.SUBS &&
                                    state.secondaryDestination == null &&
                                    state.selectedSubreddit == null,
                                onClick = { onAction { state.navigate(MainDestination.SUBS) } },
                            )
                        }
                        drawerCommunityGroups(
                            state = state,
                            communities = joinedCommunities,
                            onAction = onAction,
                            onEditCommunityMute = onEditCommunityMute,
                        )
                    }
                }

                item("drawer-you-divider") {
                    DrawerSectionDivider()
                }
                item("drawer-you-title") {
                    DrawerSectionLabel("You")
                }
                item("drawer-you-inbox") {
                    CompactDrawerItem(
                        label = "Inbox",
                        icon = Icons.Rounded.Inbox,
                        selected = state.destination == MainDestination.INBOX &&
                            state.secondaryDestination == null,
                        onClick = { onAction { state.navigate(MainDestination.INBOX) } },
                    )
                }
                item("drawer-you-profile") {
                    CompactDrawerItem(
                        label = "Profile",
                        icon = Icons.Rounded.Person,
                        selected = state.destination == MainDestination.YOU &&
                            state.secondaryDestination == null,
                        onClick = { onAction { state.navigate(MainDestination.YOU) } },
                    )
                }
                    }
            if (state.scrollPositionIndicator) {
                ScrollPositionIndicator(
                    state = drawerListState,
                    moreCanLoad = false,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }

        DrawerSectionDivider()

        if (activeCommunityMutes.isNotEmpty()) {
            CompactDrawerItem(
                label = "Muted communities",
                icon = Icons.AutoMirrored.Rounded.VolumeOff,
                selected = false,
                onClick = {
                    onAction { onOpenMutedCommunities() }
                },
                trailing = {
                    Badge {
                        Text(activeCommunityMutes.size.toString())
                    }
                },
            )
        }

        CompactDrawerItem(
            label = "Show NSFW posts",
            icon = if (state.showNsfwPosts) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
            selected = false,
            isDestination = false,
            onClick = { state.updateShowNsfwPosts(!state.showNsfwPosts) },
            // ISSUE75_NSFW_SWITCH_STATE_IS_ANNOUNCED_V1: the row was a plain
            // button to TalkBack; it is a switch with its on/off state.
            modifier = Modifier.semantics {
                role = Role.Switch
                toggleableState = ToggleableState(state.showNsfwPosts)
            },
            trailing = {
                Switch(
                    checked = state.showNsfwPosts,
                    onCheckedChange = null,
                )
            },
        )

        if (state.offersRandomNsfwCommunity(app.subshell.prototype.ui.RandomNsfwCommunityPlacement.MENU) && onRandomNsfwCommunity != null) {
            CompactDrawerItem(
                label = "Random NSFW community",
                icon = Icons.Rounded.Shuffle,
                selected = false,
                isDestination = false,
                onClick = { onAction { onRandomNsfwCommunity() } },
            )
        }

        CompactDrawerItem(
            label = "Settings",
            icon = Icons.Rounded.Settings,
            selected = state.secondaryDestination == SecondaryDestination.SETTINGS,
            onClick = { onAction { state.openSecondary(SecondaryDestination.SETTINGS) } },
        )

        DrawerThemeSelector(
            state = state,
            modifier = Modifier.padding(
                start = 12.dp,
                top = 4.dp,
                end = 12.dp,
                bottom = 10.dp,
            ),
        )
    }
}

@Composable
private fun CompactDrawerItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDestination: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    // ISSUE220_DRAWER_LABELS_WRAP_AT_LARGE_TEXT_V1: two lines before the
    // ellipsis ("Show NSFW …" at 200 % text).
    val labelText: @Composable () -> Unit = {
        Text(text = label, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    // M3223_DRAWER_ROWS_ARE_MATERIAL_DRAWER_ITEMS_V1 (#223, owner: Material
    // standard): every destination is a NavigationDrawerItem (56 dp, pill,
    // secondaryContainer when selected). LOWUI7_CHOICE_ROWS_SAY_WHICH_IS_SELECTED_V1
    // (OCB-124): the item is a tab with its selected state.
    if (isDestination) {
        NavigationDrawerItem(
            label = labelText,
            selected = selected,
            onClick = onClick,
            modifier = modifier,
            icon = { Icon(imageVector = icon, contentDescription = null) },
            badge = trailing,
        )
        return
    }
    // An action (a switch, a one-off command) keeps its own role, drawn like a
    // drawer item.
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        shape = CircleShape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null)
            // The same text style the destinations get from NavigationDrawerItem.
            Box(modifier = Modifier.weight(1f)) { labelText() }
            trailing?.invoke()
        }
    }
}


private fun androidx.compose.foundation.lazy.LazyListScope.drawerCommunityGroups(
    state: SubshellState,
    communities: List<Community>,
    onAction: (() -> Unit) -> Unit,
    onEditCommunityMute: (String) -> Unit,
) {
    val favorites = state.favoriteCommunities(communities)
        .filterNot { community -> state.isCommunityHidden(community.name) }
    val recent = if (state.drawerRecentCommunities) {
        state.recentCommunities(candidates = communities)
            .filterNot { community -> state.isCommunityFavorite(community.name) }
            .filterNot { community -> state.isCommunityHidden(community.name) }
            .take(RECENT_COMMUNITY_DRAWER_LIMIT)
    } else {
        emptyList()
    }
    val recentNames = recent
        .map { community -> community.name.lowercase() }
        .toSet()
    val otherCommunities = communities.filterNot { community ->
        state.isCommunityFavorite(community.name) ||
            state.isCommunityHidden(community.name) ||
            community.name.lowercase() in recentNames
    }

    if (favorites.isNotEmpty()) {
        item("drawer-favorite-communities-title") {
            DrawerCommunitySubsectionLabel("Favorites")
        }
        drawerCommunityItems(
            state = state,
            communities = favorites,
            onAction = onAction,
            onEditCommunityMute = onEditCommunityMute,
            keyPrefix = "drawer-favorite-community",
        )
    }

    if (recent.isNotEmpty()) {
        item("drawer-recent-communities-title") {
            DrawerCommunitySubsectionLabel("Recent")
        }
        drawerCommunityItems(
            state = state,
            communities = recent,
            onAction = onAction,
            onEditCommunityMute = onEditCommunityMute,
            keyPrefix = "drawer-recent-community",
        )
    }

    if (otherCommunities.isNotEmpty()) {
        if (favorites.isNotEmpty() || recent.isNotEmpty()) {
            item("drawer-other-communities-title") {
                DrawerCommunitySubsectionLabel("Other communities")
            }
        }
        drawerCommunityItems(
            state = state,
            communities = otherCommunities,
            onAction = onAction,
            onEditCommunityMute = onEditCommunityMute,
        )
    }

    if (
        favorites.isEmpty() &&
        recent.isEmpty() &&
        otherCommunities.isEmpty() &&
        communities.isEmpty()
    ) {
        drawerCommunityItems(
            state = state,
            communities = emptyList(),
            onAction = onAction,
            onEditCommunityMute = onEditCommunityMute,
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.drawerCommunityItems(
    state: SubshellState,
    communities: List<Community>,
    onAction: (() -> Unit) -> Unit,
    onEditCommunityMute: (String) -> Unit,
    keyPrefix: String = "drawer-community",
) {
    if (communities.isEmpty()) {
        item("$keyPrefix-empty") {
            Text(
                text = "No cached joined communities",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 10.dp),
            )
        }
        return
    }

    items(
        items = communities,
        key = { community -> "$keyPrefix-${community.name.lowercase()}" },
    ) { community ->
        NavigationDrawerItem(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp),
            label = {
                Text(
                    text = "r/${community.name}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            selected = state.selectedSubreddit.equals(community.name, ignoreCase = true),
            icon = {
                CommunityAvatar(
                    name = community.name,
                    accent = Color(community.accent),
                    iconImageUri = community.iconImageUri,
                    modifier = Modifier.size(28.dp),
                )
            },
            badge = if (state.isCommunityMuted(community.name)) {
                {
                    IconButton(
                        // LOWUI7_TOUCH_TARGETS_ARE_48DP_V1 (OCB-301): the default
                        // IconButton keeps its 48dp target inside the 56dp row.
                        onClick = {
                            onAction { onEditCommunityMute(community.name) }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.VolumeOff,
                            contentDescription =
                                "Mute settings for r/${community.name}",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            } else {
                null
            },
            onClick = { onAction { state.openSubreddit(community.name) } },
        )
    }
}

@Composable
private fun DrawerCommunitySubsectionLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = 28.dp,
            end = 16.dp,
            top = 5.dp,
            bottom = 1.dp,
        ),
    )
}

@Composable
private fun DrawerSectionLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        // M3223_DRAWER_ROWS_ARE_MATERIAL_DRAWER_ITEMS_V1: Material's section
        // headline colour, not the accent.
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = 16.dp,
            end = 16.dp,
            top = 6.dp,
            bottom = 3.dp,
        ),
    )
}

@Composable
private fun DrawerSectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DrawerThemeSelector(
    state: SubshellState,
    modifier: Modifier = Modifier,
) {
    val modes = drawerThemeChoices

    SingleChoiceSegmentedButtonRow(
        modifier = modifier.fillMaxWidth(),
    ) {
        modes.forEachIndexed { index, mode ->
            SegmentedButton(
                modifier = Modifier.weight(1f),
                selected = state.themeChoice == mode,
                onClick = { state.updateThemeChoice(mode) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = modes.size,
                ),
                icon = {},
                label = {
                    // ISSUE31_DRAWER_THEME_LABELS_ONE_LINE_V1: one line, shrinks instead of breaking.
                    OneLineAutoSizeLabel(
                        text = mode.shortLabel,
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
            )
        }
    }
}
