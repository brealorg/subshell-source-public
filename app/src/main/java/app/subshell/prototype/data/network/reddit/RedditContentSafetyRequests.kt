package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.account.RedditContentReportRequest
import app.subshell.prototype.data.account.RedditPostVisibilityRequest
import app.subshell.prototype.data.account.normalizedRedditContentReportRequest
import app.subshell.prototype.data.account.normalizedRedditPostVisibilityRequest
import app.subshell.prototype.data.network.FormPostRequest
import app.subshell.prototype.data.account.RedditUserBlockRequest
import app.subshell.prototype.data.account.normalizedRedditUserBlockRequest
import app.subshell.prototype.data.account.normalizeRedditSafetyAccountFullname

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
