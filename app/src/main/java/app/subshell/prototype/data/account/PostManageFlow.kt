package app.subshell.prototype.data.account
import app.subshell.prototype.data.error.FailureDetails

enum class PostManagePhase {
    IDLE,
    LOADING,
    AUTHORIZATION_REQUIRED,
    AUTHORIZING,
    AWAITING_CALLBACK,
    SENDING,
    FAILED,
    SUCCEEDED,
}

enum class PostManageOperation {
    FLAIR_ACCESS,
    SET_FLAIR,
    SET_NSFW,
    SET_SPOILER,
    SET_SEND_REPLIES,
}

enum class PostManageFailureKind {
    CONFIGURATION,
    ACCOUNT_CHANGED,
    NOT_OWNER,
    BROWSER_LAUNCH,
    CALLBACK,
    AUTHORIZATION,
    SESSION,
    RATE_LIMIT,
    HTTP,
    TRANSPORT,
    PROTOCOL,
    ALREADY_MUTATING,
}

data class PostManageTarget(
    val accountStableId: String,
    val postFullname: String,
    val postAuthor: String,
    val community: String,
) {
    init {
        require(accountStableId.isNotBlank()) { "Post-manage account ID cannot be blank" }
        require(postAuthor.isNotBlank()) { "Post-manage author cannot be blank" }
        require(normalizePostEditFullname(postFullname) == postFullname) {
            "Post-manage target must use a canonical t3 fullname"
        }
        require(normalizePostManageCommunity(community) == community) {
            "Post-manage community must be canonical"
        }
    }
}

data class PostManageFlairOption(
    val templateId: String,
    val text: String,
    val editable: Boolean = false,
) {
    init {
        require(templateId.isNotBlank()) { "Post flair template ID cannot be blank" }
        require(templateId.length <= 64) { "Post flair template ID is too long" }
        require(text.length <= POST_MANAGE_FLAIR_TEXT_MAX_CHARS) {
            "Post flair text is too long"
        }
    }
}

data class PostManageSnapshot(
    val postFullname: String,
    val author: String,
    val community: String,
    val flairTemplateId: String? = null,
    val flairText: String? = null,
    val nsfw: Boolean = false,
    val spoiler: Boolean = false,
    val sendReplies: Boolean = true,
    val flairOptions: List<PostManageFlairOption> = emptyList(),
    val flairOptionsLoaded: Boolean = false,
    val flairAuthorizationRequired: Boolean = false,
) {
    init {
        require(normalizePostEditFullname(postFullname) == postFullname) {
            "Post-manage snapshot target must use a canonical t3 fullname"
        }
        require(author.isNotBlank()) { "Post-manage snapshot author cannot be blank" }
        require(normalizePostManageCommunity(community) == community) {
            "Post-manage snapshot community must be canonical"
        }
        require(
            flairOptions.distinctBy(PostManageFlairOption::templateId).size ==
                flairOptions.size,
        ) {
            "Post-manage flair options must be unique"
        }
    }
}

data class PostManageMutationRequest(
    val target: PostManageTarget,
    val operation: PostManageOperation,
    val enabled: Boolean? = null,
    val flairTemplateId: String? = null,
    val flairText: String? = null,
) {
    init {
        when (operation) {
            PostManageOperation.FLAIR_ACCESS -> {
                require(enabled == null && flairTemplateId == null && flairText == null)
            }
            PostManageOperation.SET_FLAIR -> {
                // LOWUX3_POST_FLAIR_CAN_BE_REMOVED_V1 (OCB-233): no template means
                // "no flair"; it carries no text.
                if (flairTemplateId == null) {
                    require(flairText.isNullOrEmpty()) { "Removing post flair carries no text" }
                } else {
                    require(flairTemplateId.isNotBlank()) {
                        "Post flair mutation requires a template ID"
                    }
                    require(flairTemplateId.length <= 64) {
                        "Post flair template ID is too long"
                    }
                    require(flairText.orEmpty().length <= POST_MANAGE_FLAIR_TEXT_MAX_CHARS) {
                        "Post flair text is too long"
                    }
                }
                require(enabled == null)
            }
            PostManageOperation.SET_NSFW,
            PostManageOperation.SET_SPOILER,
            PostManageOperation.SET_SEND_REPLIES,
            -> {
                require(enabled != null) { "Boolean post metadata mutation requires a value" }
                require(flairTemplateId == null && flairText == null)
            }
        }
    }
}

data class PostManageUiState(
    val phase: PostManagePhase = PostManagePhase.IDLE,
    val target: PostManageTarget? = null,
    val operation: PostManageOperation? = null,
    val snapshot: PostManageSnapshot? = null,
    val failureKind: PostManageFailureKind? = null,
    val retryable: Boolean = false,
    val retryAfterMs: Long? = null,
    val httpStatusCode: Int? = null,
    val failureDetails: FailureDetails? = null,
    val completionRevision: Long = 0L,
) {
    val inProgress: Boolean
        get() = phase in setOf(
            PostManagePhase.LOADING,
            PostManagePhase.AUTHORIZING,
            PostManagePhase.AWAITING_CALLBACK,
            PostManagePhase.SENDING,
        )

    // ACCOUNT03_MATCHES_NEVER_THROW_V1: an id the normalizer refuses matches nothing.
    fun matches(accountStableId: String, postFullname: String): Boolean =
        target?.accountStableId == accountStableId &&
            runCatching { normalizePostEditFullname(postFullname) }.getOrNull()
                ?.let { normalized -> target.postFullname == normalized } == true
}

internal fun applyPostManageMutation(
    snapshot: PostManageSnapshot,
    request: PostManageMutationRequest,
): PostManageSnapshot = when (request.operation) {
    PostManageOperation.FLAIR_ACCESS ->
        snapshot.copy(flairAuthorizationRequired = false)
    PostManageOperation.SET_FLAIR ->
        snapshot.copy(
            flairTemplateId = request.flairTemplateId,
            flairText = request.flairText?.trim()?.takeIf(String::isNotEmpty),
        )
    PostManageOperation.SET_NSFW ->
        snapshot.copy(nsfw = requireNotNull(request.enabled))
    PostManageOperation.SET_SPOILER ->
        snapshot.copy(spoiler = requireNotNull(request.enabled))
    PostManageOperation.SET_SEND_REPLIES ->
        snapshot.copy(sendReplies = requireNotNull(request.enabled))
}

internal fun normalizePostManageCommunity(value: String): String {
    val trimmed = value.trim()
    val candidate = when {
        trimmed.startsWith("/r/", ignoreCase = true) -> trimmed.drop(3)
        trimmed.startsWith("r/", ignoreCase = true) -> trimmed.drop(2)
        else -> trimmed
    }.lowercase()
    require(candidate.length in 1..100) { "Post-manage community length is invalid" }
    require(
        candidate.all { character ->
            character.isLetterOrDigit() || character == '_' || character == '-'
        },
    ) {
        "Post-manage community contains unsupported characters"
    }
    return candidate
}

internal const val POST_MANAGE_FLAIR_TEXT_MAX_CHARS = 64
