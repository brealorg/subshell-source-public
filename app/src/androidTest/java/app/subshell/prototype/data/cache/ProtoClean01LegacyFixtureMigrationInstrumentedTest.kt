package app.subshell.prototype.data.cache

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProtoClean01LegacyFixtureMigrationInstrumentedTest {
    @Test
    fun migrationFiveToSixPurgesOnlyLegacyFixtureRows() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(DATABASE_NAME)

        val seed = SubshellCacheDatabase.open(context, DATABASE_NAME)
        val sqlite = seed.openHelper.writableDatabase
        seedRows(sqlite)
        sqlite.execSQL("PRAGMA user_version = 5")
        seed.close()

        val migrated = SubshellCacheDatabase.open(context, DATABASE_NAME)
        val db = migrated.openHelper.writableDatabase

        assertEquals(6, db.version)

        assertEquals(0, count(db, "SELECT COUNT(*) FROM cached_posts WHERE post_id = 'video-lab-a'"))
        assertEquals(1, count(db, "SELECT COUNT(*) FROM cached_posts WHERE post_id = '1livepost'"))
        assertEquals(0, count(db, "SELECT COUNT(*) FROM cached_query_entries WHERE post_id = 'video-lab-a'"))
        assertEquals(1, count(db, "SELECT COUNT(*) FROM cached_query_entries WHERE post_id = '1livepost'"))
        assertEquals(
            0,
            count(
                db,
                "SELECT last_successful_refresh_epoch_ms FROM cached_queries WHERE query_key = 'mixed-feed'",
            ),
        )

        assertEquals(0, count(db, "SELECT COUNT(*) FROM local_mutations WHERE mutation_key = 'saved:video-lab-a'"))
        assertEquals(1, count(db, "SELECT COUNT(*) FROM local_mutations WHERE mutation_key = 'saved:1livepost'"))
        assertEquals(
            0,
            count(
                db,
                "SELECT COUNT(*) FROM local_mutations WHERE mutation_key = 'comment:video-lab-a:local-comment'",
            ),
        )
        assertEquals(
            1,
            count(
                db,
                "SELECT COUNT(*) FROM local_mutations WHERE mutation_key = 'comment:1livepost:local-comment'",
            ),
        )

        assertEquals(0, count(db, "SELECT COUNT(*) FROM cached_communities WHERE community_id = 'fixture-android'"))
        assertEquals(1, count(db, "SELECT COUNT(*) FROM cached_communities WHERE community_id = 'live-android'"))
        assertEquals(
            0,
            count(
                db,
                "SELECT COUNT(*) FROM cached_subscriptions WHERE community_id = 'fixture-android'",
            ),
        )
        assertEquals(
            1,
            count(
                db,
                "SELECT COUNT(*) FROM cached_subscriptions WHERE community_id = 'live-android'",
            ),
        )
        assertEquals(
            0,
            count(
                db,
                "SELECT last_successful_refresh_epoch_ms FROM cached_subscription_lists WHERE account_stable_id = 'account-live'",
            ),
        )

        assertEquals(
            0,
            count(
                db,
                "SELECT COUNT(*) FROM cached_community_discovery_entries WHERE community_id = 'fixture-android'",
            ),
        )
        assertEquals(
            1,
            count(
                db,
                "SELECT COUNT(*) FROM cached_community_discovery_entries WHERE community_id = 'live-android'",
            ),
        )
        assertEquals(
            0,
            count(
                db,
                "SELECT last_successful_refresh_epoch_ms FROM cached_community_discovery_lists WHERE cache_key = 'discover-live'",
            ),
        )

        assertEquals(0, count(db, "SELECT COUNT(*) FROM cached_users WHERE username = 'fixture-root'"))
        assertEquals(1, count(db, "SELECT COUNT(*) FROM cached_users WHERE username = 'live-root'"))
        assertEquals(0, count(db, "SELECT COUNT(*) FROM cached_inbox WHERE item_id = 'fixture-i1'"))
        assertEquals(1, count(db, "SELECT COUNT(*) FROM cached_inbox WHERE item_id = 'live-i1'"))

        assertEquals(
            0,
            count(
                db,
                "SELECT COUNT(*) FROM cached_comments WHERE post_id = 'video-lab-a'",
            ),
        )
        assertEquals(
            1,
            count(
                db,
                "SELECT COUNT(*) FROM cached_comments WHERE post_id = '1livepost'",
            ),
        )
        assertEquals(
            0,
            count(
                db,
                "SELECT COUNT(*) FROM cached_comments WHERE post_id = '__all_comments__' AND comment_id = 'c1'",
            ),
        )
        assertEquals(
            1,
            count(
                db,
                "SELECT COUNT(*) FROM cached_comments WHERE post_id = '__all_comments__' AND comment_id = 'live-comment'",
            ),
        )

        assertEquals(
            0,
            count(
                db,
                "SELECT COUNT(*) FROM cached_profile_comments WHERE comment_id = 'pc-root-backup'",
            ),
        )
        assertEquals(
            1,
            count(
                db,
                "SELECT COUNT(*) FROM cached_profile_comments WHERE comment_id = 'live-profile-comment'",
            ),
        )

        // Live inbox is an authenticated Reddit cache and is intentionally outside PROTOCLEAN01A3.
        assertEquals(
            1,
            count(
                db,
                "SELECT COUNT(*) FROM cached_live_inbox_entries WHERE item_id = 'protected-live-inbox'",
            ),
        )

        migrated.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    private fun seedRows(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        val fixtureCommunity =
            "Twenty-six deterministic local video fixtures for viewport ownership, playback, audio and lifecycle validation."
        val fixtureUser =
            "Local subshell test account. Android, Linux and a home lab with more checkpoints than spare cables."
        val fixtureInbox =
            "A dedicated subreddit destination is the strongest change here."
        val fixtureGenericComment =
            "The important bit is density. Material can work for a reader, but only if every surface earns the space it consumes. 🔥"

        db.execSQL(
            "INSERT INTO cached_posts(post_id,payload,last_access_epoch_ms) VALUES('video-lab-a','fixture-post',1)",
        )
        db.execSQL(
            "INSERT INTO cached_posts(post_id,payload,last_access_epoch_ms) VALUES('1livepost','live-post',1)",
        )
        db.execSQL(
            """
            INSERT INTO cached_queries(
                query_key,generation,next_cursor,last_successful_refresh_epoch_ms,last_access_epoch_ms,terminal
            ) VALUES('mixed-feed',1,'cursor',123,123,1)
            """.trimIndent(),
        )
        db.execSQL(
            "INSERT INTO cached_query_entries(query_key,post_id,position,generation) VALUES('mixed-feed','video-lab-a',0,1)",
        )
        db.execSQL(
            "INSERT INTO cached_query_entries(query_key,post_id,position,generation) VALUES('mixed-feed','1livepost',1,1)",
        )

        db.execSQL(
            "INSERT INTO local_mutations(mutation_key,kind,subject_id,payload,updated_at_epoch_ms) VALUES('saved:video-lab-a','SAVED','video-lab-a','true',1)",
        )
        db.execSQL(
            "INSERT INTO local_mutations(mutation_key,kind,subject_id,payload,updated_at_epoch_ms) VALUES('saved:1livepost','SAVED','1livepost','true',1)",
        )
        db.execSQL(
            "INSERT INTO local_mutations(mutation_key,kind,subject_id,payload,updated_at_epoch_ms) VALUES('comment:video-lab-a:local-comment','COMMENT','local-comment','fixture',1)",
        )
        db.execSQL(
            "INSERT INTO local_mutations(mutation_key,kind,subject_id,payload,updated_at_epoch_ms) VALUES('comment:1livepost:local-comment','COMMENT','local-comment-live','live',1)",
        )

        db.execSQL(
            "INSERT INTO cached_communities(community_id,community_name,payload,last_access_epoch_ms) VALUES('fixture-android','android',?,1)",
            arrayOf("prefix${encodedField(fixtureCommunity)}suffix"),
        )
        db.execSQL(
            "INSERT INTO cached_communities(community_id,community_name,payload,last_access_epoch_ms) VALUES('live-android','android','live reddit payload',1)",
        )

        db.execSQL(
            "INSERT INTO cached_subscription_lists(account_stable_id,last_successful_refresh_epoch_ms) VALUES('account-live',123)",
        )
        db.execSQL(
            "INSERT INTO cached_subscriptions(account_stable_id,community_id,position,payload) VALUES('account-live','fixture-android',0,?)",
            arrayOf("prefix${encodedField(fixtureCommunity)}suffix"),
        )
        db.execSQL(
            "INSERT INTO cached_subscriptions(account_stable_id,community_id,position,payload) VALUES('account-live','live-android',1,'live reddit payload')",
        )

        db.execSQL(
            "INSERT INTO cached_community_discovery_lists(cache_key,last_successful_refresh_epoch_ms,last_access_epoch_ms) VALUES('discover-live',123,123)",
        )
        db.execSQL(
            "INSERT INTO cached_community_discovery_entries(cache_key,community_id,position,payload,over_18) VALUES('discover-live','fixture-android',0,?,0)",
            arrayOf("prefix${encodedField(fixtureCommunity)}suffix"),
        )
        db.execSQL(
            "INSERT INTO cached_community_discovery_entries(cache_key,community_id,position,payload,over_18) VALUES('discover-live','live-android',1,'live reddit payload',0)",
        )

        db.execSQL(
            "INSERT INTO cached_users(username,payload,last_access_epoch_ms) VALUES('fixture-root',?,1)",
            arrayOf("prefix${encodedField(fixtureUser)}suffix"),
        )
        db.execSQL(
            "INSERT INTO cached_users(username,payload,last_access_epoch_ms) VALUES('live-root','live reddit user payload',1)",
        )
        db.execSQL(
            "INSERT INTO cached_inbox(item_id,payload,last_access_epoch_ms) VALUES('fixture-i1',?,1)",
            arrayOf("prefix${encodedField(fixtureInbox)}suffix"),
        )
        db.execSQL(
            "INSERT INTO cached_inbox(item_id,payload,last_access_epoch_ms) VALUES('live-i1','live reddit inbox payload',1)",
        )

        db.execSQL(
            "INSERT INTO cached_comments(comment_id,post_id,position,payload,last_access_epoch_ms) VALUES('fixture-thread','video-lab-a',0,'fixture',1)",
        )
        db.execSQL(
            "INSERT INTO cached_comments(comment_id,post_id,position,payload,last_access_epoch_ms) VALUES('live-thread','1livepost',0,'live',1)",
        )
        db.execSQL(
            "INSERT INTO cached_comments(comment_id,post_id,position,payload,last_access_epoch_ms) VALUES('c1','__all_comments__',0,?,1)",
            arrayOf("prefix${encodedField(fixtureGenericComment)}suffix"),
        )
        db.execSQL(
            "INSERT INTO cached_comments(comment_id,post_id,position,payload,last_access_epoch_ms) VALUES('live-comment','__all_comments__',1,'live',1)",
        )

        db.execSQL(
            "INSERT INTO cached_profile_comments(comment_id,username,post_id,position,payload,last_access_epoch_ms) VALUES('pc-root-backup','root','root-backup-diff',0,'fixture',1)",
        )
        db.execSQL(
            "INSERT INTO cached_profile_comments(comment_id,username,post_id,position,payload,last_access_epoch_ms) VALUES('live-profile-comment','live-user','1livepost',0,'live',1)",
        )

        db.execSQL(
            "INSERT INTO cached_live_inbox_lists(account_stable_id,next_cursor,last_successful_refresh_epoch_ms,last_access_epoch_ms) VALUES('account-live',NULL,123,123)",
        )
        db.execSQL(
            "INSERT INTO cached_live_inbox_entries(account_stable_id,item_id,position,payload) VALUES('account-live','protected-live-inbox',0,?)",
            arrayOf("prefix${encodedField(fixtureInbox)}suffix"),
        )
    }

    private fun encodedField(value: String): String = "${value.length}:$value"


    private fun count(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        sql: String,
    ): Int = db.query(sql).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getInt(0)
    }

    companion object {
        private const val DATABASE_NAME = "subshell-protoclean01a3-migration.db"
    }
}
