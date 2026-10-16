package app.subshell.prototype.auth

import app.subshell.prototype.data.account.RedditCommunityDiscoveryUiFailureKind
import app.subshell.prototype.data.account.RedditCommunityDiscoveryUiState
import app.subshell.prototype.data.account.RedditPeopleSearchUiState
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityDiscoveryFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityDiscoveryLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityDiscoveryResult
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityDiscoveryTarget
import app.subshell.prototype.data.auth.AuthenticatedRedditPeopleSearchResult
import app.subshell.prototype.data.auth.AuthenticatedRedditPeopleSearchTarget
import app.subshell.prototype.model.CommunityDiscoverySort
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// SUBSHELL_COMMUNITYDISCOVERY01_COORDINATOR_V1
/**
 * Community discovery (search and browse) and the people search it
 * dispatches alongside. Moved verbatim out of AuthConnectViewModel; the
 * ViewModel keeps a one-line forwarder for the discovery load.
 */
internal class CommunityDiscoveryCoordinator(
    private val discoveryLoader: AuthenticatedRedditCommunityDiscoveryLoader?,
    private val scope: CoroutineScope,
    // ISSUE211_SEARCH_FOLLOWS_SHOW_NSFW_V1: "Show NSFW posts" when a load starts.
    private val showNsfwPosts: () -> Boolean = { false },
) {
    private val _redditCommunityDiscoveryUiState =
        MutableStateFlow(RedditCommunityDiscoveryUiState())
    private var redditCommunityDiscoveryJob: Job? = null
    private val _redditMentionSearchUiState = MutableStateFlow(RedditPeopleSearchUiState())

    private class PeopleSearchSlot(
        val read: () -> RedditPeopleSearchUiState,
        val write: (RedditPeopleSearchUiState) -> Unit,
    ) {
        var job: Job? = null
    }

    private val peopleSearchSlot = PeopleSearchSlot(
        read = { _redditCommunityDiscoveryUiState.value.peopleSearch },
        write = { next ->
            _redditCommunityDiscoveryUiState.value =
                _redditCommunityDiscoveryUiState.value.copy(peopleSearch = next)
        },
    )
    private val mentionSearchSlot = PeopleSearchSlot(
        read = { _redditMentionSearchUiState.value },
        write = { next -> _redditMentionSearchUiState.value = next },
    )

    val redditCommunityDiscoveryUiState:
        StateFlow<RedditCommunityDiscoveryUiState> =
        _redditCommunityDiscoveryUiState.asStateFlow()

    val redditMentionSearchUiState: StateFlow<RedditPeopleSearchUiState> =
        _redditMentionSearchUiState.asStateFlow()

    /** ViewModel.onCleared: stop in-flight discovery and people searches. */
    fun cancelPending() {
        redditCommunityDiscoveryJob?.cancel()
        peopleSearchSlot.job?.cancel()
        mentionSearchSlot.job?.cancel()
    }

    fun loadRedditCommunityDiscovery(
        accountStableId: String,
        searchQuery: String?,
        searchSort: CommunityDiscoverySort?,
        autocompleteIncludeOver18: Boolean?,
        forceRefresh: Boolean,
    ) {
        // SEARCH01C2A1_INDEPENDENT_PEOPLE_PROVIDER_DISPATCH
        if (
            !searchQuery.isNullOrBlank() &&
            searchSort == null &&
            autocompleteIncludeOver18 == null
        ) {
            loadRedditPeopleSearch(
                accountStableId = accountStableId,
                rawQuery = searchQuery,
            )
            return
        }
        val target = try {
            when {
                searchQuery.isNullOrBlank() ->
                    AuthenticatedRedditCommunityDiscoveryTarget.popular(showNsfwPosts())
                autocompleteIncludeOver18 != null ->
                    AuthenticatedRedditCommunityDiscoveryTarget.autocomplete(
                        rawQuery = searchQuery,
                        includeOver18 = autocompleteIncludeOver18,
                    )
                else -> AuthenticatedRedditCommunityDiscoveryTarget.search(
                    rawQuery = searchQuery,
                    sort = requireNotNull(searchSort),
                    includeOver18 = showNsfwPosts(),
                )
            }
        } catch (_: IllegalArgumentException) {
            _redditCommunityDiscoveryUiState.value =
                RedditCommunityDiscoveryUiState(
                    accountStableId = accountStableId,
                    failureKind = RedditCommunityDiscoveryUiFailureKind.PROTOCOL,
                    peopleSearch =
                        _redditCommunityDiscoveryUiState.value.peopleSearch,
                )
            return
        }
        val current = _redditCommunityDiscoveryUiState.value
        if (
            current.matches(
                accountStableId,
                target.searchQuery,
                target.searchSort,
                target.autocompleteIncludeOver18,
            ) && current.loading
        ) {
            return
        }
        if (
            current.matches(
                accountStableId,
                target.searchQuery,
                target.searchSort,
                target.autocompleteIncludeOver18,
            ) && current.loaded && !forceRefresh
        ) {
            return
        }

        val loader = discoveryLoader
        if (loader == null) {
            _redditCommunityDiscoveryUiState.value =
                RedditCommunityDiscoveryUiState(
                    accountStableId = accountStableId,
                    searchQuery = target.searchQuery,
                    searchSort = target.searchSort,
                    autocompleteIncludeOver18 = target.autocompleteIncludeOver18,
                    failureKind =
                        RedditCommunityDiscoveryUiFailureKind.CLIENT_CONFIGURATION,
                    failureDetails = configurationFailureDetails(),
                    peopleSearch = current.peopleSearch,
                )
            return
        }

        redditCommunityDiscoveryJob?.cancel()
        val exactPrevious = current.takeIf { state ->
            state.matches(
                accountStableId,
                target.searchQuery,
                target.searchSort,
                target.autocompleteIncludeOver18,
            )
        }
        val autocompleteFallback = current.takeIf { state ->
            target.searchSort != null &&
                state.accountStableId == accountStableId &&
                state.searchQuery.equals(target.searchQuery, ignoreCase = true) &&
                state.isAutocomplete &&
                state.items.isNotEmpty()
        }
        val previous = exactPrevious ?: autocompleteFallback
        _redditCommunityDiscoveryUiState.value =
            RedditCommunityDiscoveryUiState(
                accountStableId = accountStableId,
                searchQuery = target.searchQuery,
                searchSort = target.searchSort,
                autocompleteIncludeOver18 = target.autocompleteIncludeOver18,
                items = previous?.items.orEmpty(),
                itemsAreAutocompleteFallback = autocompleteFallback != null,
                loading = true,
                fromCache = previous?.fromCache == true,
                loaded = previous?.loaded == true,
                contentRevision = previous?.contentRevision ?: 0L,
                peopleSearch = current.peopleSearch,
            )
        authDebugLog(
            COMMUNITY_DISCOVERY_LOG_TAG,
            "start account=$accountStableId target=${target.cacheToken}",
        )
        redditCommunityDiscoveryJob = scope.launch {
            if (!forceRefresh) {
                val cached = try {
                    loader.cached(accountStableId, target)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    AuthenticatedRedditCommunityDiscoveryResult.Failed(
                        kind = AuthenticatedRedditCommunityDiscoveryFailureKind.STORAGE,
                        retryable = true,
                        details = storageFailureDetails(),
                    )
                }
                if (
                    !_redditCommunityDiscoveryUiState.value.matches(
                        accountStableId,
                        target.searchQuery,
                        target.searchSort,
                        target.autocompleteIncludeOver18,
                    )
                ) {
                    return@launch
                }
                if (cached is AuthenticatedRedditCommunityDiscoveryResult.Loaded) {
                    _redditCommunityDiscoveryUiState.value =
                        _redditCommunityDiscoveryUiState.value.copy(
                            items = cached.items,
                            itemsAreAutocompleteFallback = false,
                            fromCache = true,
                            loaded = true,
                            contentRevision =
                                _redditCommunityDiscoveryUiState.value.contentRevision + 1L,
                        )
                    authDebugLog(
                        COMMUNITY_DISCOVERY_LOG_TAG,
                        "cache target=${target.cacheToken} count=${cached.items.size}",
                    )
                }
            }

            val refreshed = try {
                loader.synchronize(accountStableId, target)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                AuthenticatedRedditCommunityDiscoveryResult.Failed(
                    kind = AuthenticatedRedditCommunityDiscoveryFailureKind.STORAGE,
                    retryable = true,
                    details = storageFailureDetails(),
                )
            }
            if (
                !_redditCommunityDiscoveryUiState.value.matches(
                    accountStableId,
                    target.searchQuery,
                    target.searchSort,
                    target.autocompleteIncludeOver18,
                )
            ) {
                return@launch
            }
            _redditCommunityDiscoveryUiState.value = when (refreshed) {
                is AuthenticatedRedditCommunityDiscoveryResult.Loaded -> {
                    authDebugLog(
                        COMMUNITY_DISCOVERY_LOG_TAG,
                        "applied target=${target.cacheToken} count=${refreshed.items.size}",
                    )
                    _redditCommunityDiscoveryUiState.value.copy(
                        items = refreshed.items,
                        itemsAreAutocompleteFallback = false,
                        loading = false,
                        fromCache = false,
                        loaded = true,
                        failureKind = null,
                        failureRetryable = false,
                        failureDetails = null,
                        failureHttpStatusCode = null,
                        contentRevision =
                            _redditCommunityDiscoveryUiState.value.contentRevision + 1L,
                    )
                }
                AuthenticatedRedditCommunityDiscoveryResult.NotCached ->
                    _redditCommunityDiscoveryUiState.value.copy(
                        loading = false,
                        failureKind = RedditCommunityDiscoveryUiFailureKind.STORAGE,
                        failureRetryable = true,
                        failureHttpStatusCode = null,
                    )
                is AuthenticatedRedditCommunityDiscoveryResult.Failed -> {
                    authDebugLog(
                        COMMUNITY_DISCOVERY_LOG_TAG,
                        "failed target=${target.cacheToken} " +
                            "kind=${refreshed.kind.name.lowercase()} " +
                            "status=${refreshed.httpStatusCode ?: "none"} " +
                            "retryable=${refreshed.retryable}",
                    )
                    _redditCommunityDiscoveryUiState.value.copy(
                        loading = false,
                        failureKind = refreshed.kind.toUiFailureKind(),
                        failureRetryable = refreshed.retryable,
                        failureDetails = refreshed.details,
                        failureHttpStatusCode = refreshed.details?.httpStatusCode
                            ?: refreshed.httpStatusCode,
                    )
                }
            }
        }
    }
    private fun loadRedditPeopleSearch(
        accountStableId: String,
        rawQuery: String,
    ) = searchPeopleInto(peopleSearchSlot, accountStableId, rawQuery)

    // ISSUE229_MENTION_SEARCH_HAS_ITS_OWN_SLOT_V1: a composer's mention search
    // uses the same people search but its own state and job, so it never
    // replaces the Search workspace's people results.
    fun loadRedditMentionSearch(
        accountStableId: String,
        rawQuery: String,
    ) = searchPeopleInto(mentionSearchSlot, accountStableId, rawQuery)

    private fun searchPeopleInto(
        slot: PeopleSearchSlot,
        accountStableId: String,
        rawQuery: String,
    ) {
        val target = try {
            AuthenticatedRedditPeopleSearchTarget.search(rawQuery)
        } catch (_: IllegalArgumentException) {
            slot.write(
                RedditPeopleSearchUiState(
                    accountStableId = accountStableId,
                    searchQuery = rawQuery.trim().takeIf(String::isNotEmpty),
                    failureKind = RedditCommunityDiscoveryUiFailureKind.PROTOCOL,
                ),
            )
            return
        }
        val current = slot.read()
        if (
            current.matches(accountStableId, target.searchQuery) &&
            (current.loading || (current.loaded && current.failureKind == null))
        ) {
            return
        }

        val loader = discoveryLoader
        if (loader == null) {
            slot.write(
                RedditPeopleSearchUiState(
                    accountStableId = accountStableId,
                    searchQuery = target.searchQuery,
                    failureKind =
                        RedditCommunityDiscoveryUiFailureKind.CLIENT_CONFIGURATION,
                    failureDetails = configurationFailureDetails(),
                ),
            )
            return
        }

        slot.job?.cancel()
        val previous = current.takeIf { state ->
            state.matches(accountStableId, target.searchQuery)
        }
        slot.write(
            RedditPeopleSearchUiState(
                accountStableId = accountStableId,
                searchQuery = target.searchQuery,
                items = previous?.items.orEmpty(),
                loading = true,
                loaded = previous?.loaded == true,
                contentRevision = previous?.contentRevision ?: 0L,
            ),
        )
        authDebugLog(
            PEOPLE_SEARCH_LOG_TAG,
            "start account=$accountStableId queryLength=${target.searchQuery.length}",
        )
        slot.job = scope.launch {
            val refreshed = try {
                loader.searchPeople(accountStableId, target)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                AuthenticatedRedditPeopleSearchResult.Failed(
                    kind = AuthenticatedRedditCommunityDiscoveryFailureKind.STORAGE,
                    retryable = true,
                    details = storageFailureDetails(),
                )
            }
            val latest = slot.read()
            if (!latest.matches(accountStableId, target.searchQuery)) {
                return@launch
            }
            val next = when (refreshed) {
                is AuthenticatedRedditPeopleSearchResult.Loaded -> {
                    authDebugLog(
                        PEOPLE_SEARCH_LOG_TAG,
                        "applied queryLength=${target.searchQuery.length} count=${refreshed.items.size}",
                    )
                    latest.copy(
                        items = refreshed.items,
                        loading = false,
                        loaded = true,
                        failureKind = null,
                        failureRetryable = false,
                        failureDetails = null,
                        failureHttpStatusCode = null,
                        contentRevision = latest.contentRevision + 1L,
                    )
                }
                is AuthenticatedRedditPeopleSearchResult.Failed -> {
                    authDebugLog(
                        PEOPLE_SEARCH_LOG_TAG,
                        "failed queryLength=${target.searchQuery.length} " +
                            "kind=${refreshed.kind.name.lowercase()} " +
                            "status=${refreshed.httpStatusCode ?: "none"} " +
                            "retryable=${refreshed.retryable}",
                    )
                    latest.copy(
                        loading = false,
                        failureKind = refreshed.kind.toUiFailureKind(),
                        failureRetryable = refreshed.retryable,
                        failureDetails = refreshed.details,
                        failureHttpStatusCode = refreshed.details?.httpStatusCode
                            ?: refreshed.httpStatusCode,
                    )
                }
            }
            slot.write(next)
        }
    }
}

