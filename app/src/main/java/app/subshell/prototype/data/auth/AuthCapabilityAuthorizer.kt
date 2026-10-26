package app.subshell.prototype.data.auth

import app.subshell.prototype.data.network.NetworkBearerTokenProvider

internal enum class AuthCapabilityAuthorizationOwner(
    val wireKey: String,
) {
    COMMENT_WRITE("comment_write"),
    COMMENT_EDIT("comment_edit"),
    COMMENT_DELETE("comment_delete"),
    POST_EDIT("post_edit"),
    POST_DELETE("post_delete"),
    POST_MANAGE("post_manage"),
    MODERATION_ACTION("moderation_action"),
    MODERATION_REASON("moderation_reason"),
    POST_VOTE("post_vote"),
    POST_SAVE("post_save"),
    POST_FOLLOW("post_follow"),
    CONTENT_SAFETY("content_safety"),
    TEXT_POST_WRITE("text_post_write"),
    COMPOSER_FLAIR("composer_flair"),
    COMPOSER_REQUIREMENTS("composer_requirements"),
    COMMUNITY_MEMBERSHIP("community_membership"),
    // REDDITFAV03_FAVORITE_AUTHORIZATION_V1
    REDDIT_FAVORITE("reddit_favorite"),
    CUSTOM_FEED_WRITE("custom_feed_write"),
    INBOX_READ("inbox_read"),
    MODMAIL_READ("modmail_read"),
    MODERATION_LOG("moderation_log"),
    MODERATION_USERS("moderation_users"),
    MODERATION_TEAM("moderation_team"),
    PROFILE_HISTORY("profile_history"),
    COMMUNITY_WIKI_READ("community_wiki_read"),
    ;

    companion object {
        fun fromWireKey(value: String?): AuthCapabilityAuthorizationOwner? {
            val normalized = normalizeAuthCapabilityAuthorizationOwnerKey(value)
                ?: return null
            return entries.firstOrNull { it.wireKey == normalized }
        }
    }
}

internal fun normalizeAuthCapabilityAuthorizationOwnerKey(value: String?): String? {
    if (value == null) return null
    val normalized = value.trim().lowercase()
    require(normalized.isNotEmpty()) {
        "Authorization owner key cannot be blank"
    }
    require(normalized.length <= 64) {
        "Authorization owner key is too long"
    }
    require(normalized.all { it.isLowerCase() || it.isDigit() || it == '_' }) {
        "Authorization owner key contains unsupported characters"
    }
    return normalized
}

// AUTHKEY01_OVERSIZED_CONTEXT_KEY_IS_UNAVAILABLE_V1 (OCB-605): wiki and
// profile-history keys hex-encode a name; a name outside Reddit's real limits
// (long or non-Latin) can exceed the 64-character key. Owners check it first
// and report the authorization as unavailable instead of throwing.
internal fun authorizationContextKeyFits(value: String): Boolean =
    runCatching { normalizeAuthCapabilityAuthorizationContextKey(value) }.isSuccess

internal fun normalizeAuthCapabilityAuthorizationContextKey(value: String?): String? {
    if (value == null) return null
    val normalized = value.trim().lowercase()
    require(normalized.isNotEmpty()) {
        "Authorization context key cannot be blank"
    }
    require(normalized.length <= 64) {
        "Authorization context key is too long"
    }
    require(normalized.all { it.isLowerCase() || it.isDigit() || it == '_' }) {
        "Authorization context key contains unsupported characters"
    }
    return normalized
}

internal enum class AuthCapabilityAuthorizationFailureKind {
    ACCOUNT,
    CALLBACK,
    TOKEN_EXCHANGE,
    IDENTITY,
    SCOPE,
    STORAGE,
}

internal sealed interface AuthCapabilityAuthorizationResult {
    data class Upgraded(
        val accountStableId: String,
        val username: String,
        val owner: AuthCapabilityAuthorizationOwner,
        val session: StoredOAuthSession,
    ) : AuthCapabilityAuthorizationResult

    data class Failed(
        val kind: AuthCapabilityAuthorizationFailureKind,
        val retryable: Boolean = false,
    ) : AuthCapabilityAuthorizationResult
}

internal interface AuthCapabilityAuthorizer {
    suspend fun beginAuthorization(
        accountStableId: String,
        owner: AuthCapabilityAuthorizationOwner,
    ): String

    fun hasPendingAuthorization(owner: AuthCapabilityAuthorizationOwner): Boolean

