package app.subshell.prototype.data.auth

import java.security.MessageDigest
import java.util.Base64
import okhttp3.HttpUrl

internal data class OAuthProviderConfiguration(
    val provider: OAuthProvider,
    val authorizationEndpoint: HttpUrl,
    val tokenEndpoint: HttpUrl,
    val revokeEndpoint: HttpUrl,
    val clientId: String,
    val clientSecret: String = "",
    val redirectUri: String,
    val scopes: Set<String>,
    val permanentAccess: Boolean,
    val clientMode: OAuthClientMode = OAuthClientMode.NATIVE_PRODUCTION,
) {
    init {
        require(authorizationEndpoint.isHttps) { "OAuth authorization endpoint must use HTTPS" }
        require(tokenEndpoint.isHttps) { "OAuth token endpoint must use HTTPS" }
        require(revokeEndpoint.isHttps) { "OAuth revoke endpoint must use HTTPS" }
        require(clientId.isNotBlank()) { "OAuth client ID cannot be blank" }
        require(redirectUri.isNotBlank()) { "OAuth redirect URI cannot be blank" }
        require(scopes.isNotEmpty()) { "At least one OAuth scope is required" }
        require(scopes.all { scope -> scope.isNotBlank() && scope.none(Char::isWhitespace) }) {
            "OAuth scopes must be non-blank single tokens"
        }
    }

    val clientIdFingerprint: String
        get() = oauthClientIdFingerprint(clientId)

    val scopeFingerprint: String
        get() = oauthScopeFingerprint(scopes)

    fun authorizationUrl(state: String): HttpUrl = authorizationEndpoint.newBuilder()
        .addQueryParameter("client_id", clientId)
        .addQueryParameter("response_type", "code")
        .addQueryParameter("state", state)
        .addQueryParameter("redirect_uri", redirectUri)
        .addQueryParameter("duration", if (permanentAccess) "permanent" else "temporary")
        .addQueryParameter("scope", scopes.sorted().joinToString(" "))
        .build()
}

internal fun oauthClientIdFingerprint(clientId: String): String {
    val normalized = clientId.trim()
    require(normalized.isNotEmpty()) { "OAuth client ID cannot be blank" }
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(normalized.toByteArray(Charsets.UTF_8))
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
}

internal fun oauthScopeFingerprint(scopes: Set<String>): String {
    val normalized = normalizeOAuthScopes(scopes).joinToString(" ")
    require(normalized.isNotEmpty()) { "OAuth scope fingerprint cannot be empty" }
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(normalized.toByteArray(Charsets.UTF_8))
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
}
