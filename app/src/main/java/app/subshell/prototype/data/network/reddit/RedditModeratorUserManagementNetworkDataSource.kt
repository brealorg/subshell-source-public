package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.account.MODERATOR_BAN_NOTE_MAX_CHARS
import app.subshell.prototype.data.account.MODERATOR_MUTE_NOTE_MAX_CHARS
import app.subshell.prototype.data.account.ModeratorUserManagementFailureKind
import app.subshell.prototype.data.account.RedditApprovedUser
import app.subshell.prototype.data.account.RedditBannedUser
import app.subshell.prototype.data.account.RedditMutedUser
import app.subshell.prototype.data.account.RedditModeratorUserApprovalRequest
import app.subshell.prototype.data.account.RedditModeratorUserBanRequest
import app.subshell.prototype.data.account.RedditModeratorUserMuteRequest
import app.subshell.prototype.data.account.normalizeRedditSafetyUsername
import app.subshell.prototype.data.account.normalized
import app.subshell.prototype.data.error.isRedditWriteOutcomeUnknownStatus
import app.subshell.prototype.data.network.FormPostRequest
import app.subshell.prototype.data.network.JsonGetRequest
import app.subshell.prototype.data.network.NetworkPage
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.map
import app.subshell.prototype.data.normalizeRedditCommunityName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

