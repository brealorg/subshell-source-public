package app.subshell.prototype.data.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlinx.serialization.Serializable

@Serializable
internal enum class OAuthProvider {
    REDDIT,
}

@Serializable
internal enum class OAuthClientMode {
    NATIVE_PRODUCTION,
    REDREADER_COMPATIBILITY,
}

@Serializable
internal data class AuthAccountIdentity(
    val providerAccountId: String,
    val username: String,
)

@Serializable
internal data class OAuthTokenSet(
    val accessToken: String,
    val refreshToken: String?,
    val tokenType: String,
    val scope: String,
    val expiresAtEpochMs: Long,
) {
    init {
        require(accessToken.isNotBlank()) { "OAuth access token cannot be blank" }
        require(tokenType.equals("bearer", ignoreCase = true)) {
            "Only bearer OAuth tokens are supported"
        }
        require(expiresAtEpochMs > 0L) { "OAuth token expiry must be positive" }
    }

    fun isUsableAt(nowEpochMs: Long, refreshSkewMs: Long): Boolean =
        accessToken.isNotBlank() && nowEpochMs + refreshSkewMs < expiresAtEpochMs

    fun grants(requiredScopes: Set<String>): Boolean =
        normalizedOAuthScopes(scope).containsAll(normalizeOAuthScopes(requiredScopes))
}

internal fun normalizeOAuthScopes(scopes: Iterable<String>): Set<String> = scopes
    .map(String::trim)
    .filter(String::isNotEmpty)
    .toCollection(sortedSetOf())

internal fun normalizedOAuthScopes(scope: String): Set<String> = scope
    .split(Regex("[\\s,]+"))
    .let(::normalizeOAuthScopes)

@Serializable
internal data class StoredOAuthSession(
    val provider: OAuthProvider,
    val tokens: OAuthTokenSet,
    val identity: AuthAccountIdentity? = null,
    val clientMode: OAuthClientMode = OAuthClientMode.NATIVE_PRODUCTION,
    val clientIdFingerprint: String = "",
)

@Serializable
internal data class StoredAuthAccountRecord(
    val accountId: String,
    val provider: OAuthProvider,
    val providerAccountId: String,
    val username: String,
    val createdAtEpochMs: Long,
    val lastUsedAtEpochMs: Long,
    val clientMode: OAuthClientMode = OAuthClientMode.NATIVE_PRODUCTION,
)

@Serializable
internal data class StoredAuthAccountRegistry(
    val accounts: List<StoredAuthAccountRecord>,
    val activeAccountId: String?,
)

@Serializable
internal data class PendingOAuthAuthorization(
    val provider: OAuthProvider,
    val state: String,
    val redirectUri: String,
    val createdAtEpochMs: Long,
    val clientMode: OAuthClientMode = OAuthClientMode.NATIVE_PRODUCTION,
    val clientIdFingerprint: String = "",
    val scopeFingerprint: String = "",
    val accountStableId: String? = null,
    val authorizationOwnerKey: String? = null,
    val authorizationContextKey: String? = null,
)

@Serializable
internal data class StoredOAuthCallback(
    val provider: OAuthProvider,
    val state: String,
    val code: String?,
    val error: String?,
    val receivedAtEpochMs: Long,
    val clientMode: OAuthClientMode = OAuthClientMode.NATIVE_PRODUCTION,
    val clientIdFingerprint: String = "",
    val scopeFingerprint: String = "",
    val accountStableId: String? = null,
    val authorizationOwnerKey: String? = null,
    val authorizationContextKey: String? = null,
)

@Serializable
internal data class StoredOAuthClientSelection(
    val provider: OAuthProvider,
    val mode: OAuthClientMode,
    val clientId: String? = null,
) {
    init {
        when (mode) {
            OAuthClientMode.NATIVE_PRODUCTION ->
                require(clientId == null) { "Native OAuth selection cannot carry a local client ID" }
            OAuthClientMode.REDREADER_COMPATIBILITY -> {
                val value = requireNotNull(clientId) {
                    "RedReader compatibility requires a local client ID"
                }
                require(value == value.trim()) {
                    "OAuth client ID cannot contain surrounding whitespace"
                }
                require(value.matches(Regex("[A-Za-z0-9_-]{8,128}"))) {
                    "OAuth client ID must use 8-128 URL-safe identifier characters"
                }
            }
        }
    }
}

