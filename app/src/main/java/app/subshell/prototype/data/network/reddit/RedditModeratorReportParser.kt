package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.account.RedditModeratorContentState
import app.subshell.prototype.data.account.RedditModeratorReportDetails
import app.subshell.prototype.data.account.RedditModeratorReportReason
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

// MODERATION02A2_REPORT_TUPLES_PRESENCE_AND_PERMISSIONS_V1
internal fun moderatorReportDetails(
    numReports: Int?,
    userReports: JsonElement?,
    modReports: JsonElement?,
    ignoreReports: JsonElement?,
    canModPost: JsonElement?,
): RedditModeratorReportDetails {
    var incomplete = false
    fun parse(raw: JsonElement?, moderator: Boolean): List<RedditModeratorReportReason>? {
        val array = raw as? JsonArray ?: return null
        if (array.size > MAX_REPORT_ROWS) incomplete = true
        return array.take(MAX_REPORT_ROWS).mapNotNull { entry ->
            val tuple = entry as? JsonArray
            val text = (tuple?.getOrNull(0) as? JsonPrimitive)?.takeIf { it.isString }?.content
                ?.trim()?.takeIf(String::isNotEmpty)
            if (text == null) {
                incomplete = true
                return@mapNotNull null
            }
            if (text.length > MAX_REASON_CHARS) incomplete = true
            val count = if (!moderator) (tuple.getOrNull(1) as? JsonPrimitive)
                ?.takeUnless { it.isString }?.intOrNull?.takeIf { it in 1..MAX_REPORT_COUNT } else null
            val suppliedModerator = if (moderator) (tuple.getOrNull(1) as? JsonPrimitive)
                ?.takeIf { it.isString }?.content?.trim()?.takeIf(String::isNotEmpty) else null
            if (!moderator && count == null) incomplete = true
            if (moderator && suppliedModerator == null) incomplete = true
            RedditModeratorReportReason(
                reason = text.take(MAX_REASON_CHARS),
                count = count,
                moderatorName = suppliedModerator?.take(100),
            )
        }
    }
    val users = parse(userReports, moderator = false)
    val moderators = parse(modReports, moderator = true)
    if (numReports != null && numReports > MAX_REPORT_COUNT) incomplete = true
    val knownTotal = numReports?.takeIf { it >= 0 }?.coerceAtMost(MAX_REPORT_COUNT)
        ?: if (users != null && moderators != null && !incomplete && users.all { it.count != null }) {
            (users.sumOf { it.count!!.toLong() } + moderators.size).coerceAtMost(MAX_REPORT_COUNT.toLong()).toInt()
        } else null
    fun boolean(raw: JsonElement?): Boolean? =
        (raw as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull
    return RedditModeratorReportDetails(
        userReports = users,
        moderatorReports = moderators,
        totalCount = knownTotal,
        ignored = boolean(ignoreReports),
        canModerate = boolean(canModPost),
        incomplete = incomplete,
    )
}


// Preserve the difference between an omitted field (state unknown) and
// Reddit's explicit null value (not distinguished). The sentinel is only a
// constructor default; server data never receives special treatment.
internal val MISSING_MODERATOR_DISTINGUISHED_FIELD: JsonElement =
    JsonPrimitive("__subshell_missing_distinguished__")

// MODERATION02A3_LOCK_DISTINGUISH_STATE_PARSER_V1
internal fun moderatorContentState(
    locked: JsonElement?,
    distinguished: JsonElement,
    canModPost: JsonElement?,
): RedditModeratorContentState {
    fun boolean(raw: JsonElement?): Boolean? =
        (raw as? JsonPrimitive)
            ?.takeUnless { it.isString }
            ?.booleanOrNull

    val canModerate = boolean(canModPost)
    val lockedState = boolean(locked)
    val distinguishedState = when {
        distinguished == MISSING_MODERATOR_DISTINGUISHED_FIELD -> null
        distinguished is JsonNull -> false
        distinguished is JsonPrimitive && distinguished.isString &&
            distinguished.content.equals("moderator", ignoreCase = true) -> true
        else -> null
    }
    return RedditModeratorContentState(
        locked = lockedState,
        distinguished = distinguishedState,
        canModerate = canModerate,
    )
}

private const val MAX_REPORT_ROWS = 100
private const val MAX_REASON_CHARS = 2_000
private const val MAX_REPORT_COUNT = 10_000
