package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.error.failureDetailsOrNull
import app.subshell.prototype.data.error.LocalFailureKind
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.account.UserFlairFailureKind
import app.subshell.prototype.data.account.UserFlairMutationRequest
import app.subshell.prototype.data.account.UserFlairOption
import app.subshell.prototype.data.account.UserFlairSnapshot
import app.subshell.prototype.data.account.normalizeUserFlairCommunity
import app.subshell.prototype.data.account.normalizedUserFlairMutationRequest
import app.subshell.prototype.data.auth.AuthAccountSessionCoordinator
import app.subshell.prototype.data.auth.AuthCommentWriteTokenSource
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.AuthenticatedRedditUserFlairLoadResult
import app.subshell.prototype.data.auth.AuthenticatedRedditUserFlairManager
import app.subshell.prototype.data.auth.AuthenticatedRedditUserFlairMutationResult
import app.subshell.prototype.data.network.FormPostRequest
import app.subshell.prototype.data.network.JsonGetRequest
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.data.network.reddit.RedditApiTypeJsonResponseDto
import app.subshell.prototype.data.network.reddit.RedditFlairRichTextDto
import app.subshell.prototype.data.network.reddit.apiErrors
import app.subshell.prototype.data.network.reddit.toFlairRichTextSegments
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.Call

@Serializable
internal data class RedditUserFlairSelectorDto(
    val current: RedditUserFlairChoiceDto? = null,
    val choices: List<RedditUserFlairChoiceDto> = emptyList(),
)

@Serializable
internal data class RedditUserFlairChoiceDto(
    @SerialName("flair_template_id") val templateId: String? = null,
    @SerialName("flair_text") val text: String? = null,
    @SerialName("flair_text_editable") val editable: Boolean = false,
    @SerialName("flair_text_color") val textColor: String? = null,
    @SerialName("flair_background_color") val backgroundColor: String? = null,
    // USERFLAIR01A3A_SELECTOR_RICHTEXT_V1
    @SerialName("flair_richtext")
    val richText: List<RedditFlairRichTextDto> = emptyList(),
)

// USERFLAIR01A3A1_USER_FLAIR_V2_TEMPLATE_DTO_V1
@Serializable
internal data class RedditUserFlairTemplateV2Dto(
    val id: String? = null,
    val text: String = "",
    @SerialName("text_editable")
    val textEditable: Boolean = false,
    @SerialName("text_color")
    val textColor: String? = null,
    @SerialName("background_color")
    val backgroundColor: String? = null,
    val richtext: List<RedditFlairRichTextDto> = emptyList(),
)

internal fun redditUserFlairSelectorRequest(
    community: String,
    username: String,
): FormPostRequest = FormPostRequest(
    pathSegments = listOf("r", normalizeUserFlairCommunity(community), "api", "flairselector"),
    fields = listOf("name" to username.trim()),
)

// USERFLAIR01A3A1_USER_FLAIR_V2_REQUEST_V1
internal fun redditUserFlairTemplatesV2Request(
    community: String,
): JsonGetRequest = JsonGetRequest(
    pathSegments = listOf(
        "r",
        normalizeUserFlairCommunity(community),
        "api",
        "user_flair_v2",
    ),
    queryParameters = listOf("raw_json" to "1"),
)

internal fun redditUserFlairSelectRequest(
    community: String,
    username: String,
    request: UserFlairMutationRequest,
): FormPostRequest {
    val normalized = request.normalizedUserFlairMutationRequest()
    return FormPostRequest(
        pathSegments = listOf("r", normalizeUserFlairCommunity(community), "api", "selectflair"),
        fields = buildList {
            add("api_type" to "json")
            add("name" to username.trim())
            add("flair_template_id" to normalized.templateId)
            normalized.text?.let { add("text" to it) }
        },
    )
}

internal fun RedditUserFlairChoiceDto.toOption(): UserFlairOption? {
    val id = templateId?.trim()?.takeIf(String::isNotEmpty) ?: return null
    return runCatching {
        UserFlairOption(
            templateId = id.take(256),
            text = text?.trim().orEmpty().take(64),
            editable = editable,
            textColor = textColor?.trim()?.takeIf(String::isNotEmpty),
            backgroundColor = backgroundColor?.trim()?.takeIf(String::isNotEmpty),
            richText = richText.toFlairRichTextSegments(),
        )
    }.getOrNull()
}