    fun hasStoredCallback(owner: AuthCapabilityAuthorizationOwner): Boolean

    fun cancelPendingAuthorization(owner: AuthCapabilityAuthorizationOwner)

    suspend fun completePendingAuthorization(
        owner: AuthCapabilityAuthorizationOwner,
    ): AuthCapabilityAuthorizationResult

    suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult

    // AUTH17_SESSION_BOUND_BEARER_V1: the provider for a client built with this
    // capability's token. The configured capabilities return the account
    // coordinator's session-bound one, so a 401 marks that token expired
    // (AUTH11: the refresh token is kept) and the next use refreshes it.
    fun bearerTokenProvider(accessToken: String): NetworkBearerTokenProvider =
        NetworkBearerTokenProvider { accessToken }
}

internal interface AuthContextualCapabilityAuthorizer : AuthCapabilityAuthorizer {
    suspend fun beginAuthorization(
        accountStableId: String,
        owner: AuthCapabilityAuthorizationOwner,
        contextKey: String?,
    ): String

    fun hasPendingAuthorization(
        owner: AuthCapabilityAuthorizationOwner,
        contextKey: String?,
    ): Boolean

    fun hasStoredCallback(
        owner: AuthCapabilityAuthorizationOwner,
        contextKey: String?,
    ): Boolean

    fun cancelPendingAuthorization(
        owner: AuthCapabilityAuthorizationOwner,
        contextKey: String?,
    )

    suspend fun completePendingAuthorization(
        owner: AuthCapabilityAuthorizationOwner,
        contextKey: String?,
    ): AuthCapabilityAuthorizationResult
}

