package io.github.brealorg.subshell.update

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/*
 * ISSUE286_UPDATE_DOWNLOADS_ONLY_THE_RELEASE_APK_V1 (#286, owner: "Yes, change
 * the rule"): Update downloads the APK of the release the manifest names, and
 * nothing else. The address is the release tooling's own asset name
 * (promote-github-beta.py: Subshell-<versionName>.apk on the release tag), so
 * the manifest needs no new field. GitHub answers with a redirect to its
 * download host; only GitHub's hosts over https are followed, the file may be
 * at most UPDATE_MAX_APK_BYTES, and it is kept only when its SHA-256 is the
 * manifest's apkSha256. Android's installer then checks the signature.
 */

internal const val UPDATE_MAX_APK_BYTES = 30L * 1024L * 1024L
internal const val UPDATE_MAX_APK_REDIRECTS = 5

internal val UPDATE_APK_DOWNLOAD_HOSTS = setOf(
    "github.com",
    "objects.githubusercontent.com",
    "release-assets.githubusercontent.com",
)

/** The release's APK on GitHub, e.g. .../releases/download/v0.90-beta/Subshell-0.90-beta.apk. */
internal fun updateApkUrl(release: UpdateManifestRelease): HttpUrl =
    "https://github.com/brealorg/subshell/releases/download/v${release.versionName}/Subshell-${release.versionName}.apk"
        .toHttpUrl()

internal fun updateApkDownloadAllowed(url: HttpUrl): Boolean =
    url.isHttps && url.host in UPDATE_APK_DOWNLOAD_HOSTS

internal sealed interface UpdateApkDownload {
    data class Verified(val file: File) : UpdateApkDownload

    data class Failed(val failure: UpdateApkFailure) : UpdateApkDownload
}

internal enum class UpdateApkFailure(val message: String) {
    NETWORK("Couldn't download the update. Check your connection and try again."),
    NOT_FOUND("GitHub doesn't have this release's APK."),
    UNEXPECTED_ADDRESS("The download went somewhere other than GitHub, so it was stopped."),
    TOO_LARGE("The download is larger than an update can be, so it was stopped."),
    HASH_MISMATCH("The downloaded file isn't the published update, so it was deleted."),
}

internal class UpdateApkDownloader(
    callFactory: Call.Factory = io.github.brealorg.subshell.data.network.SubshellNetworkTransport.sharedCallFactory,
    private val maxBytes: Long = UPDATE_MAX_APK_BYTES,
) {
    // Redirects are followed here, one host check at a time.
    private val client: Call.Factory = (callFactory as? OkHttpClient)
        ?.newBuilder()
        ?.followRedirects(false)
        ?.followSslRedirects(false)
        ?.build()
        ?: callFactory

    suspend fun download(
        release: UpdateManifestRelease,
        target: File,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ): UpdateApkDownload = withContext(Dispatchers.IO) {
        try {
            val result = fetch(release, target, onProgress)
            if (result !is UpdateApkDownload.Verified) target.delete()
            result
        } catch (cancelled: CancellationException) {
            target.delete()
            throw cancelled
        } catch (_: IOException) {
            target.delete()
            UpdateApkDownload.Failed(UpdateApkFailure.NETWORK)
        }
    }

    /**
     * ISSUE58_THE_CHECKED_APK_IS_KEPT_UNTIL_THE_INSTALL_IS_DECIDED_V1: a copy
     * kept from an earlier try is used again only when its SHA-256 is the
     * manifest's; anything else is deleted.
     */
    suspend fun reuseKept(release: UpdateManifestRelease, file: File): UpdateApkDownload.Verified? =
        withContext(Dispatchers.IO) {
            if (!file.isFile) return@withContext null
            try {
                if (file.length() > maxBytes) {
                    file.delete()
                    return@withContext null
                }
                val digest = MessageDigest.getInstance("SHA-256")
                file.inputStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                    }
                }
                val sha = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
                if (sha == release.apkSha256) {
                    UpdateApkDownload.Verified(file)
                } else {
                    file.delete()
                    null
                }
            } catch (_: IOException) {
                file.delete()
                null
            }
        }

    private suspend fun fetch(
        release: UpdateManifestRelease,
        target: File,
        onProgress: (Long, Long) -> Unit,
    ): UpdateApkDownload {
        var url = updateApkUrl(release)
        repeat(UPDATE_MAX_APK_REDIRECTS + 1) {
            if (!updateApkDownloadAllowed(url)) {
                return UpdateApkDownload.Failed(UpdateApkFailure.UNEXPECTED_ADDRESS)
            }
            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", "subshell-update01")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isRedirect) {
                    val next = response.header("Location")?.let(url::resolve)
                        ?: return UpdateApkDownload.Failed(UpdateApkFailure.UNEXPECTED_ADDRESS)
                    url = next
                    return@use
                }
                if (response.code == 404) return UpdateApkDownload.Failed(UpdateApkFailure.NOT_FOUND)
                if (!response.isSuccessful) return UpdateApkDownload.Failed(UpdateApkFailure.NETWORK)
                val total = response.body.contentLength()
                if (total > maxBytes) return UpdateApkDownload.Failed(UpdateApkFailure.TOO_LARGE)
                val digest = MessageDigest.getInstance("SHA-256")
                target.parentFile?.mkdirs()
                var downloaded = 0L
                response.body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            downloaded += count
                            if (downloaded > maxBytes) return UpdateApkDownload.Failed(UpdateApkFailure.TOO_LARGE)
                            digest.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                            onProgress(downloaded, total)
                        }
                    }
                }
                val sha = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
                return if (sha == release.apkSha256) {
                    UpdateApkDownload.Verified(target)
                } else {
                    UpdateApkDownload.Failed(UpdateApkFailure.HASH_MISMATCH)
                }
            }
        }
        return UpdateApkDownload.Failed(UpdateApkFailure.UNEXPECTED_ADDRESS)
    }
}
