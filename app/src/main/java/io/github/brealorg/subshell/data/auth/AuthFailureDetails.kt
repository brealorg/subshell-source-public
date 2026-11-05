package io.github.brealorg.subshell.data.auth

import io.github.brealorg.subshell.data.error.FailureDetails

internal fun OAuthEndpointResult<*>.toFailureDetails(): FailureDetails = when (this) {
    is OAuthEndpointResult.HttpFailure -> FailureDetails(
        httpStatusCode = statusCode,
        retryable = retryable,
    )
    is OAuthEndpointResult.TransportFailure -> FailureDetails(
        retryable = retryable,
        transportKind = kind,
    )
    is OAuthEndpointResult.ProtocolFailure -> FailureDetails(
        retryable = false,
    ).withProviderDetails(
        code = "oauth_${kind.name.lowercase()}",
    )
    is OAuthEndpointResult.Success -> FailureDetails(retryable = false)
}
