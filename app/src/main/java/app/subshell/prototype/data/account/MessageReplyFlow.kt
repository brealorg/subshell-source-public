package app.subshell.prototype.data.account

import app.subshell.prototype.data.error.FailureDetails

internal const val MESSAGE_REPLY_TEXT_MAX_LENGTH = 10_000

enum class MessageReplyPhase {
    IDLE,
    SENDING,
    SUCCEEDED,
    FAILED,
    OUTCOME_UNKNOWN,
}

enum class MessageReplyFailureKind {
    VALIDATION,
    CHAT_SESSION_REQUIRED,
    CHAT_ROOM_UNAVAILABLE,
    MEDIA_CAPTION_UNSUPPORTED,
    MEDIA_UNSUPPORTED,
    MEDIA_TOO_LARGE,
    MEDIA_UNAVAILABLE,
    MEDIA_AUTHORIZATION_REQUIRED,
    AUTHORIZATION_REQUIRED,
    RATE_LIMIT,
    API,
    HTTP,
    TRANSPORT,
    SESSION,
    OUTCOME_UNKNOWN,
    UNAVAILABLE,
}

enum class MessageReplyChannel {
    DIRECT_CHAT,
    PRIVATE_MESSAGE_THREAD,
}

internal fun messageReplyMediaSelectionEnabled(channel: MessageReplyChannel): Boolean = when (channel) {
    MessageReplyChannel.DIRECT_CHAT,
    MessageReplyChannel.PRIVATE_MESSAGE_THREAD,
    -> true
}

// DEAD03_NO_MEDIA_TRANSPORT_GATE_V1 (OCB-423/505): Direct Chat media uses
// Reddit's Matrix transport and private-message threads the richtext media
// path; both are always available, so the former readiness gate and its
// discovery-required failure kind (never produced) are gone.

data class MessageReplyMediaAttachment(
    val uri: String,
    val mimeType: String,
    val sourceLinkUri: String? = null,
)

data class MessageReplyRequest(
    val accountStableId: String,
    val inboxItemId: String,
    val parentFullname: String,
    val channel: MessageReplyChannel,
    val text: String,
    val media: MessageReplyMediaAttachment? = null,
    val directChatParticipantUsername: String? = null,
)

data class MessageReplyUiState(
    val phase: MessageReplyPhase = MessageReplyPhase.IDLE,
    val accountStableId: String? = null,
    val inboxItemId: String? = null,
    val parentFullname: String? = null,
    val failureKind: MessageReplyFailureKind? = null,
    val message: String? = null,
    val retryable: Boolean = false,
    val sentText: String? = null,
    val sentDraftText: String? = null,
    val sentHadMedia: Boolean = false,
    val sentMediaUri: String? = null,
    val optimisticMediaUri: String? = null,
    val optimisticMediaMimeType: String? = null,
    val sentMatrixMediaId: String? = null,
    val sentMatrixEventId: String? = null,
    val completedAtEpochSeconds: Long? = null,
    val completionRevision: Long = 0L,
    // CHAT01_CHAT_SESSION_FAILURE_STEP_V1: status and failed step, shown as details.
    val failureDetails: FailureDetails? = null,
) {
    val inProgress: Boolean
        get() = phase == MessageReplyPhase.SENDING

    val blocksSend: Boolean
        get() = phase == MessageReplyPhase.SENDING ||
            phase == MessageReplyPhase.OUTCOME_UNKNOWN

    fun matches(accountStableId: String, inboxItemId: String): Boolean =
        this.accountStableId == accountStableId.trim() &&
            this.inboxItemId == inboxItemId.trim()
}

internal fun messageReplySendingUiState(
    request: MessageReplyRequest,
): MessageReplyUiState {
    // SUBSHELL_MESSAGES02E2A2_REDDIT_PARITY_DUAL_WRITE_V1
    // Reddit Web sends mixed Direct Chat content as m.image first and m.text second.
    // The first write is still media, so mixed sends use the same immediate local backing.
    val optimisticMedia = request.media
        ?.takeIf {
            request.channel == MessageReplyChannel.DIRECT_CHAT
        }
    return MessageReplyUiState(
        phase = MessageReplyPhase.SENDING,
        accountStableId = request.accountStableId,
        inboxItemId = request.inboxItemId,
        parentFullname = request.parentFullname,
        optimisticMediaUri = optimisticMedia?.uri,
        optimisticMediaMimeType = optimisticMedia?.mimeType,
    )
}

