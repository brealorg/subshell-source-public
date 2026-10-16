package app.subshell.prototype.update

import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.data.network.SubshellNetworkTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import okhttp3.Call
import okhttp3.Request

/**
 * UPDATE02_FAILED_FETCH_IS_NOT_A_CHECK_V1
 * [Completed] means the manifest was read: its [Completed.release] is null
 * when it was rejected (wrong schema or channel, invalid, too large).
 * [Failed] means no usable answer (no connection, timeout, non-2xx): the
 * check did not happen and should be retried.
 */
internal sealed interface UpdateManifestFetch {
    data class Completed(val release: UpdateManifestRelease?) : UpdateManifestFetch
    data object Failed : UpdateManifestFetch
}

internal interface UpdateManifestSource {
    suspend fun fetch(): UpdateManifestFetch
}

internal class UpdateManifestClient(
    private val callFactory: Call.Factory = SubshellNetworkTransport.sharedCallFactory,
) : UpdateManifestSource {
    override suspend fun fetch(): UpdateManifestFetch = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(UPDATE01_MANIFEST_URL)
            .get()
            .header("Accept", "application/json")
            .header("User-Agent", "subshell-update01")
            .build()

        try {
            callFactory.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use UpdateManifestFetch.Failed
                val raw = try {
                    response.body.byteStream().use { input ->
                        input.readUpdateUtf8Bounded(UPDATE01_MAX_MANIFEST_BYTES)
                    }
                } catch (_: UpdateBodyTooLargeException) {
                    return@use UpdateManifestFetch.Completed(release = null)
                }
                val decoded = try {
                    SubshellNetworkJson.providerJson
                        .decodeFromString<UpdateManifestRelease>(raw)
                } catch (_: IllegalArgumentException) {
                    return@use UpdateManifestFetch.Completed(release = null)
                }
                UpdateManifestFetch.Completed(normalizeUpdateManifest(decoded))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            UpdateManifestFetch.Failed
        }
    }
}
