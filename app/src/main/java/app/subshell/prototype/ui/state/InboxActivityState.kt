package app.subshell.prototype.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.subshell.prototype.data.account.RedditModeratorQueueItem
import app.subshell.prototype.data.account.canonicalModerationTarget
import app.subshell.prototype.inbox.canonicalRedditCommentFullname
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.model.Post
import app.subshell.prototype.ui.ComposerMediaAttachment
import app.subshell.prototype.ui.FeedPostOrigin
import app.subshell.prototype.ui.MainDestination
import app.subshell.prototype.ui.PendingInboxReadReceipt
import app.subshell.prototype.ui.SecondaryDestination
import app.subshell.prototype.ui.SubshellRoute
import app.subshell.prototype.ui.normalizedPostIdentity

/**
 * Inbox activity lifecycle (per-account dismissed ids and clear boundaries
 * with single-level undo), the pending read receipt handed from an inbox
 * item to the post it opens, local read ids and per-thread conversation
 * drafts, and the inbox and moderator queue route openers (item, detail
 * replacement, new-message handoff, queue item), which reach the route
 * stack only through the injected primitives. Extracted from
 * SubshellState, which holds one instance and delegates to it; behaviour
 * is unchanged.
 */
internal class InboxActivityState(
    private val loadInboxActivityDismissedIds: (String) -> Set<String>,
    private val persistInboxActivityDismissedIds: (String, Set<String>) -> Unit,
    private val loadInboxActivityClearBoundary: (String) -> Long,
    private val persistInboxActivityClearBoundary: (String, Long) -> Unit,
    private val loadInboxActivityPreviousClearBoundary: (String) -> Long,
    private val persistInboxActivityPreviousClearBoundary: (String, Long) -> Unit,
    private val currentTimeMillis: () -> Long,
    private val currentInboxItems: () -> List<InboxItem>,
    private val activeAccountIdProvider: () -> String,
    private val activeAccountIsAuthenticatedProvider: () -> Boolean,
    private val resolvePost: (String) -> Post?,
    private val currentRoute: () -> SubshellRoute,
    private val currentConversation: () -> InboxItem?,
    private val selectConversation: (InboxItem) -> Unit,
    private val openPostRoute: (Post, String?, FeedPostOrigin) -> Unit,
    private val replaceCurrentPostRoute: (Post, String?, FeedPostOrigin) -> Boolean,
    private val pushRoute: (SubshellRoute) -> Unit,
    private val replaceCurrentRoute: (SubshellRoute) -> Unit,
    private val showChrome: () -> Unit,
) {
    private val inboxItems: List<InboxItem>
        get() = currentInboxItems()
    private val activeAccountId: String
        get() = activeAccountIdProvider()
    private val activeAccountIsAuthenticated: Boolean
        get() = activeAccountIsAuthenticatedProvider()

    var inboxActivityLifecycleRevision by mutableIntStateOf(0)
        private set
    internal var pendingInboxReadReceipt by mutableStateOf<PendingInboxReadReceipt?>(null)
    // MESSAGES02C_ACCOUNT_THREAD_DRAFTS_V1
    private val messageConversationDrafts = mutableStateMapOf<String, String>()
    // RECREATE02_MESSAGE_ATTACHMENT_SURVIVES_RECREATION_V1 (#241): the image or
    // GIF attached to an unsent reply, under the draft's key.
    private val messageConversationMedia = mutableStateMapOf<String, ComposerMediaAttachment>()
    val readInboxIds = mutableStateListOf<String>()

    internal fun consumeInboxReadReceipt(
        postId: String,
        commentId: String,
    ): PendingInboxReadReceipt? {
        val receipt = pendingInboxReadReceipt ?: return null
        val normalizedPostId = postId.trim().removePrefix("t3_")
        val normalizedComment = canonicalRedditCommentFullname(commentId) ?: return null
        if (
            receipt.postId != normalizedPostId ||
            receipt.commentFullname != normalizedComment
        ) {
            return null
        }
        pendingInboxReadReceipt = null
        return receipt
    }

    fun messageConversationDraft(
        accountStableId: String,
        inboxItemId: String,
    ): String = messageConversationDrafts[
        messageConversationDraftKey(accountStableId, inboxItemId)
    ].orEmpty()

    fun updateMessageConversationDraft(
        accountStableId: String,
        inboxItemId: String,
        text: String,
    ) {
        val key = messageConversationDraftKey(accountStableId, inboxItemId)
        val bounded = text.take(10_000)
        if (bounded.isEmpty()) {
            messageConversationDrafts.remove(key)
        } else {
            messageConversationDrafts[key] = bounded
        }
    }

    fun messageConversationMedia(
        accountStableId: String,
        inboxItemId: String,
    ): ComposerMediaAttachment? =
        messageConversationMedia[messageConversationDraftKey(accountStableId, inboxItemId)]

    fun updateMessageConversationMedia(
        accountStableId: String,
        inboxItemId: String,
        media: ComposerMediaAttachment?,
    ) {
        val key = messageConversationDraftKey(accountStableId, inboxItemId)
        if (media == null) {
            messageConversationMedia.remove(key)
        } else {
            messageConversationMedia[key] = media
        }
    }

    internal fun messageConversationMediaForSnapshot(): Map<String, ComposerMediaAttachment> =
        messageConversationMedia.toMap()

    internal fun restoreMessageConversationMedia(media: Map<String, ComposerMediaAttachment>) {
        media.forEach { (key, attachment) ->
            if (key.isNotBlank()) messageConversationMedia[key] = attachment
        }
    }

    // RECREATE01_MESSAGE_DRAFTS_SURVIVE_RECREATION_V1 (#241): the drafts ride in
    // the process snapshot under their own keys, so a dark-mode switch or a
    // process death does not empty an unsent message.
    internal fun messageConversationDraftsForSnapshot(): Map<String, String> =
        messageConversationDrafts.toMap()

    internal fun restoreMessageConversationDrafts(drafts: Map<String, String>) {
        drafts.forEach { (key, text) ->
            val bounded = text.take(10_000)
            if (key.isNotBlank() && bounded.isNotEmpty()) messageConversationDrafts[key] = bounded
        }
    }

    private fun messageConversationDraftKey(
        accountStableId: String,
        inboxItemId: String,
    ): String {
        val account = accountStableId.trim()
        val itemId = inboxItemId.trim()
        require(account.isNotEmpty()) { "Message draft account cannot be blank" }
        require(itemId.isNotEmpty()) { "Message draft thread cannot be blank" }
        return "$account::$itemId"
    }

    // LOWUI5_DISMISSED_INBOX_IDS_CACHED_AND_CAPPED_V1 (OCB-220): the dismissed
    // ids are read from preferences once per account, not on every
    // recomposition; a multi-select dismiss/undo writes once; the set keeps
    // the newest MAX_DISMISSED_INBOX_ACTIVITY_IDS.
    private val dismissedIdsByAccount = mutableMapOf<String, Set<String>>()

    fun dismissedInboxActivityIds(accountStableId: String): Set<String> =
        dismissedIdsByAccount.getOrPut(accountStableId) { loadInboxActivityDismissedIds(accountStableId) }

    private fun storeDismissedIds(accountStableId: String, ids: Set<String>) {
        val capped = if (ids.size > MAX_DISMISSED_INBOX_ACTIVITY_IDS) {
            ids.toList().takeLast(MAX_DISMISSED_INBOX_ACTIVITY_IDS).toCollection(linkedSetOf())
        } else {
            ids
        }
        dismissedIdsByAccount[accountStableId] = capped
        persistInboxActivityDismissedIds(accountStableId, capped)
        inboxActivityLifecycleRevision += 1
    }

    fun dismissInboxActivityItems(accountStableId: String, itemIds: Set<String>) {
        val current = dismissedInboxActivityIds(accountStableId)
        val added = itemIds.map(String::trim).filter { it.isNotEmpty() && it !in current }
        if (added.isEmpty()) return
        storeDismissedIds(accountStableId, current.toCollection(linkedSetOf()).apply { addAll(added) })
    }

    fun restoreInboxActivityItems(accountStableId: String, itemIds: Set<String>) {
        val current = dismissedInboxActivityIds(accountStableId)
        val removed = itemIds.map(String::trim).filter { it in current }.toSet()
        if (removed.isEmpty()) return
        storeDismissedIds(accountStableId, current - removed)
    }

    fun inboxActivityClearBoundary(accountStableId: String): Long =
        loadInboxActivityClearBoundary(accountStableId)

    fun inboxActivityPreviousClearBoundary(accountStableId: String): Long =
        loadInboxActivityPreviousClearBoundary(accountStableId)

    fun dismissInboxActivityItem(accountStableId: String, itemId: String) =
        dismissInboxActivityItems(accountStableId, setOf(itemId))

    fun restoreInboxActivityItem(accountStableId: String, itemId: String) =
        restoreInboxActivityItems(accountStableId, setOf(itemId))

    fun clearInboxActivity(accountStableId: String) {
        val currentBoundary = loadInboxActivityClearBoundary(accountStableId)
        persistInboxActivityPreviousClearBoundary(accountStableId, currentBoundary)
        persistInboxActivityClearBoundary(
            accountStableId,
            (currentTimeMillis() / 1_000L).coerceAtLeast(currentBoundary + 1L),
        )
        inboxActivityLifecycleRevision += 1
    }

    fun undoInboxActivityClear(accountStableId: String) {
        val currentBoundary = loadInboxActivityClearBoundary(accountStableId)
        val previousBoundary = loadInboxActivityPreviousClearBoundary(accountStableId)
        if (currentBoundary == previousBoundary) return
        persistInboxActivityClearBoundary(accountStableId, previousBoundary)
        persistInboxActivityPreviousClearBoundary(accountStableId, previousBoundary)
        inboxActivityLifecycleRevision += 1
    }

    fun markInboxRead(id: String) {
        if (id !in readInboxIds) readInboxIds.add(id)
    }

    fun openInboxItem(
        item: InboxItem,
        resolvedPost: Post? = null,
        allowFallbackDetail: Boolean = false,
        verifiedReadAccountStableId: String? = null,
    ) {
        if (!activeAccountIsAuthenticated) markInboxRead(item.id)
        val post = resolvedPost ?: item.targetPostId?.let(resolvePost)
        if (post != null) {
            val commentFullname = canonicalRedditCommentFullname(item.targetCommentId)
            if (verifiedReadAccountStableId != null && commentFullname != null) {
                pendingInboxReadReceipt = PendingInboxReadReceipt(
                    accountStableId = verifiedReadAccountStableId,
                    inboxItemId = item.id,
                    postId = post.id.trim().removePrefix("t3_"),
                    commentFullname = commentFullname,
                )
            } else {
                pendingInboxReadReceipt = null
            }
            openPostRoute(post, commentFullname, FeedPostOrigin.Inbox)
        } else if (
            item.kind == ActivityKind.MESSAGE ||
            item.targetPostId == null ||
            allowFallbackDetail
        ) {
            pendingInboxReadReceipt = null
            selectConversation(item)
            pushRoute(
                SubshellRoute.Conversation(
                    destination = MainDestination.INBOX,
                    inboxItemId = item.id,
                ),
            )
        }
    }

    // A11Y01A4_REPLACE_CURRENT_INBOX_DETAIL_V1
    internal fun replaceCurrentInboxDetail(
        item: InboxItem,
        resolvedPost: Post? = null,
        allowFallbackDetail: Boolean = false,
    ): Boolean {
        val current = currentRoute()
        val eligibleCurrentDetail =
            current is SubshellRoute.Conversation ||
                (
                    current is SubshellRoute.Post &&
                        current.postRoute.origin == FeedPostOrigin.Inbox
                )
        if (!eligibleCurrentDetail) return false

        if (!activeAccountIsAuthenticated) markInboxRead(item.id)
        val post = resolvedPost ?: item.targetPostId?.let(resolvePost)
        if (post != null) {
            val commentFullname = canonicalRedditCommentFullname(item.targetCommentId)
            if (activeAccountIsAuthenticated && commentFullname != null) {
                pendingInboxReadReceipt = PendingInboxReadReceipt(
                    accountStableId = activeAccountId,
                    inboxItemId = item.id,
                    postId = post.id.trim().removePrefix("t3_"),
                    commentFullname = commentFullname,
                )
            } else {
                pendingInboxReadReceipt = null
            }
            return replaceCurrentPostRoute(post, commentFullname, FeedPostOrigin.Inbox)
        }

        if (
            item.kind == ActivityKind.MESSAGE ||
            item.targetPostId == null ||
            allowFallbackDetail
        ) {
            pendingInboxReadReceipt = null
            selectConversation(item)
            replaceCurrentRoute(
                SubshellRoute.Conversation(
                    destination = MainDestination.INBOX,
                    inboxItemId = item.id,
                ),
            )
            return true
        }
        return false
    }

    // SUBSHELL_MESSAGES02D5D_AUTHORITATIVE_POST_SEND_HANDOFF_V1
    internal fun replaceCurrentNewMessageWithConversation(
        item: InboxItem,
    ): Boolean {
        val current = currentRoute() as? SubshellRoute.Secondary ?: return false
        if (current.secondaryDestination != SecondaryDestination.NEW_MESSAGE) return false
        if (item.kind != ActivityKind.MESSAGE) return false

        selectConversation(item)
        replaceCurrentRoute(
            SubshellRoute.Conversation(
                destination = MainDestination.INBOX,
                inboxItemId = item.id,
            ),
        )
        showChrome()
        return currentRoute() is SubshellRoute.Conversation &&
            currentConversation()?.id == item.id
    }

    // MOD01A22_EXACT_MODERATION_QUEUE_NAVIGATION_V1
    fun openModeratorQueueItem(
        item: RedditModeratorQueueItem,
        resolvedPost: Post,
    ) {
        val target = item.canonicalModerationTarget() ?: return
        if (normalizedPostIdentity(resolvedPost.id) != target.postId) return
        pendingInboxReadReceipt = null
        val commentId = target.commentFullname
        openPostRoute(resolvedPost, commentId, FeedPostOrigin.Inbox)
    }

    internal fun replaceCurrentModeratorQueueDetail(
        item: RedditModeratorQueueItem,
        resolvedPost: Post,
    ): Boolean {
        val current = currentRoute()
        val eligibleCurrentDetail =
            current is SubshellRoute.Conversation ||
                (
                    current is SubshellRoute.Post &&
                        current.postRoute.origin == FeedPostOrigin.Inbox
                    )
        if (!eligibleCurrentDetail) return false
        val target = item.canonicalModerationTarget() ?: return false
        if (normalizedPostIdentity(resolvedPost.id) != target.postId) return false
        pendingInboxReadReceipt = null
        val commentId = target.commentFullname
        return replaceCurrentPostRoute(resolvedPost, commentId, FeedPostOrigin.Inbox)
    }
}

internal const val MAX_DISMISSED_INBOX_ACTIVITY_IDS = 1_000
