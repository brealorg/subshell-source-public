package app.subshell.prototype.data.account

// MODRESPONSE01A1_REMOVAL_MESSAGE_MODEL_V1
/**
 * How Reddit delivers a moderator's removal response. Reddit decides
 * distinguish/sticky from the type: the public types leave a distinguished,
 * stickied comment on a post (a distinguished reply on a comment); the
 * private types go out as modmail. `title` is ignored for the public types.
 */
enum class RedditRemovalMessageType(val apiValue: String) {
    PUBLIC("public"),
    PUBLIC_AS_SUBREDDIT("public_as_subreddit"),
    PRIVATE("private"),
    PRIVATE_EXPOSED("private_exposed"),
    ;

    val isPublic: Boolean
        get() = this == PUBLIC || this == PUBLIC_AS_SUBREDDIT
}

data class RedditRemovalMessage(
    val type: RedditRemovalMessageType,
    val body: String,
    val title: String = "",
) {
    init {
        require(body.isNotBlank()) { "A removal message needs a body" }
        require(body.length <= MAX_REMOVAL_MESSAGE_BODY_CHARS) { "Removal message body is too long" }
        require(title.length <= MAX_REMOVAL_MESSAGE_TITLE_CHARS) { "Removal message title is too long" }
    }

    companion object {
        const val MAX_REMOVAL_MESSAGE_BODY_CHARS = 10_000
        const val MAX_REMOVAL_MESSAGE_TITLE_CHARS = 100
    }
}

/** Outcome of one optional step that follows a successful content removal. */
enum class RedditRemovalStepOutcome {
    NOT_REQUESTED,
    SENT,
    FAILED,
}
