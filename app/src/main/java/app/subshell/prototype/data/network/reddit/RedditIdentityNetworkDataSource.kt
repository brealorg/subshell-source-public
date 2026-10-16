package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.network.JsonGetRequest
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.map
import app.subshell.prototype.model.Community
import app.subshell.prototype.model.UserFixture
import java.util.Locale
import app.subshell.prototype.data.network.NetworkRateLimit
import app.subshell.prototype.data.network.NetworkResponseMetadata
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

interface IdentityMetadataDataSource {
    suspend fun user(
        username: String,
        fallback: UserFixture,
    ): NetworkResult<UserFixture>

    suspend fun community(
        communityName: String,
        fallback: Community,
    ): NetworkResult<Community>

    suspend fun communities(
        fallbacks: List<Community>,
    ): NetworkResult<List<Community>>

    // ISSUE22_AUTHOR_AVATAR_BATCH_LOOKUP_V1
    /**
     * Avatar image URLs for Reddit account fullnames ("t2_…"), keyed by the
     * fullname. Accounts Reddit does not know are absent; accounts without a
     * usable https avatar map to null.
     */
    suspend fun authorAvatars(
        fullnames: Collection<String>,
    ): NetworkResult<Map<String, String?>>
}

internal class RedditIdentityNetworkDataSource(
    private val httpClient: SubshellHttpClient,
    private val clock: EpochSecondsClock = SystemEpochSecondsClock,
) : IdentityMetadataDataSource {
    override suspend fun user(
        username: String,
        fallback: UserFixture,
    ): NetworkResult<UserFixture> {
        val normalized = username.normalizeRedditName(prefix = "u/")
        return httpClient.getJson(
            request = JsonGetRequest(
                pathSegments = listOf("user", normalized, "about"),
                queryParameters = listOf("raw_json" to "1"),
            ),
            deserializer = RedditUserAboutDto.serializer(),
        ).map { about ->
            about.toUserFixture(
                fallback = fallback,
                nowEpochSeconds = clock.epochSeconds(),
            )
        }
    }

    override suspend fun community(
        communityName: String,
        fallback: Community,
    ): NetworkResult<Community> {
        val normalized = communityName.normalizeRedditName(prefix = "r/")
        return httpClient.getJson(
            request = JsonGetRequest(
                pathSegments = listOf("r", normalized, "about"),
                queryParameters = listOf("raw_json" to "1"),
            ),
            deserializer = RedditCommunityAboutDto.serializer(),
        ).map { about -> about.toCommunity(fallback) }
    }

    override suspend fun authorAvatars(
        fullnames: Collection<String>,
    ): NetworkResult<Map<String, String?>> = userDataByAccountIds(httpClient, fullnames, clock)

    override suspend fun communities(
        fallbacks: List<Community>,
    ): NetworkResult<List<Community>> {
        val fallbackByName = fallbacks
            .asSequence()
            .mapNotNull { fallback ->
                fallback.name.normalizedRedditCommunityName()
                    ?.lowercase()
                    ?.let { name -> name to fallback }
            }
            .distinctBy { (name, _) -> name }
            .take(MAX_BATCH_COMMUNITIES)
            .toMap(linkedMapOf<String, Community>())
        require(fallbackByName.isNotEmpty()) {
            "At least one valid community is required for batch identity loading"
        }
        return httpClient.getJson(
            request = JsonGetRequest(
                pathSegments = listOf("api", "info"),
                queryParameters = listOf(
                    "sr_name" to fallbackByName.keys.joinToString(","),
                    "raw_json" to "1",
                ),
            ),
            deserializer = RedditCommunityInfoListingDto.serializer(),
        ).map { listing ->
            listing.data.children.mapNotNull { thing ->
                val name = thing.data.displayName
                    ?.normalizedRedditCommunityName()
                    ?.lowercase()
                    ?: return@mapNotNull null
                val fallback = fallbackByName[name] ?: return@mapNotNull null
                thing.data.toCommunity(fallback)
            }
        }
    }
}

// LOW_NET_AVATAR_CHUNKS_KEEP_WHAT_ARRIVED_V1 (OCB-547): a later chunk's
// failure keeps the avatars earlier chunks resolved. Ids of the failed chunk
// are absent from the map, so the caller asks for them again next time.
private fun partialOr(
    resolved: Map<String, String?>,
    metadata: NetworkResponseMetadata?,
    failure: NetworkResult<Map<String, String?>>,
): NetworkResult<Map<String, String?>> =
    if (resolved.isNotEmpty() && metadata != null) {
        NetworkResult.Success(value = resolved.toMap(), metadata = metadata)
    } else {
        failure
    }

