package app.subshell.prototype.ui

import androidx.compose.runtime.Immutable
import app.subshell.prototype.data.account.TextPostWriteRequest
import app.subshell.prototype.data.account.normalizeCrosspostSourceFullname
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import java.util.Locale

// CROSSPOST01A1C_SOURCE_CAPSULE_AND_DESTINATION_GATE_V1
// Snapshot metadata is for display. Only fullname identifies the selected source post.
@Immutable
data class ComposerCrosspostSource(
    val fullname: String,
    val community: String,
    val author: String,
    val title: String,
    val kind: PostKind,
    val nsfw: Boolean = false,
    val spoiler: Boolean = false,
) {
    init {
        require(fullname == normalizeCrosspostSourceFullname(fullname))
        require(community == normalizeCrosspostComposerCommunity(community))
        require(author.isNotBlank() && author.length <= 64 && '\u0000' !in author)
        require(title.isNotBlank() && title.length <= 300 && '\u0000' !in title)
        require('\n' !in title && '\r' !in title)
    }

    val permalink: String
        get() = "/r/$community/comments/${fullname.removePrefix("t3_")}/"
}

@Immutable
data class ComposerCrosspostDestination(
    val accountStableId: String,
    val community: String,
    val allowCrossposts: Boolean? = null,
    val subredditType: String? = null,
    val userIsBanned: Boolean? = null,
    val userCanSubmit: Boolean? = null,
    val userIsContributor: Boolean? = null,
    val userIsModerator: Boolean? = null,
    val nsfw: Boolean? = null,
)

internal fun normalizeCrosspostComposerCommunity(raw: String): String? {
    val value = raw.trim().removePrefix("/r/").removePrefix("r/").lowercase(Locale.ROOT)
    return value.takeIf { it.matches(Regex("[a-z0-9_]{1,64}")) }
}

internal fun Post.toComposerCrosspostSource(): ComposerCrosspostSource? = runCatching {
    ComposerCrosspostSource(
        fullname = normalizeCrosspostSourceFullname(id),
        community = requireNotNull(normalizeCrosspostComposerCommunity(community)),
        author = author,
        title = title,
        kind = kind,
        // Never strip source sensitivity when the selected post is itself a wrapper.
        nsfw = nsfw || crosspost?.nsfw == true,
        spoiler = spoiler || crosspost?.spoiler == true,
    )
}.getOrNull()

internal fun newCrosspostComposerDraft(
    source: ComposerCrosspostSource,
    accountStableId: String,
    nowEpochMs: Long = System.currentTimeMillis(),
): PostComposerDraft {
    require(accountStableId.isNotBlank() && accountStableId == accountStableId.trim())
    return PostComposerDraft(
        accountStableId = accountStableId,
        community = "", // Destination is an explicit choice, not the source community.
        type = ComposerPostType.CROSSPOST,
        title = source.title,
        nsfw = source.nsfw,
        spoiler = source.spoiler,
        createdAtEpochMs = nowEpochMs,
        updatedAtEpochMs = nowEpochMs,
        crosspostSource = source,
    )
}

internal fun PostComposerDraft.withCrosspostDestination(
    community: String,
    nowEpochMs: Long = System.currentTimeMillis(),
): PostComposerDraft {
    require(type == ComposerPostType.CROSSPOST && crosspostSource != null)
    val target = requireNotNull(normalizeCrosspostComposerCommunity(community))
    val changed = normalizeCrosspostComposerCommunity(this.community) != target
    return copy(community = target, flair = flair.takeUnless { changed }).touched(nowEpochMs)
}

internal fun canUpdateSourceBoundComposerDraft(
    previous: PostComposerDraft,
    proposed: PostComposerDraft,
): Boolean {
    if (previous.type != ComposerPostType.CROSSPOST && previous.crosspostSource == null) return true
    val source = previous.crosspostSource ?: return false
    return previous.type == ComposerPostType.CROSSPOST &&
        proposed.type == ComposerPostType.CROSSPOST &&
        proposed.id == previous.id && proposed.accountStableId == previous.accountStableId &&
        proposed.crosspostSource == source &&
        proposed.body.isEmpty() && proposed.linkUrl.isEmpty() && proposed.media.isEmpty() &&
        (!source.nsfw || proposed.nsfw) && (!source.spoiler || proposed.spoiler) &&
        (
            normalizeCrosspostComposerCommunity(previous.community) ==
                normalizeCrosspostComposerCommunity(proposed.community) || proposed.flair == null
            )
}

// Used by storage before encoding/after decoding. Invalid data never becomes a TEXT draft.
internal fun PostComposerDraft.isValidSourceBoundCrosspostDraft(): Boolean {
    val source = crosspostSource ?: return false
    return type == ComposerPostType.CROSSPOST &&
        !accountStableId.isNullOrBlank() && accountStableId == accountStableId.trim() &&
        body.isEmpty() && linkUrl.isEmpty() && media.isEmpty() &&
        (community.isEmpty() || normalizeCrosspostComposerCommunity(community) != null) &&
        (!source.nsfw || nsfw) && (!source.spoiler || spoiler)
}

// CROSSPOST01A1E3_UNKNOWN_DESTINATION_SUPPORT_V1
internal fun crosspostDestinationSupportBlockingMessage(
    destination: ComposerCrosspostDestination,
): String? = when (destination.allowCrossposts) {
    false -> "This community does not allow crossposts"
    true, null -> null
}

