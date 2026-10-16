package app.subshell.prototype.auth

import app.subshell.prototype.data.account.AccountSessionDirectory
import app.subshell.prototype.data.auth.reddit.AuthenticatedComposerCommunitySearchLoader
import app.subshell.prototype.data.auth.reddit.AuthenticatedComposerPolicyLoader
import app.subshell.prototype.data.auth.reddit.AuthenticatedComposerCommunitySearchResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedComposerPolicyResult
import app.subshell.prototype.ui.CommentMediaPolicy
import app.subshell.prototype.ui.ComposerCommunitySearchSnapshot
import app.subshell.prototype.ui.commentMediaPolicyKey
import app.subshell.prototype.ui.ComposerCommunitySearchStatus
import app.subshell.prototype.ui.ComposerPolicySnapshot
import app.subshell.prototype.ui.ComposerPolicyStatus
import app.subshell.prototype.ui.ComposerPostRequirementsStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// SUBSHELL_COMPOSERPOLICY01_COORDINATOR_V1
/**
 * The post composer's community search and posting-policy snapshot, and
 * the reload the ViewModel's callback routing triggers after any
 * capability authorization (flair or post requirements may have just
 * become available). Moved verbatim out of AuthConnectViewModel; the
 * ViewModel keeps one-line forwarders.
 */
internal class ComposerPolicyCoordinator(
    private val composerPolicyLoader: AuthenticatedComposerPolicyLoader?,
    private val composerCommunitySearchLoader: AuthenticatedComposerCommunitySearchLoader?,
    private val userFlairCoordinator: UserFlairCoordinator,
    private val accountSessionDirectory: () -> AccountSessionDirectory?,
    private val scope: CoroutineScope,
) {
    private var composerPolicyJob: Job? = null
    private var composerPolicyRevision = 0L
    private var composerCommunitySearchJob: Job? = null
    private val _composerPolicy = MutableStateFlow(ComposerPolicySnapshot())
    private val _composerCommunitySearch = MutableStateFlow(ComposerCommunitySearchSnapshot())

    val composerPolicy: StateFlow<ComposerPolicySnapshot> =
        _composerPolicy.asStateFlow()

    val composerCommunitySearch: StateFlow<ComposerCommunitySearchSnapshot> =
        _composerCommunitySearch.asStateFlow()

    // COMPOSER10_COMMENT_MEDIA_RULES_V1: comment media rules per account and
    // community (commentMediaPolicyKey), in memory for this process; a
    // community is asked once per process unless the read failed.
    private val _commentMediaPolicies = MutableStateFlow<Map<String, CommentMediaPolicy>>(emptyMap())
    private val commentMediaRequested = mutableSetOf<String>()

    val commentMediaPolicies: StateFlow<Map<String, CommentMediaPolicy>> =
        _commentMediaPolicies.asStateFlow()

    fun loadCommentMediaPolicy(accountStableId: String, community: String) {
        val loader = composerPolicyLoader ?: return
        if (accountStableId.isBlank() || community.isBlank()) return
        val key = commentMediaPolicyKey(accountStableId, community)
        if (!commentMediaRequested.add(key)) return
        scope.launch {
            val policy = unlessFailed(null) { loader.loadCommentMedia(accountStableId, community) }
            if (policy == null) {
                commentMediaRequested.remove(key)
            } else {
                _commentMediaPolicies.value = _commentMediaPolicies.value + (key to policy)
            }
        }
    }

    /** ViewModel.onCleared: stop in-flight policy and community searches. */
    fun cancelPending() {
        composerPolicyJob?.cancel()
        composerCommunitySearchJob?.cancel()
    }

    fun searchComposerCommunities(accountStableId: String, query: String) {
        val normalizedQuery = query.trim().removePrefix("/r/").removePrefix("r/")
        composerCommunitySearchJob?.cancel()
        if (normalizedQuery.length < 2) {
            _composerCommunitySearch.value = ComposerCommunitySearchSnapshot(
                query = normalizedQuery,
            )
            return
        }
        val loader = composerCommunitySearchLoader ?: run {
            _composerCommunitySearch.value = ComposerCommunitySearchSnapshot(
                query = normalizedQuery,
                status = ComposerCommunitySearchStatus.UNAVAILABLE,
            )
            return
        }
        _composerCommunitySearch.value = ComposerCommunitySearchSnapshot(
            query = normalizedQuery,
            status = ComposerCommunitySearchStatus.LOADING,
        )
        composerCommunitySearchJob = scope.launch {
            when (
                val result = unlessFailed(AuthenticatedComposerCommunitySearchResult.Unavailable) {
                    loader.search(accountStableId, normalizedQuery)
                }
            ) {
                is AuthenticatedComposerCommunitySearchResult.Loaded ->
                    _composerCommunitySearch.value = ComposerCommunitySearchSnapshot(
                        query = result.query,
                        status = ComposerCommunitySearchStatus.LOADED,
                        options = result.options,
                    )
                AuthenticatedComposerCommunitySearchResult.AuthorizationRequired,
                AuthenticatedComposerCommunitySearchResult.Unavailable,
                -> _composerCommunitySearch.value = ComposerCommunitySearchSnapshot(
                    query = normalizedQuery,
                    status = ComposerCommunitySearchStatus.UNAVAILABLE,
                )
            }
        }
    }

    fun loadComposerPolicy(accountStableId: String, community: String) {
        val loader = composerPolicyLoader ?: return
        composerPolicyJob?.cancel()
        val revision = ++composerPolicyRevision
        _composerPolicy.value = ComposerPolicySnapshot(
            community = community,
            status = ComposerPolicyStatus.LOADING,
            revision = revision,
        )
        composerPolicyJob = scope.launch {
            when (
                val result = unlessFailed(AuthenticatedComposerPolicyResult.Unavailable) {
                    loader.load(accountStableId, community)
                }
            ) {
                is AuthenticatedComposerPolicyResult.Loaded ->
                    _composerPolicy.value = ComposerPolicySnapshot(
                        community = result.policy.community,
                        status = ComposerPolicyStatus.LOADED,
                        policy = result.policy,
                        revision = revision,
                    )
                AuthenticatedComposerPolicyResult.AuthorizationRequired,
                AuthenticatedComposerPolicyResult.Unavailable,
                -> _composerPolicy.value = ComposerPolicySnapshot(
                    community = community,
                    status = ComposerPolicyStatus.UNAVAILABLE,
                    revision = revision,
                )
            }
        }
    }

    // LOWCRASH01_COMPOSER_LOADERS_NEVER_CRASH_V1 (OCB-584): the other
    // coordinators catch loader exceptions; these launched calls did not, so an
    // unexpected one ended the process. It now reads as unavailable.
    private suspend inline fun <T> unlessFailed(fallback: T, load: () -> T): T = try {
        load()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        fallback
    }

    fun reloadComposerPolicyAfterCapabilityAuthorization() {
        scope.launch { userFlairCoordinator.refreshAfterAuthorization() }
        val snapshot = _composerPolicy.value
        val policy = snapshot.policy ?: return
        val needsReload = policy.flairAuthorizationRequired ||
            policy.postRequirementsStatus ==
            ComposerPostRequirementsStatus.AUTHORIZATION_REQUIRED
        if (!needsReload) return
        val community = snapshot.community?.takeIf(String::isNotBlank) ?: return
        val accountStableId = accountSessionDirectory()
            ?.snapshot()
            ?.activeAccountId
            ?.takeIf(String::isNotBlank)
            ?: return
        loadComposerPolicy(accountStableId, community)
    }
}
