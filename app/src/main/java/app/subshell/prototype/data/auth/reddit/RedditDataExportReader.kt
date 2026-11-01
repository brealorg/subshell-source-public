package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.auth.AuthProfileHistoryCapability
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.normalizedOAuthScopes
import app.subshell.prototype.data.network.JsonGetRequest
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import kotlinx.serialization.json.JsonElement
import okhttp3.Call

internal sealed interface RedditDataExportAccess {
    data class Granted(
        val grantedScopes: Set<String>,
        val fetch: suspend (JsonGetRequest) -> NetworkResult<JsonElement>,
    ) : RedditDataExportAccess

    /** The account must sign in again (or has signed out). */
    data object SignInRequired : RedditDataExportAccess

    /** Reddit or the device could not give a token now. */
    data object Unavailable : RedditDataExportAccess
}

/**
 * REDDITEXPORT01_EXPORT_YOUR_REDDIT_DATA_V1: the account's token through the
 * profile history capability (the one You › Saved reads with, in either client
 * mode) and a reader that keeps Reddit's answer as JSON. A page of 100 saved
 * posts can pass the usual 2 MB, so the reader allows 8 MB.
 */
internal class RedditDataExportReader(
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val capability: AuthProfileHistoryCapability,
) {
    suspend fun open(accountStableId: String): RedditDataExportAccess {
        val session = when (val result = capability.ensureAuthorized(accountStableId)) {
            is AuthSessionResult.Authenticated -> result.session
            AuthSessionResult.ReauthenticationRequired,
            AuthSessionResult.ClientConfigurationMismatch,
            is AuthSessionResult.AuthorizationDenied,
            -> return RedditDataExportAccess.SignInRequired
            is AuthSessionResult.EndpointFailure,
            AuthSessionResult.StorageFailure,
            AuthSessionResult.NoPendingCallback,
            -> return RedditDataExportAccess.Unavailable
        }
        val client = SubshellHttpClient(
            callFactory = callFactory,
            endpoint = endpoint,
            json = SubshellNetworkJson.providerJson,
            userAgent = userAgent,
            bearerTokenProvider = capability.bearerTokenProvider(session.tokens.accessToken),
            maxJsonBodyBytes = EXPORT_MAX_PAGE_BYTES,
        )
        return RedditDataExportAccess.Granted(
            grantedScopes = normalizedOAuthScopes(session.tokens.scope),
            fetch = { request -> client.getJson(request, JsonElement.serializer()) },
        )
    }
}

private const val EXPORT_MAX_PAGE_BYTES = 8L * 1024L * 1024L
