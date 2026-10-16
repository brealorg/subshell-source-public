package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.account.ModeratorRemovalFailureKind
import app.subshell.prototype.data.account.RedditModeratorRemovalRequest
import app.subshell.prototype.data.account.normalized
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratorRemovalResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratorRemovalWriter
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.data.network.reddit.RedditModeratorRemovalNetworkDataSource
import app.subshell.prototype.data.network.reddit.RedditModeratorRemovalNetworkResult
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import okhttp3.Call

// MODERATION02A5A7_OWNER_BOUND_NONRETRYING_REMOVAL_WRITER_V1
internal class RedditModeratorRemovalWriter(
    private val capability: AuthCommentWriteCapability,
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val activeAccountStableId: () -> String?,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AuthenticatedRedditModeratorRemovalWriter {
    private val mutationMutex = Mutex()

    override suspend fun remove(
        request: RedditModeratorRemovalRequest,
    ): AuthenticatedRedditModeratorRemovalResult = withContext(ioDispatcher) {
        if (!mutationMutex.tryLock()) {
            return@withContext failed(ModeratorRemovalFailureKind.ALREADY_MUTATING)
        }
        try {
            val normalized = try {
                request.normalized()
            } catch (_: IllegalArgumentException) {
                return@withContext failed(ModeratorRemovalFailureKind.VALIDATION)
            }
            if (activeAccountStableId()?.trim() != normalized.accountStableId) {
                return@withContext failed(ModeratorRemovalFailureKind.ACCOUNT_CHANGED)
            }

            val session = when (
                val authorization = capability.ensureAuthorized(normalized.accountStableId)
            ) {
                is AuthSessionResult.Authenticated -> authorization.session
                is AuthSessionResult.AuthorizationDenied ->
                    return@withContext AuthenticatedRedditModeratorRemovalResult.AuthorizationRequired
                AuthSessionResult.ReauthenticationRequired,
                AuthSessionResult.StorageFailure,
                AuthSessionResult.NoPendingCallback,
                -> return@withContext failed(ModeratorRemovalFailureKind.SESSION)
                AuthSessionResult.ClientConfigurationMismatch ->
                    return@withContext failed(ModeratorRemovalFailureKind.CLIENT_CONFIGURATION)
                is AuthSessionResult.EndpointFailure ->
                    return@withContext failed(ModeratorRemovalFailureKind.SESSION)
            }
            if (activeAccountStableId()?.trim() != normalized.accountStableId) {
                return@withContext failed(ModeratorRemovalFailureKind.ACCOUNT_CHANGED)
            }

            val client = SubshellHttpClient(
                callFactory = callFactory,
                endpoint = endpoint,
                json = SubshellNetworkJson.providerJson,
                userAgent = userAgent,
                bearerTokenProvider = capability.bearerTokenProvider(session.tokens.accessToken),
            )
            val result = try {
                RedditModeratorRemovalNetworkDataSource(client).remove(normalized)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                RedditModeratorRemovalNetworkResult.Failed(
                    ModeratorRemovalFailureKind.OUTCOME_UNKNOWN,
                )
            }
            when (result) {
                RedditModeratorRemovalNetworkResult.Acknowledged ->
                    AuthenticatedRedditModeratorRemovalResult.Acknowledged
                is RedditModeratorRemovalNetworkResult.Failed ->
                    AuthenticatedRedditModeratorRemovalResult.Failed(
                        kind = result.kind,
                        httpStatusCode = result.httpStatusCode,
                        failureDetails = result.failureDetails,
                    )
            }
        } finally {
            mutationMutex.unlock()
        }
    }

    private fun failed(kind: ModeratorRemovalFailureKind) =
        AuthenticatedRedditModeratorRemovalResult.Failed(kind = kind)
}
