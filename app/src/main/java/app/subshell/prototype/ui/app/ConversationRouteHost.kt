package app.subshell.prototype.ui.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.subshell.prototype.data.account.DirectChatLiveUiState
import app.subshell.prototype.data.account.MessageReplyChannel
import app.subshell.prototype.data.account.MessageReplyMediaAttachment
import app.subshell.prototype.data.account.MessageReplyRequest
import app.subshell.prototype.data.account.MessageReplyUiState
import app.subshell.prototype.data.account.RedditInboxUiState
import app.subshell.prototype.data.account.RedditModerationUiState
import app.subshell.prototype.data.account.RedditModeratorActionRequest
import app.subshell.prototype.data.account.RedditModeratorActionUiState
import app.subshell.prototype.data.account.RedditModmailListMode
import app.subshell.prototype.data.account.RedditModmailTriageAction
import app.subshell.prototype.data.account.RedditModmailUiState
import app.subshell.prototype.data.account.RedditRemovalReasonUiState
import app.subshell.prototype.inbox.ModmailNotificationTarget
import app.subshell.prototype.model.Post
import app.subshell.prototype.ui.ExpandedContextUtilityMaxWidth
import app.subshell.prototype.ui.SubshellRoute
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.app.ExpandedContextSurface
import app.subshell.prototype.ui.app.ExpandedListDetailSurface
import app.subshell.prototype.ui.composerActionsAtStart
import app.subshell.prototype.ui.screens.ConversationDetailKind
import app.subshell.prototype.ui.screens.ConversationRouteStep
import app.subshell.prototype.ui.screens.ConversationScreen
import app.subshell.prototype.ui.screens.InboxModSection
import app.subshell.prototype.ui.screens.InboxPrimaryTab
import app.subshell.prototype.ui.screens.InboxScreen
import app.subshell.prototype.ui.screens.conversationDetailKind
import app.subshell.prototype.ui.screens.conversationParticipantUsername
import app.subshell.prototype.ui.screens.conversationRouteStep
import app.subshell.prototype.ui.screens.resolveConversationReplyParentFullname
import app.subshell.prototype.ui.screens.resolveConversationRouteItem
import app.subshell.prototype.ui.useExpandedInboxListDetail

/**
 * The Conversation route branch of SubshellApp's route dispatch: the
 * conversation screen with its private-message / direct-chat plumbing, as
 * the detail pane of the Inbox two-pane workspace in an expanded window or
 * on a compact context surface otherwise. Moved verbatim; the SubshellApp
 * vars it writes arrive as MutableState.
 */
