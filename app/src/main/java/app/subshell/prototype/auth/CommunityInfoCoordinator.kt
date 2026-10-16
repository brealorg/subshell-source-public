package app.subshell.prototype.auth

import app.subshell.prototype.data.account.RedditCommunityInfoSection
import app.subshell.prototype.data.account.RedditCommunityInfoUiFailureKind
import app.subshell.prototype.data.account.RedditCommunityInfoUiState
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityInfoFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityInfoLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityInfoResult
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityInfoSection
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityInfoTarget
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// SUBSHELL_COMMUNITYINFO01_COORDINATOR_V1
/**
 * Community info (about/rules/moderators) reads. Moved verbatim out of
 * AuthConnectViewModel; the ViewModel keeps a one-line forwarder. The
 * ViewModel never cancelled this job in onCleared, so there is no
 * cancelPending() here either.
 */
internal class CommunityInfoCoordinator(
    private val infoLoader: AuthenticatedRedditCommunityInfoLoader?,
    private val scope: CoroutineScope,
) {
    private val _redditCommunityInfoUiState =
        MutableStateFlow(RedditCommunityInfoUiState())
    private var redditCommunityInfoJob: Job? = null

    val redditCommunityInfoUiState:
        StateFlow<RedditCommunityInfoUiState> =
        _redditCommunityInfoUiState.asStateFlow()

    // COMMUNITYINFO01A2_VIEWMODEL_LATEST_REQUEST_WIRING_V1
    fun loadRedditCommunityInfo(
        accountStableId: String,
        rawCommunityName: String,
        forceRefresh: Boolean = false,
    ) {
        val target = try {
            AuthenticatedRedditCommunityInfoTarget.community(rawCommunityName)
        } catch (_: IllegalArgumentException) {
            _redditCommunityInfoUiState.value = RedditCommunityInfoUiState(
                failureKind = RedditCommunityInfoUiFailureKind.PROTOCOL,
            )
            return
        }
        val current = _redditCommunityInfoUiState.value
        if (
            current.matches(accountStableId, target.communityName) &&
            (current.loading || (current.loaded && !forceRefresh))
        ) return

        val loader = infoLoader
        if (loader == null) {
            _redditCommunityInfoUiState.value = RedditCommunityInfoUiState(
                accountStableId = accountStableId,
                communityName = target.communityName,
                failureKind = RedditCommunityInfoUiFailureKind.CLIENT_CONFIGURATION,
                failureDetails = configurationFailureDetails(),
            )
            return
        }

        redditCommunityInfoJob?.cancel()
        val previous = current.takeIf { it.matches(accountStableId, target.communityName) }
        _redditCommunityInfoUiState.value = RedditCommunityInfoUiState(
            accountStableId = accountStableId,
            communityName = target.communityName,
            snapshot = previous?.snapshot,
            loading = true,
            loaded = previous?.loaded == true,
            unavailableSections = previous?.unavailableSections.orEmpty(),
            contentRevision = previous?.contentRevision ?: 0L,
        )
        redditCommunityInfoJob = scope.launch {
            val result = try {
                loader.load(accountStableId, target)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                AuthenticatedRedditCommunityInfoResult.Failed(
                    kind = AuthenticatedRedditCommunityInfoFailureKind.TRANSPORT,
                    retryable = true,
                )
            }
            if (!_redditCommunityInfoUiState.value.matches(accountStableId, target.communityName)) {
                return@launch
            }
            _redditCommunityInfoUiState.value = when (result) {
                is AuthenticatedRedditCommunityInfoResult.Loaded ->
                    _redditCommunityInfoUiState.value.copy(
                        snapshot = result.snapshot,
                        loading = false,
                        loaded = true,
                        unavailableSections = result.sectionFailures
                            .map { it.section.toUiSection() }
                            .toSet(),
                        failureKind = null,
                        failureRetryable = false,
                        failureDetails = null,
                        failureHttpStatusCode = null,
                        contentRevision = _redditCommunityInfoUiState.value.contentRevision + 1L,
                    )
                is AuthenticatedRedditCommunityInfoResult.Failed ->
                    _redditCommunityInfoUiState.value.copy(
                        loading = false,
                        failureKind = result.kind.toCommunityInfoUiFailureKind(),
                        failureRetryable = result.retryable,
                        failureDetails = result.details,
                        failureHttpStatusCode = result.details?.httpStatusCode ?: result.httpStatusCode,
                    )
            }
        }
    }
}

private fun AuthenticatedRedditCommunityInfoFailureKind.toCommunityInfoUiFailureKind():
    RedditCommunityInfoUiFailureKind = when (this) {
    AuthenticatedRedditCommunityInfoFailureKind.CLIENT_CONFIGURATION -> RedditCommunityInfoUiFailureKind.CLIENT_CONFIGURATION
    AuthenticatedRedditCommunityInfoFailureKind.SESSION -> RedditCommunityInfoUiFailureKind.SESSION
    AuthenticatedRedditCommunityInfoFailureKind.HTTP -> RedditCommunityInfoUiFailureKind.HTTP
    AuthenticatedRedditCommunityInfoFailureKind.TRANSPORT -> RedditCommunityInfoUiFailureKind.TRANSPORT
    AuthenticatedRedditCommunityInfoFailureKind.PROTOCOL -> RedditCommunityInfoUiFailureKind.PROTOCOL
}

private fun AuthenticatedRedditCommunityInfoSection.toUiSection(): RedditCommunityInfoSection = when (this) {
    AuthenticatedRedditCommunityInfoSection.SIDEBAR -> RedditCommunityInfoSection.SIDEBAR
    AuthenticatedRedditCommunityInfoSection.RULES -> RedditCommunityInfoSection.RULES
    AuthenticatedRedditCommunityInfoSection.MODERATORS -> RedditCommunityInfoSection.MODERATORS
}
