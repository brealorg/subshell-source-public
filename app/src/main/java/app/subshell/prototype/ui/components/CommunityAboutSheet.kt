package app.subshell.prototype.ui.components

import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.subshell.prototype.data.account.RedditCommunityInfoSection
import app.subshell.prototype.data.account.RedditCommunityInfoUiState
import app.subshell.prototype.data.account.RedditCommunityWikiUiState
import app.subshell.prototype.model.CommunityModerator
import app.subshell.prototype.model.UserFixture
import app.subshell.prototype.ui.error.ErrorActionKind
import app.subshell.prototype.ui.error.redditCommunityInfoErrorPresentation
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

// COMMUNITYINFO01A3_COMMUNITY_ABOUT_SHEET_V1
// COMMUNITYINFO01A32_TABBED_COMPACT_ABOUT_SHEET_V1
// COMMUNITYINFO01A33_STRUCTURED_ABOUT_NO_COUNTS_V1
// COMMUNITYINFO01A34_DETAILS_ACCORDION_AFFORDANCE_V1
// COMMUNITYINFO01A35_EXISTING_UI_DETAILS_AFFORDANCE_V1
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
)
@Composable
fun CommunityAboutSheet(
    communityLabel: String,
    communityName: String,
    accountStableId: String,
    authenticated: Boolean,
    fallbackDescription: String,
    uiState: RedditCommunityInfoUiState,
    wikiUiState: RedditCommunityWikiUiState,
    onLoadWikiIndex: (String, String, Boolean) -> Unit,
    onLoadWikiPage: (String, String, String, Boolean) -> Unit,
    onAuthorizeWiki: (String, String) -> Unit,
    moderatorIdentity: (String) -> UserFixture,
    onEnsureModeratorIdentity: suspend (String) -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onOpenModerator: (String) -> Unit,
    linkedWikiPage: String? = null,
    onLinkedWikiPageShown: () -> Unit = {},
) {
    var selectedTab by rememberSaveable(communityName) {
        mutableStateOf(COMMUNITY_ABOUT_TAB)
    }
    var expandedCommunityDetails by rememberSaveable(communityName) {
        mutableStateOf(false)
    }
    var expandedRuleKey by rememberSaveable(communityName) {
        mutableStateOf<String?>(null)
    }
    var selectedWikiPagePath by rememberSaveable(communityName) {
        mutableStateOf<String?>(null)
    }
    // WIKI01A2R1_DEDICATED_WIKI_WORKSPACE_V1
    var wikiWorkspaceVisible by rememberSaveable(communityName) {
        mutableStateOf(false)
    }
    var showWikiConfigurationPages by rememberSaveable(communityName) {
        mutableStateOf(false)
    }
    // REDDITURL06_WIKI_PAGE_LINK_V1: "" opens the wiki's page list.
    LaunchedEffect(communityName, linkedWikiPage) {
        val page = linkedWikiPage ?: return@LaunchedEffect
        showWikiConfigurationPages = false
        selectedWikiPagePath = page.takeIf(String::isNotEmpty)
        wikiWorkspaceVisible = true
        onLinkedWikiPageShown()
    }

    // WIKI01A2R3_SHEET_HIDDEN_VETO_V1
    val wikiWorkspaceVisibleForSheet =
        rememberUpdatedState(wikiWorkspaceVisible)
    val sheetDismissGate = remember {
        { target: SheetValue ->
            communityAboutSheetMayMoveTo(target, wikiWorkspaceVisibleForSheet.value)
        }
    }
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        confirmValueChange = sheetDismissGate,
    )

    val matches = authenticated &&
        uiState.matches(accountStableId, communityName)
    val snapshot = uiState.snapshot.takeIf { matches }
    val unavailableSections = if (matches) {
        uiState.unavailableSections
    } else {
        emptySet()
    }
    val hasPartialFailures =
        snapshot != null && unavailableSections.isNotEmpty()
    val loading = authenticated && (!matches || uiState.loading)
    val failureKind = uiState.failureKind.takeIf { matches }
    val wikiMatches = authenticated &&
        wikiUiState.matches(accountStableId, communityName)

    // WIKI01A2_LAZY_WIKI_LOAD_V1
    // WIKI01A2R1_DEDICATED_WIKI_WORKSPACE_V1
    LaunchedEffect(
        wikiWorkspaceVisible,
        selectedWikiPagePath,
        authenticated,
        accountStableId,
        communityName,
    ) {
        if (authenticated && wikiWorkspaceVisible) {
            val pagePath = selectedWikiPagePath
            if (pagePath == null) {
                onLoadWikiIndex(
                    accountStableId,
                    communityName,
                    false,
                )
            } else {
                onLoadWikiPage(
                    accountStableId,
                    communityName,
                    pagePath,
                    false,
                )
            }
        }
    }

    // WIKI01A2R2_BACK_LADDER_V1
    val applyWikiBackStep: (WikiBackStep) -> Unit = { step ->
        when (step) {
            WikiBackStep.CLOSE_PAGE -> selectedWikiPagePath = null
            WikiBackStep.CLOSE_WORKSPACE -> {
                showWikiConfigurationPages = false
                wikiWorkspaceVisible = false
            }
            WikiBackStep.DISMISS_SHEET -> onDismiss()
        }
    }
    val navigateWikiBack: () -> Unit = {
        applyWikiBackStep(wikiBackStep(selectedWikiPagePath, workspaceVisible = true))
    }

    BackHandler(enabled = wikiWorkspaceVisible) {
        navigateWikiBack()
    }

    val moderatorIdentityHydrationGate = remember {
        Semaphore(MODERATOR_IDENTITY_CONCURRENCY)
    }

    ModalBottomSheet(
        // WIKI01A2R3_WINDOW_BACK_ROUTING_V1
        onDismissRequest = {
            applyWikiBackStep(wikiBackStep(selectedWikiPagePath, wikiWorkspaceVisible))
        },
        sheetState = sheetState,
        properties = ModalBottomSheetProperties(
            // Keep the popup window's Android Back callback registered.
            shouldDismissOnBackPress = true,
        ),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("about-title") {
                if (wikiWorkspaceVisible) {
                    // WIKI01A2R2_EXPRESSIVE_WORKSPACE_HEADER_V1
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = navigateWikiBack) {
                            Icon(
                                imageVector =
                                    Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription =
                                    if (selectedWikiPagePath == null) {
                                        "Back to Community info"
                                    } else {
                                        "Back to Wiki"
                                    },
                            )
                        }
                        Column {
                            Text(
                                text = "Wiki",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = communityLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    Text(
                        text = "About $communityLabel",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            }

            if (authenticated && !wikiWorkspaceVisible) {
                stickyHeader("about-tabs") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        PrimaryTabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent) {
                            CommunityAboutTab(
                                selected = selectedTab == COMMUNITY_ABOUT_TAB,
                                title = "About",
                                onClick = {
                                    selectedTab = COMMUNITY_ABOUT_TAB
                                },
                            )
                            CommunityAboutTab(
                                selected = selectedTab == COMMUNITY_RULES_TAB,
                                title = "Rules",
                                onClick = {
                                    selectedTab = COMMUNITY_RULES_TAB
                                },
                            )
                            CommunityAboutTab(
                                selected =
                                    selectedTab == COMMUNITY_MODERATORS_TAB,
                                title = "Moderators",
                                onClick = {
                                    selectedTab = COMMUNITY_MODERATORS_TAB
                                },
                            )
                        }
                    }
                }
            }

            if (!authenticated) {
                item("local-description") {
                    CommunityAboutOverview(
                        publicDescription = fallbackDescription,
                        detailsMarkdown = null,
                        detailsExpanded = false,
                        onDetailsExpandedChange = {},
                        emptyCopy =
                            "Connect a Reddit account to load community details.",
                    )
                }
            } else {
                if (
                    !wikiWorkspaceVisible &&
                    loading &&
                    snapshot == null
                ) {
                    item("about-loading") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            InlineProgressIndicator()
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Loading community info…",
                                color =
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (
                    !wikiWorkspaceVisible &&
                    failureKind != null
                ) {
                    item("about-failure") {
                        ErrorNotice(
                            presentation = redditCommunityInfoErrorPresentation(
                                failureKind = failureKind,
                                details = uiState.failureDetails,
                                retryable = uiState.failureRetryable,
                                showingPartialContent = snapshot != null,
                            ),
                            diagnosticScopeKey = "community_info",
                            onAction = { action ->
                                if (action == ErrorActionKind.RETRY) {
                                    onRetry()
                                }
                            },
                        )
                    }
                }

                if (
                    !wikiWorkspaceVisible &&
                    selectedTab == COMMUNITY_ABOUT_TAB
                ) {
                    item("about-wiki-entry") {
                        // WIKI01A2R2_EXPRESSIVE_WIKI_ENTRY_V1
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            ListItem(
                                content = {
                                    Text(
                                        text = "Wiki",
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                },
                                supportingContent = {
                                    Text("Community documentation")
                                },
                                leadingContent = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                                        contentDescription = null,
                                    )
                                },
                                trailingContent = {
                                    Icon(
                                        imageVector = Icons.Rounded.ChevronRight,
                                        contentDescription = "Open Wiki",
                                    )
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = Color.Transparent,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedWikiPagePath = null
                                        showWikiConfigurationPages = false
                                        wikiWorkspaceVisible = true
                                    },
                            )
                        }
                    }
                }

                if (wikiWorkspaceVisible) {
                    communityWikiItems(
                        state = wikiUiState,
                        matches = wikiMatches,
                        selectedPagePath = selectedWikiPagePath,
                        showConfigurationPages =
                            showWikiConfigurationPages,
                        onSelectedPagePathChange = {
                            selectedWikiPagePath = it
                        },
                        onShowConfigurationPagesChange = {
                            showWikiConfigurationPages = it
                        },
                        onAuthorize = {
                            onAuthorizeWiki(
                                accountStableId,
                                communityName,
                            )
                        },
                        onRetryIndex = {
                            onLoadWikiIndex(
                                accountStableId,
                                communityName,
                                true,
                            )
                        },
                        onRetryPage = { pagePath ->
                            onLoadWikiPage(
                                accountStableId,
                                communityName,
                                pagePath,
                                true,
                            )
                        },
                    )
                } else if (snapshot != null) {
                    when (selectedTab) {
                        COMMUNITY_ABOUT_TAB -> {
                            item("about-sidebar") {
                                val sidebarUnavailable =
                                    hasPartialFailures &&
                                        RedditCommunityInfoSection.SIDEBAR in
                                        unavailableSections
                                Column(
                                    verticalArrangement =
                                        Arrangement.spacedBy(12.dp),
                                ) {
                                    CommunityAboutOverview(
                                        publicDescription = fallbackDescription,
                                        detailsMarkdown = snapshot.sidebarMarkdown
                                            ?.takeIf(String::isNotBlank)
                                            ?.takeUnless {
                                                sidebarUnavailable
                                            },
                                        detailsExpanded =
                                            expandedCommunityDetails,
                                        onDetailsExpandedChange = {
                                            expandedCommunityDetails = it
                                        },
                                        emptyCopy =
                                            "No community description is published.",
                                    )
                                    if (sidebarUnavailable) {
                                        CommunitySectionUnavailable(
                                            label =
                                                "Additional community information",
                                            onRetry = onRetry,
                                        )
                                    }
                                }
                            }
                        }

                        COMMUNITY_RULES_TAB -> {
                            if (
                                RedditCommunityInfoSection.RULES in
                                unavailableSections
                            ) {
                                item("rules-unavailable") {
                                    CommunitySectionUnavailable(
                                        label = "Community rules",
                                        onRetry = onRetry,
                                    )
                                }
                            } else if (snapshot.rules.isEmpty()) {
                                item("rules-empty") {
                                    CommunitySectionEmpty(
                                        "No community rules were returned by Reddit.",
                                    )
                                }
                            } else {
                                itemsIndexed(
                                    items = snapshot.rules,
                                    // LOWUI1_LAZY_KEYS_NAMESPACED_V1 (OCB-128): a rule or
                                    // moderator name cannot collide with the fixed items.
                                    key = { _, rule ->
                                        "rule:" + rule.shortName.lowercase()
                                    },
                                ) { index, rule ->
                                    val ruleKey =
                                        rule.shortName.lowercase()
                                    val hasDescription =
                                        !rule.description.isNullOrBlank()
                                    val expanded =
                                        hasDescription &&
                                            expandedRuleKey == ruleKey

                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        ListItem(
                                            content = {
                                                Text(
                                                    text = rule.shortName,
                                                    fontWeight =
                                                        FontWeight.SemiBold,
                                                    maxLines = 2,
                                                    overflow =
                                                        TextOverflow.Ellipsis,
                                                )
                                            },
                                            leadingContent = {
                                                Text(
                                                    text = "${index + 1}.",
                                                    style =
                                                        MaterialTheme.typography
                                                            .labelLarge,
                                                    color =
                                                        MaterialTheme
                                                            .colorScheme
                                                            .onSurfaceVariant,
                                                )
                                            },
                                            trailingContent =
                                                if (hasDescription) {
                                                    {
                                                        Icon(
                                                            imageVector =
                                                                if (expanded) {
                                                                    Icons.Rounded
                                                                        .ExpandLess
                                                                } else {
                                                                    Icons.Rounded
                                                                        .ExpandMore
                                                                },
                                                            contentDescription =
                                                                if (expanded) {
                                                                    "Collapse rule"
                                                                } else {
                                                                    "Expand rule"
                                                                },
                                                        )
                                                    }
                                                } else {
                                                    null
                                                },
                                            colors =
                                                ListItemDefaults.colors(
                                                    containerColor =
                                                        if (expanded) {
                                                            MaterialTheme
                                                                .colorScheme
                                                                .surfaceContainerLow
                                                        } else {
                                                            MaterialTheme
                                                                .colorScheme
                                                                .surface
                                                        },
                                                ),
                                            modifier = Modifier.clickable(
                                                enabled = hasDescription,
                                            ) {
                                                expandedRuleKey =
                                                    if (expanded) {
                                                        null
                                                    } else {
                                                        ruleKey
                                                    }
                                            },
                                        )
                                        AnimatedVisibility(
                                            visible = expanded,
                                        ) {
                                            MarkdownText(
                                                text =
                                                    requireNotNull(
                                                        rule.description,
                                                    ),
                                                modifier = Modifier.padding(
                                                    start = 56.dp,
                                                    end = 16.dp,
                                                    bottom = 14.dp,
                                                ),
                                                color =
                                                    MaterialTheme.colorScheme
                                                        .onSurfaceVariant,
                                            )
                                        }
                                        HorizontalDivider()
                                    }
                                }
                            }
                        }

                        COMMUNITY_MODERATORS_TAB -> {
                            if (
                                RedditCommunityInfoSection.MODERATORS in
                                unavailableSections
                            ) {
                                item("moderators-unavailable") {
                                    CommunitySectionUnavailable(
                                        label = "Moderator list",
                                        onRetry = onRetry,
                                    )
                                }
                            } else if (snapshot.moderators.isEmpty()) {
                                item("moderators-empty") {
                                    CommunitySectionEmpty(
                                        "No moderators were returned by Reddit.",
                                    )
                                }
                            } else {
                                items(
                                    items = snapshot.moderators,
                                    key = { moderator ->
                                        "moderator:" + moderator.username.lowercase()
                                    },
                                ) { moderator ->
                                    CompactModeratorUserResult(
                                        moderator = moderator,
                                        identity =
                                            moderatorIdentity(
                                                moderator.username,
                                            ),
                                        onEnsureIdentity = {
                                            moderatorIdentityHydrationGate
                                                .withPermit {
                                                    onEnsureModeratorIdentity(
                                                        moderator.username,
                                                    )
                                                }
                                        },
                                        onClick = {
                                            onOpenModerator(moderator.username)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunityAboutTab(
    selected: Boolean,
    title: String,
    onClick: () -> Unit,
) {
    Tab(
        selected = selected,
        onClick = onClick,
        selectedContentColor = MaterialTheme.colorScheme.primary,
        unselectedContentColor =
            MaterialTheme.colorScheme.onSurfaceVariant,
        text = {
            Text(
                text = title,
                maxLines = 1,
                style = MaterialTheme.typography.labelLarge,
                fontWeight =
                    if (selected) FontWeight.SemiBold
                    else FontWeight.Normal,
            )
        },
    )
}

// COMMUNITYINFO01A33_STRUCTURED_ABOUT_DETAILS_V1
// COMMUNITYINFO01A35_EXISTING_UI_DETAILS_DISCLOSURE_V1
@Composable
private fun CommunityAboutOverview(
    publicDescription: String,
    detailsMarkdown: String?,
    detailsExpanded: Boolean,
    onDetailsExpandedChange: (Boolean) -> Unit,
    emptyCopy: String,
) {
    val summary = publicDescription.trim()
    val details = detailsMarkdown
        ?.trim()
        .orEmpty()
        .takeUnless { candidate ->
            candidate.isBlank() || candidate == summary
        }

    if (summary.isBlank() && details == null) {
        CommunitySectionEmpty(emptyCopy)
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (summary.isNotBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Text(
                    text = summary,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (details != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column {
                    ListItem(
                        content = {
                            Text(
                                text = "Community details",
                                fontWeight = FontWeight.SemiBold,
                            )
                        },
                        supportingContent = {
                            Text(
                                text = if (detailsExpanded) {
                                    "Hide links, resources and additional information."
                                } else {
                                    "Show links, resources and additional information."
                                },
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                            )
                        },
                        trailingContent = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = if (detailsExpanded) "Hide" else "Show",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    imageVector = if (detailsExpanded) {
                                        Icons.Rounded.ExpandLess
                                    } else {
                                        Icons.Rounded.ExpandMore
                                    },
                                    contentDescription = if (detailsExpanded) {
                                        "Collapse community details"
                                    } else {
                                        "Expand community details"
                                    },
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                        ),
                        modifier = Modifier.clickable {
                            onDetailsExpandedChange(!detailsExpanded)
                        },
                    )
                    AnimatedVisibility(visible = detailsExpanded) {
                        Column {
                            HorizontalDivider()
                            MarkdownText(
                                text = details,
                                modifier = Modifier.padding(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 12.dp,
                                    bottom = 16.dp,
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

// COMMUNITYINFO01A32_VISIBLE_MODERATOR_IDENTITY_HYDRATION_V1
@Composable
private fun CompactModeratorUserResult(
    moderator: CommunityModerator,
    identity: UserFixture,
    onEnsureIdentity: suspend () -> Unit,
    onClick: () -> Unit,
) {
    LaunchedEffect(moderator.username) {
        onEnsureIdentity()
    }

    val profileDescription = identity.description
        .trim()
        .takeUnless {
            it.isEmpty() ||
                it == "Public profile preview."
        }
        ?: "Moderator"
    val permissionSummary = moderator.permissions
        .asSequence()
        .filterNot { permission ->
            permission.equals("all", ignoreCase = true)
        }
        .take(MAX_VISIBLE_MOD_PERMISSIONS)
        .joinToString(" · ")

    ListCard(
        headline = "u/${moderator.username}",
        supporting = profileDescription,
        onClick = onClick,
        leading = {
            CommunityAvatar(
                name = moderator.username,
                accent = accentFor(moderator.username),
                modifier = Modifier.size(40.dp),
                iconImageUri = identity.avatarImageUri,
            )
        },
        trailing = if (permissionSummary.isNotBlank()) {
            { Text(permissionSummary, style = MaterialTheme.typography.labelMedium, maxLines = 1) }
        } else {
            null
        },
    )
}

@Composable
private fun CommunitySectionUnavailable(
    label: String,
    onRetry: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$label unavailable.",
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onRetry) {
            Icon(
                Icons.Rounded.Refresh,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text("Retry")
        }
    }
}

@Composable
private fun CommunitySectionEmpty(
    text: String,
) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private const val COMMUNITY_ABOUT_TAB = 0
private const val COMMUNITY_RULES_TAB = 1
private const val COMMUNITY_MODERATORS_TAB = 2
private const val MAX_VISIBLE_MOD_PERMISSIONS = 3
private const val MODERATOR_IDENTITY_CONCURRENCY = 2

// STEP3_WIKI_SHEET_DECISIONS_ARE_FUNCTIONS_V1: the sheet's two decisions,
// moved out unchanged so a test can call them (Robolectric cannot drive the
// sheet window). While the wiki workspace is open the sheet refuses to hide
// (WIKI01A2R3_SHEET_HIDDEN_VETO_V1); Back steps page -> wiki -> community
// info, and only then closes the sheet (WIKI01A2R2_BACK_LADDER_V1,
// WIKI01A2R3_WINDOW_BACK_ROUTING_V1).
@OptIn(ExperimentalMaterial3Api::class)
internal fun communityAboutSheetMayMoveTo(target: SheetValue, wikiWorkspaceVisible: Boolean): Boolean =
    target != SheetValue.Hidden || !wikiWorkspaceVisible

internal enum class WikiBackStep { CLOSE_PAGE, CLOSE_WORKSPACE, DISMISS_SHEET }

internal fun wikiBackStep(selectedPage: String?, workspaceVisible: Boolean): WikiBackStep = when {
    !workspaceVisible -> WikiBackStep.DISMISS_SHEET
    selectedPage != null -> WikiBackStep.CLOSE_PAGE
    else -> WikiBackStep.CLOSE_WORKSPACE
}
