package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.account.ModeratorPermissionUpdateFailureKind
import app.subshell.prototype.data.account.RedditModeratorPermissionUpdateRequest
import app.subshell.prototype.data.account.normalized
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratorPermissionUpdateResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratorPermissionWriter
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.data.network.reddit.RedditModeratorPermissionNetworkDataSource
import app.subshell.prototype.data.network.reddit.RedditModeratorPermissionNetworkResult
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import okhttp3.Call

// MODERATION02A5A6_OWNER_BOUND_NONRETRYING_PERMISSION_WRITER_V1
internal class RedditModeratorPermissionWriter(
    private val capability: AuthCommentWriteCapability,
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val activeAccountStableId: () -> String?,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AuthenticatedRedditModeratorPermissionWriter {
    private val mutationMutex = Mutex()

    override suspend fun update(
        request: RedditModeratorPermissionUpdateRequest,
    ): AuthenticatedRedditModeratorPermissionUpdateResult = withContext(ioDispatcher) {
        if (!mutationMutex.tryLock()) {
            return@withContext failed(ModeratorPermissionUpdateFailureKind.ALREADY_MUTATING)
        }
        try {
            val normalized = try {
                request.normalized()
            } catch (_: IllegalArgumentException) {
                return@withContext failed(ModeratorPermissionUpdateFailureKind.VALIDATION)
            }
            if (activeAccountStableId()?.trim() != normalized.accountStableId) {
                return@withContext failed(ModeratorPermissionUpdateFailureKind.ACCOUNT_CHANGED)
            }

            val session = when (
                val authorization = capability.ensureAuthorized(normalized.accountStableId)
            ) {
                is AuthSessionResult.Authenticated -> authorization.session
                is AuthSessionResult.AuthorizationDenied ->
                    return@withContext AuthenticatedRedditModeratorPermissionUpdateResult.AuthorizationRequired
                AuthSessionResult.ReauthenticationRequired,
                AuthSessionResult.StorageFailure,
                AuthSessionResult.NoPendingCallback,
                -> return@withContext failed(ModeratorPermissionUpdateFailureKind.SESSION)
                AuthSessionResult.ClientConfigurationMismatch ->
                    return@withContext failed(ModeratorPermissionUpdateFailureKind.CLIENT_CONFIGURATION)
                is AuthSessionResult.EndpointFailure ->
                    return@withContext failed(ModeratorPermissionUpdateFailureKind.SESSION)
            }
            if (activeAccountStableId()?.trim() != normalized.accountStableId) {
                return@withContext failed(ModeratorPermissionUpdateFailureKind.ACCOUNT_CHANGED)
            }

            val client = SubshellHttpClient(
                callFactory = callFactory,
                endpoint = endpoint,
                json = SubshellNetworkJson.providerJson,
                userAgent = userAgent,
                bearerTokenProvider = capability.bearerTokenProvider(session.tokens.accessToken),
            )
            val result = try {
                RedditModeratorPermissionNetworkDataSource(client).update(normalized)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                RedditModeratorPermissionNetworkResult.Failed(
                    ModeratorPermissionUpdateFailureKind.OUTCOME_UNKNOWN,
                )
            }
            when (result) {
                RedditModeratorPermissionNetworkResult.Acknowledged ->
                    AuthenticatedRedditModeratorPermissionUpdateResult.Acknowledged
                is RedditModeratorPermissionNetworkResult.Failed ->
                    AuthenticatedRedditModeratorPermissionUpdateResult.Failed(
                        kind = result.kind,
                        httpStatusCode = result.httpStatusCode,
                        failureDetails = result.failureDetails,
                    )
            }
        } finally {
            mutationMutex.unlock()
        }
    }

    private fun failed(kind: ModeratorPermissionUpdateFailureKind) =
        AuthenticatedRedditModeratorPermissionUpdateResult.Failed(kind = kind)
}