// USERFLAIR01A3A1_V2_TEMPLATE_MERGE_V1
private fun RedditUserFlairTemplateV2Dto.toUserFlairOption():
    UserFlairOption? {
    val template = id
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: return null

    return runCatching {
        UserFlairOption(
            templateId = template.take(256),
            text = text.trim().take(64),
            editable = textEditable,
            textColor = textColor
                ?.trim()
                ?.takeIf(String::isNotEmpty),
            backgroundColor = backgroundColor
                ?.trim()
                ?.takeIf(String::isNotEmpty),
            richText = richtext.toFlairRichTextSegments(),
        )
    }.getOrNull()
}

// USERFLAIR01A3B_CURRENT_OPTION_TEXT_MATCH_V1
private fun List<UserFlairOption>.uniqueCurrentTextMatch(
    currentText: String?,
): UserFlairOption? {
    val normalized = currentText
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: return null

    val matches = asSequence()
        .filter { option ->
            option.text.trim() == normalized
        }
        .take(2)
        .toList()

    return matches.singleOrNull()
}

internal fun RedditUserFlairSelectorDto.toSnapshot(
    community: String,
    templatesV2: List<RedditUserFlairTemplateV2Dto> = emptyList(),
): UserFlairSnapshot {
    val v2ByTemplateId = templatesV2
        .mapNotNull(RedditUserFlairTemplateV2Dto::toUserFlairOption)
        .associateBy(UserFlairOption::templateId)

    val mergedOptions = choices
        .mapNotNull(RedditUserFlairChoiceDto::toOption)
        .map { selectorOption ->
            val v2 = v2ByTemplateId[selectorOption.templateId]
                ?: return@map selectorOption

            selectorOption.copy(
                // USERFLAIR01A3B_V2_COLOR_ENRICHMENT_V1
                textColor = selectorOption.textColor
                    ?: v2.textColor,
                backgroundColor = selectorOption.backgroundColor
                    ?: v2.backgroundColor,
                richText = selectorOption.richText
                    .ifEmpty { v2.richText },
            )
        }
        .distinctBy(UserFlairOption::templateId)

    val currentTemplateId = current
        ?.templateId
        ?.trim()
        ?.takeIf(String::isNotEmpty)

    val currentMergedOption = currentTemplateId
        ?.let { templateId ->
            mergedOptions.firstOrNull { option ->
                option.templateId == templateId
            }
        }
        ?: mergedOptions.uniqueCurrentTextMatch(current?.text)

    val resolvedCurrentTemplateId =
        currentTemplateId
            ?: currentMergedOption?.templateId

    val currentV2 = resolvedCurrentTemplateId
        ?.let(v2ByTemplateId::get)

    return UserFlairSnapshot(
        communityName = normalizeUserFlairCommunity(community),
        currentTemplateId = resolvedCurrentTemplateId,
        currentText = current
            ?.text
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.take(64),
        currentTextColor = current
            ?.textColor
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: currentMergedOption?.textColor
            ?: currentV2?.textColor,
        currentBackgroundColor = current
            ?.backgroundColor
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: currentMergedOption?.backgroundColor
            ?: currentV2?.backgroundColor,
        currentRichText = current
            ?.richText
            .orEmpty()
            .toFlairRichTextSegments()
            .ifEmpty {
                currentMergedOption?.richText.orEmpty()
            }
            .ifEmpty {
                currentV2?.richText.orEmpty()
            },
        options = mergedOptions,
    )
}


