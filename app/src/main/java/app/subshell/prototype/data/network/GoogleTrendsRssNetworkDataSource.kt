package app.subshell.prototype.data.network

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.Request
import org.w3c.dom.Element

internal data class GoogleTrendsRssItem(
    val title: String,
    val approximateTraffic: String,
)

internal object GoogleTrendsRssNetworkDataSource {
    private val SOURCE_URL: HttpUrl = HttpUrl.Builder()
        .scheme("https")
        .host("trends.google.com")
        .addPathSegment("trending")
        .addPathSegment("rss")
        .addQueryParameter("geo", "US")
        .build()
    private const val MAX_RESPONSE_BYTES = 512L * 1024L
    private const val MAX_ITEMS = 16
    private const val USER_AGENT = "subshell-search-discovery/1.0"

    suspend fun load(
        callFactory: Call.Factory = SubshellNetworkTransport.sharedCallFactory,
    ): List<GoogleTrendsRssItem> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(SOURCE_URL)
            .header("Accept", "application/rss+xml, application/xml;q=0.9, text/xml;q=0.8")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        try {
            // LOW_NET_TRENDS_CANCELLABLE_AND_BOUNDED_V1 (OCB-538): a cancelled
            // search screen cancels the call, and reading a body of unknown length is
            // stopped once it passes the limit.
            callFactory.newCall(request).awaitResponse().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body
                val declaredLength = body.contentLength()
                if (declaredLength > MAX_RESPONSE_BYTES) return@withContext emptyList()
                val bytes = body.byteStream().use { input -> readAtMost(input, MAX_RESPONSE_BYTES) }
                    ?: return@withContext emptyList()
                parseGoogleTrendsRss(bytes.toString(StandardCharsets.UTF_8)).take(MAX_ITEMS)
            }
        } catch (_: IOException) {
            emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
    }
}

/** The stream's bytes, or null once it passes [maxBytes]. */
internal fun readAtMost(input: InputStream, maxBytes: Long): ByteArray? {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    var total = 0L
    while (true) {
        val count = input.read(buffer)
        if (count == -1) return output.toByteArray()
        total += count
        if (total > maxBytes) return null
        output.write(buffer, 0, count)
    }
}

internal fun parseGoogleTrendsRss(xml: String): List<GoogleTrendsRssItem> {
    if (xml.isBlank() || xml.length > 1_000_000) return emptyList()
    if (xml.contains("<!DOCTYPE", ignoreCase = true)) return emptyList()
    return runCatching {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
        }
        val document = factory.newDocumentBuilder().parse(
            ByteArrayInputStream(xml.toByteArray(StandardCharsets.UTF_8)),
        )
        val nodes = document.getElementsByTagName("item")
        buildList {
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as? Element ?: continue
                val title = element.childText("title").trim()
                if (title.isBlank()) continue
                add(
                    GoogleTrendsRssItem(
                        title = title,
                        approximateTraffic = element.childText("approx_traffic").trim(),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())
}

private fun Element.childText(localName: String): String {
    val children = childNodes
    for (index in 0 until children.length) {
        val child = children.item(index)
        val candidate = child.localName ?: child.nodeName.substringAfter(':')
        if (candidate == localName) return child.textContent.orEmpty()
    }
    return ""
}
