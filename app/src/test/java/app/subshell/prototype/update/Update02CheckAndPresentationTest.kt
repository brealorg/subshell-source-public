package app.subshell.prototype.update

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.network.SubshellNetworkJson
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
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
 * UPDATE02 (review of the pre-Claude update package):
 * - a manifest that could not be fetched is not a check (the stale window
 *   stays open and the worker retries); a manifest that was read and
 *   rejected is one; cancellation is rethrown;
 * - a last check in the future counts as stale;
 * - the "open update" notification intent opens the dialog once, never
 *   again from the same intent or from a Recents relaunch;
 * - the release URL is kept exactly as validated and opened as a browsable
 *   link.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Update02CheckAndPresentationTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val store = UpdateStateStore(application)

    @After
    fun clearPresentation() {
        UpdatePresentationCoordinator.clear()
    }

    @Test
    fun aFailedFetchIsNotACheck() = runBlocking {
        listOf(
            client { throw IOException("offline") },
            client { response(code = 500, body = "") },
            client { response(code = 301, body = "") },
        ).forEach { failing ->
            assertEquals(UpdateManifestFetch.Failed, failing.fetch())
            assertFalse(check(failing, now = 10_000L))
            assertTrue(store.shouldCheck(10_000L, UPDATE01_FOREGROUND_STALE_INTERVAL_MILLIS))
        }
    }

    @Test
    fun aManifestThatWasReadIsACheckEvenWhenRejected() = runBlocking {
        val garbage = client { response(code = 200, body = "{not json") }
        assertEquals(UpdateManifestFetch.Completed(release = null), garbage.fetch())

        val tooLarge = client { response(code = 200, body = "x".repeat(UPDATE01_MAX_MANIFEST_BYTES + 1)) }
        assertEquals(UpdateManifestFetch.Completed(release = null), tooLarge.fetch())

        val wrongChannel = client { response(code = 200, body = manifestJson(release().copy(channel = "stable"))) }
        assertEquals(UpdateManifestFetch.Completed(release = null), wrongChannel.fetch())

        assertTrue(check(garbage, now = 20_000L))
        assertFalse(store.shouldCheck(20_000L, UPDATE01_FOREGROUND_STALE_INTERVAL_MILLIS))
    }

    @Test
    fun aValidManifestIsCompleted() = runBlocking {
        val fetched = client { response(code = 200, body = manifestJson(release())) }.fetch()
        assertEquals(242, (fetched as UpdateManifestFetch.Completed).release?.versionCode)
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsRethrown() {
        runBlocking { client { throw CancellationException("cancelled") }.fetch() }
    }

    @Test
    fun aReturningReaderChecksAgainAfterAnHour() {
        val hour = 60L * 60L * 1000L
        store.recordCheck(1_000_000L)
        assertFalse(store.shouldCheck(1_000_000L + hour - 1L, UPDATE01_FOREGROUND_STALE_INTERVAL_MILLIS))
        assertTrue(store.shouldCheck(1_000_000L + hour, UPDATE01_FOREGROUND_STALE_INTERVAL_MILLIS))
    }

    @Test
    fun aLastCheckInTheFutureIsStale() {
        store.recordCheck(5_000_000L)
        assertTrue(store.shouldCheck(1_000L, UPDATE01_FOREGROUND_STALE_INTERVAL_MILLIS))
        assertFalse(store.shouldCheck(5_000_001L, UPDATE01_FOREGROUND_STALE_INTERVAL_MILLIS))
    }

    @Test
    fun theOpenUpdateIntentOpensTheDialogOnlyOnce() {
        store.saveAvailableRelease(release())
        val intent = openUpdateIntent()

        assertTrue(consume(intent))
        assertTrue(UpdatePresentationCoordinator.request.value is UpdatePresentationRequest.UpdateAvailable)
        assertNull(intent.action)

        UpdatePresentationCoordinator.clear()
        // A recreated activity sees the same intent object again.
        assertFalse(consume(intent))
        assertNull(UpdatePresentationCoordinator.request.value)
    }

    @Test
    fun aRecentsRelaunchDoesNotReopenTheDialog() {
        store.saveAvailableRelease(release())
        val intent = openUpdateIntent().addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)

        assertTrue(consume(intent))
        assertNull(UpdatePresentationCoordinator.request.value)
        assertNull(intent.action)
    }

    @Test
    fun theReleaseUrlIsKeptAsValidatedAndOpenedAsABrowsableLink() {
        val normalized = normalizeUpdateManifest(
            release().copy(releaseUrl = "  https://GITHUB.com/brealorg/subshell/releases/tag/v0.78.0-beta.1  "),
        )
        assertEquals(
            "https://github.com/brealorg/subshell/releases/tag/v0.78.0-beta.1",
            normalized?.releaseUrl,
        )
        val intent = updateReleaseViewIntent(normalized!!.releaseUrl)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertTrue(intent.hasCategory(Intent.CATEGORY_BROWSABLE))
    }

    private suspend fun check(client: UpdateManifestClient, now: Long): Boolean =
        UpdateCheckCoordinator.check(
            context = application,
            force = false,
            nowEpochMillis = now,
            client = client,
            enabled = true,
            currentVersionCode = 241,
        )

    private fun consume(intent: Intent): Boolean =
        consumeUpdatePresentationIntent(
            context = application,
            intent = intent,
            currentVersionCode = 241,
            debug = false,
        )

    private fun openUpdateIntent() = Intent(UPDATE01_ACTION_OPEN_UPDATE)
        .putExtra(UPDATE01_EXTRA_VERSION_CODE, 242)

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

    private fun manifestJson(release: UpdateManifestRelease): String =
        SubshellNetworkJson.providerJson.encodeToString(release)

    private fun client(answer: Request.() -> Response) = UpdateManifestClient(
        callFactory = OkHttpClient.Builder()
            .addInterceptor { chain -> chain.request().answer() }
            .build(),
    )

    private fun Request.response(code: Int, body: String): Response =
        Response.Builder()
            .request(this)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("test")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
}