// MODERATION02A5A1_CANONICAL_BANNED_USER_TRANSPORT_V1
internal class RedditModeratorUserManagementNetworkDataSource(
    private val httpClient: SubshellHttpClient,
) {
    suspend fun bannedUsersPage(
        rawCommunityName: String,
        after: String?,
        username: String? = null,
    ): NetworkResult<NetworkPage<RedditBannedUser>> {
        val community = normalizeRedditCommunityName(rawCommunityName)
        return httpClient.getJson(
            request = redditBannedUsersRequest(community, after, username),
            deserializer = RedditBannedUsersListingDto.serializer(),
        ).map(RedditBannedUsersListingDto::toBannedUsersPage)
    }

    suspend fun mutedUsersPage(
        rawCommunityName: String,
        after: String?,
        username: String? = null,
    ): NetworkResult<NetworkPage<RedditMutedUser>> {
        val community = normalizeRedditCommunityName(rawCommunityName)
        return httpClient.getJson(
            request = redditMutedUsersRequest(community, after, username),
            deserializer = RedditMutedUsersListingDto.serializer(),
        ).map(RedditMutedUsersListingDto::toMutedUsersPage)
    }

    suspend fun approvedUsersPage(
        rawCommunityName: String,
        after: String?,
        username: String? = null,
    ): NetworkResult<NetworkPage<RedditApprovedUser>> {
        val community = normalizeRedditCommunityName(rawCommunityName)
        return httpClient.getJson(
            request = redditApprovedUsersRequest(community, after, username),
            deserializer = RedditApprovedUsersListingDto.serializer(),
        ).map(RedditApprovedUsersListingDto::toApprovedUsersPage)
    }

    suspend fun setBanned(
        request: RedditModeratorUserBanRequest,
    ): RedditModeratorUserMutationNetworkResult {
        val normalized = request.normalized()
        return if (normalized.banned) {
            when (
                val result = httpClient.postFormJson(
                    request = redditModeratorBanFormRequest(normalized),
                    deserializer = RedditModeratorUserMutationResponseDto.serializer(),
                )
            ) {
                is NetworkResult.Success -> {
                    val rawErrors = result.value.json.errors
                    if (rawErrors.isEmpty()) {
                        RedditModeratorUserMutationNetworkResult.Acknowledged
                    } else if (parseRedditContentReportApiErrors(rawErrors).isEmpty()) {
                        RedditModeratorUserMutationNetworkResult.Failed(
                            ModeratorUserManagementFailureKind.PROTOCOL,
                        )
                    } else {
                        RedditModeratorUserMutationNetworkResult.Failed(
                            kind = ModeratorUserManagementFailureKind.VALIDATION,
                            failureDetails = moderatorInviteFailureDetails(rawErrors),
                        )
                    }
                }
                is NetworkResult.HttpFailure -> RedditModeratorUserMutationNetworkResult.Failed(
                    kind = moderatorUserMutationHttpFailureKind(result.statusCode),
                    httpStatusCode = result.statusCode,
                )
                is NetworkResult.TransportFailure -> RedditModeratorUserMutationNetworkResult.Failed(
                    kind = ModeratorUserManagementFailureKind.OUTCOME_UNKNOWN,
                )
                is NetworkResult.ProtocolFailure -> RedditModeratorUserMutationNetworkResult.Failed(
                    kind = ModeratorUserManagementFailureKind.OUTCOME_UNKNOWN,
                )
            }
        } else {
            when (val result = httpClient.postFormStatus(redditModeratorUnbanFormRequest(normalized))) {
                is NetworkResult.Success -> RedditModeratorUserMutationNetworkResult.Acknowledged
                is NetworkResult.HttpFailure -> RedditModeratorUserMutationNetworkResult.Failed(
                    kind = moderatorUserMutationHttpFailureKind(result.statusCode),
                    httpStatusCode = result.statusCode,
                )
                is NetworkResult.TransportFailure -> RedditModeratorUserMutationNetworkResult.Failed(
                    kind = ModeratorUserManagementFailureKind.OUTCOME_UNKNOWN,
                )
                is NetworkResult.ProtocolFailure -> RedditModeratorUserMutationNetworkResult.Failed(
                    kind = ModeratorUserManagementFailureKind.OUTCOME_UNKNOWN,
                )
            }
        }
    }

    suspend fun setMuted(
        request: RedditModeratorUserMuteRequest,
    ): RedditModeratorUserMutationNetworkResult {
        val normalized = request.normalized()
        return if (normalized.muted) {
            when (
                val result = httpClient.postFormJson(
                    request = redditModeratorMuteFormRequest(normalized),
                    deserializer = RedditModeratorUserMutationResponseDto.serializer(),
                )
            ) {
                is NetworkResult.Success -> {
                    val rawErrors = result.value.json.errors
                    if (rawErrors.isEmpty()) {
                        RedditModeratorUserMutationNetworkResult.Acknowledged
                    } else if (parseRedditContentReportApiErrors(rawErrors).isEmpty()) {
                        RedditModeratorUserMutationNetworkResult.Failed(
                            ModeratorUserManagementFailureKind.PROTOCOL,
                        )
                    } else {
                        RedditModeratorUserMutationNetworkResult.Failed(
                            kind = ModeratorUserManagementFailureKind.VALIDATION,
                            failureDetails = moderatorInviteFailureDetails(rawErrors),
                        )
                    }
                }
                is NetworkResult.HttpFailure -> RedditModeratorUserMutationNetworkResult.Failed(
                    kind = moderatorUserMutationHttpFailureKind(result.statusCode),
                    httpStatusCode = result.statusCode,
                )
                is NetworkResult.TransportFailure -> RedditModeratorUserMutationNetworkResult.Failed(
                    kind = ModeratorUserManagementFailureKind.OUTCOME_UNKNOWN,
                )
                is NetworkResult.ProtocolFailure -> RedditModeratorUserMutationNetworkResult.Failed(
                    kind = ModeratorUserManagementFailureKind.OUTCOME_UNKNOWN,
                )
            }
        } else {
            when (
                val result = httpClient.postFormStatus(
                    redditModeratorUnmuteFormRequest(normalized),
                )
            ) {
                is NetworkResult.Success -> RedditModeratorUserMutationNetworkResult.Acknowledged
                is NetworkResult.HttpFailure -> RedditModeratorUserMutationNetworkResult.Failed(
                    kind = moderatorUserMutationHttpFailureKind(result.statusCode),
                    httpStatusCode = result.statusCode,
                )
                is NetworkResult.TransportFailure -> RedditModeratorUserMutationNetworkResult.Failed(
                    kind = ModeratorUserManagementFailureKind.OUTCOME_UNKNOWN,
                )
                is NetworkResult.ProtocolFailure -> RedditModeratorUserMutationNetworkResult.Failed(
                    kind = ModeratorUserManagementFailureKind.OUTCOME_UNKNOWN,
                )
            }
        }
    }

    suspend fun setApproved(
        request: RedditModeratorUserApprovalRequest,
    ): RedditModeratorUserMutationNetworkResult {
        val normalized = request.normalized()
        return if (normalized.approved) {
            when (
                val result = httpClient.postFormJson(
                    request = redditModeratorApproveUserFormRequest(normalized),
                    deserializer = RedditModeratorUserMutationResponseDto.serializer(),
                )
            ) {
                is NetworkResult.Success -> {
                    val rawErrors = result.value.json.errors
                    if (rawErrors.isEmpty()) {
                        RedditModeratorUserMutationNetworkResult.Acknowledged
                    } else if (parseRedditContentReportApiErrors(rawErrors).isEmpty()) {
                        RedditModeratorUserMutationNetworkResult.Failed(
                            ModeratorUserManagementFailureKind.PROTOCOL,
                        )
                    } else {
                        RedditModeratorUserMutationNetworkResult.Failed(
                            kind = ModeratorUserManagementFailureKind.VALIDATION,
                            failureDetails = moderatorInviteFailureDetails(rawErrors),
                        )
                    }
                }
                is NetworkResult.HttpFailure -> RedditModeratorUserMutationNetworkResult.Failed(
                    kind = moderatorUserMutationHttpFailureKind(result.statusCode),
                    httpStatusCode = result.statusCode,
                )
                is NetworkResult.TransportFailure -> RedditModeratorUserMutationNetworkResult.Failed(
                    kind = ModeratorUserManagementFailureKind.OUTCOME_UNKNOWN,
                )
                is NetworkResult.ProtocolFailure -> RedditModeratorUserMutationNetworkResult.Failed(
                    kind = ModeratorUserManagementFailureKind.OUTCOME_UNKNOWN,
                )
            }
        } else {
            when (
                val result = httpClient.postFormStatus(
                    redditModeratorUnapproveUserFormRequest(normalized),
                )
            ) {
                is NetworkResult.Success -> RedditModeratorUserMutationNetworkResult.Acknowledged
                is NetworkResult.HttpFailure -> RedditModeratorUserMutationNetworkResult.Failed(
                    kind = moderatorUserMutationHttpFailureKind(result.statusCode),
                    httpStatusCode = result.statusCode,
                )
                is NetworkResult.TransportFailure -> RedditModeratorUserMutationNetworkResult.Failed(
                    kind = ModeratorUserManagementFailureKind.OUTCOME_UNKNOWN,
                )
                is NetworkResult.ProtocolFailure -> RedditModeratorUserMutationNetworkResult.Failed(
                    kind = ModeratorUserManagementFailureKind.OUTCOME_UNKNOWN,
                )
            }
        }
    }
}

