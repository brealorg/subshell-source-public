package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.account.RedditBlockedAccount
import app.subshell.prototype.data.account.normalizeRedditSafetyAccountFullname
import app.subshell.prototype.data.account.normalizeRedditSafetyUsername
import app.subshell.prototype.data.network.JsonGetRequest
import app.subshell.prototype.data.network.NetworkProtocolFailureKind
import app.subshell.prototype.data.network.NetworkResponseMetadata
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.NetworkTransportFailureKind
import app.subshell.prototype.data.network.SubshellHttpClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class RedditBlockedAccountsListingDto(
    val kind: String = "",
    val data: RedditBlockedAccountsDataDto = RedditBlockedAccountsDataDto(),
)

@Serializable
internal data class RedditBlockedAccountsDataDto(
    val children: List<RedditBlockedAccountDto> = emptyList(),
    val after: String? = null,
)

@Serializable
internal data class RedditBlockedAccountDto(
    val date: Double? = null,
    @SerialName("rel_id") val relationshipId: String? = null,
    val name: String = "",
    val id: String = "",
)

internal data class RedditBlockedAccountsPage(
    val items: List<RedditBlockedAccount>,
    val nextCursor: String?,
)

internal sealed interface RedditBlockedAccountsNetworkResult {
    data class Loaded(
        val page: RedditBlockedAccountsPage,
        val metadata: NetworkResponseMetadata,
    ) : RedditBlockedAccountsNetworkResult

    data class HttpFailure(
        val statusCode: Int,
        val retryable: Boolean,
        val metadata: NetworkResponseMetadata,
        val providerReason: String? = null,
    ) : RedditBlockedAccountsNetworkResult

    data class TransportFailure(
        val kind: NetworkTransportFailureKind,
        val retryable: Boolean,
    ) : RedditBlockedAccountsNetworkResult

    data class ProtocolFailure(
        val kind: NetworkProtocolFailureKind,
        val jsonPath: String? = null,
    ) : RedditBlockedAccountsNetworkResult

    data object MalformedPayload : RedditBlockedAccountsNetworkResult
}

// USERACTION01A2_PREFS_BLOCKED_AUTHORITATIVE_TRANSPORT_V1
internal class RedditBlockedAccountsNetworkDataSource(
    private val httpClient: SubshellHttpClient,
) {
    suspend fun load(
        after: String? = null,
    ): RedditBlockedAccountsNetworkResult = when (
        val result = httpClient.getJson(
            request = JsonGetRequest(
                pathSegments = listOf("prefs", "blocked"),
                queryParameters = buildList {
                    add("limit" to BLOCKED_ACCOUNTS_PAGE_SIZE.toString())
                    add("raw_json" to "1")
                    after
                        ?.trim()
                        ?.takeIf(String::isNotEmpty)
                        ?.let { cursor -> add("after" to cursor) }
                },
            ),
            deserializer = RedditBlockedAccountsListingDto.serializer(),
        )
    ) {
        is NetworkResult.Success -> {
            if (!result.value.kind.equals("UserList", ignoreCase = true)) {
                RedditBlockedAccountsNetworkResult.MalformedPayload
            } else {
                val items = result.value.data.children.mapNotNull(::toBlockedAccountOrNull)
                RedditBlockedAccountsNetworkResult.Loaded(
                    page = RedditBlockedAccountsPage(
                        items = items.distinctBy { it.username.lowercase() },
                        nextCursor = result.value.data.after
                            ?.trim()
                            ?.takeIf(String::isNotEmpty),
                    ),
                    metadata = result.metadata,
                )
            }
        }

        is NetworkResult.HttpFailure ->
            RedditBlockedAccountsNetworkResult.HttpFailure(
                statusCode = result.statusCode,
                retryable = result.retryable,
                metadata = result.metadata,
                providerReason = result.providerReason,
            )

        is NetworkResult.TransportFailure ->
            RedditBlockedAccountsNetworkResult.TransportFailure(
                kind = result.kind,
                retryable = result.retryable,
            )

        is NetworkResult.ProtocolFailure ->
            RedditBlockedAccountsNetworkResult.ProtocolFailure(result.kind, result.jsonPath)
    }
}

private fun toBlockedAccountOrNull(
    dto: RedditBlockedAccountDto,
): RedditBlockedAccount? = try {
    val username = normalizeRedditSafetyUsername(dto.name)
    val fullname = normalizeRedditSafetyAccountFullname(dto.id)
    val relationshipId = dto.relationshipId
        ?.trim()
        ?.takeIf { value ->
            value.startsWith("r9_") &&
                value.length <= RELATIONSHIP_ID_MAX_CHARS &&
                value.drop(3).all(Char::isLetterOrDigit)
        }
    val blockedAt = dto.date
        ?.takeIf { it.isFinite() && it > 0.0 }
        ?.toLong()
    RedditBlockedAccount(
        username = username,
        accountFullname = fullname,
        relationshipId = relationshipId,
        blockedAtEpochSeconds = blockedAt,
    )
} catch (_: IllegalArgumentException) {
    null
}

internal const val BLOCKED_ACCOUNTS_PAGE_SIZE = 100
private const val RELATIONSHIP_ID_MAX_CHARS = 64
