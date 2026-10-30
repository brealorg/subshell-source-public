package app.subshell.prototype.data.auth

// ISSUE32_REDDIT_REFUSED_ACCESS_IS_ONE_STATE_V1 (#32): how the token endpoint
// refusing the app's Client ID is told apart from refusing the account's
// grant; the state itself is data.account.RedditAccessRefusal.

/** True when the token endpoint refused the Client ID rather than the account's grant. */
internal fun isClientAccessRefusal(failure: OAuthEndpointResult.HttpFailure): Boolean =
    failure.oauthError in CLIENT_REFUSAL_OAUTH_ERRORS ||
        (failure.statusCode == 401 && failure.oauthError != "invalid_grant")

private val CLIENT_REFUSAL_OAUTH_ERRORS = setOf("invalid_client", "unauthorized_client")

/** The answer every read gets while access is refused; no request is made. */
internal val CLIENT_ACCESS_REFUSED_RESULT: OAuthEndpointResult<Nothing> =
    OAuthEndpointResult.ProtocolFailure(OAuthProtocolFailureKind.CLIENT_ACCESS_REFUSED)