// MODWRITE01_OUTCOME_UNKNOWN_V1: a 5xx/408/421/425, or a 2xx whose body cannot
// be read, may come after Reddit applied the ban/mute/approval. The outcome is
// unknown, so the coordinator reads the list back instead of showing a failure.
internal fun moderatorUserMutationHttpFailureKind(statusCode: Int): ModeratorUserManagementFailureKind =
    if (isRedditWriteOutcomeUnknownStatus(statusCode)) {
        ModeratorUserManagementFailureKind.OUTCOME_UNKNOWN
    } else {
        ModeratorUserManagementFailureKind.HTTP
    }

internal fun redditBannedUsersRequest(
    rawCommunityName: String,
    after: String? = null,
    username: String? = null,
): JsonGetRequest {
    val community = normalizeRedditCommunityName(rawCommunityName)
    return JsonGetRequest(
        pathSegments = listOf("r", community, "about", "banned"),
        queryParameters = buildList {
            add("limit" to BANNED_USERS_PAGE_LIMIT.toString())
            add("raw_json" to "1")
            after?.trim()?.takeIf(String::isNotEmpty)?.let { add("after" to it) }
            username?.let { add("user" to normalizeRedditSafetyUsername(it)) }
        },
    )
}

internal fun redditMutedUsersRequest(
    rawCommunityName: String,
    after: String? = null,
    username: String? = null,
): JsonGetRequest {
    val community = normalizeRedditCommunityName(rawCommunityName)
    return JsonGetRequest(
        pathSegments = listOf("r", community, "about", "muted"),
        queryParameters = buildList {
            add("limit" to MUTED_USERS_PAGE_LIMIT.toString())
            add("raw_json" to "1")
            after?.trim()?.takeIf(String::isNotEmpty)?.let { add("after" to it) }
            username?.let { add("user" to normalizeRedditSafetyUsername(it)) }
        },
    )
}

