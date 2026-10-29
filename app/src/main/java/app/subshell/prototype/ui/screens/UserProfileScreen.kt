package app.subshell.prototype.ui.screens

import app.subshell.prototype.ui.error.ReportFailureDiagnostic
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import app.subshell.prototype.ui.ScreenMargin
import app.subshell.prototype.ui.components.RouteTopBar
import app.subshell.prototype.ui.components.rememberLabelsFitSlots
import app.subshell.prototype.ui.components.rememberRouteTopBarScrollBehavior
import app.subshell.prototype.ui.error.redditReadHttpReason
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.ProfileCommentFixture
import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.account.RedditFeedUiFailureKind
import app.subshell.prototype.data.LIVE_PROFILE_ACTIVITY_HISTORY_KINDS
import app.subshell.prototype.data.RedditAccountHistoryKind
import app.subshell.prototype.data.account.RedditAccountHistoryUiFailureKind
import app.subshell.prototype.data.account.RedditAccountHistoryUiState
import app.subshell.prototype.data.account.RedditSavedHistoryUiState
import app.subshell.prototype.data.account.RedditFeedUiState
import app.subshell.prototype.data.redditAccountHistoryQueryKey
import app.subshell.prototype.data.redditMixedSavedHistoryQueryKey
import app.subshell.prototype.data.account.RedditSavedHistoryUiItem
import app.subshell.prototype.data.isValidRedditUsername
import app.subshell.prototype.data.redditUserSubmittedPostFeedQueryKey
import app.subshell.prototype.ui.ObserveLiveFeedAutoAppend
import app.subshell.prototype.ui.ProfileActivityEvidence
import app.subshell.prototype.ui.isUnavailablePublicProfileHttpStatus
import app.subshell.prototype.ui.canAppendRedditFeed
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.FeedScrollAnchor
import app.subshell.prototype.ui.rememberFeedScrollState
import app.subshell.prototype.ui.symbols.Symbols
import app.subshell.prototype.ui.uiDebugLog
import app.subshell.prototype.ui.userFeedScrollKey
import app.subshell.prototype.ui.userPostFeedQueryKey
import app.subshell.prototype.ui.libraryPostFeedQueryKey
import app.subshell.prototype.ui.PostComposerDraft
import app.subshell.prototype.ui.pagingFooterKey
import app.subshell.prototype.ui.components.PostCard
import app.subshell.prototype.ui.components.RepositoryPagingFooter
import app.subshell.prototype.ui.components.RemoteIdentityImage
import app.subshell.prototype.ui.components.RefreshableLazyColumn
import app.subshell.prototype.ui.components.RedditDefaultProfileAvatar
import app.subshell.prototype.ui.components.ErrorNotice
import app.subshell.prototype.ui.error.ErrorPresentation
import app.subshell.prototype.ui.error.ErrorPresentationKind
import app.subshell.prototype.ui.error.ErrorActionKind
import app.subshell.prototype.ui.error.ErrorAction
import app.subshell.prototype.ui.error.ReportViewportError
import app.subshell.prototype.ui.error.redditFeedAppendErrorPresentation
import app.subshell.prototype.ui.error.redditFeedErrorPresentation
import app.subshell.prototype.ui.error.redditProfileCommentsErrorPresentation
import app.subshell.prototype.ui.components.accentFor
import app.subshell.prototype.ui.components.mediaResource
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.snapshotFlow
import app.subshell.prototype.media.VideoVisibilityPolicy
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.ObserveFeedVideoViewport
import app.subshell.prototype.ui.captureExactFeedReturnAnchor
import app.subshell.prototype.ui.PostLayout
import app.subshell.prototype.ui.forFeed
import app.subshell.prototype.ui.captureFeedScrollAnchor
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.material3.TextButton
import app.subshell.prototype.data.account.RedditProfileCommentsUiFailureKind
import app.subshell.prototype.ui.components.MarkdownText
import app.subshell.prototype.ui.components.RemoteCommentMediaPreviews
import app.subshell.prototype.ui.components.CommentContent
import app.subshell.prototype.ui.components.CommunityAvatar
import java.text.DateFormat
import java.util.Date
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.subshell.prototype.ui.components.UserBlockConfirmationDialog
import app.subshell.prototype.ui.components.UserTagEditorDialog
import app.subshell.prototype.ui.components.UserTagProfileSection
import app.subshell.prototype.data.account.RedditBlockedAccount
import app.subshell.prototype.data.account.RedditBlockedAccountsUiState
import app.subshell.prototype.data.account.ContentSafetyFailureKind
import app.subshell.prototype.ui.components.ListCard
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab

private const val PROFILE_POSTS_TAB = 0
private const val PROFILE_COMMENTS_TAB = 1
private const val PROFILE_SAVED_TAB = 2
private const val PROFILE_UPVOTES_TAB = 3
private const val PROFILE_DOWNVOTES_TAB = 4
private const val PROFILE_HIDDEN_TAB = 5
private const val PROFILE_BLOCKED_TAB = 6
private const val PROFILE_DRAFTS_TAB = 7

// ISSUE268_YOUR_READING_PAGE_V1: You › Library › Reading, shown only while
// "Your reading" is on (local counts, no Reddit data).
internal const val PROFILE_READING_TAB = 8
private const val PROFILE_HISTORY_FIRST_TAB = PROFILE_SAVED_TAB
private const val PROFILE_HISTORY_LAST_TAB = PROFILE_HIDDEN_TAB

// NEXT01A6_PROFILE_DESTINATION_SCROLL_SEED_POLICY_V1
internal fun profileDestinationScrollSeedOrNull(
    currentAnchor: FeedScrollAnchor,
    existingTargetAnchor: FeedScrollAnchor?,
): FeedScrollAnchor? =
    if (existingTargetAnchor == null) {
        currentAnchor
    } else {
        null
    }

internal val PROFILE_OWN_DESTINATION_LABELS = listOf(
    "Posts",
    "Comments",
    "Saved",
    "Upvotes",
    "Downvotes",
    "Hidden",
    "Blocked",
    "Drafts",
    "Reading",
)

// LIBRARY02A1_NESTED_COMPACT_YOU_LIBRARY_V1
// LIBRARY02A1_POLISH_FOLDER_IDENTITY_STABLE_CHILD_GEOMETRY_V2
// LIBRARY02A1_FINAL_SELECTED_SLOT_WEIGHT_V3 retired by M3225_PROFILE_TABS_ARE_M3_TAB_ROWS_V1:
// the Library row is an M3 scrollable tab row; the named selected tab is the
// widest slot by its own label, not by a fixed weight.
internal val PROFILE_PRIMARY_DESTINATION_LABELS = listOf(
    "Posts",
    "Comments",
    "Library",
)

internal val PROFILE_LIBRARY_DESTINATION_TABS = listOf(
    PROFILE_SAVED_TAB,
    PROFILE_UPVOTES_TAB,
    PROFILE_DOWNVOTES_TAB,
    PROFILE_HIDDEN_TAB,
    PROFILE_BLOCKED_TAB,
    PROFILE_DRAFTS_TAB,
    PROFILE_READING_TAB,
)

internal val PROFILE_LIBRARY_DESTINATION_LABELS =
    PROFILE_LIBRARY_DESTINATION_TABS.map { PROFILE_OWN_DESTINATION_LABELS[it] }

internal fun profilePrimarySectionForTab(tab: Int): Int = when (tab) {
    PROFILE_POSTS_TAB -> 0
    PROFILE_COMMENTS_TAB -> 1
    else -> 2
}

internal fun profilePrimaryTargetTab(
    section: Int,
    lastLibraryTab: Int,
): Int = when (section) {
    0 -> PROFILE_POSTS_TAB
    1 -> PROFILE_COMMENTS_TAB
    else -> lastLibraryTab.takeIf { it in PROFILE_LIBRARY_DESTINATION_TABS }
        ?: PROFILE_SAVED_TAB
}

// ISSUE58_HISTORY_REENTRY_RELOADS_STATE_ONLY_DATA_V1: the automatic refresh
// runs on the first entry per query key. Comments and the mixed Saved list
// are not in the paging cache; they live only in one coordinator state that
// another tab, user or account replaces, so on re-entry they are fetched
// again when that state is not theirs ("Loading comments…" forever before).
// Post histories keep the paging cache and are not refetched.
internal fun profileHistoryEntryRefreshNeeded(
    firstEntry: Boolean,
    dataLivesOnlyInState: Boolean,
    stateMatches: Boolean,
): Boolean = firstEntry || (dataLivesOnlyInState && !stateMatches)

private fun profileHistoryEntryDecision(firstEntry: Boolean, execute: Boolean): String = when {
    firstEntry -> "execute_first_entry"
    execute -> "execute_reentry_state_replaced"
    else -> "skip_reentry"
}

internal fun profileHistoryKindForTab(tab: Int): RedditAccountHistoryKind? = when (tab) {
    PROFILE_SAVED_TAB -> RedditAccountHistoryKind.SAVED_POSTS
    PROFILE_UPVOTES_TAB -> RedditAccountHistoryKind.UPVOTED
    PROFILE_DOWNVOTES_TAB -> RedditAccountHistoryKind.DOWNVOTED
    PROFILE_HIDDEN_TAB -> RedditAccountHistoryKind.HIDDEN
    else -> null
}

internal fun profileLibraryTabForDestination(tab: Int): Int = when (tab) {
    PROFILE_SAVED_TAB -> 1
    PROFILE_UPVOTES_TAB -> 2
    PROFILE_DOWNVOTES_TAB -> 3
    PROFILE_HIDDEN_TAB -> 4
    else -> 1
}

private fun profileDestinationLabel(tab: Int): String =
    PROFILE_OWN_DESTINATION_LABELS.getOrElse(tab) { "Posts" }

