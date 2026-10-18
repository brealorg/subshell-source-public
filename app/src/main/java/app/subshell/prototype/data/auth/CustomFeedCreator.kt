package app.subshell.prototype.data.auth

import app.subshell.prototype.data.account.CustomFeedCreateFailureKind
import app.subshell.prototype.data.account.CustomFeedCreateRequest
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.LocalFailureKind
import app.subshell.prototype.data.error.failureDetailsOrNull
import app.subshell.prototype.data.network.FormPostRequest
import app.subshell.prototype.data.network.NetworkBearerTokenProvider
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import java.security.MessageDigest
import java.text.Normalizer
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import app.subshell.prototype.data.account.CustomFeedDeleteRequest
import app.subshell.prototype.data.account.CustomFeedManageFailureKind
import app.subshell.prototype.data.account.CustomFeedUpdateRequest
import app.subshell.prototype.data.account.customFeedApiPathSegments

internal sealed interface CustomFeedCreateResult {
    data object Created : CustomFeedCreateResult
    data object AuthorizationRequired : CustomFeedCreateResult
    data class Failed(
        val kind: CustomFeedCreateFailureKind,
        val retryable: Boolean = false,
        val statusCode: Int? = null,
        val failureDetails: FailureDetails? = null,
    ) : CustomFeedCreateResult
}

internal sealed interface CustomFeedManageResult {
    data object Updated : CustomFeedManageResult
    data object Deleted : CustomFeedManageResult
    data object AuthorizationRequired : CustomFeedManageResult
    data class Failed(
        val kind: CustomFeedManageFailureKind,
        val retryable: Boolean = false,
        val statusCode: Int? = null,
        val failureDetails: FailureDetails? = null,
    ) : CustomFeedManageResult
}

// HARNESS03_CUSTOM_FEED_SEAM_V1: the writes the ViewModel drives, as an
// interface so JVM tests can inject a fake through the runtime seam.
internal interface CustomFeedCreator {
    suspend fun create(
        request: CustomFeedCreateRequest,
        authorizedSession: StoredOAuthSession? = null,
    ): CustomFeedCreateResult

    suspend fun update(
        request: CustomFeedUpdateRequest,
        authorizedSession: StoredOAuthSession? = null,
    ): CustomFeedManageResult

    suspend fun delete(
        request: CustomFeedDeleteRequest,
        authorizedSession: StoredOAuthSession? = null,
    ): CustomFeedManageResult
}

