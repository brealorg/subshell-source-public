package app.subshell.prototype.ui.screens

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.semantics.liveRegion
import app.subshell.prototype.ui.ScreenMargin
import app.subshell.prototype.ui.components.rememberAccessibleAutoHideMillis
import app.subshell.prototype.ui.error.LocalViewportErrorHostState
import app.subshell.prototype.ui.app.presentRedditLinkUnavailable
import app.subshell.prototype.ui.app.RedditLinkUnavailableReason
import app.subshell.prototype.ui.components.NameWithUserTag
import app.subshell.prototype.ui.components.OneLineAutoSizeLabel
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.ui.layout.WindowInsetsRulers
import androidx.compose.foundation.layout.fitInside
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.subshell.prototype.data.account.REDDIT_MODERATION_AGGREGATE_QUEUE
import app.subshell.prototype.data.account.RedditInboxUiState
import app.subshell.prototype.data.account.RedditModerationUiState
import app.subshell.prototype.data.account.RedditModmailListMode
import app.subshell.prototype.data.account.RedditModmailTriageAction
import app.subshell.prototype.data.account.RedditModmailUiState
import app.subshell.prototype.data.account.RedditModeratorActionRequest
import app.subshell.prototype.data.account.RedditModeratorActionUiState
import app.subshell.prototype.data.account.RedditModeratorQueueItem
import app.subshell.prototype.data.account.RedditRemovalReasonUiState
import app.subshell.prototype.data.account.canonicalModerationTarget
import app.subshell.prototype.inbox.InboxActivitySection
import app.subshell.prototype.inbox.InboxTarget
import app.subshell.prototype.inbox.ModmailNotificationTarget
import app.subshell.prototype.inbox.canonicalInboxTarget
import app.subshell.prototype.inbox.hiddenByActivityLifecycle
import app.subshell.prototype.inbox.inboxActivitySection
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.model.Post
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.components.ErrorNotice
import app.subshell.prototype.ui.components.SavedResponseActions
import app.subshell.prototype.ui.components.InboxModerationActionStatus
import app.subshell.prototype.ui.components.InboxModeratorQueueFailure
import app.subshell.prototype.ui.components.InboxModeratorQueueItemCard
import app.subshell.prototype.ui.components.ModeratorReportLookupButton
import app.subshell.prototype.ui.components.InboxModeratorRemovalDialog
import app.subshell.prototype.ui.components.InboxModmailComposer
import app.subshell.prototype.ui.components.InboxModmailConversationRow
import app.subshell.prototype.ui.components.InboxModmailFailure
import app.subshell.prototype.ui.components.InboxModmailMessageRow
import app.subshell.prototype.ui.components.InboxModmailQueueFilters
import app.subshell.prototype.ui.components.InboxModmailThreadHeader
import app.subshell.prototype.ui.components.modmailEmptySupportingText
import app.subshell.prototype.ui.components.modmailEmptyTitle
import app.subshell.prototype.ui.components.RemoteIdentityImage
import app.subshell.prototype.ui.components.RefreshableLazyColumn
import app.subshell.prototype.ui.error.ErrorActionKind
import app.subshell.prototype.ui.error.ReportViewportError
import app.subshell.prototype.ui.error.redditInboxErrorPresentation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import app.subshell.prototype.ui.components.InlineProgressIndicator
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.LeadingIconTab
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import app.subshell.prototype.ui.components.rememberLabelsFitSlots

// SUBSHELL_INBOXM3A1_UNIFIED_M3_EXPRESSIVE_WORKSPACE_PREVIEW_V1
// SUBSHELL_INBOXM3A1R1_VISUAL_REFINEMENT_PREVIEW_V1
// SUBSHELL_INBOXM3A2_BADGE_OFFSET_PREVIEW_V1
// SUBSHELL_INBOXM3A2R2_BADGE_POLISH_PREVIEW_V1
private enum class InboxFilter(val label: String) {
    ALL("All"),
    UNREAD("Unread"),
}

// MODMAIL01A1_QUEUE_MODMAIL_SECONDARY_NAV_V1
enum class InboxModSection(val label: String) {
    QUEUE("Queue"),
    MODMAIL("Modmail"),
}

// MOD01A22_CONDITIONAL_INBOX_MOD_TAB_V1
enum class InboxPrimaryTab(val label: String) {
    ACTIVITY("Activity"),
    MESSAGES("Messages"),
    MOD("Mod"),
}

internal fun inboxPrimaryTabs(showModeration: Boolean): List<InboxPrimaryTab> =
    buildList {
        add(InboxPrimaryTab.ACTIVITY)
        add(InboxPrimaryTab.MESSAGES)
        if (showModeration) add(InboxPrimaryTab.MOD)
    }