// ISSUE22_AUTHOR_AVATAR_BATCH_LOOKUP_V1
// GET /api/user_data_by_account_ids?ids=t2_a,t2_b — one request per chunk of
// MAX_BATCH_ACCOUNT_IDS; the response is a map keyed by fullname. Reddit
// answers 404 when no id matches, which is an empty result, not a failure.
internal suspend fun userDataByAccountIds(
    httpClient: SubshellHttpClient,
    fullnames: Collection<String>,
    clock: EpochSecondsClock = SystemEpochSecondsClock,
): NetworkResult<Map<String, String?>> {
    val ids = fullnames
        .asSequence()
        .mapNotNull { fullname -> fullname.redditAccountFullnameOrNull() }
        .distinct()
        .toList()
    val resolved = linkedMapOf<String, String?>()
    var metadata: NetworkResponseMetadata? = null
    for (chunk in ids.chunked(MAX_BATCH_ACCOUNT_IDS)) {
        val result = httpClient.getJson(
            request = JsonGetRequest(
                pathSegments = listOf("api", "user_data_by_account_ids"),
                queryParameters = listOf(
                    "ids" to chunk.joinToString(","),
                    "raw_json" to "1",
                ),
            ),
            deserializer = MapSerializer(String.serializer(), RedditAccountDataDto.serializer()),
        )
        when (result) {
            is NetworkResult.Success -> {
                metadata = result.metadata
                for ((fullname, data) in result.value) {
                    val key = fullname.redditAccountFullnameOrNull() ?: continue
                    resolved[key] = data.profileImage.httpsImageUrlOrNull()
                }
            }
            is NetworkResult.HttpFailure ->
                if (result.statusCode == HTTP_NOT_FOUND) {
                    metadata = result.metadata
                } else {
                    return partialOr(resolved, metadata, result)
                }
            is NetworkResult.TransportFailure -> return partialOr(resolved, metadata, result)
            is NetworkResult.ProtocolFailure -> return partialOr(resolved, metadata, result)
        }
    }
    return NetworkResult.Success(
        value = resolved,
        metadata = metadata ?: NetworkResponseMetadata(
            // No request was sent (no valid id): a local, empty success.
            statusCode = HTTP_NO_CONTENT,
            receivedAtEpochMs = clock.epochSeconds() * 1_000L,
            rateLimit = NetworkRateLimit(remaining = null, used = null, resetAfterMs = null),
            retryAfterMs = null,
        ),
    )
}

@Serializable
internal data class RedditAccountDataDto(
    val name: String? = null,
    @SerialName("profile_img") val profileImage: String? = null,
    @SerialName("profile_color") val profileColor: String? = null,
)

@Serializable
internal data class RedditUserAboutDto(
    val kind: String? = null,
    val data: RedditUserAboutDataDto = RedditUserAboutDataDto(),
)

@Serializable
internal data class RedditUserAboutDataDto(
    val id: String? = null,
    val name: String? = null,
    @SerialName("created_utc") val createdUtc: Double? = null,
    @SerialName("total_karma") val totalKarma: Long? = null,
    @SerialName("link_karma") val linkKarma: Long? = null,
    @SerialName("comment_karma") val commentKarma: Long? = null,
    @SerialName("icon_img") val iconImage: String? = null,
    @SerialName("snoovatar_img") val snoovatarImage: String? = null,
    val subreddit: RedditUserProfileSubredditDto? = null,
)

@Serializable
internal data class RedditUserProfileSubredditDto(
    @SerialName("public_description") val publicDescription: String? = null,
    val subscribers: Long? = null,
    @SerialName("banner_img") val bannerImage: String? = null,
    @SerialName("banner_background_image") val bannerBackgroundImage: String? = null,
)

@Serializable
internal data class RedditCommunityAboutDto(
    val kind: String? = null,
    val data: RedditCommunityAboutDataDto = RedditCommunityAboutDataDto(),
)

@Serializable
internal data class RedditCommunityAboutDataDto(
    val id: String? = null,
    val name: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("public_description") val publicDescription: String? = null,
    val subscribers: Long? = null,
    @SerialName("user_is_subscriber") val userIsSubscriber: Boolean? = null,
    @SerialName("community_icon") val communityIcon: String? = null,
    @SerialName("icon_img") val iconImage: String? = null,
    @SerialName("mobile_banner_image") val mobileBannerImage: String? = null,
    @SerialName("banner_background_image") val bannerBackgroundImage: String? = null,
    @SerialName("banner_img") val bannerImage: String? = null,
    @SerialName("header_img") val headerImage: String? = null,
    @SerialName("primary_color") val primaryColor: String? = null,
    @SerialName("key_color") val keyColor: String? = null,
    // CORRECT01_FOLLOWED_USERS_NOT_COMMUNITIES_V1 (OCB-555): "user" marks a
    // followed user's profile (u_name) in the subscriptions listing.
    @SerialName("subreddit_type") val subredditType: String? = null,
    @SerialName("user_has_favorited") val userHasFavorited: Boolean? = null,
)

@Serializable
internal data class RedditCommunityInfoListingDto(
    val data: RedditCommunityInfoListingDataDto = RedditCommunityInfoListingDataDto(),
)

@Serializable
internal data class RedditCommunityInfoListingDataDto(
    val children: List<RedditCommunityInfoThingDto> = emptyList(),
)

@Serializable
internal data class RedditCommunityInfoThingDto(
    val kind: String? = null,
    val data: RedditCommunityAboutDataDto = RedditCommunityAboutDataDto(),
)

