package app.subshell.prototype.auth

import app.subshell.prototype.data.error.SafeFailureText
import app.subshell.prototype.data.error.safeJsonPathText
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.account.MessageReplyChannel
import app.subshell.prototype.data.account.MessageReplyFailureKind
import app.subshell.prototype.data.account.MessageReplyPhase
import app.subshell.prototype.data.account.MessageReplyRequest
import app.subshell.prototype.data.account.MessageReplyUiState
import app.subshell.prototype.data.account.messageReplyFailureMessage
import app.subshell.prototype.data.account.messageReplySendingUiState
import app.subshell.prototype.data.account.normalizedMessageReplyRequest
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditMessageReplyResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditMessageReplyWriter
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// SUBSHELL_MESSAGEREPLY01_COORDINATOR_V1
/**
 * Replying to an inbox message: one dispatch through the non-retrying
 * writer, the outcome-unknown state that blocks a blind retry, and the
 * authoritative inbox reload after an accepted reply. Moved verbatim out
 * of AuthConnectViewModel; the ViewModel keeps one-line forwarders and
 * passes its inbox reload as [loadRedditInbox].
 */
internal class MessageReplyCoordinator(
    private val messageReplyWriter: AuthenticatedRedditMessageReplyWriter?,
    private val loadRedditInbox: (accountStableId: String, forceRefresh: Boolean) -> Unit,
    private val scope: CoroutineScope,
) {
    private val _messageReplyUiState = MutableStateFlow(MessageReplyUiState())
    private var messageReplyJob: Job? = null

    val messageReplyUiState: StateFlow<MessageReplyUiState> =
        _messageReplyUiState.asStateFlow()

    fun sendRedditMessageReply(request: MessageReplyRequest) {
        // LOW10_VALIDATION_NEVER_REPLACES_A_SEND_IN_FLIGHT_V1 (OCB-588): a send in
        // flight or with an unknown outcome is checked first; an invalid
        // request no longer replaces it (its result was dropped, the next send
        // cancelled it).
        if (_messageReplyUiState.value.blocksSend) return
        val normalized = runCatching { request.normalizedMessageReplyRequest() }
            .getOrNull()
        if (normalized == null) {
            _messageReplyUiState.value = MessageReplyUiState(
                phase = MessageReplyPhase.FAILED,
                failureKind = MessageReplyFailureKind.VALIDATION,
                message = messageReplyFailureMessage(MessageReplyFailureKind.VALIDATION),
            )
            return
        }
        if (_messageReplyUiState.value.blocksSend) return

        val writer = messageReplyWriter
        if (writer == null) {
            _messageReplyUiState.value = MessageReplyUiState(
                phase = MessageReplyPhase.FAILED,
                accountStableId = normalized.accountStableId,
                inboxItemId = normalized.inboxItemId,
                parentFullname = normalized.parentFullname,
                failureKind = MessageReplyFailureKind.UNAVAILABLE,
                message = messageReplyFailureMessage(MessageReplyFailureKind.UNAVAILABLE),
            )
            return
        }

        messageReplyJob?.cancel()
        // SUBSHELL_MESSAGES02C6R5_OPTIMISTIC_DIRECT_CHAT_MEDIA_ECHO_V1
        // Publish the local Direct Chat media URI before transport starts. The UI can render
        // the selected GIF/image immediately while Matrix upload continues in the background.
        _messageReplyUiState.value = messageReplySendingUiState(normalized)
        messageReplyJob = scope.launch {
            val dispatchedAtEpochSeconds = System.currentTimeMillis() / 1_000L
            val result = try {
                writer.send(normalized)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                AuthenticatedRedditMessageReplyResult.Failed(
                    kind = MessageReplyFailureKind.OUTCOME_UNKNOWN,
                )
            }
            val current = _messageReplyUiState.value
            if (!current.matches(normalized.accountStableId, normalized.inboxItemId)) {
                return@launch
            }
            when (result) {
                is AuthenticatedRedditMessageReplyResult.Accepted -> {
                    _messageReplyUiState.value = current.copy(
                        phase = MessageReplyPhase.SUCCEEDED,
                        failureKind = null,
                        failureDetails = null,
                        message = null,
                        retryable = false,
                        sentText = result.sentBody,
                        sentDraftText = result.request.text,
                        sentHadMedia = result.request.media != null,
                        sentMediaUri = result.sentMediaUri,
                        // SUBSHELL_MESSAGES02C6R5R1_CONFIRMATION_CONTINUITY_V1
                        // Keep the selected local content URI as the visual backing after Matrix
                        // acceptance. The authoritative media ID still drives reconciliation.
                        optimisticMediaUri = current.optimisticMediaUri
                            .takeIf { result.request.media != null },
                        optimisticMediaMimeType = current.optimisticMediaMimeType
                            .takeIf { result.request.media != null },
                        sentMatrixMediaId = result.sentMatrixMediaId,
                        sentMatrixEventId = result.sentMatrixEventId,
                        completedAtEpochSeconds = System.currentTimeMillis() / 1_000L,
                        completionRevision = current.completionRevision + 1L,
                    )
                    loadRedditInbox(normalized.accountStableId, true)
                }

                is AuthenticatedRedditMessageReplyResult.Failed -> {
                    val phase = if (
                        result.kind == MessageReplyFailureKind.OUTCOME_UNKNOWN
                    ) {
                        MessageReplyPhase.OUTCOME_UNKNOWN
                    } else {
                        MessageReplyPhase.FAILED
                    }
                    // SUBSHELL_MESSAGES02E2A2_REDDIT_PARITY_DUAL_WRITE_V1
                    // The first Matrix m.image write is irreversible once its event_id is confirmed.
                    // If only the following m.text write fails/turns uncertain, preserve the media
                    // identity and the text draft instead of pretending the whole send failed.
                    val partialMediaAccepted =
                        normalized.channel == MessageReplyChannel.DIRECT_CHAT &&
                            normalized.media != null &&
                            !result.sentMatrixEventId.isNullOrBlank()
                    if (partialMediaAccepted) {
                        _messageReplyUiState.value = current.copy(
                            phase = phase,
                            failureKind = result.kind,
                            message = messageReplyFailureMessage(result.kind),
                            retryable = false,
                            sentText = null,
                            sentDraftText = normalized.text,
                            sentHadMedia = true,
                            sentMediaUri = result.sentMediaUri,
                            optimisticMediaUri = current.optimisticMediaUri,
                            optimisticMediaMimeType = current.optimisticMediaMimeType,
                            sentMatrixMediaId = result.sentMatrixMediaId,
                            sentMatrixEventId = result.sentMatrixEventId,
                            completedAtEpochSeconds = System.currentTimeMillis() / 1_000L,
                            completionRevision = current.completionRevision + 1L,
                        )
                        loadRedditInbox(normalized.accountStableId, true)
                    } else if (
                        phase == MessageReplyPhase.OUTCOME_UNKNOWN && normalized.media == null
                    ) {
                        // ISSUE99_UNKNOWN_REPLY_READ_BACK_V1: a lost answer is read back,
                        // never re-sent. Keep what was sent and when, and reload the inbox,
                        // so the conversation can recognise the reply once Reddit shows it.
                        _messageReplyUiState.value = current.copy(
                            phase = phase,
                            failureKind = result.kind,
                            message = messageReplyFailureMessage(result.kind),
                            retryable = false,
                            failureDetails = messageReplyFailureDetails(result),
                            sentText = normalized.text,
                            sentDraftText = normalized.text,
                            sentHadMedia = false,
                            sentMediaUri = null,
                            optimisticMediaUri = null,
                            optimisticMediaMimeType = null,
                            sentMatrixMediaId = null,
                            sentMatrixEventId = null,
                            completedAtEpochSeconds = dispatchedAtEpochSeconds,
                            completionRevision = current.completionRevision + 1L,
                        )
                        loadRedditInbox(normalized.accountStableId, true)
                    } else {
                        _messageReplyUiState.value = current.copy(
                            phase = phase,
                            failureKind = result.kind,
                            message = messageReplyFailureMessage(result.kind),
                            retryable = result.retryable,
                            failureDetails = messageReplyFailureDetails(result),
                            sentText = null,
                            sentDraftText = null,
                            sentHadMedia = false,
                            sentMediaUri = null,
                            optimisticMediaUri = null,
                            optimisticMediaMimeType = null,
                            sentMatrixMediaId = null,
                            sentMatrixEventId = null,
                            completedAtEpochSeconds = null,
                        )
                    }
                }
            }
        }
    }

    fun resetRedditMessageReplyState() {
        val current = _messageReplyUiState.value
        if (current.inProgress) return
        _messageReplyUiState.value = MessageReplyUiState()
    }
}

// CHAT01_CHAT_SESSION_FAILURE_STEP_V1
internal fun messageReplyFailureDetails(result: AuthenticatedRedditMessageReplyResult.Failed): FailureDetails? {
    val status = result.statusCode?.takeIf { code -> code in 100..599 }
    val step = SafeFailureText.fromUntrusted(result.providerCode)
    val jsonPath = safeJsonPathText(result.jsonPath)
    if (status == null && step == null && result.protocolKind == null && jsonPath == null) return null
    return FailureDetails(
        httpStatusCode = status,
        providerCode = step,
        retryable = result.retryable,
        protocolKind = result.protocolKind,
        jsonPath = jsonPath,
    )
}
