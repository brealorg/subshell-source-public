package app.subshell.prototype.data.cache

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.RepositoryPageRequest
import app.subshell.prototype.data.RepositoryQueryKey
import app.subshell.prototype.data.RepositoryResult
import app.subshell.prototype.data.SampleData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubshellCacheMigrationInstrumentedTest {
    @Test
    fun legacyHomeFeedCopiesOnceWithoutDeletingRollbackSource() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(COPY_DATABASE_NAME)
        val store = RoomSubshellCacheStore(
            database = SubshellCacheDatabase.open(context, COPY_DATABASE_NAME),
        )
        val legacy = RepositoryQueryKey("main|home|best")
        val scoped = RepositoryQueryKey(
            "account|authenticated|reddit:redreader_compatibility:t2_live|main|home|best",
        )
        val posts = SampleData.posts.take(3)
        store.writePostResult(
            request = RepositoryPageRequest(
                limit = 10,
                queryKey = legacy,
                operation = RepositoryLoadOperation.REFRESH,
            ),
            result = RepositoryResult.Content(
                value = posts,
                nextCursor = "t3_after",
            ),
        )

        assertTrue(store.copyPostFeedIfAbsent(legacy, scoped))
        assertTrue(!store.copyPostFeedIfAbsent(legacy, scoped))
        assertEquals(posts.map { it.id }, store.readPostFeedSnapshot(scoped)?.posts?.map { it.id })
        assertEquals(posts.map { it.id }, store.readPostFeedSnapshot(legacy)?.posts?.map { it.id })

        store.close()
        context.deleteDatabase(COPY_DATABASE_NAME)
    }

    @Test
    fun migrationsOneToSixCreateMetadataSubscriptionDiscoveryAndInboxTables() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(DATABASE_NAME)

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DATABASE_NAME)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(1) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            createVersionOneSchema(db)
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    },
                )
                .build(),
        )
        helper.writableDatabase
        helper.close()

        val database = SubshellCacheDatabase.open(
            context = context,
            databaseName = DATABASE_NAME,
        )
        val sqlite = database.openHelper.writableDatabase
        val tables = mutableSetOf<String>()
        sqlite.query(
            "SELECT name FROM sqlite_master WHERE type = 'table'",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                tables += cursor.getString(0)
            }
        }

        assertTrue("cached_posts" in tables)
        assertTrue("cached_queries" in tables)
        assertTrue("cached_query_entries" in tables)
        assertTrue("local_mutations" in tables)
        assertTrue("cached_communities" in tables)
        assertTrue("cached_subscription_lists" in tables)
        assertTrue("cached_subscriptions" in tables)
        assertTrue("cached_community_discovery_lists" in tables)
        assertTrue("cached_community_discovery_entries" in tables)
        assertTrue("cached_users" in tables)
        assertTrue("cached_inbox" in tables)
        assertTrue("cached_live_inbox_lists" in tables)
        assertTrue("cached_live_inbox_entries" in tables)
        assertTrue("cached_live_inbox_seen" in tables)
        assertTrue("cached_comments" in tables)
        assertTrue("cached_profile_comments" in tables)
        assertEquals(6, sqlite.version)

        database.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    private fun createVersionOneSchema(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `cached_posts` (
                `post_id` TEXT NOT NULL,
                `payload` TEXT NOT NULL,
                `last_access_epoch_ms` INTEGER NOT NULL,
                PRIMARY KEY(`post_id`)
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `cached_queries` (
                `query_key` TEXT NOT NULL,
                `generation` INTEGER NOT NULL,
                `next_cursor` TEXT,
                `last_successful_refresh_epoch_ms` INTEGER NOT NULL,
                `last_access_epoch_ms` INTEGER NOT NULL,
                `terminal` INTEGER NOT NULL,
                PRIMARY KEY(`query_key`)
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `cached_query_entries` (
                `query_key` TEXT NOT NULL,
                `post_id` TEXT NOT NULL,
                `position` INTEGER NOT NULL,
                `generation` INTEGER NOT NULL,
                PRIMARY KEY(`query_key`, `post_id`),
                FOREIGN KEY(`query_key`) REFERENCES `cached_queries`(`query_key`)
                    ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`post_id`) REFERENCES `cached_posts`(`post_id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_cached_query_entries_query_key` ON `cached_query_entries` (`query_key`)",
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_cached_query_entries_post_id` ON `cached_query_entries` (`post_id`)",
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_cached_query_entries_query_key_position` ON `cached_query_entries` (`query_key`, `position`)",
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `local_mutations` (
                `mutation_key` TEXT NOT NULL,
                `kind` TEXT NOT NULL,
                `subject_id` TEXT NOT NULL,
                `payload` TEXT NOT NULL,
                `updated_at_epoch_ms` INTEGER NOT NULL,
                PRIMARY KEY(`mutation_key`)
            )
            """.trimIndent(),
        )
    }

    companion object {
        private const val DATABASE_NAME = "subshell-data03-migration.db"
        private const val COPY_DATABASE_NAME = "subshell-live01-query-copy.db"
    }
}
