package app.subshell.prototype.data.account

import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.model.FlairRichTextSegment

// USERFLAIR01A1_OWN_COMMUNITY_FLAIR_DOMAIN_V1
internal const val USER_FLAIR_TEXT_MAX_CHARS = 64

data class UserFlairOption(
    val templateId: String,
    val text: String,
    val editable: Boolean = false,
    val textColor: String? = null,
    val backgroundColor: String? = null,
    // USERFLAIR01A3A_OWN_FLAIR_RICHTEXT_V1
    val richText: List<FlairRichTextSegment> = emptyList(),
) {
    init {
        require(templateId.isNotBlank())
        require(templateId.length <= 256)
        require(text.length <= USER_FLAIR_TEXT_MAX_CHARS)
    }
}

data class UserFlairSnapshot(
    val communityName: String,
    val currentTemplateId: String? = null,
    val currentText: String? = null,
    // USERFLAIR01A3B_CURRENT_SERVER_COLORS_V1
    val currentTextColor: String? = null,
    val currentBackgroundColor: String? = null,
    val currentRichText: List<FlairRichTextSegment> = emptyList(),
    val options: List<UserFlairOption> = emptyList(),
)

data class UserFlairMutationRequest(
    val accountStableId: String,
    val communityName: String,
    val templateId: String,
    val text: String? = null,
)

internal fun UserFlairMutationRequest.normalizedUserFlairMutationRequest():
    UserFlairMutationRequest {
    val account = accountStableId.trim()
    val community = normalizeUserFlairCommunity(communityName)
    val template = templateId.trim()
    val normalizedText = text?.trim()?.takeIf(String::isNotEmpty)
    require(account.isNotEmpty())
    require(template.isNotEmpty() && template.length <= 256)
    require(normalizedText == null || normalizedText.length <= USER_FLAIR_TEXT_MAX_CHARS)
    return copy(
        accountStableId = account,
        communityName = community,
        templateId = template,
        text = normalizedText,
    )
}

enum class UserFlairFailureKind {
    CONFIGURATION,
    ACCOUNT_CHANGED,
    SESSION,
    HTTP,
    TRANSPORT,
    PROTOCOL,
    OUTCOME_UNKNOWN,
    VALIDATION,
}

data class UserFlairUiState(
    val accountStableId: String? = null,
    val communityName: String? = null,
    val snapshot: UserFlairSnapshot? = null,
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val mutating: Boolean = false,
    val authorizationRequired: Boolean = false,
    val failureKind: UserFlairFailureKind? = null,
    val failureHttpStatusCode: Int? = null,
    // DIAG05_PLAIN_NOTICES_ARE_RECORDED_V1
    val failureDetails: FailureDetails? = null,
    val contentRevision: Long = 0L,
    val completionRevision: Long = 0L,
) {
    // ACCOUNT03_MATCHES_NEVER_THROW_V1: an id the normalizer refuses matches nothing.
    fun matches(account: String, community: String): Boolean =
        accountStableId == account.trim() &&
            runCatching { normalizeUserFlairCommunity(community) }.getOrNull()
                ?.let { normalized -> communityName.equals(normalized, ignoreCase = true) } == true
}

internal fun normalizeUserFlairCommunity(raw: String): String {
    val value = raw.trim().removePrefix("/r/").removePrefix("r/").trim()
    require(value.matches(Regex("[A-Za-z0-9_]{1,64}")))
    return value.lowercase()
}
