package app.subshell.prototype.data.linkpreview

import java.net.InetAddress
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkPreviewPolicyTest {
    @Test
    fun openGraphMetadataProducesCompactPreviewWithRelativeHttpsImage() {
        val result = parseLinkPreviewDocument(
            "https://www.vg.no/sport/article".toHttpUrl(),
            """
                <html><head>
                  <meta content="VG" property="og:site_name">
                  <meta content="Luis &amp; ekspertene lar seg begeistre" property="og:title">
                  <meta property="og:image" content="/images/luis.jpg">
                </head><body>ignored</body></html>
            """.trimIndent(),
        )

        requireNotNull(result)
        assertEquals("VG.NO", result.displayHost)
        assertEquals("Luis & ekspertene lar seg begeistre", result.title)
        assertEquals("https://www.vg.no/images/luis.jpg", result.imageUrl)
    }

    @Test
    fun documentTitleAndTwitterImageAreSafeFallbacks() {
        val result = parseLinkPreviewDocument(
            "https://example.com/story".toHttpUrl(),
            """
                <head>
                  <title>Useful &#x2013; story</title>
                  <meta name='twitter:image' content='https://cdn.example.com/story.png'>
                </head>
            """.trimIndent(),
        )

        requireNotNull(result)
        assertEquals("Useful – story", result.title)
        assertEquals("https://cdn.example.com/story.png", result.imageUrl)
    }

    @Test
    fun encodedOpenGraphImageFromLivePublisherFormatSurvivesPreviewHandoff() {
        val result = parseLinkPreviewDocument(
            "https://www.theverge.com/tech/975677/story".toHttpUrl(),
            """
                <head>
                  <meta property="og:title" content="Google just announced a major shakeup">
                  <meta property="og:image" content="https://platform.theverge.com/wp-content/uploads/story.jpg?quality=90&amp;strip=all&amp;crop=0%2C10.75%2C100%2C78.49&amp;w=1200">
                  <meta name="twitter:image" content="https://platform.theverge.com/wp-content/uploads/story.jpg?quality=90&amp;w=1200">
                </head>
            """.trimIndent(),
        )

        requireNotNull(result)
        assertEquals(
            "https://platform.theverge.com/wp-content/uploads/story.jpg?quality=90&strip=all&crop=0%2C10.75%2C100%2C78.49&w=1200",
            result.imageUrl,
        )
    }

    @Test
    fun documentWithoutTitleOrImageDoesNotClaimPreviewWasFound() {
        assertNull(
            parseLinkPreviewDocument(
                "https://example.com/story".toHttpUrl(),
                "<html><head><meta name=description content='Only a description'></head></html>",
            ),
        )
    }

    @Test
    fun insecureImageIsOmittedWithoutDiscardingUsefulTitle() {
        val result = parseLinkPreviewDocument(
            "https://example.com/story".toHttpUrl(),
            """
                <head>
                  <meta property="og:title" content="Safe title">
                  <meta property="og:image" content="http://example.com/image.jpg">
                </head>
            """.trimIndent(),
        )

        requireNotNull(result)
        assertEquals("Safe title", result.title)
        assertNull(result.imageUrl)
    }

    @Test
    fun localAndCredentialedPreviewUrlsAreRejected() {
        assertFalse(isStructurallySafePreviewUrl("https://localhost/page".toHttpUrl()))
        assertFalse(isStructurallySafePreviewUrl("https://192.168.1.20/page".toHttpUrl()))
        assertFalse(isStructurallySafePreviewUrl("https://user:pass@example.com/page".toHttpUrl()))
        assertTrue(isStructurallySafePreviewUrl("https://example.com/page".toHttpUrl()))
    }

    @Test
    fun privateAndSharedNetworkAddressesAreBlocked() {
        assertTrue(isBlockedPreviewAddress(InetAddress.getByName("10.0.0.1")))
        assertTrue(isBlockedPreviewAddress(InetAddress.getByName("100.64.0.1")))
        assertTrue(isBlockedPreviewAddress(InetAddress.getByName("fc00::1")))
        assertFalse(isBlockedPreviewAddress(InetAddress.getByName("1.1.1.1")))
    }
}
