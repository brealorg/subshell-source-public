package app.subshell.prototype.data.cache

import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.RepositoryPageRequest
import app.subshell.prototype.data.RepositoryQueryKey
import app.subshell.prototype.data.RepositoryResult
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.isAuthenticatedAccountScoped
import app.subshell.prototype.data.scopedToAccount
import app.subshell.prototype.data.network.NetworkPage
import app.subshell.prototype.model.Comment
import app.subshell.prototype.model.Community
import app.subshell.prototype.model.CommunityDiscoveryItem
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.ProfileCommentFixture
import app.subshell.prototype.model.UserFixture
import java.io.Closeable
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal fun interface CacheClock {
    fun nowEpochMs(): Long
}

internal object SystemCacheClock : CacheClock {
    override fun nowEpochMs(): Long = System.currentTimeMillis()
}

internal class MutableCacheClock(
    var epochMs: Long,
) : CacheClock {
    override fun nowEpochMs(): Long = epochMs
}

internal data class CachedPostPage(
    val posts: List<Post>,
    val nextCursor: String?,
    val generation: Int,
    val stale: Boolean,
    val lastSuccessfulRefreshEpochMs: Long,
)

internal data class CachedPostFeedSnapshot(
    val posts: List<Post>,
    val remoteNextCursor: String?,
    val generation: Int,
    val terminal: Boolean,
    val stale: Boolean,
    val lastSuccessfulRefreshEpochMs: Long,
)

/** CACHE01_WRITE_RETURNS_STORED_METADATA_V1: the stored query after a page write. */
internal data class CachedPostPageWrite(
    val storedItemCount: Int,
    val generation: Int,
    val remoteNextCursor: String?,
    val terminal: Boolean,
)

internal data class CachedSubscriptionSnapshot(
    val communities: List<Community>,
    val lastSuccessfulRefreshEpochMs: Long,
)

internal data class CachedCommunityDiscoverySnapshot(
    val items: List<CommunityDiscoveryItem>,
    val lastSuccessfulRefreshEpochMs: Long,
)

internal data class CachedLiveInboxSnapshot(
    val items: List<InboxItem>,
    val nextCursor: String?,
    val locallySeenIds: Set<String>,
    val lastSuccessfulRefreshEpochMs: Long,
)

internal fun interface CacheWriteTransactionObserver {
    fun beforeCommit(queryKey: RepositoryQueryKey)
}

private object NoOpCacheWriteTransactionObserver : CacheWriteTransactionObserver {
    override fun beforeCommit(queryKey: RepositoryQueryKey) = Unit
}

internal data class SubshellCacheDiagnostics(
    val queryCount: Int,
    val queryEntryCount: Int,
    val postCount: Int,
    val localMutationCount: Int,
)

internal data class SubshellCacheQueryMetadata(
    val generation: Int,
    val nextCursor: String?,
    val terminal: Boolean,
    val lastSuccessfulRefreshEpochMs: Long,
)

internal data class RestoredLocalMutation(
    val mutationKey: String,
    val kind: String,
    val subjectId: String,
    val payload: String,
    val updatedAtEpochMs: Long,
)

/** Deletes what the offline cache holds for one signed-in account (MED4A). */
internal fun interface AccountCacheRemover {
    fun removeAccount(accountStableId: String)
}

/**
 * OCB102_DELETED_POST_LEAVES_THE_CACHE_V1: a post Reddit confirmed deleted was
 * hidden only in memory; after a restart the cached feed and history pages
 * showed it again until a refresh replaced them.
 */
internal fun interface DeletedPostCacheRemover {
    fun removeDeletedPost(postId: String)
}