internal fun validateCrosspostComposerDraftIssue(
    draft: PostComposerDraft,
    activeAccountStableId: String,
    activeAccountIsAuthenticated: Boolean,
    policy: ComposerCommunityPolicy,
): ComposerValidationIssue? {
    fun issue(field: ComposerValidationField, message: String) = ComposerValidationIssue(field, message)
    if (!activeAccountIsAuthenticated || activeAccountStableId.isBlank()) {
        return issue(ComposerValidationField.GENERAL, "Connect an account before crossposting")
    }
    if (draft.accountStableId != activeAccountStableId) {
        return issue(ComposerValidationField.GENERAL, "This crosspost draft belongs to another account")
    }
    if (!draft.isValidSourceBoundCrosspostDraft()) {
        return issue(ComposerValidationField.GENERAL, "The crosspost source or draft is invalid")
    }
    val target = normalizeCrosspostComposerCommunity(draft.community)
        ?: return issue(ComposerValidationField.COMMUNITY, "Choose a destination community")
    if (!policy.loadedFromReddit || normalizeCrosspostComposerCommunity(policy.community) != target) {
        return issue(ComposerValidationField.COMMUNITY, "Load the destination's posting requirements")
    }
    val destination = policy.crosspostDestination
        ?: return issue(ComposerValidationField.COMMUNITY, "Crosspost permissions are not available")
    if (destination.accountStableId != activeAccountStableId ||
        normalizeCrosspostComposerCommunity(destination.community) != target
    ) {
        return issue(ComposerValidationField.COMMUNITY, "Reload permissions for this account and destination")
    }
    crosspostDestinationSupportBlockingMessage(destination)?.let { message ->
        return issue(
            ComposerValidationField.COMMUNITY,
            message,
        )
    }
    if (destination.userIsBanned != false || destination.userCanSubmit == false) {
        return issue(ComposerValidationField.COMMUNITY, "Posting permission is denied or not confirmed")
    }
    val restrictedGrant = destination.userCanSubmit == true ||
        destination.userIsContributor == true || destination.userIsModerator == true
    when (destination.subredditType?.trim()?.lowercase(Locale.ROOT)) {
        "public" -> Unit
        "restricted", "private" -> if (!restrictedGrant) {
            return issue(ComposerValidationField.COMMUNITY, "Posting here requires confirmed community permission")
        }
        else -> return issue(ComposerValidationField.COMMUNITY, "This destination type is not supported yet")
    }
    // CROSSPOST01A1E4_SHARED_POST_REQUIREMENTS_PREFLIGHT_V1
    when (composerSubmitPreflightAction(policy)) {
        ComposerSubmitPreflightAction.AUTHORIZE -> {
            return issue(
                ComposerValidationField.COMMUNITY,
                "Authorize access to the destination's posting requirements",
            )
        }

        ComposerSubmitPreflightAction.VALIDATE,
        ComposerSubmitPreflightAction.FALLBACK_TO_REDDIT,
        -> Unit
    }
    if (destination.nsfw == true && !draft.nsfw) {
        return issue(ComposerValidationField.GENERAL, "Mark this post NSFW for the destination community")
    }
    if (policy.flairAuthorizationRequired || (policy.flairRequired && !policy.flairOptionsLoaded)) {
        return issue(ComposerValidationField.FLAIR, "Load or authorize the destination's post flair")
    }
    draft.flair?.let { selected ->
        if (!policy.flairOptionsLoaded) {
            return issue(ComposerValidationField.FLAIR, "Reload the destination's post flair")
        }
        val option = policy.flairOptions.singleOrNull { it.templateId == selected.templateId }
            ?: return issue(ComposerValidationField.FLAIR, "Choose a current flair from the destination community")
        if (selected.text.length > COMPOSER_FLAIR_TEXT_MAX_LENGTH ||
            (!option.editable && selected.text != option.text) ||
            (option.editable && selected.text.isBlank())
        ) {
            return issue(ComposerValidationField.FLAIR, "Use valid text for the selected post flair")
        }
    }
    if (policy.domainWhitelist.isNotEmpty() || policy.domainBlacklist.isNotEmpty()) {
        // No source URL is retained in the capsule. Never invent a domain from the wrapper URL.
        return issue(ComposerValidationField.GENERAL, "Source-domain requirements need additional verification")
    }
    // Reuse title, flair-required and body-requirement validation only; no TEXT request is built.
    // A required body fails closed because native crosspost submit cannot replace the source body.
    return validateRemoteTextPostComposerDraftIssue(
        draft.copy(type = ComposerPostType.TEXT, crosspostSource = null),
        policy.copy(allowedPostTypes = setOf(ComposerPostType.TEXT)),
    )
}

// Pure request assembly for A1D's reviewed submit route. This function never performs a write.
internal fun PostComposerDraft.toCrosspostWriteRequest(
    activeAccountStableId: String,
    activeAccountIsAuthenticated: Boolean,
    policy: ComposerCommunityPolicy,
): TextPostWriteRequest? {
    if (validateCrosspostComposerDraftIssue(this, activeAccountStableId, activeAccountIsAuthenticated, policy) != null) {
        return null
    }
    val source = crosspostSource ?: return null
    return runCatching {
        TextPostWriteRequest(
            accountStableId = activeAccountStableId,
            community = requireNotNull(normalizeCrosspostComposerCommunity(community)),
            title = title.trim(),
            text = "",
            sendReplies = sendReplies,
            nsfw = nsfw,
            spoiler = spoiler,
            flairId = flair?.templateId,
            flairText = flair?.text,
            crosspostFullname = source.fullname,
        )
    }.getOrNull()
}
