package io.github.brealorg.subshell.data.auth.reddit

import io.github.brealorg.subshell.data.account.ModeratorInviteFailureKind
import io.github.brealorg.subshell.data.account.RedditModeratorInviteRequest
import io.github.brealorg.subshell.data.account.normalized
import io.github.brealorg.subshell.data.auth.AuthCommentWriteCapability
import io.github.brealorg.subshell.data.auth.AuthSessionResult
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditModeratorInviteResult
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditModeratorInviteWriter
import io.github.brealorg.subshell.data.network.NetworkEndpoint
import io.github.brealorg.subshell.data.network.SubshellHttpClient
import io.github.brealorg.subshell.data.network.SubshellNetworkJson
import io.github.brealorg.subshell.data.network.reddit.RedditModeratorInvitationNetworkDataSource
import io.github.brealorg.subshell.data.network.reddit.RedditModeratorInvitationNetworkResult
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import okhttp3.Call

// MODERATION02A5A5_OWNER_BOUND_NONRETRYING_INVITE_WRITER_V1
internal class RedditModeratorInvitationWriter(
    private val capability: AuthCommentWriteCapability,
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val activeAccountStableId: () -> String?,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AuthenticatedRedditModeratorInviteWriter {
    private val mutationMutex = Mutex()

    override suspend fun invite(
        request: RedditModeratorInviteRequest,
    ): AuthenticatedRedditModeratorInviteResult = withContext(ioDispatcher) {
        if (!mutationMutex.tryLock()) {
            return@withContext failed(ModeratorInviteFailureKind.ALREADY_MUTATING)
        }
        try {
            val normalized = try {
                request.normalized()
            } catch (_: IllegalArgumentException) {
                return@withContext failed(ModeratorInviteFailureKind.VALIDATION)
            }
            if (activeAccountStableId()?.trim() != normalized.accountStableId) {
                return@withContext failed(ModeratorInviteFailureKind.ACCOUNT_CHANGED)
            }

            val session = when (
                val authorization = capability.ensureAuthorized(normalized.accountStableId)
            ) {
                is AuthSessionResult.Authenticated -> authorization.session
                is AuthSessionResult.AuthorizationDenied ->
                    return@withContext AuthenticatedRedditModeratorInviteResult.AuthorizationRequired
                AuthSessionResult.ReauthenticationRequired,
                AuthSessionResult.StorageFailure,
                AuthSessionResult.NoPendingCallback,
                -> return@withContext failed(ModeratorInviteFailureKind.SESSION)
                AuthSessionResult.ClientConfigurationMismatch ->
                    return@withContext failed(ModeratorInviteFailureKind.CLIENT_CONFIGURATION)
                is AuthSessionResult.EndpointFailure ->
                    return@withContext failed(ModeratorInviteFailureKind.SESSION)
            }
            if (activeAccountStableId()?.trim() != normalized.accountStableId) {
                return@withContext failed(ModeratorInviteFailureKind.ACCOUNT_CHANGED)
            }

            val client = SubshellHttpClient(
                callFactory = callFactory,
                endpoint = endpoint,
                json = SubshellNetworkJson.providerJson,
                userAgent = userAgent,
                bearerTokenProvider = capability.bearerTokenProvider(session.tokens.accessToken),
            )
            val result = try {
                RedditModeratorInvitationNetworkDataSource(client).invite(normalized)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                RedditModeratorInvitationNetworkResult.Failed(
                    ModeratorInviteFailureKind.OUTCOME_UNKNOWN,
                )
            }
            when (result) {
                RedditModeratorInvitationNetworkResult.Acknowledged ->
                    AuthenticatedRedditModeratorInviteResult.Acknowledged
                is RedditModeratorInvitationNetworkResult.Failed ->
                    AuthenticatedRedditModeratorInviteResult.Failed(
                        kind = result.kind,
                        httpStatusCode = result.httpStatusCode,
                        failureDetails = result.failureDetails,
                    )
            }
        } finally {
            mutationMutex.unlock()
        }
    }

    private fun failed(kind: ModeratorInviteFailureKind) =
        AuthenticatedRedditModeratorInviteResult.Failed(kind = kind)
}
