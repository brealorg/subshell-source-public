package app.subshell.prototype.data.diagnostics

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STRICTMODE02_DIAGNOSTIC_FOLDER_RESOLVED_ON_FIRST_USE_V1 (#243): every error
 * report on screen makes a store in composition, on Main; resolving the
 * no-backup folder checks the disk, so it waits for the first read or write.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StrictMode02DiagnosticFolderTest {
    private class CountingContext(base: Context) : ContextWrapper(base) {
        var folderLookups = 0

        override fun getApplicationContext(): Context = this

        override fun getNoBackupFilesDir(): File {
            folderLookups += 1
            return super.getNoBackupFilesDir()
        }
    }

    @Test
    fun makingAStoreDoesNotLookUpItsFolder() {
        val context = CountingContext(ApplicationProvider.getApplicationContext<Application>())

        val store = DiagnosticEventStore(context)
        assertEquals(0, context.folderLookups)

        store.snapshot()
        assertEquals(1, context.folderLookups)
        store.snapshot()
        assertEquals(1, context.folderLookups)
    }
}
