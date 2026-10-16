package app.subshell.prototype.data.auth

// AUTH10_REQUEST_CURRENT_SCOPES_V1 (#59): a capability authorization asks
// Reddit for the capability's own scopes plus every scope the account's
// current token already has. Reddit issues a token with exactly the requested
// scopes and the upgrade replaces the account's session, so asking for less
// would silently drop earlier grants (vote, save, submit, other capabilities).
// The requirement for "is this capability authorized" stays the capability's
// own scopes, so one account is never judged by another account's grants.
// Computed when the authorization begins and again, the same way, when its
// pending record and callback are matched, so the scope fingerprints agree.
internal fun OAuthProviderConfiguration.requestingCurrentScopesOf(
    store: EncryptedAuthSessionStore,
    accountStableId: String?,
): OAuthProviderConfiguration {
    val account = accountStableId?.trim()?.takeIf(String::isNotEmpty) ?: return this
    val current = runCatching { store.session(account) }
        .getOrNull()
        ?.takeIf { session ->
            session.provider == provider &&
                session.clientMode == clientMode &&
                session.clientIdFingerprint.isNotEmpty() &&
                constantTimeEquals(session.clientIdFingerprint, clientIdFingerprint)
        }
        ?.tokens
        ?.scope
        ?.oauthScopeSet()
        .orEmpty()
    return if (scopes.containsAll(current)) this else copy(scopes = scopes + current)
}
