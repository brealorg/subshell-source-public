package app.subshell.prototype.data.account

// DEAD02_PROFILE_COMMENTS_FLOW_REMOVED_V1: the profile comments UI state went
// with the dead profile comments flow; the failure kind still drives the
// profile Comments tab's error presentation.
enum class RedditProfileCommentsUiFailureKind {
    CLIENT_CONFIGURATION,
    SESSION,
    HTTP,
    TRANSPORT,
    PROTOCOL,
    STORAGE,
}
