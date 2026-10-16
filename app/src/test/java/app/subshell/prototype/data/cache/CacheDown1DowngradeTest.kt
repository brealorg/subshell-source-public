package app.subshell.prototype.data.cache

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * CACHEDOWN1_DOWNGRADE_REBUILDS_THE_CACHE_V1 (OCB-622): an older build opened
 * over a newer cache schema rebuilds the cache instead of crashing on the
 * first query.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CacheDown1DowngradeTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun aNewerSchemaOnDiskIsRebuiltNotACrash() {
        val name = "cache-downgrade-test.db"
        SubshellCacheDatabase.open(context, name).apply {
            openHelper.writableDatabase.query("SELECT 1").close()
            close()
        }
        val file = context.getDatabasePath(name)
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { raw ->
            raw.version = 99
        }

        val reopened = SubshellCacheDatabase.open(context, name)
        try {
            reopened.openHelper.writableDatabase.query("SELECT COUNT(*) FROM cached_posts").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        } finally {
            reopened.close()
        }
    }
}
