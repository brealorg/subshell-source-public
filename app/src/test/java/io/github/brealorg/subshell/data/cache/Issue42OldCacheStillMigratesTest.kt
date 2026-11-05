package io.github.brealorg.subshell.data.cache

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE42_THE_FIVE_TO_SIX_STEP_IS_EMPTY_V1: the 5 -> 6 step no longer purges prototype-era fixture
 * rows, but a cache on schema 5 still migrates and keeps what it holds.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue42OldCacheStillMigratesTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun aCacheOnSchemaFiveMigratesAndKeepsItsRows() {
        val name = "cache-old-schema-test.db"
        SubshellCacheDatabase.open(context, name).apply {
            openHelper.writableDatabase.query("SELECT 1").close()
            close()
        }
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { raw ->
            // A row that a drop-and-rebuild would lose: the step must keep it.
            raw.execSQL("INSERT INTO cached_live_inbox_lists VALUES ('acct', NULL, 1, 1)")
            raw.version = 5
        }

        val reopened = SubshellCacheDatabase.open(context, name)
        try {
            reopened.openHelper.writableDatabase.query("SELECT COUNT(*) FROM cached_live_inbox_lists").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
        } finally {
            reopened.close()
        }
    }
}
