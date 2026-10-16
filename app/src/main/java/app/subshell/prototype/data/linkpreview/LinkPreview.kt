package app.subshell.prototype.data.linkpreview

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal data class LinkPreviewMetadata(
    val displayHost: String,
    val title: String?,
    val imageUrl: String?,
)

internal object LinkPreviewResolver {
    private val cache = object : LinkedHashMap<String, LinkPreviewMetadata>(
        LINK_PREVIEW_CACHE_SIZE,
        0.75f,
        true,
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, LinkPreviewMetadata>?,
        ): Boolean = size > LINK_PREVIEW_CACHE_SIZE
    }

    private val client = OkHttpClient.Builder()
        .callTimeout(LINK_PREVIEW_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .connectTimeout(LINK_PREVIEW_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(LINK_PREVIEW_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .dns(PublicPreviewDns)
        .build()

    fun canResolve(rawUrl: String): Boolean = previewUrlOrNull(rawUrl) != null

    suspend fun resolve(rawUrl: String): LinkPreviewMetadata? {
        val initialUrl = previewUrlOrNull(rawUrl) ?: return null
        val cacheKey = initialUrl.toString()
        synchronized(cache) { cache[cacheKey] }?.let { return it }

        val resolved = try {
            // LOW_LINK_PARSED_OFF_MAIN_V1 (OCB-533): the caller is a
            // composition effect; parsing up to 512 KiB of HTML stays on IO.
            fetchDocument(initialUrl)?.let { (finalUrl, html) ->
                withContext(Dispatchers.IO) {
                    parseLinkPreviewDocument(finalUrl, html)?.withPublicImageOnly()
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: IOException) {
            null
        } catch (_: RuntimeException) {
            null
        }

        if (resolved != null) synchronized(cache) { cache[cacheKey] = resolved }
        return resolved
    }

    // LOW_LINK_IMAGE_HOST_MUST_BE_PUBLIC_V1 (OCB-535): the page passed the
    // public-address DNS rule, but its og:image is loaded by the general image
    // loader; an image whose host resolves to a private address is dropped.
    private fun LinkPreviewMetadata.withPublicImageOnly(): LinkPreviewMetadata? {
        val image = imageUrl?.toHttpUrlOrNull() ?: return this
        val public = runCatching { PublicPreviewDns.lookup(image.host) }.isSuccess ||
            literalAddress(image.host) != null
        return when {
            public -> this
            title.isNullOrBlank() -> null
            else -> copy(imageUrl = null)
        }
    }

    private suspend fun fetchDocument(initialUrl: HttpUrl): Pair<HttpUrl, String>? =
        withContext(Dispatchers.IO) {
            var currentUrl = initialUrl
            for (redirectCount in 0..LINK_PREVIEW_MAX_REDIRECTS) {
                val request = Request.Builder()
                    .url(currentUrl)
                    .get()
                    .header(
                        "Accept",
                        "text/html,application/xhtml+xml;q=0.9,*/*;q=0.1",
                    )
                    .header("User-Agent", LINK_PREVIEW_USER_AGENT)
                    .build()
                val response = client.newCall(request).awaitResponse()

                if (response.code in 300..399) {
                    val nextUrl = response.header("Location")
                        ?.let(currentUrl::resolve)
                        ?.takeIf(::isStructurallySafePreviewUrl)
                    response.close()
                    if (redirectCount == LINK_PREVIEW_MAX_REDIRECTS || nextUrl == null) {
                        return@withContext null
                    }
                    currentUrl = nextUrl
                    continue
                }

                response.use { currentResponse ->
                    if (!currentResponse.isSuccessful) return@withContext null
                    val body = currentResponse.body
                    val contentType = body.contentType()
                    if (
                        contentType != null &&
                        !(contentType.type == "text" && contentType.subtype == "html") &&
                        !(contentType.type == "application" && contentType.subtype == "xhtml+xml")
                    ) {
                        return@withContext null
                    }
                    val contentLength = body.contentLength()
                    if (contentLength == 0L || contentLength > LINK_PREVIEW_MAX_HTML_BYTES) {
                        return@withContext null
                    }
                    val bytes = body.byteStream().readBounded(LINK_PREVIEW_MAX_HTML_BYTES)
                        ?: return@withContext null
                    // LOW_LINK_DOCUMENT_CHARSET_V1 (OCB-534)
                    val charset = contentType?.charset()
                        ?: declaredDocumentCharset(bytes)
                        ?: Charsets.UTF_8
                    return@withContext currentUrl to String(bytes, charset)
                }
            }
            null
        }

}

internal fun parseLinkPreviewDocument(
    pageUrl: HttpUrl,
    html: String,
): LinkPreviewMetadata? {
    val head = html.substring(0, html.indexOf("</head", ignoreCase = true).takeIf { it >= 0 }
        ?: html.length)
    val metadata = META_TAG.findAll(head).mapNotNull { match ->
        val attributes = HTML_ATTRIBUTE.findAll(match.value).associate { attribute ->
            attribute.groupValues[1].lowercase(Locale.ROOT) to attribute.groupValues
                .drop(2)
                .firstOrNull(String::isNotEmpty)
                .orEmpty()
        }
        val key = attributes["property"]?.ifBlank { null }
            ?: attributes["name"]?.ifBlank { null }
            ?: return@mapNotNull null
        val content = attributes["content"]?.ifBlank { null } ?: return@mapNotNull null
        key.lowercase(Locale.ROOT) to decodeHtmlText(content)
    }.toList()

    fun metadataValue(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
        metadata.firstOrNull { (candidate, _) -> candidate == key }?.second?.ifBlank { null }
    }

    val documentTitle = TITLE_TAG.find(head)?.groupValues?.get(1)?.let(::decodeHtmlText)
    val title = metadataValue("og:title", "twitter:title")
        ?: documentTitle?.ifBlank { null }
    val imageUrl = metadataValue(
        "og:image:secure_url",
        "og:image:url",
        "og:image",
        "twitter:image",
        "twitter:image:src",
    )
        ?.takeIf { it.length <= LINK_PREVIEW_MAX_URL_LENGTH }
        ?.let(pageUrl::resolve)
        ?.takeIf(::isStructurallySafePreviewUrl)
        ?.toString()

    if (title.isNullOrBlank() && imageUrl == null) return null
    return LinkPreviewMetadata(
        displayHost = pageUrl.host.removePrefix("www.").uppercase(Locale.ROOT),
        title = title?.take(LINK_PREVIEW_MAX_TITLE_LENGTH),
        imageUrl = imageUrl,
    )
}

/** The charset a page declares in its first bytes (<meta charset> or http-equiv). */
internal fun declaredDocumentCharset(bytes: ByteArray): java.nio.charset.Charset? {
    val start = String(bytes, 0, minOf(bytes.size, 2_048), Charsets.ISO_8859_1)
    val name = META_CHARSET.find(start)?.groupValues?.get(1)?.trim() ?: return null
    return runCatching { java.nio.charset.Charset.forName(name) }.getOrNull()
}

internal fun isStructurallySafePreviewUrl(url: HttpUrl): Boolean {
    if (!url.isHttps || url.username.isNotEmpty() || url.password.isNotEmpty()) return false
    val host = url.host.trimEnd('.').lowercase(Locale.ROOT)
    if (
        host == "localhost" ||
        host.endsWith(".localhost") ||
        host.endsWith(".local") ||
        host.endsWith(".internal") ||
        host.endsWith(".lan") ||
        host == "home.arpa" ||
        host.endsWith(".home.arpa")
    ) {
        return false
    }
    return literalAddress(host)?.let { !isBlockedPreviewAddress(it) } ?: true
}

internal fun isBlockedPreviewAddress(address: InetAddress): Boolean {
    if (
        address.isAnyLocalAddress ||
        address.isLoopbackAddress ||
        address.isLinkLocalAddress ||
        address.isSiteLocalAddress ||
        address.isMulticastAddress
    ) {
        return true
    }
    val bytes = address.address
    if (bytes.size == 4) return isBlockedIpv4(bytes)
    if (bytes.size != 16) return true

    val first = bytes[0].toInt() and 0xff
    if (first and 0xfe == 0xfc) return true
    val ipv4Mapped = bytes.take(10).all { it == 0.toByte() } &&
        bytes[10] == 0xff.toByte() && bytes[11] == 0xff.toByte()
    // LOW_LINK_EMBEDDED_IPV4_IS_CHECKED_V1 (OCB-628): IPv4-compatible
    // (::a.b.c.d), NAT64 (64:ff9b::/96) and 6to4 (2002::/16) carry an IPv4
    // address that is checked like a plain one.
    val ipv4Compatible = bytes.take(12).all { it == 0.toByte() }
    val nat64 = bytes[0] == 0x00.toByte() && bytes[1] == 0x64.toByte() &&
        bytes[2] == 0xff.toByte() && bytes[3] == 0x9b.toByte() &&
        bytes.copyOfRange(4, 12).all { it == 0.toByte() }
    if (ipv4Mapped || ipv4Compatible || nat64) return isBlockedIpv4(bytes.copyOfRange(12, 16))
    val sixToFour = bytes[0] == 0x20.toByte() && bytes[1] == 0x02.toByte()
    if (sixToFour) return isBlockedIpv4(bytes.copyOfRange(2, 6))
    return false
}

private fun previewUrlOrNull(rawUrl: String): HttpUrl? = rawUrl.trim()
    .takeIf { it.length <= LINK_PREVIEW_MAX_URL_LENGTH }
    ?.toHttpUrlOrNull()
    ?.takeIf(::isStructurallySafePreviewUrl)

internal fun literalAddress(host: String): InetAddress? {
    val isIpv4 = host.split('.').let { parts ->
        parts.size == 4 && parts.all { part ->
            part.isNotEmpty() && part.all(Char::isDigit) && part.toIntOrNull() in 0..255
        }
    }
    if (!isIpv4 && ':' !in host) return null
    return runCatching { InetAddress.getByName(host) }.getOrNull()
}

private fun isBlockedIpv4(bytes: ByteArray): Boolean {
    val first = bytes[0].toInt() and 0xff
    val second = bytes[1].toInt() and 0xff
    return first == 0 ||
        first == 10 ||
        first == 127 ||
        (first == 100 && second in 64..127) ||
        (first == 169 && second == 254) ||
        (first == 172 && second in 16..31) ||
        (first == 192 && second == 168) ||
        (first == 198 && second in 18..19) ||
        first >= 224
}

private fun decodeHtmlText(value: String): String = HTML_ENTITY.replace(value) { match ->
    val token = match.groupValues[1]
    when {
        token.equals("amp", true) -> "&"
        token.equals("quot", true) -> "\""
        token.equals("apos", true) || token == "#39" -> "'"
        token.equals("lt", true) -> "<"
        token.equals("gt", true) -> ">"
        token.equals("nbsp", true) -> " "
        // LOW_LINK_COMMON_NAMED_ENTITIES_V1 (OCB-536)
        token in NAMED_HTML_ENTITIES -> NAMED_HTML_ENTITIES.getValue(token)
        token.startsWith("#x", true) -> token.drop(2).toIntOrNull(16).toUnicodeOrNull()
            ?: match.value
        token.startsWith('#') -> token.drop(1).toIntOrNull().toUnicodeOrNull() ?: match.value
        else -> match.value
    }
}.replace(WHITESPACE, " ").trim()

private fun Int?.toUnicodeOrNull(): String? = this?.let { codePoint ->
    runCatching { String(Character.toChars(codePoint)) }.getOrNull()
}

private fun java.io.InputStream.readBounded(maxBytes: Long): ByteArray? =
    use { input ->
        ByteArrayOutputStream().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) return null
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
    }

private object PublicPreviewDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = Dns.SYSTEM.lookup(hostname)
        if (addresses.isEmpty() || addresses.any(::isBlockedPreviewAddress)) {
            throw UnknownHostException("Link preview host is not public")
        }
        return addresses
    }
}

private suspend fun Call.awaitResponse(): Response =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response) { _, responseToClose, _ ->
                        responseToClose.close()
                    }
                }
            },
        )
    }