internal fun redditApprovedUsersRequest(
    rawCommunityName: String,
    after: String? = null,
    username: String? = null,
): JsonGetRequest {
    val community = normalizeRedditCommunityName(rawCommunityName)
    return JsonGetRequest(
        pathSegments = listOf("r", community, "about", "contributors"),
        queryParameters = buildList {
            add("limit" to APPROVED_USERS_PAGE_LIMIT.toString())
            add("raw_json" to "1")
            after?.trim()?.takeIf(String::isNotEmpty)?.let { add("after" to it) }
            username?.let { add("user" to normalizeRedditSafetyUsername(it)) }
        },
    )
}

internal fun redditModeratorBanFormRequest(
    request: RedditModeratorUserBanRequest,
): FormPostRequest {
    val normalized = request.normalized()
    require(normalized.banned)
    return FormPostRequest(
        pathSegments = listOf("r", normalized.communityName, "api", "friend"),
        fields = buildList {
            add("api_type" to "json")
            add("name" to normalized.username)
            add("type" to "banned")
            normalized.moderatorNote?.let { add("note" to it) }
            normalized.banReason?.let { add("ban_reason" to it) }
            normalized.durationDays?.let { add("duration" to it.toString()) }
            normalized.messageToUser?.let { add("ban_message" to it) }
        },
    )
}

internal fun redditModeratorUnbanFormRequest(
    request: RedditModeratorUserBanRequest,
): FormPostRequest {
    val normalized = request.normalized()
    require(!normalized.banned)
    return FormPostRequest(
        pathSegments = listOf("r", normalized.communityName, "api", "unfriend"),
        fields = listOf(
            "api_type" to "json",
            "name" to normalized.username,
            "type" to "banned",
        ),
    )
}

internal fun redditModeratorMuteFormRequest(
    request: RedditModeratorUserMuteRequest,
): FormPostRequest {
    val normalized = request.normalized()
    require(normalized.muted)
    return FormPostRequest(
        pathSegments = listOf("r", normalized.communityName, "api", "friend"),
        fields = buildList {
            add("api_type" to "json")
            add("name" to normalized.username)
            add("type" to "muted")
            normalized.moderatorNote?.let { add("note" to it) }
        },
    )
}

internal fun redditModeratorUnmuteFormRequest(
    request: RedditModeratorUserMuteRequest,
): FormPostRequest {
    val normalized = request.normalized()
    require(!normalized.muted)
    return FormPostRequest(
        pathSegments = listOf("r", normalized.communityName, "api", "unfriend"),
        fields = listOf(
            "api_type" to "json",
            "name" to normalized.username,
            "type" to "muted",
        ),
    )
}