// USERFLAIR01A1_OWNER_BOUND_SELECTOR_AND_NONRETRYING_WRITE_V1
internal class RedditUserFlairManager(
    private val capability: AuthCommentWriteTokenSource,
    private val readCallFactory: Call.Factory,
    private val writeCallFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val accountCoordinator: AuthAccountSessionCoordinator,
    private val activeAccountStableId: () -> String?,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AuthenticatedRedditUserFlairManager {
    override suspend fun load(
        accountStableId: String,
        rawCommunityName: String,
    ): AuthenticatedRedditUserFlairLoadResult = withContext(ioDispatcher) {
        val account = accountStableId.trim()
        val community = runCatching { normalizeUserFlairCommunity(rawCommunityName) }
            .getOrElse {
                return@withContext AuthenticatedRedditUserFlairLoadResult.Failed(
                    UserFlairFailureKind.VALIDATION,
                )
            }
        if (account.isEmpty() || activeAccountStableId()?.trim() != account) {
            return@withContext AuthenticatedRedditUserFlairLoadResult.Failed(
                UserFlairFailureKind.ACCOUNT_CHANGED,
            )
        }
        val session = when (val auth = capability.ensureAuthorized(account)) {
            is AuthSessionResult.Authenticated -> auth.session
            AuthSessionResult.ReauthenticationRequired,
            is AuthSessionResult.AuthorizationDenied,
            -> return@withContext AuthenticatedRedditUserFlairLoadResult.AuthorizationRequired
            AuthSessionResult.ClientConfigurationMismatch ->
                return@withContext AuthenticatedRedditUserFlairLoadResult.Failed(
                    UserFlairFailureKind.CONFIGURATION,
                )
            else -> return@withContext AuthenticatedRedditUserFlairLoadResult.Failed(
                UserFlairFailureKind.SESSION,
                retryable = true,
            )
        }
        if (!session.tokens.grants(setOf("flair"))) {
            return@withContext AuthenticatedRedditUserFlairLoadResult.AuthorizationRequired
        }
        val username = session.identity?.username?.trim()?.takeIf(String::isNotEmpty)
            ?: return@withContext AuthenticatedRedditUserFlairLoadResult.Failed(
                UserFlairFailureKind.SESSION,
            )
        if (activeAccountStableId()?.trim() != account) {
            return@withContext AuthenticatedRedditUserFlairLoadResult.Failed(
                UserFlairFailureKind.ACCOUNT_CHANGED,
            )
        }
        val client = SubshellHttpClient(
            callFactory = readCallFactory,
            endpoint = endpoint,
            json = SubshellNetworkJson.providerJson,
            userAgent = userAgent,
            bearerTokenProvider =
                accountCoordinator.sessionBoundBearerTokenProvider(session.tokens.accessToken),
        )
        try {
            when (
                val result = client.postFormJson(
                    request = redditUserFlairSelectorRequest(community, username),
                    deserializer = RedditUserFlairSelectorDto.serializer(),
                )
            ) {
                is NetworkResult.Success -> {
                    // USERFLAIR01A3A1_V2_TEMPLATE_ENRICHMENT_V1
                    val templatesV2 = when (
                        val templatesResult = client.getJson(
                            redditUserFlairTemplatesV2Request(community),
                            kotlinx.serialization.builtins.ListSerializer(
                                RedditUserFlairTemplateV2Dto.serializer(),
                            ),
                        )
                    ) {
                        is NetworkResult.Success -> templatesResult.value
                        else -> emptyList()
                    }

                    AuthenticatedRedditUserFlairLoadResult.Loaded(
                        result.value.toSnapshot(
                            community = community,
                            templatesV2 = templatesV2,
                        ),
                    )
                }
                is NetworkResult.HttpFailure ->
                    AuthenticatedRedditUserFlairLoadResult.Failed(
                        UserFlairFailureKind.HTTP,
                        retryable = result.retryable,
                        httpStatusCode = result.statusCode,
                        details = result.failureDetailsOrNull(),
                    )
                is NetworkResult.TransportFailure ->
                    AuthenticatedRedditUserFlairLoadResult.Failed(
                        UserFlairFailureKind.TRANSPORT,
                        retryable = result.retryable,
                        details = result.failureDetailsOrNull(),
                    )
                is NetworkResult.ProtocolFailure ->
                    AuthenticatedRedditUserFlairLoadResult.Failed(
                        UserFlairFailureKind.PROTOCOL,
                        details = result.failureDetailsOrNull(),
                    )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AuthenticatedRedditUserFlairLoadResult.Failed(
                UserFlairFailureKind.PROTOCOL,
                details = FailureDetails(retryable = false, localKind = LocalFailureKind.UNKNOWN).withExceptionClass(error),
            )
        }
    }

    override suspend fun mutate(
        request: UserFlairMutationRequest,
    ): AuthenticatedRedditUserFlairMutationResult = withContext(ioDispatcher) {
        val normalized = try {
            request.normalizedUserFlairMutationRequest()
        } catch (_: IllegalArgumentException) {
            return@withContext AuthenticatedRedditUserFlairMutationResult.Failed(
                UserFlairFailureKind.VALIDATION,
            )
        }
        if (activeAccountStableId()?.trim() != normalized.accountStableId) {
            return@withContext AuthenticatedRedditUserFlairMutationResult.Failed(
                UserFlairFailureKind.ACCOUNT_CHANGED,
            )
        }
        val session = when (val auth = capability.ensureAuthorized(normalized.accountStableId)) {
            is AuthSessionResult.Authenticated -> auth.session
            AuthSessionResult.ReauthenticationRequired,
            is AuthSessionResult.AuthorizationDenied,
            -> return@withContext AuthenticatedRedditUserFlairMutationResult.AuthorizationRequired
            AuthSessionResult.ClientConfigurationMismatch ->
                return@withContext AuthenticatedRedditUserFlairMutationResult.Failed(
                    UserFlairFailureKind.CONFIGURATION,
                )
            else -> return@withContext AuthenticatedRedditUserFlairMutationResult.Failed(
                UserFlairFailureKind.SESSION,
            )
        }
        // DATALAYER12: ensureAuthorized can suspend on a token refresh; an account
        // switch in that window means the request no longer belongs to the active
        // account (as MODERATION02A2 RECHECK_AFTER_AUTHORIZATION in the other writers).
        if (activeAccountStableId()?.trim() != normalized.accountStableId) {
            return@withContext AuthenticatedRedditUserFlairMutationResult.Failed(
                UserFlairFailureKind.ACCOUNT_CHANGED,
            )
        }
        if (!session.tokens.grants(setOf("flair"))) {
            return@withContext AuthenticatedRedditUserFlairMutationResult.AuthorizationRequired
        }
        val username = session.identity?.username?.trim()?.takeIf(String::isNotEmpty)
            ?: return@withContext AuthenticatedRedditUserFlairMutationResult.Failed(
                UserFlairFailureKind.SESSION,
            )
        val client = SubshellHttpClient(
            callFactory = writeCallFactory,
            endpoint = endpoint,
            json = SubshellNetworkJson.providerJson,
            userAgent = userAgent,
            bearerTokenProvider =
                accountCoordinator.sessionBoundBearerTokenProvider(session.tokens.accessToken),
        )
        try {
            when (
                val result = client.postFormJson(
                    redditUserFlairSelectRequest(
                        normalized.communityName,
                        username,
                        normalized,
                    ),
                    RedditApiTypeJsonResponseDto.serializer(),
                )
            ) {
                // FLAIR02_API_TYPE_JSON_ERRORS_V1: a refusal arrives as HTTP 200 with json.errors.
                is NetworkResult.Success ->
                    if (result.value.apiErrors().isEmpty()) {
                        AuthenticatedRedditUserFlairMutationResult.Acknowledged
                    } else {
                        AuthenticatedRedditUserFlairMutationResult.Failed(
                            UserFlairFailureKind.HTTP,
                            details = FailureDetails(retryable = false)
                                .withProviderDetails(code = result.value.apiErrors().firstOrNull()?.code),
                        )
                    }
                is NetworkResult.HttpFailure ->
                    AuthenticatedRedditUserFlairMutationResult.Failed(
                        UserFlairFailureKind.HTTP,
                        httpStatusCode = result.statusCode,
                        details = result.failureDetailsOrNull(),
                    )
                is NetworkResult.TransportFailure ->
                    AuthenticatedRedditUserFlairMutationResult.Failed(
                        UserFlairFailureKind.OUTCOME_UNKNOWN,
                        details = result.failureDetailsOrNull(),
                    )
                is NetworkResult.ProtocolFailure ->
                    AuthenticatedRedditUserFlairMutationResult.Failed(
                        UserFlairFailureKind.PROTOCOL,
                        details = result.failureDetailsOrNull(),
                    )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AuthenticatedRedditUserFlairMutationResult.Failed(
                UserFlairFailureKind.OUTCOME_UNKNOWN,
                details = FailureDetails(localKind = LocalFailureKind.UNKNOWN).withExceptionClass(error),
            )
        }
    }
}
