package app.subshell.prototype.backup

import android.app.Application
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * BACKUP12 (OCB-598): each Activity used to get its own backup repository
 * (remember(context)), so a recreated Activity's instance could run beside
 * the old one with a different @Synchronized monitor. Every context of one
 * Application now gets the same repository.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Backup12OneRepositoryTest {
    @Test
    fun twoActivityContextsOfOneApplicationShareTheRepository() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val firstActivity = ContextWrapper(application)
        val recreatedActivity = ContextWrapper(application)

        assertSame(
            processSettingsBackupRepository(firstActivity),
            processSettingsBackupRepository(recreatedActivity),
        )
    }
}