internal fun RedditUserAboutDto.toUserFixture(
    fallback: UserFixture,
    nowEpochSeconds: Long,
): UserFixture {
    val karma = data.totalKarma
        ?: listOfNotNull(data.linkKarma, data.commentKarma)
            .takeIf { values -> values.isNotEmpty() }
            ?.sum()
    return fallback.copy(
        username = fallback.username,
        description = data.subreddit
            ?.publicDescription
            .cleanTextOrNull()
            ?: fallback.description,
        karma = karma?.let { value -> "${value.compactCount()} karma" } ?: fallback.karma,
        accountAge = data.createdUtc
            ?.toLong()
            ?.let { created -> accountAgeLabel(nowEpochSeconds, created) }
            ?: fallback.accountAge,
        followers = data.subreddit
            ?.subscribers
            ?.takeIf { value -> value >= 0L }
            ?.compactCount()
            ?: fallback.followers,
        avatarImageUri = data.snoovatarImage.httpsImageUrlOrNull()
            ?: data.iconImage.httpsImageUrlOrNull()
            ?: fallback.avatarImageUri,
        bannerImageUri = data.subreddit?.let { profile ->
            profile.bannerImage.httpsImageUrlOrNull()
                ?: profile.bannerBackgroundImage.httpsImageUrlOrNull()
        }
            ?: fallback.bannerImageUri,
    )
}

internal fun RedditCommunityAboutDto.toCommunity(fallback: Community): Community =
    data.toCommunity(fallback)

internal fun RedditCommunityAboutDataDto.toCommunity(fallback: Community): Community = fallback.copy(
    id = fallback.id,
    description = publicDescription.cleanTextOrNull() ?: fallback.description,
    members = subscribers
        ?.takeIf { value -> value >= 0L }
        ?.compactCount()
        ?: fallback.members,
    accent = primaryColor.redditColorOrNull()
        ?: keyColor.redditColorOrNull()
        ?: fallback.accent,
    joined = fallback.joined,
    iconImageUri = communityIcon.httpsImageUrlOrNull()
        ?: iconImage.httpsImageUrlOrNull()
        ?: fallback.iconImageUri,
    bannerImageUri = mobileBannerImage.httpsImageUrlOrNull()
        ?: bannerBackgroundImage.httpsImageUrlOrNull()
        ?: bannerImage.httpsImageUrlOrNull()
        ?: headerImage.httpsImageUrlOrNull()
        ?: fallback.bannerImageUri,
)

private fun String.normalizeRedditName(prefix: String): String {
    val normalized = trim().removePrefix(prefix)
    require(normalized.length in 1..MAX_REDDIT_NAME_LENGTH) {
        "Reddit name length is outside the supported range"
    }
    require(normalized.all { character ->
        character.isLetterOrDigit() || character == '_' || character == '-'
    }) {
        "Reddit name contains unsupported characters"
    }
    return normalized
}

private fun String?.cleanTextOrNull(): String? =
    this?.trim()?.takeIf(String::isNotEmpty)

private fun String?.httpsImageUrlOrNull(): String? {
    val candidate = cleanTextOrNull()
        ?.replace("&amp;", "&")
        ?: return null
    val parsed = candidate.toHttpUrlOrNull() ?: return null
    return candidate.takeIf {
        parsed.isHttps &&
            parsed.username.isEmpty() &&
            parsed.password.isEmpty()
    }
}

private fun String?.redditColorOrNull(): Long? {
    val hex = cleanTextOrNull()?.removePrefix("#") ?: return null
    return when (hex.length) {
        6 -> hex.toLongOrNull(16)?.let { rgb -> rgb or 0xFF000000L }
        8 -> hex.toLongOrNull(16)
        else -> null
    }
}

// CORRECT01_COMPACT_COUNT_ROUNDS_FIRST_V1 (OCB-548): the unit is picked
// after rounding, so 999 960 is "1m", not "1000k".
internal fun Long.compactCount(): String = when {
    this >= 999_950_000L -> compactDecimal(this / 1_000_000_000.0, "b")
    this >= 999_950L -> compactDecimal(this / 1_000_000.0, "m")
    this >= 1_000L -> compactDecimal(this / 1_000.0, "k")
    else -> toString()
}

private fun compactDecimal(value: Double, suffix: String): String {
    val formatted = String.format(Locale.US, "%.1f", value).removeSuffix(".0")
    return formatted + suffix
}

private fun accountAgeLabel(nowEpochSeconds: Long, createdEpochSeconds: Long): String {
    val elapsedDays = ((nowEpochSeconds - createdEpochSeconds).coerceAtLeast(0L) / 86_400L)
    return when {
        elapsedDays >= 365L -> "${elapsedDays / 365L}y"
        elapsedDays >= 30L -> "${elapsedDays / 30L}mo"
        else -> "${elapsedDays.coerceAtLeast(1L)}d"
    }
}

private const val MAX_REDDIT_NAME_LENGTH = 100
private const val MAX_BATCH_COMMUNITIES = 100
internal const val MAX_BATCH_ACCOUNT_IDS = 100
private const val HTTP_NOT_FOUND = 404
private const val HTTP_NO_CONTENT = 204
