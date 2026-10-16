package app.subshell.prototype.ui.screens

import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.WindowInsetsRulers
import androidx.compose.foundation.layout.fitInside
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import app.subshell.prototype.ui.ScreenMargin
import app.subshell.prototype.ui.components.RouteTopBar
import app.subshell.prototype.ui.components.rememberRouteTopBarScrollBehavior
import app.subshell.prototype.ui.error.LocalViewportErrorHostState
import app.subshell.prototype.ui.noticeCopied
import app.subshell.prototype.ui.theme.isDarkScheme
import app.subshell.prototype.ui.theme.withMinContrast
import app.subshell.prototype.ui.uiDebugLog
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.produceState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.lazy.LazyListState
import app.subshell.prototype.ui.CommentComposerDraft
import app.subshell.prototype.ui.commentDraftBelongsToAnotherAccount
import app.subshell.prototype.ui.components.AuthorFlairMinVisibleWidth
import app.subshell.prototype.ui.components.hiddenWhenNarrowerThan
import app.subshell.prototype.ui.components.AuthorFlairBadge
import app.subshell.prototype.ui.components.UserTagChip
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
// Comment viewport uses the shared refreshable lazy list below.
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import app.subshell.prototype.ui.components.ModeratorReportMenuItem
import app.subshell.prototype.ui.components.ModeratorContentControlsMenuItem
import app.subshell.prototype.data.account.ModeratorReportTarget
import app.subshell.prototype.data.account.moderatorReportTargetOrNull
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.subshell.prototype.data.PostRepository
import app.subshell.prototype.data.account.RedditCommentUiFailureKind
import app.subshell.prototype.data.account.RedditPeopleSearchUiState
import app.subshell.prototype.data.account.RedditCommentsUiState
import app.subshell.prototype.data.account.CommentWriteRequest
import app.subshell.prototype.data.account.CommentWriteUiState
import app.subshell.prototype.data.account.CommentEditRequest
import app.subshell.prototype.data.account.CommentEditUiState
import app.subshell.prototype.data.account.CommentDeleteFailureKind
import app.subshell.prototype.data.account.CommentDeletePhase
import app.subshell.prototype.data.account.CommentDeleteRequest
import app.subshell.prototype.data.account.CommentDeleteUiState
import app.subshell.prototype.data.account.PostEditRequest
import app.subshell.prototype.data.account.PostDeleteFailureKind
import app.subshell.prototype.data.account.PostDeletePhase
import app.subshell.prototype.data.account.PostDeleteRequest
import app.subshell.prototype.data.account.PostDeleteUiState
import app.subshell.prototype.data.account.PostManageMutationRequest
import app.subshell.prototype.data.account.PostManageTarget
import app.subshell.prototype.data.account.PostManageUiState
import app.subshell.prototype.data.account.normalizePostEditFullname
import app.subshell.prototype.data.account.PostEditUiState
import app.subshell.prototype.data.account.OwnContentType
import app.subshell.prototype.data.account.ownContentActions
import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.data.itemsOrEmpty
import app.subshell.prototype.model.redditReadableText
import app.subshell.prototype.model.CodeSample
import app.subshell.prototype.model.Comment
import app.subshell.prototype.model.isMoreRepliesNode
import app.subshell.prototype.model.Post
import app.subshell.prototype.ui.CommentActionPlacement
import app.subshell.prototype.ui.CommentCollapseMode
import app.subshell.prototype.ui.CommentHierarchyStyle
import app.subshell.prototype.ui.CommentTimeFormat
import app.subshell.prototype.ui.LocalCodeFontFamily
import app.subshell.prototype.ui.ProfileActivityEvidence
import app.subshell.prototype.ui.CommentMediaPolicy
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.FeedPostEntryTarget
import app.subshell.prototype.ui.VoteColorPalette
import app.subshell.prototype.ui.VoteControlMode
import app.subshell.prototype.ui.commentAncestorIdsForTargets
import app.subshell.prototype.ui.commentSiblingBranchesToCollapse
import app.subshell.prototype.ui.decodeCommentVisitBaseline
import app.subshell.prototype.ui.encodeCommentVisitBaseline
import app.subshell.prototype.ui.knownCommentIds
import app.subshell.prototype.ui.knownMoreCommentIds
import app.subshell.prototype.ui.newCommentIds
import app.subshell.prototype.ui.newDescendantCount
import app.subshell.prototype.ui.resolveCommentSort
import app.subshell.prototype.ui.shareCommentLink
import app.subshell.prototype.ui.components.ErrorNotice
import app.subshell.prototype.ui.components.CommentAuthorAvatar
import app.subshell.prototype.ui.components.MarkdownText
import app.subshell.prototype.ui.components.LocalUriMedia
import app.subshell.prototype.ui.components.PostCard
import app.subshell.prototype.ui.components.RedditContentReportDialog
import app.subshell.prototype.ui.components.RefreshableLazyColumn
import app.subshell.prototype.ui.components.VoteControl
import app.subshell.prototype.ui.components.compactNumber
import app.subshell.prototype.ui.components.InlineCommentMedia
import app.subshell.prototype.ui.error.ErrorAction
import app.subshell.prototype.ui.error.ErrorActionKind
import app.subshell.prototype.ui.error.ErrorPresentation
import app.subshell.prototype.ui.error.ErrorPresentationKind
import app.subshell.prototype.ui.error.ReportViewportError
import app.subshell.prototype.ui.error.redditCommentsErrorPresentation
import app.subshell.prototype.ui.error.commentDeleteErrorPresentation
import app.subshell.prototype.ui.error.postDeleteErrorPresentation
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import app.subshell.prototype.ui.components.CommentContent
import app.subshell.prototype.ui.components.SwipeActionSurface
import app.subshell.prototype.ui.components.commentSwipeActionEnabled
import app.subshell.prototype.ui.components.commentSwipeActionVisual
import app.subshell.prototype.ui.components.dispatchCommentSwipeAction
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.rounded.Search
import app.subshell.prototype.ui.components.MarkdownHighlight
import app.subshell.prototype.ui.floatingActionAtStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import app.subshell.prototype.data.account.RedditReportTargetKind
import app.subshell.prototype.ui.components.InlineProgressIndicator

internal fun postCommentsFor(
    post: Post,
    repository: PostRepository,
): List<Comment> = repository.commentsForPost(post.id).itemsOrEmpty()

internal fun commentsEntryTargetReady(
    authenticated: Boolean,
    liveStateMatches: Boolean,
    loading: Boolean,
    loadedCommentCount: Int,
): Boolean =
    !authenticated ||
        loadedCommentCount > 0 ||
        (liveStateMatches && !loading)

internal fun commentsForPostSession(
    post: Post,
    accountStableId: String,
    authenticated: Boolean,
    redditCommentsState: RedditCommentsUiState,
    repositoryComments: () -> List<Comment>,
    sort: RedditCommentSort = RedditCommentSort.BEST,
): List<Comment> = if (authenticated) {
    redditCommentsState
        .takeIf { state -> state.matches(accountStableId, post.id, sort) }
        ?.comments
        .orEmpty()
} else {
    repositoryComments()
}


internal object FocusedCommentScrollOwnershipRegistry {
    private const val MAX_ENTRIES = 128
    private val userOwnedTransactions = LinkedHashSet<String>()

    @Synchronized
    fun isUserOwned(transaction: String?): Boolean =
        transaction != null && transaction in userOwnedTransactions

    @Synchronized
    fun markUserOwned(transaction: String?) {
        if (transaction == null) return
        userOwnedTransactions.remove(transaction)
        userOwnedTransactions.add(transaction)
        while (userOwnedTransactions.size > MAX_ENTRIES) {
            userOwnedTransactions.remove(userOwnedTransactions.first())
        }
    }

    @Synchronized
    internal fun clearForTest() {
        userOwnedTransactions.clear()
    }
}

internal fun focusedCommentTopLevelIndex(
    comments: List<Comment>,
    focusedCommentId: String?,
): Int = if (focusedCommentId == null) {
    -1
} else {
    comments.indexOfFirst { it.containsComment(focusedCommentId) }
}

internal fun commentTimeLabel(
    relativeAge: String,
    createdEpochSeconds: Long?,
    format: CommentTimeFormat,
    zoneId: ZoneId = ZoneId.systemDefault(),
    nowEpochSeconds: Long = Instant.now().epochSecond,
): String {
    if (format == CommentTimeFormat.RELATIVE || createdEpochSeconds == null) {
        return relativeAge
    }
    return runCatching {
        val localTime = Instant.ofEpochSecond(createdEpochSeconds).atZone(zoneId)
        val localNow = Instant.ofEpochSecond(nowEpochSeconds).atZone(zoneId)
        if (localTime.toLocalDate() != localNow.toLocalDate()) {
            val datePattern = if (localTime.year == localNow.year) "d MMM" else "d MMM yyyy"
            return@runCatching DateTimeFormatter
                .ofPattern(datePattern, Locale.ENGLISH)
                .format(localTime)
        }
        when (format) {
            CommentTimeFormat.RELATIVE -> relativeAge
            CommentTimeFormat.LOCAL_24_HOUR ->
                DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT).format(localTime)
            CommentTimeFormat.LOCAL_12_HOUR ->
                DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH).format(localTime)
        }
    }.getOrDefault(relativeAge)
}

private fun Comment.containsComment(commentId: String): Boolean =
    id == commentId || children.any { it.containsComment(commentId) }

private fun List<Comment>.findCommentById(commentId: String): Comment? {
    for (comment in this) {
        if (comment.id == commentId) return comment
        comment.children.findCommentById(commentId)?.let { return it }
    }
    return null
}

// LOWUI4_ONLY_SUBSHELL_MEDIA_BLOCKS_AN_EDIT_V1 (OCB-237): the legacy
// Comment.inlineMedia field (old cache rows only) no longer blocks; Reddit's
// media is edited as rich text (ISSUE8B/8C).
internal fun commentContainsUnsupportedEditableInlineMedia(
    body: String,
    hasLocalMedia: Boolean,
): Boolean = hasLocalMedia
// ISSUE8B_RICHTEXT_MEDIA_EDIT_V1: Reddit's image, Giphy and emote placeholders
// no longer block an edit. Reddit refuses them in a plain-text edit
// (INVALID_COMMENT_SUBMISSION), so such an edit is sent as rich text
// (RedditRichTextMediaEdit.kt). Media Subshell attached itself still blocks.

internal fun commentThreadInsetDp(depth: Int): Int =
    if (depth <= 0) 0 else COMMENT_THREAD_INSET_DP

internal fun targetPendingMoreCommentNode(
    comments: List<Comment>,
    focusedCommentId: String,
    loadingMoreNodeId: String?,
    blockedMoreNodeIds: Set<String>,
): Comment? {
    val targetBareId = focusedCommentId
        .trim()
        .removePrefix("t1_")
        .takeIf(String::isNotEmpty)
        ?: return null

    comments.forEach { comment ->
        if (
            comment.isMoreRepliesNode &&
            comment.id != loadingMoreNodeId &&
            comment.id !in blockedMoreNodeIds &&
            comment.moreChildIds.any { childId ->
                childId.equals(targetBareId, ignoreCase = true)
            }
        ) {
            return comment
        }
        targetPendingMoreCommentNode(
            comments = comment.children,
            focusedCommentId = focusedCommentId,
            loadingMoreNodeId = loadingMoreNodeId,
            blockedMoreNodeIds = blockedMoreNodeIds,
        )?.let { return it }
    }
    return null
}



