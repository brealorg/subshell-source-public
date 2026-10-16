package app.subshell.prototype.ui

// REDDITURL04_COMPOSE_LINK_V1
/** What a compose link puts into New message: the chat partner and a first message. */
data class NewMessagePrefill(
    val recipientUsername: String,
    val text: String,
)

/** A chat has no subject, so the subject becomes the first paragraph. */
internal fun newMessagePrefillFor(target: RedditTarget.Compose): NewMessagePrefill =
    NewMessagePrefill(
        recipientUsername = target.recipient,
        text = listOfNotNull(target.subject, target.message).joinToString("\n\n"),
    )