private val META_TAG = Regex("""(?is)<meta\b(?:[^>"']|"[^"]*"|'[^']*')*>""")
private val TITLE_TAG = Regex("(?is)<title\\b[^>]*>(.*?)</title\\s*>")
private val HTML_ATTRIBUTE = Regex(
    """(?is)([a-zA-Z_:][-a-zA-Z0-9_:.]*)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+))""",
)
private val HTML_ENTITY = Regex("&(#[xX][0-9a-fA-F]+|#[0-9]+|[a-zA-Z][a-zA-Z0-9]{1,8});")
private val NAMED_HTML_ENTITIES = mapOf(
    "rsquo" to "\u2019", "lsquo" to "\u2018", "rdquo" to "\u201D", "ldquo" to "\u201C",
    "sbquo" to "\u201A", "bdquo" to "\u201E", "mdash" to "\u2014", "ndash" to "\u2013",
    "hellip" to "\u2026", "laquo" to "\u00AB", "raquo" to "\u00BB", "lsaquo" to "\u2039",
    "rsaquo" to "\u203A", "middot" to "\u00B7", "bull" to "\u2022", "copy" to "\u00A9",
    "reg" to "\u00AE", "trade" to "\u2122", "deg" to "\u00B0", "times" to "\u00D7",
    "euro" to "\u20AC", "pound" to "\u00A3", "eacute" to "\u00E9", "egrave" to "\u00E8",
    "Eacute" to "\u00C9", "aacute" to "\u00E1", "agrave" to "\u00E0", "iacute" to "\u00ED",
    "oacute" to "\u00F3", "uacute" to "\u00FA", "ntilde" to "\u00F1", "ccedil" to "\u00E7",
    "auml" to "\u00E4", "ouml" to "\u00F6", "uuml" to "\u00FC", "Auml" to "\u00C4",
    "Ouml" to "\u00D6", "Uuml" to "\u00DC", "szlig" to "\u00DF", "aring" to "\u00E5",
    "Aring" to "\u00C5", "aelig" to "\u00E6", "AElig" to "\u00C6", "oslash" to "\u00F8",
    "Oslash" to "\u00D8",
)
private val META_CHARSET = Regex(
    """(?i)<meta\b[^>]*?charset\s*=\s*["']?\s*([A-Za-z0-9._:-]{1,40})""",
)
private val WHITESPACE = Regex("\\s+")

private const val LINK_PREVIEW_CACHE_SIZE = 24
private const val LINK_PREVIEW_MAX_REDIRECTS = 3
private const val LINK_PREVIEW_MAX_HTML_BYTES = 512L * 1_024L
private const val LINK_PREVIEW_MAX_TITLE_LENGTH = 300
private const val LINK_PREVIEW_MAX_URL_LENGTH = 2_048
private const val LINK_PREVIEW_CALL_TIMEOUT_SECONDS = 8L
private const val LINK_PREVIEW_CONNECT_TIMEOUT_SECONDS = 5L
private const val LINK_PREVIEW_READ_TIMEOUT_SECONDS = 5L
private const val LINK_PREVIEW_USER_AGENT = "subshell-link-preview/1.0"
