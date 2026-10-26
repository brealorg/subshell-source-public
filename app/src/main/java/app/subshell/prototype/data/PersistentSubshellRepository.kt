package app.subshell.prototype.data

import android.content.Context
import app.subshell.prototype.data.cache.CacheClock
import app.subshell.prototype.data.cache.CachedPostFeedSnapshot
import app.subshell.prototype.data.cache.CachedPostPage
import app.subshell.prototype.data.cache.MutableCacheClock
import app.subshell.prototype.data.cache.RoomSubshellCacheStore
import app.subshell.prototype.data.cache.SubshellCacheCodec
import app.subshell.prototype.data.cache.SubshellCacheDatabase
import app.subshell.prototype.data.cache.SubshellCacheDiagnostics
import app.subshell.prototype.data.cache.SubshellCacheQueryMetadata
import app.subshell.prototype.data.cache.SystemCacheClock
import app.subshell.prototype.model.Comment
import app.subshell.prototype.model.Community
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.ProfileCommentFixture
import app.subshell.prototype.model.UserFixture
import java.io.Closeable

internal interface PersistentCacheRepository {
    fun clearRemoteCache(): RepositoryResult<Unit>

    fun cacheDiagnostics(): SubshellCacheDiagnostics

    fun cacheQueryMetadata(queryKey: RepositoryQueryKey): SubshellCacheQueryMetadata?

    fun localMutationReplaySummary(): LocalMutationReplaySummary
}

