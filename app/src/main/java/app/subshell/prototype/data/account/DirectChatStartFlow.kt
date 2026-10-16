package app.subshell.prototype.data.account

// SUBSHELL_MESSAGES02D5A_REAL_NEW_DIRECT_CHAT_V1

enum class DirectChatStartPhase {
    IDLE,
    SENDING,
    FAILED,
    OUTCOME_UNKNOWN,
    SUCCEEDED,
}

enum class DirectChatStartFailureKind {
    VALIDATION,
    SESSION,
    USER_NOT_FOUND,
    ROOM,
    MEDIA_UNSUPPORTED,
    MEDIA_TOO_LARGE,
    MEDIA_UNAVAILABLE,
    HTTP,
    TRANSPORT,
    PROTOCOL,
    OUTCOME_UNKNOWN,
    UNAVAILABLE,
}

data class DirectChatStartRequest(
    val accountStableId: String,
    val participantUsername: String,
    val text: String,
    val media: MessageReplyMediaAttachment? = null,
)

data class DirectChatStartUiState(
    val phase: DirectChatStartPhase = DirectChatStartPhase.IDLE,
    val accountStableId: String? = null,
    val participantUsername: String? = null,
    val failureKind: DirectChatStartFailureKind? = null,
    val message: String? = null,
    val httpStatusCode: Int? = null,
    val completionRevision: Long = 0L,
) {
    init {
        require(accountStableId == null || accountStableId.isNotBlank()) {
            "Direct Chat start account ID cannot be blank"
        }
        require(participantUsername == null || participantUsername.isNotBlank()) {
            "Direct Chat start participant cannot be blank"
        }
        require(httpStatusCode == null || httpStatusCode in 100..599) {
            "Direct Chat start HTTP status must be valid"
        }
        require(completionRevision >= 0L) {
            "Direct Chat start completion revision cannot be negative"
        }
    }

    val inProgress: Boolean
        get() = phase == DirectChatStartPhase.SENDING

    val blocksSend: Boolean
        get() = inProgress || phase == DirectChatStartPhase.OUTCOME_UNKNOWN

    fun matches(accountStableId: String, participantUsername: String): Boolean =
        this.accountStableId == accountStableId.trim() &&
            this.participantUsername.equals(
                normalizeDirectChatParticipantUsername(participantUsername),
                ignoreCase = true,
            )
}

internal fun DirectChatStartRequest.normalizedDirectChatStartRequest():
    DirectChatStartRequest {
    val account = accountStableId.trim()
    val participant = normalizeDirectChatParticipantUsername(participantUsername)
    val body = text.trim()
    val normalizedMedia = media?.let { attachment ->
        val uri = attachment.uri.trim()
        val mimeType = attachment.mimeType.trim()
        require(uri.isNotEmpty()) { "Direct Chat start media URI cannot be blank" }
        require(mimeType.startsWith("image/", ignoreCase = true)) {
            "Direct Chat start media must be an image or GIF"
        }
        attachment.copy(
            uri = uri,
            mimeType = mimeType,
            sourceLinkUri = attachment.sourceLinkUri
                ?.trim()
                ?.takeIf(String::isNotEmpty),
        )
    }
    require(account.isNotEmpty()) { "Direct Chat start account ID cannot be blank" }
    require(participant.isNotEmpty()) { "Direct Chat start participant cannot be blank" }
    require(
        participant.none { character -> character.isWhitespace() || character == '/' },
    ) {
        "Direct Chat start participant has an unsupported shape"
    }
    require(body.isNotEmpty() != (normalizedMedia != null)) {
        "Direct Chat start requires exactly one of text or media"
    }
    return copy(
        accountStableId = account,
        participantUsername = participant,
        text = body,
        media = normalizedMedia,
    )
}
internal fun normalizeDirectChatParticipantUsername(raw: String): String {
    // ACCOUNT02_CHAT_PARTICIPANT_SLASH_U_V1: `/u/name` is as common as `u/name`.
    val trimmed = raw.trim().removePrefix("/")
    return when {
        trimmed.startsWith("u/", ignoreCase = true) -> trimmed.drop(2).trim()
        else -> trimmed
    }
}

internal fun directChatStartFailureMessage(kind: DirectChatStartFailureKind): String = when (kind) {
    DirectChatStartFailureKind.VALIDATION ->
        "Choose a Reddit user and add either text, an image, or a GIF."
    DirectChatStartFailureKind.SESSION ->
        "Reddit Chat needs an active Reddit session."
    DirectChatStartFailureKind.USER_NOT_FOUND ->
        "That Reddit user isn't available for Direct Chat."
    DirectChatStartFailureKind.ROOM ->
        "Reddit couldn't open this Direct Chat."
    DirectChatStartFailureKind.MEDIA_UNSUPPORTED ->
        "Choose a JPEG, PNG, WebP, or GIF."
    DirectChatStartFailureKind.MEDIA_TOO_LARGE ->
        "Image/GIF must be 20 MB or smaller."
    DirectChatStartFailureKind.MEDIA_UNAVAILABLE ->
        "This image/GIF can no longer be read. Choose it again."
    DirectChatStartFailureKind.HTTP ->
        "Reddit rejected the Direct Chat request."
    DirectChatStartFailureKind.TRANSPORT ->
        "Couldn't reach Reddit Chat."
    DirectChatStartFailureKind.PROTOCOL ->
        "Reddit returned an unexpected Direct Chat response."
    DirectChatStartFailureKind.OUTCOME_UNKNOWN ->
        "The chat or message may have been created. Refresh Inbox before trying again."
    DirectChatStartFailureKind.UNAVAILABLE ->
        "Direct Chat isn't available for this account."
}
