package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.account.normalizeCrosspostSourceFullname
import app.subshell.prototype.data.network.JsonGetRequest
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.map

internal enum class RedditSubmitReconciliationKind {
    TEXT,
    LINK,
    IMAGE,
    VIDEO,
    GALLERY,
    CROSSPOST,
}

internal data class RedditSubmitReconciliationCandidate(
    val fullname: String,
    val id: String,
    val community: String,
    val author: String,
    val title: String,
    val text: String,
    val createdUtcEpochSeconds: Long,
    val permalink: String,
    val destinationUrl: String?,
    val kind: RedditSubmitReconciliationKind,
    val flairText: String?,
    val nsfw: Boolean,
    val spoiler: Boolean,
    // CROSSPOST01A1B_AUTHORITATIVE_OUTER_PARENT_V1
    val crosspostFullname: String? = null,
    val flairId: String? = null,
)

// COMPOSER02_SUBMIT_ATTEMPT_ABSENCE_V1: reachesEnd = Reddit has no older page,
// so the account's whole submission history is in candidates.
internal data class RedditSubmitReconciliationListing(
    val candidates: List<RedditSubmitReconciliationCandidate>,
    val reachesEnd: Boolean,
)

internal class RedditSubmitReconciliationDataSource(
    private val httpClient: SubshellHttpClient,
) {
    suspend fun submitted(
        username: String,
        limit: Int = DEFAULT_RECONCILIATION_LIMIT,
    ): NetworkResult<RedditSubmitReconciliationListing> {
        val normalizedUsername = requireNotNull(normalizeRedditSubmittedUsername(username)) {
            "Invalid Reddit username for submit reconciliation"
        }
        val normalizedLimit = limit.coerceIn(1, MAX_RECONCILIATION_LIMIT)
        return httpClient.getJson(
            request = JsonGetRequest(
                pathSegments = listOf("user", normalizedUsername, "submitted"),
                queryParameters = listOf(
                    "raw_json" to "1",
                    "sort" to "new",
                    "limit" to normalizedLimit.toString(),
                ),
            ),
            deserializer = RedditListingDto.serializer(),
        ).map { listing ->
            RedditSubmitReconciliationListing(
                candidates = listing.toSubmitReconciliationCandidates(),
                reachesEnd = listing.data.after == null,
            )
        }
    }
}

internal fun RedditListingDto.toSubmitReconciliationCandidates():
    List<RedditSubmitReconciliationCandidate> = data.children
    .asSequence()
    .filter { child -> child.kind == null || child.kind == "t3" }
    .mapNotNull { child ->
        val post = child.data
        val id = post.id.trim().takeIf { value ->
            value.isNotEmpty() && value.length <= 16 && value.all(Char::isLetterOrDigit)
        } ?: return@mapNotNull null
        val community = (post.subreddit ?: post.subredditNamePrefixed)
            ?.normalizedRedditCommunityName()
            ?: return@mapNotNull null
        val author = post.author?.trim()?.takeIf(String::isNotEmpty)
            ?: return@mapNotNull null
        val createdUtc = post.createdUtc
            ?.takeIf { value -> value.isFinite() && value >= 0.0 }
            ?.toLong()
            ?: return@mapNotNull null
        val fullname = post.name
            ?.trim()
            ?.takeIf { value -> value == "t3_$id" }
            ?: "t3_$id"
        // Even invalid or preview-only crosspost markers must not fall through as TEXT/LINK.
        val hasCrosspostEvidence = post.crosspostParent != null || post.crosspostParentList.isNotEmpty()
        val parentFullname = post.crosspostParent?.let { parent ->
            runCatching { normalizeCrosspostSourceFullname(parent) }.getOrNull()
        }
        if (hasCrosspostEvidence) {
            val canonicalWrapper = runCatching {
                normalizeCrosspostSourceFullname("t3_$id")
            }.getOrNull() ?: return@mapNotNull null
            if (canonicalWrapper != "t3_$id") return@mapNotNull null
            if (post.name != null && post.name.trim() != canonicalWrapper) return@mapNotNull null
        }
        val permalink = post.permalink
            ?.trim()
            ?.takeIf { value -> value.startsWith('/') }
            ?: "/r/$community/comments/$id/"
        val kind = when {
            hasCrosspostEvidence -> RedditSubmitReconciliationKind.CROSSPOST
            post.isVideo -> RedditSubmitReconciliationKind.VIDEO
            post.isGallery -> RedditSubmitReconciliationKind.GALLERY
            post.postHint.equals("image", ignoreCase = true) ->
                RedditSubmitReconciliationKind.IMAGE
            post.postHint.equals("rich:video", ignoreCase = true) ->
                RedditSubmitReconciliationKind.VIDEO
            post.postHint.equals("link", ignoreCase = true) ->
                RedditSubmitReconciliationKind.LINK
            !post.destinationUrl.isNullOrBlank() ->
                RedditSubmitReconciliationKind.LINK
            else -> RedditSubmitReconciliationKind.TEXT
        }
        RedditSubmitReconciliationCandidate(
            fullname = fullname,
            id = id,
            community = community,
            author = author,
            title = post.title,
            text = post.selftext,
            createdUtcEpochSeconds = createdUtc,
            // Route reconciliation to the created wrapper, never a copied parent permalink.
            permalink = if (hasCrosspostEvidence) "/r/$community/comments/$id/" else permalink,
            destinationUrl = post.destinationUrl,
            kind = kind,
            flairText = post.linkFlairText,
            nsfw = post.over18,
            spoiler = post.spoiler,
            crosspostFullname = parentFullname,
            flairId = post.linkFlairTemplateId?.trim()?.takeIf(String::isNotEmpty),
        )
    }
    .toList()

private fun normalizeRedditSubmittedUsername(value: String): String? {
    val trimmed = value.trim()
    val candidate = when {
        trimmed.startsWith("/u/", ignoreCase = true) -> trimmed.drop(3)
        trimmed.startsWith("u/", ignoreCase = true) -> trimmed.drop(2)
        else -> trimmed
    }
    return candidate.takeIf { username ->
        username.length in 1..100 &&
            username.all { character ->
                character.isLetterOrDigit() || character == '_' || character == '-'
            }
    }
}

private const val DEFAULT_RECONCILIATION_LIMIT = 25
private const val MAX_RECONCILIATION_LIMIT = 100