internal fun redditModeratorApproveUserFormRequest(
    request: RedditModeratorUserApprovalRequest,
): FormPostRequest {
    val normalized = request.normalized()
    require(normalized.approved)
    return FormPostRequest(
        pathSegments = listOf("r", normalized.communityName, "api", "friend"),
        fields = listOf(
            "api_type" to "json",
            "name" to normalized.username,
            "type" to "contributor",
        ),
    )
}

internal fun redditModeratorUnapproveUserFormRequest(
    request: RedditModeratorUserApprovalRequest,
): FormPostRequest {
    val normalized = request.normalized()
    require(!normalized.approved)
    return FormPostRequest(
        pathSegments = listOf("r", normalized.communityName, "api", "unfriend"),
        fields = listOf(
            "api_type" to "json",
            "name" to normalized.username,
            "type" to "contributor",
        ),
    )
}

@Serializable
internal data class RedditBannedUsersListingDto(
    val data: RedditBannedUsersListingDataDto = RedditBannedUsersListingDataDto(),
)

@Serializable
internal data class RedditBannedUsersListingDataDto(
    val children: List<RedditBannedUserDto?> = emptyList(),
    val after: String? = null,
)

@Serializable
internal data class RedditBannedUserDto(
    val name: String = "",
    val id: String? = null,
    @SerialName("rel_id") val relationshipId: String? = null,
    @SerialName("icon_img") val iconImageUri: String? = null,
    @SerialName("snoovatar_img") val snoovatarImageUri: String? = null,
    val note: String? = null,
    val date: Double? = null,
)

@Serializable
internal data class RedditMutedUsersListingDto(
    val data: RedditMutedUsersListingDataDto = RedditMutedUsersListingDataDto(),
)

@Serializable
internal data class RedditMutedUsersListingDataDto(
    val children: List<RedditMutedUserDto?> = emptyList(),
    val after: String? = null,
)

@Serializable
internal data class RedditMutedUserDto(
    val name: String = "",
    val id: String? = null,
    @SerialName("rel_id") val relationshipId: String? = null,
    @SerialName("icon_img") val iconImageUri: String? = null,
    @SerialName("snoovatar_img") val snoovatarImageUri: String? = null,
    val note: String? = null,
    val date: Double? = null,
)

@Serializable
internal data class RedditApprovedUsersListingDto(
    val data: RedditApprovedUsersListingDataDto = RedditApprovedUsersListingDataDto(),
)

@Serializable
internal data class RedditApprovedUsersListingDataDto(
    val children: List<RedditApprovedUserDto?> = emptyList(),
    val after: String? = null,
)

@Serializable
internal data class RedditApprovedUserDto(
    val name: String = "",
    val id: String? = null,
    @SerialName("rel_id") val relationshipId: String? = null,
    @SerialName("icon_img") val iconImageUri: String? = null,
    @SerialName("snoovatar_img") val snoovatarImageUri: String? = null,
    val date: Double? = null,
)

@Serializable
internal data class RedditModeratorUserMutationResponseDto(
    val json: RedditContentReportJsonDto = RedditContentReportJsonDto(),
)

internal sealed interface RedditModeratorUserMutationNetworkResult {
    data object Acknowledged : RedditModeratorUserMutationNetworkResult
    data class Failed(
        val kind: ModeratorUserManagementFailureKind,
        val httpStatusCode: Int? = null,
        // LOW_NET_USER_MUTATION_KEEPS_REDDITS_ERROR_V1 (OCB-552): Reddit's
        // error code and message for a refused ban/mute/approve.
        val failureDetails: FailureDetails? = null,
    ) : RedditModeratorUserMutationNetworkResult
}

internal fun RedditBannedUsersListingDto.toBannedUsersPage(): NetworkPage<RedditBannedUser> =
    NetworkPage(
        items = data.children.asSequence()
            .mapNotNull { it?.toBannedUserOrNull() }
            .distinctBy(RedditBannedUser::username)
            .take(BANNED_USERS_PAGE_LIMIT)
            .toList(),
        nextCursor = data.after?.trim()?.takeIf(String::isNotEmpty)?.take(160),
    )

