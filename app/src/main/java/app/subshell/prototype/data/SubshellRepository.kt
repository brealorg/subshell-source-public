package app.subshell.prototype.data

import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.model.Comment
import app.subshell.prototype.model.Community
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.ProfileCommentFixture
import app.subshell.prototype.model.UserFixture

/**
 * Transport-neutral result used by every data boundary.
 *
 * DATA-01 stays synchronous because the bundled backend is local, but the result model already
 * represents the states required by a future network/cache implementation.
 */
sealed interface RepositoryResult<out T> {
    data object Loading : RepositoryResult<Nothing>

    data class Content<T>(
        val value: T,
        val refreshing: Boolean = false,
        val nextCursor: String? = null,
        val generation: Int? = null,
        val stale: Boolean = false,
    ) : RepositoryResult<T>

    data class Empty(
        val reason: String? = null,
    ) : RepositoryResult<Nothing>

    data class Offline<T>(
        val cached: T? = null,
        val nextCursor: String? = null,
        val message: String = "Offline",
        val generation: Int? = null,
        val failureDetails: FailureDetails? = null,
    ) : RepositoryResult<T>

    data class Error<T>(
        val message: String,
        val cached: T? = null,
        val nextCursor: String? = null,
        val generation: Int? = null,
        val failureDetails: FailureDetails? = null,
    ) : RepositoryResult<T>
}

internal fun <T> RepositoryResult<T>.valueOrNull(): T? = when (this) {
    is RepositoryResult.Content -> value
    is RepositoryResult.Offline -> cached
    is RepositoryResult.Error -> cached
    RepositoryResult.Loading,
    is RepositoryResult.Empty,
    -> null
}

internal fun <T> RepositoryResult<List<T>>.itemsOrEmpty(): List<T> =
    valueOrNull().orEmpty()

data class RepositoryPageRequest(
    val cursor: String? = null,
    val limit: Int = DEFAULT_LIMIT,
    val queryKey: RepositoryQueryKey = RepositoryQueryKey.Default,
    val operation: RepositoryLoadOperation = RepositoryLoadOperation.INITIAL,
) {
    init {
        require(limit in 1..MAX_LIMIT) { "Repository page limit must be between 1 and $MAX_LIMIT" }
    }

    companion object {
        const val DEFAULT_LIMIT = 100
        const val MAX_LIMIT = 1_000
    }
}

interface FeedRepository {
    fun posts(
        request: RepositoryPageRequest = RepositoryPageRequest(),
    ): RepositoryResult<List<Post>>
}

interface PostRepository {
    fun post(postId: String): RepositoryResult<Post>

    /**
     * ISSUE70_POST_LOOKUP_STAYS_IN_ITS_ACCOUNT_V1: the post as [accountStableId]
     * sees it (its own vote and saved state): that account's row, else the
     * shared row, never another account's row. [post] may return any account's.
     */
    fun postForAccount(postId: String, accountStableId: String?): RepositoryResult<Post> =
        post(postId)

    fun comments(
        request: RepositoryPageRequest = RepositoryPageRequest(),
    ): RepositoryResult<List<Comment>>

    fun commentsForPost(
        postId: String,
        request: RepositoryPageRequest = RepositoryPageRequest(),
    ): RepositoryResult<List<Comment>>
}

interface CommunityRepository {
    fun communities(
        request: RepositoryPageRequest = RepositoryPageRequest(),
    ): RepositoryResult<List<Community>>

    fun community(name: String): RepositoryResult<Community>
}

interface UserRepository {
    val localUsername: String

    fun accounts(): RepositoryResult<List<String>>

    fun users(
        request: RepositoryPageRequest = RepositoryPageRequest(),
    ): RepositoryResult<List<UserFixture>>

    fun user(username: String): RepositoryResult<UserFixture>

    fun profileComments(
        username: String? = null,
        request: RepositoryPageRequest = RepositoryPageRequest(),
    ): RepositoryResult<List<ProfileCommentFixture>>
}

/**
 * Transport-neutral boundary for profile and community chrome loaded on demand.
 *
 * Implementations own remote I/O and persist successful responses before exposing Room-backed
 * domain objects to higher layers.
 */
interface IdentityMetadataRepository {
    fun cachedCommunityIdentity(name: String): Community?

    suspend fun refreshCommunityIdentity(
        name: String,
        fallback: Community,
    ): RepositoryResult<Community>

    suspend fun refreshCommunityIdentities(
        fallbacks: List<Community>,
    ): RepositoryResult<List<Community>> = RepositoryResult.Empty(
        reason = "Batch community identity loading is unavailable",
    )

    fun cachedUserIdentity(username: String): UserFixture?

    suspend fun refreshUserIdentity(
        username: String,
        fallback: UserFixture,
    ): RepositoryResult<UserFixture>

    // ISSUE22_AUTHOR_AVATAR_BATCH_LOOKUP_V1
    /**
     * Avatar URLs for comment authors, keyed by username, from their account
     * fullnames. Not persisted: Content carries only the accounts Reddit
     * answered for; a null value is a known account without an https avatar.
     */
    suspend fun refreshAuthorAvatars(
        fullnamesByUsername: Map<String, String>,
    ): RepositoryResult<Map<String, String?>> = RepositoryResult.Empty(
        reason = "Author avatar lookup is unavailable",
    )
}

interface InboxRepository {
    fun inbox(
        request: RepositoryPageRequest = RepositoryPageRequest(),
    ): RepositoryResult<List<InboxItem>>

    fun inboxItem(itemId: String): RepositoryResult<InboxItem>
}

interface LocalMutationRepository {
    val createdPosts: List<Post>

    fun createPost(post: Post): RepositoryResult<Post>

    fun addComment(postId: String, comment: Comment): RepositoryResult<Comment>

    fun recordSaved(postId: String, saved: Boolean): RepositoryResult<Unit>

    fun recordVote(postId: String, direction: Int): RepositoryResult<Unit>

    fun recordPollVote(postId: String, optionId: String?): RepositoryResult<Unit>
}

interface SubshellRepository :
    FeedRepository,
    PostRepository,
    CommunityRepository,
    UserRepository,
    InboxRepository,
    LocalMutationRepository {
    val defaultSavedPostIds: Set<String>
    val defaultUpvotedPostIds: Set<String>
}

/**
 * PERF03_LIST_CACHE_WRITES_INVALIDATE_V1 (#69): counts single-item writes to
 * the cached community, user and inbox lists (identity lookups, item reads).
 * They change those lists without a repository revision, so a reader that
 * keeps a list per revision also re-reads when this count moves.
 */
internal interface ListCacheWriteCounter {
    val listCacheWrites: Int
}

internal interface IdentityMetadataCacheRepository {
    fun cachedCommunityIdentity(name: String): Community?

    fun cacheCommunityIdentity(community: Community)

    fun cachedUserIdentity(username: String): UserFixture?

    fun cacheUserIdentity(user: UserFixture)
}

enum class FakeRepositoryMode {
    CONTENT,
    REFRESHING,
    LOADING,
    EMPTY,
    OFFLINE,
    ERROR,
}

data class FakeRepositoryMutationSnapshot(
    val createdPostIds: List<String>,
    val commentIdsByPost: Map<String, List<String>>,
    val savedByPost: Map<String, Boolean>,
    val votesByPost: Map<String, Int>,
    val pollVotesByPost: Map<String, String?>,
)


data class FakeRepositoryPageRequestSnapshot(
    val queryKey: RepositoryQueryKey,
    val operation: RepositoryLoadOperation,
    val cursor: String?,
    val limit: Int,
)
