package app.subshell.prototype.data.auth

import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.normalizeRedditCommunityName
import app.subshell.prototype.model.CommunityWikiIndex
import app.subshell.prototype.model.CommunityWikiPage
import app.subshell.prototype.model.normalizeCommunityWikiPagePath

// WIKI01A1_AUTHENTICATED_COMMUNITY_WIKI_DOMAIN_V1
internal enum class AuthenticatedRedditCommunityWikiFailureKind {
    CLIENT_CONFIGURATION,
    SESSION,
    HTTP,
    TRANSPORT,
    PROTOCOL,
    UNAVAILABLE,
}

@ConsistentCopyVisibility
internal data class AuthenticatedRedditCommunityWikiTarget private constructor(
    val communityName: String,
    val pagePath: String?,
) {
    val isIndex: Boolean
        get() = pagePath == null

    val requestKey: String
        get() = if (pagePath == null) {
            "community-wiki:$communityName:index"
        } else {
            "community-wiki:$communityName:$pagePath"
        }

    companion object {
        fun index(
            rawCommunityName: String,
        ): AuthenticatedRedditCommunityWikiTarget =
            AuthenticatedRedditCommunityWikiTarget(
                communityName = normalizeRedditCommunityName(rawCommunityName),
                pagePath = null,
            )

        fun page(
            rawCommunityName: String,
            rawPagePath: String,
        ): AuthenticatedRedditCommunityWikiTarget =
            AuthenticatedRedditCommunityWikiTarget(
                communityName = normalizeRedditCommunityName(rawCommunityName),
                pagePath = normalizeCommunityWikiPagePath(rawPagePath),
            )
    }
}

internal sealed interface AuthenticatedRedditCommunityWikiResult {
    data class IndexLoaded(
        val index: CommunityWikiIndex,
    ) : AuthenticatedRedditCommunityWikiResult

    data class PageLoaded(
        val page: CommunityWikiPage,
    ) : AuthenticatedRedditCommunityWikiResult

    data object AuthorizationRequired : AuthenticatedRedditCommunityWikiResult

    data class Failed(
        val kind: AuthenticatedRedditCommunityWikiFailureKind,
        val retryable: Boolean,
        val details: FailureDetails? = null,
        val httpStatusCode: Int? = null,
    ) : AuthenticatedRedditCommunityWikiResult
}

internal interface AuthenticatedRedditCommunityWikiLoader {
    suspend fun load(
        accountStableId: String,
        target: AuthenticatedRedditCommunityWikiTarget,
    ): AuthenticatedRedditCommunityWikiResult
}

// WIKI01A2_COMMUNITY_AUTHORIZATION_CONTEXT_V1
internal fun communityWikiAuthorizationContextKey(
    rawCommunityName: String,
): String {
    val normalized = normalizeRedditCommunityName(rawCommunityName)
    val bytes = normalized.toByteArray(Charsets.UTF_8)
    val digits = "0123456789abcdef"
    return buildString(5 + bytes.size * 2) {
        append("wiki_")
        bytes.forEach { byte ->
            val value = byte.toInt() and 0xff
            append(digits[value ushr 4])
            append(digits[value and 0x0f])
        }
    }
}

internal fun communityWikiCommunityFromAuthorizationContextKey(
    contextKey: String?,
): String? {
    val encoded = contextKey
        ?.trim()
        ?.lowercase()
        ?.takeIf { value -> value.startsWith("wiki_") }
        ?.removePrefix("wiki_")
        ?: return null

    if (encoded.isEmpty() || encoded.length % 2 != 0) return null

    val bytes = ByteArray(encoded.length / 2)
    for (index in bytes.indices) {
        val high = encoded[index * 2].digitToIntOrNull(16) ?: return null
        val low = encoded[index * 2 + 1].digitToIntOrNull(16) ?: return null
        bytes[index] = ((high shl 4) or low).toByte()
    }

    return runCatching {
        normalizeRedditCommunityName(String(bytes, Charsets.UTF_8))
    }.getOrNull()
}
