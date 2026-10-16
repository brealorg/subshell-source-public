package app.subshell.prototype.auth

import app.subshell.prototype.data.account.DirectChatDeleteFailureKind
import app.subshell.prototype.data.account.DirectChatLiveFailureKind
import app.subshell.prototype.data.account.DirectChatLiveUiState
import app.subshell.prototype.data.account.DirectChatReaction
import app.subshell.prototype.data.account.DirectChatReactionFailureKind
import app.subshell.prototype.data.account.DirectChatReactionPresentationCache
import app.subshell.prototype.data.account.DirectChatStartFailureKind
import app.subshell.prototype.data.account.DirectChatStartPhase
import app.subshell.prototype.data.account.DirectChatStartRequest
import app.subshell.prototype.data.account.DirectChatStartUiState
import app.subshell.prototype.data.account.directChatStartFailureMessage
import app.subshell.prototype.data.account.normalizedDirectChatStartRequest
import app.subshell.prototype.data.account.reconcileDirectChatAuthoritativeReactions
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditDirectChatLiveReader
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditDirectChatLiveResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditDirectChatReactionRelationsProbeReader
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditDirectChatReactionResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditDirectChatReactionWriter
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditDirectChatRedactionResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditDirectChatRedactionWriter
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditDirectChatStartResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditDirectChatStartWriter
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditDirectChatTypingWriter
import app.subshell.prototype.data.auth.reddit.redditChatMatrixReactionKeyOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// SUBSHELL_DIRECTCHAT01_COORDINATOR_V1
/**
 * Direct Chat over Matrix: starting a new chat, and the live session of an
 * open one (stream, resume cursor, outbound typing with idle/keep-alive,
 * delete/redaction, reactions with the authoritative relations probe and
 * the persisted presentation cache). Moved verbatim out of
 * AuthConnectViewModel; the ViewModel keeps one-line forwarders.
 * Characterised by AuthConnectViewModelDirectChatLiveTest.
 */