internal class RoomSubshellCacheStore(
    private val database: SubshellCacheDatabase,
    private val clock: CacheClock = SystemCacheClock,
    private val maxQueryCount: Int = DEFAULT_MAX_QUERY_COUNT,
    private val freshForMs: Long = DEFAULT_FRESH_FOR_MS,
    private val ownsDatabase: Boolean = true,
    private val maxCommentThreads: Int = DEFAULT_MAX_COMMENT_THREADS,
    private val maxProfileCommentLists: Int = DEFAULT_MAX_PROFILE_COMMENT_LISTS,
    private val transactionObserver: CacheWriteTransactionObserver =
        NoOpCacheWriteTransactionObserver,
) : Closeable, AccountCacheRemover, DeletedPostCacheRemover {
    private val dao = database.cacheDao()
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "subshell-data03-room").apply {
            isDaemon = true
        }
    }

    init {
        require(maxQueryCount > 0) { "Max query count must be positive" }
        require(freshForMs >= 0L) { "Cache freshness window cannot be negative" }
    }

    // CACHE01_READ_TOUCHES_ONLY_LAST_ACCESS_V1: metadata, count and rows are
    // read in one transaction, so another connection's write cannot land in
    // between, and only last access is written back.
    fun readPostPage(request: RepositoryPageRequest): CachedPostPage? = databaseCall {
        database.runInTransaction<CachedPostPage?> { readPostPageLocked(request) }
    }

    private fun readPostPageLocked(request: RepositoryPageRequest): CachedPostPage? {
        val query = dao.queryMetadata(request.queryKey.value) ?: return null
        val offset = when (val cursor = request.cursor) {
            null -> 0
            else -> cursor.toIntOrNull()?.coerceAtLeast(0)
                ?: return null
        }
        val totalEntries = dao.queryEntryCount(request.queryKey.value)
        if (offset > totalEntries) return null

        val entities = dao.queryPosts(
            queryKey = request.queryKey.value,
            offset = offset,
            limit = request.limit,
        )
        if (entities.isEmpty() && offset == totalEntries && query.nextCursor == request.cursor) {
            return null
        }
        if (entities.isEmpty() && totalEntries > 0 && offset < totalEntries) {
            return null
        }

        val posts = entities.decodedPostsOrNull() ?: return null
        val now = clock.nowEpochMs()
        dao.touchQuery(request.queryKey.value, now)
        val consumed = entities.size
        val resolvedNextCursor = when {
            offset + consumed < totalEntries -> (offset + consumed).toString()
            else -> query.nextCursor
        }
        return CachedPostPage(
            posts = posts,
            nextCursor = resolvedNextCursor,
            generation = query.generation,
            stale = now - query.lastSuccessfulRefreshEpochMs > freshForMs,
            lastSuccessfulRefreshEpochMs = query.lastSuccessfulRefreshEpochMs,
        )
    }

    // CACHE01_READ_TOUCHES_ONLY_LAST_ACCESS_V1
    fun readPostFeedSnapshot(
        queryKey: RepositoryQueryKey,
    ): CachedPostFeedSnapshot? = databaseCall {
        database.runInTransaction<CachedPostFeedSnapshot?> { readPostFeedSnapshotLocked(queryKey) }
    }

    private fun readPostFeedSnapshotLocked(
        queryKey: RepositoryQueryKey,
    ): CachedPostFeedSnapshot? {
        val query = dao.queryMetadata(queryKey.value) ?: return null
        val totalEntries = dao.queryEntryCount(queryKey.value)
        val entities = dao.queryPosts(
            queryKey = queryKey.value,
            offset = 0,
            limit = totalEntries,
        )
        if (entities.size != totalEntries) return null
        val posts = entities.decodedPostsOrNull() ?: return null

        val now = clock.nowEpochMs()
        dao.touchQuery(queryKey.value, now)
        return CachedPostFeedSnapshot(
            posts = posts,
            remoteNextCursor = query.nextCursor,
            generation = query.generation,
            terminal = query.terminal,
            stale = now - query.lastSuccessfulRefreshEpochMs > freshForMs,
            lastSuccessfulRefreshEpochMs = query.lastSuccessfulRefreshEpochMs,
        )
    }

    fun copyPostFeedIfAbsent(
        sourceQueryKey: RepositoryQueryKey,
        destinationQueryKey: RepositoryQueryKey,
    ): Boolean = databaseCall {
        database.runInTransaction<Boolean> {
            if (dao.queryMetadata(destinationQueryKey.value) != null) {
                return@runInTransaction false
            }
            val source = dao.queryMetadata(sourceQueryKey.value)
                ?: return@runInTransaction false
            val totalEntries = dao.queryEntryCount(sourceQueryKey.value)
            val sourcePosts = dao.queryPosts(
                queryKey = sourceQueryKey.value,
                offset = 0,
                limit = totalEntries,
            )
            if (sourcePosts.size != totalEntries) {
                return@runInTransaction false
            }
            val now = clock.nowEpochMs()
            dao.upsertQuery(
                source.copy(
                    queryKey = destinationQueryKey.value,
                    lastAccessEpochMs = now,
                ),
            )
            dao.insertQueryEntries(
                sourcePosts.mapIndexed { index, post ->
                    CachedQueryEntryEntity(
                        queryKey = destinationQueryKey.value,
                        postId = post.postId,
                        position = index,
                        generation = source.generation,
                    )
                },
            )
            transactionObserver.beforeCommit(destinationQueryKey)
            evictLeastRecentlyUsedQueriesLocked()
            true
        }
    }

    // CACHE01_WRITE_RETURNS_STORED_METADATA_V1: the result is read in the
    // write transaction (no second read and decode of the whole feed). An
    // APPEND whose cursor is no longer the stored one writes nothing and
    // returns null: the listing was replaced while the page was fetched.
    fun writeNetworkPostPage(
        request: RepositoryPageRequest,
        page: NetworkPage<Post>,
        receivedAtEpochMs: Long,
    ): CachedPostPageWrite? {
        require(receivedAtEpochMs >= 0L) {
            "Network receipt timestamp cannot be negative"
        }
        return writePostPage(
            request = request,
            posts = page.items,
            nextCursor = page.nextCursor,
            successfulRefreshEpochMs = receivedAtEpochMs,
            requireStoredAppendCursor = true,
        )
    }

    fun writePostResult(
        request: RepositoryPageRequest,
        result: RepositoryResult<List<Post>>,
    ) {
        when (result) {
            is RepositoryResult.Content -> writePostPage(
                request = request,
                posts = result.value,
                nextCursor = result.nextCursor,
                successfulRefreshEpochMs = clock.nowEpochMs(),
            )
            is RepositoryResult.Empty -> writeEmptyPostPage(
                request = request,
                successfulRefreshEpochMs = clock.nowEpochMs(),
            )
            RepositoryResult.Loading,
            is RepositoryResult.Offline,
            is RepositoryResult.Error,
            -> Unit
        }
    }

    // ISSUE70_POST_LOOKUP_STAYS_IN_ITS_ACCOUNT_V1: the account's own scoped row
    // (an exact key read), else the unscoped row; never another account's.
    fun postForAccount(postId: String, accountStableId: String?): Post? = databaseCall {
        val accountRow = accountStableId
            ?.takeIf(String::isNotBlank)
            ?.let { account -> dao.post(accountScopedPostRowKey(account, postId)) }
        (accountRow ?: dao.post(postId))
            ?.payload
            ?.let(::decodePostOrNull)
    }

    // CACHE05_ACCOUNT_SCOPED_POST_ROWS_V1: an unscoped row first, else the
    // most recently used account-scoped row of that post.
    fun post(postId: String): Post? = databaseCall {
        (dao.post(postId) ?: dao.scopedPost("$CACHED_POST_ROW_SEPARATOR$postId"))
            ?.payload
            ?.let(::decodePostOrNull)
    }

    // CACHE02_UNREADABLE_POST_IS_A_CACHE_MISS_V1: a payload this build cannot
    // read (a newer build's field count after installing an older one over
    // it, or a damaged row) makes the read a cache miss instead of throwing
    // out of every feed read; the next network write replaces the rows.
    private fun List<CachedPostEntity>.decodedPostsOrNull(): List<Post>? =
        map { entity -> decodePostOrNull(entity.payload) ?: return null }

    private fun decodePostOrNull(payload: String): Post? =
        decodeOrNull { SubshellCacheCodec.decodePost(payload) }

    // CACHE06_UNREADABLE_ROW_IS_A_CACHE_MISS_V1 (#204): the CACHE02 rule for
    // every cached type. A single row this build cannot read is a miss; a
    // list or snapshot with one is a miss as a whole (never shown with a
    // hole, never taken as the authoritative list).
    private inline fun <T> decodeOrNull(decode: () -> T): T? =
        try {
            decode()
        } catch (_: RuntimeException) {
            null
        }

    private inline fun <E, T> List<E>.decodedAllOrNull(decode: (E) -> T): List<T>? =
        map { entity -> decodeOrNull { decode(entity) } ?: return null }

    fun cacheCommunities(values: List<Community>) {
        if (values.isEmpty()) return
        val now = clock.nowEpochMs()
        databaseCall {
            dao.upsertCommunities(
                values.map { value ->
                    val normalizedValue = value.copy(
                        name = normalizeCachedCommunityName(value.name),
                    )
                    CachedCommunityEntity(
                        communityId = normalizedValue.id,
                        communityName = normalizedValue.name,
                        payload = SubshellCacheCodec.encodeCommunity(normalizedValue),
                        lastAccessEpochMs = now,
                    )
                },
            )
        }
    }

    fun communities(request: RepositoryPageRequest): List<Community> = databaseCall {
        val offset = request.cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        dao.communities(offset, request.limit)
            .decodedAllOrNull { entity -> SubshellCacheCodec.decodeCommunity(entity.payload) }
            .orEmpty()
    }

    fun community(name: String): Community? = databaseCall {
        val normalizedName = normalizeCachedCommunityName(name)
        val entity = dao.communityByName(normalizedName)
            ?: dao.communityByName("r/$normalizedName")
            ?: dao.communityByName("/r/$normalizedName")
        entity?.payload?.let { payload -> decodeOrNull { SubshellCacheCodec.decodeCommunity(payload) } }
    }

    fun replaceSubscriptionSnapshot(
        accountStableId: String,
        communities: List<Community>,
    ) {
        val normalizedAccountId = accountStableId.trim()
        require(normalizedAccountId.isNotEmpty()) {
            "Subscription account stable ID cannot be blank"
        }
        val normalizedCommunities = communities
            .map { community ->
                community.copy(name = normalizeCachedCommunityName(community.name))
            }
            .distinctBy { community -> community.id }
        val now = clock.nowEpochMs()
        databaseCall {
            database.runInTransaction {
                dao.deleteSubscriptions(normalizedAccountId)
                dao.upsertSubscriptionList(
                    CachedSubscriptionListEntity(
                        accountStableId = normalizedAccountId,
                        lastSuccessfulRefreshEpochMs = now,
                    ),
                )
                dao.upsertSubscriptions(
                    normalizedCommunities.mapIndexed { index, community ->
                        CachedSubscriptionEntity(
                            accountStableId = normalizedAccountId,
                            communityId = community.id,
                            position = index,
                            payload = SubshellCacheCodec.encodeCommunity(community),
                        )
                    },
                )
            }
        }
    }

    fun subscriptionSnapshot(
        accountStableId: String,
    ): CachedSubscriptionSnapshot? = databaseCall {
        val normalizedAccountId = accountStableId.trim()
        require(normalizedAccountId.isNotEmpty()) {
            "Subscription account stable ID cannot be blank"
        }
        val metadata = dao.subscriptionList(normalizedAccountId)
            ?: return@databaseCall null
        CachedSubscriptionSnapshot(
            communities = dao.subscriptions(normalizedAccountId)
                .decodedAllOrNull { entity -> SubshellCacheCodec.decodeCommunity(entity.payload) }
                ?: return@databaseCall null,
            lastSuccessfulRefreshEpochMs = metadata.lastSuccessfulRefreshEpochMs,
        )
    }

    fun replaceCommunityDiscoverySnapshot(
        cacheKey: String,
        items: List<CommunityDiscoveryItem>,
    ) {
        val normalizedCacheKey = cacheKey.trim()
        require(normalizedCacheKey.isNotEmpty()) {
            "Community discovery cache key cannot be blank"
        }
        val normalizedItems = items
            .map { item ->
                item.copy(
                    community = item.community.copy(
                        name = normalizeCachedCommunityName(item.community.name),
                    ),
                )
            }
            .distinctBy { item -> item.community.id }
        val now = clock.nowEpochMs()
        databaseCall {
            database.runInTransaction {
                dao.deleteCommunityDiscoveryEntries(normalizedCacheKey)
                dao.upsertCommunityDiscoveryList(
                    CachedCommunityDiscoveryListEntity(
                        cacheKey = normalizedCacheKey,
                        lastSuccessfulRefreshEpochMs = now,
                        lastAccessEpochMs = now,
                    ),
                )
                dao.upsertCommunityDiscoveryEntries(
                    normalizedItems.mapIndexed { index, item ->
                        CachedCommunityDiscoveryEntryEntity(
                            cacheKey = normalizedCacheKey,
                            communityId = item.community.id,
                            position = index,
                            payload = SubshellCacheCodec.encodeCommunity(item.community),
                            over18 = item.nsfw,
                        )
                    },
                )
                evictLeastRecentlyUsedCommunityDiscoveryLocked()
            }
        }
    }

    fun communityDiscoverySnapshot(
        cacheKey: String,
    ): CachedCommunityDiscoverySnapshot? = databaseCall {
        val normalizedCacheKey = cacheKey.trim()
        require(normalizedCacheKey.isNotEmpty()) {
            "Community discovery cache key cannot be blank"
        }
        val metadata = dao.communityDiscoveryList(normalizedCacheKey)
            ?: return@databaseCall null
        val now = clock.nowEpochMs()
        dao.upsertCommunityDiscoveryList(metadata.copy(lastAccessEpochMs = now))
        CachedCommunityDiscoverySnapshot(
            items = dao.communityDiscoveryEntries(normalizedCacheKey)
                .decodedAllOrNull { entity ->
                    CommunityDiscoveryItem(
                        community = SubshellCacheCodec.decodeCommunity(entity.payload),
                        nsfw = entity.over18,
                    )
                }
                ?: return@databaseCall null,
            lastSuccessfulRefreshEpochMs = metadata.lastSuccessfulRefreshEpochMs,
        )
    }

    fun cacheUsers(values: List<UserFixture>) {
        if (values.isEmpty()) return
        val now = clock.nowEpochMs()
        databaseCall {
            dao.upsertUsers(
                values.map { value ->
                    CachedUserEntity(
                        username = value.username,
                        payload = SubshellCacheCodec.encodeUser(value),
                        lastAccessEpochMs = now,
                    )
                },
            )
        }
    }

    fun users(request: RepositoryPageRequest): List<UserFixture> = databaseCall {
        val offset = request.cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        dao.users(offset, request.limit)
            .decodedAllOrNull { entity -> SubshellCacheCodec.decodeUser(entity.payload) }
            .orEmpty()
    }

    fun user(username: String): UserFixture? = databaseCall {
        dao.user(username.trim())?.payload?.let { payload ->
            decodeOrNull { SubshellCacheCodec.decodeUser(payload) }
        }
    }

    fun cacheInbox(values: List<InboxItem>) {
        if (values.isEmpty()) return
        val now = clock.nowEpochMs()
        databaseCall {
            dao.upsertInbox(
                values.map { value ->
                    CachedInboxEntity(
                        itemId = value.id,
                        payload = SubshellCacheCodec.encodeInboxItem(value),
                        lastAccessEpochMs = now,
                    )
                },
            )
        }
    }

    fun inbox(request: RepositoryPageRequest): List<InboxItem> = databaseCall {
        val offset = request.cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        dao.inbox(offset, request.limit)
            .decodedAllOrNull { entity -> SubshellCacheCodec.decodeInboxItem(entity.payload) }
            .orEmpty()
    }

    fun inboxItem(itemId: String): InboxItem? = databaseCall {
        dao.inboxItem(itemId)?.payload?.let { payload ->
            decodeOrNull { SubshellCacheCodec.decodeInboxItem(payload) }
        }
    }

    fun replaceLiveInboxSnapshot(
        accountStableId: String,
        items: List<InboxItem>,
        nextCursor: String?,
    ) {
        val account = accountStableId.trim()
        require(account.isNotEmpty()) { "Live inbox account stable ID cannot be blank" }
        val normalizedItems = items.distinctBy(InboxItem::id)
        val normalizedCursor = nextCursor?.trim()?.takeIf(String::isNotEmpty)
        val now = clock.nowEpochMs()
        databaseCall {
            database.runInTransaction {
                dao.deleteLiveInboxEntries(account)
                dao.upsertLiveInboxList(
                    CachedLiveInboxListEntity(
                        accountStableId = account,
                        nextCursor = normalizedCursor,
                        lastSuccessfulRefreshEpochMs = now,
                        lastAccessEpochMs = now,
                    ),
                )
                if (normalizedItems.isNotEmpty()) {
                    dao.upsertLiveInboxEntries(
                        normalizedItems.mapIndexed { index, item ->
                            CachedLiveInboxEntryEntity(
                                accountStableId = account,
                                itemId = item.id,
                                position = index,
                                payload = SubshellCacheCodec.encodeInboxItem(item),
                            )
                        },
                    )
                }
                // MED4A_REFRESH_KEEPS_LOCAL_SEEN_MARKS_V1 (OCB-500): a seen mark
                // kept after a failed server read survives the refresh; only
                // marks for items Reddit now reports as read are dropped.
                normalizedItems
                    .filterNot(InboxItem::unread)
                    .forEach { item -> dao.deleteLiveInboxSeen(account, item.id) }
            }
        }
    }

    fun appendLiveInboxPage(
        accountStableId: String,
        expectedCursor: String,
        items: List<InboxItem>,
        nextCursor: String?,
    ) {
        val account = accountStableId.trim()
        val cursor = expectedCursor.trim()
        require(account.isNotEmpty()) { "Live inbox account stable ID cannot be blank" }
        require(cursor.isNotEmpty()) { "Live inbox append cursor cannot be blank" }
        val normalizedCursor = nextCursor?.trim()?.takeIf(String::isNotEmpty)
        val now = clock.nowEpochMs()
        databaseCall {
            database.runInTransaction {
                val metadata = dao.liveInboxList(account)
                    ?: error("Live inbox append requires an existing snapshot")
                require(metadata.nextCursor == cursor) {
                    "Live inbox append cursor does not match the cached cursor"
                }
                val existing = dao.liveInboxEntries(account)
                val existingIds = existing.mapTo(hashSetOf()) { entity -> entity.itemId }
                val incoming = items.distinctBy(InboxItem::id)
                // INBOX04_APPEND_MERGES_MESSAGE_THREADS_V1: a thread already
                // stored is merged in place; an unreadable stored row is left.
                val merged = existing.mapNotNull { entity ->
                    val update = incoming.firstOrNull { item -> item.id == entity.itemId }
                        ?: return@mapNotNull null
                    val stored = runCatching { SubshellCacheCodec.decodeInboxItem(entity.payload) }
                        .getOrNull()
                        ?: return@mapNotNull null
                    val combined = mergeAppendedInboxItem(stored, update)
                    if (combined == stored) null else entity.copy(payload = SubshellCacheCodec.encodeInboxItem(combined))
                }
                if (merged.isNotEmpty()) {
                    dao.upsertLiveInboxEntries(merged)
                }
                val appended = incoming
                    .filterNot { item -> item.id in existingIds }
                if (appended.isNotEmpty()) {
                    dao.upsertLiveInboxEntries(
                        appended.mapIndexed { index, item ->
                            CachedLiveInboxEntryEntity(
                                accountStableId = account,
                                itemId = item.id,
                                position = existing.size + index,
                                payload = SubshellCacheCodec.encodeInboxItem(item),
                            )
                        },
                    )
                }
                dao.upsertLiveInboxList(
                    metadata.copy(
                        nextCursor = normalizedCursor,
                        lastSuccessfulRefreshEpochMs = now,
                        lastAccessEpochMs = now,
                    ),
                )
            }
        }
    }

    fun liveInboxSnapshot(accountStableId: String): CachedLiveInboxSnapshot? = databaseCall {
        val account = accountStableId.trim()
        require(account.isNotEmpty()) { "Live inbox account stable ID cannot be blank" }
        // CONC01_INBOX_READ_TOUCHES_ONLY_LAST_ACCESS_V1 (OCB-620): the snapshot is
        // read in one transaction and only the last access is written back, so
        // a commit from the other Room instance (inbox worker) is not reverted.
        database.runInTransaction<CachedLiveInboxSnapshot?> {
            val metadata = dao.liveInboxList(account) ?: return@runInTransaction null
            dao.touchLiveInboxList(account, clock.nowEpochMs())
            CachedLiveInboxSnapshot(
                items = dao.liveInboxEntries(account)
                    .decodedAllOrNull { entity -> SubshellCacheCodec.decodeInboxItem(entity.payload) }
                    ?: return@runInTransaction null,
                nextCursor = metadata.nextCursor,
                locallySeenIds = dao.liveInboxSeen(account)
                    .mapTo(linkedSetOf()) { entity -> entity.itemId },
                lastSuccessfulRefreshEpochMs = metadata.lastSuccessfulRefreshEpochMs,
            )
        }
    }

    fun markLiveInboxSeen(accountStableId: String, itemId: String) {
        val account = accountStableId.trim()
        val item = itemId.trim()
        require(account.isNotEmpty()) { "Live inbox account stable ID cannot be blank" }
        require(item.isNotEmpty()) { "Live inbox item ID cannot be blank" }
        databaseCall {
            dao.upsertLiveInboxSeen(
                CachedLiveInboxSeenEntity(
                    accountStableId = account,
                    itemId = item,
                    seenAtEpochMs = clock.nowEpochMs(),
                ),
            )
        }
    }

    fun markLiveInboxSeen(
        accountStableId: String,
        itemIds: Set<String>,
    ) {
        val account = accountStableId.trim()
        require(account.isNotEmpty()) { "Live inbox account stable ID cannot be blank" }
        val normalizedIds = itemIds
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()
        if (normalizedIds.isEmpty()) return
        val now = clock.nowEpochMs()
        databaseCall {
            database.runInTransaction {
                normalizedIds.forEach { itemId ->
                    dao.upsertLiveInboxSeen(
                        CachedLiveInboxSeenEntity(
                            accountStableId = account,
                            itemId = itemId,
                            seenAtEpochMs = now,
                        ),
                    )
                }
            }
        }
    }

    fun clearLiveInboxSeen(accountStableId: String, itemId: String) {
        val account = accountStableId.trim()
        val item = itemId.trim()
        require(account.isNotEmpty()) { "Live inbox account stable ID cannot be blank" }
        require(item.isNotEmpty()) { "Live inbox item ID cannot be blank" }
        databaseCall { dao.deleteLiveInboxSeen(account, item) }
    }

    // DATALAYER10: the seen set before an optimistic mark, so a rollback can
    // remove only what that mark added.
    fun liveInboxSeenIds(accountStableId: String): Set<String> {
        val account = accountStableId.trim()
        require(account.isNotEmpty()) { "Live inbox account stable ID cannot be blank" }
        return databaseCall {
            dao.liveInboxSeen(account).mapTo(linkedSetOf()) { entity -> entity.itemId }
        }
    }

    fun clearLiveInboxSeen(accountStableId: String, itemIds: Set<String>) {
        val account = accountStableId.trim()
        require(account.isNotEmpty()) { "Live inbox account stable ID cannot be blank" }
        val ids = itemIds.map(String::trim).filter(String::isNotEmpty)
        if (ids.isEmpty()) return
        databaseCall {
            database.runInTransaction {
                ids.forEach { itemId -> dao.deleteLiveInboxSeen(account, itemId) }
            }
        }
    }

    fun clearLiveInboxSeen(accountStableId: String) {
        val account = accountStableId.trim()
        require(account.isNotEmpty()) { "Live inbox account stable ID cannot be blank" }
        databaseCall { dao.deleteLiveInboxSeenForAccount(account) }
    }

    fun markLiveInboxItemServerRead(accountStableId: String, itemId: String) {
        val account = accountStableId.trim()
        val item = itemId.trim()
        require(account.isNotEmpty()) { "Live inbox account stable ID cannot be blank" }
        require(item.isNotEmpty()) { "Live inbox item ID cannot be blank" }
        databaseCall {
            database.runInTransaction {
                val entity = dao.liveInboxEntry(account, item) ?: return@runInTransaction
                val decoded = decodeOrNull { SubshellCacheCodec.decodeInboxItem(entity.payload) }
                if (decoded?.unread == true) {
                    dao.upsertLiveInboxEntries(
                        listOf(
                            entity.copy(
                                payload = SubshellCacheCodec.encodeInboxItem(
                                    decoded.copy(unread = false),
                                ),
                            ),
                        ),
                    )
                }
                dao.deleteLiveInboxSeen(account, item)
            }
        }
    }

    fun markAllLiveInboxServerRead(accountStableId: String) {
        val account = accountStableId.trim()
        require(account.isNotEmpty()) { "Live inbox account stable ID cannot be blank" }
        databaseCall {
            database.runInTransaction {
                val updated = dao.liveInboxEntries(account).map { entity ->
                    val decoded = decodeOrNull { SubshellCacheCodec.decodeInboxItem(entity.payload) }
                    if (decoded?.unread == true) {
                        entity.copy(
                            payload = SubshellCacheCodec.encodeInboxItem(
                                decoded.copy(unread = false),
                            ),
                        )
                    } else {
                        entity
                    }
                }
                if (updated.isNotEmpty()) dao.upsertLiveInboxEntries(updated)
                dao.deleteLiveInboxSeenForAccount(account)
            }
        }
    }

    fun cacheComments(
        postId: String,
        values: List<Comment>,
    ) {
        val now = clock.nowEpochMs()
        databaseCall {
            database.runInTransaction {
                dao.deleteCommentsForPost(postId)
                dao.upsertComments(
                    values.mapIndexed { index, value ->
                        CachedCommentEntity(
                            commentId = value.id,
                            postId = postId,
                            position = index,
                            payload = SubshellCacheCodec.encodeComment(value),
                            lastAccessEpochMs = now,
                        )
                    },
                )
                // MED4B_COMMENT_CACHE_CAPPED_V1 (OCB-526): the comment cache was
                // never evicted and grew with every thread read.
                dao.evictCommentThreadsBeyond(maxCommentThreads)
            }
        }
    }

    fun comments(
        postId: String?,
        request: RepositoryPageRequest,
    ): List<Comment> = databaseCall {
        val offset = request.cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val entities = if (postId == null) {
            dao.comments(offset, request.limit)
        } else {
            dao.commentsForPost(postId, offset, request.limit)
        }
        entities
            .decodedAllOrNull { entity -> SubshellCacheCodec.decodeComment(entity.payload) }
            .orEmpty()
    }

    // COMMENTCACHE02_WHOLE_CACHED_THREAD_V1 (#137): cacheComments stores every
    // root of a thread, but a read with a page limit (100) dropped the roots past
    // it and the trailing "more" node; a read-modify-write then cached and showed
    // the cut thread. This reads every stored root, page by page, in one
    // transaction. An unreadable row is a miss for the whole thread (CACHE06).
    fun allComments(postId: String): List<Comment> = databaseCall {
        database.runInTransaction<List<Comment>> {
            val entities = mutableListOf<CachedCommentEntity>()
            while (true) {
                val page = dao.commentsForPost(postId, entities.size, RepositoryPageRequest.MAX_LIMIT)
                entities += page
                if (page.size < RepositoryPageRequest.MAX_LIMIT) break
            }
            entities
                .decodedAllOrNull { entity -> SubshellCacheCodec.decodeComment(entity.payload) }
                .orEmpty()
        }
    }

    fun cacheProfileComments(
        username: String,
        values: List<ProfileCommentFixture>,
    ) {
        val now = clock.nowEpochMs()
        databaseCall {
            database.runInTransaction {
                dao.deleteProfileComments(username)
                dao.upsertProfileComments(
                    values.mapIndexed { index, value ->
                        CachedProfileCommentEntity(
                            commentId = value.id,
                            username = username,
                            postId = value.postId,
                            position = index,
                            payload = SubshellCacheCodec.encodeProfileComment(value),
                            lastAccessEpochMs = now,
                        )
                    },
                )
                dao.evictProfileCommentListsBeyond(maxProfileCommentLists)
            }
        }
    }

    // LOW3_PROFILE_COMMENT_APPEND_WRITES_ONLY_NEW_ROWS_V1 (OCB-519): a later
    // page adds only the comments the list does not hold yet, after its last
    // position, instead of re-encoding and rewriting the whole list. Returns
    // the comments it added, in page order.
    fun appendProfileComments(
        username: String,
        values: List<ProfileCommentFixture>,
    ): List<ProfileCommentFixture> {
        val now = clock.nowEpochMs()
        return databaseCall {
            database.runInTransaction<List<ProfileCommentFixture>> {
                val known = dao.profileCommentIds(username).toHashSet()
                val added = values.filter { value -> known.add(value.id) }
                val start = (dao.maxProfileCommentPosition(username) ?: -1) + 1
                dao.upsertProfileComments(
                    added.mapIndexed { index, value ->
                        CachedProfileCommentEntity(
                            commentId = value.id,
                            username = username,
                            postId = value.postId,
                            position = start + index,
                            payload = SubshellCacheCodec.encodeProfileComment(value),
                            lastAccessEpochMs = now,
                        )
                    },
                )
                dao.evictProfileCommentListsBeyond(maxProfileCommentLists)
                added
            }
        }
    }

    fun profileComments(
        username: String?,
        request: RepositoryPageRequest,
    ): List<ProfileCommentFixture> = databaseCall {
        val offset = request.cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val entities = if (username == null) {
            dao.profileComments(offset, request.limit)
        } else {
            dao.profileComments(username, offset, request.limit)
        }
        entities
            .decodedAllOrNull { entity -> SubshellCacheCodec.decodeProfileComment(entity.payload) }
            .orEmpty()
    }

    fun persistLocalMutation(
        mutationKey: String,
        kind: String,
        subjectId: String,
        payload: String,
    ) {
        val now = clock.nowEpochMs()
        databaseCall {
            dao.upsertLocalMutation(
                LocalMutationEntity(
                    mutationKey = mutationKey,
                    kind = kind,
                    subjectId = subjectId,
                    payload = payload,
                    updatedAtEpochMs = now,
                ),
            )
        }
    }

    fun restoredLocalMutations(): List<RestoredLocalMutation> = databaseCall {
        dao.localMutations().map { entity ->
            RestoredLocalMutation(
                mutationKey = entity.mutationKey,
                kind = entity.kind,
                subjectId = entity.subjectId,
                payload = entity.payload,
                updatedAtEpochMs = entity.updatedAtEpochMs,
            )
        }
    }

    /**
     * MED4A_LOGOUT_REMOVES_ACCOUNT_CACHE_V1 (OCB-527): what this cache holds
     * for one signed-in account (its inbox and seen marks, subscriptions,
     * account-scoped feeds and their post rows, comment threads, profile
     * comments) is deleted when the account is logged out. Shared, public
     * rows (communities, users, public feeds) stay.
     */
    // Every row of the post (unscoped and per account) and its places in every
    // cached list; positions keep their order with a gap.
    override fun removeDeletedPost(postId: String) {
        val bare = postId.trim().removePrefix("t3_").takeIf(String::isNotEmpty) ?: return
        databaseCall {
            database.runInTransaction {
                listOf(bare, "t3_$bare").forEach { id ->
                    dao.deleteQueryEntriesForPost(id, "$CACHED_POST_ROW_SEPARATOR$id")
                    dao.deletePostRows(id, "$CACHED_POST_ROW_SEPARATOR$id")
                }
            }
        }
    }

    override fun removeAccount(accountStableId: String) {
        val account = accountStableId.trim()
        if (account.isEmpty()) return
        val scoped = RepositoryQueryKey("x")
            .scopedToAccount(account, AccountSessionSource.AUTHENTICATED)
            .value
            .removeSuffix("x")
        val profileComments = "account|$account|"
        databaseCall {
            database.runInTransaction {
                dao.deleteLiveInboxEntries(account)
                dao.deleteLiveInboxList(account)
                dao.deleteLiveInboxSeenForAccount(account)
                dao.deleteSubscriptions(account)
                dao.deleteSubscriptionList(account)
                dao.deleteQueryEntriesWithPrefix(scoped)
                dao.deleteQueriesWithPrefix(scoped)
                dao.deletePostsWithPrefix(scoped)
                dao.deleteCommentsWithPrefix(scoped)
                dao.deleteProfileCommentsWithPrefix(profileComments)
            }
        }
    }

    fun clearRemoteCache() {
        databaseCall {
            database.runInTransaction {
                dao.deleteAllQueryEntries()
                dao.deleteAllQueries()
                dao.deleteAllPosts()
                dao.deleteCommunities()
                dao.deleteAllSubscriptions()
                dao.deleteAllSubscriptionLists()
                dao.deleteAllCommunityDiscoveryEntries()
                dao.deleteAllCommunityDiscoveryLists()
                dao.deleteUsers()
                dao.deleteInbox()
                dao.deleteAllLiveInboxEntries()
                dao.deleteAllLiveInboxLists()
                dao.deleteAllLiveInboxSeen()
                dao.deleteComments()
                dao.deleteProfileComments()
            }
        }
    }

    fun diagnostics(): SubshellCacheDiagnostics = databaseCall {
        SubshellCacheDiagnostics(
            queryCount = dao.queryCount(),
            queryEntryCount = dao.queryEntryCount(),
            postCount = dao.postCount(),
            localMutationCount = dao.localMutationCount(),
        )
    }

    fun queryMetadata(queryKey: RepositoryQueryKey): SubshellCacheQueryMetadata? =
        databaseCall {
            dao.queryMetadata(queryKey.value)?.let { entity ->
                SubshellCacheQueryMetadata(
                    generation = entity.generation,
                    nextCursor = entity.nextCursor,
                    terminal = entity.terminal,
                    lastSuccessfulRefreshEpochMs = entity.lastSuccessfulRefreshEpochMs,
                )
            }
        }

    private fun evictLeastRecentlyUsedCommunityDiscoveryLocked() {
        while (dao.communityDiscoveryListCount() > DEFAULT_MAX_COMMUNITY_DISCOVERY_COUNT) {
            val oldestKey = dao.communityDiscoveryKeysByLeastRecentAccess().firstOrNull()
                ?: return
            dao.deleteCommunityDiscoveryEntries(oldestKey)
            dao.deleteCommunityDiscoveryList(oldestKey)
        }
    }

    private fun writePostPage(
        request: RepositoryPageRequest,
        posts: List<Post>,
        nextCursor: String?,
        successfulRefreshEpochMs: Long,
        requireStoredAppendCursor: Boolean = false,
    ): CachedPostPageWrite? {
        val now = clock.nowEpochMs()
        return databaseCall {
            database.runInTransaction<CachedPostPageWrite?> {
                val existingQuery = dao.queryMetadata(request.queryKey.value)
                if (
                    requireStoredAppendCursor &&
                    request.operation == RepositoryLoadOperation.APPEND &&
                    existingQuery?.nextCursor?.trim()?.takeIf(String::isNotEmpty) !=
                    request.cursor?.trim()?.takeIf(String::isNotEmpty)
                ) {
                    return@runInTransaction null
                }
                val generation = when (request.operation) {
                    RepositoryLoadOperation.REFRESH -> (existingQuery?.generation ?: 0) + 1
                    RepositoryLoadOperation.INITIAL,
                    RepositoryLoadOperation.APPEND,
                    -> existingQuery?.generation ?: 0
                }

                // CACHE05_ACCOUNT_SCOPED_POST_ROWS_V1
                dao.upsertPosts(
                    posts.map { post ->
                        CachedPostEntity(
                            postId = cachedPostRowKey(request.queryKey, post.id),
                            payload = SubshellCacheCodec.encodePost(post),
                            lastAccessEpochMs = now,
                        )
                    },
                )

                if (request.operation != RepositoryLoadOperation.APPEND) {
                    dao.deleteQueryEntries(request.queryKey.value)
                }

                // Entries written before CACHE05 point at unscoped rows; both
                // forms name the same post.
                val existingIds = if (request.operation == RepositoryLoadOperation.APPEND) {
                    dao.queryPostIds(request.queryKey.value).mapTo(hashSetOf(), ::cachedPostIdOfRowKey)
                } else {
                    hashSetOf()
                }
                var position = if (request.operation == RepositoryLoadOperation.APPEND) {
                    (dao.maxQueryPosition(request.queryKey.value) ?: -1) + 1
                } else {
                    0
                }
                val entries = posts.mapNotNull { post ->
                    if (!existingIds.add(post.id)) {
                        null
                    } else {
                        CachedQueryEntryEntity(
                            queryKey = request.queryKey.value,
                            postId = cachedPostRowKey(request.queryKey, post.id),
                            position = position++,
                            generation = generation,
                        )
                    }
                }

                dao.upsertQuery(
                    CachedQueryEntity(
                        queryKey = request.queryKey.value,
                        generation = generation,
                        nextCursor = nextCursor,
                        lastSuccessfulRefreshEpochMs = if (
                            request.operation == RepositoryLoadOperation.APPEND &&
                            existingQuery != null
                        ) {
                            existingQuery.lastSuccessfulRefreshEpochMs
                        } else {
                            successfulRefreshEpochMs
                        },
                        lastAccessEpochMs = now,
                        terminal = nextCursor == null,
                    ),
                )
                dao.insertQueryEntries(entries)
                transactionObserver.beforeCommit(request.queryKey)
                evictLeastRecentlyUsedQueriesLocked()
                dao.deleteOrphanPosts()
                CachedPostPageWrite(
                    storedItemCount = dao.queryEntryCount(request.queryKey.value),
                    generation = generation,
                    remoteNextCursor = nextCursor,
                    terminal = nextCursor == null,
                )
            }
        }
    }

    private fun writeEmptyPostPage(
        request: RepositoryPageRequest,
        successfulRefreshEpochMs: Long,
    ) {
        val now = clock.nowEpochMs()
        databaseCall {
            database.runInTransaction {
                val existingQuery = dao.queryMetadata(request.queryKey.value)
                val generation = when (request.operation) {
                    RepositoryLoadOperation.REFRESH -> (existingQuery?.generation ?: 0) + 1
                    RepositoryLoadOperation.INITIAL,
                    RepositoryLoadOperation.APPEND,
                    -> existingQuery?.generation ?: 0
                }
                if (request.operation != RepositoryLoadOperation.APPEND) {
                    dao.deleteQueryEntries(request.queryKey.value)
                }
                dao.upsertQuery(
                    CachedQueryEntity(
                        queryKey = request.queryKey.value,
                        generation = generation,
                        nextCursor = null,
                        lastSuccessfulRefreshEpochMs = if (
                            request.operation == RepositoryLoadOperation.APPEND &&
                            existingQuery != null
                        ) {
                            existingQuery.lastSuccessfulRefreshEpochMs
                        } else {
                            successfulRefreshEpochMs
                        },
                        lastAccessEpochMs = now,
                        terminal = true,
                    ),
                )
                transactionObserver.beforeCommit(request.queryKey)
                evictLeastRecentlyUsedQueriesLocked()
                dao.deleteOrphanPosts()
            }
        }
    }

    private fun evictLeastRecentlyUsedQueriesLocked() {
        val queryKeys = dao.queryKeysByLeastRecentAccess()
        val removeCount = (queryKeys.size - maxQueryCount).coerceAtLeast(0)
        queryKeys.take(removeCount).forEach(dao::deleteQuery)
    }

    private fun <T> databaseCall(block: () -> T): T {
        if (Thread.currentThread().name == "subshell-data03-room") {
            return block()
        }
        return executor.submit(Callable(block)).get()
    }

    override fun close() {
        executor.shutdown()
        executor.awaitTermination(5, TimeUnit.SECONDS)
        if (ownsDatabase) {
            database.close()
        }
    }

    companion object {
        const val DEFAULT_MAX_QUERY_COUNT = 24
        const val DEFAULT_FRESH_FOR_MS = 15 * 60 * 1_000L
        private const val DEFAULT_MAX_COMMUNITY_DISCOVERY_COUNT = 12
        const val DEFAULT_MAX_COMMENT_THREADS = 200
        const val DEFAULT_MAX_PROFILE_COMMENT_LISTS = 50
    }
}