internal fun RedditMutedUsersListingDto.toMutedUsersPage(): NetworkPage<RedditMutedUser> =
    NetworkPage(
        items = data.children.asSequence()
            .mapNotNull { it?.toMutedUserOrNull() }
            .distinctBy(RedditMutedUser::username)
            .take(MUTED_USERS_PAGE_LIMIT)
            .toList(),
        nextCursor = data.after?.trim()?.takeIf(String::isNotEmpty)?.take(160),
    )

private fun RedditMutedUserDto.toMutedUserOrNull(): RedditMutedUser? {
    val username = runCatching { normalizeRedditSafetyUsername(name) }.getOrNull() ?: return null
    return RedditMutedUser(
        username = username,
        accountId = id?.trim()?.takeIf(String::isNotEmpty)?.take(64),
        relationshipId = relationshipId?.trim()?.takeIf(String::isNotEmpty)?.take(160),
        avatarImageUri = snoovatarImageUri.secureModeratorUserImageUrlOrNull()
            ?: iconImageUri.secureModeratorUserImageUrlOrNull(),
        note = note?.trim()?.takeIf(String::isNotEmpty)?.take(MODERATOR_MUTE_NOTE_MAX_CHARS),
        createdEpochSeconds = date?.toLong()?.coerceAtLeast(0L),
    )
}

internal fun RedditApprovedUsersListingDto.toApprovedUsersPage(): NetworkPage<RedditApprovedUser> =
    NetworkPage(
        items = data.children.asSequence()
            .mapNotNull { it?.toApprovedUserOrNull() }
            .distinctBy(RedditApprovedUser::username)
            .take(APPROVED_USERS_PAGE_LIMIT)
            .toList(),
        nextCursor = data.after?.trim()?.takeIf(String::isNotEmpty)?.take(160),
    )

private fun RedditApprovedUserDto.toApprovedUserOrNull(): RedditApprovedUser? {
    val username = runCatching { normalizeRedditSafetyUsername(name) }.getOrNull() ?: return null
    return RedditApprovedUser(
        username = username,
        accountId = id?.trim()?.takeIf(String::isNotEmpty)?.take(64),
        relationshipId = relationshipId?.trim()?.takeIf(String::isNotEmpty)?.take(160),
        avatarImageUri = snoovatarImageUri.secureModeratorUserImageUrlOrNull()
            ?: iconImageUri.secureModeratorUserImageUrlOrNull(),
        createdEpochSeconds = date?.toLong()?.coerceAtLeast(0L),
    )
}

private fun RedditBannedUserDto.toBannedUserOrNull(): RedditBannedUser? {
    val username = runCatching { normalizeRedditSafetyUsername(name) }.getOrNull() ?: return null
    return RedditBannedUser(
        username = username,
        accountId = id?.trim()?.takeIf(String::isNotEmpty)?.take(64),
        relationshipId = relationshipId?.trim()?.takeIf(String::isNotEmpty)?.take(160),
        avatarImageUri = snoovatarImageUri.secureModeratorUserImageUrlOrNull()
            ?: iconImageUri.secureModeratorUserImageUrlOrNull(),
        note = note?.trim()?.takeIf(String::isNotEmpty)?.take(MODERATOR_BAN_NOTE_MAX_CHARS),
        createdEpochSeconds = date?.toLong()?.coerceAtLeast(0L),
    )
}

private fun String?.secureModeratorUserImageUrlOrNull(): String? {
    val candidate = this
        ?.replace("&amp;", "&")
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: return null
    val parsed = candidate.toHttpUrlOrNull() ?: return null
    return candidate.takeIf {
        parsed.isHttps &&
            parsed.username.isEmpty() &&
            parsed.password.isEmpty()
    }
}

private const val BANNED_USERS_PAGE_LIMIT = 100
private const val MUTED_USERS_PAGE_LIMIT = 100
private const val APPROVED_USERS_PAGE_LIMIT = 100