private fun profileDestinationIcon(tab: Int): ImageVector = when (tab) {
    PROFILE_POSTS_TAB -> Symbols.Article
    PROFILE_COMMENTS_TAB -> Symbols.ChatBubble
    PROFILE_SAVED_TAB -> Symbols.Bookmark
    PROFILE_UPVOTES_TAB -> Symbols.ThumbUp
    PROFILE_DOWNVOTES_TAB -> Symbols.ThumbDown
    PROFILE_HIDDEN_TAB -> Symbols.VisibilityOff
    PROFILE_BLOCKED_TAB -> Symbols.PersonOff
    PROFILE_DRAFTS_TAB -> Symbols.Edit
    PROFILE_READING_TAB -> Symbols.Schedule
    else -> Symbols.Article
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    username: String,
    state: SubshellState,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    // NEXT01A6_PUBLIC_USER_SPLIT_BACK_AFFORDANCE_V1
    showBackButton: Boolean = true,
    redditFeedState: RedditFeedUiState = RedditFeedUiState(),
    onRefreshRedditUserSubmitted: (String, String) -> Unit = { _, _ -> },
    onAppendRedditUserSubmitted: (String, String) -> Unit = { _, _ -> },
    redditAccountHistoryState: RedditAccountHistoryUiState =
        RedditAccountHistoryUiState(),
    onRefreshRedditAccountHistory: (String, String, RedditAccountHistoryKind) -> Unit =
        { _, _, _ -> },
    onAppendRedditAccountHistory: (String, String, RedditAccountHistoryKind) -> Unit =
        { _, _, _ -> },
    redditSavedHistoryState: RedditSavedHistoryUiState = RedditSavedHistoryUiState(),
    onRefreshRedditSavedHistory: (String, String) -> Unit = { _, _ -> },
    onAppendRedditSavedHistory: (String, String) -> Unit = { _, _ -> },
    onResolveRedditProfileCommentTarget: (
        String,
        String,
        String,
        (Post?) -> Unit,
    ) -> Unit = { _, _, _, onResolved -> onResolved(null) },
    onResolveRedditSavedCommentTarget: (
        String,
        String,
        String,
        (Post?) -> Unit,
    ) -> Unit = { _, _, _, onResolved -> onResolved(null) },
    onAuthorizeRedditProfileHistory: (String, String) -> Unit = { _, _ -> },
    blockedAccountsState: RedditBlockedAccountsUiState = RedditBlockedAccountsUiState(),
    onRefreshBlockedAccounts: (String) -> Unit = {},
    onAuthorizeBlockedAccounts: (String) -> Unit = {},
    onAppendBlockedAccounts: (String) -> Unit = {},
    // NEXT01A4B_BLOCKED_ACCOUNT_SELECTION_OVERRIDE_V1
    onOpenBlockedAccount: ((String) -> Unit)? = null,
    // NEXT01A4A_YOU_CONTENT_SELECTION_OVERRIDE_V1
    onOpenContentSelection: ((Post, String?) -> Unit)? = null,
    onProfileTabSelected: ((Int) -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(
        start = ScreenMargin,
        top = 4.dp,
        end = ScreenMargin,
        bottom = 32.dp,
    ),
) {
    val user = state.userFor(username)
    val authenticated = state.activeAccountIsAuthenticated
    // ISSUE46_REDDIT_USERNAME_LENGTH_V1: a name Reddit cannot have (an old
    // saved route, a short link) used to crash while building the feed keys.
    if (authenticated && !isValidRedditUsername(username)) {
        Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
            Text("This user can't be shown", style = MaterialTheme.typography.titleMedium)
            Text(
                "u/$username isn't a valid Reddit username.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            onBack?.let { back -> TextButton(onClick = back) { Text("Back") } }
        }
        return
    }
    val identityProfileUnavailable =
        authenticated && state.publicUserProfileUnavailable(username)
    LaunchedEffect(username, authenticated) {
        state.ensureUserIdentity(username)
    }
    val isOwnProfile = user.username.equals(
        state.activeAccountUsername,
        ignoreCase = true,
    )
    val accent = accentFor(username)
    val tabs = profileTabsForSession(
        username = username,
        activeAccountUsername = state.activeAccountUsername,
        authenticated = authenticated,
    )
    val tab = state.userProfileTab(username).coerceIn(0, tabs.lastIndex)
    // NEXT01A4A_CANONICAL_USER_CONTENT_OPEN_V1
    // NEXT01A4A_SAVED_COMMENT_NAMED_IDENTITY_REPAIR_V1
    fun openContentSelection(
        selectedPost: Post,
        commentId: String? = null,
    ) {
        if (onOpenContentSelection != null) {
            onOpenContentSelection(
                selectedPost,
                commentId,
            )
        } else {
            state.openFromUser(
                selectedPost,
                username,
                commentId,
            )
        }
    }
    val activityTab = if (tab in PROFILE_HISTORY_FIRST_TAB..PROFILE_HISTORY_LAST_TAB) {
        tab - PROFILE_HISTORY_FIRST_TAB
    } else {
        0
    }
    val authenticatedRedditUserSubmitted = authenticated && tab == PROFILE_POSTS_TAB
    val authenticatedRedditProfileComments = authenticated && tab == PROFILE_COMMENTS_TAB
    val authenticatedRedditMixedSaved =
        authenticated && isOwnProfile && tab == PROFILE_SAVED_TAB
    val authenticatedRedditBlockedAccounts =
        authenticated && isOwnProfile && tab == PROFILE_BLOCKED_TAB
    val authenticatedRedditAccountHistoryKind =
        when {
            authenticatedRedditProfileComments ->
                RedditAccountHistoryKind.COMMENTS
            authenticated && isOwnProfile && tab != PROFILE_SAVED_TAB ->
                profileHistoryKindForTab(tab)
            else ->
                null
        }
    val authenticatedRedditAccountHistory =
        authenticatedRedditAccountHistoryKind != null
    val authenticatedRedditPostHistory =
        authenticatedRedditAccountHistoryKind?.isPostHistory == true
    val pagingKey = remember(
        state.activeAccountId,
        username,
        tab,
        activityTab,
        authenticatedRedditUserSubmitted,
        authenticatedRedditMixedSaved,
        authenticatedRedditAccountHistoryKind,
    ) {
        when {
            authenticatedRedditUserSubmitted ->
                redditUserSubmittedPostFeedQueryKey(
                    accountStableId = state.activeAccountId,
                    username = username,
                )
            authenticatedRedditMixedSaved ->
                redditMixedSavedHistoryQueryKey(
                    accountStableId = state.activeAccountId,
                    username = username,
                )
            authenticatedRedditAccountHistoryKind != null ->
                redditAccountHistoryQueryKey(
                    accountStableId = state.activeAccountId,
                    username = username,
                    kind = authenticatedRedditAccountHistoryKind,
                )
            !authenticated && isOwnProfile && tab in PROFILE_HISTORY_FIRST_TAB..PROFILE_HISTORY_LAST_TAB ->
                libraryPostFeedQueryKey(profileLibraryTabForDestination(tab))
            else ->
                userPostFeedQueryKey(username, tab, activityTab)
        }
    }
    LaunchedEffect(
        pagingKey,
        authenticated,
        authenticatedRedditUserSubmitted,
        authenticatedRedditPostHistory,
        state.postPagingInvalidationRevision,
    ) {
        if (
            tab != PROFILE_DRAFTS_TAB &&
                tab != PROFILE_BLOCKED_TAB &&
                tab != PROFILE_READING_TAB &&
                (
                    !authenticated ||
                        authenticatedRedditUserSubmitted ||
                        authenticatedRedditPostHistory
                )
        ) {
            state.ensurePostPaging(pagingKey)
        }
    }
    LaunchedEffect(pagingKey, authenticatedRedditUserSubmitted) {
        if (authenticatedRedditUserSubmitted) {
            val execute = state.claimProfileHistoryAutoRefresh(pagingKey)
            uiDebugLog(
                "SubshellProfileHistory",
                "entry_refresh destination=posts decision=" +
                    if (execute) "execute_first_entry" else "skip_reentry",
            )
            if (execute) {
                onRefreshRedditUserSubmitted(
                    state.activeAccountId,
                    username,
                )
            }
        }
    }
    LaunchedEffect(
        pagingKey,
        authenticatedRedditAccountHistoryKind,
        state.activeAccountId,
        username,
    ) {
        authenticatedRedditAccountHistoryKind?.let { kind ->
            val firstEntry = state.claimProfileHistoryAutoRefresh(pagingKey)
            val execute = profileHistoryEntryRefreshNeeded(
                firstEntry = firstEntry,
                dataLivesOnlyInState = !kind.isPostHistory,
                stateMatches = redditAccountHistoryState.matches(
                    accountStableId = state.activeAccountId,
                    username = username,
                    kind = kind,
                ),
            )
            uiDebugLog(
                "SubshellProfileHistory",
                "entry_refresh destination=account_history:${kind.name.lowercase()} decision=" +
                    profileHistoryEntryDecision(firstEntry, execute),
            )
            if (execute) {
                onRefreshRedditAccountHistory(
                    state.activeAccountId,
                    username,
                    kind,
                )
            }
        }
    }
    LaunchedEffect(
        pagingKey,
        authenticatedRedditMixedSaved,
        state.activeAccountId,
        username,
    ) {
        if (authenticatedRedditMixedSaved) {
            val firstEntry = state.claimProfileHistoryAutoRefresh(pagingKey)
            val execute = profileHistoryEntryRefreshNeeded(
                firstEntry = firstEntry,
                dataLivesOnlyInState = true,
                stateMatches = redditSavedHistoryState.matches(
                    accountStableId = state.activeAccountId,
                    username = username,
                ),
            )
            uiDebugLog(
                "SubshellProfileHistory",
                "entry_refresh destination=saved decision=" +
                    profileHistoryEntryDecision(firstEntry, execute),
            )
            if (execute) {
                onRefreshRedditSavedHistory(
                    state.activeAccountId,
                    username,
                )
            }
        }
    }
    // USERACTION01A2_AUTHORITATIVE_BLOCKED_LIST_ENTRY_REFRESH_V1
    LaunchedEffect(
        authenticatedRedditBlockedAccounts,
        state.activeAccountId,
        blockedAccountsState.accountStableId,
        blockedAccountsState.loaded,
        blockedAccountsState.inProgress,
    ) {
        // ISSUE118_BLOCKED_LIST_SURVIVES_ACCOUNT_SWITCH_V1: only a load for
        // this account holds the entry refresh back, and the effect looks
        // again when a load ends.
        if (
            authenticatedRedditBlockedAccounts &&
            !(blockedAccountsState.inProgress && blockedAccountsState.matches(state.activeAccountId)) &&
            (!blockedAccountsState.matches(state.activeAccountId) || !blockedAccountsState.loaded)
        ) {
            onRefreshBlockedAccounts(state.activeAccountId)
        }
    }

    val pagingSnapshot = state.postPagingSnapshot(pagingKey)
    val pagedPosts = pagingSnapshot.items.filter(state::showsPost)
    val profilePosts = if (authenticatedRedditUserSubmitted) {
        pagedPosts
    } else {
        pagedPosts.filter { post ->
            post.author.equals(username, ignoreCase = true)
        }
    }
    val redditAccountHistoryStateMatches =
        authenticatedRedditAccountHistoryKind?.let { kind ->
            redditAccountHistoryState.matches(
                accountStableId = state.activeAccountId,
                username = username,
                kind = kind,
            )
        } == true
    val activeRedditAccountHistoryState =
        if (redditAccountHistoryStateMatches) {
            redditAccountHistoryState
        } else {
            RedditAccountHistoryUiState()
        }
    val activeRedditSavedHistoryState =
        if (
            authenticatedRedditMixedSaved &&
            redditSavedHistoryState.matches(
                accountStableId = state.activeAccountId,
                username = username,
            )
        ) {
            redditSavedHistoryState
        } else {
            RedditSavedHistoryUiState()
        }
    val savedHistoryItems = if (authenticatedRedditMixedSaved) {
        activeRedditSavedHistoryState.items.filter { item ->
            item !is RedditSavedHistoryUiItem.PostItem || state.showsPost(item.post)
        }
    } else {
        emptyList()
    }
    val profileComments = when {
        authenticatedRedditProfileComments ->
            activeRedditAccountHistoryState.comments
        authenticated -> emptyList()
        else -> state.profileCommentsFor(username)
    }
    val localHistoryPosts = if (isOwnProfile && !authenticated) {
        when (tab) {
            PROFILE_SAVED_TAB -> pagedPosts.filter { it.id in state.savedPostIds }
            PROFILE_UPVOTES_TAB -> pagedPosts.filter { state.votes[it.id] == 1 }
            PROFILE_DOWNVOTES_TAB -> pagedPosts.filter { state.votes[it.id] == -1 }
            PROFILE_HIDDEN_TAB -> pagedPosts.filter { it.id in state.hiddenPostIds }
            else -> emptyList()
        }
    } else {
        emptyList()
    }
    val historyPosts = when {
        authenticatedRedditPostHistory -> pagedPosts
        authenticated -> emptyList()
        else -> localHistoryPosts
    }
    val profileDrafts = if (isOwnProfile && tab == PROFILE_DRAFTS_TAB) {
        state.activeAccountPostComposerDrafts
    } else {
        emptyList()
    }
    val knownPublicPostActivity = state.hasPublicProfileActivityEvidence(
        username = username,
        evidence = ProfileActivityEvidence.POST,
    )
    val knownPublicCommentActivity = state.hasPublicProfileActivityEvidence(
        username = username,
        evidence = ProfileActivityEvidence.COMMENT,
    )
    val redditUserSubmittedStateMatches =
        authenticatedRedditUserSubmitted &&
            redditFeedState.matches(
                accountStableId = state.activeAccountId,
                queryKey = pagingKey,
            )
    val activeRedditFeedState = if (redditUserSubmittedStateMatches) {
        redditFeedState
    } else {
        RedditFeedUiState()
    }
    val submittedProfileUnavailable = isUnavailablePublicProfileHttpStatus(
        activeRedditFeedState.failureDetails?.httpStatusCode,
    )
    val commentsProfileUnavailable = isUnavailablePublicProfileHttpStatus(
        activeRedditAccountHistoryState.failureDetails?.httpStatusCode,
    )
    val profileUnavailable =
        identityProfileUnavailable || submittedProfileUnavailable || commentsProfileUnavailable
    val following =
        !isOwnProfile && !profileUnavailable && state.isFollowingUser(user.username)
    val blocked =
        authenticated && !isOwnProfile && !profileUnavailable &&
            state.isUserBlocked(user.username)
    val userBlockInProgress =
        authenticated && state.userBlockInProgress(user.username)
    var showBlockConfirmation by remember(user.username) { mutableStateOf(false) }
    var userTagEditorVisible by remember(user.username) { mutableStateOf(false) }
    val submittedPostsFailure = activeRedditFeedState.failureKind?.takeUnless { failure ->
        failure == RedditFeedUiFailureKind.END_REACHED
    }
    val degradedProfilePostsPresentation = if (
        tab == PROFILE_POSTS_TAB &&
        authenticatedRedditUserSubmitted &&
        submittedPostsFailure != null &&
        profilePosts.isNotEmpty()
    ) {
        val base = if (activeRedditFeedState.failedOperation == RepositoryLoadOperation.APPEND) {
            redditFeedAppendErrorPresentation(
                failureKind = submittedPostsFailure,
                details = activeRedditFeedState.failureDetails,
                retryable = activeRedditFeedState.failureRetryable,
            ).copy(title = "Couldn't load more profile posts")
        } else {
            redditFeedErrorPresentation(
                failureKind = submittedPostsFailure,
                details = activeRedditFeedState.failureDetails,
                retryable = activeRedditFeedState.failureRetryable,
                showingCachedContent = true,
            ).copy(title = "Couldn't refresh posts")
        }
        base.copy(
            message = base.message.replace(
                "Posts already loaded",
                "Profile posts already loaded",
            ),
        )
    } else {
        null
    }
    ReportViewportError(
        scopeKey = "profile-posts:${state.activeAccountId}:$username",
        presentation = degradedProfilePostsPresentation,
        failureDetails = activeRedditFeedState.failureDetails,
        onRetry = if (degradedProfilePostsPresentation != null) {
            if (activeRedditFeedState.failedOperation == RepositoryLoadOperation.APPEND) {
                {
                    onAppendRedditUserSubmitted(
                        state.activeAccountId,
                        username,
                    )
                }
            } else {
                {
                    onRefreshRedditUserSubmitted(
                        state.activeAccountId,
                        username,
                    )
                }
            }
        } else {
            null
        },
    )
    val degradedProfileCommentsPresentation = if (
        tab == PROFILE_COMMENTS_TAB &&
        authenticatedRedditProfileComments &&
        activeRedditAccountHistoryState.failureKind != null &&
        profileComments.isNotEmpty()
    ) {
        redditProfileCommentsErrorPresentation(
            failureKind = requireNotNull(activeRedditAccountHistoryState.failureKind)
                .toProfileCommentsUiFailureKind(),
            details = activeRedditAccountHistoryState.failureDetails,
            retryable = activeRedditAccountHistoryState.failureRetryable,
            showingCachedContent = true,
        )
    } else {
        null
    }
    ReportViewportError(
        scopeKey = "profile-comments:${state.activeAccountId}:$username",
        presentation = degradedProfileCommentsPresentation,
        failureDetails = activeRedditAccountHistoryState.failureDetails,
        onRetry = if (degradedProfileCommentsPresentation != null) {
            {
                onRefreshRedditAccountHistory(
                    state.activeAccountId,
                    username,
                    RedditAccountHistoryKind.COMMENTS,
                )
            }
        } else {
            null
        },
    )
    val liveAppendEnabled = when {
        profileUnavailable -> false
        authenticatedRedditUserSubmitted ->
            canAppendRedditFeed(
                live = true,
                nextCursor = pagingSnapshot.nextCursor,
                state = activeRedditFeedState,
            )
        authenticatedRedditMixedSaved ->
            activeRedditSavedHistoryState.nextCursor != null &&
                !activeRedditSavedHistoryState.inProgress &&
                activeRedditSavedHistoryState.failureKind == null &&
                !activeRedditSavedHistoryState.authorizationRequired &&
                !activeRedditSavedHistoryState.authorizing
        authenticatedRedditPostHistory ->
            pagingSnapshot.nextCursor != null &&
                !activeRedditAccountHistoryState.inProgress &&
                activeRedditAccountHistoryState.failureKind == null &&
                !activeRedditAccountHistoryState.authorizationRequired &&
                !activeRedditAccountHistoryState.authorizing
        else -> false
    }
    val remoteRedditRequestKnown =
        activeRedditFeedState.accountStableId != null
    val showRedditPagingStatus =
        !profileUnavailable &&
            authenticatedRedditUserSubmitted &&
            remoteRedditRequestKnown &&
            (
                activeRedditFeedState.failureKind == RedditFeedUiFailureKind.END_REACHED ||
                    (
                        activeRedditFeedState.failureKind == null &&
                            !activeRedditFeedState.inProgress &&
                            pagingSnapshot.nextCursor == null
                    )
            )
    // LOWUX3_ONE_COMMENTS_AUTHORIZATION_CARD_V1 (OCB-285): with no comments the
    // empty Comments tab shows the same card; the status row only joins a list.
    val showProfileCommentsStatus =
        !profileUnavailable &&
            authenticatedRedditProfileComments &&
            profileComments.isNotEmpty() &&
            (
                activeRedditAccountHistoryState.authorizationRequired ||
                    activeRedditAccountHistoryState.authorizing
            )
    val showAccountHistoryStatus =
        !profileUnavailable &&
            authenticatedRedditPostHistory &&
            historyPosts.isNotEmpty() &&
            (
                activeRedditAccountHistoryState.authorizationRequired ||
                    activeRedditAccountHistoryState.authorizing ||
                    activeRedditAccountHistoryState.failureKind != null
            )
    val showSavedHistoryStatus =
        !profileUnavailable &&
            authenticatedRedditMixedSaved &&
            savedHistoryItems.isNotEmpty() &&
            (
                activeRedditSavedHistoryState.authorizationRequired ||
                    activeRedditSavedHistoryState.authorizing ||
                    activeRedditSavedHistoryState.failureKind != null
            )
    val pagingFooterKey = if (
        profileUnavailable ||
            authenticatedRedditMixedSaved ||
            tab == PROFILE_COMMENTS_TAB ||
            tab == PROFILE_BLOCKED_TAB ||
            tab == PROFILE_DRAFTS_TAB ||
            tab == PROFILE_READING_TAB
    ) {
        null
    } else {
        pagingSnapshot.pagingFooterKey
    }
    // LOWUI8_PROFILE_KEYS_MATCH_THE_ROWS_V1 (OCB-283): the Blocked tab shows
    // only accounts not optimistically unblocked; its keys follow the same list.
    val keyedBlockedAccounts = blockedAccountsState.takeIf {
        it.matches(state.activeAccountId)
    } ?: RedditBlockedAccountsUiState()
    val keyedVisibleBlockedNames = keyedBlockedAccounts.items
        .filter { account -> state.showsAuthoritativeBlockedAccount(account.username) }
        .map { account -> account.username.lowercase() }
    val feedItemKeys = remember(
        username,
        onBack != null,
        profileUnavailable,
        tab,
        activityTab,
        profilePosts,
        profileComments,
        savedHistoryItems,
        historyPosts,
        profileDrafts,
        blockedAccountsState,
        keyedVisibleBlockedNames,
        pagingFooterKey,
        showRedditPagingStatus,
        showProfileCommentsStatus,
        showAccountHistoryStatus,
        showSavedHistoryStatus,
    ) {
        buildList {
            if (onBack != null) add("context-header")
            add("profile-header")
            if (profileUnavailable) {
                add("profile-unavailable")
            } else {
                // OCB-325: the comments status row is emitted once, after the
                // tab's rows (below); it was listed here a second time.
                add("tabs")
            }
            if (!profileUnavailable) when (tab) {
                PROFILE_POSTS_TAB -> if (profilePosts.isEmpty()) add("no-posts")
                else addAll(profilePosts.map { post -> "profile-post-${post.id}" })
                PROFILE_COMMENTS_TAB -> if (profileComments.isEmpty()) add("no-comments")
                else addAll(profileComments.map { comment -> "profile-comment-${comment.id}" })
                PROFILE_SAVED_TAB -> if (authenticatedRedditMixedSaved) {
                    if (savedHistoryItems.isEmpty()) add("no-saved-history")
                    else addAll(
                        savedHistoryItems.map { item ->
                            savedHistoryItemKey("profile-saved", item)
                        },
                    )
                } else {
                    if (historyPosts.isEmpty()) add("no-history-$tab")
                    else addAll(
                        historyPosts.map { post ->
                            "profile-history-$tab-${post.id}"
                        },
                    )
                }
                in PROFILE_HISTORY_FIRST_TAB..PROFILE_HISTORY_LAST_TAB -> {
                    if (historyPosts.isEmpty()) add("no-history-$tab")
                    else addAll(
                        historyPosts.map { post ->
                            "profile-history-$tab-${post.id}"
                        },
                    )
                }
                PROFILE_BLOCKED_TAB -> {
                    val activeBlocked = keyedBlockedAccounts
                    when {
                        activeBlocked.failureKind != null &&
                            keyedVisibleBlockedNames.isEmpty() &&
                            activeBlocked.items.isEmpty() ->
                            add("blocked-accounts-error")
                        !activeBlocked.loaded && activeBlocked.inProgress ->
                            add("blocked-accounts-loading")
                        activeBlocked.loaded && keyedVisibleBlockedNames.isEmpty() ->
                            add("blocked-accounts-empty")
                        else -> {
                            add("blocked-accounts-heading")
                            addAll(keyedVisibleBlockedNames.map { name -> "blocked-account-$name" })
                            if (activeBlocked.failureKind != null && keyedVisibleBlockedNames.isNotEmpty()) {
                                add("blocked-accounts-degraded-error")
                            }
                        }
                    }
                }
            PROFILE_DRAFTS_TAB -> {
                    if (profileDrafts.isEmpty()) add("no-drafts")
                    else addAll(profileDrafts.map { draft -> "profile-draft-${draft.id}" })
                }
            PROFILE_READING_TAB -> if (isOwnProfile) add("reading-stats")
            }
            if (showProfileCommentsStatus) {
                add("profile-comments-status")
            }
            if (showAccountHistoryStatus) {
                add("account-history-status")
            }
            if (showSavedHistoryStatus) {
                add("saved-history-status")
            }
            pagingFooterKey?.let(::add)
            if (showRedditPagingStatus) add("reddit-paging")
        }
    }
    val scrollKey = remember(username, tab, activityTab) {
        userFeedScrollKey(username, tab, activityTab)
    }
    val listState = rememberFeedScrollState(
        state = state,
        scrollKey = scrollKey,
        itemKeys = feedItemKeys,
    )
    val captureCurrentScroll = {
        captureFeedScrollAnchor(
            state = state,
            scrollKey = scrollKey,
            itemKeys = feedItemKeys,
            listState = listState,
        )
    }

    // NEXT01A6_PROFILE_DESTINATION_SCROLL_CONTINUITY_V1
    fun selectProfileDestination(selectedTab: Int) {
        val targetActivityTab =
            if (
                selectedTab in
                PROFILE_HISTORY_FIRST_TAB..PROFILE_HISTORY_LAST_TAB
            ) {
                selectedTab - PROFILE_HISTORY_FIRST_TAB
            } else {
                0
            }
        val targetKey =
            userFeedScrollKey(
                username,
                selectedTab,
                targetActivityTab,
            )

        val currentIndex =
            listState.firstVisibleItemIndex
        val currentAnchor =
            FeedScrollAnchor(
                itemKey =
                    feedItemKeys.getOrNull(currentIndex),
                fallbackIndex =
                    currentIndex,
                pixelOffset =
                    listState.firstVisibleItemScrollOffset,
            )

        val targetSeed =
            profileDestinationScrollSeedOrNull(
                currentAnchor = currentAnchor,
                existingTargetAnchor =
                    state.feedScrollAnchorSnapshot()[targetKey],
            )

        // Freeze the exact source viewport before changing destination.
        captureCurrentScroll()

        // Only first entry into a destination inherits the current viewport.
        // A previously visited destination keeps its own exact scroll history.
        targetSeed?.let { seed ->
            state.captureFeedScrollAnchor(
                key = targetKey,
                itemKey = seed.itemKey,
                fallbackIndex = seed.fallbackIndex,
                pixelOffset = seed.pixelOffset,
            )
        }

        state.selectUserProfileTab(username, selectedTab)
        onProfileTabSelected?.invoke(selectedTab)
    }

    val captureCommentReturn: (String) -> Unit = { commentId ->
        captureCurrentScroll()
        captureExactFeedReturnAnchor(
            state = state,
            scrollKey = scrollKey,
            itemKey = "profile-comment-$commentId",
            listState = listState,
        )
    }
    val captureSavedCommentReturn: (RedditSavedHistoryUiItem) -> Unit = { item ->
        captureCurrentScroll()
        captureExactFeedReturnAnchor(
            state = state,
            scrollKey = scrollKey,
            itemKey = savedHistoryItemKey("profile-saved", item),
            listState = listState,
        )
    }

    val visiblePostCount = when {
        tab == PROFILE_POSTS_TAB -> profilePosts.size
        authenticatedRedditMixedSaved -> savedHistoryItems.size
        tab in PROFILE_HISTORY_FIRST_TAB..PROFILE_HISTORY_LAST_TAB -> historyPosts.size
        else -> 0
    }
    ObserveLiveFeedAutoAppend(
        listState = listState,
        sessionKey = if (authenticatedRedditMixedSaved) {
            "${pagingKey.value}|revision=${activeRedditSavedHistoryState.contentRevision}"
        } else {
            "${pagingKey.value}|generation=${pagingSnapshot.generation}"
        },
        appendEnabled = liveAppendEnabled,
        contentToken = if (authenticatedRedditMixedSaved) {
            activeRedditSavedHistoryState.nextCursor
        } else {
            pagingSnapshot.nextCursor
        },
        loadedItemCount = if (authenticatedRedditMixedSaved) {
            savedHistoryItems.size
        } else {
            pagingSnapshot.items.size
        },
        visibleItemCount = visiblePostCount,
        onAppend = {
            when {
                authenticatedRedditUserSubmitted ->
                    onAppendRedditUserSubmitted(
                        state.activeAccountId,
                        username,
                    )
                authenticatedRedditMixedSaved ->
                    onAppendRedditSavedHistory(
                        state.activeAccountId,
                        username,
                    )
                // LOWUI4 (OCB-284): the Blocked tab pages through
                // RefreshableLazyColumn.onLoadNext (liveAppendEnabled is false
                // there), so it has no branch here.
                authenticatedRedditAccountHistoryKind != null ->
                    onAppendRedditAccountHistory(
                        state.activeAccountId,
                        username,
                        authenticatedRedditAccountHistoryKind,
                    )
            }
        },
    )

    val revealedMediaIds = state.revealedMediaIds.toSet()
    // ISSUE234_FEED_POST_LAYOUTS_V1: profile lists mix posts with comments and
    // keep exact return anchors per post, so Grid shows as List; only Large
    // plays media.
    val profileLayout = state.postLayout.forFeed(gridAllowed = false)
    val mediaPosts = when {
        profileLayout != PostLayout.LARGE -> emptyList()
        tab == PROFILE_POSTS_TAB -> profilePosts
        authenticatedRedditMixedSaved -> savedHistoryMediaPosts(savedHistoryItems)
        tab in PROFILE_HISTORY_FIRST_TAB..PROFILE_HISTORY_LAST_TAB -> historyPosts
        else -> emptyList()
    }

    val videoPostIds = remember(
        mediaPosts,
        revealedMediaIds,
        state.showNsfwMedia,
    ) {
        mediaPosts
            .filter { post ->
                post.kind == PostKind.VIDEO &&
                    state.sensitiveMediaAutoplayAllowed(post)
            }
            .map(Post::id)
    }

    val mediaFeedExhausted = if (authenticatedRedditMixedSaved) {
        activeRedditSavedHistoryState.loaded &&
            activeRedditSavedHistoryState.nextCursor == null
    } else {
        pagingSnapshot.endReached
    }
    val terminalVideoId = remember(
        videoPostIds,
        mediaFeedExhausted,
    ) {
        VideoVisibilityPolicy.selectTerminalVideoId(
            orderedVideoIds = videoPostIds,
            feedExhausted = mediaFeedExhausted,
        )
    }

    // LOWUI8_PROFILE_TAB_SWITCH_REPUBLISHES_THE_VIDEO_CONTEXT_V1 (OCB-326):
    // a tab switch clears the context (DisposableEffect(scrollKey) below);
    // it is published again even when the new tab has the same videos.
    LaunchedEffect(
        scrollKey,
        videoPostIds,
        terminalVideoId,
    ) {
        state.updateVideoFeedContext(
            videoPostIds,
            terminalVideoId,
        )
    }

    ObserveFeedVideoViewport(
        listState = listState,
        state = state,
    )

    val savedPostItemKeysByPostId = remember(savedHistoryItems) {
        savedHistoryItems.mapNotNull { item ->
            (item as? RedditSavedHistoryUiItem.PostItem)?.let { postItem ->
                postItem.post.id to savedHistoryItemKey("profile-saved", postItem)
            }
        }.toMap()
    }
    val animatedPostIdsByItemKey = remember(
        mediaPosts,
        revealedMediaIds,
        state.showNsfwMedia,
        savedPostItemKeysByPostId,
        authenticatedRedditMixedSaved,
    ) {
        mediaPosts
            .filter { post ->
                post.kind in listOf(
                    PostKind.GIF,
                    PostKind.GALLERY,
                ) &&
                    state.sensitiveMediaAutoplayAllowed(post)
            }
            .associate { post ->
                val itemKey = if (authenticatedRedditMixedSaved) {
                    savedPostItemKeysByPostId[post.id]
                        ?: profilePostItemKey(tab, activityTab, post.id)
                } else {
                    profilePostItemKey(tab, activityTab, post.id)
                }
                itemKey to post.id
            }
    }

    LaunchedEffect(
        listState,
        animatedPostIdsByItemKey,
        state.videoAutoplayAllowed,
    ) {
        if (!state.videoAutoplayAllowed) {
            state.setFeedAutoplayAnimatedMedia(null)
        } else {
            snapshotFlow {
                val layoutInfo = listState.layoutInfo

                val item = layoutInfo.visibleItemsInfo
                    .filter { visible ->
                        visible.key in animatedPostIdsByItemKey
                    }
                    .maxByOrNull { visible ->
                        val start =
                            visible.offset.coerceAtLeast(0)
                        val end = (
                            visible.offset + visible.size
                        ).coerceAtMost(
                            layoutInfo.viewportEndOffset,
                        )

                        (end - start).coerceAtLeast(0)
                    }

                val itemKey = item?.key as? String
                itemKey?.let(animatedPostIdsByItemKey::get)
            }
                .distinctUntilChanged()
                .collect(
                    state::setFeedAutoplayAnimatedMedia,
                )
        }
    }

    // PARITY01_FEED_SURFACE_CLEARS_ONLY_WHAT_IT_OWNS_V1: a tab switch still
    // clears (the old tab releases before the new one claims).
    DisposableEffect(scrollKey) {
        val owner = Any()
        state.claimFeedSurface(owner)
        onDispose { state.releaseFeedSurface(owner) }
    }

    val profileCommentCommunities = remember(profileComments, savedHistoryItems) {
        val savedComments = savedHistoryItems.mapNotNull { item ->
            (item as? RedditSavedHistoryUiItem.CommentItem)?.comment
        }
        (profileComments + savedComments)
            .mapNotNull { comment ->
                comment.community
                    ?.trim()
                    ?.removePrefix("/r/")
                    ?.removePrefix("r/")
                    ?.takeIf(String::isNotEmpty)
            }
            .distinctBy(String::lowercase)
    }
    LaunchedEffect(profileCommentCommunities, state.activeAccountIsAuthenticated) {
        if (profileCommentCommunities.isNotEmpty()) {
            state.ensureCommunityIdentities(profileCommentCommunities)
        }
    }

    if (showBlockConfirmation) {
        UserBlockConfirmationDialog(
            username = user.username,
            onDismiss = { showBlockConfirmation = false },
            onConfirm = {
                showBlockConfirmation = false
                state.setUserBlocked(user.username, blocked = true)
            },
        )
    }

    // ISSUE34_USER_TAGS_V1
    if (userTagEditorVisible) {
        UserTagEditorDialog(
            username = user.username,
            existing = state.userTagFor(user.username),
            onDismiss = { userTagEditorVisible = false },
            onSave = { tag ->
                state.setUserTag(user.username, tag)
                userTagEditorVisible = false
            },
        )
    }

    // M3223_ONE_ROUTE_TOP_BAR_V1: another user's Back, name and Follow/Block stay
    // reachable while the profile scrolls (they were the list's first row).
    // STEP3_NO_BAR_NO_BAR_SCROLL_V1: the own profile has no route bar; a bar
    // that was never laid out has no height limit, so its enterAlways
    // connection would take every upward drag and the list could not scroll.
    val routeTopBarScroll = rememberRouteTopBarScrollBehavior(
        canScroll = {
            onBack != null && (listState.canScrollForward || listState.canScrollBackward)
        },
    )
    Column(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(routeTopBarScroll.nestedScrollConnection),
    ) {
        if (onBack != null) {
            RouteTopBar(
                title = "u/${user.username}",
                scrollBehavior = routeTopBarScroll,
                onBack = if (showBackButton) onBack else null,
                actions = {
                    if (!isOwnProfile && !profileUnavailable) {
                        if (authenticated) {
                            if (blocked) {
                                FilledTonalButton(
                                    onClick = {
                                        state.setUserBlocked(
                                            username = user.username,
                                            blocked = false,
                                        )
                                    },
                                    enabled = state.userBlockControlEnabled(user.username),
                                    modifier = Modifier.height(40.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp),
                                ) {
                                    Icon(
                                        Symbols.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(17.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (userBlockInProgress) "Blocking…" else "Unblock")
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { showBlockConfirmation = true },
                                    enabled = state.userBlockControlEnabled(user.username),
                                    modifier = Modifier.height(40.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp),
                                ) {
                                    Icon(
                                        Symbols.Block,
                                        contentDescription = null,
                                        modifier = Modifier.size(17.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (userBlockInProgress) "Unblocking…" else "Block")
                                }
                            }
                        } else if (following) {
                            FilledTonalButton(
                                onClick = { state.toggleFollowUser(user.username) },
                                enabled = state.localMutationsAllowed,
                                modifier = Modifier.height(40.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp),
                            ) {
                                Icon(
                                    Symbols.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Following")
                            }
                        } else {
                            Button(
                                onClick = { state.toggleFollowUser(user.username) },
                                enabled = state.localMutationsAllowed,
                                modifier = Modifier.height(40.dp),
                                contentPadding = PaddingValues(horizontal = 17.dp),
                            ) {
                                Text("Follow")
                            }
                        }
                    }
                },
            )
        }
        RefreshableLazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            // ISSUE205_PASSIVE_SCROLL_POSITION_INDICATOR_V1
            scrollPositionIndicator = state.scrollPositionIndicator,
            contentPadding = contentPadding,
            indicatorTopPadding = contentPadding.calculateTopPadding(),
            verticalArrangement = Arrangement.spacedBy(9.dp),
            enabled =
                !profileUnavailable &&
                    tab != PROFILE_DRAFTS_TAB &&
                    (
                        !authenticated ||
                            authenticatedRedditUserSubmitted ||
                            authenticatedRedditProfileComments ||
                            authenticatedRedditMixedSaved ||
                            authenticatedRedditBlockedAccounts ||
                            authenticatedRedditAccountHistory
                    ),
            isRefreshing = when {
                profileUnavailable -> false
                authenticatedRedditUserSubmitted ->
                    activeRedditFeedState.refreshing
                authenticatedRedditProfileComments ->
                    activeRedditAccountHistoryState.refreshing
                authenticatedRedditMixedSaved ->
                    activeRedditSavedHistoryState.inProgressOperation ==
                        RepositoryLoadOperation.REFRESH
                authenticatedRedditBlockedAccounts ->
                    blockedAccountsState.matches(state.activeAccountId) &&
                        blockedAccountsState.inProgressOperation == RepositoryLoadOperation.REFRESH
                authenticatedRedditAccountHistory ->
                    activeRedditAccountHistoryState.refreshing
                else ->
                    !authenticated && pagingSnapshot.refreshing
            },
            onRefresh = when {
                profileUnavailable -> null
                authenticatedRedditUserSubmitted -> {
                    {
                        onRefreshRedditUserSubmitted(
                            state.activeAccountId,
                            username,
                        )
                    }
                }
                authenticatedRedditProfileComments -> {
                    {
                        onRefreshRedditAccountHistory(
                            state.activeAccountId,
                            username,
                            RedditAccountHistoryKind.COMMENTS,
                        )
                    }
                }
                authenticatedRedditMixedSaved -> {
                    {
                        onRefreshRedditSavedHistory(
                            state.activeAccountId,
                            username,
                        )
                    }
                }
                authenticatedRedditBlockedAccounts -> {
                    { onRefreshBlockedAccounts(state.activeAccountId) }
                }
                authenticatedRedditAccountHistoryKind != null -> {
                    {
                        onRefreshRedditAccountHistory(
                            state.activeAccountId,
                            username,
                            authenticatedRedditAccountHistoryKind,
                        )
                    }
                }
                authenticated -> null
                else -> if (tab != PROFILE_DRAFTS_TAB && tab != PROFILE_BLOCKED_TAB) {
                    { state.refreshPostPaging(pagingKey) }
                } else {
                    null
                }
            },
            loadNextEnabled = when {
                profileUnavailable -> false
                authenticatedRedditUserSubmitted ->
                    liveAppendEnabled
                authenticatedRedditProfileComments ->
                    activeRedditAccountHistoryState.nextCursor != null &&
                        !activeRedditAccountHistoryState.inProgress &&
                        !activeRedditAccountHistoryState.authorizationRequired &&
                        activeRedditAccountHistoryState.failureKind == null
                authenticatedRedditMixedSaved -> liveAppendEnabled
                authenticatedRedditBlockedAccounts ->
                    blockedAccountsState.matches(state.activeAccountId) &&
                        blockedAccountsState.nextCursor != null &&
                        !blockedAccountsState.inProgress &&
                        blockedAccountsState.failureKind == null
                authenticatedRedditAccountHistory ->
                    liveAppendEnabled
                authenticated -> false
                else -> tab != PROFILE_COMMENTS_TAB && tab != PROFILE_DRAFTS_TAB && pagingSnapshot.canAppend
            },
            loadNextToken = when {
                authenticatedRedditProfileComments ->
                    activeRedditAccountHistoryState.nextCursor
                authenticatedRedditMixedSaved ->
                    activeRedditSavedHistoryState.nextCursor
                authenticatedRedditBlockedAccounts ->
                    blockedAccountsState.nextCursor
                else -> pagingSnapshot.items.size
            },
            onLoadNext = {
                when {
                    authenticatedRedditUserSubmitted ->
                        onAppendRedditUserSubmitted(
                            state.activeAccountId,
                            username,
                        )
                    authenticatedRedditProfileComments ->
                        onAppendRedditAccountHistory(
                            state.activeAccountId,
                            username,
                            RedditAccountHistoryKind.COMMENTS,
                        )
                    authenticatedRedditMixedSaved ->
                        onAppendRedditSavedHistory(
                            state.activeAccountId,
                            username,
                        )
                    authenticatedRedditBlockedAccounts ->
                        onAppendBlockedAccounts(state.activeAccountId)
                    authenticatedRedditAccountHistoryKind != null ->
                        onAppendRedditAccountHistory(
                            state.activeAccountId,
                            username,
                            authenticatedRedditAccountHistoryKind,
                        )
                    !authenticated ->
                        state.appendPostPaging(pagingKey)
                }
            },
        ) {
            if (onBack != null) {
                // An empty anchor: feedItemKeys and the stored scroll anchors count it
                // (M3223_ONE_ROUTE_TOP_BAR_V1 moved its contents to RouteTopBar).
                item("context-header") {}
            }

            item("profile-header") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column {
                        // ISSUE218_PROFILE_NAME_NEVER_OVER_THE_BANNER_V1: the name sat bottom-aligned
                        // in a fixed 156 dp box, so it rose onto the banner's dark fade
                        // (1.1–1.3:1 in light theme at large text). Banner and name are now
                        // stacked; the avatar still overlaps the banner's lower edge.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(112.dp),
                        ) {
                            val bannerResource = mediaResource(user.bannerAsset)
                                .takeUnless { profileUnavailable }
                            if (!profileUnavailable && user.bannerImageUri != null) {
                                RemoteIdentityImage(
                                    uri = user.bannerImageUri,
                                    contentDescription = null, // decorative beside the visible name (ISSUE219)
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(112.dp),
                                ) {
                                    ProfileBannerPlaceholder(
                                        accent = accent,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            } else if (bannerResource != null) {
                                Image(
                                    painter = painterResource(bannerResource),
                                    contentDescription = null, // decorative beside the visible name (ISSUE219)
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(112.dp),
                                )
                            } else {
                                ProfileBannerPlaceholder(
                                    accent = accent,
                                    label = if (profileUnavailable) null else "No profile banner",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(112.dp),
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(112.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.68f)),
                                        ),
                                    ),
                            )
                        }
                        // The 80 dp avatar hangs 40 dp into the banner; the row keeps room
                        // for its lower half when the name is short.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp),
                        ) {
                            Surface(
                                // The upper 40 dp hang into the banner and are not part of
                                // this row's height (an offset alone kept the full 80 dp
                                // and left a gap under the name).
                                modifier = Modifier
                                    .offset(x = 14.dp)
                                    .layout { measurable, constraints ->
                                        val placeable = measurable.measure(constraints)
                                        val overhang = 40.dp.roundToPx()
                                        layout(placeable.width, placeable.height - overhang) {
                                            placeable.place(0, -overhang)
                                        }
                                    },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainer,
                            ) {
                                RemoteIdentityImage(
                                    uri = user.avatarImageUri.takeUnless { profileUnavailable },
                                    contentDescription = null, // decorative beside the visible name (ISSUE219)
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .size(72.dp)
                                        .clip(CircleShape),
                                ) {
                                    RedditDefaultProfileAvatar(
                                        seed = user.username,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier.padding(
                                    start = 104.dp,
                                    top = 6.dp,
                                    end = 14.dp,
                                ),
                            ) {
                                Text("u/${user.username}", style = MaterialTheme.typography.headlineSmall)
                                Text(
                                    if (profileUnavailable) {
                                        "Unavailable on Reddit"
                                    } else {
                                        "Redditor for ${user.accountAge}"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Text(
                            if (profileUnavailable) "" else user.description,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (state.canTagUser(user.username)) {
                            UserTagProfileSection(
                                tag = state.userTagFor(user.username),
                                onEdit = { userTagEditorVisible = true },
                                modifier = Modifier.padding(start = 16.dp, end = 8.dp, bottom = 8.dp),
                            )
                        }
                        // ISSUE220_PROFILE_STATS_WRAP_AS_WHOLE_ITEMS_V1: at large text a
                        // stat moves to the next line whole, so "account age" and
                        // "followers" neither run together nor break mid-word.
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, bottom = 16.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ProfileStat(
                                if (profileUnavailable) "—" else user.karma.removeSuffix(" karma"),
                                "karma",
                                Modifier.padding(horizontal = 8.dp),
                            )
                            ProfileStat(
                                if (profileUnavailable) "—" else user.accountAge,
                                "account age",
                                Modifier.padding(horizontal = 8.dp),
                            )
                            ProfileStat(
                                if (profileUnavailable) "—" else followerCountLabel(user.followers, following),
                                "followers",
                                Modifier.padding(horizontal = 8.dp),
                            )
                        }
                    }
                }
            }

            if (profileUnavailable) {
                item("profile-unavailable") {
                    val presentation = profileUnavailablePresentation()
                    ErrorNotice(
                        presentation = ErrorPresentation(
                            kind = ErrorPresentationKind.INLINE,
                            title = presentation.title,
                            message = presentation.description,
                        ),
                        onAction = {},
                    )
                }
            } else {
                item("tabs") {
                    if (isOwnProfile && tabs.size > 2) {
                        OwnProfileDestinationStrip(
                            profileKey = username,
                            selectedTab = tab,
                            onSelect = { selectedTab ->
                                selectProfileDestination(selectedTab)
                            },
                        )
                    } else {
                        ProfileDestinationStrip(
                            destinations = tabs,
                            selectedTab = tab,
                            onSelect = { selectedTab ->
                                selectProfileDestination(selectedTab)
                            },
                        )
                    }
                }
            }

            if (!profileUnavailable) when (tab) {
                PROFILE_POSTS_TAB -> if (profilePosts.isEmpty()) {
                    item("no-posts") {
                        if (authenticatedRedditUserSubmitted) {
                            if (submittedPostsFailure != null) {
                                ErrorNotice(
                                    presentation = redditFeedErrorPresentation(
                                        failureKind = submittedPostsFailure,
                                        details = activeRedditFeedState.failureDetails,
                                        retryable = activeRedditFeedState.failureRetryable,
                                        showingCachedContent = false,
                                    ).copy(title = "Couldn't load profile posts"),
                                    diagnosticScopeKey = "profile_posts",
                                    onAction = { action ->
                                        if (action == ErrorActionKind.RETRY) {
                                            onRefreshRedditUserSubmitted(
                                                state.activeAccountId,
                                                username,
                                            )
                                        }
                                    },
                                )
                            } else {
                                val presentation = when {
                                    activeRedditFeedState.inProgress ->
                                        ProfileEmptyPresentation(
                                            title = "Loading posts…",
                                            description = "Fetching submitted posts for u/$username.",
                                        )
                                    knownPublicPostActivity ->
                                        profilePrivacyEmptyPresentation()
                                    else ->
                                        publicProfileEmptyPresentation(
                                            username = username,
                                            activity = ProfileActivityEvidence.POST,
                                        )
                                }
                                EmptyProfileTab(
                                    presentation.title,
                                    presentation.description,
                                )
                            }
                        } else {
                            EmptyProfileTab(
                                "No local posts",
                                "This user has no post fixtures yet.",
                            )
                        }
                    }
                } else {
                    items(profilePosts, key = { "profile-post-${it.id}" }) { post ->
                        PostCard(
                            post = post,
                            state = state,
                            layout = profileLayout,
                            onOpen = {
                                openContentSelection(
                                    post,
                                    null,
                                )
                            },
                            beforeNavigation = captureCurrentScroll,
                        )
                    }
                }
                PROFILE_COMMENTS_TAB -> if (profileComments.isEmpty()) {
                    item("no-comments") {
                        if (authenticatedRedditProfileComments) {
                            when {
                                activeRedditAccountHistoryState.authorizationRequired ||
                                    activeRedditAccountHistoryState.authorizing ->
                                    ProfileCommentsStatus(
                                        state =
                                            activeRedditAccountHistoryState,
                                        onAuthorize = {
                                            onAuthorizeRedditProfileHistory(
                                                state.activeAccountId,
                                                username,
                                            )
                                        },
                                        onRetry = {
                                            onRefreshRedditAccountHistory(
                                                state.activeAccountId,
                                                username,
                                                RedditAccountHistoryKind.COMMENTS,
                                            )
                                        },
                                    )

                                activeRedditAccountHistoryState.failureKind != null ->
                                    ProfileCommentsStatus(
                                        state =
                                            activeRedditAccountHistoryState,
                                        onAuthorize = {
                                            onAuthorizeRedditProfileHistory(
                                                state.activeAccountId,
                                                username,
                                            )
                                        },
                                        onRetry = {
                                            onRefreshRedditAccountHistory(
                                                state.activeAccountId,
                                                username,
                                                RedditAccountHistoryKind.COMMENTS,
                                            )
                                        },
                                    )

                                activeRedditAccountHistoryState.loaded -> {
                                    val presentation = if (knownPublicCommentActivity) {
                                        profilePrivacyEmptyPresentation()
                                    } else {
                                        publicProfileEmptyPresentation(
                                            username = username,
                                            activity = ProfileActivityEvidence.COMMENT,
                                        )
                                    }
                                    EmptyProfileTab(
                                        presentation.title,
                                        presentation.description,
                                    )
                                }

                                else ->
                                    EmptyProfileTab(
                                        "Loading comments…",
                                        "Fetching Reddit comments for u/$username.",
                                    )
                            }
                        } else {
                            EmptyProfileTab(
                                "No local comments",
                                "This user has no comment fixtures yet.",
                            )
                        }
                    }
                } else {
                    items(
                        profileComments,
                        key = { "profile-comment-${it.id}" },
                    ) { comment ->
                        val relatedPost =
                            if (authenticatedRedditProfileComments) {
                                null
                            } else {
                                state.posts.firstOrNull {
                                    it.id == comment.postId
                                }
                            }
                        val onOpen: (() -> Unit)? =
                            if (authenticatedRedditProfileComments) {
                                {
                                    captureCommentReturn(comment.id)
                                    onResolveRedditProfileCommentTarget(
                                        state.activeAccountId,
                                        username,
                                        comment.postId,
                                    ) { post ->
                                        if (post != null) {
                                            uiDebugLog(
                                                "SubshellCommentNav",
                                                "phase=profile_open post=${post.id} target=${comment.id}",
                                            )
                                            openContentSelection(
                                                post,
                                                comment.id,
                                            )
                                        }
                                    }
                                }
                            } else {
                                relatedPost?.let { post ->
                                    {
                                        captureCommentReturn(comment.id)
                                        uiDebugLog(
                                            "SubshellCommentNav",
                                            "phase=profile_open post=${post.id} target=${comment.id}",
                                        )
                                        openContentSelection(
                                            post,
                                            comment.id,
                                        )
                                    }
                                }
                            }
                        ProfileComment(
                            comment = comment,
                            relatedPost = relatedPost,
                            state = state,
                            onOpen = onOpen,
                        )
                    }
                }
                PROFILE_SAVED_TAB -> if (authenticatedRedditMixedSaved) {
                    if (savedHistoryItems.isEmpty()) {
                        item("no-saved-history") {
                            when {
                                activeRedditSavedHistoryState.authorizationRequired ||
                                    activeRedditSavedHistoryState.authorizing ||
                                    activeRedditSavedHistoryState.failureKind != null ->
                                    SavedHistoryStatus(
                                        state = activeRedditSavedHistoryState,
                                        onAuthorize = {
                                            onAuthorizeRedditProfileHistory(
                                                state.activeAccountId,
                                                username,
                                            )
                                        },
                                        onRetry = {
                                            onRefreshRedditSavedHistory(
                                                state.activeAccountId,
                                                username,
                                            )
                                        },
                                    )

                                activeRedditSavedHistoryState.inProgress ->
                                    EmptyProfileTab(
                                        "Loading saved posts and comments…",
                                        "Fetching Reddit Saved for u/$username.",
                                    )

                                activeRedditSavedHistoryState.loaded ->
                                    EmptyProfileTab(
                                        "No saved posts or comments",
                                        "Saved posts and comments will appear here.",
                                    )

                                // ISSUE117_SAVED_ROW_NEVER_BLANK_V1: not loaded,
                                // not loading and no failure (another account's
                                // state, or the entry refresh has not started yet)
                                // was a blank row; ISSUE58 refetches it, so it is
                                // shown as loading.
                                else ->
                                    EmptyProfileTab(
                                        "Loading saved posts and comments…",
                                        "Fetching Reddit Saved for u/$username.",
                                    )
                            }
                        }
                    } else {
                        items(
                            savedHistoryItems,
                            key = { item -> savedHistoryItemKey("profile-saved", item) },
                        ) { item ->
                            when (item) {
                                is RedditSavedHistoryUiItem.PostItem ->
                                    PostCard(
                                        post = item.post,
                                        state = state,
                                        layout = profileLayout,
                                        onOpen = {
                                            openContentSelection(
                                                item.post,
                                                null,
                                            )
                                        },
                                        beforeNavigation = captureCurrentScroll,
                                    )

                                is RedditSavedHistoryUiItem.CommentItem -> {
                                    val target = item.savedCommentNavigationTarget()
                                    ProfileComment(
                                        comment = item.comment,
                                        relatedPost = null,
                                        state = state,
                                        saved = true,
                                        onOpen = target?.let { resolvedTarget ->
                                            {
                                                captureSavedCommentReturn(item)
                                                onResolveRedditSavedCommentTarget(
                                                    state.activeAccountId,
                                                    username,
                                                    resolvedTarget.postId,
                                                ) { post ->
                                                    if (post != null) {
                                                        openContentSelection(
                                                            selectedPost = post,
                                                            commentId = resolvedTarget.commentId,
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                } else {
                    if (historyPosts.isEmpty()) {
                        item("no-history-$tab") {
                            val label = profileDestinationLabel(tab)
                            EmptyProfileTab(
                                "No ${label.lowercase()}",
                                "No ${label.lowercase()} posts are available in this local account.",
                            )
                        }
                    } else {
                        items(
                            historyPosts,
                            key = { "profile-history-$tab-${it.id}" },
                        ) { post ->
                            PostCard(
                                post = post,
                                state = state,
                                layout = profileLayout,
                                onOpen = {
                                    openContentSelection(
                                        post,
                                        null,
                                    )
                                },
                                beforeNavigation = captureCurrentScroll,
                                knownHidden = tab == PROFILE_HIDDEN_TAB,
                            )
                        }
                    }
                }
                in PROFILE_HISTORY_FIRST_TAB..PROFILE_HISTORY_LAST_TAB -> {
                    if (historyPosts.isEmpty()) {
                        item("no-history-$tab") {
                            if (
                                authenticatedRedditAccountHistoryKind != null
                            ) {
                                when {
                                    activeRedditAccountHistoryState.authorizationRequired ||
                                        activeRedditAccountHistoryState.authorizing ||
                                        activeRedditAccountHistoryState.failureKind != null ->
                                        AccountHistoryStatus(
                                            state = activeRedditAccountHistoryState,
                                            kind = authenticatedRedditAccountHistoryKind,
                                            onAuthorize = {
                                                onAuthorizeRedditProfileHistory(
                                                    state.activeAccountId,
                                                    username,
                                                )
                                            },
                                            onRetry = {
                                                onRefreshRedditAccountHistory(
                                                    state.activeAccountId,
                                                    username,
                                                    authenticatedRedditAccountHistoryKind,
                                                )
                                            },
                                        )
                                    activeRedditAccountHistoryState.inProgress ->
                                        EmptyProfileTab(
                                            "Loading ${authenticatedRedditAccountHistoryKind.activityLabel.lowercase()}…",
                                            "Fetching Reddit account history for u/$username.",
                                        )
                                    else ->
                                        EmptyProfileTab(
                                            "No ${authenticatedRedditAccountHistoryKind.activityLabel.lowercase()} posts",
                                            authenticatedRedditAccountHistoryKind.emptyActivityMessage,
                                        )
                                }
                            } else {
                                val label = profileDestinationLabel(tab)
                                EmptyProfileTab(
                                    "No ${label.lowercase()}",
                                    "No ${label.lowercase()} posts are available in this local account.",
                                )
                            }
                        }
                    } else {
                        items(
                            historyPosts,
                            key = { "profile-history-$tab-${it.id}" },
                        ) { post ->
                            PostCard(
                                post = post,
                                state = state,
                                layout = profileLayout,
                                onOpen = {
                                    openContentSelection(
                                        post,
                                        null,
                                    )
                                },
                                beforeNavigation = captureCurrentScroll,
                                // PROFILEHIDDEN01_LOCAL_HIDDEN_TAB_SHOWS_ITS_POSTS_V1: the
                                // Hidden tab lists hidden posts for a local account too
                                // (it asked only the signed-in history kind, so a local
                                // hidden card dropped itself and the tab stayed blank).
                                knownHidden = tab == PROFILE_HIDDEN_TAB,
                            )
                        }
                    }
                }
                PROFILE_BLOCKED_TAB -> {
                    val activeBlockedAccountsState = if (
                        blockedAccountsState.matches(state.activeAccountId)
                    ) {
                        blockedAccountsState
                    } else {
                        RedditBlockedAccountsUiState()
                    }
                    val visibleBlockedAccounts = activeBlockedAccountsState.items.filter { account ->
                        state.showsAuthoritativeBlockedAccount(account.username)
                    }
                    when {
                        activeBlockedAccountsState.failureKind != null &&
                            visibleBlockedAccounts.isEmpty() &&
                            activeBlockedAccountsState.items.isEmpty() -> {
                            item("blocked-accounts-error") {
                                BlockedAccountsError(
                                    state = activeBlockedAccountsState,
                                    onRetry = { onRefreshBlockedAccounts(state.activeAccountId) },
                                    onAuthorize = { onAuthorizeBlockedAccounts(state.activeAccountId) },
                                )
                            }
                        }

                        !activeBlockedAccountsState.loaded &&
                            activeBlockedAccountsState.inProgress -> {
                            item("blocked-accounts-loading") {
                                EmptyProfileTab(
                                    "Loading blocked accounts…",
                                    "Fetching your authoritative blocked-account list from Reddit.",
                                )
                            }
                        }

                        activeBlockedAccountsState.loaded &&
                            visibleBlockedAccounts.isEmpty() -> {
                            item("blocked-accounts-empty") {
                                EmptyProfileTab(
                                    "No blocked accounts",
                                    "Accounts you block on Reddit will appear here.",
                                )
                            }
                        }

                        else -> {
                            item("blocked-accounts-heading") {
                                Column(
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Text(
                                        "Blocked accounts",
                                        style = MaterialTheme.typography.titleLarge,
                                    )
                                    Text(
                                        "Manage people you've blocked on Reddit.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            items(
                                visibleBlockedAccounts,
                                key = { account -> "blocked-account-${account.username.lowercase()}" },
                            ) { account ->
                                BlockedAccountRow(
                                    account = account,
                                    inProgress = state.userBlockInProgress(account.username),
                                    onOpen = onOpenBlockedAccount?.let { callback ->
                                        { callback(account.username) }
                                    },
                                    onUnblock = {
                                        state.setUserBlocked(account.username, blocked = false)
                                    },
                                )
                            }
                            if (
                                activeBlockedAccountsState.failureKind != null &&
                                visibleBlockedAccounts.isNotEmpty()
                            ) {
                                item("blocked-accounts-degraded-error") {
                                    BlockedAccountsError(
                                        state = activeBlockedAccountsState,
                                        onRetry = { onRefreshBlockedAccounts(state.activeAccountId) },
                                        onAuthorize = { onAuthorizeBlockedAccounts(state.activeAccountId) },
                                    )
                                }
                            }
                        }
                    }
                }
                PROFILE_DRAFTS_TAB -> {
                    if (profileDrafts.isEmpty()) {
                        item("no-drafts") {
                            EmptyProfileTab(
                                "No drafts",
                                "Leaving Create saves anything you have started here.",
                            )
                        }
                    } else {
                        items(
                            profileDrafts,
                            key = { "profile-draft-${it.id}" },
                        ) { draft ->
                            // NEXT01A4C_DRAFT_CARD_ADAPTIVE_SELECTION_V1
                            ProfileDraftCard(
                                draft = draft,
                                onOpen = {
                                    if (
                                        !state.replaceCurrentPostComposerDraftSelection(
                                            draft.id,
                                        )
                                    ) {
                                        state.openPostComposerDraft(draft.id)
                                    }
                                },
                                onDelete = {
                                    if (
                                        !state.deleteCurrentPostComposerDraftSelection(
                                            draft.id,
                                        )
                                    ) {
                                        state.deletePostComposerDraft(draft.id)
                                    }
                                },
                            )
                        }
                    }
                }
                PROFILE_READING_TAB -> if (isOwnProfile) {
                    item("reading-stats") { ReadingStatsPanel(state) }
                }
            }

            if (showProfileCommentsStatus) {
                item("profile-comments-status") {
                    ProfileCommentsStatus(
                        state = activeRedditAccountHistoryState,
                        onAuthorize = {
                            onAuthorizeRedditProfileHistory(
                                state.activeAccountId,
                                username,
                            )
                        },
                        onRetry = {
                            onRefreshRedditAccountHistory(
                                state.activeAccountId,
                                username,
                                RedditAccountHistoryKind.COMMENTS,
                            )
                        },
                    )
                }
            }

            if (showAccountHistoryStatus) {
                authenticatedRedditAccountHistoryKind.let { accountHistoryKind ->
                    item("account-history-status") {
                        AccountHistoryStatus(
                            state = activeRedditAccountHistoryState,
                            kind = accountHistoryKind,
                            onAuthorize = {
                                onAuthorizeRedditProfileHistory(
                                    state.activeAccountId,
                                    username,
                                )
                            },
                            onRetry = {
                                onRefreshRedditAccountHistory(
                                    state.activeAccountId,
                                    username,
                                    accountHistoryKind,
                                )
                            },
                        )
                    }
                }
            }
            if (showSavedHistoryStatus) {
                item("saved-history-status") {
                    SavedHistoryStatus(
                        state = activeRedditSavedHistoryState,
                        onAuthorize = {
                            onAuthorizeRedditProfileHistory(
                                state.activeAccountId,
                                username,
                            )
                        },
                        onRetry = {
                            onRefreshRedditSavedHistory(
                                state.activeAccountId,
                                username,
                            )
                        },
                    )
                }
            }

            if (pagingFooterKey != null) {
                item(key = pagingFooterKey) {
                    RepositoryPagingFooter(
                        snapshot = pagingSnapshot,
                        onRetry = { state.retryPostPaging(pagingKey) },
                    )
                }
            }

            if (showRedditPagingStatus) {
                item("reddit-paging") {
                    RedditPagingControl(
                        state = activeRedditFeedState,
                        hasPosts = profilePosts.isNotEmpty(),
                        canAppend = pagingSnapshot.nextCursor != null,
                        onRefresh = {
                            onRefreshRedditUserSubmitted(
                                state.activeAccountId,
                                username,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun OwnProfileDestinationStrip(
    profileKey: String,
    selectedTab: Int,
    onSelect: (Int) -> Unit,
) {
    // ISSUE268: the Reading tab only while "Your reading" is on.
    val readingStatsEnabled by app.subshell.prototype.data.reading.ReadingStats
        .store(androidx.compose.ui.platform.LocalContext.current)
        .enabled
        .collectAsStateWithLifecycle()
    val libraryTabs = PROFILE_LIBRARY_DESTINATION_TABS.filter { destinationTab ->
        destinationTab != PROFILE_READING_TAB || readingStatsEnabled == true || selectedTab == PROFILE_READING_TAB
    }
    var lastLibraryTab by rememberSaveable(profileKey) {
        mutableIntStateOf(
            selectedTab.takeIf { it in PROFILE_LIBRARY_DESTINATION_TABS }
                ?: PROFILE_SAVED_TAB,
        )
    }
    LaunchedEffect(selectedTab) {
        if (selectedTab in PROFILE_LIBRARY_DESTINATION_TABS) {
            lastLibraryTab = selectedTab
        }
    }

    val selectedPrimary = profilePrimarySectionForTab(selectedTab)
    // ISSUE220_TABS_SCROLL_INSTEAD_OF_SHRINKING_V1: at large text the three
    // labels ("Comme…", "Libra…") no longer fit equal slots; the row then
    // scrolls with each tab at its own width instead of cutting the labels.
    var primaryRowWidth by remember { mutableStateOf(0.dp) }
    val primaryRowDensity = LocalDensity.current
    val primaryLabelsFit = rememberLabelsFitSlots(
        labels = PROFILE_PRIMARY_DESTINATION_LABELS,
        // A LeadingIconTab spends 16 + 24 + 8 + 16 dp around its titleSmall label.
        slotWidth = primaryRowWidth / PROFILE_PRIMARY_DESTINATION_LABELS.size - 64.dp,
        style = MaterialTheme.typography.titleSmall,
    )
    // M3225_PROFILE_TABS_ARE_M3_TAB_ROWS_V1 (#225, reading-02): the primary row
    // is an M3 primary tab row with icon + label tabs, the Library row an M3
    // secondary tab row (selected tab named, the others icon-only); the
    // components bring the indicator, its animation and the divider.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { size ->
                primaryRowWidth = with(primaryRowDensity) { size.width.toDp() }
            },
    ) {
        val primaryTabs: @Composable () -> Unit = {
            PROFILE_PRIMARY_DESTINATION_LABELS.forEachIndexed { section, label ->
                val selected = section == selectedPrimary
                LeadingIconTab(
                    // LOWUI7_CHOICE_ROWS_SAY_WHICH_IS_SELECTED_V1 (OCB-286)
                    selected = selected,
                    onClick = {
                        onSelect(
                            profilePrimaryTargetTab(
                                section = section,
                                lastLibraryTab = lastLibraryTab,
                            ),
                        )
                    },
                    text = { Text(label, maxLines = 1) },
                    icon = {
                        Icon(
                            imageVector = when (section) {
                                0 -> Symbols.Article
                                1 -> Symbols.ChatBubble
                                else -> if (selected) Symbols.FolderOpen else Symbols.Folder
                            },
                            contentDescription = null,
                        )
                    },
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (primaryLabelsFit) {
            PrimaryTabRow(
                selectedTabIndex = selectedPrimary,
                containerColor = Color.Transparent,
                divider = { if (selectedPrimary != 2) HorizontalDivider() },
            ) { primaryTabs() }
        } else {
            PrimaryScrollableTabRow(
                selectedTabIndex = selectedPrimary,
                containerColor = Color.Transparent,
                edgePadding = 0.dp,
                divider = { if (selectedPrimary != 2) HorizontalDivider() },
            ) { primaryTabs() }
        }

        if (selectedPrimary == 2) {
            val selectedLibraryIndex = libraryTabs.indexOf(selectedTab).coerceAtLeast(0)
            SecondaryScrollableTabRow(
                selectedTabIndex = selectedLibraryIndex,
                containerColor = Color.Transparent,
                edgePadding = 0.dp,
            ) {
                libraryTabs.forEach { destinationTab ->
                    val label = profileDestinationLabel(destinationTab)
                    val selected = destinationTab == selectedTab
                    val showLabel = selected
                    val icon: @Composable () -> Unit = {
                        Icon(
                            imageVector = profileDestinationIcon(destinationTab),
                            contentDescription = if (showLabel) null else label,
                        )
                    }
                    val onClick = {
                        lastLibraryTab = destinationTab
                        onSelect(destinationTab)
                    }
                    if (showLabel) {
                        LeadingIconTab(
                            selected = true,
                            onClick = onClick,
                            text = { Text(label, maxLines = 1) },
                            icon = icon,
                        )
                    } else {
                        Tab(
                            selected = false,
                            onClick = onClick,
                            icon = icon,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileDestinationStrip(
    destinations: List<String>,
    selectedTab: Int,
    onSelect: (Int) -> Unit,
) {
    // M3225_PROFILE_TABS_ARE_M3_TAB_ROWS_V1: an M3 primary tab row; with three or
    // more tabs only the selected one is named (the others are icons with a
    // description), as before.
    PrimaryTabRow(
        selectedTabIndex = selectedTab.coerceIn(0, destinations.lastIndex),
        containerColor = Color.Transparent,
    ) {
        destinations.forEachIndexed { index, label ->
            val selected = index == selectedTab
            val showLabel = selected || destinations.size <= 2
            val icon: @Composable () -> Unit = {
                Icon(
                    imageVector = profileDestinationIcon(index),
                    contentDescription = if (showLabel) null else label,
                )
            }
            if (showLabel) {
                LeadingIconTab(
                    selected = selected,
                    onClick = { onSelect(index) },
                    text = { Text(label, maxLines = 1) },
                    icon = icon,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Tab(
                    selected = false,
                    onClick = { onSelect(index) },
                    icon = icon,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BlockedAccountRow(
    account: RedditBlockedAccount,
    inProgress: Boolean,
    onOpen: (() -> Unit)? = null,
    onUnblock: () -> Unit,
) {
    ListCard(
        headline = "u/${account.username}",
        supporting = account.blockedAtEpochSeconds?.let { epochSeconds ->
            "Blocked ${formatBlockedAccountDate(epochSeconds)}"
        },
        onClick = onOpen,
        shape = MaterialTheme.shapes.large,
        leading = {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                RedditDefaultProfileAvatar(
                    seed = account.username,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        },
        trailing = {
            OutlinedButton(
                onClick = onUnblock,
                enabled = !inProgress,
                contentPadding = PaddingValues(horizontal = 14.dp),
            ) {
                Text(if (inProgress) "Unblocking…" else "Unblock")
            }
        },
    )
}

@Composable
internal fun BlockedAccountsError(
    state: RedditBlockedAccountsUiState,
    onRetry: () -> Unit,
    onAuthorize: () -> Unit = {},
) {
    // OCB414_BLOCKED_LIST_CAN_ASK_FOR_AUTHORIZATION_V1 (OCB-414): a read that
    // needs authorization offers Authorize (Retry failed the same way); while
    // the browser is open no action is offered.
    val needsAuthorization = state.failureKind == ContentSafetyFailureKind.AUTHORIZATION
    val message = when (state.failureKind) {
        ContentSafetyFailureKind.AUTHORIZATION ->
            "Reddit authorization is required to read blocked accounts."
        ContentSafetyFailureKind.HTTP ->
            state.statusCode?.let { "Reddit rejected the blocked-account request. HTTP $it" }
                ?: "Reddit rejected the blocked-account request."
        ContentSafetyFailureKind.TRANSPORT ->
            "Couldn't reach Reddit to load blocked accounts."
        ContentSafetyFailureKind.PROTOCOL ->
            "Reddit returned an unexpected blocked-account response."
        else ->
            "Blocked accounts couldn't be loaded from Reddit."
    }
    ErrorNotice(
        presentation = ErrorPresentation(
            kind = ErrorPresentationKind.INLINE,
            title = "Couldn't load blocked accounts",
            message = message,
            primaryAction = when {
                state.authorizing -> null
                needsAuthorization -> ErrorAction(ErrorActionKind.SIGN_IN, "Authorize")
                state.retryable -> ErrorAction(ErrorActionKind.RETRY, "Retry")
                else -> null
            },
        ).withFailureDetails(state.failureDetails),
        diagnosticScopeKey = "profile_blocked_accounts",
        onAction = { action ->
            when (action) {
                ErrorActionKind.RETRY -> onRetry()
                ErrorActionKind.SIGN_IN -> onAuthorize()
                else -> Unit
            }
        },
    )
}

private fun formatBlockedAccountDate(epochSeconds: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochSeconds * 1_000L))

@Composable
private fun ProfileDraftCard(
    draft: PostComposerDraft,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        onClick = onOpen,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    draft.title.ifBlank { "Untitled ${draft.type.label.lowercase()} post" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        append(
                            draft.community.takeIf(String::isNotBlank)
                                ?.let { "r/$it" }
                                ?: "No community selected",
                        )
                        append(" · ${draft.type.label}")
                        draft.flair?.text?.takeIf(String::isNotBlank)?.let { flair ->
                            append(" · $flair")
                        }
                        append(" · ${formatProfileDraftDate(draft.updatedAtEpochMs)}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                draft.body.takeIf(String::isNotBlank)?.let { body ->
                    Text(
                        body,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Symbols.Delete, contentDescription = "Delete draft")
            }
        }
    }
}

private fun formatProfileDraftDate(epochMs: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(epochMs))

internal fun profileTabsForSession(
    username: String,
    activeAccountUsername: String,
    authenticated: Boolean,
): List<String> =
    if (
        authenticated &&
        username.equals(activeAccountUsername, ignoreCase = true)
    ) {
        PROFILE_OWN_DESTINATION_LABELS
    } else if (authenticated) {
        listOf("Posts", "Comments")
    } else {
        profileTabsFor(username, activeAccountUsername)
    }

internal fun authenticatedProfileActivityHistoryKind(
    activityTab: Int,
): RedditAccountHistoryKind? =
    LIVE_PROFILE_ACTIVITY_HISTORY_KINDS.getOrNull(activityTab)

private fun profilePostItemKey(
    tab: Int,
    activityTab: Int,
    postId: String,
): String =
    if (tab in PROFILE_HISTORY_FIRST_TAB..PROFILE_HISTORY_LAST_TAB) {
        "profile-history-$tab-$postId"
    } else {
        "profile-post-$postId"
    }

internal fun profileTabsFor(
    username: String,
    localUsername: String,
): List<String> =
    if (username.equals(localUsername, ignoreCase = true)) {
        PROFILE_OWN_DESTINATION_LABELS
    } else {
        listOf("Posts", "Comments")
    }

internal fun savedPostsForProfile(
    username: String,
    localUsername: String,
    posts: List<Post>,
    savedPostIds: Collection<String>,
): List<Post> =
    if (username.equals(localUsername, ignoreCase = true)) {
        posts.filter { it.id in savedPostIds }
    } else {
        emptyList()
    }

internal fun upvotedPostsForProfile(
    username: String,
    localUsername: String,
    posts: List<Post>,
    votes: Map<String, Int>,
): List<Post> =
    if (username.equals(localUsername, ignoreCase = true)) {
        posts.filter { votes[it.id] == 1 }
    } else {
        emptyList()
    }

private val RedditAccountHistoryKind.activityLabel: String
    get() = when (this) {
        RedditAccountHistoryKind.UPVOTED -> "Upvoted"
        RedditAccountHistoryKind.DOWNVOTED -> "Downvoted"
        RedditAccountHistoryKind.HIDDEN -> "Hidden"
        RedditAccountHistoryKind.COMMENTS -> "Comments"
        RedditAccountHistoryKind.SAVED_POSTS -> "Saved posts"
        RedditAccountHistoryKind.SAVED_COMMENTS -> "Saved comments"
    }

private val RedditAccountHistoryKind.emptyActivityMessage: String
    get() = when (this) {
        RedditAccountHistoryKind.UPVOTED ->
            "No upvoted Reddit posts are available for this account."
        RedditAccountHistoryKind.DOWNVOTED ->
            "No downvoted Reddit posts are available for this account."
        RedditAccountHistoryKind.HIDDEN ->
            "No hidden Reddit posts are available for this account."
        else ->
            "No Reddit account history items are available."
    }

@Composable
private fun SavedHistoryStatus(
    state: RedditSavedHistoryUiState,
    onAuthorize: () -> Unit,
    onRetry: () -> Unit,
) {
    // DIAG05_PLAIN_NOTICES_ARE_RECORDED_V1
    ReportFailureDiagnostic(
        scopeKey = "profile_saved_history",
        active = state.failureKind != null,
        failureDetails = state.failureDetails,
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = when {
                    state.authorizing -> "Waiting for Reddit authorization…"
                    state.authorizationRequired ->
                        "Reddit needs history permission before subshell can load Saved."
                    state.failureKind != null ->
                        "Could not load saved posts and comments from Reddit."
                    else -> "Saved is unavailable."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.failureKind != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            when {
                state.authorizationRequired || state.authorizing ->
                    TextButton(
                        enabled = !state.authorizing,
                        onClick = onAuthorize,
                    ) {
                        Text(if (state.authorizing) "Authorizing…" else "Enable activity")
                    }

                state.failureKind != null && state.failureRetryable ->
                    TextButton(onClick = onRetry) {
                        Text("Retry")
                    }
            }
        }
    }
}

@Composable
private fun AccountHistoryStatus(
    state: RedditAccountHistoryUiState,
    kind: RedditAccountHistoryKind,
    onAuthorize: () -> Unit,
    onRetry: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = when {
                    state.authorizing ->
                        "Waiting for Reddit authorization…"
                    state.authorizationRequired ->
                        "Reddit needs history permission before subshell can load ${kind.activityLabel.lowercase()} activity for this account."
                    state.failureKind != null ->
                        state.failureKind.accountHistoryFailureMessage(state.failureDetails?.httpStatusCode)
                    else ->
                        "Reddit account history is unavailable."
                },
                style = MaterialTheme.typography.bodyMedium,
                color =
                    if (state.failureKind != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )

            when {
                state.authorizationRequired || state.authorizing ->
                    TextButton(
                        enabled = !state.authorizing,
                        onClick = onAuthorize,
                    ) {
                        Text(
                            if (state.authorizing) {
                                "Authorizing…"
                            } else {
                                "Enable activity"
                            },
                        )
                    }

                state.failureKind != null && state.failureRetryable ->
                    TextButton(onClick = onRetry) {
                        Text("Retry")
                    }
            }
        }
    }
}

private fun RedditAccountHistoryUiFailureKind.accountHistoryFailureMessage(httpStatusCode: Int?): String = when (this) {
    RedditAccountHistoryUiFailureKind.CLIENT_CONFIGURATION ->
        "Reddit account history is not configured for this account."
    RedditAccountHistoryUiFailureKind.SESSION ->
        "The Reddit session needs attention before account history can load."
    // ISSUE208_SERVER_FAILURES_ARE_NOT_REJECTIONS_V1
    RedditAccountHistoryUiFailureKind.HTTP ->
        redditReadHttpReason(httpStatusCode, "Reddit rejected the account history request.")
    RedditAccountHistoryUiFailureKind.TRANSPORT ->
        "Reddit could not be reached. Cached activity remains available."
    RedditAccountHistoryUiFailureKind.PROTOCOL ->
        "Reddit returned an unexpected account history response."
    RedditAccountHistoryUiFailureKind.STORAGE ->
        "Account history could not be read from local storage."
    RedditAccountHistoryUiFailureKind.END_REACHED ->
        "There is no more Reddit account history to load."
}

@Composable
private fun ProfileCommentsStatus(
    state: RedditAccountHistoryUiState,
    onAuthorize: () -> Unit,
    onRetry: () -> Unit,
) {
    val failure = state.failureKind
    if (failure != null) {
        ErrorNotice(
            presentation = redditProfileCommentsErrorPresentation(
                failureKind = failure.toProfileCommentsUiFailureKind(),
                details = state.failureDetails,
                retryable = state.failureRetryable,
                showingCachedContent = state.comments.isNotEmpty(),
            ),
            diagnosticScopeKey = "profile_comments",
            onAction = { action ->
                if (action == ErrorActionKind.RETRY) onRetry()
            },
        )
        return
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = when {
                    state.authorizing ->
                        "Waiting for Reddit authorization…"
                    state.authorizationRequired ->
                        "Reddit needs one additional permission before subshell can load profile comments for this account."
                    else ->
                        "Reddit profile comments are unavailable."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.authorizationRequired || state.authorizing) {
                TextButton(
                    enabled = !state.authorizing,
                    onClick = onAuthorize,
                ) {
                    Text(
                        if (state.authorizing) {
                            "Authorizing…"
                        } else {
                            "Enable comments"
                        },
                    )
                }
            }
        }
    }
}


private fun RedditAccountHistoryUiFailureKind.toProfileCommentsUiFailureKind(): RedditProfileCommentsUiFailureKind =
    when (this) {
        RedditAccountHistoryUiFailureKind.CLIENT_CONFIGURATION ->
            RedditProfileCommentsUiFailureKind.CLIENT_CONFIGURATION
        RedditAccountHistoryUiFailureKind.SESSION ->
            RedditProfileCommentsUiFailureKind.SESSION
        RedditAccountHistoryUiFailureKind.HTTP ->
            RedditProfileCommentsUiFailureKind.HTTP
        RedditAccountHistoryUiFailureKind.TRANSPORT ->
            RedditProfileCommentsUiFailureKind.TRANSPORT
        RedditAccountHistoryUiFailureKind.PROTOCOL ->
            RedditProfileCommentsUiFailureKind.PROTOCOL
        RedditAccountHistoryUiFailureKind.STORAGE,
        RedditAccountHistoryUiFailureKind.END_REACHED ->
            RedditProfileCommentsUiFailureKind.STORAGE
    }

@Composable
private fun ProfileStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun ProfileComment(
    comment: ProfileCommentFixture,
    relatedPost: Post?,
    state: SubshellState,
    onOpen: (() -> Unit)?,
    saved: Boolean = false,
) {
    val communityName = (comment.community ?: relatedPost?.community)
        ?.trim()
        ?.removePrefix("/r/")
        ?.removePrefix("r/")
        ?.takeIf(String::isNotEmpty)
    val communityIdentity = communityName?.let(state::communityFor)
    val communityAccent = communityIdentity
        ?.let { community -> Color(community.accent) }
        ?: communityName?.let(::accentFor)
        ?: MaterialTheme.colorScheme.primary
    val postTitle = comment.postTitle
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: relatedPost?.title
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: "Open discussion"
    val communityLabel = communityName?.let(state::subredditLabel) ?: "Reddit"

    // M3225_LIST_ROWS_ARE_LIST_ITEMS_V1: a comment card (Markdown body, not a
    // list row); its click is clipped to the card and says it is a button.
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = onOpen != null, role = Role.Button) { onOpen?.invoke() },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(
                start = 14.dp,
                top = 12.dp,
                end = 12.dp,
                bottom = 11.dp,
            ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RemoteIdentityImage(
                    uri = communityIdentity?.iconImageUri,
                    contentDescription = null, // decorative beside the visible name (ISSUE219)
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape),
                ) {
                    CommunityAvatar(
                        name = communityName ?: "reddit",
                        accent = communityAccent,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                ) {
                    Text(
                        text = communityLabel,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "@${comment.author} · ${comment.age}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (saved) {
                    Icon(
                        imageVector = Symbols.BookmarkFilled,
                        contentDescription = "Saved comment",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(
                text = postTitle,
                modifier = Modifier.padding(top = 9.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            if (comment.body.isNotBlank()) {
                CommentContent(
                    source = comment.body,
                    modifier = Modifier.padding(top = 6.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = if (comment.score == 1) "1 point" else "${comment.score} points",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal data class ProfileEmptyPresentation(
    val title: String,
    val description: String,
)

internal fun profileUnavailablePresentation(): ProfileEmptyPresentation =
    ProfileEmptyPresentation(
        title = "Profile unavailable",
        description =
            "This Reddit profile could not be found. The account may have been deleted, " +
                "suspended, or otherwise unavailable.",
    )

internal fun profilePrivacyEmptyPresentation(): ProfileEmptyPresentation =
    ProfileEmptyPresentation(
        title = "Profile privacy may be enabled",
        description =
            "This user may have enabled Reddit's profile privacy settings. " +
                "Some posts and comments can be hidden from their profile while " +
                "remaining visible elsewhere on Reddit.",
    )

internal fun publicProfileEmptyPresentation(
    username: String,
    activity: ProfileActivityEvidence,
): ProfileEmptyPresentation = when (activity) {
    ProfileActivityEvidence.POST ->
        ProfileEmptyPresentation(
            title = "No public profile posts",
            description = "No public posts are currently available on u/$username's profile.",
        )
    ProfileActivityEvidence.COMMENT ->
        ProfileEmptyPresentation(
            title = "No public profile comments",
            description = "No public comments are currently available on u/$username's profile.",
        )
}

@Composable
private fun ProfileBannerPlaceholder(
    accent: Color,
    modifier: Modifier = Modifier,
    label: String? = "No profile banner",
) {
    Box(
        modifier = modifier.background(
            Brush.horizontalGradient(
                listOf(
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                    accent.copy(alpha = 0.24f),
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ),
        ),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 26.dp, y = (-28).dp)
                .size(112.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.18f)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-22).dp, y = 38.dp)
                .size(92.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.12f)),
        )
        if (label != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyProfileTab(title: String, description: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                description,
                modifier = Modifier.padding(top = 6.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun followerCountLabel(baseCount: String, following: Boolean): String {
    if (!following) return baseCount
    return baseCount.toIntOrNull()?.plus(1)?.toString() ?: baseCount
}