// CACHE05_ACCOUNT_SCOPED_POST_ROWS_V1: a post row carries per-account state
// (vote, saved), so account-scoped feeds store their rows under
// "<account scope>|post|<post id>" instead of sharing one row per post across
// accounts. Other feeds keep the plain post id. Schema and codec unchanged.
internal fun cachedPostRowKey(queryKey: RepositoryQueryKey, postId: String): String {
    if (!queryKey.isAuthenticatedAccountScoped) return postId
    val accountScope = queryKey.value.split('|', limit = 4).take(3).joinToString("|")
    return "$accountScope$CACHED_POST_ROW_SEPARATOR$postId"
}

internal fun accountScopedPostRowKey(accountStableId: String, postId: String): String =
    cachedPostRowKey(
        RepositoryQueryKey("post").scopedToAccount(
            stableId = accountStableId,
            source = AccountSessionSource.AUTHENTICATED,
        ),
        postId,
    )

internal fun cachedPostIdOfRowKey(rowKey: String): String =
    rowKey.substringAfterLast(CACHED_POST_ROW_SEPARATOR)

private const val CACHED_POST_ROW_SEPARATOR = "|post|"

// MED4B_ONE_RUNTIME_CACHE_STORE_PER_PROCESS_V1 (OCB-578/619): every
// AuthApplicationRuntime (the UI's and each inbox worker run) opened its own
// Room database and executor thread and never closed them. The runtime's
// store is now one per process, like the repository's.
private object ProcessRuntimeCacheStore {
    @Volatile
    var context: android.content.Context? = null

    @Volatile
    var store: RoomSubshellCacheStore? = null
}

internal fun processRuntimeCacheStore(context: android.content.Context): RoomSubshellCacheStore {
    val appContext = context.applicationContext
    ProcessRuntimeCacheStore.store
        ?.takeIf { ProcessRuntimeCacheStore.context === appContext }
        ?.let { return it }
    return synchronized(ProcessRuntimeCacheStore) {
        ProcessRuntimeCacheStore.store
            ?.takeIf { ProcessRuntimeCacheStore.context === appContext }
            ?: RoomSubshellCacheStore(database = SubshellCacheDatabase.open(appContext)).also { created ->
                ProcessRuntimeCacheStore.context = appContext
                ProcessRuntimeCacheStore.store = created
            }
    }
}