internal fun interface AuthClock {
    fun epochMillis(): Long
}

internal object SystemAuthClock : AuthClock {
    override fun epochMillis(): Long = System.currentTimeMillis()
}

internal fun interface OAuthStateGenerator {
    fun generate(): String
}

internal class SecureOAuthStateGenerator(
    private val secureRandom: SecureRandom = SecureRandom(),
) : OAuthStateGenerator {
    override fun generate(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}

internal fun constantTimeEquals(left: String, right: String): Boolean =
    MessageDigest.isEqual(
        left.toByteArray(Charsets.UTF_8),
        right.toByteArray(Charsets.UTF_8),
    )

internal enum class AuthCallbackFailureKind {
    NO_PENDING_AUTHORIZATION,
    PENDING_AUTHORIZATION_EXPIRED,
    REDIRECT_URI_MISMATCH,
    STATE_MISMATCH,
    MALFORMED_CALLBACK,
    STORAGE_FAILURE,
}

internal sealed interface AuthCallbackResult {
    data class Accepted(
        val provider: OAuthProvider,
        val denied: Boolean,
    ) : AuthCallbackResult

    data class Rejected(
        val kind: AuthCallbackFailureKind,
    ) : AuthCallbackResult
}

internal enum class OAuthProtocolFailureKind {
    EMPTY_BODY,
    UNEXPECTED_CONTENT_TYPE,
    BODY_TOO_LARGE,
    MALFORMED_JSON,
    UNSUPPORTED_TOKEN_TYPE,
    INVALID_EXPIRY,
    MISSING_ACCESS_TOKEN,
}

internal sealed interface OAuthEndpointResult<out T> {
    data class Success<T>(val value: T) : OAuthEndpointResult<T>

    data class HttpFailure(
        val statusCode: Int,
        val retryable: Boolean,
        /** AUTH11: the answer was an OAuth JSON body, with its `error` value if any. */
        val jsonBody: Boolean = false,
        val oauthError: String? = null,
    ) : OAuthEndpointResult<Nothing>

    data class TransportFailure(
        val retryable: Boolean,
    ) : OAuthEndpointResult<Nothing>

    data class ProtocolFailure(
        val kind: OAuthProtocolFailureKind,
    ) : OAuthEndpointResult<Nothing>
}

internal sealed interface AuthSessionResult {
    data class Authenticated(
        val session: StoredOAuthSession,
    ) : AuthSessionResult

    data class AuthorizationDenied(
        val error: String,
    ) : AuthSessionResult

    data class EndpointFailure(
        val result: OAuthEndpointResult<Nothing>,
    ) : AuthSessionResult

    data object NoPendingCallback : AuthSessionResult

    data object ClientConfigurationMismatch : AuthSessionResult

    data object ReauthenticationRequired : AuthSessionResult

    data object StorageFailure : AuthSessionResult
}

internal data class AuthLogoutResult(
    val localSessionCleared: Boolean,
    val remoteRevocationSucceeded: Boolean,
    // ACCOUNT01_REACTION_CACHE_ACCOUNT_SCOPED_V1: the account that left the
    // registry, so its account-scoped local state can go with it.
    val removedAccountStableId: String? = null,
)

/**
 * LOW1_ONLY_A_MISSING_GRANT_ASKS_TO_AUTHORIZE_V1 (OCB-497/506/508): only a
 * session that must be signed in again or a denied/missing grant means "ask
 * the user to authorize"; a storage, network or configuration failure is a
 * plain failure (often worth a retry), not a reason to send the user to
 * Reddit's consent page.
 */
internal val AuthSessionResult.needsAuthorization: Boolean
    get() = this is AuthSessionResult.ReauthenticationRequired ||
        this is AuthSessionResult.AuthorizationDenied
