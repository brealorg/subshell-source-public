package app.subshell.prototype.media

import app.subshell.prototype.data.error.LocalFailureKind
import app.subshell.prototype.data.linkpreview.PublicPreviewDns
import java.net.UnknownHostException
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SECAUDIT14 (security audit #14): post images were fetched from any https
 * host, private addresses included, so a post could make the reader's device
 * send a GET into its own network. Post media now follows the link-preview
 * rule for hosts and addresses.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SecAudit14PublicMediaHostsTest {
    @Test
    fun localNamesAndPrivateAddressesAreNotPostMediaHosts() {
        listOf(
            "https://192.168.1.1/a.png",
            "https://10.0.0.5/a.png",
            "https://127.0.0.1/a.png",
            "https://[::1]/a.png",
            "https://localhost/a.png",
            "https://printer.local/a.png",
            "https://router.lan/a.png",
            "https://user:pass@i.redd.it/a.png",
            "http://i.redd.it/a.png",
        ).forEach { url -> assertFalse(url, isPublicRemoteMediaUrl(url.toHttpUrl())) }

        assertTrue(isPublicRemoteMediaUrl("https://i.redd.it/a.png".toHttpUrl()))
        assertTrue(isPublicRemoteMediaUrl("https://i.imgur.com/a.png".toHttpUrl()))
    }

    @Test
    fun aPrivateImageIsRefusedWithoutARequest() = runBlocking {
        val noNetwork = Call.Factory { error("no request may be made") }

        val result = RemoteImageLoader.load("https://192.168.1.1/secaudit14.png", noNetwork)

        assertTrue(result is RemoteMediaLoadResult.Failed)
        assertEquals(LocalFailureKind.SECURITY, (result as RemoteMediaLoadResult.Failed).details.localKind)
    }

    @Test
    fun theMediaClientResolvesOnlyPublicAddresses() {
        val client = publicMediaReadCallFactory as OkHttpClient
        assertSame(PublicPreviewDns, client.dns)
        val refused = runCatching { client.dns.lookup("localhost") }.exceptionOrNull()
        assertTrue(refused is UnknownHostException)
    }
}
