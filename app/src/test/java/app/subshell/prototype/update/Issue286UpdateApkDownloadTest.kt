package app.subshell.prototype.update

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ISSUE286_UPDATE_DOWNLOADS_ONLY_THE_RELEASE_APK_V1: Update fetches the
 * release's own APK from GitHub, follows redirects only to GitHub's hosts over
 * https, stops at the size limit and keeps the file only when its SHA-256 is
 * the manifest's.
 */
class Issue286UpdateApkDownloadTest {
    private val apk = ByteArray(4096) { index -> (index % 251).toByte() }
    private val assetUrl = "https://release-assets.githubusercontent.com/github-production-release-asset/1/apk?sig=x"
    private val dir: File = Files.createTempDirectory("issue286").toFile()
    private val target = File(dir, "update/Subshell.apk")

    private fun release(sha: String = sha256(apk)) = UpdateManifestRelease(
        schemaVersion = UPDATE01_MANIFEST_SCHEMA_VERSION,
        channel = UPDATE01_BETA_CHANNEL,
        versionCode = 255,
        versionName = "0.91-beta",
        publishedAt = "2026-11-02T10:00:00Z",
        releaseUrl = "https://github.com/brealorg/subshell/releases/tag/v0.91-beta",
        apkSha256 = sha,
        headline = "Subshell 0.91",
    )

    private val asked = mutableListOf<String>()

    private fun github(
        redirectTo: String? = assetUrl,
        code: Int = 200,
        body: () -> ResponseBody = { apk.toResponseBody(APK) },
    ) = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            asked += request.url.toString()
            if (request.url.host == "github.com" && redirectTo != null) {
                respond(request, 302, "".toResponseBody(null)).newBuilder().header("Location", redirectTo).build()
            } else {
                respond(request, code, body())
            }
        }
        .build()

    @Test
    fun theAddressIsTheReleasesOwnApk() {
        assertEquals(
            "https://github.com/brealorg/subshell/releases/download/v0.91-beta/Subshell-0.91-beta.apk",
            updateApkUrl(release()).toString(),
        )
        assertTrue(updateApkDownloadAllowed(okhttp3.HttpUrl.Builder().scheme("https").host("objects.githubusercontent.com").build()))
        assertFalse(updateApkDownloadAllowed(okhttp3.HttpUrl.Builder().scheme("http").host("github.com").build()))
        assertFalse(updateApkDownloadAllowed(okhttp3.HttpUrl.Builder().scheme("https").host("github.com.example.org").build()))
    }

    @Test
    fun theReleaseApkThroughGitHubsRedirectIsKept() = runBlocking {
        val result = UpdateApkDownloader(github()).download(release(), target)

        assertEquals(UpdateApkDownload.Verified(target), result)
        assertTrue(target.readBytes().contentEquals(apk))
        assertEquals(listOf(updateApkUrl(release()).toString(), assetUrl), asked)
    }

    @Test
    fun aRedirectAwayFromGitHubIsNotFollowed() = runBlocking {
        for (elsewhere in listOf("https://example.org/Subshell.apk", "http://objects.githubusercontent.com/x.apk")) {
            asked.clear()
            val result = UpdateApkDownloader(github(redirectTo = elsewhere)).download(release(), target)

            assertEquals(UpdateApkDownload.Failed(UpdateApkFailure.UNEXPECTED_ADDRESS), result)
            assertEquals(listOf(updateApkUrl(release()).toString()), asked)
            assertFalse(target.exists())
        }
    }

    @Test
    fun aFileThatIsNotThePublishedOneIsDeleted() = runBlocking {
        val result = UpdateApkDownloader(github()).download(release(sha = sha256(byteArrayOf(1))), target)

        assertEquals(UpdateApkDownload.Failed(UpdateApkFailure.HASH_MISMATCH), result)
        assertFalse(target.exists())
    }

    @Test
    fun aDownloadOverTheLimitStops() = runBlocking {
        // Said up front, and found while reading (no length given).
        val stated = UpdateApkDownloader(github(), maxBytes = 1000).download(release(), target)
        val unstated = UpdateApkDownloader(
            github(body = { UnsizedBody(apk) }),
            maxBytes = 1000,
        ).download(release(), target)

        assertEquals(UpdateApkDownload.Failed(UpdateApkFailure.TOO_LARGE), stated)
        assertEquals(UpdateApkDownload.Failed(UpdateApkFailure.TOO_LARGE), unstated)
        assertFalse(target.exists())
    }

    @Test
    fun aMissingApkOrAFailedConnectionIsSaid() = runBlocking {
        val missing = UpdateApkDownloader(github(code = 404)).download(release(), target)
        val offline = UpdateApkDownloader(
            OkHttpClient.Builder().addInterceptor { throw java.io.IOException("offline") }.build(),
        ).download(release(), target)

        assertEquals(UpdateApkDownload.Failed(UpdateApkFailure.NOT_FOUND), missing)
        assertEquals(UpdateApkDownload.Failed(UpdateApkFailure.NETWORK), offline)
    }

    private class UnsizedBody(private val bytes: ByteArray) : ResponseBody() {
        override fun contentType() = APK
        override fun contentLength() = -1L
        override fun source(): BufferedSource = Buffer().write(bytes)
    }

    private companion object {
        val APK = "application/vnd.android.package-archive".toMediaType()

        fun respond(request: Request, code: Int, body: ResponseBody): Response =
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("x")
                .body(body)
                .build()

        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