internal class ConfiguredCustomFeedCreator(
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val accountCoordinator: AuthAccountSessionCoordinator,
    private val requiredScopes: Set<String>,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CustomFeedCreator {
    override suspend fun create(
        request: CustomFeedCreateRequest,
        authorizedSession: StoredOAuthSession?,
    ): CustomFeedCreateResult = withContext(ioDispatcher) {
            try {
                val account = request.accountStableId.trim()
                val displayName = request.displayName.trim()
                val communities = request.subredditNames
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .distinctBy(String::lowercase)
                if (
                    account.isEmpty() ||
                    displayName.isEmpty() ||
                    displayName.length > 50 ||
                    communities.isEmpty()
                ) {
                    return@withContext failed(CustomFeedCreateFailureKind.VALIDATION)
                }
                if (accountCoordinator.activeAuthenticatedAccountId() != account) {
                    return@withContext failed(CustomFeedCreateFailureKind.ACCOUNT_CHANGED)
                }
                val slug = customFeedSlug(displayName)
                    ?: return@withContext failed(CustomFeedCreateFailureKind.VALIDATION)
                val session = authorizedCustomFeedSession(
                    candidate = authorizedSession,
                    requiredScopes = requiredScopes,
                ) ?: when (
                    val tokenResult = accountCoordinator.ensureAccessTokenForAccount(
                        accountStableId = account,
                        requiredScopes = requiredScopes,
                    )
                ) {
                    is AuthSessionResult.Authenticated -> tokenResult.session
                    AuthSessionResult.ReauthenticationRequired,
                    AuthSessionResult.ClientConfigurationMismatch,
                    -> return@withContext CustomFeedCreateResult.AuthorizationRequired
                    is AuthSessionResult.EndpointFailure -> return@withContext CustomFeedCreateResult.Failed(
                        kind = CustomFeedCreateFailureKind.SESSION,
                        retryable = when (val endpointResult = tokenResult.result) {
                            is OAuthEndpointResult.HttpFailure -> endpointResult.retryable
                            is OAuthEndpointResult.TransportFailure -> endpointResult.retryable
                            is OAuthEndpointResult.ProtocolFailure -> false
                            is OAuthEndpointResult.Success -> false
                        },
                        failureDetails = tokenResult.result.toFailureDetails(),
                    )
                    AuthSessionResult.StorageFailure,
                    AuthSessionResult.NoPendingCallback,
                    is AuthSessionResult.AuthorizationDenied,
                    -> return@withContext failed(CustomFeedCreateFailureKind.SESSION)
                }
                val username = session.identity?.username?.trim()?.takeIf(String::isNotEmpty)
                    ?: return@withContext failed(CustomFeedCreateFailureKind.SESSION)
                if (!session.tokens.grants(requiredScopes)) {
                    return@withContext CustomFeedCreateResult.AuthorizationRequired
                }
                if (accountCoordinator.activeAuthenticatedAccountId() != account) {
                    return@withContext failed(CustomFeedCreateFailureKind.ACCOUNT_CHANGED)
                }
                val model = buildJsonObject {
                    put("display_name", displayName)
                    put("description_md", "")
                    put("visibility", request.visibility.apiValue)
                    put(
                        "subreddits",
                        buildJsonArray {
                            communities.forEach { community ->
                                add(buildJsonObject { put("name", community) })
                            }
                        },
                    )
                }
                val client = SubshellHttpClient(
                    callFactory = callFactory,
                    endpoint = endpoint,
                    json = SubshellNetworkJson.providerJson,
                    userAgent = userAgent,
                    bearerTokenProvider = accountCoordinator.sessionBoundBearerTokenProvider(session.tokens.accessToken),
                )
                when (
                    val result = client.postFormStatus(
                        request = FormPostRequest(
                            pathSegments = listOf(
                                "api",
                                "multi",
                                "user",
                                username,
                                "m",
                                slug,
                            ),
                            fields = listOf("model" to model.toString()),
                        ),
                    )
                ) {
                    is NetworkResult.Success -> CustomFeedCreateResult.Created
                    is NetworkResult.HttpFailure -> if (result.statusCode == 409) {
                        CustomFeedCreateResult.Failed(
                            kind = CustomFeedCreateFailureKind.NAME_CONFLICT,
                            statusCode = result.statusCode,
                            failureDetails = result.failureDetailsOrNull(),
                        )
                    } else {
                        CustomFeedCreateResult.Failed(
                            kind = CustomFeedCreateFailureKind.HTTP,
                            retryable = result.retryable,
                            statusCode = result.statusCode,
                            failureDetails = result.failureDetailsOrNull(),
                        )
                    }
                    is NetworkResult.TransportFailure ->
                        CustomFeedCreateResult.Failed(
                            kind = CustomFeedCreateFailureKind.OUTCOME_UNKNOWN,
                            retryable = false,
                            failureDetails = result.failureDetailsOrNull(),
                        )
                    is NetworkResult.ProtocolFailure ->
                        CustomFeedCreateResult.Failed(
                            kind = CustomFeedCreateFailureKind.PROTOCOL,
                            failureDetails = result.failureDetailsOrNull(),
                        )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                failed(
                    kind = CustomFeedCreateFailureKind.OUTCOME_UNKNOWN,
                    retryable = false,
                    failureDetails = FailureDetails(
                        retryable = false,
                        localKind = LocalFailureKind.UNKNOWN,
                    ).withExceptionClass(error),
                )
            }
        }

    override suspend fun update(
        request: CustomFeedUpdateRequest,
        authorizedSession: StoredOAuthSession?,
    ): CustomFeedManageResult = withContext(ioDispatcher) {
        try {
            val account = request.accountStableId.trim()
            val displayName = request.displayName.trim()
            val communities = request.subredditNames
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinctBy(String::lowercase)
            val pathSegments = customFeedApiPathSegments(request.path)
            if (
                account.isEmpty() ||
                displayName.isEmpty() ||
                displayName.length > 50 ||
                communities.isEmpty() ||
                pathSegments == null
            ) {
                return@withContext CustomFeedManageResult.Failed(CustomFeedManageFailureKind.VALIDATION)
            }
            if (accountCoordinator.activeAuthenticatedAccountId() != account) {
                return@withContext CustomFeedManageResult.Failed(CustomFeedManageFailureKind.ACCOUNT_CHANGED)
            }
            val session = resolveManageSession(account, authorizedSession)
                ?: return@withContext CustomFeedManageResult.AuthorizationRequired
            val model = buildJsonObject {
                put("display_name", displayName)
                put("visibility", request.visibility.apiValue)
                put(
                    "subreddits",
                    buildJsonArray {
                        communities.forEach { community ->
                            add(buildJsonObject { put("name", community) })
                        }
                    },
                )
            }
            val client = writeClient(session)
            when (
                val result = client.putFormStatus(
                    FormPostRequest(
                        pathSegments = pathSegments,
                        fields = listOf("model" to model.toString()),
                    ),
                )
            ) {
                is NetworkResult.Success -> CustomFeedManageResult.Updated
                is NetworkResult.HttpFailure -> CustomFeedManageResult.Failed(
                    kind = CustomFeedManageFailureKind.HTTP,
                    retryable = result.retryable,
                    statusCode = result.statusCode,
                    failureDetails = result.failureDetailsOrNull(),
                )
                is NetworkResult.TransportFailure ->
                    CustomFeedManageResult.Failed(
                        CustomFeedManageFailureKind.OUTCOME_UNKNOWN,
                        failureDetails = result.failureDetailsOrNull(),
                    )
                is NetworkResult.ProtocolFailure ->
                    CustomFeedManageResult.Failed(
                        CustomFeedManageFailureKind.PROTOCOL,
                        failureDetails = result.failureDetailsOrNull(),
                    )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            CustomFeedManageResult.Failed(
                kind = CustomFeedManageFailureKind.OUTCOME_UNKNOWN,
                retryable = false,
                failureDetails = FailureDetails(
                    retryable = false,
                    localKind = LocalFailureKind.UNKNOWN,
                ).withExceptionClass(error),
            )
        }
    }

    override suspend fun delete(
        request: CustomFeedDeleteRequest,
        authorizedSession: StoredOAuthSession?,
    ): CustomFeedManageResult = withContext(ioDispatcher) {
        try {
            val account = request.accountStableId.trim()
            val pathSegments = customFeedApiPathSegments(request.path)
            if (account.isEmpty() || pathSegments == null) {
                return@withContext CustomFeedManageResult.Failed(CustomFeedManageFailureKind.VALIDATION)
            }
            if (accountCoordinator.activeAuthenticatedAccountId() != account) {
                return@withContext CustomFeedManageResult.Failed(CustomFeedManageFailureKind.ACCOUNT_CHANGED)
            }
            val session = resolveManageSession(account, authorizedSession)
                ?: return@withContext CustomFeedManageResult.AuthorizationRequired
            when (val result = writeClient(session).deleteStatus(pathSegments)) {
                is NetworkResult.Success -> CustomFeedManageResult.Deleted
                is NetworkResult.HttpFailure -> CustomFeedManageResult.Failed(
                    kind = CustomFeedManageFailureKind.HTTP,
                    retryable = result.retryable,
                    statusCode = result.statusCode,
                    failureDetails = result.failureDetailsOrNull(),
                )
                is NetworkResult.TransportFailure ->
                    CustomFeedManageResult.Failed(
                        CustomFeedManageFailureKind.OUTCOME_UNKNOWN,
                        failureDetails = result.failureDetailsOrNull(),
                    )
                is NetworkResult.ProtocolFailure ->
                    CustomFeedManageResult.Failed(
                        CustomFeedManageFailureKind.PROTOCOL,
                        failureDetails = result.failureDetailsOrNull(),
                    )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            CustomFeedManageResult.Failed(
                kind = CustomFeedManageFailureKind.OUTCOME_UNKNOWN,
                retryable = false,
                failureDetails = FailureDetails(
                    retryable = false,
                    localKind = LocalFailureKind.UNKNOWN,
                ).withExceptionClass(error),
            )
        }
    }

    private suspend fun resolveManageSession(
        accountStableId: String,
        authorizedSession: StoredOAuthSession?,
    ): StoredOAuthSession? {
        authorizedCustomFeedSession(
            candidate = authorizedSession,
            requiredScopes = requiredScopes,
        )?.let { return it }
        return when (
            val tokenResult = accountCoordinator.ensureAccessTokenForAccount(
                accountStableId = accountStableId,
                requiredScopes = requiredScopes,
            )
        ) {
            is AuthSessionResult.Authenticated -> tokenResult.session
            else -> null
        }
    }

    private fun writeClient(session: StoredOAuthSession): SubshellHttpClient = SubshellHttpClient(
        callFactory = callFactory,
        endpoint = endpoint,
        json = SubshellNetworkJson.providerJson,
        userAgent = userAgent,
        bearerTokenProvider = accountCoordinator.sessionBoundBearerTokenProvider(session.tokens.accessToken),
    )

    private fun failed(
        kind: CustomFeedCreateFailureKind,
        retryable: Boolean = false,
        failureDetails: FailureDetails? = null,
    ): CustomFeedCreateResult.Failed = CustomFeedCreateResult.Failed(
        kind = kind,
        retryable = retryable,
        failureDetails = failureDetails,
    )
}


// AUTH14_CUSTOM_FEED_SESSION_MUST_BE_USABLE_V1 (#132): the pre-authorized
// session is used as is only while its access token is still usable. An
// expired one used to go out unrefreshed and draw a 401; now it is null and
// the caller refreshes through ensureAccessTokenForAccount.
internal fun authorizedCustomFeedSession(
    candidate: StoredOAuthSession?,
    requiredScopes: Set<String>,
    nowEpochMs: Long = System.currentTimeMillis(),
): StoredOAuthSession? = candidate?.takeIf { session ->
    session.identity != null &&
        session.tokens.grants(requiredScopes) &&
        session.tokens.isUsableAt(nowEpochMs, OAuthSessionManager.DEFAULT_REFRESH_SKEW_MS)
}

internal fun customFeedSlug(displayName: String): String? {
    val normalized = Normalizer.normalize(displayName.trim(), Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "")
        .replace("&", " and ")
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
        .take(50)
    if (normalized.length >= 2) return normalized
    // CORRECT03_NON_LATIN_FEED_NAME_GETS_A_SLUG_V1 (OCB-477): a name with no
    // Latin letters or digits (Cyrillic, Greek, CJK …) or only one left no
    // usable URL name (Reddit needs 2–50). Such a name gets "feed_" and a short
    // hash of itself; the display name is kept as typed.
    val name = displayName.trim().takeIf(String::isNotEmpty) ?: return null
    val digest = MessageDigest.getInstance("SHA-256").digest(name.toByteArray(Charsets.UTF_8))
    return "feed_" + digest.take(4).joinToString("") { byte -> "%02x".format(byte) }
}
