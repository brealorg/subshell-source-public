package app.subshell.prototype.update

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * UPDATE04 (owner decisions on the update review, 1b / 5a / 6c):
 * - the dialog shows the APK hash;
 * - the release URL must be exactly .../releases/tag/v<versionName>;
 * - three days of checks without a usable manifest mark the update check as
 *   failing; a usable manifest clears it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Update04ManifestTrustAndHealthTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val store = UpdateStateStore(application)

    @After
    fun clearPresentation() {
        UpdatePresentationCoordinator.clear()
    }

    @Test
    fun theDialogShowsTheApkHash() {
        assertEquals("APK SHA-256: ${"a".repeat(64)}", updateApkHashLine(release()))
    }

    @Test
    fun theReleaseUrlMustNameTheSameVersion() {
        assertNotNull(normalizeUpdateManifest(release()))
        assertNull(
            normalizeUpdateManifest(
                release().copy(releaseUrl = "https://github.com/brealorg/subshell/releases/tag/v0.78.0-beta.0"),
            ),
        )
        assertNull(
            normalizeUpdateManifest(
                release().copy(releaseUrl = "https://github.com/brealorg/subshell/releases/tag/v0.78.0-beta.1?x=1"),
            ),
        )
        assertNull(
            normalizeUpdateManifest(
                release().copy(releaseUrl = "https://github.com/brealorg/subshell/releases/tag/v0.78.0-beta.1#top"),
            ),
        )
        assertNull(
            normalizeUpdateManifest(
                release().copy(releaseUrl = "https://github.com/brealorg/subshell/releases/tag/v0.78.0-beta.1/extra"),
            ),
        )
    }

    @Test
    fun threeDaysWithoutAUsableManifestMarkTheCheckAsFailing() = runBlocking {
        check(UpdateManifestFetch.Failed, now = START)
        assertFalse(store.updateCheckFailing(START + DAY * 2))

        // A rejected manifest is no better than none; the start time stays.
        check(UpdateManifestFetch.Completed(release = null), now = START + DAY)
        assertTrue(store.updateCheckFailing(START + DAY * 3))

        check(UpdateManifestFetch.Completed(release()), now = START + DAY * 4)
        assertFalse(store.updateCheckFailing(START + DAY * 10))
    }

    private suspend fun check(fetch: UpdateManifestFetch, now: Long) {
        UpdateCheckCoordinator.check(
            context = application,
            force = true,
            nowEpochMillis = now,
            client = object : UpdateManifestSource {
                override suspend fun fetch(): UpdateManifestFetch = fetch
            },
            enabled = true,
            currentVersionCode = 241,
        )
    }

    private fun release() = UpdateManifestRelease(
        schemaVersion = 1,
        channel = "beta",
        versionCode = 242,
        versionName = "0.78.0-beta.1",
        publishedAt = "2026-09-27T10:00:00Z",
        releaseUrl = "https://github.com/brealorg/subshell/releases/tag/v0.78.0-beta.1",
        apkSha256 = "a".repeat(64),
        headline = "A Subshell beta update",
        highlights = listOf("One"),
    )

    private companion object {
        const val DAY = 24L * 60L * 60L * 1000L
        const val START = 1_000_000_000L
    }
}
