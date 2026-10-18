package app.subshell.prototype.auth

import app.subshell.prototype.data.account.RedditCommunityPinnedPostsUiState
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityPinnedPostsLoader
import app.subshell.prototype.data.normalizeRedditCommunityName
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ISSUE230_PINNED_POSTS_FIRST_V1: the latest pinned-posts read for one account
 * and community. A refresh keeps the posts already shown; an answer for an
 * account or community no longer open is dropped; a failure keeps what was
 * there (the feed then shows its listing as it is).
 */
internal class CommunityPinnedPostsCoordinator(
    private val loader: AuthenticatedRedditCommunityPinnedPostsLoader?,
    private val scope: CoroutineScope,
) {
    private val state = MutableStateFlow(RedditCommunityPinnedPostsUiState())
    private var job: Job? = null

    val uiState: StateFlow<RedditCommunityPinnedPostsUiState> = state.asStateFlow()

    fun load(accountStableId: String, rawCommunityName: String, forceRefresh: Boolean = false) {
        val communityName = runCatching { normalizeRedditCommunityName(rawCommunityName) }
            .getOrNull() ?: return
        val current = state.value
        val same = current.matches(accountStableId, communityName)
        if (same && (current.loading || (current.loaded && !forceRefresh))) return
        val active = loader ?: return
        job?.cancel()
        state.value = RedditCommunityPinnedPostsUiState(
            accountStableId = accountStableId,
            communityName = communityName,
            posts = if (same) current.posts else emptyList(),
            loading = true,
            loaded = same && current.loaded,
        )
        job = scope.launch {
            val posts = try {
                active.load(accountStableId, communityName)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            val now = state.value
            if (!now.matches(accountStableId, communityName)) return@launch
            state.value = if (posts != null) {
                now.copy(posts = posts, loading = false, loaded = true)
            } else {
                now.copy(loading = false)
            }
        }
    }
}
