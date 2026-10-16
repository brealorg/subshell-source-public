package app.subshell.prototype.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * BACKUP11 (#130): listSnapshots() waited on the repository monitor that a
 * folder sync holds through all its provider reads and writes, so the Saved
 * setups screen (which lists on the main thread) froze during a slow sync.
 * Listing no longer takes that lock.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Backup11ListWithoutLockTest {
    @Test
    fun listingDoesNotWaitForAnOperationHoldingTheRepository() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = SettingsBackupRepository(context, appVersion = "test")
        repository.deleteAllSnapshots()
        val before = repository.listSnapshots().size

        val holding = CountDownLatch(1)
        val release = CountDownLatch(1)
        val sync = Executors.newSingleThreadExecutor()
        try {
            // Stands in for a slow folder sync: the repository's monitor held.
            sync.execute {
                synchronized(repository) {
                    holding.countDown()
                    release.await(10, TimeUnit.SECONDS)
                }
            }
            holding.await(5, TimeUnit.SECONDS)

            val lister = Executors.newSingleThreadExecutor()
            val listed = lister.submit<Int> { repository.listSnapshots().size }
            assertEquals(before, listed.get(2, TimeUnit.SECONDS))
            lister.shutdownNow()
        } finally {
            release.countDown()
            sync.shutdownNow()
        }
    }
}
