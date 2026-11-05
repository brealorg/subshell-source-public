package io.github.brealorg.subshell.data.reading

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE268_THE_READING_STORE_TOUCHES_NO_DISK_WHEN_MADE_V1: ReadingStatsEffect gets the
 * process store while composing; getting it must not look up noBackupFilesDir (a disk
 * read on the main thread at a cold start).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue268ReadingStoreOffMainTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()

    @After
    fun forget() = ReadingStats.installForTest(null)

    @Test
    fun gettingTheProcessStoreLooksUpNoFolder() {
        ReadingStats.installForTest(null)
        var lookups = 0
        val counting = object : ContextWrapper(application) {
            override fun getApplicationContext(): Context = this
            override fun getNoBackupFilesDir(): File {
                lookups += 1
                return super.getNoBackupFilesDir()
            }
        }

        val store = ReadingStats.store(counting)
        assertEquals(0, lookups)

        store.load()
        assertTrue(lookups > 0)
    }
}