internal class ConfiguredAuthCapabilityAuthorizer(
    private val configuration: OAuthProviderConfiguration,
    private val store: EncryptedAuthSessionStore,
    private val tokenClient: OAuthTokenEndpointClient,
    private val identityClient: OAuthAccountIdentityClient,
    private val accountCoordinator: AuthAccountSessionCoordinator,
    private val clock: AuthClock = SystemAuthClock,
    private val stateGenerator: OAuthStateGenerator = SecureOAuthStateGenerator(),
) : AuthContextualCapabilityAuthorizer {
    override fun bearerTokenProvider(accessToken: String): NetworkBearerTokenProvider =
        accountCoordinator.sessionBoundBearerTokenProvider(accessToken)

    override suspend fun beginAuthorization(
        accountStableId: String,
        owner: AuthCapabilityAuthorizationOwner,
    ): String =
        beginAuthorization(
            accountStableId = accountStableId,
            owner = owner,
            contextKey = null,
        )

    override suspend fun beginAuthorization(
        accountStableId: String,
        owner: AuthCapabilityAuthorizationOwner,
        contextKey: String?,
    ): String {
        val pinnedAccountId = accountStableId.trim()
        require(pinnedAccountId.isNotEmpty()) {
            "Account stable ID cannot be blank"
        }
        require(accountCoordinator.activeAuthenticatedAccountId() == pinnedAccountId) {
            "Capability authorization must start from the active authenticated account"
        }
        val normalizedContextKey =
            normalizeAuthCapabilityAuthorizationContextKey(contextKey)
        return manager(
            accountStableId = pinnedAccountId,
            owner = owner,
            contextKey = normalizedContextKey,
        ).beginAuthorization()
    }

    override fun hasPendingAuthorization(
        owner: AuthCapabilityAuthorizationOwner,
    ): Boolean = hasPendingAuthorization(owner, contextKey = null)

    override fun hasPendingAuthorization(
        owner: AuthCapabilityAuthorizationOwner,
        contextKey: String?,
    ): Boolean =
        runCatching(store::pendingAuthorization)
            .getOrNull()
            ?.matchesConfiguration(owner, contextKey)
            ?: false

    override fun hasStoredCallback(
        owner: AuthCapabilityAuthorizationOwner,
    ): Boolean = hasStoredCallback(owner, contextKey = null)

    override fun hasStoredCallback(
        owner: AuthCapabilityAuthorizationOwner,
        contextKey: String?,
    ): Boolean =
        runCatching(store::callback)
            .getOrNull()
            ?.matchesConfiguration(owner, contextKey)
            ?: false

    override fun cancelPendingAuthorization(
        owner: AuthCapabilityAuthorizationOwner,
    ) {
        cancelPendingAuthorization(owner, contextKey = null)
    }

    override fun cancelPendingAuthorization(
        owner: AuthCapabilityAuthorizationOwner,
        contextKey: String?,
    ) {
        val ownsPending = hasPendingAuthorization(owner, contextKey)
        val ownsCallback = hasStoredCallback(owner, contextKey)
        if (ownsPending) {
            runCatching(store::deletePendingAuthorization)
        }
        if (ownsCallback) {
            runCatching(store::deleteCallback)
        }
        if (ownsPending || ownsCallback) {
            cleanupUnboundSession()
        }
    }

    override suspend fun completePendingAuthorization(
        owner: AuthCapabilityAuthorizationOwner,
    ): AuthCapabilityAuthorizationResult =
        completePendingAuthorization(owner, contextKey = null)

    override suspend fun completePendingAuthorization(
        owner: AuthCapabilityAuthorizationOwner,
        contextKey: String?,
    ): AuthCapabilityAuthorizationResult {
        val callback = runCatching(store::callback).getOrNull()
            ?: return failed(
                AuthCapabilityAuthorizationFailureKind.CALLBACK,
                retryable = true,
            )

        if (!callback.matchesConfiguration(owner, contextKey)) {
            return AuthCapabilityAuthorizationResult.Failed(
                AuthCapabilityAuthorizationFailureKind.CALLBACK,
            )
        }

        val pinnedAccountId = callback.accountStableId
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return failed(AuthCapabilityAuthorizationFailureKind.CALLBACK)

        val exchanged = when (
            val result = manager(
                accountStableId = pinnedAccountId,
                owner = owner,
                contextKey = normalizeAuthCapabilityAuthorizationContextKey(contextKey),
            ).completePendingCallback()
        ) {
            is AuthSessionResult.Authenticated -> result.session
            is AuthSessionResult.EndpointFailure -> return failed(
                kind = AuthCapabilityAuthorizationFailureKind.TOKEN_EXCHANGE,
                retryable = when (val endpoint = result.result) {
                    is OAuthEndpointResult.HttpFailure -> endpoint.retryable
                    is OAuthEndpointResult.TransportFailure -> endpoint.retryable
                    is OAuthEndpointResult.ProtocolFailure -> false
                    is OAuthEndpointResult.Success -> false
                },
            )
            is AuthSessionResult.AuthorizationDenied ->
                return failed(AuthCapabilityAuthorizationFailureKind.CALLBACK)
            AuthSessionResult.StorageFailure ->
                return failed(
                    AuthCapabilityAuthorizationFailureKind.STORAGE,
                    retryable = true,
                )
            AuthSessionResult.NoPendingCallback ->
                return failed(
                    AuthCapabilityAuthorizationFailureKind.CALLBACK,
                    retryable = true,
                )
            AuthSessionResult.ClientConfigurationMismatch ->
                return failed(AuthCapabilityAuthorizationFailureKind.SCOPE)
            AuthSessionResult.ReauthenticationRequired ->
                return failed(AuthCapabilityAuthorizationFailureKind.SCOPE)
        }

        val identity = when (val resolved = identityClient.resolve(exchanged)) {
            is AuthIdentityResult.Success -> resolved.identity
            is AuthIdentityResult.Failure -> {
                accountCoordinator.recordUnshownFailure("capability_identity", resolved.details)
                cleanupUnboundSession()
                return failed(
                    AuthCapabilityAuthorizationFailureKind.IDENTITY,
                    retryable = resolved.retryable,
                )
            }
        }

        val normalizedIdentity = AuthAccountIdentity(
            providerAccountId = identity.providerAccountId.trim(),
            username = identity.username.trim(),
        )

        val upgraded = exchanged.copy(identity = normalizedIdentity)
        // AUTH10: the new token must cover everything requested (the account's
        // current scopes included), or it would drop grants when it replaces the session.
        if (!upgraded.tokens.grants(configuration.requestingCurrentScopesOf(store, pinnedAccountId).scopes)) {
            cleanupUnboundSession()
            return failed(AuthCapabilityAuthorizationFailureKind.SCOPE)
        }

        // SAFETY01A32_SAME_IDENTITY_CAPABILITY_ACCOUNT_REBIND_V1
        val boundAccountStableId =
            when {
                accountCoordinator.replaceAuthenticatedSessionForCapability(
                    accountStableId = pinnedAccountId,
                    session = upgraded,
                    requiredScopes = configuration.scopes,
                ) -> pinnedAccountId

                else -> {
                    val activeAccountStableId =
                        accountCoordinator.activeAuthenticatedAccountId()
                            ?.trim()
                            ?.takeIf(String::isNotEmpty)
                    if (
                        activeAccountStableId != null &&
                        activeAccountStableId != pinnedAccountId &&
                        accountCoordinator.replaceAuthenticatedSessionForCapability(
                            accountStableId = activeAccountStableId,
                            session = upgraded,
                            requiredScopes = configuration.scopes,
                        )
                    ) {
                        activeAccountStableId
                    } else {
                        cleanupUnboundSession()
                        return failed(AuthCapabilityAuthorizationFailureKind.ACCOUNT)
                    }
                }
            }
        accountCoordinator.webSessionSignedInAs(boundAccountStableId)

        return AuthCapabilityAuthorizationResult.Upgraded(
            accountStableId = boundAccountStableId,
            username = normalizedIdentity.username,
            owner = owner,
            session = upgraded,
        )
    }

    override suspend fun ensureAuthorized(
        accountStableId: String,
    ): AuthSessionResult {
        val pinnedAccountStableId = accountStableId.trim()
        val storedSession = runCatching {
            store.session(pinnedAccountStableId)
        }.getOrNull()

        // MOD01A21_INCREMENTAL_SCOPE_DOES_NOT_INVALIDATE_ACCOUNT_V3
        // Missing an optional capability scope is not an invalid account session.
        // Keep the base account connected and let the feature request the
        // incremental grant through its owner-bound authorization flow.
        if (
            storedSession != null &&
            storedSession.provider == configuration.provider &&
            storedSession.clientMode == configuration.clientMode &&
            storedSession.clientIdFingerprint.isNotEmpty() &&
            constantTimeEquals(
                storedSession.clientIdFingerprint,
                configuration.clientIdFingerprint,
            ) &&
            !storedSession.tokens.grants(configuration.scopes)
        ) {
            return AuthSessionResult.AuthorizationDenied(
                error = "missing_incremental_scope",
            )
        }

        return accountCoordinator.ensureAccessTokenForAccount(
            accountStableId = pinnedAccountStableId,
            requiredScopes = configuration.scopes,
        )
    }

    private fun PendingOAuthAuthorization.matchesConfiguration(
        owner: AuthCapabilityAuthorizationOwner,
        contextKey: String?,
    ): Boolean =
        accountStableId?.isNotBlank() == true &&
            provider == configuration.provider &&
            clientMode == configuration.clientMode &&
            clientIdFingerprint.isNotEmpty() &&
            scopeFingerprint.isNotEmpty() &&
            constantTimeEquals(
                clientIdFingerprint,
                configuration.clientIdFingerprint,
            ) &&
            constantTimeEquals(
                scopeFingerprint,
                configuration.requestingCurrentScopesOf(store, accountStableId).scopeFingerprint,
            ) &&
            authorizationOwnerKey == owner.wireKey &&
            authorizationContextKey ==
                normalizeAuthCapabilityAuthorizationContextKey(contextKey)

    private fun StoredOAuthCallback.matchesConfiguration(
        owner: AuthCapabilityAuthorizationOwner,
        contextKey: String?,
    ): Boolean =
        accountStableId?.isNotBlank() == true &&
            provider == configuration.provider &&
            clientMode == configuration.clientMode &&
            clientIdFingerprint.isNotEmpty() &&
            scopeFingerprint.isNotEmpty() &&
            constantTimeEquals(
                clientIdFingerprint,
                configuration.clientIdFingerprint,
            ) &&
            constantTimeEquals(
                scopeFingerprint,
                configuration.requestingCurrentScopesOf(store, accountStableId).scopeFingerprint,
            ) &&
            authorizationOwnerKey == owner.wireKey &&
            authorizationContextKey ==
                normalizeAuthCapabilityAuthorizationContextKey(contextKey)

    private fun manager(
        accountStableId: String,
        owner: AuthCapabilityAuthorizationOwner,
        contextKey: String?,
    ): OAuthSessionManager = OAuthSessionManager(
        configuration = configuration.requestingCurrentScopesOf(store, accountStableId),
        store = store,
        tokenClient = tokenClient,
        clock = clock,
        stateGenerator = stateGenerator,
        authorizationAccountStableId = accountStableId,
        authorizationOwnerKey = owner.wireKey,
        authorizationContextKey =
            normalizeAuthCapabilityAuthorizationContextKey(contextKey),
    )

    private fun cleanupUnboundSession() {
        runCatching(store::deleteSession)
    }

    private fun failed(
        kind: AuthCapabilityAuthorizationFailureKind,
        retryable: Boolean = false,
    ): AuthCapabilityAuthorizationResult.Failed {
        cleanupUnboundSession()
        return AuthCapabilityAuthorizationResult.Failed(
            kind = kind,
            retryable = retryable,
        )
    }
}