@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PostScreen(
    post: Post,
    state: SubshellState,
    onBack: () -> Unit,
    routeFocusedCommentId: String? = null,
    modifier: Modifier = Modifier,
    redditCommentsState: RedditCommentsUiState = RedditCommentsUiState(),
    // ISSUE229_MENTION_AUTOCOMPLETE_V1: the reader's u/ suggestions from Reddit.
    mentionSearchState: RedditPeopleSearchUiState = RedditPeopleSearchUiState(),
    onSearchMentions: (String) -> Unit = {},
    onRetryRedditComments: (String, String, RedditCommentSort) -> Unit =
        { _, _, _ -> },
    onLoadCachedRedditComments: (String, String, RedditCommentSort) -> Unit =
        { _, _, _ -> },
    onRefreshRedditCommentsPreservingFocusedTarget: (
        String,
        String,
        RedditCommentSort,
        String,
    ) -> Unit = { _, _, _, _ -> },
    onLoadFocusedRedditComment: (
        String,
        String,
        RedditCommentSort,
        String,
    ) -> Unit = { _, _, _, _ -> },
    // OCB581_REFUSED_LOAD_IS_NOT_AWAITED_V1: true when the load started.
    onLoadMoreRedditComments: (
        String,
        String,
        RedditCommentSort,
        String,
    ) -> Boolean = { _, _, _, _ -> false },
    onFocusedCommentLandingVerified: (String) -> Unit = {},
    commentWriteState: CommentWriteUiState = CommentWriteUiState(),
    onSubmitComment: (CommentWriteRequest) -> Unit = {},
    // COMPOSER10_COMMENT_MEDIA_RULES_V1
    commentMediaPolicies: Map<String, CommentMediaPolicy> = emptyMap(),
    onLoadCommentMediaPolicy: (String, String) -> Unit = { _, _ -> },
    onAuthorizeCommentWrite: () -> Unit = {},
    onCancelCommentWriteAuthorization: () -> Unit = {},
    onResetCommentWriteState: () -> Unit = {},
    commentEditState: CommentEditUiState = CommentEditUiState(),
    onEditComment: (CommentEditRequest) -> Unit = {},
    onAuthorizeCommentEdit: () -> Unit = {},
    onCancelCommentEditAuthorization: () -> Unit = {},
    onResetCommentEditState: () -> Unit = {},
    onCheckCommentEdit: () -> Unit = {},
    commentDeleteState: CommentDeleteUiState = CommentDeleteUiState(),
    onDeleteComment: (CommentDeleteRequest) -> Unit = {},
    onAuthorizeCommentDelete: () -> Unit = {},
    onCancelCommentDeleteAuthorization: () -> Unit = {},
    onResetCommentDeleteState: () -> Unit = {},
    postEditState: PostEditUiState = PostEditUiState(),
    onEditPost: (PostEditRequest) -> Unit = {},
    onAuthorizePostEdit: () -> Unit = {},
    onCancelPostEditAuthorization: () -> Unit = {},
    onResetPostEditState: () -> Unit = {},
    onCheckPostEdit: () -> Unit = {},
    postDeleteState: PostDeleteUiState = PostDeleteUiState(),
    onDeletePost: (PostDeleteRequest) -> Unit = {},
    onAuthorizePostDelete: () -> Unit = {},
    onCancelPostDeleteAuthorization: () -> Unit = {},
    onResetPostDeleteState: () -> Unit = {},
    onCheckPostDelete: () -> Unit = {},
    postManageState: PostManageUiState = PostManageUiState(),
    onLoadPostManagement: (PostManageTarget) -> Unit = {},
    onMutatePostManagement: (PostManageMutationRequest) -> Unit = {},
    onAuthorizePostManagement: () -> Unit = {},
    onCancelPostManageAuthorization: () -> Unit = {},
    onResetPostManageState: () -> Unit = {},
) {
    var manualSortName by rememberSaveable(post.id, state.postVisitId) {
        mutableStateOf<String?>(null)
    }
    val authenticated = state.activeAccountIsAuthenticated
    val accountStableId = state.activeAccountId
    val suggestedSort = redditCommentsState.suggestedSort.takeIf {
        authenticated &&
            redditCommentsState.accountStableId == accountStableId &&
            redditCommentsState.postId == post.id
    }
    val resolvedSort = resolveCommentSort(
        manualSort = manualSortName?.let { name -> RedditCommentSort.valueOf(name) },
        useSuggestedSort = state.useSuggestedCommentSort,
        suggestedSort = suggestedSort,
    )
    val focusedFrozenSortName = rememberSaveable(
        post.id,
        state.postVisitId,
        routeFocusedCommentId,
        manualSortName,
    ) {
        resolvedSort.name
    }
    val sort = if (routeFocusedCommentId != null) {
        RedditCommentSort.valueOf(focusedFrozenSortName)
    } else {
        resolvedSort
    }
    var sortMenu by remember(post.id) { mutableStateOf(false) }
    // UIPOST03: keyed on the post, so another post in the same host (the
    // user workspace list-detail) opens at its top, not at the last post's
    // scroll position.
    val listState = rememberSaveable(post.id, saver = LazyListState.Saver) { LazyListState() }
    var editingPost by rememberSaveable(post.id, state.activeAccountId) {
        mutableStateOf(false)
    }
    var deletingPost by rememberSaveable(post.id, state.activeAccountId) {
        mutableStateOf(false)
    }
    var managingPost by rememberSaveable(post.id, state.activeAccountId) {
        mutableStateOf(false)
    }
    LaunchedEffect(post.id, state.postVisitId, state.activeAccountId) {
        if (state.consumePostEditRequest(post.id)) {
            onResetPostEditState()
            editingPost = true
        }
        if (state.consumePostDeleteRequest(post.id)) {
            onResetPostDeleteState()
            deletingPost = true
        }
        if (state.consumePostManageRequest(post.id)) {
            onResetPostManageState()
            managingPost = true
        }
    }
    var editingCommentId by rememberSaveable(post.id, state.activeAccountId) {
        mutableStateOf<String?>(null)
    }
    // UIPOST02_ASK_BEFORE_REPLACING_AN_UNSENT_DRAFT_V1: the reply target the
    // user asked for while another unsent draft exists (null = the post).
    var composerConflict by remember(post.id) { mutableStateOf<CommentComposerRequest?>(null) }
    fun openComposer(comment: Comment?) {
        if (!state.openCommentComposer(post, comment)) {
            composerConflict = CommentComposerRequest(comment)
        }
    }
    var deletingCommentId by rememberSaveable(post.id, state.activeAccountId) {
        mutableStateOf<String?>(null)
    }
    var inlineMediaEditBlocked by rememberSaveable(post.id, state.activeAccountId) {
        mutableStateOf(false)
    }
    val focusedCommentId = routeFocusedCommentId
    val localContentRevision = state.localContentRevision
    val liveStateMatches = authenticated &&
        redditCommentsState.matches(accountStableId, post.id, sort)
    // ISSUE22_AUTHOR_AVATAR_BATCH_LOOKUP_V1
    // Resolve comment author avatars for every network-loaded page of this
    // thread; already-known authors are skipped inside the state.
    val commentAuthorFullnames = redditCommentsState.authorFullnames
    LaunchedEffect(liveStateMatches, commentAuthorFullnames) {
        if (!liveStateMatches || commentAuthorFullnames.isEmpty()) return@LaunchedEffect
        val before = commentAuthorFullnames.keys.count { author -> state.avatarUriForAuthor(author) != null }
        state.ensureAuthorAvatars(commentAuthorFullnames)
        val after = commentAuthorFullnames.keys.count { author -> state.avatarUriForAuthor(author) != null }
        uiDebugLog(
            "subshell-issue22",
            "author-avatars post=${post.id} authors=${commentAuthorFullnames.size} " +
                "known_before=$before known_after=$after",
        )
    }
    val liveStatusVisible = authenticated && (
        !liveStateMatches ||
            (
                redditCommentsState.loading &&
                    redditCommentsState.comments.isEmpty()
                ) ||
            (
                redditCommentsState.fullDiscussionLoading &&
                    !redditCommentsState.fullDiscussionLoaded
                ) ||
            (
                redditCommentsState.failureKind != null &&
                    redditCommentsState.failedMoreNodeId == null
                ) ||
            redditCommentsState.comments.isEmpty()
        )
    val pinnedCommentFailure =
        authenticated &&
            liveStateMatches &&
            !redditCommentsState.loading &&
            redditCommentsState.failureKind != null &&
            redditCommentsState.failedMoreNodeId == null &&
            redditCommentsState.comments.isNotEmpty()
    // UIPOST03_ONE_LIVE_STATUS_ROW_VALUE_V1: the status row is emitted only
    // without a pinned comment failure; every lazy-list index counts the same.
    val liveStatusRowVisible = liveStatusVisible && !pinnedCommentFailure
    val degradedCommentsPresentation = if (pinnedCommentFailure) {
        redditCommentsState.failureKind.let { failureKind ->
            redditCommentsErrorPresentation(
                failureKind = failureKind,
                details = redditCommentsState.failureDetails,
                retryable = redditCommentsState.failureRetryable,
                showingCachedContent = true,
            )
        }
    } else {
        null
    }
    ReportViewportError(
        scopeKey = "post-comments:${post.id}:$sort",
        presentation = degradedCommentsPresentation,
        failureDetails = redditCommentsState.failureDetails,
        onRetry = if (degradedCommentsPresentation != null) {
            { onRetryRedditComments(accountStableId, post.id, sort) }
        } else {
            null
        },
    )
    val sessionComments = commentsForPostSession(
        post = post,
        accountStableId = accountStableId,
        authenticated = authenticated,
        redditCommentsState = redditCommentsState,
        repositoryComments = { state.commentsForPost(post) },
        sort = sort,
    )
    val sortedComments = remember(
        post.id,
        sort,
        localContentRevision,
        redditCommentsState.contentRevision,
        sessionComments,
    ) {
        if (authenticated) {
            sessionComments
        } else {
            when (sort) {
                RedditCommentSort.BEST -> sessionComments
                RedditCommentSort.TOP ->
                    sessionComments.sortedByDescending(Comment::score)
                RedditCommentSort.NEW -> sessionComments.reversed()
                RedditCommentSort.CONTROVERSIAL -> sessionComments
                RedditCommentSort.OLD ->
                    sessionComments.sortedWith(
                        compareBy<Comment> { it.createdEpochSeconds ?: Long.MAX_VALUE }
                            .thenBy { it.id },
                    )
            }
        }
    }
    var viewportSortedComments by remember(
        post.id,
        state.postVisitId,
        accountStableId,
        focusedCommentId,
        sort.name,
    ) {
        mutableStateOf(sortedComments)
    }
    var viewportContentRevision by remember(
        post.id,
        state.postVisitId,
        accountStableId,
        focusedCommentId,
        sort.name,
    ) {
        mutableStateOf(redditCommentsState.contentRevision)
    }
    // UIPOST05: rebuilt only when the list changes, not on every recomposition;
    // rows read this set instead of searching the session-wide list.
    val collapsedCommentIds by remember(state) {
        derivedStateOf { state.collapsedCommentIds.toSet() }
    }
    val mainCommentRows = remember(viewportSortedComments, collapsedCommentIds) {
        visibleCommentRows(
            comments = viewportSortedComments,
            collapsedCommentIds = collapsedCommentIds,
        )
    }
    val duplicateViewportRowKeys = remember(viewportSortedComments, collapsedCommentIds) {
        duplicateVisibleCommentRowKeys(
            comments = viewportSortedComments,
            collapsedCommentIds = collapsedCommentIds,
        )
    }
    LaunchedEffect(post.id, duplicateViewportRowKeys) {
        if (duplicateViewportRowKeys.isNotEmpty()) {
            Log.e(
                COMMENT_NAV_LOG_TAG,
                "phase=duplicate_comment_identity post=${post.id} " +
                    "count=${duplicateViewportRowKeys.size} " +
                    "keys=${duplicateViewportRowKeys.sorted().joinToString(",")}",
            )
        }
    }
    val branchRailVisualByCommentId = remember(viewportSortedComments, mainCommentRows) {
        commentBranchRailVisualByCommentId(
            comments = viewportSortedComments,
            visibleRows = mainCommentRows,
        )
    }
    val parentConnectorVisualByCommentId = remember(viewportSortedComments, mainCommentRows) {
        commentParentConnectorVisualByCommentId(
            comments = viewportSortedComments,
            visibleRows = mainCommentRows,
        )
    }
    val latestSortedComments by rememberUpdatedState(sortedComments)
    val latestCommentRows by rememberUpdatedState(mainCommentRows)
    val latestRedditCommentsState by rememberUpdatedState(redditCommentsState)
    val latestLiveStatusVisible by rememberUpdatedState(liveStatusRowVisible)
    val latestLoadCachedRedditComments by rememberUpdatedState(onLoadCachedRedditComments)
    val latestRefreshPreservingTarget by rememberUpdatedState(
        onRefreshRedditCommentsPreservingFocusedTarget,
    )
    val latestLoadFocusedRedditComment by rememberUpdatedState(onLoadFocusedRedditComment)
    val latestLoadMoreRedditComments by rememberUpdatedState(onLoadMoreRedditComments)

    // COMPOSER14_COMMENT_EDIT_KEPT_V1: while an edit is open, a comment that
    // briefly leaves the loaded thread (refresh, reload, sort) keeps its last
    // copy, so the editor and the text typed in it stay.
    val editingCommentMemory = remember(editingCommentId) { EditingCommentMemory() }
    val editingComment = editingCommentMemory.keep(
        editingCommentId?.let(sortedComments::findCommentById),
    )
    val deletingComment = deletingCommentId?.let(sortedComments::findCommentById)
    val editingCommentHasUnsupportedInlineMedia = editingComment?.let { comment ->
        commentContainsUnsupportedEditableInlineMedia(
            body = comment.body,
            hasLocalMedia = comment.localMediaUri != null,
        )
    } == true
    val focusedTransactionKey = remember(
        post.id,
        focusedCommentId,
        state.postVisitId,
        accountStableId,
        sort.name,
    ) {
        focusedCommentId?.let { targetId ->
            "${post.id}|$targetId|${state.postVisitId}|$accountStableId|${sort.name}"
        }
    }

    var focusedLandingVerifiedId by rememberSaveable(
        post.id,
        state.postVisitId,
        accountStableId,
        sort.name,
        focusedCommentId,
    ) {
        mutableStateOf<String?>(null)
    }
    var focusedLandingFailedReason by rememberSaveable(
        post.id,
        state.postVisitId,
        accountStableId,
        sort.name,
        focusedCommentId,
    ) {
        mutableStateOf<String?>(null)
    }
    var focusedArrivalPulseGeneration by rememberSaveable(
        post.id,
        state.postVisitId,
        accountStableId,
        sort.name,
        focusedCommentId,
    ) {
        mutableIntStateOf(0)
    }
    var focusedOpeningIndicatorVisible by remember(
        post.id,
        state.postVisitId,
        focusedCommentId,
    ) {
        mutableStateOf(false)
    }
    // UIPOST05: true while the focused-comment anchor corrects the list
    // itself, so that scroll is not mistaken for the reader's.
    val focusedAnchorCorrection = remember(post.id) { booleanArrayOf(false) }
    var focusedUserOwnsScroll by rememberSaveable(
        post.id,
        state.postVisitId,
        accountStableId,
        focusedCommentId,
        sort.name,
    ) {
        mutableStateOf(
            FocusedCommentScrollOwnershipRegistry.isUserOwned(focusedTransactionKey),
        )
    }
    var commentHydrationGestureGeneration by remember(
        post.id,
        state.postVisitId,
        focusedCommentId,
        sort.name,
    ) {
        mutableIntStateOf(0)
    }
    var commentHydrationBudget by remember(
        post.id,
        state.postVisitId,
        focusedCommentId,
        sort.name,
    ) {
        mutableIntStateOf(0)
    }
    // ISSUE94_FAILED_NODES_WAIT_FOR_RETRY_V1: the coordinator keeps only the
    // last failed node, so with two failing nodes on screen automatic loading
    // alternated A, B, A … without pause (offline, 429). Every node that failed
    // here waits for its Retry row or a reload of the thread.
    var failedMoreNodeIds by remember(
        post.id,
        state.postVisitId,
        accountStableId,
        sort.name,
    ) {
        mutableStateOf(emptySet<String>())
    }
    LaunchedEffect(redditCommentsState.failedMoreNodeId, liveStateMatches) {
        val failed = redditCommentsState.failedMoreNodeId
        if (liveStateMatches && failed != null) failedMoreNodeIds = failedMoreNodeIds + failed
    }
    LaunchedEffect(redditCommentsState.loading) {
        if (redditCommentsState.loading) failedMoreNodeIds = emptySet()
    }
    var pendingHydrationCommit by remember(
        post.id,
        state.postVisitId,
        focusedCommentId,
        sort.name,
    ) {
        mutableStateOf<CommentHydrationCommitFence?>(null)
    }
    var hydrationMaterializedCommentIds by remember(
        post.id,
        state.postVisitId,
        focusedCommentId,
        sort.name,
    ) {
        mutableStateOf<Set<String>>(emptySet())
    }
    var hydrationContinuityRevision by remember(
        post.id,
        state.postVisitId,
        focusedCommentId,
        sort.name,
    ) {
        mutableStateOf<Long?>(null)
    }
    var hydrationTargetVisibleBeforeCommit by remember(
        post.id,
        state.postVisitId,
        focusedCommentId,
        sort.name,
    ) {
        mutableStateOf(false)
    }
    val density = LocalDensity.current
    val focusedOffsetTolerancePx = with(density) {
        FOCUSED_COMMENT_OFFSET_TOLERANCE_DP.dp.roundToPx()
    }
    val addCommentVisible = state.localMutationsAllowed || state.activeAccountIsAuthenticated
    LaunchedEffect(
        authenticated,
        accountStableId,
        post.id,
        sort,
        state.postVisitId,
        focusedCommentId,
    ) {
        if (authenticated && focusedCommentId == null) {
            onRetryRedditComments(accountStableId, post.id, sort)
        }
    }

    LaunchedEffect(
        post.id,
        state.postVisitId,
        focusedCommentId,
        sort.name,
    ) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) {
                commentHydrationGestureGeneration += 1
                commentHydrationBudget = COMMENT_AUTO_HYDRATION_MAX_PER_USER_GESTURE
                uiDebugLog(
                    COMMENT_NAV_LOG_TAG,
                    "phase=hydration_gesture_armed post=${post.id} " +
                        "generation=$commentHydrationGestureGeneration " +
                        "budget=$commentHydrationBudget",
                )
                if (focusedCommentId != null && !focusedUserOwnsScroll) {
                    FocusedCommentScrollOwnershipRegistry.markUserOwned(focusedTransactionKey)
                    focusedUserOwnsScroll = true
                    focusedOpeningIndicatorVisible = false
                    uiDebugLog(
                        COMMENT_NAV_LOG_TAG,
                        "phase=anchor_released post=${post.id} target=$focusedCommentId reason=user_drag",
                    )
                    uiDebugLog(
                        COMMENT_NAV_LOG_TAG,
                        "phase=focused_scroll_ownership post=${post.id} target=$focusedCommentId " +
                            "visit=${state.postVisitId} tx=${focusedTransactionKey ?: "none"}",
                    )
                }
            }
        }
    }

    LaunchedEffect(
        sortedComments,
        redditCommentsState.contentRevision,
        redditCommentsState.loadingMoreNodeId,
        redditCommentsState.failedMoreNodeId,
        pendingHydrationCommit,
    ) {
        val fence = pendingHydrationCommit
        if (fence == null) {
            if (
                viewportSortedComments != sortedComments ||
                viewportContentRevision != redditCommentsState.contentRevision
            ) {
                // LOWUI8_LANDING_LOADS_ARE_NOT_NEW_V1 (OCB-320): while a comment
                // link lands, its own loads (target more nodes, the canonical
                // tree) commit here without a fence; what they bring in is
                // hidden content, as for a hydration, not new comments.
                if (
                    focusedCommentId != null &&
                    focusedLandingVerifiedId != focusedCommentId &&
                    viewportSortedComments.isNotEmpty()
                ) {
                    val landingIds = commentHydrationMaterializedCommentIds(
                        previousViewportComments = viewportSortedComments,
                        hydratedComments = sortedComments,
                    )
                    if (landingIds.isNotEmpty()) {
                        hydrationMaterializedCommentIds = hydrationMaterializedCommentIds + landingIds
                    }
                }
                viewportSortedComments = sortedComments
                viewportContentRevision = redditCommentsState.contentRevision
            }
            return@LaunchedEffect
        }

        if (
            redditCommentsState.loadingMoreNodeId == null &&
            redditCommentsState.failedMoreNodeId == fence.moreNodeId
        ) {
            uiDebugLog(
                COMMENT_NAV_LOG_TAG,
                "phase=hydration_commit_release post=${post.id} node=${fence.moreNodeId} " +
                    "reason=load_failed revision=${redditCommentsState.contentRevision}",
            )
            viewportSortedComments = sortedComments
            viewportContentRevision = redditCommentsState.contentRevision
            pendingHydrationCommit = null
            return@LaunchedEffect
        }

        if (
            redditCommentsState.contentRevision <= fence.sourceContentRevision ||
            redditCommentsState.loadingMoreNodeId != null
        ) {
            return@LaunchedEffect
        }

        val moreKey = "more:${fence.moreNodeId}"
        fun moreNodeVisible(): Boolean = listState.layoutInfo.visibleItemsInfo.any { item ->
            item.key.toString() == moreKey
        }

        // ISSUE110_ONE_HYDRATION_PATH_V1: every hydration (automatic or a
        // Retry) publishes once nobody scrolls, also while its row is on
        // screen; stable lazy-list keys keep the viewport still. The manual
        // path (wait until the row left the screen, then restore a captured
        // anchor) had no caller left but Retry, which it made spin.
        val initiallyScrolling = listState.isScrollInProgress
        if (initiallyScrolling) {
            uiDebugLog(
                COMMENT_NAV_LOG_TAG,
                "phase=hydration_commit_deferred post=${post.id} node=${fence.moreNodeId} " +
                    "reason=user_scroll " +
                    "revision=${redditCommentsState.contentRevision} gesture=${fence.gestureGeneration}",
            )
        }

        while (pendingHydrationCommit == fence) {
            snapshotFlow { listState.isScrollInProgress }
                .first { scrolling -> !scrolling }
            withFrameNanos { }

            if (
                pendingHydrationCommit == fence &&
                commentHydrationCommitCanPublish(
                    fence = fence,
                    currentContentRevision = redditCommentsState.contentRevision,
                    loadingMoreNodeId = redditCommentsState.loadingMoreNodeId,
                    failedMoreNodeId = redditCommentsState.failedMoreNodeId,
                    scrollInProgress = listState.isScrollInProgress,
                )
            ) {
                val materializedIds = commentHydrationMaterializedCommentIds(
                    previousViewportComments = viewportSortedComments,
                    hydratedComments = sortedComments,
                )
                if (materializedIds.isNotEmpty()) {
                    hydrationMaterializedCommentIds =
                        hydrationMaterializedCommentIds + materializedIds
                    uiDebugLog(
                        COMMENT_NAV_LOG_TAG,
                        "phase=hydration_materialized_ids post=${post.id} node=${fence.moreNodeId} " +
                            "count=${materializedIds.size} revision=${redditCommentsState.contentRevision}",
                    )
                }
                hydrationTargetVisibleBeforeCommit = focusedCommentViewportRowIndex(
                    rows = mainCommentRows,
                    focusedCommentId = focusedCommentId,
                ) >= 0
                val appliedRevision = redditCommentsState.contentRevision
                viewportSortedComments = sortedComments
                viewportContentRevision = appliedRevision
                hydrationContinuityRevision = appliedRevision
                pendingHydrationCommit = null
                uiDebugLog(
                    COMMENT_NAV_LOG_TAG,
                    "phase=hydration_commit_applied post=${post.id} node=${fence.moreNodeId} " +
                        "revision=$viewportContentRevision scrollInProgress=false " +
                        "placeholderVisible=${moreNodeVisible()} " +
                        "materialized=${materializedIds.size} " +
                        "targetVisibleBefore=$hydrationTargetVisibleBeforeCommit",
                )
                return@LaunchedEffect
            }
        }
    }

    // ISSUE109_FAILED_LANDING_SAYS_SO_V1: a landing that could not reach its
    // comment showed the thread from the top without a word; it now says so,
    // and Retry runs the landing again (the generation is an effect key).
    var focusedLandingRetryGeneration by remember(focusedTransactionKey) { mutableIntStateOf(0) }
    val focusedLandingFailurePresentation =
        if (focusedCommentId != null && focusedLandingFailedReason != null && !focusedUserOwnsScroll) {
            ErrorPresentation(
                kind = ErrorPresentationKind.DEGRADED,
                title = "Couldn't find this comment",
                message = "It may have been removed, or the connection failed",
                primaryAction = ErrorAction(ErrorActionKind.RETRY, "Retry"),
            )
        } else {
            null
        }
    ReportViewportError(
        scopeKey = "focused-comment:${post.id}",
        presentation = focusedLandingFailurePresentation,
        failureDetails = null,
        onRetry = if (focusedLandingFailurePresentation != null) {
            {
                focusedLandingFailedReason = null
                focusedLandingRetryGeneration += 1
            }
        } else {
            null
        },
    )

    LaunchedEffect(focusedTransactionKey, focusedUserOwnsScroll, focusedLandingRetryGeneration) {
        val transaction = focusedTransactionKey ?: return@LaunchedEffect
        val targetId = focusedCommentId ?: return@LaunchedEffect
        if (
            focusedUserOwnsScroll ||
            FocusedCommentScrollOwnershipRegistry.isUserOwned(transaction)
        ) {
            focusedUserOwnsScroll = true
            focusedOpeningIndicatorVisible = false
            uiDebugLog(
                COMMENT_NAV_LOG_TAG,
                "phase=transaction_skipped post=${post.id} target=$targetId " +
                    "reason=user_scroll_owned visit=${state.postVisitId} tx=$transaction",
            )
            return@LaunchedEffect
        }
        focusedLandingVerifiedId = null
        focusedLandingFailedReason = null
        focusedOpeningIndicatorVisible = false

        uiDebugLog(
            COMMENT_NAV_LOG_TAG,
            "phase=transaction_start post=${post.id} target=$targetId " +
                "sort=${sort.apiValue} visit=${state.postVisitId} tx=$transaction",
        )
        uiDebugLog(
            COMMENT_NAV_LOG_TAG,
            "phase=screen_entry post=${post.id} target=$targetId " +
                "projected=${state.focusedCommentId ?: "none"} visit=${state.postVisitId}",
        )

        coroutineScope {
            val openingJob = launch {
                delay(FOCUSED_COMMENT_OPENING_INDICATOR_DELAY_MS)
                if (
                    focusedLandingVerifiedId != targetId &&
                    focusedLandingFailedReason == null
                ) {
                    focusedOpeningIndicatorVisible = true
                    uiDebugLog(
                        COMMENT_NAV_LOG_TAG,
                        "phase=opening_indicator_show post=${post.id} target=$targetId " +
                            "visit=${state.postVisitId} tx=$transaction",
                    )
                }
            }
            suspend fun targetPresent(): Boolean {
                val current = latestRedditCommentsState
                return if (authenticated) {
                    focusedCommentTargetMatchesQuery(
                        stateMatchesTargetQuery = current.matches(
                            accountStableId,
                            post.id,
                            sort,
                        ),
                        targetPresent = current.comments.findCommentById(targetId) != null,
                    )
                } else {
                    latestSortedComments.findCommentById(targetId) != null
                }
            }

            fun focusedCommentRequestInFlight(current: RedditCommentsUiState): Boolean =
                current.matches(accountStableId, post.id, sort) &&
                    (current.loading || current.loadingMoreNodeId != null)

            var focusedAnchorFraction = FOCUSED_COMMENT_NORMAL_ANCHOR_FRACTION
            var focusedAnchorPolicyLocked = false

            suspend fun awaitStateAdvance(
                previousRevision: Long,
                timeoutMs: Long = FOCUSED_COMMENT_ACQUIRE_STEP_TIMEOUT_MS,
            ): Boolean = withTimeoutOrNull(timeoutMs) {
                while (true) {
                    val current = latestRedditCommentsState
                    val completed = focusedCommentStateAdvanceCompleted(
                        previousRevision = previousRevision,
                        stateMatchesTargetQuery = current.matches(
                            accountStableId,
                            post.id,
                            sort,
                        ),
                        loading = current.loading,
                        loadingMoreNodeId = current.loadingMoreNodeId,
                        contentRevision = current.contentRevision,
                        hasFailure = current.failureKind != null,
                        failedMoreNodeId = current.failedMoreNodeId,
                    )
                    if (completed) {
                        return@withTimeoutOrNull targetPresent()
                    }
                    delay(FOCUSED_COMMENT_STATE_POLL_MS)
                }
                false
            } ?: false

            suspend fun positionTargetInCurrentLayout(): Pair<Int, androidx.compose.foundation.lazy.LazyListItemInfo>? =
                withTimeoutOrNull(FOCUSED_COMMENT_MATERIALIZE_TIMEOUT_MS) {
                    var attempt = 0
                    var waitingForProjectedIndexLogged = false
                    while (attempt < FOCUSED_COMMENT_LAYOUT_IDENTITY_MAX_PASSES) {
                        val requestedIndex = focusedCommentLazyListIndex(
                            rows = latestCommentRows,
                            focusedCommentId = targetId,
                            addCommentVisible = addCommentVisible,
                            liveStatusVisible = latestLiveStatusVisible,
                        )
                        if (requestedIndex < 0) {
                            if (!waitingForProjectedIndexLogged) {
                                uiDebugLog(
                                    COMMENT_NAV_LOG_TAG,
                                    "phase=layout_identity_wait post=${post.id} target=$targetId " +
                                        "reason=projected_index_missing",
                                )
                                waitingForProjectedIndexLogged = true
                            }
                            delay(FOCUSED_COMMENT_STATE_POLL_MS)
                            continue
                        }
                        waitingForProjectedIndexLogged = false
                        attempt += 1
                        val beforeLayout = listState.layoutInfo
                        val requestOffset = focusedCommentRequestScrollOffsetPx(
                            viewportStartOffset = beforeLayout.viewportStartOffset,
                            viewportEndOffset = beforeLayout.viewportEndOffset,
                            anchorFraction = focusedAnchorFraction,
                        )
                        listState.scrollToItem(
                            index = requestedIndex,
                            scrollOffset = requestOffset,
                        )
                        withFrameNanos { }

                        val currentIndex = focusedCommentLazyListIndex(
                            rows = latestCommentRows,
                            focusedCommentId = targetId,
                            addCommentVisible = addCommentVisible,
                            liveStatusVisible = latestLiveStatusVisible,
                        )
                        val layout = listState.layoutInfo
                        val targetItem = layout.visibleItemsInfo.firstOrNull { info ->
                            info.key == "comment:$targetId"
                        }
                        val keyAtCurrentIndex = layout.visibleItemsInfo
                            .firstOrNull { info -> info.index == currentIndex }
                            ?.key
                            ?.toString()
                        val identityMatches = focusedCommentLayoutIdentityMatches(
                            focusedCommentId = targetId,
                            expectedIndex = currentIndex,
                            observedTargetIndex = targetItem?.index,
                            observedKeyAtExpectedIndex = keyAtCurrentIndex,
                        )
                        uiDebugLog(
                            COMMENT_NAV_LOG_TAG,
                            "phase=layout_identity post=${post.id} target=$targetId attempt=$attempt " +
                                "requestedIndex=$requestedIndex currentIndex=$currentIndex " +
                                "targetIndex=${targetItem?.index ?: -1} " +
                                "keyAtCurrent=${keyAtCurrentIndex ?: "none"} " +
                                "requestOffset=$requestOffset match=$identityMatches",
                        )
                        if (identityMatches && targetItem != null) {
                            if (!focusedAnchorPolicyLocked) {
                                focusedAnchorFraction = focusedCommentLandingAnchorFraction(
                                    itemSizePx = targetItem.size,
                                    viewportStartOffset = layout.viewportStartOffset,
                                    viewportEndOffset = layout.viewportEndOffset,
                                )
                                focusedAnchorPolicyLocked = true
                                uiDebugLog(
                                    COMMENT_NAV_LOG_TAG,
                                    "phase=anchor_policy post=${post.id} target=$targetId " +
                                        "fraction=$focusedAnchorFraction itemSize=${targetItem.size} " +
                                        "viewportSize=${layout.viewportEndOffset - layout.viewportStartOffset}",
                                )
                            }
                            return@withTimeoutOrNull currentIndex to targetItem
                        }
                        delay(FOCUSED_COMMENT_STATE_POLL_MS)
                    }
                    null
                }

            try {
                if (targetPresent()) {
                    uiDebugLog(
                        COMMENT_NAV_LOG_TAG,
                        "phase=acquire_memory_hit post=${post.id} target=$targetId",
                    )
                } else if (authenticated) {
                    val beforeCacheState = latestRedditCommentsState
                    val beforeCacheRevision = focusedCommentAcquireBaselineRevision(
                        stateMatchesTargetQuery = beforeCacheState.matches(
                            accountStableId,
                            post.id,
                            sort,
                        ),
                        contentRevision = beforeCacheState.contentRevision,
                    )
                    uiDebugLog(
                        COMMENT_NAV_LOG_TAG,
                        "phase=acquire_cache_start post=${post.id} target=$targetId sort=${sort.apiValue}",
                    )
                    latestLoadCachedRedditComments(accountStableId, post.id, sort)
                    awaitStateAdvance(beforeCacheRevision)
                    if (targetPresent()) {
                        uiDebugLog(
                            COMMENT_NAV_LOG_TAG,
                            "phase=acquire_cache_hit post=${post.id} target=$targetId",
                        )
                    }
                }

                var locatorRequested = false
                if (!targetPresent() && authenticated) {
                    locatorRequested = true
                    val currentState = latestRedditCommentsState
                    val beforeRevision = focusedCommentAcquireBaselineRevision(
                        stateMatchesTargetQuery = currentState.matches(
                            accountStableId,
                            post.id,
                            sort,
                        ),
                        contentRevision = currentState.contentRevision,
                    )
                    uiDebugLog(
                        COMMENT_NAV_LOG_TAG,
                        "phase=locator_request post=${post.id} target=$targetId sort=${sort.apiValue}",
                    )
                    latestLoadFocusedRedditComment(
                        accountStableId,
                        post.id,
                        sort,
                        targetId,
                    )
                    awaitStateAdvance(beforeRevision)
                }

                val blockedMoreNodeIds = linkedSetOf<String>()
                var acquireSteps = 0
                var inFlightWaitRanOut = false
                while (!targetPresent() && acquireSteps < FOCUSED_COMMENT_MAX_ACQUIRE_STEPS) {
                    val currentState = latestRedditCommentsState
                    // ISSUE109_LANDING_WAITS_FOR_THE_REQUEST_IN_FLIGHT_V1: a step's
                    // 2.5 s ran out while Reddit was still answering (mobile data),
                    // the loop gave up and the late answer was never used; a
                    // request for this thread that is still running is waited
                    // for (up to FOCUSED_COMMENT_IN_FLIGHT_WAIT_MS; once a wait
                    // runs out, no more waiting).
                    if (
                        authenticated &&
                        !inFlightWaitRanOut &&
                        focusedCommentRequestInFlight(currentState)
                    ) {
                        awaitStateAdvance(
                            previousRevision = -1L,
                            timeoutMs = FOCUSED_COMMENT_IN_FLIGHT_WAIT_MS,
                        )
                        inFlightWaitRanOut = focusedCommentRequestInFlight(latestRedditCommentsState)
                        continue
                    }
                    acquireSteps += 1
                    val currentComments = latestSortedComments
                    val moreNode = targetPendingMoreCommentNode(
                        comments = currentComments,
                        focusedCommentId = targetId,
                        loadingMoreNodeId = currentState.loadingMoreNodeId,
                        blockedMoreNodeIds = blockedMoreNodeIds,
                    )
                    if (
                        authenticated &&
                        moreNode != null &&
                        currentState.matches(accountStableId, post.id, sort) &&
                        !currentState.loading &&
                        currentState.loadingMoreNodeId == null
                    ) {
                        val beforeRevision = currentState.contentRevision
                        uiDebugLog(
                            COMMENT_NAV_LOG_TAG,
                            "phase=hydrate_target_node post=${post.id} target=$targetId node=${moreNode.id}",
                        )
                        val started = latestLoadMoreRedditComments(
                            accountStableId,
                            post.id,
                            sort,
                            moreNode.id,
                        )
                        // OCB581_REFUSED_LOAD_IS_NOT_AWAITED_V1: a refused load
                        // changes nothing, so waiting 2.5 s for it (and asking
                        // again, up to 8 times while scrolling is locked) only
                        // delayed the outcome; the node is done for this landing.
                        if (!started) {
                            blockedMoreNodeIds += moreNode.id
                            continue
                        }
                        awaitStateAdvance(beforeRevision)
                        if (latestRedditCommentsState.failedMoreNodeId == moreNode.id) {
                            blockedMoreNodeIds += moreNode.id
                        }
                        continue
                    }
                    if (authenticated && !locatorRequested) {
                        locatorRequested = true
                        val beforeRevision = focusedCommentAcquireBaselineRevision(
                            stateMatchesTargetQuery = currentState.matches(
                                accountStableId,
                                post.id,
                                sort,
                            ),
                            contentRevision = currentState.contentRevision,
                        )
                        uiDebugLog(
                            COMMENT_NAV_LOG_TAG,
                            "phase=locator_request post=${post.id} target=$targetId sort=${sort.apiValue}",
                        )
                        latestLoadFocusedRedditComment(
                            accountStableId,
                            post.id,
                            sort,
                            targetId,
                        )
                        awaitStateAdvance(beforeRevision)
                        continue
                    }
                    break
                }

                if (!targetPresent()) {
                    focusedLandingFailedReason = if (authenticated) {
                        latestRedditCommentsState.failureKind?.name?.lowercase()
                            ?: "target_not_materialized"
                    } else {
                        "target_unavailable_offline"
                    }
                    uiDebugLog(
                        COMMENT_NAV_LOG_TAG,
                        "phase=landing_failed post=${post.id} target=$targetId " +
                            "reason=$focusedLandingFailedReason",
                    )
                    return@coroutineScope
                }

                val targetAncestors = commentAncestorIdsForTargets(
                    comments = latestSortedComments,
                    targetIds = setOf(targetId),
                )
                state.revealNewCommentPaths(
                    ancestorIds = targetAncestors + targetId,
                    siblingBranchIds = emptySet(),
                    manuallyCollapsedIds = emptySet(),
                )

                val rowReady = withTimeoutOrNull(FOCUSED_COMMENT_MATERIALIZE_TIMEOUT_MS) {
                    while (focusedCommentViewportRowIndex(latestCommentRows, targetId) < 0) {
                        delay(FOCUSED_COMMENT_STATE_POLL_MS)
                    }
                    true
                } ?: false
                if (!rowReady) {
                    focusedLandingFailedReason = "target_row_not_visible"
                    uiDebugLog(
                        COMMENT_NAV_LOG_TAG,
                        "phase=landing_failed post=${post.id} target=$targetId " +
                            "reason=$focusedLandingFailedReason",
                    )
                    return@coroutineScope
                }

                val initialFocusedListIndex = focusedCommentLazyListIndex(
                    rows = latestCommentRows,
                    focusedCommentId = targetId,
                    addCommentVisible = addCommentVisible,
                    liveStatusVisible = latestLiveStatusVisible,
                )
                if (initialFocusedListIndex < 0) {
                    focusedLandingFailedReason = "target_index_missing"
                    uiDebugLog(
                        COMMENT_NAV_LOG_TAG,
                        "phase=landing_failed post=${post.id} target=$targetId " +
                            "reason=$focusedLandingFailedReason",
                    )
                    return@coroutineScope
                }

                val targetComment = latestSortedComments.findCommentById(targetId)
                uiDebugLog(
                    COMMENT_NAV_LOG_TAG,
                    "phase=target_materialized post=${post.id} target=$targetId " +
                        "index=$initialFocusedListIndex author=${targetComment?.author ?: "unknown"} " +
                        "bodyLength=${targetComment?.body?.length ?: -1}",
                )
                if (authenticated) {
                    val beforeRefreshState = latestRedditCommentsState
                    val canonicalWasReady = beforeRefreshState.fullDiscussionLoaded
                    val beforeRefreshRevision = focusedCommentAcquireBaselineRevision(
                        stateMatchesTargetQuery = beforeRefreshState.matches(
                            accountStableId,
                            post.id,
                            sort,
                        ),
                        contentRevision = beforeRefreshState.contentRevision,
                    )
                    uiDebugLog(
                        COMMENT_NAV_LOG_TAG,
                        "phase=full_discussion_start post=${post.id} target=$targetId " +
                            "cached=$canonicalWasReady revision=$beforeRefreshRevision",
                    )
                    latestRefreshPreservingTarget(
                        accountStableId,
                        post.id,
                        sort,
                        targetId,
                    )
                    uiDebugLog(
                        COMMENT_NAV_LOG_TAG,
                        "phase=canonical_stabilization_wait post=${post.id} target=$targetId",
                    )
                    val canonicalSettled = withTimeoutOrNull(
                        FOCUSED_COMMENT_CANONICAL_STABILIZE_TIMEOUT_MS,
                    ) {
                        while (true) {
                            val current = latestRedditCommentsState
                            val refreshCompleted =
                                current.matches(accountStableId, post.id, sort) &&
                                    !current.fullDiscussionLoading &&
                                    (
                                        current.contentRevision > beforeRefreshRevision ||
                                            current.failureKind != null
                                        )
                            if (refreshCompleted) {
                                return@withTimeoutOrNull current.fullDiscussionLoaded
                            }
                            delay(FOCUSED_COMMENT_STATE_POLL_MS)
                        }
                        false
                    } ?: false
                    withFrameNanos { }
                    val canonicalState = latestRedditCommentsState
                    uiDebugLog(
                        COMMENT_NAV_LOG_TAG,
                        "phase=canonical_stabilization_settled post=${post.id} target=$targetId " +
                            "ready=$canonicalSettled revision=${canonicalState.contentRevision} " +
                            "failure=${canonicalState.failureKind?.name?.lowercase() ?: "none"}",
                    )
                    if (
                        canonicalState.matches(accountStableId, post.id, sort) &&
                        canonicalState.fullDiscussionLoaded
                    ) {
                        val canonicalComments = canonicalState.comments
                        val canonicalTargetPresent =
                            canonicalComments.findCommentById(targetId) != null
                        val projectedBeforeCommit = focusedCommentViewportRowIndex(
                            rows = latestCommentRows,
                            focusedCommentId = targetId,
                        )
                        if (canonicalTargetPresent) {
                            val canonicalFocusedPathIds = commentFocusedPathIds(
                                comments = canonicalComments,
                                focusedCommentId = targetId,
                            )
                            state.revealNewCommentPaths(
                                ancestorIds = canonicalFocusedPathIds,
                                siblingBranchIds = emptySet(),
                                manuallyCollapsedIds = emptySet(),
                            )
                            viewportSortedComments = canonicalComments
                            viewportContentRevision = canonicalState.contentRevision
                            withFrameNanos { }
                            val projectedAfterCommit = focusedCommentViewportRowIndex(
                                rows = latestCommentRows,
                                focusedCommentId = targetId,
                            )
                            uiDebugLog(
                                COMMENT_NAV_LOG_TAG,
                                "phase=canonical_target_continuity post=${post.id} target=$targetId " +
                                    "raw=true revision=${canonicalState.contentRevision} " +
                                    "projectedBefore=$projectedBeforeCommit " +
                                    "projectedAfter=$projectedAfterCommit " +
                                    "pathSize=${canonicalFocusedPathIds.size}",
                            )
                        } else {
                            Log.e(
                                COMMENT_NAV_LOG_TAG,
                                "phase=canonical_target_tree_lost post=${post.id} target=$targetId " +
                                    "revision=${canonicalState.contentRevision} " +
                                    "projectedBefore=$projectedBeforeCommit",
                            )
                        }
                    }
                }
                val positionedTarget = positionTargetInCurrentLayout()
                if (positionedTarget == null) {
                    focusedLandingFailedReason = "target_layout_identity_not_verified"
                    uiDebugLog(
                        COMMENT_NAV_LOG_TAG,
                        "phase=landing_failed post=${post.id} target=$targetId " +
                            "reason=$focusedLandingFailedReason",
                    )
                    return@coroutineScope
                }
                var focusedListIndex = positionedTarget.first
                uiDebugLog(
                    COMMENT_NAV_LOG_TAG,
                    "phase=coarse_position post=${post.id} target=$targetId " +
                        "index=$focusedListIndex offset=${positionedTarget.second.offset}",
                )

                var verified = false
                var lastErrorPx = Int.MAX_VALUE
                for (pass in 0 until FOCUSED_COMMENT_OFFSET_MAX_PASSES) {
                    var layout = listState.layoutInfo
                    var currentTargetItem = layout.visibleItemsInfo.firstOrNull { info ->
                        info.key == "comment:$targetId"
                    }
                    if (currentTargetItem == null || currentTargetItem.index != focusedListIndex) {
                        val repositioned = positionTargetInCurrentLayout() ?: break
                        focusedListIndex = repositioned.first
                        currentTargetItem = repositioned.second
                        layout = listState.layoutInfo
                    }
                    val verifiedTargetItem = currentTargetItem
                    lastErrorPx = focusedCommentOffsetDeltaPx(
                        itemOffset = verifiedTargetItem.offset,
                        viewportStartOffset = layout.viewportStartOffset,
                        viewportEndOffset = layout.viewportEndOffset,
                        anchorFraction = focusedAnchorFraction,
                    )
                    uiDebugLog(
                        COMMENT_NAV_LOG_TAG,
                        "phase=target_geometry post=${post.id} target=$targetId pass=$pass " +
                            "offset=${verifiedTargetItem.offset} size=${verifiedTargetItem.size} " +
                            "viewportStart=${layout.viewportStartOffset} " +
                            "viewportEnd=${layout.viewportEndOffset} " +
                            "anchorFraction=$focusedAnchorFraction error=$lastErrorPx",
                    )
                    if (
                        focusedCommentOffsetVerified(
                            itemOffset = verifiedTargetItem.offset,
                            viewportStartOffset = layout.viewportStartOffset,
                            viewportEndOffset = layout.viewportEndOffset,
                            tolerancePx = focusedOffsetTolerancePx,
                            anchorFraction = focusedAnchorFraction,
                        )
                    ) {
                        verified = true
                        break
                    }
                    val consumedPx = listState.scrollBy(lastErrorPx.toFloat())
                    uiDebugLog(
                        COMMENT_NAV_LOG_TAG,
                        "phase=center_adjust post=${post.id} target=$targetId pass=$pass " +
                            "requested=$lastErrorPx consumed=${consumedPx.toInt()}",
                    )
                    if (
                        kotlin.math.abs(consumedPx) < 0.5f &&
                        focusedCommentBoundaryConstrainedLandingVerified(
                            itemOffset = verifiedTargetItem.offset,
                            itemSize = verifiedTargetItem.size,
                            viewportStartOffset = layout.viewportStartOffset,
                            viewportEndOffset = layout.viewportEndOffset,
                            requestedDeltaPx = lastErrorPx,
                            canScrollBackward = listState.canScrollBackward,
                            canScrollForward = listState.canScrollForward,
                        )
                    ) {
                        verified = true
                        uiDebugLog(
                            COMMENT_NAV_LOG_TAG,
                            "phase=landing_constrained post=${post.id} target=$targetId " +
                                "error=$lastErrorPx",
                        )
                        break
                    }
                    delay(FOCUSED_COMMENT_STATE_POLL_MS)
                }

                if (!verified) {
                    val layout = listState.layoutInfo
                    val currentItem = layout.visibleItemsInfo.firstOrNull { info ->
                        info.key == "comment:$targetId"
                    }
                    val currentExpectedIndex = focusedCommentLazyListIndex(
                        rows = latestCommentRows,
                        focusedCommentId = targetId,
                        addCommentVisible = addCommentVisible,
                        liveStatusVisible = latestLiveStatusVisible,
                    )
                    val keyAtExpectedIndex = layout.visibleItemsInfo
                        .firstOrNull { info -> info.index == currentExpectedIndex }
                        ?.key
                        ?.toString()
                    if (
                        currentItem != null &&
                        focusedCommentLayoutIdentityMatches(
                            focusedCommentId = targetId,
                            expectedIndex = currentExpectedIndex,
                            observedTargetIndex = currentItem.index,
                            observedKeyAtExpectedIndex = keyAtExpectedIndex,
                        ) &&
                        focusedCommentOffsetVerified(
                            itemOffset = currentItem.offset,
                            viewportStartOffset = layout.viewportStartOffset,
                            viewportEndOffset = layout.viewportEndOffset,
                            tolerancePx = focusedOffsetTolerancePx,
                            anchorFraction = focusedAnchorFraction,
                        )
                    ) {
                        verified = true
                        lastErrorPx = focusedCommentOffsetDeltaPx(
                            itemOffset = currentItem.offset,
                            viewportStartOffset = layout.viewportStartOffset,
                            viewportEndOffset = layout.viewportEndOffset,
                            anchorFraction = focusedAnchorFraction,
                        )
                    }
                }

                if (!verified) {
                    focusedLandingFailedReason = "target_offset_not_verified"
                    uiDebugLog(
                        COMMENT_NAV_LOG_TAG,
                        "phase=landing_failed post=${post.id} target=$targetId " +
                            "reason=$focusedLandingFailedReason error=$lastErrorPx",
                    )
                    return@coroutineScope
                }

                focusedLandingVerifiedId = targetId
                focusedOpeningIndicatorVisible = false
                focusedArrivalPulseGeneration += 1
                uiDebugLog(
                    COMMENT_NAV_LOG_TAG,
                    "phase=landing_verified post=${post.id} target=$targetId " +
                        "anchorFraction=$focusedAnchorFraction error=$lastErrorPx",
                )
                onFocusedCommentLandingVerified(targetId)
                uiDebugLog(
                    COMMENT_NAV_LOG_TAG,
                    "phase=arrival_pulse post=${post.id} target=$targetId",
                )

                snapshotFlow {
                    val layout = listState.layoutInfo
                    val item = layout.visibleItemsInfo.firstOrNull { info ->
                        info.key == "comment:$targetId"
                    }
                    Triple(
                        item?.offset,
                        layout.viewportStartOffset,
                        layout.viewportEndOffset,
                    )
                }.collect { (itemOffset, viewportStart, viewportEnd) ->
                    if (focusedUserOwnsScroll) return@collect
                    if (itemOffset == null) {
                        val currentIndex = focusedCommentLazyListIndex(
                            rows = latestCommentRows,
                            focusedCommentId = targetId,
                            addCommentVisible = addCommentVisible,
                            liveStatusVisible = latestLiveStatusVisible,
                        )
                        if (currentIndex >= 0) {
                            val rematerializeRequestOffset =
                                focusedCommentRequestScrollOffsetPx(
                                    viewportStartOffset = viewportStart,
                                    viewportEndOffset = viewportEnd,
                                    anchorFraction = focusedAnchorFraction,
                                )
                            uiDebugLog(
                                COMMENT_NAV_LOG_TAG,
                                "phase=anchor_rematerialize post=${post.id} target=$targetId " +
                                    "index=$currentIndex requestOffset=$rematerializeRequestOffset",
                            )
                            listState.requestScrollToItem(
                                index = currentIndex,
                                scrollOffset = rematerializeRequestOffset,
                            )
                        }
                        return@collect
                    }
                    val deltaPx = focusedCommentOffsetDeltaPx(
                        itemOffset = itemOffset,
                        viewportStartOffset = viewportStart,
                        viewportEndOffset = viewportEnd,
                        anchorFraction = focusedAnchorFraction,
                    )
                    if (abs(deltaPx) > focusedOffsetTolerancePx) {
                        focusedAnchorCorrection[0] = true
                        val consumedPx = try {
                            listState.scrollBy(deltaPx.toFloat())
                        } finally {
                            focusedAnchorCorrection[0] = false
                        }
                        uiDebugLog(
                            COMMENT_NAV_LOG_TAG,
                            "phase=anchor_preserved post=${post.id} target=$targetId " +
                                "error=$deltaPx consumed=${consumedPx.toInt()}",
                        )
                    }
                }
            } finally {
                openingJob.cancel()
                focusedOpeningIndicatorVisible = false
            }
        }
    }
    val focusedBootstrapGate = focusedCommentId != null &&
        focusedLandingVerifiedId != focusedCommentId &&
        focusedLandingFailedReason == null &&
        !focusedUserOwnsScroll
    val focusedAnchorOwned = focusedCommentId != null &&
        focusedLandingFailedReason == null &&
        !focusedUserOwnsScroll
    val visitBaselineToken = rememberSaveable(
        post.id,
        state.postVisitId,
        accountStableId,
    ) {
        encodeCommentVisitBaseline(state.commentVisitBaseline(accountStableId, post.id))
    }
    val visitBaseline = remember(visitBaselineToken) {
        decodeCommentVisitBaseline(visitBaselineToken)
    }
    val currentCommentIds = remember(viewportSortedComments) {
        knownCommentIds(viewportSortedComments)
    }
    val newIds = remember(
        state.highlightNewComments,
        visitBaseline,
        currentCommentIds,
        hydrationMaterializedCommentIds,
    ) {
        if (state.highlightNewComments) {
            newCommentIds(visitBaseline, currentCommentIds) - hydrationMaterializedCommentIds
        } else {
            emptySet()
        }
    }
    var manuallyCollapsedIdsToken by rememberSaveable(post.id, state.postVisitId) {
        mutableStateOf("")
    }
    val visitCanBeRecorded = !authenticated || (liveStateMatches && !redditCommentsState.loading)
    LaunchedEffect(
        accountStableId,
        post.id,
        currentCommentIds,
        visitCanBeRecorded,
    ) {
        if (visitCanBeRecorded) {
            state.recordCommentVisit(accountStableId, post.id, currentCommentIds)
        }
    }
    LaunchedEffect(
        viewportSortedComments,
        newIds,
        manuallyCollapsedIdsToken,
        focusedCommentId,
        hydrationMaterializedCommentIds,
    ) {
        if (newIds.isEmpty()) return@LaunchedEffect
        val manuallyCollapsedIds = decodeCommentIdToken(manuallyCollapsedIdsToken)
        val ancestorIds = commentAncestorIdsForTargets(viewportSortedComments, newIds)
        val autoExpandedAncestorIds = ancestorIds
            .filterTo(linkedSetOf()) { id ->
                id in state.collapsedCommentIds && id !in manuallyCollapsedIds
            }
        val focusedProtectedPathIds = commentFocusedPathIds(
            comments = viewportSortedComments,
            focusedCommentId = focusedCommentId,
        )
        val siblingBranchesToCollapse = commentSiblingBranchesToCollapse(
            comments = viewportSortedComments,
            targetIds = newIds,
            autoExpandedAncestorIds = autoExpandedAncestorIds,
        ) - focusedProtectedPathIds
        uiDebugLog(
            COMMENT_NAV_LOG_TAG,
            "phase=new_comment_reveal post=${post.id} new=${newIds.size} " +
                "hydrationExcluded=${hydrationMaterializedCommentIds.size} " +
                "collapseCandidates=${siblingBranchesToCollapse.size} " +
                "focusedProtected=${focusedProtectedPathIds.size}",
        )
        state.revealNewCommentPaths(
            ancestorIds = ancestorIds,
            siblingBranchIds = siblingBranchesToCollapse,
            manuallyCollapsedIds = manuallyCollapsedIds,
        )
    }
    LaunchedEffect(
        hydrationContinuityRevision,
        mainCommentRows,
        collapsedCommentIds,
        focusedCommentId,
    ) {
        val revision = hydrationContinuityRevision ?: return@LaunchedEffect
        val targetId = focusedCommentId ?: run {
            hydrationContinuityRevision = null
            return@LaunchedEffect
        }
        if (!hydrationTargetVisibleBeforeCommit) {
            hydrationContinuityRevision = null
            return@LaunchedEffect
        }
        withFrameNanos { }
        val targetVisibleAfter = focusedCommentViewportRowIndex(
            rows = latestCommentRows,
            focusedCommentId = targetId,
        ) >= 0
        val targetAncestorIds = commentAncestorIdsForTargets(
            comments = viewportSortedComments,
            targetIds = setOf(targetId),
        )
        val collapsedTargetAncestors = targetAncestorIds.intersect(state.collapsedCommentIds)
        uiDebugLog(
            COMMENT_NAV_LOG_TAG,
            "phase=hydration_target_continuity post=${post.id} target=$targetId " +
                "revision=$revision before=true after=$targetVisibleAfter " +
                "collapsedAncestors=${collapsedTargetAncestors.size}",
        )
        if (!targetVisibleAfter) {
            Log.e(
                COMMENT_NAV_LOG_TAG,
                "phase=hydration_target_projection_lost post=${post.id} target=$targetId " +
                    "revision=$revision collapsedAncestors=${collapsedTargetAncestors.joinToString(",")}",
            )
        }
        hydrationContinuityRevision = null
    }
    val toggleComment: (String) -> Unit = { id ->
        val manuallyCollapsedIds = decodeCommentIdToken(manuallyCollapsedIdsToken).toMutableSet()
        if (id in state.collapsedCommentIds) {
            manuallyCollapsedIds.remove(id)
        } else {
            manuallyCollapsedIds.add(id)
        }
        manuallyCollapsedIdsToken = encodeCommentIdToken(manuallyCollapsedIds)
        state.toggleComment(id)
    }
    val loadMore: (Comment, Boolean) -> Unit = { moreNode, userInitiated ->
        if (redditCommentsState.fullDiscussionLoaded) {
            if (userInitiated) failedMoreNodeIds = failedMoreNodeIds - moreNode.id
            if (userInitiated && !focusedUserOwnsScroll) {
                // LOWUI8_RETRY_RECORDS_SCROLL_OWNERSHIP_V1 (OCB-321): recorded in
                // the registry like a drag, so a lost saveable state cannot pull
                // the reader back to the linked comment.
                if (focusedCommentId != null) {
                    FocusedCommentScrollOwnershipRegistry.markUserOwned(focusedTransactionKey)
                }
                focusedUserOwnsScroll = true
                uiDebugLog(
                    COMMENT_NAV_LOG_TAG,
                    "phase=anchor_released post=${post.id} target=${focusedCommentId ?: "none"} " +
                        "reason=retry_more",
                )
            }
            val layout = listState.layoutInfo
            if (pendingHydrationCommit == null) {
                val moreKey = "more:${moreNode.id}"
                val moreVisible = layout.visibleItemsInfo.any { item ->
                    item.key.toString() == moreKey
                }
                // ISSUE110_REPLIES_RETRY_PUBLISHES_IN_VIEW_V1: a Retry is
                // published like automatic loading, while its row is on
                // screen (stable keys keep the viewport still); the manual
                // path waited for the row to leave the screen and spun.
                pendingHydrationCommit = CommentHydrationCommitFence(
                    moreNodeId = moreNode.id,
                    sourceContentRevision = viewportContentRevision,
                    gestureGeneration = commentHydrationGestureGeneration,
                )
                uiDebugLog(
                    COMMENT_NAV_LOG_TAG,
                    "phase=hydration_commit_fence post=${post.id} node=${moreNode.id} " +
                        "revision=$viewportContentRevision gesture=$commentHydrationGestureGeneration " +
                        "userInitiated=$userInitiated placeholderVisible=$moreVisible",
                )
            }
            uiDebugLog(
                COMMENT_NAV_LOG_TAG,
                "phase=inline_hydration_start post=${post.id} node=${moreNode.id} " +
                    "remaining=${moreNode.moreCount} automatic=${!userInitiated} " +
                    "gesture=$commentHydrationGestureGeneration budget=$commentHydrationBudget",
            )
            val started = onLoadMoreRedditComments(
                accountStableId,
                post.id,
                sort,
                moreNode.id,
            )
            // OCB581_REFUSED_LOAD_IS_NOT_AWAITED_V1: the fence set above waits
            // for an answer a refused load never sends; it would hold every
            // later hydration back.
            if (!started && pendingHydrationCommit?.moreNodeId == moreNode.id) {
                pendingHydrationCommit = null
            }
        }
    }

    if (managingPost) {
        PostManageScreen(
            post = post,
            state = state,
            manageState = postManageState,
            onLoad = onLoadPostManagement,
            onMutate = onMutatePostManagement,
            onAuthorize = onAuthorizePostManagement,
            onCancelAuthorization = onCancelPostManageAuthorization,
            onReset = onResetPostManageState,
            onClose = {
                managingPost = false
                onResetPostManageState()
            },
            modifier = modifier,
        )
        return
    }

    if (editingPost) {
        PostEditScreen(
            post = post,
            state = state,
            editState = postEditState,
            onSubmitEdit = onEditPost,
            onAuthorizeEdit = onAuthorizePostEdit,
            onCancelAuthorization = onCancelPostEditAuthorization,
            onResetEditState = onResetPostEditState,
            onCheckAgain = onCheckPostEdit,
            onSaved = { editedBody ->
                state.applyEditedPostBody(post.id, editedBody)
                editingPost = false
                onResetPostEditState()
            },
            onClose = {
                editingPost = false
                onResetPostEditState()
            },
            modifier = modifier,
        )
        return
    }

    LaunchedEffect(postDeleteState.phase, postDeleteState.completionRevision, deletingPost) {
        if (
            deletingPost &&
            postDeleteState.phase == PostDeletePhase.SUCCEEDED &&
            postDeleteState.matches(accountStableId, normalizePostEditFullname(post.id))
        ) {
            deletingPost = false
            onResetPostDeleteState()
            state.applyDeletedPost(post.id)
        }
    }

    if (deletingPost) {
        val postFullname = normalizePostEditFullname(post.id)
        val deleteState = if (postDeleteState.matches(accountStableId, postFullname)) {
            postDeleteState
        } else {
            PostDeleteUiState()
        }
        AlertDialog(
            // ISSUE96_CANCEL_WAITING_AUTHORIZATION_V1: closing cancels a pending
            // authorization; only deleting and checking keep the dialog.
            onDismissRequest = {
                if (!postDeleteCloseLocked(deleteState.phase)) {
                    onCancelPostDeleteAuthorization()
                    deletingPost = false
                    onResetPostDeleteState()
                }
            },
            title = {
                Text(
                    when {
                        deleteState.phase == PostDeletePhase.AUTHORIZATION_REQUIRED -> "Permission required"
                        deleteState.failureKind == PostDeleteFailureKind.OUTCOME_UNKNOWN &&
                            deleteState.phase == PostDeletePhase.FAILED -> "Deletion not confirmed"
                        else -> "Delete post?"
                    },
                )
            },
            text = {
                if (deleteState.phase == PostDeletePhase.FAILED) {
                    ErrorNotice(
                        presentation = postDeleteErrorPresentation(deleteState).copy(primaryAction = null),
                        diagnosticScopeKey = "post_delete",
                        onAction = {},
                    )
                } else {
                    Text(postDeleteDialogMessage(deleteState))
                }
            },
            confirmButton = {
                when (deleteState.phase) {
                    PostDeletePhase.AUTHORIZATION_REQUIRED -> TextButton(onClick = onAuthorizePostDelete) {
                        Text("Authorize")
                    }
                    PostDeletePhase.AUTHORIZING,
                    PostDeletePhase.AWAITING_CALLBACK,
                    PostDeletePhase.SENDING,
                    PostDeletePhase.CHECKING,
                    -> TextButton(onClick = {}, enabled = false) {
                        InlineProgressIndicator()
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when (deleteState.phase) {
                                PostDeletePhase.SENDING -> "Deleting…"
                                PostDeletePhase.CHECKING -> "Checking…"
                                else -> "Authorizing…"
                            },
                        )
                    }
                    PostDeletePhase.SUCCEEDED -> Unit
                    // H3_POST_DELETE_CHECK_AGAIN_READS_ONLY_V1
                    PostDeletePhase.FAILED
                    -> if (deleteState.failureKind == PostDeleteFailureKind.OUTCOME_UNKNOWN) {
                        TextButton(onClick = onCheckPostDelete) { Text("Check again") }
                    } else {
                        PostDeleteConfirmButton(
                            deleteState = deleteState,
                            onDelete = {
                                onDeletePost(
                                    PostDeleteRequest(
                                        accountStableId = accountStableId,
                                        postFullname = postFullname,
                                        postAuthor = post.author,
                                    ),
                                )
                            },
                        )
                    }
                    PostDeletePhase.IDLE,
                    -> PostDeleteConfirmButton(
                        deleteState = deleteState,
                        onDelete = {
                            onDeletePost(
                                PostDeleteRequest(
                                    accountStableId = accountStableId,
                                    postFullname = postFullname,
                                    postAuthor = post.author,
                                ),
                            )
                        },
                    )
                }
            },
            dismissButton = {
                if (deleteState.phase != PostDeletePhase.SUCCEEDED) {
                    TextButton(
                        enabled = !postDeleteCloseLocked(deleteState.phase),
                        onClick = {
                            onCancelPostDeleteAuthorization()
                            deletingPost = false
                            onResetPostDeleteState()
                        },
                    ) { Text("Cancel") }
                }
            },
        )
    }

    composerConflict?.let { request ->
        val existing = state.commentComposerDraft
        AlertDialog(
            onDismissRequest = { composerConflict = null },
            title = { Text("Discard your unsent reply?") },
            text = {
                Text(
                    unsentCommentDraftConflictText(
                        existing = existing,
                        currentPostId = post.id,
                        writtenWithAnotherAccount = existing != null &&
                            commentDraftBelongsToAnotherAccount(existing, state.activeAccountId),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        composerConflict = null
                        state.discardCommentComposerDraft()
                        state.openCommentComposer(post, request.comment)
                    },
                ) {
                    Text("Discard and reply")
                }
            },
            dismissButton = {
                TextButton(onClick = { composerConflict = null }) {
                    Text("Keep draft")
                }
            },
        )
    }

    if (inlineMediaEditBlocked || editingCommentHasUnsupportedInlineMedia) {
        AlertDialog(
            onDismissRequest = {
                inlineMediaEditBlocked = false
                editingCommentId = null
                onResetCommentEditState()
            },
            title = { Text("Can't edit this comment yet") },
            text = {
                Text(
                    "Comments with media attached in subshell can't be edited yet. " +
                        "subshell can't safely keep that media during an edit.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        inlineMediaEditBlocked = false
                        editingCommentId = null
                        onResetCommentEditState()
                    },
                ) {
                    Text("OK")
                }
            },
        )
    }

    // LOWUI8_WRITE_REFRESH_KEEPS_THE_FOCUSED_COMMENT_V1 (OCB-238): after an
    // edit or delete on a comment link, the refresh keeps the linked comment
    // (as pull-to-refresh does) unless that comment is the one deleted.
    val refreshCommentsAfterWrite: (String?) -> Unit = { deletedCommentId ->
        val focusedId = focusedCommentId
        if (focusedId != null && focusedId != deletedCommentId) {
            onRefreshRedditCommentsPreservingFocusedTarget(accountStableId, post.id, sort, focusedId)
        } else {
            onRetryRedditComments(accountStableId, post.id, sort)
        }
    }

    LaunchedEffect(commentDeleteState.phase, commentDeleteState.completionRevision, deletingCommentId) {
        val targetId = deletingCommentId ?: return@LaunchedEffect
        if (commentDeleteState.phase == CommentDeletePhase.SUCCEEDED && commentDeleteState.matches(accountStableId, targetId)) {
            deletingCommentId = null
            onResetCommentDeleteState()
            refreshCommentsAfterWrite(targetId)
        }
    }

    if (deletingComment != null) {
        val deleteState = if (commentDeleteState.matches(accountStableId, deletingComment.id)) commentDeleteState else CommentDeleteUiState()
        AlertDialog(
            // ISSUE96_CANCEL_WAITING_AUTHORIZATION_V1
            onDismissRequest = {
                if (!commentDeleteCloseLocked(deleteState.phase)) {
                    onCancelCommentDeleteAuthorization()
                    deletingCommentId = null
                    onResetCommentDeleteState()
                }
            },
            title = {
                Text(
                    when {
                        deleteState.phase == CommentDeletePhase.AUTHORIZATION_REQUIRED ->
                            "Permission required"
                        deleteState.phase == CommentDeletePhase.FAILED &&
                            deleteState.failureKind ==
                                CommentDeleteFailureKind.OUTCOME_NOT_CONFIRMED ->
                            "Deletion not confirmed"
                        else -> "Delete comment?"
                    },
                )
            },
            text = {
                if (deleteState.phase == CommentDeletePhase.FAILED) {
                    ErrorNotice(
                        presentation = commentDeleteErrorPresentation(deleteState).copy(primaryAction = null),
                        diagnosticScopeKey = "comments_delete",
                        onAction = {},
                    )
                } else {
                    Text(commentDeleteDialogMessage(deleteState))
                }
            },
            confirmButton = {
                when (deleteState.phase) {
                    CommentDeletePhase.AUTHORIZATION_REQUIRED -> TextButton(onClick = onAuthorizeCommentDelete) { Text("Authorize") }
                    CommentDeletePhase.AUTHORIZING, CommentDeletePhase.AWAITING_CALLBACK, CommentDeletePhase.SENDING -> TextButton(onClick = {}, enabled = false) {
                        InlineProgressIndicator()
                        Spacer(Modifier.width(8.dp))
                        Text(if (deleteState.phase == CommentDeletePhase.SENDING) "Deleting…" else "Authorizing…")
                    }
                    CommentDeletePhase.SUCCEEDED -> Unit
                    CommentDeletePhase.IDLE -> TextButton(
                        onClick = {
                            onDeleteComment(
                                CommentDeleteRequest(
                                    accountStableId,
                                    post.id,
                                    deletingComment.id,
                                    deletingComment.author,
                                    sort,
                                ),
                            )
                        },
                    ) {
                        Text(
                            "Delete",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    CommentDeletePhase.FAILED -> {
                        if (
                            deleteState.failureKind ==
                            CommentDeleteFailureKind.OUTCOME_NOT_CONFIRMED
                        ) {
                            TextButton(
                                onClick = {
                                    refreshCommentsAfterWrite(deletingCommentId)
                                    deletingCommentId = null
                                    onResetCommentDeleteState()
                                },
                            ) {
                                Text("Check again")
                            }
                        } else {
                            TextButton(
                                enabled = deleteState.retryable,
                                onClick = {
                                    onDeleteComment(
                                        CommentDeleteRequest(
                                            accountStableId,
                                            post.id,
                                            deletingComment.id,
                                            deletingComment.author,
                                            sort,
                                        ),
                                    )
                                },
                            ) {
                                Text(
                                    "Retry",
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            },
            dismissButton = {
                if (deleteState.phase != CommentDeletePhase.SUCCEEDED) {
                    TextButton(
                        enabled = !commentDeleteCloseLocked(deleteState.phase),
                        onClick = {
                            onCancelCommentDeleteAuthorization()
                            deletingCommentId = null
                            onResetCommentDeleteState()
                        },
                    ) {
                        Text(
                            if (
                                deleteState.phase == CommentDeletePhase.FAILED &&
                                deleteState.failureKind ==
                                    CommentDeleteFailureKind.OUTCOME_NOT_CONFIRMED
                            ) {
                                "Close"
                            } else {
                                "Cancel"
                            },
                        )
                    }
                }
            },
        )
    }

    if (editingComment != null && !editingCommentHasUnsupportedInlineMedia) {
        CommentEditScreen(
            post = post,
            comment = editingComment,
            state = state,
            commentSort = sort,
            editState = commentEditState,
            onSubmitEdit = onEditComment,
            onAuthorizeEdit = onAuthorizeCommentEdit,
            onCancelAuthorization = onCancelCommentEditAuthorization,
            onResetEditState = onResetCommentEditState,
            onCheckAgain = onCheckCommentEdit,
            onSaved = {
                editingCommentId = null
                onResetCommentEditState()
                refreshCommentsAfterWrite(null)
            },
            onClose = {
                editingCommentId = null
                onResetCommentEditState()
            },
            modifier = modifier,
        )
        return
    }

    val activeCommentDraft = state.commentComposerDraft
    val activeCommentComposerVisible =
        state.commentComposerVisible && activeCommentDraft?.target?.postId == post.id

    val postListScope = rememberCoroutineScope()
    val commentControlsIndex = if (addCommentVisible) 3 else 2
    // ISSUE28_TOP_LEVEL_COMMENT_JUMP_V1: see TopLevelCommentJump.kt. A jump is
    // the reader's own scroll, so it releases a focused-comment anchor the way
    // a drag does; the target lands just below the stuck comment header.
    val releaseFocusedAnchorForReaderNavigation: (String) -> Unit = { reason ->
        if (focusedCommentId != null && !focusedUserOwnsScroll) {
            FocusedCommentScrollOwnershipRegistry.markUserOwned(focusedTransactionKey)
            focusedUserOwnsScroll = true
            focusedOpeningIndicatorVisible = false
            uiDebugLog(
                COMMENT_NAV_LOG_TAG,
                "phase=anchor_released post=${post.id} target=$focusedCommentId " +
                    "reason=$reason",
            )
        }
    }
    // UIPOST05_ANY_READER_SCROLL_RELEASES_THE_ANCHOR_V1: only a touch drag
    // released the focused-comment anchor, so TalkBack, keyboard, mouse-wheel
    // and focus scrolling were pulled back to the target. After the landing,
    // any scroll that is not the anchor's own correction releases it.
    // LOWUI8_SCROLL_RELEASE_FOLLOWS_THE_SORT_V1 (OCB-239): the landing and
    // ownership state is saved per sort; keyed on it, the collector reads and
    // releases the current sort's anchor, not the first one's.
    LaunchedEffect(post.id, state.postVisitId, focusedCommentId, sort) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (
                scrolling &&
                focusedCommentId != null &&
                focusedLandingVerifiedId == focusedCommentId &&
                !focusedAnchorCorrection[0]
            ) {
                releaseFocusedAnchorForReaderNavigation("reader_scroll")
            }
        }
    }
    val scrollCommentRowBelowHeader: suspend (Int) -> Unit = { listIndex ->
        listState.animateScrollToItem(listIndex)
        val landed = listState.layoutInfo.visibleItemsInfo
        val header = landed.firstOrNull { item -> item.key == COMMENT_CONTROLS_ITEM_KEY }
        val item = landed.firstOrNull { visible -> visible.index == listIndex }
        if (header != null && item != null) {
            val hidden = header.offset + header.size - item.offset
            if (hidden > 0) listState.animateScrollBy(-hidden.toFloat())
        }
    }
    val jumpToTopLevelComment: (TopLevelCommentJumpDirection) -> Unit = { direction ->
        val layout = listState.layoutInfo
        val position = commentReadingPosition(
            rows = mainCommentRows,
            visibleItems = layout.visibleItemsInfo.map { item ->
                VisibleCommentListItem(key = item.key, offset = item.offset, size = item.size)
            },
            viewportStartOffset = layout.viewportStartOffset,
        )
        val target = topLevelCommentJumpTarget(mainCommentRows, position, direction)
        if (target != null) {
            releaseFocusedAnchorForReaderNavigation("top_level_jump")
            val listIndex = commentViewportLazyListIndex(
                rows = mainCommentRows,
                rowKey = mainCommentRows[target].key,
                addCommentVisible = addCommentVisible,
                liveStatusVisible = liveStatusRowVisible,
            )
            postListScope.launch { scrollCommentRowBelowHeader(listIndex) }
        }
    }
    // ISSUE29_THREAD_FIND_V1: see ThreadFind.kt. The query is matched after a
    // short pause; stepping reveals collapsed ancestors of the match (and
    // forgets them as manual collapses), then lands it below the header.
    var threadFindOpen by rememberSaveable(post.id) { mutableStateOf(false) }
    var threadFindQuery by rememberSaveable(post.id) { mutableStateOf("") }
    var threadFindActiveId by rememberSaveable(post.id) { mutableStateOf<String?>(null) }
    var threadFindAppliedQuery by remember(post.id) { mutableStateOf("") }
    LaunchedEffect(threadFindOpen, threadFindQuery) {
        if (!threadFindOpen) {
            threadFindAppliedQuery = ""
            return@LaunchedEffect
        }
        delay(THREAD_FIND_QUERY_DEBOUNCE_MS)
        threadFindAppliedQuery = threadFindQuery
    }
    // UIPOST05: matching projects every comment's markdown, so it runs off the
    // main thread; the previous result stays until the new one is ready.
    // The query the current matches were computed for (OCB-280).
    var threadFindMatchesQuery by remember(post.id) { mutableStateOf("") }
    val threadFindMatches by produceState(
        initialValue = emptyList<String>(),
        viewportSortedComments,
        threadFindAppliedQuery,
    ) {
        val query = threadFindAppliedQuery
        value = if (query.isBlank()) {
            emptyList()
        } else {
            withContext(Dispatchers.Default) {
                threadFindMatchIds(viewportSortedComments, query)
            }
        }
        threadFindMatchesQuery = query
    }
    val threadFindActiveIndex = threadFindMatches.indexOf(threadFindActiveId)
    // LOWUI8_THREAD_FIND_SEARCH_USES_THE_TYPED_QUERY_V1 (OCB-280): the
    // keyboard's Search applies the typed query at once and steps when the
    // matches for that query are ready, not through the previous query's.
    var threadFindPendingSearch by remember(post.id) { mutableStateOf<String?>(null) }
    val threadFindGoTo: (Boolean) -> Unit = { forward ->
        threadFindStep(threadFindActiveIndex, threadFindMatches.size, forward)?.let { next ->
            val targetId = threadFindMatches[next]
            threadFindActiveId = targetId
            releaseFocusedAnchorForReaderNavigation("thread_find")
            val collapsedAncestors = commentAncestorIdsForTargets(
                comments = viewportSortedComments,
                targetIds = setOf(targetId),
            ).filter { id -> id in state.collapsedCommentIds }.toSet()
            if (collapsedAncestors.isNotEmpty()) {
                manuallyCollapsedIdsToken = encodeCommentIdToken(
                    decodeCommentIdToken(manuallyCollapsedIdsToken) - collapsedAncestors,
                )
                state.revealNewCommentPaths(
                    ancestorIds = collapsedAncestors,
                    siblingBranchIds = emptySet(),
                    manuallyCollapsedIds = emptySet(),
                )
            }
            val rowKey = "comment:$targetId"
            postListScope.launch {
                withTimeoutOrNull(2_000) {
                    snapshotFlow { latestCommentRows.indexOfFirst { row -> row.key == rowKey } }
                        .first { index -> index >= 0 }
                } ?: return@launch
                // LOWUI1_THREAD_FIND_LOOKS_UP_BY_KEY_V1 (OCB-322): the rows may have
                // changed since the index was found; look the row up again by key.
                val lazyIndex = commentViewportLazyListIndex(
                    rows = latestCommentRows,
                    rowKey = rowKey,
                    addCommentVisible = addCommentVisible,
                    liveStatusVisible = latestLiveStatusVisible,
                )
                if (lazyIndex >= 0) scrollCommentRowBelowHeader(lazyIndex)
            }
        }
    }
    LaunchedEffect(threadFindPendingSearch, threadFindMatchesQuery) {
        val pending = threadFindPendingSearch ?: return@LaunchedEffect
        if (pending != threadFindMatchesQuery) return@LaunchedEffect
        threadFindPendingSearch = null
        threadFindGoTo(true)
    }
    val openThreadFind: () -> Unit = {
        threadFindOpen = true
        if (threadFindNeedsHeaderAtTop(listState.firstVisibleItemIndex, commentControlsIndex)) {
            releaseFocusedAnchorForReaderNavigation("thread_find")
            postListScope.launch { listState.animateScrollToItem(commentControlsIndex) }
        }
    }
    val closeThreadFind: () -> Unit = {
        threadFindOpen = false
        threadFindQuery = ""
        threadFindActiveId = null
    }
    BackHandler(enabled = threadFindOpen, onBack = closeThreadFind)
    val threadFindMatchBackground = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    val threadFindActiveBackground = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    val threadFindHighlight: (String) -> MarkdownHighlight? = { commentId ->
        threadFindAppliedQuery.takeIf { query -> threadFindOpen && query.isNotBlank() }?.let { query ->
            MarkdownHighlight(
                query = query,
                background = if (commentId == threadFindActiveId) {
                    threadFindActiveBackground
                } else {
                    threadFindMatchBackground
                },
            )
        }
    }
    val topLevelCommentJumpVisible = state.topLevelCommentJumpButton &&
        !focusedBootstrapGate &&
        !activeCommentComposerVisible &&
        mainCommentRows.any { row -> row.comment.depth <= 0 }
    val commentsEntryReady = commentsEntryTargetReady(
        authenticated = authenticated,
        liveStateMatches = liveStateMatches,
        loading = redditCommentsState.loading,
        loadedCommentCount = sortedComments.size,
    )
    LaunchedEffect(
        post.id,
        state.postVisitId,
        focusedCommentId,
        commentControlsIndex,
        commentsEntryReady,
        sortedComments.size,
    ) {
        if (focusedCommentId != null || !commentsEntryReady) return@LaunchedEffect
        if (
            !state.isPostEntryTargetPending(
                state.postVisitId,
                FeedPostEntryTarget.COMMENTS,
            )
        ) {
            return@LaunchedEffect
        }
        snapshotFlow { listState.layoutInfo.totalItemsCount }
            .first { totalItems -> totalItems > commentControlsIndex }
        listState.scrollToItem(commentControlsIndex)
        state.consumePostEntryTarget(
            state.postVisitId,
            FeedPostEntryTarget.COMMENTS,
        )
    }

    LaunchedEffect(
        authenticated,
        liveStateMatches,
        redditCommentsState.fullDiscussionLoaded,
        redditCommentsState.fullDiscussionLoading,
        redditCommentsState.loadingMoreNodeId,
        redditCommentsState.failedMoreNodeId,
        redditCommentsState.contentRevision,
        mainCommentRows,
        liveStatusRowVisible,
        focusedBootstrapGate,
        focusedAnchorOwned,
        commentHydrationGestureGeneration,
        pendingHydrationCommit,
    ) {
        if (
            !authenticated ||
            !liveStateMatches ||
            !redditCommentsState.fullDiscussionLoaded ||
            focusedBootstrapGate
        ) {
            return@LaunchedEffect
        }
        snapshotFlow {
            val (firstVisible, lastVisible) = commentViewportVisibleRowLazyIndices(
                listState.layoutInfo.visibleItemsInfo.map { item -> item.index to item.key.toString() },
            )
            val hydrationAnchorPending = pendingHydrationCommit != null
            // UIPOST04_SEAMLESS_MORE_REPLIES_V1 (owner: loading is seamless,
            // never a tap): a node already on screen loads first, without the
            // per-gesture budget; the prefetch below the screen stays budgeted.
            commentViewportVisibleIdleMoreNode(
                rows = mainCommentRows,
                firstVisibleLazyIndex = firstVisible,
                lastVisibleLazyIndex = lastVisible,
                addCommentVisible = addCommentVisible,
                liveStatusVisible = liveStatusRowVisible,
                fullDiscussionLoaded = redditCommentsState.fullDiscussionLoaded,
                fullDiscussionLoading = redditCommentsState.fullDiscussionLoading,
                focusedAnchorOwned = focusedAnchorOwned,
                loadingMoreNodeId = redditCommentsState.loadingMoreNodeId,
                failedMoreNodeId = redditCommentsState.failedMoreNodeId,
                scrollInProgress = listState.isScrollInProgress,
                hydrationAnchorPending = hydrationAnchorPending,
                blockedMoreNodeIds = failedMoreNodeIds,
            )?.let { node -> node.id to true } ?: commentViewportAutoHydrationCandidate(
                rows = mainCommentRows,
                firstVisibleLazyIndex = firstVisible,
                lastVisibleLazyIndex = lastVisible,
                addCommentVisible = addCommentVisible,
                liveStatusVisible = liveStatusRowVisible,
                fullDiscussionLoaded = redditCommentsState.fullDiscussionLoaded,
                fullDiscussionLoading = redditCommentsState.fullDiscussionLoading,
                focusedAnchorOwned = focusedAnchorOwned,
                automaticHydrationBudget = commentHydrationBudget,
                loadingMoreNodeId = redditCommentsState.loadingMoreNodeId,
                failedMoreNodeId = redditCommentsState.failedMoreNodeId,
                scrollInProgress = listState.isScrollInProgress,
                hydrationAnchorPending = hydrationAnchorPending,
                blockedMoreNodeIds = failedMoreNodeIds,
            )?.let { node -> node.id to false }
        }.collect { candidate ->
            val (nodeId, onScreen) = candidate ?: return@collect
            val moreNode = mainCommentRows.firstOrNull { row -> row.comment.id == nodeId }
                ?.comment
                ?: return@collect
            if (onScreen) {
                uiDebugLog(
                    COMMENT_NAV_LOG_TAG,
                    "phase=hydration_visible_node post=${post.id} node=${moreNode.id}",
                )
                loadMore(moreNode, false)
                return@collect
            }
            if (commentHydrationBudget <= 0) return@collect
            commentHydrationBudget -= 1
            uiDebugLog(
                COMMENT_NAV_LOG_TAG,
                "phase=hydration_budget_consume post=${post.id} " +
                    "generation=$commentHydrationGestureGeneration " +
                    "remaining=$commentHydrationBudget node=${moreNode.id}",
            )
            loadMore(moreNode, false)
        }
    }

    // M3223_ONE_ROUTE_TOP_BAR_V1: Back and the title stay reachable while the
    // thread scrolls (they were the list's first row and scrolled away).
    val routeTopBarScroll = rememberRouteTopBarScrollBehavior(
        canScroll = { listState.canScrollForward || listState.canScrollBackward },
    )
    Column(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(routeTopBarScroll.nestedScrollConnection),
    ) {
        RouteTopBar(title = "Post", scrollBehavior = routeTopBarScroll, onBack = onBack)
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            RefreshableLazyColumn(
                state = listState,
                // ISSUE205_PASSIVE_SCROLL_POSITION_INDICATOR_V1
                scrollPositionIndicator = state.scrollPositionIndicator,
                scrollPositionMoreToLoad = commentThreadHasMoreToLoad(
                    rows = mainCommentRows,
                    loading = redditCommentsState.loading,
                ),
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !focusedBootstrapGate,
                enabled = authenticated,
                isRefreshing = liveStateMatches && redditCommentsState.loading,
                onRefresh = if (authenticated) {
                    {
                        if (focusedCommentId != null) {
                            onRefreshRedditCommentsPreservingFocusedTarget(
                                accountStableId,
                                post.id,
                                sort,
                                focusedCommentId,
                            )
                        } else {
                            onRetryRedditComments(accountStableId, post.id, sort)
                        }
                    }
                } else {
                    null
                },
                contentPadding = PaddingValues(
                    start = ScreenMargin,
                    top = 4.dp,
                    end = ScreenMargin,
                    bottom = when {
                        !addCommentVisible -> 32.dp
                        activeCommentComposerVisible -> 224.dp
                        else -> 88.dp
                    },
                ),
            ) {
            // The row stays as an empty anchor: commentControlsIndex and the comment
            // viewport projection count it (Back and "Post" are in RouteTopBar).
            item("post-navigation") {}

                item("post-card") {
                    PostCard(
                        post = post,
                        state = state,
                        onOpen = {},
                        onComments = {
                            postListScope.launch {
                                listState.animateScrollToItem(commentControlsIndex)
                            }
                        },
                        bodyMaxLines = Int.MAX_VALUE,
                        suppressWhenHiddenStateDiffers = false,
                        // ISSUE217_OPEN_POST_TAKES_SWIPE_ACTIONS_V1 (owner reversed the #16
                        // opt-out): the post card at the top swipes like a feed card;
                        // Hide works as the card's Hide/Unhide menu item (the card stays).
                        swipeActionsEnabled = true,
                        inPostDetail = true,
                        onEditPost = {
                            onResetPostEditState()
                            editingPost = true
                        },
                        onManagePost = {
                            onResetPostManageState()
                            managingPost = true
                        },
                        onDeletePost = {
                            onResetPostDeleteState()
                            deletingPost = true
                        },
                        onRefreshComments = if (authenticated) {
                            {
                                if (focusedCommentId != null) {
                                    onRefreshRedditCommentsPreservingFocusedTarget(
                                        accountStableId,
                                        post.id,
                                        sort,
                                        focusedCommentId,
                                    )
                                } else {
                                    onRetryRedditComments(accountStableId, post.id, sort)
                                }
                            }
                        } else {
                            null
                        },
                    )
                }
                if (state.localMutationsAllowed || state.activeAccountIsAuthenticated) {
                    item("add-comment") {
                        // Kept as a stable lazy-list index anchor for comment navigation/hydration.
                        // The actual composer launcher is bottom-anchored below the list.
                        Spacer(Modifier.height(1.dp))
                    }
                }

                stickyHeader("comment-controls") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        Column {
                            if (threadFindOpen) {
                                ThreadFindBar(
                                    query = threadFindQuery,
                                    onQueryChange = { query -> threadFindQuery = query },
                                    countLabel = if (threadFindAppliedQuery.isBlank()) {
                                        null
                                    } else {
                                        threadFindCountLabel(
                                            active = threadFindActiveIndex,
                                            size = threadFindMatches.size,
                                        )
                                    },
                                    countDescription = threadFindCountDescription(
                                        active = threadFindActiveIndex,
                                        size = threadFindMatches.size,
                                    ),
                                    onNext = { threadFindGoTo(true) },
                                    onPrevious = { threadFindGoTo(false) },
                                    onClose = closeThreadFind,
                                    onSearch = {
                                        if (threadFindQuery.isNotBlank()) {
                                            threadFindAppliedQuery = threadFindQuery
                                            threadFindPendingSearch = threadFindQuery
                                        }
                                    },
                                )
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "${post.comments} comments",
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (newIds.isNotEmpty()) {
                                        NewCommentBadge("${newIds.size} new")
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    Box {
                                        TextButton(onClick = { sortMenu = true }) {
                                            // LOWUI7_LABELS_SAY_WHAT_A_CONTROL_DOES_V1 (OCB-240)
                                            Text(
                                                sort.label,
                                                modifier = Modifier.semantics {
                                                    contentDescription = "Sort comments: ${sort.label}"
                                                },
                                            )
                                            Icon(
                                                Icons.Rounded.KeyboardArrowDown,
                                                contentDescription = null,
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = sortMenu,
                                            onDismissRequest = { sortMenu = false },
                                        ) {
                                            RedditCommentSort.entries.forEach { option ->
                                                // ISSUE219_COMMENT_SORT_MENU_MARKS_THE_CURRENT_SORT_V1:
                                                // as the community time-period menu.
                                                DropdownMenuItem(
                                                    text = { Text(option.label) },
                                                    modifier = Modifier.semantics { selected = option == sort },
                                                    trailingIcon = if (option == sort) {
                                                        { Icon(Icons.Rounded.Check, contentDescription = null) }
                                                    } else {
                                                        null
                                                    },
                                                    onClick = {
                                                        manualSortName = option.name
                                                        sortMenu = false
                                                    },
                                                )
                                            }
                                        }
                                    }
                                    IconButton(onClick = openThreadFind) {
                                        Icon(
                                            Icons.Rounded.Search,
                                            contentDescription = THREAD_FIND_OPEN_LABEL,
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                            )
                        }
                    }
                }
                if (liveStatusVisible && !pinnedCommentFailure) {
                    item("live-comment-status") {
                        LiveCommentStatus(
                            matches = liveStateMatches,
                            state = redditCommentsState,
                            onRetry = {
                                if (focusedCommentId != null) {
                                    onRefreshRedditCommentsPreservingFocusedTarget(
                                        accountStableId,
                                        post.id,
                                        sort,
                                        focusedCommentId,
                                    )
                                } else {
                                    onRetryRedditComments(accountStableId, post.id, sort)
                                }
                            },
                        )
                    }
                }
                items(
                    items = mainCommentRows,
                    key = CommentViewportRow::key,
                    contentType = CommentViewportRow::kind,
                ) { row ->
                    when (row.kind) {
                        CommentViewportRowKind.COMMENT -> {
                            // ISSUE16_COMMENT_SWIPE_ACTION_SURFACE_V1
                            val swipeComment = row.comment
                            val swipeCommentVote = state.effectiveCommentVote(swipeComment)
                            SwipeActionSurface(
                                modifier = Modifier.fillMaxWidth(),
                                enabled = state.swipeActionsEnabled,
                                reduceMotion = state.reduceMotion,
                                shape = MaterialTheme.shapes.small,
                                leftAction = commentSwipeActionVisual(
                                    action = state.commentSwipeLeftAction,
                                    vote = swipeCommentVote,
                                    enabled = commentSwipeActionEnabled(
                                        state,
                                        swipeComment,
                                        state.commentSwipeLeftAction,
                                    ),
                                    voteColorPalette = state.voteColorPalette,
                                    voteControlMode = state.voteControlMode,
                                    darkTheme = isDarkScheme(),
                                ),
                                rightAction = commentSwipeActionVisual(
                                    action = state.commentSwipeRightAction,
                                    vote = swipeCommentVote,
                                    enabled = commentSwipeActionEnabled(
                                        state,
                                        swipeComment,
                                        state.commentSwipeRightAction,
                                    ),
                                    voteColorPalette = state.voteColorPalette,
                                    voteControlMode = state.voteControlMode,
                                    darkTheme = isDarkScheme(),
                                ),
                                onLeft = {
                                    dispatchCommentSwipeAction(
                                        state,
                                        swipeComment,
                                        state.commentSwipeLeftAction,
                                    )
                                },
                                onRight = {
                                    dispatchCommentSwipeAction(
                                        state,
                                        swipeComment,
                                        state.commentSwipeRightAction,
                                    )
                                },
                            ) { swipeContentModifier ->
                                Box(
                                    modifier = swipeContentModifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.background),
                                ) {
                                    CommentRow(
                            post = post,
                            comment = row.comment,
                            state = state,
                            focusedCommentId = focusedCommentId,
                            focusedLandingActive = focusedAnchorOwned,
                            focusedArrivalPulseGeneration = focusedArrivalPulseGeneration,
                            newCommentIds = newIds,
                            collapsed = row.comment.id in collapsedCommentIds,
                            threadVisual = branchRailVisualByCommentId[row.comment.id],
                            parentConnectorVisual =
                                parentConnectorVisualByCommentId[row.comment.id],
                            onToggleComment = toggleComment,
                            findHighlight = threadFindHighlight(row.comment.id),
                            onReply = { comment -> openComposer(comment) },
                            onDelete = { comment ->
                                onResetCommentDeleteState()
                                deletingCommentId = comment.id
                            },
                            onEdit = { comment ->
                                onResetCommentEditState()
                                if (
                                    commentContainsUnsupportedEditableInlineMedia(
                                        body = comment.body,
                                        hasLocalMedia = comment.localMediaUri != null,
                                    )
                                ) {
                                    inlineMediaEditBlocked = true
                                    editingCommentId = null
                                } else {
                                    inlineMediaEditBlocked = false
                                    editingCommentId = comment.id
                                }
                            },
                                                        )
                                }
                            }
                        }
                        CommentViewportRowKind.MORE_REPLIES -> MoreRepliesRow(
                            comment = row.comment,
                            loading = redditCommentsState.loadingMoreNodeId == row.comment.id ||
                                pendingHydrationCommit?.moreNodeId == row.comment.id,
                            failed = redditCommentsState.failedMoreNodeId == row.comment.id ||
                                row.comment.id in failedMoreNodeIds,
                            fullDiscussionReady = redditCommentsState.fullDiscussionLoaded,
                            fullDiscussionLoading = redditCommentsState.fullDiscussionLoading,
                            onRetry = { comment -> loadMore(comment, true) },
                        )
                    }
                    val showDivider = when (row.kind) {
                        CommentViewportRowKind.COMMENT -> true
                        CommentViewportRowKind.MORE_REPLIES -> commentTransportMoreRowVisible(
                            fullDiscussionReady = redditCommentsState.fullDiscussionLoaded,
                            fullDiscussionLoading = redditCommentsState.fullDiscussionLoading,
                            loading = redditCommentsState.loadingMoreNodeId == row.comment.id ||
                                pendingHydrationCommit?.moreNodeId == row.comment.id,
                            failed = redditCommentsState.failedMoreNodeId == row.comment.id ||
                                row.comment.id in failedMoreNodeIds,
                        )
                    }
                    if (showDivider) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f),
                        )
                    }
                }
                if (focusedAnchorOwned) {
                    item("focused-comment-landing-space") {
                        Spacer(Modifier.fillParentMaxHeight(0.60f))
                    }
                }
            }

            if (addCommentVisible && !focusedBootstrapGate) {
                // COMPOSER232_COMPOSER_SITS_ON_THE_KEYBOARD_V1 (#232): the composer
                // fits above the keyboard by the IME ruler, as CommentEditScreen
                // does; imePadding counted the navigation bar the route already
                // leaves out and left a see-through strip above the keyboard.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .fitInside(WindowInsetsRulers.Ime.current),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                if (activeCommentComposerVisible) {
                    CommentComposerScreen(
                        post = post,
                        state = state,
                        commentSort = sort,
                        commentWriteState = commentWriteState,
                        onSubmitComment = onSubmitComment,
                        onAuthorizeCommentWrite = onAuthorizeCommentWrite,
                        onCancelCommentWriteAuthorization = onCancelCommentWriteAuthorization,
                        onResetCommentWriteState = onResetCommentWriteState,
                        commentMediaPolicies = commentMediaPolicies,
                        onLoadCommentMediaPolicy = onLoadCommentMediaPolicy,
                        threadComments = sessionComments,
                        mentionSearchState = mentionSearchState,
                        onSearchMentions = onSearchMentions,
                        onClose = state::closeCommentComposer,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    CommentComposerLauncher(
                        onClick = { openComposer(null) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                }
            }

            if (topLevelCommentJumpVisible) {
                TopLevelCommentJumpButton(
                    onNext = { jumpToTopLevelComment(TopLevelCommentJumpDirection.NEXT) },
                    onPrevious = { jumpToTopLevelComment(TopLevelCommentJumpDirection.PREVIOUS) },
                    modifier = Modifier
                        .align(
                            if (state.interactionSide.floatingActionAtStart()) {
                                Alignment.BottomStart
                            } else {
                                Alignment.BottomEnd
                            },
                        )
                        .padding(
                            horizontal = 16.dp,
                            vertical = if (addCommentVisible) 80.dp else 16.dp,
                        ),
                )
            }

            if (focusedBootstrapGate) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    // LOWUX4_COMMENT_LINK_GATE_KEEPS_A_WAY_OUT_V1 (OCB-241): the gate
                    // covered the screen's Back for as long as the landing took; it
                    // keeps Back, and "Show thread" hands the scroll to the reader.
                    Box(modifier = Modifier.fillMaxSize()) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                        if (focusedOpeningIndicatorVisible) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                InlineProgressIndicator()
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = "Opening comment…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TextButton(
                                    onClick = { releaseFocusedAnchorForReaderNavigation("show_thread") },
                                    modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp),
                                ) {
                                    Text("Show thread")
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
private fun PostDeleteConfirmButton(deleteState: PostDeleteUiState, onDelete: () -> Unit) {
    TextButton(
        enabled = deleteState.phase != PostDeletePhase.FAILED || deleteState.retryable,
        onClick = onDelete,
    ) {
        Text(
            if (deleteState.phase == PostDeletePhase.FAILED && deleteState.retryable) "Retry" else "Delete",
            color = MaterialTheme.colorScheme.error,
        )
    }
}

private fun postDeleteDialogMessage(state: PostDeleteUiState): String = when (state.phase) {
    PostDeletePhase.AUTHORIZATION_REQUIRED ->
        "subshell needs Reddit's edit permission before it can delete this post."
    PostDeletePhase.FAILED -> when (state.failureKind) {
        PostDeleteFailureKind.CONFIGURATION -> "Post deletion is not configured for this account."
        PostDeleteFailureKind.ACCOUNT_CHANGED -> "The active account changed. Reopen the post and try again."
        PostDeleteFailureKind.NOT_OWNER -> "This post is not owned by the active account."
        PostDeleteFailureKind.BROWSER_LAUNCH -> "The authorization browser could not be opened."
        PostDeleteFailureKind.CALLBACK -> "The authorization callback could not be completed."
        PostDeleteFailureKind.AUTHORIZATION -> "Reddit did not grant the permission required to delete this post."
        PostDeleteFailureKind.SESSION -> "The Reddit session could not be refreshed."
        PostDeleteFailureKind.RATE_LIMIT -> state.retryAfterMs?.let { retryAfterMs ->
            "Reddit rate-limited the request. Try again in about ${(retryAfterMs / 1000L).coerceAtLeast(1L)} seconds."
        } ?: "Reddit rate-limited the request. Try again shortly."
        PostDeleteFailureKind.HTTP -> state.httpStatusCode?.let { statusCode ->
            "Reddit rejected the delete request (HTTP $statusCode)."
        } ?: "Reddit rejected the delete request."
        PostDeleteFailureKind.TRANSPORT -> "The delete request could not reach Reddit."
        PostDeleteFailureKind.PROTOCOL -> "Reddit returned an unexpected response."
        PostDeleteFailureKind.ALREADY_DELETING -> "A post deletion is already in progress."
        PostDeleteFailureKind.OUTCOME_UNKNOWN -> "Reddit may have deleted the post."
        PostDeleteFailureKind.NOT_DELETED -> "Reddit didn't delete the post."
        null -> "The post could not be deleted."
    }
    else -> "This permanently deletes your post from Reddit. This can't be undone."
}

@Composable
private fun LiveCommentStatus(
    matches: Boolean,
    state: RedditCommentsUiState,
    onRetry: () -> Unit,
) {
    val loading = !matches || state.loading || state.fullDiscussionLoading
    val hasCachedComments = matches && state.comments.isNotEmpty()
    val failure = state.failureKind
    if (!loading && failure != null) {
        ErrorNotice(
            presentation = redditCommentsErrorPresentation(
                failureKind = failure,
                details = state.failureDetails,
                retryable = state.failureRetryable,
                showingCachedContent = hasCachedComments,
            ),
            diagnosticScopeKey = "comments_thread",
            modifier = Modifier.padding(top = 10.dp),
            onAction = { action ->
                if (action == ErrorActionKind.RETRY) onRetry()
            },
        )
        return
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                InlineProgressIndicator()
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = when {
                    state.fullDiscussionLoading && !state.fullDiscussionLoaded && hasCachedComments ->
                        "Loading full discussion…"
                    loading && hasCachedComments ->
                        "Showing cached comments · refreshing…"
                    loading -> "Loading comments…"

                    else -> "No comments returned."
                },
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun commentDeleteDialogMessage(state: CommentDeleteUiState): String = when (state.phase) {
    CommentDeletePhase.IDLE ->
        "This permanently deletes your comment from Reddit. Replies to it will remain."
    CommentDeletePhase.AUTHORIZATION_REQUIRED ->
        "subshell needs Reddit's edit permission before it can delete this comment."
    CommentDeletePhase.AUTHORIZING ->
        "Opening Reddit authorization…"
    CommentDeletePhase.AWAITING_CALLBACK ->
        "Waiting for Reddit authorization…"
    CommentDeletePhase.SENDING ->
        "Deleting the comment from Reddit…"
    CommentDeletePhase.SUCCEEDED ->
        "Comment deleted."
    CommentDeletePhase.FAILED -> when (state.failureKind) {
        CommentDeleteFailureKind.CONFIGURATION ->
            "Comment deletion is not configured for this account."
        CommentDeleteFailureKind.ACCOUNT_CHANGED ->
            "The active account changed before the comment could be deleted."
        CommentDeleteFailureKind.NOT_OWNER ->
            "Only comments authored by the active account can be deleted."
        CommentDeleteFailureKind.BROWSER_LAUNCH ->
            "Reddit authorization could not be opened."
        CommentDeleteFailureKind.CALLBACK ->
            "Reddit authorization did not complete."
        CommentDeleteFailureKind.AUTHORIZATION ->
            "Reddit did not grant the permission required to delete this comment."
        CommentDeleteFailureKind.SESSION ->
            "The Reddit session needs attention before this comment can be deleted."
        CommentDeleteFailureKind.RATE_LIMIT ->
            "Reddit rate-limited the delete request. Try again shortly."
        CommentDeleteFailureKind.HTTP ->
            state.httpStatusCode?.let { code ->
                "Reddit returned HTTP $code while deleting the comment."
            } ?: "Reddit rejected the delete request."
        CommentDeleteFailureKind.TRANSPORT ->
            "Reddit could not be reached. The comment was not deleted."
        CommentDeleteFailureKind.PROTOCOL ->
            "The delete request or Reddit response was not valid."
        CommentDeleteFailureKind.OUTCOME_NOT_CONFIRMED ->
            // LOWUI_DELETE_NOT_CONFIRMED_SAYS_MAY_V1 (OCB-172)
            "Reddit may have received the delete request, but subshell could not confirm that the comment was deleted. Check the thread before trying again."
        CommentDeleteFailureKind.ALREADY_DELETING ->
            "This comment is already being deleted."
        null ->
            "The comment could not be deleted."
    }
}

internal fun commentAuthorProfileTarget(author: String): String? {
    val normalized = author.trim()
    return normalized.takeIf {
        it.isNotEmpty() && !it.equals("[deleted]", ignoreCase = true)
    }
}

@Composable
private fun CommentRow(
    post: Post,
    comment: Comment,
    state: SubshellState,
    focusedCommentId: String?,
    focusedLandingActive: Boolean,
    focusedArrivalPulseGeneration: Int,
    newCommentIds: Set<String>,
    threadVisual: CommentBranchRailVisual?,
    parentConnectorVisual: CommentParentConnectorVisual?,
    onToggleComment: (String) -> Unit,
    onReply: (Comment) -> Unit,
    onEdit: (Comment) -> Unit,
    onDelete: (Comment) -> Unit,
    findHighlight: MarkdownHighlight? = null,
    collapsed: Boolean = comment.id in state.collapsedCommentIds,
) {
    val focused = comment.id == focusedCommentId
    val isNew = comment.id in newCommentIds
    val newReplies = newDescendantCount(comment, newCommentIds)
    val focusedPulse = remember(comment.id) { Animatable(0f) }
    val vote = state.effectiveCommentVote(comment)
    val displayedUpvotes = state.effectiveCommentScore(comment)
    val displayedTime = commentTimeLabel(
        relativeAge = comment.age,
        createdEpochSeconds = comment.createdEpochSeconds,
        format = state.commentTimeFormat,
    )
    val accent = Color(comment.accent)
    val context = LocalContext.current
    val notices = LocalViewportErrorHostState.current
    val ownActions = ownContentActions(
        activeUsername = state.activeAccountUsername,
        author = comment.author,
        contentType = OwnContentType.COMMENT,
    )
    val authorProfileTarget = commentAuthorProfileTarget(comment.author)
    var deletedAuthorUnavailableVisible by rememberSaveable(comment.id) {
        mutableStateOf(false)
    }
    var reportDialogVisible by rememberSaveable(comment.id) {
        mutableStateOf(false)
    }
    val onAuthorClick = {
        if (authorProfileTarget == null) {
            if (comment.author.equals("[deleted]", ignoreCase = true)) {
                deletedAuthorUnavailableVisible = true
            }
        } else {
            deletedAuthorUnavailableVisible = false
            state.openUser(
                authorProfileTarget,
                ProfileActivityEvidence.COMMENT,
            )
        }
    }

    LaunchedEffect(collapsed) {
        if (collapsed) deletedAuthorUnavailableVisible = false
    }

    LaunchedEffect(focused, comment.id, comment.author, comment.body) {
        if (focused) {
            uiDebugLog(
                COMMENT_NAV_LOG_TAG,
                "phase=focused_row_composed target=${comment.id} author=${comment.author} " +
                    "bodyLength=${comment.body.length}",
            )
        }
    }

    LaunchedEffect(focused, focusedArrivalPulseGeneration) {
        if (focused && focusedArrivalPulseGeneration > 0) {
            focusedPulse.snapTo(FOCUSED_COMMENT_PULSE_START_ALPHA)
            focusedPulse.animateTo(
                targetValue = 0f,
                animationSpec = tween(FOCUSED_COMMENT_PULSE_DURATION_MS),
            )
        } else if (!focused) {
            focusedPulse.snapTo(0f)
        }
    }

    val focusedPulseColor = MaterialTheme.colorScheme.primary
    // ISSUE226_REMOVED_COMMENT_STANDS_OUT_V1 (owner, from a phone screenshot:
    // the pale badge was easy to miss): a removed comment has a red edge.
    val removedEdgeColor = MaterialTheme.colorScheme.error
    // ISSUE218_COMMENT_RAILS_KEEP_THEIR_CONTRAST_V1: rail colours come from the
    // stored per-comment accent (cache codec, unchanged); what is drawn reaches
    // 3:1 for the emphasised rails and 1.5:1 for the faintest ancestors, so
    // the fade still shows depth (light theme was 1.3–1.5:1).
    val railBackground = MaterialTheme.colorScheme.surface
    fun railColor(accent: Color, alpha: Float): Color =
        accent.copy(alpha = alpha).withMinContrast(
            railBackground,
            1.5f + 1.5f * ((alpha - 0.18f) / 0.44f).coerceIn(0f, 1f),
        )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (focusedLandingActive) {
                    Modifier
                } else {
                    Modifier.animateContentSize()
                },
            )
            .drawBehind {
                when (state.commentHierarchyStyle) {
                    CommentHierarchyStyle.GUIDE_LINES -> {
                        val railVisual = threadVisual
                        val railAccents = railVisual?.railAccents.orEmpty()
                        val railIds = railVisual?.railIds.orEmpty()
                        val railCount = railAccents.size
                        val layoutRailCount = railVisual?.layoutRailCount ?: railCount
                        if (railVisual != null && railCount > 0 && layoutRailCount >= railCount) {
                            val connectorY = 22.dp.toPx().coerceAtMost(size.height)
                            val isolatedStubHalfHeight = 4.dp.toPx()
                            railAccents.forEachIndexed { index, railAccent ->
                                val railId = railIds.getOrNull(index) ?: return@forEachIndexed
                                val x = commentBranchRailPositionDp(
                                    branchRailIndex = index,
                                    branchRailCount = layoutRailCount,
                                ).dp.toPx()
                                val continuesFromPrevious =
                                    railId in railVisual.continuesFromPreviousRailIds
                                val continuesToNext = railId in railVisual.continuesToNextRailIds
                                var lineStartY = if (continuesFromPrevious) 0f else connectorY
                                var lineEndY = if (continuesToNext) size.height else connectorY
                                if (lineStartY == lineEndY) {
                                    lineStartY = (connectorY - isolatedStubHalfHeight).coerceAtLeast(0f)
                                    lineEndY = (connectorY + isolatedStubHalfHeight).coerceAtMost(size.height)
                                }
                                val relativePosition = if (layoutRailCount <= 1) {
                                    1f
                                } else {
                                    index.toFloat() / (layoutRailCount - 1).toFloat()
                                }
                                drawLine(
                                    color = railColor(Color(railAccent), 0.18f + (0.50f * relativePosition)),
                                    start = Offset(x, lineStartY),
                                    end = Offset(x, lineEndY),
                                    strokeWidth = 1.5.dp.toPx(),
                                )
                            }

                            val lastRailX = commentBranchRailPositionDp(
                                branchRailIndex = railCount - 1,
                                branchRailCount = layoutRailCount,
                            ).dp.toPx()
                            if (railVisual.startsNewBranchLane) {
                                val previousRailX = if (railCount > 1) {
                                    commentBranchRailPositionDp(
                                        branchRailIndex = railCount - 2,
                                        branchRailCount = layoutRailCount,
                                    ).dp.toPx()
                                } else {
                                    0f
                                }
                                drawLine(
                                    color = railColor(accent, 0.62f),
                                    start = Offset(previousRailX, connectorY),
                                    end = Offset(lastRailX, connectorY),
                                    strokeWidth = 1.5.dp.toPx(),
                                )
                            }
                            val gutterEndX = commentBranchRailGutterDp(
                                branchRailCount = railCount,
                                layoutRailCount = layoutRailCount,
                            ).dp.toPx()
                            drawLine(
                                color = railColor(accent, 0.62f),
                                start = Offset(lastRailX, connectorY),
                                end = Offset(gutterEndX, connectorY),
                                strokeWidth = 1.5.dp.toPx(),
                            )
                        }
                    }
                    CommentHierarchyStyle.PARENT_CONNECTORS -> {
                        val visual = parentConnectorVisual
                        if (visual != null) {
                            /*
                             * ISSUE10A3_PARENT_EDGE_GEOMETRY_V1
                             *
                             * Incoming edges enter a child close to its header.
                             * Outgoing edges do NOT originate beside the
                             * parent's username. They leave near the lower edge
                             * of the complete parent comment.
                             */
                            val childEntryY =
                                22.dp.toPx().coerceAtMost(size.height)
                            val parentExitY =
                                (size.height - 18.dp.toPx())
                                    .coerceIn(0f, size.height)
                            val strokeWidth = 1.5.dp.toPx()

                            visual.ancestorContinuationLanes.forEach { lane ->
                                val x = commentParentConnectorPositionDp(
                                    lane.connectorDepth,
                                ).dp.toPx()

                                drawLine(
                                    color = railColor(Color(lane.accent), 0.34f),
                                    start = Offset(x, 0f),
                                    end = Offset(x, size.height),
                                    strokeWidth = strokeWidth,
                                )
                            }

                            /*
                             * Child side of parent -> child.
                             *
                             * For a final sibling the parent's lane terminates
                             * at the child header. When another direct sibling
                             * follows, the lane remains alive through this
                             * subtree.
                             */
                            visual.incomingLane?.let { lane ->
                                val x = commentParentConnectorPositionDp(
                                    lane.connectorDepth,
                                ).dp.toPx()

                                val lineEndY =
                                    if (visual.incomingContinuesToNextSibling) {
                                        size.height
                                    } else {
                                        childEntryY
                                    }

                                drawLine(
                                    color = railColor(Color(lane.accent), 0.62f),
                                    start = Offset(x, 0f),
                                    end = Offset(x, lineEndY),
                                    strokeWidth = strokeWidth,
                                )

                                val gutterEndX =
                                    commentParentConnectorGutterDp(
                                        visual.structuralDepth,
                                    ).dp.toPx()

                                drawLine(
                                    color = railColor(Color(lane.accent), 0.62f),
                                    start = Offset(x, childEntryY),
                                    end = Offset(gutterEndX, childEntryY),
                                    strokeWidth = strokeWidth,
                                )
                            }

                            /*
                             * Parent side of parent -> child.
                             *
                             * This is the visual change requested by ISSUE10:
                             * the edge attaches to the parent near its lower
                             * boundary, not across the username/header.
                             */
                            visual.outgoingLane?.let { lane ->
                                val x = commentParentConnectorPositionDp(
                                    lane.connectorDepth,
                                ).dp.toPx()

                                val childGutterEndX =
                                    commentParentConnectorGutterDp(
                                        visual.structuralDepth + 1,
                                    ).dp.toPx()

                                drawLine(
                                    color = railColor(Color(lane.accent), 0.62f),
                                    start = Offset(x, parentExitY),
                                    end = Offset(childGutterEndX, parentExitY),
                                    strokeWidth = strokeWidth,
                                )

                                drawLine(
                                    color = railColor(Color(lane.accent), 0.62f),
                                    start = Offset(x, parentExitY),
                                    end = Offset(x, size.height),
                                    strokeWidth = strokeWidth,
                                )
                            }
                        }
                    }
                }
            }
            .padding(
                start = when (state.commentHierarchyStyle) {
                    CommentHierarchyStyle.GUIDE_LINES ->
                        threadVisual?.let { visual ->
                            commentBranchRailGutterDp(
                                branchRailCount = visual.branchRailCount,
                                layoutRailCount = visual.layoutRailCount,
                            )
                        }?.dp ?: 0.dp
                    CommentHierarchyStyle.PARENT_CONNECTORS ->
                        parentConnectorVisual?.gutterDp?.dp ?: 0.dp
                },
            )
            .padding(start = 12.dp, top = 10.dp, end = 2.dp, bottom = 6.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    if (comment.removedByModerator) {
                        val edgeWidth = 3.dp.toPx()
                        drawRoundRect(
                            color = removedEdgeColor,
                            topLeft = Offset(-8.dp.toPx(), 0f),
                            size = Size(edgeWidth, size.height),
                            cornerRadius = CornerRadius(edgeWidth / 2f, edgeWidth / 2f),
                        )
                    }
                    if (focused) {
                        val radius = 10.dp.toPx()
                        drawRoundRect(
                            color = focusedPulseColor.copy(
                                alpha = FOCUSED_COMMENT_PERSISTENT_SHADE_ALPHA + focusedPulse.value,
                            ),
                            cornerRadius = CornerRadius(radius, radius),
                        )
                        drawRoundRect(
                            color = focusedPulseColor.copy(
                                alpha = FOCUSED_COMMENT_PERSISTENT_OUTLINE_ALPHA +
                                    focusedPulse.value.coerceAtMost(0.16f),
                            ),
                            cornerRadius = CornerRadius(radius, radius),
                            style = Stroke(width = 1.5.dp.toPx()),
                        )
                    }
                },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .then(
                        if (state.commentCollapseMode == CommentCollapseMode.HEADER) {
                            Modifier.clickable { onToggleComment(comment.id) }
                        } else {
                            Modifier
                        },
                    )
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // ISSUE22_COMMENT_AUTHOR_AVATARS_SETTING_V1
                CommentAuthorAvatar(
                    author = comment.author,
                    accent = accent,
                    avatarUri = commentRowAvatarUri(state, comment.author),
                    // LOWUI7_TOUCH_TARGETS_ARE_48DP_V1 (OCB-243): the avatar is an
                    // extra touch area for the author; screen readers get one
                    // target, the name beside it.
                    modifier = Modifier
                        .clearAndSetSemantics {}
                        .size(25.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onAuthorClick),
                )
                Spacer(Modifier.width(7.dp))
                Box(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable(onClick = onAuthorClick),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // The author is measured first and keeps its width; the
                        // flair takes what is left and ellipsizes. (A weighted
                        // author next to an unweighted flair got no width at all
                        // when the flair was long or the text large.)
                        Text(
                            text = comment.author,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        // ISSUE34_USER_TAGS_V1
                        state.userTagFor(comment.author)?.let { authorTag ->
                            Spacer(Modifier.width(5.dp))
                            UserTagChip(
                                tag = authorTag,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .hiddenWhenNarrowerThan(AuthorFlairMinVisibleWidth),
                            )
                        }
                        if (
                            comment.authorFlairText != null ||
                            comment.authorFlairRichText.isNotEmpty()
                        ) {
                            Spacer(Modifier.width(5.dp))
                            AuthorFlairBadge(
                                text = comment.authorFlairText.orEmpty(),
                                richText = comment.authorFlairRichText,
                                textColor =
                                    comment.authorFlairTextColor,
                                backgroundColor =
                                    comment.authorFlairBackgroundColor,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .hiddenWhenNarrowerThan(AuthorFlairMinVisibleWidth),
                            )
                        }
                    }
                }
            if (comment.isOp) {
                Spacer(Modifier.width(5.dp))
                CommentRoleBadge("OP")
            }
            if (comment.isModerator) {
                Spacer(Modifier.width(5.dp))
                CommentRoleBadge("MOD")
            }
            // ISSUE226_REMOVED_COMMENT_IS_MARKED_V1
            if (comment.removedByModerator) {
                Spacer(Modifier.width(5.dp))
                RemovedCommentBadge()
            }
            if (isNew) {
                Spacer(Modifier.width(5.dp))
                NewCommentBadge("NEW")
            }
            Spacer(Modifier.width(5.dp))
            Text(
                text = if (state.voteControlMode == VoteControlMode.COMPACT) {
                    "${compactNumber(displayedUpvotes)} · $displayedTime"
                } else {
                    displayedTime
                },
                maxLines = 1,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (comment.award != null) {
                Spacer(Modifier.width(5.dp))
                Text(comment.award, color = MaterialTheme.colorScheme.tertiary)
            }
            // A11Y01A11_COMMENT_THREAD_TARGET_V1
            IconButton(
                onClick = { onToggleComment(comment.id) },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    if (collapsed) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                    contentDescription = if (collapsed) "Expand thread" else "Collapse thread",
                    modifier = Modifier.size(19.dp),
                )
            }
        }
        if (
            commentViewportShowsReplyContext(comment.depth) &&
            !threadVisual?.parentAuthor.isNullOrBlank()
        ) {
            Text(
                text = "↳ replying to u/${threadVisual.parentAuthor}",
                modifier = Modifier.padding(top = 4.dp, start = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(!collapsed) {
            Column {
                if (deletedAuthorUnavailableVisible) {
                    ErrorNotice(
                        presentation = ErrorPresentation(
                            kind = ErrorPresentationKind.INLINE,
                            title = "Account unavailable",
                            message = "This Reddit account is no longer available.",
                        ),
                        modifier = Modifier.padding(top = 6.dp, end = 6.dp),
                        onAction = {},
                    )
                }
                if (comment.body.isNotBlank()) {
                    CommentContent(
                        source = comment.body,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (state.commentCollapseMode == CommentCollapseMode.COMMENT) {
                                    Modifier.clickable { onToggleComment(comment.id) }
                                } else {
                                    Modifier
                                },
                            )
                            .padding(top = 6.dp, end = 6.dp)
                            // ISSUE226_REMOVED_COMMENT_STANDS_OUT_V1: still readable, but quieter.
                            .alpha(if (comment.removedByModerator) 0.7f else 1f),
                        onMediaClick = { media ->
                            state.openCommentMedia(
                                commentId = comment.id,
                                media = media,
                            )
                        },
                        highlight = findHighlight,
                    )
                }
                comment.inlineMedia?.let { media ->
                    InlineCommentMedia(
                        media = media,
                        playing = state.activeAnimatedMediaId == comment.id,
                        modifier = Modifier
                            .padding(top = 9.dp)
                            .clip(MaterialTheme.shapes.medium)
                            // ISSUE219_INLINE_GIF_TOGGLE_IS_LABELLED_V1
                            .clickable(
                                onClickLabel = if (state.activeAnimatedMediaId == comment.id) "Pause" else "Play",
                                role = Role.Button,
                            ) {
                                state.setActiveAnimatedMedia(
                                    if (state.activeAnimatedMediaId == comment.id) null else comment.id,
                                )
                            },
                    )
                }
                comment.localMediaUri?.let { mediaUri ->
                    LocalUriMedia(
                        uri = mediaUri,
                        mimeType = comment.localMediaMimeType,
                        contentDescription = "Comment media",
                        modifier = Modifier
                            .padding(top = 9.dp, end = 6.dp)
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(MaterialTheme.shapes.medium),
                    )
                }
                comment.codeSample?.let { CodeBlock(it) }
                CommentActionRow(
                    placement = state.commentActionPlacement,
                    score = displayedUpvotes,
                    vote = vote,
                    voteColorPalette = state.voteColorPalette,
                    voteControlMode = state.voteControlMode,
                    onUpvote = { state.vote(comment, 1) },
                    onDownvote = { state.vote(comment, -1) },
                    onReply = { onReply(comment) },
                    onCopy = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText("Comment by ${comment.author}", comment.body.redditReadableText()),
                        )
                        notices.noticeCopied("Comment") // ISSUE222_NO_TOASTS_V1
                    },
                    onShare = { shareCommentLink(context, post, comment.id, notices) },
                    onCollapse = { onToggleComment(comment.id) },
                    showSaveAction = state.activeAccountIsAuthenticated,
                    saved = state.effectiveCommentSaved(comment),
                    saveEnabled = state.commentSaveControlEnabled(comment),
                    onSave = { state.toggleSaved(comment) },
                    canEdit = state.activeAccountIsAuthenticated && ownActions.canEdit,
                    onEdit = { onEdit(comment) },
                    canDelete = state.activeAccountIsAuthenticated && ownActions.canDelete,
                    onDelete = { onDelete(comment) },
                    showReportAction = state.activeAccountIsAuthenticated,
                    reportEnabled = state.contentReportControlEnabled(),
                    onReport = { reportDialogVisible = true },
                    voteActionsEnabled = state.commentVoteControlEnabled(comment),
                    replyEnabled = state.localMutationsAllowed ||
                        state.activeAccountIsAuthenticated,
                    moderatorReportTarget = moderatorReportTargetOrNull(
                        if (comment.id.startsWith("t1_")) comment.id else "t1_${comment.id}",
                        post.community,
                    ),
                )
                if (reportDialogVisible) {
                    RedditContentReportDialog(
                        targetLabel = "comment",
                        communityName = post.community,
                        targetKind = RedditReportTargetKind.COMMENT,
                        state = state,
                        onDismiss = { reportDialogVisible = false },
                        onReport = { reason ->
                            reportDialogVisible = false
                            state.report(comment, reason)
                        },
                    )
                }

            }
        }
        }
        if (collapsed) {
            val hiddenReplies = comment.descendantCount()
            Text(
                when {
                    newReplies == 1 -> "1 new reply · $hiddenReplies hidden"
                    newReplies > 1 -> "$newReplies new replies · $hiddenReplies hidden"
                    hiddenReplies == 0 -> "Comment collapsed"
                    hiddenReplies == 1 -> "Comment collapsed · 1 reply hidden"
                    else -> "Comment collapsed · $hiddenReplies replies hidden"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (newReplies > 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                // ISSUE219_COLLAPSED_COMMENT_ROW_IS_A_LABELLED_TARGET_V1: 48 dp tall
                // and says what it does.
                modifier = Modifier
                    .clickable(onClickLabel = "Expand comment", role = Role.Button) {
                        onToggleComment(comment.id)
                    }
                    .heightIn(min = 48.dp)
                    .wrapContentHeight(Alignment.CenterVertically)
                    .padding(vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun MoreRepliesRow(
    comment: Comment,
    loading: Boolean,
    failed: Boolean,
    fullDiscussionReady: Boolean,
    fullDiscussionLoading: Boolean,
    onRetry: (Comment) -> Unit,
) {
    if (
        !commentTransportMoreRowVisible(
            fullDiscussionReady = fullDiscussionReady,
            fullDiscussionLoading = fullDiscussionLoading,
            loading = loading,
            failed = failed,
        )
    ) {
        // UIPOST04_SEAMLESS_MORE_REPLIES_V1: an idle node stays invisible;
        // automatic loading fetches it, also when it is already on screen.
        Spacer(Modifier.fillMaxWidth().height(1.dp))
        return
    }
    val label = when {
        !fullDiscussionReady && fullDiscussionLoading -> "Loading full discussion…"
        !fullDiscussionReady -> "Full discussion unavailable"
        loading -> "Loading replies…"
        failed -> "Couldn't load replies · Retry"
        else -> "Loading replies…"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = (commentViewportInsetDp(comment.depth) + 18).dp,
                top = 9.dp,
                end = 8.dp,
                bottom = 7.dp,
            )
            .then(
                // LOWUI7_TOUCH_TARGETS_ARE_48DP_V1 (OCB-244): the Retry row is a
                // 48dp button.
                if (failed && fullDiscussionReady && !loading) {
                    Modifier
                        .heightIn(min = 48.dp)
                        .clickable(onClickLabel = "Retry", role = Role.Button) { onRetry(comment) }
                } else {
                    Modifier
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading || (!fullDiscussionReady && fullDiscussionLoading)) {
            InlineProgressIndicator()
            Spacer(Modifier.width(9.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = when {
                failed -> MaterialTheme.colorScheme.error
                loading -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun NewCommentBadge(label: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            // ISSUE218: content on primaryContainer is onPrimaryContainer.
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun RemovedCommentBadge() {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.error,
    ) {
        Text(
            "Removed",
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onError,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun CommentRoleBadge(label: String) {
    Surface(
        shape = CircleShape,
        color = if (label == "MOD") MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun Comment.descendantCount(): Int = children.sumOf { child ->
    if (child.isMoreRepliesNode) {
        child.moreCount
    } else {
        1 + child.descendantCount()
    }
}

private fun encodeCommentIdToken(ids: Set<String>): String = ids.sorted().joinToString("\n")

private fun decodeCommentIdToken(encoded: String): Set<String> = encoded
    .lineSequence()
    .map(String::trim)
    .filter(String::isNotEmpty)
    .toCollection(linkedSetOf())

@Composable
private fun CodeBlock(sample: CodeSample) {
    val context = LocalContext.current
    val notices = LocalViewportErrorHostState.current
    val codeFontFamily = LocalCodeFontFamily.current
    val scrollState = androidx.compose.foundation.rememberScrollState()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, end = 6.dp, bottom = 4.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.small,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    sample.language,
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = codeFontFamily),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("${sample.language} code", sample.source))
                        notices.noticeCopied("Code")
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Copy")
                }
            }
            SelectionContainer {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState)
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                ) {
                    Text(
                        sample.source,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = codeFontFamily),
                        softWrap = false,
                    )
                }
            }
        }
    }
}

@Composable
// COMMENTSAVE01A2_COMMENT_OVERFLOW_SAVE_ACTION_V1
private fun CommentActionRow(
    placement: CommentActionPlacement,
    score: Int,
    vote: Int,
    voteColorPalette: VoteColorPalette,
    voteControlMode: VoteControlMode,
    onUpvote: () -> Unit,
    onDownvote: () -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onCollapse: () -> Unit,
    showSaveAction: Boolean,
    saved: Boolean,
    saveEnabled: Boolean,
    onSave: () -> Unit,
    canEdit: Boolean,
    onEdit: () -> Unit,
    canDelete: Boolean,
    onDelete: () -> Unit,
    showReportAction: Boolean,
    reportEnabled: Boolean,
    onReport: () -> Unit,
    voteActionsEnabled: Boolean,
    replyEnabled: Boolean,
    moderatorReportTarget: ModeratorReportTarget? = null,
) {
    val actions: @Composable () -> Unit = {
        VoteControl(
            score = score,
            vote = vote,
            mode = voteControlMode,
            palette = voteColorPalette,
            enabled = voteActionsEnabled,
            onUpvote = onUpvote,
            onDownvote = onDownvote,
        )
        SmallCommentAction(
            icon = Icons.AutoMirrored.Rounded.Reply,
            label = "Reply",
            contentDescription = "Reply",
            enabled = replyEnabled,
            onClick = onReply,
        )
    }
    var moreMenuExpanded by remember { mutableStateOf(false) }
    val more: @Composable () -> Unit = {
        Box {
            // A11Y01A11_COMMENT_OPTIONS_TARGET_V1
            IconButton(
                onClick = { moreMenuExpanded = true },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    Icons.Rounded.MoreVert,
                    contentDescription = "Comment options",
                    modifier = Modifier.size(19.dp),
                )
            }
            DropdownMenu(
                expanded = moreMenuExpanded,
                onDismissRequest = { moreMenuExpanded = false },
            ) {
                if (canEdit) {
                    DropdownMenuItem(
                        text = { Text("Edit comment") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            moreMenuExpanded = false
                            onEdit()
                        },
                    )
                }
                if (canDelete) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Delete comment",
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            moreMenuExpanded = false
                            onDelete()
                        },
                    )
                }
                if (showSaveAction) {
                    // COMMENTSAVE01A3_COMMENT_SAVE_ICON_CONSISTENCY_V1
                    DropdownMenuItem(
                        text = {
                            Text(if (saved) "Unsave comment" else "Save comment")
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (saved) {
                                    Icons.Rounded.Bookmark
                                } else {
                                    Icons.Rounded.BookmarkBorder
                                },
                                contentDescription = null,
                                tint = if (saved) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        },
                        enabled = saveEnabled,
                        onClick = {
                            moreMenuExpanded = false
                            onSave()
                        },
                    )
                }
                // MODERATION02A2_COMMENT_REPORT_REOPEN_OUTSIDE_QUEUE_V1
                ModeratorReportMenuItem(
                    target = moderatorReportTarget,
                    onDismissMenu = { moreMenuExpanded = false },
                )
                // MODERATION02A3_COMMENT_CONTENT_CONTROLS_REOPEN_V1
                ModeratorContentControlsMenuItem(
                    target = moderatorReportTarget,
                    onDismissMenu = { moreMenuExpanded = false },
                )
                if (showReportAction) {
                    DropdownMenuItem(
                        text = { Text("Report comment") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Flag,
                                contentDescription = null,
                            )
                        },
                        enabled = reportEnabled,
                        onClick = {
                            moreMenuExpanded = false
                            onReport()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Share comment") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        moreMenuExpanded = false
                        onShare()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Copy text") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        moreMenuExpanded = false
                        onCopy()
                    },
                )
                // COMMENTSAVE01A31_COMMENT_OVERFLOW_ICON_SYSTEM_V1
                DropdownMenuItem(
                    text = { Text("Collapse thread") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.ExpandLess,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        moreMenuExpanded = false
                        onCollapse()
                    },
                )
            }
        }
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        when (placement) {
            CommentActionPlacement.LEFT -> {
                actions()
                more()
                Spacer(Modifier.weight(1f))
            }
            CommentActionPlacement.BALANCED -> {
                actions()
                Spacer(Modifier.weight(1f))
                more()
            }
            CommentActionPlacement.RIGHT -> {
                Spacer(Modifier.weight(1f))
                actions()
                more()
            }
        }
    }
}

@Composable
private fun SmallCommentAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Icon(
            icon,
            // LOWUI7_NO_DOUBLE_READOUTS_V1 (OCB-245): a visible label is enough.
            contentDescription = contentDescription.takeUnless { it == label },
            modifier = Modifier.size(17.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (label.isNotEmpty()) {
            Spacer(Modifier.width(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val COMMENT_NAV_LOG_TAG = "SubshellCommentNav"
private const val FOCUSED_COMMENT_OFFSET_TOLERANCE_DP = 24
private const val FOCUSED_COMMENT_OFFSET_MAX_PASSES = 4
private const val FOCUSED_COMMENT_LAYOUT_IDENTITY_MAX_PASSES = 4
private const val FOCUSED_COMMENT_PULSE_START_ALPHA = 0.12f
private const val FOCUSED_COMMENT_PERSISTENT_SHADE_ALPHA = 0.10f
private const val FOCUSED_COMMENT_PERSISTENT_OUTLINE_ALPHA = 0.58f
private const val FOCUSED_COMMENT_PULSE_DURATION_MS = 900
private const val FOCUSED_COMMENT_ACQUIRE_STEP_TIMEOUT_MS = 2_500L
private const val FOCUSED_COMMENT_MATERIALIZE_TIMEOUT_MS = 2_500L
private const val FOCUSED_COMMENT_CANONICAL_STABILIZE_TIMEOUT_MS = 4_000L
private const val FOCUSED_COMMENT_STATE_POLL_MS = 16L
private const val FOCUSED_COMMENT_MAX_ACQUIRE_STEPS = 8
private const val FOCUSED_COMMENT_IN_FLIGHT_WAIT_MS = 30_000L
private const val FOCUSED_COMMENT_OPENING_INDICATOR_DELAY_MS = 160L

/** A reply the user asked for while another unsent draft exists (comment null = the post). */
internal data class CommentComposerRequest(val comment: Comment?)

internal fun unsentCommentDraftConflictText(
    existing: CommentComposerDraft?,
    currentPostId: String,
    writtenWithAnotherAccount: Boolean,
): String {
    if (existing == null) return "You have an unsent reply."
    val where = when {
        existing.target.postId != currentPostId -> "on another post"
        existing.target.isPostTarget -> "to this post"
        else -> "to u/${existing.target.parentAuthor ?: "unknown"}"
    }
    val account = if (writtenWithAnotherAccount) ", written with another account" else ""
    return "You have an unsent reply $where$account. Discard it to start this one?"
}

// COMPOSER14_COMMENT_EDIT_KEPT_V1: the last copy of the comment being edited.
internal class EditingCommentMemory {
    private var last: Comment? = null

    fun keep(found: Comment?): Comment? {
        if (found != null) last = found
        return found ?: last
    }
}

// ISSUE96_CANCEL_WAITING_AUTHORIZATION_V1: the delete dialogs used to lock
// every way out while waiting for the browser (no timeout). Only deleting
// (and reading a post back after an unknown outcome) keeps them open.
internal fun postDeleteCloseLocked(phase: PostDeletePhase): Boolean =
    phase == PostDeletePhase.SENDING || phase == PostDeletePhase.CHECKING

internal fun commentDeleteCloseLocked(phase: CommentDeletePhase): Boolean =
    phase == CommentDeletePhase.SENDING

// ISSUE22_COMMENT_AUTHOR_AVATARS_SETTING_V1: the thread shows an author's
// avatar only while "Show comment avatars" is on; off, the letter stays.
internal fun commentRowAvatarUri(state: SubshellState, author: String): String? =
    if (state.showCommentAvatars) state.avatarUriForAuthor(author) else null