internal class PersistentSubshellRepository(
    private val delegate: SubshellRepository,
    private val cacheStore: RoomSubshellCacheStore,
) : SubshellRepository,
    PersistentCacheRepository,
    IdentityMetadataCacheRepository,
    ListStoreChanges,
    PostStoreChanges,
    SettledPostActions,
    Closeable {
    override fun storeSettledVote(accountStableId: String, postFullname: String, direction: Int) {
        if (direction !in -1..1) return
        cacheStore.updateStoredPostLater(accountStableId, postFullname) { post ->
            post.copy(score = post.score + (direction - post.vote), vote = direction)
        }
    }

    override fun storeSettledSave(accountStableId: String, postFullname: String, saved: Boolean) {
        cacheStore.updateStoredPostLater(accountStableId, postFullname) { post -> post.copy(saved = saved) }
    }

    override fun storeEditedPost(accountStableId: String, postFullname: String, transform: (Post) -> Post) {
        cacheStore.updateStoredPostLater(accountStableId, postFullname, transform)
    }

    override fun postStoreChanges(): kotlinx.coroutines.flow.Flow<Unit> = cacheStore.postStoreChanges()

    override fun listStoreChanges(): kotlinx.coroutines.flow.Flow<Unit> = cacheStore.listStoreChanges()

    override val defaultSavedPostIds: Set<String>
        get() = delegate.defaultSavedPostIds

    override val defaultUpvotedPostIds: Set<String>
        get() = delegate.defaultUpvotedPostIds

    override val localUsername: String
        get() = delegate.localUsername

    override val createdPosts: List<Post>
        get() = delegate.createdPosts

    private var latestLocalMutationReplaySummary: LocalMutationReplaySummary =
        LocalMutationReplaySummary.Empty

    init {
        // LOW_REPO_NO_REPLAY_INTO_THE_FIXTURE_FREE_BACKEND_V1 (OCB-457): the app's
        // backend holds no posts, so a replay changes nothing there; it only
        // read Room on the main thread during the first composition.
        if (delegate !is FixtureFreeLocalSubshellRepository) replayLocalMutations()
    }

    override fun posts(request: RepositoryPageRequest): RepositoryResult<List<Post>> {
        if (request.queryKey.isAuthenticatedAccountScoped) {
            return cacheStore.readPostFeedSnapshot(request.queryKey)
                ?.toContentOrEmpty()
                ?: RepositoryResult.Empty("No live feed cached for this account")
        }
        // DEAD03 (OCB-458): account-scoped feeds returned above; no second check.
        val cached = cacheStore.readPostPage(request)
        val useCachedBeforeBackend = when (request.operation) {
            RepositoryLoadOperation.INITIAL -> cached != null && !cached.stale
            RepositoryLoadOperation.APPEND -> cached?.posts?.isNotEmpty() == true
            RepositoryLoadOperation.REFRESH -> false
        }
        if (useCachedBeforeBackend) {
            return requireNotNull(cached).toContentOrEmpty()
        }

        return when (val result = delegate.posts(request)) {
            is RepositoryResult.Content -> {
                cacheStore.writePostResult(request, result)
                result
            }
            is RepositoryResult.Empty -> {
                // LOW_REPO_EMPTY_WRITTEN_ONLY_OVER_SOMETHING_V1 (OCB-459): with
                // nothing cached there is nothing to clear; writing the empty page
                // ran a write transaction on every read of a default feed.
                if (cached != null) cacheStore.writePostResult(request, result)
                result
            }
            RepositoryResult.Loading -> result
            is RepositoryResult.Offline -> cached?.let { page ->
                RepositoryResult.Offline(
                    cached = page.posts,
                    nextCursor = page.nextCursor,
                    message = result.message,
                    generation = page.generation,
                    failureDetails = result.failureDetails,
                )
            } ?: result
            is RepositoryResult.Error -> cached?.let { page ->
                RepositoryResult.Error(
                    message = result.message,
                    cached = page.posts,
                    nextCursor = page.nextCursor,
                    generation = page.generation,
                    failureDetails = result.failureDetails,
                )
            } ?: result
        }
    }

    override fun post(postId: String): RepositoryResult<Post> =
        when (val result = delegate.post(postId)) {
            is RepositoryResult.Content -> result
            is RepositoryResult.Offline -> cacheStore.post(postId)
                ?.let { cached -> RepositoryResult.Offline(cached, message = result.message, failureDetails = result.failureDetails) }
                ?: result
            is RepositoryResult.Error -> cacheStore.post(postId)
                ?.let { cached -> RepositoryResult.Error(result.message, cached, failureDetails = result.failureDetails) }
                ?: result
            RepositoryResult.Loading,
            is RepositoryResult.Empty,
            -> cacheStore.post(postId)
                ?.let { value -> RepositoryResult.Content(value) }
                ?: result
        }

    override fun postForAccount(postId: String, accountStableId: String?): RepositoryResult<Post> =
        when (val result = delegate.post(postId)) {
            is RepositoryResult.Content -> result
            is RepositoryResult.Offline -> cacheStore.postForAccount(postId, accountStableId)
                ?.let { cached -> RepositoryResult.Offline(cached, message = result.message, failureDetails = result.failureDetails) }
                ?: result
            is RepositoryResult.Error -> cacheStore.postForAccount(postId, accountStableId)
                ?.let { cached -> RepositoryResult.Error(result.message, cached, failureDetails = result.failureDetails) }
                ?: result
            RepositoryResult.Loading,
            is RepositoryResult.Empty,
            -> cacheStore.postForAccount(postId, accountStableId)
                ?.let { value -> RepositoryResult.Content(value) }
                ?: result
        }

    override fun comments(
        request: RepositoryPageRequest,
    ): RepositoryResult<List<Comment>> =
        cacheCollection(
            backend = { delegate.comments(request) },
            cached = { cacheStore.comments(postId = null, request = request) },
            onContent = { values -> cacheStore.cacheComments(ALL_COMMENTS_CACHE_KEY, values) },
        )

    override fun commentsForPost(
        postId: String,
        request: RepositoryPageRequest,
    ): RepositoryResult<List<Comment>> =
        cacheCollection(
            backend = { delegate.commentsForPost(postId, request) },
            cached = { cacheStore.comments(postId = postId, request = request) },
            onContent = { values -> cacheStore.cacheComments(postId, values) },
        )

    override fun communities(
        request: RepositoryPageRequest,
    ): RepositoryResult<List<Community>> =
        cacheCollection(
            backend = { delegate.communities(request) },
            cached = { cacheStore.communities(request) },
            onContent = cacheStore::cacheCommunities,
        )

    override fun community(name: String): RepositoryResult<Community> =
        cacheItem(
            backend = { delegate.community(name) },
            cached = { cacheStore.community(name) },
            onContent = { value -> cacheStore.cacheCommunities(listOf(value)) },
        )

    override fun cachedCommunityIdentity(name: String): Community? =
        cacheStore.community(name)

    override fun cacheCommunityIdentity(community: Community) {
        cacheStore.cacheCommunities(listOf(community))
    }

    override fun accounts(): RepositoryResult<List<String>> =
        delegate.accounts()

    override fun users(
        request: RepositoryPageRequest,
    ): RepositoryResult<List<UserFixture>> =
        cacheCollection(
            backend = { delegate.users(request) },
            cached = { cacheStore.users(request) },
            onContent = cacheStore::cacheUsers,
        )

    override fun user(username: String): RepositoryResult<UserFixture> =
        cacheItem(
            backend = { delegate.user(username) },
            cached = { cacheStore.user(username) },
            onContent = { value -> cacheStore.cacheUsers(listOf(value)) },
        )

    override fun cachedUserIdentity(username: String): UserFixture? =
        cacheStore.user(username)

    override fun cacheUserIdentity(user: UserFixture) {
        cacheStore.cacheUsers(listOf(user))
    }

    override fun profileComments(
        username: String?,
        request: RepositoryPageRequest,
    ): RepositoryResult<List<ProfileCommentFixture>> =
        cacheCollection(
            backend = { delegate.profileComments(username, request) },
            cached = { cacheStore.profileComments(username, request) },
            onContent = { values ->
                cacheStore.cacheProfileComments(
                    username = username ?: ALL_PROFILE_COMMENTS_CACHE_KEY,
                    values = values,
                )
            },
        )

    override fun inbox(
        request: RepositoryPageRequest,
    ): RepositoryResult<List<InboxItem>> =
        cacheCollection(
            backend = { delegate.inbox(request) },
            cached = { cacheStore.inbox(request) },
            onContent = cacheStore::cacheInbox,
        )

    override fun inboxItem(itemId: String): RepositoryResult<InboxItem> =
        cacheItem(
            backend = { delegate.inboxItem(itemId) },
            cached = { cacheStore.inboxItem(itemId) },
            onContent = { value -> cacheStore.cacheInbox(listOf(value)) },
        )

    override fun createPost(post: Post): RepositoryResult<Post> =
        delegate.createPost(post).also { result ->
            if (result is RepositoryResult.Content) {
                cacheStore.persistLocalMutation(
                    mutationKey = "created-post:${post.id}",
                    kind = LocalMutationKind.CREATED_POST.storageValue,
                    subjectId = post.id,
                    payload = SubshellCacheCodec.encodePost(post),
                )
            }
        }

    override fun addComment(
        postId: String,
        comment: Comment,
    ): RepositoryResult<Comment> =
        delegate.addComment(postId, comment).also { result ->
            if (result is RepositoryResult.Content) {
                cacheStore.persistLocalMutation(
                    mutationKey = "comment:$postId:${comment.id}",
                    kind = LocalMutationKind.COMMENT.storageValue,
                    subjectId = comment.id,
                    payload = SubshellCacheCodec.encodeMutationFields(
                        postId,
                        SubshellCacheCodec.encodeComment(comment),
                    ),
                )
            }
        }

    override fun recordSaved(
        postId: String,
        saved: Boolean,
    ): RepositoryResult<Unit> =
        delegate.recordSaved(postId, saved).also { result ->
            if (result is RepositoryResult.Content) {
                cacheStore.persistLocalMutation(
                    mutationKey = "saved:$postId",
                    kind = LocalMutationKind.SAVED.storageValue,
                    subjectId = postId,
                    payload = saved.toString(),
                )
            }
        }

    override fun recordVote(
        postId: String,
        direction: Int,
    ): RepositoryResult<Unit> =
        delegate.recordVote(postId, direction).also { result ->
            if (result is RepositoryResult.Content) {
                cacheStore.persistLocalMutation(
                    mutationKey = "vote:$postId",
                    kind = LocalMutationKind.VOTE.storageValue,
                    subjectId = postId,
                    payload = direction.toString(),
                )
            }
        }

    override fun recordPollVote(
        postId: String,
        optionId: String?,
    ): RepositoryResult<Unit> =
        delegate.recordPollVote(postId, optionId).also { result ->
            if (result is RepositoryResult.Content) {
                cacheStore.persistLocalMutation(
                    mutationKey = "poll:$postId",
                    kind = LocalMutationKind.POLL.storageValue,
                    subjectId = postId,
                    payload = optionId.orEmpty(),
                )
            }
        }

    override fun clearRemoteCache(): RepositoryResult<Unit> {
        cacheStore.clearRemoteCache()
        return RepositoryResult.Content(Unit)
    }

    override fun cacheDiagnostics(): SubshellCacheDiagnostics =
        cacheStore.diagnostics()

    override fun cacheQueryMetadata(
        queryKey: RepositoryQueryKey,
    ): SubshellCacheQueryMetadata? =
        cacheStore.queryMetadata(queryKey)

    override fun localMutationReplaySummary(): LocalMutationReplaySummary =
        latestLocalMutationReplaySummary

    override fun close() {
        cacheStore.close()
    }

    internal fun replayLocalMutations(): LocalMutationReplaySummary =
        LocalMutationReplayer(delegate)
            .replay(cacheStore.restoredLocalMutations())
            .also { summary ->
                latestLocalMutationReplaySummary = summary
            }

    private fun <T> cacheItem(
        backend: () -> RepositoryResult<T>,
        cached: () -> T?,
        onContent: (T) -> Unit,
    ): RepositoryResult<T> = when (val result = backend()) {
        is RepositoryResult.Content -> {
            onContent(result.value)
            result
        }
        is RepositoryResult.Offline -> cached()
            ?.let { value -> RepositoryResult.Offline(value, message = result.message, failureDetails = result.failureDetails) }
            ?: result
        is RepositoryResult.Error -> cached()
            ?.let { value -> RepositoryResult.Error(result.message, value, failureDetails = result.failureDetails) }
            ?: result
        RepositoryResult.Loading,
        is RepositoryResult.Empty,
        -> cached()?.let { value -> RepositoryResult.Content(value) } ?: result
    }

    private fun <T> cacheCollection(
        backend: () -> RepositoryResult<List<T>>,
        cached: () -> List<T>,
        onContent: (List<T>) -> Unit,
    ): RepositoryResult<List<T>> = when (val result = backend()) {
        is RepositoryResult.Content -> {
            onContent(result.value)
            result
        }
        is RepositoryResult.Offline -> cached()
            .takeIf { values -> values.isNotEmpty() }
            ?.let { values ->
                RepositoryResult.Offline(
                    cached = values,
                    nextCursor = result.nextCursor,
                    message = result.message,
                    failureDetails = result.failureDetails,
                )
            }
            ?: result
        is RepositoryResult.Error -> cached()
            .takeIf { values -> values.isNotEmpty() }
            ?.let { values ->
                RepositoryResult.Error(
                    message = result.message,
                    cached = values,
                    nextCursor = result.nextCursor,
                    failureDetails = result.failureDetails,
                )
            }
            ?: result
        RepositoryResult.Loading,
        is RepositoryResult.Empty,
        -> cached()
            .takeIf { values -> values.isNotEmpty() }
            ?.let { value -> RepositoryResult.Content(value) }
            ?: result
    }

    private fun CachedPostPage.toContentOrEmpty(): RepositoryResult<List<Post>> =
        if (posts.isEmpty()) {
            RepositoryResult.Empty()
        } else {
            RepositoryResult.Content(
                value = posts,
                nextCursor = nextCursor,
                generation = generation,
                stale = stale,
            )
        }

    private fun CachedPostFeedSnapshot.toContentOrEmpty(): RepositoryResult<List<Post>> =
        if (posts.isEmpty()) {
            RepositoryResult.Empty()
        } else {
            RepositoryResult.Content(
                value = posts,
                nextCursor = remoteNextCursor,
                generation = generation,
                stale = stale,
            )
        }

    companion object {
        private const val ALL_COMMENTS_CACHE_KEY = "__all_comments__"
        private const val ALL_PROFILE_COMMENTS_CACHE_KEY = "__all_profile_comments__"
    }
}

