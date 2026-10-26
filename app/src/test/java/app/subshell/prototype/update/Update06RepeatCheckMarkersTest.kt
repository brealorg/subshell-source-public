package app.subshell.prototype.update

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * UPDATE06 (#63): checking the same manifest again must not reopen the
 * notified/presented markers, or the update is notified and its dialog shown
 * again on every check. Only a correction (a known release replaced by a lower
 * version) reopens them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Update06RepeatCheckMarkersTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val store = UpdateStateStore(application)

    @After
    fun clearPresentation() {
        UpdatePresentationCoordinator.clear()
    }

    @Test
    fun aRepeatCheckOfTheSameReleaseKeepsItsMarkers() = runBlocking {
        val release = release(versionCode = 243, publishedAt = "2026-10-02T10:00:00Z")
        check(release)
        store.markNotified(243)
        store.markUpdatePresented(243)

        check(release)

        assertEquals(243, store.lastNotifiedVersionCode())
        assertEquals(243, store.lastPresentedUpdateVersionCode())
        assertFalse(shouldNotifyUpdate(release, CURRENT, store.lastNotifiedVersionCode()))
        assertFalse(shouldAutoPresentUpdate(release, CURRENT, store.lastPresentedUpdateVersionCode()))
    }

    @Test
    fun aNewerReleaseIsStillOffered() = runBlocking {
        check(release(versionCode = 243, publishedAt = "2026-10-02T10:00:00Z"))
        store.markNotified(243)
        store.markUpdatePresented(243)

        val newer = release(versionCode = 244, publishedAt = "2026-10-03T10:00:00Z")
        check(newer)

        assertEquals(244, store.availableRelease(CURRENT)?.versionCode)
        assertTrue(shouldNotifyUpdate(newer, CURRENT, store.lastNotifiedVersionCode()))
        assertTrue(shouldAutoPresentUpdate(newer, CURRENT, store.lastPresentedUpdateVersionCode()))
    }

    @Test
    fun onlyALowerVersionReplacingAKnownReleaseIsACorrection() {
        val known = release(versionCode = 243, publishedAt = "2026-10-02T10:00:00Z")
        assertTrue(updateManifestCorrects(release(242, "2026-10-03T10:00:00Z"), known))
        assertFalse(updateManifestCorrects(release(243, "2026-10-03T10:00:00Z"), known))
        assertFalse(updateManifestCorrects(release(244, "2026-10-03T10:00:00Z"), known))
        assertFalse(updateManifestCorrects(release(242, "2026-10-03T10:00:00Z"), known = null))
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
