package app.subshell.prototype.data.linkpreview

import java.net.InetAddress
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LINK1 (low link preview findings): a charset declared in the page is used
 * (OCB-534), common named entities are decoded (OCB-536), a quoted '>' does
 * not cut a meta tag (OCB-537), and private IPv4 inside NAT64, 6to4 and
 * IPv4-compatible IPv6 is blocked (OCB-628).
 */
class LowLinkPreviewTest {
    @Test
    fun aCharsetDeclaredInThePageIsRead() {
        val page = """<html><head><meta charset="windows-1252"><title>caf""".toByteArray(Charsets.ISO_8859_1)
        assertEquals("windows-1252", declaredDocumentCharset(page)?.name()?.lowercase())
        val httpEquiv = """<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">"""
            .toByteArray(Charsets.ISO_8859_1)
        assertEquals("ISO-8859-1", declaredDocumentCharset(httpEquiv)?.name())
        assertNull(declaredDocumentCharset("<html><head><title>x</title>".toByteArray()))
    }

    @Test
    fun commonNamedEntitiesAreDecoded() {
        val result = parseLinkPreviewDocument(
            "https://example.com/a".toHttpUrl(),
            """<head><meta property="og:title" content="It&rsquo;s here &mdash; caf&eacute; &laquo;news&raquo;&hellip; &unknown;"></head>""",
        )

        assertEquals("It’s here — café «news»… &unknown;", result?.title)
    }

    @Test
    fun aQuotedGreaterThanSignDoesNotCutTheMetaTag() {
        val result = parseLinkPreviewDocument(
            "https://example.com/a".toHttpUrl(),
            """<head><title>Fallback</title><meta property="og:title" content="A > B, really"></head>""",
        )

        assertEquals("A > B, really", result?.title)
    }

    @Test
    fun privateIpv4InsideIpv6FormsIsBlocked() {
        fun blocked(literal: String) = isBlockedPreviewAddress(InetAddress.getByName(literal))

        assertTrue("NAT64", blocked("64:ff9b::a00:1"))
        assertTrue("6to4", blocked("2002:c0a8:0101::1"))
        assertTrue("IPv4-compatible", blocked("::7f00:1"))
        assertFalse("NAT64 of a public address", blocked("64:ff9b::808:808"))
        assertFalse("6to4 of a public address", blocked("2002:0808:0808::1"))
    }
}
