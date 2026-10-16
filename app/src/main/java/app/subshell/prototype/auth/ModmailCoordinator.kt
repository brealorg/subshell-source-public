package app.subshell.prototype.auth

import app.subshell.prototype.data.account.RedditModmailUiFailureKind
import app.subshell.prototype.data.account.RedditModmailListMode
import app.subshell.prototype.data.account.RedditModmailTriageAction
import app.subshell.prototype.data.account.RedditModmailUiState
import app.subshell.prototype.data.account.normalizeRedditModmailId
import app.subshell.prototype.data.auth.AuthBrowserLaunchResult
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityFailureKind
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModmailFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditModmailLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditModmailResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModmailThreadResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditModmailArchiveRequest
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditModmailArchiveResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditModmailSendRequest
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditModmailSendResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditModmailTriageRequest
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditModmailTriageResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditModmailReadStateRequest
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditModmailReadStateResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditModmailWriter
import app.subshell.prototype.data.auth.OAuthClientMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.cancellation.CancellationException
import app.subshell.prototype.data.auth.AUTH_CALLBACK_CANCELLED_TOKEN

// MODMAIL01A1_ACCOUNT_PINNED_READ_COORDINATOR_V1
// MODMAIL02_REPLY_INTERNAL_NOTE_COORDINATOR_V1
// MODMAIL04A1_ARCHIVE_UNARCHIVE_COORDINATOR_V1
// MODMAIL05_TRIAGE_COORDINATOR_V1
// MODMAIL06_READ_UNREAD_COORDINATOR_V1
internal class ModmailCoordinator(
    private val capability: AuthCommentWriteCapability?,
    private val loader: AuthenticatedRedditModmailLoader?,
    private val writer: AuthenticatedRedditModmailWriter?,
    private val clientMode: OAuthClientMode?,
    private val activeAccountStableId: () -> String?,
) {
    private val mutableUiState = MutableStateFlow(RedditModmailUiState())
    private var pendingAccountStableId: String? = null

    // MODMAIL07_PER_CONVERSATION_SEND_STATE_V1 (#126-#128): on two-pane the list
    // stays tappable, so a send or mark-read can finish after the reader opened
    // another conversation. The unknown send outcome belongs to its own
    // conversation (shown only there, cleared when that thread loads again,
    // which is the refresh the composer asks for), and every finished request
    // clears its in-progress flag whichever conversation is selected.
    private var unknownSendConversationId: String? = null

    val uiState: StateFlow<RedditModmailUiState> = mutableUiState.asStateFlow()

    // LOW8_MODMAIL_RESULTS_STAY_WITH_THEIR_CONVERSATION_V1 (OCB-593): a result
    // that comes back after the account or the open conversation changed is
    // not written into what is shown now. Another conversation of the same
    // account only gets [elsewhere] (clearing an in-progress flag).
    private fun applyToConversation(
        account: String,
        conversationId: String,
        elsewhere: (RedditModmailUiState) -> RedditModmailUiState = { it },
        change: (RedditModmailUiState) -> RedditModmailUiState,
    ) {
        val latest = mutableUiState.value
        if (!latest.matches(account)) return
        mutableUiState.value = if (latest.selectedConversationId == conversationId) {
            change(latest)
        } else {
            elsewhere(latest)
        }
    }

    suspend fun load(
        accountStableId: String,
        forceRefresh: Boolean = false,
        keepOpenConversation: Boolean = false,
    ) {
        val account = accountStableId.trim()
        if (account.isEmpty()) return
        if (activeAccountStableId()?.trim() != account) {
            mutableUiState.value = failure(
                account = account,
                kind = RedditModmailUiFailureKind.ACCOUNT_CHANGED,
            )
            return
        }
        val configuredLoader = loader
        if (configuredLoader == null) {
            mutableUiState.value = failure(
                account = account,
                kind = RedditModmailUiFailureKind.CONFIGURATION,
            )
            return
        }
        val current = mutableUiState.value
        if (current.matches(account) && current.loading) return
        if (
            !forceRefresh &&
            current.matches(account) &&
            current.loaded &&
            !current.authorizationRequired &&
            current.failureKind == null
        ) return

        pendingAccountStableId = account
        val retained = current.takeIf { state -> state.matches(account) }
        val listMode = retained?.listMode ?: RedditModmailListMode.ACTIVE
        mutableUiState.value = RedditModmailUiState(
            accountStableId = account,
            mailCommunities = retained?.mailCommunities.orEmpty(),
            conversations = retained?.conversations.orEmpty(),
            nextCursor = retained?.nextCursor,
            loading = true,
            loaded = retained?.loaded == true,
            listMode = listMode,
            unreadCounts = retained?.unreadCounts ?: app.subshell.prototype.data.account.RedditModmailUnreadCounts(),
            contentRevision = retained?.contentRevision ?: 0L,
            selectedConversationId = retained?.selectedConversationId.takeIf { keepOpenConversation },
            thread = retained?.thread.takeIf { keepOpenConversation },
        )
        applyListResult(
            account = account,
            result = configuredLoader.loadByMode(account, after = null, listMode = listMode),
            append = false,
        )
    }

    suspend fun append(accountStableId: String) {
        val account = accountStableId.trim()
        val current = mutableUiState.value
        val cursor = current.nextCursor ?: return
        if (
            account.isEmpty() ||
            !current.matches(account) ||
            current.inProgress ||
            current.authorizationRequired
        ) return
        val configuredLoader = loader ?: return
        mutableUiState.value = current.copy(
            appending = true,
            failureKind = null,
            failureRetryable = false,
            failureHttpStatusCode = null,
            failureDetails = null,
        )
        applyListResult(
            account = account,
            result = configuredLoader.loadByMode(account, after = cursor, listMode = current.listMode),
            append = true,
        )
    }

    suspend fun selectListMode(
        accountStableId: String,
        listMode: RedditModmailListMode,
    ) {
        val account = accountStableId.trim()
        val current = mutableUiState.value
        if (account.isEmpty() || activeAccountStableId()?.trim() != account || !current.matches(account) || current.inProgress) return
        if (current.listMode == listMode && current.loaded) return
        mutableUiState.value = current.copy(
            listMode = listMode,
            conversations = emptyList(),
            nextCursor = null,
            loaded = false,
            failureKind = null,
            failureRetryable = false,
            failureHttpStatusCode = null,
            failureDetails = null,
        )
        load(account, forceRefresh = true)
    }

    suspend fun openConversation(
        accountStableId: String,
        rawConversationId: String,
    ) {
        val account = accountStableId.trim()
        val conversationId = runCatching {
            normalizeRedditModmailId(rawConversationId)
        }.getOrNull() ?: return
        if (activeAccountStableId()?.trim() != account) return
        val configuredLoader = loader ?: return
        val current = mutableUiState.value
        if (!current.matches(account)) return
        mutableUiState.value = current.copy(
            selectedConversationId = conversationId,
            thread = current.thread?.takeIf { thread ->
                thread.conversation.id == conversationId
            },
            threadLoading = true,
            conversationActionOutcomeUnknown = false,
            readStateOutcomeUnknown = false,
            sendOutcomeUnknown = conversationId == unknownSendConversationId,
            failureKind = null,
            failureRetryable = false,
            failureHttpStatusCode = null,
            failureDetails = null,
        )
        when (val result = configuredLoader.loadThread(account, conversationId)) {
            is AuthenticatedRedditModmailThreadResult.Loaded -> {
                val latest = mutableUiState.value
                if (!latest.matches(account) || latest.selectedConversationId != conversationId) {
                    return
                }
                if (conversationId == unknownSendConversationId) unknownSendConversationId = null
                mutableUiState.value = latest.copy(
                    sendOutcomeUnknown = false,
                    thread = result.thread,
                    conversations = latest.conversations.map { conversation ->
                        if (conversation.id == conversationId) {
                            result.thread.conversation
                        } else {
                            conversation
                        }
                    },
                    threadLoading = false,
                    authorizationRequired = false,
                    failureKind = null,
                    failureRetryable = false,
                    failureHttpStatusCode = null,
                    failureDetails = null,
                    contentRevision = latest.contentRevision + 1L,
                )
                if (result.thread.conversation.unread) {
                    mutateReadState(
                        account = account,
                        conversationId = conversationId,
                        unread = false,
                    )
                }
            }
            AuthenticatedRedditModmailThreadResult.AuthorizationRequired ->
                applyToConversation(account, conversationId) { latest ->
                    latest.copy(
                        threadLoading = false,
                        authorizationRequired = true,
                        failureKind = null,
                    )
                }
            is AuthenticatedRedditModmailThreadResult.Failed ->
                applyToConversation(account, conversationId) { latest ->
                    latest.copy(
                        threadLoading = false,
                        failureKind = result.kind.toUiFailureKind(),
                        failureRetryable = result.retryable,
                        failureHttpStatusCode = result.httpStatusCode,
                        failureDetails = result.details,
                    )
                }
        }
    }

    suspend fun setUnread(
        accountStableId: String,
        rawConversationId: String,
        unread: Boolean,
    ) {
        val account = accountStableId.trim()
        val conversationId = runCatching {
            normalizeRedditModmailId(rawConversationId)
        }.getOrNull() ?: return
        mutateReadState(
            account = account,
            conversationId = conversationId,
            unread = unread,
        )
    }

    suspend fun verifyReadState(
        accountStableId: String,
        rawConversationId: String,
    ) {
        val account = accountStableId.trim()
        val conversationId = runCatching {
            normalizeRedditModmailId(rawConversationId)
        }.getOrNull() ?: return
        val current = mutableUiState.value
        val configuredLoader = loader ?: return
        if (
            account.isEmpty() ||
            activeAccountStableId()?.trim() != account ||
            !current.matches(account) ||
            current.selectedConversationId != conversationId ||
            current.threadLoading
        ) {
            return
        }
        mutableUiState.value = current.copy(
            threadLoading = true,
            readStateOutcomeUnknown = false,
            failureKind = null,
            failureRetryable = false,
            failureHttpStatusCode = null,
            failureDetails = null,
        )
        when (val result = configuredLoader.loadThread(account, conversationId)) {
            is AuthenticatedRedditModmailThreadResult.Loaded -> {
                val latest = mutableUiState.value
                if (!latest.matches(account) || latest.selectedConversationId != conversationId) {
                    return
                }
                mutableUiState.value = latest.copy(
                    thread = result.thread,
                    conversations = latest.conversations.map { conversation ->
                        if (conversation.id == conversationId) {
                            result.thread.conversation
                        } else {
                            conversation
                        }
                    },
                    threadLoading = false,
                    readStateOutcomeUnknown = false,
                    failureKind = null,
                    failureRetryable = false,
                    failureHttpStatusCode = null,
                    failureDetails = null,
                    contentRevision = latest.contentRevision + 1L,
                )
                refreshSelectedQueueAndUnreadCounts(account)
            }
            AuthenticatedRedditModmailThreadResult.AuthorizationRequired ->
                mutableUiState.value = mutableUiState.value.copy(
                    threadLoading = false,
                    authorizationRequired = true,
                    failureKind = null,
                )
            is AuthenticatedRedditModmailThreadResult.Failed ->
                mutableUiState.value = mutableUiState.value.copy(
                    threadLoading = false,
                    failureKind = result.kind.toUiFailureKind(),
                    failureRetryable = result.retryable,
                    failureHttpStatusCode = result.httpStatusCode,
                    failureDetails = result.details,
                )
        }
    }

    suspend fun sendMessage(
        accountStableId: String,
        rawConversationId: String,
        body: String,
        internal: Boolean,
        authorHidden: Boolean,
    ) {
        val account = accountStableId.trim()
        val conversationId = runCatching {
            normalizeRedditModmailId(rawConversationId)
        }.getOrNull() ?: return
        val current = mutableUiState.value
        if (
            account.isEmpty() ||
            activeAccountStableId()?.trim() != account ||
            !current.matches(account) ||
            current.selectedConversationId != conversationId ||
            current.sendInProgress ||
            current.conversationActionInProgress ||
            current.readStateMutationInProgress
        ) {
            return
        }
        val configuredWriter = writer
        if (configuredWriter == null) {
            mutableUiState.value = current.copy(
                failureKind = RedditModmailUiFailureKind.CONFIGURATION,
                failureRetryable = false,
                sendOutcomeUnknown = false,
            )
            return
        }

        mutableUiState.value = current.copy(
            sendInProgress = true,
            sendOutcomeUnknown = false,
            failureKind = null,
            failureRetryable = false,
            failureHttpStatusCode = null,
            failureDetails = null,
        )
        val result = try {
            configuredWriter.send(
                AuthenticatedRedditModmailSendRequest(
                    accountStableId = account,
                    conversationId = conversationId,
                    body = body,
                    internal = internal,
                    authorHidden = authorHidden,
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            AuthenticatedRedditModmailSendResult.Failed(
                kind = AuthenticatedRedditModmailFailureKind.TRANSPORT,
                retryable = false,
                outcomeUnknown = true,
            )
        }

        when (result) {
            AuthenticatedRedditModmailSendResult.Sent -> {
                val afterSend = mutableUiState.value
                if (!afterSend.matches(account)) return
                if (afterSend.selectedConversationId != conversationId) {
                    mutableUiState.value = afterSend.copy(sendInProgress = false)
                    return
                }
                if (conversationId == unknownSendConversationId) unknownSendConversationId = null
                mutableUiState.value = afterSend.copy(
                    sendInProgress = false,
                    sendOutcomeUnknown = false,
                    sendRevision = afterSend.sendRevision + 1L,
                    failureKind = null,
                    failureRetryable = false,
                    failureHttpStatusCode = null,
                    failureDetails = null,
                )
                val configuredLoader = loader ?: return
                when (val threadResult = configuredLoader.loadThread(account, conversationId)) {
                    is AuthenticatedRedditModmailThreadResult.Loaded -> {
                        val latest = mutableUiState.value
                        if (!latest.matches(account) || latest.selectedConversationId != conversationId) {
                            return
                        }
                        mutableUiState.value = latest.copy(
                            thread = threadResult.thread,
                            conversations = latest.conversations.map { conversation ->
                                if (conversation.id == conversationId) {
                                    threadResult.thread.conversation
                                } else {
                                    conversation
                                }
                            },
                            threadLoading = false,
                            failureKind = null,
                            failureRetryable = false,
                            failureHttpStatusCode = null,
                            failureDetails = null,
                            contentRevision = latest.contentRevision + 1L,
                        )
                    }
                    AuthenticatedRedditModmailThreadResult.AuthorizationRequired ->
                        applyToConversation(account, conversationId) { latest ->
                            latest.copy(authorizationRequired = true, failureKind = null)
                        }
                    is AuthenticatedRedditModmailThreadResult.Failed ->
                        applyToConversation(account, conversationId) { latest ->
                            latest.copy(
                                failureKind = threadResult.kind.toUiFailureKind(),
                                failureRetryable = threadResult.retryable,
                                failureHttpStatusCode = threadResult.httpStatusCode,
                                failureDetails = threadResult.details,
                            )
                        }
                }
            }
            AuthenticatedRedditModmailSendResult.AuthorizationRequired -> {
                val latest = mutableUiState.value
                if (!latest.matches(account)) return
                mutableUiState.value = latest.copy(
                    sendInProgress = false,
                    authorizationRequired = true,
                    failureKind = null,
                    failureRetryable = false,
                )
            }
            is AuthenticatedRedditModmailSendResult.Failed -> {
                val latest = mutableUiState.value
                if (result.outcomeUnknown) unknownSendConversationId = conversationId
                if (!latest.matches(account)) return
                if (latest.selectedConversationId != conversationId) {
                    mutableUiState.value = latest.copy(sendInProgress = false)
                    return
                }
                mutableUiState.value = latest.copy(
                    sendInProgress = false,
                    sendOutcomeUnknown = result.outcomeUnknown,
                    failureKind = result.kind.toUiFailureKind(),
                    failureRetryable = false,
                    failureHttpStatusCode = result.httpStatusCode,
                    failureDetails = result.failureDetails,
                )
            }
        }
    }

    suspend fun setArchived(
        accountStableId: String,
        rawConversationId: String,
        archived: Boolean,
    ) {
        val account = accountStableId.trim()
        val conversationId = runCatching { normalizeRedditModmailId(rawConversationId) }.getOrNull() ?: return
        val current = mutableUiState.value
        if (
            account.isEmpty() ||
            activeAccountStableId()?.trim() != account ||
            !current.matches(account) ||
            current.selectedConversationId != conversationId ||
            current.sendInProgress ||
            current.conversationActionInProgress ||
            current.readStateMutationInProgress
        ) return
        val configuredWriter = writer ?: run {
            mutableUiState.value = current.copy(failureKind = RedditModmailUiFailureKind.CONFIGURATION)
            return
        }
        mutableUiState.value = current.copy(
            conversationActionInProgress = true,
            conversationActionOutcomeUnknown = false,
            failureKind = null,
            failureRetryable = false,
            failureHttpStatusCode = null,
            failureDetails = null,
        )
        val result = try {
            configuredWriter.setArchived(
                AuthenticatedRedditModmailArchiveRequest(account, conversationId, archived),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            AuthenticatedRedditModmailArchiveResult.Failed(
                kind = AuthenticatedRedditModmailFailureKind.TRANSPORT,
                outcomeUnknown = true,
            )
        }
        when (result) {
            AuthenticatedRedditModmailArchiveResult.Applied -> {
                mutableUiState.value = mutableUiState.value.copy(
                    conversationActionInProgress = false,
                    conversationActionOutcomeUnknown = false,
                    failureKind = null,
                    failureRetryable = false,
                    failureHttpStatusCode = null,
                    failureDetails = null,
                )
                val configuredLoader = loader ?: return
                when (val threadResult = configuredLoader.loadThread(account, conversationId)) {
                    is AuthenticatedRedditModmailThreadResult.Loaded -> {
                        val latest = mutableUiState.value
                        if (latest.matches(account) && latest.selectedConversationId == conversationId) {
                            mutableUiState.value = latest.copy(
                                thread = threadResult.thread,
                                contentRevision = latest.contentRevision + 1L,
                            )
                        }
                    }
                    AuthenticatedRedditModmailThreadResult.AuthorizationRequired ->
                        applyToConversation(account, conversationId) { it.copy(authorizationRequired = true) }
                    is AuthenticatedRedditModmailThreadResult.Failed ->
                        applyToConversation(account, conversationId) {
                            it.copy(
                                failureKind = threadResult.kind.toUiFailureKind(),
                                failureRetryable = threadResult.retryable,
                                failureHttpStatusCode = threadResult.httpStatusCode,
                                failureDetails = threadResult.details,
                            )
                        }
                }
                val latest = mutableUiState.value
                if (latest.matches(account)) {
                    applyListResult(
                        account = account,
                        result = configuredLoader.loadByMode(account, after = null, listMode = latest.listMode),
                        append = false,
                    )
                }
            }
            AuthenticatedRedditModmailArchiveResult.AuthorizationRequired ->
                applyToConversation(
                    account,
                    conversationId,
                    elsewhere = { it.copy(conversationActionInProgress = false) },
                ) {
                    it.copy(
                        conversationActionInProgress = false,
                        conversationActionOutcomeUnknown = false,
                        authorizationRequired = true,
                        failureKind = null,
                    )
                }
            is AuthenticatedRedditModmailArchiveResult.Failed ->
                applyToConversation(
                    account,
                    conversationId,
                    elsewhere = { it.copy(conversationActionInProgress = false) },
                ) {
                    it.copy(
                        conversationActionInProgress = false,
                        conversationActionOutcomeUnknown = result.outcomeUnknown,
                        failureKind = result.kind.toUiFailureKind(),
                        failureRetryable = false,
                        failureHttpStatusCode = result.httpStatusCode,
                        failureDetails = result.failureDetails,
                    )
                }
        }
    }


    suspend fun applyTriage(
        accountStableId: String,
        rawConversationId: String,
        action: RedditModmailTriageAction,
    ) {
        val account = accountStableId.trim()
        val conversationId = runCatching {
            normalizeRedditModmailId(rawConversationId)
        }.getOrNull() ?: return
        val current = mutableUiState.value
        if (
            account.isEmpty() ||
            activeAccountStableId()?.trim() != account ||
            !current.matches(account) ||
            current.selectedConversationId != conversationId ||
            current.sendInProgress ||
            current.conversationActionInProgress ||
            current.readStateMutationInProgress
        ) return

        val threadConversation = current.thread
            ?.conversation
            ?.takeIf { conversation -> conversation.id == conversationId }
        if (
            action.muteHours != null || action == RedditModmailTriageAction.UNMUTE
        ) {
            if (
                threadConversation == null ||
                threadConversation.isInternal ||
                threadConversation.participantName.isNullOrBlank()
            ) return
        }

        val configuredWriter = writer ?: run {
            mutableUiState.value = current.copy(
                failureKind = RedditModmailUiFailureKind.CONFIGURATION,
            )
            return
        }
        mutableUiState.value = current.copy(
            conversationActionInProgress = true,
            conversationActionOutcomeUnknown = false,
            failureKind = null,
            failureRetryable = false,
            failureHttpStatusCode = null,
            failureDetails = null,
        )

        val result = try {
            configuredWriter.triage(
                AuthenticatedRedditModmailTriageRequest(
                    accountStableId = account,
                    conversationId = conversationId,
                    action = action,
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            AuthenticatedRedditModmailTriageResult.Failed(
                kind = AuthenticatedRedditModmailFailureKind.TRANSPORT,
                outcomeUnknown = true,
            )
        }

        when (result) {
            AuthenticatedRedditModmailTriageResult.Applied -> {
                mutableUiState.value = mutableUiState.value.copy(
                    conversationActionInProgress = false,
                    conversationActionOutcomeUnknown = false,
                    failureKind = null,
                    failureRetryable = false,
                    failureHttpStatusCode = null,
                    failureDetails = null,
                )
                val configuredLoader = loader ?: return
                when (val threadResult = configuredLoader.loadThread(account, conversationId)) {
                    is AuthenticatedRedditModmailThreadResult.Loaded -> {
                        val latest = mutableUiState.value
                        if (
                            latest.matches(account) &&
                            latest.selectedConversationId == conversationId
                        ) {
                            mutableUiState.value = latest.copy(
                                thread = threadResult.thread,
                                conversations = latest.conversations.map { conversation ->
                                    if (conversation.id == conversationId) {
                                        threadResult.thread.conversation
                                    } else {
                                        conversation
                                    }
                                },
                                contentRevision = latest.contentRevision + 1L,
                            )
                        }
                    }
                    AuthenticatedRedditModmailThreadResult.AuthorizationRequired ->
                        applyToConversation(account, conversationId) { it.copy(authorizationRequired = true) }
                    is AuthenticatedRedditModmailThreadResult.Failed ->
                        applyToConversation(account, conversationId) {
                            it.copy(
                                failureKind = threadResult.kind.toUiFailureKind(),
                                failureRetryable = threadResult.retryable,
                                failureHttpStatusCode = threadResult.httpStatusCode,
                                failureDetails = threadResult.details,
                            )
                        }
                }

                val latest = mutableUiState.value
                if (latest.matches(account)) {
                    applyListResult(
                        account = account,
                        result = configuredLoader.loadByMode(
                            account,
                            after = null,
                            listMode = latest.listMode,
                        ),
                        append = false,
                    )
                }
            }
            AuthenticatedRedditModmailTriageResult.AuthorizationRequired ->
                applyToConversation(
                    account,
                    conversationId,
                    elsewhere = { it.copy(conversationActionInProgress = false) },
                ) {
                    it.copy(
                        conversationActionInProgress = false,
                        conversationActionOutcomeUnknown = false,
                        authorizationRequired = true,
                        failureKind = null,
                    )
                }
            is AuthenticatedRedditModmailTriageResult.Failed ->
                applyToConversation(
                    account,
                    conversationId,
                    elsewhere = { it.copy(conversationActionInProgress = false) },
                ) {
                    it.copy(
                        conversationActionInProgress = false,
                        conversationActionOutcomeUnknown = result.outcomeUnknown,
                        failureKind = result.kind.toUiFailureKind(),
                        failureRetryable = false,
                        failureHttpStatusCode = result.httpStatusCode,
                        failureDetails = result.failureDetails,
                    )
                }
        }
    }

    private suspend fun mutateReadState(
        account: String,
        conversationId: String,
        unread: Boolean,
    ) {
        val current = mutableUiState.value
        if (
            account.isEmpty() ||
            activeAccountStableId()?.trim() != account ||
            !current.matches(account) ||
            current.selectedConversationId != conversationId ||
            current.sendInProgress ||
            current.conversationActionInProgress ||
            current.readStateMutationInProgress
        ) {
            return
        }
        val configuredWriter = writer ?: run {
            mutableUiState.value = current.copy(
                failureKind = RedditModmailUiFailureKind.CONFIGURATION,
                readStateOutcomeUnknown = false,
            )
            return
        }
        mutableUiState.value = current.copy(
            readStateMutationInProgress = true,
            readStateOutcomeUnknown = false,
            failureKind = null,
            failureRetryable = false,
            failureHttpStatusCode = null,
            failureDetails = null,
        )
        val result = try {
            configuredWriter.setReadState(
                AuthenticatedRedditModmailReadStateRequest(
                    accountStableId = account,
                    conversationId = conversationId,
                    unread = unread,
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            AuthenticatedRedditModmailReadStateResult.Failed(
                kind = AuthenticatedRedditModmailFailureKind.TRANSPORT,
                outcomeUnknown = true,
            )
        }

        when (result) {
            AuthenticatedRedditModmailReadStateResult.Applied -> {
                val latest = mutableUiState.value
                if (!latest.matches(account)) return
                if (latest.selectedConversationId != conversationId) {
                    mutableUiState.value = latest.copy(
                        conversations = latest.conversations.map { conversation ->
                            if (conversation.id == conversationId) {
                                conversation.withUnread(unread)
                            } else {
                                conversation
                            }
                        },
                        readStateMutationInProgress = false,
                        contentRevision = latest.contentRevision + 1L,
                    )
                    refreshSelectedQueueAndUnreadCounts(account)
                    return
                }
                val projectedThread = latest.thread?.takeIf { thread ->
                    thread.conversation.id == conversationId
                }?.let { thread ->
                    thread.copy(
                        conversation = thread.conversation.withUnread(unread),
                    )
                }
                mutableUiState.value = latest.copy(
                    thread = projectedThread ?: latest.thread,
                    conversations = latest.conversations.map { conversation ->
                        if (conversation.id == conversationId) {
                            conversation.withUnread(unread)
                        } else {
                            conversation
                        }
                    },
                    readStateMutationInProgress = false,
                    readStateOutcomeUnknown = false,
                    failureKind = null,
                    failureRetryable = false,
                    failureHttpStatusCode = null,
                    failureDetails = null,
                    contentRevision = latest.contentRevision + 1L,
                )
                refreshSelectedQueueAndUnreadCounts(account)
            }
            AuthenticatedRedditModmailReadStateResult.AuthorizationRequired ->
                mutableUiState.value = mutableUiState.value.copy(
                    readStateMutationInProgress = false,
                    readStateOutcomeUnknown = false,
                    authorizationRequired = true,
                    failureKind = null,
                )
            is AuthenticatedRedditModmailReadStateResult.Failed ->
                mutableUiState.value = if (mutableUiState.value.selectedConversationId != conversationId) {
                    mutableUiState.value.copy(readStateMutationInProgress = false)
                } else mutableUiState.value.copy(
                    readStateMutationInProgress = false,
                    readStateOutcomeUnknown = result.outcomeUnknown,
                    failureKind = result.kind.toUiFailureKind(),
                    failureRetryable = false,
                    failureHttpStatusCode = result.httpStatusCode,
                    failureDetails = result.failureDetails,
                )
        }
    }

    private suspend fun refreshSelectedQueueAndUnreadCounts(account: String) {
        val configuredLoader = loader ?: return
        val latest = mutableUiState.value
        if (!latest.matches(account)) return
        applyListResult(
            account = account,
            result = configuredLoader.loadByMode(
                accountStableId = account,
                after = null,
                listMode = latest.listMode,
            ),
            append = false,
        )
    }

    fun closeConversation() {
        mutableUiState.value = mutableUiState.value.copy(
            selectedConversationId = null,
            thread = null,
            threadLoading = false,
            sendInProgress = false,
            sendOutcomeUnknown = false,
            conversationActionInProgress = false,
            conversationActionOutcomeUnknown = false,
            readStateMutationInProgress = false,
            readStateOutcomeUnknown = false,
            failureKind = null,
            failureRetryable = false,
            failureHttpStatusCode = null,
            failureDetails = null,
        )
    }

    suspend fun beginAuthorization(accountStableId: String): AuthBrowserLaunchResult {
        val account = accountStableId.trim()
        val configuredCapability = capability
        val configuredClientMode = clientMode
        if (
            account.isEmpty() ||
            configuredCapability == null ||
            configuredClientMode == null
        ) {
            mutableUiState.value = failure(
                account = account,
                kind = RedditModmailUiFailureKind.CONFIGURATION,
            )
            return AuthBrowserLaunchResult.NotStarted
        }
        if (activeAccountStableId()?.trim() != account) {
            mutableUiState.value = failure(
                account = account,
                kind = RedditModmailUiFailureKind.ACCOUNT_CHANGED,
            )
            return AuthBrowserLaunchResult.NotStarted
        }
        pendingAccountStableId = account
        val current = mutableUiState.value.takeIf { state -> state.matches(account) }
            ?: RedditModmailUiState(accountStableId = account)
        mutableUiState.value = current.copy(
            authorizing = true,
            authorizationRequired = true,
            failureKind = null,
            failureRetryable = false,
        )
        return try {
            AuthBrowserLaunchResult.Ready(
                authorizationUrl = configuredCapability.beginAuthorization(account),
                clientMode = configuredClientMode,
                freshAccountSession = true,
            )
        } catch (_: Exception) {
            configuredCapability.cancelPendingAuthorization()
            mutableUiState.value = mutableUiState.value.copy(
                authorizing = false,
                authorizationRequired = true,
                failureKind = RedditModmailUiFailureKind.AUTHORIZATION,
                failureRetryable = true,
            )
            AuthBrowserLaunchResult.NotStarted
        }
    }

    fun browserLaunchFailed() {
        capability?.cancelPendingAuthorization()
        mutableUiState.value = mutableUiState.value.copy(
            authorizing = false,
            authorizationRequired = true,
            failureKind = RedditModmailUiFailureKind.AUTHORIZATION,
            failureRetryable = true,
        )
    }

    fun cancelPendingAuthorization() {
        capability?.cancelPendingAuthorization()
        mutableUiState.value = mutableUiState.value.copy(
            authorizing = false,
            authorizationRequired = true,
        )
    }

    suspend fun handleCallbackResultTokenIfOwned(resultToken: String): Boolean {
        if (resultToken == AUTH_CALLBACK_CANCELLED_TOKEN) {
            cancelPendingAuthorization()
            return true
        }
        val configuredCapability = capability ?: return false
        if (!configuredCapability.hasStoredCallback()) return false
        completeAuthorization()
        return true
    }

    suspend fun resumeStoredAuthorizationIfPresent(): Boolean {
        val configuredCapability = capability ?: return false
        return when {
            configuredCapability.hasStoredCallback() -> {
                completeAuthorization()
                true
            }
            configuredCapability.hasPendingAuthorization() -> true
            else -> false
        }
    }

    private suspend fun completeAuthorization() {
        val configuredCapability = capability ?: return
        val account = pendingAccountStableId ?: activeAccountStableId()?.trim() ?: return
        mutableUiState.value = mutableUiState.value.copy(authorizing = true)
        when (val result = configuredCapability.completePendingAuthorization()) {
            is AuthCommentWriteCapabilityResult.Upgraded -> {
                if (
                    result.accountStableId != account ||
                    activeAccountStableId()?.trim() != account
                ) {
                    mutableUiState.value = failure(
                        account = account,
                        kind = RedditModmailUiFailureKind.ACCOUNT_CHANGED,
                    )
                    return
                }
                mutableUiState.value = mutableUiState.value.copy(
                    authorizing = false,
                    authorizationRequired = false,
                    failureKind = null,
                    failureRetryable = false,
                )
                // LOW11_AUTHORIZATION_KEEPS_THE_OPEN_CONVERSATION_V1 (OCB-433): an
                // authorization asked for from inside a conversation (send,
                // archive, triage) no longer closes it; the list and the
                // conversation are read again, the reply draft stays.
                val openConversationId = mutableUiState.value.selectedConversationId
                load(account, forceRefresh = true, keepOpenConversation = true)
                if (openConversationId != null && mutableUiState.value.matches(account)) {
                    openConversation(account, openConversationId)
                }
            }
            is AuthCommentWriteCapabilityResult.Failed -> {
                mutableUiState.value = mutableUiState.value.copy(
                    authorizing = false,
                    authorizationRequired = true,
                    failureKind = result.kind.toUiFailureKind(),
                    failureRetryable = result.retryable,
                )
            }
        }
    }

    private fun applyListResult(
        account: String,
        result: AuthenticatedRedditModmailResult,
        append: Boolean,
    ) {
        val current = mutableUiState.value
        if (!current.matches(account)) return
        mutableUiState.value = when (result) {
            is AuthenticatedRedditModmailResult.Loaded -> current.copy(
                mailCommunities = result.mailCommunities,
                conversations = if (append) {
                    (current.conversations + result.conversations)
                        .distinctBy { conversation -> conversation.id }
                } else {
                    result.conversations
                },
                nextCursor = result.nextCursor,
                unreadCounts = result.unreadCounts ?: current.unreadCounts,
                loading = false,
                appending = false,
                loaded = true,
                authorizationRequired = false,
                authorizing = false,
                failureKind = null,
                failureRetryable = false,
                failureHttpStatusCode = null,
                failureDetails = null,
                contentRevision = current.contentRevision + 1L,
            )
            AuthenticatedRedditModmailResult.AuthorizationRequired -> current.copy(
                loading = false,
                appending = false,
                authorizing = false,
                authorizationRequired = true,
                failureKind = null,
                failureRetryable = false,
            )
            is AuthenticatedRedditModmailResult.Failed -> current.copy(
                loading = false,
                appending = false,
                authorizing = false,
                failureKind = result.kind.toUiFailureKind(),
                failureRetryable = result.retryable,
                failureHttpStatusCode = result.httpStatusCode,
                failureDetails = result.details,
            )
        }
    }

    private fun failure(
        account: String,
        kind: RedditModmailUiFailureKind,
    ) = RedditModmailUiState(
        accountStableId = account.takeIf(String::isNotEmpty),
        failureKind = kind,
        contentRevision = mutableUiState.value.contentRevision,
    )
}

private fun app.subshell.prototype.data.account.RedditModmailConversation.withUnread(
    unread: Boolean,
): app.subshell.prototype.data.account.RedditModmailConversation =
    copy(
        unread = unread,
        lastUnreadEpochSeconds = if (unread) {
            lastUnreadEpochSeconds ?: lastUpdatedEpochSeconds
        } else {
            null
        },
    )

private fun AuthenticatedRedditModmailFailureKind.toUiFailureKind():
    RedditModmailUiFailureKind = when (this) {
    AuthenticatedRedditModmailFailureKind.CONFIGURATION ->
        RedditModmailUiFailureKind.CONFIGURATION
    AuthenticatedRedditModmailFailureKind.ACCOUNT_CHANGED ->
        RedditModmailUiFailureKind.ACCOUNT_CHANGED
    AuthenticatedRedditModmailFailureKind.SESSION ->
        RedditModmailUiFailureKind.SESSION
    AuthenticatedRedditModmailFailureKind.HTTP ->
        RedditModmailUiFailureKind.HTTP
    AuthenticatedRedditModmailFailureKind.TRANSPORT ->
        RedditModmailUiFailureKind.TRANSPORT
    AuthenticatedRedditModmailFailureKind.PROTOCOL ->
        RedditModmailUiFailureKind.PROTOCOL
}

private fun AuthCommentWriteCapabilityFailureKind.toUiFailureKind():
    RedditModmailUiFailureKind = when (this) {
    AuthCommentWriteCapabilityFailureKind.ACCOUNT ->
        RedditModmailUiFailureKind.ACCOUNT_CHANGED
    AuthCommentWriteCapabilityFailureKind.CALLBACK,
    AuthCommentWriteCapabilityFailureKind.TOKEN_EXCHANGE,
    AuthCommentWriteCapabilityFailureKind.IDENTITY,
    AuthCommentWriteCapabilityFailureKind.SCOPE,
    -> RedditModmailUiFailureKind.AUTHORIZATION
    AuthCommentWriteCapabilityFailureKind.STORAGE ->
        RedditModmailUiFailureKind.SESSION
}
