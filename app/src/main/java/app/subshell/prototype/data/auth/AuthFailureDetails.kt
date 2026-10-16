package app.subshell.prototype.data.auth

import app.subshell.prototype.data.error.FailureDetails

internal fun OAuthEndpointResult<*>.toFailureDetails(): FailureDetails = when (this) {
    is OAuthEndpointResult.HttpFailure -> FailureDetails(
        httpStatusCode = statusCode,
        retryable = retryable,
    )
    is OAuthEndpointResult.TransportFailure -> FailureDetails(
        retryable = retryable,
    )
    is OAuthEndpointResult.ProtocolFailure -> FailureDetails(
        retryable = false,
    ).withProviderDetails(
        code = "oauth_${kind.name.lowercase()}",
    )
    is OAuthEndpointResult.Success -> FailureDetails(retryable = false)
}