private const val COMMUNITY_DISCOVERY_LOG_TAG = "subshell-discover"
private const val PEOPLE_SEARCH_LOG_TAG = "subshell-people-search"

private fun AuthenticatedRedditCommunityDiscoveryFailureKind.toUiFailureKind():
    RedditCommunityDiscoveryUiFailureKind = when (this) {
    AuthenticatedRedditCommunityDiscoveryFailureKind.CLIENT_CONFIGURATION ->
        RedditCommunityDiscoveryUiFailureKind.CLIENT_CONFIGURATION
    AuthenticatedRedditCommunityDiscoveryFailureKind.SESSION ->
        RedditCommunityDiscoveryUiFailureKind.SESSION
    AuthenticatedRedditCommunityDiscoveryFailureKind.HTTP ->
        RedditCommunityDiscoveryUiFailureKind.HTTP
    AuthenticatedRedditCommunityDiscoveryFailureKind.TRANSPORT ->
        RedditCommunityDiscoveryUiFailureKind.TRANSPORT
    AuthenticatedRedditCommunityDiscoveryFailureKind.PROTOCOL ->
        RedditCommunityDiscoveryUiFailureKind.PROTOCOL
    AuthenticatedRedditCommunityDiscoveryFailureKind.STORAGE ->
        RedditCommunityDiscoveryUiFailureKind.STORAGE
}