internal fun MessageReplyRequest.normalizedMessageReplyRequest(): MessageReplyRequest {
    val account = accountStableId.trim()
    val itemId = inboxItemId.trim()
    val parent = parentFullname.trim().lowercase()
    val body = text.trim()
    val normalizedMedia = media?.normalizedMessageReplyMediaAttachment()
    val participantUsername = directChatParticipantUsername
        ?.trim()
        ?.takeIf(String::isNotEmpty)
    require(account.isNotEmpty()) { "Message reply account cannot be blank" }
    require(itemId.isNotEmpty()) { "Message reply inbox item cannot be blank" }
    require(body.isNotEmpty() || normalizedMedia != null) {
        "Message reply must contain text or media"
    }
    require(body.length <= MESSAGE_REPLY_TEXT_MAX_LENGTH) {
        "Message reply text exceeds the supported limit"
    }
    if (channel == MessageReplyChannel.DIRECT_CHAT && normalizedMedia != null) {
        require(participantUsername != null) {
            "Direct Chat media requires a participant username"
        }
    }
    return copy(
        accountStableId = account,
        inboxItemId = itemId,
        parentFullname = parent,
        text = body,
        media = normalizedMedia,
        directChatParticipantUsername = participantUsername,
    )
}

private fun MessageReplyMediaAttachment.normalizedMessageReplyMediaAttachment(): MessageReplyMediaAttachment {
    val normalizedUri = uri.trim()
    val normalizedMimeType = mimeType.trim().lowercase()
    val normalizedSourceLink = sourceLinkUri
        ?.trim()
        ?.takeIf(String::isNotEmpty)
    require(normalizedUri.isNotEmpty()) { "Message reply media URI cannot be blank" }
    require(
        normalizedMimeType == "image/jpeg" ||
            normalizedMimeType == "image/jpg" ||
            normalizedMimeType == "image/png" ||
            normalizedMimeType == "image/webp" ||
            normalizedMimeType == "image/gif" ||
            normalizedMimeType == "image/*"
    ) { "Message reply media must be an image or GIF" }
    return copy(
        uri = normalizedUri,
        mimeType = normalizedMimeType,
        sourceLinkUri = normalizedSourceLink,
    )
}

internal fun messageReplyFailureMessage(kind: MessageReplyFailureKind): String = when (kind) {
    MessageReplyFailureKind.VALIDATION -> "Enter a message or choose an image/GIF before sending."
    MessageReplyFailureKind.CHAT_SESSION_REQUIRED ->
        "subshell couldn't open Reddit's chat session for images; text messages still work."
    MessageReplyFailureKind.CHAT_ROOM_UNAVAILABLE ->
        "subshell could not match this Direct Chat to its Reddit Chat room."
    MessageReplyFailureKind.MEDIA_CAPTION_UNSUPPORTED ->
        "Send Direct Chat text and media separately for now."
    MessageReplyFailureKind.MEDIA_UNSUPPORTED -> "Choose a JPEG, PNG, WebP, or GIF."
    MessageReplyFailureKind.MEDIA_TOO_LARGE -> "Image/GIF must be 20 MB or smaller."
    MessageReplyFailureKind.MEDIA_UNAVAILABLE -> "This image/GIF can no longer be read. Choose it again."
    MessageReplyFailureKind.MEDIA_AUTHORIZATION_REQUIRED -> "Grant Reddit media access to send images or GIFs."
    MessageReplyFailureKind.AUTHORIZATION_REQUIRED -> "Reconnect Reddit access to reply."
    MessageReplyFailureKind.RATE_LIMIT -> "Reddit is rate-limiting replies. Try again later."
    MessageReplyFailureKind.API -> "Reddit rejected this reply."
    MessageReplyFailureKind.HTTP -> "Reddit could not send this reply."
    MessageReplyFailureKind.TRANSPORT -> "Could not reach Reddit. Check your connection."
    MessageReplyFailureKind.SESSION -> "The Reddit session is unavailable."
    MessageReplyFailureKind.OUTCOME_UNKNOWN ->
        "The send result is unknown. Refresh this thread before trying again."
    MessageReplyFailureKind.UNAVAILABLE -> "Reply sending is unavailable."
}
