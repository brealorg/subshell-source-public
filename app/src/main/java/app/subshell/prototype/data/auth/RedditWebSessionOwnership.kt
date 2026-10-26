package app.subshell.prototype.data.auth

/**
 * SECAUDIT02_WEB_SESSION_GOES_WITH_ITS_ACCOUNT_V1 (security audit #2): the
 * RedReader sign-in window leaves Reddit's web login (`reddit_session`) in the
 * app's browser cookie store, and Direct Chat mints its tokens from it. That
 * login is not limited by scopes and `revoke_token` does not end it, so it is
 * owned by the account whose sign-in created it and removed with that account.
 */
internal interface RedditWebSessionOwnership {
    /** A sign-in for [accountStableId] just finished; the web login now belongs to it. */
    suspend fun signedInAs(accountStableId: String)

    /**
     * [accountStableId] was logged out or removed. Its web login is deleted; a web
     * login known to belong to another account is kept. An unknown owner counts as
     * this account's.
     */
    suspend fun accountRemoved(accountStableId: String)

    data object None : RedditWebSessionOwnership {
        override suspend fun signedInAs(accountStableId: String) = Unit

        override suspend fun accountRemoved(accountStableId: String) = Unit
    }
}

internal data class RedditWebSessionOwner(
    val accountStableId: String,
    val sessionFingerprint: String,
)

/** Whether a logout of [removedAccountStableId] keeps the web login whose fingerprint is [currentFingerprint]. */
internal fun redditWebSessionSurvivesRemoval(
    owner: RedditWebSessionOwner?,
    currentFingerprint: String?,
    removedAccountStableId: String,
): Boolean = currentFingerprint != null &&
    owner != null &&
    owner.sessionFingerprint == currentFingerprint &&
    owner.accountStableId != removedAccountStableId.trim()
