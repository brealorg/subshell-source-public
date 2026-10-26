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
 * SECAUDIT01 (security audit 2026-10-22, #1): one forged manifest with the
 * highest versionCode and a publishedAt far in the future must not be kept
 * for good. It is refused when it arrives; one already stored (from an app
 * without this fence) gives way to the next real manifest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SecAudit01ManifestFromTheFutureTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val store = UpdateStateStore(application)

    @After
    fun clearPresentation() {
        UpdatePresentationCoordinator.clear()
    }

    @Test
    fun aManifestPublishedFarAheadIsRefused() = runBlocking {
        check(release(versionCode = 243, publishedAt = "2026-10-03T10:00:00Z"))

        check(release(versionCode = Int.MAX_VALUE, publishedAt = "9999-12-31T23:59:59Z"))

        assertEquals(243, store.availableRelease(CURRENT)?.versionCode)
    }

    @Test
    fun aStoredManifestFromTheFutureGivesWayToTheNextRealOne() = runBlocking {
        store.saveAvailableRelease(release(versionCode = Int.MAX_VALUE, publishedAt = "9999-12-31T23:59:59Z"))
        store.markNotified(Int.MAX_VALUE)
        store.markUpdatePresented(Int.MAX_VALUE)

        val real = release(versionCode = 243, publishedAt = "2026-10-03T10:00:00Z")
        check(real)

        assertEquals(243, store.availableRelease(CURRENT)?.versionCode)
        // The forged release's markers would otherwise silence every real one.
        assertTrue(shouldAutoPresentUpdate(real, CURRENT, store.lastPresentedUpdateVersionCode()))
        // 243 when the notification was posted, lower when it could not be.
        assertTrue(store.lastNotifiedVersionCode() <= 243)
    }

    @Test
    fun aClockThatRunsALittleBehindStillAcceptsTheRelease() {
        val known = release(versionCode = 242, publishedAt = "2026-10-01T10:00:00Z")
        val inTwoDays = release(versionCode = 243, publishedAt = "2026-10-05T23:59:59Z")
        val inThreeDays = release(versionCode = 243, publishedAt = "2026-10-07T00:00:01Z")

        assertTrue(updateManifestSupersedes(inTwoDays, known, NOW))
        assertFalse(updateManifestSupersedes(inThreeDays, known, NOW))
        assertFalse(updateManifestSupersedes(inThreeDays, known = null, NOW))
    }

    @Test
    fun aStaleCopyStillDoesNotReplaceARealNewerRelease() {
        val known = release(versionCode = 243, publishedAt = "2026-10-05T10:00:00Z")
        assertFalse(updateManifestSupersedes(release(242, "2026-10-01T10:00:00Z"), known, NOW))
    }

    @Test
    fun aYearBeyondWhatEpochMillisHoldsIsRefusedWithoutAnError() {
        val farBeyond = release(versionCode = 243, publishedAt = "+1000000-01-01T00:00:00Z")
        assertFalse(updateManifestSupersedes(farBeyond, known = null, NOW))
    }

    @Test
    fun aRefusedManifestLeavesNoOffer() = runBlocking {
        check(release(versionCode = 243, publishedAt = "9999-12-31T23:59:59Z"))

        assertNull(store.availableRelease(CURRENT))
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
        val NOW = Instant.parse("2026-10-04T00:00:00Z").toEpochMilli()
    }
}
