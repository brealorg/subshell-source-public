package app.subshell.prototype.update

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * UPDATE03 (review of the pre-Claude update package, findings 2 and 7):
 * - a manifest with a mistyped high versionCode no longer silences later
 *   releases once a corrected manifest (published later) replaces it;
 * - a stale copy (lower versionCode, published earlier) no longer makes a
 *   known newer release disappear;
 * - a withdrawn release (a later manifest with a lower versionCode) still
 *   clears it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Update03ManifestFreshnessTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val store = UpdateStateStore(application)

    @After
    fun clearPresentation() {
        UpdatePresentationCoordinator.clear()
    }

    @Test
    fun aCorrectedManifestReopensTheMarkersAMistypedOneRaised() = runBlocking {
        check(release(versionCode = 2410, publishedAt = "2026-10-01T10:00:00Z"))
        store.markNotified(2410)
        store.markUpdatePresented(2410)

        check(release(versionCode = 242, publishedAt = "2026-10-01T11:00:00Z"))

        val known = store.availableRelease(CURRENT)
        assertEquals(242, known?.versionCode)
        assertTrue(shouldNotifyUpdate(known!!, CURRENT, store.lastNotifiedVersionCode()))
        assertTrue(shouldAutoPresentUpdate(known, CURRENT, store.lastPresentedUpdateVersionCode()))
    }

    @Test
    fun aStaleCopyDoesNotReplaceAKnownNewerRelease() = runBlocking {
        check(release(versionCode = 243, publishedAt = "2026-10-02T10:00:00Z"))
        store.markNotified(243)

        check(release(versionCode = 242, publishedAt = "2026-10-01T10:00:00Z"))

        assertEquals(243, store.availableRelease(CURRENT)?.versionCode)
        assertEquals(243, store.lastNotifiedVersionCode())
    }

    @Test
    fun aWithdrawnReleaseIsStillCleared() = runBlocking {
        check(release(versionCode = 243, publishedAt = "2026-10-02T10:00:00Z"))

        // A later manifest says the newest beta is the installed one.
        check(release(versionCode = CURRENT, publishedAt = "2026-10-03T10:00:00Z"))

        assertNull(store.availableRelease(CURRENT))
    }

    @Test
    fun theSameOrAHigherVersionAlwaysSupersedes() {
        val known = release(versionCode = 242, publishedAt = "2026-10-02T10:00:00Z")
        assertTrue(updateManifestSupersedes(release(242, "2026-10-01T10:00:00Z"), known, NOW))
        assertTrue(updateManifestSupersedes(release(243, "2026-10-01T10:00:00Z"), known, NOW))
        assertTrue(updateManifestSupersedes(release(241, "2026-10-01T10:00:00Z"), known = null, NOW))
        assertFalse(updateManifestSupersedes(release(241, "2026-10-01T10:00:00Z"), known, NOW))
    }

    @Test
    fun anUnreadablePublicationTimeLetsTheIncomingManifestWin() {
        val known = release(versionCode = 243, publishedAt = "2026-10-02T10:00:00Z")
        assertTrue(updateManifestSupersedes(release(242, "yesterday"), known, NOW))
        assertTrue(updateManifestSupersedes(release(242, "2026-10-01T10:00:00Z"), known.copy(publishedAt = "?"), NOW))
    }

    private suspend fun check(release: UpdateManifestRelease) {
        UpdateCheckCoordinator.check(
            context = application,
            force = true,
            nowEpochMillis = NOW,
            client = FixedManifestClient(release),
            enabled = true,
            currentVersionCode = CURRENT,
        )
    }

    private class FixedManifestClient(
        private val release: UpdateManifestRelease,
    ) : UpdateManifestSource {
        override suspend fun fetch(): UpdateManifestFetch = UpdateManifestFetch.Completed(release)
    }

    private fun release(versionCode: Int, publishedAt: String) = UpdateManifestRelease(
        schemaVersion = 1,
        channel = "beta",
        versionCode = versionCode,
        versionName = "0.78.0-beta.$versionCode",
        publishedAt = publishedAt,
        releaseUrl = "https://github.com/brealorg/subshell/releases/tag/v0.78.0-beta.$versionCode",
        apkSha256 = "a".repeat(64),
        headline = "A Subshell beta update",
        highlights = listOf("One"),
    )

    private companion object {
        const val CURRENT = 241

        // A device clock just after the fixtures' publishedAt (SECAUDIT01).
        val NOW = Instant.parse("2026-10-04T00:00:00Z").toEpochMilli()
    }
}
