package app.subshell.prototype.visual

import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.IdentityMetadataRepository
import app.subshell.prototype.data.RedditAccountHistoryKind
import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.data.RepositoryPageRequest
import app.subshell.prototype.data.RepositoryResult
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.SubshellRepository
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.RedditAccountHistoryUiState
import app.subshell.prototype.data.account.RedditCommentsUiState
import app.subshell.prototype.data.account.RedditInboxUiState
import app.subshell.prototype.data.account.RedditSubscriptionsUiState
import app.subshell.prototype.data.redditAccountHistoryQueryKey
import app.subshell.prototype.model.Comment
import app.subshell.prototype.model.Community
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.model.UserFixture

/**
 * VISUAL01: SampleData shaped the way the signed-in app receives it, so the
 * captured surfaces show content instead of "Preparing your feed".
 */
internal object VisualFixtures {
    val accounts: List<AccountSessionSummary> = SampleData.accounts.map { username ->
        AccountSessionSummary(
            stableId = "reddit:$username",
            username = username,
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
    }
    val active: AccountSessionSummary = accounts.first()

    /**
     * SampleData's posts with Reddit-shaped ids (theirs have hyphens, which no
     * Reddit id has, so the vote bar stayed disabled) and a few cast votes, so
     * enabled and voted vote bars are captured.
     */
    val posts: List<Post> = run {
        val upvoted = SampleData.posts.indexOfFirst { it.kind == PostKind.IMAGE }
        val downvoted = SampleData.posts.indexOfFirst { it.kind == PostKind.TEXT }
        SampleData.posts.mapIndexed { index, post ->
            post.copy(
                id = "v${index.toString(36)}",
                vote = when (index) {
                    upvoted -> 1
                    downvoted -> -1
                    else -> 0
                },
            )
        }
    }

    /** The SampleData post behind a visual id (its comments are keyed by it). */
    fun original(postId: String): Post = SampleData.posts[posts.indexOfFirst { it.id == postId }]

    /** Joined communities: opens the Home landing and fills Communities. */
    val subscriptions = RedditSubscriptionsUiState(
        accountStableId = active.stableId,
        communities = SampleData.communities,
        loaded = true,
    )

    val inbox = RedditInboxUiState(
        accountStableId = active.stableId,
        items = SampleData.inbox,
        loaded = true,
    )

    val profileComments = RedditAccountHistoryUiState(
        accountStableId = active.stableId,
        username = active.username,
        kind = RedditAccountHistoryKind.COMMENTS,
        queryKey = redditAccountHistoryQueryKey(
            active.stableId,
            active.username,
            RedditAccountHistoryKind.COMMENTS,
        ),
        comments = SampleData.profileComments,
        loaded = true,
    )

    fun comments(postId: String, sort: RedditCommentSort, comments: List<Comment>) =
        RedditCommentsUiState(
            accountStableId = active.stableId,
            postId = postId,
            sort = sort,
            comments = comments,
            fullDiscussionLoaded = true,
            contentRevision = 1L,
        )
}

/**
 * The fake repository pages SampleData in file order, whose first page is
 * all video-lab posts. Feeds get the post kinds interleaved instead, and a
 * community feed gets only that community's posts.
 */
internal class VisualRepository(
    private val fake: FakeSubshellRepository = FakeSubshellRepository(),
) : SubshellRepository by fake {
    override fun posts(request: RepositoryPageRequest): RepositoryResult<List<Post>> {
        val community = request.queryKey.value
            .substringAfter(COMMUNITY_KEY, missingDelimiterValue = "")
            .substringBefore('|')
        val posts = if (community.isNotEmpty()) {
            VisualFixtures.posts.filter { it.community.equals(community, ignoreCase = true) }
        } else {
            mixedPosts
        }
        val start = request.cursor?.toIntOrNull() ?: 0
        val page = posts.drop(start).take(request.limit)
        if (page.isEmpty()) return RepositoryResult.Empty()
        val next = (start + page.size).takeIf { it < posts.size }?.toString()
        return RepositoryResult.Content(page, nextCursor = next)
    }

    private companion object {
        const val COMMUNITY_KEY = "live04|community|"

        val mixedPosts: List<Post> = VisualFixtures.posts
            .filterNot { it.nsfw }
            .groupBy { it.kind }
            .values
            .let { groups ->
                buildList {
                    var index = 0
                    while (groups.any { index < it.size }) {
                        groups.forEach { group -> group.getOrNull(index)?.let(::add) }
                        index += 1
                    }
                }
            }
    }
}

/** Community and user headers from SampleData instead of Reddit's about pages. */
internal object VisualIdentityRepository : IdentityMetadataRepository {
    private fun community(name: String) =
        SampleData.communities.firstOrNull { it.name.equals(name, ignoreCase = true) }

    private fun user(name: String) =
        SampleData.users.firstOrNull { it.username.equals(name, ignoreCase = true) }

    override fun cachedCommunityIdentity(name: String): Community? = community(name)

    override suspend fun refreshCommunityIdentity(name: String, fallback: Community): RepositoryResult<Community> =
        RepositoryResult.Content(community(name) ?: fallback)

    override suspend fun refreshCommunityIdentities(fallbacks: List<Community>): RepositoryResult<List<Community>> =
        RepositoryResult.Content(fallbacks.map { community(it.name) ?: it })

    override fun cachedUserIdentity(username: String): UserFixture? = user(username)

    override suspend fun refreshUserIdentity(username: String, fallback: UserFixture): RepositoryResult<UserFixture> =
        RepositoryResult.Content(user(username) ?: fallback)
}