internal class DirectChatCoordinator(
    private val directChatStartWriter: AuthenticatedRedditDirectChatStartWriter?,
    private val directChatLiveReader: AuthenticatedRedditDirectChatLiveReader?,
    private val directChatTypingWriter: AuthenticatedRedditDirectChatTypingWriter?,
    private val directChatRedactionWriter: AuthenticatedRedditDirectChatRedactionWriter?,
    private val directChatReactionWriter: AuthenticatedRedditDirectChatReactionWriter?,
    private val directChatReactionPresentationCache: DirectChatReactionPresentationCache?,
    private val directChatReactionRelationsProbeReader: AuthenticatedRedditDirectChatReactionRelationsProbeReader?,
    /** After an accepted start: the ViewModel's inbox reload for that account. */
    private val onChatStarted: (accountStableId: String) -> Unit,
    private val scope: CoroutineScope,
) {
    // SUBSHELL_MESSAGES02D5A_REAL_NEW_DIRECT_CHAT_V1
    private val _directChatStartUiState = MutableStateFlow(DirectChatStartUiState())

    // OCB418_UNKNOWN_START_OUTCOME_KEPT_PER_ACCOUNT_V1 (OCB-418): one start
    // state is shown; an account's unknown outcome that another account's
    // start would replace is set aside here. It keeps blocking that account's
    // starts (a resend could post the first message twice) and is shown again
    // when that account's New message screen resets.
    private val parkedUnknownStarts = mutableMapOf<String, DirectChatStartUiState>()
    private val _directChatLiveUiState = MutableStateFlow(DirectChatLiveUiState())
    private var directChatStartJob: Job? = null
    private var directChatLiveJob: Job? = null
    @Volatile
    private var directChatLiveResumeTargetKey: String? = null
    @Volatile
    private var directChatLiveResumeCursor: String? = null
    // SUBSHELL_MESSAGES02D3B_DELETE_OWN_DIRECT_CHAT_MESSAGE_V1
    private var directChatDeleteJob: Job? = null
    // SUBSHELL_MESSAGES02E2A_DIRECT_CHAT_REACTION_WRITE_V1
    // SUBSHELL_INBOXM3A2R2B1_REACTION_LIFECYCLE_HARDENING_V1
    private var directChatReactionJob: Job? = null
    private var directChatReactionGeneration = 0L
    // SUBSHELL_MESSAGES02E2A_R3A_REACTION_RELATIONS_READ_PROBE_V1
    private var directChatReactionRelationsProbeJob: Job? = null
    // SUBSHELL_MESSAGES02D2B_OUTBOUND_TYPING_RUNTIME_V1
    private var directChatTypingIdleJob: Job? = null
    private var directChatTypingTargetKey: String? = null
    private var directChatTypingDesired = false
    private var directChatTypingLastRequested: Boolean? = null
    private var directChatTypingLastAttemptAtMs = 0L

    val directChatStartUiState: StateFlow<DirectChatStartUiState> =
        _directChatStartUiState.asStateFlow()
    val directChatLiveUiState: StateFlow<DirectChatLiveUiState> =
        _directChatLiveUiState.asStateFlow()

    // SUBSHELL_MESSAGES02D5A_REAL_NEW_DIRECT_CHAT_V1
    fun startRedditDirectChat(request: DirectChatStartRequest) {
        val normalized = runCatching { request.normalizedDirectChatStartRequest() }
            .getOrNull()
        if (normalized == null) {
            _directChatStartUiState.value = DirectChatStartUiState(
                phase = DirectChatStartPhase.FAILED,
                failureKind = DirectChatStartFailureKind.VALIDATION,
                message = directChatStartFailureMessage(
                    DirectChatStartFailureKind.VALIDATION,
                ),
            )
            return
        }
        // LOW8_DIRECT_CHAT_START_GUARD_PER_ACCOUNT_V1 (OCB-418): a send in flight
        // or an unknown outcome blocks starts of the account that owns it only.
        val blocking = _directChatStartUiState.value
        if (blocking.blocksSend && blocking.accountStableId == normalized.accountStableId) return
        if (normalized.accountStableId in parkedUnknownStarts) return
        val writer = directChatStartWriter
        if (writer == null) {
            _directChatStartUiState.value = DirectChatStartUiState(
                phase = DirectChatStartPhase.FAILED,
                accountStableId = normalized.accountStableId,
                participantUsername = normalized.participantUsername,
                failureKind = DirectChatStartFailureKind.UNAVAILABLE,
                message = directChatStartFailureMessage(
                    DirectChatStartFailureKind.UNAVAILABLE,
                ),
            )
            return
        }

        directChatStartJob?.cancel()
        val replaced = _directChatStartUiState.value
        val replacedAccount = replaced.accountStableId
        if (replaced.phase == DirectChatStartPhase.OUTCOME_UNKNOWN && replacedAccount != null) {
            parkedUnknownStarts[replacedAccount] = replaced
        }
        val previousRevision = _directChatStartUiState.value.completionRevision
        _directChatStartUiState.value = DirectChatStartUiState(
            phase = DirectChatStartPhase.SENDING,
            accountStableId = normalized.accountStableId,
            participantUsername = normalized.participantUsername,
            completionRevision = previousRevision,
        )
        directChatStartJob = scope.launch {
            val result = try {
                writer.start(normalized)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                AuthenticatedRedditDirectChatStartResult.Failed(
                    kind = DirectChatStartFailureKind.OUTCOME_UNKNOWN,
                )
            }
            val latest = _directChatStartUiState.value
            if (!latest.matches(normalized.accountStableId, normalized.participantUsername)) {
                return@launch
            }
            when (result) {
                is AuthenticatedRedditDirectChatStartResult.Accepted -> {
                    _directChatStartUiState.value = latest.copy(
                        phase = DirectChatStartPhase.SUCCEEDED,
                        failureKind = null,
                        message = null,
                        httpStatusCode = null,
                        completionRevision = latest.completionRevision + 1L,
                    )
                    onChatStarted(normalized.accountStableId)
                }
                is AuthenticatedRedditDirectChatStartResult.Failed -> {
                    val phase = if (
                        result.kind == DirectChatStartFailureKind.OUTCOME_UNKNOWN
                    ) {
                        DirectChatStartPhase.OUTCOME_UNKNOWN
                    } else {
                        DirectChatStartPhase.FAILED
                    }
                    _directChatStartUiState.value = latest.copy(
                        phase = phase,
                        failureKind = result.kind,
                        message = directChatStartFailureMessage(result.kind),
                        httpStatusCode = result.statusCode,
                    )
                }
            }
        }
    }

    fun resetRedditDirectChatStartState(activeAccountStableId: String? = null) {
        val current = _directChatStartUiState.value
        if (current.inProgress) return
        val parked = activeAccountStableId?.let(parkedUnknownStarts::remove)
        if (parked != null) {
            if (current.blocksSend && current.accountStableId != null) {
                parkedUnknownStarts[current.accountStableId] = current
            }
            _directChatStartUiState.value = parked
            return
        }
        // LOW8 (OCB-418): another account's unknown outcome is kept; only that
        // account's own New message screen may clear it.
        if (
            current.blocksSend &&
            activeAccountStableId != null &&
            current.accountStableId != null &&
            current.accountStableId != activeAccountStableId
        ) {
            return
        }
        _directChatStartUiState.value = DirectChatStartUiState()
    }

    // SUBSHELL_MESSAGES02C7_FOREGROUND_MATRIX_DIRECT_CHAT_LIVE_READ_V1
    fun startRedditDirectChatLive(
        accountStableId: String,
        inboxItemId: String,
        participantUsername: String,
    ) {
        val account = accountStableId.trim()
        val inboxItem = inboxItemId.trim()
        val participant = participantUsername.trim()
        if (account.isEmpty() || inboxItem.isEmpty() || participant.isEmpty()) return
        val reader = directChatLiveReader ?: return
        val targetKey = directChatLiveTargetKey(account, inboxItem, participant)
        val current = _directChatLiveUiState.value
        if (
            directChatLiveJob?.isActive == true &&
            current.matches(account, inboxItem, participant)
        ) {
            return
        }

        directChatLiveJob?.cancel()
        directChatReactionRelationsProbeJob?.cancel()
        directChatReactionRelationsProbeJob = null
        if (directChatLiveResumeTargetKey != targetKey) {
            directChatLiveResumeTargetKey = targetKey
            directChatLiveResumeCursor = null
        }
        val previousForTarget = current.takeIf { state ->
            state.matches(account, inboxItem, participant)
        }
        // SUBSHELL_MESSAGES02E2A_R3D_PERSISTED_REACTION_PRESENTATION_CACHE_V1
        val cachedPresentation = if (previousForTarget == null) {
            directChatReactionPresentationCache?.load(
                accountStableId = account,
                inboxItemId = inboxItem,
                participantUsername = participant,
            )
        } else {
            null
        }
        _directChatLiveUiState.value = DirectChatLiveUiState(
            accountStableId = account,
            inboxItemId = inboxItem,
            participantUsername = participant,
            connecting = true,
            peerTyping = false,
            messages = previousForTarget?.messages.orEmpty(),
            redactedMessageIds = previousForTarget?.redactedMessageIds.orEmpty(),
            reactions = previousForTarget?.reactions ?: cachedPresentation?.reactions.orEmpty(),
            cachedReactionTargets =
                previousForTarget?.cachedReactionTargets ?: cachedPresentation?.targets.orEmpty(),
            contentRevision = previousForTarget?.contentRevision ?: 0L,
            // LOW11_REOPEN_KEEPS_A_DELETE_IN_FLIGHT_V1 (OCB-419): a delete still
            // running for this chat keeps its target, so its result lands.
            deletingEventId = previousForTarget?.deletingEventId,
            deleteFailureKind = previousForTarget?.deleteFailureKind,
        )
        val resumeCursor = directChatLiveResumeCursor
        directChatLiveJob = scope.launch {
            // LOW11_PROBE_IS_A_CHILD_OF_THE_LIVE_JOB_V1 (OCB-586): the reaction
            // probe starts on the reader's thread; as a child of this job it
            // ends with the live session even when stop runs at the same time.
            val liveJob = coroutineContext[Job]
            val result = try {
                reader.stream(
                    accountStableId = account,
                    participantUsername = participant,
                    since = resumeCursor,
                    onConnected = {
                        val latest = _directChatLiveUiState.value
                        if (latest.matches(account, inboxItem, participant)) {
                            _directChatLiveUiState.value = latest.copy(
                                connecting = false,
                                live = true,
                                failureKind = null,
                            )
                            // SUBSHELL_MESSAGES02E2A_R3A_REACTION_RELATIONS_READ_PROBE_V1
                            // SUBSHELL_MESSAGES02E2A_R3B_AUTHORITATIVE_REACTION_HYDRATION_V1
                            // SUBSHELL_MESSAGES02E2A_R3C_FAST_REACTION_HYDRATION_V1
                            directChatReactionRelationsProbeReader?.let { probeReader ->
                                val targets = latest.messages
                                    .asSequence()
                                    .filter { message -> message.id.startsWith('$') }
                                    .filterNot { message ->
                                        message.id in latest.redactedMessageIds
                                    }
                                    .map { message -> message.id }
                                    .toList()
                                if (targets.isNotEmpty()) {
                                    val reactionEventIdsAtReadStart =
                                        latest.reactions.map(DirectChatReaction::reactionEventId).toSet()
                                    directChatReactionRelationsProbeJob?.cancel()
                                    directChatReactionRelationsProbeJob = scope.launch(liveJob ?: kotlin.coroutines.EmptyCoroutineContext) {
                                        probeReader.probe(
                                            accountStableId = account,
                                            participantUsername = participant,
                                            targetEventIds = targets,
                                            onBatch = { relationsBatch ->
                                                while (true) {
                                                    val hydratedState =
                                                        _directChatLiveUiState.value
                                                    if (
                                                        !hydratedState.matches(
                                                            account,
                                                            inboxItem,
                                                            participant,
                                                        )
                                                    ) {
                                                        break
                                                    }

                                                    val locallyAddedReactionEventIds =
                                                        hydratedState.reactions
                                                            .asSequence()
                                                            .map(
                                                                DirectChatReaction::reactionEventId,
                                                            )
                                                            .filterNot { eventId ->
                                                                eventId in reactionEventIdsAtReadStart
                                                            }
                                                            .toSet()
                                                    val authoritativeReactions =
                                                        relationsBatch.reactions.map { relation ->
                                                            DirectChatReaction(
                                                                reactionEventId =
                                                                    relation.reactionEventId,
                                                                targetEventId =
                                                                    relation.targetEventId,
                                                                key = relation.key,
                                                                senderIsSelf =
                                                                    relation.senderIsSelf,
                                                            )
                                                        }

                                                    val updated = hydratedState.copy(
                                                        reactions =
                                                            reconcileDirectChatAuthoritativeReactions(
                                                                current =
                                                                    hydratedState.reactions,
                                                                authoritativeTargetEventIds =
                                                                    relationsBatch
                                                                        .authoritativeTargetEventIds,
                                                                authoritative =
                                                                    authoritativeReactions,
                                                                preserveReactionEventIds =
                                                                    locallyAddedReactionEventIds,
                                                                redactedEventIds =
                                                                    hydratedState
                                                                        .redactedMessageIds,
                                                                maxEvents =
                                                                    DIRECT_CHAT_REACTION_MAX_EVENTS,
                                                            ),
                                                        contentRevision =
                                                            hydratedState.contentRevision + 1L,
                                                    )
                                                    if (
                                                        _directChatLiveUiState.compareAndSet(
                                                            hydratedState,
                                                            updated,
                                                        )
                                                    ) {
                                                        directChatReactionPresentationCache?.save(
                                                            accountStableId = account,
                                                            inboxItemId = inboxItem,
                                                            participantUsername = participant,
                                                            messages = updated.messages,
                                                            reactions = updated.reactions,
                                                        )
                                                        break
                                                    }
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    },
                    onCursor = { cursor ->
                        if (directChatLiveResumeTargetKey == targetKey) {
                            directChatLiveResumeCursor = cursor
                        }
                    },
                    // CONC01_LIVE_CALLBACKS_UPDATE_ATOMICALLY_V1 (OCB-481/587): these
                    // run on the IO thread; update {} retries on a concurrent main
                    // write instead of overwriting it with a stale copy.
                    onMessages = { batch ->
                        _directChatLiveUiState.update { latest ->
                            if (!latest.matches(account, inboxItem, participant)) return@update latest
                            val merged = (latest.messages + batch)
                                .distinctBy { message -> message.id }
                                .sortedWith(
                                    compareBy<app.subshell.prototype.model.MessageBubble> { message ->
                                        message.createdEpochSeconds ?: Long.MAX_VALUE
                                    }.thenBy { message -> message.id },
                                )
                                .takeLast(DIRECT_CHAT_LIVE_MAX_MESSAGES)
                            latest.copy(
                                connecting = false,
                                live = true,
                                peerTyping = if (batch.any { message -> !message.outgoing }) {
                                    false
                                } else {
                                    latest.peerTyping
                                },
                                messages = merged,
                                failureKind = null,
                                contentRevision = latest.contentRevision + 1L,
                            )
                        }
                    },
                    onPeerTyping = { peerTyping ->
                        _directChatLiveUiState.update { latest ->
                            if (latest.matches(account, inboxItem, participant)) {
                                latest.copy(peerTyping = peerTyping)
                            } else {
                                latest
                            }
                        }
                    },
                    onRedactions = { redactedTargets ->
                        val targets = redactedTargets
                            .asSequence()
                            .map(String::trim)
                            .filter { target -> target.startsWith('$') }
                            .distinct()
                            .toList()
                        if (targets.isNotEmpty()) {
                            _directChatLiveUiState.update { latest ->
                                if (!latest.matches(account, inboxItem, participant)) return@update latest
                                val pendingConfirmed = latest.deletingEventId
                                    ?.let { target -> target in targets } == true
                                latest.copy(
                                    redactedMessageIds =
                                        (latest.redactedMessageIds + targets)
                                            .toList()
                                            .takeLast(DIRECT_CHAT_REDACTED_MAX_IDS)
                                            .toSet(),
                                    reactions = latest.reactions.filterNot { reaction ->
                                        reaction.reactionEventId in targets ||
                                            reaction.targetEventId in targets
                                    },
                                    deletingEventId = latest.deletingEventId
                                        ?.takeUnless { target -> target in targets },
                                    deleteFailureKind = if (pendingConfirmed) {
                                        null
                                    } else {
                                        latest.deleteFailureKind
                                    },
                                    contentRevision = latest.contentRevision + 1L,
                                )
                            }
                        }
                    },
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                AuthenticatedRedditDirectChatLiveResult.Failed(
                    kind = DirectChatLiveFailureKind.TRANSPORT,
                )
            }
            val latest = _directChatLiveUiState.value
            if (!latest.matches(account, inboxItem, participant)) return@launch
            when (result) {
                is AuthenticatedRedditDirectChatLiveResult.Failed -> {
                    if (
                        result.kind == DirectChatLiveFailureKind.SESSION ||
                        result.kind == DirectChatLiveFailureKind.ROOM ||
                        result.kind == DirectChatLiveFailureKind.PROTOCOL
                    ) {
                        if (directChatLiveResumeTargetKey == targetKey) {
                            directChatLiveResumeCursor = null
                        }
                    }
                    _directChatLiveUiState.value = latest.copy(
                        connecting = false,
                        live = false,
                        peerTyping = false,
                        failureKind = result.kind,
                    )
                }
            }
        }
    }

    fun updateRedditDirectChatTyping(
        accountStableId: String,
        inboxItemId: String,
        participantUsername: String,
        typing: Boolean,
    ) {
        val account = accountStableId.trim()
        val inboxItem = inboxItemId.trim()
        val participant = participantUsername.trim()
        if (account.isEmpty() || inboxItem.isEmpty() || participant.isEmpty()) return

        val writer = directChatTypingWriter ?: return
        val liveState = _directChatLiveUiState.value
        if (
            typing &&
            (
                !liveState.matches(account, inboxItem, participant) ||
                    !liveState.live
                )
        ) {
            return
        }

        val targetKey = directChatLiveTargetKey(account, inboxItem, participant)
        if (directChatTypingTargetKey != targetKey) {
            directChatTypingIdleJob?.cancel()
            directChatTypingTargetKey = targetKey
            directChatTypingDesired = false
            directChatTypingLastRequested = null
            directChatTypingLastAttemptAtMs = 0L
        }

        directChatTypingDesired = typing
        directChatTypingIdleJob?.cancel()
        directChatTypingIdleJob = null
        if (typing) {
            directChatTypingIdleJob = scope.launch {
                delay(DIRECT_CHAT_TYPING_IDLE_MS)
                directChatTypingIdleJob = null
                if (
                    directChatTypingTargetKey == targetKey &&
                    directChatTypingDesired
                ) {
                    updateRedditDirectChatTyping(
                        accountStableId = account,
                        inboxItemId = inboxItem,
                        participantUsername = participant,
                        typing = false,
                    )
                }
            }
        }

        val nowMs = System.currentTimeMillis()
        if (
            !app.subshell.prototype.data.auth.reddit.shouldSendDirectChatTypingUpdate(
                previousTyping = directChatTypingLastRequested,
                previousAttemptAtMs = directChatTypingLastAttemptAtMs,
                desiredTyping = typing,
                nowMs = nowMs,
                keepAliveMs = DIRECT_CHAT_TYPING_KEEPALIVE_MS,
            )
        ) {
            return
        }

        directChatTypingLastRequested = typing
        directChatTypingLastAttemptAtMs = nowMs
        scope.launch {
            val accepted = try {
                writer.setTyping(
                    accountStableId = account,
                    participantUsername = participant,
                    typing = typing,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            if (
                !accepted &&
                directChatTypingTargetKey == targetKey &&
                directChatTypingDesired == typing
            ) {
                // No blind retry. The next genuine edit may retry the same desired state.
                directChatTypingLastRequested = null
            }
        }
    }

    fun stopRedditDirectChatLive(
        accountStableId: String,
        inboxItemId: String,
    ) {
        val account = accountStableId.trim()
        val inboxItem = inboxItemId.trim()
        val current = _directChatLiveUiState.value
        if (!current.matches(account, inboxItem)) return
        current.participantUsername?.let { participant ->
            updateRedditDirectChatTyping(
                accountStableId = account,
                inboxItemId = inboxItem,
                participantUsername = participant,
                typing = false,
            )
        }
        directChatTypingIdleJob?.cancel()
        directChatTypingIdleJob = null
        val reactionWasInProgress = current.reactingEventId != null
        if (reactionWasInProgress) {
            directChatReactionGeneration += 1L
        }
        directChatReactionJob?.cancel()
        directChatReactionJob = null
        directChatLiveJob?.cancel()
        directChatLiveJob = null
        directChatReactionRelationsProbeJob?.cancel()
        directChatReactionRelationsProbeJob = null
        _directChatLiveUiState.value = current.copy(
            connecting = false,
            live = false,
            peerTyping = false,
            reactingEventId = null,
            reactionFailureKind = if (reactionWasInProgress) {
                DirectChatReactionFailureKind.OUTCOME_UNKNOWN
            } else {
                current.reactionFailureKind
            },
        )
    }

    fun deleteRedditDirectChatMessage(
        accountStableId: String,
        inboxItemId: String,
        participantUsername: String,
        eventId: String,
    ) {
        val account = accountStableId.trim()
        val inboxItem = inboxItemId.trim()
        val participant = participantUsername.trim()
        val target = eventId.trim()
        if (account.isEmpty() || inboxItem.isEmpty() || participant.isEmpty() || target.isEmpty()) return

        val current = _directChatLiveUiState.value
        if (!current.matches(account, inboxItem, participant)) return
        if (current.deletingEventId != null) return

        val liveTarget = current.messages.singleOrNull { message -> message.id == target }
        if (
            liveTarget == null ||
            !liveTarget.outgoing ||
            target in current.redactedMessageIds ||
            !target.startsWith('$')
        ) {
            _directChatLiveUiState.value = current.copy(
                deleteFailureKind = DirectChatDeleteFailureKind.NOT_OWNER,
            )
            return
        }

        val writer = directChatRedactionWriter
        if (writer == null) {
            _directChatLiveUiState.value = current.copy(
                deleteFailureKind = DirectChatDeleteFailureKind.UNAVAILABLE,
            )
            return
        }

        _directChatLiveUiState.value = current.copy(
            deletingEventId = target,
            deleteFailureKind = null,
        )
        directChatDeleteJob = scope.launch {
            val result = try {
                writer.redact(
                    accountStableId = account,
                    participantUsername = participant,
                    targetEventId = target,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                AuthenticatedRedditDirectChatRedactionResult.Failed(
                    kind = DirectChatDeleteFailureKind.OUTCOME_UNKNOWN,
                )
            }

            val latest = _directChatLiveUiState.value
            if (!latest.matches(account, inboxItem, participant)) return@launch
            if (latest.deletingEventId != target) return@launch

            when (result) {
                is AuthenticatedRedditDirectChatRedactionResult.Accepted -> {
                    _directChatLiveUiState.value = latest.copy(
                        redactedMessageIds =
                            (latest.redactedMessageIds + target)
                                .toList()
                                .takeLast(DIRECT_CHAT_REDACTED_MAX_IDS)
                                .toSet(),
                        deletingEventId = null,
                        deleteFailureKind = null,
                        contentRevision = latest.contentRevision + 1L,
                    )
                }

                is AuthenticatedRedditDirectChatRedactionResult.Failed -> {
                    _directChatLiveUiState.value = latest.copy(
                        deletingEventId = null,
                        deleteFailureKind = result.kind,
                    )
                }
            }
        }
    }

    fun resetRedditDirectChatDeleteFailure(
        accountStableId: String,
        inboxItemId: String,
    ) {
        val account = accountStableId.trim()
        val inboxItem = inboxItemId.trim()
        val current = _directChatLiveUiState.value
        if (!current.matches(account, inboxItem) || current.deletingEventId != null) return
        if (current.deleteFailureKind != null) {
            _directChatLiveUiState.value = current.copy(deleteFailureKind = null)
        }
    }

    // SUBSHELL_MESSAGES02E2A_DIRECT_CHAT_REACTION_WRITE_V1
    fun reactRedditDirectChatMessage(
        accountStableId: String,
        inboxItemId: String,
        participantUsername: String,
        eventId: String,
        reactionKey: String,
    ) {
        val account = accountStableId.trim()
        val inboxItem = inboxItemId.trim()
        val participant = participantUsername.trim()
        val target = eventId.trim()
        val key = redditChatMatrixReactionKeyOrNull(reactionKey)
        if (
            account.isEmpty() ||
            inboxItem.isEmpty() ||
            participant.isEmpty() ||
            target.isEmpty() ||
            key == null
        ) {
            return
        }

        val current = _directChatLiveUiState.value
        if (!current.matches(account, inboxItem, participant)) return
        if (current.reactingEventId != null) return

        val liveTarget = current.messages.singleOrNull { message -> message.id == target }
        if (
            liveTarget == null ||
            target in current.redactedMessageIds ||
            !target.startsWith('$')
        ) {
            _directChatLiveUiState.value = current.copy(
                reactionFailureKind = DirectChatReactionFailureKind.TARGET,
            )
            return
        }
        if (
            current.reactions.any { reaction ->
                reaction.targetEventId == target &&
                    reaction.senderIsSelf &&
                    reaction.key == key &&
                    reaction.reactionEventId !in current.redactedMessageIds
            }
        ) {
            return
        }

        val writer = directChatReactionWriter
        if (writer == null) {
            _directChatLiveUiState.value = current.copy(
                reactionFailureKind = DirectChatReactionFailureKind.UNAVAILABLE,
            )
            return
        }

        _directChatLiveUiState.value = current.copy(
            reactingEventId = target,
            reactionFailureKind = null,
        )
        val reactionGeneration = ++directChatReactionGeneration
        directChatReactionJob = scope.launch {
            try {
                val result = try {
                    writer.react(
                        accountStableId = account,
                        participantUsername = participant,
                        targetEventId = target,
                        reactionKey = key,
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    AuthenticatedRedditDirectChatReactionResult.Failed(
                        kind = DirectChatReactionFailureKind.OUTCOME_UNKNOWN,
                    )
                }

                val latest = _directChatLiveUiState.value
                if (!latest.matches(account, inboxItem, participant)) return@launch
                if (latest.reactingEventId != target) return@launch

                when (result) {
                    is AuthenticatedRedditDirectChatReactionResult.Accepted -> {
                        val confirmed = DirectChatReaction(
                            reactionEventId = result.reactionEventId,
                            targetEventId = result.targetEventId,
                            key = result.key,
                            senderIsSelf = true,
                        )
                        val updated = latest.copy(
                            reactions =
                                (latest.reactions + confirmed)
                                    .distinctBy(DirectChatReaction::reactionEventId)
                                    .takeLast(DIRECT_CHAT_REACTION_MAX_EVENTS),
                            reactingEventId = null,
                            reactionFailureKind = null,
                            contentRevision = latest.contentRevision + 1L,
                        )
                        _directChatLiveUiState.value = updated
                        directChatReactionPresentationCache?.save(
                            accountStableId = account,
                            inboxItemId = inboxItem,
                            participantUsername = participant,
                            messages = updated.messages,
                            reactions = updated.reactions,
                        )
                    }

                    is AuthenticatedRedditDirectChatReactionResult.Failed -> {
                        _directChatLiveUiState.value = latest.copy(
                            reactingEventId = null,
                            reactionFailureKind = result.kind,
                        )
                    }
                }
            } finally {
                if (directChatReactionGeneration == reactionGeneration) {
                    val latest = _directChatLiveUiState.value
                    if (
                        latest.matches(account, inboxItem, participant) &&
                        latest.reactingEventId == target
                    ) {
                        _directChatLiveUiState.value = latest.copy(
                            reactingEventId = null,
                            reactionFailureKind = DirectChatReactionFailureKind.OUTCOME_UNKNOWN,
                        )
                    }
                    directChatReactionJob = null
                }
            }
        }
    }

    fun resetRedditDirectChatReactionFailure(
        accountStableId: String,
        inboxItemId: String,
    ) {
        val account = accountStableId.trim()
        val inboxItem = inboxItemId.trim()
        val current = _directChatLiveUiState.value
        if (!current.matches(account, inboxItem) || current.reactingEventId != null) return
        if (current.reactionFailureKind != null) {
            _directChatLiveUiState.value = current.copy(reactionFailureKind = null)
        }
    }
}

private fun directChatLiveTargetKey(
    accountStableId: String,
    inboxItemId: String,
    participantUsername: String,
): String = listOf(
    accountStableId.trim(),
    inboxItemId.trim(),
    participantUsername.trim().lowercase(),
).joinToString("|")

private const val DIRECT_CHAT_LIVE_MAX_MESSAGES = 100
private const val DIRECT_CHAT_REDACTED_MAX_IDS = 100
private const val DIRECT_CHAT_REACTION_MAX_EVENTS = 200
private const val DIRECT_CHAT_TYPING_IDLE_MS = 2_500L
private const val DIRECT_CHAT_TYPING_KEEPALIVE_MS = 5_000L
