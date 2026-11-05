package io.github.brealorg.subshell.data.network.reddit

import io.github.brealorg.subshell.data.account.RedditContentReportRequest
import io.github.brealorg.subshell.data.account.RedditPostVisibilityRequest
import io.github.brealorg.subshell.data.account.normalizedRedditContentReportRequest
import io.github.brealorg.subshell.data.account.normalizedRedditPostVisibilityRequest
import io.github.brealorg.subshell.data.network.FormPostRequest
import io.github.brealorg.subshell.data.account.RedditUserBlockRequest
import io.github.brealorg.subshell.data.account.normalizedRedditUserBlockRequest
import io.github.brealorg.subshell.data.account.normalizeRedditSafetyAccountFullname

// SAFETY01A1_CANONICAL_REDDIT_SAFETY_REQUESTS_V1
internal fun redditPostVisibilityFormRequest(
    request: RedditPostVisibilityRequest,
): FormPostRequest {
    val normalized = request.normalizedRedditPostVisibilityRequest()
    return FormPostRequest(
        pathSegments = listOf(
            "api",
            if (normalized.hidden) "hide" else "unhide",
        ),
        fields = listOf(
            "id" to normalized.postFullname,
        ),
    )
}

internal fun redditContentReportFormRequest(
    request: RedditContentReportRequest,
): FormPostRequest {
    val normalized = request.normalizedRedditContentReportRequest()
    return FormPostRequest(
        pathSegments = listOf("api", "report"),
        fields = buildList {
            add("api_type" to "json")
            // Reddit's deployed /api/report clients, including current PRAW,
            // submit the target fullname as `id`. Keep that deployed wire shape
            // isolated here so a future Reddit API correction is one boundary change.
            add("id" to normalized.thingFullname)
            add("reason" to normalized.reason)
            normalized.communityName?.let { add("sr_name" to it) }
        },
    )
}

// USERACTION01A1_CANONICAL_BLOCK_UNBLOCK_REQUESTS_V1
internal fun redditUserBlockFormRequest(
    request: RedditUserBlockRequest,
    currentUserFullname: String,
): FormPostRequest {
    val normalized = request.normalizedRedditUserBlockRequest()
    return if (normalized.blocked) {
        FormPostRequest(
            pathSegments = listOf("api", "block_user"),
            fields = listOf(
                "api_type" to "json",
                "name" to normalized.username,
            ),
        )
    } else {
        FormPostRequest(
            pathSegments = listOf("api", "unfriend"),
            fields = listOf(
                "container" to normalizeRedditSafetyAccountFullname(currentUserFullname),
                "name" to normalized.username,
                "type" to "enemy",
            ),
        )
    }
}