data class InboxContextualSelection(
    val selectedCount: Int,
    val canMarkRead: Boolean,
    val allVisibleSelected: Boolean,
    val onClose: () -> Unit,
    val onMarkRead: () -> Unit,
    val onDismiss: () -> Unit,
    val onSelectAll: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    modifier: Modifier = Modifier,
    state: SubshellState,
    redditInboxState: RedditInboxUiState = RedditInboxUiState(),
    onLoadRedditInbox: (String, Boolean) -> Unit = { _, _ -> },
    onAppendRedditInbox: (String) -> Unit = {},
    onMarkRedditInboxRead: (String, String) -> Unit = { _, _ -> },
    onResolveRedditInboxTarget: (String, String, (Post?) -> Unit) -> Unit =
        { _, _, onResolved -> onResolved(null) },
    onAuthorizeRedditInbox: (String) -> Unit = {},
    onCancelRedditInboxAuthorization: () -> Unit = {},
    redditModerationState: RedditModerationUiState = RedditModerationUiState(),
    onLoadRedditModQueue: (String, String, Boolean) -> Unit = { _, _, _ -> },
    onAppendRedditModQueue: (String, String) -> Unit = { _, _ -> },
    onResolveRedditModerationTarget: (String, String, (Post?) -> Unit) -> Unit =
        { _, _, onResolved -> onResolved(null) },
    moderationActionState: RedditModeratorActionUiState =
        RedditModeratorActionUiState(),
    onModerateRedditQueueItem: (RedditModeratorActionRequest) -> Unit = {},
    onRetryRedditModerationAction: () -> Unit = {},
    onAuthorizeRedditModerationAction: () -> Unit = {},
    removalReasonState: RedditRemovalReasonUiState = RedditRemovalReasonUiState(),
    onLoadRedditRemovalReasons: (String, String, Boolean) -> Unit = { _, _, _ -> },
    onAuthorizeRedditRemovalReasons: () -> Unit = {},
    redditModmailState: RedditModmailUiState = RedditModmailUiState(),
    onLoadRedditModmail: (String, Boolean) -> Unit = { _, _ -> },
    onAppendRedditModmail: (String) -> Unit = {},
    onSelectRedditModmailListMode: (String, RedditModmailListMode) -> Unit = { _, _ -> },
    onOpenRedditModmailConversation: (String, String) -> Unit = { _, _ -> },
    onSendRedditModmailMessage: (String, String, String, Boolean, Boolean) -> Unit =
        { _, _, _, _, _ -> },
    onSetRedditModmailArchived: (String, String, Boolean) -> Unit = { _, _, _ -> },
    onSetRedditModmailUnread: (String, String, Boolean) -> Unit = { _, _, _ -> },
    onVerifyRedditModmailReadState: (String, String) -> Unit = { _, _ -> },
    onApplyRedditModmailTriage: (String, String, RedditModmailTriageAction) -> Unit =
        { _, _, _ -> },
    onCloseRedditModmailConversation: () -> Unit = {},
    onAuthorizeRedditModmail: (String) -> Unit = {},
    modmailNotificationTarget: ModmailNotificationTarget? = null,
    onModmailNotificationConsumed: () -> Unit = {},
    onContextualSelectionChanged: (InboxContextualSelection?) -> Unit = {},
    // NEXT01A3A_CONTROLLED_INBOX_PRIMARY_TAB_V1
    selectedPrimaryTab: InboxPrimaryTab? = null,
    // NEXT01A3C_CONTROLLED_MOD_SECTION_V1
    controlledModSection: InboxModSection? = null,
    // NEXT01A3D_MODMAIL_FULLSCREEN_PRESENTATION_SWITCH_V1
    showSelectedModmailThread: Boolean = true,
    // LOWUX3_MODMAIL_THREAD_IN_A_PANE_HAS_NO_SYSTEM_BAR_INSETS_V1 (OCB-222):
    // as the list pane of a two-pane conversation/post, the thread is not the
    // whole screen and the system-bar insets left blank bands.
    modmailThreadFillsTheScreen: Boolean = true,
    onPrimaryTabChanged: (InboxPrimaryTab) -> Unit = {},
    onModSectionChanged: (InboxModSection) -> Unit = {},
    onOpenItemSelection: ((InboxItem, Post?, Boolean) -> Unit)? = null,
    onOpenModerationItemSelection: ((RedditModeratorQueueItem, Post) -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(
        start = ScreenMargin,
        top = 8.dp,
        end = ScreenMargin,
        bottom = 96.dp,
    ),
) {
    val authenticated = state.activeAccountIsAuthenticated
    val accountStableId = state.activeAccountId
    val liveStateMatches = authenticated && redditInboxState.matches(accountStableId)
    val liveItems = if (liveStateMatches) redditInboxState.items else emptyList()
    val sourceItems = if (authenticated) liveItems else state.inboxItems
    val locallySeenIds = if (authenticated) {
        if (liveStateMatches) redditInboxState.locallySeenIds else emptySet()
    } else {
        state.readInboxIds.toSet()
    }
    val tabUnreadCounts = inboxTabUnreadCounts(
        items = sourceItems,
        locallySeenIds = locallySeenIds,
    )
    val moderationStateMatches =
        authenticated && redditModerationState.matchesAccount(accountStableId)
    val showModerationTab = moderationStateMatches &&
        redditModerationState.moderatedCommunities.isNotEmpty()
    // LOWUI8_SAVED_MOD_TAB_WAITS_FOR_THE_DIRECTORY_V1 (OCB-219): until this
    // account's moderator directory has loaded, a requested Mod tab shows
    // Activity but is not written back, so a restore keeps the reader's tab.
    val moderationTabKnown = !authenticated || (
        moderationStateMatches &&
            redditModerationState.directoryLoaded &&
            redditModerationState.directoryFailureKind == null
        )
    val primaryTabs = inboxPrimaryTabs(showModerationTab)
    var selectedTabName by rememberSaveable(accountStableId) {
        mutableStateOf(InboxPrimaryTab.ACTIVITY.name)
    }
    val requestedTab = selectedPrimaryTab ?: runCatching {
        InboxPrimaryTab.valueOf(selectedTabName)
    }.getOrDefault(InboxPrimaryTab.ACTIVITY)
    val selectedTab = requestedTab.takeIf(primaryTabs::contains)
        ?: InboxPrimaryTab.ACTIVITY

    var selectedModSectionName by rememberSaveable(accountStableId) {
        mutableStateOf(InboxModSection.QUEUE.name)
    }
    val selectedModSection = controlledModSection ?: runCatching {
        InboxModSection.valueOf(selectedModSectionName)
    }.getOrDefault(InboxModSection.QUEUE)

    val modmailNotificationMatchesAccount =
        authenticated &&
            modmailNotificationTarget?.accountStableId == accountStableId

    // MODMAIL06_NOTIFICATION_EXACT_THREAD_FOCUS_V1
    LaunchedEffect(
        modmailNotificationTarget?.eventKey,
        modmailNotificationMatchesAccount,
        showModerationTab,
    ) {
        if (modmailNotificationMatchesAccount && showModerationTab) {
            selectedTabName = InboxPrimaryTab.MOD.name
            onPrimaryTabChanged(InboxPrimaryTab.MOD)
            selectedModSectionName = InboxModSection.MODMAIL.name
            onModSectionChanged(InboxModSection.MODMAIL)
        }
    }

    // ISSUE105_NOTIFICATION_TARGET_OPENS_ONCE_V1: the target was consumed only
    // once its thread had loaded, and re-opened whenever another conversation
    // was selected; a target that failed to load took over every later tap and
    // Back until the app restarted. It is opened once; when that attempt ends
    // (loaded or failed), or the reader picks or closes another conversation
    // first, the target is done.
    var modmailNotificationOpenedEventKey by remember(accountStableId) {
        mutableStateOf<String?>(null)
    }
    LaunchedEffect(
        modmailNotificationTarget?.eventKey,
        selectedTab,
        selectedModSection,
        redditModmailState.loaded,
        redditModmailState.loading,
        redditModmailState.threadLoading,
        redditModmailState.selectedConversationId,
        redditModmailState.contentRevision,
        accountStableId,
    ) {
        val target = modmailNotificationTarget ?: return@LaunchedEffect
        if (
            !modmailNotificationMatchesAccount ||
            selectedTab != InboxPrimaryTab.MOD ||
            selectedModSection != InboxModSection.MODMAIL ||
            !redditModmailState.matches(accountStableId) ||
            redditModmailState.loading ||
            redditModmailState.authorizationRequired ||
            !redditModmailState.loaded
        ) {
            return@LaunchedEffect
        }
        if (redditModmailState.selectedConversationId != target.conversationId) {
            if (modmailNotificationOpenedEventKey == target.eventKey) {
                onModmailNotificationConsumed()
                return@LaunchedEffect
            }
            modmailNotificationOpenedEventKey = target.eventKey
            onOpenRedditModmailConversation(accountStableId, target.conversationId)
            return@LaunchedEffect
        }
        modmailNotificationOpenedEventKey = target.eventKey
        if (redditModmailState.threadLoading) return@LaunchedEffect
        onModmailNotificationConsumed()
    }

    LaunchedEffect(authenticated, accountStableId) {
        if (authenticated) {
            onLoadRedditInbox(accountStableId, false)
        }
    }
    LaunchedEffect(showModerationTab, requestedTab, moderationTabKnown) {
        if (requestedTab == InboxPrimaryTab.MOD && !showModerationTab && moderationTabKnown) {
            selectedTabName = InboxPrimaryTab.ACTIVITY.name
        }
    }
    LaunchedEffect(selectedTab, moderationTabKnown) {
        if (selectedTab == requestedTab || moderationTabKnown) {
            onPrimaryTabChanged(selectedTab)
        }
    }
    LaunchedEffect(
        selectedTab,
        selectedModSection,
        showModerationTab,
        accountStableId,
    ) {
        if (
            selectedTab == InboxPrimaryTab.MOD &&
            showModerationTab &&
            authenticated
        ) {
            when (selectedModSection) {
                InboxModSection.QUEUE -> onLoadRedditModQueue(
                    accountStableId,
                    REDDIT_MODERATION_AGGREGATE_QUEUE,
                    false,
                )
                InboxModSection.MODMAIL -> onLoadRedditModmail(
                    accountStableId,
                    false,
                )
            }
        }
    }

    val activityListState = rememberLazyListState()
    val messagesListState = rememberLazyListState()
    val moderationListState = rememberLazyListState()
    val modmailListState = rememberLazyListState()
    val modmailThreadListState = rememberLazyListState()
    val listState = when (selectedTab) {
        InboxPrimaryTab.ACTIVITY -> activityListState
        InboxPrimaryTab.MESSAGES -> messagesListState
        InboxPrimaryTab.MOD -> when (selectedModSection) {
            InboxModSection.QUEUE -> moderationListState
            InboxModSection.MODMAIL -> modmailListState
        }
    }
    var filter by rememberSaveable(accountStableId) {
        mutableStateOf(InboxFilter.ALL)
    }
    var selectedActivityIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    // LOWUI6_INBOX_UNDO_BELONGS_TO_ITS_ACCOUNT_V1 (OCB-317): the undo offer is
    // kept per account (another account never undoes this one's clear),
    // survives a configuration change and goes away after INBOX_UNDO_VISIBLE_MS.
    var showUndoClear by rememberSaveable(accountStableId) { mutableStateOf(false) }
    var undoDismissedIdList by rememberSaveable(accountStableId) { mutableStateOf<List<String>>(ArrayList()) }
    // ISSUE218_INBOX_UNDO_IS_AN_M3_SNACKBAR_V1: the time follows the system's
    // "Time to take action" setting, as Material's snackbar host does.
    val undoVisibleMillis = rememberAccessibleAutoHideMillis(INBOX_UNDO_VISIBLE_MS)
    LaunchedEffect(accountStableId, showUndoClear, undoDismissedIdList, undoVisibleMillis) {
        if (showUndoClear || undoDismissedIdList.isNotEmpty()) {
            delay(undoVisibleMillis)
            showUndoClear = false
            undoDismissedIdList = ArrayList()
        }
    }
    var pendingModeratorRemoval by remember {
        mutableStateOf<RedditModeratorQueueItem?>(null)
    }
    LaunchedEffect(accountStableId, selectedTab, selectedModSection) {
        if (
            selectedTab != InboxPrimaryTab.MOD ||
            selectedModSection != InboxModSection.QUEUE
        ) {
            pendingModeratorRemoval = null
        }
        if (
            selectedTab != InboxPrimaryTab.MOD ||
            selectedModSection != InboxModSection.MODMAIL
        ) {
            onCloseRedditModmailConversation()
        }
    }

    @Suppress("UNUSED_VARIABLE")
    val lifecycleRevision = state.inboxActivityLifecycleRevision
    val dismissedIds = state.dismissedInboxActivityIds(accountStableId)
    val clearBoundary = state.inboxActivityClearBoundary(accountStableId)
    val visibleActivity = sourceItems.filter { item ->
        item.kind != ActivityKind.MESSAGE &&
            !item.hiddenByActivityLifecycle(dismissedIds, clearBoundary)
    }
    val clearedActivity = sourceItems.filter { item ->
        item.kind != ActivityKind.MESSAGE &&
            item.hiddenByActivityLifecycle(dismissedIds, clearBoundary)
    }
    val messages = sourceItems.filter { item -> item.kind == ActivityKind.MESSAGE }
    val tabItems = when (selectedTab) {
        InboxPrimaryTab.ACTIVITY -> visibleActivity
        InboxPrimaryTab.MESSAGES -> messages
        InboxPrimaryTab.MOD -> emptyList()
    }
    val inboxItems = tabItems.filter { item ->
        val read = !item.unread || item.id in locallySeenIds
        filter == InboxFilter.ALL || !read
    }
    val aggregateQueueMatches = authenticated &&
        redditModerationState.aggregateQueueMatches(accountStableId)
    val moderationItems = if (aggregateQueueMatches) {
        redditModerationState.queueItems
    } else {
        emptyList()
    }
    val moderationQueueLoading = selectedTab == InboxPrimaryTab.MOD &&
        (!aggregateQueueMatches || redditModerationState.queueLoading)
    val moderationQueueFailure = redditModerationState.queueFailureKind
        .takeIf { aggregateQueueMatches }
    val moderationQueueNextCursor = redditModerationState.queueNextCursor
        .takeIf { aggregateQueueMatches }


    val modmailStateMatches = authenticated && redditModmailState.matches(accountStableId)
    val modmailConversations = if (modmailStateMatches) {
        redditModmailState.conversations
    } else {
        emptyList()
    }
    val modmailThread = redditModmailState.thread.takeIf { modmailStateMatches }
    val modmailAuthorizationRequired =
        modmailStateMatches && redditModmailState.authorizationRequired
    val modmailFailure = redditModmailState.failureKind.takeIf { modmailStateMatches }
    val modmailNextCursor = redditModmailState.nextCursor.takeIf { modmailStateMatches }

    LaunchedEffect(
        authenticated,
        selectedTab,
        selectedModSection,
        modmailConversations.map { conversation -> conversation.communityName },
    ) {
        if (
            authenticated &&
            selectedTab == InboxPrimaryTab.MOD &&
            selectedModSection == InboxModSection.MODMAIL
        ) {
            state.ensureCommunityIdentities(
                modmailConversations.map { conversation -> conversation.communityName },
            )
        }
    }

    LaunchedEffect(
        authenticated,
        modmailThread?.conversation?.id,
        modmailThread?.messages?.map { message -> message.authorName },
    ) {
        val thread = modmailThread ?: return@LaunchedEffect
        if (!authenticated) return@LaunchedEffect
        state.ensureCommunityIdentity(thread.conversation.communityName)
        buildList {
            thread.conversation.participantName?.let { participant -> add(participant) }
            add(state.activeAccountUsername)
            thread.messages.forEach { message -> add(message.authorName) }
        }
            .map { username -> username.trim().removePrefix("u/") }
            .filter { username ->
                username.isNotBlank() &&
                    username != "[deleted]" &&
                    !username.equals("reddit", ignoreCase = true)
            }
            .distinctBy { username -> username.lowercase() }
            .forEach { username -> state.ensureUserIdentity(username) }
    }

    LaunchedEffect(
        authenticated,
        accountStableId,
        selectedTab,
        visibleActivity,
        activityListState,
    ) {
        if (!authenticated || selectedTab != InboxPrimaryTab.ACTIVITY) {
            return@LaunchedEffect
        }
        snapshotFlow {
            activityListState.firstVisibleItemIndex / INBOX_ACTIVITY_IDENTITY_PREFETCH_STEP
        }
            .distinctUntilChanged()
            .collect { bucket ->
                val approximateStart = (
                    bucket * INBOX_ACTIVITY_IDENTITY_PREFETCH_STEP -
                        INBOX_ACTIVITY_IDENTITY_PREFETCH_LOOKBEHIND
                    ).coerceAtLeast(0)
                visibleActivity
                    .drop(approximateStart)
                    .take(INBOX_ACTIVITY_IDENTITY_PREFETCH_WINDOW)
                    .mapNotNull(InboxItem::activityActorUsername)
                    .distinctBy { username -> username.lowercase() }
                    .forEach { username ->
                        state.ensureUserIdentity(username)
                    }
            }
    }

    // MESSAGES02A_MAIN_MESSAGES_IDENTITY_PREFETCH_V1
    LaunchedEffect(
        authenticated,
        accountStableId,
        selectedTab,
        messages,
        messagesListState,
    ) {
        if (!authenticated || selectedTab != InboxPrimaryTab.MESSAGES) {
            return@LaunchedEffect
        }
        snapshotFlow {
            messagesListState.firstVisibleItemIndex / INBOX_ACTIVITY_IDENTITY_PREFETCH_STEP
        }
            .distinctUntilChanged()
            .collect { bucket ->
                val approximateStart = (
                    bucket * INBOX_ACTIVITY_IDENTITY_PREFETCH_STEP -
                        INBOX_ACTIVITY_IDENTITY_PREFETCH_LOOKBEHIND
                    ).coerceAtLeast(0)
                messages
                    .drop(approximateStart)
                    .take(INBOX_ACTIVITY_IDENTITY_PREFETCH_WINDOW)
                    .mapNotNull(InboxItem::inboxMessageIdentityUsername)
                    .distinctBy { messageUsername -> messageUsername.lowercase() }
                    .forEach { messageUsername ->
                        state.ensureUserIdentity(messageUsername)
                    }
            }
    }

    // ISSUE106_TAB_MARK_READ_STAYS_IN_ITS_TAB_V1: an item seen here whose own
    // read never reached Reddit is still unread there; it is sent again.
    fun markInboxItemsRead(items: List<InboxItem>) {
        items
            .filter { item -> item.unread }
            .forEach { item ->
                if (authenticated) {
                    onMarkRedditInboxRead(accountStableId, item.id)
                } else {
                    state.markInboxRead(item.id)
                }
            }
    }

    // ISSUE106_TAB_MARK_READ_STAYS_IN_ITS_TAB_V1: a tab's "mark all read"
    // marks only that tab's loaded unread items. It used api/read_all_messages
    // when the other tab showed nothing unread, which also marked unloaded and
    // newly arrived private messages read on Reddit.
    fun markAllVisibleActivityRead() {
        if (tabUnreadCounts.activity <= 0) return
        markInboxItemsRead(visibleActivity)
    }

    fun markAllVisibleMessagesRead() {
        if (tabUnreadCounts.messages <= 0) return
        markInboxItemsRead(messages)
    }

    fun clearActivitySelection() {
        selectedActivityIds = emptySet()
    }

    fun toggleActivitySelection(itemId: String) {
        selectedActivityIds = if (itemId in selectedActivityIds) {
            selectedActivityIds - itemId
        } else {
            selectedActivityIds + itemId
        }
    }

    val selectionMode =
        selectedTab == InboxPrimaryTab.ACTIVITY && selectedActivityIds.isNotEmpty()
    val selectableActivityIds = inboxItems.map(InboxItem::id).toSet()
    val selectedActivityItems = visibleActivity.filter { item ->
        item.id in selectedActivityIds
    }
    val allVisibleSelected = selectableActivityIds.isNotEmpty() &&
        selectedActivityIds.containsAll(selectableActivityIds)

    fun markSelectedActivityRead() {
        markInboxItemsRead(selectedActivityItems)
        clearActivitySelection()
    }

    fun dismissSelectedActivity() {
        if (selectedActivityItems.isEmpty()) {
            clearActivitySelection()
            return
        }
        val allCurrentActivityIds = visibleActivity.map(InboxItem::id).toSet()
        if (
            allCurrentActivityIds.isNotEmpty() &&
            selectedActivityIds.containsAll(allCurrentActivityIds)
        ) {
            state.clearInboxActivity(accountStableId)
            showUndoClear = true
            undoDismissedIdList = ArrayList()
        } else {
            val ids = selectedActivityItems.map(InboxItem::id).toSet()
            state.dismissInboxActivityItems(accountStableId, ids)
            undoDismissedIdList = ArrayList(ids)
            showUndoClear = false
        }
        clearActivitySelection()
    }

    fun toggleSelectAllActivity() {
        selectedActivityIds = if (allVisibleSelected) {
            emptySet()
        } else {
            selectableActivityIds
        }
    }

    BackHandler(
        enabled = selectedTab == InboxPrimaryTab.MOD &&
            selectedModSection == InboxModSection.MODMAIL &&
            redditModmailState.selectedConversationId != null,
    ) {
        onCloseRedditModmailConversation()
    }

    BackHandler(enabled = selectionMode) {
        clearActivitySelection()
    }

    LaunchedEffect(
        selectionMode,
        selectedActivityIds,
        selectableActivityIds,
        locallySeenIds,
        accountStableId,
        selectedTab,
    ) {
        if (!selectionMode) {
            onContextualSelectionChanged(null)
        } else {
            onContextualSelectionChanged(
                InboxContextualSelection(
                    selectedCount = selectedActivityIds.size,
                    canMarkRead = selectedActivityItems.any { item ->
                        item.unread && item.id !in locallySeenIds
                    },
                    allVisibleSelected = allVisibleSelected,
                    onClose = ::clearActivitySelection,
                    onMarkRead = ::markSelectedActivityRead,
                    onDismiss = ::dismissSelectedActivity,
                    onSelectAll = ::toggleSelectAllActivity,
                ),
            )
        }
    }

    DisposableEffect(onContextualSelectionChanged) {
        onDispose {
            onContextualSelectionChanged(null)
        }
    }

    val liveRefreshing = liveStateMatches && redditInboxState.loading
    val liveAppending = liveStateMatches && redditInboxState.appending
    val liveAuthorizationRequired = liveStateMatches && redditInboxState.authorizationRequired
    val liveAuthorizing = liveStateMatches && redditInboxState.authorizing
    val liveFailure = redditInboxState.failureKind.takeIf { liveStateMatches }
    val liveNextCursor = redditInboxState.nextCursor.takeIf { liveStateMatches }
    val selectedRefreshing = if (selectedTab == InboxPrimaryTab.MOD) {
        when (selectedModSection) {
            InboxModSection.QUEUE -> aggregateQueueMatches && redditModerationState.queueLoading
            InboxModSection.MODMAIL -> modmailStateMatches &&
                if (
                    showSelectedModmailThread &&
                    redditModmailState.selectedConversationId != null
                ) {
                    redditModmailState.threadLoading
                } else {
                    redditModmailState.loading
                }
        }
    } else {
        liveRefreshing
    }
    val selectedNextCursor = if (selectedTab == InboxPrimaryTab.MOD) {
        when (selectedModSection) {
            InboxModSection.QUEUE -> moderationQueueNextCursor
            InboxModSection.MODMAIL -> modmailNextCursor.takeIf {
                !showSelectedModmailThread ||
                    redditModmailState.selectedConversationId == null
            }
        }
    } else {
        liveNextCursor
    }
    // LOWUX3_MOD_QUEUE_REFRESH_IS_NOT_LOAD_MORE_V1 (OCB-221): the moderation
    // state has one loading flag, so a refresh of a filled queue also read
    // "Loading more…"; the screen remembers whether it asked for a next page.
    var modQueueAppendRequested by remember(accountStableId) { mutableStateOf(false) }
    LaunchedEffect(redditModerationState.queueLoading) {
        if (!redditModerationState.queueLoading) modQueueAppendRequested = false
    }
    val selectedAppending = if (selectedTab == InboxPrimaryTab.MOD) {
        when (selectedModSection) {
            InboxModSection.QUEUE -> aggregateQueueMatches &&
                redditModerationState.queueLoading &&
                modQueueAppendRequested &&
                moderationItems.isNotEmpty()
            InboxModSection.MODMAIL -> modmailStateMatches && redditModmailState.appending
        }
    } else {
        liveAppending
    }
    val degradedInboxPresentation = if (
        authenticated && liveFailure != null && sourceItems.isNotEmpty()
    ) {
        redditInboxErrorPresentation(
            failureKind = liveFailure,
            details = redditInboxState.failureDetails,
            retryable = redditInboxState.failureRetryable,
            showingCachedContent = true,
        )
    } else {
        null
    }
    ReportViewportError(
        scopeKey = "inbox:$accountStableId",
        presentation = degradedInboxPresentation,
        failureDetails = redditInboxState.failureDetails,
        onRetry = if (degradedInboxPresentation != null) {
            { onLoadRedditInbox(accountStableId, true) }
        } else {
            null
        },
    )

    var openingTargetKeys by remember(accountStableId) { mutableStateOf(emptySet<String>()) }
    val targetNoticeHost = LocalViewportErrorHostState.current
    // ISSUE124_INBOX_TARGET_FAILURES_ARE_SHOWN_V1 / ISSUE107_ONE_OPEN_PER_INBOX_ITEM_V1:
    // a reply, post or queue item is read from Reddit before it opens. One
    // read per item (a repeat tap used to push the post twice), a progress
    // line on the row meanwhile, an answer after an account switch or after
    // leaving the inbox is dropped (it opened over whatever showed), and a
    // post that cannot be loaded says so (it did nothing).
    fun resolveTargetThenOpen(
        key: String,
        postId: String,
        resolve: (String, String, (Post?) -> Unit) -> Unit,
        open: (Post) -> Unit,
    ) {
        if (key in openingTargetKeys) return
        openingTargetKeys = openingTargetKeys + key
        val dispatchAccount = state.activeAccountId
        val dispatchRoute = state.currentNavigationRoute
        resolve(accountStableId, postId) { resolvedPost ->
            openingTargetKeys = openingTargetKeys - key
            if (
                !resolvedTargetStillWanted(
                    dispatchAccount = dispatchAccount,
                    dispatchRoute = dispatchRoute,
                    currentAccount = state.activeAccountId,
                    currentRoute = state.currentNavigationRoute,
                )
            ) {
                return@resolve
            }
            if (resolvedPost != null) {
                open(resolvedPost)
            } else {
                targetNoticeHost?.presentRedditLinkUnavailable(RedditLinkUnavailableReason.POST_UNAVAILABLE)
            }
        }
    }

    fun openItem(item: InboxItem) {
        if (!authenticated) {
            // A11Y01A4_INBOX_LIST_DETAIL_SELECTION_V1
            onOpenItemSelection?.invoke(
                item,
                null,
                false,
            ) ?: state.openInboxItem(item)
            return
        }
        when (val target = item.canonicalInboxTarget()) {
            is InboxTarget.ExactComment -> {
                resolveTargetThenOpen(item.id, target.postId, onResolveRedditInboxTarget) { resolvedPost ->
                    onOpenItemSelection?.invoke(
                        item,
                        resolvedPost,
                        false,
                    ) ?: state.openInboxItem(
                        item = item,
                        resolvedPost = resolvedPost,
                        allowFallbackDetail = false,
                        verifiedReadAccountStableId = accountStableId,
                    )
                }
            }
            is InboxTarget.Post -> {
                resolveTargetThenOpen(item.id, target.postId, onResolveRedditInboxTarget) { resolvedPost ->
                    onOpenItemSelection?.invoke(
                        item,
                        resolvedPost,
                        false,
                    ) ?: state.openInboxItem(
                        item = item,
                        resolvedPost = resolvedPost,
                        allowFallbackDetail = false,
                    )
                    onMarkRedditInboxRead(accountStableId, item.id)
                }
            }
            is InboxTarget.MessageThread -> {
                onOpenItemSelection?.invoke(
                    item,
                    null,
                    false,
                ) ?: state.openInboxItem(item)
                onMarkRedditInboxRead(accountStableId, item.id)
            }
            null -> {
                onOpenItemSelection?.invoke(
                    item,
                    null,
                    true,
                ) ?: state.openInboxItem(item, allowFallbackDetail = true)
                onMarkRedditInboxRead(accountStableId, item.id)
            }
        }
    }

    fun openModeratorItem(item: RedditModeratorQueueItem) {
        val target = item.canonicalModerationTarget() ?: return
        resolveTargetThenOpen(item.fullname, target.postId, onResolveRedditModerationTarget) { resolvedPost ->
            onOpenModerationItemSelection?.invoke(
                item,
                resolvedPost,
            ) ?: state.openModeratorQueueItem(
                item = item,
                resolvedPost = resolvedPost,
            )
        }
    }

    pendingModeratorRemoval?.let { item ->
        InboxModeratorRemovalDialog(
            item = item,
            accountStableId = accountStableId,
            actionUiState = moderationActionState,
            removalReasonState = removalReasonState,
            onLoadRemovalReasons = { communityName, force ->
                onLoadRedditRemovalReasons(
                    accountStableId,
                    communityName,
                    force,
                )
            },
            onAuthorizeRemovalReasons = onAuthorizeRedditRemovalReasons,
            onModerate = onModerateRedditQueueItem,
            onDismiss = { pendingModeratorRemoval = null },
            // MODRESPONSE01A3_SAVED_RESPONSES_PICKER_V1
            savedResponses = SavedResponseActions(
                responses = state.moderatorSavedResponses,
                onSave = state::saveModeratorResponse,
                onDelete = state::deleteModeratorResponse,
                initialType = state.moderatorRemovalMessageType(state.activeAccountId),
                onTypeUsed = { type ->
                    state.rememberModeratorRemovalMessageType(state.activeAccountId, type)
                },
            ),
        )
    }

    if (
        showSelectedModmailThread &&
        selectedTab == InboxPrimaryTab.MOD &&
        selectedModSection == InboxModSection.MODMAIL &&
        modmailThread != null
    ) {
        // NEXT01A3D_FULLSCREEN_THREAD_REUSES_SHARED_SURFACE_V1
        InboxModmailThreadSurface(
            modifier = modifier,
            thread = modmailThread,
            state = state,
            redditModmailState = redditModmailState,
            accountStableId = accountStableId,
            authenticated = authenticated,
            listState = modmailThreadListState,
            applySystemBars = modmailThreadFillsTheScreen,
            onOpenRedditModmailConversation = onOpenRedditModmailConversation,
            onSendRedditModmailMessage = onSendRedditModmailMessage,
            onSetRedditModmailArchived = onSetRedditModmailArchived,
            onSetRedditModmailUnread = onSetRedditModmailUnread,
            onVerifyRedditModmailReadState =
                onVerifyRedditModmailReadState,
            onApplyRedditModmailTriage = onApplyRedditModmailTriage,
            onCloseRedditModmailConversation =
                onCloseRedditModmailConversation,
        )
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        RefreshableLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = contentPadding,
            indicatorTopPadding = contentPadding.calculateTopPadding(),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            isRefreshing = selectedRefreshing,
            onRefresh = if (authenticated) {
                {
                    if (selectedTab == InboxPrimaryTab.MOD) {
                        when (selectedModSection) {
                            InboxModSection.QUEUE -> onLoadRedditModQueue(
                                accountStableId,
                                REDDIT_MODERATION_AGGREGATE_QUEUE,
                                true,
                            )
                            InboxModSection.MODMAIL -> {
                                val conversationId = redditModmailState.selectedConversationId
                                if (showSelectedModmailThread && conversationId != null) {
                                    onOpenRedditModmailConversation(
                                        accountStableId,
                                        conversationId,
                                    )
                                } else {
                                    onLoadRedditModmail(accountStableId, true)
                                }
                            }
                        }
                    } else {
                        onLoadRedditInbox(accountStableId, true)
                    }
                }
            } else {
                null
            },
            loadNextEnabled = authenticated &&
                selectedNextCursor != null &&
                !selectedRefreshing &&
                !selectedAppending &&
                (selectedTab == InboxPrimaryTab.MOD || !liveAuthorizing),
            loadNextToken = selectedNextCursor,
            onLoadNext = {
                if (selectedTab == InboxPrimaryTab.MOD) {
                    when (selectedModSection) {
                        InboxModSection.QUEUE -> {
                            modQueueAppendRequested = true
                            onAppendRedditModQueue(
                                accountStableId,
                                REDDIT_MODERATION_AGGREGATE_QUEUE,
                            )
                        }
                        InboxModSection.MODMAIL -> onAppendRedditModmail(accountStableId)
                    }
                } else {
                    onAppendRedditInbox(accountStableId)
                }
            },
        ) {
            item("inbox-controls") {
                // ISSUE108_TWO_PANE_MODMAIL_KEEPS_INBOX_NAVIGATION_V1: the guard
                // predates the two-pane workspace; the full-screen thread returns
                // early above, so it only hid the tabs, the Queue/Modmail switch
                // and refresh from the list pane while a thread was open beside it.
                if (!(showSelectedModmailThread && modmailThread != null)) Column {
                    if (selectedTab != InboxPrimaryTab.MOD) {
                        val operationalStatus = inboxOperationalStatusLabel(
                            authenticated = authenticated,
                            liveStateMatches = liveStateMatches,
                            liveState = redditInboxState,
                        )
                        if (
                            operationalStatus != null ||
                            authenticated && liveAuthorizationRequired
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                operationalStatus?.let { status ->
                                    Text(
                                        text = status,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                    )
                                } ?: Spacer(Modifier.weight(1f))
                                if (authenticated && liveAuthorizing) {
                                    // ISSUE96_CANCEL_WAITING_AUTHORIZATION_V1: waiting for
                                    // the browser had no way out (Enable Inbox was off).
                                    TextButton(onClick = onCancelRedditInboxAuthorization) {
                                        Text("Cancel authorization")
                                    }
                                } else if (authenticated && liveAuthorizationRequired) {
                                    TextButton(
                                        onClick = {
                                            onAuthorizeRedditInbox(accountStableId)
                                        },
                                    ) {
                                        Text("Enable Inbox")
                                    }
                                }
                            }
                        }
                    }

                    // MODMAIL06R1_UNREAD_BADGE_HIERARCHY_V1
                    val liveModmailUnreadCount = if (modmailStateMatches) {
                        redditModmailState.unreadCount
                    } else {
                        0
                    }
                    val moderationAttentionCount = (
                        moderationItems.size.toLong() +
                            liveModmailUnreadCount.toLong()
                        )
                        .coerceAtMost(Int.MAX_VALUE.toLong())
                        .toInt()

                    InboxWorkspacePrimaryNavigation(
                        tabs = primaryTabs,
                        selectedTab = selectedTab,
                        badgeCount = { primaryTab ->
                            when (primaryTab) {
                                InboxPrimaryTab.ACTIVITY -> tabUnreadCounts.activity
                                InboxPrimaryTab.MESSAGES -> tabUnreadCounts.messages
                                InboxPrimaryTab.MOD -> moderationAttentionCount
                            }
                        },
                        onSelect = { primaryTab ->
                            selectedTabName = primaryTab.name
                            onPrimaryTabChanged(primaryTab)
                            filter = InboxFilter.ALL
                            clearActivitySelection()
                        },
                    )

                    if (selectedTab == InboxPrimaryTab.MOD) {
                        Column {
                            InboxWorkspaceModerationNavigation(
                                selectedSection = selectedModSection,
                                modmailUnreadCount = liveModmailUnreadCount,
                                onSelect = { section ->
                                    selectedModSectionName = section.name
                                    onModSectionChanged(section)
                                    pendingModeratorRemoval = null
                                },
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 2.dp),
                            ) {
                                val statusText = when (selectedModSection) {
                                    InboxModSection.QUEUE -> moderationCommunityCountLabel(
                                        redditModerationState.moderatedCommunities.size,
                                    )
                                    InboxModSection.MODMAIL -> when {
                                        !modmailStateMatches -> "Modmail"
                                        redditModmailState.mailCommunities.isNotEmpty() ->
                                            modmailCommunityCountLabel(
                                                redditModmailState.mailCommunities.size,
                                            )
                                        redditModmailState.loaded -> "No communities with mail permission"
                                        else -> "Modmail"
                                    }
                                }
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                if (
                                    selectedModSection == InboxModSection.MODMAIL &&
                                    modmailAuthorizationRequired
                                ) {
                                    TextButton(
                                        enabled = !redditModmailState.authorizing,
                                        onClick = { onAuthorizeRedditModmail(accountStableId) },
                                    ) {
                                        Text(
                                            if (redditModmailState.authorizing) {
                                                "Authorizing…"
                                            } else {
                                                "Enable Modmail"
                                            },
                                        )
                                    }
                                }
                                IconButton(
                                    enabled = when (selectedModSection) {
                                        InboxModSection.QUEUE ->
                                            !redditModerationState.queueLoading &&
                                                !moderationActionState.inProgress
                                        InboxModSection.MODMAIL -> !redditModmailState.inProgress
                                    },
                                    onClick = {
                                        when (selectedModSection) {
                                            InboxModSection.QUEUE -> onLoadRedditModQueue(
                                                accountStableId,
                                                REDDIT_MODERATION_AGGREGATE_QUEUE,
                                                true,
                                            )
                                            InboxModSection.MODMAIL -> {
                                                val conversationId =
                                                    redditModmailState.selectedConversationId
                                                if (showSelectedModmailThread && conversationId != null) {
                                                    onOpenRedditModmailConversation(
                                                        accountStableId,
                                                        conversationId,
                                                    )
                                                } else {
                                                    onLoadRedditModmail(
                                                        accountStableId,
                                                        true,
                                                    )
                                                }
                                            }
                                        }
                                    },
                                ) {
                                    Icon(
                                        Icons.Rounded.Refresh,
                                        contentDescription = when (selectedModSection) {
                                            InboxModSection.QUEUE -> "Refresh moderation queue"
                                            InboxModSection.MODMAIL -> "Refresh Modmail"
                                        },
                                    )
                                }
                            }
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            FilterChip(
                                selected = filter == InboxFilter.UNREAD,
                                onClick = {
                                    filter = if (filter == InboxFilter.UNREAD) {
                                        InboxFilter.ALL
                                    } else {
                                        InboxFilter.UNREAD
                                    }
                                    clearActivitySelection()
                                },
                                label = { Text("Unread") },
                            )
                            Spacer(Modifier.weight(1f))
                            if (
                                selectedTab == InboxPrimaryTab.ACTIVITY &&
                                tabUnreadCounts.activity > 0
                            ) {
                                IconButton(
                                    enabled = !authenticated ||
                                        !redditInboxState.readMutationInProgress,
                                    onClick = ::markAllVisibleActivityRead,
                                ) {
                                    Icon(
                                        Icons.Rounded.DoneAll,
                                        contentDescription =
                                            "Mark all activity as read",
                                    )
                                }
                            }
                            if (
                                selectedTab == InboxPrimaryTab.MESSAGES &&
                                tabUnreadCounts.messages > 0
                            ) {
                                IconButton(
                                    enabled = !authenticated ||
                                        !redditInboxState.readMutationInProgress,
                                    onClick = ::markAllVisibleMessagesRead,
                                ) {
                                    Icon(
                                        Icons.Rounded.DoneAll,
                                        contentDescription =
                                            "Mark all messages as read",
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (
                selectedTab != InboxPrimaryTab.MOD &&
                authenticated &&
                liveFailure != null &&
                sourceItems.isEmpty()
            ) {
                item("inbox-error") {
                    ErrorNotice(
                        presentation = redditInboxErrorPresentation(
                            failureKind = liveFailure,
                            details = redditInboxState.failureDetails,
                            retryable = redditInboxState.failureRetryable,
                            showingCachedContent = false,
                        ),
                        diagnosticScopeKey = "inbox_read",
                        onAction = { action ->
                            if (action == ErrorActionKind.RETRY) {
                                onLoadRedditInbox(accountStableId, true)
                            }
                        },
                    )
                }
            }

            when (selectedTab) {
                InboxPrimaryTab.ACTIVITY -> {
                    InboxActivitySection.entries.forEach { section ->
                        val sectionItems = inboxItems.filter { item ->
                            inboxActivitySection(item.createdEpochSeconds) == section
                        }
                        if (sectionItems.isNotEmpty()) {
                            item("activity-section-${section.name}") {
                                Text(
                                    section.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(
                                        start = 12.dp,
                                        top = 16.dp,
                                        end = 12.dp,
                                        bottom = 6.dp,
                                    ),
                                )
                            }
                            items(sectionItems, key = InboxItem::id) { item ->
                                val read = !item.unread || item.id in locallySeenIds
                                OpeningTargetOverlay(opening = item.id in openingTargetKeys) {
                                    ActivityRow(
                                        state = state,
                                        item = item,
                                        read = read,
                                        selectionMode = selectionMode,
                                        selected = item.id in selectedActivityIds,
                                        onToggleSelected = {
                                            toggleActivitySelection(item.id)
                                        },
                                        onOpen = { openItem(item) },
                                        onLongPress = {
                                            selectedActivityIds =
                                                selectedActivityIds + item.id
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                InboxPrimaryTab.MESSAGES -> {
                    InboxActivitySection.entries.forEach { section ->
                        val sectionItems = inboxItems.filter { item ->
                            inboxActivitySection(item.createdEpochSeconds) == section
                        }
                        if (sectionItems.isNotEmpty()) {
                            item("message-section-${section.name}") {
                                Text(
                                    section.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(
                                        start = 12.dp,
                                        top = 16.dp,
                                        end = 12.dp,
                                        bottom = 6.dp,
                                    ),
                                )
                            }
                            items(sectionItems, key = InboxItem::id) { item ->
                                InboxMessageRow(
                                    state = state,
                                    item = item,
                                    read = !item.unread || item.id in locallySeenIds,
                                    onOpen = { openItem(item) },
                                )
                            }
                        }
                    }
                }

                InboxPrimaryTab.MOD -> {
                    when (selectedModSection) {
                        InboxModSection.QUEUE -> {
                        item("moderator-report-lookup") { ModeratorReportLookupButton() }
                        moderationQueueFailure?.let { failureKind ->
                            item("mod-inbox-error") {
                                InboxModeratorQueueFailure(
                                    failureKind = failureKind,
                                    httpStatusCode =
                                        redditModerationState.queueFailureHttpStatusCode,
                                    retryable =
                                        redditModerationState.queueFailureRetryable,
                                    onRetry = {
                                        onLoadRedditModQueue(
                                            accountStableId,
                                            REDDIT_MODERATION_AGGREGATE_QUEUE,
                                            true,
                                        )
                                    },
                                )
                            }
                        }

                        if (
                            moderationActionState.phase !=
                            app.subshell.prototype.data.account.RedditModeratorActionPhase.IDLE &&
                            moderationActionState.phase !=
                            app.subshell.prototype.data.account.RedditModeratorActionPhase.SENDING
                        ) {
                            item("mod-inbox-action-status") {
                                InboxModerationActionStatus(
                                    uiState = moderationActionState,
                                    onRetry = onRetryRedditModerationAction,
                                    onAuthorize = onAuthorizeRedditModerationAction,
                                )
                            }
                        }

                        if (
                            moderationQueueLoading &&
                            moderationItems.isEmpty() &&
                            moderationQueueFailure == null
                        ) {
                            item("mod-inbox-loading") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 36.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    InlineProgressIndicator()
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        "Loading moderation queue…",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        items(
                            items = moderationItems,
                            key = RedditModeratorQueueItem::fullname,
                        ) { item ->
                            OpeningTargetOverlay(opening = item.fullname in openingTargetKeys) {
                                InboxModeratorQueueItemCard(
                                    item = item,
                                    accountStableId = accountStableId,
                                    actionUiState = moderationActionState,
                                    onOpen = { openModeratorItem(item) },
                                    onModerate = onModerateRedditQueueItem,
                                    onRequestRemove = { pendingModeratorRemoval = it },
                                )
                            }
                        }

                        if (
                            aggregateQueueMatches &&
                            redditModerationState.queueLoaded &&
                            moderationItems.isEmpty() &&
                            moderationQueueFailure == null
                        ) {
                            item("mod-inbox-empty") {
                                InboxEmptyState(
                                    title = "Moderation queue is clear",
                                    supportingText =
                                        "No reported posts or comments need review.",
                                )
                            }
                        }
                                        }

                        InboxModSection.MODMAIL -> {
                            item("modmail-list-mode") {
                                InboxModmailQueueFilters(
                                    selectedMode = redditModmailState.listMode,
                                    enabled = !redditModmailState.inProgress,
                                    onSelect = { mode ->
                                        onSelectRedditModmailListMode(accountStableId, mode)
                                    },
                                )
                            }

                            modmailFailure?.let { failureKind ->
                                item("modmail-error") {
                                    InboxModmailFailure(
                                        failureKind = failureKind,
                                        httpStatusCode = redditModmailState.failureHttpStatusCode,
                                        retryable = redditModmailState.failureRetryable,
                                        onRetry = {
                                            onLoadRedditModmail(accountStableId, true)
                                        },
                                    )
                                }
                            }

                            if (
                                modmailAuthorizationRequired &&
                                modmailConversations.isEmpty()
                            ) {
                                item("modmail-authorization") {
                                    InboxEmptyState(
                                        title = "Modmail permission required",
                                        supportingText =
                                            "Enable Reddit Modmail access for this account to read moderator conversations.",
                                    )
                                }
                            }

                            if (
                                showSelectedModmailThread &&
                                redditModmailState.threadLoading &&
                                modmailThread == null
                            ) {
                                item("modmail-thread-loading") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 36.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        InlineProgressIndicator()
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            "Loading conversation…",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            } else {
                                if (
                                    redditModmailState.loading &&
                                    modmailConversations.isEmpty() &&
                                    modmailFailure == null &&
                                    !modmailAuthorizationRequired
                                ) {
                                    item("modmail-loading") {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 36.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            InlineProgressIndicator()
                                            Spacer(Modifier.width(10.dp))
                                            Text(
                                                "Loading Modmail…",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }

                                items(
                                    items = modmailConversations,
                                    key = { conversation -> conversation.id },
                                ) { conversation ->
                                    InboxModmailConversationRow(
                                        conversation = conversation,
                                        communityIconUri = state
                                            .communityIdentityFor(conversation.communityName)
                                            .iconImageUri,
                                        selected = modmailStateMatches &&
                                            redditModmailState.selectedConversationId ==
                                            conversation.id,
                                        onOpen = {
                                            onOpenRedditModmailConversation(
                                                accountStableId,
                                                conversation.id,
                                            )
                                        },
                                    )
                                }

                                if (
                                    modmailStateMatches &&
                                    redditModmailState.loaded &&
                                    redditModmailState.mailCommunities.isEmpty() &&
                                    !modmailAuthorizationRequired &&
                                    modmailFailure == null
                                ) {
                                    item("modmail-no-mail-permission") {
                                        InboxEmptyState(
                                            title = "No Modmail access",
                                            supportingText =
                                                "This account has no moderated communities with mail permission.",
                                        )
                                    }
                                } else if (
                                    modmailStateMatches &&
                                    redditModmailState.loaded &&
                                    redditModmailState.mailCommunities.isNotEmpty() &&
                                    modmailConversations.isEmpty() &&
                                    !modmailAuthorizationRequired &&
                                    modmailFailure == null
                                ) {
                                    item("modmail-empty") {
                                        InboxEmptyState(
                                            title = redditModmailState.listMode.modmailEmptyTitle(),
                                            supportingText =
                                                redditModmailState.listMode.modmailEmptySupportingText(),
                                        )
                                    }
                                }
                            }
                        }
                    }
}
            }

            if (
                selectedTab != InboxPrimaryTab.MOD &&
                inboxItems.isEmpty() &&
                !(authenticated && liveFailure != null && sourceItems.isEmpty())
            ) {
                item("inbox-empty-${selectedTab.name}-${filter.name}") {
                    val clearedCurrentActivity =
                        selectedTab == InboxPrimaryTab.ACTIVITY &&
                            filter == InboxFilter.ALL &&
                            visibleActivity.isEmpty() &&
                            clearedActivity.isNotEmpty()
                    // LOWUX3_INBOX_EMPTY_STATE_WAITS_FOR_THE_INBOX_V1 (OCB-223): before
                    // the first page (or without Inbox access) it read "No activity
                    // yet"; inboxEmptyLabel already knew better.
                    val inboxNotReady = authenticated && (
                        !liveStateMatches ||
                            (redditInboxState.loading && !redditInboxState.loaded) ||
                            redditInboxState.authorizationRequired
                        )
                    when {
                        inboxNotReady && selectedTab != InboxPrimaryTab.MOD -> InboxEmptyState(
                            title = inboxEmptyLabel(
                                authenticated = authenticated,
                                filter = filter,
                                liveStateMatches = liveStateMatches,
                                liveState = redditInboxState,
                            ),
                            supportingText = "",
                        )
                        clearedCurrentActivity -> InboxEmptyState(
                            title = "Activity cleared",
                            supportingText = "New activity will appear here.",
                        )
                        selectedTab == InboxPrimaryTab.ACTIVITY &&
                            filter == InboxFilter.UNREAD -> InboxEmptyState(
                            title = "You're all caught up",
                            supportingText = "No unread activity.",
                        )
                        selectedTab == InboxPrimaryTab.ACTIVITY -> InboxEmptyState(
                            title = "No activity yet",
                            supportingText = "Replies and mentions will appear here.",
                        )
                        filter == InboxFilter.UNREAD -> InboxEmptyState(
                            title = "You're all caught up",
                            supportingText = "No unread messages.",
                        )
                        else -> InboxEmptyState(
                            title = "No messages yet",
                            supportingText = "Private messages will appear here.",
                        )
                    }
                }
            }

            if (selectedAppending) {
                item("inbox-appending-${selectedTab.name}") {
                    Text(
                        "Loading more…",
                        modifier = Modifier.padding(
                            horizontal = 24.dp,
                            vertical = 12.dp,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }


        if (
            selectedTab == InboxPrimaryTab.ACTIVITY &&
            (showUndoClear || undoDismissedIdList.isNotEmpty())
        ) {
            // M3 Snackbar: inverse colours with an inversePrimary action (the
            // hand-built bar kept primary on inverseSurface, ~1.9:1), announced.
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                action = {
                    TextButton(
                        onClick = {
                            if (showUndoClear) {
                                state.undoInboxActivityClear(accountStableId)
                                showUndoClear = false
                            } else {
                                state.restoreInboxActivityItems(accountStableId, undoDismissedIdList.toSet())
                                undoDismissedIdList = ArrayList()
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = SnackbarDefaults.actionColor,
                        ),
                    ) {
                        Text("Undo")
                    }
                },
            ) {
                Text(
                    if (showUndoClear) {
                        "Activity cleared"
                    } else {
                        "${undoDismissedIdList.size} dismissed"
                    },
                )
            }
        }
    }
}

internal data class InboxTabUnreadCounts(
    val activity: Int,
    val messages: Int,
)

internal fun inboxTabUnreadCounts(
    items: List<InboxItem>,
    locallySeenIds: Set<String>,
): InboxTabUnreadCounts {
    var activity = 0
    var messages = 0
    items.forEach { item ->
        if (!item.unread || item.id in locallySeenIds) return@forEach
        if (item.kind == ActivityKind.MESSAGE) {
            messages += 1
        } else {
            activity += 1
        }
    }
    return InboxTabUnreadCounts(activity = activity, messages = messages)
}

internal fun moderationCommunityCountLabel(count: Int): String = when (count) {
    1 -> "Across 1 moderated community"
    else -> "Across $count moderated communities"
}

internal fun modmailCommunityCountLabel(count: Int): String = when (count) {
    1 -> "Modmail for 1 community"
    else -> "Modmail for $count communities"
}

private fun InboxPrimaryTab.workspaceIcon(): ImageVector = when (this) {
    InboxPrimaryTab.ACTIVITY -> Icons.Rounded.AlternateEmail
    InboxPrimaryTab.MESSAGES -> Icons.Rounded.MailOutline
    InboxPrimaryTab.MOD -> Icons.Rounded.Security
}

private fun InboxModSection.workspaceIcon(): ImageVector = when (this) {
    InboxModSection.QUEUE -> Icons.Rounded.Security
    InboxModSection.MODMAIL -> Icons.Rounded.MailOutline
}

// M3225_INBOX_DESTINATIONS_ARE_M3_TABS_V1 (owner 2026-10-13, modinbox-14): the
// Inbox destinations are M3 tabs (they switch content views at one level), not
// a segmented control; labels keep the reader's text size, and the row scrolls
// when they do not fit instead of shrinking them (#220).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InboxWorkspacePrimaryNavigation(
    tabs: List<InboxPrimaryTab>,
    selectedTab: InboxPrimaryTab,
    badgeCount: (InboxPrimaryTab) -> Int,
    onSelect: (InboxPrimaryTab) -> Unit,
) {
    InboxTabRow(
        labels = tabs.map { it.label },
        selectedIndex = tabs.indexOf(selectedTab).coerceAtLeast(0),
        primary = true,
    ) {
        tabs.forEach { tab ->
            InboxTab(
                label = tab.label,
                icon = tab.workspaceIcon(),
                count = badgeCount(tab),
                selected = selectedTab == tab,
                onClick = { onSelect(tab) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InboxWorkspaceModerationNavigation(
    selectedSection: InboxModSection,
    modmailUnreadCount: Int,
    onSelect: (InboxModSection) -> Unit,
) {
    InboxTabRow(
        labels = InboxModSection.entries.map { it.label },
        selectedIndex = InboxModSection.entries.indexOf(selectedSection),
        primary = false,
    ) {
        InboxModSection.entries.forEach { section ->
            InboxTab(
                label = section.label,
                icon = section.workspaceIcon(),
                count = if (section == InboxModSection.MODMAIL) modmailUnreadCount else 0,
                selected = selectedSection == section,
                onClick = { onSelect(section) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InboxTabRow(
    labels: List<String>,
    selectedIndex: Int,
    primary: Boolean,
    tabs: @Composable () -> Unit,
) {
    var rowWidth by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    // A LeadingIconTab spends 16 + 24 + 8 + 16 dp around its titleSmall label.
    val labelsFit = rememberLabelsFitSlots(
        labels = labels,
        slotWidth = rowWidth / labels.size.coerceAtLeast(1) - 64.dp,
        style = MaterialTheme.typography.titleSmall,
    )
    val modifier = Modifier
        .fillMaxWidth()
        .onSizeChanged { size -> rowWidth = with(density) { size.width.toDp() } }
    when {
        primary && labelsFit -> PrimaryTabRow(
            selectedTabIndex = selectedIndex,
            modifier = modifier,
            containerColor = Color.Transparent,
        ) { tabs() }
        primary -> PrimaryScrollableTabRow(
            selectedTabIndex = selectedIndex,
            modifier = modifier,
            containerColor = Color.Transparent,
            edgePadding = 0.dp,
        ) { tabs() }
        labelsFit -> SecondaryTabRow(
            selectedTabIndex = selectedIndex,
            modifier = modifier,
            containerColor = Color.Transparent,
        ) { tabs() }
        else -> SecondaryScrollableTabRow(
            selectedTabIndex = selectedIndex,
            modifier = modifier,
            containerColor = Color.Transparent,
            edgePadding = 0.dp,
        ) { tabs() }
    }
}

@Composable
private fun InboxTab(
    label: String,
    icon: ImageVector,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    LeadingIconTab(
        selected = selected,
        onClick = onClick,
        text = { Text(label, maxLines = 1) },
        icon = {
            BadgedBox(
                badge = {
                    if (count > 0) {
                        // ISSUE220_INBOX_BADGE_USES_BADGEDBOX_PLACEMENT_V1: a fixed
                        // -6 dp offset clipped the badge at large text.
                        Badge {
                            Text(if (count > 99) "99+" else count.toString())
                        }
                    }
                },
            ) {
                Icon(imageVector = icon, contentDescription = null)
            }
        },
        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun inboxOperationalStatusLabel(
    authenticated: Boolean,
    liveStateMatches: Boolean,
    liveState: RedditInboxUiState,
): String? {
    if (!authenticated) return null
    if (!liveStateMatches) return "Loading Inbox…"
    return when {
        liveState.authorizing -> "Waiting for Reddit authorization…"
        liveState.authorizationRequired -> "Inbox permission required"
        liveState.loading && !liveState.loaded -> "Loading Inbox…"
        liveState.failureKind != null && liveState.items.isNotEmpty() -> "Showing saved Inbox"
        liveState.failureKind != null -> "Inbox unavailable"
        else -> null
    }
}

private fun inboxEmptyLabel(
    authenticated: Boolean,
    filter: InboxFilter,
    liveStateMatches: Boolean,
    liveState: RedditInboxUiState,
): String {
    if (!authenticated) {
        return if (filter == InboxFilter.UNREAD) "Nothing unread here." else "No local items for this filter."
    }
    if (!liveStateMatches || liveState.loading && !liveState.loaded) return "Loading Inbox…"
    if (liveState.authorizationRequired) {
        return "Allow Inbox read access to load Reddit activity and messages."
    }
    return if (filter == InboxFilter.UNREAD) "Nothing unread here." else "No Reddit items for this filter."
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActivityRow(
    state: SubshellState,
    item: InboxItem,
    read: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    onToggleSelected: () -> Unit,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val user = state.userFor(item.sender)
    val avatarUri = user.avatarImageUri
        ?.trim()
        ?.takeIf(String::isNotEmpty)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
        color = when {
            selectionMode && selected -> MaterialTheme.colorScheme.surfaceContainerHigh
            !read -> MaterialTheme.colorScheme.surfaceContainer
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
    ) {
        ListItem(
            modifier = Modifier
                .fillMaxWidth()
                // A11Y01A1_INBOX_READ_STATE_SEMANTICS_V1
                // LOWUI7_READ_STATE_ON_THE_FOCUSED_ROW_V1 (OCB-224): on the
                // clickable node TalkBack focuses, not the outer Surface.
                .semantics {
                    stateDescription = if (read) "Read" else "Unread"
                }
                .combinedClickable(
                    onClick = {
                        if (selectionMode) onToggleSelected() else onOpen()
                    },
                    onLongClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (selectionMode) onToggleSelected() else onLongPress()
                    },
                ),
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
            ),
            leadingContent = {
                if (selectionMode) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onToggleSelected() },
                    )
                } else {
                    InboxActivityIdentity(
                        item = item,
                        avatarUri = avatarUri,
                        unread = !read,
                    )
                }
            },
            content = {
                // ISSUE34_USER_TAGS_V1
                NameWithUserTag(tag = state.userTagFor(item.sender)) {
                    Text(
                        text = item.sender.ifBlank { "Reddit" },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (read) FontWeight.Normal else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = item.activityVerbLabel(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (read) FontWeight.Normal else FontWeight.Medium,
                        color = if (read) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.preview.isNotBlank()) {
                        Text(
                            item.preview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            // ISSUE220: two preview lines (one line showed a few words at large text).
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    item.activityContextLabel()?.let { context ->
                        Text(
                            context,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            trailingContent = {
                Text(
                    item.age,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }
}

@Composable
private fun InboxActivityIdentity(
    item: InboxItem,
    avatarUri: String?,
    unread: Boolean,
) {
    Box(
        modifier = Modifier
            .width(48.dp)
            .size(48.dp),
    ) {
        if (unread) {
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(6.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
            ) {}
        }

        if (avatarUri == null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        item.kind.icon(),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            return@Box
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(42.dp),
        ) {
            RemoteIdentityImage(
                uri = avatarUri,
                contentDescription = "Avatar for u/${item.sender}",
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(40.dp)
                    .clip(CircleShape),
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            item.kind.icon(),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(16.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        item.kind.icon(),
                        contentDescription = null,
                        modifier = Modifier.size(10.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun InboxEmptyState(
    title: String,
    supportingText: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            supportingText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun InboxMessageRow(
    state: SubshellState,
    item: InboxItem,
    read: Boolean,
    onOpen: () -> Unit,
) {
    val user = state.userFor(item.sender)
    val avatarUri = user.avatarImageUri
        ?.trim()
        ?.takeIf(String::isNotEmpty)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            // A11Y01A1_INBOX_MESSAGE_READ_STATE_SEMANTICS_V1
            .semantics {
                stateDescription = if (read) "Read" else "Unread"
            }
            .padding(horizontal = 4.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
        color = if (read) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        onClick = onOpen,
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                InboxMessageIdentity(
                    sender = item.sender,
                    avatarUri = avatarUri,
                    unread = !read,
                    contentDescription = "Open ${item.title}",
                )
            },
            content = {
                // ISSUE34_USER_TAGS_V1
                NameWithUserTag(tag = state.userTagFor(item.sender)) {
                    Text(
                        text = item.sender.ifBlank { "reddit" },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (read) FontWeight.Normal else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = inboxMessageSubjectLabel(item),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (read) FontWeight.Normal else FontWeight.Medium,
                        color = if (read) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (
                        item.preview.isNotBlank() &&
                        !item.preview.equals(item.title, ignoreCase = true)
                    ) {
                        Text(
                            text = item.preview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            trailingContent = {
                Text(
                    item.age,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }
}

@Composable
private fun InboxMessageIdentity(
    sender: String,
    avatarUri: String?,
    unread: Boolean,
    contentDescription: String,
) {
    Box(
        modifier = Modifier
            .width(56.dp)
            .size(48.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        if (unread) {
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(6.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
            ) {}
        }

        if (avatarUri == null) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.MailOutline,
                        contentDescription = contentDescription,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            return@Box
        }

        RemoteIdentityImage(
            uri = avatarUri,
            contentDescription = "Avatar for u/$sender",
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(40.dp)
                .clip(CircleShape),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.MailOutline,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

private const val INBOX_ACTIVITY_IDENTITY_PREFETCH_STEP = 4
private const val INBOX_ACTIVITY_IDENTITY_PREFETCH_LOOKBEHIND = 4
private const val INBOX_ACTIVITY_IDENTITY_PREFETCH_WINDOW = 8
private const val INBOX02A_CONTEXTUAL_SELECTION_V10 = "android-contextual-top-app-bar"
private const val INBOX02A_ACTIVITY_SURFACE_V102 = "tonal-rounded-material-card"
private const val MESSAGES02A_LIST_PARITY_V1 = "activity-material-surface-parity"
private const val MESSAGES02A_MAIN_MESSAGES_IDENTITY_PREFETCH_V1 = "main-messages-bounded-identity-prefetch"

private fun InboxItem.activityActorUsername(): String? = sender
    .trim()
    .removePrefix("u/")
    .takeIf { username ->
        username.isNotBlank() &&
            !username.equals("[deleted]", ignoreCase = true) &&
            !username.equals("reddit", ignoreCase = true)
    }

internal fun inboxMessageSubjectLabel(item: InboxItem): String =
    if (item.title.trim().equals("[direct chat room]", ignoreCase = true)) {
        "Direct chat"
    } else {
        item.title.ifBlank { "Message" }
    }

private fun InboxItem.inboxMessageIdentityUsername(): String? {
    val normalized = sender.trim()
    if (normalized.startsWith("r/", ignoreCase = true)) return null
    return normalized
        .removePrefix("u/")
        .takeIf { username ->
            username.isNotBlank() &&
                !username.equals("[deleted]", ignoreCase = true) &&
                !username.equals("reddit", ignoreCase = true)
        }
}

private fun InboxItem.activityVerbLabel(): String = when (kind) {
    ActivityKind.REPLY -> "Replied to your comment"
    ActivityKind.POST_REPLY -> "Replied to your post"
    ActivityKind.MENTION -> "Mentioned you"
    ActivityKind.MOD -> title.ifBlank { "Reddit activity" }
    ActivityKind.MESSAGE -> "Message"
}

private fun InboxItem.activityContextLabel(): String? {
    val community = subreddit?.trim()?.takeIf(String::isNotEmpty)
    val post = postTitle?.trim()?.takeIf(String::isNotEmpty) ?: title.trim().takeIf(String::isNotEmpty)
    return when {
        community != null && post != null -> "$community · $post"
        community != null -> community
        post != null -> post
        else -> null
    }
}

private fun ActivityKind.icon(): ImageVector = when (this) {
    ActivityKind.REPLY,
    ActivityKind.POST_REPLY,
    -> Icons.AutoMirrored.Rounded.Reply
    ActivityKind.MENTION -> Icons.Rounded.AlternateEmail
    ActivityKind.MESSAGE -> Icons.Rounded.MailOutline
    ActivityKind.MOD -> Icons.Rounded.Security
}

// NEXT01A3D_SHARED_MODMAIL_THREAD_SURFACE_V1
@Composable
internal fun InboxModmailThreadSurface(
    modifier: Modifier = Modifier,
    thread: app.subshell.prototype.data.account.RedditModmailThread,
    state: SubshellState,
    redditModmailState: RedditModmailUiState,
    accountStableId: String,
    authenticated: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    applySystemBars: Boolean,
    showBackButton: Boolean = true,
    onOpenRedditModmailConversation: (String, String) -> Unit,
    onSendRedditModmailMessage:
        (String, String, String, Boolean, Boolean) -> Unit,
    onSetRedditModmailArchived: (String, String, Boolean) -> Unit,
    onSetRedditModmailUnread: (String, String, Boolean) -> Unit,
    onVerifyRedditModmailReadState: (String, String) -> Unit,
    onApplyRedditModmailTriage:
        (String, String, RedditModmailTriageAction) -> Unit,
    onCloseRedditModmailConversation: () -> Unit,
) {
    BackHandler(onBack = onCloseRedditModmailConversation)

    val containerModifier =
        if (applySystemBars) {
            modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        } else {
            modifier.fillMaxSize()
        }

    Column(
        // COMPOSER232_COMPOSER_SITS_ON_THE_KEYBOARD_V1 (#232): the thread and its
        // composer fit above the keyboard by the IME ruler.
        modifier = containerModifier.fitInside(WindowInsetsRulers.Ime.current),
    ) {
        InboxModmailThreadHeader(
            conversation = thread.conversation,
            participantMuteStatus = thread.participantMuteStatus,
            communityIconUri = state
                .communityIdentityFor(thread.conversation.communityName)
                .iconImageUri,
            archiveInProgress =
                redditModmailState.conversationActionInProgress,
            archiveOutcomeUnknown =
                redditModmailState.conversationActionOutcomeUnknown,
            readStateInProgress =
                redditModmailState.readStateMutationInProgress,
            readStateOutcomeUnknown =
                redditModmailState.readStateOutcomeUnknown,
            onSetArchived = { archived ->
                onSetRedditModmailArchived(
                    accountStableId,
                    thread.conversation.id,
                    archived,
                )
            },
            onSetUnread = { unread ->
                onSetRedditModmailUnread(
                    accountStableId,
                    thread.conversation.id,
                    unread,
                )
            },
            onTriageAction = { action ->
                onApplyRedditModmailTriage(
                    accountStableId,
                    thread.conversation.id,
                    action,
                )
            },
            onVerifyArchived = {
                onOpenRedditModmailConversation(
                    accountStableId,
                    thread.conversation.id,
                )
            },
            onVerifyReadState = {
                onVerifyRedditModmailReadState(
                    accountStableId,
                    thread.conversation.id,
                )
            },
            showBackButton = showBackButton,
            onBack = onCloseRedditModmailConversation,
        )

        RefreshableLazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            contentPadding =
                PaddingValues(
                    horizontal = 8.dp,
                    vertical = 10.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            enabled = authenticated,
            isRefreshing = redditModmailState.threadLoading,
            onRefresh = {
                onOpenRedditModmailConversation(
                    accountStableId,
                    thread.conversation.id,
                )
            },
        ) {
            items(
                items = thread.messages,
                key = { message -> message.id },
            ) { message ->
                InboxModmailMessageRow(
                    message = message,
                    communityName =
                        thread.conversation.communityName,
                    authorTag = state.userTagFor(message.authorName),
                    authorAvatarUri = state
                        .userFor(message.authorName)
                        .avatarImageUri,
                    communityIconUri = state
                        .communityIdentityFor(
                            thread.conversation.communityName,
                        )
                        .iconImageUri,
                )
            }

            if (thread.messages.isEmpty()) {
                item("modmail-thread-empty") {
                    InboxEmptyState(
                        title = "No messages",
                        supportingText =
                            "Reddit returned this conversation without readable messages.",
                    )
                }
            }
        }

        InboxModmailComposer(
            conversation = thread.conversation,
            moderatorUsername = state.activeAccountUsername,
            moderatorAvatarUri = state
                .userFor(state.activeAccountUsername)
                .avatarImageUri,
            communityIconUri = state
                .communityIdentityFor(
                    thread.conversation.communityName,
                )
                .iconImageUri,
            sendInProgress =
                redditModmailState.sendInProgress ||
                    redditModmailState.conversationActionInProgress,
            // LOWUX1_MODMAIL_SEND_WAITS_FOR_A_READ_CHANGE_V1 (OCB-135): the
            // coordinator ignores a send while a read/unread change runs; Send
            // waits for it (typing stays open).
            sendBlocked = redditModmailState.readStateMutationInProgress,
            sendOutcomeUnknown =
                redditModmailState.sendOutcomeUnknown,
            sendRevision = redditModmailState.sendRevision,
            onSend = { body, internal, authorHidden ->
                onSendRedditModmailMessage(
                    accountStableId,
                    thread.conversation.id,
                    body,
                    internal,
                    authorHidden,
                )
            },
        )
    }
}

// ISSUE107_ONE_OPEN_PER_INBOX_ITEM_V1: the row that is being opened shows a
// progress line while its post is read.
@Composable
private fun OpeningTargetOverlay(
    opening: Boolean,
    content: @Composable () -> Unit,
) {
    Box {
        content()
        if (opening) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .semantics { contentDescription = "Opening" },
            )
        }
    }
}

internal const val INBOX_UNDO_VISIBLE_MS = 10_000L