@Composable
internal fun ConversationRouteHost(
    redditModeration: ModerationQueueEntryPoints,
    redditModmail: ModmailEntryPoints,
    commentMutations: CommentMutationEntryPoints,
    redditInbox: InboxEntryPoints,
    redditMessaging: MessagingEntryPoints,
    state: SubshellState,
    route: SubshellRoute.Conversation,
    padding: PaddingValues,
    expandedWindowLayout: Boolean,
    inboxPrimaryTabState: MutableState<InboxPrimaryTab>,
    inboxModSectionState: MutableState<InboxModSection>,
) {
    var inboxPrimaryTab by inboxPrimaryTabState
    var inboxModSection by inboxModSectionState
    val redditModmailState = redditModmail.redditModmailState
    val onLoadRedditModmail = redditModmail.onLoadRedditModmail
    val onAppendRedditModmail = redditModmail.onAppendRedditModmail
    val onSelectRedditModmailListMode = redditModmail.onSelectRedditModmailListMode
    val onOpenRedditModmailConversation = redditModmail.onOpenRedditModmailConversation
    val onSendRedditModmailMessage = redditModmail.onSendRedditModmailMessage
    val onSetRedditModmailArchived = redditModmail.onSetRedditModmailArchived
    val onSetRedditModmailUnread = redditModmail.onSetRedditModmailUnread
    val onVerifyRedditModmailReadState = redditModmail.onVerifyRedditModmailReadState
    val onApplyRedditModmailTriage = redditModmail.onApplyRedditModmailTriage
    val onCloseRedditModmailConversation = redditModmail.onCloseRedditModmailConversation
    val onAuthorizeRedditModmail = redditModmail.onAuthorizeRedditModmail
    val modmailNotificationTarget = redditModmail.modmailNotificationTarget
    val onModmailNotificationConsumed = redditModmail.onModmailNotificationConsumed
    val redditModerationState = redditModeration.redditModerationState
    val onLoadRedditModQueue = redditModeration.onLoadRedditModQueue
    val onAppendRedditModQueue = redditModeration.onAppendRedditModQueue
    val onResolveRedditModerationTarget = redditModeration.onResolveRedditModerationTarget
    val moderationActionState = redditModeration.moderationActionState
    val onModerateRedditQueueItem = redditModeration.onModerateRedditQueueItem
    val onRetryRedditModerationAction = redditModeration.onRetryRedditModerationAction
    val onAuthorizeRedditModerationAction = redditModeration.onAuthorizeRedditModerationAction
    val removalReasonState = redditModeration.removalReasonState
    val onLoadRedditRemovalReasons = redditModeration.onLoadRedditRemovalReasons
    val onAuthorizeRedditRemovalReasons = redditModeration.onAuthorizeRedditRemovalReasons
    val redditInboxState = redditInbox.redditInboxState
    val onLoadRedditInbox = redditInbox.onLoadRedditInbox
    val onAppendRedditInbox = redditInbox.onAppendRedditInbox
    val onMarkRedditInboxRead = redditInbox.onMarkRedditInboxRead
    val onResolveRedditInboxTarget = redditInbox.onResolveRedditInboxTarget
    val onAuthorizeRedditInbox = redditInbox.onAuthorizeRedditInbox
    val onCancelRedditInboxAuthorization = redditInbox.onCancelRedditInboxAuthorization
    val messageReplyState = redditMessaging.messageReplyState
    val directChatLiveState = redditMessaging.directChatLiveState
    val onSendRedditMessageReply = redditMessaging.onSendRedditMessageReply
    val onResetRedditMessageReplyState = redditMessaging.onResetRedditMessageReplyState
    val onStartRedditDirectChatLive = redditMessaging.onStartRedditDirectChatLive
    val onStopRedditDirectChatLive = redditMessaging.onStopRedditDirectChatLive
    val onUpdateRedditDirectChatTyping = redditMessaging.onUpdateRedditDirectChatTyping
    val onDeleteRedditDirectChatMessage = redditMessaging.onDeleteRedditDirectChatMessage
    val onResetRedditDirectChatDeleteFailure = redditMessaging.onResetRedditDirectChatDeleteFailure
    val onReactRedditDirectChatMessage = redditMessaging.onReactRedditDirectChatMessage
    val onResetRedditDirectChatReactionFailure = redditMessaging.onResetRedditDirectChatReactionFailure
    val onAuthorizeCommentWrite = commentMutations.onAuthorizeCommentWrite
    val liveConversationMatches =
        state.activeAccountIsAuthenticated &&
            redditInboxState.matches(state.activeAccountId)
    // ISSUE50_CONVERSATION_BACK_WITHOUT_SELECTION_V1: after Back from a link
    // opened in a live conversation the selection is gone (only the local
    // cache could restore it, and it never holds live Reddit items). The live
    // inbox list is asked first; with nothing to show the route is left, as
    // the media route does, instead of crashing on requireNotNull.
    val conversationItem = resolveConversationRouteItem(
        selected = state.selectedConversation,
        inboxItemId = route.inboxItemId,
        liveItems = if (liveConversationMatches) {
            redditInboxState.items
        } else {
            emptyList()
        },
    )
    val extraInboxPagesState = remember(route.inboxItemId) { mutableIntStateOf(0) }
    when (
        conversationRouteStep(
            itemFound = conversationItem != null,
            authenticated = state.activeAccountIsAuthenticated,
            accountStableId = state.activeAccountId,
            inbox = redditInboxState,
            extraPagesLoaded = extraInboxPagesState.intValue,
        )
    ) {
        ConversationRouteStep.SHOW -> Unit
        ConversationRouteStep.WAIT -> {
            ConversationRouteWaiting(padding)
            return
        }
        ConversationRouteStep.LOAD_MORE -> {
            LaunchedEffect(route.inboxItemId, redditInboxState.nextCursor) {
                extraInboxPagesState.intValue += 1
                onAppendRedditInbox(state.activeAccountId)
            }
            ConversationRouteWaiting(padding)
            return
        }
        ConversationRouteStep.LEAVE -> {
            LaunchedEffect(route.inboxItemId) { state.closeConversation() }
            return
        }
    }
    if (conversationItem == null) return
    val conversationIdentityUsername =
        conversationParticipantUsername(conversationItem.sender)
    LaunchedEffect(conversationIdentityUsername) {
        if (conversationIdentityUsername != null) {
            state.ensureUserIdentity(conversationIdentityUsername)
        }
    }
    val conversationParticipantAvatarUri =
        conversationIdentityUsername
            ?.let { username -> state.userFor(username).avatarImageUri }
            ?.trim()
            ?.takeIf(String::isNotEmpty)

    // SUBSHELL_INBOXM3A2R1_OWN_AVATAR_PLUMBING_V1
    val conversationOwnUsername = state.activeAccountUsername
        .trim()
        .takeIf(String::isNotEmpty)
    LaunchedEffect(conversationOwnUsername) {
        if (conversationOwnUsername != null) {
            state.ensureUserIdentity(conversationOwnUsername)
        }
    }
    val conversationOwnAvatarUri = conversationOwnUsername
        ?.let { username -> state.userFor(username).avatarImageUri }
        ?.trim()
        ?.takeIf(String::isNotEmpty)

    val conversationDraft = state.messageConversationDraft(
        accountStableId = state.activeAccountId,
        inboxItemId = conversationItem.id,
    )
    val conversationReplyParentFullname =
        resolveConversationReplyParentFullname(conversationItem)
    val conversationKind = conversationDetailKind(conversationItem)
    val directChatParticipantUsername = if (
        conversationKind == ConversationDetailKind.DIRECT_CHAT
    ) {
        conversationItem.conversation
            .firstOrNull { message -> !message.outgoing }
            ?.sender
            ?.let(::conversationParticipantUsername)
            ?: conversationParticipantUsername(conversationItem.sender)
    } else {
        null
    }
    LaunchedEffect(
        liveConversationMatches,
        conversationItem.id,
        conversationItem.unread,
        redditInboxState.contentRevision,
    ) {
        if (
            liveConversationMatches &&
            conversationItem.unread &&
            conversationItem.id !in redditInboxState.locallySeenIds &&
            conversationItem.id !in redditInboxState.readMutationItemIds
        ) {
            onMarkRedditInboxRead(
                state.activeAccountId,
                conversationItem.id,
            )
        }
    }
    val conversationDetail: @Composable (Modifier) -> Unit =
        { conversationModifier ->
            ConversationScreen(
                modifier = conversationModifier,
                item = conversationItem,
                // CONVPROFILE01_TAP_PARTICIPANT_OPENS_PROFILE_V1: the same path a u/ link takes.
                onOpenUser = { username -> state.openUser(username) },
                onBack = {
                    // SUBSHELL_INBOXM3A2R2B2_EXPLICIT_DIRECT_CHAT_ROUTE_EXIT_STOP_V1
                    if (
                        conversationKind ==
                            ConversationDetailKind.DIRECT_CHAT
                    ) {
                        onStopRedditDirectChatLive(
                            state.activeAccountId,
                            conversationItem.id,
                        )
                    }
                    state.closeConversation()
                },
                writeEnabled = state.activeAccountIsAuthenticated,
                isRefreshing =
                    liveConversationMatches && redditInboxState.loading,
                onRefresh = if (liveConversationMatches) {
                    { onLoadRedditInbox(state.activeAccountId, true) }
                } else {
                    null
                },
                participantAvatarUri = conversationParticipantAvatarUri,
                ownAvatarUri = conversationOwnAvatarUri,
                ownUsername = conversationOwnUsername,
                accountStableId = state.activeAccountId,
                replyText = conversationDraft,
                onReplyTextChange = { text ->
                    val actualEdit = text != conversationDraft
                    state.updateMessageConversationDraft(
                        accountStableId = state.activeAccountId,
                        inboxItemId = conversationItem.id,
                        text = text,
                    )
                    if (
                        actualEdit &&
                        conversationKind == ConversationDetailKind.DIRECT_CHAT
                    ) {
                        directChatParticipantUsername?.let { participant ->
                            onUpdateRedditDirectChatTyping(
                                state.activeAccountId,
                                conversationItem.id,
                                participant,
                                text.isNotEmpty(),
                            )
                        }
                    }
                },
                replyParentFullname = conversationReplyParentFullname,
                messageReplyState = messageReplyState,
                directChatLiveState = directChatLiveState,
                directChatParticipantUsername = directChatParticipantUsername,
                onStartDirectChatLive = onStartRedditDirectChatLive,
                onStopDirectChatLive = onStopRedditDirectChatLive,
                onReactDirectChatMessage = { eventId, key ->
                    directChatParticipantUsername?.let { participant ->
                        onReactRedditDirectChatMessage(
                            state.activeAccountId,
                            conversationItem.id,
                            participant,
                            eventId,
                            key,
                        )
                    }
                },
                onResetDirectChatReactionFailure = {
                    onResetRedditDirectChatReactionFailure(
                        state.activeAccountId,
                        conversationItem.id,
                    )
                },
                onDeleteDirectChatMessage = { eventId ->
                    directChatParticipantUsername?.let { participant ->
                        onDeleteRedditDirectChatMessage(
                            state.activeAccountId,
                            conversationItem.id,
                            participant,
                            eventId,
                        )
                    }
                },
                onResetDirectChatDeleteFailure = {
                    onResetRedditDirectChatDeleteFailure(
                        state.activeAccountId,
                        conversationItem.id,
                    )
                },
                onSendPrivateMessageReply = send@{ text, media ->
                    if (conversationKind == ConversationDetailKind.DIRECT_CHAT) {
                        directChatParticipantUsername?.let { participant ->
                            onUpdateRedditDirectChatTyping(
                                state.activeAccountId,
                                conversationItem.id,
                                participant,
                                false,
                            )
                        }
                    }
                    val parent = conversationReplyParentFullname ?: return@send
                    onSendRedditMessageReply(
                        MessageReplyRequest(
                            accountStableId = state.activeAccountId,
                            inboxItemId = conversationItem.id,
                            parentFullname = parent,
                            channel = when (conversationKind) {
                                ConversationDetailKind.DIRECT_CHAT ->
                                    MessageReplyChannel.DIRECT_CHAT
                                ConversationDetailKind.PRIVATE_MESSAGE_THREAD ->
                                    MessageReplyChannel.PRIVATE_MESSAGE_THREAD
                            },
                            text = text,
                            media = media?.let { attachment ->
                                MessageReplyMediaAttachment(
                                    uri = attachment.uri,
                                    mimeType = attachment.mimeType,
                                    sourceLinkUri = attachment.sourceLinkUri,
                                )
                            },
                            directChatParticipantUsername =
                                directChatParticipantUsername,
                        ),
                    )
                },
                onReconnectInbox = {
                    onAuthorizeRedditInbox(state.activeAccountId)
                },
                onAuthorizeMediaReply = onAuthorizeCommentWrite,
                interactionActionsAtStart =
                    state.interactionSide.composerActionsAtStart(),
                onResetMessageReplyState =
                    onResetRedditMessageReplyState,
                onRefreshInboxAfterSuccessfulDirectChatExit = {
                    onLoadRedditInbox(state.activeAccountId, true)
                },
            )
        }

    if (
        useExpandedInboxListDetail(
            expandedWindow = expandedWindowLayout,
            supportedOrigin = true,
            tabletLayoutMode = state.tabletLayoutMode,
        )
    ) {
        // A11Y01A4_INBOX_CONVERSATION_TWO_PANE_V1
        ExpandedListDetailSurface(
            scaffoldPadding = padding,
            listTitle = "Inbox",
            listSubtitle = "Activity, messages & moderation",
            listContent = { listModifier ->
                InboxScreen(
                    modmailThreadFillsTheScreen = false,
                    modifier = listModifier,
                    state = state,
                    selectedPrimaryTab = inboxPrimaryTab,
                            controlledModSection = inboxModSection,
                    redditInboxState = redditInboxState,
                    onLoadRedditInbox = onLoadRedditInbox,
                    onAppendRedditInbox = onAppendRedditInbox,
                    onMarkRedditInboxRead = onMarkRedditInboxRead,
                    onResolveRedditInboxTarget =
                        onResolveRedditInboxTarget,
                    onAuthorizeRedditInbox =
                        onAuthorizeRedditInbox,
                    onCancelRedditInboxAuthorization = onCancelRedditInboxAuthorization,
                    redditModerationState = redditModerationState,
                    onLoadRedditModQueue = onLoadRedditModQueue,
                    onAppendRedditModQueue = onAppendRedditModQueue,
                    onResolveRedditModerationTarget =
                        onResolveRedditModerationTarget,
                    moderationActionState = moderationActionState,
                    onModerateRedditQueueItem = onModerateRedditQueueItem,
                    onRetryRedditModerationAction = onRetryRedditModerationAction,
                    onAuthorizeRedditModerationAction = onAuthorizeRedditModerationAction,
                    removalReasonState = removalReasonState,
                    onLoadRedditRemovalReasons = onLoadRedditRemovalReasons,
                    onAuthorizeRedditRemovalReasons = onAuthorizeRedditRemovalReasons,
                    redditModmailState = redditModmailState,
                    onLoadRedditModmail = onLoadRedditModmail,
                    onAppendRedditModmail = onAppendRedditModmail,
                    onSelectRedditModmailListMode = onSelectRedditModmailListMode,
                    onOpenRedditModmailConversation = onOpenRedditModmailConversation,
                    onSendRedditModmailMessage = onSendRedditModmailMessage,
                    onSetRedditModmailArchived = onSetRedditModmailArchived,
                    onSetRedditModmailUnread = onSetRedditModmailUnread,
                    onVerifyRedditModmailReadState =
                        onVerifyRedditModmailReadState,
                    onApplyRedditModmailTriage = onApplyRedditModmailTriage,
                    onCloseRedditModmailConversation = onCloseRedditModmailConversation,
                    onAuthorizeRedditModmail = onAuthorizeRedditModmail,
                    modmailNotificationTarget = modmailNotificationTarget,
                    onModmailNotificationConsumed = onModmailNotificationConsumed,
                    onPrimaryTabChanged = { inboxPrimaryTab = it },
                            onModSectionChanged = { inboxModSection = it },
                    onOpenItemSelection = {
                            item,
                            resolvedPost,
                            allowFallbackDetail,
                        ->
                        state.replaceCurrentInboxDetail(
                            item = item,
                            resolvedPost = resolvedPost,
                            allowFallbackDetail =
                                allowFallbackDetail,
                        )
                    },
                    onOpenModerationItemSelection = { item, resolvedPost ->
                        state.replaceCurrentModeratorQueueDetail(
                            item = item,
                            resolvedPost = resolvedPost,
                        )
                    },
                    contentPadding = PaddingValues(
                        horizontal = 8.dp,
                        vertical = 8.dp,
                    ),
                )
            },
            detailContent = conversationDetail,
        )
    } else {
        ExpandedContextSurface(
            scaffoldPadding = padding,
            expanded = false,
            maxWidth = ExpandedContextUtilityMaxWidth,
        ) { contextModifier ->
            conversationDetail(contextModifier)
        }
    }
}

// ISSUE48_CONVERSATION_WAITS_FOR_THE_LIVE_INBOX_V1
@Composable
private fun ConversationRouteWaiting(padding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .semantics { contentDescription = "Loading conversation" },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}
