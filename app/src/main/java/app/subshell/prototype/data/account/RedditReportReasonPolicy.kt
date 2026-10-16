package app.subshell.prototype.data.account

import app.subshell.prototype.model.CommunityInfoSnapshot
import app.subshell.prototype.model.CommunityRule

// REPORT01A1_TYPED_REDDIT_NATIVE_REPORT_REASON_POLICY_V1
enum class RedditReportTargetKind { POST, COMMENT }

data class RedditReportReasonOption(
    val label: String,
    val wireReason: String,
    val description: String? = null,
)

data class RedditReportReasonPolicy(
    val communityName: String,
    val targetKind: RedditReportTargetKind,
    val options: List<RedditReportReasonOption>,
    val freeFormReportsAllowed: Boolean,
    val freeFormReportsKnown: Boolean,
)

internal fun CommunityInfoSnapshot.toRedditReportReasonPolicy(
    targetKind: RedditReportTargetKind,
): RedditReportReasonPolicy = RedditReportReasonPolicy(
    communityName = communityName,
    targetKind = targetKind,
    options = rules.asSequence()
        .filter { it.appliesTo(targetKind) }
        .mapNotNull { rule ->
            val wire = rule.violationReason?.trim()?.takeIf(String::isNotEmpty)
                ?: rule.shortName.trim().takeIf(String::isNotEmpty)
                ?: return@mapNotNull null
            if (wire.length > REPORT_REASON_MAX_CHARS) return@mapNotNull null
            RedditReportReasonOption(
                label = rule.shortName.trim().ifBlank { wire },
                wireReason = wire,
                description = rule.description?.trim()?.takeIf(String::isNotEmpty),
            )
        }
        .distinctBy { it.wireReason.lowercase() }
        .toList(),
    freeFormReportsAllowed = freeFormReports == true,
    freeFormReportsKnown = freeFormReports != null,
)

private fun CommunityRule.appliesTo(target: RedditReportTargetKind): Boolean =
    when (kind?.trim()?.lowercase()) {
        "all" -> true
        "link" -> target == RedditReportTargetKind.POST
        "comment" -> target == RedditReportTargetKind.COMMENT
        else -> false
    }
