package app.subshell.prototype.backup

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.ui.SubshellSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (backup04a4): saved moderator responses are shared by every account
 * on the device, so they go into a backup only when the reader opts in. The
 * opt-in is off on a fresh install; a setup saved while it is off carries no
 * responses module at all (a later restore leaves the current ones alone),
 * and once it is on the responses round-trip.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3ModeratorResponsesBackupOptInTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val responses = listOf(
        PortableModeratorSavedResponse("r1", "Rule 1", "Please read rule 1.", 10L),
        PortableModeratorSavedResponse("r2", "Spam", "Removed as spam.", 20L),
    )

    private fun repository() = SettingsBackupRepository(application, appVersion = "test")

    private fun saved(repository: SettingsBackupRepository, name: String): SettingsSnapshot =
        repository.listSnapshots().single { it.name == name }

    @Test
    fun theOptInIsOffOnAFreshInstall() {
        assertFalse(repository().moderatorSavedResponsesBackupEnabled())
    }

    @Test
    fun aSetupSavedWithTheOptInOffCarriesNoResponses() {
        val repository = repository()
        repository.saveCurrent(
            name = "Without responses",
            settings = SubshellSettings(),
            moderatorSavedResponses = responses,
        )

        assertNull(saved(repository, "Without responses").moderatorSavedResponses)
    }

    @Test
    fun withTheOptInOnTheResponsesRoundTrip() {
        val repository = repository()
        repository.setModeratorSavedResponsesBackupEnabled(true)
        repository.saveCurrent(
            name = "With responses",
            settings = SubshellSettings(),
            moderatorSavedResponses = responses,
        )

        assertEquals(
            responses.map { it.title }.toSet(),
            saved(repository, "With responses").moderatorSavedResponses?.map { it.title }?.toSet(),
        )
    }
}
