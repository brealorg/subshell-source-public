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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

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

    /**
     * VOTESSOT01_SETTLED_ACTIONS_REACH_THE_STORE_V1: rewrites the account's
     * scoped row and the unscoped row of one post (either id spelling) with
     * [transform], on the store's thread without waiting (callers are on Main).
     * A row that is missing, unreadable or unchanged is left.
     */
    fun updateStoredPostLater(
        accountStableId: String,
        postFullname: String,
        transform: (Post) -> Post,
    ) {
        val bare = postFullname.trim().removePrefix("t3_").takeIf(String::isNotEmpty) ?: return
        val now = clock.nowEpochMs()
        executor.execute {
            runCatching {
                database.runInTransaction {
                    val keys = listOf("t3_$bare", bare).flatMap { postId ->
                        listOf(accountScopedPostRowKey(accountStableId, postId), postId)
                    }
                    keys.distinct().forEach { key ->
                        val entity = dao.post(key) ?: return@forEach
                        val stored = decodePostOrNull(entity.payload) ?: return@forEach
                        val updated = transform(stored)
                        if (updated != stored) {
                            dao.upsertPosts(
                                listOf(entity.copy(payload = SubshellCacheCodec.encodePost(updated), lastAccessEpochMs = now)),
                            )
                        }
                    }
                }
            }
        }
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
                writeLiveInboxSnapshotInTransaction(account, normalizedItems, normalizedCursor, now)
            }
        }
    }

    private fun writeLiveInboxSnapshotInTransaction(
        account: String,
        normalizedItems: List<InboxItem>,
        normalizedCursor: String?,
        now: Long,
    ) {
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

    /**
     * INBOXSSOT01_REFRESH_KEEPS_THE_OLDER_PAGES_V1: a refresh (the screen's or
     * the 15-minute background sync) writes the fresh first page on top of
     * the pages already stored instead of replacing them, so a reader who
     * scrolled down keeps the list while the inbox updates under it. Returns
     * the stored list.
     */
    fun refreshLiveInboxHead(
        accountStableId: String,
        fresh: List<InboxItem>,
        nextCursor: String?,
        completeSinceEpochSeconds: Long?,
        unreadItemIds: Set<String>? = null,
    ): CachedLiveInboxSnapshot? {
        val account = accountStableId.trim()
        require(account.isNotEmpty()) { "Live inbox account stable ID cannot be blank" }
        val now = clock.nowEpochMs()
        // Read and write in one transaction, so nothing written between them
        // (a read mark from the writer) is lost.
        return databaseCall {
            database.runInTransaction<CachedLiveInboxSnapshot?> {
                val stored = readLiveInboxSnapshotInTransaction(account, touch = false)
                val (items, cursor) = refreshedLiveInboxHead(
                    storedItems = stored?.items.orEmpty(),
                    storedCursor = stored?.nextCursor,
                    freshItems = fresh.distinctBy(InboxItem::id),
                    freshCursor = nextCursor?.trim()?.takeIf(String::isNotEmpty),
                    completeSinceEpochSeconds = completeSinceEpochSeconds,
                    unreadItemIds = unreadItemIds,
                )
                writeLiveInboxSnapshotInTransaction(account, items, cursor, now)
                readLiveInboxSnapshotInTransaction(account, touch = false)
            }
        }
    }

    // INBOXFAST01_A_REFRESH_READS_ONLY_THE_FIRST_PAGES_V1: whether a refresh
    // has stored pages to keep below the fresh one.
    fun hasLiveInboxItems(accountStableId: String): Boolean {
        val account = accountStableId.trim()
        require(account.isNotEmpty()) { "Live inbox account stable ID cannot be blank" }
        return databaseCall {
            dao.liveInboxList(account) != null && dao.liveInboxEntryCount(account) > 0
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
                    keepLiveInboxInTimeOrder(account)
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

    /**
     * INBOXORDER01_A_LATER_PAGE_TAKES_ITS_PLACE_IN_TIME_V1: a later inbox page
     * can hold items newer than stored rows near the end (an old thread that
     * only your sent history reached), so the list is put back in time order,
     * newest first, as Reddit's pages are. Rows without a time keep their
     * place relative to each other at the end; an unreadable row is left.
     */
    private fun keepLiveInboxInTimeOrder(account: String) {
        val entries = dao.liveInboxEntries(account)
        val timed = entries.map { entity ->
            entity to runCatching { SubshellCacheCodec.decodeInboxItem(entity.payload).createdEpochSeconds }
                .getOrElse { return }
        }
        val ordered = timed.sortedByDescending { (_, created) -> created ?: Long.MIN_VALUE }.map { (entity, _) -> entity }
        if (ordered == entries) return
        dao.deleteLiveInboxEntries(account)
        dao.upsertLiveInboxEntries(ordered.mapIndexed { index, entity -> entity.copy(position = index) })
    }

    fun liveInboxSnapshot(accountStableId: String): CachedLiveInboxSnapshot? =
        liveInboxSnapshot(accountStableId, touch = true)

    /**
     * INBOXSSOT01_THE_INBOX_FOLLOWS_ITS_CACHE_V1: the stored inbox, read again
     * whenever one of its tables changes (the background sync, a refresh, an
     * append, a read mark), the way Google's offline-first guide has the
     * screen observe the local source of truth. The read does not touch the
     * last access: that write would invalidate the table and read again
     * forever. Blocking reads; collect it off the main thread.
     */
    fun observeLiveInboxSnapshot(accountStableId: String): Flow<CachedLiveInboxSnapshot?> {
        val account = accountStableId.trim()
        require(account.isNotEmpty()) { "Live inbox account stable ID cannot be blank" }
        return database.invalidationTracker
            .createFlow(
                "cached_live_inbox_lists",
                "cached_live_inbox_entries",
                "cached_live_inbox_seen",
                emitInitialState = true,
            )
            .map { liveInboxSnapshot(account, touch = false) }
            .distinctUntilChanged()
    }

    private fun liveInboxSnapshot(
        accountStableId: String,
        touch: Boolean,
    ): CachedLiveInboxSnapshot? = databaseCall {
        val account = accountStableId.trim()
        require(account.isNotEmpty()) { "Live inbox account stable ID cannot be blank" }
        // CONC01_INBOX_READ_TOUCHES_ONLY_LAST_ACCESS_V1 (OCB-620): the snapshot is
        // read in one transaction and only the last access is written back, so
        // a commit from the other Room instance (inbox worker) is not reverted.
        database.runInTransaction<CachedLiveInboxSnapshot?> {
            readLiveInboxSnapshotInTransaction(account, touch)
        }
    }

    private fun readLiveInboxSnapshotInTransaction(
        account: String,
        touch: Boolean,
    ): CachedLiveInboxSnapshot? {
        val metadata = dao.liveInboxList(account) ?: return null
        if (touch) dao.touchLiveInboxList(account, clock.nowEpochMs())
        return CachedLiveInboxSnapshot(
            items = dao.liveInboxEntries(account)
                .decodedAllOrNull { entity -> SubshellCacheCodec.decodeInboxItem(entity.payload) }
                ?: return null,
            nextCursor = metadata.nextCursor,
            locallySeenIds = dao.liveInboxSeen(account)
                .mapTo(linkedSetOf()) { entity -> entity.itemId },
            lastSuccessfulRefreshEpochMs = metadata.lastSuccessfulRefreshEpochMs,
        )
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

    /**
     * COMMENTSSOT01_THE_THREAD_FOLLOWS_ITS_CACHE_V1 (#244): the stored thread,
     * read again whenever `cached_comments` changes (a refresh, "load more", a
     * posted reply, an edit, a delete), the way Google's offline-first guide
     * has the screen observe the local source of truth. Reads only, so it does
     * not wake itself. Blocking reads; collect it off the main thread.
     */
    /**
     * FEEDSSOT01_FEEDS_FOLLOW_THE_STORE_V1: a signal per committed write to
     * the stored posts or to a query's post list (no initial one). A feed
     * read touches only `cached_queries`, so reading after a signal does not
     * signal again.
     */
    fun postStoreChanges(): Flow<Unit> =
        database.invalidationTracker
            .createFlow("cached_posts", "cached_query_entries", emitInitialState = false)
            .map { }

    /**
     * LISTSSOT01_LISTS_FOLLOW_THE_STORE_V1 (#244): one signal when what the
     * stored community, user or (signed-out) inbox lists hold changes. Their
     * reads write what the backend returned, so a rewrite of the same rows is
     * not a change; otherwise a reader that re-reads on the signal would wake
     * itself for ever. Compared on the IO dispatcher. The first signal is the
     * state when observing starts (a write just before it is not lost).
     */
    fun listStoreChanges(): Flow<Unit> =
        database.invalidationTracker
            .createFlow("cached_communities", "cached_users", "cached_inbox", emitInitialState = true)
            .map { databaseCall { dao.storedListPayloads() }.hashCode() }
            .distinctUntilChanged()
            .map { }
            .flowOn(kotlinx.coroutines.Dispatchers.IO)

    fun observeComments(postId: String): Flow<List<Comment>> =
        database.invalidationTracker
            .createFlow("cached_comments", emitInitialState = true)
            .map { allComments(postId) }
            .distinctUntilChanged()

    /**
     * COMMENTSSOT01_LOCAL_WRITES_REACH_THE_CACHE_V1: rewrites each stored
     * thread in [postIds] with [transform] in one transaction (null or an
     * unchanged list leaves it). An edit, delete or moderation used to change
     * only the screen's copy, so the cached thread showed the old body on the
     * next open, and the thread's other sorts never had it.
     */
    fun updateStoredComments(
        postIds: Collection<String>,
        transform: (List<Comment>) -> List<Comment>?,
    ) {
        if (postIds.isEmpty()) return
        val now = clock.nowEpochMs()
        databaseCall {
            database.runInTransaction {
                postIds.distinct().forEach { postId ->
                    val stored = dao.storedThread(postId) ?: return@forEach
                    if (stored.isEmpty()) return@forEach
                    val updated = transform(stored) ?: return@forEach
                    if (updated == stored) return@forEach
                    dao.deleteCommentsForPost(postId)
                    dao.upsertComments(
                        updated.mapIndexed { index, value ->
                            CachedCommentEntity(
                                commentId = value.id,
                                postId = postId,
                                position = index,
                                payload = SubshellCacheCodec.encodeComment(value),
                                lastAccessEpochMs = now,
                            )
                        },
                    )
                }
            }
        }
    }

    private fun SubshellCacheDao.storedThread(postId: String): List<Comment>? {
        val entities = mutableListOf<CachedCommentEntity>()
        while (true) {
            val page = commentsForPost(postId, entities.size, RepositoryPageRequest.MAX_LIMIT)
            entities += page
            if (page.size < RepositoryPageRequest.MAX_LIMIT) break
        }
        return entities.decodedAllOrNull { entity -> SubshellCacheCodec.decodeComment(entity.payload) }
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

    /**
     * PROFILESSOT01_HISTORY_FOLLOWS_ITS_CACHE_V1 (#244): one stored comment
     * history (all its rows), read again whenever `cached_profile_comments`
     * changes. Reads only. Blocking reads; collect it off the main thread.
     */
    fun observeProfileComments(username: String): Flow<List<ProfileCommentFixture>> =
        database.invalidationTracker
            .createFlow("cached_profile_comments", emitInitialState = true)
            .map {
                databaseCall {
                    database.runInTransaction<List<ProfileCommentFixture>> {
                        val entities = mutableListOf<CachedProfileCommentEntity>()
                        while (true) {
                            val page = dao.profileComments(username, entities.size, RepositoryPageRequest.MAX_LIMIT)
                            entities += page
                            if (page.size < RepositoryPageRequest.MAX_LIMIT) break
                        }
                        entities
                            .decodedAllOrNull { entity -> SubshellCacheCodec.decodeProfileComment(entity.payload) }
                            .orEmpty()
                    }
                }
            }
            .distinctUntilChanged()

    /**
     * PROFILESSOT01_HISTORY_KEEPS_EDITS_AND_DELETES_V1: a confirmed edit or
     * delete of one comment reaches every stored history that lists it
     * ([commentIds] are its spellings); [transform] gives the new row, or null
     * to drop it (Reddit leaves a deleted comment out of the history). An
     * unreadable row is left.
     */
    fun updateProfileComment(
        commentIds: Collection<String>,
        transform: (ProfileCommentFixture) -> ProfileCommentFixture?,
    ) {
        if (commentIds.isEmpty()) return
        databaseCall {
            database.runInTransaction {
                dao.profileCommentRows(commentIds.distinct()).forEach { entity ->
                    val stored = runCatching { SubshellCacheCodec.decodeProfileComment(entity.payload) }
                        .getOrNull() ?: return@forEach
                    val updated = transform(stored)
                    if (updated == null) {
                        dao.deleteProfileComment(entity.username, entity.commentId)
                    } else if (updated != stored) {
                        dao.upsertProfileComments(
                            listOf(entity.copy(payload = SubshellCacheCodec.encodeProfileComment(updated))),
                        )
                    }
                }
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
            ?: RoomSubshellCacheStore(
                database = SubshellCacheDatabase.processShared(appContext),
                ownsDatabase = false,
            ).also { created ->
                ProcessRuntimeCacheStore.context = appContext
                ProcessRuntimeCacheStore.store = created
            }
    }
}