private object ProductionPersistentRepositoryHolder {
    @Volatile
    var repository: SubshellRepository? = null
}

internal fun createPersistentSubshellRepository(
    context: Context,
): SubshellRepository {
    ProductionPersistentRepositoryHolder.repository?.let { return it }
    return synchronized(ProductionPersistentRepositoryHolder) {
        ProductionPersistentRepositoryHolder.repository
            ?: PersistentSubshellRepository(
                delegate = createFixtureFreeLocalSubshellRepository(),
                cacheStore = RoomSubshellCacheStore(
                    database = SubshellCacheDatabase.processShared(context),
                    clock = SystemCacheClock,
                    ownsDatabase = false,
                ),
            ).also { created ->
                ProductionPersistentRepositoryHolder.repository = created
            }
    }
}

internal fun createPersistentSubshellRepositoryForTest(
    context: Context,
    delegate: SubshellRepository = FakeSubshellRepository(),
    clock: CacheClock = MutableCacheClock(0L),
    databaseName: String,
    maxQueryCount: Int = RoomSubshellCacheStore.DEFAULT_MAX_QUERY_COUNT,
    freshForMs: Long = RoomSubshellCacheStore.DEFAULT_FRESH_FOR_MS,
): PersistentSubshellRepository =
    PersistentSubshellRepository(
        delegate = delegate,
        cacheStore = RoomSubshellCacheStore(
            database = SubshellCacheDatabase.open(
                context = context,
                databaseName = databaseName,
            ),
            clock = clock,
            maxQueryCount = maxQueryCount,
            freshForMs = freshForMs,
        ),
    )
