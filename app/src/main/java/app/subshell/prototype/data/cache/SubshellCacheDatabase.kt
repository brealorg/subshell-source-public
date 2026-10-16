package app.subshell.prototype.data.cache

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal const val SUBSHELL_CACHE_DATABASE_NAME = "subshell-offline-cache.db"

@Entity(tableName = "cached_posts")
internal data class CachedPostEntity(
    @PrimaryKey
    @ColumnInfo(name = "post_id")
    val postId: String,
    val payload: String,
    @ColumnInfo(name = "last_access_epoch_ms")
    val lastAccessEpochMs: Long,
)

@Entity(tableName = "cached_queries")
internal data class CachedQueryEntity(
    @PrimaryKey
    @ColumnInfo(name = "query_key")
    val queryKey: String,
    val generation: Int,
    @ColumnInfo(name = "next_cursor")
    val nextCursor: String?,
    @ColumnInfo(name = "last_successful_refresh_epoch_ms")
    val lastSuccessfulRefreshEpochMs: Long,
    @ColumnInfo(name = "last_access_epoch_ms")
    val lastAccessEpochMs: Long,
    val terminal: Boolean,
)

@Entity(
    tableName = "cached_query_entries",
    primaryKeys = ["query_key", "post_id"],
    foreignKeys = [
        ForeignKey(
            entity = CachedQueryEntity::class,
            parentColumns = ["query_key"],
            childColumns = ["query_key"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CachedPostEntity::class,
            parentColumns = ["post_id"],
            childColumns = ["post_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["query_key"]),
        Index(value = ["post_id"]),
        Index(value = ["query_key", "position"], unique = true),
    ],
)
internal data class CachedQueryEntryEntity(
    @ColumnInfo(name = "query_key")
    val queryKey: String,
    @ColumnInfo(name = "post_id")
    val postId: String,
    val position: Int,
    val generation: Int,
)

@Entity(tableName = "local_mutations")
internal data class LocalMutationEntity(
    @PrimaryKey
    @ColumnInfo(name = "mutation_key")
    val mutationKey: String,
    val kind: String,
    @ColumnInfo(name = "subject_id")
    val subjectId: String,
    val payload: String,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long,
)

@Entity(tableName = "cached_communities")
internal data class CachedCommunityEntity(
    @PrimaryKey
    @ColumnInfo(name = "community_id")
    val communityId: String,
    @ColumnInfo(name = "community_name")
    val communityName: String,
    val payload: String,
    @ColumnInfo(name = "last_access_epoch_ms")
    val lastAccessEpochMs: Long,
)

@Entity(tableName = "cached_subscription_lists")
internal data class CachedSubscriptionListEntity(
    @PrimaryKey
    @ColumnInfo(name = "account_stable_id")
    val accountStableId: String,
    @ColumnInfo(name = "last_successful_refresh_epoch_ms")
    val lastSuccessfulRefreshEpochMs: Long,
)

@Entity(
    tableName = "cached_subscriptions",
    primaryKeys = ["account_stable_id", "community_id"],
    indices = [
        Index(value = ["account_stable_id"]),
        Index(value = ["account_stable_id", "position"], unique = true),
    ],
)
internal data class CachedSubscriptionEntity(
    @ColumnInfo(name = "account_stable_id")
    val accountStableId: String,
    @ColumnInfo(name = "community_id")
    val communityId: String,
    val position: Int,
    val payload: String,
)

@Entity(tableName = "cached_community_discovery_lists")
internal data class CachedCommunityDiscoveryListEntity(
    @PrimaryKey
    @ColumnInfo(name = "cache_key")
    val cacheKey: String,
    @ColumnInfo(name = "last_successful_refresh_epoch_ms")
    val lastSuccessfulRefreshEpochMs: Long,
    @ColumnInfo(name = "last_access_epoch_ms")
    val lastAccessEpochMs: Long,
)

@Entity(
    tableName = "cached_community_discovery_entries",
    primaryKeys = ["cache_key", "community_id"],
    indices = [
        Index(value = ["cache_key"]),
        Index(value = ["cache_key", "position"], unique = true),
    ],
)
internal data class CachedCommunityDiscoveryEntryEntity(
    @ColumnInfo(name = "cache_key")
    val cacheKey: String,
    @ColumnInfo(name = "community_id")
    val communityId: String,
    val position: Int,
    val payload: String,
    @ColumnInfo(name = "over_18")
    val over18: Boolean,
)

@Entity(tableName = "cached_users")
internal data class CachedUserEntity(
    @PrimaryKey
    val username: String,
    val payload: String,
    @ColumnInfo(name = "last_access_epoch_ms")
    val lastAccessEpochMs: Long,
)

@Entity(tableName = "cached_inbox")
internal data class CachedInboxEntity(
    @PrimaryKey
    @ColumnInfo(name = "item_id")
    val itemId: String,
    val payload: String,
    @ColumnInfo(name = "last_access_epoch_ms")
    val lastAccessEpochMs: Long,
)

@Entity(tableName = "cached_live_inbox_lists")
internal data class CachedLiveInboxListEntity(
    @PrimaryKey
    @ColumnInfo(name = "account_stable_id")
    val accountStableId: String,
    @ColumnInfo(name = "next_cursor")
    val nextCursor: String?,
    @ColumnInfo(name = "last_successful_refresh_epoch_ms")
    val lastSuccessfulRefreshEpochMs: Long,
    @ColumnInfo(name = "last_access_epoch_ms")
    val lastAccessEpochMs: Long,
)

@Entity(
    tableName = "cached_live_inbox_entries",
    primaryKeys = ["account_stable_id", "item_id"],
    indices = [
        Index(value = ["account_stable_id"]),
        Index(value = ["account_stable_id", "position"], unique = true),
    ],
)
internal data class CachedLiveInboxEntryEntity(
    @ColumnInfo(name = "account_stable_id")
    val accountStableId: String,
    @ColumnInfo(name = "item_id")
    val itemId: String,
    val position: Int,
    val payload: String,
)

@Entity(
    tableName = "cached_live_inbox_seen",
    primaryKeys = ["account_stable_id", "item_id"],
    indices = [Index(value = ["account_stable_id"])],
)
internal data class CachedLiveInboxSeenEntity(
    @ColumnInfo(name = "account_stable_id")
    val accountStableId: String,
    @ColumnInfo(name = "item_id")
    val itemId: String,
    @ColumnInfo(name = "seen_at_epoch_ms")
    val seenAtEpochMs: Long,
)

@Entity(
    tableName = "cached_comments",
    primaryKeys = ["post_id", "comment_id"],
    indices = [Index(value = ["post_id", "position"], unique = true)],
)
internal data class CachedCommentEntity(
    @ColumnInfo(name = "comment_id")
    val commentId: String,
    @ColumnInfo(name = "post_id")
    val postId: String,
    val position: Int,
    val payload: String,
    @ColumnInfo(name = "last_access_epoch_ms")
    val lastAccessEpochMs: Long,
)

@Entity(
    tableName = "cached_profile_comments",
    primaryKeys = ["username", "comment_id"],
    indices = [
        Index(value = ["username", "position"], unique = true),
        Index(value = ["post_id"]),
    ],
)
internal data class CachedProfileCommentEntity(
    @ColumnInfo(name = "comment_id")
    val commentId: String,
    val username: String,
    @ColumnInfo(name = "post_id")
    val postId: String,
    val position: Int,
    val payload: String,
    @ColumnInfo(name = "last_access_epoch_ms")
    val lastAccessEpochMs: Long,
)

@Dao
internal interface SubshellCacheDao {
    @Query("SELECT * FROM cached_queries WHERE query_key = :queryKey LIMIT 1")
    fun queryMetadata(queryKey: String): CachedQueryEntity?

    @Query(
        """
        SELECT cached_posts.* FROM cached_posts
        INNER JOIN cached_query_entries
            ON cached_query_entries.post_id = cached_posts.post_id
        WHERE cached_query_entries.query_key = :queryKey
        ORDER BY cached_query_entries.position ASC
        LIMIT :limit OFFSET :offset
        """,
    )
    fun queryPosts(
        queryKey: String,
        offset: Int,
        limit: Int,
    ): List<CachedPostEntity>

    @Query("SELECT COUNT(*) FROM cached_query_entries WHERE query_key = :queryKey")
    fun queryEntryCount(queryKey: String): Int

    @Query("SELECT post_id FROM cached_query_entries WHERE query_key = :queryKey")
    fun queryPostIds(queryKey: String): List<String>

    @Query("SELECT MAX(position) FROM cached_query_entries WHERE query_key = :queryKey")
    fun maxQueryPosition(queryKey: String): Int?

    @Query("SELECT * FROM cached_posts WHERE post_id = :postId LIMIT 1")
    fun post(postId: String): CachedPostEntity?

    // CACHE05_ACCOUNT_SCOPED_POST_ROWS_V1: rows keyed "<scope>|post|<id>".
    @Query(
        """
        SELECT * FROM cached_posts
        WHERE length(post_id) > length(:rowKeySuffix)
          AND substr(post_id, length(post_id) - length(:rowKeySuffix) + 1) = :rowKeySuffix
        ORDER BY last_access_epoch_ms DESC
        LIMIT 1
        """,
    )
    fun scopedPost(rowKeySuffix: String): CachedPostEntity?

    @Upsert
    fun upsertPosts(posts: List<CachedPostEntity>)

    @Upsert
    fun upsertQuery(query: CachedQueryEntity)

    // CACHE01_READ_TOUCHES_ONLY_LAST_ACCESS_V1: a read marks the query used
    // without writing back metadata it read earlier.
    @Query("UPDATE cached_queries SET last_access_epoch_ms = :nowEpochMs WHERE query_key = :queryKey")
    fun touchQuery(queryKey: String, nowEpochMs: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertQueryEntries(entries: List<CachedQueryEntryEntity>)

    @Query("DELETE FROM cached_query_entries WHERE query_key = :queryKey")
    fun deleteQueryEntries(queryKey: String)

    @Query("DELETE FROM cached_queries WHERE query_key = :queryKey")
    fun deleteQuery(queryKey: String)

    @Query("SELECT query_key FROM cached_queries ORDER BY last_access_epoch_ms ASC")
    fun queryKeysByLeastRecentAccess(): List<String>

    @Query("SELECT COUNT(*) FROM cached_queries")
    fun queryCount(): Int

    @Query("SELECT COUNT(*) FROM cached_query_entries")
    fun queryEntryCount(): Int

    @Query("SELECT COUNT(*) FROM cached_posts")
    fun postCount(): Int

    @Query("SELECT COUNT(*) FROM local_mutations")
    fun localMutationCount(): Int

    @Query(
        """
        DELETE FROM cached_posts
        WHERE post_id NOT IN (SELECT post_id FROM cached_query_entries)
          AND post_id NOT IN (
              SELECT subject_id FROM local_mutations WHERE kind = 'CREATED_POST'
          )
        """,
    )
    fun deleteOrphanPosts()

    @Query("DELETE FROM cached_query_entries")
    fun deleteAllQueryEntries()

    @Query("DELETE FROM cached_queries")
    fun deleteAllQueries()

    @Query("DELETE FROM cached_posts")
    fun deleteAllPosts()

    @Upsert
    fun upsertCommunities(values: List<CachedCommunityEntity>)

    @Query("SELECT * FROM cached_communities ORDER BY community_name COLLATE NOCASE LIMIT :limit OFFSET :offset")
    fun communities(offset: Int, limit: Int): List<CachedCommunityEntity>

    @Query("SELECT * FROM cached_communities WHERE community_name = :name COLLATE NOCASE LIMIT 1")
    fun communityByName(name: String): CachedCommunityEntity?

    @Query("DELETE FROM cached_communities")
    fun deleteCommunities()

    @Query("SELECT * FROM cached_subscription_lists WHERE account_stable_id = :accountStableId LIMIT 1")
    fun subscriptionList(accountStableId: String): CachedSubscriptionListEntity?

    @Query("SELECT * FROM cached_subscriptions WHERE account_stable_id = :accountStableId ORDER BY position ASC")
    fun subscriptions(accountStableId: String): List<CachedSubscriptionEntity>

    @Upsert
    fun upsertSubscriptionList(value: CachedSubscriptionListEntity)

    @Upsert
    fun upsertSubscriptions(values: List<CachedSubscriptionEntity>)

    @Query("DELETE FROM cached_subscriptions WHERE account_stable_id = :accountStableId")
    fun deleteSubscriptions(accountStableId: String)

    @Query("DELETE FROM cached_subscriptions")
    fun deleteAllSubscriptions()

    // MED4B_COMMENT_CACHE_CAPPED_V1 (OCB-526): keep the most recently written
    // comment threads / profile comment lists, drop the rest by key.
    @Query(
        """
        DELETE FROM cached_comments WHERE post_id IN (
            SELECT post_id FROM cached_comments GROUP BY post_id
            ORDER BY MAX(last_access_epoch_ms) DESC LIMIT -1 OFFSET :keep
        )
        """,
    )
    fun evictCommentThreadsBeyond(keep: Int)

    @Query(
        """
        DELETE FROM cached_profile_comments WHERE username IN (
            SELECT username FROM cached_profile_comments GROUP BY username
            ORDER BY MAX(last_access_epoch_ms) DESC LIMIT -1 OFFSET :keep
        )
        """,
    )
    fun evictProfileCommentListsBeyond(keep: Int)

    // MED4A_LOGOUT_REMOVES_ACCOUNT_CACHE_V1 (OCB-527): per-account deletes.
    // Prefix matches use substr so '_' and '%' in account ids are literal.
    @Query("DELETE FROM cached_subscription_lists WHERE account_stable_id = :accountStableId")
    fun deleteSubscriptionList(accountStableId: String)

    @Query("DELETE FROM cached_queries WHERE substr(query_key, 1, length(:prefix)) = :prefix")
    fun deleteQueriesWithPrefix(prefix: String)

    @Query("DELETE FROM cached_query_entries WHERE substr(query_key, 1, length(:prefix)) = :prefix")
    fun deleteQueryEntriesWithPrefix(prefix: String)

    @Query("DELETE FROM cached_posts WHERE substr(post_id, 1, length(:prefix)) = :prefix")
    fun deletePostsWithPrefix(prefix: String)

    @Query(
        "DELETE FROM cached_query_entries WHERE post_id = :postId OR " +
            "substr(post_id, -length(:rowSuffix)) = :rowSuffix",
    )
    fun deleteQueryEntriesForPost(postId: String, rowSuffix: String)

    @Query(
        "DELETE FROM cached_posts WHERE post_id = :postId OR " +
            "substr(post_id, -length(:rowSuffix)) = :rowSuffix",
    )
    fun deletePostRows(postId: String, rowSuffix: String)

    @Query("DELETE FROM cached_comments WHERE substr(post_id, 1, length(:prefix)) = :prefix")
    fun deleteCommentsWithPrefix(prefix: String)

    @Query("DELETE FROM cached_profile_comments WHERE substr(username, 1, length(:prefix)) = :prefix")
    fun deleteProfileCommentsWithPrefix(prefix: String)

    @Query("DELETE FROM cached_subscription_lists")
    fun deleteAllSubscriptionLists()

    @Query("SELECT * FROM cached_community_discovery_lists WHERE cache_key = :cacheKey LIMIT 1")
    fun communityDiscoveryList(cacheKey: String): CachedCommunityDiscoveryListEntity?

    @Query("SELECT * FROM cached_community_discovery_entries WHERE cache_key = :cacheKey ORDER BY position ASC")
    fun communityDiscoveryEntries(cacheKey: String): List<CachedCommunityDiscoveryEntryEntity>

    @Upsert
    fun upsertCommunityDiscoveryList(value: CachedCommunityDiscoveryListEntity)

    @Upsert
    fun upsertCommunityDiscoveryEntries(values: List<CachedCommunityDiscoveryEntryEntity>)

    @Query("DELETE FROM cached_community_discovery_entries WHERE cache_key = :cacheKey")
    fun deleteCommunityDiscoveryEntries(cacheKey: String)

    @Query("DELETE FROM cached_community_discovery_entries")
    fun deleteAllCommunityDiscoveryEntries()

    @Query("SELECT cache_key FROM cached_community_discovery_lists ORDER BY last_access_epoch_ms ASC")
    fun communityDiscoveryKeysByLeastRecentAccess(): List<String>

    @Query("SELECT COUNT(*) FROM cached_community_discovery_lists")
    fun communityDiscoveryListCount(): Int

    @Query("DELETE FROM cached_community_discovery_lists WHERE cache_key = :cacheKey")
    fun deleteCommunityDiscoveryList(cacheKey: String)

    @Query("DELETE FROM cached_community_discovery_lists")
    fun deleteAllCommunityDiscoveryLists()

    @Upsert
    fun upsertUsers(values: List<CachedUserEntity>)

    @Query("SELECT * FROM cached_users ORDER BY username COLLATE NOCASE LIMIT :limit OFFSET :offset")
    fun users(offset: Int, limit: Int): List<CachedUserEntity>

    @Query("SELECT * FROM cached_users WHERE username = :username COLLATE NOCASE LIMIT 1")
    fun user(username: String): CachedUserEntity?

    @Query("DELETE FROM cached_users")
    fun deleteUsers()

    @Upsert
    fun upsertInbox(values: List<CachedInboxEntity>)

    @Query("SELECT * FROM cached_inbox ORDER BY item_id LIMIT :limit OFFSET :offset")
    fun inbox(offset: Int, limit: Int): List<CachedInboxEntity>

    @Query("SELECT * FROM cached_inbox WHERE item_id = :itemId LIMIT 1")
    fun inboxItem(itemId: String): CachedInboxEntity?

    @Query("DELETE FROM cached_inbox")
    fun deleteInbox()

    @Query("SELECT * FROM cached_live_inbox_lists WHERE account_stable_id = :accountStableId LIMIT 1")
    fun liveInboxList(accountStableId: String): CachedLiveInboxListEntity?

    // CONC01_INBOX_READ_TOUCHES_ONLY_LAST_ACCESS_V1 (OCB-620), as CACHE01 for feeds.
    @Query("UPDATE cached_live_inbox_lists SET last_access_epoch_ms = :nowEpochMs WHERE account_stable_id = :accountStableId")
    fun touchLiveInboxList(accountStableId: String, nowEpochMs: Long)

    @Query("SELECT * FROM cached_live_inbox_entries WHERE account_stable_id = :accountStableId ORDER BY position ASC")
    fun liveInboxEntries(accountStableId: String): List<CachedLiveInboxEntryEntity>

    @Query("SELECT * FROM cached_live_inbox_entries WHERE account_stable_id = :accountStableId AND item_id = :itemId LIMIT 1")
    fun liveInboxEntry(
        accountStableId: String,
        itemId: String,
    ): CachedLiveInboxEntryEntity?

    @Query("SELECT * FROM cached_live_inbox_seen WHERE account_stable_id = :accountStableId ORDER BY seen_at_epoch_ms ASC")
    fun liveInboxSeen(accountStableId: String): List<CachedLiveInboxSeenEntity>

    @Upsert
    fun upsertLiveInboxList(value: CachedLiveInboxListEntity)

    @Upsert
    fun upsertLiveInboxEntries(values: List<CachedLiveInboxEntryEntity>)

    @Upsert
    fun upsertLiveInboxSeen(value: CachedLiveInboxSeenEntity)

    @Query("DELETE FROM cached_live_inbox_seen WHERE account_stable_id = :accountStableId AND item_id = :itemId")
    fun deleteLiveInboxSeen(
        accountStableId: String,
        itemId: String,
    )

    @Query("DELETE FROM cached_live_inbox_seen WHERE account_stable_id = :accountStableId")
    fun deleteLiveInboxSeenForAccount(accountStableId: String)

    @Query("DELETE FROM cached_live_inbox_entries WHERE account_stable_id = :accountStableId")
    fun deleteLiveInboxEntries(accountStableId: String)

    @Query("DELETE FROM cached_live_inbox_lists WHERE account_stable_id = :accountStableId")
    fun deleteLiveInboxList(accountStableId: String)

    @Query("DELETE FROM cached_live_inbox_entries")
    fun deleteAllLiveInboxEntries()

    @Query("DELETE FROM cached_live_inbox_lists")
    fun deleteAllLiveInboxLists()

    @Query("DELETE FROM cached_live_inbox_seen")
    fun deleteAllLiveInboxSeen()

    @Upsert
    fun upsertComments(values: List<CachedCommentEntity>)

    @Query("SELECT * FROM cached_comments WHERE post_id = :postId ORDER BY position ASC LIMIT :limit OFFSET :offset")
    fun commentsForPost(postId: String, offset: Int, limit: Int): List<CachedCommentEntity>

    @Query("SELECT * FROM cached_comments ORDER BY post_id, position ASC LIMIT :limit OFFSET :offset")
    fun comments(offset: Int, limit: Int): List<CachedCommentEntity>

    @Query("DELETE FROM cached_comments WHERE post_id = :postId")
    fun deleteCommentsForPost(postId: String)

    @Query("DELETE FROM cached_comments")
    fun deleteComments()

    @Upsert
    fun upsertProfileComments(values: List<CachedProfileCommentEntity>)

    @Query("SELECT * FROM cached_profile_comments WHERE username = :username COLLATE NOCASE ORDER BY position ASC LIMIT :limit OFFSET :offset")
    fun profileComments(username: String, offset: Int, limit: Int): List<CachedProfileCommentEntity>

    @Query("SELECT * FROM cached_profile_comments ORDER BY username, position ASC LIMIT :limit OFFSET :offset")
    fun profileComments(offset: Int, limit: Int): List<CachedProfileCommentEntity>

    @Query("DELETE FROM cached_profile_comments WHERE username = :username COLLATE NOCASE")
    fun deleteProfileComments(username: String)

    @Query("SELECT comment_id FROM cached_profile_comments WHERE username = :username COLLATE NOCASE")
    fun profileCommentIds(username: String): List<String>

    @Query("SELECT MAX(position) FROM cached_profile_comments WHERE username = :username COLLATE NOCASE")
    fun maxProfileCommentPosition(username: String): Int?

    @Query("DELETE FROM cached_profile_comments")
    fun deleteProfileComments()

    @Upsert
    fun upsertLocalMutation(value: LocalMutationEntity)

    @Query("SELECT * FROM local_mutations ORDER BY updated_at_epoch_ms ASC, mutation_key ASC")
    fun localMutations(): List<LocalMutationEntity>

    @Query("DELETE FROM local_mutations")
    fun deleteLocalMutations()
}

@Database(
    entities = [
        CachedPostEntity::class,
        CachedQueryEntity::class,
        CachedQueryEntryEntity::class,
        LocalMutationEntity::class,
        CachedCommunityEntity::class,
        CachedSubscriptionListEntity::class,
        CachedSubscriptionEntity::class,
        CachedCommunityDiscoveryListEntity::class,
        CachedCommunityDiscoveryEntryEntity::class,
        CachedUserEntity::class,
        CachedInboxEntity::class,
        CachedLiveInboxListEntity::class,
        CachedLiveInboxEntryEntity::class,
        CachedLiveInboxSeenEntity::class,
        CachedCommentEntity::class,
        CachedProfileCommentEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
internal abstract class SubshellCacheDatabase : RoomDatabase() {
    abstract fun cacheDao(): SubshellCacheDao

    companion object {
        val Migration1To2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cached_communities` (
                        `community_id` TEXT NOT NULL,
                        `community_name` TEXT NOT NULL,
                        `payload` TEXT NOT NULL,
                        `last_access_epoch_ms` INTEGER NOT NULL,
                        PRIMARY KEY(`community_id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cached_users` (
                        `username` TEXT NOT NULL,
                        `payload` TEXT NOT NULL,
                        `last_access_epoch_ms` INTEGER NOT NULL,
                        PRIMARY KEY(`username`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cached_inbox` (
                        `item_id` TEXT NOT NULL,
                        `payload` TEXT NOT NULL,
                        `last_access_epoch_ms` INTEGER NOT NULL,
                        PRIMARY KEY(`item_id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cached_comments` (
                        `comment_id` TEXT NOT NULL,
                        `post_id` TEXT NOT NULL,
                        `position` INTEGER NOT NULL,
                        `payload` TEXT NOT NULL,
                        `last_access_epoch_ms` INTEGER NOT NULL,
                        PRIMARY KEY(`post_id`, `comment_id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_cached_comments_post_id_position` ON `cached_comments` (`post_id`, `position`)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cached_profile_comments` (
                        `comment_id` TEXT NOT NULL,
                        `username` TEXT NOT NULL,
                        `post_id` TEXT NOT NULL,
                        `position` INTEGER NOT NULL,
                        `payload` TEXT NOT NULL,
                        `last_access_epoch_ms` INTEGER NOT NULL,
                        PRIMARY KEY(`username`, `comment_id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_cached_profile_comments_username_position` ON `cached_profile_comments` (`username`, `position`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_cached_profile_comments_post_id` ON `cached_profile_comments` (`post_id`)",
                )
            }
        }

        val Migration2To3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cached_subscription_lists` (
                        `account_stable_id` TEXT NOT NULL,
                        `last_successful_refresh_epoch_ms` INTEGER NOT NULL,
                        PRIMARY KEY(`account_stable_id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cached_subscriptions` (
                        `account_stable_id` TEXT NOT NULL,
                        `community_id` TEXT NOT NULL,
                        `position` INTEGER NOT NULL,
                        `payload` TEXT NOT NULL,
                        PRIMARY KEY(`account_stable_id`, `community_id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_cached_subscriptions_account_stable_id` ON `cached_subscriptions` (`account_stable_id`)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_cached_subscriptions_account_stable_id_position` ON `cached_subscriptions` (`account_stable_id`, `position`)",
                )
            }
        }

        val Migration3To4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cached_community_discovery_lists` (
                        `cache_key` TEXT NOT NULL,
                        `last_successful_refresh_epoch_ms` INTEGER NOT NULL,
                        `last_access_epoch_ms` INTEGER NOT NULL,
                        PRIMARY KEY(`cache_key`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cached_community_discovery_entries` (
                        `cache_key` TEXT NOT NULL,
                        `community_id` TEXT NOT NULL,
                        `position` INTEGER NOT NULL,
                        `payload` TEXT NOT NULL,
                        `over_18` INTEGER NOT NULL,
                        PRIMARY KEY(`cache_key`, `community_id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_cached_community_discovery_entries_cache_key` ON `cached_community_discovery_entries` (`cache_key`)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_cached_community_discovery_entries_cache_key_position` ON `cached_community_discovery_entries` (`cache_key`, `position`)",
                )
            }
        }

        val Migration4To5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cached_live_inbox_lists` (
                        `account_stable_id` TEXT NOT NULL,
                        `next_cursor` TEXT,
                        `last_successful_refresh_epoch_ms` INTEGER NOT NULL,
                        `last_access_epoch_ms` INTEGER NOT NULL,
                        PRIMARY KEY(`account_stable_id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cached_live_inbox_entries` (
                        `account_stable_id` TEXT NOT NULL,
                        `item_id` TEXT NOT NULL,
                        `position` INTEGER NOT NULL,
                        `payload` TEXT NOT NULL,
                        PRIMARY KEY(`account_stable_id`, `item_id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_cached_live_inbox_entries_account_stable_id` ON `cached_live_inbox_entries` (`account_stable_id`)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_cached_live_inbox_entries_account_stable_id_position` ON `cached_live_inbox_entries` (`account_stable_id`, `position`)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cached_live_inbox_seen` (
                        `account_stable_id` TEXT NOT NULL,
                        `item_id` TEXT NOT NULL,
                        `seen_at_epoch_ms` INTEGER NOT NULL,
                        PRIMARY KEY(`account_stable_id`, `item_id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_cached_live_inbox_seen_account_stable_id` ON `cached_live_inbox_seen` (`account_stable_id`)",
                )
            }
        }

        fun open(
            context: Context,
            databaseName: String = SUBSHELL_CACHE_DATABASE_NAME,
        ): SubshellCacheDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                SubshellCacheDatabase::class.java,
                databaseName,
            )
                .addMigrations(
                    Migration1To2,
                    Migration2To3,
                    Migration3To4,
                    Migration4To5,
                    ProtoClean01Migration5To6,
                )
                // CACHEDOWN1_DOWNGRADE_REBUILDS_THE_CACHE_V1 (OCB-622, owner
                // decision 2026-10-07): an older build over a newer schema used
                // to crash on the first query until the app was uninstalled.
                // It now drops and rebuilds this database. That loses the cache
                // (rebuilt from Reddit) and any offline actions in
                // local_mutations not yet sent; uninstalling lost those too.
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .build()
    }
}
