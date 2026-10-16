package app.subshell.prototype.data.account

// ISSUE211_ACCOUNT_NSFW_PREFERENCE_V1 (#211): whether the signed-in Reddit
// account itself hides NSFW content. Reddit filters listings and search by
// these account preferences whatever Subshell shows, so with "Show NSFW
// posts" on and over_18 off the reader would see empty feeds without a word.
// Subshell reads them and never writes them (owner: link to Reddit's settings).

data class NsfwAccountPreferences(
    /** "I am over eighteen years old and willing to view adult content". */
    val over18: Boolean,
    /** "Include not safe for work (NSFW) search results in searches", when Reddit reports it. */
    val searchIncludeOver18: Boolean?,
)

sealed interface NsfwPreferencesResult {
    data class Known(val preferences: NsfwAccountPreferences) : NsfwPreferencesResult

    /** Reddit answered without a readable over_18. */
    data object Unavailable : NsfwPreferencesResult

    /** Not read (signed out, account changed, network or session problem); say nothing. */
    data object Failed : NsfwPreferencesResult
}

internal fun interface RedditNsfwPreferencesReader {
    suspend fun read(account: String, isCurrent: () -> Boolean): NsfwPreferencesResult
}

// ISSUE211_QUARANTINE_OPT_IN_V1 (#211): "View anyway" on a quarantined
// community. Sent only on the reader's tap, never retried or re-sent; the
// feed reload that follows is the read-back.
sealed interface QuarantineOptInResult {
    data object Sent : QuarantineOptInResult

    /** Reddit refused it (HTTP status), or nothing could be sent (signed out, account changed). */
    data class Refused(val httpStatusCode: Int?) : QuarantineOptInResult

    /** Sent, but the answer was lost; the reload shows whether it took. */
    data object OutcomeUnknown : QuarantineOptInResult

    /**
     * ISSUE211_QUARANTINE_OPT_IN_NEEDS_SUBSCRIBE_V1: Reddit's opt-in takes the
     * `subscribe` scope (as joining does); a grant without it is not sent.
     */
    data object MissingPermission : QuarantineOptInResult
}

internal fun interface RedditQuarantineOptIn {
    suspend fun optIn(account: String, communityName: String, isCurrent: () -> Boolean): QuarantineOptInResult
}

/** ISSUE211: the account's NSFW preference and the quarantine opt-in, with one session rule. */
internal interface RedditCommunityAccessService : RedditNsfwPreferencesReader, RedditQuarantineOptIn