internal data class AuthCapabilityAuthorizationRoute(
    val owner: AuthCapabilityAuthorizationOwner,
    val contextKey: String? = null,
)

internal enum class AuthCapabilityAuthorizationRoutingKind {
    NONE,
    LEGACY_OWNERLESS,
    OWNED,
    INVALID,
}

internal data class AuthCapabilityAuthorizationResolution(
    val kind: AuthCapabilityAuthorizationRoutingKind,
    val route: AuthCapabilityAuthorizationRoute? = null,
) {
    init {
        require(
            (kind == AuthCapabilityAuthorizationRoutingKind.OWNED) ==
                (route != null),
        ) {
            "Owned authorization routing requires exactly one route"
        }
    }
}

internal data class AuthCapabilityAuthorizationRouteMetadata(
    val ownerKey: String?,
    val contextKey: String?,
)

internal fun resolveAuthCapabilityAuthorizationRouting(
    records: List<AuthCapabilityAuthorizationRouteMetadata>,
): AuthCapabilityAuthorizationResolution {
    if (records.isEmpty()) {
        return AuthCapabilityAuthorizationResolution(
            kind = AuthCapabilityAuthorizationRoutingKind.NONE,
        )
    }

    if (
        records.all { metadata ->
            metadata.ownerKey == null &&
                metadata.contextKey == null
        }
    ) {
        return AuthCapabilityAuthorizationResolution(
            kind = AuthCapabilityAuthorizationRoutingKind.LEGACY_OWNERLESS,
        )
    }

    if (records.any { metadata -> metadata.ownerKey == null }) {
        return AuthCapabilityAuthorizationResolution(
            kind = AuthCapabilityAuthorizationRoutingKind.INVALID,
        )
    }

    val routes = mutableListOf<AuthCapabilityAuthorizationRoute>()

    for (metadata in records) {
        val owner =
            runCatching {
                AuthCapabilityAuthorizationOwner.fromWireKey(
                    metadata.ownerKey,
                )
            }.getOrNull()
                ?: return AuthCapabilityAuthorizationResolution(
                    kind = AuthCapabilityAuthorizationRoutingKind.INVALID,
                )

        val contextKey =
            if (metadata.contextKey == null) {
                null
            } else {
                runCatching {
                    normalizeAuthCapabilityAuthorizationContextKey(
                        metadata.contextKey,
                    )
                }.getOrNull()
                    ?: return AuthCapabilityAuthorizationResolution(
                        kind = AuthCapabilityAuthorizationRoutingKind.INVALID,
                    )
            }

        routes += AuthCapabilityAuthorizationRoute(
            owner = owner,
            contextKey = contextKey,
        )
    }

    val route = routes.first()

    if (routes.any { candidate -> candidate != route }) {
        return AuthCapabilityAuthorizationResolution(
            kind = AuthCapabilityAuthorizationRoutingKind.INVALID,
        )
    }

    return AuthCapabilityAuthorizationResolution(
        kind = AuthCapabilityAuthorizationRoutingKind.OWNED,
        route = route,
    )
}

internal class AuthCapabilityAuthorizationOwnerResolver(
    private val store: EncryptedAuthSessionStore,
) {
    fun currentResolution(): AuthCapabilityAuthorizationResolution {
        val pendingResult =
            runCatching(store::pendingAuthorization)
        val callbackResult =
            runCatching(store::callback)

        if (pendingResult.isFailure || callbackResult.isFailure) {
            return AuthCapabilityAuthorizationResolution(
                kind = AuthCapabilityAuthorizationRoutingKind.INVALID,
            )
        }

        val records =
            buildList {
                pendingResult.getOrNull()?.let { pending ->
                    add(
                        AuthCapabilityAuthorizationRouteMetadata(
                            ownerKey = pending.authorizationOwnerKey,
                            contextKey = pending.authorizationContextKey,
                        ),
                    )
                }

                callbackResult.getOrNull()?.let { callback ->
                    add(
                        AuthCapabilityAuthorizationRouteMetadata(
                            ownerKey = callback.authorizationOwnerKey,
                            contextKey = callback.authorizationContextKey,
                        ),
                    )
                }
            }

        return resolveAuthCapabilityAuthorizationRouting(records)
    }
}
